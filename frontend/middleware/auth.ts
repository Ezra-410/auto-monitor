export default defineNuxtRouteMiddleware(() => {
  if (import.meta.client) {
    const token = localStorage.getItem('monitor_token')
    if (!token) {
      return navigateTo('/login')
    }
  }
})
