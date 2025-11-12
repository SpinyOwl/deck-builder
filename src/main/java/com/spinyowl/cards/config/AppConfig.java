package com.spinyowl.cards.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents persisted application preferences.
 */
public class AppConfig {
    private final List<String> recentProjects = new ArrayList<>();
    private String lastProjectsParent;
    private double windowWidth = 800;
    private double windowHeight = 600;
    private Double windowX;
    private Double windowY;
    private boolean windowMaximized;

    public List<String> getRecentProjects() {
        return Collections.unmodifiableList(recentProjects);
    }

    public void setRecentProjects(List<String> projects) {
        recentProjects.clear();
        if (projects != null) {
            recentProjects.addAll(projects);
        }
    }

    public void addRecentProject(String projectPath, int maxEntries) {
        if (projectPath == null || projectPath.isBlank()) {
            return;
        }
        recentProjects.remove(projectPath);
        recentProjects.add(0, projectPath);
        while (recentProjects.size() > maxEntries) {
            recentProjects.remove(recentProjects.size() - 1);
        }
    }

    public String getLastProjectsParent() {
        return lastProjectsParent;
    }

    public void setLastProjectsParent(String lastProjectsParent) {
        this.lastProjectsParent = lastProjectsParent;
    }

    public double getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(double windowWidth) {
        this.windowWidth = windowWidth;
    }

    public double getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(double windowHeight) {
        this.windowHeight = windowHeight;
    }

    public Double getWindowX() {
        return windowX;
    }

    public void setWindowX(Double windowX) {
        this.windowX = windowX;
    }

    public Double getWindowY() {
        return windowY;
    }

    public void setWindowY(Double windowY) {
        this.windowY = windowY;
    }

    public boolean isWindowMaximized() {
        return windowMaximized;
    }

    public void setWindowMaximized(boolean windowMaximized) {
        this.windowMaximized = windowMaximized;
    }
}
