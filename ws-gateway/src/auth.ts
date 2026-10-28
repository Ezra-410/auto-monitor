import { createRemoteJWKSet, jwtVerify } from 'jose';
import { config } from './config.js';

export interface JwtIdentity {
  userId: number;
  username: string;
  roles: string[];
}

const jwksUrl = new URL(`${config.backendUrl}/.well-known/jwks.json`);
const jwks = createRemoteJWKSet(jwksUrl);

/** Vérifie un JWT RS256 émis par le backend via son JWKS. */
export async function verifyToken(token: string): Promise<JwtIdentity> {
  const { payload } = await jwtVerify(token, jwks, { algorithms: ['RS256'] });
  return {
    userId: Number(payload.uid),
    username: String(payload.sub ?? ''),
    roles: Array.isArray(payload.roles) ? (payload.roles as string[]) : [],
  };
}

/** Readiness : le JWKS du backend est récupérable. */
export async function jwksReachable(): Promise<boolean> {
  try {
    const res = await fetch(jwksUrl, { signal: AbortSignal.timeout(3000) });
    return res.ok;
  } catch {
    return false;
  }
}
