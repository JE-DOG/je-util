plugins {
    kotlin("jvm") version "2.3.10"
    application
}

group = "dag.je_dog"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation(project(path = ":notion-manager", configuration = "shadowRuntimeElements"))
    implementation(project(":matrix-manager"))
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(20)
}

application {
    mainClass.set("dag.je_dog.MainKt")
    applicationName = "je-util"
}

tasks.test {
    useJUnitPlatform()
}
