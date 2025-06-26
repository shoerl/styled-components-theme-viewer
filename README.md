# Theme JSON Completion Plugin

## Overview

The Theme JSON Completion plugin is an IntelliJ Platform plugin designed to enhance the development experience when working with custom UI theme JSON files. It provides intelligent code completion for theme properties, making it easier and faster to define and customize IntelliJ-based IDE themes.

## Features

*   **Smart Code Completion**: Offers context-aware suggestions for theme keys and values within your JSON theme files.
*   **Support for Standard Theme Structure**: Understands common structures used in IntelliJ theme files like `*.theme.json`.
*   **Dynamic Data Loading**: Loads theme data and provides completions based on available theme properties.
*   **Configurable Settings**: Allows users to customize certain aspects of the plugin's behavior (details to be confirmed based on `ThemeJsonSettingsConfigurable`).

## Getting Started

### Prerequisites

*   An IntelliJ-based IDE (e.g., IntelliJ IDEA, WebStorm, Android Studio) that is compatible with the plugin's target version (currently built for IntelliJ IDEA 2025.1, build `251` and later).

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

### Usage

Once installed, the plugin will automatically provide completion suggestions when you are editing JSON files that are recognized as IntelliJ theme files (e.g., files named `*.theme.json` or opened in a context where theming is active).

(Further details on specific usage patterns or configuration will be added as the plugin's functionality is more deeply understood, particularly regarding the settings in `ThemeJsonSettingsConfigurable`.)

## Contributing

Please see `DEVELOP.md` for details on how to contribute to this project, set up your development environment, and understand the codebase.

## License

(Specify License Here - e.g., Apache 2.0, MIT) - *You'll need to decide on a license.*
