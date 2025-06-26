package com.example.themejsoncompletion.settings;

import com.example.themejsoncompletion.ThemeDataManager;
import com.example.themejsoncompletion.ThemeRefreshTrigger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

/**
 * Controller for the settings UI of the Theme JSON Autocompletion plugin.
 * This class implements {@link Configurable} to provide a settings page within
 * the IntelliJ IDEA settings dialog (File > Settings > Tools > Theme JSON Autocompletion).
 *
 * It allows users to specify a project-relative path to a `theme-imports.json` file,
 * which dictates how themes are loaded by {@link ThemeDataManager}.
 *
 * When the path is changed and applied, it triggers a refresh of the theme data
 * and clears caches in {@link ThemeRefreshTrigger} to ensure that file watchers
 * and data loaders use the new configuration.
 */
public class ThemeJsonSettingsConfigurable implements Configurable {

    private static final Logger LOG = Logger.getInstance(ThemeJsonSettingsConfigurable.class);
    private final Project project;
    private JPanel myMainPanel;
    private TextFieldWithBrowseButton themeImportsJsonPathField;

    /**
     * Constructs the settings configurable for a given project.
     * @param project The current project.
     */
    public ThemeJsonSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Theme JSON Autocompletion";
    }

    /**
     * Creates the Swing component for the settings UI.
     * This panel includes a text field with a browse button to select the `theme-imports.json` file.
     * The initial value is loaded from {@link ThemeJsonSettingsState}.
     * @return The main panel for the settings UI.
     */
    @Nullable
    @Override
    public JComponent createComponent() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        themeImportsJsonPathField = new TextFieldWithBrowseButton();
        themeImportsJsonPathField.addBrowseFolderListener("Select theme-imports.json",
                "Select the project-relative theme-imports.json file.",
                project, // Project context for the file chooser
                FileChooserDescriptorFactory.createSingleFileDescriptor("json")); // Allow only .json files

        if (settings != null && settings.themeImportsJsonPath != null) {
            themeImportsJsonPathField.setText(settings.themeImportsJsonPath);
        } else {
            // Default to empty if settings are null or path is null
            themeImportsJsonPathField.setText("");
        }

        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Path to theme-imports.json (relative to project root):"), themeImportsJsonPathField, 1, false)
                .addComponentFillVertically(new JPanel(), 0) // Spacer
                .getPanel();
        return myMainPanel;
    }

    /**
     * Checks if the settings in the UI have been modified compared to the stored settings.
     * @return {@code true} if the path in the text field differs from the persisted path, {@code false} otherwise.
     */
    @Override
    public boolean isModified() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        String currentPathInField = themeImportsJsonPathField.getText();
        String persistedPath = (settings != null) ? settings.themeImportsJsonPath : "";
        return !Objects.equals(persistedPath, currentPathInField);
    }

    /**
     * Applies the changes from the UI to the {@link ThemeJsonSettingsState}.
     * If the path to `theme-imports.json` has changed, this method also:
     * 1. Triggers a refresh of the themes via {@link ThemeDataManager}.
     * 2. Clears the file name cache in {@link ThemeRefreshTrigger} to ensure that
     *    the VFS listener (configured by {@link com.example.themejsoncompletion.ThemeJsonStartupActivity})
     *    starts monitoring based on the new `theme-imports.json` file.
     */
    @Override
    public void apply() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        if (settings == null) {
            LOG.warn("Cannot apply settings: ThemeJsonSettingsState is null for project " + project.getName());
            return;
        }

        String newPath = themeImportsJsonPathField.getText();
        String oldPath = settings.themeImportsJsonPath;

        if (!Objects.equals(oldPath, newPath)) {
            LOG.info("themeImportsJsonPath changed for project " + project.getName() + " from '" + oldPath + "' to '" + newPath + "'. Applying changes.");
            settings.themeImportsJsonPath = newPath;

            // Trigger refresh of theme data
            ThemeDataManager dataManager = ThemeDataManager.getInstance(project);
            if (dataManager != null) {
                LOG.debug("Requesting ThemeDataManager to refresh themes due to settings change.");
                dataManager.refreshThemes();
            } else {
                LOG.warn("ThemeDataManager not available to trigger refresh after settings change for project " + project.getName());
            }

            // Clear the cache in ThemeRefreshTrigger so it re-evaluates which files to watch.
            // This is crucial for the VFS listener in ThemeJsonStartupActivity to pick up changes
            // related to the new theme-imports.json path or its content.
            LOG.debug("Clearing ThemeRefreshTrigger cache due to settings change.");
            ThemeRefreshTrigger.clearCache(project);
            // Consider if a more direct re-initialization of the VFS listener is needed,
            // e.g., via a project message bus event, if simply clearing the cache isn't sufficient
            // for all scenarios (e.g., if the listener itself caches the file names).
            // For now, clearing the trigger's cache should make getThemeFileNames re-read.
        } else {
            LOG.debug("themeImportsJsonPath not modified for project " + project.getName() + ". No changes to apply.");
        }
    }

    /**
     * Resets the UI fields to reflect the currently persisted settings in {@link ThemeJsonSettingsState}.
     */
    @Override
    public void reset() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        if (settings != null) {
            themeImportsJsonPathField.setText(settings.themeImportsJsonPath);
            LOG.debug("Settings UI reset to persisted state for project " + project.getName());
        } else {
            themeImportsJsonPathField.setText(""); // Default to empty if settings somehow null
            LOG.warn("Cannot reset settings UI: ThemeJsonSettingsState is null for project " + project.getName());
        }
    }

    /**
     * Disposes of the UI resources created by this configurable.
     * Called when the settings dialog is closed.
     */
    @Override
    public void disposeUIResources() {
        myMainPanel = null;
        themeImportsJsonPathField = null;
        LOG.debug("Disposed UI resources for ThemeJsonSettingsConfigurable for project " + project.getName());
    }
}
