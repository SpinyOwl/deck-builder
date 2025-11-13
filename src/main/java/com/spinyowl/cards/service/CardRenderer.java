package com.spinyowl.cards.service;

import com.spinyowl.cards.model.Card;
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

            log.info("Renderer initialized with {} cards", cards.size());
        } catch (Exception e) {
            log.error("Failed to rebuild card renderer", e);
            cards = List.of();
            engine = null;
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
            } finally {
                translationFunction.clearLanguage();
                cardTranslationFunction.clearLanguage();
                cardTranslationFunction.clearCardContext();
            }

            log.debug("Rendered card {} with template {}", index, tpl);
            return wrapWithHtml(sw.toString(), projectManager.getProjectProperties(), card);
        } catch (Exception e) {
            log.error("Error rendering card {}", index, e);
            return "<p>Error rendering card.</p>";
        }
    }

    private String wrapWithHtml(String content, Map<String, Object> projectProps, Card card) {
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

        List<String> styles = new ArrayList<>();
        styles.add("margin:0");
        addStyleIfNotNull("width", width, styles);
        addStyleIfNotNull("height", height, styles);

        String bodyStyle = String.join(";", styles);

        return "<html><body style=\"%s\">%s</body></html>".formatted(escapeHtmlAttribute(bodyStyle), content);
    }

    private static void addStyleIfNotNull(String styleName, String styleValue, List<String> styles) {
        if (styleValue != null && !styleValue.isBlank()) {
            styles.add("%s:%s".formatted(styleName, styleValue.trim()));
        }
    }

    private String escapeHtmlAttribute(String value) {
        return value.replace("\"", "&quot;");
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
}
