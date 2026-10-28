// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  modules: ['@nuxtjs/tailwindcss'],
  compatibilityDate: '2024-11-01',

  runtimeConfig: {
    // côté serveur Nitro uniquement
    backendUrl: process.env.NUXT_BACKEND_URL || 'http://localhost:8080',
    wsInternalUrl: process.env.NUXT_WS_INTERNAL_URL || 'http://localhost:4000',
    public: {
      // exposé au navigateur
      wsUrl: process.env.NUXT_PUBLIC_WS_URL || 'ws://localhost:4000',
    },
  },

  // Le navigateur ne parle qu'à Nuxt : un middleware Nitro relaie /api/v1/**
  // vers le backend (voir server/middleware/api-proxy.ts).
  // /api/_health reste une route Nitro locale (non proxyfiée).
})
