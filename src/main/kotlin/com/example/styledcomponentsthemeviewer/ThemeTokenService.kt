package com.example.styledcomponentsthemeviewer

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.io.InputStreamReader

@Service(Service.Level.PROJECT)
class ThemeTokenService(private val project: Project) : Disposable {

    private var themeTokens: Map<String, Any> = emptyMap()
    private var tokenFile: VirtualFile? = null
    private val tokenFileName = ".theme-tokens.json"
    private var loadedFromFile: Boolean = false // Flag to indicate if tokens were last loaded from file

    init {
        // Attempt to load from file on initialization as a default/fallback
        loadTokensFromFile()
        subscribeToFileChanges()
    }

    fun getTokens(): Map<String, Any> = themeTokens

    fun getTokenValue(tokenKey: String): Any? = themeTokens[tokenKey]

    /**
     * Updates the theme tokens directly from a map (e.g., from PSI analysis).
     * This will mark the tokens as not being sourced from the file.
     */
    fun updateTokens(newTokens: Map<String, Any>, source: String = "psi") {
        thisLogger().info("Updating tokens directly from $source. Count: ${newTokens.size}")
        themeTokens = newTokens
        loadedFromFile = false
        // TODO: Consider broadcasting a token change event if other components need to react immediately.
        // project.messageBus.syncPublisher(THEME_TOKENS_CHANGED_TOPIC).tokensChanged()
    }

    /**
     * Loads tokens from the .theme-tokens.json file.
     * Can be called to explicitly reload from file.
     */
    fun loadTokensFromFile() {
        thisLogger().info("Attempting to load $tokenFileName for project: ${project.name}")
        val projectBasePath = project.basePath ?: run {
            thisLogger().warn("Project base path is null, cannot load theme tokens.")
            themeTokens = emptyMap()
            loadedFromFile = false
            return
        }

        tokenFile = LocalFileSystem.getInstance().findFileByPath(projectBasePath)?.findChild(tokenFileName)

        tokenFile?.let { file ->
            if (file.exists() && !file.isDirectory) {
                try {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    val reader = InputStreamReader(file.inputStream, file.charset)
                    themeTokens = Gson().fromJson(reader, type)
                    loadedFromFile = true
                    thisLogger().info("Successfully loaded ${themeTokens.size} tokens from $tokenFileName")
                } catch (e: Exception) {
                    thisLogger().error("Error parsing $tokenFileName: ${e.message}", e)
                    themeTokens = emptyMap()
                    loadedFromFile = false
                }
            } else {
                thisLogger().warn("$tokenFileName not found or is a directory at project root.")
                themeTokens = emptyMap()
                loadedFromFile = false
            }
        } ?: run {
            thisLogger().warn("$tokenFileName not found at project root.")
            themeTokens = emptyMap()
            loadedFromFile = false
        }
        // TODO: Consider broadcasting a token change event.
    }

    /**
     * This method is kept for the action that explicitly runs the script.
     * It reloads from the file, assuming the script has updated it.
     */
    fun forceReloadFromFile() {
        thisLogger().info("Forcing reload from $tokenFileName for project: ${project.name}")
        loadTokensFromFile()
    }


    private fun subscribeToFileChanges() {
        project.messageBus.connect(this).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                for (event in events) {
                    if (event.file?.name == tokenFileName &&
                        event.file?.parent?.path == project.basePath) {
                        thisLogger().info("$tokenFileName changed, reloading tokens from file.")
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed) {
                                loadTokensFromFile() // Always reload from file if the file itself changes
                            }
                        }
                        return
                    }
                }
            }
        })
        thisLogger().info("Subscribed to VFS changes for $tokenFileName")
    }

    override fun dispose() {
        thisLogger().info("ThemeTokenService disposed for project: ${project.name}")
    }

    companion object {
        fun getInstance(project: Project): ThemeTokenService =
            project.getService(ThemeTokenService::class.java)
    }
}
