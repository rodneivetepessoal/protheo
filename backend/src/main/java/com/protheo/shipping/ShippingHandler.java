package com.protheo.shipping;

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
@Named("shipping")
public class ShippingHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

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
            String caseId = event.getPathParameters().get("caseId");
            ShippingRequest request = objectMapper.readValue(
                    event.getBody(),
                    ShippingRequest.class
            );

            log.info("Shipping confirmation for case: {}", caseId);

            DynamoDbTable<Case> casesTable = dynamoDb.table(
                    System.getenv("CASES_TABLE"),
                    TableSchema.fromBean(Case.class)
            );

            Case caseEntity = casesTable.getItem(r -> r.key(k -> k.partitionValue(caseId)));

            if (caseEntity == null) {
                return buildResponse(404, Map.of("error", "Case not found"));
            }

            if (caseEntity.getStatus() != Case.CaseStatus.PAID) {
                return buildResponse(400, Map.of(
                        "error", "Case not in PAID status",
                        "currentStatus", caseEntity.getStatus()
                ));
            }

            // Atualizar status do caso
            caseEntity.setStatus(Case.CaseStatus.SHIPPED);
            caseEntity.setUpdatedAt(Instant.now().toString());
            casesTable.putItem(caseEntity);

            // Atualizar pagamento com informações de envio
            Payment payment = paymentService.findByCaseId(caseId);
            if (payment != null) {
                payment = paymentService.markAsShipped(payment, request.getTrackingCode());
            }

            log.info("Shipping confirmed for case: {}, tracking: {}, dispute deadline: {}",
                    caseId, request.getTrackingCode(), payment != null ? payment.getDisputeDeadline() : "N/A");

            return buildResponse(200, Map.of(
                    "message", "Shipping confirmed successfully",
                    "case", caseEntity,
                    "payment", payment,
                    "disputePeriodDays", paymentConfig.getDisputePeriodDays(),
                    "disputeDeadline", payment != null ? payment.getDisputeDeadline() : null
            ));

        } catch (Exception e) {
            log.error("Error confirming shipping", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
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

    @Data
    public static class ShippingRequest {
        private String trackingCode;
        private String carrier; // Correios, Sedex, etc.
    }
}
