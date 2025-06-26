package com.example.themejsoncompletion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.Disposable; // Added import

import java.util.List;

public class ThemeJsonStartupActivity implements StartupActivity, Disposable {

    @Override
    public void dispose() {
        // This method will be called when the project is disposed,
        // cleaning up the message bus connection.
        System.out.println("ThemeJsonStartupActivity: Disposing activity and message bus connection.");
    }

    @Override
    public void runActivity(@NotNull Project project) {
        // Register this activity as a disposable with the project.
        // This ensures its dispose() method is called when the project closes.
        Disposer.register(project, this);

        // Connect to the message bus using 'this' as the disposable parent.
        // The connection will be automatically disposed when 'this' is disposed.
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                // Pass project to getThemeFileNames to respect project-specific settings
                List<String> themeFileNames = ThemeRefreshTrigger.getThemeFileNames(project);
                if (themeFileNames.isEmpty()) {
                    // No themes configured or found to watch.
                    return;
                }

                boolean needsRefresh = false;
                for (VFileEvent event : events) {
                    String path = event.getPath();
                    if (path != null) {
                        for (String themeFileName : themeFileNames) {
                            if (path.endsWith(themeFileName)) {
                                System.out.println("ThemeJsonStartupActivity: Detected change in a theme file: " + path);
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
                    System.out.println("ThemeJsonStartupActivity: A theme JSON file changed. Triggering refresh logic...");
                    ThemeRefreshTrigger.refreshThemes(project); // Pass project if needed by the trigger
                }
            }
        });
        System.out.println("ThemeJsonStartupActivity: Registered VFS listener for theme file changes using BulkFileListener.");
        
        // Ensure the disposable is disposed when the project is disposed
        // This is important to prevent memory leaks.
        // However, StartupActivity itself doesn't have a natural dispose point tied to the project lifecycle
        // other than the project closing. The connection made via project.getMessageBus().connect(disposable)
        // should be automatically disposed when the project is disposed if `disposable` is registered
        // with the project. A common practice is to use a project-level service for such disposables.
        // For a StartupActivity, if it needs to clean up when the plugin unloads or project closes,
        // it might need to register the disposable with a project service or the Application.
        // For now, we'll assume connect(disposable) handles this for project-specific listeners.
        // If this were a project service, we'd implement Disposable and Disposer.register(project, this)
        // and then connect(this) to the message bus.
    }
}
