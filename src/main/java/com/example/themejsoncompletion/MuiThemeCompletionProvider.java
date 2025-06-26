package com.example.themejsoncompletion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
// Removed ArrayList import as pathSegments are directly from MuiPsiUtil
import java.util.List;
import java.util.Map;

public class MuiThemeCompletionProvider extends CompletionProvider<CompletionParameters> {

    private static final Logger LOG = Logger.getInstance(MuiThemeCompletionProvider.class);
    private final Map<String, Object> muiTheme;

    public MuiThemeCompletionProvider(Map<String, Object> muiTheme) {
        this.muiTheme = muiTheme;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        // The JSReferenceExpression is the full chain, e.g., theme.palette.primary
        // or what's being typed theme.palette.p<caret>
        JSReferenceExpression referenceExpression = PsiTreeUtil.getParentOfType(position, JSReferenceExpression.class, false);

        if (referenceExpression == null) {
             PsiElement parent = position.getParent();
            if (parent instanceof JSReferenceExpression) {
                referenceExpression = (JSReferenceExpression) parent;
            } else {
                LOG.debug("MUI Completion: Cannot find JSReferenceExpression at or above position: " + position.getText());
                return;
            }
        }

        // We need the reference expression that represents the path being typed.
        // For `theme.palette.pri<caret>`, `referenceExpression` would be `theme.palette.pri`.
        // For `theme.palette.<caret>`, `referenceExpression` would be `theme.palette`.
        // (IntelliJ might insert a dummy identifier at caret, so position.getParent() is often correct)

        LOG.debug("MUI Completion Provider invoked for reference: " + referenceExpression.getText() + " at offset " + parameters.getOffset());

        List<String> pathSegments = MuiPsiUtil.extractThemePathSegments(referenceExpression);

        // If the referenceExpression does not end with a dot, it means the user is currently typing
        // a segment name (e.g. theme.palet<caret>). In this case, `extractThemePathSegments` would
        // include "palet". We need to provide completions for "palet" under "theme", so
        // the effective path for lookup is the parent of "palet".
        // If it ends with a dot (e.g. theme.palette.<caret>), then `extractThemePathSegments` gives ["palette"],
        // which is correct for looking up children of "palette".

        String text = referenceExpression.getText();
        boolean endsWithDot = text.endsWith(".");
        JSReferenceExpression lookupRef = referenceExpression;

        if(!endsWithDot && !pathSegments.isEmpty()){
            // User is typing a segment name, e.g. theme.palette.pri<caret>
            // pathSegments from extractThemePathSegments might be ["palette", "pri"]
            // We need to provide completions for "pri" under "palette".
            // So, the parent path is ["palette"].
            // We use the qualifier of the current referenceExpression for path extraction.
            if(referenceExpression.getQualifier() instanceof JSReferenceExpression){
                lookupRef = (JSReferenceExpression) referenceExpression.getQualifier();
                pathSegments = MuiPsiUtil.extractThemePathSegments(lookupRef);
            } else { // No qualifier, means we are at the root e.g. them<caret>
                 pathSegments.clear(); // No path segments, lookup at root of muiTheme
            }
        }
        // If endsWithDot is true, pathSegments from extractThemePathSegments is already correct.
        // e.g. theme.palette.<caret> -> pathSegments = ["palette"]

        LOG.debug("MUI Completion: Effective path segments for lookup: " + pathSegments);

        Map<String, Object> currentLevel = this.muiTheme;
        for (String segment : pathSegments) {
            Object value = currentLevel.get(segment);
            if (value instanceof Map) {
                currentLevel = (Map<String, Object>) value;
            } else {
                LOG.debug("MUI Completion: Path segment '" + segment + "' in " + pathSegments + " does not lead to a map. Value: " + value);
                return;
            }
        }

        for (Map.Entry<String, Object> entry : currentLevel.entrySet()) {
            LookupElementBuilder element = LookupElementBuilder.create(entry.getKey());
            Object value = entry.getValue();
            String typeText = value == null ? "null" : value.getClass().getSimpleName();
            Color color = null;

            if (value instanceof Map) {
                element = element.withTypeText("object", true);
            } else if (value instanceof String) {
                String strValue = (String) value;
                element = element.withTypeText("\"" + strValue + "\"", true);
                if (strValue.matches("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")) {
                    try {
                        color = Color.decode(strValue);
                    } catch (NumberFormatException e) { /* ignore */ }
                }
            } else if (value instanceof Number || value instanceof Boolean) {
                element = element.withTypeText(value.toString(), true);
            } else {
                 element = element.withTypeText(typeText, true);
            }

            if (color != null) {
                element = element.withIcon(new com.intellij.ui.ColorIcon(16, new JBColor(color, color)));
            }

            result.addElement(element);
            // LOG.debug("MUI Completion: Added suggestion: " + entry.getKey() + " (type: " + typeText + ")");
        }
    }
}
