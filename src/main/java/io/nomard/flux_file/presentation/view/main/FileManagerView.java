package io.nomard.flux_file.presentation.view.main;

import io.nomard.flux_file.core.domain.model.FileItem;
import io.nomard.flux_file.presentation.controller.main.FileManagerController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Getter
public class FileManagerView {

    private final FileManagerController controller;
    private final BorderPane root;

    private TextField pathField;
    private TextField searchField;
    private TableView<FileItem> fileTable;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;
    private Button backButton;
    private Button refreshButton;

    @Autowired
    public FileManagerView(FileManagerController controller) {
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

        // Toolbar
        ToolBar toolBar = new ToolBar();
        backButton = new Button("◄ Back");
        Button forwardButton = new Button("► Forward");
        refreshButton = new Button("⟳ Refresh");
        Button newFolderButton = new Button("📁 New Folder");
        Button deleteButton = new Button("🗑 Delete");
        Button compressButton = new Button("📦 Compress");
        Button terminalButton = new Button("⌨ Terminal");
        Button cloudButton = new Button("☁ Cloud");

        toolBar.getItems().addAll(
                backButton,
                forwardButton,
                refreshButton,
                new Separator(),
                newFolderButton,
                deleteButton,
                compressButton,
                terminalButton,
                cloudButton
        );

        // Path and Search Bar
        HBox pathBar = new HBox(10);
        pathBar.setAlignment(Pos.CENTER_LEFT);
        pathBar.setPadding(new Insets(10));

        Label pathLabel = new Label("Path:");
        pathField = new TextField();
        HBox.setHgrow(pathField, Priority.ALWAYS);

        Label searchLabel = new Label("Search:");
        searchField = new TextField();
        searchField.setPrefWidth(200);

        pathBar.getChildren().addAll(pathLabel, pathField, searchLabel, searchField);

        topSection.getChildren().addAll(toolBar, pathBar);

        // Wire up button actions
        backButton.setOnAction(e -> controller.navigateBack());
        forwardButton.setOnAction(e -> controller.refreshDirectory());
        refreshButton.setOnAction(e -> controller.refreshDirectory());
        newFolderButton.setOnAction(e -> controller.handleNewFolder());
        deleteButton.setOnAction(e -> controller.handleDelete());
        compressButton.setOnAction(e -> controller.handleCompress());
        terminalButton.setOnAction(e -> controller.handleOpenTerminal());
        cloudButton.setOnAction(e -> controller.handleRemoteConnect());

        return topSection;
    }

    private VBox createCenterSection() {
        VBox centerSection = new VBox();
        VBox.setVgrow(centerSection, Priority.ALWAYS);

        fileTable = new TableView<>();
        VBox.setVgrow(fileTable, Priority.ALWAYS);

        // Create columns
        TableColumn<FileItem, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().name())
        );
        nameColumn.setPrefWidth(400);

        TableColumn<FileItem, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getType())
        );
        typeColumn.setPrefWidth(100);

        TableColumn<FileItem, String> sizeColumn = new TableColumn<>("Size");
        sizeColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFormattedSize())
        );
        sizeColumn.setPrefWidth(150);

        TableColumn<FileItem, String> modifiedColumn = new TableColumn<>("Modified");
        modifiedColumn.setCellValueFactory(cellData ->
                new javafx.beans.property.SimpleStringProperty(cellData.getValue().getFormattedDate())
        );
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
        controller.initialize();
    }

    public BorderPane getView() {
        return root;
    }
}