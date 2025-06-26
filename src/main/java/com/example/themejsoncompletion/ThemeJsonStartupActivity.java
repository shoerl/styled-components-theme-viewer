package com.example.themejsoncompletion;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A {@link StartupActivity} that runs when a project is opened.
 * This activity is responsible for setting up a {@link BulkFileListener} to monitor
 * changes in theme JSON files. If a relevant theme file is changed, it triggers
 * a refresh of the theme data via {@link ThemeRefreshTrigger}.
 *
 * It implements {@link Disposable} and registers itself with the project
 * to ensure that the VFS listener is properly cleaned up when the project is closed,
 * preventing memory leaks.
 */
public class ThemeJsonStartupActivity implements StartupActivity, Disposable {

    private static final Logger LOG = Logger.getInstance(ThemeJsonStartupActivity.class);

    /**
     * Called when the project is disposed or the plugin is unloaded.
     * This method handles the cleanup of resources, specifically the VFS listener
     * connection to the message bus.
     */
    @Override
    public void dispose() {
        // The message bus connection established in runActivity using project.getMessageBus().connect(this)
        // will be automatically disposed because 'this' (ThemeJsonStartupActivity instance)
        // is registered as a disposable with the project. No explicit disconnect is needed here
        // if Disposer.register(project, this) was called.
        LOG.info("Disposing ThemeJsonStartupActivity and its associated message bus connection.");
    }

    /**
     * The main entry point for the startup activity.
     * It registers this activity as a {@link Disposable} with the project,
     * then sets up a VFS listener to detect changes in theme files.
     *
     * @param project The project that has just been opened.
     */
    @Override
    public void runActivity(@NotNull Project project) {
        // Register this activity as a disposable with the project.
        // This ensures its dispose() method is called when the project closes,
        // which in turn ensures the message bus connection is cleaned up.
        Disposer.register(project, this);

        // Connect to the message bus using 'this' as the disposable parent.
        // The connection will be automatically disposed when 'this' (the activity) is disposed.
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                // Pass project to getThemeFileNames to respect project-specific settings
                // and to allow ThemeRefreshTrigger to potentially use project-specific caching.
                List<String> themeFileNamesToWatch = ThemeRefreshTrigger.getThemeFileNames(project);

                if (themeFileNamesToWatch.isEmpty()) {
                    // LOG.debug("No theme files configured or found to watch for project: " + project.getName());
                    return;
                }

                boolean needsRefresh = false;
                for (VFileEvent event : events) {
                    String path = event.getPath();
                    if (path != null) {
                        for (String themeFileName : themeFileNamesToWatch) {
                            // Simple check: does the path end with one of the configured theme file names?
                            // This is a basic check and might lead to false positives if theme file names
                            // are very generic (e.g., "index.json") and appear in many non-theme contexts.
                            // A more robust check might involve verifying if the path is under a recognized
                            // theme directory or matches a more specific pattern from settings.
                            if (path.endsWith(themeFileName)) {
                                LOG.info("Detected change in a potential theme file: " + path + " for project: " + project.getName());
                                needsRefresh = true;
                                break;
                            }
                        }
                    }
                    if (needsRefresh) {
                        break;
                    }
                }

                if (needsRefresh) {
                    LOG.info("A theme JSON file relevant to project " + project.getName() + " changed. Triggering theme data refresh.");
                    ThemeRefreshTrigger.refreshThemes(project);
                }
            }
        });
        LOG.info("Registered VFS listener for theme file changes for project: " + project.getName());
    }
}
