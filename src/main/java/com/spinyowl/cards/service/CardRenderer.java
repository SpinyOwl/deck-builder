package com.spinyowl.cards.service;

import com.spinyowl.cards.model.Card;
import com.spinyowl.cards.util.CsvLoader;
import com.spinyowl.cards.util.PebbleCardTranslationFunction;
import com.spinyowl.cards.util.PebbleTranslationFunction;
import com.spinyowl.cards.util.TranslationService;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import lombok.extern.slf4j.Slf4j;

import java.io.StringWriter;
import java.nio.file.Path;
import java.util.*;

@Slf4j
public class CardRenderer {
    private final ProjectManager projectManager;
    private PebbleEngine engine;
    private List<Card> cards;
    private TranslationService translations;
    private PebbleTranslationFunction translationFunction;
    private PebbleCardTranslationFunction cardTranslationFunction;

    public CardRenderer(ProjectManager pm) {
        this.projectManager = pm;
        reload();
    }

    public void reload() {
        log.info("Reloading project assets from {}", projectManager.getProjectDir());
        try {
            Path csv = projectManager.resolve("cards.csv");
            cards = CsvLoader.loadCards(csv);
            translations = new TranslationService(projectManager.resolve("i18n"));
            translationFunction = new PebbleTranslationFunction(translations, projectManager::getDefaultLanguage);
            TranslationService cardTranslations = new TranslationService(projectManager.resolve("i18n/cards"));
            cardTranslationFunction = new PebbleCardTranslationFunction(cardTranslations, projectManager::getDefaultLanguage);

            FileLoader loader = new FileLoader();
            loader.setPrefix(projectManager.resolve("templates").toString());
            engine = new PebbleEngine.Builder()
                    .loader(loader)
                    .extension(new AbstractExtension() {
                        @Override
                        public Map<String, Function> getFunctions() {
                            Map<String, Function> functions = new HashMap<>();
                            functions.put("t", translationFunction);
                            functions.put("card_t", cardTranslationFunction);
                            return functions;
                        }
                    })
                    .build();

            log.info("Loaded {} cards successfully", cards.size());
        } catch (Exception e) {
            log.error("Failed to reload project assets", e);
        }
    }

    public String renderCard(int index, String lang) {
        if (index < 0 || index >= cards.size()) {
            log.warn("Card index {} out of bounds", index);
            return "<p>No such card index</p>";
        }

        if (engine == null || translationFunction == null || cardTranslationFunction == null) {
            log.error("Renderer not initialized correctly");
            return "<p>Error rendering card.</p>";
        }

        Card card = cards.get(index);
        String tpl = Optional.ofNullable(card.getTemplate())
                .filter(s -> !s.isBlank())
                .orElse(projectManager.getDefaultTemplate());

        try {
            Map<String, Object> ctx = new HashMap<>(card.asMap());
            ctx.put("lang", lang);
            ctx.put("project", projectManager.getProjectProperties());

            PebbleTemplate template = engine.getTemplate(tpl);
            StringWriter sw = new StringWriter();
            translationFunction.setLanguage(lang);
            cardTranslationFunction.setLanguage(lang);
            cardTranslationFunction.setCardContext(card.getId(), ctx);
            try {
                template.evaluate(sw, ctx);
            } finally {
                translationFunction.clearLanguage();
                cardTranslationFunction.clearLanguage();
                cardTranslationFunction.clearCardContext();
            }

            log.debug("Rendered card {} with template {}", index, tpl);
            return sw.toString();
        } catch (Exception e) {
            log.error("Error rendering card {}", index, e);
            return "<p>Error rendering card.</p>";
        }
    }
}
