package com.protheo.project;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.protheo.config.PaymentConfig;
import com.protheo.model.Case;
import com.protheo.model.Payment;
import com.protheo.service.PaymentService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Named("projectApproval")
public class ProjectApprovalHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Inject
    DynamoDbEnhancedClient dynamoDb;

    @Inject
    PaymentService paymentService;

    @Inject
    PaymentConfig paymentConfig;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String path = event.getRawPath();
            String caseId = event.getPathParameters().get("caseId");

            log.info("Project approval handler - Path: {}, CaseId: {}", path, caseId);

            if (path.contains("/approve-project")) {
                return approveProject(caseId, event);
            } else if (path.contains("/request-revision")) {
                return requestRevision(caseId, event);
            }

            return buildResponse(404, Map.of("error", "Not found"));

        } catch (Exception e) {
            log.error("Error processing project approval request", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /cases/{caseId}/approve-project - Dentista aprova projeto digital
     * Libera primeira parcela (70%) para o laboratório
     */
    private APIGatewayV2HTTPResponse approveProject(String caseId, APIGatewayV2HTTPEvent event) {
        try {
            DynamoDbTable<Case> casesTable = dynamoDb.table(
                    System.getenv("CASES_TABLE"),
                    TableSchema.fromBean(Case.class)
            );

            Case caseEntity = casesTable.getItem(r -> r.key(k -> k.partitionValue(caseId)));

            if (caseEntity == null) {
                return buildResponse(404, Map.of("error", "Case not found"));
            }

            if (caseEntity.getStatus() != Case.CaseStatus.AWAITING_APPROVAL) {
                return buildResponse(400, Map.of(
                        "error", "Case not in AWAITING_APPROVAL status",
                        "currentStatus", caseEntity.getStatus()
                ));
            }

            // Atualizar status do caso
            caseEntity.setStatus(Case.CaseStatus.APPROVED);
            caseEntity.setUpdatedAt(Instant.now().toString());
            casesTable.putItem(caseEntity);

            // Buscar pagamento
            Payment payment = paymentService.findByCaseId(caseId);
            if (payment == null) {
                return buildResponse(404, Map.of("error", "Payment not found for case"));
            }

            // Liberar primeira parcela (70%)
            String labStripeAccountId = getLabStripeAccountId(caseEntity.getLabId());
            payment = paymentService.releaseFirstPayment(payment, labStripeAccountId);

            log.info("Project approved for case: {}, released {}% to lab",
                    caseId, paymentConfig.getFirstReleasePercentage());

            return buildResponse(200, Map.of(
                    "message", "Project approved successfully",
                    "case", caseEntity,
                    "payment", payment,
                    "releasedPercentage", paymentConfig.getFirstReleasePercentage(),
                    "releasedAmount", payment.getTransfers().get(0).getAmount()
            ));

        } catch (Exception e) {
            log.error("Error approving project", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /cases/{caseId}/request-revision - Dentista solicita revisão
     */
    private APIGatewayV2HTTPResponse requestRevision(String caseId, APIGatewayV2HTTPEvent event) {
        try {
            RevisionRequest request = objectMapper.readValue(
                    event.getBody(),
                    RevisionRequest.class
            );

            DynamoDbTable<Case> casesTable = dynamoDb.table(
                    System.getenv("CASES_TABLE"),
                    TableSchema.fromBean(Case.class)
            );

            Case caseEntity = casesTable.getItem(r -> r.key(k -> k.partitionValue(caseId)));

            if (caseEntity == null) {
                return buildResponse(404, Map.of("error", "Case not found"));
            }

            if (caseEntity.getStatus() != Case.CaseStatus.AWAITING_APPROVAL) {
                return buildResponse(400, Map.of(
                        "error", "Case not in AWAITING_APPROVAL status",
                        "currentStatus", caseEntity.getStatus()
                ));
            }

            // Verificar se ainda tem revisões gratuitas
            boolean isFreeRevision = caseEntity.canRequestFreeRevision(
                    paymentConfig.getMaxFreeRevisions()
            );

            // Incrementar contador de revisões
            caseEntity.incrementRevisionCount();

            // Atualizar status e notas
            caseEntity.setStatus(Case.CaseStatus.REVISION_REQUESTED);
            caseEntity.setRevisionNotes(request.getNotes());
            caseEntity.setUpdatedAt(Instant.now().toString());
            casesTable.putItem(caseEntity);

            log.info("Revision requested for case: {}, count: {}, free: {}",
                    caseId, caseEntity.getRevisionCount(), isFreeRevision);

            return buildResponse(200, Map.of(
                    "message", "Revision requested successfully",
                    "case", caseEntity,
                    "revisionCount", caseEntity.getRevisionCount(),
                    "isFreeRevision", isFreeRevision,
                    "maxFreeRevisions", paymentConfig.getMaxFreeRevisions()
            ));

        } catch (Exception e) {
            log.error("Error requesting revision", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Busca conta Stripe do laboratório
     * TODO: Implementar lookup real no DynamoDB
     */
    private String getLabStripeAccountId(String labId) {
        // Placeholder - implementar busca real
        return "acct_lab_placeholder";
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

    @Data
    public static class RevisionRequest {
        private String notes;
    }
}
