package com.protheo.payment;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.protheo.model.Budget;
import com.protheo.model.Case;
import com.protheo.model.Payment;
import com.protheo.service.PaymentService;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Named("payment")
public class PaymentHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Inject
    PaymentService paymentService;

    @Inject
    DynamoDbEnhancedClient dynamoDb;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String path = event.getRawPath();
            String method = event.getRequestContext().getHttp().getMethod();

            log.info("Payment handler - Path: {}, Method: {}", path, method);

            if (path.equals("/payments") && method.equals("POST")) {
                return createPayment(event);
            } else if (path.startsWith("/payments/") && method.equals("GET")) {
                return getPayment(event);
            } else if (path.equals("/webhooks/stripe") && method.equals("POST")) {
                return handleStripeWebhook(event);
            }

            return buildResponse(404, Map.of("error", "Not found"));

        } catch (Exception e) {
            log.error("Error processing payment request", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /payments - Cria pagamento inicial
     */
    private APIGatewayV2HTTPResponse createPayment(APIGatewayV2HTTPEvent event) {
        try {
            CreatePaymentRequest request = objectMapper.readValue(
                    event.getBody(),
                    CreatePaymentRequest.class
            );

            // Buscar caso e orçamento
            DynamoDbTable<Case> casesTable = dynamoDb.table(
                    System.getenv("CASES_TABLE"),
                    TableSchema.fromBean(Case.class)
            );
            Case caseEntity = casesTable.getItem(r -> r.key(k -> k.partitionValue(request.getCaseId())));

            DynamoDbTable<Budget> budgetsTable = dynamoDb.table(
                    System.getenv("BUDGETS_TABLE"),
                    TableSchema.fromBean(Budget.class)
            );
            Budget budget = budgetsTable.getItem(r -> r.key(k -> k.partitionValue(request.getBudgetId())));

            if (caseEntity == null || budget == null) {
                return buildResponse(404, Map.of("error", "Case or budget not found"));
            }

            // Criar pagamento
            Payment payment = paymentService.createPayment(
                    caseEntity,
                    budget,
                    request.getLabStripeAccountId()
            );

            // Atualizar status do caso
            caseEntity.setStatus(Case.CaseStatus.AWAITING_PAYMENT);
            casesTable.putItem(caseEntity);

            return buildResponse(201, payment);

        } catch (Exception e) {
            log.error("Error creating payment", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /payments/{paymentId} - Busca pagamento
     */
    private APIGatewayV2HTTPResponse getPayment(APIGatewayV2HTTPEvent event) {
        try {
            String paymentId = event.getPathParameters().get("paymentId");

            DynamoDbTable<Payment> paymentsTable = dynamoDb.table(
                    System.getenv("PAYMENTS_TABLE"),
                    TableSchema.fromBean(Payment.class)
            );
            Payment payment = paymentsTable.getItem(r -> r.key(k -> k.partitionValue(paymentId)));

            if (payment == null) {
                return buildResponse(404, Map.of("error", "Payment not found"));
            }

            return buildResponse(200, payment);

        } catch (Exception e) {
            log.error("Error getting payment", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /webhooks/stripe - Webhook do Stripe
     */
    private APIGatewayV2HTTPResponse handleStripeWebhook(APIGatewayV2HTTPEvent event) {
        try {
            String payload = event.getBody();
            String sigHeader = event.getHeaders().get("stripe-signature");

            // Verificar assinatura do webhook (TODO: carregar webhook secret)
            // Event stripeEvent = Webhook.constructEvent(payload, sigHeader, webhookSecret);

            // Por simplicidade, parsear diretamente
            Event stripeEvent = objectMapper.readValue(payload, Event.class);

            log.info("Stripe webhook received: {}", stripeEvent.getType());

            if ("payment_intent.succeeded".equals(stripeEvent.getType())) {
                PaymentIntent intent = (PaymentIntent) stripeEvent.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow();

                paymentService.confirmPayment(intent.getId());

                // Atualizar status do caso para PAID
                String caseId = intent.getMetadata().get("caseId");
                if (caseId != null) {
                    DynamoDbTable<Case> casesTable = dynamoDb.table(
                            System.getenv("CASES_TABLE"),
                            TableSchema.fromBean(Case.class)
                    );
                    Case caseEntity = casesTable.getItem(r -> r.key(k -> k.partitionValue(caseId)));
                    if (caseEntity != null) {
                        caseEntity.setStatus(Case.CaseStatus.PAID);
                        casesTable.putItem(caseEntity);
                    }
                }
            }

            return buildResponse(200, Map.of("received", true));

        } catch (Exception e) {
            log.error("Error processing Stripe webhook", e);
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
    public static class CreatePaymentRequest {
        private String caseId;
        private String budgetId;
        private String labStripeAccountId;
    }
}
