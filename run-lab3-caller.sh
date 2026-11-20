#!/usr/bin/env bash
set -euo pipefail
umask 077

DIR="${DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
if [[ -f "$DIR/.env" ]]; then source "$DIR/.env"; fi
JAR="$DIR/spring-orgdirectory-service.jar"
TRUSTSTORE="$DIR/tls/truststore.p12"
REPLICA_ONLY="${REPLICA_ONLY:-false}"
SECOND_REPLICA_EXTERNAL="${SECOND_REPLICA_EXTERNAL:-true}"
HAPROXY_BIN="${HAPROXY_BIN:-haproxy}"
JAVA_BIN="${JAVA_BIN:-java}"
if [[ -s "$DIR/consul-url" ]]; then read -r SOA_CONSUL_URL < "$DIR/consul-url"; fi
CONSUL_HTTP_ADDR="${CONSUL_HTTP_ADDR:-${SOA_CONSUL_URL:-http://127.0.0.1:8500}}"
ORGANIZATION_SERVICE_URL="${ORGANIZATION_SERVICE_URL:-https://localhost:${SOA_HTTPS_PORT:-61811}}"

if [[ "$REPLICA_ONLY" != true ]]; then
  if [[ "$HAPROXY_BIN" == haproxy && -x "$HOME/.local/bin/haproxy" ]]; then HAPROXY_BIN="$HOME/.local/bin/haproxy"; fi
  command -v "$HAPROXY_BIN" >/dev/null || { echo "haproxy is required" >&2; exit 1; }
  if ! "$HAPROXY_BIN" -vv | grep -F ' +SSL ' >/dev/null; then
    echo "HAProxy at $HAPROXY_BIN lacks TLS support. Run bash $DIR/install-haproxy.sh on this host." >&2
    exit 1
  fi
fi
command -v "$JAVA_BIN" >/dev/null || { echo "Java is required" >&2; exit 1; }
[[ -f "$JAR" && -f "$TRUSTSTORE" ]] || { echo "Missing caller jar or TLS truststore in $DIR" >&2; exit 1; }

mkdir -p "$DIR/logs" "$DIR/run"
names=(orgdirectory1 orgdirectory2 haproxy)
if [[ "$REPLICA_ONLY" == true ]]; then names=(orgdirectory2); fi
for name in "${names[@]}"; do
  if [[ -f "$DIR/run/$name.pid" ]]; then
    old_pid="$(cat "$DIR/run/$name.pid")"
    kill "$old_pid" 2>/dev/null || true
    rm -f "$DIR/run/$name.pid"
  fi
done

first_port=61816
instances=(1 2)
if [[ "$SECOND_REPLICA_EXTERNAL" == true ]]; then instances=(1); fi
if [[ "$REPLICA_ONLY" == true ]]; then first_port=61816; instances=(2); fi
for instance in "${instances[@]}"; do
  port=$((first_port + instance - 1))
  SERVER_ADDRESS=127.0.0.1 SERVER_PORT="$port" CONSUL_HTTP_ADDR="$CONSUL_HTTP_ADDR" \
    SERVER_TOMCAT_ACCESSLOG_ENABLED=true \
    SERVER_TOMCAT_ACCESSLOG_DIRECTORY="$DIR/logs" \
    SERVER_TOMCAT_ACCESSLOG_PREFIX="access-orgdirectory$instance" \
    SERVER_TOMCAT_ACCESSLOG_SUFFIX=.log \
    SERVER_TOMCAT_ACCESSLOG_ROTATABLE=false \
    SERVER_TOMCAT_ACCESSLOG_BUFFERED=false \
    SERVER_TOMCAT_ACCESSLOG_PATTERN='%t %a "%r" %s %b %D' \
    ORGANIZATION_SERVICE_URL="$ORGANIZATION_SERVICE_URL" \
    nohup "$JAVA_BIN" -Xms32m "-Xmx${SOA_CALLER_MAX_HEAP:-256m}" -XX:CompressedClassSpaceSize=32m -XX:ReservedCodeCacheSize=48m -XX:ActiveProcessorCount=2 -XX:+UseSerialGC -Djavax.net.ssl.trustStore="$TRUSTSTORE" \
      -Djavax.net.ssl.trustStorePassword="${SOA_KEYSTORE_PASSWORD:-changeit}" \
      -Djavax.net.ssl.trustStoreType=PKCS12 -jar "$JAR" \
      >"$DIR/logs/orgdirectory$instance.log" 2>&1 </dev/null &
  echo "$!" > "$DIR/run/orgdirectory$instance.pid"
done

if [[ "$REPLICA_ONLY" == true ]]; then
  echo "Waiting for the second replica on 61817..."
  curl -fsS --max-time 5 --retry 30 --retry-connrefused --retry-delay 1 http://127.0.0.1:61817/health >/dev/null
  echo "Second replica is ready on 61817"
  exit 0
fi

sed "s|@@DIR@@|$DIR|g;s|127.0.0.1:61813|127.0.0.1:$first_port|;s|127.0.0.1:61814|127.0.0.1:$((first_port+1))|;s|127.0.0.1:61811|127.0.0.1:${SOA_HTTPS_PORT:-61811}|" "$DIR/haproxy/helios.cfg" > "$DIR/run/haproxy.cfg"
"$HAPROXY_BIN" -c -f "$DIR/run/haproxy.cfg"
nohup "$HAPROXY_BIN" -db -f "$DIR/run/haproxy.cfg" \
  >"$DIR/logs/haproxy.log" 2>&1 </dev/null &
echo "$!" > "$DIR/run/haproxy.pid"

for port in "$first_port" "$((first_port+1))"; do
  echo "Waiting for replica health on $port..."
  curl -fsS --max-time 5 --retry 30 --retry-connrefused --retry-delay 1 "http://127.0.0.1:$port/health" >/dev/null
done
curl -fkSs --retry 10 --retry-delay 1 https://localhost:61815/orgdirectory/order/name/false >/dev/null
echo "HAProxy exposes https://localhost:61815/orgdirectory/..."
