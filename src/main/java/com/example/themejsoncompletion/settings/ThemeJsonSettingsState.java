package com.example.themejsoncompletion.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(
        name = "com.example.themejsoncompletion.settings.ThemeJsonSettingsState",
        storages = @Storage("themeJsonCompletionSettings.xml")
)
public class ThemeJsonSettingsState implements PersistentStateComponent<ThemeJsonSettingsState> {

    public String themeImportsJsonPath = ""; // Default to empty, meaning use plugin resources

    public static ThemeJsonSettingsState getInstance(Project project) {
        return project.getService(ThemeJsonSettingsState.class);
    }

    @Nullable
    @Override
    public ThemeJsonSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ThemeJsonSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
