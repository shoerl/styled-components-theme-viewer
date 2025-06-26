package com.example.themejsoncompletion;

import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
// import com.intellij.psi.util.PsiTreeUtil; // Not directly used after refactor
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Nls;

import java.util.List;
import java.util.Map;

public class MuiThemeDocumentationProvider extends AbstractDocumentationProvider {

    private static final Logger LOG = Logger.getInstance(MuiThemeDocumentationProvider.class);

    @Override
    @Nullable
    @Nls
    public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        // The 'element' is usually the specific token under cursor (e.g., an identifier).
        // 'originalElement' is often the containing expression or a more relevant parent.
        // We are interested in the JSReferenceExpression.
        JSReferenceExpression refExpr = null;
        if (element instanceof JSReferenceExpression) {
            refExpr = (JSReferenceExpression) element;
        } else if (originalElement instanceof JSReferenceExpression) {
            refExpr = (JSReferenceExpression) originalElement;
        } else if (element.getParent() instanceof JSReferenceExpression) {
            // Handles cases where 'element' is an identifier token within the reference
            refExpr = (JSReferenceExpression) element.getParent();
        }

        if (refExpr == null) {
            return null;
        }

        if (!MuiPsiUtil.isPotentialMuiThemeContext(refExpr)) {
            // LOG.trace("DocProvider: Not a potential MUI theme context for ref: " + refExpr.getText());
            return null;
        }

        Project project = refExpr.getProject();
        ThemeDataManager themeDataManager = ThemeDataManager.getInstance(project);
        if (themeDataManager == null) {
            LOG.debug("DocProvider: ThemeDataManager not available for project: " + project.getName());
            return null;
        }
        Map<String, Object> muiTheme = themeDataManager.getMuiTheme();
        if (muiTheme == null || muiTheme.isEmpty()) {
            LOG.debug("DocProvider: MUI Theme not loaded or empty for project: " + project.getName());
            return null;
        }

        List<String> pathSegments = MuiPsiUtil.extractThemePathSegments(refExpr);
        if (pathSegments.isEmpty()) {
            // LOG.trace("DocProvider: Could not extract valid theme path from: " + refExpr.getText());
            return null;
        }

        Object value = MuiThemeValueResolver.resolveValue(muiTheme, pathSegments);

        if (value != null) {
            String fullPath = "theme." + String.join(".", pathSegments); // Reconstruct full path for display
            StringBuilder doc = new StringBuilder();
            doc.append("<html><body>");
            doc.append("<b>MUI Theme Path:</b> <code>").append(escapeHtml(fullPath)).append("</code><br/>");
            doc.append("<b>Resolved Value:</b> ");

            if (value instanceof Map) {
                doc.append("<i>Object</i> <pre>{...}</pre>"); // Basic representation for objects
            } else if (value instanceof List) {
                doc.append("<i>Array</i> <pre>[...]</pre>"); // Basic representation for arrays
            } else if (value instanceof String) {
                String strValue = (String) value;
                doc.append("<code>\"").append(escapeHtml(strValue)).append("\"</code>");
                if (strValue.matches("^#([0-9A-Fa-f]{3}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")) {
                    doc.append(" <span style='display:inline-block; width:12px; height:12px; border:1px solid #ccc; background-color:")
                       .append(escapeHtml(strValue)) // Escape here too for safety
                       .append(";'></span>");
                }
            } else {
                doc.append("<code>").append(escapeHtml(value.toString())).append("</code>");
            }
            doc.append("</body></html>");
            LOG.debug("DocProvider: Generated doc for " + fullPath + ": " + value);
            return doc.toString();
        }
        return null;
    }

    // Removed getPathFromReferenceForDoc as logic is now in MuiPsiUtil

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    @Override
    public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        // This could show a simplified version of the doc, e.g., just path and value.
        // For now, focusing on the main hover documentation.
        return null;
    }
}
