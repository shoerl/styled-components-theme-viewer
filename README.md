# MUI Theme & JSON Autocompletion Plugin

## Overview

The **MUI Theme & JSON Autocompletion** plugin is an IntelliJ Platform plugin designed to enhance the development experience when working with:
1.  **MUI (Material-UI) themes** defined in JavaScript or TypeScript files.
2.  Custom UI **theme JSON files** for IntelliJ-based IDEs.

It provides intelligent code completion, documentation on hover, and inlay hints, making it easier and faster to work with your theme properties.

## Features

*   **MUI Theme Support (JS/TS)**:
    *   **Smart Code Completion**: Offers context-aware suggestions for theme keys (e.g., `theme.palette.primary.main`) within your JavaScript/TypeScript files, particularly in styled-components template literals or when accessing theme objects.
    *   **Documentation on Hover**: Shows documentation for MUI theme properties.
    *   **Inlay Hints**: Displays resolved theme values (e.g., color previews) directly in your code.
    *   **Configurable Theme Path**: Allows you to specify the project-relative path to your main MUI theme file (e.g., `src/theme.ts`) via plugin settings.
*   **Generic JSON Theme Support**:
    *   **Smart Code Completion**: Offers context-aware suggestions for theme keys and values within your `*.theme.json` files.
    *   **`theme-imports.json` Support**: Understands the structure of `theme-imports.json` to load multiple theme files, configurable via plugin settings or by using a bundled version.
*   **Dynamic Data Loading**: Loads theme data (both MUI and JSON) and provides completions based on the actual properties defined in your theme files.
*   **Automatic Refresh**: Monitors your theme files (JSON and the configured MUI file) for changes and automatically reloads the theme data.

## Getting Started

### Prerequisites

*   An IntelliJ-based IDE (e.g., IntelliJ IDEA, WebStorm, Android Studio) that is compatible with the plugin's target version.
*   A project using MUI with a recognized theme file (e.g., `theme.ts`, `theme.js`) and/or IntelliJ JSON theme files.
*   Dependencies declared in `plugin.xml` (Platform, JavaScript, NodeJS) should be available in your IDE.

### Installation

Currently, this plugin is intended to be built from source.

1.  **Clone the repository:**
    ```bash
    git clone <repository-url>
    cd <repository-directory>
    ```
2.  **Build the plugin:**
    Use the Gradle wrapper to build the plugin:
    ```bash
    ./gradlew buildPlugin
    ```
    This will produce a `.zip` file in the `build/distributions` directory.
3.  **Install in your IDE:**
    *   Go to `Settings/Preferences` > `Plugins`.
    *   Click on the gear icon ⚙️ and select `Install Plugin from Disk...`.
    *   Navigate to the `build/distributions` directory and select the generated `.zip` file.
    *   Restart the IDE when prompted.

### Configuration

After installation, you may need to configure the plugin depending on your project setup:

1.  Go to `Settings/Preferences` > `Tools` > `MUI Theme & JSON Autocompletion`.
2.  **For MUI Theme Support**:
    *   Set the **"Path to MUI theme file (JS/TS, relative to project root)"** field to point to your main MUI theme definition file (e.g., `src/myTheme.js`, `app/styles/theme.ts`). The default is `src/theme.ts`.
3.  **For Generic JSON Theme Support (Optional)**:
    *   If your project uses a custom `theme-imports.json` at a non-standard location, set the **"Path to theme-imports.json"** field. If left blank, the plugin attempts to use a bundled `theme-imports.json` and corresponding theme files from its resources, or a `theme-imports.json` located at the root of your project if the path setting is empty but a file exists there.

Changes to these settings will trigger a refresh of the theme data.

### Usage

*   **MUI Theme**: Once configured, the plugin will automatically provide completion, hover documentation, and inlay hints when you are editing JavaScript or TypeScript files and accessing properties of your MUI theme object (e.g., `theme.palette.primary.main`, `props.theme.shape.borderRadius` within styled components).
*   **JSON Themes**: The plugin will provide completion suggestions when you are editing JSON files recognized as IntelliJ theme files (e.g., files named `*.theme.json` referenced in `theme-imports.json`).

## How it Works

The plugin leverages several key components:

*   **`ThemeDataManager`**: A project service responsible for loading, parsing, caching, and refreshing theme data from both the configured MUI theme file (JS/TS) and JSON theme files (via `theme-imports.json`).
*   **`ThemeJsonCompletionContributor`**: Provides code completion logic for JavaScript, TypeScript (for MUI themes), and JSON files. It queries `ThemeDataManager` for available theme properties.
*   **`MuiThemeDocumentationProvider` & `MuiThemeInlayHintsProvider`**: Enhance the editing experience for MUI themes by providing hover information and inline value previews.
*   **`ThemeJsonStartupActivity`**: Sets up file listeners when a project is opened. These listeners monitor your theme files for changes and trigger `ThemeDataManager` to refresh its data, ensuring completions are always up-to-date.
*   **`ThemeJsonSettingsState` & `ThemeJsonSettingsConfigurable`**: Manage the plugin's settings (like the MUI theme file path) and provide the UI for configuring them in the IDE's settings dialog.

## Troubleshooting

*   **No completions for MUI theme?**
    *   Ensure the "Path to MUI theme file" in the plugin settings (`Settings/Preferences` > `Tools` > `MUI Theme & JSON Autocompletion`) correctly points to your project's MUI theme file.
    *   Check the IntelliJ IDEA event log (`Help` > `Show Log in ...`) for any error messages from "ThemeDataManager" or "ThemeJsonCompletionContributor" which might indicate issues parsing your theme file.
    *   The plugin's ability to parse JS/TS theme files has limitations. It works best with theme objects defined as plain object literals. Complex setups involving many dynamic imports, function calls to generate parts of the theme, or heavy use of variables defined elsewhere might not be fully resolved.
*   **Completions are stale?**
    *   The plugin automatically refreshes on file changes. If you suspect data is stale, you can try manually triggering a refresh by modifying and saving one of your theme files or by toggling a setting in the plugin's configuration panel.

## Contributing

Please see `DEVELOP.md` for details on how to contribute to this project, set up your development environment, and understand the codebase.

## License

(Specify License Here - e.g., Apache 2.0, MIT) - *You'll need to decide on a license for your project.*
