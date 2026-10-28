from __future__ import annotations

import os
from dataclasses import dataclass, field


def _int(name: str, default: int) -> int:
    try:
        return int(os.environ.get(name, "") or default)
    except ValueError:
        return default


@dataclass(frozen=True)
class Settings:
    pg_dsn: str = os.environ.get("PG_DSN", "postgresql://monitor:monitor@localhost:5432/agent_memory")
    backend_url: str = os.environ.get("BACKEND_URL", "http://localhost:8080").rstrip("/")
    ws_url: str = os.environ.get("WS_URL", "http://localhost:4000").rstrip("/")
    internal_token: str = os.environ.get("INTERNAL_API_TOKEN", "")

    # Providers LLM — Mistral prioritaire, OpenAI en fallback (LLM_PROVIDER_ORDER)
    llm_provider_order: tuple[str, ...] = field(
        default_factory=lambda: tuple(
            p.strip()
            for p in os.environ.get("LLM_PROVIDER_ORDER", "mistral,openai").split(",")
            if p.strip()
        )
    )
    # "mock" force un provider déterministe local (dev/tests sans clé)
    llm_provider_override: str = os.environ.get("LLM_PROVIDER", "").strip()
    mistral_api_key: str = os.environ.get("MISTRAL_API_KEY", "")
    mistral_model: str = os.environ.get("MISTRAL_MODEL", "mistral-large-latest")
    openai_api_key: str = os.environ.get("OPENAI_API_KEY", "")
    openai_model: str = os.environ.get("OPENAI_MODEL", "gpt-4o-mini")
    openai_embedding_model: str = os.environ.get("OPENAI_EMBEDDING_MODEL", "text-embedding-3-small")
    gemini_api_key: str = os.environ.get("GEMINI_API_KEY", "")

    llm_daily_token_budget: int = _int("LLM_DAILY_TOKEN_BUDGET", 2_000_000)
    heartbeat_minutes: int = _int("HEARTBEAT_MINUTES", 5)
    repos_dir: str = os.environ.get("REPOS_DIR", "/data/repos")

    # Jobs sous-agents
    subagent_poll_seconds: int = _int("SUBAGENT_POLL_SECONDS", 60)
    subagent_timeout_minutes: int = _int("SUBAGENT_TIMEOUT_MINUTES", 30)
    subagent_max_attempts: int = _int("SUBAGENT_MAX_ATTEMPTS", 3)
    llm_timeout_seconds: int = _int("LLM_TIMEOUT_SECONDS", 120)


settings = Settings()
