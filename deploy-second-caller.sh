#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SECOND_HOST="${SECOND_HOST:-helios2}"
SECOND_DIR="${SECOND_DIR:-/home/studs/s408614/soa-lab3}"
HOST="${HOST:-helios}"
REMOTE_DIR="${REMOTE_DIR:-/home/studs/s389491/soa-lab2}"
runtime=$(mktemp)
trap 'rm -f "$runtime"' EXIT
# Resolve the same private settings as the primary startup, without logging them.
ssh "$HOST" "cd '$REMOTE_DIR' && bash -s" > "$runtime" <<'REMOTE'
set -euo pipefail
source .env
if [[ -s consul-url ]]; then read -r SOA_CONSUL_URL < consul-url; fi
printf 'SOA_HTTPS_PORT=%q\n' "${SOA_HTTPS_PORT:-61811}"
printf 'SOA_CONSUL_URL=%q\n' "${SOA_CONSUL_URL:-http://127.0.0.1:61818}"
printf 'SOA_KEYSTORE_PASSWORD=%q\n' "${SOA_KEYSTORE_PASSWORD:-changeit}"
printf 'REPLICA_ONLY=true\n'
if [[ -f run/orgdirectory2.pid ]]; then
  pid=$(cat run/orgdirectory2.pid)
  case "$(ps -p "$pid" -o command= || true)" in
    *spring-orgdirectory-service.jar*)
      kill "$pid"
      for _ in {1..30}; do
        kill -0 "$pid" 2>/dev/null || break
        sleep 1
      done
      if kill -0 "$pid" 2>/dev/null; then
        echo "Old second replica did not stop" >&2
        exit 1
      fi
      ;;
  esac
  rm -f run/orgdirectory2.pid
fi
REMOTE
ssh "$SECOND_HOST" "umask 077; mkdir -p '$SECOND_DIR/tls'"
rsync -t --checksum "$ROOT/spring-orgdirectory-service/build/libs/spring-orgdirectory-service-1.0.0.jar" "$SECOND_HOST:$SECOND_DIR/spring-orgdirectory-service.jar"
rsync -pt --chmod=F600 "$ROOT/tls/truststore.p12" "$SECOND_HOST:$SECOND_DIR/tls/"
rsync -pt --chmod=F600 "$runtime" "$SECOND_HOST:$SECOND_DIR/.env"
rsync -pt "$ROOT/run-lab3-caller.sh" "$SECOND_HOST:$SECOND_DIR/"
ssh "$SECOND_HOST" "cd '$SECOND_DIR' && bash run-lab3-caller.sh"
