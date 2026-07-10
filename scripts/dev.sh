#!/usr/bin/env bash
#
# One-command local dev launcher for OpenLOSA.
#
#   ./scripts/dev.sh            # mysql + engine in Docker, backend + frontend on host
#   OPENLOSA_PROFILES=""        # just mysql (no feed engine)
#   OPENLOSA_PROFILES="engine email"  # also start the email-finder sidecar
#
# Starts the Docker services, waits for MySQL to report healthy, then runs the
# backend (mvn spring-boot:run) and frontend (npm run dev) with live reload.
# Ctrl-C stops the backend and frontend; the Docker services are left running
# (stop them with `docker compose down`).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

# Space-separated docker compose profiles to bring up alongside mysql.
PROFILES="${OPENLOSA_PROFILES-engine}"

compose=(docker compose)
for p in $PROFILES; do compose+=(--profile "$p"); done

echo "==> Starting Docker services (mysql${PROFILES:+ + $PROFILES})..."
"${compose[@]}" up -d

echo -n "==> Waiting for MySQL to become healthy"
until [ "$(docker inspect -f '{{.State.Health.Status}}' openlosa-mysql 2>/dev/null)" = "healthy" ]; do
  printf '.'
  sleep 3
done
echo " healthy."

pids=()
cleanup() {
  trap - INT TERM EXIT
  echo
  echo "==> Stopping backend + frontend..."
  for pid in "${pids[@]:-}"; do
    [ -n "$pid" ] || continue
    pkill -TERM -P "$pid" 2>/dev/null || true
    kill -TERM "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
  echo "==> Done. Docker services still running — stop them with: docker compose down"
}
trap cleanup INT TERM EXIT

if [ ! -d frontend/node_modules ]; then
  echo "==> Installing frontend dependencies..."
  (cd frontend && npm install)
fi

echo "==> Starting backend  -> http://127.0.0.1:8080"
(cd backend && mvn spring-boot:run) &
pids+=($!)

echo "==> Starting frontend -> http://127.0.0.1:5173"
(cd frontend && npm run dev) &
pids+=($!)

echo
echo "==> OpenLOSA is starting."
echo "      App:     http://127.0.0.1:5173"
echo "      Backend: http://127.0.0.1:8080/api/v1/health"
echo "      Ctrl-C stops backend + frontend."
echo

wait
