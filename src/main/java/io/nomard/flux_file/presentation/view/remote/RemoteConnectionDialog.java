package io.nomard.flux_file.presentation.view.remote;

import io.nomard.flux_file.infrastructure.service.remote.RemoteConnectionManager;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

@Component
public class RemoteConnectionDialog {

    @Autowired
    private RemoteConnectionManager connectionManager;

    public Optional<RemoteConnection> showConnectionDialog() {
        Dialog<RemoteConnection> dialog = new Dialog<>();
        dialog.setTitle("Connect to Remote File System");
        dialog.setHeaderText("Choose a service and provide credentials");

        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        ComboBox<String> serviceCombo = new ComboBox<>();
        serviceCombo.getItems().addAll("Google Drive", "Microsoft OneDrive", "Dropbox", "SFTP/SSH");
        serviceCombo.setValue("Google Drive");

        TextField nameField = new TextField();
        nameField.setPromptText("Connection name");

        VBox credentialsBox = new VBox(10);
        
        // Google Drive fields
        VBox googleDriveBox = new VBox(10);
        Label googleLabel = new Label("Google Drive OAuth:");
        TextField googleClientId = new TextField();
        googleClientId.setPromptText("Client ID");
        TextField googleClientSecret = new TextField();
        googleClientSecret.setPromptText("Client Secret");
        Button googleAuthButton = new Button("Upload credentials.json");
        TextField googleCredsPath = new TextField();
        googleCredsPath.setEditable(false);
        googleAuthButton.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Google Drive Credentials JSON");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("JSON Files", "*.json")
            );
            File file = fileChooser.showOpenDialog(dialog.getOwner());
            if (file != null) {
                googleCredsPath.setText(file.getAbsolutePath());
            }
        });
        googleDriveBox.getChildren().addAll(googleLabel, googleAuthButton, googleCredsPath);

        // OneDrive fields
        VBox oneDriveBox = new VBox(10);
        Label oneDriveLabel = new Label("OneDrive OAuth:");
        TextField oneDriveClientId = new TextField();
        oneDriveClientId.setPromptText("Application (client) ID");
        Hyperlink oneDriveHelp = new Hyperlink("Get OneDrive App ID");
        oneDriveHelp.setOnAction(e -> {
            // Open Azure portal registration page
            try {
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://portal.azure.com/#blade/Microsoft_AAD_RegisteredApps/ApplicationsListBlade")
                );
            } catch (Exception ex) {
                // Ignore
            }
        });
        oneDriveBox.getChildren().addAll(oneDriveLabel, oneDriveClientId, oneDriveHelp);

        // Dropbox fields
        VBox dropboxBox = new VBox(10);
        Label dropboxLabel = new Label("Dropbox Access Token:");
        TextField dropboxToken = new TextField();
        dropboxToken.setPromptText("Access Token");
        Hyperlink dropboxHelp = new Hyperlink("Generate Dropbox Token");
        dropboxHelp.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(
                    new java.net.URI("https://www.dropbox.com/developers/apps")
                );
            } catch (Exception ex) {
                // Ignore
            }
        });
        dropboxBox.getChildren().addAll(dropboxLabel, dropboxToken, dropboxHelp);

        // SFTP fields
        VBox sftpBox = new VBox(10);
        Label sftpLabel = new Label("SFTP Connection:");
        TextField sftpHost = new TextField();
        sftpHost.setPromptText("Host (e.g., example.com)");
        TextField sftpPort = new TextField("22");
        sftpPort.setPromptText("Port");
        TextField sftpUsername = new TextField();
        sftpUsername.setPromptText("Username");
        PasswordField sftpPassword = new PasswordField();
        sftpPassword.setPromptText("Password (optional if using SSH key)");
        Label sftpNote = new Label("Note: Will use ~/.ssh/id_rsa if password is empty");
        sftpNote.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
        sftpBox.getChildren().addAll(sftpLabel, sftpHost, sftpPort, sftpUsername, sftpPassword, sftpNote);

        credentialsBox.getChildren().add(googleDriveBox);

        serviceCombo.setOnAction(e -> {
            credentialsBox.getChildren().clear();
            String selected = serviceCombo.getValue();
            switch (selected) {
                case "Google Drive":
                    credentialsBox.getChildren().add(googleDriveBox);
                    break;
                case "Microsoft OneDrive":
                    credentialsBox.getChildren().add(oneDriveBox);
                    break;
                case "Dropbox":
                    credentialsBox.getChildren().add(dropboxBox);
                    break;
                case "SFTP/SSH":
                    credentialsBox.getChildren().add(sftpBox);
                    break;
            }
        });

        int row = 0;
        grid.add(new Label("Service:"), 0, row);
        grid.add(serviceCombo, 1, row++);
        
        grid.add(new Label("Name:"), 0, row);
        grid.add(nameField, 1, row++);
        
        grid.add(credentialsBox, 0, row++, 2, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == connectButtonType) {
                String service = serviceCombo.getValue();
                String name = nameField.getText();
                String credentials = "";

                switch (service) {
                    case "Google Drive":
                        String credsPath = googleCredsPath.getText();
                        if (credsPath != null && !credsPath.isEmpty()) {
                            try {
                                credentials = Files.readString(new File(credsPath).toPath());
                            } catch (Exception e) {
                                showError("Error", "Failed to read credentials file");
                                return null;
                            }
                        }
                        break;
                    case "Microsoft OneDrive":
                        credentials = oneDriveClientId.getText();
                        break;
                    case "Dropbox":
                        credentials = dropboxToken.getText();
                        break;
                    case "SFTP/SSH":
                        String host = sftpHost.getText();
                        String port = sftpPort.getText();
                        String username = sftpUsername.getText();
                        String password = sftpPassword.getText();
                        
                        if (password.isEmpty()) {
                            credentials = username + "@" + host + ":" + port;
                        } else {
                            credentials = username + ":" + password + "@" + host + ":" + port;
                        }
                        break;
                }

                return new RemoteConnection(name, service, credentials);
            }
            return null;
        });

        return dialog.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static class RemoteConnection {
        private final String name;
        private final String service;
        private final String credentials;

        public RemoteConnection(String name, String service, String credentials) {
            this.name = name;
            this.service = service;
            this.credentials = credentials;
        }

        public String getName() {
            return name;
        }

        public String getService() {
            return service;
        }

        public String getCredentials() {
            return credentials;
        }
    }
}