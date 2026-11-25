import type { H3Event } from 'h3'

/**
 * Steps 5-11 of the login (brief M3 tasks 5-8): Keycloak sends the browser back here with a code.
 */
export default defineEventHandler(async (event) => {
  const s = oidcSettings(event)
  const q = getQuery(event)
  const tx = await takeLoginTransaction(event) // single use

  // Keycloak reported an error (user cancelled, invalid request...)
  if (typeof q.error === 'string') return fail(event, q.error)

  // CHECK 1, state: the response must belong to the login this browser started.
  if (!tx.state || typeof q.state !== 'string' || !safeEqual(q.state, tx.state)) return fail(event, 'state_mismatch')
  if (typeof q.code !== 'string' || !tx.codeVerifier || !tx.nonce) return fail(event, 'missing_code')

  // CHECK 2, code exchange: server to server, with our client secret AND the PKCE code_verifier.
  // Keycloak recomputes SHA-256(verifier) and compares it with the challenge sent at /auth/login.
  let tokens
  try {
    tokens = await tokenRequest(s, {
      grant_type: 'authorization_code',
      code: q.code,
      redirect_uri: s.redirectUri,
      code_verifier: tx.codeVerifier,
    })
  } catch {
    return fail(event, 'token_exchange_failed')
  }
  if (!tokens.id_token) return fail(event, 'no_id_token')

  // CHECK 3, ID token: signature, issuer, audience, expiry, nonce, azp (see validateIdToken).
  let claims
  try {
    claims = await validateIdToken(s, tokens.id_token, tx.nonce)
  } catch (e) {
    console.warn(`[oidc] ID token rejected: ${(e as Error).message}`)
    return fail(event, 'invalid_id_token')
  }

  // Create or update the Co-Note profile (D-24). The backend validates the access token itself.
  let profile: Record<string, unknown>
  try {
    profile = await $fetch<Record<string, unknown>>(`${s.backendUrl}/api/v1/auth/me`, {
      headers: { authorization: `Bearer ${tokens.access_token}` },
    })
  } catch {
    return fail(event, 'profile_failed')
  }

  await createOidcSession(event, {
    sub: String(claims.sub),
    idToken: tokens.id_token,
    accessToken: tokens.access_token,
    refreshToken: tokens.refresh_token,
    accessExpiresAt: Date.now() + tokens.expires_in * 1000,
    profile,
  })
  console.info(`[oidc] login ok: ${String(claims.preferred_username ?? claims.sub)}`)
  return sendRedirect(event, safeReturnTo(tx.returnTo), 302)
})

function fail(event: H3Event, reason: string) {
  console.warn(`[oidc] login failed: ${reason}`)
  return sendRedirect(event, `/login?error=${encodeURIComponent(reason)}`, 302)
}
