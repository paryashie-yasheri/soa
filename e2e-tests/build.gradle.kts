plugins { kotlin("jvm") }

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.1")
    testImplementation("org.testcontainers:postgresql:1.20.6")
    testRuntimeOnly("org.postgresql:postgresql:42.7.5")
}

java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 1
    dependsOn(":organization-service:quarkusBuild", ":orgdirectory-service:quarkusBuild", ":client:quarkusBuild")
    systemProperty("soa.root", rootProject.projectDir.absolutePath)
    systemProperty("soa.e2e.work", layout.buildDirectory.dir("stack").get().asFile.absolutePath)
    outputs.upToDateWhen { false }
}
