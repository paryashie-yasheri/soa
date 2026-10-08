#!/usr/bin/env bash
# Applies the WildFly configuration (TLS-only listener, PostgreSQL data source) used by both
# the local Docker verification stack and the helios deployment. Values are injected through
# environment variables so the data source password can come from ~/.pgpass without ever
# being written into the repository.
#
# The script is safe to re-run: creation is attempted first (it only succeeds on a pristine
# configuration), then the mutable attributes are reconciled so a changed database or port
# takes effect without reinstalling WildFly.
set -euo pipefail
umask 077

WILDFLY_HOME="${WILDFLY_HOME:?WILDFLY_HOME must point to the WildFly installation}"
SOA_HTTPS_PORT="${SOA_HTTPS_PORT:-61811}"
SOA_KEYSTORE_PASSWORD="${SOA_KEYSTORE_PASSWORD:-changeit}"
SOA_JDBC_URL="${SOA_JDBC_URL:?SOA_JDBC_URL is required}"
SOA_DB_USER="${SOA_DB_USER:?SOA_DB_USER is required}"
SOA_DB_PASSWORD="${SOA_DB_PASSWORD:?SOA_DB_PASSWORD is required}"
SOA_ORGANIZATION_URL="${SOA_ORGANIZATION_URL:-https://localhost:${SOA_HTTPS_PORT}}"

export SOA_JDBC_URL SOA_DB_USER SOA_DB_PASSWORD SOA_KEYSTORE_PASSWORD SOA_ORGANIZATION_URL

# Shared hosts often cap the account's address space, while the JVM would otherwise size its
# heap to a quarter of physical RAM. Pin a small heap for the embedded server used by the CLI.
CLI_JAVA_OPTS="${SOA_CLI_JAVA_OPTS:--Xms64m -Xmx512m}"
WORK="$(mktemp -d "$WILDFLY_HOME/standalone/configuration/soa-cli.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

cat > "$WORK/create.cli" <<CLI_EOF
embed-server --server-config=standalone.xml --std-out=echo

# The remote EJB connector rides on the plaintext listener, so it goes away with it.
/subsystem=ejb3/service=remote:remove()
/subsystem=remoting/http-connector=http-remoting-connector:remove()

# HTTPS only: drop the plaintext listener so HTTP cannot reach the applications.
/subsystem=undertow/server=default-server/http-listener=default:remove()
/socket-binding-group=standard-sockets/socket-binding=https:write-attribute(name=port, value=${SOA_HTTPS_PORT})

# Elytron TLS material (self-signed server certificate plus its own trust store).
/subsystem=elytron/key-store=soaKeystore:add(path=soa-server.p12, relative-to=jboss.server.config.dir, type=PKCS12, credential-reference={clear-text=\${env.SOA_KEYSTORE_PASSWORD}})
/subsystem=elytron/key-manager=soaKeyManager:add(key-store=soaKeystore, credential-reference={clear-text=\${env.SOA_KEYSTORE_PASSWORD}})
/subsystem=elytron/key-store=soaTruststore:add(path=soa-truststore.p12, relative-to=jboss.server.config.dir, type=PKCS12, credential-reference={clear-text=\${env.SOA_KEYSTORE_PASSWORD}})
/subsystem=elytron/trust-manager=soaTrustManager:add(key-store=soaTruststore)
/subsystem=elytron/server-ssl-context=soaSsl:add(key-manager=soaKeyManager, trust-manager=soaTrustManager, protocols=["TLSv1.2","TLSv1.3"])
/subsystem=undertow/server=default-server/https-listener=https:write-attribute(name=ssl-context, value=soaSsl)

# PostgreSQL driver and the shared schema data source.
/subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql, driver-module-name=org.postgresql, driver-class-name=org.postgresql.Driver)
/subsystem=datasources/data-source=SoaDS:add(jndi-name=java:jboss/datasources/SoaDS, driver-name=postgresql, connection-url="\${env.SOA_JDBC_URL}", user-name="\${env.SOA_DB_USER}", password="\${env.SOA_DB_PASSWORD}", enabled=true, use-java-context=true, min-pool-size=1, max-pool-size=10, background-validation=true, background-validation-millis=10000, validate-on-match=false)

# Where the directory service reaches the organization service.
/system-property=organization-service.url:add(value="\${env.SOA_ORGANIZATION_URL}")

stop-embedded-server
CLI_EOF

cat > "$WORK/update.cli" <<CLI_EOF
embed-server --server-config=standalone.xml --std-out=echo
if (outcome != success) of /extension=org.wildfly.extension.microprofile.openapi-smallrye:read-resource()
  /extension=org.wildfly.extension.microprofile.openapi-smallrye:add()
end-if
if (outcome != success) of /subsystem=microprofile-openapi-smallrye:read-resource()
  /subsystem=microprofile-openapi-smallrye:add()
end-if
/socket-binding-group=standard-sockets/socket-binding=https:write-attribute(name=port, value=${SOA_HTTPS_PORT})
/subsystem=undertow/server=default-server/https-listener=https:write-attribute(name=ssl-context, value=soaSsl)
/subsystem=datasources/data-source=SoaDS:write-attribute(name=connection-url, value="\${env.SOA_JDBC_URL}")
/subsystem=datasources/data-source=SoaDS:write-attribute(name=user-name, value="\${env.SOA_DB_USER}")
/subsystem=datasources/data-source=SoaDS:write-attribute(name=password, value="\${env.SOA_DB_PASSWORD}")
/system-property=organization-service.url:write-attribute(name=value, value="\${env.SOA_ORGANIZATION_URL}")
stop-embedded-server
CLI_EOF

if grep -q 'pool-name="SoaDS"' "$WILDFLY_HOME/standalone/configuration/standalone.xml"; then
  # Already configured: reconcile the mutable attributes only.
  JAVA_OPTS="$CLI_JAVA_OPTS" "${WILDFLY_HOME}/bin/jboss-cli.sh" --file="$WORK/update.cli"
else
  JAVA_OPTS="$CLI_JAVA_OPTS" "${WILDFLY_HOME}/bin/jboss-cli.sh" --file="$WORK/create.cli"
  JAVA_OPTS="$CLI_JAVA_OPTS" "${WILDFLY_HOME}/bin/jboss-cli.sh" --file="$WORK/update.cli"
fi
