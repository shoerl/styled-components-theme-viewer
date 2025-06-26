package com.example.themejsoncompletion;

import com.example.themejsoncompletion.settings.ThemeJsonSettingsState;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Utility class responsible for determining which theme files to monitor for changes
 * and for triggering a refresh of theme data when changes are detected.
 *
 * It reads `theme-imports.json` (from project settings or plugin resources) to find
 * the names of theme files. This list of names is then used by {@link ThemeJsonStartupActivity}
 * to filter VFS events.
 *
 * This class also provides the {@link #refreshThemes(Project)} method, which delegates
 * to {@link ThemeDataManager} to actually reload the theme data.
 */
public class ThemeRefreshTrigger {

    private static final Logger LOG = Logger.getInstance(ThemeRefreshTrigger.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Cache for theme file names. Keyed by project hash code to provide basic project-specific caching.
    // A more robust solution might use Project as key if ThemeDataManager itself becomes a true project service
    // or if this cache is managed within a project service.
    private static final Map<Integer, List<String>> projectCachedThemeFileNames = new ConcurrentHashMap<>();
    private static final Map<Integer, Long> projectLastLoadTime = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION_MS = 60000; // Cache for 1 minute

    /**
     * Retrieves a list of theme file names that the plugin should monitor for changes.
     * These names are extracted from the file paths specified in the `theme-imports.json` file.
     * The method first attempts to load `theme-imports.json` from a path configured in the
     * project's settings. If not found or not configured, it falls back to the
     * `theme-imports.json` bundled with the plugin resources.
     *
     * The results are cached per project for a short duration to avoid redundant file I/O
     * and parsing.
     *
     * @param project The current IntelliJ project, used to access project-specific settings
     *                and for caching.
     * @return A list of unique theme file names (e.g., "my-dark-theme.json"). Returns an empty
     *         list if `theme-imports.json` cannot be loaded or is empty.
     */
    public static synchronized List<String> getThemeFileNames(Project project) {
        if (project == null || project.isDisposed()) {
            LOG.warn("Cannot get theme file names: project is null or disposed.");
            return Collections.emptyList();
        }

        long currentTime = System.currentTimeMillis();
        Integer projectKey = project.hashCode(); // Simple project identifier for cache

        if (projectCachedThemeFileNames.containsKey(projectKey) &&
            (currentTime - projectLastLoadTime.getOrDefault(projectKey, 0L)) < CACHE_DURATION_MS) {
            // LOG.debug("Returning cached theme file names for project " + project.getName());
            return projectCachedThemeFileNames.get(projectKey);
        }

        Map<String, String> importMap = Collections.emptyMap();
        InputStream importMapStream = null;
        boolean loadedFromProjectSettings = false;
        String sourceDescription = "plugin resources"; // Default source

        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        String configuredPathStr = (settings != null) ? settings.themeImportsJsonPath : null;

        if (configuredPathStr != null && !configuredPathStr.trim().isEmpty()) {
            LOG.debug("Attempting to load theme-imports.json from configured project path: " + configuredPathStr + " for project " + project.getName());
            if (project.getBasePath() != null) {
                Path absoluteConfiguredPath = Paths.get(project.getBasePath()).resolve(configuredPathStr);
                VirtualFile themeImportsVirtualFile = LocalFileSystem.getInstance().findFileByNioPath(absoluteConfiguredPath);

                if (themeImportsVirtualFile != null && themeImportsVirtualFile.exists()) {
                    try {
                        importMapStream = themeImportsVirtualFile.getInputStream();
                        loadedFromProjectSettings = true;
                        sourceDescription = "project settings path: " + themeImportsVirtualFile.getPath();
                        LOG.info("Found theme-imports.json via project settings: " + themeImportsVirtualFile.getPath());
                    } catch (IOException e) {
                        LOG.warn("Error opening configured theme-imports.json from '" + themeImportsVirtualFile.getPath() + "': " + e.getMessage(), e);
                        importMapStream = null; // Ensure fallback
                    }
                } else {
                    LOG.warn("Configured theme-imports.json not found at project path: " + absoluteConfiguredPath);
                }
            } else {
                LOG.warn("Project base path is null. Cannot resolve configured path: " + configuredPathStr);
            }
        }

        // Fallback to resources if not loaded from project settings
        if (importMapStream == null) {
            LOG.info("Falling back to loading theme-imports.json from plugin resources for project " + project.getName());
            importMapStream = ThemeRefreshTrigger.class.getResourceAsStream("/theme-imports.json");
            sourceDescription = "plugin resources"; // Update source description
        }

        if (importMapStream == null) {
            LOG.error("Critical: theme-imports.json not found in configured path or plugin resources for project " + project.getName() + ". Theme file monitoring will be inactive.");
            projectCachedThemeFileNames.put(projectKey, Collections.emptyList());
            projectLastLoadTime.put(projectKey, currentTime);
            return Collections.emptyList();
        }

        try {
            importMap = objectMapper.readValue(importMapStream, new TypeReference<Map<String, String>>() {});
            LOG.debug("Successfully parsed theme-imports.json from " + sourceDescription + " for project " + project.getName());
        } catch (IOException e) {
            LOG.error("Error parsing theme-imports.json from " + sourceDescription + " for project " + project.getName() + ": " + e.getMessage(), e);
            importMap = Collections.emptyMap(); // Proceed with empty map on error
        } finally {
            try {
                importMapStream.close();
            } catch (IOException e) {
                LOG.warn("Failed to close stream for theme-imports.json from " + sourceDescription, e);
            }
        }

        List<String> finalThemeFileNames;
        if (importMap.isEmpty()) {
            LOG.info("Import map from " + sourceDescription + " is empty for project " + project.getName() + ". No theme files to monitor.");
            finalThemeFileNames = Collections.emptyList();
        } else {
            // Extract just the file names from the paths in the import map.
            // The VFS listener in ThemeJsonStartupActivity checks event.getPath().endsWith(themeFileName).
            // This is a simple but effective way to identify relevant files.
            List<String> names = new ArrayList<>();
            for (String pathValue : importMap.values()) {
                try {
                    // Using Paths.get().getFileName() is robust for extracting the name part of a path.
                    names.add(Paths.get(pathValue).getFileName().toString());
                } catch (InvalidPathException e) {
                    LOG.warn("Invalid path string '" + pathValue + "' in import map from " + sourceDescription + ". Skipping. Error: " + e.getMessage());
                }
            }
            finalThemeFileNames = names.stream().distinct().collect(Collectors.toList());
            LOG.info("Determined theme file names to monitor for project " + project.getName() + " (from " + sourceDescription + "): " + finalThemeFileNames);
        }

        projectCachedThemeFileNames.put(projectKey, finalThemeFileNames);
        projectLastLoadTime.put(projectKey, currentTime);
        return finalThemeFileNames;
    }

    /**
     * Clears the cache of theme file names for a specific project.
     * This might be useful if settings change and a re-evaluation is needed sooner than cache expiry.
     * @param project The project whose cache should be cleared.
     */
    public static synchronized void clearCache(Project project) {
        if (project != null) {
            Integer projectKey = project.hashCode();
            projectCachedThemeFileNames.remove(projectKey);
            projectLastLoadTime.remove(projectKey);
            LOG.info("Cleared cached theme file names for project: " + project.getName());
        }
    }

    /**
     * Triggers a refresh of the theme data for the specified project.
     * This method delegates the actual reloading and parsing of theme JSON files
     * to the {@link ThemeDataManager} service for the given project.
     *
     * @param project The project for which themes should be refreshed.
     */
    public static void refreshThemes(Project project) {
        if (project == null || project.isDisposed()) {
            LOG.warn("Project is null or disposed. Cannot refresh themes.");
            return;
        }
        ThemeDataManager dataManager = ThemeDataManager.getInstance(project);
        if (dataManager != null) {
            LOG.info("Requesting ThemeDataManager to refresh themes for project: " + project.getName());
            dataManager.refreshThemes();
        } else {
            LOG.error("ThemeDataManager service not found for project: " + project.getName() + ". Cannot refresh themes. This may indicate an issue with plugin initialization or project setup.");
        }
    }
}
