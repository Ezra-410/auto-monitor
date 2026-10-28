from __future__ import annotations

import asyncio
import logging
import os
from contextlib import asynccontextmanager
from datetime import UTC, datetime, timedelta

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from . import db
from .backend_client import backend
from .config import settings
from .llm import build_llm
from .llm.embeddings import Embedder
from .loop import AgentLoop
from .scheduler import AgentScheduler
from .subagents import SubagentManager
from .tools import ToolBox
from .ws_client import typing as ws_typing

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger(__name__)

llm = build_llm(settings)
subagents = SubagentManager(llm)
tools = ToolBox(subagents, Embedder(settings))
agent_loop = AgentLoop(llm, tools)
agent_scheduler = AgentScheduler(agent_loop, subagents)


@asynccontextmanager
async def lifespan(app: FastAPI):
    os.makedirs(settings.repos_dir, exist_ok=True)
    await db.init_pool()
    agent_scheduler.start()
    yield
    agent_scheduler.stop()
    subagents.shutdown()
    await db.close_pool()


app = FastAPI(title="monitor-agent", lifespan=lifespan)


@app.middleware("http")
async def internal_token_guard(request: Request, call_next):
    """Les routes /internal/** exigent X-Internal-Token (réseau Docker uniquement)."""
    if request.url.path.startswith("/internal/"):
        if request.headers.get("x-internal-token") != settings.internal_token \
                or not settings.internal_token:
            return JSONResponse({"error": "unauthorized"}, status_code=401)
    return await call_next(request)


@app.get("/healthz")
async def healthz():
    return {"status": "ok"}


@app.get("/readyz")
async def readyz():
    pg_ok = False
    try:
        await db.count_pending_actions()
        pg_ok = True
    except Exception:  # noqa: BLE001
        pass

    backend_ok = await backend.ping()
    providers = llm.configured_providers()

    last_hb = agent_scheduler.last_heartbeat_run or await db.last_heartbeat()
    now = datetime.now(UTC)
    heartbeat_fresh = bool(last_hb) and (now - last_hb) < timedelta(minutes=10)

    repos_ok = os.access(settings.repos_dir, os.R_OK | os.W_OK)

    ready = pg_ok and backend_ok and bool(providers) and heartbeat_fresh and repos_ok
    body = {
        "status": "ready" if ready else "not_ready",
        "postgres": pg_ok,
        "backend": backend_ok,
        "llm_providers_ok": bool(providers),
        "llm_providers": providers,
        "last_heartbeat": last_hb.isoformat() if last_hb else None,
        "heartbeat_fresh": heartbeat_fresh,
        "repos_readable": repos_ok,
        "pending_actions": await db.count_pending_actions() if pg_ok else None,
        "active_subagent_jobs": await db.count_active_jobs() if pg_ok else None,
        "failed_jobs_24h": await db.count_failed_jobs_24h() if pg_ok else None,
        "token_budget_remaining": {
            p.name: llm.budget.remaining(p.name) for p in llm.providers if p.is_configured()},
    }
    return JSONResponse(body, status_code=200 if ready else 503)


@app.post("/internal/agent/messages", status_code=202)
async def receive_message(request: Request):
    """Réception d'un message utilisateur relayé par le backend."""
    body = await request.json()
    project_id = int(body["project_id"])
    user_id = int(body["user_id"])
    conversation_id = body.get("conversation_id")
    content = str(body["content"])

    async def handle():
        await ws_typing(project_id, user_id, True)
        try:
            await agent_loop.run_cycle(
                project_id, "user_message",
                user_message=content,
                conversation_id=conversation_id,
                user_id=user_id)
        finally:
            await ws_typing(project_id, user_id, False)

    asyncio.create_task(handle())
    return {"queued": True}


@app.get("/internal/agent/jobs/{job_id}")
async def job_status(job_id: str):
    job = await subagents.check_job(job_id)
    if not job:
        return JSONResponse({"error": "job inconnu"}, status_code=404)
    return job
