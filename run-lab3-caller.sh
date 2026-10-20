#!/usr/bin/env bash
set -euo pipefail
umask 077

DIR="${DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
JAR="$DIR/spring-orgdirectory-service.jar"
TRUSTSTORE="$DIR/tls/truststore.p12"
HAPROXY_BIN="${HAPROXY_BIN:-haproxy}"
JAVA_BIN="${JAVA_BIN:-java}"
CONSUL_HTTP_ADDR="${CONSUL_HTTP_ADDR:-${SOA_CONSUL_URL:-http://127.0.0.1:8500}}"
ORGANIZATION_SERVICE_URL="${ORGANIZATION_SERVICE_URL:-https://localhost:61811}"

command -v "$HAPROXY_BIN" >/dev/null || { echo "haproxy is required" >&2; exit 1; }
command -v "$JAVA_BIN" >/dev/null || { echo "Java is required" >&2; exit 1; }
[[ -f "$JAR" && -f "$TRUSTSTORE" ]] || { echo "Missing caller jar or TLS truststore in $DIR" >&2; exit 1; }

mkdir -p "$DIR/logs" "$DIR/run"
for name in orgdirectory1 orgdirectory2 haproxy; do
  if [[ -f "$DIR/run/$name.pid" ]]; then
    old_pid="$(cat "$DIR/run/$name.pid")"
    kill "$old_pid" 2>/dev/null || true
    rm -f "$DIR/run/$name.pid"
  fi
done

for instance in 1 2; do
  port=$((61812 + instance))
  SERVER_ADDRESS=127.0.0.1 SERVER_PORT="$port" CONSUL_HTTP_ADDR="$CONSUL_HTTP_ADDR" \
    ORGANIZATION_SERVICE_URL="$ORGANIZATION_SERVICE_URL" \
    nohup "$JAVA_BIN" -Djavax.net.ssl.trustStore="$TRUSTSTORE" \
      -Djavax.net.ssl.trustStorePassword="${SOA_KEYSTORE_PASSWORD:-changeit}" \
      -Djavax.net.ssl.trustStoreType=PKCS12 -jar "$JAR" \
      >"$DIR/logs/orgdirectory$instance.log" 2>&1 </dev/null &
  echo "$!" > "$DIR/run/orgdirectory$instance.pid"
done

sed "s|@@DIR@@|$DIR|g" "$DIR/haproxy/helios.cfg" > "$DIR/run/haproxy.cfg"
"$HAPROXY_BIN" -c -f "$DIR/run/haproxy.cfg"
nohup "$HAPROXY_BIN" -db -f "$DIR/run/haproxy.cfg" \
  >"$DIR/logs/haproxy.log" 2>&1 </dev/null &
echo "$!" > "$DIR/run/haproxy.pid"

echo "OrgDirectory replicas listen on 127.0.0.1:61813 and :61814."
echo "HAProxy exposes https://localhost:61815/orgdirectory/..."
