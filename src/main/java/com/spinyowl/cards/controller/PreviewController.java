package com.spinyowl.cards.controller;

import com.spinyowl.cards.config.AppConfig;
import com.spinyowl.cards.config.ConfigService;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebView;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PreviewController {

    private static final double DEFAULT_ZOOM = 1.0;
    private static final double MIN_ZOOM = 0.25;
    private static final double MAX_ZOOM = 3.0;
    private static final double ZOOM_STEP = 0.1;

    private final ConfigService configService;
    private final AppConfig appConfig;
    private final WebView webView;
    private final BorderPane previewPane;
    private final BorderPane previewContainer;
    private final ToggleButton previewToggle;
    private final ToggleButton zoomFitToggle;
    private final SplitPane mainHorizontalSplit;

    private ChangeListener<Number> previewDividerListener;
    private SplitPane.Divider previewDivider;
    private double currentZoom = DEFAULT_ZOOM;
    private final ChangeListener<Number> previewResizeListener = (obs, oldVal, newVal) -> {
        if (isFitToContainerActive()) {
            fitPreviewToContainer();
        }
    };

    @Setter
    private ProjectViewController projectViewController;

    public PreviewController(WebView webView,
                             BorderPane previewPane,
                             BorderPane previewContainer,
                             ToggleButton previewToggle,
                             ToggleButton zoomFitToggle,
                             SplitPane mainHorizontalSplit,
                             ConfigService configService,
                             AppConfig appConfig) {
        this.webView = webView;
        this.previewPane = previewPane;
        this.previewContainer = previewContainer;
        this.previewToggle = previewToggle;
        this.zoomFitToggle = zoomFitToggle;
        this.mainHorizontalSplit = mainHorizontalSplit;
        this.configService = configService;
        this.appConfig = appConfig;
    }

    public void initialize() {
        if (previewToggle != null) {
            previewToggle.setSelected(appConfig.isPreviewVisible());
        }

        if (previewPane != null) {
            previewPane.widthProperty().addListener(previewResizeListener);
            previewPane.heightProperty().addListener(previewResizeListener);
        }
        if (previewContainer != null) {
            previewContainer.widthProperty().addListener(previewResizeListener);
            previewContainer.heightProperty().addListener(previewResizeListener);
        }
    }

    public void applyInitialState() {
        updatePreviewVisibility(previewToggle == null || previewToggle.isSelected(), false);
    }

    public void onTogglePreview() {
        if (previewToggle == null) {
            return;
        }
        updatePreviewVisibility(previewToggle.isSelected(), true);
    }

    public void zoomIn() {
        disableFitToContainer();
        adjustZoom(ZOOM_STEP);
    }

    public void zoomOut() {
        disableFitToContainer();
        adjustZoom(-ZOOM_STEP);
    }

    public void zoomReset() {
        disableFitToContainer();
        applyZoom(DEFAULT_ZOOM);
    }

    public void zoomFit() {
        if (zoomFitToggle != null && !zoomFitToggle.isSelected()) {
            disableFitToContainer();
            return;
        }

        fitPreviewToContainer();
    }

    public void ensurePreviewDividerListener() {
        if (mainHorizontalSplit == null || previewPane == null) {
            removePreviewDividerListener();
            return;
        }

        if (!mainHorizontalSplit.getItems().contains(previewPane)) {
            removePreviewDividerListener();
            return;
        }

        if (mainHorizontalSplit.getDividers().isEmpty()) {
            removePreviewDividerListener();
            return;
        }

        SplitPane.Divider divider = mainHorizontalSplit.getDividers().get(mainHorizontalSplit.getDividers().size() - 1);
        if (divider == previewDivider && previewDividerListener != null) {
            return;
        }

        removePreviewDividerListener();

        previewDivider = divider;
        previewDividerListener = (obs, oldVal, newVal) -> {
            if (previewToggle == null || previewToggle.isSelected()) {
                double position = clamp(newVal.doubleValue());
                appConfig.setPreviewDividerPosition(position);
            }
        };
        divider.positionProperty().addListener(previewDividerListener);
    }

    public void removePreviewDividerListener() {
        if (previewDivider != null && previewDividerListener != null) {
            previewDivider.positionProperty().removeListener(previewDividerListener);
        }
        previewDivider = null;
        previewDividerListener = null;
    }

    private void updatePreviewVisibility(boolean show, boolean persist) {
        if (mainHorizontalSplit == null || previewPane == null) {
            return;
        }

        SplitPane.setResizableWithParent(previewPane, false);

        var items = mainHorizontalSplit.getItems();
        boolean currentlyVisible = items.contains(previewPane);

        if (show) {
            if (!currentlyVisible) {
                items.add(previewPane);
            }
            appConfig.setPreviewVisible(true);
            Platform.runLater(() -> {
                if (projectViewController != null) {
                    projectViewController.applyStoredDividerPositions();
                    projectViewController.ensureHorizontalDividerListener();
                }
                ensurePreviewDividerListener();
            });
        } else if (currentlyVisible) {
            var dividers = mainHorizontalSplit.getDividers();
            if (!dividers.isEmpty()) {
                SplitPane.Divider divider = dividers.get(dividers.size() - 1);
                double position = clamp(divider.getPosition());
                appConfig.setPreviewDividerPosition(position);
            }
            items.remove(previewPane);
            removePreviewDividerListener();
            appConfig.setPreviewVisible(false);
            Platform.runLater(() -> {
                if (projectViewController != null) {
                    projectViewController.applyStoredDividerPositions();
                    projectViewController.ensureHorizontalDividerListener();
                }
            });
        } else {
            appConfig.setPreviewVisible(false);
        }

        if (persist) {
            configService.save();
        }
    }

    private void adjustZoom(double delta) {
        applyZoom(currentZoom + delta);
    }

    private void applyZoom(double zoom) {
        if (webView == null) {
            return;
        }
        double clamped = clampZoom(zoom);
        currentZoom = clamped;
        webView.setZoom(clamped);
    }

    private void fitPreviewToContainer() {
        if (webView == null || previewContainer == null || !isFitToContainerActive()) {
            return;
        }

        Platform.runLater(() -> {
            if (!isFitToContainerActive()) {
                return;
            }
            double containerWidth = previewContainer.getWidth();
            double containerHeight = previewContainer.getHeight();
            if (containerWidth <= 0 || containerHeight <= 0) {
                return;
            }

            try {
                Object widthObj = webView.getEngine().executeScript(
                        "Math.max(document.body ? document.body.scrollWidth : 0, document.documentElement ? document.documentElement.scrollWidth : 0)"
                );
                Object heightObj = webView.getEngine().executeScript(
                        "Math.max(document.body ? document.body.scrollHeight : 0, document.documentElement ? document.documentElement.scrollHeight : 0)"
                );

                double contentWidth = toDouble(widthObj);
                double contentHeight = toDouble(heightObj);

                if (contentWidth <= 0 || contentHeight <= 0) {
                    applyZoom(DEFAULT_ZOOM);
                    return;
                }

                double targetZoom = Math.min(containerWidth / contentWidth, containerHeight / contentHeight);

                log.info("Zooming on autofit: {},{},{},{},{}", containerWidth, containerHeight, contentWidth, contentHeight, targetZoom);
                if (Double.isFinite(targetZoom) && targetZoom > 0) {
                    applyZoom(targetZoom);
                }
            } catch (RuntimeException ex) {
                log.warn("Failed to fit preview to container", ex);
                applyZoom(DEFAULT_ZOOM);
            }
        });
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private double clamp(double pos) {
        if (pos < 0.0) return 0.0;
        if (pos > 1.0) return 1.0;
        return pos;
    }

    private double clampZoom(double zoom) {
        if (zoom < MIN_ZOOM) return MIN_ZOOM;
        if (zoom > MAX_ZOOM) return MAX_ZOOM;
        return zoom;
    }

    private void disableFitToContainer() {
        if (zoomFitToggle != null) {
            zoomFitToggle.setSelected(false);
        }
    }

    private boolean isFitToContainerActive() {
        return zoomFitToggle == null || zoomFitToggle.isSelected();
    }
}

