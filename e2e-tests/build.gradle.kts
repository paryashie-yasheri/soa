plugins { kotlin("jvm") }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
    testRuntimeOnly("org.postgresql:postgresql:42.7.5")
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

val composeFile = "wildfly/compose.yaml"

val stackUp = tasks.register<Exec>("stackUp") {
    group = "verification"
    description = "Builds and starts the WildFly verification stack with Docker Compose."
    dependsOn(":uiWar", ":copyPostgresDriver", ":generateTls", ":organization-service:war", ":orgdirectory-service:war")
    workingDir = rootProject.projectDir
    commandLine("docker", "compose", "-f", composeFile, "up", "-d", "--build", "--wait")
    finalizedBy("stackDown")
}

val stackDown = tasks.register<Exec>("stackDown") {
    group = "verification"
    description = "Stops the WildFly verification stack."
    workingDir = rootProject.projectDir
    commandLine("docker", "compose", "-f", composeFile, "down", "-v")
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    dependsOn(":organization-service:war", ":orgdirectory-service:war", stackUp)
    finalizedBy(stackDown)
    mustRunAfter("openapiExamples")
    outputs.upToDateWhen { false }
}

// Fault injection and example mutations are confined to the disposable Compose stack.
tasks.register<Exec>("openapiExamples") {
    group = "verification"
    description = "Executes every explicit OpenAPI XML example, including controlled failure responses."
    dependsOn(stackUp)
    finalizedBy(stackDown)
    workingDir = rootProject.projectDir
    commandLine("python3", "e2e-tests/scripts/openapi_examples.py")
}
