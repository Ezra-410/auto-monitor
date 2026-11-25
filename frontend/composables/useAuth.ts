export interface AuthUser {
  id: number
  username: string
  display_name?: string
  email?: string
  roles: string[]
  groups?: string[]
  student_id?: string
  department?: string
}

/**
 * Milestone 3: authentication is done by Keycloak through the Nuxt server (D-21).
 * The browser never handles tokens: it asks /auth/session who is logged in.
 */
export function useAuth() {
  const user = useState<AuthUser | null>('user', () => null)

  async function refresh() {
    // useRequestFetch forwards the session cookie when this runs during server-side rendering
    const res = await useRequestFetch()<{ authenticated: boolean; user?: AuthUser }>('/auth/session')
    user.value = res.authenticated ? (res.user ?? null) : null
    if (import.meta.client) {
      localStorage.removeItem('monitor_token') // clean-up of the pre-SSO token, never used again
    }
    return user.value
  }

  function login(returnTo = '/') {
    return navigateTo(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`, { external: true })
  }

  /** 'full' ends the Keycloak SSO session too; 'local' ends only the Co-Note session. */
  function logout(mode: 'full' | 'local' = 'full') {
    return navigateTo(mode === 'local' ? '/auth/logout?scope=local' : '/auth/logout', { external: true })
  }

  /** Fresh access token for the WebSocket only (kept in memory, never stored). */
  async function wsToken() {
    const res = await $fetch<{ token: string }>('/auth/ws-token')
    return res.token
  }

  const isAuthenticated = computed(() => Boolean(user.value))

  return { user, isAuthenticated, refresh, login, logout, wsToken }
}
