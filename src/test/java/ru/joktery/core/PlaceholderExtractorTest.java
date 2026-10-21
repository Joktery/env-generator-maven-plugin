package ru.joktery.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderExtractorTest {

    private final PlaceholderExtractor extractor =
            new PlaceholderExtractor(PlaceholderExtractor.DEFAULT_ENV_NAME_PATTERN);

    private Map<String, String> extract(String text) {
        Map<String, String> map = new HashMap<>();
        for (Placeholder p : extractor.extract(text)) {
            if (!map.containsKey(p.name()) || map.get(p.name()) == null) {
                map.put(p.name(), p.defaultValue());
            }
        }
        return map;
    }

    @Test
    void simplePlaceholderWithoutDefault() {
        Map<String, String> found = extract("password: ${DB_PASSWORD}");
        assertEquals(1, found.size());
        assertTrue(found.containsKey("DB_PASSWORD"));
        assertEquals(null, found.get("DB_PASSWORD"));
    }

    @Test
    void placeholderWithDefault() {
        Map<String, String> found = extract("url: jdbc://${DB_HOST:localhost}/app");
        assertEquals("localhost", found.get("DB_HOST"));
    }

    @Test
    void multiplePlaceholdersInOneValue() {
        Map<String, String> found = extract("url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME}");
        assertEquals(3, found.size());
        assertEquals(null, found.get("DB_HOST"));
        assertEquals("5432", found.get("DB_PORT"));
        assertEquals(null, found.get("DB_NAME"));
    }

    @Test
    void propertyReferencesAreIgnored() {
        Map<String, String> found = extract("url: ${spring.datasource.url}/${DB_HOST}");
        assertEquals(1, found.size());
        assertTrue(found.containsKey("DB_HOST"));
    }

    @Test
    void nestedPlaceholderIsUnwrapped() {
        Map<String, String> found = extract("host: ${DB_HOST:${DEFAULT_HOST:localhost}}");
        // outer var gets no literal default; inner one keeps its own
        assertEquals(null, found.get("DB_HOST"));
        assertEquals("localhost", found.get("DEFAULT_HOST"));
    }

    @Test
    void nestedInsideIgnoredPropertyReferenceIsStillFound() {
        Map<String, String> found = extract("url: ${some.property:${ENV_URL:fallback}}");
        assertEquals("fallback", found.get("ENV_URL"));
    }

    @Test
    void emptyDefaultMeansNoDefault() {
        Map<String, String> found = extract("x: ${API_KEY:}");
        assertEquals(null, found.get("API_KEY"));
    }

    @Test
    void unclosedPlaceholderIsSkipped() {
        Map<String, String> found = extract("x: ${BROKEN and ${OK_VAR}");
        assertEquals(1, found.size());
        assertTrue(found.containsKey("OK_VAR"));
    }

    @Test
    void defaultWithColonInsideIsPreserved() {
        Map<String, String> found = extract("url: ${DB_URL:jdbc:postgresql://localhost/db}");
        assertEquals("jdbc:postgresql://localhost/db", found.get("DB_URL"));
    }

    @Test
    void lowerCaseNamesAreFiltered() {
        Map<String, String> found = extract("x: ${db.host} ${db_host}");
        assertTrue(found.isEmpty());
    }

    @Test
    void commentLinesAreStripped() {
        String text = "# ${COMMENTED_OUT:yes}\n"
                + "real: ${REAL_VAR:1}\n"
                + "  # indented comment ${ANOTHER:2}\n";
        Map<String, String> found = extract(PlaceholderExtractor.stripCommentLines(text, false));
        assertEquals(Map.of("REAL_VAR", "1"), found);
    }

    @Test
    void yamlInlineCommentsAreStripped() {
        String text = "cron: \"0 0 * * * #not-a-comment\"   # real comment ${INLINE_VAR:x}\n"
                + "plain: ${KEEP_ME:1}  # trailing ${GONE:2}\n";
        Map<String, String> found = extract(PlaceholderExtractor.stripCommentLines(text, true));
        assertEquals(Map.of("KEEP_ME", "1"), found);
    }

    @Test
    void propertiesInlineHashIsPartOfValue() {
        String text = "key=${VAL:a#b} # not stripped for properties\n";
        Map<String, String> found = extract(PlaceholderExtractor.stripCommentLines(text, false));
        assertEquals(Map.of("VAL", "a#b"), found);
    }

    @Test
    void extractReturnsRawOccurrences() {
        List<Placeholder> found = extractor.extract("${A:1} ${A:2} ${B}");
        // raw occurrences are not deduplicated
        assertEquals(3, found.size());
    }
}
