package com.example.themejsoncompletion;

import com.intellij.lang.javascript.psi.JSCallExpression;
import com.intellij.lang.javascript.psi.JSEmbeddedContent;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSFunction;
import com.intellij.lang.javascript.psi.JSReferenceExpression;
import com.intellij.lang.javascript.psi.ecma6.JSArrowFunction;
import com.intellij.lang.javascript.psi.ecma6.JSTaggedTemplateExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MuiPsiUtil {

    /**
     * Checks if the given JSReferenceExpression is likely part of an MUI theme access
     * within a styled-component context (e.g., styled`...${({theme}) => theme.palette.primary}`
     * or styled(Comp)(({theme}) => theme.palette.secondary)).
     *
     * @param refExpr The JSReferenceExpression to check.
     * @return true if it's a potential MUI theme access, false otherwise.
     */
    public static boolean isPotentialMuiThemeContext(@NotNull JSReferenceExpression refExpr) {
        // Check 1: Are we inside a styled component template or function argument?
        JSTaggedTemplateExpression taggedTemplate = PsiTreeUtil.getParentOfType(refExpr, JSTaggedTemplateExpression.class);
        JSCallExpression callExpression = PsiTreeUtil.getParentOfType(refExpr, JSCallExpression.class, true, JSTaggedTemplateExpression.class); // Stop at tagged template

        boolean inStyledRelatedContext = false;
        if (taggedTemplate != null) {
            JSExpression tag = taggedTemplate.getTag();
            if (tag != null && tag.getText().startsWith("styled")) {
                // Ensure refExpr is within the template part, not the tag itself.
                JSEmbeddedContent embeddedContent = PsiTreeUtil.getParentOfType(refExpr, JSEmbeddedContent.class);
                if (embeddedContent != null && PsiTreeUtil.isAncestor(taggedTemplate, embeddedContent, true)) {
                    inStyledRelatedContext = true;
                }
            }
        }

        if (!inStyledRelatedContext && callExpression != null) {
            // This handles styled(Component)(props => ...) or styled(Component)({ theme => ... })
            // We need to check if the callExpression is the one applying the styles (the second call in curried form, or the main call if direct object styles)
            JSExpression methodExpr = callExpression.getMethodExpression(); // Could be `styled(Button)` or `styled.div`
            if (methodExpr != null && methodExpr.getText().startsWith("styled")) {
                 // Check if refExpr is inside an arrow function or function expression that is an argument to this call
                PsiElement argParent = PsiTreeUtil.getParentOfType(refExpr, JSArrowFunction.class, JSFunction.class);
                if (argParent != null && PsiTreeUtil.isAncestor(callExpression.getArgumentList(), argParent, false)){
                    inStyledRelatedContext = true;
                }
            } else {
                 // Check for curried styled(Component)(({theme}) => ...)
                 // Here, callExpression is the outer call, e.g. styled(Button). The methodExpr is styled.
                 // The actual theme access is in an argument to a *subsequent* call expression.
                 // This case is complex; the current logic might catch it if the inner function call is the `callExpression`
                 // due to the stop condition in PsiTreeUtil.getParentOfType.
                 // A more robust way is to check if this callExpression's result is then called.
                 // For now, the existing logic might cover it if `refExpr` is inside the inner function.
            }
        }

        if (!inStyledRelatedContext) {
            // Also consider cases like sx prop: <Box sx={{ color: 'primary.main' }} />
            // This is more complex as it requires knowledge of specific prop names and component names.
            // Out of scope for this utility for now, but could be an extension.
            return false;
        }

        // Check 2: Does the reference chain start with 'theme' or 'props.theme'?
        JSReferenceExpression outermostRef = refExpr;
        while (outermostRef.getQualifier() instanceof JSReferenceExpression) {
            outermostRef = (JSReferenceExpression) outermostRef.getQualifier();
        }

        String baseName = outermostRef.getReferenceName();
        if ("theme".equals(baseName)) return true;

        if ("props".equals(baseName)) {
            // If base is 'props', the full reference must be 'props.theme...'
            // We need to check the actual path segments.
            // A simple text check on the full reference can be a starting point.
            PsiElement fullRefElement = PsiTreeUtil.getTopmostParentOfType(refExpr, JSReferenceExpression.class);
            if (fullRefElement != null && fullRefElement.getText().startsWith("props.theme")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the path segments (e.g., ["palette", "primary", "main"]) from a
     * JSReferenceExpression that is assumed to be an MUI theme access like
     * `theme.palette.primary.main` or `props.theme.spacing`.
     *
     * @param fullReferenceExpression The complete JSReferenceExpression (e.g., `theme.palette.primary.main`).
     * @return A list of path segments, or an empty list if not a valid theme path.
     *         The leading "theme" or "props.theme" is removed.
     */
    @NotNull
    public static List<String> extractThemePathSegments(@Nullable JSReferenceExpression fullReferenceExpression) {
        if (fullReferenceExpression == null) {
            return Collections.emptyList();
        }

        List<String> pathSegments = new ArrayList<>();
        JSReferenceExpression currentRef = fullReferenceExpression;

        while (currentRef != null) {
            String refName = currentRef.getReferenceName();
            if (refName != null) {
                pathSegments.add(0, refName); // Add to beginning to maintain order
            } else {
                // This can happen for incomplete references or weird PSI states.
                // If the last child is an identifier, try to use its text.
                PsiElement lastChild = currentRef.getLastChild();
                if (lastChild != null && lastChild.getNode().getElementType().toString().equals("JS:IDENTIFIER")) {
                    pathSegments.add(0, lastChild.getText());
                } else {
                    // Cannot determine segment name, path is likely invalid from this point
                    return Collections.emptyList();
                }
            }

            PsiElement qualifier = currentRef.getQualifier();
            if (qualifier instanceof JSReferenceExpression) {
                currentRef = (JSReferenceExpression) qualifier;
            } else {
                // Reached the start of the chain (qualifier is not a JSReferenceExpression, or null)
                currentRef = null;
            }
        }

        // Now, pathSegments contains the full path, e.g., ["theme", "palette", "primary"]
        // or ["props", "theme", "spacing"]. We need to validate and strip the base.
        if (!pathSegments.isEmpty()) {
            if ("theme".equals(pathSegments.get(0))) {
                return pathSegments.subList(1, pathSegments.size()); // Remove "theme"
            } else if (pathSegments.size() > 1 && "props".equals(pathSegments.get(0)) && "theme".equals(pathSegments.get(1))) {
                return pathSegments.subList(2, pathSegments.size()); // Remove "props" and "theme"
            }
        }

        return Collections.emptyList(); // Not a recognized theme path structure
    }
}
