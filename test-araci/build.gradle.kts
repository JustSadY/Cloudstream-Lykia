import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.3.0" apply false
}

val repo = providers.gradleProperty("repo").map { file(it) }.getOrElse(rootDir.parentFile)

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "application")

    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }

    val pluginDir = File(repo, name)
    extensions.configure<KotlinJvmProjectExtension> {
        sourceSets.getByName("main").kotlin {
            srcDir(File(pluginDir, "src/main/kotlin"))
            srcDir(rootProject.file("common"))
            exclude("**/*Plugin.kt")
        }
    }

    dependencies {
        "implementation"("com.github.recloudstream.cloudstream:library-jvm:-SNAPSHOT")
        "implementation"("com.github.Blatzar:NiceHttp:0.4.18")
        "implementation"("org.jsoup:jsoup:1.22.1")
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        "implementation"("org.mozilla:rhino:1.8.1")
        "implementation"("org.json:json:20240303")
    }

    extensions.configure<JavaApplication> {
        mainClass.set("lykia.tester.TesterKt")
    }

    tasks.named<JavaExec>("run") {
        args(pluginDir.absolutePath)
        environment("VERBOSE", System.getenv("VERBOSE") ?: "0")
        environment("TEST_URL", providers.gradleProperty("url").getOrElse(""))
        environment("TEST_REF", providers.gradleProperty("ref").getOrElse(""))
        environment("TEST_PAGE", providers.gradleProperty("page").getOrElse(""))
    }
}
