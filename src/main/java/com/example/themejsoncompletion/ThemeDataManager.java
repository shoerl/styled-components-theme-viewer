package com.example.themejsoncompletion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.example.themejsoncompletion.settings.ThemeJsonSettingsState;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages the loading, caching, and refreshing of theme data from JSON files.
 * This service is project-specific and loads theme information based on a central
 * `theme-imports.json` file. This file can be located either in a project-configured path
 * or fall back to a version bundled with the plugin.
 *
 * The `theme-imports.json` maps theme aliases to their respective theme JSON file paths.
 * Individual theme files are then loaded relative to the location of `theme-imports.json`
 * (if project-based) or from plugin resources.
 *
 * The class uses System.out and System.err for logging. For production,
 * consider replacing these with {@link com.intellij.openapi.diagnostic.Logger}.
 */
@Service(Service.Level.PROJECT)
public final class ThemeDataManager {

    // TODO: Replace System.out/err with this Logger instance throughout the class.
    private static final Logger LOG = Logger.getInstance(ThemeDataManager.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, Map<String, Object>> allThemes;
    private final Project project;

    /**
     * Constructs a new ThemeDataManager for the given project.
     * Initializes themes by calling {@link #loadThemes()}.
     *
     * @param project The current IntelliJ project.
     */
    public ThemeDataManager(Project project) {
        this.project = project;
        this.allThemes = loadThemes();
        // LOG.info("ThemeDataManager: Initialized and loaded themes for project: " + project.getName());
        System.out.println("ThemeDataManager: Initialized and loaded themes for project: " + project.getName());
    }

    /**
     * Retrieves the singleton instance of ThemeDataManager for the specified project.
     *
     * @param project The project for which the service is requested.
     * @return The instance of ThemeDataManager.
     */
    public static ThemeDataManager getInstance(Project project) {
        return project.getService(ThemeDataManager.class);
    }

    /**
     * Gets all loaded themes. Each theme is represented as a map of its properties.
     * The outer map's key is the theme alias (e.g., "MyDarkTheme"), and its value is the theme data map.
     *
     * @return An unmodifiable map of all loaded themes. Returns an empty map if no themes are loaded.
     */
    public synchronized Map<String, Map<String, Object>> getAllThemes() {
        // Consider returning a deep copy or truly unmodifiable view if downstream modification is a risk.
        return allThemes;
    }

    /**
     * Reloads all themes from their sources. This method is synchronized to prevent concurrent modification issues.
     * It first attempts to load `theme-imports.json` from a user-configured project path,
     * then falls back to the plugin's bundled resources.
     * After reloading, it updates the internal cache of themes.
     *
     * TODO: Implement a notification mechanism (e.g., using IntelliJ's MessageBus)
     *       to inform other components (like completion providers) that themes have been updated,
     *       allowing them to clear caches or refresh their state.
     */
    public synchronized void refreshThemes() {
        // LOG.info("ThemeDataManager: Refreshing themes for project: " + project.getName());
        System.out.println("ThemeDataManager: Refreshing themes for project: " + project.getName());
        this.allThemes = loadThemes();
        // LOG.info("ThemeDataManager: Themes reloaded. Current themes: " + this.allThemes.keySet());
        System.out.println("ThemeDataManager: Themes reloaded. Current themes: " + this.allThemes.keySet());
    }

    /**
     * Core logic for loading themes.
     * 1. Loads `theme-imports.json`:
     *    - Tries the path configured in {@link ThemeJsonSettingsState}.
    *    - If not found or not configured, falls back to `/theme-imports.json` from plugin resources.
     * 2. Parses `theme-imports.json` to get a map of theme aliases to theme file paths.
     * 3. For each theme entry:
     *    - If `theme-imports.json` was loaded from the project, attempts to load the theme file relative to it.
     *    - If that fails or `theme-imports.json` was from resources, attempts to load the theme file from plugin resources (stripping any leading `src/main/resources/` from the path).
     * 4. Parses each theme JSON file into a {@code Map<String, Object>}.
     *
     * @return An unmodifiable map containing all successfully loaded themes, keyed by their aliases.
     *         Returns an empty map if `theme-imports.json` cannot be found/parsed or no themes are loaded.
     */
    private Map<String, Map<String, Object>> loadThemes() {
        Map<String, String> importMap = Collections.emptyMap();
        InputStream importMapStream = null;
        boolean loadedFromProject = false; // Indicates if theme-imports.json was loaded from project path

        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        String configuredPath = (settings != null) ? settings.themeImportsJsonPath : "";

        VirtualFile themeImportsVirtualFile = null; // Represents theme-imports.json if loaded from project

        // Step 1: Attempt to load theme-imports.json from configured project path
        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            // LOG.debug("Attempting to load theme-imports.json from configured project path: " + configuredPath);
            System.out.println("ThemeDataManager: Attempting to load theme-imports.json from configured project path: " + configuredPath);
            if (project.getBasePath() == null) {
                // LOG.warn("Project base path is null, cannot load from relative project path: " + configuredPath);
                 System.err.println("ThemeDataManager: Project base path is null, cannot load from relative project path: " + configuredPath);
            } else {
                themeImportsVirtualFile = LocalFileSystem.getInstance().findFileByPath(
                    Paths.get(project.getBasePath()).resolve(configuredPath).toString()
                );

                if (themeImportsVirtualFile != null && themeImportsVirtualFile.exists()) {
                    try {
                        importMapStream = themeImportsVirtualFile.getInputStream();
                        loadedFromProject = true;
                        // LOG.info("Found theme-imports.json at configured project path: " + themeImportsVirtualFile.getPath());
                        System.out.println("ThemeDataManager: Found theme-imports.json at configured project path: " + themeImportsVirtualFile.getPath());
                    } catch (IOException e) {
                        // LOG.warn("Error opening stream for theme-imports.json from project path '" + configuredPath + "': " + e.getMessage(), e);
                        System.err.println("ThemeDataManager: Error opening stream for theme-imports.json from project path '" + configuredPath + "': " + e.getMessage());
                        importMapStream = null;
                    }
                } else {
                    // LOG.warn("Configured theme-imports.json not found at project path: " + Paths.get(project.getBasePath()).resolve(configuredPath).toString());
                    System.err.println("ThemeDataManager: Configured theme-imports.json not found at project path: " + Paths.get(project.getBasePath()).resolve(configuredPath).toString());
                }
            }
        }

        // Step 2: Fallback to loading theme-imports.json from plugin resources
        if (importMapStream == null) {
            // LOG.info("Falling back to loading theme-imports.json from plugin resources.");
            System.out.println("ThemeDataManager: Falling back to loading theme-imports.json from plugin resources.");
            importMapStream = ThemeDataManager.class.getResourceAsStream("/theme-imports.json");
            if (importMapStream == null) {
                // LOG.error("/theme-imports.json not found in resources! Theme autocompletion will be severely limited or unavailable.");
                System.err.println("ThemeDataManager: Error: /theme-imports.json not found in resources! Theme autocompletion will not be available.");
                return Collections.emptyMap();
            }
        }

        // Step 3: Parse theme-imports.json
        try {
            importMap = objectMapper.readValue(importMapStream, new TypeReference<Map<String, String>>() {});
            // LOG.info("Loaded importMap (" + (loadedFromProject ? "project" : "resources") + "): " + importMap.keySet());
            System.out.println("ThemeDataManager: Loaded importMap (" + (loadedFromProject ? "project" : "resources") + "): " + importMap);
        } catch (IOException e) {
            // LOG.error("Error parsing theme-imports.json (" + (loadedFromProject ? "project" : "resources") + ")", e);
            // System.err.println("ThemeDataManager: Error parsing theme-imports.json (" + (loadedFromProject ? "project" : "resources") + "): " + e.getMessage());
            // e.printStackTrace(); // Keep stack trace for critical failure
            LOG.error("Error parsing theme-imports.json (" + (loadedFromProject ? "project" : "resources") + ")", e);
            return Collections.emptyMap();
        } finally {
            if (importMapStream != null) {
                try {
                    importMapStream.close();
                } catch (IOException e) {
                    // LOG.warn("Failed to close importMapStream", e);
                }
            }
        }

        if (importMap.isEmpty()) {
            // LOG.warn("Import map is empty. No themes will be loaded.");
            return Collections.emptyMap();
        }

        Map<String, Map<String, Object>> tempAllThemes = new HashMap<>();
        // Step 4: Load individual themes based on the importMap
        for (Map.Entry<String, String> entry : importMap.entrySet()) {
            String alias = entry.getKey();
            String themePathInImport = entry.getValue(); // Path from theme-imports.json
            InputStream themeStream = null;
            String loadedThemePathDescription = ""; // For logging

            // Try loading from project if theme-imports.json was from project
            if (loadedFromProject && themeImportsVirtualFile != null && themeImportsVirtualFile.getParent() != null) {
                VirtualFile themeFile = themeImportsVirtualFile.getParent().findFileByRelativePath(themePathInImport);
                if (themeFile != null && themeFile.exists()) {
                    try {
                        themeStream = themeFile.getInputStream();
                        loadedThemePathDescription = "project path: " + themeFile.getPath();
                        // LOG.info("Attempting to load theme '" + alias + "' from " + loadedThemePathDescription);
                        System.out.println("ThemeDataManager: Attempting to load theme '" + alias + "' from project path: " + themeFile.getPath());
                    } catch (IOException e) {
                        // LOG.warn("Error opening stream for theme '" + alias + "' from project path '" + themeFile.getPath() + "': " + e.getMessage(), e);
                        System.err.println("ThemeDataManager: Error opening stream for theme '" + alias + "' from project path '" + themeFile.getPath() + "': " + e.getMessage());
                    }
                } else {
                    // LOG.warn("Theme file '" + alias + "' not found at project relative path: " + themePathInImport + " (relative to " + themeImportsVirtualFile.getParent().getPath() + ")");
                    System.err.println("ThemeDataManager: Theme file '" + alias + "' not found at project relative path: " + themePathInImport + " (relative to " + themeImportsVirtualFile.getParent().getPath() + ")");
                }
            }

            // Fallback or default: load from resources
            if (themeStream == null) {
                // Ensure resource path starts with '/' and doesn't include typical source directory prefixes.
                String resourcePath = "/" + themePathInImport.replaceAll("^(src/main/resources/|resources/)", "");
                loadedThemePathDescription = "classpath resource '" + resourcePath + "'";
                // LOG.info("Attempting to load theme '" + alias + "' from " + loadedThemePathDescription + " (original path: '" + themePathInImport + "')");
                System.out.println("ThemeDataManager: Attempting to load theme '" + alias + "' from classpath resource '" + resourcePath + "' (original path: '" + themePathInImport + "')");
                themeStream = ThemeDataManager.class.getResourceAsStream(resourcePath);
                if (themeStream == null) {
                    // LOG.warn("Error: Theme file '" + alias + "' not found at classpath resource path: '" + resourcePath + "'");
                     System.err.println("ThemeDataManager: Error: Theme file '" + alias + "' not found at classpath resource path: '" + resourcePath + "'");
                }
            }

            if (themeStream != null) {
                try {
                    Map<String, Object> themeData = objectMapper.readValue(themeStream, new TypeReference<Map<String, Object>>() {});
                    if (themeData != null && !themeData.isEmpty()) {
                        tempAllThemes.put(alias, themeData);
                        // LOG.debug("Successfully parsed theme '" + alias + "' from " + loadedThemePathDescription);
                    } else {
                        // LOG.warn("Theme file '" + alias + "' (" + loadedThemePathDescription + ") was parsed as empty or null.");
                        System.err.println("ThemeDataManager: Warning: Theme file '" + alias + "' (" + themePathInImport + ") was parsed as empty or null.");
                    }
                } catch (Exception e) {
                    // LOG.error("Error loading/parsing theme '" + alias + "' from " + loadedThemePathDescription + ": " + e.getMessage(), e);
                    // System.err.println("ThemeDataManager: Error loading/parsing theme '" + alias + "' from path '" + themePathInImport + "': " + e.getMessage());
                    // e.printStackTrace(); // Keep stack trace for critical failure
                    LOG.error("Error loading/parsing theme '" + alias + "' from " + loadedThemePathDescription, e);
                } finally {
                    try {
                        themeStream.close();
                    } catch (IOException e) { /* LOG.warn("Failed to close themeStream for " + alias, e); */ }
                }
            }
        }

        if (tempAllThemes.isEmpty() && !importMap.isEmpty()) {
            // LOG.warn("Import map was loaded, but no themes were successfully parsed or all theme files were empty/corrupted.");
             System.err.println("ThemeDataManager: Warning: Import map was loaded, but no themes were successfully parsed or all theme files were empty/corrupted.");
        } else if (!tempAllThemes.isEmpty()) {
            // LOG.info("Successfully loaded and parsed themes: " + String.join(", ", tempAllThemes.keySet()));
            System.out.println("ThemeDataManager: Successfully loaded and parsed themes: " + String.join(", ", tempAllThemes.keySet()));
        }
        return Collections.unmodifiableMap(tempAllThemes);
    }
}
//                e.printStackTrace();
//            }
//        }

//        if (tempAllThemes.isEmpty() && !importMap.isEmpty()) {
//             System.err.println("ThemeDataManager: Warning: Import map was loaded, but no themes were successfully parsed or all theme files were empty/corrupted.");
//        } else if (!tempAllThemes.isEmpty()) {
//            System.out.println("ThemeDataManager: Successfully loaded and parsed themes: " + String.join(", ", tempAllThemes.keySet()));
//        }
//        return Collections.unmodifiableMap(tempAllThemes);
//    }
//}
