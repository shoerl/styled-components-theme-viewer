package com.example.themejsoncompletion;

import com.example.themejsoncompletion.settings.ThemeJsonSettingsState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;
import java.util.List;

/**
 * A {@link StartupActivity} that runs when a project is opened.
 * This activity sets up a {@link BulkFileListener} to monitor changes in:
 * 1. JSON theme files (names derived from `theme-imports.json` via {@link ThemeRefreshTrigger}).
 * 2. The configured MUI theme file (JS/TS) from {@link ThemeJsonSettingsState}.
 * If a relevant file changes, it triggers a refresh of theme data.
 */
public class ThemeJsonStartupActivity implements StartupActivity, Disposable {

    private static final Logger LOG = Logger.getInstance(ThemeJsonStartupActivity.class);

    @Override
    public void dispose() {
        LOG.info("Disposing ThemeJsonStartupActivity and its VFS listener connection.");
    }

    @Override
    public void runActivity(@NotNull Project project) {
        Disposer.register(project, this);

        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                // Get JSON theme file names to watch (e.g., "dark-theme.json")
                List<String> jsonThemeFileNamesToWatch = ThemeRefreshTrigger.getThemeFileNames(project);

                // Get the configured MUI theme file path (project-relative)
                ThemeJsonSettingsState settings = ThemeJsonSettingsState.getInstance(project);
                String muiThemeRelativePath = (settings != null) ? settings.muiThemeFilePath : null;
                String muiThemeAbsolutePath = null;

                if (muiThemeRelativePath != null && !muiThemeRelativePath.trim().isEmpty() && project.getBasePath() != null) {
                    try {
                        muiThemeAbsolutePath = Paths.get(project.getBasePath()).resolve(muiThemeRelativePath).normalize().toString();
                    } catch (Exception e) {
                        LOG.warn("Could not resolve MUI theme absolute path from relative: " + muiThemeRelativePath, e);
                    }
                }

                if (jsonThemeFileNamesToWatch.isEmpty() && muiThemeAbsolutePath == null) {
                    // LOG.debug("No theme files (JSON or MUI) configured to watch for project: " + project.getName());
                    return;
                }

                boolean needsRefresh = false;
                for (VFileEvent event : events) {
                    String eventPath = event.getPath();
                    if (eventPath == null) continue;

                    // Check if it's the MUI theme file
                    if (muiThemeAbsolutePath != null && eventPath.equals(muiThemeAbsolutePath)) {
                        LOG.info("Detected change in configured MUI theme file: " + eventPath + " for project: " + project.getName());
                        needsRefresh = true;
                        break;
                    }

                    // Check if it's one of the JSON theme files
                    for (String jsonThemeFileName : jsonThemeFileNamesToWatch) {
                        if (eventPath.endsWith("/" + jsonThemeFileName) || eventPath.endsWith("\\" + jsonThemeFileName) || eventPath.equals(jsonThemeFileName) ) {
                             // Check endsWith for full name, possibly with directory separator
                            LOG.info("Detected change in a potential JSON theme file: " + eventPath + " (watching for name: " + jsonThemeFileName + ") for project: " + project.getName());
                            needsRefresh = true;
                            break;
                        }
                    }
                    if (needsRefresh) break;
                }

                if (needsRefresh) {
                    LOG.info("A relevant theme file (JSON or MUI) changed for project " + project.getName() + ". Triggering theme data refresh.");
                    // ThemeRefreshTrigger.refreshThemes calls ThemeDataManager.refreshThemes() which now handles both.
                    ThemeRefreshTrigger.refreshThemes(project);
                }
            }
        });
        LOG.info("Registered VFS listener for JSON and MUI theme file changes for project: " + project.getName());
    }
}
