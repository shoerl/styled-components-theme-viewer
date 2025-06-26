package com.example.themejsoncompletion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.example.themejsoncompletion.settings.ThemeJsonSettingsState;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service(Service.Level.PROJECT)
public final class ThemeDataManager {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, Map<String, Object>> allThemes;
    private final Project project; // Store project for potential future use (e.g., project-specific paths)

    public ThemeDataManager(Project project) {
        this.project = project;
        this.allThemes = loadThemes();
        System.out.println("ThemeDataManager: Initialized and loaded themes for project: " + project.getName());
    }

    public static ThemeDataManager getInstance(Project project) {
        return project.getService(ThemeDataManager.class);
    }

    public synchronized Map<String, Map<String, Object>> getAllThemes() {
        // Return a copy or unmodifiable view if direct modification by consumers is a concern
        return allThemes;
    }

    public synchronized void refreshThemes() {
        System.out.println("ThemeDataManager: Refreshing themes for project: " + project.getName());
        this.allThemes = loadThemes();
        // TODO: Consider notifying listeners if themes actually changed, e.g., for UI updates or forcing re-completion.
        // This might involve using MessageBus for project-level messages.
        System.out.println("ThemeDataManager: Themes reloaded. Current themes: " + this.allThemes.keySet());
    }

    private Map<String, Map<String, Object>> loadThemes() {
        Map<String, String> importMap = Collections.emptyMap();
        InputStream importMapStream = null;
        boolean loadedFromProject = false;

        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        String configuredPath = (settings != null) ? settings.themeImportsJsonPath : "";

        VirtualFile themeImportsVirtualFile = null;

        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            System.out.println("ThemeDataManager: Attempting to load theme-imports.json from configured project path: " + configuredPath);
            if (project.getBasePath() == null) {
                 System.err.println("ThemeDataManager: Project base path is null, cannot load from relative project path: " + configuredPath);
            } else {
                // Try to find the file relative to the project root
                themeImportsVirtualFile = LocalFileSystem.getInstance().findFileByPath(
                    Paths.get(project.getBasePath()).resolve(configuredPath).toString()
                );

                if (themeImportsVirtualFile != null && themeImportsVirtualFile.exists()) {
                    try {
                        importMapStream = themeImportsVirtualFile.getInputStream();
                        loadedFromProject = true;
                        System.out.println("ThemeDataManager: Found theme-imports.json at configured project path: " + themeImportsVirtualFile.getPath());
                    } catch (IOException e) {
                        System.err.println("ThemeDataManager: Error opening stream for theme-imports.json from project path '" + configuredPath + "': " + e.getMessage());
                        importMapStream = null; // Ensure it's null if opening failed
                    }
                } else {
                    System.err.println("ThemeDataManager: Configured theme-imports.json not found at project path: " + Paths.get(project.getBasePath()).resolve(configuredPath).toString());
                }
            }
        }

        // Fallback to resources if not loaded from project
        if (importMapStream == null) {
            System.out.println("ThemeDataManager: Falling back to loading theme-imports.json from plugin resources.");
            importMapStream = ThemeDataManager.class.getResourceAsStream("/theme-imports.json");
            if (importMapStream == null) {
                System.err.println("ThemeDataManager: Error: /theme-imports.json not found in resources! Theme autocompletion will not be available.");
                return Collections.emptyMap();
            }
        }

        try {
            importMap = objectMapper.readValue(importMapStream, new TypeReference<Map<String, String>>() {});
            System.out.println("ThemeDataManager: Loaded importMap (" + (loadedFromProject ? "project" : "resources") + "): " + importMap);
        } catch (IOException e) {
            System.err.println("ThemeDataManager: Error parsing theme-imports.json (" + (loadedFromProject ? "project" : "resources") + "): " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyMap();
        } finally {
            if (importMapStream != null) {
                try {
                    importMapStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        if (importMap.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, Object>> tempAllThemes = new HashMap<>();
        for (Map.Entry<String, String> entry : importMap.entrySet()) {
            String alias = entry.getKey();
            String themePathInImport = entry.getValue(); // This path is relative to theme-imports.json if loaded from project, or relative to resources if loaded from resources
            InputStream themeStream = null;

            if (loadedFromProject && themeImportsVirtualFile != null && themeImportsVirtualFile.getParent() != null) {
                // Paths in theme-imports.json are relative to the directory of theme-imports.json itself
                VirtualFile themeFile = themeImportsVirtualFile.getParent().findFileByRelativePath(themePathInImport);
                if (themeFile != null && themeFile.exists()) {
                    try {
                        themeStream = themeFile.getInputStream();
                        System.out.println("ThemeDataManager: Attempting to load theme '" + alias + "' from project path: " + themeFile.getPath());
                    } catch (IOException e) {
                        System.err.println("ThemeDataManager: Error opening stream for theme '" + alias + "' from project path '" + themeFile.getPath() + "': " + e.getMessage());
                    }
                } else {
                    System.err.println("ThemeDataManager: Theme file '" + alias + "' not found at project relative path: " + themePathInImport + " (relative to " + themeImportsVirtualFile.getParent().getPath() + ")");
                }
            }

            // Fallback or default: load from resources
            if (themeStream == null) {
                String resourcePath = "/" + themePathInImport.replaceFirst("^src/main/resources/", "");
                System.out.println("ThemeDataManager: Attempting to load theme '" + alias + "' from classpath resource '" + resourcePath + "' (original path: '" + themePathInImport + "')");
                themeStream = ThemeDataManager.class.getResourceAsStream(resourcePath);
                if (themeStream == null) {
                     System.err.println("ThemeDataManager: Error: Theme file '" + alias + "' not found at classpath resource path: '" + resourcePath + "'");
                }
            }

            if (themeStream != null) {
                try {
                    Map<String, Object> themeData = objectMapper.readValue(themeStream, new TypeReference<Map<String, Object>>() {});
                    if (themeData != null && !themeData.isEmpty()) {
                        tempAllThemes.put(alias, themeData);
                    } else {
                        System.err.println("ThemeDataManager: Warning: Theme file '" + alias + "' (" + themePathInImport + ") was parsed as empty or null.");
                    }
                } catch (Exception e) {
                    System.err.println("ThemeDataManager: Error loading/parsing theme '" + alias + "' from path '" + themePathInImport + "': " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    try {
                        themeStream.close();
                    } catch (IOException e) { /* ignore */ }
                }
            }
        }

        if (tempAllThemes.isEmpty() && !importMap.isEmpty()) {
             System.err.println("ThemeDataManager: Warning: Import map was loaded, but no themes were successfully parsed or all theme files were empty/corrupted.");
        } else if (!tempAllThemes.isEmpty()) {
            System.out.println("ThemeDataManager: Successfully loaded and parsed themes: " + String.join(", ", tempAllThemes.keySet()));
        }
        return Collections.unmodifiableMap(tempAllThemes);
    }
}
                e.printStackTrace();
            }
        }

        if (tempAllThemes.isEmpty() && !importMap.isEmpty()) {
             System.err.println("ThemeDataManager: Warning: Import map was loaded, but no themes were successfully parsed or all theme files were empty/corrupted.");
        } else if (!tempAllThemes.isEmpty()) {
            System.out.println("ThemeDataManager: Successfully loaded and parsed themes: " + String.join(", ", tempAllThemes.keySet()));
        }
        return Collections.unmodifiableMap(tempAllThemes);
    }
}
