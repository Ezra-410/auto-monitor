// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  modules: ['@nuxtjs/tailwindcss'],
  compatibilityDate: '2024-11-01',

  runtimeConfig: {
    // Server-side (Nitro) only: never sent to the browser.
    backendUrl: process.env.NUXT_BACKEND_URL || 'http://localhost:8080',
    wsInternalUrl: process.env.NUXT_WS_INTERNAL_URL || 'http://localhost:4000',

    // Milestone 3, OpenID Connect (D-21 BFF). Filled at runtime from NUXT_OIDC_* env vars.
    oidcIssuer: '',                // http://keycloak.conote.local:8180/realms/conote
    oidcClientId: '',              // conote-portal
    oidcClientSecret: '',          // confidential client secret (server only)
    oidcRedirectUri: '',           // http://localhost:3000/auth/callback
    oidcPostLogoutRedirectUri: '', // http://localhost:3000/
    oidcDebug: false,              // LAB ONLY: enables /auth/debug
    sessionPassword: '',           // key that seals the session cookies (>= 32 chars)

    public: {
      // exposé au navigateur
      wsUrl: process.env.NUXT_PUBLIC_WS_URL || 'ws://localhost:4000',
    },
  },

  // Le navigateur ne parle qu'à Nuxt : un middleware Nitro relaie /api/v1/**
  // vers le backend (voir server/middleware/api-proxy.ts).
  // /api/_health reste une route Nitro locale (non proxyfiée).
  // /auth/** = the OpenID Connect routes (server/routes/auth/).
})
