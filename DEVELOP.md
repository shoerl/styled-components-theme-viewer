# Developer Guide: MUI Theme & JSON Autocompletion Plugin

## 1. Introduction

This guide is intended for developers looking to contribute to the "MUI Theme & JSON Autocompletion" IntelliJ plugin. This plugin enhances the development experience for projects using Material-UI (MUI) by providing autocompletion, hover documentation, and inlay hints for MUI theme objects within JavaScript and TypeScript files. It also supports autocompletion for standard IntelliJ `*.theme.json` files.

Understanding the IntelliJ Platform SDK and plugin development basics will be helpful.

## 2. Development Setup

### 2.1. System Requirements

*   **Java Development Kit (JDK)**: Version 11 or later (as typically required by modern IntelliJ Platform plugin development). Check `build.gradle.kts` for the specific `jvmToolchain` version.
*   **IntelliJ IDEA**: IntelliJ IDEA Community Edition or Ultimate Edition, suitable for plugin development. The version should be compatible with the target IntelliJ Platform version specified in `build.gradle.kts` (e.g., `pluginUntilBuild`, `pluginSinceBuild`).
*   **Gradle**: The project uses Gradle for building. The Gradle wrapper (`gradlew`) is included in the repository, so no separate Gradle installation is needed.

### 2.2. Cloning the Repository

```bash
git clone <repository-url>
cd <repository-directory>
```

### 2.3. Importing the Project into IntelliJ IDEA

1.  Open IntelliJ IDEA.
2.  Select "Open" from the welcome screen or "File" > "Open..." from the menu.
3.  Navigate to the cloned repository directory and select it.
4.  IntelliJ IDEA should automatically recognize it as a Gradle project. If prompted, confirm to "Import Gradle Project" or "Trust Project".
5.  Allow IntelliJ IDEA to download dependencies and set up the project. This might take a few minutes.

### 2.4. Building the Plugin

To build the plugin, use the Gradle wrapper task `buildPlugin`:

```bash
./gradlew buildPlugin
```

This command will compile the code, run any tests (if configured), and package the plugin into a `.zip` file located in the `build/distributions/` directory.

### 2.5. Running the Plugin in a Development Instance

The most common way to test the plugin during development is to run it in a sandboxed instance of IntelliJ IDEA:

1.  Ensure the project has been imported correctly into IntelliJ IDEA.
2.  Look for a pre-configured run configuration named **"Run IDE with Plugin"** or similar (often found in `.run/` directory, e.g., `Run IDE with Plugin.run.xml`).
3.  If it exists, select it from the run configurations dropdown and click the "Run" (play) button.
4.  If it doesn't exist, you can create one:
    *   Go to "Run" > "Edit Configurations...".
    *   Click the "+" button and select "Plugin".
    *   Give it a name (e.g., "Run Plugin").
    *   The "Use classpath of module" should be automatically set to your plugin module.
    *   Click "OK".
5.  This will launch a new instance of IntelliJ IDEA with your plugin installed and enabled, allowing you to test its functionality in a separate project.

## 3. Codebase Overview

### 3.1. Key Directories

*   `src/main/java/com/example/themejsoncompletion/`: Contains the core Java/Kotlin source code for the plugin.
    *   `settings/`: Classes related to plugin settings (`ThemeJsonSettingsState`, `ThemeJsonSettingsConfigurable`).
*   `src/main/resources/`: Contains plugin resources.
    *   `META-INF/plugin.xml`: The main descriptor file for the plugin.
    *   `theme-imports.json`, `*.theme.json`: Default/fallback JSON theme files bundled with the plugin.
*   `build.gradle.kts`: Gradle build script defining project dependencies, IntelliJ Platform version, and build tasks.
*   `gradle/`: Gradle wrapper files.

### 3.2. Core Classes and Their Responsibilities

*   **`ThemeDataManager` (`ProjectService`)**:
    *   Manages the lifecycle of theme data (loading, parsing, caching, refreshing).
    *   Handles both MUI themes (from JS/TS files) and generic JSON themes (from `theme-imports.json` and `*.theme.json` files).
    *   For MUI themes, it uses IntelliJ's PSI (Program Structure Interface) to parse JavaScript/TypeScript files and extract theme object literals.
    *   Provides methods like `getMuiTheme()` and `getAllJsonThemes()` for other components to access theme data.

*   **`ThemeJsonCompletionContributor` (`completion.contributor`)**:
    *   Provides code completion suggestions.
    *   Registered for JavaScript and TypeScript to offer MUI theme completions (e.g., `theme.palette.primary.main`).
    *   Also handles completions for standard JSON theme files.
    *   Uses `ThemeDataManager` to get the current theme data.

*   **`MuiThemeCompletionProvider`**:
    *   A helper class used by `ThemeJsonCompletionContributor` specifically for generating completion items from the parsed MUI theme structure.

*   **`ThemeJsonCompletionProvider`**:
    *   A helper class used by `ThemeJsonCompletionContributor` specifically for generating completion items from standard JSON theme structures.

*   **`MuiPsiUtil`**:
    *   Contains utility methods for working with the PSI tree, specifically for identifying MUI theme contexts in PSI trees (especially within styled-components).

*   **`MuiThemeValueResolver`**:
    *   A utility class used by inlay hint and documentation providers to traverse the parsed MUI theme (a `Map<String, Object>`) using a list of path segments (e.g., `["palette", "primary", "main"]`) and retrieve the final value.

*   **`MuiThemeDocumentationProvider` (`documentationProvider`)**:
    *   Provides documentation that appears on hover for MUI theme properties in JS/TS files.

*   **`MuiThemeInlayHintsProvider` (`inlayHintsProvider`)**:
    *   Provides inlay hints (e.g., color previews) for MUI theme properties in JS/TS files.

*   **`ThemeJsonStartupActivity` (`postStartupActivity`)**:
    *   Runs when a project is opened.
    *   Sets up `BulkFileListener` to monitor changes in configured theme files (both the MUI theme file and JSON theme files).
    *   Triggers `ThemeDataManager.refreshThemes()` when relevant files are modified.

*   **`ThemeRefreshTrigger`**:
    *   Helper class used by `ThemeJsonStartupActivity` and settings changes to initiate a theme data refresh in `ThemeDataManager`. Manages a cache of theme file names to watch.

*   **`ThemeJsonSettingsState` (`projectService`)**:
    *   A persistent state component that stores plugin settings on a per-project basis (in `.idea/themeJsonCompletionSettings.xml`).
    *   Holds paths to `theme-imports.json` and the MUI theme JS/TS file.

*   **`ThemeJsonSettingsConfigurable` (`projectConfigurable`)**:
    *   Provides the UI for plugin settings under `Settings/Preferences > Tools > MUI Theme & JSON Autocompletion`.
    *   Allows users to configure the paths stored in `ThemeJsonSettingsState`.

### 3.3. Plugin Configuration (`plugin.xml`)

Located in `src/main/resources/META-INF/plugin.xml`, this file defines:
*   Plugin ID, name, version, vendor, description.
*   Dependencies (e.g., `com.intellij.modules.platform`, `JavaScript`, `NodeJS`).
*   **Extension Points**: How the plugin integrates with IntelliJ IDEA. Key extensions used include:
    *   `com.intellij.completion.contributor`: For code completion.
    *   `com.intellij.inlayHintsProvider`: For inlay hints.
    *   `com.intellij.documentationProvider`: For hover documentation.
    *   `com.intellij.postStartupActivity`: For actions on project open.
    *   `com.intellij.projectService`: For project-level services like settings state.
    *   `com.intellij.projectConfigurable`: For the settings UI.

### 3.4. Theme Data Sources

*   **MUI Theme File**: A user-configured JavaScript or TypeScript file (e.g., `my-project/src/theme.ts`) that exports the MUI theme object. Parsed by `ThemeDataManager` using PSI.
*   **`theme-imports.json`**: A JSON file that maps aliases to theme file paths (e.g., `{ "darcula": "themes/darcula.theme.json" }`).
    *   Can be project-specific (path configured in settings).
    *   If not configured or found in project, a fallback version from `src/main/resources/theme-imports.json` is used.
*   **`*.theme.json` files**: Standard IntelliJ JSON theme files, typically referenced by `theme-imports.json`.

## 4. Working with Themes

### 4.1. JSON Theme Processing

1.  `ThemeDataManager` attempts to load `theme-imports.json` first from the path specified in `ThemeJsonSettingsState`, then from plugin resources as a fallback.
2.  It parses `theme-imports.json` to get a map of theme aliases to relative file paths.
3.  For each entry, it attempts to load the corresponding `*.theme.json` file (again, trying project-relative paths first if `theme-imports.json` was loaded from the project, then plugin resources).
4.  Parsed JSON themes are stored in a map in `ThemeDataManager`.

### 4.2. MUI Theme Processing (JS/TS)

1.  `ThemeDataManager` gets the path to the MUI theme file from `ThemeJsonSettingsState`.
2.  It uses `LocalFileSystem` and `PsiManager` to get a `PsiFile` (specifically a `JSFile`) for the configured path.
3.  It then traverses the PSI tree of this file (using helper methods in `ThemeDataManager` itself, like `findThemeObjectLiteral`) to locate the main theme object literal (e.g., one assigned to `export const theme = ...` or `export default createTheme({...})`).
4.  The `JSObjectLiteralExpression` representing the theme is then converted into a nested `Map<String, Object>` structure. This conversion (`convertJsObjectLiteralToMap`) handles basic JavaScript types (strings, numbers, booleans, arrays, nested objects).
    *   **Note**: This parsing has limitations. It may not fully resolve theme properties defined by complex JavaScript (e.g., function calls, imported variables from other files, spread operators that are not directly resolvable at parse time). Placeholders might be stored for such complex values.
5.  The parsed MUI theme map is stored in `ThemeDataManager`.

### 4.3. Theme Refreshing

*   `ThemeJsonStartupActivity` registers a `BulkFileListener` that monitors:
    *   The configured MUI theme file.
    *   JSON theme files listed in `theme-imports.json` (via `ThemeRefreshTrigger.getThemeFileNames()`).
*   If any of these files change on disk, the listener calls `ThemeRefreshTrigger.refreshThemes(project)`, which in turn calls `ThemeDataManager.refreshAllThemesData()`.
*   Changes in plugin settings via `ThemeJsonSettingsConfigurable` also trigger a theme refresh.

## 5. Testing

(Information about existing tests, how to run them, or how to write new ones would go here. If no tests currently exist, this section could briefly state that and suggest areas for future test development, e.g., unit tests for theme parsing logic, or light integration tests for completion.)

Currently, automated tests are not implemented. Key areas for future tests include:
*   Unit testing the `ThemeDataManager`'s logic for parsing various structures in JS/TS MUI theme files.
*   Testing the `convertJsObjectLiteralToMap` and `convertJsArrayLiteralToList` methods with diverse inputs.
*   Unit testing `MuiPsiUtil` context detection with various code snippets.
*   Unit testing `MuiThemeValueResolver` with different theme structures and paths.
*   Integration tests for completion, inlay hints, and hover documentation using `com.intellij.testFramework.fixtures.BasePlatformTestCase`.

During the implementation of styled-components features, manual testing was guided by a sample MUI theme object and a JavaScript file containing various styled-component definitions (like those described in the "Testing and Refinement" plan step for that feature). This involved checking autocomplete suggestions, inlay hint presence and correctness, and hover documentation content within a development instance of the IDE.

## 6. Contribution Guidelines

*   **Branching**: Create a new branch for each feature or bug fix (e.g., `feature/new-completion-context` or `fix/mui-parsing-error`).
*   **Commits**: Write clear and concise commit messages.
*   **Code Style**: Follow standard Java/Kotlin coding conventions. If an `.editorconfig` file is present, adhere to its rules.
*   **Pull Requests**:
    *   Ensure your code builds successfully (`./gradlew buildPlugin`).
    *   If applicable, update `README.md` or other documentation.
    *   Explain the purpose and changes of your PR clearly.
    *   Be prepared to discuss and iterate on your submission.

## 7. Logging

The plugin uses `com.intellij.openapi.diagnostic.Logger` for logging.
*   `Logger.getInstance(MyClass.class)`

To view plugin logs:
1.  Go to `Help` > `Show Log in ...` (e.g., Finder, Explorer). This will open the directory containing `idea.log`.

To enable **DEBUG** level logging for specific classes (useful during development):
1.  Go to `Help` > `Diagnostic Tools` > `Debug Log Settings...`.
2.  Add new lines with the fully qualified class name or package name, prefixed with `#`. For example:
    ```
    #com.example.themejsoncompletion.ThemeDataManager
    #com.example.themejsoncompletion.ThemeJsonCompletionContributor
    ```
This will cause DEBUG (and higher level) log statements from these classes to appear in `idea.log`. Remember to remove these settings after debugging.

---

This document should provide a good starting point for new developers. It can be expanded as the plugin evolves.
