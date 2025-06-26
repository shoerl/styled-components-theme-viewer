package com.example.themejsoncompletion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Provides code completion for theme JSON files.
 * This contributor registers a {@link ThemeJsonCompletionProvider} to offer suggestions
 * based on the loaded theme data.
 *
 * The current implementation targets {@link PsiNameIdentifierOwner} elements that are
 * children of {@link JSReferenceExpression}. This pattern suggests it might be
 * targeting JavaScript-like structures or a specific dialect of JSON (like JSON5)
 * where such references are valid. For standard JSON, one would typically target
 * JSON property names or string literals.
 *
 * TODO: Verify if targeting JSReferenceExpression is intended for the specific JSON files
 *       this plugin supports, or if it should be adjusted for standard JSON PSI elements
 *       (e.g., `JsonPsiUtil`, `JsonPropertyName`, `JsonStringLiteral`).
 */
public class ThemeJsonCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(ThemeJsonCompletionContributor.class);

    public ThemeJsonCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(PsiNameIdentifierOwner.class) // The element where completion is invoked
                        .withParent(PlatformPatterns.psiElement(JSReferenceExpression.class)), // Parent is a JSReferenceExpression
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet resultSet) {
                        PsiElement position = parameters.getPosition();
                        Project project = position.getProject();
                        if (project == null) {
                            LOG.warn("Cannot provide completions: Project is null.");
                            return;
                        }

                        ThemeDataManager themeDataManager = ThemeDataManager.getInstance(project);
                        if (themeDataManager == null) {
                            LOG.warn("ThemeDataManager service not available. Cannot provide completions.");
                            // System.err.println("ThemeJsonCompletionContributor: ThemeDataManager service not available.");
                            return;
                        }

                        Map<String, Map<String, Object>> allThemes = themeDataManager.getAllThemes();
                        if (allThemes == null || allThemes.isEmpty()) {
                            // LOG.debug("No themes available from ThemeDataManager. No completions to offer.");
                            // No need to print constantly, ThemeDataManager will log issues.
                            return;
                        }
                        // LOG.debug("Providing completions based on themes: " + allThemes.keySet());
                        // System.out.println("ThemeJsonCompletionContributor: Providing completions based on themes: " + allThemes.keySet());
                        new ThemeJsonCompletionProvider(allThemes).addCompletions(parameters, context, resultSet);
                    }
                }
        );
    }
}
