<script setup lang="ts">
definePageMeta({ layout: false })

const route = useRoute()
const { login } = useAuth()

const errors: Record<string, string> = {
  state_mismatch: 'La réponse de connexion ne correspond pas à votre demande (state). Réessayez.',
  invalid_id_token: "Le jeton d'identité a été rejeté (signature, émetteur, audience, expiration ou nonce).",
  token_exchange_failed: "L'échange du code d'autorisation a échoué.",
  access_denied: 'Connexion annulée ou refusée.',
}
const error = computed(() => {
  const code = route.query.error
  return typeof code === 'string' ? (errors[code] ?? `Échec de la connexion (${code}).`) : ''
})
const loggedOut = computed(() => route.query.loggedOut)
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-950 px-4">
    <div class="w-full max-w-sm rounded-2xl border border-gray-800 bg-gray-900 p-8 shadow-xl">
      <h1 class="mb-1 text-xl font-bold text-white">Co-Note</h1>
      <p class="mb-6 text-sm text-gray-400">
        Connexion unique (SSO) avec votre compte institutionnel.
      </p>

      <p v-if="loggedOut === 'local'" class="mb-4 text-sm text-amber-300">
        Vous êtes déconnecté de Co-Note uniquement. Votre session institutionnelle (Keycloak) est
        toujours active : la prochaine connexion ne demandera pas de mot de passe.
      </p>
      <p v-else-if="loggedOut === 'full'" class="mb-4 text-sm text-emerald-300">
        Vous êtes déconnecté.
      </p>
      <p v-if="error" class="mb-4 text-sm text-red-400">{{ error }}</p>

      <button
        type="button"
        class="w-full rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white transition hover:bg-indigo-500"
        @click="login('/')"
      >
        Se connecter avec votre compte institutionnel
      </button>
    </div>
  </div>
</template>
