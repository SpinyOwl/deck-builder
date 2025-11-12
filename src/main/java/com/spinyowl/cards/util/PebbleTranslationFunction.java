package com.spinyowl.cards.util;

import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.util.*;
import java.util.function.Supplier;

/**
 * Pebble function used in templates to resolve translation keys.
 */
public class PebbleTranslationFunction implements Function {
    private final TranslationService translations;
    private final Supplier<String> defaultLanguageSupplier;
    private final ThreadLocal<String> currentLanguage = new ThreadLocal<>();

    public PebbleTranslationFunction(TranslationService translations, Supplier<String> defaultLanguageSupplier) {
        this.translations = Objects.requireNonNull(translations, "translations");
        this.defaultLanguageSupplier = Objects.requireNonNull(defaultLanguageSupplier, "defaultLanguageSupplier");
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

        Object langObj = firstNonNull(args, "lang", "1");
        String lang = langObj != null ? langObj.toString() : currentLanguage.get();
        String fallbackLang = Optional.ofNullable(defaultLanguageSupplier.get())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse("en");

        if (lang == null || lang.isBlank()) {
            lang = fallbackLang;
        }

        return translations.get(lang, key, fallbackLang);
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
}
