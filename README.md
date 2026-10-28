# Moniteur Produit Autonome

Application web pilotée par un agent IA qui analyse les dépôts de code et les objectifs produit, puis planifie, suit et assigne des tâches. **Le chat est l'unique interface utilisateur.** Voir `specs.md`.

## Architecture

| Composant | Techno | Rôle |
|---|---|---|
| `frontend/` | Nuxt 3 + Tailwind | Chat unique (messages, notifications de tâches en cartes) |
| `ws-gateway/` | Node 22 + TypeScript | WebSocket navigateur ↔ backend, diffusion des événements |
| `backend/` | Spring Boot 3.4 / Java 21 | JWT RS256 + JWKS, API métier, API interne agent |
| `agent/` | Python 3.12 (FastAPI + APScheduler) | Boucle autonome, LLM (Mistral → fallback OpenAI), sous-agents |
| `postgres/` | PostgreSQL 16 + pgvector | Mémoire de l'agent (état, actions, souvenirs, jobs) |
| — | MySQL 8.4 | Données métier (users, projets, objectifs, tâches, bugs, messages) |

Flux chat : `Navigateur → Nuxt (3000) → WS → ws-gateway (4000) → backend (8080) → agent (8000) → ws-gateway → Navigateur`. Le backend n'est jamais exposé directement ; les routes `/internal/**` exigent `X-Internal-Token`.

## Démarrage

```bash
cp .env.example .env
# renseigner : mots de passe, INTERNAL_API_TOKEN, ENCRYPTION_KEY (openssl rand -base64 32)
sh scripts/gen-jwt-key.sh        # → coller la sortie dans JWT_PRIVATE_KEY
# au moins une clé LLM : MISTRAL_API_KEY (prioritaire) et/ou OPENAI_API_KEY (fallback)
#   ou LLM_PROVIDER=mock pour tester sans clé

mkdir -p data/postgres data/mysql data/repos
sudo chown -R 999:999 data/postgres data/mysql && sudo chown -R 1000:1000 data/repos
docker compose up -d --build

docker compose ps                # tous les services doivent être "healthy"
sh scripts/check.sh
```

Login : `ADMIN_USERNAME` / `ADMIN_PASSWORD` (défaut `admin`/`admin` — à changer en prod).

## Endpoints utiles

| Endpoint | Accès |
|---|---|
| `POST /api/v1/auth/login` | public (via Nuxt) |
| `GET /.well-known/jwks.json` | public (backend) |
| `GET/POST /api/v1/projects`, `/objectives`, `/tasks`, `/bugs`, `/conversations` | JWT |
| `POST /api/v1/chat/messages` | JWT (appelé par ws-gateway) |
| `POST /internal/agent/messages` | `X-Internal-Token` (backend → agent) |
| `GET|POST|PATCH /internal/v1/**` | `X-Internal-Token` (agent → backend) |
| `POST /internal/events` | `X-Internal-Token` (→ ws-gateway) |
| `/healthz`, `/readyz` | agent (8000), ws-gateway (4000) |
| `/actuator/health/liveness|readiness` | backend (8080, interne) |
| `GET /api/_health[?deep=1]` | frontend (3000) |

## Conventions agent

- **Boucle** : `Observer → Analyser → Planifier → Agir → Vérifier`, à chaque message + heartbeat (`HEARTBEAT_MINUTES`, défaut 5 min).
- **Providers LLM** : ordre `LLM_PROVIDER_ORDER=mistral,openai` — bascule automatique sur erreur/timeout/quota. Budget journalier `LLM_DAILY_TOKEN_BUDGET` (compteur en mémoire process).
- **Sous-agents** : rôles `planner`, `code_reviewer`, `bug_triager`, `test_writer`, `doc_writer`. Jobs `CREATED→RUNNING→WAITING→DONE|FAILED|TIMEOUT`, polling 60 s, timeout 30 min, 3 retries backoff, résultat vérifié par un second appel LLM avant acceptation.
- **Garde-fou** : les actions destructrices / écritures dépôt passent par `request_confirmation` — l'agent attend un « oui » explicite dans le chat.
- Sans clé LLM ni `LLM_PROVIDER=mock`, l'agent répond en mode dégradé (règles) et `/readyz` renvoie 503 (`llm_providers_ok=false`), conformément à la spec §6.

## Exploitation

```bash
git pull && docker compose up -d --build          # redéploiement, données conservées
# Ne JAMAIS faire `docker compose down -v` (volumes bind-mount data/)

docker exec monitor-postgres pg_dump -U $PG_USER agent_memory > backup/pg_$(date +%F).sql
docker exec monitor-mysql mysqldump -u root -p$MYSQL_ROOT_PASSWORD monitor > backup/mysql_$(date +%F).sql
```

## Vérification statique (dev local, sans lancer la stack)

```bash
# backend (JDK 21 requis — sinon via image Maven)
docker run --rm -v "$PWD/backend":/b -w /b maven:3.9-eclipse-temurin-21 mvn -q verify
# agent (Python 3.12)
docker run --rm -v "$PWD/agent":/a -w /a python:3.12-slim sh -c "pip install -q -r requirements.txt && python -m compileall app && pytest -q && ruff check app tests"
# ws-gateway & frontend (Node ≥ 20 local OK)
cd ws-gateway && npm install && npm run build && npx vitest run
cd ../frontend && npm install && npm run typecheck && npm run build
```
