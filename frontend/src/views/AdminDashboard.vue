<template>
  <div class="admin-dashboard p-6 bg-gray-50 min-h-screen">
    <!-- Header -->
    <div class="mb-6">
      <h1 class="text-3xl font-bold text-gray-900">Painel Administrativo</h1>
      <p class="text-gray-600 mt-2">Gerencie o marketplace Protheo</p>
    </div>

    <!-- Estatísticas Principais -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
      <div class="bg-white p-6 rounded-lg shadow">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-gray-500 text-sm font-medium">Receita Total</div>
            <div class="text-2xl font-bold text-green-600 mt-1">
              {{ formatCurrency(stats?.totalRevenue || 0) }}
            </div>
          </div>
          <div class="bg-green-100 p-3 rounded-full">
            <svg class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
        </div>
      </div>

      <div class="bg-white p-6 rounded-lg shadow">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-gray-500 text-sm font-medium">GMV Total</div>
            <div class="text-2xl font-bold text-blue-600 mt-1">
              {{ formatCurrency(stats?.totalGMV || 0) }}
            </div>
          </div>
          <div class="bg-blue-100 p-3 rounded-full">
            <svg class="w-6 h-6 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
            </svg>
          </div>
        </div>
      </div>

      <div class="bg-white p-6 rounded-lg shadow">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-gray-500 text-sm font-medium">Repasses Pendentes</div>
            <div class="text-2xl font-bold text-orange-600 mt-1">
              {{ stats?.pendingTransfers || 0 }}
            </div>
          </div>
          <div class="bg-orange-100 p-3 rounded-full">
            <svg class="w-6 h-6 text-orange-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
        </div>
      </div>

      <div class="bg-white p-6 rounded-lg shadow">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-gray-500 text-sm font-medium">Casos Concluídos</div>
            <div class="text-2xl font-bold text-purple-600 mt-1">
              {{ stats?.completedCases || 0 }}
            </div>
          </div>
          <div class="bg-purple-100 p-3 rounded-full">
            <svg class="w-6 h-6 text-purple-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="bg-white rounded-lg shadow mb-6">
      <div class="border-b border-gray-200">
        <nav class="flex -mb-px">
          <button
            v-for="tab in tabs"
            :key="tab.id"
            @click="activeTab = tab.id"
            :class="[
              'px-6 py-4 text-sm font-medium border-b-2 transition-colors',
              activeTab === tab.id
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300'
            ]"
          >
            {{ tab.label }}
            <span v-if="tab.badge" class="ml-2 px-2 py-1 text-xs rounded-full bg-red-100 text-red-600">
              {{ tab.badge }}
            </span>
          </button>
        </nav>
      </div>

      <!-- Tab Content -->
      <div class="p-6">
        <!-- Laboratórios -->
        <div v-if="activeTab === 'labs'">
          <LabsManagement />
        </div>

        <!-- Disputas -->
        <div v-if="activeTab === 'disputes'">
          <DisputesManagement />
        </div>

        <!-- Repasses -->
        <div v-if="activeTab === 'transfers'">
          <TransfersManagement />
        </div>

        <!-- Logs de Auditoria -->
        <div v-if="activeTab === 'audit'">
          <AuditLogs />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import { useConfigStore } from '@/stores/config'
import LabsManagement from '@/components/admin/LabsManagement.vue'
import DisputesManagement from '@/components/admin/DisputesManagement.vue'
import TransfersManagement from '@/components/admin/TransfersManagement.vue'
import AuditLogs from '@/components/admin/AuditLogs.vue'

const adminStore = useAdminStore()
const configStore = useConfigStore()

const activeTab = ref('labs')
const stats = computed(() => adminStore.stats)

const tabs = computed(() => [
  { id: 'labs', label: 'Laboratórios', badge: null },
  { id: 'disputes', label: 'Disputas', badge: adminStore.pendingDisputesCount || null },
  { id: 'transfers', label: 'Repasses', badge: adminStore.pendingTransfers.length || null },
  { id: 'audit', label: 'Auditoria', badge: null }
])

onMounted(async () => {
  await adminStore.loadDashboardStats()
  await adminStore.loadLabs()
  await adminStore.loadDisputes('PENDING')
  await adminStore.loadPendingTransfers()
})

function formatCurrency(cents) {
  return configStore.formatCurrency(cents)
}
</script>

<style scoped>
.admin-dashboard {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
