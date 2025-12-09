package io.nomard.flux_file.presentation.view.remote;

import io.nomard.flux_file.core.domain.model.RemoteFileItem;
import io.nomard.flux_file.presentation.controller.remote.RemoteBrowserController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RemoteBrowserView {

    private final RemoteBrowserController controller;
    private final BorderPane root;
    
    private Label connectionLabel;
    private TextField pathField;
    private TableView<RemoteFileItem> fileTable;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private Button downloadButton;
    private Button uploadButton;
    private Button deleteButton;

    @Autowired
    public RemoteBrowserView(RemoteBrowserController controller) {
        this.controller = controller;
        this.root = new BorderPane();
        buildUI();
        wireController();
    }

    private void buildUI() {
        root.setTop(createTopSection());
        root.setCenter(createCenterSection());
        root.setBottom(createBottomSection());
    }

    private VBox createTopSection() {
        VBox topSection = new VBox();
        
        // Connection info bar
        HBox connectionBar = new HBox(10);
        connectionBar.setAlignment(Pos.CENTER_LEFT);
        connectionBar.setPadding(new Insets(10));
        connectionBar.setStyle("-fx-background-color: #e3f2fd;");
        
        connectionLabel = new Label("Not connected");
        connectionLabel.setStyle("-fx-font-weight: bold;");
        Button disconnectButton = new Button("Disconnect");
        disconnectButton.setOnAction(e -> controller.disconnect());
        
        connectionBar.getChildren().addAll(new Label("Connected to:"), connectionLabel, disconnectButton);
        
        // Toolbar
        ToolBar toolBar = new ToolBar();
        Button backButton = new Button("◄ Back");
        Button refreshButton = new Button("⟳ Refresh");
        downloadButton = new Button("⬇ Download");
        uploadButton = new Button("⬆ Upload");
        Button newFolderButton = new Button("📁 New Folder");
        deleteButton = new Button("🗑 Delete");
        
        toolBar.getItems().addAll(
            backButton,
            refreshButton,
            new Separator(),
            downloadButton,
            uploadButton,
            newFolderButton,
            deleteButton
        );
        
        // Path bar
        HBox pathBar = new HBox(10);
        pathBar.setAlignment(Pos.CENTER_LEFT);
        pathBar.setPadding(new Insets(10));
        
        Label pathLabel = new Label("Path:");
        pathField = new TextField();
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathField.setEditable(false);
        
        pathBar.getChildren().addAll(pathLabel, pathField);
        
        topSection.getChildren().addAll(connectionBar, toolBar, pathBar);
        
        // Wire up button actions
        backButton.setOnAction(e -> controller.navigateBack());
        refreshButton.setOnAction(e -> controller.refreshDirectory());
        downloadButton.setOnAction(e -> controller.handleDownload());
        uploadButton.setOnAction(e -> controller.handleUpload());
        newFolderButton.setOnAction(e -> controller.handleNewFolder());
        deleteButton.setOnAction(e -> controller.handleDelete());
        
        return topSection;
    }

    private VBox createCenterSection() {
        VBox centerSection = new VBox();
        VBox.setVgrow(centerSection, Priority.ALWAYS);
        
        fileTable = new TableView<>();
        VBox.setVgrow(fileTable, Priority.ALWAYS);
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Create columns
        TableColumn<RemoteFileItem, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameColumn.setPrefWidth(400);
        
        TableColumn<RemoteFileItem, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeColumn.setPrefWidth(100);
        
        TableColumn<RemoteFileItem, String> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(new PropertyValueFactory<>("formattedSize"));
        sizeColumn.setPrefWidth(150);
        
        TableColumn<RemoteFileItem, String> modifiedColumn = new TableColumn<>("Modified");
        modifiedColumn.setCellValueFactory(new PropertyValueFactory<>("formattedDate"));
        modifiedColumn.setPrefWidth(200);
        
        fileTable.getColumns().addAll(nameColumn, typeColumn, sizeColumn, modifiedColumn);
        fileTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        centerSection.getChildren().add(fileTable);
        
        return centerSection;
    }

    private HBox createBottomSection() {
        HBox bottomSection = new HBox(10);
        bottomSection.setAlignment(Pos.CENTER_LEFT);
        bottomSection.setPadding(new Insets(5));
        bottomSection.setStyle("-fx-background-color: #f0f0f0;");
        
        statusLabel = new Label("Ready");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(20, 20);
        progressIndicator.setVisible(false);
        
        bottomSection.getChildren().addAll(statusLabel, spacer, progressIndicator);
        
        return bottomSection;
    }

    private void wireController() {
        controller.setView(this);
    }

    public BorderPane getView() {
        return root;
    }

    public Label getConnectionLabel() {
        return connectionLabel;
    }

    public TextField getPathField() {
        return pathField;
    }

    public TableView<RemoteFileItem> getFileTable() {
        return fileTable;
    }

    public Label getStatusLabel() {
        return statusLabel;
    }

    public ProgressIndicator getProgressIndicator() {
        return progressIndicator;
    }

    public Button getDownloadButton() {
        return downloadButton;
    }

    public Button getUploadButton() {
        return uploadButton;
    }

    public Button getDeleteButton() {
        return deleteButton;
    }
}