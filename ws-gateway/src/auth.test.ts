import http from 'node:http';
import type { AddressInfo } from 'node:net';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { SignJWT, exportJWK, generateKeyPair, type KeyLike } from 'jose';

// A fake Keycloak (JWKS) + fake backend (/api/v1/auth/me) on one local HTTP server.
let server: http.Server;
let issuer: string;
let goodKey: KeyLike;
let otherKey: KeyLike;
let verifyToken: (t: string) => Promise<{ userId: number; username: string }>;

beforeAll(async () => {
  const good = await generateKeyPair('RS256');
  const other = await generateKeyPair('RS256');
  goodKey = good.privateKey;
  otherKey = other.privateKey;
  const jwk = { ...(await exportJWK(good.publicKey)), kid: 'k1', alg: 'RS256', use: 'sig' };

  server = http.createServer((req, res) => {
    if (req.url?.endsWith('/protocol/openid-connect/certs')) {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ keys: [jwk] }));
    } else if (req.url === '/api/v1/auth/me') {
      res.writeHead(200, { 'content-type': 'application/json' });
      res.end(JSON.stringify({ id: 42, username: 'student01', roles: ['USER'] }));
    } else {
      res.writeHead(404).end();
    }
  });
  await new Promise<void>((r) => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  issuer = `${base}/realms/conote`;
  process.env.OIDC_ISSUER = issuer;
  process.env.OIDC_API_AUDIENCE = 'conote-api';
  process.env.BACKEND_URL = base;
  ({ verifyToken } = await import('./auth.js'));
});

afterAll(() => server?.close());

function token(opts: { key?: KeyLike; iss?: string; aud?: string; exp?: string | number } = {}) {
  return new SignJWT({ preferred_username: 'student01' })
    .setProtectedHeader({ alg: 'RS256', kid: 'k1' })
    .setSubject('kc-sub-1')
    .setIssuer(opts.iss ?? issuer)
    .setAudience(opts.aud ?? 'conote-api')
    .setIssuedAt()
    .setExpirationTime(opts.exp ?? '5m')
    .sign(opts.key ?? goodKey);
}

describe('verifyToken (Keycloak access tokens)', () => {
  it('accepts a valid token and resolves the Co-Note user', async () => {
    await expect(verifyToken(await token())).resolves.toEqual({ userId: 42, username: 'student01', roles: ['USER'] });
  });
  it('rejects a token signed with another key', async () => {
    await expect(verifyToken(await token({ key: otherKey }))).rejects.toThrow();
  });
  it('rejects a token from another issuer', async () => {
    await expect(verifyToken(await token({ iss: 'http://evil.example/realms/conote' }))).rejects.toThrow();
  });
  it('rejects a token for another audience', async () => {
    await expect(verifyToken(await token({ aud: 'account' }))).rejects.toThrow();
  });
  it('rejects an expired token', async () => {
    const past = Math.floor(Date.now() / 1000) - 3600;
    await expect(verifyToken(await token({ exp: past }))).rejects.toThrow();
  });
});
