package com.protheo.budget;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.protheo.config.PaymentConfig;
import com.protheo.model.Budget;
import com.protheo.model.Case;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Named("budget")
public class BudgetHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Inject
    DynamoDbEnhancedClient dynamoDb;

    @Inject
    PaymentConfig paymentConfig;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String path = event.getRawPath();
            String method = event.getRequestContext().getHttp().getMethod();

            log.info("Budget handler - Path: {}, Method: {}", path, method);

            if (path.equals("/budgets") && method.equals("POST")) {
                return createBudget(event);
            } else if (path.startsWith("/budgets/") && method.equals("GET")) {
                return getBudget(event);
            } else if (path.contains("/approve") && method.equals("POST")) {
                return approveBudget(event);
            }

            return buildResponse(404, Map.of("error", "Not found"));

        } catch (Exception e) {
            log.error("Error processing budget request", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /budgets - Laboratório cria orçamento
     */
    private APIGatewayV2HTTPResponse createBudget(APIGatewayV2HTTPEvent event) {
        try {
            CreateBudgetRequest request = objectMapper.readValue(
                    event.getBody(),
                    CreateBudgetRequest.class
            );

            DynamoDbTable<Budget> budgetsTable = dynamoDb.table(
                    System.getenv("BUDGETS_TABLE"),
                    TableSchema.fromBean(Budget.class)
            );

            DynamoDbTable<Case> casesTable = dynamoDb.table(
                    System.getenv("CASES_TABLE"),
                    TableSchema.fromBean(Case.class)
            );

            // Verificar se caso existe e está no status correto
            Case caseEntity = casesTable.getItem(r -> r.key(k -> k.partitionValue(request.getCaseId())));
            if (caseEntity == null) {
                return buildResponse(404, Map.of("error", "Case not found"));
            }

            if (caseEntity.getStatus() != Case.CaseStatus.AWAITING_BUDGET) {
                return buildResponse(400, Map.of(
                        "error", "Case not in AWAITING_BUDGET status",
                        "currentStatus", caseEntity.getStatus()
                ));
            }

            // Criar orçamento
            Budget budget = Budget.builder()
                    .budgetId(UUID.randomUUID().toString())
                    .caseId(request.getCaseId())
                    .labId(caseEntity.getLabId())
                    .amount(request.getAmount())
                    .currency("BRL")
                    .breakdown(request.getBreakdown())
                    .validUntil(Budget.calculateValidUntil(paymentConfig.getBudgetValidityDays()))
                    .status(Budget.BudgetStatus.PENDING)
                    .createdAt(Instant.now().toString())
                    .build();

            budgetsTable.putItem(budget);

            // Atualizar status do caso
            caseEntity.setStatus(Case.CaseStatus.BUDGET_SENT);
            caseEntity.setUpdatedAt(Instant.now().toString());
            casesTable.putItem(caseEntity);

            log.info("Budget created: {} for case: {}", budget.getBudgetId(), request.getCaseId());

            return buildResponse(201, Map.of(
                    "message", "Budget created successfully",
                    "budget", budget,
                    "validityDays", paymentConfig.getBudgetValidityDays()
            ));

        } catch (Exception e) {
            log.error("Error creating budget", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /budgets/{budgetId} - Busca orçamento
     */
    private APIGatewayV2HTTPResponse getBudget(APIGatewayV2HTTPEvent event) {
        try {
            String budgetId = event.getPathParameters().get("budgetId");

            DynamoDbTable<Budget> budgetsTable = dynamoDb.table(
                    System.getenv("BUDGETS_TABLE"),
                    TableSchema.fromBean(Budget.class)
            );

            Budget budget = budgetsTable.getItem(r -> r.key(k -> k.partitionValue(budgetId)));

            if (budget == null) {
                return buildResponse(404, Map.of("error", "Budget not found"));
            }

            // Verificar se expirou
            if (budget.isExpired() && budget.getStatus() == Budget.BudgetStatus.PENDING) {
                budget.setStatus(Budget.BudgetStatus.EXPIRED);
                budgetsTable.putItem(budget);
            }

            return buildResponse(200, budget);

        } catch (Exception e) {
            log.error("Error getting budget", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /budgets/{budgetId}/approve - Dentista aprova orçamento
     */
    private APIGatewayV2HTTPResponse approveBudget(APIGatewayV2HTTPEvent event) {
        try {
            String budgetId = event.getPathParameters().get("budgetId");

            DynamoDbTable<Budget> budgetsTable = dynamoDb.table(
                    System.getenv("BUDGETS_TABLE"),
                    TableSchema.fromBean(Budget.class)
            );

            DynamoDbTable<Case> casesTable = dynamoDb.table(
                    System.getenv("CASES_TABLE"),
                    TableSchema.fromBean(Case.class)
            );

            Budget budget = budgetsTable.getItem(r -> r.key(k -> k.partitionValue(budgetId)));

            if (budget == null) {
                return buildResponse(404, Map.of("error", "Budget not found"));
            }

            if (budget.getStatus() != Budget.BudgetStatus.PENDING) {
                return buildResponse(400, Map.of(
                        "error", "Budget not in PENDING status",
                        "currentStatus", budget.getStatus()
                ));
            }

            if (budget.isExpired()) {
                budget.setStatus(Budget.BudgetStatus.EXPIRED);
                budgetsTable.putItem(budget);
                return buildResponse(400, Map.of("error", "Budget expired"));
            }

            // Aprovar orçamento
            budget.setStatus(Budget.BudgetStatus.APPROVED);
            budget.setApprovedAt(Instant.now().toString());
            budgetsTable.putItem(budget);

            // Atualizar status do caso
            Case caseEntity = casesTable.getItem(r -> r.key(k -> k.partitionValue(budget.getCaseId())));
            if (caseEntity != null) {
                caseEntity.setStatus(Case.CaseStatus.IN_PROGRESS);
                caseEntity.setUpdatedAt(Instant.now().toString());
                casesTable.putItem(caseEntity);
            }

            log.info("Budget approved: {} for case: {}", budgetId, budget.getCaseId());

            return buildResponse(200, Map.of(
                    "message", "Budget approved successfully",
                    "budget", budget,
                    "case", caseEntity
            ));

        } catch (Exception e) {
            log.error("Error approving budget", e);
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
    public static class CreateBudgetRequest {
        private String caseId;
        private Long amount; // em centavos
        private List<Budget.BudgetItem> breakdown;
    }
}
