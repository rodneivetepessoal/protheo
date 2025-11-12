package com.protheo.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import lombok.Getter;

/**
 * Configurações customizáveis de pagamento e comissão.
 * Permite ajustar porcentagens via variáveis de ambiente sem redeployar código.
 */
@ApplicationScoped
@Getter
public class PaymentConfig {

    /**
     * Porcentagem de comissão da Protheo (0-30%)
     * Padrão: 10%
     */
    @ConfigProperty(name = "payment.commission.percentage", defaultValue = "10")
    Integer commissionPercentage;

    /**
     * Porcentagem liberada para o laboratório após aprovação do projeto digital (0-100%)
     * Padrão: 70%
     */
    @ConfigProperty(name = "payment.first.release.percentage", defaultValue = "70")
    Integer firstReleasePercentage;

    /**
     * Dias para contestação após recebimento do produto físico
     * Padrão: 14 dias
     */
    @ConfigProperty(name = "payment.dispute.period.days", defaultValue = "14")
    Integer disputePeriodDays;

    /**
     * Número máximo de revisões gratuitas
     * Padrão: 2
     */
    @ConfigProperty(name = "payment.max.free.revisions", defaultValue = "2")
    Integer maxFreeRevisions;

    /**
     * Dias de validade do orçamento
     * Padrão: 7 dias
     */
    @ConfigProperty(name = "payment.budget.validity.days", defaultValue = "7")
    Integer budgetValidityDays;

    /**
     * Calcula o valor da comissão em centavos
     */
    public long calculateCommission(long totalAmount) {
        return (totalAmount * commissionPercentage) / 100;
    }

    /**
     * Calcula o valor líquido do laboratório (total - comissão)
     */
    public long calculateLabAmount(long totalAmount) {
        return totalAmount - calculateCommission(totalAmount);
    }

    /**
     * Calcula o valor da primeira liberação (após aprovação digital)
     */
    public long calculateFirstRelease(long labAmount) {
        return (labAmount * firstReleasePercentage) / 100;
    }

    /**
     * Calcula o valor da segunda liberação (após período de contestação)
     */
    public long calculateSecondRelease(long labAmount) {
        long firstRelease = calculateFirstRelease(labAmount);
        return labAmount - firstRelease;
    }

    /**
     * Calcula a porcentagem restante após primeira liberação
     */
    public int getSecondReleasePercentage() {
        return 100 - firstReleasePercentage;
    }

    /**
     * Verifica se ainda há revisões gratuitas disponíveis
     */
    public boolean hasRevisionAvailable(int currentRevisions) {
        return currentRevisions < maxFreeRevisions;
    }
}
