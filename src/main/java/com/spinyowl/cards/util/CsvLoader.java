package com.spinyowl.cards.util;

import com.spinyowl.cards.model.Card;
import org.apache.commons.csv.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class CsvLoader {

    public static List<Card> loadCards(Path csvPath) {
        List<Card> list = new ArrayList<>();
        try (Reader r = new FileReader(csvPath.toFile())) {
            for (CSVRecord rec : CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .parse(r)) {
                Card c = new Card();
                c.setName(rec.get("name"));
                c.setDescription(rec.get("description"));
                c.setImage(rec.get("image"));
                c.setTemplate(rec.isMapped("template") ? rec.get("template") : null);
                list.add(c);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }
}
