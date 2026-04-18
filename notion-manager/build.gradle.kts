import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.StandardOpenOption

plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "9.0.0-beta16"
}

group = "dag.je_dog"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jraf:klibnotion:1.12.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(20)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    relocate("io.ktor", "klibnotion.io.ktor")
    mergeServiceFiles()

    doLast {
        val outputJar = archiveFile.get().asFile
        val servicePath =
            "META-INF/services/klibnotion.io.ktor.serialization.kotlinx.KotlinxSerializationExtensionProvider"
        val uri = URI.create("jar:${outputJar.toURI()}")
        FileSystems.newFileSystem(uri, mapOf("create" to "false")).use { fileSystem ->
            val file = fileSystem.getPath("/$servicePath")
            Files.createDirectories(file.parent)
            Files.writeString(
                file,
                "klibnotion.io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
        }
    }
}
