import { createRemoteJWKSet, jwtVerify } from 'jose';
import { config } from './config.js';

export interface JwtIdentity {
  userId: number;
  username: string;
  roles: string[];
}

// Keycloak's public signing keys (JWKS). jose downloads them on first use and caches them.
const jwksUrl = new URL(`${config.oidcIssuer}/protocol/openid-connect/certs`);
const jwks = createRemoteJWKSet(jwksUrl);

/**
 * Validates a Keycloak access token before a WebSocket is accepted (D-23).
 * SECURITY CHECKS: RS256 signature with Keycloak's keys, issuer = our realm,
 * audience contains "conote-api", not expired (30 s clock tolerance).
 * Then resolves the Co-Note user ID by asking the backend, which validates the token again
 * and creates/updates the profile (D-24).
 */
export async function verifyToken(token: string): Promise<JwtIdentity> {
  await jwtVerify(token, jwks, {
    issuer: config.oidcIssuer,
    audience: config.apiAudience,
    algorithms: ['RS256'],
    clockTolerance: 30,
  });

  const res = await fetch(`${config.backendUrl}/api/v1/auth/me`, {
    headers: { authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(5000),
  });
  if (!res.ok) {
    throw new Error(`backend refused the token (${res.status})`);
  }
  const me = (await res.json()) as { id: number; username: string; roles?: string[] };
  return {
    userId: Number(me.id),
    username: String(me.username),
    roles: Array.isArray(me.roles) ? me.roles : [],
  };
}

/** Readiness: Keycloak's JWKS can be downloaded. */
export async function jwksReachable(): Promise<boolean> {
  try {
    const res = await fetch(jwksUrl, { signal: AbortSignal.timeout(3000) });
    return res.ok;
  } catch {
    return false;
  }
}
