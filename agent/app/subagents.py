from __future__ import annotations

import asyncio
import logging
from datetime import UTC, datetime, timedelta
from typing import Any

from . import db
from .config import settings
from .llm import FailoverLLM, LLMError
from .prompts import ROLE_SYSTEMS, VERIFIER_SYSTEM, extract_json

log = logging.getLogger(__name__)


class SubagentManager:
    """Gère les jobs délégués aux sous-agents LLM.
    Cycle de vie : CREATED → RUNNING → WAITING → DONE | FAILED | TIMEOUT.
    Le poller (60 s) relance les jobs en backoff et applique le timeout."""

    def __init__(self, llm: FailoverLLM):
        self.llm = llm
        self._running: dict[str, asyncio.Task] = {}

    async def delegate(self, project_id: int, role: str, prompt: str,
                       task_id: int | None = None) -> str:
        if role not in ROLE_SYSTEMS:
            raise ValueError(f"Rôle de sous-agent inconnu: {role}")
        provider = (self.llm.configured_providers() or ["none"])[0]
        job_id = await db.create_job(project_id, role, provider, prompt, task_id)
        self._launch(job_id)
        return job_id

    def _launch(self, job_id: str) -> None:
        if job_id in self._running and not self._running[job_id].done():
            return
        self._running[job_id] = asyncio.create_task(self._execute(job_id))

    async def _execute(self, job_id: str) -> None:
        job = await db.get_job(job_id)
        if not job or job["status"] in ("DONE", "FAILED", "TIMEOUT"):
            return
        await db.update_job(job_id, status="RUNNING",
                            started_at=datetime.now(UTC),
                            last_checked_at=datetime.now(UTC))
        messages = [
            {"role": "system", "content": ROLE_SYSTEMS.get(job["role"], ROLE_SYSTEMS["planner"])},
            {"role": "user", "content": job["prompt"]},
        ]
        try:
            completion = await asyncio.wait_for(
                self.llm.complete(messages, max_tokens=4000),
                timeout=settings.subagent_timeout_minutes * 60)
        except (TimeoutError, LLMError) as e:
            await self._fail_or_retry(job, str(e))
            return
        except Exception as e:  # noqa: BLE001
            await self._fail_or_retry(job, f"erreur inattendue: {e}")
            return

        parsed = extract_json(completion.text)
        result: dict[str, Any] = {
            "text": completion.text[:20000],
            "parsed": parsed,
            "provider": completion.provider,
            "model": completion.model,
        }
        # Vérification du résultat avant acceptation
        verification = await self._verify(job, completion.text)
        result["verification"] = verification
        status = "DONE" if verification.get("accepted", True) else "FAILED"
        if status == "FAILED":
            result["rejected"] = True
        await db.update_job(job_id, status=status, result=result,
                            last_checked_at=datetime.now(UTC))
        self._running.pop(job_id, None)

    async def _verify(self, job: dict, result_text: str) -> dict:
        """Second appel LLM : le résultat est-il cohérent avec l'objectif ?"""
        messages = [
            {"role": "system", "content": VERIFIER_SYSTEM},
            {"role": "user", "content":
                f"Rôle du sous-agent : {job['role']}\n"
                f"Mission : {job['prompt'][:2000]}\n"
                f"Résultat produit :\n{result_text[:6000]}"},
        ]
        try:
            completion = await self.llm.complete(messages, max_tokens=500)
            parsed = extract_json(completion.text)
            if isinstance(parsed, dict) and "accepted" in parsed:
                return {"accepted": bool(parsed["accepted"]),
                        "notes": str(parsed.get("notes", ""))[:500]}
            return {"accepted": True, "notes": "vérification non concluante, accepté par défaut"}
        except Exception as e:  # noqa: BLE001
            log.warning("vérification job %s impossible: %s", job["id"], e)
            return {"accepted": False, "notes": f"vérification impossible: {e}"}

    async def _fail_or_retry(self, job: dict, error: str) -> None:
        attempts = int(job.get("attempts") or 0) + 1
        if attempts < settings.subagent_max_attempts:
            # WAITING + backoff exponentiel ; le poller relancera
            await db.update_job(job["id"], status="WAITING", attempts=attempts,
                                result={"last_error": error, "retry_after_seconds": 60 * attempts},
                                last_checked_at=datetime.now(UTC))
        else:
            await db.update_job(job["id"], status="FAILED", attempts=attempts,
                                result={"error": error},
                                last_checked_at=datetime.now(UTC))
        self._running.pop(job["id"], None)

    async def poll(self) -> None:
        """Interroge les jobs actifs : timeout, retries en backoff, relance."""
        now = datetime.now(UTC)
        for job in await db.active_jobs():
            job_id = job["id"]
            await db.mark_job_checked(job_id)
            status = job["status"]
            started = job.get("started_at")
            if started is not None and started.tzinfo is None:
                started = started.replace(tzinfo=UTC)

            if status == "RUNNING" and started and \
                    now - started > timedelta(minutes=settings.subagent_timeout_minutes):
                task = self._running.pop(job_id, None)
                if task:
                    task.cancel()
                await db.update_job(job_id, status="TIMEOUT",
                                    result={"error": "timeout"},
                                    last_checked_at=now)
                continue

            if status in ("CREATED", "WAITING") and job_id not in self._running:
                attempts = int(job.get("attempts") or 0)
                backoff = timedelta(seconds=60 * attempts) if status == "WAITING" else timedelta(0)
                last = job.get("last_checked_at") or now
                if last.tzinfo is None:
                    last = last.replace(tzinfo=UTC)
                if now - last >= backoff:
                    self._launch(job_id)

    async def check_job(self, job_id: str) -> dict | None:
        await db.mark_job_checked(job_id)
        return await db.get_job(job_id)

    def shutdown(self) -> None:
        for task in self._running.values():
            task.cancel()
        self._running.clear()
