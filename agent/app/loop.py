from __future__ import annotations

import asyncio
import logging
from datetime import date
from typing import Any

from . import db, gitops
from .backend_client import backend
from .llm import FailoverLLM, LLMError
from .prompts import PLANNER_SYSTEM, extract_json
from .tools import ToolBox

log = logging.getLogger(__name__)

CONFIRM_WORDS = {"oui", "confirm", "confirme", "ok", "vas-y", "vas y", "yes", "go"}

_locks: dict[int, asyncio.Lock] = {}


class AgentLoop:
    """Boucle autonome : Observer → Analyser → Planifier → Agir → Vérifier (spec §2.1)."""

    def __init__(self, llm: FailoverLLM, tools: ToolBox):
        self.llm = llm
        self.tools = tools

    async def run_cycle(self, project_id: int, trigger: str, *,
                        user_message: str | None = None,
                        conversation_id: int | None = None,
                        user_id: int | None = None) -> dict:
        lock = _locks.setdefault(project_id, asyncio.Lock())
        async with lock:
            try:
                return await self._cycle(project_id, trigger,
                                         user_message=user_message,
                                         conversation_id=conversation_id,
                                         user_id=user_id)
            except Exception as e:  # noqa: BLE001
                log.exception("cycle agent projet %s en échec", project_id)
                await db.upsert_state(project_id, status="IDLE",
                                      current_focus=f"erreur: {e}"[:500])
                return {"ok": False, "error": str(e)}

    async def _cycle(self, project_id: int, trigger: str, *,
                     user_message: str | None,
                     conversation_id: int | None,
                     user_id: int | None) -> dict:
        await db.upsert_state(project_id, status="THINKING",
                              current_focus=f"cycle {trigger}", heartbeat=True)

        # ---------- 1. Observer ----------
        context = await self._observe(project_id)

        # ---------- 2. Analyser ----------
        findings = self._analyze(context)

        # ---------- 3. Vérifier les jobs délégués ----------
        job_updates = await self._verify_subagents(project_id, context)

        # ---------- 4. Planifier ----------
        owner_id = (context.get("project") or {}).get("owner_id")
        effective_user_id = user_id or owner_id
        plan = await self._plan(project_id, context, findings, job_updates,
                                user_message, trigger)

        # ---------- 5. Agir ----------
        reply = await self._act(project_id, plan, context,
                                trigger=trigger,
                                user_id=effective_user_id,
                                conversation_id=conversation_id,
                                user_message=user_message)

        await db.upsert_state(project_id, status="IDLE",
                              current_focus=(plan or {}).get("focus"),
                              next_action=(plan or {}).get("next_action"))
        return {"ok": True, "reply": reply, "findings": findings}

    # -------------------------------------------------------- Observer

    async def _observe(self, project_id: int) -> dict:
        context = {"project": {"id": project_id}, "objectives": [],
                   "tasks": [], "bugs": [], "repositories": [],
                   "repo_status": [], "recent_messages": []}
        try:
            context.update(await backend.get_context(project_id))
        except Exception as e:  # noqa: BLE001
            log.warning("contexte backend projet %s indisponible: %s", project_id, e)
        context["repo_status"] = await gitops.sync_repositories(
            project_id, context.get("repositories", []))
        return context

    # -------------------------------------------------------- Analyser

    def _analyze(self, context: dict) -> list[dict]:
        """Heuristiques de détection : retards, risques, bugs bloquants, dérive."""
        today = date.today()
        findings: list[dict] = []

        for o in context.get("objectives", []):
            due = o.get("due_date")
            status = o.get("status")
            if due and status not in ("DONE", "CANCELLED"):
                due_d = date.fromisoformat(due) if isinstance(due, str) else due
                if due_d < today:
                    findings.append({"kind": "objective_late", "objective_id": o["id"],
                                     "title": o["title"], "due_date": str(due_d),
                                     "days_late": (today - due_d).days})
                elif (due_d - today).days <= 7:
                    open_tasks = [t for t in context.get("tasks", [])
                                  if t.get("objective_id") == o["id"]
                                  and t.get("status") not in ("DONE", "CANCELLED")]
                    if open_tasks:
                        findings.append({"kind": "objective_at_risk", "objective_id": o["id"],
                                         "title": o["title"], "due_date": str(due_d),
                                         "open_tasks": len(open_tasks)})

        for t in context.get("tasks", []):
            due = t.get("due_date")
            if due and t.get("status") not in ("DONE", "CANCELLED"):
                due_d = date.fromisoformat(due) if isinstance(due, str) else due
                if due_d < today:
                    findings.append({"kind": "task_late", "task_id": t["id"],
                                     "title": t["title"], "days_late": (today - due_d).days})

        for b in context.get("bugs", []):
            if b.get("severity") == "CRITICAL" and b.get("status") in ("OPEN", "TRIAGED"):
                findings.append({"kind": "critical_bug", "bug_id": b["id"], "title": b["title"]})
            elif b.get("severity") == "HIGH" and b.get("status") == "OPEN":
                findings.append({"kind": "high_bug_untriaged", "bug_id": b["id"],
                                 "title": b["title"]})

        for repo in context.get("repo_status", []):
            if not repo.get("ok"):
                findings.append({"kind": "repo_unreachable",
                                 "repo_id": repo.get("repo_id"),
                                 "error": repo.get("error", "")[:200]})

        return findings

    # -------------------------------------------------------- Planifier

    async def _plan(self, project_id: int, context: dict, findings: list[dict],
                    job_updates: list[dict], user_message: str | None,
                    trigger: str) -> dict | None:
        memories = await self.tools.read_memory(project_id, query=user_message, limit=10)
        compact = {
            "projet": context.get("project"),
            "objectifs": [self._compact_objective(o) for o in context.get("objectives", [])],
            "taches": [self._compact_task(t) for t in context.get("tasks", [])],
            "bugs": [self._compact_bug(b) for b in context.get("bugs", [])],
            "constats_analyse": findings,
            "jobs_sous_agents": job_updates,
            "depots": context.get("repo_status"),
            "souvenirs": [m["content"] for m in memories],
            "date_du_jour": date.today().isoformat(),
            "declencheur": trigger,
            "message_utilisateur": user_message,
        }

        if not self.llm.is_configured():
            return self._fallback_plan(context, findings, user_message)

        try:
            completion = await self.llm.complete(
                [{"role": "system", "content": PLANNER_SYSTEM},
                 {"role": "user", "content": f"Contexte courant :\n{_json(compact)}"}],
                max_tokens=3000)
            plan = extract_json(completion.text)
            if isinstance(plan, dict):
                return plan
            log.warning("plan LLM non-JSON, fallback. texte=%s", completion.text[:300])
            return {"reply": completion.text[:2000], "actions": []}
        except LLMError as e:
            log.warning("planification LLM impossible: %s", e)
            return self._fallback_plan(context, findings, user_message)

    def _fallback_plan(self, context: dict, findings: list[dict],
                       user_message: str | None) -> dict:
        """Mode dégradé déterministe quand aucun LLM n'est disponible."""
        lines = []
        if not findings:
            lines.append("Tout est sous contrôle : aucun retard ni bug bloquant détecté.")
        else:
            lines.append(f"{len(findings)} point(s) d'attention détecté(s) :")
            for f in findings[:8]:
                lines.append(f"- [{f['kind']}] {f.get('title') or f.get('error') or f}")
        lines.append("(LLM indisponible : réponse générée par règles.)")
        return {"reply": "\n".join(lines), "actions": [],
                "focus": "surveillance dégradée"}

    # -------------------------------------------------------- Agir

    async def _act(self, project_id: int, plan: dict | None, context: dict,
                   *, trigger: str, user_id: int | None,
                   conversation_id: int | None,
                   user_message: str | None) -> str | None:
        plan = plan or {}
        reply_text = (plan.get("reply") or "").strip()
        actions = plan.get("actions") or []

        # Confirmation d'une action en attente ?
        state = await db.get_state(project_id)
        pending = (state or {}).get("next_action") or {}
        if pending.get("type") == "pending_confirm" and user_message:
            if any(w in user_message.lower() for w in CONFIRM_WORDS):
                confirmed = pending.get("params", {})
                result = await self.tools.run_action(project_id, confirmed,
                                                     user_id=user_id,
                                                     conversation_id=conversation_id)
                reply_text = (reply_text or "") + \
                    f"\nAction confirmée exécutée ({confirmed.get('type')}) : {result}"
            else:
                reply_text = (reply_text or "") + "\nAction en attente annulée."
            await db.upsert_state(project_id, next_action=None)

        confirmation_requests = []
        for action in actions[:10]:
            if action.get("type") == "request_confirmation":
                confirmation_requests.append(action)
                continue
            try:
                await self.tools.run_action(project_id, action,
                                            user_id=user_id,
                                            conversation_id=conversation_id)
            except Exception as e:  # noqa: BLE001
                log.warning("action %s en échec: %s", action.get("type"), e)

        if confirmation_requests:
            first = confirmation_requests[0]
            await db.upsert_state(project_id, next_action={
                "type": "pending_confirm", "params": first.get("params", first)})
            reply_text = (reply_text + "\n\n" if reply_text else "") + \
                "⚠️ Confirmation requise : " + \
                (first.get("message") or first.get("title") or "confirmez-vous ?")

        # Souvenirs proposés par le planner
        for mem in (plan.get("memories") or [])[:5]:
            try:
                await self.tools.write_memory(project_id, mem.get("kind", "fact"),
                                              mem.get("content", ""))
            except Exception as e:  # noqa: BLE001
                log.warning("écriture mémoire impossible: %s", e)

        # Poster la réponse dans le chat si pertinent
        should_post = bool(reply_text) and (
            trigger == "user_message"
            or (trigger == "heartbeat" and self._worth_notifying(plan, context)))
        if should_post and user_id:
            await self.tools.post_message(project_id, user_id, reply_text, conversation_id)
        return reply_text or None

    @staticmethod
    def _worth_notifying(plan: dict, context: dict) -> bool:
        """En heartbeat, ne spammer que s'il y a du signal (findings/actions)."""
        return bool(plan.get("actions")) or "attention" in (plan.get("reply") or "").lower()

    # -------------------------------------------------------- Vérifier

    async def _verify_subagents(self, project_id: int, context: dict) -> list[dict]:
        """Contrôle l'avancée des jobs délégués et intègre les résultats acceptés."""
        updates = []
        for job in await db.active_jobs():
            if job["project_id"] != project_id:
                continue
            updates.append({"job_id": job["id"], "role": job["role"],
                            "status": job["status"], "task_id": job.get("task_id")})
        # Jobs récemment terminés : marquer la tâche associée si le résultat est accepté
        async with db.conn() as c:
            cur = await c.execute(
                "SELECT id, task_id, result FROM subagent_jobs "
                "WHERE project_id = %s AND status = 'DONE' AND task_id IS NOT NULL "
                "AND last_checked_at > now() - interval '10 minutes'",
                (project_id,))
            rows = await cur.fetchall()
        for job_id, task_id, result in rows:
            verification = (result or {}).get("verification") or {}
            if verification.get("accepted"):
                try:
                    await self.tools.update_task(task_id, status="DONE")
                    updates.append({"job_id": job_id, "task_id": task_id,
                                    "integrated": True})
                except Exception as e:  # noqa: BLE001
                    log.warning("intégration job %s → tâche %s impossible: %s",
                                job_id, task_id, e)
        return updates

    # -------------------------------------------------------- Compaction

    @staticmethod
    def _compact_objective(o: dict) -> dict:
        return {"id": o.get("id"), "title": o.get("title"), "status": o.get("status"),
                "priority": o.get("priority"), "due_date": o.get("due_date")}

    @staticmethod
    def _compact_task(t: dict) -> dict:
        return {"id": t.get("id"), "title": t.get("title"), "status": t.get("status"),
                "objective_id": t.get("objective_id"), "assignee": t.get("assignee"),
                "due_date": t.get("due_date")}

    @staticmethod
    def _compact_bug(b: dict) -> dict:
        return {"id": b.get("id"), "title": b.get("title"),
                "severity": b.get("severity"), "status": b.get("status")}


def _json(obj: Any) -> str:
    import json
    return json.dumps(obj, ensure_ascii=False, default=str)
