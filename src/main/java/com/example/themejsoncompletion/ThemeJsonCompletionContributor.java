package com.example.themejsoncompletion;

import com.intellij.codeInsight.completion.*;
import com.intellij.lang.javascript.JSTokenTypes;
import com.intellij.lang.javascript.psi.*;
import com.intellij.lang.javascript.psi.ecma6.JSArrowFunction;
import com.intellij.lang.javascript.psi.ecma6.JSTaggedTemplateExpression;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Provides code completion for theme values within styled-component template literals
 * and other MUI theme access points.
 */
public class ThemeJsonCompletionContributor extends CompletionContributor {
    private static final Logger LOG = Logger.getInstance(ThemeJsonCompletionContributor.class);

    public ThemeJsonCompletionContributor() {
        // This pattern broadly identifies potential locations for MUI theme completion.
        // The MuiPsiUtil.isPotentialMuiThemeContext check inside addCompletions will refine this.
        PsiElementPattern.Capture<PsiElement> muiThemePattern = PlatformPatterns.psiElement(JSTokenTypes.IDENTIFIER)
                .withParent(PlatformPatterns.psiElement(JSReferenceExpression.class)
                    .withSuperParent(2, PlatformPatterns.or( // Check grandparent or great-grandparent for styled context
                        PlatformPatterns.psiElement(JSEmbeddedContent.class), // Inside ${...}
                        PlatformPatterns.psiElement(JSArgumentList.class) // Inside function arguments like ({theme}) => ...
                )));
        // A simpler, broader pattern. The real check is MuiPsiUtil.isPotentialMuiThemeContext.
        // This pattern targets an identifier that is part of a JSReferenceExpression.

        extend(CompletionType.BASIC, muiThemePattern, new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters parameters,
                                          @NotNull ProcessingContext context,
                                          @NotNull CompletionResultSet resultSet) {
                PsiElement position = parameters.getPosition(); // This is often the IDENTIFIER token
                Project project = position.getProject();

                JSReferenceExpression refExpr = PsiTreeUtil.getParentOfType(position, JSReferenceExpression.class, false);
                if (refExpr == null) {
                    // If position itself is a ref (e.g. when completing `theme.<caret>`), its parent might not be.
                    // This case should be handled by ensuring refExpr is the one we analyze.
                    if (position.getParent() instanceof JSReferenceExpression) {
                        refExpr = (JSReferenceExpression) position.getParent();
                    } else {
                        LOG.debug("MUI Contributor: No JSReferenceExpression found for position: " + position.getText());
                        return;
                    }
                }

                // Use MuiPsiUtil to determine if this is a valid context
                if (!MuiPsiUtil.isPotentialMuiThemeContext(refExpr)) {
                    // LOG.trace("MUI Contributor: Not a potential MUI theme context for ref: " + refExpr.getText());
                    return;
                }

                if (project == null) {
                    LOG.warn("MUI Contributor: Project is null.");
                    return;
                }

                ThemeDataManager themeDataManager = ThemeDataManager.getInstance(project);
                if (themeDataManager == null) {
                    LOG.warn("MUI Contributor: ThemeDataManager service not available.");
                    return;
                }

                Map<String, Object> muiTheme = themeDataManager.getMuiTheme();
                if (muiTheme == null || muiTheme.isEmpty()) {
                    LOG.debug("MUI Contributor: MUI theme not available or empty.");
                    return;
                }

                LOG.debug("MUI Contributor: Providing completions for: " + refExpr.getText());
                new MuiThemeCompletionProvider(muiTheme).addCompletions(parameters, context, resultSet);
            }
        });

        // Fallback for generic JSON theme completion (original logic)
        extend(
                CompletionType.BASIC,
                // Original broader pattern for JSON files or general JS object property access
                PlatformPatterns.psiElement().withParent(JSReferenceExpression.class),
                new CompletionProvider<CompletionParameters>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet resultSet) {
                        PsiElement position = parameters.getPosition();
                        Project project = position.getProject();
                        if (project == null) { return; }

                        // Check if we are in an MUI context; if so, the MUI provider already handled it.
                        JSReferenceExpression refExpr = PsiTreeUtil.getParentOfType(position, JSReferenceExpression.class, false);
                        if (refExpr != null && MuiPsiUtil.isPotentialMuiThemeContext(refExpr)) {
                            // LOG.trace("Fallback Contributor: Skipping, potential MUI context for: " + refExpr.getText());
                            return;
                        }

                        ThemeDataManager themeDataManager = ThemeDataManager.getInstance(project);
                        if (themeDataManager == null) { return; }

                        Map<String, Map<String, Object>> allThemes = themeDataManager.getAllJsonThemes(); // Use specific getter
                        if (allThemes == null || allThemes.isEmpty()) {
                            return;
                        }

                        // The old ThemeJsonCompletionProvider expects a theme alias as the qualifier.
                        // This part of the logic might need review if it conflicts with MUI theme structure
                        // or if `getAllJsonThemes` could somehow include something `MuiPsiUtil` doesn't catch.
                        // For now, assume `MuiPsiUtil.isPotentialMuiThemeContext` is sufficient to separate concerns.

                        // LOG.debug("Fallback Contributor: Providing JSON theme completions for: " + position.getText());
                        new ThemeJsonCompletionProvider(allThemes).addCompletions(parameters, context, resultSet);
                    }
                }
        );
    }
}
