package com.spinyowl.cards.controller;

import com.spinyowl.cards.service.CardRenderer;
import com.spinyowl.cards.service.ProjectManager;
import com.spinyowl.cards.service.ProjectWatcher;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;

@Slf4j
public class PreviewController {

    @FXML private WebView webView;
    @FXML private TextField indexField;
    @FXML private ComboBox<String> langBox;
    @FXML private TreeView<Path> projectTree;

    private ProjectManager projectManager;
    private CardRenderer renderer;
    private ProjectWatcher projectWatcher;
    private final AtomicBoolean reloadScheduled = new AtomicBoolean();

    public void setProject(ProjectManager pm) {
        this.projectManager = pm;
        this.renderer = new CardRenderer(pm);

        langBox.getItems().addAll("en");
        langBox.getSelectionModel().selectFirst();
        indexField.setText("0");
        initProjectTree();
        refresh();
        startWatcher();
    }

    @FXML
    public void onReload() {
        performReload();
    }

    @FXML
    public void onShow() {
        refresh();
    }

    @FXML
    public void onCloseProject() {
        stopWatcher();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/startup.fxml"));
            Scene scene = new Scene(loader.load(), 800, 600);
            Stage stage = (Stage) webView.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("SpinyOwl.DeckBuilder");
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void refresh() {
        try {
            int idx = Integer.parseInt(indexField.getText().trim());
            String html = renderer.renderCard(idx, langBox.getValue());
            Platform.runLater(() -> webView.getEngine().loadContent(html));
        } catch (Exception e) {
            e.printStackTrace();
            webView.getEngine().loadContent("<p>Error rendering card.</p>");
        }
    }

    private void performReload() {
        if (projectManager == null || renderer == null) {
            return;
        }

        try {
            projectManager.reloadProject();
        } catch (IOException e) {
            log.error("Failed to reload project configuration", e);
        }

        renderer.reload();
        initProjectTree();
        refresh();
    }

    private void initProjectTree() {
        if (projectManager == null || projectManager.getProjectDir() == null) {
            return;
        }

        Path rootPath = projectManager.getProjectDir();
        TreeItem<Path> rootItem = createTreeItem(rootPath);
        rootItem.setExpanded(true);
        projectTree.setRoot(rootItem);
        projectTree.setShowRoot(true);
        projectTree.setCellFactory(treeView -> createPathTreeCell());
    }

    private TreeItem<Path> createTreeItem(Path path) {
        TreeItem<Path> item = new TreeItem<>(path);
        if (Files.isDirectory(path)) {
            try {
                List<Path> children;
                try (var stream = Files.list(path)) {
                    children = stream
                            .sorted(directoryFirst())
                            .toList();
                }
                for (Path child : children) {
                    item.getChildren().add(createTreeItem(child));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return item;
    }

    private void startWatcher() {
        stopWatcher();
        if (projectManager == null || projectManager.getProjectDir() == null) {
            return;
        }

        try {
            projectWatcher = new ProjectWatcher(projectManager.getProjectDir(), path -> scheduleReload());
        } catch (IOException e) {
            log.warn("Unable to start project watcher", e);
        }
    }

    private void stopWatcher() {
        if (projectWatcher != null) {
            try {
                projectWatcher.close();
            } catch (IOException e) {
                log.warn("Failed to stop project watcher", e);
            }
            projectWatcher = null;
        }
    }

    private void scheduleReload() {
        if (!reloadScheduled.compareAndSet(false, true)) {
            return;
        }

        Platform.runLater(() -> {
            try {
                performReload();
            } finally {
                reloadScheduled.set(false);
            }
        });
    }

    private Comparator<Path> directoryFirst() {
        return Comparator
                .comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(Path::getFileName, Comparator.nullsFirst(Comparator.naturalOrder()));
    }

    private TreeCell<Path> createPathTreeCell() {
        TreeCell<Path> cell = new TreeCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Path fileName = item.getFileName();
                    setText(fileName != null ? fileName.toString() : item.toString());
                }
            }
        };

        ContextMenu contextMenu = new ContextMenu();
        MenuItem newFileItem = new MenuItem("New File…");
        MenuItem newFolderItem = new MenuItem("New Folder…");
        contextMenu.getItems().addAll(newFileItem, newFolderItem);

        newFileItem.setOnAction(event -> handleCreateEntry(cell, false));
        newFolderItem.setOnAction(event -> handleCreateEntry(cell, true));

        cell.emptyProperty().addListener((obs, wasEmpty, isEmpty) -> {
            if (isEmpty) {
                cell.setContextMenu(null);
            } else {
                cell.setContextMenu(contextMenu);
            }
        });

        cell.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && !cell.isEmpty()) {
                Path path = cell.getItem();
                if (Files.isDirectory(path)) {
                    TreeItem<Path> treeItem = cell.getTreeItem();
                    treeItem.setExpanded(!treeItem.isExpanded());
                } else {
                    openInDesktop(path);
                }
            }
        });

        return cell;
    }

    private void handleCreateEntry(TreeCell<Path> cell, boolean directory) {
        Path selected = cell.getItem();
        if (selected == null) {
            return;
        }

        TreeItem<Path> treeItem = cell.getTreeItem();
        TreeItem<Path> targetItem = Files.isDirectory(selected) ? treeItem : treeItem.getParent();
        Path targetDir = Files.isDirectory(selected) ? selected : selected.getParent();

        if (targetItem == null) {
            targetItem = treeItem;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(directory ? "Create Folder" : "Create File");
        dialog.setHeaderText(null);
        dialog.setContentText("Enter " + (directory ? "folder" : "file") + " name:");
        if (projectTree.getScene() != null) {
            dialog.initOwner(projectTree.getScene().getWindow());
        }

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }

        String name = result.get().trim();
        if (name.isEmpty()) {
            return;
        }

        if (name.contains("/") || name.contains("\\") || name.contains(":")) {
            showErrorAlert("Invalid Name", "Name must not contain path separators or colon characters.");
            return;
        }

        if (targetDir == null) {
            showErrorAlert("Invalid Selection", "Unable to determine target directory for new entry.");
            return;
        }

        Path newPath = targetDir.resolve(name);

        try {
            if (directory) {
                Files.createDirectories(newPath);
            } else {
                Files.createFile(newPath);
            }
            addChildToTree(targetItem, newPath);
        } catch (FileAlreadyExistsException e) {
            showErrorAlert("Already Exists", "An entry named '" + name + "' already exists.");
        } catch (IOException e) {
            showErrorAlert("Creation Failed", "Unable to create " + (directory ? "folder" : "file") + ".\n" + e.getMessage());
        }
    }

    private void addChildToTree(TreeItem<Path> parent, Path childPath) {
        if (parent == null) {
            initProjectTree();
            return;
        }

        TreeItem<Path> newChild = createTreeItem(childPath);
        var children = parent.getChildren();
        Comparator<TreeItem<Path>> comparator = Comparator.comparing(TreeItem<Path>::getValue, directoryFirst());

        int index = 0;
        while (index < children.size() && comparator.compare(newChild, children.get(index)) > 0) {
            index++;
        }
        children.add(index, newChild);
        parent.setExpanded(true);
    }

    private void showErrorAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        if (projectTree.getScene() != null) {
            alert.initOwner(projectTree.getScene().getWindow());
        }
        alert.showAndWait();
    }

    private void openInDesktop(Path path) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.OPEN)) {
            return;
        }

        try {
            desktop.open(path.toFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
