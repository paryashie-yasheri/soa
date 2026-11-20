#!/usr/bin/env bash
# Builds the WildFly deployment and installs it on helios:
#   * the Java EE organization EAR and Spring Boot directory caller
#   * WildFly itself (downloaded on the host if missing) with an HTTP-to-HTTPS redirect
#   * a PostgreSQL data source configured by soa-lab2/.env
#   * the React client at /ui/ and API documentation at /ui/docs/ as ui.war
#
# Run it from the repository root; it uses ssh/rsync to reach the host.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST="${HOST:-helios}"
REMOTE_DIR="${REMOTE_DIR:-/home/studs/s389491/soa-lab2}"
SECOND_HOST="${SECOND_HOST:-helios2}"
SECOND_DIR="${SECOND_DIR:-/home/studs/s408614/soa-lab3}"
export HOST REMOTE_DIR SECOND_HOST SECOND_DIR
WILDFLY_VERSION="${WILDFLY_VERSION:-35.0.1.Final}"
WILDFLY_HOME="${WILDFLY_HOME:-}"
SOA_HTTPS_PORT="${SOA_HTTPS_PORT:-61811}"
SOA_HTTP_PORT="${SOA_HTTP_PORT:-61810}"
SOA_HTTPS_REDIRECT_PORT="${SOA_HTTPS_REDIRECT_PORT:-$SOA_HTTPS_PORT}"
SOA_KEYSTORE_PASSWORD="${SOA_KEYSTORE_PASSWORD:-changeit}"
SCHEMA="${SCHEMA:-s389491}"
PUBLIC_HOST="${PUBLIC_HOST:-helios.cs.ifmo.ru}"
SOA_PUBLIC_HOST="${SOA_PUBLIC_HOST:-$PUBLIC_HOST}"
POSTGRES_DRIVER_JAR="$ROOT/wildfly/postgresql-module/postgresql-42.7.5.jar"

command -v rsync >/dev/null || { echo "rsync is required for deployment" >&2; exit 1; }
command -v bun >/dev/null 2>&1 || { echo 'Bun is required to build the frontend; install it before deploying' >&2; exit 1; }

JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
[[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -ge 17 ]] || { echo 'Java 17 or newer is required to build the WARs' >&2; exit 1; }

echo "==> Building the Lab 3 services and staging the PostgreSQL driver"
"$ROOT/gradlew" -p "$ROOT" --no-daemon organizationEar :spring-orgdirectory-service:bootJar copyPostgresDriver
[[ -f "$POSTGRES_DRIVER_JAR" ]] || { echo "Missing PostgreSQL driver: $POSTGRES_DRIVER_JAR" >&2; exit 1; }
for artifact in "$ROOT/build/libs/organization-service.ear" "$ROOT/spring-orgdirectory-service/build/libs/spring-orgdirectory-service-1.0.0.jar"; do
  [[ -s "$artifact" ]] || { echo "Missing or empty deployment artifact: $artifact" >&2; exit 1; }
done

echo "==> Building the locked frontend and packaging it into ui.war"
"$ROOT/gradlew" -p "$ROOT" --no-daemon uiWar --rerun-tasks
[[ -s "$ROOT/build/libs/ui.war" ]] || { echo 'Missing or empty frontend WAR' >&2; exit 1; }

echo "==> Preparing the self-signed certificate"
mkdir -p "$ROOT/tls"
if [[ ! -f "$ROOT/tls/server.p12" ]]; then
  keytool -genkeypair -alias soa-lab2 -keyalg RSA -keysize 3072 -storetype PKCS12 \
    -keystore "$ROOT/tls/server.p12" -storepass "$SOA_KEYSTORE_PASSWORD" \
    -keypass "$SOA_KEYSTORE_PASSWORD" -validity 825 -dname 'CN=localhost, OU=SOA Lab, O=IFMO, C=RU' \
    -ext 'SAN=dns:localhost,dns:app,dns:helios,dns:se.ifmo.ru,dns:helios.cs.ifmo.ru,ip:127.0.0.1'
fi
if [[ ! -f "$ROOT/tls/truststore.p12" ]]; then
  keytool -exportcert -rfc -alias soa-lab2 -keystore "$ROOT/tls/server.p12" -storepass "$SOA_KEYSTORE_PASSWORD" -file "$ROOT/tls/server.crt"
  keytool -importcert -noprompt -alias soa-lab2 -file "$ROOT/tls/server.crt" -storetype PKCS12 \
    -keystore "$ROOT/tls/truststore.p12" -storepass "$SOA_KEYSTORE_PASSWORD"
fi
if [[ ! -f "$ROOT/tls/haproxy.pem" ]]; then
  SOA_CERT_PASSWORD="$SOA_KEYSTORE_PASSWORD" openssl pkcs12 -in "$ROOT/tls/server.p12" \
    -nodes -passin env:SOA_CERT_PASSWORD -out "$ROOT/tls/haproxy.pem"
  chmod 600 "$ROOT/tls/haproxy.pem"
fi

echo "==> Preparing $HOST:$REMOTE_DIR"
ssh "$HOST" "command -v rsync >/dev/null && umask 077 && mkdir -p '$REMOTE_DIR/deployments' '$REMOTE_DIR/tls' '$REMOTE_DIR/logs' '$REMOTE_DIR/modules/org/postgresql/main' '$REMOTE_DIR/swagger' '$REMOTE_DIR/openapi' '$REMOTE_DIR/haproxy'"

echo "==> Uploading the deployment bundle"
rsync -t --checksum -- "$ROOT/build/libs/organization-service.ear" "$HOST:$REMOTE_DIR/deployments/organization-service.ear"
rsync -t --checksum -- "$ROOT/spring-orgdirectory-service/build/libs/spring-orgdirectory-service-1.0.0.jar" "$HOST:$REMOTE_DIR/spring-orgdirectory-service.jar"
rsync -t --checksum -- "$ROOT/build/libs/ui.war" "$HOST:$REMOTE_DIR/deployments/ui.war"
rsync -pt --chmod=F600 --checksum -- "$ROOT/tls/server.p12" "$ROOT/tls/truststore.p12" "$ROOT/tls/haproxy.pem" "$HOST:$REMOTE_DIR/tls/"
rsync -t --checksum -- "$ROOT/wildfly/postgresql-module/module.xml" "$POSTGRES_DRIVER_JAR" "$HOST:$REMOTE_DIR/modules/org/postgresql/main/"
rsync -t --checksum -- "$ROOT/wildfly/configure-wildfly.sh" "$ROOT/wildfly/setup-wildfly.sh" "$ROOT/wildfly/setup-proxy.sh" "$ROOT/wildfly/install-haproxy.sh" "$ROOT/wildfly/.env.example" "$HOST:$REMOTE_DIR/"

rsync -t --checksum -- "$ROOT/swagger/build.sh" "$HOST:$REMOTE_DIR/swagger/"
rsync -t --checksum -- "$ROOT/haproxy/helios.cfg" "$HOST:$REMOTE_DIR/haproxy/helios.cfg"
rsync -t --checksum -- "$ROOT/run-lab3-caller.sh" "$HOST:$REMOTE_DIR/run-lab3-caller.sh"
rsync -t --checksum -- "$ROOT/openapi/organization-service.yaml" "$ROOT/openapi/orgdirectory-service.yaml" "$HOST:$REMOTE_DIR/openapi/"

echo "==> Installing and starting WildFly on $HOST"
ssh "$HOST" "chmod +x '$REMOTE_DIR/configure-wildfly.sh' '$REMOTE_DIR/setup-wildfly.sh' '$REMOTE_DIR/run-lab3-caller.sh' && DIR='$REMOTE_DIR' WILDFLY_HOME='$WILDFLY_HOME' WILDFLY_VERSION='$WILDFLY_VERSION' SOA_HTTP_PORT='$SOA_HTTP_PORT' SOA_HTTPS_PORT='$SOA_HTTPS_PORT' SOA_HTTPS_REDIRECT_PORT='$SOA_HTTPS_REDIRECT_PORT' SOA_PUBLIC_HOST='$SOA_PUBLIC_HOST' SOA_KEYSTORE_PASSWORD='$SOA_KEYSTORE_PASSWORD' SCHEMA='$SCHEMA' '$REMOTE_DIR/setup-wildfly.sh'"

echo "==> Deploying the second caller to $SECOND_HOST"
"$ROOT/deploy-second-caller.sh"
ssh "$HOST" "cd '$REMOTE_DIR' && bash run-lab3-caller.sh"

echo "Lab 3 gateway: https://$PUBLIC_HOST:61815/ui/"
