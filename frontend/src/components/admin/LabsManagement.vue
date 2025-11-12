<template>
  <div class="labs-management">
    <div class="mb-4 flex justify-between items-center">
      <h2 class="text-xl font-bold text-gray-900">Gestão de Laboratórios</h2>
      <button
        @click="refreshLabs"
        class="px-4 py-2 bg-primary-600 text-white rounded-lg hover:bg-primary-700"
      >
        Atualizar
      </button>
    </div>

    <!-- Filtros -->
    <div class="mb-4 flex gap-4">
      <select v-model="filterStatus" class="px-4 py-2 border rounded-lg">
        <option value="">Todos os Status</option>
        <option value="ACTIVE">Ativo</option>
        <option value="PENDING_REVIEW">Aguardando Revisão</option>
        <option value="SUSPENDED">Suspenso</option>
        <option value="BLOCKED">Bloqueado</option>
      </select>
    </div>

    <!-- Tabela de Laboratórios -->
    <div class="overflow-x-auto">
      <table class="min-w-full divide-y divide-gray-200">
        <thead class="bg-gray-50">
          <tr>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Lab</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Comissão</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Liberação</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Repasse</th>
            <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Ações</th>
          </tr>
        </thead>
        <tbody class="bg-white divide-y divide-gray-200">
          <tr v-for="lab in filteredLabs" :key="lab.labId">
            <td class="px-6 py-4 whitespace-nowrap">
              <div class="text-sm font-medium text-gray-900">{{ lab.name }}</div>
              <div class="text-sm text-gray-500">{{ lab.city }}, {{ lab.uf }}</div>
            </td>
            <td class="px-6 py-4 whitespace-nowrap">
              <span :class="getStatusClass(lab.status)" class="px-2 py-1 text-xs rounded-full">
                {{ getStatusLabel(lab.status) }}
              </span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ lab.customCommissionPercentage || configStore.commissionPercentage }}%
              <span v-if="lab.customCommissionPercentage" class="text-xs text-primary-600">(custom)</span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ lab.customFirstReleasePercentage || configStore.firstReleasePercentage }}%
              <span v-if="lab.customFirstReleasePercentage" class="text-xs text-primary-600">(custom)</span>
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
              {{ getTransferTypeLabel(lab.transferConfig?.transferType) }}
            </td>
            <td class="px-6 py-4 whitespace-nowrap text-sm">
              <button
                @click="editLab(lab)"
                class="text-primary-600 hover:text-primary-900 mr-3"
              >
                Editar
              </button>
              <button
                @click="changeStatus(lab)"
                class="text-orange-600 hover:text-orange-900"
              >
                Status
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Modal de Edição -->
    <EditLabModal
      v-if="selectedLab"
      :lab="selectedLab"
      @close="selectedLab = null"
      @updated="onLabUpdated"
    />

    <!-- Modal de Status -->
    <ChangeStatusModal
      v-if="labForStatusChange"
      :lab="labForStatusChange"
      @close="labForStatusChange = null"
      @updated="onLabUpdated"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAdminStore } from '@/stores/admin'
import { useConfigStore } from '@/stores/config'
import EditLabModal from './EditLabModal.vue'
import ChangeStatusModal from './ChangeStatusModal.vue'

const adminStore = useAdminStore()
const configStore = useConfigStore()

const filterStatus = ref('')
const selectedLab = ref(null)
const labForStatusChange = ref(null)

const filteredLabs = computed(() => {
  if (!filterStatus.value) return adminStore.labs
  return adminStore.labs.filter(l => l.status === filterStatus.value)
})

onMounted(() => {
  refreshLabs()
})

async function refreshLabs() {
  await adminStore.loadLabs()
}

function editLab(lab) {
  selectedLab.value = lab
}

function changeStatus(lab) {
  labForStatusChange.value = lab
}

function onLabUpdated() {
  selectedLab.value = null
  labForStatusChange.value = null
  refreshLabs()
}

function getStatusClass(status) {
  const classes = {
    'ACTIVE': 'bg-green-100 text-green-800',
    'PENDING_REVIEW': 'bg-yellow-100 text-yellow-800',
    'SUSPENDED': 'bg-orange-100 text-orange-800',
    'BLOCKED': 'bg-red-100 text-red-800',
    'INACTIVE': 'bg-gray-100 text-gray-800'
  }
  return classes[status] || 'bg-gray-100 text-gray-800'
}

function getStatusLabel(status) {
  const labels = {
    'ACTIVE': 'Ativo',
    'PENDING_REVIEW': 'Aguardando Revisão',
    'SUSPENDED': 'Suspenso',
    'BLOCKED': 'Bloqueado',
    'INACTIVE': 'Inativo'
  }
  return labels[status] || status
}

function getTransferTypeLabel(type) {
  const labels = {
    'IMMEDIATE': 'Imediato',
    'SCHEDULED': 'Agendado',
    'MANUAL': 'Manual'
  }
  return labels[type] || 'Não configurado'
}
</script>
