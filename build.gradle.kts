plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "io.github.ibrahimio"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2023.3.4")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
        instrumentationTools()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        name = "PSI Agent"
        version = "1.0.0-SNAPSHOT"
    }
    // Persist sandbox state (installed plugins like Copilot) across runIde restarts
    sandboxContainer = layout.projectDirectory.dir(".sandbox")
    buildSearchableOptions = false
}

kotlin {
    jvmToolchain(17)
}

// Headless task: run the IDE without GUI for faster PSI server startup
tasks.register("runHeadless") {
    dependsOn("runIde")
    doFirst {
        val jvmArgs = tasks.named<JavaExec>("runIde").get().jvmArgs ?: mutableListOf()
        jvmArgs.add("-Djava.awt.headless=true")
        tasks.named<JavaExec>("runIde").get().jvmArgs = jvmArgs
    }
}
