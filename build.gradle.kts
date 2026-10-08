plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.allopen") version "2.1.20" apply false
}

val postgresDriver by configurations.creating

dependencies {
    postgresDriver("org.postgresql:postgresql:42.7.5")
}

// WildFly loads the JDBC driver from a module directory, so the jar is staged inside the repo.
tasks.register("copyPostgresDriver") {
    group = "build"
    description = "Stages the PostgreSQL JDBC driver for the WildFly module."
    val configuration = postgresDriver
    val destination = layout.projectDirectory.file("wildfly/postgresql-module/postgresql-42.7.5.jar").asFile
    inputs.files(configuration)
    outputs.file(destination)
    doLast {
        val jar = configuration.files.single { it.name == "postgresql-42.7.5.jar" }
        destination.parentFile.mkdirs()
        jar.copyTo(destination, overwrite = true)
    }
}

// The services require a self-signed certificate; it is generated once and reused afterwards.
tasks.register("generateTls") {
    group = "build"
    description = "Creates the self-signed TLS material in tls/ if it is missing."
    val tlsDir = layout.projectDirectory.dir("tls").asFile
    val server = File(tlsDir, "server.p12")
    val truststore = File(tlsDir, "truststore.p12")
    outputs.files(server, truststore)
    doLast {
        fun run(vararg command: String) {
            val process = ProcessBuilder(*command).inheritIO().start()
            check(process.waitFor() == 0) { "Command failed: ${command.joinToString(" ")}" }
        }
        val keytool = File(System.getProperty("java.home"), "bin/keytool").absolutePath
        tlsDir.mkdirs()
        if (!server.exists()) {
            run(keytool, "-genkeypair", "-alias", "soa-lab2", "-keyalg", "RSA", "-keysize", "3072", "-storetype", "PKCS12",
                "-keystore", server.absolutePath, "-storepass", "changeit", "-keypass", "changeit", "-validity", "825",
                "-dname", "CN=localhost, OU=SOA Lab, O=IFMO, C=RU",
                "-ext", "SAN=dns:localhost,dns:helios,dns:se.ifmo.ru,dns:helios.cs.ifmo.ru,ip:127.0.0.1")
        }
        if (!truststore.exists()) {
            val crt = File(tlsDir, "server.crt")
            run(keytool, "-exportcert", "-rfc", "-alias", "soa-lab2", "-keystore", server.absolutePath, "-storepass", "changeit", "-file", crt.absolutePath)
            run(keytool, "-importcert", "-noprompt", "-alias", "soa-lab2", "-file", crt.absolutePath, "-storetype", "PKCS12",
                "-keystore", truststore.absolutePath, "-storepass", "changeit")
        }
    }
}

tasks.register("e2eTest") {
    group = "verification"
    description = "Builds the WildFly deployment and runs the HTTPS end-to-end suite against it."
    dependsOn(":e2e-tests:test")
}

// Package Swagger UI connected to WildFly OpenAPI endpoints for the /ui context.
val swaggerUi by tasks.registering(Exec::class) {
    group = "build"
    inputs.file("swagger/build.sh")
    inputs.property("swaggerUiVersion", providers.environmentVariable("SWAGGER_UI_VERSION").orElse("5.33.0"))
    outputs.dir("swagger/dist")
    commandLine("bash", "swagger/build.sh")
}

tasks.register<Zip>("uiWar") {
    group = "build"
    dependsOn(swaggerUi)
    archiveFileName.set("ui.war")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from("swagger/dist")
}
