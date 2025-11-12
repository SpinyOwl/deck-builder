package com.spinyowl.cards.util;

import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Pebble function used in templates to resolve card specific translations.
 */
public class PebbleCardTranslationFunction implements Function {
    private final TranslationService translations;
    private final Supplier<String> defaultLanguageSupplier;

    private final ThreadLocal<String> currentLanguage = new ThreadLocal<>();
    private final ThreadLocal<String> currentCardId = new ThreadLocal<>();
    private final ThreadLocal<Map<String, ?>> fallbackValues = new ThreadLocal<>();

    public PebbleCardTranslationFunction(TranslationService translations,
                                         Supplier<String> defaultLanguageSupplier) {
        this.translations = Objects.requireNonNull(translations, "translations");
        this.defaultLanguageSupplier = Objects.requireNonNull(defaultLanguageSupplier,
                "defaultLanguageSupplier");
    }

    public void setLanguage(String lang) {
        if (lang == null || lang.isBlank()) {
            currentLanguage.remove();
        } else {
            currentLanguage.set(lang);
        }
    }

    public void clearLanguage() {
        currentLanguage.remove();
    }

    public void setCardContext(String cardId, Map<String, ?> fallbackValues) {
        if (cardId == null || cardId.isBlank()) {
            currentCardId.remove();
        } else {
            currentCardId.set(cardId.trim());
        }
        if (fallbackValues == null) {
            this.fallbackValues.remove();
        } else {
            this.fallbackValues.set(fallbackValues);
        }
    }

    public void clearCardContext() {
        currentCardId.remove();
        fallbackValues.remove();
    }

    @Override
    public List<String> getArgumentNames() {
        return Arrays.asList("key", "lang");
    }

    @Override
    public Object execute(Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
        Object keyObj = firstNonNull(args, "key", "0");
        if (keyObj == null) {
            return "";
        }
        String key = keyObj.toString();

        String lang = resolveLanguage(args);
        String cardId = currentCardId.get();
        if (cardId == null || cardId.isBlank()) {
            return fallbackValue(key);
        }

        String translationKey = cardId + "." + key;
        String translated = translations.get(lang, translationKey, resolveFallbackLanguage());
        if (translationKey.equals(translated)) {
            return fallbackValue(key);
        }
        return translated;
    }

    private String resolveLanguage(Map<String, Object> args) {
        Object langObj = firstNonNull(args, "lang", "1");
        String lang = langObj != null ? langObj.toString() : currentLanguage.get();
        if (lang == null || lang.isBlank()) {
            lang = resolveFallbackLanguage();
        }
        return lang;
    }

    private String resolveFallbackLanguage() {
        return Optional.ofNullable(defaultLanguageSupplier.get())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse("en");
    }

    private Object firstNonNull(Map<String, Object> args, String primaryKey, String positionalKey) {
        Object value = args.get(primaryKey);
        if (value != null) {
            return value;
        }
        value = args.get(positionalKey);
        if (value != null) {
            return value;
        }
        if (!args.isEmpty()) {
            return args.values().iterator().next();
        }
        return null;
    }

    private Object fallbackValue(String key) {
        Map<String, ?> map = fallbackValues.get();
        if (map != null && key != null) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return "";
    }
}
