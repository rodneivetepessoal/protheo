package com.protheo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.util.List;

/**
 * Contestação/Disputa de caso
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Dispute {

    private String disputeId;
    private String caseId;
    private String paymentId;
    private String dentistId;
    private String labId;
    
    // Motivo da contestação
    private DisputeReason reason;
    private String reasonDescription;
    
    // Evidências
    private List<String> evidenceFileKeys; // Fotos, documentos
    
    // Status
    private DisputeStatus status;
    
    // Decisão do admin
    private DisputeResolution resolution;
    private String resolutionNotes;
    private String resolvedBy; // Admin user ID
    private String resolvedAt;
    
    // Ações financeiras
    private RefundAction refundAction;
    
    // Timeline
    private String createdAt;
    private String updatedAt;
    
    // Comunicação
    private List<DisputeMessage> messages;

    @DynamoDbPartitionKey
    public String getDisputeId() {
        return disputeId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "case-index")
    public String getCaseId() {
        return caseId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    public DisputeStatus getStatus() {
        return status;
    }

    public enum DisputeReason {
        QUALITY_ISSUE,           // Problema de qualidade
        WRONG_SPECIFICATIONS,    // Especificações incorretas
        DAMAGED_IN_SHIPPING,     // Danificado no transporte
        LATE_DELIVERY,           // Entrega atrasada
        WRONG_PRODUCT,           // Produto errado
        NOT_AS_DESCRIBED,        // Não conforme descrição
        OTHER                    // Outro motivo
    }

    public enum DisputeStatus {
        PENDING,                 // Aguardando análise do admin
        UNDER_REVIEW,            // Em análise pelo admin
        AWAITING_LAB_RESPONSE,   // Aguardando resposta do lab
        AWAITING_DENTIST_INFO,   // Aguardando informações do dentista
        RESOLVED,                // Resolvida
        CLOSED                   // Fechada
    }

    public enum DisputeResolution {
        FAVOR_DENTIST,           // A favor do dentista
        FAVOR_LAB,               // A favor do laboratório
        PARTIAL_REFUND,          // Reembolso parcial
        REMAKE_REQUIRED,         // Refazer produto
        MEDIATION_FAILED,        // Mediação falhou
        WITHDRAWN                // Contestação retirada
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RefundAction {
        private RefundType type;
        private Long amount; // Em centavos
        private Integer percentage; // Para PARTIAL
        private String stripeRefundId;
        private String processedAt;
        
        public enum RefundType {
            FULL_REFUND,         // Reembolso total ao dentista
            PARTIAL_REFUND,      // Reembolso parcial ao dentista
            NO_REFUND,           // Sem reembolso
            CHARGEBACK           // Chargeback (caso extremo)
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisputeMessage {
        private String messageId;
        private String senderId;
        private String senderRole; // DENTIST, LAB, ADMIN
        private String message;
        private List<String> attachmentKeys;
        private String sentAt;
    }

    /**
     * Verifica se disputa está pendente de análise
     */
    public boolean isPending() {
        return status == DisputeStatus.PENDING || status == DisputeStatus.UNDER_REVIEW;
    }

    /**
     * Verifica se disputa foi resolvida
     */
    public boolean isResolved() {
        return status == DisputeStatus.RESOLVED || status == DisputeStatus.CLOSED;
    }

    /**
     * Adiciona mensagem à disputa
     */
    public void addMessage(DisputeMessage message) {
        if (this.messages == null) {
            this.messages = new java.util.ArrayList<>();
        }
        this.messages.add(message);
    }
}
