import { defineEventHandler, getRequestURL, proxyRequest } from 'h3'

/**
 * Relaie /api/v1/** vers le backend Java (NUXT_BACKEND_URL).
 * Le navigateur ne contacte jamais le backend directement (spec §1.3).
 * /api/_health n'est pas concerné (route Nitro locale).
 */
export default defineEventHandler((event) => {
  const url = getRequestURL(event)
  if (!url.pathname.startsWith('/api/v1/')) {
    return
  }
  const config = useRuntimeConfig(event)
  const target = `${config.backendUrl}${url.pathname}${url.search}`
  return proxyRequest(event, target)
})
