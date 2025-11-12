import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor - adiciona token de autenticação
api.interceptors.request.use(
  (config) => {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers.Authorization = `Bearer ${authStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// Response interceptor - trata erros globalmente
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const authStore = useAuthStore()
    
    // Se token expirou, tenta refresh
    if (error.response?.status === 401) {
      try {
        await authStore.refreshToken()
        // Retry request original
        return api.request(error.config)
      } catch (refreshError) {
        authStore.logout()
        window.location.href = '/login'
      }
    }
    
    return Promise.reject(error)
  }
)

// ==================== Cases API ====================

export const casesApi = {
  list: (params) => api.get('/cases', { params }),
  get: (caseId) => api.get(`/cases/${caseId}`),
  create: (data) => api.post('/cases', data),
  update: (caseId, data) => api.put(`/cases/${caseId}`, data),
  submitProject: (caseId, data) => api.post(`/cases/${caseId}/submit-project`, data),
  approveProject: (caseId) => api.post(`/cases/${caseId}/approve-project`),
  requestRevision: (caseId, notes) => api.post(`/cases/${caseId}/request-revision`, { notes }),
  confirmShipping: (caseId, trackingCode) => api.post(`/cases/${caseId}/shipping`, { trackingCode })
}

// ==================== Labs API ====================

export const labsApi = {
  list: (params) => api.get('/labs', { params }),
  get: (labId) => api.get(`/labs/${labId}`),
  create: (data) => api.post('/labs', data),
  acceptCase: (caseId) => api.post(`/labs/cases/${caseId}/accept`),
  rejectCase: (caseId) => api.post(`/labs/cases/${caseId}/reject`)
}

// ==================== Budgets API ====================

export const budgetsApi = {
  get: (budgetId) => api.get(`/budgets/${budgetId}`),
  create: (data) => api.post('/budgets', data),
  approve: (budgetId) => api.post(`/budgets/${budgetId}/approve`)
}

// ==================== Payments API ====================

export const paymentsApi = {
  get: (paymentId) => api.get(`/payments/${paymentId}`),
  create: (data) => api.post('/payments', data)
}

// ==================== Files API ====================

export const filesApi = {
  getUploadUrl: (fileName, fileType) => 
    api.post('/files/upload-url', { fileName, fileType }),
  getDownloadUrl: (fileKey) => 
    api.post('/files/download-url', { fileKey }),
  
  /**
   * Upload direto para S3 usando presigned URL
   */
  uploadToS3: async (file, onProgress) => {
    // 1. Obter presigned URL
    const { data } = await filesApi.getUploadUrl(file.name, file.type)
    
    // 2. Upload direto para S3
    await axios.put(data.uploadUrl, file, {
      headers: {
        'Content-Type': file.type
      },
      onUploadProgress: (progressEvent) => {
        if (onProgress) {
          const percentCompleted = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total
          )
          onProgress(percentCompleted)
        }
      }
    })
    
    return data.fileKey
  }
}

// ==================== Reviews API ====================

export const reviewsApi = {
  create: (data) => api.post('/reviews', data),
  getLabReviews: (labId) => api.get(`/labs/${labId}/reviews`)
}

// ==================== Disputes API ====================

export const disputesApi = {
  create: (data) => api.post('/disputes', data),
  get: (caseId) => api.get(`/disputes/${caseId}`),
  resolve: (caseId, data) => api.post(`/disputes/${caseId}/resolve`, data)
}

// ==================== GDPR API ====================

export const gdprApi = {
  exportData: () => api.post('/gdpr/export'),
  deleteData: () => api.post('/gdpr/delete')
}

export default api
