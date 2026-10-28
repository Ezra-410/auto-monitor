from __future__ import annotations

import logging
from typing import Any

import httpx

from .config import settings

log = logging.getLogger(__name__)


async def publish_event(event_type: str, *, project_id: int | None = None,
                        user_id: int | None = None, payload: Any = None) -> bool:
    """POST /internal/events sur le ws-gateway (best-effort)."""
    body: dict[str, Any] = {"type": event_type}
    if project_id is not None:
        body["project_id"] = project_id
    if user_id is not None:
        body["user_id"] = user_id
    if payload is not None:
        body["payload"] = payload
    try:
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.post(
                f"{settings.ws_url}/internal/events",
                headers={"X-Internal-Token": settings.internal_token},
                json=body,
            )
            return resp.status_code < 300
    except httpx.HTTPError as e:
        log.warning("ws-gateway inaccessible (%s): %s", event_type, e)
        return False


async def typing(project_id: int, user_id: int, on: bool = True) -> None:
    await publish_event("agent.typing", project_id=project_id, user_id=user_id,
                        payload={"typing": on})


async def notify_user(project_id: int, user_id: int, message: str) -> None:
    await publish_event("notification", project_id=project_id, user_id=user_id,
                        payload={"message": message})
