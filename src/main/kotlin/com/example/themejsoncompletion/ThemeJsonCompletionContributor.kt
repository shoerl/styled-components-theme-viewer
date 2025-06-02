package com.example.themejsoncompletion

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiNameIdentifierOwner // Required for PlatformPatterns.psiElement(PsiNameIdentifierOwner::class.java)

class ThemeJsonCompletionContributor : CompletionContributor() {

    private val objectMapper = jacksonObjectMapper() // Standard Jackson mapper
    private val importMap: Map<String, String>
    private val allThemes: Map<String, Map<String, Any>>

    init {
        // Load theme-imports.json
        val importMapStream = ThemeJsonCompletionContributor::class.java.getResourceAsStream("/theme-imports.json")
        if (importMapStream == null) {
            // Log error or handle missing resource
            this.importMap = emptyMap()
            this.allThemes = emptyMap()
            System.err.println("Error: /theme-imports.json not found! Theme autocompletion will not be available.")
        } else {
            this.importMap = importMapStream.use { stream ->
                objectMapper.readValue(stream, object : TypeReference<Map<String, String>>() {})
            }
            println("ThemeJsonCompletionContributor: Loaded importMap: $importMap")

            // Load all themes defined in importMap
            this.allThemes = this.importMap.mapValues { (alias, originalPath) ->
                val resourcePath = "/" + originalPath.removePrefix("src/main/resources/")
                println("ThemeJsonCompletionContributor: Attempting to load theme '$alias' from original path '$originalPath' as classpath resource '$resourcePath'")

                val themeStream = ThemeJsonCompletionContributor::class.java.getResourceAsStream(resourcePath)
                if (themeStream == null) {
                    System.err.println("Error: Theme file '$alias' not found at classpath resource path: '$resourcePath' (original path: '$originalPath')")
                    emptyMap<String, Any>() // Return an empty map if a theme file is not found
                } else {
                    println("ThemeJsonCompletionContributor: Successfully found theme '$alias' at '$resourcePath', attempting to parse.")
                    themeStream.use { stream ->
                        objectMapper.readValue(stream, object : TypeReference<Map<String, Any>>() {})
                    }
                }
            }.filterValues { it.isNotEmpty() } // Filter out themes that failed to load
        }
        
        if (this.importMap.isNotEmpty() && this.allThemes.isEmpty()) {
             System.err.println("ThemeJsonCompletionContributor: Warning: Import map was loaded, but no themes were successfully parsed. Autocomplete will be non-functional.")
        } else if (this.allThemes.isNotEmpty()) {
             println("ThemeJsonCompletionContributor: Successfully loaded and parsed themes: " + this.allThemes.keys.joinToString(", "))
        } else if (this.importMap.isEmpty()) {
            // This case is already handled by the "Error: /theme-imports.json not found!" message, but adding for completeness.
            println("ThemeJsonCompletionContributor: Info: No import map found, so no themes loaded. Autocomplete will be non-functional.")
        }


        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(PsiNameIdentifierOwner::class.java)
                .withParent(PlatformPatterns.psiElement(JSReferenceExpression::class.java)),
            ThemeJsonCompletionProvider(allThemes)
        )
    }
}
