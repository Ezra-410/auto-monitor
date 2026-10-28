# Moniteur Produit Autonome — Spécification technique

> Application web pilotée par un agent IA autonome qui analyse les dépôts de code et les objectifs produit, puis planifie, suit et assigne des tâches pour garantir l'atteinte des objectifs. **Le chat est l'unique interface utilisateur.**

---

## 1. Vue d'ensemble

### 1.1 Objectif
L'agent s'assure que l'utilisateur atteint ses objectifs produit. Il s'appuie sur :
- les dépôts Git fournis (code actuel, historique, branches, PR) ;
- les objectifs (accomplis, en retard, à venir), jalons et délais ;
- les bugs signalés et les priorités (explicites ou déduites) ;
- toute information fournie dans le chat ou déduite du contexte.

### 1.2 Responsabilités par composant

| Composant | Techno | Rôle |
|---|---|---|
| `frontend` | Nuxt 3 | Interface de chat unique (messages, streaming, notifications de tâches) |
| `ws-gateway` | Node.js | WebSockets entre frontend et backend, diffusion temps réel des événements |
| `backend` | Java Spring Boot | Sécurité (JWT, rôles), accès, API métier (projets, dépôts, objectifs, tâches, bugs, conversations) |
| `agent-worker` | Python (FastAPI + APScheduler) | Cerveau de l'agent, batch processing, boucle autonome, appels aux sous-agents LLM |
| `postgres` | PostgreSQL 16 (+ pgvector) | **Mémoire de l'agent** : état courant, prochaine action, souvenirs, jobs sous-agents |
| `mysql` | MySQL 8 | **Données relationnelles** métier : utilisateurs, projets, objectifs, tâches, bugs, messages |

### 1.3 Flux principal d'un message de chat

```
Utilisateur → Nuxt → (WS) ws-gateway → (HTTP) backend [auth + persistance MySQL]
  → (HTTP interne) agent-worker [raisonne, agit, consulte la mémoire PostgreSQL]
  → (HTTP interne) ws-gateway /internal/events → (WS) Nuxt → Utilisateur
```

Le navigateur ne parle qu'à **Nuxt (3000)** et **ws-gateway (4000)**. Nuxt relaie les appels REST vers le backend via son serveur Nitro (`routeRules` proxy), le backend n'est donc jamais exposé directement.

---

## 2. Fonctionnement de l'agent

### 2.1 Boucle autonome (dans `agent-worker`)
Cycle exécuté à chaque message utilisateur **et** toutes les 5 minutes (heartbeat) :

1. **Observer** : lire l'état mémoire (PostgreSQL), synchroniser les dépôts (`git fetch`), récupérer objectifs/tâches/bugs via l'API interne du backend.
2. **Analyser** : détecter retards, risques de délai, bugs bloquants, dérive par rapport aux priorités.
3. **Planifier** : produire/mettre à jour le plan (objectifs → tâches → échéances) et choisir la **prochaine action**.
4. **Agir** : répondre dans le chat, créer/assigner des tâches (via backend), déléguer à un sous-agent, relancer l'utilisateur.
5. **Vérifier** : contrôler l'avancée des jobs délégués et des tâches utilisateur ; consigner le résultat en mémoire.

### 2.2 Outils disponibles pour l'agent
`read_repo`, `search_code`, `list_commits/prs`, `get_objectives`, `create_task`, `assign_task`, `update_task`, `list_bugs`, `delegate_to_subagent`, `check_subagent_job`, `notify_user`, `write_memory`, `read_memory`.

### 2.3 Sous-agents spécialisés
L'agent délègue à des agents spécialisés via les API **Anthropic**, **OpenAI** ou **Google Gemini** (couche d'abstraction `LLMProvider` côté Python). Exemples de rôles : `planner`, `code_reviewer`, `bug_triager`, `test_writer`, `doc_writer`.

Cycle de vie d'un job : `CREATED → RUNNING → WAITING → DONE | FAILED | TIMEOUT`.
- Le worker **interroge** chaque job actif toutes les 60 s (polling) et applique : timeout configurable (défaut 30 min), 3 retries avec backoff, bascule possible vers un autre fournisseur.
- Un résultat n'est jamais accepté tel quel : l'agent le **vérifie** (cohérence avec le code et l'objectif) avant de marquer la tâche comme faite.

### 2.4 Garde-fous
- Toute action destructrice ou écrivant sur un dépôt exige une **confirmation explicite** de l'utilisateur dans le chat.
- Clés API et tokens Git : variables d'environnement / chiffrés en base, jamais renvoyés au frontend ni loggés.
- Budget : limite quotidienne de tokens par fournisseur (`LLM_DAILY_TOKEN_BUDGET`).

---

## 3. Modèle de données

### 3.1 PostgreSQL — mémoire de l'agent (`agent_memory`)

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- État courant : une ligne par projet suivi
CREATE TABLE agent_state (
  project_id      BIGINT PRIMARY KEY,
  status          TEXT NOT NULL,            -- IDLE | THINKING | WAITING_USER | WAITING_SUBAGENT
  current_focus   TEXT,                     -- ce sur quoi l'agent travaille maintenant
  next_action     JSONB,                    -- {type, params, due_at}
  heartbeat_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- File d'actions planifiées
CREATE TABLE agent_actions (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL,
  type TEXT NOT NULL, payload JSONB, status TEXT NOT NULL DEFAULT 'PENDING',
  scheduled_at TIMESTAMPTZ NOT NULL DEFAULT now(), result JSONB, created_at TIMESTAMPTZ DEFAULT now()
);

-- Souvenirs (faits, décisions, préférences) avec recherche sémantique
CREATE TABLE agent_memory (
  id BIGSERIAL PRIMARY KEY, project_id BIGINT NOT NULL,
  kind TEXT NOT NULL,                       -- fact | decision | preference | summary
  content TEXT NOT NULL, embedding vector(1536), created_at TIMESTAMPTZ DEFAULT now()
);

-- Jobs délégués aux sous-agents
CREATE TABLE subagent_jobs (
  id UUID PRIMARY KEY, project_id BIGINT NOT NULL, role TEXT NOT NULL,
  provider TEXT NOT NULL,                   -- anthropic | openai | gemini
  task_id BIGINT, prompt TEXT, status TEXT NOT NULL, result JSONB,
  attempts INT DEFAULT 0, started_at TIMESTAMPTZ, last_checked_at TIMESTAMPTZ
);
```

### 3.2 MySQL — données relationnelles (`monitor`)
Tables principales (migrations **Flyway** côté Java) : `users`, `roles`, `projects`, `repositories` (url, branche, token chiffré), `objectives` (statut, priorité, échéance), `milestones`, `tasks` (objectif, assigné, statut, échéance), `bugs` (sévérité, statut), `conversations`, `messages`.

---

## 4. Contrats d'API internes

| Appel | Endpoint | Auth |
|---|---|---|
| Navigateur → backend (via Nuxt) | `/api/v1/**` | JWT RS256 (émis par le backend) |
| Navigateur → ws-gateway | `ws://…:4000/ws?token=<JWT>` | JWT validé via JWKS du backend (`/.well-known/jwks.json`) |
| ws-gateway → backend | `/api/v1/chat/messages` | JWT de l'utilisateur |
| backend → agent-worker | `POST /internal/agent/messages` | Header `X-Internal-Token` |
| agent-worker → backend | `/internal/v1/**` (objectifs, tâches, bugs) | Header `X-Internal-Token` |
| backend / agent-worker → ws-gateway | `POST /internal/events` | Header `X-Internal-Token` |

Les routes `/internal/**` ne sont accessibles qu'au sein du réseau Docker et refusent toute requête sans `X-Internal-Token` (variable `INTERNAL_API_TOKEN`).

---

## 5. Topologie réseau Docker

- Réseau bridge dédié : **`monitor-net`**, sous-réseau **`172.28.0.0/24`**, passerelle `172.28.0.1`.
- Seuls `frontend` et `ws-gateway` publient un port sur l'hôte. Les bases ne sont joignables que depuis `monitor-net` (option de debug : publier en `127.0.0.1` uniquement).

| Service | Container | IP | Port interne | Port hôte |
|---|---|---|---|---|
| Nuxt 3 | `monitor-frontend` | 172.28.0.10 | 3000 | **3000** |
| Node WebSocket | `monitor-ws` | 172.28.0.11 | 4000 | **4000** |
| Java Spring Boot | `monitor-backend` | 172.28.0.12 | 8080 | — |
| Python agent | `monitor-agent` | 172.28.0.13 | 8000 | — |
| PostgreSQL | `monitor-postgres` | 172.28.0.20 | 5432 | — (`127.0.0.1:5432` en debug) |
| MySQL | `monitor-mysql` | 172.28.0.21 | 3306 | — (`127.0.0.1:3306` en debug) |

Les services se joignent par **nom DNS Docker** (`backend`, `postgres`…) ; les IP fixes servent de référence d'exploitation et de règles de pare-feu.

---

## 6. Contrôles de santé

Chaque composant expose deux niveaux :
- **UP (liveness)** : le process répond. Un échec déclenche un redémarrage.
- **Bon état (readiness)** : le composant est réellement fonctionnel (dépendances, données, activité). Utilisé par Docker (`healthcheck`) et par `depends_on: condition: service_healthy`.

| Composant | UP (liveness) | Bon état (readiness) |
|---|---|---|
| `postgres` | `pg_isready` | `SELECT 1` + table `agent_state` existe ; extension `vector` chargée |
| `mysql` | `mysqladmin ping` | `SELECT 1` sur la base `monitor` ; migrations Flyway appliquées |
| `backend` | `GET /actuator/health/liveness` → 200 | `GET /actuator/health/readiness` → `UP` (MySQL OK, JWKS prêt, `agent-worker` joignable) |
| `agent-worker` | `GET /healthz` → 200 | `GET /readyz` → 200 si : PostgreSQL OK, backend joignable, au moins un fournisseur LLM configuré, **heartbeat du scheduler < 10 min**, dépôts accessibles en lecture |
| `ws-gateway` | `GET /healthz` → 200 | `GET /readyz` → 200 si : serveur WS actif, JWKS du backend récupéré, backend joignable |
| `frontend` | `GET /api/_health` → 200 | `GET /api/_health?deep=1` → 200 si backend et ws-gateway répondent |

**Paramètres Docker recommandés** : `interval: 15s`, `timeout: 5s`, `retries: 5`, `start_period` : 60 s (backend), 30 s (agent, ws, frontend), 30 s (bases).

**Indicateurs d'état métier** (exposés dans `/readyz` de l'agent, en JSON) : `last_heartbeat`, `pending_actions`, `active_subagent_jobs`, `failed_jobs_24h`, `llm_providers_ok`.

Script de vérification globale (`scripts/check.sh`) :

```bash
#!/usr/bin/env sh
set -e
check() { code=$(curl -s -o /dev/null -w "%{http_code}" "$2"); \
  [ "$code" = "200" ] && echo "OK   $1" || { echo "FAIL $1 ($code)"; FAIL=1; }; }
check "frontend"   http://localhost:3000/api/_health
check "ws-gateway" http://localhost:4000/readyz
docker exec monitor-backend  wget -qO- http://localhost:8080/actuator/health/readiness >/dev/null && echo "OK   backend"  || FAIL=1
docker exec monitor-agent    python -c "import urllib.request as u;u.urlopen('http://localhost:8000/readyz',timeout=3)" && echo "OK   agent" || FAIL=1
exit ${FAIL:-0}
```

---

## 7. Conteneurisation

### 7.1 Arborescence du projet

```
monitor/
├── docker-compose.yml
├── .env                      # secrets (jamais versionné)
├── scripts/check.sh
├── backend/    (Dockerfile, pom.xml, src/)
├── agent/      (Dockerfile, requirements.txt, app/)
├── ws-gateway/ (Dockerfile, package.json, src/)
├── frontend/   (Dockerfile, package.json, nuxt.config.ts)
└── data/                     # volumes locaux persistants
    ├── postgres/
    ├── mysql/
    └── repos/                # dépôts clonés par l'agent
```

### 7.2 Règles de conception des Dockerfiles
- **Multi-stage** : une étape de build, une image finale minimale.
- Images **alpine/slim** avec **versions figées** (jamais `latest`).
- Exécution **non-root** (`USER`).
- Copier les fichiers de dépendances **avant** le code pour exploiter le cache.
- Aucun secret dans l'image ; configuration par variables d'environnement.
- Un seul process par conteneur ; `EXPOSE` du port interne.

**`backend/Dockerfile`**
```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
```

**`agent/Dockerfile`**
```dockerfile
FROM python:3.12-slim
ENV PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1
RUN apt-get update && apt-get install -y --no-install-recommends git \
 && rm -rf /var/lib/apt/lists/* \
 && useradd -m -u 1000 app
WORKDIR /app
COPY requirements.txt .
RUN pip install -r requirements.txt
COPY . .
USER app
EXPOSE 8000
CMD ["uvicorn","app.main:app","--host","0.0.0.0","--port","8000"]
```

**`ws-gateway/Dockerfile`**
```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-alpine
ENV NODE_ENV=production
WORKDIR /app
COPY package*.json ./
RUN npm ci --omit=dev
COPY --from=build /app/dist ./dist
USER node
EXPOSE 4000
CMD ["node","dist/server.js"]
```

**`frontend/Dockerfile`**
```dockerfile
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-alpine
ENV NODE_ENV=production NITRO_HOST=0.0.0.0 NITRO_PORT=3000
WORKDIR /app
COPY --from=build /app/.output ./.output
USER node
EXPOSE 3000
CMD ["node",".output/server/index.mjs"]
```

### 7.3 Règles de conception du `docker-compose.yml`
- Un réseau `monitor-net` avec IP statiques (section 5).
- **Volumes en bind mount local** (`./data/...`) pour que les bases survivent aux redéploiements. **Ne jamais utiliser `docker compose down -v`** ; `docker compose up -d --build` suffit pour redéployer.
- `depends_on` avec `condition: service_healthy` pour imposer l'ordre : bases → backend & agent → ws-gateway → frontend.
- `restart: unless-stopped` sur tous les services.
- Secrets dans `.env` (référencés par `${VAR}`).
- Droits des dossiers de données : `data/postgres` et `data/mysql` appartiennent à l'UID 999, `data/repos` à l'UID 1000 (`sudo chown -R 999:999 data/postgres data/mysql && sudo chown -R 1000:1000 data/repos`).

**`docker-compose.yml`**
```yaml
name: monitor

networks:
  monitor-net:
    driver: bridge
    ipam:
      config:
        - subnet: 172.28.0.0/24
          gateway: 172.28.0.1

services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: monitor-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: agent_memory
      POSTGRES_USER: ${PG_USER}
      POSTGRES_PASSWORD: ${PG_PASSWORD}
    volumes:
      - ./data/postgres:/var/lib/postgresql/data
      - ./postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    networks:
      monitor-net: { ipv4_address: 172.28.0.20 }
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB && psql -U $$POSTGRES_USER -d $$POSTGRES_DB -tAc 'SELECT 1 FROM agent_state LIMIT 1' >/dev/null"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s

  mysql:
    image: mysql:8.4
    container_name: monitor-mysql
    restart: unless-stopped
    environment:
      MYSQL_DATABASE: monitor
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes:
      - ./data/mysql:/var/lib/mysql
    networks:
      monitor-net: { ipv4_address: 172.28.0.21 }
    healthcheck:
      test: ["CMD-SHELL", "mysqladmin ping -h 127.0.0.1 -u root -p$$MYSQL_ROOT_PASSWORD --silent && mysql -u root -p$$MYSQL_ROOT_PASSWORD -e 'SELECT 1' monitor >/dev/null"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s

  backend:
    build: ./backend
    container_name: monitor-backend
    restart: unless-stopped
    depends_on:
      mysql: { condition: service_healthy }
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/monitor
      SPRING_DATASOURCE_USERNAME: ${MYSQL_USER}
      SPRING_DATASOURCE_PASSWORD: ${MYSQL_PASSWORD}
      INTERNAL_API_TOKEN: ${INTERNAL_API_TOKEN}
      JWT_PRIVATE_KEY: ${JWT_PRIVATE_KEY}
      AGENT_URL: http://agent:8000
      WS_URL: http://ws-gateway:4000
    networks:
      monitor-net: { ipv4_address: 172.28.0.12 }
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health/readiness | grep -q UP"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 60s

  agent:
    build: ./agent
    container_name: monitor-agent
    restart: unless-stopped
    depends_on:
      postgres: { condition: service_healthy }
      backend:  { condition: service_healthy }
    environment:
      PG_DSN: postgresql://${PG_USER}:${PG_PASSWORD}@postgres:5432/agent_memory
      BACKEND_URL: http://backend:8080
      WS_URL: http://ws-gateway:4000
      INTERNAL_API_TOKEN: ${INTERNAL_API_TOKEN}
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      GEMINI_API_KEY: ${GEMINI_API_KEY}
      HEARTBEAT_MINUTES: 5
      LLM_DAILY_TOKEN_BUDGET: ${LLM_DAILY_TOKEN_BUDGET:-2000000}
      REPOS_DIR: /data/repos
    volumes:
      - ./data/repos:/data/repos
    networks:
      monitor-net: { ipv4_address: 172.28.0.13 }
    healthcheck:
      test: ["CMD", "python", "-c", "import urllib.request as u; u.urlopen('http://localhost:8000/readyz', timeout=3)"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s

  ws-gateway:
    build: ./ws-gateway
    container_name: monitor-ws
    restart: unless-stopped
    depends_on:
      backend: { condition: service_healthy }
    environment:
      PORT: 4000
      BACKEND_URL: http://backend:8080
      INTERNAL_API_TOKEN: ${INTERNAL_API_TOKEN}
      ALLOWED_ORIGIN: ${PUBLIC_URL}
    ports:
      - "4000:4000"
    networks:
      monitor-net: { ipv4_address: 172.28.0.11 }
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:4000/readyz >/dev/null"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s

  frontend:
    build: ./frontend
    container_name: monitor-frontend
    restart: unless-stopped
    depends_on:
      backend:    { condition: service_healthy }
      ws-gateway: { condition: service_healthy }
    environment:
      NUXT_BACKEND_URL: http://backend:8080
      NUXT_PUBLIC_WS_URL: ${PUBLIC_WS_URL}
    ports:
      - "3000:3000"
    networks:
      monitor-net: { ipv4_address: 172.28.0.10 }
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:3000/api/_health >/dev/null"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s
```

**`.env` (exemple)**
```dotenv
PG_USER=monitor
PG_PASSWORD=change_me
MYSQL_USER=monitor
MYSQL_PASSWORD=change_me
MYSQL_ROOT_PASSWORD=change_me_too
INTERNAL_API_TOKEN=generer_une_longue_valeur_aleatoire
JWT_PRIVATE_KEY=<clé RSA PEM encodée en base64>
ANTHROPIC_API_KEY=
OPENAI_API_KEY=
GEMINI_API_KEY=
LLM_DAILY_TOKEN_BUDGET=2000000
PUBLIC_URL=http://localhost:3000
PUBLIC_WS_URL=ws://localhost:4000
```

---

## 8. Déploiement et exploitation

```bash
# Premier déploiement
mkdir -p data/postgres data/mysql data/repos
sudo chown -R 999:999 data/postgres data/mysql && sudo chown -R 1000:1000 data/repos
docker compose up -d --build

# Vérification
docker compose ps          # tous les services doivent être "healthy"
sh scripts/check.sh

# Redéploiement (données conservées)
git pull && docker compose up -d --build

# Sauvegarde (à planifier)
docker exec monitor-postgres pg_dump -U $PG_USER agent_memory > backup/pg_$(date +%F).sql
docker exec monitor-mysql mysqldump -u root -p$MYSQL_ROOT_PASSWORD monitor > backup/mysql_$(date +%F).sql
```

### Checklist de mise en production
- [ ] `.env` renseigné et exclu de Git
- [ ] Dossiers `data/*` créés avec les bons droits
- [ ] Au moins une clé LLM configurée
- [ ] Reverse proxy HTTPS/WSS (Nginx, Caddy) devant les ports 3000 et 4000
- [ ] Sauvegardes PostgreSQL et MySQL planifiées
- [ ] `docker compose ps` : tous les services `healthy`