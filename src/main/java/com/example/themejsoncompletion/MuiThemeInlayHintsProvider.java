package com.example.themejsoncompletion;

// import com.intellij.codeInsight.hints.HintInfo; // Not directly used for newer API
import com.intellij.codeInsight.hints.InlayInfo;
// import com.intellij.codeInsight.hints.InlayParameterHintsProvider; // Deprecated
import com.intellij.codeInsight.hints.InlayHintsProvider;
import com.intellij.codeInsight.hints.InlayHintsSink;
import com.intellij.codeInsight.hints.SettingsKey; // Required for getSettingsKey
import com.intellij.codeInsight.hints.presentation.PresentationFactory;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.List;
import java.util.Map;


public class MuiThemeInlayHintsProvider implements InlayHintsProvider<NoSettings> {

    private static final Logger LOG = Logger.getInstance(MuiThemeInlayHintsProvider.class);
    private static final String PROVIDER_ID = "mui.theme.value.hints"; // Changed ID slightly for clarity
    private static final SettingsKey<NoSettings> SETTINGS_KEY = new SettingsKey<>(PROVIDER_ID);


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

                // We only want to show hints at the *end* of a complete theme path reference.
                // Example: For `theme.palette.primary.main`, the hint appears after `main`.
                // If `refExpr.getQualifier()` is null, it means it's a top-level reference (e.g. `theme`).
                // If `refExpr.getQualifier()` is not a `JSReferenceExpression`, it's also the start of a chain we might be interested in.
                // However, `isPotentialMuiThemeContext` should handle the overall context.
                // The main check: is this specific `refExpr` something that has a resolvable value?
                // (i.e. not an intermediate part like `theme.palette` unless palette itself is a string/number)

                if (!MuiPsiUtil.isPotentialMuiThemeContext(refExpr)) {
                    return;
                }

                List<String> pathSegments = MuiPsiUtil.extractThemePathSegments(refExpr);
                if (pathSegments.isEmpty()) {
                    // This can happen if refExpr is just "theme" or "props.theme"
                    // and we don't want to show a hint for the theme object itself.
                    return;
                }

                Object value = MuiThemeValueResolver.resolveValue(muiTheme, pathSegments);

                if (value != null && !(value instanceof Map) && !(value instanceof List)) {
                    // Value is a primitive or string
                    String inlayText = value.toString();
                    Color colorValue = null;

                    if (value instanceof String) {
                        String strValue = (String) value;
                        if (strValue.matches("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")) {
                            try {
                                colorValue = Color.decode(strValue);
                            } catch (NumberFormatException e) { /* ignore */ }
                        }
                        // Truncate long strings for display
                        if (inlayText.length() > 30) {
                            inlayText = "\"" + inlayText.substring(0, 27) + "...\"";
                        } else {
                            inlayText = "\"" + inlayText + "\"";
                        }
                    } else if (value instanceof Number) {
                        // Potentially append "px" if key suggests it (very basic heuristic)
                        String lastSegment = pathSegments.get(pathSegments.size() - 1).toLowerCase();
                        if (lastSegment.contains("radius") || lastSegment.contains("width") ||
                            lastSegment.contains("height") || lastSegment.contains("size") ||
                            lastSegment.contains("padding") || lastSegment.contains("margin") ||
                            lastSegment.contains("offset") || lastSegment.endsWith("font") /* e.g. fontSize */ ) {
                            // inlayText += "px"; // Decided against auto-px for now to avoid incorrect assumptions
                        }
                         if (inlayText.length() > 30) {
                            inlayText = inlayText.substring(0, 27) + "...";
                        }
                    }


                    PresentationFactory factory = new PresentationFactory(editor);
                    com.intellij.codeInsight.hints.presentation.InsetPresentation textPresentation =
                        factory.smallText(" = " + inlayText);

                    com.intellij.codeInsight.hints.presentation.Presentation finalPresentation;
                    if (colorValue != null) {
                        finalPresentation = factory.seq(
                            factory.smallSquare(new JBColor(colorValue,colorValue)),
                            textPresentation
                        );
                    } else {
                        finalPresentation = textPresentation;
                    }

                    // Apply a slightly subdued text attribute
                    TextAttributes attributes = editor.getColorsScheme().getAttributes(com.intellij.openapi.editor.DefaultLanguageHighlighterColors.LINE_COMMENT);
                    com.intellij.codeInsight.hints.presentation.Presentation styledPresentation = factory.roundWithBackgroundAndText(attributes, finalPresentation);


                    sink.addInlineElement(refExpr.getTextRange().getEndOffset(),
                                          false, // relatesToPrecedingText
                                          styledPresentation,
                                          false); // isLeading
                    LOG.debug("InlayHints: Added hint '" + inlayText + "' for path: " + String.join(".", pathSegments));
                }
            }
        };
    }

    @NotNull
    @Override
    public String getName() {
        return "MUI Theme: Resolved Values";
    }

    @NotNull
    @Override
    public String getKey() { // Corresponds to SettingsKey ID
        return SETTINGS_KEY.getId();
    }

    @Nullable
    @Override
    public String getPreviewText() {
        return "const MyComponent = styled.div`\n" +
               "  color: ${({ theme }) => theme.palette.primary.main}; // = \"#1976d2\"\n" +
               "  border-radius: ${({ theme }) => theme.shape.borderRadius}px; // = 4\n" +
               "`";
    }

    @NotNull
    @Override
    public SettingsKey<NoSettings> getSettingsKey() {
        return SETTINGS_KEY;
    }

    @NotNull
    @Override
    public NoSettings createSettings() {
        return new NoSettings();
    }

    // Default collectTraverser is fine; visitor does the filtering.
}
// NoSettings class is implicitly available or defined in a shared context
// if not, it would be: public static class NoSettings {}
