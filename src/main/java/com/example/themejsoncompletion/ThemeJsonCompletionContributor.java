package com.example.themejsoncompletion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiNameIdentifierOwner;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ThemeJsonCompletionContributor extends CompletionContributor {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, String> importMap;
    private final Map<String, Map<String, Object>> allThemes;

    public ThemeJsonCompletionContributor() {
        Map<String, String> tempImportMap;
        try (InputStream importMapStream = ThemeJsonCompletionContributor.class.getResourceAsStream("/theme-imports.json")) {
            if (importMapStream == null) {
                System.err.println("Error: /theme-imports.json not found! Theme autocompletion will not be available.");
                tempImportMap = Collections.emptyMap();
            } else {
                tempImportMap = objectMapper.readValue(importMapStream, new TypeReference<Map<String, String>>() {});
                System.out.println("ThemeJsonCompletionContributor: Loaded importMap: " + tempImportMap);
            }
        } catch (Exception e) {
            System.err.println("Error loading /theme-imports.json: " + e.getMessage());
            e.printStackTrace();
            tempImportMap = Collections.emptyMap();
        }
        this.importMap = tempImportMap;

        if (this.importMap.isEmpty()) {
            this.allThemes = Collections.emptyMap();
            // Message already printed or will be printed by subsequent logic
        } else {
            Map<String, Map<String, Object>> tempAllThemes = new HashMap<>();
            for (Map.Entry<String, String> entry : this.importMap.entrySet()) {
                String alias = entry.getKey();
                String originalPath = entry.getValue();
                String resourcePath = "/" + originalPath.replaceFirst("^src/main/resources/", "");
                System.out.println("ThemeJsonCompletionContributor: Attempting to load theme '" + alias + "' from original path '" + originalPath + "' as classpath resource '" + resourcePath + "'");

                try (InputStream themeStream = ThemeJsonCompletionContributor.class.getResourceAsStream(resourcePath)) {
                    if (themeStream == null) {
                        System.err.println("Error: Theme file '" + alias + "' not found at classpath resource path: '" + resourcePath + "' (original path: '" + originalPath + "')");
                    } else {
                        System.out.println("ThemeJsonCompletionContributor: Successfully found theme '" + alias + "' at '" + resourcePath + "', attempting to parse.");
                        Map<String, Object> themeData = objectMapper.readValue(themeStream, new TypeReference<Map<String, Object>>() {});
                        if (themeData != null && !themeData.isEmpty()) {
                            tempAllThemes.put(alias, themeData);
                        } else {
                             System.err.println("Warning: Theme file '" + alias + "' at '" + resourcePath + "' was parsed as empty or null.");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error loading theme '" + alias + "' from resource path '" + resourcePath + "': " + e.getMessage());
                    e.printStackTrace();
                }
            }
            this.allThemes = Collections.unmodifiableMap(tempAllThemes);
        }

        if (this.importMap.isEmpty() && this.allThemes.isEmpty()) {
             // This state implies /theme-imports.json was not found or was empty.
             System.out.println("ThemeJsonCompletionContributor: Info: No import map found or it was empty, so no themes loaded. Autocomplete will be non-functional.");
        } else if (!this.importMap.isEmpty() && this.allThemes.isEmpty()) {
             System.err.println("ThemeJsonCompletionContributor: Warning: Import map was loaded, but no themes were successfully parsed or all theme files were empty/corrupted. Autocomplete will be non-functional.");
        } else if (!this.allThemes.isEmpty()) {
            System.out.println("ThemeJsonCompletionContributor: Successfully loaded and parsed themes: " + String.join(", ", this.allThemes.keySet()));
        }


        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiNameIdentifierOwner.class)
                .withParent(PlatformPatterns.psiElement(JSReferenceExpression.class)),
            new ThemeJsonCompletionProvider(allThemes)
        );
    }
}
