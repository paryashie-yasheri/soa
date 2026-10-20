plugins { kotlin("jvm"); kotlin("plugin.allopen"); war }
group = "ru.ifmo.soa"
version = "1.0.0"
dependencies {
    compileOnly("jakarta.platform:jakarta.jakartaee-api:10.0.0")
    compileOnly(project(":organization-ejb"))
    implementation("org.liquibase:liquibase-core:4.30.0")
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
allOpen { annotation("jakarta.ws.rs.Path"); annotation("jakarta.enterprise.context.ApplicationScoped") }
tasks.war { archiveFileName.set("organization-web.war") }
tasks.processResources {
    from(rootProject.file("openapi/organization-service.yaml")) { into("META-INF"); rename { "openapi.yaml" } }
    from(rootProject.file("openapi/orgdirectory-service.yaml")) { into("META-INF"); rename { "openapi-orgdirectory.yaml" } }
}
tasks.test { useJUnitPlatform() }
