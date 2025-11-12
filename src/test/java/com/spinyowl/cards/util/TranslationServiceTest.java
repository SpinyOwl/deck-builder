package com.spinyowl.cards.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TranslationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsTranslationForRequestedLanguage() throws IOException {
        Files.writeString(tempDir.resolve("en.yml"), "greeting: Hello\n");

        TranslationService service = new TranslationService(tempDir);

        assertEquals("Hello", service.get("en", "greeting", "en"));
    }

    @Test
    void fallsBackToBaseLanguageWhenRegionalVariantMissing() throws IOException {
        Files.writeString(tempDir.resolve("en.yml"), "greeting: Hello\n");

        TranslationService service = new TranslationService(tempDir);

        assertEquals("Hello", service.get("en-US", "greeting", "en"));
    }

    @Test
    void fallsBackToDefaultLanguageWhenSpecificLanguageMissing() throws IOException {
        Files.writeString(tempDir.resolve("de.yml"), "greeting: Hallo\n");

        TranslationService service = new TranslationService(tempDir);

        assertEquals("Hallo", service.get("fr", "greeting", "de"));
    }

    @Test
    void returnsKeyWhenTranslationNotFound() {
        TranslationService service = new TranslationService(tempDir);

        assertEquals("missing.key", service.get("fr", "missing.key", "de"));
    }
}
