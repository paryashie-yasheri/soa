#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="$ROOT/wildfly/compose.yaml"

cleanup_on_failure() {
  local status=$?
  if ((status != 0)); then
    docker compose -f "$COMPOSE_FILE" down --remove-orphans || true
  fi
}

trap cleanup_on_failure EXIT
docker compose -f "$COMPOSE_FILE" up -d --build --wait
trap - EXIT
