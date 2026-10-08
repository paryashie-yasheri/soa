plugins { kotlin("jvm"); kotlin("plugin.allopen"); war }

group = "ru.ifmo.soa"
version = "1.0.0"

dependencies {
    compileOnly("jakarta.platform:jakarta.jakartaee-api:10.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
allOpen { annotation("jakarta.ws.rs.Path"); annotation("jakarta.enterprise.context.ApplicationScoped") }

tasks.war { archiveFileName.set("orgdirectory.war") }

// Preserve XML schemas and query parameters that cannot be inferred from Response/String.
tasks.processResources {
    from(rootProject.file("openapi/orgdirectory-service.yaml")) {
        into("META-INF")
        rename { "openapi.yaml" }
    }
}

tasks.test { useJUnitPlatform() }
