package com.example.themejsoncompletion.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the persistent state for the Theme JSON Autocompletion plugin's settings.
 * This class implements {@link PersistentStateComponent} to allow IntelliJ IDEA
 * to save and load its state automatically.
 *
 * The settings are stored on a per-project basis in a file named `themeJsonCompletionSettings.xml`
 * within the project's `.idea` directory (or an equivalent configuration directory).
 *
 * It stores:
 * - {@code themeImportsJsonPath}: Path to `theme-imports.json` for generic JSON themes.
 * - {@code muiThemeFilePath}: Path to the JavaScript/TypeScript MUI theme file.
 */
@State(
        name = "com.example.themejsoncompletion.settings.ThemeJsonSettingsState",
        storages = @Storage("themeJsonCompletionSettings.xml") // Specifies the file to store settings in (project-level)
)
public class ThemeJsonSettingsState implements PersistentStateComponent<ThemeJsonSettingsState> {

    /**
     * Path to the `theme-imports.json` file, relative to the project root.
     * An empty string indicates that the default `theme-imports.json` from
     * the plugin's resources should be used for generic JSON themes.
     */
    public String themeImportsJsonPath = "";

    /**
     * Path to the MUI theme file (e.g., theme.ts or theme.js), relative to the project root.
     * An empty string indicates that MUI theme features should be disabled or may not function.
     */
    public String muiThemeFilePath = "src/theme.ts"; // Default common path

    /**
     * Retrieves the singleton instance of {@link ThemeJsonSettingsState} for the given project.
     * IntelliJ IDEA manages the lifecycle and instantiation of services, including persistent state components.
     *
     * @param project The project for which the settings state is requested.
     * @return The instance of {@link ThemeJsonSettingsState} for the project.
     *         Returns {@code null} if the service cannot be retrieved (e.g., if project is not yet initialized or disposed).
     */
    public static ThemeJsonSettingsState getInstance(Project project) {
        if (project == null || project.isDisposed()) {
            // Consider logging a warning if project is null or disposed, as this might indicate an issue.
            // For now, returning null is consistent with how getService behaves in such cases.
            return null;
        }
        return project.getService(ThemeJsonSettingsState.class);
    }

    /**
     * Returns the current state of this component. This state object will be serialized to XML.
     *
     * @return The current state (this instance).
     */
    @Nullable
    @Override
    public ThemeJsonSettingsState getState() {
        return this;
    }

    /**
     * Loads the state of the component from the persisted XML.
     * This method is called by IntelliJ IDEA when the component is initialized and its state is found.
     *
     * @param state The state object loaded from XML.
     */
    @Override
    public void loadState(@NotNull ThemeJsonSettingsState state) {
        // Copies all public fields from the loaded state object to this instance.
        XmlSerializerUtil.copyBean(state, this);
    }
}
