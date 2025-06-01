package com.example.styledcomponentsthemeviewer

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.example.styledcomponentsthemeviewer.ThemeTokenService

class ThemeDocumentationProvider : AbstractDocumentationProvider() {

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        // element is often the resolved element, originalElement is the one under cursor
        val themeTokenElement = getValidThemeTokenElement(originalElement) ?: return null
        val project = themeTokenElement.project
        val service = ThemeTokenService.Companion.getInstance(project)
        val tokenPath = extractTokenPath(themeTokenElement) ?: return null

        val value = service.getTokenValue(tokenPath.substringAfter("theme."))
        return if (value != null) {
            "<b>${tokenPath}</b>: ${valueToString(value)}"
        } else {
            null // Or "Unknown theme token"
        }
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        // element is often the resolved element, originalElement is the one under cursor
        val themeTokenElement = getValidThemeTokenElement(originalElement) ?: return null
        val project = themeTokenElement.project
        val service = ThemeTokenService.Companion.getInstance(project)
        val tokenPath = extractTokenPath(themeTokenElement) ?: return null

        val value = service.getTokenValue(tokenPath.substringAfter("theme."))

        return if (value != null) {
            val valueStr = valueToString(value)
            val typeHint = inferType(value)
            // Basic HTML for the tooltip
            buildString {
                append("<html><body>")
                append("<b>Path:</b> <code>${tokenPath}</code><br>")
                append("<b>Value:</b> <code>${valueStr}</code>")
                if (typeHint != null) {
                    append("<br><b>Type:</b> ${typeHint}")
                }
                // Optional: Add color swatch here if it's a color
                if (typeHint == "color" && isColorString(valueStr)) {
                    append("<br><span style='background-color:${valueStr}; border: 1px solid #ccc;'>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</span>")
                }
                append("</body></html>")
            }
        } else {
            "<html><body>Unknown theme token: <code>${tokenPath}</code></body></html>"
        }
    }

    override fun getDocumentationElementForLink(psiManager: PsiManager?, link: String?, context: PsiElement?): PsiElement? {
        // Not used for this provider
        return null
    }

    override fun getDocumentationElementForLookupItem(
        psiManager: PsiManager?,
        obj: Any?,
        element: PsiElement?
    ): PsiElement? {
        // Can be used to provide documentation for completion items if they are custom objects
        // For now, our completion items are strings, so this might not be directly needed.
        return null
    }

    // This is called when hovering over an element in the editor.
    // We need to return the element for which documentation should be shown,
    // or null if no documentation is available for the element under the cursor.
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int // offset of the element under cursor, useful for more precise lookups
    ): PsiElement? {
        return getValidThemeTokenElement(contextElement)
    }


    private fun getValidThemeTokenElement(element: PsiElement?): PsiElement? {
        if (element == null) return null

        // We are interested in JSReferenceExpressions like theme.palette.primary.main
        var refExpr = PsiTreeUtil.getParentOfType(element, JSReferenceExpression::class.java, false)

        // If the element itself is a reference expression (e.g. user hovers over 'main')
        if (element.parent is JSReferenceExpression && element.prevSibling?.text == ".") {
             refExpr = element.parent as JSReferenceExpression
        }


        if (refExpr != null) {
            val path = extractTokenPath(refExpr)
            if (path != null && path.startsWith("theme.")) {
                thisLogger().debug("Valid theme token element found for hover: $path")
                return refExpr // Return the whole expression like theme.palette.primary
            }
        }
        thisLogger().debug("No valid theme token element found for hover at: ${element.text}")
        return null
    }

    private fun extractTokenPath(element: PsiElement?): String? {
        if (element !is JSReferenceExpression) return null

        val parts = mutableListOf<String>()
        var current: PsiElement? = element
        while (current is JSReferenceExpression) {
            parts.add(current.referenceName ?: "")
            current = current.qualifier
        }
        if (current?.text == "theme") { // Check if the qualifier chain ends with 'theme'
            return "theme." + parts.reversed().joinToString(".")
        }
        return null
    }

    private fun valueToString(value: Any?): String {
        return when (value) {
            is String -> "\"$value\""
            else -> value?.toString() ?: "null"
        }
    }

    private fun inferType(value: Any?): String? {
        return when (value) {
            is String -> if (isColorString(value)) "color" else "string"
            is Number -> "number"
            is Boolean -> "boolean"
            else -> null
        }
    }

    private fun isColorString(value: String): Boolean {
        val s = value.toLowerCase()
        return s.startsWith("#") || s.startsWith("rgb(") || s.startsWith("rgba(") || s.startsWith("hsl(") || s.startsWith("hsla(")
    }
}
