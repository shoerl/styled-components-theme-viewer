package com.example.styledcomponentsthemeviewer

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.example.styledcomponentsthemeviewer.ThemePluginSettingsState

class ThemePluginSettingsConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private val nodePathField = TextFieldWithBrowseButton()

    override fun getDisplayName(): String = "MUI Theme Support" // Name in settings tree

    override fun createComponent(): JComponent? {
        nodePathField.addBrowseFolderListener(
            "Select Node.js Executable",
            "Path to the Node.js executable",
            null,
            FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
        )

        settingsPanel = panel {
            row {
                val label = JBLabel("Node.js executable path:")
                cell(label)
                cell(nodePathField).growX()
            }
            row {
                comment("Specify the full path to the Node.js executable (e.g., /usr/local/bin/node or C:\\Program Files\\nodejs\\node.exe). Default is 'node' (uses system PATH).")
            }
        }
        return settingsPanel
    }

    override fun isModified(): Boolean {
        return nodePathField.text != ThemePluginSettingsState.Companion.instance.nodePath
    }

    override fun apply() {
        ThemePluginSettingsState.Companion.instance.nodePath = nodePathField.text
    }

    override fun reset() {
        nodePathField.text = ThemePluginSettingsState.Companion.instance.nodePath
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
