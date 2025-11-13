package com.spinyowl.cards.service;

import com.spinyowl.cards.model.Card;
import com.spinyowl.cards.util.FileUtils;
import com.spinyowl.cards.util.PebbleCardTranslationFunction;
import com.spinyowl.cards.util.PebbleTranslationFunction;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import lombok.extern.slf4j.Slf4j;

import java.io.StringWriter;
import java.util.*;

@Slf4j
public class CardRenderer implements ProjectManager.ReloadListener {

    private final ProjectManager projectManager;
    private PebbleEngine engine;
    private List<Card> cards = List.of();
    private PebbleTranslationFunction translationFunction;
    private PebbleCardTranslationFunction cardTranslationFunction;
    private String htmlWrapperTemplate = ProjectManager.DEFAULT_HTML_WRAPPER_TEMPLATE;

    public CardRenderer(ProjectManager pm) {
        this.projectManager = pm;
        projectManager.addReloadListener(this);
        rebuildFromProject();
    }

    @Override
    public void onProjectReload(ProjectManager manager) {
        rebuildFromProject();
    }

    private void rebuildFromProject() {
        log.info("Rebuilding renderer using project data from {}", projectManager.getProjectDir());
        try {
            cards = projectManager.getCards();

            translationFunction = new PebbleTranslationFunction(
                    projectManager.getTranslations(),
                    projectManager::getDefaultLanguage);

            cardTranslationFunction = new PebbleCardTranslationFunction(
                    projectManager.getCardTranslations(),
                    projectManager::getDefaultLanguage);

            FileLoader loader = new FileLoader();
            loader.setPrefix(projectManager.getTemplatesDirectory().toString());
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

            htmlWrapperTemplate = resolveHtmlWrapperTemplate();

            log.info("Renderer initialized with {} cards", cards.size());
        } catch (Exception e) {
            log.error("Failed to rebuild card renderer", e);
            cards = List.of();
            engine = null;
            htmlWrapperTemplate = ProjectManager.DEFAULT_HTML_WRAPPER_TEMPLATE;
        }
    }

    public int getCardCount() {
        return cards != null ? cards.size() : 0;
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
                String wrapped = wrapWithHtml(sw.toString(), projectManager.getProjectProperties(), card, lang);
                log.debug("Rendered card {} with template {}", index, tpl);
                return wrapped;
            } finally {
                translationFunction.clearLanguage();
                cardTranslationFunction.clearLanguage();
                cardTranslationFunction.clearCardContext();
            }
        } catch (Exception e) {
            log.error("Error rendering card {}", index, e);
            return "<p>Error rendering card.</p>";
        }
    }

    private String wrapWithHtml(String content, Map<String, Object> projectProps, Card card, String lang) {
        Objects.requireNonNull(projectProps, "projectProps");

        String width = extractCardDimension(card, "width");
        String height = extractCardDimension(card, "height");

        if (isBlank(width) || isBlank(height)) {
            Object cardProps = projectProps.get("card");
            if (cardProps instanceof Map<?, ?> map) {
                if (isBlank(width)) {
                    Object cardWidth = map.get("width");
                    width = cardWidth != null ? cardWidth.toString() : null;
                }
                if (isBlank(height)) {
                    Object cardHeight = map.get("height");
                    height = cardHeight != null ? cardHeight.toString() : null;
                }
            }
        }

        Map<String, Object> wrapperContext = new HashMap<>();
        wrapperContext.put("content", content);
        wrapperContext.put("lang", lang);
        wrapperContext.put("project", projectProps);
        wrapperContext.put("card", card != null ? card.asMap() : Map.of());
        wrapperContext.put("card_width", width);
        wrapperContext.put("card_height", height);

        try {
            PebbleTemplate wrapperTemplate = engine.getTemplate(htmlWrapperTemplate);
            StringWriter writer = new StringWriter();
            wrapperTemplate.evaluate(writer, wrapperContext);
            return writer.toString();
        } catch (Exception wrapperError) {
            log.error("Failed to render HTML wrapper template '{}'", htmlWrapperTemplate, wrapperError);
            throw new IllegalStateException("Failed to render HTML wrapper template '%s'".formatted(htmlWrapperTemplate), wrapperError);
        }
    }

    private String extractCardDimension(Card card, String key) {
        if (card == null) {
            return null;
        }
        return card.getProperty(key);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String resolveHtmlWrapperTemplate() {
        String configured = projectManager.getHtmlWrapperTemplate();
        if (configured == null || configured.isBlank()) {
            return ProjectManager.DEFAULT_HTML_WRAPPER_TEMPLATE;
        }

        if (!FileUtils.isReadableFile(projectManager.getTemplatesDirectory().resolve(configured))) {
            log.warn("HTML wrapper template {} not found in project; falling back to {}", configured, ProjectManager.DEFAULT_HTML_WRAPPER_TEMPLATE);
            return ProjectManager.DEFAULT_HTML_WRAPPER_TEMPLATE;
        }

        return configured;
    }

}
