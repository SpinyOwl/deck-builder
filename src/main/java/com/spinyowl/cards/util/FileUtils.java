package com.spinyowl.cards.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility helpers for working with files throughout the application.
 */
public final class FileUtils {

    private FileUtils() {
        // Utility class
    }

    /**
     * Checks that the provided path points to an existing, readable regular file.
     *
     * @param file        the file path to validate
     * @param description human-friendly description used in error messages
     * @return the same {@link Path} instance if validation succeeds
     * @throws IOException if the path is null, does not exist, or is not a readable file
     */
    public static Path requireFile(Path file, String description) throws IOException {
        if (file == null) {
            throw new IOException(description + " path is not set");
        }
        if (!Files.exists(file)) {
            throw new IOException(description + " not found: " + file);
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException(description + " is not a regular file: " + file);
        }
        if (!Files.isReadable(file)) {
            throw new IOException(description + " is not readable: " + file);
        }
        return file;
    }

    /**
     * Opens a new {@link InputStream} for the given file after validating that it exists.
     *
     * @param file        the file to open
     * @param description description of the file used for error messages
     * @return an open {@link InputStream}; the caller is responsible for closing it
     * @throws IOException if the file does not exist or cannot be opened
     */
    public static InputStream newInputStream(Path file, String description) throws IOException {
        requireFile(file, description);
        return Files.newInputStream(file);
    }

    /**
     * Opens a classpath resource, ensuring it exists.
     *
     * @param owner        class used to resolve the resource
     * @param resourcePath path to the resource relative to the classpath root
     * @return an open {@link InputStream}; the caller must close it
     * @throws IOException if the resource does not exist
     */
    public static InputStream openResource(Class<?> owner, String resourcePath) throws IOException {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IOException("Resource path is not set");
        }
        ClassLoader classLoader = owner == null ? FileUtils.class.getClassLoader() : owner.getClassLoader();
        InputStream in = classLoader.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IOException("Resource not found: " + resourcePath);
        }
        return in;
    }

    /**
     * Determines if the given path refers to an existing, readable regular file.
     *
     * @param file path to validate
     * @return {@code true} if the path is a readable regular file; {@code false} otherwise
     */
    public static boolean isReadableFile(Path file) {
        return file != null && Files.exists(file) && Files.isRegularFile(file) && Files.isReadable(file);
    }
}
