#!/usr/bin/env bash
# Runs on the machine that hosts WildFly (helios). It installs WildFly if needed, reads the
# database credentials from .env (bootstrapped from ~/.pgpass), applies the TLS/data-source configuration and starts
# the server with both JAX-RS applications.
#
# Expected layout, created by deploy-wildfly.sh:
#   $DIR/deployments/ROOT.war, $DIR/deployments/orgdirectory.war
#   $DIR/tls/server.p12, $DIR/tls/truststore.p12
#   $DIR/modules/org/postgresql/main/{module.xml,postgresql-*.jar}
#   $DIR/configure-wildfly.sh
set -euo pipefail
umask 077

DIR="${DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)}"
# This private file uses Bash assignment syntax. Never enable shell tracing here.
if [[ -f "$DIR/.env" ]]; then
  set -a
  source "$DIR/.env"
  set +a
fi
WILDFLY_HOME="${WILDFLY_HOME:-$DIR/wildfly}"
WILDFLY_VERSION="${WILDFLY_VERSION:-35.0.1.Final}"
WILDFLY_ZIP="${WILDFLY_ZIP:-}"
SOA_HTTPS_PORT="${SOA_HTTPS_PORT:-61811}"
SOA_KEYSTORE_PASSWORD="${SOA_KEYSTORE_PASSWORD:-changeit}"
SCHEMA="${SCHEMA:-s389491}"
PGPASS_FILE="${PGPASS_FILE:-$HOME/.pgpass}"

log() { printf '==> %s\n' "$*"; }

# --- 1. WildFly itself -------------------------------------------------------------------
if [[ ! -x "$WILDFLY_HOME/bin/standalone.sh" ]]; then
  log "Installing WildFly $WILDFLY_VERSION into $WILDFLY_HOME"
  mkdir -p "$DIR"
  archive="$DIR/wildfly-$WILDFLY_VERSION.zip"
  if [[ -n "$WILDFLY_ZIP" ]]; then
    cp "$WILDFLY_ZIP" "$archive"
  else
    url="https://github.com/wildfly/wildfly/releases/download/$WILDFLY_VERSION/wildfly-$WILDFLY_VERSION.zip"
    log "Downloading $url"
    if command -v curl >/dev/null 2>&1; then curl -fL "$url" -o "$archive"
    elif command -v wget >/dev/null 2>&1; then wget -O "$archive" "$url"
    else echo "Neither curl nor wget is available to download WildFly" >&2; exit 1
    fi
  fi
  if command -v unzip >/dev/null 2>&1; then unzip -q "$archive" -d "$DIR"
  else ( cd "$DIR" && "${JAVA_HOME:-$(dirname "$(dirname "$(command -v java)")")}/bin/jar" xf "$archive" ); fi
  mv "$DIR/wildfly-$WILDFLY_VERSION" "$WILDFLY_HOME"
  rm -f "$archive"
fi

# --- 2. Database credentials from ~/.pgpass ----------------------------------------------
parse_pgpass_line() {
  local line="$1" field="" char escaped=0 i fields=()
  for ((i = 0; i < ${#line}; i++)); do
    char="${line:i:1}"
    if ((escaped)); then
      field+="$char"
      escaped=0
    elif [[ "$char" == '\' ]]; then
      escaped=1
    elif [[ "$char" == ':' ]]; then
      fields+=("$field")
      field=""
    else
      field+="$char"
    fi
  done
  ((escaped == 0)) || return 1
  fields+=("$field")
  [[ ${#fields[@]} -eq 5 ]] || return 1
  PG_HOST="${fields[0]}"
  PG_PORT="${fields[1]}"
  PG_DB="${fields[2]}"
  PG_USER="${fields[3]}"
  PG_PASSWORD="${fields[4]}"
}

if [[ -z "${SOA_DB_USER:-}" || -z "${SOA_DB_PASSWORD:-}" ]]; then
  [[ -f "$PGPASS_FILE" ]] || { echo "No database credentials: $PGPASS_FILE not found" >&2; exit 1; }
  chosen=""
  while IFS= read -r candidate || [[ -n "$candidate" ]]; do
    [[ "$candidate" =~ ^[[:space:]]*(#|$) ]] && continue
    parse_pgpass_line "$candidate" || continue
    [[ -z "${SOA_DB_HOST:-}" || "$PG_HOST" == '*' || "$PG_HOST" == "$SOA_DB_HOST" ]] || continue
    [[ -z "${SOA_DB_PORT:-}" || "$PG_PORT" == '*' || "$PG_PORT" == "$SOA_DB_PORT" ]] || continue
    [[ -z "${SOA_DB_NAME:-}" || "$PG_DB" == '*' || "$PG_DB" == "$SOA_DB_NAME" ]] || continue
    [[ -z "${SOA_DB_USER:-}" || "$PG_USER" == '*' || "$PG_USER" == "$SOA_DB_USER" ]] || continue
    chosen="$candidate"
    break
  done < "$PGPASS_FILE"
  [[ -n "$chosen" ]] || { echo "No matching database credentials in $PGPASS_FILE" >&2; exit 1; }
  parse_pgpass_line "$chosen"
  SOA_DB_USER="${SOA_DB_USER:-$PG_USER}"
  SOA_DB_PASSWORD="${SOA_DB_PASSWORD:-$PG_PASSWORD}"
  PG_HOST="${PG_HOST:-pg}"; [[ "$PG_HOST" == "*" ]] && PG_HOST="pg"
  PG_PORT="${PG_PORT:-5432}"; [[ "$PG_PORT" == "*" ]] && PG_PORT="5432"
  # ~/.pgpass commonly uses a wildcard database; the shared helios database is "studs".
  PG_DB="${SOA_DB_NAME:-$PG_DB}"
  [[ -z "$PG_DB" || "$PG_DB" == "*" ]] && PG_DB="studs"
  log "Using $PG_HOST:$PG_PORT/$PG_DB as user $SOA_DB_USER from $PGPASS_FILE"
fi

PG_HOST="${SOA_DB_HOST:-${PG_HOST:-pg}}"
PG_PORT="${SOA_DB_PORT:-${PG_PORT:-5432}}"
PG_DB="${SOA_DB_NAME:-${PG_DB:-studs}}"
export SOA_DB_USER SOA_DB_PASSWORD
export SOA_JDBC_URL="${SOA_JDBC_URL:-jdbc:postgresql://$PG_HOST:$PG_PORT/$PG_DB?currentSchema=$SCHEMA}"
export SOA_HTTPS_PORT SOA_KEYSTORE_PASSWORD
export SOA_ORGANIZATION_URL="${SOA_ORGANIZATION_URL:-https://localhost:$SOA_HTTPS_PORT}"

# Bootstrap once from the existing pgpass; later starts use only this private .env.
if [[ ! -e "$DIR/.env" ]]; then
  (umask 077; for name in SOA_DB_USER SOA_DB_PASSWORD SOA_JDBC_URL SOA_HTTPS_PORT SOA_KEYSTORE_PASSWORD SOA_ORGANIZATION_URL; do
    printf '%s=%q\n' "$name" "${!name}"
  done) > "$DIR/.env"
fi
chmod 600 "$DIR/.env"
export SOA_MANAGEMENT_PORT="${SOA_MANAGEMENT_PORT:-61812}"

# --- 3. Stop any previous instance before touching the configuration ----------------------
# standalone.sh spawns the JVM as a child, so the server is matched by its home directory.
WILDFLY_PATTERN="[j]ava .*jboss.home.dir=$WILDFLY_HOME( |$)"
stop_wildfly() {
  pkill -f "$WILDFLY_PATTERN" 2>/dev/null || true
  for _ in $(seq 1 30); do
    pgrep -f "$WILDFLY_PATTERN" >/dev/null 2>&1 || break
    sleep 1
 done
  if pgrep -f "$WILDFLY_PATTERN" >/dev/null 2>&1; then
    echo "WildFly did not stop within 30 seconds" >&2; exit 1
  fi
  rm -f "$DIR/wildfly.pid"
}
if [[ -f "$DIR/wildfly.pid" ]] || pgrep -f "$WILDFLY_PATTERN" >/dev/null 2>&1; then
  log "Stopping the running WildFly instance"
  stop_wildfly
fi

log "Installing PostgreSQL driver module"
mkdir -p "$WILDFLY_HOME/modules/org/postgresql/main"
cp "$DIR/modules/org/postgresql/main/"* "$WILDFLY_HOME/modules/org/postgresql/main/"

log "Deploying applications"
mkdir -p "$WILDFLY_HOME/standalone/deployments"
cp "$DIR/deployments/ROOT.war" "$DIR/deployments/orgdirectory.war" "$DIR/deployments/ui.war" "$WILDFLY_HOME/standalone/deployments/"

log "Installing TLS material"
install -m 600 "$DIR/tls/server.p12" "$WILDFLY_HOME/standalone/configuration/soa-server.p12"
install -m 600 "$DIR/tls/truststore.p12" "$WILDFLY_HOME/standalone/configuration/soa-truststore.p12"


# --- 5. Server configuration --------------------------------------------------------------
# configure-wildfly.sh is idempotent, so it runs on every deployment to pick up changes to
# the database, port or credentials.
log "Applying WildFly configuration"
WILDFLY_HOME="$WILDFLY_HOME" "$DIR/configure-wildfly.sh"

if ! grep -q 'soa-truststore.p12' "$WILDFLY_HOME/bin/standalone.conf"; then
  log "Configuring the server JVM heap and trust store"
  # An explicit -Xmx is required because the default (a quarter of physical RAM) can exceed the
  # per-account memory limit on shared hosts such as helios.
  printf '\nJAVA_OPTS="$JAVA_OPTS %s -Djavax.net.ssl.trustStore=%s/standalone/configuration/soa-truststore.p12 -Djavax.net.ssl.trustStorePassword=%s -Djavax.net.ssl.trustStoreType=PKCS12"\n' \
    "${SOA_SERVER_JAVA_OPTS:--Xms128m -Xmx1g}" "$WILDFLY_HOME" "$SOA_KEYSTORE_PASSWORD" >> "$WILDFLY_HOME/bin/standalone.conf"
fi

# --- 6. Start -----------------------------------------------------------------------------
mkdir -p "$DIR/logs"
log "Starting WildFly on https://0.0.0.0:$SOA_HTTPS_PORT"
nohup "$WILDFLY_HOME/bin/standalone.sh" -b 0.0.0.0 -Djboss.management.http.port="$SOA_MANAGEMENT_PORT" > "$DIR/logs/wildfly.log" 2>&1 < /dev/null &
launcher=$!
jvm=""
for _ in $(seq 1 60); do
  jvm="$(pgrep -f "$WILDFLY_PATTERN" | head -n1 || true)"
  [[ -n "$jvm" ]] && break
  sleep 1
done
echo "${jvm:-$launcher}" > "$DIR/wildfly.pid"

log "Waiting for the deployment to answer"
for _ in $(seq 1 120); do
  if grep -q 'WFLYSRV0025\|WFLYSRV0026' "$DIR/logs/wildfly.log" 2>/dev/null; then break; fi
  sleep 2
done
if grep -q 'WFLYSRV0026' "$DIR/logs/wildfly.log" 2>/dev/null; then
  {
    echo "WildFly started with errors; see $DIR/logs/wildfly.log"
    echo "--- data source diagnostics ---"
    grep -nE 'IJ000453|Caused by|PSQLException|FATAL|Unable to get managed connection' "$DIR/logs/wildfly.log" | tail -20 || true
    echo "--- live connection test ---"
    JAVA_OPTS="${SOA_CLI_JAVA_OPTS:--Xms64m -Xmx512m}" "$WILDFLY_HOME/bin/jboss-cli.sh" --connect --controller="localhost:$SOA_MANAGEMENT_PORT" \
      --command='/subsystem=datasources/data-source=SoaDS:test-connection-in-pool' 2>&1 | tail -20 || true
  } >&2
  exit 1
fi
if ! grep -q 'WFLYSRV0025' "$DIR/logs/wildfly.log"; then
  echo "WildFly startup timed out; see $DIR/logs/wildfly.log" >&2; exit 1
fi
for endpoint in /organizations /orgdirectory/order/name/false /openapi/organizations /openapi/orgdirectory /ui/; do
  curl --fail --silent --show-error --insecure --max-time 30 "https://localhost:$SOA_HTTPS_PORT$endpoint" > /dev/null
done
log "WildFly is running; both APIs and UI passed HTTPS checks (pid $(cat "$DIR/wildfly.pid"))"
