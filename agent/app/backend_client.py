from __future__ import annotations

import logging
from typing import Any

import httpx

from .config import settings

log = logging.getLogger(__name__)


class BackendClient:
    """Client de l'API interne du backend Java (/internal/v1/**)."""

    def __init__(self) -> None:
        self._base = settings.backend_url
        self._headers = {"X-Internal-Token": settings.internal_token}

    async def _request(self, method: str, path: str, **kwargs) -> Any:
        async with httpx.AsyncClient(base_url=self._base, timeout=15,
                                     headers=self._headers) as client:
            resp = await client.request(method, path, **kwargs)
            resp.raise_for_status()
            return resp.json()

    async def ping(self) -> bool:
        try:
            async with httpx.AsyncClient(timeout=5) as client:
                resp = await client.get(f"{self._base}/actuator/health/liveness")
                return resp.status_code == 200
        except httpx.HTTPError:
            return False

    # --- projets / contexte ---

    async def list_projects(self) -> list[dict]:
        return await self._request("GET", "/internal/v1/projects")

    async def get_context(self, project_id: int) -> dict:
        return await self._request("GET", f"/internal/v1/projects/{project_id}/context")

    async def repo_credential(self, repo_id: int) -> dict:
        return await self._request("GET", f"/internal/v1/repositories/{repo_id}/credential")

    # --- objectifs ---

    async def get_objectives(self, project_id: int) -> list[dict]:
        return await self._request("GET", "/internal/v1/objectives",
                                   params={"project_id": project_id})

    async def create_objective(self, project_id: int, **fields) -> dict:
        return await self._request("POST", "/internal/v1/objectives",
                                   json={"project_id": project_id, **fields})

    async def update_objective(self, objective_id: int, **fields) -> dict:
        return await self._request("PATCH", f"/internal/v1/objectives/{objective_id}", json=fields)

    # --- tâches ---

    async def create_task(self, project_id: int, **fields) -> dict:
        return await self._request("POST", "/internal/v1/tasks",
                                   json={"project_id": project_id, "created_by": "AGENT", **fields})

    async def update_task(self, task_id: int, **fields) -> dict:
        return await self._request("PATCH", f"/internal/v1/tasks/{task_id}", json=fields)

    async def list_tasks(self, project_id: int) -> list[dict]:
        return await self._request("GET", "/internal/v1/tasks", params={"project_id": project_id})

    # --- bugs ---

    async def list_bugs(self, project_id: int) -> list[dict]:
        return await self._request("GET", "/internal/v1/bugs", params={"project_id": project_id})

    async def update_bug(self, bug_id: int, **fields) -> dict:
        return await self._request("PATCH", f"/internal/v1/bugs/{bug_id}", json=fields)

    # --- messages ---

    async def post_agent_message(self, project_id: int, user_id: int, content: str,
                                 conversation_id: int | None = None) -> dict:
        body: dict[str, Any] = {"project_id": project_id, "user_id": user_id, "content": content}
        if conversation_id is not None:
            body["conversation_id"] = conversation_id
        return await self._request("POST", "/internal/v1/chat/messages", json=body)

    async def recent_messages(self, conversation_id: int) -> list[dict]:
        return await self._request("GET", f"/internal/v1/conversations/{conversation_id}/messages")


backend = BackendClient()
