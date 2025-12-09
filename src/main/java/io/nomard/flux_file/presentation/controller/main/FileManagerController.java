package io.nomard.flux_file.presentation.controller.main;

import io.nomard.flux_file.core.domain.model.FileItem;
import io.nomard.flux_file.infrastructure.service.FileService;
import io.nomard.flux_file.infrastructure.service.FileWatchService;
import io.nomard.flux_file.infrastructure.service.PreferencesService;
import io.nomard.flux_file.infrastructure.service.SystemService;
import io.nomard.flux_file.infrastructure.service.remote.RemoteFileSystemService;
import io.nomard.flux_file.presentation.controller.remote.RemoteBrowserController;
import io.nomard.flux_file.presentation.view.main.FileManagerView;
import io.nomard.flux_file.presentation.view.remote.RemoteBrowserView;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@Setter
public class FileManagerController {

    private final ObservableList<FileItem> fileItems = FXCollections.observableArrayList();
    private final FileService fileService;
    private final FileWatchService fileWatchService;
    private final SystemService systemService;
    private final PreferencesService preferencesService;
    private final RemoteBrowserController remoteBrowserController;
    private final RemoteBrowserView remoteBrowserView;
    private final Map<Path, Disposable> watcherDisposables = new HashMap<>();
    private FileManagerView view;
    private Path currentPath;
    private Disposable watchDisposable;
    private Path clipboard;
    private boolean isCutOperation = false;

    public FileManagerController(RemoteBrowserView remoteBrowserView, RemoteBrowserController remoteBrowserController, PreferencesService preferencesService, SystemService systemService, FileWatchService fileWatchService, FileService fileService) {
        this.remoteBrowserView = remoteBrowserView;
        this.remoteBrowserController = remoteBrowserController;
        this.preferencesService = preferencesService;
        this.systemService = systemService;
        this.fileWatchService = fileWatchService;
        this.fileService = fileService;
    }

    public void initialize() {
        setupEventHandlers();

        String userHome = System.getProperty("user.home");
        currentPath = Paths.get(userHome);
        view.getPathField().setText(currentPath.toString());

        view.getFileTable().setItems(fileItems);

        loadDirectory(currentPath);
    }

    private void setupEventHandlers() {
        view.getPathField().setOnAction(e -> {
            Path path = Paths.get(view.getPathField().getText());
            loadDirectory(path);
        });

        view.getSearchField().setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                performSearch();
            }
        });

        view.getFileTable().setRowFactory(tv -> {
            TableRow<FileItem> row = new TableRow<>();

            // Double-click to open
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    FileItem item = row.getItem();
                    if (item.isDirectory()) {
                        navigateToDirectory(item.path());
                    } else {
                        openFile(item);
                    }
                }
            });

            // Right-click context menu
            row.setContextMenu(createContextMenu(row));

            return row;
        });
    }

    private ContextMenu createContextMenu(TableRow<FileItem> row) {
        ContextMenu contextMenu = new ContextMenu();

        MenuItem openItem = new MenuItem("Open");
        openItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                FileItem item = row.getItem();
                if (item.isDirectory()) {
                    navigateToDirectory(item.path());
                } else {
                    openFile(item);
                }
            }
        });

        MenuItem openWithItem = new MenuItem("Open With...");
        openWithItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                showOpenWithDialog(row.getItem());
            }
        });

        MenuItem setDefaultItem = new MenuItem("Set Default Application");
        setDefaultItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                showSetDefaultDialog(row.getItem());
            }
        });

        MenuItem propertiesItem = new MenuItem("Properties");
        propertiesItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                showPropertiesDialog(row.getItem());
            }
        });

        MenuItem renameItem = new MenuItem("Rename");
        renameItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                handleRename(row.getItem());
            }
        });

        MenuItem copyItem = new MenuItem("Copy");
        copyItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                handleCopy(row.getItem());
            }
        });

        MenuItem cutItem = new MenuItem("Cut");
        cutItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                handleCut(row.getItem());
            }
        });

        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setOnAction(e -> handlePaste());
        pasteItem.setDisable(clipboard == null);

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                handleDelete();
            }
        });

        MenuItem compressItem = new MenuItem("Compress");
        compressItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                handleCompress();
            }
        });

        MenuItem shareItem = new MenuItem("Share");
        shareItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                handleShare(row.getItem());
            }
        });

        MenuItem terminalItem = new MenuItem("Open in Terminal");
        terminalItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                FileItem item = row.getItem();
                Path terminalPath = item.isDirectory() ? item.path() : item.path().getParent();
                openTerminalAt(terminalPath);
            }
        });

        MenuItem backupToCloudItem = new MenuItem("Backup to Cloud");
        backupToCloudItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                handleBackupToCloud(row.getItem());
            }
        });

        contextMenu.getItems().addAll(
                openItem,
                openWithItem,
                setDefaultItem,
                new SeparatorMenuItem(),
                renameItem,
                copyItem,
                cutItem,
                pasteItem,
                deleteItem,
                new SeparatorMenuItem(),
                compressItem,
                shareItem,
                terminalItem,
                backupToCloudItem,
                new SeparatorMenuItem(),
                propertiesItem
        );

        return contextMenu;
    }

    private void openFile(FileItem item) {
        String extension = item.getExtension();
        String defaultApp = preferencesService.getDefaultApplication(extension);

        if (defaultApp != null && !defaultApp.isEmpty()) {
            fileService.openWith(item.path(), defaultApp)
                    .doOnError(e -> Platform.runLater(() ->
                            showError("Error", "Failed to open file: " + e.getMessage())))
                    .subscribe();
        } else {
            fileService.openFile(item.path())
                    .doOnError(e -> Platform.runLater(() ->
                            showError("Error", "Failed to open file: " + e.getMessage())))
                    .subscribe();
        }
    }

    private void showOpenWithDialog(FileItem item) {
        systemService.getInstalledApplications()
                .doOnSuccess(apps -> Platform.runLater(() -> {
                    ChoiceDialog<String> dialog = new ChoiceDialog<>(apps.isEmpty() ? null : apps.getFirst(), apps);
                    dialog.setTitle("Open With");
                    dialog.setHeaderText("Select an application to open " + item.name());
                    dialog.setContentText("Application:");

                    Optional<String> result = dialog.showAndWait();
                    result.ifPresent(app -> {
                        fileService.openWith(item.path(), app)
                                .doOnError(e -> Platform.runLater(() ->
                                        showError("Error", "Failed to open file: " + e.getMessage())))
                                .subscribe();
                    });
                }))
                .subscribe();
    }

    private void showSetDefaultDialog(FileItem item) {
        if (item.isDirectory()) {
            showError("Error", "Cannot set default application for directories");
            return;
        }

        String extension = item.getExtension();
        if (extension.isEmpty()) {
            showError("Error", "File has no extension");
            return;
        }

        systemService.getInstalledApplications()
                .doOnSuccess(apps -> Platform.runLater(() -> {
                    String currentDefault = preferencesService.getDefaultApplication(extension);
                    ChoiceDialog<String> dialog = new ChoiceDialog<>(
                            currentDefault != null ? currentDefault : (apps.isEmpty() ? null : apps.getFirst()),
                            apps
                    );
                    dialog.setTitle("Set Default Application");
                    dialog.setHeaderText("Set default application for ." + extension + " files");
                    dialog.setContentText("Application:");

                    Optional<String> result = dialog.showAndWait();
                    result.ifPresent(app -> {
                        preferencesService.setDefaultApplication(extension, app);
                        view.getStatusLabel().setText("Default application set for ." + extension);
                    });
                }))
                .subscribe();
    }

    private void showPropertiesDialog(FileItem item) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Properties");
        dialog.setHeaderText(item.name());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));

        int row = 0;
        grid.add(new Label("Name:"), 0, row);
        grid.add(new Label(item.name()), 1, row++);

        grid.add(new Label("Type:"), 0, row);
        grid.add(new Label(item.getType()), 1, row++);

        grid.add(new Label("Location:"), 0, row);
        grid.add(new Label(item.path().getParent().toString()), 1, row++);

        grid.add(new Label("Size:"), 0, row);
        grid.add(new Label(item.getFormattedSize()), 1, row++);

        grid.add(new Label("Modified:"), 0, row);
        grid.add(new Label(item.getFormattedDate()), 1, row++);

        try {
            BasicFileAttributes attrs = Files.readAttributes(item.path(), BasicFileAttributes.class);

            grid.add(new Label("Created:"), 0, row);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
            grid.add(new Label(formatter.format(attrs.creationTime().toInstant())), 1, row++);

            grid.add(new Label("Accessed:"), 0, row);
            grid.add(new Label(formatter.format(attrs.lastAccessTime().toInstant())), 1, row++);

            grid.add(new Label("Readable:"), 0, row);
            grid.add(new Label(Files.isReadable(item.path()) ? "Yes" : "No"), 1, row++);

            grid.add(new Label("Writable:"), 0, row);
            grid.add(new Label(Files.isWritable(item.path()) ? "Yes" : "No"), 1, row++);

            grid.add(new Label("Executable:"), 0, row);
            grid.add(new Label(Files.isExecutable(item.path()) ? "Yes" : "No"), 1, row++);

            if (!item.isDirectory() && !item.getExtension().isEmpty()) {
                String defaultApp = preferencesService.getDefaultApplication(item.getExtension());
                if (defaultApp != null) {
                    grid.add(new Label("Default App:"), 0, row);
                    grid.add(new Label(defaultApp), 1, row++);
                }
            }

        } catch (IOException e) {
            // Ignore
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }

    private void handleRename(FileItem item) {
        TextInputDialog dialog = new TextInputDialog(item.name());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename " + item.name());
        dialog.setContentText("New name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(newName -> {
            if (!newName.equals(item.name())) {
                fileService.renameFile(item.path(), newName)
                        .doOnSuccess(v -> Platform.runLater(this::refreshDirectory))
                        .doOnError(e -> Platform.runLater(() ->
                                showError("Rename Failed", e.getMessage())))
                        .subscribe();
            }
        });
    }

    private void handleCopy(FileItem item) {
        clipboard = item.path();
        isCutOperation = false;
        view.getStatusLabel().setText("Copied: " + item.name());
    }

    private void handleCut(FileItem item) {
        clipboard = item.path();
        isCutOperation = true;
        view.getStatusLabel().setText("Cut: " + item.name());
    }

    private void handlePaste() {
        if (clipboard == null) return;

        Path target = currentPath.resolve(clipboard.getFileName());

        // Handle file name conflicts
        if (Files.exists(target)) {
            int counter = 1;
            String baseName = clipboard.getFileName().toString();
            String extension = "";
            int lastDot = baseName.lastIndexOf('.');
            if (lastDot > 0) {
                extension = baseName.substring(lastDot);
                baseName = baseName.substring(0, lastDot);
            }

            while (Files.exists(target)) {
                target = currentPath.resolve(baseName + " (" + counter + ")" + extension);
                counter++;
            }
        }

        Path finalTarget = target;

        if (isCutOperation) {
            fileService.moveFile(clipboard, finalTarget)
                    .doOnSuccess(v -> Platform.runLater(() -> {
                        clipboard = null;
                        isCutOperation = false;
                        refreshDirectory();
                    }))
                    .doOnError(e -> Platform.runLater(() ->
                            showError("Move Failed", e.getMessage())))
                    .subscribe();
        } else {
            fileService.copyFile(clipboard, finalTarget)
                    .doOnSuccess(v -> Platform.runLater(this::refreshDirectory))
                    .doOnError(e -> Platform.runLater(() ->
                            showError("Copy Failed", e.getMessage())))
                    .subscribe();
        }
    }

    private void handleShare(FileItem item) {
        systemService.shareFile(item.path())
                .doOnError(e -> Platform.runLater(() ->
                        showError("Share Failed", e.getMessage())))
                .subscribe();
    }

    private void openTerminalAt(Path directory) {
        systemService.getAvailableTerminals()
                .doOnSuccess(terminals -> Platform.runLater(() -> {
                    if (terminals.isEmpty()) {
                        showError("Error", "No terminal applications found");
                        return;
                    }

                    if (terminals.size() == 1) {
                        systemService.openInTerminal(directory, terminals.getFirst())
                                .doOnError(e -> Platform.runLater(() ->
                                        showError("Error", "Failed to open terminal: " + e.getMessage())))
                                .subscribe();
                    } else {
                        ChoiceDialog<String> dialog = new ChoiceDialog<>(terminals.getFirst(), terminals);
                        dialog.setTitle("Open Terminal");
                        dialog.setHeaderText("Select a terminal to open");
                        dialog.setContentText("Terminal:");

                        Optional<String> result = dialog.showAndWait();
                        result.ifPresent(terminal -> {
                            systemService.openInTerminal(directory, terminal)
                                    .doOnError(e -> Platform.runLater(() ->
                                            showError("Error", "Failed to open terminal: " + e.getMessage())))
                                    .subscribe();
                        });
                    }
                }))
                .subscribe();
    }

    private void loadDirectory(Path directory) {
        // Dispose old watcher before creating new one
        disposeCurrentWatcher();

        currentPath = directory;

        fileItems.clear();
        view.getProgressIndicator().setVisible(true);
        view.getStatusLabel().setText("Loading...");

        if (watchDisposable != null && !watchDisposable.isDisposed()) {
            watchDisposable.dispose();
        }

        fileService.listFiles(directory)
                .doOnNext(fileItem -> Platform.runLater(() -> fileItems.add(fileItem)))
                .doOnComplete(() -> Platform.runLater(() -> {
                    view.getProgressIndicator().setVisible(false);
                    view.getStatusLabel().setText(fileItems.size() + " items");
                    currentPath = directory;
                    view.getPathField().setText(directory.toString());
                    startWatching(directory);
                }))
                .doOnError(error -> Platform.runLater(() -> {
                    view.getProgressIndicator().setVisible(false);
                    view.getStatusLabel().setText("Error loading directory");
                    showError("Error", "Failed to load directory: " + error.getMessage());
                }))
                .subscribe();

        // Start watching new directory
        startWatching(directory);
    }

    private void startWatching(Path directory) {
        if (directory == null) return;

        // Dispose any existing watcher for this path
        disposeWatcher(directory);

        log.debug("Starting to watch directory: {}", directory);

        Disposable disposable = fileWatchService.watchDirectory(directory)
                .subscribe(
                        event -> Platform.runLater(() -> {
                            log.debug("File change detected: {} - {}", event.kind(), event.context());
                            refreshDirectory();
                        }),
                        error -> {
                            log.error("Error watching directory: {}", directory, error);
                            Platform.runLater(() -> showError("Watch Error", error.getMessage()));
                        },
                        () -> log.debug("Watch completed for: {}", directory)
                );

        watcherDisposables.put(directory, disposable);
    }

    private void disposeCurrentWatcher() {
        if (currentPath != null) {
            disposeWatcher(currentPath);
        }
    }

    private void disposeWatcher(Path path) {
        Disposable disposable = watcherDisposables.remove(path);
        if (disposable != null && !disposable.isDisposed()) {
            log.debug("Disposing watcher for: {}", path);
            disposable.dispose();
        }

        // Also tell the service to stop
        fileWatchService.stopWatching(path);
    }

    public void cleanup() {
        log.info("Cleaning up controller, disposing all watchers");

        watcherDisposables.values().forEach(disposable -> {
            if (!disposable.isDisposed()) {
                disposable.dispose();
            }
        });

        watcherDisposables.clear();
    }

    private void navigateToDirectory(Path path) {
        loadDirectory(path);
    }

    public void navigateBack() {
        if (currentPath.getParent() != null) {
            loadDirectory(currentPath.getParent());
        }
    }

    public void refreshDirectory() {
        loadDirectory(currentPath);
    }

    private void performSearch() {
        String searchTerm = view.getSearchField().getText().trim();
        if (searchTerm.isEmpty()) {
            refreshDirectory();
            return;
        }

        fileItems.clear();
        view.getProgressIndicator().setVisible(true);
        view.getStatusLabel().setText("Searching...");

        fileService.searchFiles(currentPath, searchTerm)
                .doOnNext(fileItem -> Platform.runLater(() -> fileItems.add(fileItem)))
                .doOnComplete(() -> Platform.runLater(() -> {
                    view.getProgressIndicator().setVisible(false);
                    view.getStatusLabel().setText("Found " + fileItems.size() + " items");
                }))
                .subscribe();
    }

    public void handleDelete() {
        ObservableList<FileItem> selected = view.getFileTable().getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Files");
        alert.setHeaderText("Delete " + selected.size() + " item(s)?");
        alert.setContentText("This action cannot be undone.");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            List<FileItem> itemsToDelete = new ArrayList<>(selected);
            for (FileItem item : itemsToDelete) {
                fileService.deleteFile(item.path())
                        .doOnSuccess(v -> Platform.runLater(this::refreshDirectory))
                        .doOnError(e -> Platform.runLater(() ->
                                showError("Delete Failed", e.getMessage())))
                        .subscribe();
            }
        }
    }

    public void handleNewFolder() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Folder");
        dialog.setHeaderText("Create a new folder");
        dialog.setContentText("Folder name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            Path newFolder = currentPath.resolve(name);
            fileService.createDirectory(newFolder)
                    .doOnSuccess(success -> Platform.runLater(() -> {
                        if (success) {
                            refreshDirectory();
                        } else {
                            showError("Error", "Failed to create folder");
                        }
                    }))
                    .subscribe();
        });
    }

    public void handleCompress() {
        ObservableList<FileItem> selected = view.getFileTable().getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;

        TextInputDialog dialog = new TextInputDialog("archive.zip");
        dialog.setTitle("Compress Files");
        dialog.setHeaderText("Compress " + selected.size() + " item(s)");
        dialog.setContentText("Archive name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.endsWith(".zip")) {
                name += ".zip";
            }
            Path zipFile = currentPath.resolve(name);
            List<Path> files = selected.stream()
                    .map(FileItem::path)
                    .collect(Collectors.toList());

            String finalName = name;
            fileService.compressFiles(files, zipFile)
                    .doOnSuccess(v -> Platform.runLater(() -> {
                        refreshDirectory();
                        view.getStatusLabel().setText("Compressed to " + finalName);
                    }))
                    .doOnError(e -> Platform.runLater(() ->
                            showError("Compression Failed", e.getMessage())))
                    .subscribe();
        });
    }

    public void handleOpenTerminal() {
        openTerminalAt(currentPath);
    }

    public void handleRemoteConnect() {
        // Open remote browser in new window or tab
        javafx.stage.Stage stage = new javafx.stage.Stage();
        stage.setTitle("Remote File Browser");
        javafx.scene.Scene scene = new javafx.scene.Scene(remoteBrowserView.getView(), 1000, 600);
        stage.setScene(scene);
        stage.show();

        // Trigger connection dialog
        remoteBrowserController.connect();
    }

    private void handleBackupToCloud(FileItem item) {
        if (!remoteBrowserController.isConnected()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Cloud Backup");
            alert.setHeaderText("Not connected to any cloud service");
            alert.setContentText("Would you like to connect now?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                handleRemoteConnect();
            }
            return;
        }

        // Upload to current remote service
        view.getProgressIndicator().setVisible(true);
        view.getStatusLabel().setText("Backing up to cloud...");

        RemoteFileSystemService service =
                remoteBrowserController.getCurrentService();

        service.uploadFile(item.path(), item.name())
                .doOnSuccess(v -> Platform.runLater(() -> {
                    view.getProgressIndicator().setVisible(false);
                    view.getStatusLabel().setText("Backup complete: " + item.name());
                }))
                .doOnError(error -> Platform.runLater(() -> {
                    view.getProgressIndicator().setVisible(false);
                    showError("Backup Failed", error.getMessage());
                }))
                .subscribe();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}