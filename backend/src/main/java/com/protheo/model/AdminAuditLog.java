package com.protheo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Map;

/**
 * Log de auditoria de ações administrativas
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class AdminAuditLog {

    private String logId;
    private String timestamp; // ISO 8601
    
    // Quem fez a ação
    private String adminId;
    private String adminEmail;
    
    // O que foi feito
    private AdminAction action;
    private String actionDescription;
    
    // Em qual entidade
    private String entityType; // LAB, CASE, PAYMENT, DISPUTE, USER
    private String entityId;
    
    // Detalhes da mudança
    private Map<String, String> oldValues;
    private Map<String, String> newValues;
    
    // Contexto
    private String ipAddress;
    private String userAgent;
    
    // Resultado
    private ActionResult result;
    private String errorMessage;

    @DynamoDbPartitionKey
    public String getLogId() {
        return logId;
    }

    @DynamoDbSortKey
    public String getTimestamp() {
        return timestamp;
    }

    public enum AdminAction {
        // Laboratórios
        UPDATE_LAB_COMMISSION,
        UPDATE_LAB_STATUS,
        UPDATE_LAB_TRANSFER_CONFIG,
        APPROVE_LAB,
        SUSPEND_LAB,
        BLOCK_LAB,
        
        // Disputas
        REVIEW_DISPUTE,
        RESOLVE_DISPUTE,
        PROCESS_REFUND,
        
        // Pagamentos
        MANUAL_TRANSFER,
        CANCEL_TRANSFER,
        ADJUST_PAYMENT,
        
        // Casos
        CANCEL_CASE,
        FORCE_COMPLETE_CASE,
        
        // Usuários
        SUSPEND_USER,
        DELETE_USER,
        
        // Configurações globais
        UPDATE_GLOBAL_CONFIG,
        
        // Outros
        MANUAL_INTERVENTION,
        SYSTEM_OVERRIDE
    }

    public enum ActionResult {
        SUCCESS,
        FAILED,
        PARTIAL
    }

    /**
     * Cria log de sucesso
     */
    public static AdminAuditLog success(String adminId, String adminEmail, AdminAction action, 
                                        String entityType, String entityId, String description) {
        return AdminAuditLog.builder()
                .logId(java.util.UUID.randomUUID().toString())
                .timestamp(java.time.Instant.now().toString())
                .adminId(adminId)
                .adminEmail(adminEmail)
                .action(action)
                .actionDescription(description)
                .entityType(entityType)
                .entityId(entityId)
                .result(ActionResult.SUCCESS)
                .build();
    }

    /**
     * Cria log de erro
     */
    public static AdminAuditLog error(String adminId, String adminEmail, AdminAction action, 
                                      String entityType, String entityId, String errorMessage) {
        return AdminAuditLog.builder()
                .logId(java.util.UUID.randomUUID().toString())
                .timestamp(java.time.Instant.now().toString())
                .adminId(adminId)
                .adminEmail(adminEmail)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .result(ActionResult.FAILED)
                .errorMessage(errorMessage)
                .build();
    }
}
