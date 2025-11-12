import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { 
  CognitoUserPool, 
  CognitoUser, 
  AuthenticationDetails,
  CognitoUserAttribute 
} from 'amazon-cognito-identity-js'

const userPool = new CognitoUserPool({
  UserPoolId: import.meta.env.VITE_COGNITO_USER_POOL_ID,
  ClientId: import.meta.env.VITE_COGNITO_CLIENT_ID
})

export const useAuthStore = defineStore('auth', () => {
  const user = ref(null)
  const token = ref(null)
  const loading = ref(false)
  const error = ref(null)

  const isAuthenticated = computed(() => !!token.value)
  const userRole = computed(() => user.value?.role || null)
  const isDentist = computed(() => userRole.value === 'dentist')
  const isLab = computed(() => userRole.value === 'lab')

  /**
   * Registrar novo usuário
   */
  async function register(email, password, name, role) {
    loading.value = true
    error.value = null

    try {
      const attributeList = [
        new CognitoUserAttribute({ Name: 'email', Value: email }),
        new CognitoUserAttribute({ Name: 'name', Value: name }),
        new CognitoUserAttribute({ Name: 'custom:role', Value: role })
      ]

      return new Promise((resolve, reject) => {
        userPool.signUp(email, password, attributeList, null, (err, result) => {
          if (err) {
            error.value = err.message
            reject(err)
            return
          }
          resolve(result.user)
        })
      })
    } catch (err) {
      error.value = err.message
      throw err
    } finally {
      loading.value = false
    }
  }

  /**
   * Login
   */
  async function login(email, password) {
    loading.value = true
    error.value = null

    try {
      const authenticationDetails = new AuthenticationDetails({
        Username: email,
        Password: password
      })

      const cognitoUser = new CognitoUser({
        Username: email,
        Pool: userPool
      })

      return new Promise((resolve, reject) => {
        cognitoUser.authenticateUser(authenticationDetails, {
          onSuccess: (result) => {
            token.value = result.getIdToken().getJwtToken()
            
            // Obter atributos do usuário
            cognitoUser.getUserAttributes((err, attributes) => {
              if (!err) {
                const userAttrs = {}
                attributes.forEach(attr => {
                  userAttrs[attr.getName()] = attr.getValue()
                })
                
                user.value = {
                  email: userAttrs.email,
                  name: userAttrs.name,
                  role: userAttrs['custom:role'],
                  sub: userAttrs.sub
                }
              }
              
              loading.value = false
              resolve(result)
            })
          },
          onFailure: (err) => {
            error.value = err.message
            loading.value = false
            reject(err)
          }
        })
      })
    } catch (err) {
      error.value = err.message
      loading.value = false
      throw err
    }
  }

  /**
   * Logout
   */
  function logout() {
    const cognitoUser = userPool.getCurrentUser()
    if (cognitoUser) {
      cognitoUser.signOut()
    }
    
    user.value = null
    token.value = null
    error.value = null
  }

  /**
   * Verificar sessão atual
   */
  async function checkSession() {
    const cognitoUser = userPool.getCurrentUser()
    
    if (!cognitoUser) {
      return false
    }

    return new Promise((resolve) => {
      cognitoUser.getSession((err, session) => {
        if (err || !session.isValid()) {
          logout()
          resolve(false)
          return
        }

        token.value = session.getIdToken().getJwtToken()
        
        cognitoUser.getUserAttributes((err, attributes) => {
          if (!err) {
            const userAttrs = {}
            attributes.forEach(attr => {
              userAttrs[attr.getName()] = attr.getValue()
            })
            
            user.value = {
              email: userAttrs.email,
              name: userAttrs.name,
              role: userAttrs['custom:role'],
              sub: userAttrs.sub
            }
          }
          
          resolve(true)
        })
      })
    })
  }

  /**
   * Refresh token
   */
  async function refreshToken() {
    const cognitoUser = userPool.getCurrentUser()
    
    if (!cognitoUser) {
      return false
    }

    return new Promise((resolve, reject) => {
      cognitoUser.getSession((err, session) => {
        if (err) {
          reject(err)
          return
        }

        cognitoUser.refreshSession(session.getRefreshToken(), (err, session) => {
          if (err) {
            reject(err)
            return
          }

          token.value = session.getIdToken().getJwtToken()
          resolve(session)
        })
      })
    })
  }

  return {
    user,
    token,
    loading,
    error,
    isAuthenticated,
    userRole,
    isDentist,
    isLab,
    register,
    login,
    logout,
    checkSession,
    refreshToken
  }
})
