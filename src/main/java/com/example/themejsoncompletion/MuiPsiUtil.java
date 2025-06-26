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
        boolean inStyledCallOrTaggedTemplate = false;

        // Check 1: Inside JSTaggedTemplateExpression (styled`...`)
        JSTaggedTemplateExpression taggedTemplate = PsiTreeUtil.getParentOfType(refExpr, JSTaggedTemplateExpression.class);
        if (taggedTemplate != null) {
            JSExpression tag = taggedTemplate.getTag();
            // Check if the tag is `styled.foo` or `styled(Component)`
            if (tag instanceof JSReferenceExpression && tag.getText().startsWith("styled")) { // styled.div, styled.button
                JSEmbeddedContent embeddedContent = PsiTreeUtil.getParentOfType(refExpr, JSEmbeddedContent.class);
                if (embeddedContent != null && PsiTreeUtil.isAncestor(taggedTemplate, embeddedContent, true)) {
                    inStyledCallOrTaggedTemplate = true;
                }
            } else if (tag instanceof JSCallExpression && tag.getText().startsWith("styled")) { // styled(Component)
                JSEmbeddedContent embeddedContent = PsiTreeUtil.getParentOfType(refExpr, JSEmbeddedContent.class);
                if (embeddedContent != null && PsiTreeUtil.isAncestor(taggedTemplate, embeddedContent, true)) {
                    inStyledCallOrTaggedTemplate = true;
                }
            }
        }

        // Check 2: Inside JSCallExpression (styled(Component)(args...) or styled.div(args...))
        if (!inStyledCallOrTaggedTemplate) {
            // Iterate upwards to find the relevant "styled" call.
            // We are looking for `styled(...)` or `styled.foo(...)` or `styled(Bar)(...)`
            // The refExpr should be inside a function argument of one of these.
            PsiElement current = refExpr;
            while (current != null && !(current instanceof PsiFile)) {
                if (current instanceof JSCallExpression) {
                    JSCallExpression callExpr = (JSCallExpression) current;
                    JSExpression methodExpression = callExpr.getMethodExpression();
                    String methodText = methodExpression != null ? methodExpression.getText() : "";

                    boolean isStyledCall = false;
                    if (methodText.startsWith("styled")) { // Covers styled.div(), styled(Component)()
                        isStyledCall = true;
                    } else if (methodExpression instanceof JSReferenceExpression) {
                        // Handles the case of styled(Component)(args) where methodExpression is the `styled(Component)` part
                        JSExpression qualifier = ((JSReferenceExpression) methodExpression).getQualifier();
                        if (qualifier instanceof JSCallExpression && qualifier.getText().startsWith("styled")) {
                           // This means `methodExpression` is the result of `styled(Component)`, so `callExpr` is the one with style function
                           isStyledCall = true;
                        }
                    }


                    if (isStyledCall) {
                        // Check if refExpr is within an argument of this styled call, specifically in a function
                        PsiElement argParentFunction = PsiTreeUtil.getParentOfType(refExpr, JSArrowFunction.class, JSFunction.class);
                        if (argParentFunction != null && PsiTreeUtil.isAncestor(callExpr.getArgumentList(), argParentFunction, false)) {
                            inStyledCallOrTaggedTemplate = true;
                            break; // Found the styled context
                        }
                    }
                }
                current = current.getParent();
            }
        }

        if (!inStyledCallOrTaggedTemplate) {
            return false; // Not in a recognized styled-component structure
        }

        // Check 3: Does the reference chain start with 'theme' or 'props.theme'?
        // This check is crucial to ensure we are actually trying to access the theme.
        JSReferenceExpression outermostRef = refExpr;
        while (outermostRef.getQualifier() instanceof JSReferenceExpression) {
            outermostRef = (JSReferenceExpression) outermostRef.getQualifier();
        }

        String baseName = outermostRef.getReferenceName();
        if (baseName == null) return false;

        if (baseName.equals("theme")) {
            return true; // Directly accessing `theme.something`
        }

        if (baseName.equals("props")) {
            // If base is 'props', the full reference must be 'props.theme...'
            // We check this by looking at the segments of the original refExpr.
            List<String> segments = extractThemePathSegmentsFromFullChain(refExpr); // Use a helper that gives ["props", "theme", ...]
            if (segments.size() >= 2 && segments.get(0).equals("props") && segments.get(1).equals("theme")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Helper to get all segments of a JSReferenceExpression chain, e.g., props.theme.palette -> ["props", "theme", "palette"]
     */
    @NotNull
    private static List<String> extractThemePathSegmentsFromFullChain(@Nullable JSReferenceExpression fullReferenceExpression) {
        if (fullReferenceExpression == null) {
            return Collections.emptyList();
        }
        List<String> pathSegments = new ArrayList<>();
        JSReferenceExpression currentRef = fullReferenceExpression;
        while (currentRef != null) {
            String refName = currentRef.getReferenceName();
            if (refName != null) {
                pathSegments.add(0, refName);
            } else {
                PsiElement lastChild = currentRef.getLastChild();
                if (lastChild != null && lastChild.getNode().getElementType().toString().equals("JS:IDENTIFIER")) {
                    pathSegments.add(0, lastChild.getText());
                } else {
                    return Collections.emptyList(); // Invalid segment
                }
            }
            PsiElement qualifier = currentRef.getQualifier();
            if (qualifier instanceof JSReferenceExpression) {
                currentRef = (JSReferenceExpression) qualifier;
            } else {
                currentRef = null;
            }
        }
        return pathSegments;
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
