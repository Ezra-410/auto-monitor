/**
 * Brief M3 task 9: logout.
 *  /auth/logout              full logout: ends the Co-Note session AND the Keycloak SSO session
 *                            (RP-initiated logout with id_token_hint).
 *  /auth/logout?scope=local  ends ONLY the Co-Note session; Keycloak's SSO session survives, so the
 *                            next login completes without a password (understanding question 6).
 */
export default defineEventHandler(async (event) => {
  const s = oidcSettings(event)
  const local = getQuery(event).scope === 'local'
  const current = await getOidcSession(event)
  await destroyOidcSession(event)

  if (local || !current) {
    return sendRedirect(event, `/login?loggedOut=${local ? 'local' : 'full'}`, 302)
  }
  const d = await discovery(s.issuer)
  const url = new URL(d.end_session_endpoint)
  url.search = new URLSearchParams({
    id_token_hint: current.data.idToken,
    post_logout_redirect_uri: s.postLogoutRedirectUri,
    client_id: s.clientId,
  }).toString()
  return sendRedirect(event, url.toString(), 302)
})
