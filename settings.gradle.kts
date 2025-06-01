pluginManagement {
    repositories {
        gradlePluginPortal()     // <— This is where 'org.jetbrains.intellij' is published
        mavenCentral()           // (optional) in case you also resolve some custom plugins from Maven Central
        // you can add other repositories here if you host your own private plugin jars
    }
}


rootProject.name = "StyledComponentsThemeViewer"