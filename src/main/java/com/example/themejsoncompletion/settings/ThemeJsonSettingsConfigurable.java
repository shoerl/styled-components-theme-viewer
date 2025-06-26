package com.example.themejsoncompletion.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects; // Added import

public class ThemeJsonSettingsConfigurable implements Configurable {

    private final Project project;
    private JPanel myMainPanel;
    private TextFieldWithBrowseButton themeImportsJsonPathField;

    public ThemeJsonSettingsConfigurable(Project project) {
        this.project = project;
    }

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "Theme JSON Autocompletion";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        themeImportsJsonPathField = new TextFieldWithBrowseButton();
        themeImportsJsonPathField.addBrowseFolderListener("Select theme-imports.json",
                "Select the theme-imports.json file for your project.",
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor("json"));

        if (settings != null && settings.themeImportsJsonPath != null) {
            themeImportsJsonPathField.setText(settings.themeImportsJsonPath);
        }

        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("Path to theme-imports.json (relative to project root):"), themeImportsJsonPathField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
        return myMainPanel;
    }

    @Override
    public boolean isModified() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        return settings != null && !settings.themeImportsJsonPath.equals(themeImportsJsonPathField.getText());
    }

    @Override
    public void apply() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        if (settings != null) {
            String newPath = themeImportsJsonPathField.getText();
            String oldPath = settings.themeImportsJsonPath;
            settings.themeImportsJsonPath = newPath;

            if (!Objects.equals(oldPath, newPath) && project != null) {
                System.out.println("ThemeJsonSettingsConfigurable: themeImportsJsonPath changed from '" + oldPath + "' to '" + newPath + "'. Triggering theme refresh.");
                ThemeDataManager dataManager = ThemeDataManager.getInstance(project);
                if (dataManager != null) {
                    dataManager.refreshThemes();
                } else {
                    System.err.println("ThemeJsonSettingsConfigurable: ThemeDataManager not available to trigger refresh.");
                }
                // Also, the file watcher might need to update its list of files.
                // ThemeJsonStartupActivity's VFS listener uses ThemeRefreshTrigger.getThemeFileNames(project)
                // which now reads the setting. The cache in getThemeFileNames might delay pickup.
                ThemeRefreshTrigger.clearCache(); // Clear cache to ensure VFS listener uses new file list sooner.
                System.out.println("ThemeJsonSettingsConfigurable: Cleared ThemeRefreshTrigger cache.");
                // A more robust solution might involve a project message bus event for VFS listener re-init.
            }
        }
    }

    @Override
    public void reset() {
        ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
        if (settings != null) {
            themeImportsJsonPathField.setText(settings.themeImportsJsonPath);
        }
    }

    @Override
    public void disposeUIResources() {
        myMainPanel = null;
        themeImportsJsonPathField = null;
    }
}
