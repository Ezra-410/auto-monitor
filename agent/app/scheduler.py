from __future__ import annotations

import asyncio
import logging
from datetime import UTC, datetime

from apscheduler.schedulers.asyncio import AsyncIOScheduler

from . import db
from .backend_client import backend
from .config import settings
from .loop import AgentLoop
from .subagents import SubagentManager

log = logging.getLogger(__name__)


class AgentScheduler:
    """Heartbeat périodique (défaut 5 min) + polling des jobs sous-agents (60 s)."""

    def __init__(self, loop: AgentLoop, subagents: SubagentManager):
        self.loop = loop
        self.subagents = subagents
        self.scheduler = AsyncIOScheduler()
        self.last_heartbeat_run: datetime | None = None

    async def heartbeat(self) -> None:
        """Un cycle complet par projet suivi."""
        self.last_heartbeat_run = datetime.now(UTC)
        try:
            projects = await backend.list_projects()
        except Exception as e:  # noqa: BLE001
            log.warning("heartbeat: liste des projets indisponible: %s", e)
            return
        for p in projects:
            try:
                await self.loop.run_cycle(p["id"], "heartbeat")
            except Exception as e:  # noqa: BLE001
                log.warning("heartbeat projet %s en échec: %s", p.get("id"), e)

    async def poll_jobs(self) -> None:
        try:
            await self.subagents.poll()
        except Exception as e:  # noqa: BLE001
            log.warning("poll jobs sous-agents en échec: %s", e)

    async def drain_actions(self) -> None:
        """Exécute les actions planifiées arrivées à échéance."""
        try:
            for action in await db.pending_actions():
                try:
                    await self.loop.run_cycle(action["project_id"], "scheduled_action")
                    await db.complete_action(action["id"], {"cycle": "ran"})
                except Exception as e:  # noqa: BLE001
                    await db.complete_action(action["id"], {"error": str(e)}, ok=False)
        except Exception as e:  # noqa: BLE001
            log.warning("drain actions en échec: %s", e)

    def start(self) -> None:
        self.scheduler.add_job(
            lambda: asyncio.create_task(self.heartbeat()),
            "interval", minutes=settings.heartbeat_minutes, id="heartbeat",
            next_run_time=datetime.now(UTC))
        self.scheduler.add_job(
            lambda: asyncio.create_task(self.poll_jobs()),
            "interval", seconds=settings.subagent_poll_seconds, id="job-poller")
        self.scheduler.add_job(
            lambda: asyncio.create_task(self.drain_actions()),
            "interval", seconds=30, id="action-drain")
        self.scheduler.start()
        log.info("scheduler démarré (heartbeat=%smin, poller=%ss)",
                 settings.heartbeat_minutes, settings.subagent_poll_seconds)

    def stop(self) -> None:
        self.scheduler.shutdown(wait=False)
