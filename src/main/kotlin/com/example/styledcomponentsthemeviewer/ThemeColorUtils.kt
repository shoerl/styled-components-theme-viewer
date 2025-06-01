package com.example.styledcomponentsthemeviewer

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.ColorIcon
import java.awt.Color
import javax.swing.Icon

fun parseColor(colorString: String): Color? {
    return try {
        // Attempt to parse hex (e.g. #FFF, #FFFFFF) or rgb/rgba (e.g. rgb(0,0,0))
        // ColorUtil.fromHex doesn't support all CSS color names directly.
        // ColorUtil.fromRgb/fromRgba might be specific.
        // For a more robust solution, a dedicated CSS color parsing library might be needed.
        val trimmed = colorString.trim()
        if (trimmed.startsWith("#")) {
            ColorUtil.fromHex(trimmed, null)
        } else if (trimmed.startsWith("rgb")) { // Catches rgb and rgba
             ColorUtil.fromRgb(trimmed, null) // This might handle rgba too, or ColorUtil.fromRgba can be specific
        } else if (trimmed.startsWith("hsl")) { // Catches hsl and hsla
            // IntelliJ's ColorUtil doesn't have a direct HSL parser as of some versions.
            // This would require a custom parser or a third-party library for full HSL support.
            // For now, we'll return null for HSL, indicating it's not directly supported by this simple parser.
            null
        }
        else {
            // Could try to match common color names, but this is prone to errors.
            null
        }
    } catch (e: Exception) {
        // Exceptions can occur for malformed strings.
        null
    }
}

fun isPotentiallyColorString(value: String): Boolean {
    val s = value.toLowerCase().trim()
    // Keep this check broad, parseColor will do the actual validation
    return s.startsWith("#") || s.startsWith("rgb(") || s.startsWith("rgba(") || s.startsWith("hsl(") || s.startsWith("hsla(") || ColorUtil.getColorNames().contains(s)
}

class ColorGutterIconRenderer(private val color: Color) : GutterIconRenderer() {
    // Using a standard 12x12 ColorIcon. Size can be adjusted.
    private val icon: Icon = ColorIcon(12, color, true) // true for isEditable=false, makes it a plain square

    override fun getIcon(): Icon = icon
    override fun getTooltipText(): String = "Color: ${ColorUtil.toHex(color, true)}" // Show hex with alpha if present
    override fun equals(other: Any?): Boolean = (other as? ColorGutterIconRenderer)?.color == color
    override fun hashCode(): Int = color.hashCode()
    override fun isNavigateAction(): Boolean = false // Set to true to handle click, e.g., open color picker
    // override fun getClickAction(): AnAction? = if (isNavigateAction) ... else null
}
