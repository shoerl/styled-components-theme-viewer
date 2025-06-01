package com.example.styledcomponentsthemeviewer

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

class ThemeColorAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // We are interested in the JSReferenceExpression that forms the theme path
        // e.g., `main` in `theme.palette.primary.main` or the whole `theme.palette.primary.main`
        var refExprUnderCursor: JSReferenceExpression? = null

        if (element is JSReferenceExpression) {
            refExprUnderCursor = element
        } else if (element.parent is JSReferenceExpression && element.prevSibling?.text == ".") {
            // This could be an identifier that is part of a JSReferenceExpression
            // e.g. if `element` is `main` in `theme.palette.primary.main`
            refExprUnderCursor = element.parent as JSReferenceExpression
        } else {
            // If the element itself isn't a JSReferenceExpression or part of one in the expected way, skip.
            return
        }

        if (refExprUnderCursor == null) return

        val project = element.project
        val service = ThemeTokenService.getInstance(project)

        // `refExprUnderCursor` could be a partial path (e.g., `primary.main` if `theme.palette` is resolved)
        // or the full path from `theme`. We need the full path starting from `theme`.
        val tokenPath = extractFullThemePath(refExprUnderCursor)

        if (tokenPath != null && tokenPath.startsWith("theme.")) {
            val value = service.getTokenValue(tokenPath.substringAfter("theme."))
            if (value is String && isPotentiallyColorString(value)) {
                parseColor(value)?.let { color ->
                    thisLogger().debug("Annotating color for $tokenPath with value $value")

                    // The annotation should ideally be on the specific part of the token,
                    // e.g., on 'main' in 'theme.palette.primary.main'.
                    // `refExprUnderCursor.referenceNameElement` gives the last segment.
                    val elementToAnnotate = refExprUnderCursor.referenceNameElement ?: refExprUnderCursor

                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .gutterIconRenderer(ColorGutterIconRenderer(color))
                        .tooltip("Theme token value: ${valueToString(value)}") // Tooltip for the gutter icon itself
                        // To also provide a tooltip on hover for the text element (optional, if DocumentationProvider is not enough)
                        // .withFix(MyQuickFix()) // Example of adding a quick fix
                        .range(elementToAnnotate.textRange) // Apply gutter icon to the range of the last segment
                        .create()
                }
            }
        }
    }

    private fun extractFullThemePath(element: JSReferenceExpression?): String? {
        if (element == null) return null
        val parts = mutableListOf<String>()
        var current: PsiElement? = element
        while (current is JSReferenceExpression) {
            parts.add(current.referenceName ?: return null)
            current = current.qualifier
        }
        return if (current?.text == "theme") {
            "theme." + parts.reversed().joinToString(".")
        } else {
            // This means the reference expression doesn't start with "theme"
            null
        }
    }

    private fun valueToString(value: Any?): String {
        return when (value) {
            is String -> "\"$value\""
            else -> value?.toString() ?: "null"
        }
    }
}
