package com.protheo.payment;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.protheo.model.Payment;
import com.protheo.service.PaymentService;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handler executado periodicamente (a cada 1 hora) para liberar pagamentos
 * após o período de contestação expirar.
 */
@Slf4j
@Named("paymentRelease")
public class PaymentReleaseHandler implements RequestHandler<ScheduledEvent, String> {

    @Inject
    PaymentService paymentService;

    @Inject
    DynamoDbEnhancedClient dynamoDb;

    @Override
    public String handleRequest(ScheduledEvent event, Context context) {
        try {
            log.info("Starting payment release check at {}", Instant.now());

            DynamoDbTable<Payment> paymentsTable = dynamoDb.table(
                    System.getenv("PAYMENTS_TABLE"),
                    TableSchema.fromBean(Payment.class)
            );

            // Buscar pagamentos no status SHIPPED com prazo expirado
            List<Payment> paymentsToRelease = paymentsTable.index("status-deadline-index")
                    .query(r -> r.queryConditional(
                            QueryConditional.sortLessThanOrEqualTo(
                                    k -> k.partitionValue(Payment.PaymentStatus.SHIPPED.name())
                                            .sortValue(Instant.now().toString())
                            )
                    ))
                    .stream()
                    .flatMap(page -> page.items().stream())
                    .filter(Payment::isDisputeDeadlineExpired)
                    .collect(Collectors.toList());

            log.info("Found {} payments ready for final release", paymentsToRelease.size());

            int successCount = 0;
            int failureCount = 0;

            for (Payment payment : paymentsToRelease) {
                try {
                    // Buscar conta Stripe do laboratório (TODO: implementar lookup)
                    String labStripeAccountId = getLabStripeAccountId(payment.getCaseId());

                    paymentService.releaseFinalPayment(payment, labStripeAccountId);
                    successCount++;

                    log.info("Released final payment for case: {}", payment.getCaseId());

                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to release payment for case: {}", payment.getCaseId(), e);
                }
            }

            String result = String.format(
                    "Payment release completed: %d succeeded, %d failed",
                    successCount,
                    failureCount
            );

            log.info(result);
            return result;

        } catch (Exception e) {
            log.error("Error in payment release handler", e);
            throw new RuntimeException("Payment release failed", e);
        }
    }

    /**
     * Busca conta Stripe do laboratório
     * TODO: Implementar lookup real no DynamoDB
     */
    private String getLabStripeAccountId(String caseId) {
        // Placeholder - implementar busca real
        return "acct_lab_placeholder";
    }
}
