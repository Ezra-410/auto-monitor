/**
 * Step 1-2 of the login (brief M3 task 4): send the browser to Keycloak.
 * Creates fresh state, nonce and PKCE verifier, keeps them in a short-lived sealed cookie, and
 * redirects to Keycloak's authorization endpoint with the PKCE challenge (S256).
 */
export default defineEventHandler(async (event) => {
  const s = oidcSettings(event)
  const d = await discovery(s.issuer)

  const state = randomToken(32) // ties the callback to THIS browser's login (stops login CSRF)
  const nonce = randomToken(32) // will be echoed inside the ID token (stops token replay)
  const codeVerifier = randomToken(64) // PKCE secret: never leaves this server
  const returnTo = safeReturnTo(getQuery(event).returnTo)

  await saveLoginTransaction(event, { state, nonce, codeVerifier, returnTo })

  const url = new URL(d.authorization_endpoint)
  url.search = new URLSearchParams({
    response_type: 'code', // Authorization Code flow
    client_id: s.clientId,
    redirect_uri: s.redirectUri,
    scope: 'openid profile email',
    state,
    nonce,
    code_challenge: pkceChallenge(codeVerifier),
    code_challenge_method: 'S256',
  }).toString()
  return sendRedirect(event, url.toString(), 302)
})
