package com.example.themejsoncompletion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


public class ThemeJsonCompletionContributor extends CompletionContributor {

    public ThemeJsonCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement(PsiNameIdentifierOwner.class)
                        .withParent(PlatformPatterns.psiElement(JSReferenceExpression.class)),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet resultSet) {
                        PsiElement position = parameters.getPosition();
                        Project project = position.getProject();
                        if (project == null) {
                            return;
                        }

                        ThemeDataManager themeDataManager = ThemeDataManager.getInstance(project);
                        if (themeDataManager == null) {
                            System.err.println("ThemeJsonCompletionContributor: ThemeDataManager service not available.");
                            return;
                        }

                        Map<String, Map<String, Object>> allThemes = themeDataManager.getAllThemes();
                        if (allThemes == null || allThemes.isEmpty()) {
                            // System.out.println("ThemeJsonCompletionContributor: No themes available from ThemeDataManager.");
                            // No need to print constantly, ThemeDataManager will log issues.
                            return;
                        }
                         // System.out.println("ThemeJsonCompletionContributor: Providing completions based on themes: " + allThemes.keySet());
                        new ThemeJsonCompletionProvider(allThemes).addCompletions(parameters, context, resultSet);
                    }
                }
        );
    }
}
