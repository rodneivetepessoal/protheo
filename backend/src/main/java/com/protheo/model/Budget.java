package com.protheo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Budget {

    private String budgetId;
    private String caseId;
    private String labId;
    private Long amount; // em centavos
    private String currency; // BRL
    
    @Builder.Default
    private List<BudgetItem> breakdown = new ArrayList<>();
    
    private String validUntil; // ISO-8601
    private BudgetStatus status;
    private String createdAt; // ISO-8601
    private String approvedAt; // ISO-8601
    private String rejectedAt; // ISO-8601
    private String rejectionReason;

    @DynamoDbPartitionKey
    public String getBudgetId() {
        return budgetId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "case-index")
    public String getCaseId() {
        return caseId;
    }

    public enum BudgetStatus {
        PENDING,    // Aguardando aprovação do dentista
        APPROVED,   // Aprovado pelo dentista
        REJECTED,   // Rejeitado pelo dentista
        EXPIRED     // Prazo de validade expirou
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetItem {
        private String description;
        private Long amount; // em centavos
    }

    /**
     * Verifica se o orçamento expirou
     */
    public boolean isExpired() {
        if (validUntil == null) {
            return false;
        }
        return Instant.parse(validUntil).isBefore(Instant.now());
    }

    /**
     * Calcula data de validade baseada em dias configurados
     */
    public static String calculateValidUntil(int validityDays) {
        return Instant.now()
                .plus(validityDays, ChronoUnit.DAYS)
                .toString();
    }

    /**
     * Adiciona item ao breakdown
     */
    public void addItem(String description, Long amount) {
        if (this.breakdown == null) {
            this.breakdown = new ArrayList<>();
        }
        this.breakdown.add(BudgetItem.builder()
                .description(description)
                .amount(amount)
                .build());
    }

    /**
     * Calcula total do breakdown
     */
    public long calculateTotal() {
        if (breakdown == null) {
            return 0L;
        }
        return breakdown.stream()
                .mapToLong(BudgetItem::getAmount)
                .sum();
    }
}
