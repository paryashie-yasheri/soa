FROM eclipse-temurin:17-jre
WORKDIR /app
COPY spring-orgdirectory-service/build/libs/spring-orgdirectory-service-1.0.0.jar app.jar
COPY tls/truststore.p12 /app/truststore.p12
ENV JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/app/truststore.p12 -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
