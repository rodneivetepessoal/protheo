package com.protheo.service;

import com.protheo.config.PaymentConfig;
import com.protheo.model.Budget;
import com.protheo.model.Case;
import com.protheo.model.Payment;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.TransferCreateParams;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@ApplicationScoped
public class PaymentService {

    @Inject
    DynamoDbEnhancedClient dynamoDb;

    @Inject
    SecretsManagerClient secretsManager;

    @Inject
    PaymentConfig paymentConfig;

    @Inject
    ObjectMapper objectMapper;

    private DynamoDbTable<Payment> paymentsTable;
    private String stripeSecretKey;

    public void init() {
        if (paymentsTable == null) {
            String tableName = System.getenv("PAYMENTS_TABLE");
            paymentsTable = dynamoDb.table(tableName, TableSchema.fromBean(Payment.class));
        }
        
        if (stripeSecretKey == null) {
            loadStripeSecretKey();
        }
    }

    /**
     * Carrega chave secreta do Stripe do Secrets Manager
     */
    private void loadStripeSecretKey() {
        try {
            String secretArn = System.getenv("STRIPE_SECRET_ARN");
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretArn)
                    .build();
            
            String secretString = secretsManager.getSecretValue(request).secretString();
            Map<String, String> secrets = objectMapper.readValue(secretString, Map.class);
            
            stripeSecretKey = secrets.get("secret_key");
            Stripe.apiKey = stripeSecretKey;
            
            log.info("Stripe API key loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load Stripe secret key", e);
            throw new RuntimeException("Failed to initialize Stripe", e);
        }
    }

    /**
     * Cria pagamento inicial (captura imediata para Protheo)
     */
    public Payment createPayment(Case caseEntity, Budget budget, String labStripeAccountId) throws StripeException {
        init();

        long totalAmount = budget.getAmount();
        long commission = paymentConfig.calculateCommission(totalAmount);
        long labAmount = paymentConfig.calculateLabAmount(totalAmount);

        // Criar Payment Intent no Stripe (captura imediata)
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(totalAmount)
                .setCurrency(budget.getCurrency().toLowerCase())
                .putMetadata("caseId", caseEntity.getCaseId())
                .putMetadata("budgetId", budget.getBudgetId())
                .putMetadata("labId", caseEntity.getLabId())
                .setDescription("Protheo - Caso " + caseEntity.getCaseId())
                .build();

        PaymentIntent intent = PaymentIntent.create(params);

        // Criar registro no DynamoDB
        Payment payment = Payment.builder()
                .paymentId(UUID.randomUUID().toString())
                .caseId(caseEntity.getCaseId())
                .stripePaymentIntentId(intent.getId())
                .totalAmount(totalAmount)
                .commission(commission)
                .labAmount(labAmount)
                .holdAmount(labAmount) // Todo valor retido inicialmente
                .status(Payment.PaymentStatus.PENDING)
                .createdAt(Instant.now().toString())
                .updatedAt(Instant.now().toString())
                .build();

        paymentsTable.putItem(payment);

        log.info("Payment created: {} for case: {}", payment.getPaymentId(), caseEntity.getCaseId());
        return payment;
    }

    /**
     * Confirma pagamento após sucesso no Stripe (webhook)
     */
    public Payment confirmPayment(String paymentIntentId) {
        init();

        Payment payment = findByStripePaymentIntentId(paymentIntentId);
        if (payment == null) {
            throw new IllegalArgumentException("Payment not found for intent: " + paymentIntentId);
        }

        payment.setStatus(Payment.PaymentStatus.HELD);
        payment.setUpdatedAt(Instant.now().toString());

        paymentsTable.putItem(payment);

        log.info("Payment confirmed: {}", payment.getPaymentId());
        return payment;
    }

    /**
     * Libera primeira parcela (70%) após aprovação do projeto digital
     */
    public Payment releaseFirstPayment(Payment payment, String labStripeAccountId) throws StripeException {
        init();

        if (payment.getStatus() != Payment.PaymentStatus.HELD) {
            throw new IllegalStateException("Payment not in HELD status");
        }

        long firstReleaseAmount = paymentConfig.calculateFirstRelease(payment.getLabAmount());

        // Criar transferência no Stripe
        TransferCreateParams params = TransferCreateParams.builder()
                .setAmount(firstReleaseAmount)
                .setCurrency("brl")
                .setDestination(labStripeAccountId)
                .putMetadata("paymentId", payment.getPaymentId())
                .putMetadata("caseId", payment.getCaseId())
                .putMetadata("reason", "digital_approval")
                .setDescription("Protheo - Primeira liberação (70%)")
                .build();

        Transfer transfer = Transfer.create(params);

        // Registrar transferência
        Payment.Transfer paymentTransfer = Payment.Transfer.builder()
                .transferId(transfer.getId())
                .amount(firstReleaseAmount)
                .percentage(paymentConfig.getFirstReleasePercentage())
                .releasedAt(Instant.now().toString())
                .reason(Payment.TransferReason.DIGITAL_APPROVAL)
                .build();

        payment.addTransfer(paymentTransfer);
        payment.setHoldAmount(payment.getLabAmount() - firstReleaseAmount);
        payment.setStatus(Payment.PaymentStatus.PARTIAL_RELEASED);

        paymentsTable.putItem(payment);

        log.info("First payment released: {} to lab account: {}", firstReleaseAmount, labStripeAccountId);
        return payment;
    }

    /**
     * Atualiza status para SHIPPED e define prazo de contestação
     */
    public Payment markAsShipped(Payment payment, String trackingCode) {
        init();

        if (payment.getStatus() != Payment.PaymentStatus.PARTIAL_RELEASED) {
            throw new IllegalStateException("Payment not in PARTIAL_RELEASED status");
        }

        String disputeDeadline = Instant.now()
                .plus(paymentConfig.getDisputePeriodDays(), ChronoUnit.DAYS)
                .toString();

        payment.setStatus(Payment.PaymentStatus.SHIPPED);
        payment.setShippedAt(Instant.now().toString());
        payment.setTrackingCode(trackingCode);
        payment.setDisputeDeadline(disputeDeadline);
        payment.setUpdatedAt(Instant.now().toString());

        paymentsTable.putItem(payment);

        log.info("Payment marked as shipped: {} with deadline: {}", payment.getPaymentId(), disputeDeadline);
        return payment;
    }

    /**
     * Libera segunda parcela (30%) após período de contestação
     */
    public Payment releaseFinalPayment(Payment payment, String labStripeAccountId) throws StripeException {
        init();

        if (payment.getStatus() != Payment.PaymentStatus.SHIPPED) {
            throw new IllegalStateException("Payment not in SHIPPED status");
        }

        if (!payment.isDisputeDeadlineExpired()) {
            throw new IllegalStateException("Dispute period not expired yet");
        }

        long secondReleaseAmount = paymentConfig.calculateSecondRelease(payment.getLabAmount());

        // Criar transferência no Stripe
        TransferCreateParams params = TransferCreateParams.builder()
                .setAmount(secondReleaseAmount)
                .setCurrency("brl")
                .setDestination(labStripeAccountId)
                .putMetadata("paymentId", payment.getPaymentId())
                .putMetadata("caseId", payment.getCaseId())
                .putMetadata("reason", "final_release")
                .setDescription("Protheo - Liberação final (30%)")
                .build();

        Transfer transfer = Transfer.create(params);

        // Registrar transferência
        Payment.Transfer paymentTransfer = Payment.Transfer.builder()
                .transferId(transfer.getId())
                .amount(secondReleaseAmount)
                .percentage(paymentConfig.getSecondReleasePercentage())
                .releasedAt(Instant.now().toString())
                .reason(Payment.TransferReason.FINAL_RELEASE)
                .build();

        payment.addTransfer(paymentTransfer);
        payment.setHoldAmount(0L);
        payment.setStatus(Payment.PaymentStatus.COMPLETED);
        payment.setUpdatedAt(Instant.now().toString());

        paymentsTable.putItem(payment);

        log.info("Final payment released: {} to lab account: {}", secondReleaseAmount, labStripeAccountId);
        return payment;
    }

    /**
     * Busca pagamento por Payment Intent ID do Stripe
     */
    private Payment findByStripePaymentIntentId(String paymentIntentId) {
        // Implementar scan ou GSI se necessário
        // Por simplicidade, assumindo que temos o paymentId
        return null; // TODO: implementar busca
    }

    /**
     * Busca pagamento por ID do caso
     */
    public Payment findByCaseId(String caseId) {
        init();
        
        // Usar GSI case-index
        return paymentsTable.index("case-index")
                .query(r -> r.queryConditional(
                        software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional
                                .keyEqualTo(k -> k.partitionValue(caseId))
                ))
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst()
                .orElse(null);
    }

    /**
     * Busca pagamento por ID
     */
    public Payment findById(String paymentId) {
        init();
        return paymentsTable.getItem(r -> r.key(k -> k.partitionValue(paymentId)));
    }

    /**
     * Atualiza pagamento
     */
    public Payment update(Payment payment) {
        init();
        payment.setUpdatedAt(Instant.now().toString());
        paymentsTable.putItem(payment);
        return payment;
    }
}
