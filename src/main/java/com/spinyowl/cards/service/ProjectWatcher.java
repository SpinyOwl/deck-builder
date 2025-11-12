package com.spinyowl.cards.service;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

@Slf4j
public class ProjectWatcher implements AutoCloseable {

    private final Path root;
    private final WatchService watchService;
    private final ExecutorService executorService;
    private final Consumer<Path> callback;
    private final Set<Path> registeredDirectories = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ProjectWatcher(Path root, Consumer<Path> callback) throws IOException {
        this.root = root;
        this.callback = callback;
        this.watchService = FileSystems.getDefault().newWatchService();
        this.executorService = Executors.newSingleThreadExecutor(new WatcherThreadFactory());
        registerAll(root);
        startWatching();
        log.info("Started watching project directory {}", root);
    }

    private void startWatching() {
        executorService.submit(() -> {
            while (running.get()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException e) {
                    break;
                }

                Path dir = (Path) key.watchable();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }

                    Path name = (Path) event.context();
                    Path child = dir.resolve(name);

                    if (kind == ENTRY_CREATE && Files.isDirectory(child)) {
                        try {
                            registerAll(child);
                        } catch (IOException e) {
                            log.warn("Failed to register new directory {}", child, e);
                        }
                    }

                    try {
                        callback.accept(child);
                    } catch (Exception e) {
                        log.error("Error processing change notification for {}", child, e);
                    }
                }

                boolean valid = key.reset();
                if (!valid) {
                    registeredDirectories.remove(dir);
                }
            }
        });
    }

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerDirectory(Path dir) throws IOException {
        if (registeredDirectories.contains(dir)) {
            return;
        }
        dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        registeredDirectories.add(dir);
    }

    @Override
    public void close() throws IOException {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        watchService.close();
        executorService.shutdownNow();
        log.info("Stopped watching project directory {}", root);
    }

    private static class WatcherThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "project-watcher");
            thread.setDaemon(true);
            return thread;
        }
    }
}
