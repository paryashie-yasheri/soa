plugins {
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.allopen") version "2.1.20" apply false
    kotlin("plugin.spring") version "2.1.20" apply false
    id("org.springframework.boot") version "3.4.4" apply false
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

tasks.register("lab3Bundle") {
    group = "build"
    description = "Builds the Lab 3 EAR, compatibility WAR, caller replicas and UI."
    dependsOn("organizationEar", ":orgdirectory-service:war", ":spring-orgdirectory-service:bootJar", "uiWar")
}

tasks.register<Exec>("lab3StackUp") {
    group = "application"
    description = "Starts the local Lab 3 WildFly, Consul, Spring Boot replicas and HAProxy stack."
    dependsOn("lab3Bundle", "generateTls", "copyPostgresDriver")
    workingDir = rootProject.projectDir
    commandLine("bash", "wildfly/run-local-stack.sh")
}

tasks.register<Exec>("lab3StackDown") {
    group = "application"
    description = "Stops the local Lab 3 stack and removes its disposable PostgreSQL volume."
    workingDir = rootProject.projectDir
    commandLine("docker", "compose", "-f", "wildfly/compose.yaml", "down", "-v")
}

// Build the Bun/Vite client as a fully static app for the /ui context.
val frontendInstall by tasks.registering(Exec::class) {
    group = "build"
    workingDir("frontend")
    inputs.files("frontend/package.json", "frontend/bun.lock")
    outputs.dir("frontend/node_modules")
    commandLine("bun", "install", "--frozen-lockfile")
}
val frontendBuild by tasks.registering(Exec::class) {
    group = "build"
    dependsOn(frontendInstall)
    workingDir("frontend")
    inputs.files(fileTree("frontend/src"), fileTree("frontend/scripts"), fileTree("frontend/docs"), fileTree("openapi"), file("frontend/index.html"), fileTree("frontend") { include("tsconfig*.json") }, file("frontend/package.json"), file("frontend/bun.lock"), file("frontend/vite.config.ts"))
    outputs.dir("frontend/dist")
    commandLine("bun", "run", "build")
}

tasks.register<Zip>("uiWar") {
    group = "build"
    dependsOn(frontendBuild)
    archiveFileName.set("ui.war")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from("frontend/dist")
}

tasks.register<Zip>("organizationEar") {
    group = "build"
    description = "Packages the organization web module and its EJB-JAR into an EAR."
    dependsOn(":organization-ejb:jar", ":organization-web:war")
    archiveBaseName.set("organization-service")
    archiveVersion.set("")
    archiveFileName.set("organization-service.ear")
    archiveVersion.set("")
    archiveFileName.set("organization-service.ear")
    archiveExtension.set("ear")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(project(":organization-ejb").layout.buildDirectory.file("libs/organization-ejb.jar"))
    from(project(":organization-web").layout.buildDirectory.file("libs/organization-web.war"))
    // Kotlin classes in the EJB module need the matching stdlib at runtime on WildFly.
    from(project(":organization-ejb").configurations.getByName("runtimeClasspath").filter { it.isFile && it.extension == "jar" }) {
        into("lib")
    }
    from("organization-web/src/main/application")
}
