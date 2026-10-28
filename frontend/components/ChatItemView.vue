<script setup lang="ts">
import type { ChatItem } from '~/composables/useChat'

const props = defineProps<{ item: ChatItem }>()

const isUser = computed(() => props.item.kind === 'message' && props.item.author === 'USER')

const eventLabel = computed(() => {
  switch (props.item.eventType) {
    case 'task.created': return 'Nouvelle tâche'
    case 'task.updated': return 'Tâche mise à jour'
    case 'objective.created': return 'Nouvel objectif'
    case 'objective.updated': return 'Objectif mis à jour'
    case 'bug.created': return 'Bug signalé'
    case 'bug.updated': return 'Bug mis à jour'
    case 'notification': return 'Notification'
    default: return props.item.eventType ?? 'Événement'
  }
})

const eventTitle = computed(() => {
  const p = props.item.payload
  if (!p) return props.item.content
  return p.title ?? p.message ?? props.item.content
})

const eventDetail = computed(() => {
  const p = props.item.payload
  if (!p) return ''
  const parts: string[] = []
  if (p.status) parts.push(`statut : ${p.status}`)
  if (p.severity) parts.push(`sévérité : ${p.severity}`)
  if (p.priority) parts.push(`priorité : ${p.priority}`)
  if (p.due_date) parts.push(`échéance : ${p.due_date}`)
  if (p.assignee) parts.push(`assigné à : ${p.assignee}`)
  return parts.join(' · ')
})
</script>

<template>
  <div v-if="item.kind === 'message'" class="flex" :class="isUser ? 'justify-end' : 'justify-start'">
    <div
      class="max-w-[75%] rounded-2xl px-4 py-2.5 text-sm whitespace-pre-wrap"
      :class="isUser ? 'bg-indigo-600 text-white' : 'bg-gray-800 text-gray-100'"
    >
      <div v-if="!isUser" class="mb-1 text-xs font-semibold text-indigo-300">Agent</div>
      {{ item.content }}
    </div>
  </div>

  <div v-else class="flex justify-center">
    <div class="w-full max-w-md rounded-lg border border-gray-700 bg-gray-900 px-4 py-2.5 text-xs">
      <div class="mb-0.5 font-semibold uppercase tracking-wide text-amber-400">{{ eventLabel }}</div>
      <div class="text-sm text-gray-100">{{ eventTitle }}</div>
      <div v-if="eventDetail" class="mt-0.5 text-gray-400">{{ eventDetail }}</div>
    </div>
  </div>
</template>
