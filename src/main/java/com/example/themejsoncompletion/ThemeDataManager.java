package com.example.themejsoncompletion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.ES6ExportDefaultAssignment;
import com.intellij.lang.javascript.psi.ecma6.ES6ExportSpecifier;
import com.intellij.lang.javascript.psi.ecma6.ES6NamedExportDeclaration;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.example.themejsoncompletion.settings.ThemeJsonSettingsState;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;


/**
 * Manages loading, caching, and refreshing of theme data from JSON and JS/TS (for MUI) files.
 */
@Service(Service.Level.PROJECT)
public final class ThemeDataManager {

    private static final Logger LOG = Logger.getInstance(ThemeDataManager.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, Map<String, Object>> allJsonThemes; // For legacy JSON themes
    private Map<String, Object> muiTheme; // For MUI theme from JS/TS
    private final Project project;

    public static final String MUI_THEME_KEY = "muiTheme"; // Key if storing MUI theme in allJsonThemes

    public ThemeDataManager(Project project) {
        this.project = project;
        refreshAllThemesData(); // Initial load
        LOG.info("ThemeDataManager: Initialized for project: " + project.getName());
    }

    public static ThemeDataManager getInstance(Project project) {
        return project.getService(ThemeDataManager.class);
    }

    /**
     * Gets all loaded JSON themes.
     * @return An unmodifiable map of JSON themes.
     */
    public synchronized Map<String, Map<String, Object>> getAllJsonThemes() {
        return allJsonThemes != null ? Collections.unmodifiableMap(allJsonThemes) : Collections.emptyMap();
    }

    /**
     * Gets the parsed MUI theme.
     * @return An unmodifiable map representing the MUI theme, or null if not loaded/found.
     */
    @Nullable
    public synchronized Map<String, Object> getMuiTheme() {
        return muiTheme != null ? Collections.unmodifiableMap(muiTheme) : null;
    }

    /**
     * For ThemeJsonCompletionContributor, which expects all themes in one map.
     * This can be refactored later.
     */
    public synchronized Map<String, Map<String, Object>> getAllThemes() {
        Map<String, Map<String, Object>> combined = new HashMap<>();
        if (allJsonThemes != null) {
            combined.putAll(allJsonThemes);
        }
        if (muiTheme != null && !muiTheme.isEmpty()) {
            // The old ThemeJsonCompletionProvider expects Map<String, Map<String,Object>>
            // This might need adjustment if MUI theme is directly used by a new provider
            // For now, let's wrap it if it's requested via getAllThemes().
            // However, MuiThemeCompletionProvider should use getMuiTheme() directly.
            // So, this key here is more for the legacy provider.
            // combined.put(MUI_THEME_KEY, muiTheme); // This line is problematic if muiTheme is not Map<String, Map<String, Object>>
        }
        return Collections.unmodifiableMap(combined);
    }


    public synchronized void refreshThemes() { // Old method, effectively refreshAllThemesData now
        refreshAllThemesData();
    }

    private synchronized void refreshAllThemesData() {
        LOG.info("Refreshing all themes data for project: " + project.getName());
        this.allJsonThemes = loadJsonThemes();
        this.muiTheme = loadMuiThemeFromPsi();

        if (this.muiTheme != null && !this.muiTheme.isEmpty()) {
            LOG.info("MUI Theme loaded successfully. Top-level keys: " + this.muiTheme.keySet());
        } else {
            LOG.info("MUI Theme not loaded or is empty.");
        }
        if (this.allJsonThemes != null && !this.allJsonThemes.isEmpty()) {
            LOG.info("JSON Themes reloaded. Current themes: " + this.allJsonThemes.keySet());
        } else {
            LOG.info("No JSON themes loaded.");
        }
    }

    private Map<String, Object> loadMuiThemeFromPsi() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        if (settings == null || settings.muiThemeFilePath == null || settings.muiThemeFilePath.trim().isEmpty()) {
            LOG.debug("MUI theme file path not configured.");
            return null;
        }

        String muiThemePath = settings.muiThemeFilePath;
        LOG.debug("Attempting to load MUI theme from PSI: " + muiThemePath);

        return ReadAction.compute(() -> {
            VirtualFile muiThemeVirtualFile = LocalFileSystem.getInstance().findFileByPath(
                    Paths.get(project.getBasePath()).resolve(muiThemePath).toString()
            );

            if (muiThemeVirtualFile == null || !muiThemeVirtualFile.exists()) {
                LOG.warn("MUI theme file not found at: " + muiThemePath);
                return null;
            }

            PsiFile psiFile = PsiManager.getInstance(project).findFile(muiThemeVirtualFile);
            if (!(psiFile instanceof JSFile)) {
                LOG.warn("MUI theme file is not a JavaScript/TypeScript file: " + muiThemePath);
                return null;
            }

            JSObjectLiteralExpression themeObjectLiteral = findThemeObjectLiteral(psiFile);
            if (themeObjectLiteral == null) {
                LOG.warn("Could not find theme object literal in: " + muiThemePath);
                return null;
            }

            return convertJsObjectLiteralToMap(themeObjectLiteral);
        });
    }

    @Nullable
    private JSObjectLiteralExpression findThemeObjectLiteral(PsiFile psiFile) {
        // Try common patterns:
        // 1. export const theme = { ... }
        // 2. export default { ... }
        // 3. export default createTheme({ ... })
        // 4. const theme = { ... }; (then look for 'theme' export or assume it's the one)

        // Simplified: Find the first top-level object literal in an export or a variable named 'theme'.
        // This needs to be more robust for real-world scenarios.

        // Look for `export const theme = { ... }` or `const theme = { ... }`
        for (PsiElement element : psiFile.getChildren()) {
            if (element instanceof ES6NamedExportDeclaration) { // `export const/let/var ...`
                ES6NamedExportDeclaration exportDeclaration = (ES6NamedExportDeclaration) element;
                JSVariableStatement varStatement = PsiTreeUtil.getChildOfType(exportDeclaration, JSVariableStatement.class);
                if (varStatement != null) {
                    for (JSVariable variable : varStatement.getVariables()) {
                        if ("theme".equals(variable.getName()) && variable.getInitializer() instanceof JSObjectLiteralExpression) {
                            return (JSObjectLiteralExpression) variable.getInitializer();
                        }
                         // Check for createTheme({ ... })
                        if (variable.getInitializer() instanceof JSCallExpression) {
                            JSCallExpression call = (JSCallExpression) variable.getInitializer();
                            if (call.getMethodExpression() instanceof JSReferenceExpression &&
                                "createTheme".equals(((JSReferenceExpression) call.getMethodExpression()).getReferenceName())) {
                                if (call.getArguments().length > 0 && call.getArguments()[0] instanceof JSObjectLiteralExpression) {
                                    return (JSObjectLiteralExpression) call.getArguments()[0];
                                }
                            }
                        }
                    }
                }
            }
            // Look for `export default { ... }`
            if (element instanceof ES6ExportDefaultAssignment) {
                ES6ExportDefaultAssignment defaultExport = (ES6ExportDefaultAssignment) element;
                if (defaultExport.getExpression() instanceof JSObjectLiteralExpression) {
                    return (JSObjectLiteralExpression) defaultExport.getExpression();
                }
                // Check for `export default createTheme({ ... })`
                if (defaultExport.getExpression() instanceof JSCallExpression) {
                     JSCallExpression call = (JSCallExpression) defaultExport.getExpression();
                     if (call.getMethodExpression() instanceof JSReferenceExpression &&
                         "createTheme".equals(((JSReferenceExpression) call.getMethodExpression()).getReferenceName())) {
                         if (call.getArguments().length > 0 && call.getArguments()[0] instanceof JSObjectLiteralExpression) {
                             return (JSObjectLiteralExpression) call.getArguments()[0];
                         }
                     }
                }
            }
             // Look for `const theme = { ... }` (not exported directly but might be exported later)
            if (element instanceof JSVariableStatement) {
                JSVariableStatement varStatement = (JSVariableStatement) element;
                 for (JSVariable variable : varStatement.getVariables()) {
                     if ("theme".equals(variable.getName()) && variable.getInitializer() instanceof JSObjectLiteralExpression) {
                         // Could add a check here if this 'theme' is actually exported
                         return (JSObjectLiteralExpression) variable.getInitializer();
                     }
                     if ("theme".equals(variable.getName()) && variable.getInitializer() instanceof JSCallExpression) {
                        JSCallExpression call = (JSCallExpression) variable.getInitializer();
                        if (call.getMethodExpression() instanceof JSReferenceExpression &&
                            "createTheme".equals(((JSReferenceExpression) call.getMethodExpression()).getReferenceName())) {
                            if (call.getArguments().length > 0 && call.getArguments()[0] instanceof JSObjectLiteralExpression) {
                                return (JSObjectLiteralExpression) call.getArguments()[0];
                            }
                        }
                    }
                 }
            }
        }
        LOG.warn("No suitable 'theme' object literal found in " + psiFile.getName());
        return null; // Fallback or more complex resolution needed
    }


    private Map<String, Object> convertJsObjectLiteralToMap(JSObjectLiteralExpression literal) {
        Map<String, Object> map = new LinkedHashMap<>(); // Preserve order if relevant
        if (literal == null) return map;

        for (JSProperty property : literal.getProperties()) {
            String key = property.getName();
            JSExpression valueExpression = property.getValue();
            if (key == null) continue;

            if (valueExpression instanceof JSObjectLiteralExpression) {
                map.put(key, convertJsObjectLiteralToMap((JSObjectLiteralExpression) valueExpression));
            } else if (valueExpression instanceof JSLiteralExpression) {
                JSLiteralExpression literalValue = (JSLiteralExpression) valueExpression;
                if (literalValue.isQuotedLiteral()) {
                    map.put(key, literalValue.getStringValue());
                } else {
                    // Try to parse as number, boolean, or keep as string
                    try {
                        String text = literalValue.getText();
                        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
                            map.put(key, Boolean.parseBoolean(text));
                        } else if (text.contains(".")) {
                            map.put(key, Double.parseDouble(text));
                        } else {
                            map.put(key, Long.parseLong(text));
                        }
                    } catch (NumberFormatException e) {
                        map.put(key, literalValue.getText()); // Fallback to text
                    }
                }
            } else if (valueExpression instanceof JSArrayLiteralExpression) {
                map.put(key, convertJsArrayLiteralToList((JSArrayLiteralExpression) valueExpression));
            } else if (valueExpression instanceof JSReferenceExpression) {
                // TODO: Handle references (e.g., to other variables or imports) - complex
                // For now, store as string placeholder
                map.put(key, "Ref(" + ((JSReferenceExpression) valueExpression).getReferencedName() + ")");
                LOG.debug("Property '" + key + "' is a reference: " + valueExpression.getText() + ". Storing as placeholder.");
            } else if (valueExpression instanceof JSCallExpression) {
                // TODO: Handle function calls (e.g. theme.spacing(2)) - complex
                map.put(key, "Call(" + valueExpression.getText() + ")");
                 LOG.debug("Property '" + key + "' is a call expression: " + valueExpression.getText() + ". Storing as placeholder.");
            }
            // Add more types as needed (JSUnaryExpression for negative numbers, etc.)
            else if (valueExpression != null) {
                map.put(key, valueExpression.getText()); // Fallback: store the text content
                LOG.debug("Property '" + key + "' has unhandled JSExpression type: " + valueExpression.getClass().getSimpleName() + ". Storing raw text: " + valueExpression.getText());
            }
        }
        return map;
    }

    private List<Object> convertJsArrayLiteralToList(JSArrayLiteralExpression arrayLiteral) {
        List<Object> list = new ArrayList<>();
        if (arrayLiteral == null) return list;

        for (JSExpression expression : arrayLiteral.getExpressions()) {
            if (expression instanceof JSObjectLiteralExpression) {
                list.add(convertJsObjectLiteralToMap((JSObjectLiteralExpression) expression));
            } else if (expression instanceof JSLiteralExpression) {
                JSLiteralExpression literalValue = (JSLiteralExpression) expression;
                if (literalValue.isQuotedLiteral()) {
                    list.add(literalValue.getStringValue());
                } else {
                     try {
                        String text = literalValue.getText();
                        if (text.equalsIgnoreCase("true") || text.equalsIgnoreCase("false")) {
                            list.add(Boolean.parseBoolean(text));
                        } else if (text.contains(".")) {
                            list.add(Double.parseDouble(text));
                        } else {
                            list.add(Long.parseLong(text));
                        }
                    } catch (NumberFormatException e) {
                        list.add(literalValue.getText()); // Fallback to text
                    }
                }
            } else if (expression instanceof JSArrayLiteralExpression) {
                list.add(convertJsArrayLiteralToList((JSArrayLiteralExpression) expression));
            } else if (expression != null) {
                list.add(expression.getText()); // Fallback
            }
        }
        return list;
    }


    /**
     * Core logic for loading JSON themes (legacy).
     */
    private Map<String, Map<String, Object>> loadJsonThemes() {
        Map<String, String> importMap = Collections.emptyMap();
        InputStream importMapStream = null;
        boolean loadedFromProject = false;

        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        String configuredPath = (settings != null) ? settings.themeImportsJsonPath : "";
        VirtualFile themeImportsVirtualFile = null;

        if (configuredPath != null && !configuredPath.trim().isEmpty()) {
            LOG.debug("Attempting to load theme-imports.json from configured project path: " + configuredPath);
            if (project.getBasePath() != null) {
                 themeImportsVirtualFile = LocalFileSystem.getInstance().findFileByPath(
                    Paths.get(project.getBasePath()).resolve(configuredPath).toString()
                );
                if (themeImportsVirtualFile != null && themeImportsVirtualFile.exists()) {
                    try {
                        importMapStream = themeImportsVirtualFile.getInputStream();
                        loadedFromProject = true;
                        LOG.info("Found theme-imports.json at configured project path: " + themeImportsVirtualFile.getPath());
                    } catch (IOException e) {
                        LOG.warn("Error opening stream for theme-imports.json from project path '" + configuredPath + "': " + e.getMessage(), e);
                        importMapStream = null;
                    }
                } else {
                    LOG.warn("Configured theme-imports.json not found at project path: " + Paths.get(project.getBasePath()).resolve(configuredPath).toString());
                }
            } else {
                LOG.warn("Project base path is null, cannot load from relative project path: " + configuredPath);
            }
        }

        if (importMapStream == null) {
            LOG.debug("Falling back to loading theme-imports.json from plugin resources.");
            importMapStream = ThemeDataManager.class.getResourceAsStream("/theme-imports.json");
            if (importMapStream == null) {
                LOG.info("/theme-imports.json not found in resources. No JSON themes will be loaded this way.");
                return Collections.emptyMap();
            }
        }

        try {
            importMap = objectMapper.readValue(importMapStream, new TypeReference<Map<String, String>>() {});
            LOG.info("Loaded JSON importMap (" + (loadedFromProject ? "project" : "resources") + "): " + importMap.keySet());
        } catch (IOException e) {
            LOG.error("Error parsing theme-imports.json (" + (loadedFromProject ? "project path" : "plugin resources") + ")", e);
            return Collections.emptyMap();
        } finally {
            if (importMapStream != null) {
                try { importMapStream.close(); } catch (IOException e) { LOG.warn("Failed to close importMapStream", e); }
            }
        }

        if (importMap.isEmpty()) return Collections.emptyMap();

        Map<String, Map<String, Object>> tempAllThemes = new HashMap<>();
        for (Map.Entry<String, String> entry : importMap.entrySet()) {
            String alias = entry.getKey();
            String themePathInImport = entry.getValue();
            InputStream themeStream = null;
            String loadedThemePathDescription = "";

            if (loadedFromProject && themeImportsVirtualFile != null && themeImportsVirtualFile.getParent() != null) {
                VirtualFile themeFile = themeImportsVirtualFile.getParent().findFileByRelativePath(themePathInImport);
                if (themeFile != null && themeFile.exists()) {
                    try {
                        themeStream = themeFile.getInputStream();
                        loadedThemePathDescription = "project path: " + themeFile.getPath();
                        LOG.debug("Attempting to load JSON theme '" + alias + "' from " + loadedThemePathDescription);
                    } catch (IOException e) {
                        LOG.warn("Error opening stream for JSON theme '" + alias + "' from project path '" + themeFile.getPath() + "': " + e.getMessage(), e);
                    }
                } else {
                     LOG.warn("JSON Theme file '" + alias + "' not found at project relative path: " + themePathInImport + " (relative to " + themeImportsVirtualFile.getParent().getPath() + ")");
                }
            }

            if (themeStream == null) {
                String resourcePath = "/" + themePathInImport.replaceAll("^(src/main/resources/|resources/)", "");
                loadedThemePathDescription = "classpath resource '" + resourcePath + "'";
                LOG.debug("Attempting to load JSON theme '" + alias + "' from " + loadedThemePathDescription);
                themeStream = ThemeDataManager.class.getResourceAsStream(resourcePath);
                 if (themeStream == null) {
                    LOG.warn("JSON Theme file '" + alias + "' not found at classpath resource: '" + resourcePath + "'");
                }
            }

            if (themeStream != null) {
                try {
                    Map<String, Object> themeData = objectMapper.readValue(themeStream, new TypeReference<Map<String, Object>>() {});
                    if (themeData != null && !themeData.isEmpty()) {
                        tempAllThemes.put(alias, themeData);
                    } else {
                        LOG.warn("JSON Theme file '" + alias + "' (" + loadedThemePathDescription + ") was parsed as empty or null.");
                    }
                } catch (Exception e) {
                    LOG.error("Error loading/parsing JSON theme '" + alias + "' from " + loadedThemePathDescription, e);
                } finally {
                    try { themeStream.close(); } catch (IOException e) { LOG.warn("Failed to close themeStream for " + alias, e); }
                }
            }
        }
        LOG.info("Successfully loaded and parsed JSON themes: " + String.join(", ", tempAllThemes.keySet()));
        return Collections.unmodifiableMap(tempAllThemes);
    }
}
