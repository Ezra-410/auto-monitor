export interface AuthUser {
  id: number
  username: string
  display_name?: string
  roles: string[]
}

const TOKEN_KEY = 'monitor_token'

export function useAuth() {
  const token = useState<string | null>('token', () => null)
  const user = useState<AuthUser | null>('user', () => null)

  function loadFromStorage() {
    if (import.meta.client) {
      token.value = localStorage.getItem(TOKEN_KEY)
    }
  }

  async function login(username: string, password: string) {
    const res = await $fetch<{ token: string; user: AuthUser }>('/api/v1/auth/login', {
      method: 'POST',
      body: { username, password },
    })
    token.value = res.token
    user.value = res.user
    if (import.meta.client) {
      localStorage.setItem(TOKEN_KEY, res.token)
    }
  }

  async function fetchMe() {
    if (!token.value) return null
    try {
      user.value = await $fetch<AuthUser>('/api/v1/auth/me', {
        headers: { Authorization: `Bearer ${token.value}` },
      })
      return user.value
    } catch {
      logout()
      return null
    }
  }

  function logout() {
    token.value = null
    user.value = null
    if (import.meta.client) {
      localStorage.removeItem(TOKEN_KEY)
    }
    navigateTo('/login')
  }

  const isAuthenticated = computed(() => Boolean(token.value))

  return { token, user, isAuthenticated, loadFromStorage, login, fetchMe, logout }
}
