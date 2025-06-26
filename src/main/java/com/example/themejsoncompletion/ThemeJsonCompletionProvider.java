package com.example.themejsoncompletion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Provides completion suggestions for theme JSON properties.
 * This class is instantiated by {@link ThemeJsonCompletionContributor} with the
 * currently loaded theme data. It then generates lookup elements based on the
 * structure of these themes.
 *
 * The completion logic appears to expect a structure like `ThemeAlias.property.subproperty`.
 * It uses the qualifier of a {@link JSReferenceExpression} to determine the theme alias
 * and then provides flattened keys from that theme object.
 *
 * For example, if `allThemes` contains an entry "MyDarkTheme" and the user types `MyDarkTheme.`,
 * this provider would suggest flattened keys from the "MyDarkTheme" object (e.g., "colors.primary", "fonts.defaultSize").
 */
public class ThemeJsonCompletionProvider extends CompletionProvider<CompletionParameters> {
    private static final Logger LOG = Logger.getInstance(ThemeJsonCompletionProvider.class);

    private final Map<String, Map<String, Object>> allThemes;

    /**
     * Constructs a new completion provider with the given theme data.
     * @param allThemes A map where keys are theme aliases and values are maps representing the theme structure.
     */
    public ThemeJsonCompletionProvider(Map<String, Map<String, Object>> allThemes) {
        this.allThemes = allThemes;
    }

    /**
     * Adds completion suggestions to the result set.
     * It inspects the PSI element at the cursor's position to determine the context:
     * - It expects the parent of the current element to be a {@link JSReferenceExpression}.
     * - It then looks at the qualifier of this reference (e.g., the `MyTheme` part in `MyTheme.foo`).
     * - If the qualifier matches a known theme alias, it flattens the theme's properties
     *   and adds them as suggestions.
     *
     * @param parameters Completion parameters.
     * @param context Processing context.
     * @param result The result set to add completions to.
     */
    @Override
    protected void addCompletions(@NotNull CompletionParameters parameters,
                                  @NotNull ProcessingContext context,
                                  @NotNull CompletionResultSet result) {
        PsiElement position = parameters.getPosition();

        // Expecting the completion to be invoked on an identifier part of a JSReferenceExpression
        // e.g., MyTheme.<caret> or MyTheme.colors.<caret>
        if (!(position.getParent() instanceof JSReferenceExpression)) {
            // LOG.debug("Parent of position is not JSReferenceExpression: " + position.getParent());
            return;
        }
        JSReferenceExpression parentRef = (JSReferenceExpression) position.getParent();

        // The qualifier is the part before the dot (e.g., "MyTheme" in "MyTheme.colors")
        PsiElement qualifierElement = parentRef.getQualifier();
        if (!(qualifierElement instanceof JSReferenceExpression)) {
            // This means we are likely at the first part of a potential theme reference (e.g. `MyTh<caret>`)
            // Or it's not a qualified reference.
            // For completing the theme alias itself, a different logic or an additional check might be needed.
            // For now, this provider focuses on properties *after* a theme alias is typed.
            // LOG.debug("Qualifier is not JSReferenceExpression: " + qualifierElement);
            return;
        }
        JSReferenceExpression qualifierRef = (JSReferenceExpression) qualifierElement;
        String themeAliasQualifier = qualifierRef.getReferenceName(); // This should be the theme alias

        if (themeAliasQualifier == null) {
            // LOG.debug("Qualifier reference name is null.");
            return;
        }

        Map<String, Object> selectedThemeObject = allThemes.get(themeAliasQualifier);
        if (selectedThemeObject == null) {
            // LOG.debug("No theme found for alias: " + themeAliasQualifier + ". Available themes: " + allThemes.keySet());
            return;
        }

        // LOG.debug("Providing completions for theme alias: " + themeAliasQualifier);
        List<String> keys = flattenKeys(selectedThemeObject, "");
        for (String key : keys) {
            result.addElement(LookupElementBuilder.create(key));
        }
    }

    /**
     * Flattens a nested map structure into a list of dot-separated keys.
     * For example, a map like `{"colors": {"primary": "blue"}}` would produce `["colors.primary"]`.
     * If a value is not a map, its full path is added.
     *
     * @param map The map to flatten.
     * @param prefix The current prefix for keys (used in recursion).
     * @return A list of flattened keys.
     */
    @SuppressWarnings("unchecked")
    private List<String> flattenKeys(Map<String, Object> map, String prefix) {
        List<String> flattenedKeys = new ArrayList<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String currentFullKey = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                try {
                    // If it's a map, recurse to get sub-keys
                    flattenedKeys.addAll(flattenKeys((Map<String, Object>) value, currentFullKey));
                } catch (ClassCastException e) {
                    // This might happen if the map is not Map<String, Object> despite TypeReference.
                    // In such a case, add the key itself as it cannot be expanded further.
                    LOG.warn("Could not cast nested map for key '" + currentFullKey + "'. Value type: " + value.getClass().getName() + ". Adding key as is.", e);
                    flattenedKeys.add(currentFullKey);
                }
            } else {
                // If it's a terminal value (not a map), add the full key path to it.
                flattenedKeys.add(currentFullKey);
            }
        }
        return flattenedKeys;
    }
}
