<script setup lang="ts">
definePageMeta({ middleware: 'auth' })

const { user, logout, wsToken } = useAuth()
const ws = useWs()
const chat = useChat(ws)

const input = ref('')
const threadEl = ref<HTMLElement | null>(null)

const currentProject = computed(() =>
  chat.projects.value.find((p) => p.id === chat.currentProjectId.value))

onMounted(async () => {
  // The auth middleware already guaranteed a Keycloak session (no token handling in the browser).
  ws.connect(wsToken)
  ws.on(chat.handleEvent)
  await chat.loadProjects()
  scrollToBottom()
})

onBeforeUnmount(() => ws.disconnect())

watch(() => chat.items.value.length, () => nextTick(scrollToBottom))

function scrollToBottom() {
  if (threadEl.value) {
    threadEl.value.scrollTop = threadEl.value.scrollHeight
  }
}

function submit() {
  if (chat.sendMessage(input.value)) {
    input.value = ''
  }
}
</script>

<template>
  <div class="flex h-screen flex-col">
    <!-- Header -->
    <header class="flex items-center justify-between border-b border-gray-800 px-4 py-3">
      <div class="flex items-center gap-3">
        <h1 class="text-lg font-bold">Moniteur Produit</h1>
        <select
          v-if="chat.projects.value.length > 0"
          :value="chat.currentProjectId.value ?? ''"
          class="rounded-lg border border-gray-700 bg-gray-800 px-2 py-1 text-sm"
          @change="chat.selectProject(Number(($event.target as HTMLSelectElement).value))"
        >
          <option v-for="p in chat.projects.value" :key="p.id" :value="p.id">
            {{ p.name }}
          </option>
        </select>
      </div>
      <div class="flex items-center gap-3 text-sm">
        <span
          class="inline-flex items-center gap-1.5"
          :class="ws.connected.value ? 'text-emerald-400' : 'text-gray-500'"
        >
          <span class="h-2 w-2 rounded-full" :class="ws.connected.value ? 'bg-emerald-400' : 'bg-gray-500'" />
          {{ ws.connected.value ? 'temps réel' : 'hors ligne' }}
        </span>
        <span class="text-gray-400" :title="user?.email">
          {{ user?.display_name || user?.username }}
          <span v-if="user?.groups?.length" class="text-gray-500">({{ user.groups.join(', ') }})</span>
        </span>
        <button class="text-gray-400 hover:text-white" title="Ends only the Co-Note session" @click="logout('local')">
          Se déconnecter de Co-Note
        </button>
        <button class="text-gray-400 hover:text-white" title="Ends the Keycloak SSO session too" @click="logout('full')">
          Se déconnecter partout
        </button>
      </div>
    </header>

    <!-- Fil de conversation -->
    <main ref="threadEl" class="flex-1 space-y-3 overflow-y-auto px-4 py-4">
      <div v-if="chat.loading.value" class="py-10 text-center text-sm text-gray-500">
        Chargement…
      </div>
      <div
        v-else-if="chat.items.value.length === 0"
        class="py-10 text-center text-sm text-gray-500"
      >
        <template v-if="chat.projects.value.length === 0">
          Aucun projet. Demandez à l'agent de s'occuper d'un dépôt : créez un projet via
          <code class="text-gray-300">POST /api/v1/projects</code>, puis envoyez un message ici.
        </template>
        <template v-else>
          Démarrez la conversation avec l'agent sur « {{ currentProject?.name }} ».
        </template>
      </div>
      <ChatItemView v-for="item in chat.items.value" :key="item.id" :item="item" />
      <div v-if="chat.agentTyping.value" class="flex justify-start">
        <div class="rounded-2xl bg-gray-800 px-4 py-2.5 text-sm text-gray-400">
          L'agent réfléchit…
        </div>
      </div>
    </main>

    <!-- Saisie -->
    <footer class="border-t border-gray-800 p-4">
      <form class="mx-auto flex max-w-3xl gap-2" @submit.prevent="submit">
        <input
          v-model="input"
          type="text"
          placeholder="Votre message à l'agent…"
          class="flex-1 rounded-xl border border-gray-700 bg-gray-800 px-4 py-2.5 text-sm outline-none focus:border-indigo-500"
        >
        <button
          type="submit"
          class="rounded-xl bg-indigo-600 px-5 py-2.5 text-sm font-semibold hover:bg-indigo-500"
        >
          Envoyer
        </button>
      </form>
    </footer>
  </div>
</template>
