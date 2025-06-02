package com.example.themejsoncompletion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ThemeJsonCompletionProvider extends CompletionProvider<CompletionParameters> {

    private final Map<String, Map<String, Object>> allThemes;

    public ThemeJsonCompletionProvider(Map<String, Map<String, Object>> allThemes) {
        this.allThemes = allThemes;
    }

    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        if (!(position.getParent() instanceof JSReferenceExpression)) {
            return;
        }
        JSReferenceExpression parentRef = (JSReferenceExpression) position.getParent();

        PsiElement qualifierElement = parentRef.getQualifier();
        if (!(qualifierElement instanceof JSReferenceExpression)) {
            return;
        }
        JSReferenceExpression qualifierRef = (JSReferenceExpression) qualifierElement;
        String qualifier = qualifierRef.getReferenceName();

        if (qualifier == null) {
            return;
        }

        Map<String, Object> themeObject = allThemes.get(qualifier);
        if (themeObject == null) {
            return;
        }

        List<String> keys = flatten(themeObject, "");
        for (String key : keys) {
            result.addElement(LookupElementBuilder.create(key));
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> flatten(Map<String, Object> obj, String prefix) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : obj.entrySet()) {
            String fullKey = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                try {
                    result.addAll(flatten((Map<String, Object>) value, fullKey));
                } catch (ClassCastException e) {
                    // Handle cases where the map might not be Map<String, Object> as expected, though TypeReference should ensure this.
                    System.err.println("Warning: Could not cast nested map for key '" + fullKey + "'. Value type: " + value.getClass().getName());
                    result.add(fullKey); // Add the key itself, as it's not further expandable
                }
            } else {
                result.add(fullKey);
            }
        }
        return result;
    }
}
