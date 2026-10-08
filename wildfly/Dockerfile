FROM quay.io/wildfly/wildfly:35.0.1.Final-jdk21

ENV JBOSS_HOME=/opt/jboss/wildfly
ENV WILDFLY_HOME=/opt/jboss/wildfly
# Runtime values used by the expressions in standalone.xml (local verification only).
ENV SOA_JDBC_URL="jdbc:postgresql://db:5432/soa?currentSchema=s389491" \
    SOA_DB_USER=soa \
    SOA_DB_PASSWORD=soa-local \
    SOA_KEYSTORE_PASSWORD=changeit \
    SOA_ORGANIZATION_URL=https://localhost:61811

# PostgreSQL driver installed as a JBoss module referenced by the data source.
COPY --chown=jboss:jboss wildfly/postgresql-module/ ${WILDFLY_HOME}/modules/org/postgresql/main/

# The two JAX-RS applications. The WAR names set the context roots: / and /orgdirectory.
COPY --chown=jboss:jboss organization-service/build/libs/ROOT.war ${WILDFLY_HOME}/standalone/deployments/ROOT.war
COPY --chown=jboss:jboss orgdirectory-service/build/libs/orgdirectory.war ${WILDFLY_HOME}/standalone/deployments/orgdirectory.war

COPY --chown=jboss:jboss build/libs/ui.war ${WILDFLY_HOME}/standalone/deployments/ui.war

# Self-signed TLS material.
COPY --chown=jboss:jboss tls/server.p12 ${WILDFLY_HOME}/standalone/configuration/soa-server.p12
COPY --chown=jboss:jboss tls/truststore.p12 ${WILDFLY_HOME}/standalone/configuration/soa-truststore.p12

# Apply the same configuration script the helios deployment uses.
COPY --chown=jboss:jboss wildfly/configure-wildfly.sh /tmp/configure-wildfly.sh
RUN chmod +x /tmp/configure-wildfly.sh \
    && SOA_HTTPS_PORT=61811 \
       SOA_KEYSTORE_PASSWORD=changeit \
       SOA_JDBC_URL="jdbc:postgresql://db:5432/soa?currentSchema=s389491" \
       SOA_DB_USER=soa \
       SOA_DB_PASSWORD=soa-local \
       SOA_ORGANIZATION_URL="https://localhost:61811" \
       /tmp/configure-wildfly.sh \
    && rm -f /tmp/configure-wildfly.sh

# Make the server JVM trust the self-signed certificate for the directory service upstream calls.
RUN echo 'JAVA_OPTS="$JAVA_OPTS -Djavax.net.ssl.trustStore=${JBOSS_HOME}/standalone/configuration/soa-truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12"' >> ${JBOSS_HOME}/bin/standalone.conf

EXPOSE 61811
