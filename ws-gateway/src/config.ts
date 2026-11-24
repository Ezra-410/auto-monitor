const oidcIssuer = (process.env.OIDC_ISSUER ?? '').replace(/\/$/, '');
if (!oidcIssuer) {
  throw new Error('OIDC_ISSUER is not set (Keycloak realm URL, e.g. http://keycloak.conote.local:8180/realms/conote)');
}

export const config = {
  port: Number(process.env.PORT ?? 4000),
  backendUrl: (process.env.BACKEND_URL ?? 'http://localhost:8080').replace(/\/$/, ''),
  internalToken: process.env.INTERNAL_API_TOKEN ?? '',
  allowedOrigin: process.env.ALLOWED_ORIGIN ?? '',
  // Milestone 3: tokens are issued by Keycloak, no longer by the backend.
  oidcIssuer,
  apiAudience: process.env.OIDC_API_AUDIENCE ?? 'conote-api',
};
