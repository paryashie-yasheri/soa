plugins { id("io.quarkus"); kotlin("jvm"); kotlin("plugin.allopen") }
group = "ru.ifmo.soa"; version = "1.0.0"
dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.20.1"))
    implementation("io.quarkus:quarkus-resteasy")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach { compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
allOpen { annotation("jakarta.ws.rs.Path"); annotation("jakarta.enterprise.context.ApplicationScoped") }
