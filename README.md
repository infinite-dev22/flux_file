# Reactive JavaFX File Manager (Java-based Views + Gradle)

A modern, reactive file manager built with Spring Boot and JavaFX using **pure Java views** (no FXML) and **Gradle** for build management. Uses Project Reactor for asynchronous file operations.

## Features

- ✅ **Reactive File Operations**: Non-blocking file I/O using Project Reactor
- ✅ **Java-based Views**: No XML/FXML - UI built entirely in Java code
- ✅ **Gradle Build System**: Modern dependency management
- ✅ **Real-time Directory Watching**: Automatically refreshes when files change
- ✅ **Search Functionality**: Recursive file search across directories
- ✅ **File Management**: Create folders, delete files/folders, rename, copy, cut, paste
- ✅ **File Opening**: Double-click to open files with system default or custom applications
- ✅ **Open With**: Choose application to open files
- ✅ **Default Applications**: Set and manage default applications per file extension
- ✅ **Terminal Integration**: Open current directory in available terminals (detects system terminals)
- ✅ **File Compression**: Create ZIP archives from selected files/folders
- ✅ **File Sharing**: System-native file sharing
- ✅ **Properties Dialog**: View detailed file/folder information
- ✅ **Context Menu**: Right-click menu with all file operations
- ✅ **Multi-selection**: Select multiple files for batch operations
- ✅ **Preferences**: Persistent user preferences for default applications
- ✅ **Clean Architecture**: Separation of concerns (View, Controller, Service, Model)
- ✅ **Spring Integration**: Dependency injection and Spring Boot configuration

## Project Structure

```
reactive-file-manager/
│
├── .github/                                    # GitHub specific files
│   ├── workflows/
│   │   ├── build.yml                          # CI/CD pipeline
│   │   ├── release.yml                        # Release automation
│   │   └── test.yml                           # Test automation
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.md
│   │   └── feature_request.md
│   └── PULL_REQUEST_TEMPLATE.md
│
├── .idea/                                      # IntelliJ IDEA project files (gitignored)
│
├── build/                                      # Build output (gitignored)
│
├── gradle/                                     # Gradle wrapper
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
│
├── docs/                                       # Documentation
│   ├── api/                                   # API documentation
│   ├── architecture/
│   │   ├── architecture.md                    # System architecture
│   │   ├── diagrams/                          # Architecture diagrams
│   │   └── decisions/                         # ADRs (Architecture Decision Records)
│   ├── user-guide/
│   │   ├── getting-started.md
│   │   ├── features.md
│   │   └── troubleshooting.md
│   ├── developer-guide/
│   │   ├── setup.md
│   │   ├── contributing.md
│   │   └── testing.md
│   └── cloud-setup-guide.md
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── example/
│   │   │           └── filemanager/
│   │   │               │
│   │   │               ├── FileManagerApplication.java      # Main entry point
│   │   │               │
│   │   │               ├── config/                          # Configuration
│   │   │               │   ├── AppConfig.java               # Application config
│   │   │               │   ├── DependencyConfig.java        # DI configuration
│   │   │               │   ├── PropertiesConfig.java        # Properties loading
│   │   │               │   └── SchedulerConfig.java         # Reactor schedulers
│   │   │               │
│   │   │               ├── core/                            # Core domain
│   │   │               │   ├── domain/                      # Domain models
│   │   │               │   │   ├── model/
│   │   │               │   │   │   ├── FileItem.java
│   │   │               │   │   │   ├── RemoteFileItem.java
│   │   │               │   │   │   ├── FileMetadata.java
│   │   │               │   │   │   ├── User.java
│   │   │               │   │   │   └── Preferences.java
│   │   │               │   │   │
│   │   │               │   │   ├── valueobject/             # Value objects
│   │   │               │   │   │   ├── FilePath.java
│   │   │               │   │   │   ├── FileSize.java
│   │   │               │   │   │   └── Timestamp.java
│   │   │               │   │   │
│   │   │               │   │   └── exception/               # Domain exceptions
│   │   │               │   │       ├── FileNotFoundException.java
│   │   │               │   │       ├── PermissionDeniedException.java
│   │   │               │   │       └── InvalidPathException.java
│   │   │               │   │
│   │   │               │   ├── port/                        # Ports (interfaces)
│   │   │               │   │   ├── input/                   # Use case interfaces
│   │   │               │   │   │   ├── FileOperationsUseCase.java
│   │   │               │   │   │   ├── SearchUseCase.java
│   │   │               │   │   │   └── CloudSyncUseCase.java
│   │   │               │   │   │
│   │   │               │   │   └── output/                  # Repository interfaces
│   │   │               │   │       ├── FileRepository.java
│   │   │               │   │       ├── PreferencesRepository.java
│   │   │               │   │       └── RemoteFileRepository.java
│   │   │               │   │
│   │   │               │   └── usecase/                     # Use case implementations
│   │   │               │       ├── file/
│   │   │               │       │   ├── ListFilesUseCase.java
│   │   │               │       │   ├── CreateFileUseCase.java
│   │   │               │       │   ├── DeleteFileUseCase.java
│   │   │               │       │   ├── CopyFileUseCase.java
│   │   │               │       │   ├── MoveFileUseCase.java
│   │   │               │       │   └── CompressFilesUseCase.java
│   │   │               │       │
│   │   │               │       ├── search/
│   │   │               │       │   └── SearchFilesUseCase.java
│   │   │               │       │
│   │   │               │       └── cloud/
│   │   │               │           ├── UploadToCloudUseCase.java
│   │   │               │           ├── DownloadFromCloudUseCase.java
│   │   │               │           └── SyncWithCloudUseCase.java
│   │   │               │
│   │   │               ├── infrastructure/                  # Infrastructure layer
│   │   │               │   ├── adapter/
│   │   │               │   │   ├── filesystem/              # Local filesystem
│   │   │               │   │   │   ├── LocalFileSystemAdapter.java
│   │   │               │   │   │   └── FileWatcherAdapter.java
│   │   │               │   │   │
│   │   │               │   │   ├── remote/                  # Remote systems
│   │   │               │   │   │   ├── RemoteFileSystemAdapter.java
│   │   │               │   │   │   ├── googledrive/
│   │   │               │   │   │   │   ├── GoogleDriveAdapter.java
│   │   │               │   │   │   │   └── GoogleDriveAuthHandler.java
│   │   │               │   │   │   ├── onedrive/
│   │   │               │   │   │   │   ├── OneDriveAdapter.java
│   │   │               │   │   │   │   └── OneDriveAuthHandler.java
│   │   │               │   │   │   ├── dropbox/
│   │   │               │   │   │   │   ├── DropboxAdapter.java
│   │   │               │   │   │   │   └── DropboxAuthHandler.java
│   │   │               │   │   │   └── sftp/
│   │   │               │   │   │       ├── SftpAdapter.java
│   │   │               │   │   │       └── SshKeyHandler.java
│   │   │               │   │   │
│   │   │               │   │   ├── persistence/             # Data persistence
│   │   │               │   │   │   ├── PreferencesFileAdapter.java
│   │   │               │   │   │   └── CacheAdapter.java
│   │   │               │   │   │
│   │   │               │   │   └── system/                  # System integration
│   │   │               │   │       ├── TerminalAdapter.java
│   │   │               │   │       ├── SystemApplicationAdapter.java
│   │   │               │   │       └── ClipboardAdapter.java
│   │   │               │   │
│   │   │               │   ├── service/                     # Infrastructure services
│   │   │               │   │   ├── FileServiceImpl.java
│   │   │               │   │   ├── FileWatchService.java
│   │   │               │   │   ├── SystemService.java
│   │   │               │   │   ├── PreferencesService.java
│   │   │               │   │   ├── CompressionService.java
│   │   │               │   │   └── EncryptionService.java
│   │   │               │   │
│   │   │               │   └── util/                        # Utilities
│   │   │               │       ├── FileUtils.java
│   │   │               │       ├── PathUtils.java
│   │   │               │       ├── FormatUtils.java
│   │   │               │       └── ValidationUtils.java
│   │   │               │
│   │   │               ├── presentation/                    # Presentation layer
│   │   │               │   ├── controller/                  # Controllers
│   │   │               │   │   ├── base/
│   │   │               │   │   │   ├── BaseController.java
│   │   │               │   │   │   └── ViewController.java
│   │   │               │   │   │
│   │   │               │   │   ├── main/
│   │   │               │   │   │   ├── FileManagerController.java
│   │   │               │   │   │   ├── NavigationController.java
│   │   │               │   │   │   └── ToolbarController.java
│   │   │               │   │   │
│   │   │               │   │   ├── remote/
│   │   │               │   │   │   ├── RemoteBrowserController.java
│   │   │               │   │   │   ├── RemoteConnectionController.java
│   │   │               │   │   │   └── CloudSyncController.java
│   │   │               │   │   │
│   │   │               │   │   └── dialog/
│   │   │               │   │       ├── PropertiesDialogController.java
│   │   │               │   │       ├── SettingsDialogController.java
│   │   │               │   │       └── AboutDialogController.java
│   │   │               │   │
│   │   │               │   ├── viewmodel/                   # ViewModels
│   │   │               │   │   ├── FileManagerViewModel.java
│   │   │               │   │   ├── RemoteBrowserViewModel.java
│   │   │               │   │   ├── SearchViewModel.java
│   │   │               │   │   └── SettingsViewModel.java
│   │   │               │   │
│   │   │               │   ├── view/                        # Views (Java-based)
│   │   │               │   │   ├── base/
│   │   │               │   │   │   └── BaseView.java
│   │   │               │   │   │
│   │   │               │   │   ├── main/
│   │   │               │   │   │   ├── FileManagerView.java
│   │   │               │   │   │   ├── FileTableView.java
│   │   │               │   │   │   ├── NavigationBarView.java
│   │   │               │   │   │   ├── ToolbarView.java
│   │   │               │   │   │   └── StatusBarView.java
│   │   │               │   │   │
│   │   │               │   │   ├── remote/
│   │   │               │   │   │   ├── RemoteBrowserView.java
│   │   │               │   │   │   ├── RemoteConnectionDialog.java
│   │   │               │   │   │   └── CloudSyncView.java
│   │   │               │   │   │
│   │   │               │   │   ├── dialog/
│   │   │               │   │   │   ├── PropertiesDialog.java
│   │   │               │   │   │   ├── SettingsDialog.java
│   │   │               │   │   │   ├── AboutDialog.java
│   │   │               │   │   │   └── ConfirmationDialog.java
│   │   │               │   │   │
│   │   │               │   │   └── component/               # Reusable components
│   │   │               │   │       ├── SearchBar.java
│   │   │               │   │       ├── PathBreadcrumb.java
│   │   │               │   │       ├── FileIconView.java
│   │   │               │   │       └── ProgressOverlay.java
│   │   │               │   │
│   │   │               │   ├── factory/                     # View factories
│   │   │               │   │   ├── ViewFactory.java
│   │   │               │   │   ├── DialogFactory.java
│   │   │               │   │   └── ComponentFactory.java
│   │   │               │   │
│   │   │               │   ├── mapper/                      # View mappers
│   │   │               │   │   ├── FileItemMapper.java
│   │   │               │   │   └── RemoteFileItemMapper.java
│   │   │               │   │
│   │   │               │   └── util/                        # UI utilities
│   │   │               │       ├── FxUtils.java
│   │   │               │       ├── DialogUtils.java
│   │   │               │       ├── IconUtils.java
│   │   │               │       └── ThemeUtils.java
│   │   │               │
│   │   │               ├── common/                          # Common/Shared code
│   │   │               │   ├── constant/
│   │   │               │   │   ├── AppConstants.java
│   │   │               │   │   ├── FileConstants.java
│   │   │               │   │   └── UiConstants.java
│   │   │               │   │
│   │   │               │   ├── event/                       # Event bus/system
│   │   │               │   │   ├── Event.java
│   │   │               │   │   ├── EventBus.java
│   │   │               │   │   └── events/
│   │   │               │   │       ├── FileChangedEvent.java
│   │   │               │   │       ├── DirectoryChangedEvent.java
│   │   │               │   │       └── ConnectionEvent.java
│   │   │               │   │
│   │   │               │   ├── reactive/                    # Reactive utilities
│   │   │               │   │   ├── ReactorFx.java
│   │   │               │   │   ├── FxSchedulers.java
│   │   │               │   │   └── PropertyObservable.java
│   │   │               │   │
│   │   │               │   ├── validation/                  # Validation
│   │   │               │   │   ├── Validator.java
│   │   │               │   │   ├── ValidationResult.java
│   │   │               │   │   └── validators/
│   │   │               │   │       ├── PathValidator.java
│   │   │               │   │       ├── EmailValidator.java
│   │   │               │   │       └── FileNameValidator.java
│   │   │               │   │
│   │   │               │   └── annotation/                  # Custom annotations
│   │   │               │       ├── FxThread.java
│   │   │               │       └── Async.java
│   │   │               │
│   │   │               └── module-info.java                 # Java module descriptor
│   │   │
│   │   └── resources/
│   │       ├── application.properties                       # Main config
│   │       ├── application-dev.properties                   # Dev config
│   │       ├── application-prod.properties                  # Prod config
│   │       │
│   │       ├── fxml/                                        # FXML files (if used)
│   │       │   └── (empty - using Java-based views)
│   │       │
│   │       ├── styles/                                      # CSS stylesheets
│   │       │   ├── base.css                                 # Base styles
│   │       │   ├── theme-light.css                          # Light theme
│   │       │   ├── theme-dark.css                           # Dark theme
│   │       │   ├── components.css                           # Component styles
│   │       │   └── custom.css                               # Custom overrides
│   │       │
│   │       ├── icons/                                       # Application icons
│   │       │   ├── app/
│   │       │   │   ├── icon-16.png
│   │       │   │   ├── icon-32.png
│   │       │   │   ├── icon-64.png
│   │       │   │   ├── icon-128.png
│   │       │   │   ├── icon-256.png
│   │       │   │   ├── icon.ico                            # Windows
│   │       │   │   ├── icon.icns                           # macOS
│   │       │   │   └── icon.svg                            # Linux
│   │       │   │
│   │       │   ├── file-types/                             # File type icons
│   │       │   │   ├── folder.png
│   │       │   │   ├── file.png
│   │       │   │   ├── txt.png
│   │       │   │   ├── pdf.png
│   │       │   │   └── ...
│   │       │   │
│   │       │   └── toolbar/                                # Toolbar icons
│   │       │       ├── back.png
│   │       │       ├── forward.png
│   │       │       ├── refresh.png
│   │       │       └── ...
│   │       │
│   │       ├── fonts/                                      # Custom fonts
│   │       │   └── custom-font.ttf
│   │       │
│   │       ├── i18n/                                       # Internationalization
│   │       │   ├── messages.properties                     # Default (English)
│   │       │   ├── messages_es.properties                  # Spanish
│   │       │   ├── messages_fr.properties                  # French
│   │       │   ├── messages_de.properties                  # German
│   │       │   └── messages_ja.properties                  # Japanese
│   │       │
│   │       ├── templates/                                  # Templates
│   │       │   └── error-report.txt
│   │       │
│   │       └── META-INF/
│   │           └── MANIFEST.MF
│   │
│   └── test/
│       ├── java/
│       │   └── com/
│       │       └── example/
│       │           └── filemanager/
│       │               │
│       │               ├── core/                            # Core tests
│       │               │   ├── domain/
│       │               │   │   └── FileItemTest.java
│       │               │   │
│       │               │   └── usecase/
│       │               │       ├── ListFilesUseCaseTest.java
│       │               │       └── SearchFilesUseCaseTest.java
│       │               │
│       │               ├── infrastructure/                  # Infrastructure tests
│       │               │   ├── adapter/
│       │               │   │   └── LocalFileSystemAdapterTest.java
│       │               │   │
│       │               │   └── service/
│       │               │       ├── FileServiceTest.java
│       │               │       └── CompressionServiceTest.java
│       │               │
│       │               ├── presentation/                    # Presentation tests
│       │               │   ├── controller/
│       │               │   │   └── FileManagerControllerTest.java
│       │               │   │
│       │               │   └── viewmodel/
│       │               │       └── FileManagerViewModelTest.java
│       │               │
│       │               ├── integration/                     # Integration tests
│       │               │   ├── FileOperationsIT.java
│       │               │   ├── CloudSyncIT.java
│       │               │   └── SearchIT.java
│       │               │
│       │               ├── e2e/                             # End-to-end tests
│       │               │   ├── FileManagerE2ETest.java
│       │               │   └── RemoteBrowserE2ETest.java
│       │               │
│       │               └── util/                            # Test utilities
│       │                   ├── TestDataBuilder.java
│       │                   ├── MockFactory.java
│       │                   └── FxTestUtils.java
│       │
│       └── resources/
│           ├── application-test.properties                  # Test config
│           ├── test-data/                                   # Test data
│           │   ├── sample-files/
│           │   └── test-credentials.json
│           │
│           └── logback-test.xml                             # Test logging config
│
├── scripts/                                                 # Build/deployment scripts
│   ├── build/
│   │   ├── build-native.sh                                  # Native build script
│   │   └── build-installer.sh                              # Installer creation
│   │
│   ├── deploy/
│   │   ├── deploy-local.sh
│   │   └── deploy-release.sh
│   │
│   └── dev/
│       ├── setup-env.sh                                     # Environment setup
│       └── run-dev.sh                                       # Development run script
│
├── tools/                                                   # Development tools
│   ├── code-quality/
│   │   ├── checkstyle.xml                                   # Checkstyle config
│   │   ├── spotbugs-exclude.xml                            # SpotBugs exclusions
│   │   └── pmd-ruleset.xml                                 # PMD rules
│   │
│   └── ide/
│       ├── intellij/
│       │   └── code-style.xml
│       └── eclipse/
│           └── formatter.xml
│
├── .gitignore                                               # Git ignore rules
├── .gitattributes                                           # Git attributes
├── .editorconfig                                            # Editor configuration
│
├── build.gradle                                             # Gradle build file
├── settings.gradle                                          # Gradle settings
├── gradle.properties                                        # Gradle properties
├── gradlew                                                  # Gradle wrapper (Unix)
├── gradlew.bat                                              # Gradle wrapper (Windows)
│
├── README.md                                                # Project README
├── CHANGELOG.md                                             # Version changelog
├── CONTRIBUTING.md                                          # Contribution guidelines
├── LICENSE                                                  # License file
├── CODE_OF_CONDUCT.md                                       # Code of conduct
│
└── docker/                                                  # Docker files (optional)
    ├── Dockerfile
    └── docker-compose.yml
```

## Prerequisites

- Java 25 or higher
- Gradle 8.0+ (or use the Gradle wrapper)

## Getting Started

### 1. Build the project

```bash
./gradlew build
```

Or on Windows:

```bash
gradlew.bat build
```

### 2. Run the application

```bash
./gradlew bootRun
```

Or use the custom run task:

```bash
./gradlew runApp
```

### 3. Create a distribution

```bash
./gradlew installDist
```

The distribution will be in `build/install/reactive-file-manager/`

## Architecture

### Java-based Views (No FXML)

Unlike traditional JavaFX applications that use FXML for UI definition, this application builds the entire UI programmatically in Java:

**FileManagerView.java** - Constructs the UI hierarchy:
- Uses JavaFX layout containers (BorderPane, VBox, HBox)
- Creates controls programmatically (TableView, TextField, Button)
- Provides getter methods for controller access
- No external XML files needed

**Benefits:**
- Type safety at compile time
- Better IDE support (autocomplete, refactoring)
- Easier to understand control flow
- No FXML loader overhead
- Simpler debugging

### Reactive Design

The application uses Project Reactor (Flux/Mono) for all file operations:

- **Flux<FileItem>**: Streams of files from directories
- **Mono<Void>**: Single async operations (copy, move, delete)
- **Schedulers.boundedElastic()**: Dedicated thread pool for blocking I/O

### Spring Boot Integration

- **@SpringBootApplication**: Auto-configuration and component scanning
- **@Service**: Business logic for file operations
- **@Component**: Views and controllers with dependency injection
- **Constructor Injection**: Automatic wiring of dependencies

### JavaFX UI Thread

All reactive streams use `Platform.runLater()` to update the UI on the JavaFX Application Thread, ensuring thread safety.

## Key Components

### ReactiveFileManagerApp
Main application class that:
- Initializes Spring Boot context
- Launches JavaFX application
- Retrieves the view from Spring context
- Manages application lifecycle

### FileManagerView
Java-based UI builder that:
- Constructs all UI components programmatically
- Organizes layout (top toolbar, center table, bottom status)
- Exposes components via getters
- Wires up basic event handlers
- Creates context menu for file operations

### FileManagerController
Business logic coordinator that:
- Manages application state (current path, file items, clipboard)
- Handles user interactions (open, copy, cut, paste, delete, etc.)
- Bridges reactive streams to UI updates
- Coordinates file operations
- Manages context menu actions

### FileService
Provides reactive file operations:
- `listFiles(Path)`: List directory contents
- `searchFiles(Path, String)`: Recursive search
- `openFile(Path)`: Open with system default
- `openWith(Path, String)`: Open with specific application
- `copyFile/moveFile/deleteFile`: File operations
- `createDirectory`: Folder creation
- `compressFiles`: Create ZIP archives
- `renameFile`: Rename files/folders

### SystemService
System integration services:
- `getAvailableTerminals()`: Detect installed terminals
- `openInTerminal(Path, String)`: Launch terminal at location
- `getInstalledApplications()`: List available applications
- `shareFile(Path)`: Native file sharing

### PreferencesService
User preferences management:
- `setDefaultApplication(String, String)`: Set default app for extension
- `getDefaultApplication(String)`: Get default app for extension
- `getAllDefaultApplications()`: List all configured defaults
- Persists to `~/.filemanager/preferences.properties`

### FileWatchService
Monitors directory changes using Java NIO WatchService wrapped in reactive streams.

## Usage

### Navigation
- Double-click folders to navigate into them
- Double-click files to open them with default application
- Click "Back" button to go to parent directory
- Type path in text field and press Enter to jump to location
- Click "Refresh" to reload current directory

### Search
- Enter search term in search field
- Press Enter to search recursively from current directory
- Clear search field and press Enter to show all files

### File Operations

**Opening Files:**
- Double-click any file to open with system default or your configured default app
- Right-click → "Open With..." to choose a specific application
- Right-click → "Set Default Application" to configure default app for file type

**File Management:**
- Right-click → "Copy" or "Cut" to prepare file for moving
- Right-click → "Paste" in destination folder
- Right-click → "Rename" to change file/folder name
- Right-click → "Delete" to remove files/folders
- Select multiple files (Ctrl/Cmd+Click) for batch operations

**Compression:**
- Select one or more files/folders
- Click "Compress" button or right-click → "Compress"
- Enter archive name (will add .zip if not specified)

**Terminal Access:**
- Click "Terminal" button to open terminal in current directory
- Right-click → "Open in Terminal" for specific folder
- Automatically detects available terminals on your system:
    - Windows: Windows Terminal, PowerShell, CMD
    - macOS: Terminal, iTerm
    - Linux: gnome-terminal, konsole, xterm, alacritty, kitty, terminator

**Properties:**
- Right-click any file/folder → "Properties"
- View: name, type, size, location, dates, permissions, default app

**Sharing:**
- Right-click → "Share" to open system sharing dialog

## Gradle Tasks

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun
./gradlew runApp

# Run tests
./gradlew test

# Create distribution
./gradlew installDist

# Clean build directory
./gradlew clean

# Show dependencies
./gradlew dependencies
```

## Extending the Application

### Adding Custom File Type Handlers

```java
// In FileService
public Mono<Void> openMarkdownFile(Path file) {
    return Mono.fromRunnable(() -> {
        // Custom markdown viewer logic
    }).subscribeOn(ioScheduler).then();
}
```

### Adding More Context Menu Items

```java
// In FileManagerController.createContextMenu()
MenuItem customItem = new MenuItem("Custom Action");
customItem.setOnAction(e -> {
    if (!row.isEmpty()) {
        // Your custom logic here
    }
});
contextMenu.getItems().add(customItem);
```

### Adding Drag and Drop Support

```java
// In FileManagerView
fileTable.setOnDragOver(event -> {
    if (event.getDragboard().hasFiles()) {
        event.acceptTransferModes(TransferMode.COPY);
    }
    event.consume();
});

fileTable.setOnDragDropped(event -> {
    Dragboard db = event.getDragboard();
    if (db.hasFiles()) {
        for (File file : db.getFiles()) {
            // Handle dropped files
        }
        event.setDropCompleted(true);
    }
    event.consume();
});
```

### Adding File Preview Panel

```java
@Component
public class FilePreviewPanel extends VBox {
    private ImageView imagePreview;
    private TextArea textPreview;
    
    public void showPreview(FileItem item) {
        String ext = item.getExtension().toLowerCase();
        if (List.of("jpg", "png", "gif").contains(ext)) {
            showImagePreview(item.getPath());
        } else if (List.of("txt", "java", "md").contains(ext)) {
            showTextPreview(item.getPath());
        }
    }
}
```

## Performance Considerations

- **Bounded Elastic Scheduler**: Optimized for blocking I/O operations
- **Stream Processing**: Files loaded incrementally, not all at once
- **Backpressure Handling**: Reactive streams handle fast producers
- **Platform.runLater()**: Ensures UI updates are thread-safe
- **JavaFX Scene Graph**: Efficient rendering without FXML parsing overhead

## Advantages of Java-based Views

1. **Type Safety**: Compile-time checking of all UI code
2. **Refactoring**: IDE can safely rename and move components
3. **Debugging**: Step through UI creation code
4. **Code Reuse**: Easily extract common UI patterns into methods
5. **No XML Parsing**: Faster startup time
6. **Better Testing**: Can unit test view construction

## Troubleshooting

### JavaFX not found
Make sure the JavaFX plugin is properly configured in build.gradle.

### Module errors
If you get module-related errors, ensure your JVM args include:
```
--module-path <classpath>
--add-modules javafx.controls,javafx.graphics
```

### Files not refreshing
Check that the FileWatchService is properly initialized and not disposed.

### Slow directory loading
Large directories are loaded incrementally. Consider adding virtual scrolling or pagination.

## License

MIT License

## Contributing

Contributions welcome! Please feel free to submit a Pull Request.

---

## Quick Start Commands

```bash
# Clone and run
git clone <repository>
cd reactive-file-manager
./gradlew bootRun

# Build standalone
./gradlew build
java -jar build/libs/reactive-file-manager-1.0.0.jar
```