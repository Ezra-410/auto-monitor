/** Brief M3 task 4: unauthenticated users are sent to Keycloak, then back to the page they asked for. */
export default defineNuxtRouteMiddleware(async (to) => {
  const { user, refresh, login } = useAuth()
  if (!user.value) {
    await refresh()
  }
  if (!user.value) {
    return login(to.fullPath)
  }
})
