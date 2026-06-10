import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const apiKey = ref(localStorage.getItem('adminApiKey') || '')

  const isAuthenticated = computed(() => apiKey.value.length > 0)

  function login(key: string) {
    apiKey.value = key
    localStorage.setItem('adminApiKey', key)
  }

  function logout() {
    apiKey.value = ''
    localStorage.removeItem('adminApiKey')
  }

  return { apiKey, isAuthenticated, login, logout }
})
