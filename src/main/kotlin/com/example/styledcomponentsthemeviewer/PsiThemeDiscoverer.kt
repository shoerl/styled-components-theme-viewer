package com.example.styledcomponentsthemeviewer

import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding

class PsiThemeDiscoverer(private val project: Project) {

    private val logger = thisLogger()

    companion object {
        // Only these names matter—if you see <ThemeProvider> or <StyledComponentsThemeProvider>, etc.
        private val THEME_PROVIDER_NAMES = setOf("ThemeProvider")
        private val CREATE_THEME_FUNCTION_NAMES = setOf("createTheme", "extendTheme")

        // Common “where MUI or styled-components theme providers live”:
        private val MUI_THEME_PROVIDER_PACKAGES = setOf(
            "@mui/material/styles",
            "@mui/system",
            "@material-ui/core/styles",
            "styled-components",
            "emotion-theming"
        )
    }

    /**
     * Given an ES6ImportedBinding (i.e. something like `import { something } from "…"`),
     * walk up to the ES6ImportDeclaration and attempt to resolve that module‐specifier
     * to a real PsiFile.  If it’s a relative path, we do a VfsUtil.findRelativeFile.
     * Otherwise, fall back to resolve() (e.g. for node_modules).
     */
    private fun resolveImportedElementToPsiFile(importedElement: ES6ImportedBinding): PsiFile? {
        // 1) Find the ES6ImportDeclaration (not the old JSImportStatement)
        val importDeclaration = PsiTreeUtil.getParentOfType(
            importedElement,
            ES6ImportDeclaration::class.java
        )
        if (importDeclaration == null) {
            logger.warn("Could not find ES6ImportDeclaration parent for `${importedElement.text}`")
            return null
        }

        // 2) Grab the “from …” clause.  In ecma6, that is JSFromClause.
        val fromClause: JSFromClause? = importDeclaration.fromClause
        val moduleRefText: String? = fromClause
            ?.importModuleReference  // e.g. the `"./myTheme"` or `"styled-components"`
            ?.text
            ?.removeSurrounding("\"", "'") // strip the quotes

        if (moduleRefText.isNullOrBlank()) {
            logger.warn("Could not extract module specifier text from import: `${importDeclaration.text}`")
            return null
        }

        // 3) Current file’s VirtualFile
        val currentVirtual = importedElement.containingFile?.virtualFile
        if (currentVirtual == null) {
            logger.warn("Containing file is null for import `${importDeclaration.text}`")
            return null
        }

        // 4) Try to interpret it as a relative path first
        val targetVf = VfsUtil.findRelativeFile(moduleRefText, currentVirtual.parent)
        // If that fails, try resolving via normal PSI resolution (e.g. node_modules, aliases, etc.)
            ?: run {
                importDeclaration.resolve()
                    ?.containingFile
                    ?.virtualFile
            }

        if (targetVf == null) {
            logger.warn("Could not resolve module '$moduleRefText' from `${currentVirtual.path}`")
            return null
        }

        val targetPsi = PsiManager.getInstance(project).findFile(targetVf)
        if (targetPsi == null) {
            logger.warn("Found VirtualFile but no PsiFile: `${targetVf.path}`")
            return null
        }

        logger.debug("Resolved import `$moduleRefText` → `${targetVf.path}`")
        return targetPsi
    }

    /**
     * Given any PsiElement that might represent a theme (object literal, variable, or a call to createTheme),
     * try to drill down to one of:
     *  - JSObjectLiteralExpression  (inline theme literal),
     *  - ES6ImportedBinding        (→ PsiFile via resolveImportedElementToPsiFile),
     *  - JSVariable                (→ initializer),
     *  - JSCallExpression          (i.e. createTheme({...}) → recurse into first arg).
     */
    private fun getThemeDefinitionElement(element: PsiElement?): PsiElement? {
        return when (element) {
            is JSObjectLiteralExpression -> element

            is JSVariable -> {
                // if `const myTheme = { … }` or `const myTheme = createTheme({...})`, recurse
                getThemeDefinitionElement(element.initializer)
            }

            is JSCallExpression -> {
                // e.g. createTheme(...) or extendTheme(...)
                val method = (element.methodExpression as? JSReferenceExpression)?.referenceName
                if (method != null && CREATE_THEME_FUNCTION_NAMES.contains(method)) {
                    val args = element.arguments
                    if (args.isNotEmpty()) {
                        return getThemeDefinitionElement(args[0])
                    } else {
                        logger.warn("`${method}` called with no args: `${element.text}`")
                        return null
                    }
                }
                null
            }

            is JSReferenceExpression -> {
                // Could be a local variable or an imported binding
                val resolved = element.resolve()
                if (resolved is ES6ImportedBinding) {
                    logger.debug("Reference `${element.text}` → imported. Tracing import…")
                    return resolveImportedElementToPsiFile(resolved)
                }
                if (resolved != null && resolved != element) {
                    // e.g. a local JSVariable or JSObjectLiteralExpression
                    logger.debug("Reference `${element.text}` → local element `${resolved.javaClass.simpleName}`")
                    return resolved
                }
                logger.warn("Cannot resolve JSReferenceExpression `${element.text}`")
                null
            }

            is PsiFile -> element
            else -> {
                logger.debug("Skipping unexpected PsiElement type: ${element?.javaClass?.simpleName}")
                null
            }
        }
    }

    /**
     * Checks whether an <XmlTag> (JSX/XML) is a ThemeProvider AND was imported from one of our known theme‐provider packages.
     */
    private fun isMuiThemeProviderTag(tag: XmlTag): Boolean {
        if (!THEME_PROVIDER_NAMES.contains(tag.name)) {
            return false
        }

        val containingPsiFile = tag.containingFile
        var foundImport = false

        // Collect all ES6 imports in this file
        PsiTreeUtil.collectElementsOfType(containingPsiFile, ES6ImportDeclaration::class.java).forEach { importDecl ->
            val moduleRef = importDecl.fromClause
                ?.referenceText
                ?.removeSurrounding("\"", "'")

            if (moduleRef != null && MUI_THEME_PROVIDER_PACKAGES.contains(moduleRef)) {
                // Now check if one of the specifiers matches <ThemeProvider> (or an alias)
                importDecl.importSpecifiers.forEach { spec ->
                    val importedName = spec.importedName
                    val aliasName = spec.aliasName
                    if (importedName == tag.name || aliasName == tag.name) {
                        foundImport = true
                        return@forEach
                    }
                }
            }
            if (foundImport) return@forEach
        }

        if (foundImport) {
            logger.debug("Confirmed <${tag.name}> in `${containingPsiFile.name}` is from `${
                containingPsiFile.virtualFile.path
            }` via one of $MUI_THEME_PROVIDER_PACKAGES")
            return true
        }

        // Even if we can’t see the import, we can still treat it as a ThemeProvider by name alone:
        logger.debug("Tag <${tag.name}> in `${containingPsiFile.name}` matches name but no explicit import from MUI packages.")
        return true
    }

    /**
     * Walk every .js/.jsx/.ts/.tsx in the project.  If we see a <ThemeProvider theme={…} />,
     * extract that “theme” expression, then follow it either in‐file or via import to return one path.
     *
     * Returns the first file‐path that held a theme definition.
     */
    fun discoverThemeInProject(): String? {
        logger.info("Starting theme discovery in project `${project.name}`")

        val projectScope = GlobalSearchScope.projectScope(project)
        // We want JavaScriptFileType and also TypeScriptJsxFileType:
        val fileTypes = listOf(JavaScriptFileType.INSTANCE, TypeScriptJsxFileType.INSTANCE)

        for (ft in fileTypes) {
            FileTypeIndex.getFiles(ft, projectScope).forEach { vf ->
                val psiFile = PsiManager.getInstance(project).findFile(vf) ?: return@forEach
                logger.debug("Scanning PSI file: `${psiFile.name}` @ `${vf.path}`")

                // Look for any JSX/HTML tag:
                PsiTreeUtil.collectElementsOfType(psiFile, XmlTag::class.java).forEach { tag ->
                    if (isMuiThemeProviderTag(tag)) {
                        logger.debug("Found <${tag.name}> in `${psiFile.name}`")
                        val themeAttr = tag.getAttribute("theme")
                        if (themeAttr != null) {
                            val valueElem = themeAttr.valueElement
                            val jsExpr = PsiTreeUtil.findChildOfType(valueElem, JSExpression::class.java)
                            if (jsExpr != null) {
                                logger.debug("theme={${jsExpr.text}} in `${psiFile.name}`")
                                val themeDef = getThemeDefinitionElement(jsExpr)
                                when (themeDef) {
                                    is PsiFile -> {
                                        logger.info("Theme imported from `${themeDef.virtualFile.path}`")
                                        return themeDef.virtualFile.path
                                    }
                                    is JSObjectLiteralExpression -> {
                                        logger.info("Inline theme object in `${psiFile.virtualFile.path}`")
                                        return psiFile.virtualFile.path
                                    }
                                    else -> {
                                        // Could be a JSVariable or something else; still return the containing file
                                        if (themeDef != null) {
                                            logger.info(
                                                "Theme defined in `${psiFile.virtualFile.path}` as `${themeDef.javaClass.simpleName}`"
                                            )
                                            return psiFile.virtualFile.path
                                        } else {
                                            logger.warn("Could not resolve theme definition for `<${tag.name}>` in `${psiFile.name}`")
                                        }
                                    }
                                }
                            } else {
                                logger.warn("No JSExpression found for theme attribute in `<${tag.name}>` of `${psiFile.name}`")
                            }
                        }
                    }
                }
            }
        }

        logger.info("No theme definition found in project.")
        return null
    }
}
