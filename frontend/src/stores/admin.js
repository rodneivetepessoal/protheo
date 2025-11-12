import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/services/api'

/**
 * Store para funcionalidades administrativas
 */
export const useAdminStore = defineStore('admin', () => {
  const stats = ref(null)
  const labs = ref([])
  const disputes = ref([])
  const pendingTransfers = ref([])
  const auditLogs = ref([])
  const loading = ref(false)
  const error = ref(null)

  // ==================== Dashboard Stats ====================

  /**
   * Carregar estatísticas do dashboard
   */
  async function loadDashboardStats() {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.get('/admin/dashboard/stats')
      stats.value = data
      return data
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Carregar estatísticas de receita
   */
  async function loadRevenueStats(period = 30) {
    try {
      const { data } = await api.get('/admin/dashboard/revenue', {
        params: { period }
      })
      return data
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    }
  }

  // ==================== Labs Management ====================

  /**
   * Carregar laboratórios com configurações
   */
  async function loadLabs() {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.get('/admin/labs')
      labs.value = data.items || []
      return labs.value
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Atualizar configuração de laboratório
   */
  async function updateLabConfig(labId, config) {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.put(`/admin/labs/${labId}`, config)
      
      // Atualizar na lista
      const index = labs.value.findIndex(l => l.labId === labId)
      if (index !== -1) {
        labs.value[index] = { ...labs.value[index], ...data.config }
      }

      return data.config
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Atualizar status de laboratório
   */
  async function updateLabStatus(labId, status, reason) {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.put(`/admin/labs/${labId}/status`, {
        status,
        reason
      })

      // Atualizar na lista
      const index = labs.value.findIndex(l => l.labId === labId)
      if (index !== -1) {
        labs.value[index].status = status
        labs.value[index].statusReason = reason
      }

      return data.config
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Atualizar configuração de repasse
   */
  async function updateTransferConfig(labId, transferConfig) {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.put(`/admin/labs/${labId}/transfer-config`, transferConfig)
      
      // Atualizar na lista
      const index = labs.value.findIndex(l => l.labId === labId)
      if (index !== -1) {
        labs.value[index].transferConfig = transferConfig
      }

      return data.config
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  // ==================== Disputes Management ====================

  /**
   * Carregar disputas
   */
  async function loadDisputes(status = null) {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.get('/admin/disputes', {
        params: status ? { status } : {}
      })
      disputes.value = data.items || []
      return disputes.value
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Resolver disputa
   */
  async function resolveDispute(disputeId, resolution, notes) {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.post(`/admin/disputes/${disputeId}/resolve`, {
        resolution,
        notes
      })

      // Atualizar na lista
      const index = disputes.value.findIndex(d => d.disputeId === disputeId)
      if (index !== -1) {
        disputes.value[index] = data.dispute
      }

      return data.dispute
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Processar reembolso
   */
  async function processRefund(disputeId, type, percentage = null) {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.post(`/admin/disputes/${disputeId}/refund`, {
        type,
        percentage
      })

      return data
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  // ==================== Transfers Management ====================

  /**
   * Carregar repasses pendentes
   */
  async function loadPendingTransfers() {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.get('/admin/transfers/pending')
      pendingTransfers.value = data.items || []
      return {
        items: data.items,
        totalPendingAmount: data.totalPendingAmount
      }
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Processar repasse manual
   */
  async function processManualTransfer(paymentId, stripeAccountId, amount, reason) {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.post('/admin/transfers/manual', {
        paymentId,
        stripeAccountId,
        amount,
        reason
      })

      // Remover da lista de pendentes
      pendingTransfers.value = pendingTransfers.value.filter(
        t => t.paymentId !== paymentId
      )

      return data
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  // ==================== Audit Logs ====================

  /**
   * Carregar logs de auditoria
   */
  async function loadAuditLogs(limit = 100) {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.get('/admin/audit-logs', {
        params: { limit }
      })
      auditLogs.value = data.items || []
      return auditLogs.value
    } catch (err) {
      error.value = err.response?.data?.error || err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  // ==================== Computed ====================

  const pendingDisputesCount = computed(() => {
    return disputes.value.filter(d => 
      d.status === 'PENDING' || d.status === 'UNDER_REVIEW'
    ).length
  })

  const totalPendingAmount = computed(() => {
    return pendingTransfers.value.reduce((sum, t) => sum + (t.holdAmount || 0), 0)
  })

  const activeLabs = computed(() => {
    return labs.value.filter(l => l.status === 'ACTIVE').length
  })

  return {
    // State
    stats,
    labs,
    disputes,
    pendingTransfers,
    auditLogs,
    loading,
    error,

    // Computed
    pendingDisputesCount,
    totalPendingAmount,
    activeLabs,

    // Actions
    loadDashboardStats,
    loadRevenueStats,
    loadLabs,
    updateLabConfig,
    updateLabStatus,
    updateTransferConfig,
    loadDisputes,
    resolveDispute,
    processRefund,
    loadPendingTransfers,
    processManualTransfer,
    loadAuditLogs
  }
})
