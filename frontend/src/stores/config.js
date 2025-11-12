import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * Store de configurações customizáveis do sistema
 * Valores sincronizados com backend via variáveis de ambiente
 */
export const useConfigStore = defineStore('config', () => {
  // Configurações de pagamento (read-only no frontend)
  const commissionPercentage = ref(parseInt(import.meta.env.VITE_COMMISSION_PERCENTAGE || '10'))
  const firstReleasePercentage = ref(parseInt(import.meta.env.VITE_FIRST_RELEASE_PERCENTAGE || '70'))
  const disputePeriodDays = ref(parseInt(import.meta.env.VITE_DISPUTE_PERIOD_DAYS || '14'))
  const maxFreeRevisions = ref(parseInt(import.meta.env.VITE_MAX_FREE_REVISIONS || '2'))
  const budgetValidityDays = ref(parseInt(import.meta.env.VITE_BUDGET_VALIDITY_DAYS || '7'))

  // Computed values
  const secondReleasePercentage = computed(() => 100 - firstReleasePercentage.value)
  
  const labPercentage = computed(() => 100 - commissionPercentage.value)

  /**
   * Calcula valor da comissão
   */
  function calculateCommission(totalAmount) {
    return Math.round((totalAmount * commissionPercentage.value) / 100)
  }

  /**
   * Calcula valor líquido do laboratório
   */
  function calculateLabAmount(totalAmount) {
    return totalAmount - calculateCommission(totalAmount)
  }

  /**
   * Calcula primeira liberação (após aprovação digital)
   */
  function calculateFirstRelease(labAmount) {
    return Math.round((labAmount * firstReleasePercentage.value) / 100)
  }

  /**
   * Calcula segunda liberação (após período de contestação)
   */
  function calculateSecondRelease(labAmount) {
    const firstRelease = calculateFirstRelease(labAmount)
    return labAmount - firstRelease
  }

  /**
   * Formata valor em centavos para BRL
   */
  function formatCurrency(cents) {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL'
    }).format(cents / 100)
  }

  /**
   * Verifica se ainda há revisões gratuitas disponíveis
   */
  function hasRevisionAvailable(currentRevisions) {
    return currentRevisions < maxFreeRevisions.value
  }

  /**
   * Calcula data de expiração do orçamento
   */
  function calculateBudgetExpiration() {
    const date = new Date()
    date.setDate(date.getDate() + budgetValidityDays.value)
    return date
  }

  /**
   * Calcula prazo de contestação
   */
  function calculateDisputeDeadline() {
    const date = new Date()
    date.setDate(date.getDate() + disputePeriodDays.value)
    return date
  }

  return {
    // Configurações
    commissionPercentage,
    firstReleasePercentage,
    secondReleasePercentage,
    disputePeriodDays,
    maxFreeRevisions,
    budgetValidityDays,
    labPercentage,
    
    // Métodos
    calculateCommission,
    calculateLabAmount,
    calculateFirstRelease,
    calculateSecondRelease,
    formatCurrency,
    hasRevisionAvailable,
    calculateBudgetExpiration,
    calculateDisputeDeadline
  }
})
