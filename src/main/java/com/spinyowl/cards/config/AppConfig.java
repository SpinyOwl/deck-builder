package com.spinyowl.cards.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents persisted application preferences.
 */
public class AppConfig {
    private final List<String> recentProjects = new ArrayList<>();
    @Setter
    @Getter
    private String lastProjectsParent;
    @Setter
    @Getter
    private double windowWidth = 800;
    @Setter
    @Getter
    private double windowHeight = 600;
    @Setter
    @Getter
    private Double windowX;
    @Setter
    @Getter
    private Double windowY;
    @Setter
    @Getter
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
        recentProjects.addFirst(projectPath);
        while (recentProjects.size() > maxEntries) {
            recentProjects.removeLast();
        }
    }

    public void removeRecentProject(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return;
        }
        recentProjects.remove(projectPath);
    }

}
