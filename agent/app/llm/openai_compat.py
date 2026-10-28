from __future__ import annotations

import httpx

from .base import Completion, LLMError, LLMProvider


class OpenAICompatibleProvider(LLMProvider):
    """Base pour les APIs compatibles OpenAI /v1/chat/completions (OpenAI, Mistral…)."""

    base_url: str = ""
    api_key: str = ""
    default_model: str = ""

    def is_configured(self) -> bool:
        return bool(self.api_key)

    async def complete(
        self,
        messages: list[dict],
        *,
        model: str | None = None,
        max_tokens: int = 2000,
        temperature: float = 0.2,
        timeout: float = 120.0,
    ) -> Completion:
        if not self.is_configured():
            raise LLMError(f"{self.name}: clé API absente", retryable=False, provider=self.name)
        payload = {
            "model": model or self.default_model,
            "messages": messages,
            "max_tokens": max_tokens,
            "temperature": temperature,
        }
        try:
            async with httpx.AsyncClient(timeout=timeout) as client:
                resp = await client.post(
                    f"{self.base_url}/chat/completions",
                    headers={"Authorization": f"Bearer {self.api_key}"},
                    json=payload,
                )
        except httpx.HTTPError as e:
            raise LLMError(f"{self.name}: erreur réseau: {e}", provider=self.name) from e

        if resp.status_code == 401 or resp.status_code == 403:
            raise LLMError(f"{self.name}: authentification refusée ({resp.status_code})",
                           retryable=False, provider=self.name)
        if resp.status_code == 429:
            raise LLMError(f"{self.name}: quota dépassé (429)", provider=self.name)
        if resp.status_code >= 400:
            raise LLMError(
                f"{self.name}: HTTP {resp.status_code}: {resp.text[:200]}",
                retryable=resp.status_code >= 500,
                provider=self.name,
            )

        data = resp.json()
        try:
            text = data["choices"][0]["message"]["content"] or ""
        except (KeyError, IndexError, TypeError) as e:
            raise LLMError(f"{self.name}: réponse malformée", provider=self.name) from e
        usage = data.get("usage") or {}
        return Completion(
            text=text,
            provider=self.name,
            model=data.get("model", payload["model"]),
            prompt_tokens=int(usage.get("prompt_tokens") or 0),
            completion_tokens=int(usage.get("completion_tokens") or 0),
            raw=data,
        )
