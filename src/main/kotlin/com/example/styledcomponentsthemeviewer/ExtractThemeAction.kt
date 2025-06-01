package com.example.styledcomponentsthemeviewer

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.example.styledcomponentsthemeviewer.PsiThemeDiscoverer
import com.example.styledcomponentsthemeviewer.ThemeTokenService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class ExtractThemeAction : AnAction("Extract Theme Tokens") {

    private val logger = thisLogger()
    private val PLUGIN_ID = "org.example.project.theme-tokens-plugin" // Replace with your actual plugin ID

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        FileDocumentManager.getInstance().saveAllDocuments() // Save any unsaved changes

        val themeTokenService = ThemeTokenService.Companion.getInstance(project)

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Extracting Theme Tokens", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true // Overall task is indeterminate until script runs
                indicator.text = "Discovering theme file..."
                logger.info("Starting theme extraction process.")

                val psiDiscoverer = PsiThemeDiscoverer(project)
                val themeFilePath: String? = try {
                    psiDiscoverer.discoverThemeInProject()
                } catch (ex: Exception) {
                    logger.error("Error during PSI-based theme discovery: ${ex.message}", ex)
                    showErrorDialog(project, "Error during theme discovery: ${ex.message}")
                    null
                }

                if (indicator.isCanceled) {
                    logger.info("Theme extraction cancelled by user.")
                    return
                }

                if (themeFilePath != null) {
                    logger.info("Discovered theme file path: $themeFilePath")
                    indicator.text = "Extracting tokens from: ${File(themeFilePath).name}"
                    indicator.isIndeterminate = false // Script execution has a definite start/end

                    // Resolve script path from plugin resources or installation directory
                    val scriptPath: String? = getBundledScriptPath("scripts/extract-theme.js")

                    if (scriptPath == null) {
                        val errorMsg = "Could not find 'extract-theme.js' script within the plugin."
                        logger.error(errorMsg)
                        showErrorDialog(project, errorMsg)
                        return
                    }

                    val projectBasePath = project.basePath ?: run {
                        logger.error("Project base path not found.")
                        showErrorDialog(project, "Project base path not found.")
                        return
                    }

                    val outputJsonPath = Paths.get(projectBasePath, ".theme-tokens.json").toString()

                    logger.info("Executing Node.js script directly: $scriptPath with arguments: $themeFilePath, $outputJsonPath")
                    logger.info("Outputting .theme-tokens.json to: $outputJsonPath")

                    try {
                        val command = listOf("node", scriptPath, themeFilePath, outputJsonPath)
                        val processBuilder = ProcessBuilder(command)
                        processBuilder.directory(File(projectBasePath))
                        processBuilder.redirectErrorStream(true) // Combine stdout and stderr

                        val process = processBuilder.start()
                        val processOutputText = process.inputStream.bufferedReader().use { it.readText() }
                        // Wait for the process to complete, with a timeout
                        val exited = process.waitFor(60, TimeUnit.SECONDS)

                        if (exited) {
                            val exitCode = process.exitValue()
                            if (exitCode == 0) {
                                logger.info("Theme extraction script finished successfully. Output:\n$processOutputText")
                                ApplicationManager.getApplication().invokeAndWait {
                                    themeTokenService.forceReloadFromFile()
                                }
                                showSuccessDialog(project, "Theme tokens extracted successfully from '${File(themeFilePath).name}' to '${File(outputJsonPath).name}'.")
                            } else {
                                val errorDetails = "Exit Code: $exitCode\nOutput (stdout/stderr):\n$processOutputText"
                                logger.warn("Theme extraction script failed or produced errors.\n$errorDetails")
                                val dialogMessage = "Node.js script execution failed (Exit Code: $exitCode). " +
                                        "Please check IntelliJ's idea.log (Help -> Show Log in...) for the full output. " +
                                        "Output: ${processOutputText.take(1000)}" // Show first 1000 chars of combined output
                                showErrorDialog(project, dialogMessage)
                            }
                        } else {
                            process.destroyForcibly() // Ensure the process is killed if it times out
                            val timeoutMsg = "Node.js script execution timed out after 60 seconds."
                            logger.warn(timeoutMsg)
                            showErrorDialog(project, timeoutMsg)
                        }
                    } catch (ex: IOException) {
                        val msg = "Failed to start 'node' command. Is Node.js installed and in your system PATH? Error: ${ex.message}"
                        logger.error(msg, ex)
                        showErrorDialog(project, msg)
                    } catch (ex: InterruptedException) {
                        Thread.currentThread().interrupt() // Restore interruption status
                        val msg = "Node.js script execution was interrupted. Error: ${ex.message}"
                        logger.warn(msg, ex)
                        showErrorDialog(project, msg)
                    } catch (ex: Exception) {
                        throw ex
//                        logger.error("Error running theme extraction script with ProcessBuilder: ${ex.message}", ex)
                        showErrorDialog(project, "An unexpected error occurred while running the extraction script: ${ex.message}")
                    }

                } else {
                    logger.info("No theme file was automatically discovered.")
                    showInfoDialog(project, "Could not automatically discover the theme file. Please ensure it's configured correctly or accessible.")
                }
            }
        })
    }

    private fun getBundledScriptPath(relativePath: String): String? {
        logger.debug("Attempting to find script '$relativePath'.")

        // Try loading via classloader (works for resources in src/main/resources or if scripts/ is on classpath)
        try {
            val resourceUrl = javaClass.classLoader.getResource(relativePath)
            if (resourceUrl != null) {
                logger.info("Resource URL found via classloader: $resourceUrl")
                if ("jar" == resourceUrl.protocol) {
                    logger.info("Script is in a JAR, extracting to a temporary file.")
                    return extractScriptFromJar(resourceUrl, relativePath)
                } else { // Typically "file" protocol for dev environment
                    val scriptFile = File(resourceUrl.toURI())
                    if (scriptFile.exists()) {
                        logger.info("Found script via classloader (file protocol) at: ${scriptFile.absolutePath}")
                        return scriptFile.absolutePath
                    } else {
                        logger.warn("Script file not found at URI from classloader: ${scriptFile.absolutePath}")
                    }
                }
            } else {
                logger.info("Script not found via classloader resource: $relativePath. Trying plugin path.")
            }
        } catch (e: Exception) { // Catch broad exceptions for classloader/URI issues
            logger.warn("Error accessing script via classloader: ${e.message}", e)
        }

        // Fallback: Try resolving from plugin installation directory
        // This is important if the 'scripts' directory is not part of 'resources' and thus not on the classpath.
        logger.info("Attempting to find script via plugin directory method for '$relativePath'.")
        val plugin = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID))
        if (plugin == null) {
            logger.error("Could not find plugin descriptor for ID: $PLUGIN_ID. Script not found.")
            return null
        }

        val pluginBasePath = plugin.pluginPath.toFile()
        val scriptFile = File(pluginBasePath, relativePath)

        if (pluginBasePath.isFile && pluginBasePath.extension.equals("jar", ignoreCase = true)) {
            // Plugin is running from a JAR file, script needs to be extracted from this JAR
            logger.info("Plugin is a JAR file. Attempting to extract '$relativePath' from it.")
            // We need to construct a "jar:" URL to use extractScriptFromJar or read from JarFile directly
            // For simplicity, we'll assume `relativePath` is the path *within* the JAR.
            val jarUrl = pluginBasePath.toURI().toURL() // URL to the JAR itself
            // It's tricky to get a URL to an entry *inside* another JAR this way directly for getResource
            // Instead, we'll try to open the plugin's JAR and extract the entry.
            // A simpler way for this specific fallback: if we couldn't get it via classloader's getResource,
            // and the plugin is a JAR, then the script is likely not being found correctly by classloader.
            // The most robust way if it's *not* in resources is to ensure it's copied to output directory
            // when building and then rely on pluginPath method.

            // The classloader method with `extractScriptFromJar` should ideally handle this if the script is packaged correctly.
            // If we reach here and it's a JAR, it implies classloader didn't find it.
            // This could mean the script isn't in a location the classloader searches (e.g. not in `resources`).
            // For a script in `scripts/` at the root of a JAR, classloader should find it.
            // Let's refine the logging. If classloader failed and we are here, and it's a JAR, the script is likely missing or path is wrong.
            logger.warn(
                "Script not found via classloader. Plugin is a JAR ('${pluginBasePath.absolutePath}'), " +
                        "and script '$relativePath' was not found within it using classloader. " +
                        "Ensure the script is correctly packaged."
            )
            // Attempt extraction as a last resort if we assume relativePath is valid inside the JAR
            // This part is tricky because we don't have a direct resourceUrl here.
            // For now, we will rely on the classloader to find it if it's in the JAR.
            // If it's not found by classloader, and plugin is a JAR, it's an issue with packaging or path.
            return null // Or try a more complex JarFile-based extraction if truly necessary.

        } else if (scriptFile.exists()) { // Plugin is an exploded directory (dev mode or unzipped)
            logger.info("Found script via plugin path (exploded directory): ${scriptFile.absolutePath}")
            return scriptFile.absolutePath
        }

        logger.error("Script '$relativePath' not found at plugin path: ${scriptFile.absolutePath} nor via classloader.")
        return null
    }

    private fun extractScriptFromJar(resourceUrl: URL, scriptNameHint: String): String? {
        return try {
            val tempFile = Files.createTempFile("extract-theme-script-", ".js").toFile()
            tempFile.deleteOnExit()
            resourceUrl.openStream().use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            logger.info("Script '$scriptNameHint' extracted from JAR to temporary file: ${tempFile.absolutePath}")
            tempFile.absolutePath
        } catch (e: Exception) {
            logger.error("Failed to extract script '$scriptNameHint' from JAR: ${e.message}", e)
            null
        }
    }

    private fun showSuccessDialog(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showInfoMessage(project, message, "Theme Token Extraction")
        }
    }

    private fun showErrorDialog(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, message, "Theme Token Extraction Error")
        }
    }

    private fun showInfoDialog(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showInfoMessage(project, message, "Theme Token Extraction")
        }
    }
}
