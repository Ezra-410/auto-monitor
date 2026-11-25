type EventHandler = (event: { type: string; payload?: any }) => void

/**
 * Connexion WebSocket au ws-gateway avec reconnexion automatique (backoff).
 * Milestone 3: the Keycloak access token is fetched from /auth/ws-token before EVERY connection
 * (kept in memory only), so a reconnection never reuses an expired token.
 */
export function useWs() {
  const connected = useState<boolean>('ws-connected', () => false)
  const lastError = useState<string | null>('ws-error', () => null)
  const runtimeConfig = useRuntimeConfig()

  let ws: WebSocket | null = null
  let handlers: EventHandler[] = []
  let reconnectDelay = 1000
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let closedByUser = false
  let getToken: (() => Promise<string>) | null = null

  function connect(tokenProvider: () => Promise<string>) {
    if (!import.meta.client) return
    getToken = tokenProvider
    closedByUser = false
    open()
  }

  async function open() {
    if (!getToken || closedByUser) return
    const base = String(runtimeConfig.public.wsUrl || 'ws://localhost:4000')
    try {
      const token = await getToken()
      ws = new WebSocket(`${base}/ws?token=${encodeURIComponent(token)}`)
    } catch {
      scheduleReconnect()
      return
    }
    ws.onopen = () => {
      connected.value = true
      lastError.value = null
      reconnectDelay = 1000
    }
    ws.onmessage = (e) => {
      try {
        const event = JSON.parse(String(e.data))
        for (const h of handlers) h(event)
      } catch {
        // message non-JSON ignoré
      }
    }
    ws.onclose = () => {
      connected.value = false
      if (!closedByUser) scheduleReconnect()
    }
    ws.onerror = () => {
      lastError.value = 'Connexion temps réel interrompue'
    }
  }

  function scheduleReconnect() {
    if (closedByUser || reconnectTimer) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      reconnectDelay = Math.min(reconnectDelay * 2, 30000)
      open()
    }, reconnectDelay)
  }

  function disconnect() {
    closedByUser = true
    if (reconnectTimer) clearTimeout(reconnectTimer)
    reconnectTimer = null
    ws?.close()
    ws = null
    connected.value = false
  }

  function send(msg: Record<string, unknown>) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg))
      return true
    }
    return false
  }

  function on(handler: EventHandler) {
    handlers.push(handler)
    return () => {
      handlers = handlers.filter((h) => h !== handler)
    }
  }

  return { connected, lastError, connect, disconnect, send, on }
}
