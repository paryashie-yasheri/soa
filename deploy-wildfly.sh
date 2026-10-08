#!/usr/bin/env bash
# Builds the WildFly deployment and installs it on helios:
#   * the two JAX-RS applications as ROOT.war and orgdirectory.war
#   * WildFly itself (downloaded on the host if missing) with an HTTPS-only listener
#   * a PostgreSQL data source configured by soa-lab2/.env
#   * Swagger UI with both API specifications at /ui/ as ui.war
#
# Run it from the repository root; it uses ssh/scp to reach the host.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="${HOST:-helios}"
REMOTE_DIR="${REMOTE_DIR:-/home/studs/s389491/soa-lab2}"
WILDFLY_VERSION="${WILDFLY_VERSION:-35.0.1.Final}"
WILDFLY_HOME="${WILDFLY_HOME:-}"
SOA_HTTPS_PORT="${SOA_HTTPS_PORT:-61811}"
SOA_KEYSTORE_PASSWORD="${SOA_KEYSTORE_PASSWORD:-changeit}"
SCHEMA="${SCHEMA:-s389491}"
PUBLIC_HOST="${PUBLIC_HOST:-se.ifmo.ru}"
POSTGRES_DRIVER_JAR="$ROOT/wildfly/postgresql-module/postgresql-42.7.5.jar"

JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
[[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -ge 17 ]] || { echo 'Java 17 or newer is required to build the WARs' >&2; exit 1; }

echo "==> Building the WARs and staging the PostgreSQL driver"
"$ROOT/gradlew" -p "$ROOT" --no-daemon clean :organization-service:war :orgdirectory-service:war uiWar copyPostgresDriver
[[ -f "$POSTGRES_DRIVER_JAR" ]] || { echo "Missing PostgreSQL driver: $POSTGRES_DRIVER_JAR" >&2; exit 1; }

echo "==> Preparing the self-signed certificate"
mkdir -p "$ROOT/tls"
if [[ ! -f "$ROOT/tls/server.p12" ]]; then
  keytool -genkeypair -alias soa-lab2 -keyalg RSA -keysize 3072 -storetype PKCS12 \
    -keystore "$ROOT/tls/server.p12" -storepass "$SOA_KEYSTORE_PASSWORD" \
    -keypass "$SOA_KEYSTORE_PASSWORD" -validity 825 -dname 'CN=localhost, OU=SOA Lab, O=IFMO, C=RU' \
    -ext 'SAN=dns:localhost,dns:helios,dns:se.ifmo.ru,dns:helios.cs.ifmo.ru,ip:127.0.0.1'
fi
if [[ ! -f "$ROOT/tls/truststore.p12" ]]; then
  keytool -exportcert -rfc -alias soa-lab2 -keystore "$ROOT/tls/server.p12" -storepass "$SOA_KEYSTORE_PASSWORD" -file "$ROOT/tls/server.crt"
  keytool -importcert -noprompt -alias soa-lab2 -file "$ROOT/tls/server.crt" -storetype PKCS12 \
    -keystore "$ROOT/tls/truststore.p12" -storepass "$SOA_KEYSTORE_PASSWORD"
fi

echo "==> Preparing $HOST:$REMOTE_DIR"
ssh "$HOST" "mkdir -p '$REMOTE_DIR/deployments' '$REMOTE_DIR/tls' '$REMOTE_DIR/logs' '$REMOTE_DIR/modules/org/postgresql/main' '$REMOTE_DIR/swagger' '$REMOTE_DIR/openapi'"

echo "==> Uploading the deployment bundle"
scp "$ROOT/organization-service/build/libs/ROOT.war" "$HOST:$REMOTE_DIR/deployments/ROOT.war"
scp "$ROOT/orgdirectory-service/build/libs/orgdirectory.war" "$HOST:$REMOTE_DIR/deployments/orgdirectory.war"
scp "$ROOT/build/libs/ui.war" "$HOST:$REMOTE_DIR/deployments/ui.war"
scp "$ROOT/tls/server.p12" "$ROOT/tls/truststore.p12" "$HOST:$REMOTE_DIR/tls/"
scp "$ROOT/wildfly/postgresql-module/module.xml" "$POSTGRES_DRIVER_JAR" "$HOST:$REMOTE_DIR/modules/org/postgresql/main/"
scp "$ROOT/wildfly/configure-wildfly.sh" "$ROOT/wildfly/setup-wildfly.sh" "$ROOT/wildfly/.env.example" "$HOST:$REMOTE_DIR/"

scp "$ROOT/swagger/build.sh" "$HOST:$REMOTE_DIR/swagger/"
scp "$ROOT/openapi/organization-service.yaml" "$ROOT/openapi/orgdirectory-service.yaml" "$HOST:$REMOTE_DIR/openapi/"

echo "==> Installing and starting WildFly on $HOST"
ssh "$HOST" "chmod +x '$REMOTE_DIR/configure-wildfly.sh' '$REMOTE_DIR/setup-wildfly.sh' && DIR='$REMOTE_DIR' WILDFLY_HOME='$WILDFLY_HOME' WILDFLY_VERSION='$WILDFLY_VERSION' SOA_HTTPS_PORT='$SOA_HTTPS_PORT' SOA_KEYSTORE_PASSWORD='$SOA_KEYSTORE_PASSWORD' SCHEMA='$SCHEMA' '$REMOTE_DIR/setup-wildfly.sh'"

cat <<SUMMARY

Deployed.
  Swagger UI:    https://$PUBLIC_HOST:$SOA_HTTPS_PORT/ui/
  Organization: https://$PUBLIC_HOST:$SOA_HTTPS_PORT/organizations
  Directory:    https://$PUBLIC_HOST:$SOA_HTTPS_PORT/orgdirectory/order/name/false

The browser will warn about the self-signed certificate; accept it once for both the page
and the API host. Logs live in $REMOTE_DIR/logs/wildfly.log.
SUMMARY
