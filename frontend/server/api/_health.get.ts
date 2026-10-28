export default defineEventHandler(async (event) => {
  const query = getQuery(event)
  if (query.deep !== '1') {
    return { status: 'ok' }
  }
  const config = useRuntimeConfig()
  const check = async (url: string) => {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(3000) })
      return res.ok
    } catch {
      return false
    }
  }
  const [backend, ws] = await Promise.all([
    check(`${config.backendUrl}/actuator/health/liveness`),
    check(`${config.wsInternalUrl}/readyz`),
  ])
  const ok = backend && ws
  setResponseStatus(event, ok ? 200 : 503)
  return { status: ok ? 'ok' : 'degraded', backend, ws_gateway: ws }
})
