package com.example.themejsoncompletion;

import com.example.themejsoncompletion.settings.ThemeJsonSettingsState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ThemeRefreshTrigger {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static List<String> cachedThemeFileNames = null; // Project specific cache might be better
    private static long lastLoadTime = 0; // Also project specific
    private static final long CACHE_DURATION_MS = 60000; // Cache for 1 minute

    /**
     * Returns a list of theme file names that the plugin should monitor for changes.
     * These names are extracted from the values in theme-imports.json (from project or resources).
     * The result is cached. This method needs project context to access settings.
     *
     * @param project The current project.
     * @return A list of theme file names.
     */
    public static synchronized List<String> getThemeFileNames(Project project) {
        // Note: Caching here is tricky if it's static and shared across projects.
        // For simplicity, we'll keep the static cache but acknowledge it's not ideal for multi-project scenarios
        // unless the cache key includes project identifier or we use a project service for this cache.
        // However, this method is called by ThemeJsonStartupActivity which is project-specific.
        // A better approach would be for ThemeJsonStartupActivity to get this list once and store it,
        // or for this method to not be static and be part of a project service.

        long currentTime = System.currentTimeMillis();
        if (cachedThemeFileNames != null && (currentTime - lastLoadTime) < CACHE_DURATION_MS) {
            // This cache doesn't distinguish by project, which is a flaw if multiple projects are open
            // and have different settings. For now, we proceed with this simplification.
            // System.out.println("ThemeRefreshTrigger: Returning cached theme file names for project " + project.getName());
            // return cachedThemeFileNames;
            // Given the flaw, let's recalculate if project context is available, or make cache project-aware.
            // For now, let's assume the call from StartupActivity is the primary one and it passes project.
        }

        Map<String, String> importMap = Collections.emptyMap();
        InputStream importMapStream = null;
        boolean loadedFromProject = false;

        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        String configuredPath = (settings != null) ? settings.themeImportsJsonPath : "";
        VirtualFile themeImportsVirtualFile = null;


        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            if (project.getBasePath() != null) {
                themeImportsVirtualFile = LocalFileSystem.getInstance().findFileByPath(
                        Paths.get(project.getBasePath()).resolve(configuredPath).toString()
                );
                if (themeImportsVirtualFile != null && themeImportsVirtualFile.exists()) {
                    try {
                        importMapStream = themeImportsVirtualFile.getInputStream();
                        loadedFromProject = true;
                    } catch (IOException e) {
                        System.err.println("ThemeRefreshTrigger: Error opening configured theme-imports.json: " + e.getMessage());
                        importMapStream = null;
                    }
                }
            }
        }

        if (importMapStream == null) { // Fallback to resources
            importMapStream = ThemeRefreshTrigger.class.getResourceAsStream("/theme-imports.json");
        }

        if (importMapStream == null) {
            System.err.println("ThemeRefreshTrigger: Critical: theme-imports.json not found in configured path or resources.");
            cachedThemeFileNames = Collections.emptyList();
            lastLoadTime = currentTime;
            return cachedThemeFileNames;
        }

        try {
            importMap = objectMapper.readValue(importMapStream, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            System.err.println("ThemeRefreshTrigger: Error parsing theme-imports.json: " + e.getMessage());
            importMap = Collections.emptyMap();
        } finally {
            try {
                importMapStream.close();
            } catch (IOException e) { /* ignore */ }
        }

        if (importMap.isEmpty()) {
            cachedThemeFileNames = Collections.emptyList();
        } else {
            // If loaded from project, the paths inside importMap are relative to theme-imports.json's directory.
            // If loaded from resources, paths are relative to resources root (e.g. "theme.json" or "themes/mytheme.json").
            // The VFS listener in ThemeJsonStartupActivity works with *absolute* paths or paths relative to project modules.
            // For simplicity, we just extract file names. The listener checks event.getPath().endsWith(themeFileName).
            // This means if multiple files with the same name exist (e.g. /foo/theme.json and /bar/theme.json)
            // and "theme.json" is in our list, changes to *either* will trigger a refresh. This is usually acceptable.

            List<String> names = new ArrayList<>();
            for (String pathValue : importMap.values()) {
                try {
                    names.add(Paths.get(pathValue).getFileName().toString());
                } catch (Exception e) {
                    System.err.println("ThemeRefreshTrigger: Invalid path in import map: " + pathValue);
                }
            }
            cachedThemeFileNames = names.stream().distinct().collect(Collectors.toList());
            System.out.println("ThemeRefreshTrigger: Determined theme file names for project " + project.getName() + " (" + (loadedFromProject ? "project" : "resources") + "): " + cachedThemeFileNames);
        }
        lastLoadTime = currentTime;
        return cachedThemeFileNames;
    }

    public static synchronized void clearCache() {
        System.out.println("ThemeRefreshTrigger: Clearing cached theme file names.");
        cachedThemeFileNames = null;
        lastLoadTime = 0;
    }

    /**
     * Placeholder method to simulate triggering a refresh of theme data.
     * <p>
     * In a real implementation, this would trigger reloading and re-parsing of theme
     * JSON files and update the data structures used by {@link ThemeJsonCompletionContributor}.
     * This might involve interacting with an application or project service that holds the theme data,
     * or re-initializing parts of the {@link ThemeJsonCompletionContributor}.
     * </p>
     * @param project The current project. This parameter is included because the
     *                {@link ThemeJsonStartupActivity} calls it with the project,
     *                allowing future implementations to use project-specific services
     *                if needed for the refresh logic.
     */
    public static void refreshThemes(Project project) {
        if (project == null || project.isDisposed()) {
            System.err.println("ThemeRefreshTrigger: Project is null or disposed. Cannot refresh themes.");
            return;
        }
        ThemeDataManager dataManager = ThemeDataManager.getInstance(project);
        if (dataManager != null) {
            System.out.println("ThemeRefreshTrigger: Requesting ThemeDataManager to refresh themes for project: " + project.getName());
            dataManager.refreshThemes();
        } else {
            System.err.println("ThemeRefreshTrigger: ThemeDataManager service not found for project: " + project.getName() + ". Cannot refresh themes.");
            // This might happen if the plugin is being disabled or if there's an issue with service registration/initialization.
        }
    }
}
