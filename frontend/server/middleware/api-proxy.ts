import { defineEventHandler, getRequestURL, proxyRequest } from 'h3'

/**
 * Relaie /api/v1/** vers le backend Java (NUXT_BACKEND_URL).
 * Le navigateur ne contacte jamais le backend directement (spec §1.3).
 *
 * Milestone 3 (D-21): the browser sends only its session cookie. The access token is taken from
 * the server-side session (refreshed if needed) and attached here. Any Authorization header sent by
 * the browser is replaced, and the session cookie is not forwarded to the backend.
 */
export default defineEventHandler(async (event) => {
  const url = getRequestURL(event)
  if (!url.pathname.startsWith('/api/v1/')) {
    return
  }
  const token = await getValidAccessToken(event)
  if (!token) {
    setResponseStatus(event, 401)
    return { error: 'unauthorized' }
  }
  const config = useRuntimeConfig(event)
  const target = `${config.backendUrl}${url.pathname}${url.search}`
  return proxyRequest(event, target, {
    headers: { authorization: `Bearer ${token}`, cookie: '' },
  })
})
