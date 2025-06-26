package com.example.themejsoncompletion;

// import com.intellij.codeInsight.hints.HintInfo; // Not directly used for newer API
import com.intellij.codeInsight.hints.InlayInfo;
// import com.intellij.codeInsight.hints.InlayParameterHintsProvider; // Deprecated
import com.intellij.codeInsight.hints.InlayHintsProvider;
import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.SettingsKey; // Required for getSettingsKey
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
// import com.intellij.lang.javascript.psi.JSCallExpression; // Not directly used after refactor
// import com.intellij.lang.javascript.psi.JSElement; // Not directly used after refactor
// import com.intellij.lang.javascript.psi.JSEmbeddedContent; // Not directly used after refactor
// import com.intellij.lang.javascript.psi.JSExpression; // Not directly used after refactor
// import com.intellij.lang.javascript.psi.JSFunction; // Not directly used after refactor
import com.intellij.lang.javascript.psi.JSReferenceExpression;
// import com.intellij.lang.javascript.psi.ecma6.JSArrowFunction; // Not directly used after refactor
// import com.intellij.lang.javascript.psi.ecma6.JSTaggedTemplateExpression; // Not directly used after refactor
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
// import com.intellij.psi.util.PsiTreeUtil; // Not directly used after refactor to MuiPsiUtil
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// import java.util.ArrayList; // Not used
// import java.util.Collections; // Not used
import java.util.List;
import java.util.Map;
// import java.util.Set; // Not used

public class MuiThemeInlayHintsProvider implements InlayHintsProvider<NoSettings> {

    private static final Logger LOG = Logger.getInstance(MuiThemeInlayHintsProvider.class);
    private static final String PROVIDER_ID = "mui.theme.hints";

    @Nullable
    @Override
    public PsiElementVisitor createVisitor(@NotNull PsiFile file,
                                           @NotNull Editor editor,
                                           @NotNull InlayHintsSink sink,
                                           @NotNull NoSettings settings) {
        Project project = file.getProject();
        ThemeDataManager themeDataManager = ThemeDataManager.getInstance(project);
        if (themeDataManager == null) {
            LOG.debug("InlayHints: ThemeDataManager not available for project: " + project.getName());
            return null;
        }
        Map<String, Object> muiTheme = themeDataManager.getMuiTheme();
        if (muiTheme == null || muiTheme.isEmpty()) {
            LOG.debug("InlayHints: MUI Theme not loaded or empty for project: " + project.getName());
            return null;
        }

        return new PsiRecursiveElementVisitor() {
            @Override
            public void visitElement(@NotNull PsiElement element) {
                super.visitElement(element);

                if (!(element instanceof JSReferenceExpression)) {
                    return;
                }

                JSReferenceExpression refExpr = (JSReferenceExpression) element;

                // Ensure this is the fully qualified reference, not a partial one.
                // Inlay hints should appear at the end of a complete theme path.
                if (refExpr.getQualifier() == null && !(refExpr.getText().equals("theme") || refExpr.getText().startsWith("props.theme"))) {
                     // If it has no qualifier, it must be 'theme' itself or 'props.theme' to start a chain.
                     // We are interested in the *ends* of chains like theme.palette.primary.main
                     // This check might be too simplistic. The main check is isPotentialMuiThemeContext.
                }
                if (refExpr.getQualifier() != null && refExpr.getQualifier() instanceof JSReferenceExpression && refExpr.getQualifier().getText().equals("props") && !refExpr.getReferenceName().equals("theme")) {
                    // This is props.somethingNotTheme - skip
                    // We want props.theme.X
                }


                if (!MuiPsiUtil.isPotentialMuiThemeContext(refExpr)) {
                    // LOG.trace("InlayHints: Not a potential MUI theme context for ref: " + refExpr.getText());
                    return;
                }

                List<String> pathSegments = MuiPsiUtil.extractThemePathSegments(refExpr);
                if (pathSegments.isEmpty()) {
                    // LOG.trace("InlayHints: Could not extract path segments for ref: " + refExpr.getText());
                    return;
                }

                Object value = MuiThemeValueResolver.resolveValue(muiTheme, pathSegments);

                if (value != null && !(value instanceof Map) && !(value instanceof List)) {
                    String inlayText = value.toString();
                    if (inlayText.length() > 50) {
                        inlayText = inlayText.substring(0, 47) + "...";
                    }
                    if (value instanceof String) {
                        inlayText = "\"" + inlayText + "\""; // Add quotes for string values
                    }

                    PresentationFactory factory = new PresentationFactory(editor);
                    // Using addInlineElement for hints at the end of the expression
                    sink.addInlineElement(refExpr.getTextRange().getEndOffset(),
                                          false, // relatesToPrecedingText = false
                                          factory.smallText(":" + inlayText),
                                          false); // isLeading = false
                    LOG.debug("InlayHints: Added hint ':" + inlayText + "' for path: " + String.join(".", pathSegments));
                }
            }
        };
    }

    // Removed isPotentialThemeAccess and getPathFromReference as logic is now in MuiPsiUtil

    @NotNull
    @Override
    public String getName() {
        return "MUI Theme Value Hints";
    }

    @NotNull
    @Override
    public String getKey() {
        return PROVIDER_ID;
    }

    @Nullable
    @Override
    public String getPreviewText() {
        // Example: theme.palette.primary.main: "#123456"
        return "const Button = styled.button`\n color: ${({theme}) => theme.palette.primary.main}; \n // Hint will show :\"#1976d2\"\n`";
    }

    @NotNull
    @Override
    public SettingsKey<NoSettings> getSettingsKey() {
        return new SettingsKey<>(PROVIDER_ID);
    }

    @NotNull
    @Override
    public NoSettings createSettings() {
        return new NoSettings();
    }

    @Override
    public void collectTraverser(@NotNull PsiFile file,
                                 @NotNull Editor editor,
                                 @NotNull InlayHintsSink sink,
                                 @NotNull NoSettings settings,
                                 @NotNull List<? super PsiElement> elements) {
        // Default implementation is fine for now; visitor handles filtering.
        InlayHintsProvider.super.collectTraverser(file, editor, sink, settings, elements);
    }
}

// Helper class for resolving values, can be expanded or moved
// (Assuming MuiThemeValueResolver is defined elsewhere or kept here if not too large)
// For this exercise, it's already provided in a previous step.
// class MuiThemeValueResolver { ... }

// We are using NoSettings for now.
// class NoSettings {} // Already provided in a previous step.
