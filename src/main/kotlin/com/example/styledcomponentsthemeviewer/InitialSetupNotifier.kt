package com.example.styledcomponentsthemeviewer

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem

class InitialSetupNotifier : ProjectActivity {
    private val logger = thisLogger()

    override suspend fun execute(project: Project) {
        val projectBasePath = project.basePath
        if (projectBasePath == null) {
            logger.warn("Project base path is null. Cannot check for initial setup.")
            return
        }

        val properties = PropertiesComponent.getInstance(project)
        val notificationShownKey = "theme.tokens.initial.notification.shown"

        if (properties.isTrueValue(notificationShownKey)) {
            logger.info("Initial setup notification already shown for this project.")
            return
        }

        val tokenFileName = ".theme-tokens.json"
        val tokenFilePath = "$projectBasePath/$tokenFileName"
        val tokenFile = LocalFileSystem.getInstance().findFileByPath(tokenFilePath)

        if (tokenFile == null || !tokenFile.exists()) {
            logger.info(".theme-tokens.json not found. Showing initial setup notification.")

            val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("MUI Theme Tokens Support")
            if (notificationGroup == null) {
                logger.warn("Notification group 'MUI Theme Tokens Support' not found. Please ensure it's registered in plugin.xml.")
                // Fallback or alternative handling if the group isn't registered, though the task includes registering it.
                // For now, we'll proceed assuming it will be registered. If it's null, createNotification will fail.
            }

            val notification = notificationGroup?.createNotification(
                "Theme Tokens Support", // Title
                "To enable theme token autocompletion, color previews, and documentation for this project, " +
                "please run the 'Extract/Refresh Theme Tokens' action (e.g., from Tools menu or Project view right-click).", // Content
                NotificationType.INFORMATION
            )

            if (notification != null) {
                notification.notify(project)
                properties.setValue(notificationShownKey, true)
                logger.info("Initial setup notification shown and property set.")
            } else {
                logger.error("Failed to create notification. Notification group 'MUI Theme Tokens Support' might be missing or not registered correctly.")
            }
        } else {
            logger.info(".theme-tokens.json found. No need for initial setup notification.")
            // If the file exists, we can also mark the notification as "shown" to prevent it
            // from appearing if the file is later deleted and the project reopened before the action is run.
            // This makes sense as the user has already somehow generated the file.
            properties.setValue(notificationShownKey, true)
        }
    }
}
