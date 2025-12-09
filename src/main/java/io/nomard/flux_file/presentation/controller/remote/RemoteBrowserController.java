package io.nomard.flux_file.presentation.controller.remote;

import io.nomard.flux_file.core.domain.model.RemoteFileItem;
import io.nomard.flux_file.infrastructure.service.remote.RemoteConnectionManager;
import io.nomard.flux_file.infrastructure.service.remote.RemoteFileSystemService;
import io.nomard.flux_file.presentation.view.remote.RemoteBrowserView;
import io.nomard.flux_file.presentation.view.remote.RemoteConnectionDialog;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class RemoteBrowserController {

    @Autowired
    private RemoteConnectionManager connectionManager;
    
    @Autowired
    private RemoteConnectionDialog connectionDialog;

    private RemoteBrowserView view;
    private ObservableList<RemoteFileItem> remoteFileItems = FXCollections.observableArrayList();
    private RemoteFileSystemService currentService;
    private String currentRemotePath;
    private String connectionName;

    public void setView(RemoteBrowserView view) {
        this.view = view;
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        view.getFileTable().setRowFactory(tv -> {
            TableRow<RemoteFileItem> row = new TableRow<>();
            
            // Double-click to open folder
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    RemoteFileItem item = row.getItem();
                    if (item.isDirectory()) {
                        navigateToDirectory(item.getId());
                    }
                }
            });
            
            // Context menu
            row.setContextMenu(createContextMenu(row));
            
            return row;
        });
        
        view.getFileTable().setItems(remoteFileItems);
    }

    private ContextMenu createContextMenu(TableRow<RemoteFileItem> row) {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem downloadItem = new MenuItem("Download");
        downloadItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                handleDownload();
            }
        });
        
        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setOnAction(e -> {
            if (!row.isEmpty()) {
                handleDelete();
            }
        });
        
        MenuItem refreshItem = new MenuItem("Refresh");
        refreshItem.setOnAction(e -> refreshDirectory());
        
        contextMenu.getItems().addAll(downloadItem, deleteItem, new SeparatorMenuItem(), refreshItem);
        
        return contextMenu;
    }

    public void connect() {
        Optional<RemoteConnectionDialog.RemoteConnection> result = connectionDialog.showConnectionDialog();
        
        result.ifPresent(connection -> {
            connectionName = connection.getName();
            String serviceType = getServiceType(connection.getService());
            currentService = connectionManager.getService(serviceType);
            
            view.getProgressIndicator().setVisible(true);
            view.getStatusLabel().setText("Connecting to " + connection.getService() + "...");
            
            currentService.connect(connection.getCredentials())
                .doOnSuccess(success -> Platform.runLater(() -> {
                    if (success) {
                        connectionManager.registerConnection(connectionName, currentService);
                        view.getConnectionLabel().setText(connectionName + " (" + connection.getService() + ")");
                        view.getStatusLabel().setText("Connected successfully");
                        
                        // Show storage info
                        showStorageInfo();
                        
                        // Load root directory
                        loadDirectory(null);
                    } else {
                        view.getProgressIndicator().setVisible(false);
                        showError("Connection Failed", "Could not connect to " + connection.getService());
                    }
                }))
                .doOnError(error -> Platform.runLater(() -> {
                    view.getProgressIndicator().setVisible(false);
                    showError("Connection Error", error.getMessage());
                }))
                .subscribe();
        });
    }

    private String getServiceType(String serviceName) {
        switch (serviceName) {
            case "Google Drive":
                return "googledrive";
            case "Microsoft OneDrive":
                return "onedrive";
            case "Dropbox":
                return "dropbox";
            case "SFTP/SSH":
                return "sftp";
            default:
                return serviceName.toLowerCase().replace(" ", "");
        }
    }

    private void showStorageInfo() {
        if (currentService == null) return;
        
        currentService.getUsedSpace()
            .zipWith(currentService.getAvailableSpace())
            .doOnSuccess(tuple -> Platform.runLater(() -> {
                long used = tuple.getT1();
                long available = tuple.getT2();
                
                if (used >= 0 && available >= 0) {
                    String usedStr = formatSize(used);
                    String availableStr = formatSize(available);
                    String totalStr = formatSize(used + available);
                    
                    view.getStatusLabel().setText(
                        String.format("Storage: %s used of %s (%s available)", 
                            usedStr, totalStr, availableStr)
                    );
                }
            }))
            .subscribe();
    }

    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.2f MB", size / (1024.0 * 1024));
        return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
    }

    public void disconnect() {
        if (currentService != null) {
            currentService.disconnect()
                .doOnSuccess(v -> Platform.runLater(() -> {
                    if (connectionName != null) {
                        connectionManager.removeConnection(connectionName);
                    }
                    currentService = null;
                    currentRemotePath = null;
                    connectionName = null;
                    remoteFileItems.clear();
                    view.getConnectionLabel().setText("Not connected");
                    view.getStatusLabel().setText("Disconnected");
                    view.getPathField().setText("");
                }))
                .subscribe();
        }
    }

    private void loadDirectory(String remotePath) {
        if (currentService == null) {
            showError("Error", "Not connected to any remote service");
            return;
        }
        
        remoteFileItems.clear();
        view.getProgressIndicator().setVisible(true);
        view.getStatusLabel().setText("Loading...");
        
        currentService.listFiles(remotePath)
            .doOnNext(item -> Platform.runLater(() -> remoteFileItems.add(item)))
            .doOnComplete(() -> Platform.runLater(() -> {
                view.getProgressIndicator().setVisible(false);
                view.getStatusLabel().setText(remoteFileItems.size() + " items");
                currentRemotePath = remotePath != null ? remotePath : "root";
                view.getPathField().setText(currentRemotePath);
            }))
            .doOnError(error -> Platform.runLater(() -> {
                view.getProgressIndicator().setVisible(false);
                showError("Error", "Failed to load directory: " + error.getMessage());
            }))
            .subscribe();
    }

    private void navigateToDirectory(String remotePath) {
        loadDirectory(remotePath);
    }

    public void navigateBack() {
        if (currentRemotePath != null && !currentRemotePath.equals("root")) {
            // Navigate to parent - implementation depends on service
            String parent = getParentPath(currentRemotePath);
            loadDirectory(parent);
        }
    }

    private String getParentPath(String path) {
        if (path == null || path.equals("root") || path.isEmpty()) {
            return "root";
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash > 0) {
            return path.substring(0, lastSlash);
        }
        return "root";
    }

    public void refreshDirectory() {
        loadDirectory(currentRemotePath);
    }

    public void handleDownload() {
        ObservableList<RemoteFileItem> selected = view.getFileTable().getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            showError("Error", "Please select files to download");
            return;
        }
        
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Download Location");
        File selectedDirectory = directoryChooser.showDialog(view.getView().getScene().getWindow());
        
        if (selectedDirectory != null) {
            Path downloadPath = selectedDirectory.toPath();
            
            view.getProgressIndicator().setVisible(true);
            view.getStatusLabel().setText("Downloading " + selected.size() + " item(s)...");
            
            List<RemoteFileItem> itemsToDownload = new ArrayList<>(selected);
            downloadFiles(itemsToDownload, downloadPath, 0);
        }
    }

    private void downloadFiles(List<RemoteFileItem> items, Path downloadPath, int index) {
        if (index >= items.size()) {
            Platform.runLater(() -> {
                view.getProgressIndicator().setVisible(false);
                view.getStatusLabel().setText("Download complete");
            });
            return;
        }
        
        RemoteFileItem item = items.get(index);
        
        if (item.isDirectory()) {
            // Skip directories for now - could implement recursive download
            downloadFiles(items, downloadPath, index + 1);
            return;
        }
        
        Path localFile = downloadPath.resolve(item.getName());
        
        currentService.downloadFile(item.getId(), localFile)
            .doOnSuccess(v -> {
                Platform.runLater(() -> {
                    view.getStatusLabel().setText(
                        String.format("Downloaded %d of %d", index + 1, items.size())
                    );
                });
                downloadFiles(items, downloadPath, index + 1);
            })
            .doOnError(error -> Platform.runLater(() -> {
                view.getProgressIndicator().setVisible(false);
                showError("Download Failed", "Failed to download " + item.getName() + ": " + error.getMessage());
            }))
            .subscribe();
    }

    public void handleUpload() {
        if (currentService == null) {
            showError("Error", "Not connected to any remote service");
            return;
        }
        
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Files to Upload");
        List<File> files = fileChooser.showOpenMultipleDialog(view.getView().getScene().getWindow());
        
        if (files != null && !files.isEmpty()) {
            view.getProgressIndicator().setVisible(true);
            view.getStatusLabel().setText("Uploading " + files.size() + " file(s)...");
            
            uploadFiles(files, 0);
        }
    }

    private void uploadFiles(List<File> files, int index) {
        if (index >= files.size()) {
            Platform.runLater(() -> {
                view.getProgressIndicator().setVisible(false);
                view.getStatusLabel().setText("Upload complete");
                refreshDirectory();
            });
            return;
        }
        
        File file = files.get(index);
        String remoteDestination = currentRemotePath != null && !currentRemotePath.equals("root")
            ? currentRemotePath + "/" + file.getName()
            : file.getName();
        
        currentService.uploadFile(file.toPath(), remoteDestination)
            .doOnSuccess(v -> {
                Platform.runLater(() -> {
                    view.getStatusLabel().setText(
                        String.format("Uploaded %d of %d", index + 1, files.size())
                    );
                });
                uploadFiles(files, index + 1);
            })
            .doOnError(error -> Platform.runLater(() -> {
                view.getProgressIndicator().setVisible(false);
                showError("Upload Failed", "Failed to upload " + file.getName() + ": " + error.getMessage());
            }))
            .subscribe();
    }

    public void handleDelete() {
        ObservableList<RemoteFileItem> selected = view.getFileTable().getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) return;
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Files");
        alert.setHeaderText("Delete " + selected.size() + " item(s) from remote storage?");
        alert.setContentText("This action cannot be undone.");
        
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            view.getProgressIndicator().setVisible(true);
            
            List<RemoteFileItem> itemsToDelete = new ArrayList<>(selected);
            deleteFiles(itemsToDelete, 0);
        }
    }

    private void deleteFiles(List<RemoteFileItem> items, int index) {
        if (index >= items.size()) {
            Platform.runLater(() -> {
                view.getProgressIndicator().setVisible(false);
                view.getStatusLabel().setText("Deletion complete");
                refreshDirectory();
            });
            return;
        }
        
        RemoteFileItem item = items.get(index);
        
        currentService.deleteFile(item.getId())
            .doOnSuccess(v -> {
                Platform.runLater(() -> {
                    view.getStatusLabel().setText(
                        String.format("Deleted %d of %d", index + 1, items.size())
                    );
                });
                deleteFiles(items, index + 1);
            })
            .doOnError(error -> Platform.runLater(() -> {
                view.getProgressIndicator().setVisible(false);
                showError("Delete Failed", "Failed to delete " + item.getName() + ": " + error.getMessage());
            }))
            .subscribe();
    }

    public void handleNewFolder() {
        if (currentService == null) {
            showError("Error", "Not connected to any remote service");
            return;
        }
        
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Folder");
        dialog.setHeaderText("Create a new folder");
        dialog.setContentText("Folder name:");
        
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            String remotePath = currentRemotePath != null && !currentRemotePath.equals("root")
                ? currentRemotePath + "/" + name
                : name;
            
            currentService.createDirectory(remotePath)
                .doOnSuccess(v -> Platform.runLater(() -> {
                    view.getStatusLabel().setText("Folder created");
                    refreshDirectory();
                }))
                .doOnError(error -> Platform.runLater(() -> 
                    showError("Error", "Failed to create folder: " + error.getMessage())))
                .subscribe();
        });
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public RemoteFileSystemService getCurrentService() {
        return currentService;
    }

    public boolean isConnected() {
        return currentService != null;
    }
}