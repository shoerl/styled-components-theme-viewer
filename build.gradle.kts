// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html

plugins {
  id("java")
  id("org.jetbrains.intellij.platform") version "2.6.0"
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
  intellijPlatform {
    defaultRepositories()
  }
}

// Dependencies block consolidated from user input and existing file
dependencies {
    intellijPlatform {
        create("IU", "2025.1") // Updated from intellijIdeaCommunity to create("IU", ...)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        bundledPlugin("JavaScript")
        bundledPlugin("NodeJS")
        // Add necessary plugin dependencies for compilation here, example:
        // bundledPlugin("com.intellij.java")
    }
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.0")
}

// IntelliJ Platform configuration from user input
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            // untilBuild = null // Explicitly setting null if needed, or omitting if that's the default
        }

        changeNotes = """
      Initial version
    """.trimIndent()
    }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
}

tasks {
  withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
  }

  // Configure runIde task to run in headless mode for CI/sandbox
  withType<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask> { 
    systemProperty("java.awt.headless", "true")
  }
}
