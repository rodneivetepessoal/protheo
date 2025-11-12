<template>
  <div class="dentist-dashboard p-6">
    <div class="mb-6">
      <h1 class="text-3xl font-bold text-gray-900">Dashboard - Dentista</h1>
      <p class="text-gray-600 mt-2">Gerencie seus casos clínicos</p>
    </div>

    <!-- Estatísticas -->
    <div class="grid grid-cols-1 md:grid-cols-4 gap-4 mb-6">
      <div class="bg-white p-4 rounded-lg shadow">
        <div class="text-gray-500 text-sm">Total de Casos</div>
        <div class="text-2xl font-bold text-primary-600">{{ stats.total }}</div>
      </div>
      <div class="bg-white p-4 rounded-lg shadow">
        <div class="text-gray-500 text-sm">Em Andamento</div>
        <div class="text-2xl font-bold text-yellow-600">{{ stats.inProgress }}</div>
      </div>
      <div class="bg-white p-4 rounded-lg shadow">
        <div class="text-gray-500 text-sm">Aguardando Aprovação</div>
        <div class="text-2xl font-bold text-orange-600">{{ stats.awaitingApproval }}</div>
      </div>
      <div class="bg-white p-4 rounded-lg shadow">
        <div class="text-gray-500 text-sm">Concluídos</div>
        <div class="text-2xl font-bold text-green-600">{{ stats.completed }}</div>
      </div>
    </div>

    <!-- Ações rápidas -->
    <div class="mb-6">
      <button 
        @click="showCreateCase = true"
        class="px-6 py-3 bg-primary-600 text-white rounded-lg hover:bg-primary-700 font-semibold"
      >
        + Novo Caso
      </button>
    </div>

    <!-- Filtros -->
    <div class="bg-white p-4 rounded-lg shadow mb-6">
      <div class="flex gap-4">
        <select v-model="filterStatus" class="px-4 py-2 border rounded-lg">
          <option value="">Todos os Status</option>
          <option value="PENDING">Pendente</option>
          <option value="AWAITING_BUDGET">Aguardando Orçamento</option>
          <option value="BUDGET_SENT">Orçamento Enviado</option>
          <option value="IN_PROGRESS">Em Produção</option>
          <option value="AWAITING_APPROVAL">Aguardando Aprovação</option>
          <option value="APPROVED">Aprovado</option>
          <option value="PAID">Pago</option>
          <option value="SHIPPED">Enviado</option>
          <option value="COMPLETED">Concluído</option>
        </select>
      </div>
    </div>

    <!-- Lista de casos -->
    <div class="bg-white rounded-lg shadow overflow-hidden">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">ID</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Título</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Laboratório</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Data</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Ações</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="case_ in filteredCases" :key="case_.caseId">
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ case_.caseId.substring(0, 8) }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ case_.title }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
              {{ case_.labName || 'N/A' }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span :class="getStatusClass(case_.status)" class="px-2 py-1 text-xs rounded-full">
                {{ getStatusLabel(case_.status) }}
              </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-600">
              {{ formatDate(case_.createdAt) }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm">
              <button 
                @click="viewCase(case_)"
                class="text-primary-600 hover:text-primary-900"
              >
                Ver Detalhes
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Modal de criação de caso -->
    <CreateCaseModal 
      v-if="showCreateCase"
      @close="showCreateCase = false"
      @created="onCaseCreated"
    />

    <!-- Modal de detalhes do caso -->
    <CaseDetailsModal
      v-if="selectedCase"
      :case="selectedCase"
      @close="selectedCase = null"
      @updated="loadCases"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { casesApi } from '@/services/api'
import CreateCaseModal from '@/components/CreateCaseModal.vue'
import CaseDetailsModal from '@/components/CaseDetailsModal.vue'

const cases = ref([])
const filterStatus = ref('')
const showCreateCase = ref(false)
const selectedCase = ref(null)

const filteredCases = computed(() => {
  if (!filterStatus.value) return cases.value
  return cases.value.filter(c => c.status === filterStatus.value)
})

const stats = computed(() => {
  return {
    total: cases.value.length,
    inProgress: cases.value.filter(c => 
      ['IN_PROGRESS', 'AWAITING_BUDGET', 'BUDGET_SENT'].includes(c.status)
    ).length,
    awaitingApproval: cases.value.filter(c => 
      c.status === 'AWAITING_APPROVAL'
    ).length,
    completed: cases.value.filter(c => 
      ['COMPLETED', 'REVIEWED'].includes(c.status)
    ).length
  }
})

onMounted(() => {
  loadCases()
  
  // Polling a cada 15 segundos
  setInterval(loadCases, 15000)
})

async function loadCases() {
  try {
    const { data } = await casesApi.list()
    cases.value = data.items || []
  } catch (error) {
    console.error('Error loading cases:', error)
  }
}

function viewCase(case_) {
  selectedCase.value = case_
}

function onCaseCreated() {
  showCreateCase.value = false
  loadCases()
}

function getStatusClass(status) {
  const classes = {
    'PENDING': 'bg-yellow-100 text-yellow-800',
    'AWAITING_BUDGET': 'bg-blue-100 text-blue-800',
    'BUDGET_SENT': 'bg-purple-100 text-purple-800',
    'IN_PROGRESS': 'bg-indigo-100 text-indigo-800',
    'AWAITING_APPROVAL': 'bg-orange-100 text-orange-800',
    'APPROVED': 'bg-green-100 text-green-800',
    'PAID': 'bg-emerald-100 text-emerald-800',
    'SHIPPED': 'bg-teal-100 text-teal-800',
    'COMPLETED': 'bg-green-100 text-green-800',
    'REVIEWED': 'bg-gray-100 text-gray-800'
  }
  return classes[status] || 'bg-gray-100 text-gray-800'
}

function getStatusLabel(status) {
  const labels = {
    'PENDING': 'Pendente',
    'AWAITING_BUDGET': 'Aguardando Orçamento',
    'BUDGET_SENT': 'Orçamento Enviado',
    'IN_PROGRESS': 'Em Produção',
    'AWAITING_APPROVAL': 'Aguardando Aprovação',
    'APPROVED': 'Aprovado',
    'PAID': 'Pago',
    'SHIPPED': 'Enviado',
    'COMPLETED': 'Concluído',
    'REVIEWED': 'Avaliado'
  }
  return labels[status] || status
}

function formatDate(isoDate) {
  return new Date(isoDate).toLocaleDateString('pt-BR')
}
</script>
