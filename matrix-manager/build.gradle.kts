plugins {
    kotlin("jvm") version "2.3.10"
}

group = "dag.je_dog"
version = "1.0-SNAPSHOT"
val trixnityVersion = "5.4.0"
val ktorVersion = "3.4.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation(platform("de.connect2x.trixnity:trixnity-bom:$trixnityVersion"))
    implementation("de.connect2x.trixnity:trixnity-client-jvm:$trixnityVersion")
    implementation("de.connect2x.trixnity:trixnity-crypto-driver-libolm-jvm:${trixnityVersion}")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(20)
}

tasks.test {
    useJUnitPlatform()
}
