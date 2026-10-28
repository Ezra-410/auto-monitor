from __future__ import annotations

import json

from .base import Completion, LLMProvider


class MockProvider(LLMProvider):
    """Provider déterministe pour le dev/tests (LLM_PROVIDER=mock)."""

    name = "mock"

    def __init__(self) -> None:
        self.calls: list[list[dict]] = []

    def is_configured(self) -> bool:
        return True

    async def complete(self, messages: list[dict], **kwargs) -> Completion:
        self.calls.append(messages)
        system = (messages[0].get("content") if messages else "") or ""
        if "verifier" in system.lower() or "vérificateur" in system.lower():
            text = json.dumps({"accepted": True, "notes": "Résultat cohérent (mock)."})
        else:
            text = json.dumps({
                "reply": "Réponse simulée de l'agent (provider mock).",
                "actions": [],
                "next_action": None,
                "memories": [],
            })
        return Completion(text=text, provider=self.name, model="mock-1",
                          prompt_tokens=10, completion_tokens=20)
