package com.spinyowl.cards.service;

import com.spinyowl.cards.model.Card;
import com.spinyowl.cards.util.PebbleCardTranslationFunction;
import com.spinyowl.cards.util.PebbleTranslationFunction;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.FileLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import io.pebbletemplates.pebble.extension.AbstractExtension;
import io.pebbletemplates.pebble.extension.Function;
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
            return wrapWithHtml(sw.toString(), projectManager.getProjectProperties());
        } catch (Exception e) {
            log.error("Error rendering card {}", index, e);
            return "<p>Error rendering card.</p>";
        }
    }

    private String wrapWithHtml(String content, Map<String, Object> projectProps) {
        Objects.requireNonNull(projectProps, "projectProps");

        String width = null;
        String height = null;
        Object cardProps = projectProps.get("card");
        if (cardProps instanceof Map<?, ?> map) {
            Object cardWidth = map.get("width");
            Object cardHeight = map.get("height");
            width = cardWidth != null ? cardWidth.toString() : null;
            height = cardHeight != null ? cardHeight.toString() : null;
        }

        List<String> styles = new ArrayList<>();
        styles.add("margin:0");
        if (width != null && !width.isBlank()) {
            styles.add("width:" + width.trim());
        }
        if (height != null && !height.isBlank()) {
            styles.add("height:" + height.trim());
        }

        String bodyStyle = String.join(";", styles);

        return new StringBuilder()
                .append("<html>")
                .append("<body style=\"")
                .append(escapeHtmlAttribute(bodyStyle))
                .append("\">")
                .append(content)
                .append("</body></html>")
                .toString();
    }

    private String escapeHtmlAttribute(String value) {
        return value.replace("\"", "&quot;");
    }
}
