/**
 * D-21 accepted exception: the WebSocket goes straight from the browser to ws-gateway, so the
 * browser needs the access token for that one connection. It is kept in memory only (never stored),
 * fetched fresh before each connection, and short-lived.
 */
export default defineEventHandler(async (event) => {
  setHeader(event, 'cache-control', 'no-store')
  const token = await getValidAccessToken(event)
  if (!token) throw createError({ statusCode: 401, statusMessage: 'unauthorized' })
  return { token }
})
