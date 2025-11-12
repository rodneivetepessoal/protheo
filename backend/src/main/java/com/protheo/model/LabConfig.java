package com.protheo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Configurações customizadas por laboratório (definidas pelo admin)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class LabConfig {

    private String labId;
    
    // Comissão customizada (sobrescreve global)
    private Integer customCommissionPercentage; // null = usa global
    
    // Liberação customizada (sobrescreve global)
    private Integer customFirstReleasePercentage; // null = usa global
    
    // Prazo de contestação customizado
    private Integer customDisputePeriodDays; // null = usa global
    
    // Revisões gratuitas customizadas
    private Integer customMaxFreeRevisions; // null = usa global
    
    // Status do laboratório
    private LabStatus status;
    
    // Motivo de bloqueio/suspensão
    private String statusReason;
    
    // Configurações de repasse
    private TransferConfig transferConfig;
    
    // Metadados
    private String createdAt;
    private String updatedAt;
    private String updatedBy; // Admin user ID

    @DynamoDbPartitionKey
    public String getLabId() {
        return labId;
    }

    public enum LabStatus {
        ACTIVE,          // Ativo e operando normalmente
        PENDING_REVIEW,  // Aguardando revisão do admin
        SUSPENDED,       // Suspenso temporariamente
        BLOCKED,         // Bloqueado permanentemente
        INACTIVE         // Inativo (desativado pelo próprio lab)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferConfig {
        // Stripe Connect Account ID
        private String stripeAccountId;
        
        // Tipo de repasse
        private TransferType transferType;
        
        // Frequência de repasse (para SCHEDULED)
        private TransferFrequency transferFrequency;
        
        // Dia do mês para repasse (1-31, para MONTHLY)
        private Integer transferDayOfMonth;
        
        // Dia da semana para repasse (1-7, para WEEKLY)
        private Integer transferDayOfWeek;
        
        // Valor mínimo para repasse automático (em centavos)
        private Long minimumTransferAmount;
        
        // Retenção de segurança (dias após liberação final)
        private Integer securityHoldDays; // Padrão: 0
        
        // Status do onboarding Stripe Connect
        private StripeOnboardingStatus onboardingStatus;
    }

    public enum TransferType {
        IMMEDIATE,    // Repasse imediato após cada liberação
        SCHEDULED,    // Repasse agendado (semanal/mensal)
        MANUAL        // Repasse manual pelo admin
    }

    public enum TransferFrequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY
    }

    public enum StripeOnboardingStatus {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    /**
     * Verifica se tem comissão customizada
     */
    public boolean hasCustomCommission() {
        return customCommissionPercentage != null;
    }

    /**
     * Verifica se tem primeira liberação customizada
     */
    public boolean hasCustomFirstRelease() {
        return customFirstReleasePercentage != null;
    }

    /**
     * Verifica se laboratório está ativo
     */
    public boolean isActive() {
        return status == LabStatus.ACTIVE;
    }

    /**
     * Verifica se pode receber novos casos
     */
    public boolean canReceiveCases() {
        return status == LabStatus.ACTIVE;
    }

    /**
     * Verifica se pode receber repasses
     */
    public boolean canReceiveTransfers() {
        return status == LabStatus.ACTIVE && 
               transferConfig != null && 
               transferConfig.getOnboardingStatus() == StripeOnboardingStatus.COMPLETED;
    }
}
