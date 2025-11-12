package com.protheo.admin;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.protheo.model.AdminAuditLog;
import com.protheo.model.Dispute;
import com.protheo.model.Payment;
import com.protheo.service.PaymentService;
import com.stripe.Stripe;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
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
 * Handler administrativo para gestão de disputas
 */
@Slf4j
@Named("adminDispute")
public class AdminDisputeHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Inject
    DynamoDbEnhancedClient dynamoDb;

    @Inject
    PaymentService paymentService;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String path = event.getRawPath();
            String method = event.getRequestContext().getHttp().getMethod();

            // Verificar se é admin
            if (!isAdmin(event)) {
                return buildResponse(403, Map.of("error", "Forbidden: Admin access required"));
            }

            String adminId = getAdminId(event);
            String adminEmail = getAdminEmail(event);

            // Inicializar Stripe
            Stripe.apiKey = System.getenv("STRIPE_SECRET_KEY");

            // Roteamento
            if (path.contains("/disputes") && method.equals("GET")) {
                return listDisputes(event);
            } else if (path.contains("/disputes/") && path.contains("/resolve") && method.equals("POST")) {
                return resolveDispute(event, adminId, adminEmail);
            } else if (path.contains("/disputes/") && path.contains("/refund") && method.equals("POST")) {
                return processRefund(event, adminId, adminEmail);
            }

            return buildResponse(404, Map.of("error", "Endpoint not found"));

        } catch (Exception e) {
            log.error("Error in admin dispute handler", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Listar disputas pendentes
     */
    private APIGatewayV2HTTPResponse listDisputes(APIGatewayV2HTTPEvent event) {
        try {
            String status = event.getQueryStringParameters() != null ?
                    event.getQueryStringParameters().get("status") : null;

            DynamoDbTable<Dispute> disputeTable = dynamoDb.table(
                    System.getenv("DISPUTES_TABLE"),
                    TableSchema.fromBean(Dispute.class)
            );

            var disputes = disputeTable.scan().items().stream()
                    .filter(d -> status == null || d.getStatus().name().equals(status))
                    .toList();

            return buildResponse(200, Map.of(
                    "items", disputes,
                    "count", disputes.size()
            ));

        } catch (Exception e) {
            log.error("Error listing disputes", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Resolver disputa (aprovar/rejeitar)
     */
    private APIGatewayV2HTTPResponse resolveDispute(APIGatewayV2HTTPEvent event,
                                                     String adminId, String adminEmail) {
        try {
            String disputeId = event.getPathParameters().get("disputeId");
            ResolveDisputeRequest request = objectMapper.readValue(
                    event.getBody(),
                    ResolveDisputeRequest.class
            );

            DynamoDbTable<Dispute> disputeTable = dynamoDb.table(
                    System.getenv("DISPUTES_TABLE"),
                    TableSchema.fromBean(Dispute.class)
            );

            Dispute dispute = disputeTable.getItem(r -> r.key(k -> k.partitionValue(disputeId)));
            if (dispute == null) {
                return buildResponse(404, Map.of("error", "Dispute not found"));
            }

            // Atualizar disputa
            dispute.setStatus(Dispute.DisputeStatus.RESOLVED);
            dispute.setResolution(request.getResolution());
            dispute.setResolutionNotes(request.getNotes());
            dispute.setResolvedBy(adminId);
            dispute.setResolvedAt(Instant.now().toString());
            dispute.setUpdatedAt(Instant.now().toString());

            disputeTable.putItem(dispute);

            // Log de auditoria
            logAudit(adminId, adminEmail, AdminAuditLog.AdminAction.RESOLVE_DISPUTE,
                    "DISPUTE", disputeId,
                    String.format("Resolved dispute: %s - %s", request.getResolution(), request.getNotes()),
                    null, null);

            log.info("Dispute resolved by admin: {} - disputeId: {} - resolution: {}",
                    adminEmail, disputeId, request.getResolution());

            return buildResponse(200, Map.of(
                    "message", "Dispute resolved successfully",
                    "dispute", dispute
            ));

        } catch (Exception e) {
            log.error("Error resolving dispute", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Processar reembolso
     */
    private APIGatewayV2HTTPResponse processRefund(APIGatewayV2HTTPEvent event,
                                                    String adminId, String adminEmail) {
        try {
            String disputeId = event.getPathParameters().get("disputeId");
            RefundRequest request = objectMapper.readValue(
                    event.getBody(),
                    RefundRequest.class
            );

            DynamoDbTable<Dispute> disputeTable = dynamoDb.table(
                    System.getenv("DISPUTES_TABLE"),
                    TableSchema.fromBean(Dispute.class)
            );

            Dispute dispute = disputeTable.getItem(r -> r.key(k -> k.partitionValue(disputeId)));
            if (dispute == null) {
                return buildResponse(404, Map.of("error", "Dispute not found"));
            }

            // Buscar pagamento
            Payment payment = paymentService.findById(dispute.getPaymentId());
            if (payment == null) {
                return buildResponse(404, Map.of("error", "Payment not found"));
            }

            // Calcular valor do reembolso
            long refundAmount = calculateRefundAmount(payment, request);

            // Processar reembolso no Stripe
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .setAmount(refundAmount)
                    .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                    .putMetadata("disputeId", disputeId)
                    .putMetadata("adminId", adminId)
                    .build();

            Refund refund = Refund.create(params);

            // Atualizar disputa com informações do reembolso
            Dispute.RefundAction refundAction = Dispute.RefundAction.builder()
                    .type(request.getType())
                    .amount(refundAmount)
                    .percentage(request.getPercentage())
                    .stripeRefundId(refund.getId())
                    .processedAt(Instant.now().toString())
                    .build();

            dispute.setRefundAction(refundAction);
            dispute.setUpdatedAt(Instant.now().toString());
            disputeTable.putItem(dispute);

            // Atualizar pagamento
            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            paymentService.update(payment);

            // Log de auditoria
            logAudit(adminId, adminEmail, AdminAuditLog.AdminAction.PROCESS_REFUND,
                    "PAYMENT", payment.getPaymentId(),
                    String.format("Processed refund: %s - Amount: %d", request.getType(), refundAmount),
                    null, Map.of("refundAmount", String.valueOf(refundAmount)));

            log.info("Refund processed by admin: {} - disputeId: {} - amount: {}",
                    adminEmail, disputeId, refundAmount);

            return buildResponse(200, Map.of(
                    "message", "Refund processed successfully",
                    "refund", refund,
                    "dispute", dispute
            ));

        } catch (Exception e) {
            log.error("Error processing refund", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    // ==================== Helpers ====================

    private long calculateRefundAmount(Payment payment, RefundRequest request) {
        return switch (request.getType()) {
            case FULL_REFUND -> payment.getTotalAmount();
            case PARTIAL_REFUND -> (payment.getTotalAmount() * request.getPercentage()) / 100;
            case NO_REFUND -> 0L;
            default -> throw new IllegalArgumentException("Invalid refund type");
        };
    }

    private boolean isAdmin(APIGatewayV2HTTPEvent event) {
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
    public static class ResolveDisputeRequest {
        private Dispute.DisputeResolution resolution;
        private String notes;
    }

    @Data
    public static class RefundRequest {
        private Dispute.RefundAction.RefundType type;
        private Integer percentage; // Para PARTIAL_REFUND
    }
}
