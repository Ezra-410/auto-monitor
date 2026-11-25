import { decodeJwt, decodeProtectedHeader } from 'jose'

/**
 * LAB ONLY (OIDC_DEBUG=true): shows the decoded tokens of the current session, as evidence for
 * "decode and inspect the ID token". Disabled (404) in normal operation; switched off in Milestone 7.
 */
export default defineEventHandler(async (event) => {
  if (!oidcSettings(event).debug) throw createError({ statusCode: 404 })
  setHeader(event, 'cache-control', 'no-store')
  const current = await getOidcSession(event)
  if (!current) throw createError({ statusCode: 401, statusMessage: 'not logged in' })
  const { idToken, accessToken } = current.data
  return {
    warning: 'LAB ONLY - never enable OIDC_DEBUG in production',
    id_token: { header: decodeProtectedHeader(idToken), claims: decodeJwt(idToken) },
    access_token: { header: decodeProtectedHeader(accessToken), claims: decodeJwt(accessToken) },
    raw_access_token: accessToken,
  }
})
