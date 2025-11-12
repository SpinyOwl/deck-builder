package com.spinyowl.cards.util;

import com.spinyowl.cards.model.Card;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CsvLoader {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();

    public static List<Card> loadCards(Path csvPath) {
        if (csvPath == null || !Files.exists(csvPath)) {
            return Collections.emptyList();
        }

        List<Card> list = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(csvPath);
             CSVParser parser = CSV_FORMAT.parse(reader)) {

            List<String> headers = parser.getHeaderNames();
            for (CSVRecord record : parser) {
                Map<String, Object> properties = new LinkedHashMap<>();
                for (String header : headers) {
                    if (header == null || header.isBlank()) {
                        continue;
                    }
                    String value = record.get(header);
                    properties.put(header, value);
                }

                try {
                    list.add(new Card(properties));
                } catch (IllegalArgumentException ex) {
                    log.warn("Skipping card without required id at record {}", record.getRecordNumber());
                }
            }
        } catch (IOException e) {
            log.error("Failed to load cards from {}", csvPath, e);
        }
        return list;
    }
}
