from __future__ import annotations

from .openai_compat import OpenAICompatibleProvider


class MistralProvider(OpenAICompatibleProvider):
    name = "mistral"
    base_url = "https://api.mistral.ai/v1"

    def __init__(self, api_key: str, model: str = "mistral-large-latest"):
        self.api_key = api_key
        self.default_model = model
