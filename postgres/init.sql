CREATE EXTENSION IF NOT EXISTS vector;

-- État courant : une ligne par projet suivi
CREATE TABLE agent_state (
  project_id      BIGINT PRIMARY KEY,
  status          TEXT NOT NULL,            -- IDLE | THINKING | WAITING_USER | WAITING_SUBAGENT
  current_focus   TEXT,                     -- ce sur quoi l'agent travaille maintenant
  next_action     JSONB,                    -- {type, params, due_at}
  heartbeat_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- File d'actions planifiées
CREATE TABLE agent_actions (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL,
  type TEXT NOT NULL, payload JSONB, status TEXT NOT NULL DEFAULT 'PENDING',
  scheduled_at TIMESTAMPTZ NOT NULL DEFAULT now(), result JSONB, created_at TIMESTAMPTZ DEFAULT now()
);

-- Souvenirs (faits, décisions, préférences) avec recherche sémantique
CREATE TABLE agent_memory (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL,
  kind TEXT NOT NULL,                       -- fact | decision | preference | summary
  content TEXT NOT NULL, embedding vector(1536), created_at TIMESTAMPTZ DEFAULT now()
);

-- Jobs délégués aux sous-agents
CREATE TABLE subagent_jobs (
  id UUID PRIMARY KEY, project_id BIGINT NOT NULL, role TEXT NOT NULL,
  provider TEXT NOT NULL,                   -- mistral | openai | gemini | anthropic
  task_id BIGINT, prompt TEXT, status TEXT NOT NULL, result JSONB,
  attempts INT DEFAULT 0, started_at TIMESTAMPTZ, last_checked_at TIMESTAMPTZ
);
