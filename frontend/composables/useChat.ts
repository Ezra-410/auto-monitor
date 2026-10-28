export interface ChatItem {
  id: string
  kind: 'message' | 'event'
  author?: 'USER' | 'AGENT' | 'SYSTEM'
  content: string
  eventType?: string
  payload?: any
  created_at?: string
}

interface Project {
  id: number
  name: string
  description?: string
}

export function useChat(token: Ref<string | null>, ws: ReturnType<typeof useWs>) {
  const projects = useState<Project[]>('projects', () => [])
  const currentProjectId = useState<number | null>('current-project', () => null)
  const conversationId = useState<number | null>('conversation-id', () => null)
  const items = useState<ChatItem[]>('chat-items', () => [])
  const agentTyping = useState<boolean>('agent-typing', () => false)
  const loading = useState<boolean>('chat-loading', () => false)

  const authHeaders = computed(() => ({ Authorization: `Bearer ${token.value}` }))

  async function loadProjects() {
    if (!token.value) return
    try {
      projects.value = await $fetch<Project[]>('/api/v1/projects', { headers: authHeaders.value })
      if (!currentProjectId.value && projects.value.length > 0) {
        await selectProject(projects.value[0].id)
      }
    } catch {
      // liste indisponible — l'écran affiche l'état vide
    }
  }

  async function selectProject(projectId: number) {
    currentProjectId.value = projectId
    items.value = []
    loading.value = true
    ws.send({ type: 'subscribe', project_id: projectId })
    try {
      const convs = await $fetch<{ id: number }[]>('/api/v1/conversations', {
        params: { project_id: projectId },
        headers: authHeaders.value,
      })
      conversationId.value = convs[0]?.id ?? null
      if (conversationId.value) {
        const history = await $fetch<any[]>(
          `/api/v1/conversations/${conversationId.value}/messages`,
          { headers: authHeaders.value })
        items.value = history.map((m) => ({
          id: `m-${m.id}`,
          kind: 'message',
          author: m.author,
          content: m.content,
          created_at: m.created_at,
        }))
      }
    } finally {
      loading.value = false
    }
  }

  function sendMessage(content: string) {
    if (!content.trim() || !currentProjectId.value) return false
    const sent = ws.send({
      type: 'chat.message',
      project_id: currentProjectId.value,
      conversation_id: conversationId.value ?? undefined,
      content: content.trim(),
    })
    if (sent) {
      items.value.push({
        id: `local-${Date.now()}`,
        kind: 'message',
        author: 'USER',
        content: content.trim(),
        created_at: new Date().toISOString(),
      })
      agentTyping.value = true
    }
    return sent
  }

  function handleEvent(event: { type: string; payload?: any; user_id?: number; project_id?: number }) {
    switch (event.type) {
      case 'agent.typing':
        agentTyping.value = Boolean(event.payload?.typing)
        break
      case 'message.created':
        agentTyping.value = false
        items.value.push({
          id: `m-${event.payload?.id ?? Date.now()}`,
          kind: 'message',
          author: event.payload?.author ?? 'AGENT',
          content: event.payload?.content ?? '',
          created_at: event.payload?.created_at,
        })
        break
      case 'task.created':
      case 'task.updated':
      case 'objective.created':
      case 'objective.updated':
      case 'bug.created':
      case 'bug.updated':
      case 'notification':
        items.value.push({
          id: `e-${Date.now()}-${Math.random()}`,
          kind: 'event',
          eventType: event.type,
          content: event.payload?.message ?? '',
          payload: event.payload,
        })
        break
    }
  }

  return {
    projects, currentProjectId, conversationId, items, agentTyping, loading,
    loadProjects, selectProject, sendMessage, handleEvent,
  }
}
