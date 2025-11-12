package com.protheo.project;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.protheo.model.Case;
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
@Named("projectSubmission")
public class ProjectSubmissionHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Inject
    DynamoDbEnhancedClient dynamoDb;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String caseId = event.getPathParameters().get("caseId");
            SubmitProjectRequest request = objectMapper.readValue(
                    event.getBody(),
                    SubmitProjectRequest.class
            );

            log.info("Project submission for case: {}", caseId);

            DynamoDbTable<Case> casesTable = dynamoDb.table(
                    System.getenv("CASES_TABLE"),
                    TableSchema.fromBean(Case.class)
            );

            Case caseEntity = casesTable.getItem(r -> r.key(k -> k.partitionValue(caseId)));

            if (caseEntity == null) {
                return buildResponse(404, Map.of("error", "Case not found"));
            }

            // Validar status (deve estar IN_PROGRESS ou REVISION_REQUESTED)
            if (caseEntity.getStatus() != Case.CaseStatus.IN_PROGRESS &&
                caseEntity.getStatus() != Case.CaseStatus.REVISION_REQUESTED) {
                return buildResponse(400, Map.of(
                        "error", "Case not in valid status for project submission",
                        "currentStatus", caseEntity.getStatus()
                ));
            }

            // Atualizar caso com arquivo do projeto
            caseEntity.setProjectFileKey(request.getProjectFileKey());
            caseEntity.setStatus(Case.CaseStatus.AWAITING_APPROVAL);
            caseEntity.setUpdatedAt(Instant.now().toString());

            // Limpar notas de revisão se existirem
            if (caseEntity.getStatus() == Case.CaseStatus.REVISION_REQUESTED) {
                caseEntity.setRevisionNotes(null);
            }

            casesTable.putItem(caseEntity);

            log.info("Project submitted successfully for case: {}", caseId);

            return buildResponse(200, Map.of(
                    "message", "Project submitted successfully",
                    "case", caseEntity
            ));

        } catch (Exception e) {
            log.error("Error submitting project", e);
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
    public static class SubmitProjectRequest {
        private String projectFileKey; // S3 key do arquivo STL final
    }
}
