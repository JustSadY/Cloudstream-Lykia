rootProject.name = "test-araci"

val repo = providers.gradleProperty("repo").map { file(it) }.getOrElse(rootDir.parentFile)
repo.listFiles()!!
    .filter { it.isDirectory && File(it, "build.gradle.kts").exists() && it.name !in setOf("__Temel", "test-araci") && !it.name.startsWith(".") }
    .forEach {
        val dir = file("modules/${it.name}").apply { mkdirs() }
        include(":${it.name}")
        project(":${it.name}").projectDir = dir
    }
