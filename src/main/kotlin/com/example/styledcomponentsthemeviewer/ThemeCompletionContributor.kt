package com.example.styledcomponentsthemeviewer

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.typescript.TypeScriptLanguage
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext

class ThemeCompletionContributor : CompletionContributor() {

    companion object {
        private val THEME_ROOT_IDENTIFIER = "theme" // Configurable: could be derived from settings or by resolving types
        private val logger = thisLogger()
    }

    init {
        // Completion for JS/TS files in general
        val jsTsElementPattern = psiElement()
            .withParent(JSReferenceExpression::class.java)
            .withLanguage(JavascriptLanguage.INSTANCE)
        val tsElementPattern = psiElement()
            .withParent(JSReferenceExpression::class.java)
            .withLanguage(TypeScriptLanguage.INSTANCE)

        extend(CompletionType.BASIC, jsTsElementPattern, ThemeCompletionProvider())
        extend(CompletionType.BASIC, tsElementPattern, ThemeCompletionProvider())

        // Completion specifically within string template expressions (covers styled-components tagged templates)
        val stringTemplatePatternJS = psiElement()
            .inside(JSStringTemplateExpression::class.java)
            .withLanguage(JavascriptLanguage.INSTANCE)
        val stringTemplatePatternTS = psiElement()
            .inside(JSStringTemplateExpression::class.java)
            .withLanguage(TypeScriptLanguage.INSTANCE)

        extend(CompletionType.BASIC, stringTemplatePatternJS, ThemeCompletionProvider())
        extend(CompletionType.BASIC, stringTemplatePatternTS, ThemeCompletionProvider())
    }

    private class ThemeCompletionProvider : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(
            parameters: CompletionParameters,
            context: ProcessingContext,
            resultSet: CompletionResultSet
        ) {
            val position = parameters.position
            val project = position.project

            // Find the JSReferenceExpression at the caret
            val refExpr = PsiTreeUtil.getParentOfType(position, JSReferenceExpression::class.java)
                ?: PsiTreeUtil.getParentOfType(parameters.originalPosition, JSReferenceExpression::class.java)

            if (refExpr == null && position.parent !is JSReferenceExpression) {
                 logger.trace("No JSReferenceExpression at caret. Position: ${position.text}, Parent: ${position.parent?.text}")
                 return
            }

            val currentRefExpr = refExpr ?: position.parent as JSReferenceExpression

            // 1. Build the path of properties already typed (e.g., "palette.primary")
            // 2. Identify the root of this path (e.g., "theme")
            val (themePathRoot, pathSegments) = getPathFromReference(currentRefExpr)

            if (themePathRoot == null || themePathRoot.referenceName != THEME_ROOT_IDENTIFIER) {
                logger.trace("Completion not rooted at '${THEME_ROOT_IDENTIFIER}'. Root found: ${themePathRoot?.referenceName}")
                return
            }

            // The prefix for filtering tokens from the ThemeTokenService.
            // If pathSegments = ["palette", "primary"], contextualPrefix = "palette.primary."
            val contextualPrefixForService = if (pathSegments.isNotEmpty()) pathSegments.joinToString(".") + "." else ""

            // The prefix the user is currently typing for the current segment.
            val typedPrefix = resultSet.prefixMatcher.prefix
            logger.debug("Contextual Prefix: '$contextualPrefixForService', Typed Prefix: '$typedPrefix'")

            val service = ThemeTokenService.getInstance(project)
            val allTokens = service.getTokens() // Map<String, Any> (assuming flat map "palette.primary.main" -> value)

            val matchingTokens = allTokens.filterKeys { tokenKey ->
                tokenKey.startsWith(contextualPrefixForService) &&
                tokenKey.substringAfter(contextualPrefixForService).startsWith(typedPrefix)
            }

            if (matchingTokens.isEmpty()) {
                logger.debug("No tokens found for contextualPrefix: '$contextualPrefixForService' and typedPrefix: '$typedPrefix'")
                return
            }

            logger.debug("Found ${matchingTokens.size} matching tokens.")

            matchingTokens.forEach { (fullTokenPath, value) ->
                // fullTokenPath is like "palette.primary.main"
                // The part to display and insert for this specific completion depends on the context.
                // If context is "palette.", and token is "palette.primary.main", we suggest "primary.main".
                val suggestionPath = fullTokenPath.substringAfter(contextualPrefixForService)

                val lookupElement = LookupElementBuilder.create(fullTokenPath) // Data for insert handler
                    .withLookupString(suggestionPath) // String used for matching in UI and what's initially shown
                    .withPresentableText(suggestionPath.substringBeforeLast('.', suggestionPath)) // Show "primary" or "primary.main"
                    .withTailText("  ${value?.toString()?.take(50)}", true) // Show preview of value
                    .withTypeText(if (value is Map<*,*>) "Object" else value?.javaClass?.simpleName ?: "Unknown", true) // Type of the token
                    .withInsertHandler(ThemeInsertHandler(fullTokenPath, contextualPrefixForService))
                resultSet.addElement(lookupElement)
            }
            // resultSet.stopProgress() // Consider if this is always desired
        }

        private fun getPathFromReference(refExpr: JSReferenceExpression?): Pair<JSReferenceExpression?, List<String>> {
            val segments = mutableListOf<String>()
            var current: JSExpression? = refExpr
            var rootRef: JSReferenceExpression? = null

            while (current is JSReferenceExpression) {
                current.referenceName?.let { segments.add(0, it) } // Add to front to reverse order
                rootRef = current
                current = current.qualifier
            }
            // If the loop terminated because current is not JSReferenceExpression,
            // then 'current' holds the ultimate qualifier (e.g., the 'theme' identifier itself if it's standalone).
            // If it was a JSReferenceExpression, rootRef is it.
            if (current != null && segments.isEmpty() && current.text == THEME_ROOT_IDENTIFIER) {
                 // This handles the case where only "theme" is typed, and we are completing its direct properties.
                 // In this case, rootRef might not be set if refExpr was the 'theme' identifier itself.
                 // We need to ensure rootRef is the 'theme' JSReferenceExpression.
                 if (refExpr?.referenceName == THEME_ROOT_IDENTIFIER && refExpr.qualifier == null) {
                    rootRef = refExpr
                 }
            }
             // If segments has "theme" as first element, remove it as it's the root identifier, not part of path
            if (segments.isNotEmpty() && segments.first() == THEME_ROOT_IDENTIFIER) {
                segments.removeAt(0)
            }

            return Pair(rootRef, segments)
        }
    }

    private class ThemeInsertHandler(
        private val fullTokenPathToInsert: String, // e.g., "palette.primary.main"
        private val alreadyTypedContextPath: String // e.g., "palette." or ""
    ) : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            val editor = context.editor
            val document = editor.document

            // The lookupString from LookupElementBuilder is suggestionPath (e.g. "primary.main")
            // The text to actually insert to complete the path.
            val textToInsert = fullTokenPathToInsert.substringAfter(alreadyTypedContextPath)

            // Replace the currently typed prefix with the chosen suggestion's relevant part
            // context.startOffset is the beginning of the typed prefix.
            // context.tailOffset is the end of the typed prefix.
            document.replaceString(context.startOffset, context.tailOffset, textToInsert)
            editor.caretModel.moveToOffset(context.startOffset + textToInsert.length)

            // Check if the inserted token is an object node (i.e., it has children in the theme structure)
            // This is a simplified check; ideally, we'd check against the actual theme structure.
            // For now, if the full inserted path doesn't look like a leaf (e.g. has no value or is an object itself),
            // we can infer it might be an object.
            // A better way: ThemeTokenService could provide this info.
            val service = ThemeTokenService.getInstance(context.project)
            val tokenValue = service.getTokens()[fullTokenPathToInsert]

            if (tokenValue is Map<*, *> && tokenValue.isNotEmpty()) { // It's an object with further properties
                EditorModificationUtil.insertStringAtCaret(editor, ".", false, true)
                // Optionally, trigger auto-popup completion again
                AutoPopupController.getInstance(context.project).scheduleAutoPopup(editor)
            }
        }
    }
}
