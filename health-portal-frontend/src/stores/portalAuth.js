import {defineStore} from 'pinia'
import {computed, ref} from 'vue'
import {portalUserApi} from '../api/portalUser'

export const usePortalAuthStore = defineStore('portalAuth', () => {
  const user = ref(null)
  const authDialogVisible = ref(false)
  const authDialogTab = ref('login')
  const pendingAction = ref(null)

    const isLoggedIn = computed(() => !!localStorage.getItem('portalToken'))
  const isResearcher = computed(() => user.value?.role === 'RESEARCHER')
  const certifyStatus = computed(() => user.value?.certifyStatus || 'NONE')

  function loadFromStorage() {
    try {
      const raw = localStorage.getItem('portalUser')
      if (raw) user.value = JSON.parse(raw)
    } catch {
      user.value = null
    }
  }

  async function fetchMe() {
      if (!localStorage.getItem('portalToken')) {
      user.value = null
      return null
    }
    try {
      const res = await portalUserApi.me()
      user.value = res.data
      localStorage.setItem('portalUser', JSON.stringify(res.data))
      return res.data
    } catch {
      logout()
      return null
    }
  }

  async function login(form) {
    const res = await portalUserApi.login(form)
    localStorage.setItem('portalToken', res.data.token)
    user.value = res.data
    localStorage.setItem('portalUser', JSON.stringify(res.data))
    return res.data
  }

  async function register(form) {
    const res = await portalUserApi.register(form)
    localStorage.setItem('portalToken', res.data.token)
    user.value = res.data
    localStorage.setItem('portalUser', JSON.stringify(res.data))
    return res.data
  }

  function logout() {
    localStorage.removeItem('portalToken')
    localStorage.removeItem('portalUser')
    user.value = null
  }

  function openAuthDialog(tab = 'login', action = null) {
    authDialogTab.value = tab
    pendingAction.value = action
    authDialogVisible.value = true
  }

  function closeAuthDialog() {
    authDialogVisible.value = false
    pendingAction.value = null
  }

  async function requireAuth(action, options = {}) {
    if (!isLoggedIn.value) {
      openAuthDialog(options.tab || 'login', action)
      return false
    }
    await fetchMe()
    if (action) await action()
    return true
  }

  async function afterAuthSuccess() {
    authDialogVisible.value = false
    if (pendingAction.value) {
      const fn = pendingAction.value
      pendingAction.value = null
      await fn()
    }
  }

  loadFromStorage()
  return {
      user, authDialogVisible, authDialogTab, pendingAction,
    isLoggedIn, isResearcher, certifyStatus,
    fetchMe, login, register, logout,
    openAuthDialog, closeAuthDialog, requireAuth, afterAuthSuccess
  }
})
