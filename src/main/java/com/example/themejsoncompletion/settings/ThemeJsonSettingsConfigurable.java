package com.example.themejsoncompletion.settings;

import com.example.themejsoncompletion.ThemeDataManager;
import com.example.themejsoncompletion.ThemeRefreshTrigger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
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
 * Controller for the settings UI of the MUI Theme & JSON Autocompletion plugin.
 * Implements {@link Configurable} for the IntelliJ IDEA settings dialog.
 * Allows users to specify paths for:
 * - `theme-imports.json` (for generic JSON themes).
 * - MUI theme file (JS/TS for MUI specific features).
 * Changes trigger theme data refresh and cache clearing.
 */
public class ThemeJsonSettingsConfigurable implements Configurable {

    private static final Logger LOG = Logger.getInstance(ThemeJsonSettingsConfigurable.class);
    private final Project project;
    private JPanel myMainPanel;
    private TextFieldWithBrowseButton themeImportsJsonPathField;
    private TextFieldWithBrowseButton muiThemeFilePathField; // New field for MUI theme path

    public ThemeJsonSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        // Consistent with plugin.xml
        return "MUI Theme & JSON Autocompletion";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);

        // Field for theme-imports.json
        themeImportsJsonPathField = new TextFieldWithBrowseButton();
        themeImportsJsonPathField.addBrowseFolderListener("Select theme-imports.json",
                "Select the project-relative theme-imports.json file.",
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor("json"));
        themeImportsJsonPathField.setText(settings != null ? settings.themeImportsJsonPath : "");

        // New field for muiThemeFilePath
        muiThemeFilePathField = new TextFieldWithBrowseButton();
        FileChooserDescriptor muiThemeFileChooserDescriptor = new FileChooserDescriptor(
                true, false, false, false, false, false)
                .withFileFilter(vf -> vf.getName().endsWith(".js") || vf.getName().endsWith(".ts") || vf.getName().endsWith(".jsx") || vf.getName().endsWith(".tsx"));
        muiThemeFileChooserDescriptor.setTitle("Select MUI Theme File (JS/TS)");
        muiThemeFileChooserDescriptor.setDescription("Select your project's main MUI theme definition file (e.g., theme.ts, muiTheme.js).");

        muiThemeFilePathField.addBrowseFolderListener(null, // No text for title in text field
                "Select your project's main MUI theme definition file (e.g., theme.ts, muiTheme.js).",
                project,
                muiThemeFileChooserDescriptor);
        muiThemeFilePathField.setText(settings != null ? settings.muiThemeFilePath : "src/theme.ts"); // Default from state

        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Path to theme-imports.json (for generic JSON themes, relative to project root):"), themeImportsJsonPathField, 1, false)
                .addLabeledComponent(new JBLabel("Path to MUI theme file (JS/TS, relative to project root):"), muiThemeFilePathField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        return myMainPanel;
    }

    @Override
    public boolean isModified() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        if (settings == null) return false; // Should not happen if component is created

        boolean themeImportsModified = !Objects.equals(themeImportsJsonPathField.getText(), settings.themeImportsJsonPath);
        boolean muiThemeFileModified = !Objects.equals(muiThemeFilePathField.getText(), settings.muiThemeFilePath);

        return themeImportsModified || muiThemeFileModified;
    }

    @Override
    public void apply() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        if (settings == null) {
            LOG.warn("Cannot apply settings: ThemeJsonSettingsState is null for project " + project.getName());
            return;
        }

        String newThemeImportsPath = themeImportsJsonPathField.getText();
        String oldThemeImportsPath = settings.themeImportsJsonPath;
        boolean themeImportsChanged = !Objects.equals(oldThemeImportsPath, newThemeImportsPath);

        String newMuiThemePath = muiThemeFilePathField.getText();
        String oldMuiThemePath = settings.muiThemeFilePath;
        boolean muiThemePathChanged = !Objects.equals(oldMuiThemePath, newMuiThemePath);

        if (themeImportsChanged || muiThemePathChanged) {
            if (themeImportsChanged) {
                LOG.info("themeImportsJsonPath changed for project " + project.getName() + " from '" + oldThemeImportsPath + "' to '" + newThemeImportsPath + "'.");
                settings.themeImportsJsonPath = newThemeImportsPath;
            }
            if (muiThemePathChanged) {
                LOG.info("muiThemeFilePath changed for project " + project.getName() + " from '" + oldMuiThemePath + "' to '" + newMuiThemePath + "'.");
                settings.muiThemeFilePath = newMuiThemePath;
            }

            LOG.info("Applying settings changes and refreshing themes.");
            ThemeDataManager dataManager = ThemeDataManager.getInstance(project);
            if (dataManager != null) {
                dataManager.refreshThemes();
            } else {
                LOG.warn("ThemeDataManager not available to trigger refresh after settings change for project " + project.getName());
            }
            ThemeRefreshTrigger.clearCache(project); // Clears cache for file watcher
        } else {
            LOG.debug("Settings not modified for project " + project.getName() + ". No changes to apply.");
        }
    }

    @Override
    public void reset() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        if (settings != null) {
            themeImportsJsonPathField.setText(settings.themeImportsJsonPath);
            muiThemeFilePathField.setText(settings.muiThemeFilePath);
            LOG.debug("Settings UI reset to persisted state for project " + project.getName());
        } else {
            themeImportsJsonPathField.setText("");
            muiThemeFilePathField.setText("src/theme.ts"); // Default from state
            LOG.warn("Cannot reset settings UI: ThemeJsonSettingsState is null for project " + project.getName());
        }
    }

    @Override
    public void disposeUIResources() {
        myMainPanel = null;
        themeImportsJsonPathField = null;
        muiThemeFilePathField = null; // Dispose new field
        LOG.debug("Disposed UI resources for ThemeJsonSettingsConfigurable for project " + project.getName());
    }
}
