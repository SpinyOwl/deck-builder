package com.spinyowl.cards.controller;

import com.spinyowl.cards.config.AppConfig;
import com.spinyowl.cards.config.ConfigService;
import com.spinyowl.cards.service.ProjectManager;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ProjectViewController {

    private final ConfigService configService;
    private final AppConfig appConfig;
    private final TreeView<Path> projectTree;
    private final StackPane projectTreeContainer;
    private final ToggleButton projectTreeToggle;
    private final BorderPane previewPane;
    private final SplitPane mainHorizontalSplit;
    private final Runnable expandConsole;

    private ChangeListener<Number> horizontalDividerListener;
    private SplitPane.Divider horizontalDivider;
    private ProjectManager projectManager;

    @Setter
    private PreviewController previewController;

    public ProjectViewController(TreeView<Path> projectTree,
                                 StackPane projectTreeContainer,
                                 ToggleButton projectTreeToggle,
                                 BorderPane previewPane,
                                 SplitPane mainHorizontalSplit,
                                 ConfigService configService,
                                 AppConfig appConfig,
                                 Runnable expandConsole) {
        this.projectTree = projectTree;
        this.projectTreeContainer = projectTreeContainer;
        this.projectTreeToggle = projectTreeToggle;
        this.previewPane = previewPane;
        this.mainHorizontalSplit = mainHorizontalSplit;
        this.configService = configService;
        this.appConfig = appConfig;
        this.expandConsole = expandConsole;
    }

    public void initialize() {
        if (projectTreeToggle != null) {
            projectTreeToggle.setSelected(appConfig.isProjectTreeVisible());
        }
    }

    public void applyInitialState() {
        updateProjectTreeVisibility(projectTreeToggle == null || projectTreeToggle.isSelected(), false);
    }

    public void onToggleProjectTree() {
        if (projectTreeToggle == null) {
            return;
        }
        updateProjectTreeVisibility(projectTreeToggle.isSelected(), true);
    }

    public void setProjectManager(ProjectManager projectManager) {
        this.projectManager = projectManager;
    }

    public void initProjectTree() {
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

    public void applyStoredDividerPositions() {
        if (mainHorizontalSplit == null) {
            return;
        }

        ObservableList<Node> items = mainHorizontalSplit.getItems();
        int itemCount = items.size();
        if (itemCount <= 1) {
            return;
        }

        boolean treeVisible = items.contains(projectTreeContainer);
        boolean previewVisible = items.contains(previewPane);

        if (treeVisible && previewVisible && itemCount >= 3) {
            double treePosition = clamp(appConfig.getProjectTreeDividerPosition());
            double previewPosition = clamp(appConfig.getPreviewDividerPosition());
            double minGap = 0.05;
            if (previewPosition <= treePosition + minGap) {
                previewPosition = clamp(treePosition + minGap);
            }
            mainHorizontalSplit.setDividerPositions(treePosition, previewPosition);
        } else if (treeVisible) {
            mainHorizontalSplit.setDividerPositions(clamp(appConfig.getProjectTreeDividerPosition()));
        } else if (previewVisible) {
            mainHorizontalSplit.setDividerPositions(clamp(appConfig.getPreviewDividerPosition()));
        }
    }

    public void ensureHorizontalDividerListener() {
        if (mainHorizontalSplit == null || mainHorizontalSplit.getDividers().isEmpty() || !appConfig.isProjectTreeVisible()) {
            removeHorizontalDividerListener();
            return;
        }

        SplitPane.Divider divider = mainHorizontalSplit.getDividers().get(0);
        if (divider == horizontalDivider && horizontalDividerListener != null) {
            return;
        }

        removeHorizontalDividerListener();

        horizontalDivider = divider;
        horizontalDividerListener = (obs, oldVal, newVal) -> {
            if (projectTreeToggle == null || projectTreeToggle.isSelected()) {
                double position = clamp(newVal.doubleValue());
                appConfig.setProjectTreeDividerPosition(position);
            }
        };
        divider.positionProperty().addListener(horizontalDividerListener);
    }

    public void removeHorizontalDividerListener() {
        if (horizontalDivider != null && horizontalDividerListener != null) {
            horizontalDivider.positionProperty().removeListener(horizontalDividerListener);
        }
        horizontalDivider = null;
        horizontalDividerListener = null;
    }

    private void updateProjectTreeVisibility(boolean show, boolean persist) {
        if (mainHorizontalSplit == null || projectTreeContainer == null) {
            return;
        }

        SplitPane.setResizableWithParent(projectTreeContainer, false);

        ObservableList<Node> items = mainHorizontalSplit.getItems();
        boolean currentlyVisible = items.contains(projectTreeContainer);

        if (show) {
            if (!currentlyVisible) {
                items.add(0, projectTreeContainer);
            }
            appConfig.setProjectTreeVisible(true);
            Platform.runLater(() -> {
                applyStoredDividerPositions();
                ensureHorizontalDividerListener();
                if (previewController != null) {
                    previewController.ensurePreviewDividerListener();
                }
            });
        } else if (currentlyVisible) {
            if (!mainHorizontalSplit.getDividers().isEmpty()) {
                double position = clamp(mainHorizontalSplit.getDividers().get(0).getPosition());
                appConfig.setProjectTreeDividerPosition(position);
            }
            items.remove(projectTreeContainer);
            removeHorizontalDividerListener();
            appConfig.setProjectTreeVisible(false);
            Platform.runLater(() -> {
                applyStoredDividerPositions();
                if (previewController != null) {
                    previewController.ensurePreviewDividerListener();
                }
            });
        } else {
            appConfig.setProjectTreeVisible(false);
        }

        if (persist) {
            configService.save();
        }
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
                log.warn("Failed to list directory {}", path, e);
            }
        }
        return item;
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
            log.error("Failed to open file {}", path, e);
            if (expandConsole != null) {
                expandConsole.run();
            }
        }
    }

    private double clamp(double pos) {
        if (pos < 0.0) return 0.0;
        if (pos > 1.0) return 1.0;
        return pos;
    }

    private Comparator<Path> directoryFirst() {
        return Comparator
            .comparing((Path p) -> !Files.isDirectory(p))
            .thenComparing(Path::getFileName, Comparator.nullsFirst(Comparator.naturalOrder()));
    }
}

