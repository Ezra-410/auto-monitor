/** Tells the browser who is logged in. Returns the profile only, NEVER the tokens. */
export default defineEventHandler(async (event) => {
  setHeader(event, 'cache-control', 'no-store')
  const current = await getOidcSession(event)
  return current ? { authenticated: true, user: current.data.profile } : { authenticated: false }
})
