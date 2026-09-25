#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="${HOST:-helios}"
REMOTE_DIR="${REMOTE_DIR:-/home/studs/s389491/public_html/soa}"
LOCAL_DIR="$SCRIPT_DIR/swagger/dist"
PUBLIC_URL="${PUBLIC_URL:-https://se.ifmo.ru}"

echo "==> Building static documentation"
"$SCRIPT_DIR/swagger/build.sh"

echo "==> Ensuring $HOST:$REMOTE_DIR exists"
ssh "$HOST" "mkdir -p '$REMOTE_DIR'"

echo "==> Uploading $LOCAL_DIR/ -> $HOST:$REMOTE_DIR/"
rsync -avz --delete -e ssh "$LOCAL_DIR/" "$HOST:$REMOTE_DIR/"

echo "==> Fixing permissions"
ssh "$HOST" "chmod -R a+rX '$REMOTE_DIR'"

echo "==> Deployed: $PUBLIC_URL"
