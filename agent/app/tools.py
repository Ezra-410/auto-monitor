from __future__ import annotations

import logging
from typing import Any

from . import db, gitops
from .backend_client import backend
from .llm.embeddings import Embedder
from .subagents import SubagentManager
from .ws_client import notify_user as ws_notify

log = logging.getLogger(__name__)


class ToolBox:
    """Implémente les outils de l'agent (spec §2.2) appelés depuis la boucle et les actions LLM."""

    def __init__(self, subagents: SubagentManager, embedder: Embedder):
        self.subagents = subagents
        self.embedder = embedder

    # --- dépôt ---

    async def read_repo(self, project_id: int, repo_id: int, path: str) -> str:
        return gitops.read_repo(project_id, repo_id, path)

    async def search_code(self, project_id: int, repo_id: int, pattern: str) -> list[dict]:
        return gitops.search_code(project_id, repo_id, pattern)

    async def list_commits(self, project_id: int, repo_id: int, limit: int = 20) -> list[dict]:
        return gitops.list_commits(project_id, repo_id, limit=limit)

    async def list_prs(self, project_id: int, repo_id: int) -> list[dict]:
        return await gitops.list_prs(project_id, repo_id)

    async def list_files(self, project_id: int, repo_id: int) -> list[str]:
        return gitops.list_files(project_id, repo_id)

    # --- métier ---

    async def get_objectives(self, project_id: int) -> list[dict]:
        return await backend.get_objectives(project_id)

    async def create_task(self, project_id: int, title: str,
                          objective_id: int | None = None,
                          assignee_id: int | None = None,
                          due_date: str | None = None,
                          description: str | None = None,
                          status: str | None = None) -> dict:
        return await backend.create_task(
            project_id, title=title, objective_id=objective_id,
            assignee_id=assignee_id, due_date=due_date,
            description=description, status=status)

    async def assign_task(self, task_id: int, assignee_id: int) -> dict:
        return await backend.update_task(task_id, assignee_id=assignee_id)

    async def update_task(self, task_id: int, **fields) -> dict:
        return await backend.update_task(task_id, **fields)

    async def update_objective(self, objective_id: int, **fields) -> dict:
        return await backend.update_objective(objective_id, **fields)

    async def list_bugs(self, project_id: int) -> list[dict]:
        return await backend.list_bugs(project_id)

    async def update_bug(self, bug_id: int, **fields) -> dict:
        return await backend.update_bug(bug_id, **fields)

    # --- délégation ---

    async def delegate_to_subagent(self, project_id: int, role: str, prompt: str,
                                   task_id: int | None = None) -> str:
        return await self.subagents.delegate(project_id, role, prompt, task_id)

    async def check_subagent_job(self, job_id: str) -> dict | None:
        return await self.subagents.check_job(job_id)

    # --- utilisateur ---

    async def notify_user(self, project_id: int, user_id: int, message: str) -> None:
        await ws_notify(project_id, user_id, message)

    async def post_message(self, project_id: int, user_id: int, content: str,
                           conversation_id: int | None = None) -> dict:
        return await backend.post_agent_message(project_id, user_id, content, conversation_id)

    # --- mémoire ---

    async def write_memory(self, project_id: int, kind: str, content: str) -> int:
        embedding = await self.embedder.embed(content)
        return await db.write_memory(project_id, kind, content, embedding)

    async def read_memory(self, project_id: int, query: str | None = None,
                          limit: int = 10) -> list[dict]:
        query_embedding = await self.embedder.embed(query) if query else None
        return await db.read_memory(project_id, query_embedding=query_embedding,
                                    query_text=query, limit=limit)

    async def run_action(self, project_id: int, action: dict, *, user_id: int | None = None,
                       conversation_id: int | None = None) -> dict[str, Any]:
        """Exécute une action produite par le planner."""
        atype = action.get("type")
        if atype == "create_task":
            result = await self.create_task(
                project_id,
                title=action.get("title", "Tâche sans titre"),
                objective_id=action.get("objective_id"),
                assignee_id=action.get("assignee_id"),
                due_date=action.get("due_date"),
                description=action.get("description"))
            return {"ok": True, "task_id": result.get("id")}
        if atype == "update_task":
            fields = {k: v for k, v in action.items()
                      if k in ("status", "title", "description", "due_date", "objective_id") and v is not None}
            await self.update_task(action["task_id"], **fields)
            return {"ok": True}
        if atype == "assign_task":
            await self.assign_task(action["task_id"], action["assignee_id"])
            return {"ok": True}
        if atype == "update_objective":
            fields = {k: v for k, v in action.items()
                      if k in ("status", "title", "description", "priority", "due_date") and v is not None}
            await self.update_objective(action["objective_id"], **fields)
            return {"ok": True}
        if atype == "update_bug":
            fields = {k: v for k, v in action.items()
                      if k in ("status", "severity", "title", "description", "task_id") and v is not None}
            await self.update_bug(action["bug_id"], **fields)
            return {"ok": True}
        if atype == "delegate":
            job_id = await self.delegate_to_subagent(
                project_id, action.get("role", "planner"),
                action.get("prompt", ""), action.get("task_id"))
            return {"ok": True, "job_id": job_id}
        if atype == "write_memory":
            mem_id = await self.write_memory(project_id, action.get("kind", "fact"),
                                             action.get("content", ""))
            return {"ok": True, "memory_id": mem_id}
        if atype == "notify_user" and user_id:
            await self.notify_user(project_id, user_id, action.get("message", ""))
            return {"ok": True}
        if atype == "request_confirmation":
            # Jamais exécuté : l'action est retranscrite dans le chat pour confirmation explicite
            return {"ok": True, "confirmation_requested": True}
        return {"ok": False, "error": f"type d'action inconnu: {atype}"}
