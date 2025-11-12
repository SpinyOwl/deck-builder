package com.spinyowl.cards.controller;

import com.spinyowl.cards.config.AppConfig;
import com.spinyowl.cards.config.ConfigService;
import com.spinyowl.cards.service.CardRenderer;
import com.spinyowl.cards.service.ProjectManager;
import com.spinyowl.cards.service.ProjectWatcher;
import com.spinyowl.cards.ui.WindowStateHandler;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class DeckBuilderController {

    @FXML private WebView webView;
    @FXML private Spinner<Integer> indexSpinner;
    @FXML private ComboBox<String> langBox;
    @FXML private TreeView<Path> projectTree;
    @FXML private TitledPane consolePane;
    @FXML private TextArea consoleTextArea;
    @FXML private SplitPane mainVerticalSplit;
    @FXML private SplitPane mainHorizontalSplit;
    @FXML private StackPane projectTreeContainer;
    @FXML private ToggleButton projectTreeToggle;
    @FXML private BorderPane previewPane;
    @FXML private BorderPane previewContainer;
    @FXML private ToggleButton previewToggle;
    @FXML private ToggleButton zoomFitToggle;

    private ProjectManager projectManager;
    private CardRenderer renderer;
    private ProjectWatcher projectWatcher;
    private final AtomicBoolean reloadScheduled = new AtomicBoolean();
    private final ConfigService configService = ConfigService.getInstance();
    private final AppConfig appConfig = configService.getConfig();

    private ProjectViewController projectViewController;
    private PreviewController previewController;
    private ConsoleLogController consoleLogController;

    @FXML
    public void initialize() {
        previewController = new PreviewController(webView, previewPane, previewContainer, previewToggle, zoomFitToggle, mainHorizontalSplit, configService, appConfig);
        consoleLogController = new ConsoleLogController(configService, appConfig, consolePane, consoleTextArea, mainVerticalSplit);
        projectViewController = new ProjectViewController(projectTree, projectTreeContainer, projectTreeToggle, previewPane, mainHorizontalSplit, configService, appConfig, this::expandConsole);

        previewController.setProjectViewController(projectViewController);
        projectViewController.setPreviewController(previewController);

        projectViewController.initialize();
        previewController.initialize();
        consoleLogController.initialize();

        Platform.runLater(() -> {
            consoleLogController.applyInitialState();
            projectViewController.applyInitialState();
            previewController.applyInitialState();
        });
    }

    public void setProject(ProjectManager pm) {
        this.projectManager = pm;
        this.renderer = new CardRenderer(pm);

        projectViewController.setProjectManager(pm);

        langBox.getItems().addAll("en");
        langBox.getSelectionModel().selectFirst();
        configureIndexSpinner();
        projectViewController.initProjectTree();
        refresh();
        startWatcher();
        consoleLogController.startLogUpdates();
        if (projectManager != null && projectManager.getProjectDir() != null) {
            log.info("Preview initialized for {}", projectManager.getProjectDir());
        }
    }

    @FXML
    public void onReload() {
        performReload();
        log.info("Manual template reload requested");
    }

    @FXML
    public void onShow() {
        refresh();
    }

    @FXML
    public void onCloseProject() {
        stopWatcher();
        consoleLogController.stopLogUpdates();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main/ui/startup.fxml"));
            Scene scene = new Scene(loader.load(), 400, 400);
            Stage stage = (Stage) webView.getScene().getWindow();
            WindowStateHandler.disable(stage);
            stage.setMaximized(false);
            stage.setScene(scene);
            stage.setResizable(false);
            stage.setWidth(400);
            stage.setHeight(400);
            stage.setTitle("SpinyOwl.DeckBuilder");
            stage.show();
            log.info("Project closed");
        } catch (IOException e) {
            log.error("Failed to close project view", e);
            expandConsole();
        }
    }

    @FXML
    private void onToggleProjectTree() {
        projectViewController.onToggleProjectTree();
    }

    @FXML
    private void onTogglePreview() {
        previewController.onTogglePreview();
    }

    @FXML
    private void onZoomIn() {
        previewController.zoomIn();
    }

    @FXML
    private void onZoomOut() {
        previewController.zoomOut();
    }

    @FXML
    private void onZoomReset() {
        previewController.zoomReset();
    }

    @FXML
    private void onZoomFit() {
        previewController.zoomFit();
    }

    private void refresh() {
        if (renderer == null) {
            return;
        }

        int cardCount = renderer.getCardCount();
        if (cardCount <= 0) {
            Platform.runLater(() -> webView.getEngine().loadContent("<p>No cards available.</p>"));
            return;
        }

        try {
            int idx = getSelectedCardIndex(cardCount);
            String html = renderer.renderCard(idx, langBox.getValue());
            Platform.runLater(() -> webView.getEngine().loadContent(html));
            log.info("Rendered card {} ({})", idx, langBox.getValue());
        } catch (Exception e) {
            log.error("Error rendering card", e);
            expandConsole();
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
        configureIndexSpinner();
        projectViewController.initProjectTree();
        refresh();
    }

    private void configureIndexSpinner() {
        if (indexSpinner == null) {
            return;
        }

        int cardCount = renderer != null ? renderer.getCardCount() : 0;
        int maxIndex = Math.max(cardCount - 1, 0);

        SpinnerValueFactory<Integer> valueFactory = indexSpinner.getValueFactory();
        SpinnerValueFactory.IntegerSpinnerValueFactory integerFactory;
        if (valueFactory instanceof SpinnerValueFactory.IntegerSpinnerValueFactory existingFactory) {
            integerFactory = existingFactory;
            integerFactory.setMin(0);
            integerFactory.setMax(maxIndex);
        } else {
            integerFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxIndex, 0);
            indexSpinner.setValueFactory(integerFactory);
        }

        if (cardCount > 0) {
            Integer value = integerFactory.getValue();
            if (value == null || value < 0 || value > maxIndex) {
                integerFactory.setValue(0);
            }
        } else {
            integerFactory.setValue(0);
        }

        indexSpinner.setDisable(cardCount <= 0);
    }

    private int getSelectedCardIndex(int cardCount) {
        if (indexSpinner == null) {
            return 0;
        }

        SpinnerValueFactory<Integer> valueFactory = indexSpinner.getValueFactory();
        if (valueFactory == null) {
            valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, cardCount - 1, 0);
            indexSpinner.setValueFactory(valueFactory);
        }

        Integer value = valueFactory.getValue();
        if (value == null) {
            value = 0;
            valueFactory.setValue(value);
        }

        int maxIndex = Math.max(0, cardCount - 1);
        int clampedValue = Math.max(0, Math.min(value, maxIndex));
        if (clampedValue != value) {
            valueFactory.setValue(clampedValue);
        }
        return clampedValue;
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

    private void expandConsole() {
        if (consoleLogController != null) {
            consoleLogController.expandConsole();
        }
    }
}

