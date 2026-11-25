import type { H3Event } from 'h3'

/**
 * Server-side session store (D-25).
 * The browser gets only a sealed (encrypted + signed) HttpOnly cookie containing a random session ID.
 * The tokens stay on the server, in Nitro storage (memory for the lab: sessions end when the
 * frontend container restarts; use Redis in production). Reason: ID + access + refresh tokens
 * together exceed the ~4 KB browser cookie limit, and tokens must never be readable by JavaScript.
 */

export interface OidcSession {
  sub: string
  idToken: string
  accessToken: string
  refreshToken?: string
  accessExpiresAt: number // epoch ms
  expiresAt: number // epoch ms: absolute end of the Co-Note session
  profile: Record<string, unknown>
}

interface LoginTransaction {
  state?: string
  nonce?: string
  codeVerifier?: string
  returnTo?: string
}

const SESSION_SECONDS = 8 * 60 * 60 // 8 h
const TX_SECONDS = 10 * 60 // a login must complete within 10 min

function cookieOptions(event: H3Event, name: string, maxAge: number) {
  const c = useRuntimeConfig(event)
  const password = String(c.sessionPassword || '')
  if (password.length < 32) {
    throw createError({ statusCode: 500, statusMessage: 'NUXT_SESSION_PASSWORD must be at least 32 characters' })
  }
  return {
    name,
    password,
    maxAge,
    cookie: {
      httpOnly: true, // not readable by page scripts
      sameSite: 'lax' as const, // not sent on cross-site POSTs (CSRF protection)
      secure: String(c.oidcRedirectUri || '').startsWith('https://'), // true once HTTPS is enabled (M7)
      path: '/',
    },
  }
}

const store = () => useStorage('oidc-sessions')

// --- Login transaction: state, nonce and PKCE verifier between /auth/login and /auth/callback ---

export async function saveLoginTransaction(event: H3Event, tx: LoginTransaction) {
  const s = await useSession<LoginTransaction>(event, cookieOptions(event, 'conote_oidc_tx', TX_SECONDS))
  await s.update(tx)
}

/** Reads the transaction and clears it at once: each login transaction can be used only once. */
export async function takeLoginTransaction(event: H3Event): Promise<LoginTransaction> {
  const s = await useSession<LoginTransaction>(event, cookieOptions(event, 'conote_oidc_tx', TX_SECONDS))
  const tx = { ...s.data }
  await s.clear()
  return tx
}

// --- The Co-Note session -----------------------------------------------------------------------

async function sessionCookie(event: H3Event) {
  return await useSession<{ sid?: string }>(event, cookieOptions(event, 'conote_session', SESSION_SECONDS))
}

export async function getOidcSession(event: H3Event): Promise<{ sid: string; data: OidcSession } | null> {
  const cookie = await sessionCookie(event)
  const sid = cookie.data.sid
  if (!sid) return null
  const data = await store().getItem<OidcSession>(sid)
  if (!data || Date.now() > data.expiresAt) {
    if (data) await store().removeItem(sid)
    return null
  }
  return { sid, data }
}

/** Creates a NEW session ID at every login (prevents session fixation) and stores the tokens server-side. */
export async function createOidcSession(event: H3Event, data: Omit<OidcSession, 'expiresAt'>) {
  const cookie = await sessionCookie(event)
  if (cookie.data.sid) await store().removeItem(cookie.data.sid)
  const sid = randomToken(32)
  await store().setItem(sid, { ...data, expiresAt: Date.now() + SESSION_SECONDS * 1000 })
  await cookie.update({ sid })
}

export async function destroyOidcSession(event: H3Event) {
  const cookie = await sessionCookie(event)
  if (cookie.data.sid) await store().removeItem(cookie.data.sid)
  await cookie.clear()
}

/**
 * Returns a usable access token for this session, refreshing it with the refresh token when it
 * expires within 30 s. If the refresh fails (session ended in Keycloak, user disabled...), the
 * Co-Note session is destroyed and null is returned, so the caller answers 401.
 */
export async function getValidAccessToken(event: H3Event): Promise<string | null> {
  const current = await getOidcSession(event)
  if (!current) return null
  const { sid, data } = current
  if (Date.now() < data.accessExpiresAt - 30_000) return data.accessToken
  if (!data.refreshToken) {
    await destroyOidcSession(event)
    return null
  }
  try {
    const t = await tokenRequest(oidcSettings(event), { grant_type: 'refresh_token', refresh_token: data.refreshToken })
    const updated: OidcSession = {
      ...data,
      accessToken: t.access_token,
      refreshToken: t.refresh_token ?? data.refreshToken,
      idToken: t.id_token ?? data.idToken,
      accessExpiresAt: Date.now() + t.expires_in * 1000,
    }
    await store().setItem(sid, updated)
    return updated.accessToken
  } catch {
    console.warn('[oidc] refresh failed, session destroyed')
    await destroyOidcSession(event)
    return null
  }
}
