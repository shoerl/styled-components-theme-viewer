package com.example.themejsoncompletion

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.AsyncFileListener // Using AsyncFileListener for modern API
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

// Placeholder for how the ThemeJsonCompletionContributor might expose its data or a refresh method
// This will likely need to be a proper service or a static holder in a real plugin.
object ThemeRefreshTrigger {
    fun refreshThemes() {
        // In a real implementation, this would trigger reloading in ThemeJsonCompletionContributor
        println("ThemeJsonStartupActivity: ThemeRefreshTrigger: Firing refreshThemes(). Implement actual reload logic here.")
        // For example, by calling a static method on ThemeJsonCompletionContributor or an application/project service.
    }

    fun getThemeFileNames(): List<String> {
        // In a real plugin, this would dynamically get the list of watched files
        // from the theme-imports.json or a similar configuration.
        // For now, hardcoding common names.
        // This ideally should be driven by the actual paths in theme-imports.json
        return listOf("theme.json", "dark-theme.json") 
    }
}

class ThemeJsonStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        val busConnection = project.messageBus.connect()
        
        busConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: MutableList<out VFileEvent>) {
                val themeFileNames = ThemeRefreshTrigger.getThemeFileNames()
                var needsRefresh = false
                for (event in events) {
                    if (event.path != null && themeFileNames.any { event.path.endsWith(it) }) {
                        println("ThemeJsonStartupActivity: Detected change in a theme file: ${event.path}")
                        needsRefresh = true
                        break 
                    }
                }
                if (needsRefresh) {
                    // This is where you would re-parse JSON and update the allThemes map.
                    // For this example, we'll just print a message.
                    // In a real plugin, you'd need a robust way to update the
                    // ThemeJsonCompletionContributor's data.
                    println("ThemeJsonStartupActivity: A theme JSON file changed. Triggering refresh logic...")
                    ThemeRefreshTrigger.refreshThemes() 
                }
            }
        })
        println("ThemeJsonStartupActivity: Registered VFS listener for theme file changes using BulkFileListener.")
    }
}
