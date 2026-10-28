from __future__ import annotations

import logging
import os
import re
import subprocess
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

import git

from .backend_client import backend
from .config import settings

log = logging.getLogger(__name__)


def _authed_url(url: str, token: str | None) -> str:
    """Injecte un token dans une URL https sans le journaliser."""
    if not token or not url.startswith("http"):
        return url
    parts = urlsplit(url)
    host = parts.hostname or ""
    if "github" in host:
        netloc = f"x-access-token:{token}@{host}"
    else:
        netloc = f"oauth2:{token}@{host}"
    if parts.port:
        netloc += f":{parts.port}"
    return urlunsplit((parts.scheme, netloc, parts.path, parts.query, parts.fragment))


def repo_dir(project_id: int, repo_id: int) -> Path:
    return Path(settings.repos_dir) / str(project_id) / str(repo_id)


async def sync_repositories(project_id: int, repositories: list[dict]) -> list[dict]:
    """Clone ou fetch tous les dépôts d'un projet. Retourne l'état par dépôt."""
    statuses = []
    for repo in repositories:
        repo_id = repo["id"]
        path = repo_dir(project_id, repo_id)
        try:
            cred = await backend.repo_credential(repo_id)
            token = cred.get("token") or None
            branch = cred.get("branch") or repo.get("branch") or "main"
            url = _authed_url(cred.get("url") or repo["url"], token)
            path.parent.mkdir(parents=True, exist_ok=True)
            if (path / ".git").exists():
                r = git.Repo(path)
                r.remotes.origin.fetch()
                statuses.append({"repo_id": repo_id, "ok": True, "path": str(path)})
            else:
                git.Repo.clone_from(url, path, branch=branch, depth=200)
                statuses.append({"repo_id": repo_id, "ok": True, "path": str(path)})
        except Exception as e:  # noqa: BLE001
            safe = re.sub(r"//[^@/]+@", "//***@", str(e))
            log.warning("sync repo %s/%s impossible: %s", project_id, repo_id, safe)
            statuses.append({"repo_id": repo_id, "ok": False, "error": safe})
    return statuses


def read_repo(project_id: int, repo_id: int, path: str, *, max_bytes: int = 40_000) -> str:
    """Lit un fichier du dépôt cloné (chemin relatif, sans échappement)."""
    root = repo_dir(project_id, repo_id).resolve()
    target = (root / path).resolve()
    if not str(target).startswith(str(root)) or not target.is_file():
        raise FileNotFoundError(f"Fichier introuvable dans le dépôt: {path}")
    data = target.read_bytes()[:max_bytes]
    return data.decode("utf-8", errors="replace")


def list_files(project_id: int, repo_id: int, *, limit: int = 500) -> list[str]:
    root = repo_dir(project_id, repo_id)
    if not root.exists():
        return []
    out = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d != ".git"]
        for f in filenames:
            out.append(str(Path(dirpath, f).relative_to(root)))
            if len(out) >= limit:
                return out
    return out


def search_code(project_id: int, repo_id: int, pattern: str, *, max_hits: int = 50) -> list[dict]:
    root = repo_dir(project_id, repo_id)
    if not root.exists():
        return []
    try:
        proc = subprocess.run(
            ["git", "grep", "-n", "-I", "-e", pattern, "--", ":(exclude)*lock*"],
            cwd=root, capture_output=True, text=True, timeout=15)
    except (subprocess.SubprocessError, FileNotFoundError) as e:
        log.warning("git grep impossible: %s", e)
        return []
    hits = []
    for line in proc.stdout.splitlines()[:max_hits]:
        file, _, rest = line.partition(":")
        num, _, content = rest.partition(":")
        hits.append({"file": file, "line": num, "content": content.strip()[:300]})
    return hits


def list_commits(project_id: int, repo_id: int, *, limit: int = 20) -> list[dict]:
    root = repo_dir(project_id, repo_id)
    if not (root / ".git").exists():
        return []
    try:
        r = git.Repo(root)
        commits = []
        for c in r.iter_commits(max_count=limit):
            commits.append({
                "sha": c.hexsha[:10],
                "message": (c.message or "").strip().splitlines()[0][:200],
                "author": str(c.author),
                "date": c.committed_datetime.isoformat(),
            })
        return commits
    except Exception as e:  # noqa: BLE001
        log.warning("list_commits impossible: %s", e)
        return []


async def list_prs(project_id: int, repo_id: int) -> list[dict]:
    """Liste les PRs ouvertes via l'API du remote (GitHub/GitLab) si le token le permet."""
    try:
        cred = await backend.repo_credential(repo_id)
    except Exception as e:  # noqa: BLE001
        log.warning("credential repo %s inaccessible: %s", repo_id, e)
        return []
    url = cred.get("url", "")
    token = cred.get("token") or ""
    m = re.match(r"https?://github\.com/([^/]+)/([^/.]+)", url)
    if m and token:
        import httpx
        owner, name = m.group(1), m.group(2)
        try:
            async with httpx.AsyncClient(timeout=15) as client:
                resp = await client.get(
                    f"https://api.github.com/repos/{owner}/{name}/pulls",
                    params={"state": "open", "per_page": 20},
                    headers={"Authorization": f"Bearer {token}",
                             "Accept": "application/vnd.github+json"})
            if resp.status_code == 200:
                return [{"number": p["number"], "title": p["title"],
                         "user": p["user"]["login"], "url": p["html_url"]}
                        for p in resp.json()]
        except Exception as e:  # noqa: BLE001
            log.warning("list_prs github impossible: %s", e)
    return []
