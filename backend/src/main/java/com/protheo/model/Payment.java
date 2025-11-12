package com.protheo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Payment {

    private String paymentId;
    private String caseId;
    private String stripePaymentIntentId;
    private Long totalAmount; // em centavos
    private Long commission; // em centavos
    private Long labAmount; // em centavos (total - commission)
    private PaymentStatus status;
    
    @Builder.Default
    private List<Transfer> transfers = new ArrayList<>();
    
    private Long holdAmount; // valor ainda retido
    private String disputeDeadline; // ISO-8601
    private String shippedAt; // ISO-8601
    private String trackingCode;
    private String createdAt; // ISO-8601
    private String updatedAt; // ISO-8601

    @DynamoDbPartitionKey
    public String getPaymentId() {
        return paymentId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "case-index")
    public String getCaseId() {
        return caseId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-deadline-index")
    public PaymentStatus getStatus() {
        return status;
    }

    @DynamoDbSecondarySortKey(indexNames = "status-deadline-index")
    public String getDisputeDeadline() {
        return disputeDeadline;
    }

    public enum PaymentStatus {
        PENDING,           // Aguardando pagamento
        HELD,              // Pago, retido na Protheo
        PARTIAL_RELEASED,  // 70% liberado após aprovação digital
        SHIPPED,           // Produto despachado, aguardando prazo
        COMPLETED,         // Prazo expirou, pagamento completo ao lab
        DISPUTED,          // Dentista contestou
        REFUNDED           // Reembolso processado
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transfer {
        private String transferId; // Stripe Transfer ID
        private Long amount; // em centavos
        private Integer percentage;
        private String releasedAt; // ISO-8601
        private TransferReason reason;
    }

    public enum TransferReason {
        DIGITAL_APPROVAL,  // Aprovação do projeto digital
        FINAL_RELEASE      // Liberação final após período de contestação
    }

    /**
     * Adiciona uma transferência ao histórico
     */
    public void addTransfer(Transfer transfer) {
        if (this.transfers == null) {
            this.transfers = new ArrayList<>();
        }
        this.transfers.add(transfer);
        this.updatedAt = Instant.now().toString();
    }

    /**
     * Calcula o total já transferido para o laboratório
     */
    public long getTotalTransferred() {
        if (transfers == null) {
            return 0L;
        }
        return transfers.stream()
                .mapToLong(Transfer::getAmount)
                .sum();
    }

    /**
     * Verifica se há saldo pendente para liberar
     */
    public boolean hasPendingBalance() {
        return holdAmount != null && holdAmount > 0;
    }

    /**
     * Verifica se o prazo de contestação expirou
     */
    public boolean isDisputeDeadlineExpired() {
        if (disputeDeadline == null) {
            return false;
        }
        return Instant.parse(disputeDeadline).isBefore(Instant.now());
    }
}
