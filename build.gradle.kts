plugins {
    id("io.quarkus") version "3.20.1" apply false
    kotlin("jvm") version "2.1.20" apply false
    kotlin("plugin.allopen") version "2.1.20" apply false
}

tasks.register("e2eTest") {
    group = "verification"
    description = "Runs the complete HTTPS application stack against isolated PostgreSQL."
    dependsOn(":e2e-tests:test")
}
