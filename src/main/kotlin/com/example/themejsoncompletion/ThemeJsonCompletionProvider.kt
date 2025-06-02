package com.example.themejsoncompletion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.util.ProcessingContext

class ThemeJsonCompletionProvider(
    private val allThemes: Map<String, Map<String, Any>>
) : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val position = parameters.position
        val parentRef = position.parent as? JSReferenceExpression ?: return

        // Resolve the qualifier (e.g., `theme` in `theme.|`)
        val qualifier = (parentRef.qualifier as? JSReferenceExpression)?.referenceName ?: return
        
        // If our data-driven map contains this qualifier:
        val themeObject = allThemes[qualifier] ?: return

        // Flatten nested keys into dot-notated strings:
        fun flatten(obj: Map<String, Any>, prefix: String = ""): List<String> {
            return obj.flatMap { (key, value) ->
                val fullKey = if (prefix.isEmpty()) key else "$prefix.$key"
                when (value) {
                    is Map<*, *> -> flatten(value as Map<String, Any>, fullKey)
                    else -> listOf(fullKey)
                }
            }
        }

        val keys = flatten(themeObject)
        keys.forEach { key ->
            result.addElement(LookupElementBuilder.create(key))
        }
    }
}
