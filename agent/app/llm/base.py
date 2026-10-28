from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field


@dataclass
class Completion:
    text: str
    provider: str
    model: str
    prompt_tokens: int = 0
    completion_tokens: int = 0
    raw: dict = field(default_factory=dict)

    @property
    def total_tokens(self) -> int:
        return self.prompt_tokens + self.completion_tokens


class LLMError(Exception):
    """Erreur d'appel à un fournisseur LLM (réseau, auth, quota, 5xx…)."""

    def __init__(self, message: str, *, retryable: bool = True, provider: str = ""):
        super().__init__(message)
        self.retryable = retryable
        self.provider = provider


class LLMProvider(ABC):
    """Abstraction d'un fournisseur LLM type chat-completions."""

    name: str = "abstract"

    @abstractmethod
    def is_configured(self) -> bool:
        """True si le provider a les credentials nécessaires."""

    @abstractmethod
    async def complete(
        self,
        messages: list[dict],
        *,
        model: str | None = None,
        max_tokens: int = 2000,
        temperature: float = 0.2,
        timeout: float = 120.0,
    ) -> Completion:
        """Effectue un appel chat et retourne la complétion."""
