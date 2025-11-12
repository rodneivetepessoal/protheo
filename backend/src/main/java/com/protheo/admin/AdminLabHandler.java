package com.protheo.admin;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.protheo.model.AdminAuditLog;
import com.protheo.model.LabConfig;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.Map;

/**
 * Handler administrativo para gestão de laboratórios
 */
@Slf4j
@Named("adminLab")
public class AdminLabHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Inject
    DynamoDbEnhancedClient dynamoDb;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String path = event.getRawPath();
            String method = event.getRequestContext().getHttp().getMethod();
            
            // Verificar se é admin (via Cognito groups)
            if (!isAdmin(event)) {
                return buildResponse(403, Map.of("error", "Forbidden: Admin access required"));
            }

            String adminId = getAdminId(event);
            String adminEmail = getAdminEmail(event);

            // Roteamento
            if (path.contains("/labs/") && method.equals("PUT")) {
                return updateLabConfig(event, adminId, adminEmail);
            } else if (path.contains("/labs/") && path.contains("/status") && method.equals("PUT")) {
                return updateLabStatus(event, adminId, adminEmail);
            } else if (path.contains("/labs/") && path.contains("/transfer-config") && method.equals("PUT")) {
                return updateTransferConfig(event, adminId, adminEmail);
            } else if (path.contains("/labs") && method.equals("GET")) {
                return listLabsWithConfig(event);
            }

            return buildResponse(404, Map.of("error", "Endpoint not found"));

        } catch (Exception e) {
            log.error("Error in admin lab handler", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Atualizar configuração de laboratório (comissões, liberações, etc)
     */
    private APIGatewayV2HTTPResponse updateLabConfig(APIGatewayV2HTTPEvent event, 
                                                      String adminId, String adminEmail) {
        try {
            String labId = event.getPathParameters().get("labId");
            UpdateLabConfigRequest request = objectMapper.readValue(
                    event.getBody(),
                    UpdateLabConfigRequest.class
            );

            DynamoDbTable<LabConfig> configTable = dynamoDb.table(
                    System.getenv("LAB_CONFIG_TABLE"),
                    TableSchema.fromBean(LabConfig.class)
            );

            // Buscar ou criar config
            LabConfig config = configTable.getItem(r -> r.key(k -> k.partitionValue(labId)));
            if (config == null) {
                config = LabConfig.builder()
                        .labId(labId)
                        .status(LabConfig.LabStatus.ACTIVE)
                        .createdAt(Instant.now().toString())
                        .build();
            }

            // Atualizar valores
            Map<String, String> oldValues = captureOldValues(config);
            
            if (request.getCustomCommissionPercentage() != null) {
                config.setCustomCommissionPercentage(request.getCustomCommissionPercentage());
            }
            if (request.getCustomFirstReleasePercentage() != null) {
                config.setCustomFirstReleasePercentage(request.getCustomFirstReleasePercentage());
            }
            if (request.getCustomDisputePeriodDays() != null) {
                config.setCustomDisputePeriodDays(request.getCustomDisputePeriodDays());
            }
            if (request.getCustomMaxFreeRevisions() != null) {
                config.setCustomMaxFreeRevisions(request.getCustomMaxFreeRevisions());
            }

            config.setUpdatedAt(Instant.now().toString());
            config.setUpdatedBy(adminId);

            configTable.putItem(config);

            // Log de auditoria
            Map<String, String> newValues = captureNewValues(config);
            logAudit(adminId, adminEmail, AdminAuditLog.AdminAction.UPDATE_LAB_COMMISSION,
                    "LAB", labId, "Updated lab configuration", oldValues, newValues);

            log.info("Lab config updated by admin: {} for lab: {}", adminEmail, labId);

            return buildResponse(200, Map.of(
                    "message", "Lab configuration updated successfully",
                    "config", config
            ));

        } catch (Exception e) {
            log.error("Error updating lab config", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Atualizar status do laboratório (ativo, suspenso, bloqueado)
     */
    private APIGatewayV2HTTPResponse updateLabStatus(APIGatewayV2HTTPEvent event,
                                                      String adminId, String adminEmail) {
        try {
            String labId = event.getPathParameters().get("labId");
            UpdateLabStatusRequest request = objectMapper.readValue(
                    event.getBody(),
                    UpdateLabStatusRequest.class
            );

            DynamoDbTable<LabConfig> configTable = dynamoDb.table(
                    System.getenv("LAB_CONFIG_TABLE"),
                    TableSchema.fromBean(LabConfig.class)
            );

            LabConfig config = configTable.getItem(r -> r.key(k -> k.partitionValue(labId)));
            if (config == null) {
                config = LabConfig.builder()
                        .labId(labId)
                        .createdAt(Instant.now().toString())
                        .build();
            }

            LabConfig.LabStatus oldStatus = config.getStatus();
            config.setStatus(request.getStatus());
            config.setStatusReason(request.getReason());
            config.setUpdatedAt(Instant.now().toString());
            config.setUpdatedBy(adminId);

            configTable.putItem(config);

            // Log de auditoria
            AdminAuditLog.AdminAction action = determineStatusAction(request.getStatus());
            logAudit(adminId, adminEmail, action, "LAB", labId,
                    String.format("Status changed from %s to %s: %s", 
                            oldStatus, request.getStatus(), request.getReason()),
                    Map.of("oldStatus", String.valueOf(oldStatus)),
                    Map.of("newStatus", String.valueOf(request.getStatus())));

            log.info("Lab status updated by admin: {} for lab: {} to {}", 
                    adminEmail, labId, request.getStatus());

            return buildResponse(200, Map.of(
                    "message", "Lab status updated successfully",
                    "config", config
            ));

        } catch (Exception e) {
            log.error("Error updating lab status", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Atualizar configuração de repasse
     */
    private APIGatewayV2HTTPResponse updateTransferConfig(APIGatewayV2HTTPEvent event,
                                                           String adminId, String adminEmail) {
        try {
            String labId = event.getPathParameters().get("labId");
            LabConfig.TransferConfig transferConfig = objectMapper.readValue(
                    event.getBody(),
                    LabConfig.TransferConfig.class
            );

            DynamoDbTable<LabConfig> configTable = dynamoDb.table(
                    System.getenv("LAB_CONFIG_TABLE"),
                    TableSchema.fromBean(LabConfig.class)
            );

            LabConfig config = configTable.getItem(r -> r.key(k -> k.partitionValue(labId)));
            if (config == null) {
                config = LabConfig.builder()
                        .labId(labId)
                        .status(LabConfig.LabStatus.ACTIVE)
                        .createdAt(Instant.now().toString())
                        .build();
            }

            config.setTransferConfig(transferConfig);
            config.setUpdatedAt(Instant.now().toString());
            config.setUpdatedBy(adminId);

            configTable.putItem(config);

            // Log de auditoria
            logAudit(adminId, adminEmail, AdminAuditLog.AdminAction.UPDATE_LAB_TRANSFER_CONFIG,
                    "LAB", labId, "Updated transfer configuration", null, null);

            log.info("Transfer config updated by admin: {} for lab: {}", adminEmail, labId);

            return buildResponse(200, Map.of(
                    "message", "Transfer configuration updated successfully",
                    "config", config
            ));

        } catch (Exception e) {
            log.error("Error updating transfer config", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Listar laboratórios com configurações
     */
    private APIGatewayV2HTTPResponse listLabsWithConfig(APIGatewayV2HTTPEvent event) {
        try {
            // TODO: Implementar listagem com join de labs + configs
            // Por enquanto, retornar apenas configs
            
            DynamoDbTable<LabConfig> configTable = dynamoDb.table(
                    System.getenv("LAB_CONFIG_TABLE"),
                    TableSchema.fromBean(LabConfig.class)
            );

            var configs = configTable.scan().items().stream().toList();

            return buildResponse(200, Map.of(
                    "items", configs,
                    "count", configs.size()
            ));

        } catch (Exception e) {
            log.error("Error listing labs", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    // ==================== Helpers ====================

    private boolean isAdmin(APIGatewayV2HTTPEvent event) {
        // Verificar se usuário está no grupo "admins" do Cognito
        var claims = event.getRequestContext().getAuthorizer().getJwt().getClaims();
        String groups = claims.get("cognito:groups");
        return groups != null && groups.contains("admins");
    }

    private String getAdminId(APIGatewayV2HTTPEvent event) {
        return event.getRequestContext().getAuthorizer().getJwt().getClaims().get("sub");
    }

    private String getAdminEmail(APIGatewayV2HTTPEvent event) {
        return event.getRequestContext().getAuthorizer().getJwt().getClaims().get("email");
    }

    private Map<String, String> captureOldValues(LabConfig config) {
        return Map.of(
                "commission", String.valueOf(config.getCustomCommissionPercentage()),
                "firstRelease", String.valueOf(config.getCustomFirstReleasePercentage())
        );
    }

    private Map<String, String> captureNewValues(LabConfig config) {
        return Map.of(
                "commission", String.valueOf(config.getCustomCommissionPercentage()),
                "firstRelease", String.valueOf(config.getCustomFirstReleasePercentage())
        );
    }

    private AdminAuditLog.AdminAction determineStatusAction(LabConfig.LabStatus status) {
        return switch (status) {
            case SUSPENDED -> AdminAuditLog.AdminAction.SUSPEND_LAB;
            case BLOCKED -> AdminAuditLog.AdminAction.BLOCK_LAB;
            case ACTIVE -> AdminAuditLog.AdminAction.APPROVE_LAB;
            default -> AdminAuditLog.AdminAction.UPDATE_LAB_STATUS;
        };
    }

    private void logAudit(String adminId, String adminEmail, AdminAuditLog.AdminAction action,
                          String entityType, String entityId, String description,
                          Map<String, String> oldValues, Map<String, String> newValues) {
        try {
            DynamoDbTable<AdminAuditLog> auditTable = dynamoDb.table(
                    System.getenv("AUDIT_LOG_TABLE"),
                    TableSchema.fromBean(AdminAuditLog.class)
            );

            AdminAuditLog log = AdminAuditLog.builder()
                    .logId(java.util.UUID.randomUUID().toString())
                    .timestamp(Instant.now().toString())
                    .adminId(adminId)
                    .adminEmail(adminEmail)
                    .action(action)
                    .actionDescription(description)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValues(oldValues)
                    .newValues(newValues)
                    .result(AdminAuditLog.ActionResult.SUCCESS)
                    .build();

            auditTable.putItem(log);
        } catch (Exception e) {
            log.error("Error logging audit", e);
        }
    }

    private APIGatewayV2HTTPResponse buildResponse(int statusCode, Object body) {
        try {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(statusCode)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(objectMapper.writeValueAsString(body))
                    .build();
        } catch (Exception e) {
            log.error("Error building response", e);
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(500)
                    .withBody("{\"error\":\"Internal server error\"}")
                    .build();
        }
    }

    // ==================== Request DTOs ====================

    @Data
    public static class UpdateLabConfigRequest {
        private Integer customCommissionPercentage;
        private Integer customFirstReleasePercentage;
        private Integer customDisputePeriodDays;
        private Integer customMaxFreeRevisions;
    }

    @Data
    public static class UpdateLabStatusRequest {
        private LabConfig.LabStatus status;
        private String reason;
    }
}
