package com.protheo.admin;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.protheo.model.AdminAuditLog;
import com.protheo.model.Payment;
import com.protheo.service.PaymentService;
import com.stripe.Stripe;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handler administrativo para dashboard e repasses manuais
 */
@Slf4j
@Named("adminDashboard")
public class AdminDashboardHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    @Inject
    DynamoDbEnhancedClient dynamoDb;

    @Inject
    PaymentService paymentService;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        try {
            String path = event.getRawPath();
            String method = event.getRequestContext().getHttp().getMethod();

            // Verificar se é admin
            if (!isAdmin(event)) {
                return buildResponse(403, Map.of("error", "Forbidden: Admin access required"));
            }

            String adminId = getAdminId(event);
            String adminEmail = getAdminEmail(event);

            // Inicializar Stripe
            Stripe.apiKey = System.getenv("STRIPE_SECRET_KEY");

            // Roteamento
            if (path.contains("/dashboard/stats") && method.equals("GET")) {
                return getDashboardStats(event);
            } else if (path.contains("/dashboard/revenue") && method.equals("GET")) {
                return getRevenueStats(event);
            } else if (path.contains("/transfers/manual") && method.equals("POST")) {
                return processManualTransfer(event, adminId, adminEmail);
            } else if (path.contains("/transfers/pending") && method.equals("GET")) {
                return getPendingTransfers(event);
            } else if (path.contains("/audit-logs") && method.equals("GET")) {
                return getAuditLogs(event);
            }

            return buildResponse(404, Map.of("error", "Endpoint not found"));

        } catch (Exception e) {
            log.error("Error in admin dashboard handler", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obter estatísticas do dashboard
     */
    private APIGatewayV2HTTPResponse getDashboardStats(APIGatewayV2HTTPEvent event) {
        try {
            // Buscar todos os pagamentos
            DynamoDbTable<Payment> paymentTable = dynamoDb.table(
                    System.getenv("PAYMENTS_TABLE"),
                    TableSchema.fromBean(Payment.class)
            );

            List<Payment> allPayments = paymentTable.scan().items().stream().toList();

            // Calcular estatísticas
            long totalRevenue = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                    .mapToLong(Payment::getCommission)
                    .sum();

            long totalGMV = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                    .mapToLong(Payment::getTotalAmount)
                    .sum();

            long pendingTransfers = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.PARTIAL_RELEASED ||
                                 p.getStatus() == Payment.PaymentStatus.HELD)
                    .count();

            long completedCases = allPayments.stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                    .count();

            // Estatísticas por período
            Map<String, Long> revenueByMonth = calculateRevenueByMonth(allPayments);
            Map<String, Long> gmvByMonth = calculateGMVByMonth(allPayments);

            DashboardStats stats = DashboardStats.builder()
                    .totalRevenue(totalRevenue)
                    .totalGMV(totalGMV)
                    .averageCommission(totalGMV > 0 ? (totalRevenue * 100 / totalGMV) : 0)
                    .pendingTransfers(pendingTransfers)
                    .completedCases(completedCases)
                    .revenueByMonth(revenueByMonth)
                    .gmvByMonth(gmvByMonth)
                    .build();

            return buildResponse(200, stats);

        } catch (Exception e) {
            log.error("Error getting dashboard stats", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obter estatísticas de receita detalhadas
     */
    private APIGatewayV2HTTPResponse getRevenueStats(APIGatewayV2HTTPEvent event) {
        try {
            String period = event.getQueryStringParameters() != null ?
                    event.getQueryStringParameters().getOrDefault("period", "30") : "30";

            DynamoDbTable<Payment> paymentTable = dynamoDb.table(
                    System.getenv("PAYMENTS_TABLE"),
                    TableSchema.fromBean(Payment.class)
            );

            List<Payment> payments = paymentTable.scan().items().stream()
                    .filter(p -> isWithinPeriod(p, Integer.parseInt(period)))
                    .toList();

            // Calcular métricas
            long totalRevenue = payments.stream()
                    .mapToLong(Payment::getCommission)
                    .sum();

            long totalGMV = payments.stream()
                    .mapToLong(Payment::getTotalAmount)
                    .sum();

            Map<String, Long> revenueByLab = payments.stream()
                    .collect(Collectors.groupingBy(
                            p -> p.getCaseId(), // TODO: Mapear para labId
                            Collectors.summingLong(Payment::getCommission)
                    ));

            RevenueStats stats = RevenueStats.builder()
                    .period(period + " days")
                    .totalRevenue(totalRevenue)
                    .totalGMV(totalGMV)
                    .transactionCount(payments.size())
                    .averageTransactionValue(payments.isEmpty() ? 0 : totalGMV / payments.size())
                    .revenueByLab(revenueByLab)
                    .build();

            return buildResponse(200, stats);

        } catch (Exception e) {
            log.error("Error getting revenue stats", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Processar repasse manual
     */
    private APIGatewayV2HTTPResponse processManualTransfer(APIGatewayV2HTTPEvent event,
                                                            String adminId, String adminEmail) {
        try {
            ManualTransferRequest request = objectMapper.readValue(
                    event.getBody(),
                    ManualTransferRequest.class
            );

            // Buscar pagamento
            Payment payment = paymentService.findById(request.getPaymentId());
            if (payment == null) {
                return buildResponse(404, Map.of("error", "Payment not found"));
            }

            // Validar se pode fazer repasse
            if (payment.getStatus() != Payment.PaymentStatus.PARTIAL_RELEASED &&
                payment.getStatus() != Payment.PaymentStatus.HELD) {
                return buildResponse(400, Map.of("error", "Payment not eligible for transfer"));
            }

            // Processar transfer no Stripe
            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(request.getAmount())
                    .setCurrency("brl")
                    .setDestination(request.getStripeAccountId())
                    .putMetadata("paymentId", payment.getPaymentId())
                    .putMetadata("adminId", adminId)
                    .putMetadata("type", "manual")
                    .build();

            Transfer transfer = Transfer.create(params);

            // Atualizar pagamento
            Payment.TransferRecord transferRecord = Payment.TransferRecord.builder()
                    .stripeTransferId(transfer.getId())
                    .amount(request.getAmount())
                    .type(Payment.TransferType.MANUAL)
                    .processedAt(Instant.now().toString())
                    .build();

            payment.getTransfers().add(transferRecord);
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            paymentService.update(payment);

            // Log de auditoria
            logAudit(adminId, adminEmail, AdminAuditLog.AdminAction.MANUAL_TRANSFER,
                    "PAYMENT", payment.getPaymentId(),
                    String.format("Manual transfer: %d to %s", request.getAmount(), request.getStripeAccountId()),
                    null, Map.of("amount", String.valueOf(request.getAmount())));

            log.info("Manual transfer processed by admin: {} - paymentId: {} - amount: {}",
                    adminEmail, payment.getPaymentId(), request.getAmount());

            return buildResponse(200, Map.of(
                    "message", "Manual transfer processed successfully",
                    "transfer", transfer,
                    "payment", payment
            ));

        } catch (Exception e) {
            log.error("Error processing manual transfer", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obter repasses pendentes
     */
    private APIGatewayV2HTTPResponse getPendingTransfers(APIGatewayV2HTTPEvent event) {
        try {
            DynamoDbTable<Payment> paymentTable = dynamoDb.table(
                    System.getenv("PAYMENTS_TABLE"),
                    TableSchema.fromBean(Payment.class)
            );

            List<Payment> pendingPayments = paymentTable.scan().items().stream()
                    .filter(p -> p.getStatus() == Payment.PaymentStatus.PARTIAL_RELEASED ||
                                 p.getStatus() == Payment.PaymentStatus.HELD)
                    .toList();

            long totalPendingAmount = pendingPayments.stream()
                    .mapToLong(Payment::getHoldAmount)
                    .sum();

            return buildResponse(200, Map.of(
                    "items", pendingPayments,
                    "count", pendingPayments.size(),
                    "totalPendingAmount", totalPendingAmount
            ));

        } catch (Exception e) {
            log.error("Error getting pending transfers", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obter logs de auditoria
     */
    private APIGatewayV2HTTPResponse getAuditLogs(APIGatewayV2HTTPEvent event) {
        try {
            String limit = event.getQueryStringParameters() != null ?
                    event.getQueryStringParameters().getOrDefault("limit", "100") : "100";

            DynamoDbTable<AdminAuditLog> auditTable = dynamoDb.table(
                    System.getenv("AUDIT_LOG_TABLE"),
                    TableSchema.fromBean(AdminAuditLog.class)
            );

            List<AdminAuditLog> logs = auditTable.scan().items().stream()
                    .limit(Integer.parseInt(limit))
                    .toList();

            return buildResponse(200, Map.of(
                    "items", logs,
                    "count", logs.size()
            ));

        } catch (Exception e) {
            log.error("Error getting audit logs", e);
            return buildResponse(500, Map.of("error", e.getMessage()));
        }
    }

    // ==================== Helpers ====================

    private Map<String, Long> calculateRevenueByMonth(List<Payment> payments) {
        return payments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .collect(Collectors.groupingBy(
                        p -> extractMonth(p.getCreatedAt()),
                        Collectors.summingLong(Payment::getCommission)
                ));
    }

    private Map<String, Long> calculateGMVByMonth(List<Payment> payments) {
        return payments.stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.COMPLETED)
                .collect(Collectors.groupingBy(
                        p -> extractMonth(p.getCreatedAt()),
                        Collectors.summingLong(Payment::getTotalAmount)
                ));
    }

    private String extractMonth(String isoDate) {
        return isoDate.substring(0, 7); // YYYY-MM
    }

    private boolean isWithinPeriod(Payment payment, int days) {
        try {
            Instant paymentDate = Instant.parse(payment.getCreatedAt());
            Instant cutoff = Instant.now().minusSeconds(days * 24L * 60 * 60);
            return paymentDate.isAfter(cutoff);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAdmin(APIGatewayV2HTTPEvent event) {
        var claims = event.getRequestContext().getAuthorizer().getJwt().getClaims();
        String groups = claims.get("cognito:groups");
        return groups != null && groups.contains("admins");
    }

    private String getAdminId(APIGatewayV2HTTPEvent event) {
        return event.getRequestContext().getAuthorizer().getJwt().getClaims().get("sub");
    }

    private String getAdminEmail(APIGatewayV2HTTPEvent event) {
        return event.getRequestContext().getAuthorizer().getJwt().getClaims().get("email");
    }

    private void logAudit(String adminId, String adminEmail, AdminAuditLog.AdminAction action,
                          String entityType, String entityId, String description,
                          Map<String, String> oldValues, Map<String, String> newValues) {
        try {
            DynamoDbTable<AdminAuditLog> auditTable = dynamoDb.table(
                    System.getenv("AUDIT_LOG_TABLE"),
                    TableSchema.fromBean(AdminAuditLog.class)
            );

            AdminAuditLog log = AdminAuditLog.builder()
                    .logId(java.util.UUID.randomUUID().toString())
                    .timestamp(Instant.now().toString())
                    .adminId(adminId)
                    .adminEmail(adminEmail)
                    .action(action)
                    .actionDescription(description)
                    .entityType(entityType)
                    .entityId(entityId)
                    .oldValues(oldValues)
                    .newValues(newValues)
                    .result(AdminAuditLog.ActionResult.SUCCESS)
                    .build();

            auditTable.putItem(log);
        } catch (Exception e) {
            log.error("Error logging audit", e);
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

    // ==================== DTOs ====================

    @Data
    @Builder
    public static class DashboardStats {
        private long totalRevenue;
        private long totalGMV;
        private long averageCommission;
        private long pendingTransfers;
        private long completedCases;
        private Map<String, Long> revenueByMonth;
        private Map<String, Long> gmvByMonth;
    }

    @Data
    @Builder
    public static class RevenueStats {
        private String period;
        private long totalRevenue;
        private long totalGMV;
        private long transactionCount;
        private long averageTransactionValue;
        private Map<String, Long> revenueByLab;
    }

    @Data
    public static class ManualTransferRequest {
        private String paymentId;
        private String stripeAccountId;
        private Long amount;
        private String reason;
    }
}
