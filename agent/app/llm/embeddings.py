from __future__ import annotations

import logging

import httpx

from ..config import Settings

log = logging.getLogger(__name__)


class Embedder:
    """Embeddings OpenAI text-embedding-3-small (1536 dims, conforme vector(1536)).
    Retourne None si la clé est absente — la recherche mémoire bascule alors en texte plein."""

    def __init__(self, settings: Settings):
        self.api_key = settings.openai_api_key
        self.model = settings.openai_embedding_model

    @property
    def available(self) -> bool:
        return bool(self.api_key)

    async def embed(self, text: str) -> list[float] | None:
        if not self.available:
            return None
        try:
            async with httpx.AsyncClient(timeout=30) as client:
                resp = await client.post(
                    "https://api.openai.com/v1/embeddings",
                    headers={"Authorization": f"Bearer {self.api_key}"},
                    json={"model": self.model, "input": text[:8000]},
                )
            if resp.status_code != 200:
                log.warning("embeddings HTTP %s", resp.status_code)
                return None
            return resp.json()["data"][0]["embedding"]
        except Exception as e:  # noqa: BLE001
            log.warning("embeddings indisponibles: %s", e)
            return None
