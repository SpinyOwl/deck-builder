package com.spinyowl.cards.util;

import com.spinyowl.cards.model.Card;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CsvLoader {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get();

    public static List<Card> loadCards(Path csvPath) {
        if (!FileUtils.isReadableFile(csvPath)) {
            return Collections.emptyList();
        }

        List<Card> list = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(csvPath);
             CSVParser parser = CSV_FORMAT.parse(reader)) {

            List<String> headers = parser.getHeaderNames();
            for (CSVRecord cardRecord : parser) {
                Map<String, String> properties = new LinkedHashMap<>();
                for (String header : headers) {
                    if (header == null || header.isBlank()) {
                        continue;
                    }
                    String value = cardRecord.get(header);
                    properties.put(header, value);
                }

                createAndAddCard(cardRecord, list, properties);
            }
        } catch (IOException e) {
            log.error("Failed to load cards from {}", csvPath, e);
        }
        return list;
    }

    private static void createAndAddCard(CSVRecord cardRecord, List<Card> list, Map<String, String> properties) {
        try {
            list.add(Card.of(properties));
        } catch (IllegalArgumentException ex) {
            log.warn("Skipping card without required id at record {}", cardRecord.getRecordNumber());
        }
    }
}
