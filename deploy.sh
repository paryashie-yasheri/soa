#!/usr/bin/env bash
# Compatibility entrypoint: deployment now stays inside soa-lab2 on WildFly.
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/deploy-wildfly.sh" "$@"
