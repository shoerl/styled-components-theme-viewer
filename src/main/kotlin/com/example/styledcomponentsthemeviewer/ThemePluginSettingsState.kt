package com.example.styledcomponentsthemeviewer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
    name = "org.example.project.theme.ThemePluginSettingsState",
    storages = [Storage("MuiThemePluginSettings.xml")]
)
class ThemePluginSettingsState : PersistentStateComponent<ThemePluginSettingsState> {

    var nodePath: String = "node" // Default value

    override fun getState(): ThemePluginSettingsState {
        return this
    }

    override fun loadState(state: ThemePluginSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: ThemePluginSettingsState
            get() = ApplicationManager.getApplication().getService(ThemePluginSettingsState::class.java)
    }
}
