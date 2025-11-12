package com.protheo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class Case {

    private String caseId;
    private String dentistId;
    private String labId;
    private CaseStatus status;
    private String title;
    private String description;
    
    @Builder.Default
    private List<String> filesKeys = new ArrayList<>(); // STL e fotos iniciais
    
    private String projectFileKey; // STL final do laboratório
    private String revisionNotes; // Comentários de revisão do dentista
    private Integer revisionCount; // Contador de revisões
    
    private String createdAt; // ISO-8601
    private String updatedAt; // ISO-8601
    private String acceptedAt; // ISO-8601
    private String completedAt; // ISO-8601

    @DynamoDbPartitionKey
    public String getCaseId() {
        return caseId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "dentist-index")
    public String getDentistId() {
        return dentistId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "lab-index")
    public String getLabId() {
        return labId;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "status-index")
    public CaseStatus getStatus() {
        return status;
    }

    @DynamoDbSecondarySortKey(indexNames = {"dentist-index", "lab-index", "status-index"})
    public String getCreatedAt() {
        return createdAt;
    }

    public enum CaseStatus {
        DRAFT,                  // Dentista criando caso
        PENDING,                // Aguardando laboratório aceitar
        AWAITING_BUDGET,        // Lab aceitou, precisa enviar orçamento
        BUDGET_SENT,            // Orçamento enviado, aguardando aprovação dentista
        IN_PROGRESS,            // Orçamento aprovado, lab trabalhando
        AWAITING_APPROVAL,      // Lab enviou projeto 3D, aguardando aprovação dentista
        REVISION_REQUESTED,     // Dentista solicitou alterações
        APPROVED,               // Projeto aprovado, aguardando pagamento
        AWAITING_PAYMENT,       // Aguardando pagamento via Stripe
        PAID,                   // Pagamento confirmado, lab pode enviar produto físico
        SHIPPED,                // Produto despachado
        COMPLETED,              // Caso finalizado, aguardando avaliação
        REVIEWED,               // Dentista avaliou laboratório
        CANCELLED,              // Caso cancelado
        DISPUTED                // Em disputa
    }

    /**
     * Adiciona arquivo ao caso
     */
    public void addFile(String fileKey) {
        if (this.filesKeys == null) {
            this.filesKeys = new ArrayList<>();
        }
        this.filesKeys.add(fileKey);
    }

    /**
     * Incrementa contador de revisões
     */
    public void incrementRevisionCount() {
        if (this.revisionCount == null) {
            this.revisionCount = 0;
        }
        this.revisionCount++;
    }

    /**
     * Verifica se pode solicitar revisão gratuita
     */
    public boolean canRequestFreeRevision(int maxFreeRevisions) {
        return (this.revisionCount == null ? 0 : this.revisionCount) < maxFreeRevisions;
    }
}
