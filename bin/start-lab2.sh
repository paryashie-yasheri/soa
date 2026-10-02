#!/usr/bin/env bash
set -euo pipefail
DIR="${1:?directory required}"
PASS="${2:?keystore password required}"
cd "$DIR"
mkdir -p "$DIR/logs" "$DIR/pids"

stop_one(){
  local name="$1"
  if [[ -f "$DIR/pids/$name.pid" ]]; then kill "$(cat "$DIR/pids/$name.pid")" 2>/dev/null || true; rm -f "$DIR/pids/$name.pid"; fi
}
start_one(){
  local name="$1" port="$2" jar="$3"; shift 3
  stop_one "$name"
  ( export TLS_KEYSTORE="$DIR/tls/server.p12" TLS_KEYSTORE_PASSWORD="$PASS" PORT="$port"
    export DB_USER="${DB_USER:-soa}" DB_PASSWORD="${DB_PASSWORD:-soa-local}" DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/soa?currentSchema=s389491}"
    export ORGANIZATION_SERVICE_URL=https://localhost:9443 ORG_DIRECTORY_SERVICE_URL=https://localhost:9444/orgdirectory
    export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$DIR/tls/truststore.p12 -Djavax.net.ssl.trustStorePassword=$PASS"
    nohup java "$@" -jar "$DIR/$jar" >"$DIR/logs/$name.log" 2>&1 < /dev/null & echo $! >"$DIR/pids/$name.pid" )
}
start_one organization 9443 organization-service/app.jar
start_one directory 9444 orgdirectory-service/app.jar
start_one client 9445 client/app.jar
echo "Lab 2 services started in $DIR"
