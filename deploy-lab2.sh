#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="${HOST:-helios}"
REMOTE_DIR="${REMOTE_DIR:-/home/studs/s389491/soa-lab2}"
TLS_KEYSTORE_PASSWORD="${TLS_KEYSTORE_PASSWORD:-changeit}"

JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
[[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -ge 17 ]] || { echo 'Java 17 or newer is required to build Lab 2; bytecode target is Java 17' >&2; exit 1; }
mkdir -p "$ROOT/tls"
if [[ ! -f "$ROOT/tls/server.p12" ]]; then
  keytool -genkeypair -alias soa-lab2 -keyalg RSA -keysize 3072 -storetype PKCS12 \
    -keystore "$ROOT/tls/server.p12" -storepass "$TLS_KEYSTORE_PASSWORD" \
    -keypass "$TLS_KEYSTORE_PASSWORD" -validity 825 -dname 'CN=localhost, OU=SOA Lab, O=IFMO, C=RU' \
    -ext 'SAN=dns:localhost,dns:helios,dns:se.ifmo.ru,dns:helios.ifmo.ru,ip:127.0.0.1'
fi
if [[ ! -f "$ROOT/tls/truststore.p12" ]]; then
  keytool -exportcert -rfc -alias soa-lab2 -keystore "$ROOT/tls/server.p12" -storepass "$TLS_KEYSTORE_PASSWORD" -file "$ROOT/tls/server.crt"
  keytool -importcert -noprompt -alias soa-lab2 -file "$ROOT/tls/server.crt" -storetype PKCS12 \
    -keystore "$ROOT/tls/truststore.p12" -storepass "$TLS_KEYSTORE_PASSWORD"
fi

"$ROOT/gradlew" -p "$ROOT" --no-daemon clean build
ssh "$HOST" "mkdir -p '$REMOTE_DIR/tls' '$REMOTE_DIR/bin' '$REMOTE_DIR/logs' '$REMOTE_DIR/pids' '$REMOTE_DIR/organization-service' '$REMOTE_DIR/orgdirectory-service' '$REMOTE_DIR/client' '$REMOTE_DIR/database/init'"
for module in organization-service orgdirectory-service client; do
  ARTIFACT="$(find "$ROOT/$module/build" -maxdepth 1 -name '*-runner.jar' -print -quit)"
  [[ -n "$ARTIFACT" ]] || { echo "Missing Quarkus uber-jar for $module" >&2; exit 1; }
  scp "$ARTIFACT" "$HOST:$REMOTE_DIR/$module/app.jar"
done
scp "$ROOT/tls/server.p12" "$ROOT/tls/truststore.p12" "$HOST:$REMOTE_DIR/tls/"
scp "$ROOT/compose.yaml" "$HOST:$REMOTE_DIR/compose.yaml"
scp "$ROOT/database/init/01-schema.sql" "$HOST:$REMOTE_DIR/database/init/01-schema.sql"
scp "$ROOT/bin/start-lab2.sh" "$HOST:$REMOTE_DIR/bin/start-lab2.sh"
ssh "$HOST" "chmod 700 '$REMOTE_DIR/bin/start-lab2.sh' && chmod 600 '$REMOTE_DIR/tls/'*.p12 && cd '$REMOTE_DIR' && docker compose up -d --wait database && '$REMOTE_DIR/bin/start-lab2.sh' '$REMOTE_DIR' '$TLS_KEYSTORE_PASSWORD'"
echo "Client UI: https://${PUBLIC_HOST:-se.ifmo.ru}:9445/"
