#!/usr/bin/env sh
set -e
check() { code=$(curl -s -o /dev/null -w "%{http_code}" "$2"); \
  [ "$code" = "200" ] && echo "OK   $1" || { echo "FAIL $1 ($code)"; FAIL=1; }; }
check "frontend"   http://localhost:3000/api/_health
check "ws-gateway" http://localhost:4000/readyz
docker exec monitor-backend  wget -qO- http://localhost:8080/actuator/health/readiness >/dev/null && echo "OK   backend"  || FAIL=1
docker exec monitor-agent    python -c "import urllib.request as u;u.urlopen('http://localhost:8000/readyz',timeout=3)" && echo "OK   agent" || FAIL=1
exit ${FAIL:-0}
