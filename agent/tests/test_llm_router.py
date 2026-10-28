import pytest

from app.llm.base import Completion, LLMError, LLMProvider
from app.llm.router import FailoverLLM, TokenBudget


class StubProvider(LLMProvider):
    def __init__(self, name, *, configured=True, fail=False, non_retryable=False):
        self.name = name
        self._configured = configured
        self._fail = fail
        self._non_retryable = non_retryable
        self.attempts = 0

    def is_configured(self):
        return self._configured

    async def complete(self, messages, **kwargs):
        self.attempts += 1
        if self._fail:
            raise LLMError(f"{self.name} down", retryable=not self._non_retryable,
                           provider=self.name)
        return Completion(text="ok", provider=self.name, model="m",
                          prompt_tokens=5, completion_tokens=5)


@pytest.mark.asyncio
async def test_failover_prefere_le_premier_provider():
    mistral = StubProvider("mistral")
    openai = StubProvider("openai")
    llm = FailoverLLM([mistral, openai], TokenBudget(1000))
    out = await llm.complete([{"role": "user", "content": "hi"}])
    assert out.provider == "mistral"
    assert openai.attempts == 0


@pytest.mark.asyncio
async def test_failover_bascule_sur_le_second():
    mistral = StubProvider("mistral", fail=True)
    openai = StubProvider("openai")
    llm = FailoverLLM([mistral, openai], TokenBudget(1000))
    out = await llm.complete([{"role": "user", "content": "hi"}])
    assert out.provider == "openai"
    assert mistral.attempts >= 1


@pytest.mark.asyncio
async def test_failover_saute_provider_non_configure():
    mistral = StubProvider("mistral", configured=False)
    openai = StubProvider("openai")
    llm = FailoverLLM([mistral, openai], TokenBudget(1000))
    out = await llm.complete([{"role": "user", "content": "hi"}])
    assert out.provider == "openai"
    assert mistral.attempts == 0


@pytest.mark.asyncio
async def test_budget_epuise_fait_basculer():
    mistral = StubProvider("mistral")
    openai = StubProvider("openai")
    budget = TokenBudget(5)
    llm = FailoverLLM([mistral, openai], budget)
    await llm.complete([{"role": "user", "content": "hi"}])  # consomme 10 > 5
    out = await llm.complete([{"role": "user", "content": "hi"}])
    assert out.provider == "openai"


@pytest.mark.asyncio
async def test_tous_en_echec_leve_llmerror():
    llm = FailoverLLM([StubProvider("mistral", fail=True, non_retryable=True),
                       StubProvider("openai", fail=True, non_retryable=True)],
                      TokenBudget(1000))
    with pytest.raises(LLMError):
        await llm.complete([{"role": "user", "content": "hi"}])


def test_token_budget_comptabilise():
    budget = TokenBudget(100)
    budget.record("mistral", 40)
    assert budget.remaining("mistral") == 60
    assert budget.usage("mistral") == 40
