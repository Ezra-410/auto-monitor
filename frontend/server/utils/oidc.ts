import { createHash, randomBytes, timingSafeEqual } from 'node:crypto'
import { createRemoteJWKSet, jwtVerify, type JWTPayload } from 'jose'
import type { H3Event } from 'h3'

/**
 * OpenID Connect helpers for the Backend-for-Frontend (D-21).
 * Everything here runs on the Nuxt SERVER. The browser never sees the client secret or the tokens.
 */

export interface OidcSettings {
  issuer: string
  clientId: string
  clientSecret: string
  redirectUri: string
  postLogoutRedirectUri: string
  debug: boolean
  backendUrl: string
}

interface Discovery {
  issuer: string
  authorization_endpoint: string
  token_endpoint: string
  jwks_uri: string
  end_session_endpoint: string
}

export interface TokenResponse {
  access_token: string
  id_token?: string
  refresh_token?: string
  expires_in: number
  refresh_expires_in?: number
  token_type: string
}

export function oidcSettings(event?: H3Event): OidcSettings {
  const c = useRuntimeConfig(event)
  const issuer = String(c.oidcIssuer || '').replace(/\/$/, '')
  if (!issuer || !c.oidcClientId || !c.oidcClientSecret || !c.oidcRedirectUri) {
    throw createError({ statusCode: 500, statusMessage: 'OpenID Connect is not configured (NUXT_OIDC_*)' })
  }
  return {
    issuer,
    clientId: String(c.oidcClientId),
    clientSecret: String(c.oidcClientSecret),
    redirectUri: String(c.oidcRedirectUri),
    postLogoutRedirectUri: String(c.oidcPostLogoutRedirectUri || ''),
    debug: String(c.oidcDebug) === 'true', // env values may arrive as boolean or string
    backendUrl: String(c.backendUrl),
  }
}

// --- Discovery and keys (cached) -------------------------------------------------------------

let discoveryPromise: Promise<Discovery> | null = null
let jwks: ReturnType<typeof createRemoteJWKSet> | null = null

/** Reads Keycloak's /.well-known/openid-configuration once and checks it is really our issuer. */
export function discovery(issuer: string): Promise<Discovery> {
  if (!discoveryPromise) {
    discoveryPromise = $fetch<Discovery>(`${issuer}/.well-known/openid-configuration`)
      .then((d) => {
        if (d.issuer !== issuer) throw new Error(`discovery issuer mismatch: ${d.issuer}`)
        return d
      })
      .catch((e) => {
        discoveryPromise = null // retry on the next request
        throw e
      })
  }
  return discoveryPromise
}

async function keySet(issuer: string) {
  if (!jwks) jwks = createRemoteJWKSet(new URL((await discovery(issuer)).jwks_uri))
  return jwks
}

// --- Random values and PKCE ------------------------------------------------------------------

/** Cryptographically random, URL-safe string (used for state, nonce, PKCE verifier, session IDs). */
export function randomToken(bytes = 32): string {
  return randomBytes(bytes).toString('base64url')
}

/** PKCE S256: code_challenge = BASE64URL(SHA-256(code_verifier)). Only the challenge goes to the browser. */
export function pkceChallenge(verifier: string): string {
  return createHash('sha256').update(verifier).digest('base64url')
}

/** Constant-time string comparison (does not leak how many characters matched). */
export function safeEqual(a: string, b: string): boolean {
  const x = Buffer.from(a)
  const y = Buffer.from(b)
  return x.length === y.length && timingSafeEqual(x, y)
}

/** Only relative paths inside the app: prevents an open redirect after login. */
export function safeReturnTo(value: unknown): string {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') && !value.startsWith('/\\')
    ? value
    : '/'
}

// --- Token endpoint --------------------------------------------------------------------------

/** Calls Keycloak's token endpoint, authenticating this server with its client secret (client_secret_basic). */
export async function tokenRequest(s: OidcSettings, params: Record<string, string>): Promise<TokenResponse> {
  const d = await discovery(s.issuer)
  const basic = Buffer.from(`${encodeURIComponent(s.clientId)}:${encodeURIComponent(s.clientSecret)}`).toString('base64')
  return await $fetch<TokenResponse>(d.token_endpoint, {
    method: 'POST',
    headers: {
      authorization: `Basic ${basic}`,
      'content-type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(params).toString(),
  })
}

/**
 * SECURITY CHECKS on the ID token (brief M3 task 6):
 *  1. signature: RS256, verified with Keycloak's public keys (JWKS);
 *  2. issuer:    must equal our realm (iss);
 *  3. audience:  must be this client, conote-portal (aud);
 *  4. expiry:    exp / iat checked by jose (30 s clock tolerance);
 *  5. nonce:     must equal the random value we sent at /auth/login (stops token replay/injection);
 *  6. azp:       if present, must be this client.
 * Any failure throws, and the login is refused.
 */
export async function validateIdToken(s: OidcSettings, idToken: string, expectedNonce: string): Promise<JWTPayload> {
  const { payload } = await jwtVerify(idToken, await keySet(s.issuer), {
    issuer: s.issuer,
    audience: s.clientId,
    algorithms: ['RS256'],
    clockTolerance: 30,
  })
  if (typeof payload.nonce !== 'string' || !safeEqual(payload.nonce, expectedNonce)) {
    throw new Error('nonce mismatch')
  }
  if (payload.azp !== undefined && payload.azp !== s.clientId) {
    throw new Error('azp mismatch')
  }
  return payload
}
