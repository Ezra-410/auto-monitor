<script setup lang="ts">
definePageMeta({ layout: false })

const { login } = useAuth()
const username = ref('')
const password = ref('')
const error = ref('')
const pending = ref(false)

async function submit() {
  error.value = ''
  pending.value = true
  try {
    await login(username.value, password.value)
    await navigateTo('/')
  } catch {
    error.value = 'Identifiants invalides'
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-950 px-4">
    <form
      class="w-full max-w-sm rounded-2xl border border-gray-800 bg-gray-900 p-8 shadow-xl"
      @submit.prevent="submit"
    >
      <h1 class="mb-1 text-xl font-bold text-white">Moniteur Produit</h1>
      <p class="mb-6 text-sm text-gray-400">Connectez-vous pour discuter avec l'agent.</p>

      <label class="mb-1 block text-xs font-medium text-gray-400">Nom d'utilisateur</label>
      <input
        v-model="username"
        type="text"
        required
        autocomplete="username"
        class="mb-4 w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm text-white outline-none focus:border-indigo-500"
      >

      <label class="mb-1 block text-xs font-medium text-gray-400">Mot de passe</label>
      <input
        v-model="password"
        type="password"
        required
        autocomplete="current-password"
        class="mb-6 w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm text-white outline-none focus:border-indigo-500"
      >

      <p v-if="error" class="mb-4 text-sm text-red-400">{{ error }}</p>

      <button
        type="submit"
        :disabled="pending"
        class="w-full rounded-lg bg-indigo-600 py-2 text-sm font-semibold text-white transition hover:bg-indigo-500 disabled:opacity-50"
      >
        {{ pending ? 'Connexion…' : 'Se connecter' }}
      </button>
    </form>
  </div>
</template>
