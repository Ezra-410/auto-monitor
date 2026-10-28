from __future__ import annotations

import asyncio
import logging
from datetime import date

from ..config import Settings
from .base import Completion, LLMError, LLMProvider
from .mistral import MistralProvider
from .mock import MockProvider
from .openai import OpenAIProvider

log = logging.getLogger(__name__)


class TokenBudget:
    """Compteur de tokens par fournisseur, remis à zéro chaque jour (en mémoire)."""

    def __init__(self, daily_budget: int):
        self.daily_budget = daily_budget
        self._usage: dict[tuple[str, str], int] = {}

    def _key(self, provider: str) -> tuple[str, str]:
        return (provider, date.today().isoformat())

    def remaining(self, provider: str) -> int:
        return self.daily_budget - self._usage.get(self._key(provider), 0)

    def record(self, provider: str, tokens: int) -> None:
        key = self._key(provider)
        self._usage[key] = self._usage.get(key, 0) + max(tokens, 0)

    def usage(self, provider: str) -> int:
        return self._usage.get(self._key(provider), 0)


class FailoverLLM(LLMProvider):
    """Essaie les providers dans l'ordre configuré ; bascule sur le suivant en cas d'erreur."""

    name = "failover"

    def __init__(self, providers: list[LLMProvider], budget: TokenBudget,
                 *, retries_per_provider: int = 1, backoff_base: float = 2.0):
        self.providers = providers
        self.budget = budget
        self.retries_per_provider = retries_per_provider
        self.backoff_base = backoff_base

    def is_configured(self) -> bool:
        return any(p.is_configured() for p in self.providers)

    def configured_providers(self) -> list[str]:
        return [p.name for p in self.providers if p.is_configured()]

    async def complete(self, messages: list[dict], **kwargs) -> Completion:
        errors: list[str] = []
        for provider in self.providers:
            if not provider.is_configured():
                continue
            if self.budget.remaining(provider.name) <= 0:
                errors.append(f"{provider.name}: budget quotidien épuisé")
                continue
            for attempt in range(self.retries_per_provider + 1):
                try:
                    completion = await provider.complete(messages, **kwargs)
                    self.budget.record(provider.name, completion.total_tokens)
                    return completion
                except LLMError as e:
                    errors.append(f"{provider.name}: {e}")
                    log.warning("LLM %s a échoué (tentative %d): %s",
                                provider.name, attempt + 1, e)
                    if not e.retryable or attempt >= self.retries_per_provider:
                        break
                    await asyncio.sleep(self.backoff_base ** attempt)
                except (TimeoutError, Exception) as e:  # noqa: BLE001
                    errors.append(f"{provider.name}: {e}")
                    log.warning("LLM %s erreur inattendue: %s", provider.name, e)
                    break
        raise LLMError("Tous les providers LLM ont échoué: " + " | ".join(errors))


def build_llm(settings: Settings) -> FailoverLLM:
    """Construit la chaîne de providers. Mistral prioritaire par défaut."""
    if settings.llm_provider_override == "mock":
        providers: list[LLMProvider] = [MockProvider()]
    else:
        registry: dict[str, LLMProvider] = {
            "mistral": MistralProvider(settings.mistral_api_key, settings.mistral_model),
            "openai": OpenAIProvider(settings.openai_api_key, settings.openai_model),
        }
        providers = [registry[name] for name in settings.llm_provider_order if name in registry]
        if settings.llm_provider_override and settings.llm_provider_override in registry:
            providers = [registry[settings.llm_provider_override]]
    return FailoverLLM(providers, TokenBudget(settings.llm_daily_token_budget))
