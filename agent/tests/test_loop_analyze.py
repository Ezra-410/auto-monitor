from datetime import date, timedelta

from app.loop import AgentLoop


def _context(objectives=None, tasks=None, bugs=None, repo_status=None):
    return {
        "project": {"id": 1, "name": "P"},
        "objectives": objectives or [],
        "tasks": tasks or [],
        "bugs": bugs or [],
        "repo_status": repo_status or [],
    }


def test_analyze_detecte_objectif_en_retard():
    yesterday = (date.today() - timedelta(days=1)).isoformat()
    ctx = _context(objectives=[{"id": 1, "title": "Obj", "status": "IN_PROGRESS",
                              "due_date": yesterday}])
    findings = AgentLoop._analyze(None, ctx)
    assert any(f["kind"] == "objective_late" for f in findings)


def test_analyze_objectif_fait_ignore():
    yesterday = (date.today() - timedelta(days=1)).isoformat()
    ctx = _context(objectives=[{"id": 1, "title": "Obj", "status": "DONE",
                              "due_date": yesterday}])
    findings = AgentLoop._analyze(None, ctx)
    assert not any(f["kind"] == "objective_late" for f in findings)


def test_analyze_detecte_bug_critique_ouvert():
    ctx = _context(bugs=[{"id": 9, "title": "Crash", "severity": "CRITICAL",
                          "status": "OPEN"}])
    findings = AgentLoop._analyze(None, ctx)
    assert any(f["kind"] == "critical_bug" for f in findings)


def test_analyze_detecte_objectif_a_risque():
    soon = (date.today() + timedelta(days=3)).isoformat()
    ctx = _context(
        objectives=[{"id": 1, "title": "Obj", "status": "IN_PROGRESS", "due_date": soon}],
        tasks=[{"id": 5, "objective_id": 1, "status": "TODO", "title": "T"}])
    findings = AgentLoop._analyze(None, ctx)
    assert any(f["kind"] == "objective_at_risk" for f in findings)


def test_analyze_detecte_depot_injoignable():
    ctx = _context(repo_status=[{"repo_id": 3, "ok": False, "error": "auth failed"}])
    findings = AgentLoop._analyze(None, ctx)
    assert any(f["kind"] == "repo_unreachable" for f in findings)
