package ru.joktery.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceScannerTest {

    private final PlaceholderExtractor extractor =
            new PlaceholderExtractor(PlaceholderExtractor.DEFAULT_ENV_NAME_PATTERN);
    private final JavaSourceScanner scanner = new JavaSourceScanner();

    private Map<String, String> scan(String javaCode) {
        List<Placeholder> out = new ArrayList<>();
        scanner.scan(javaCode, out, extractor);
        Map<String, String> map = new HashMap<>();
        for (Placeholder p : out) {
            if (!map.containsKey(p.name()) || map.get(p.name()) == null) {
                map.put(p.name(), p.defaultValue());
            }
        }
        return map;
    }

    @Test
    void systemGetenv() {
        Map<String, String> found = scan("String key = System.getenv(\"API_KEY\");");
        assertEquals(1, found.size());
        assertTrue(found.containsKey("API_KEY"));
    }

    @Test
    void systemGetenvAcceptsLowerCaseName() {
        Map<String, String> found = scan("String s = System.getenv(\"apiKey\");");
        assertTrue(found.containsKey("apiKey"));
    }

    @Test
    void systemGetPropertyWithDefault() {
        Map<String, String> found = scan("String p = System.getProperty(\"TIMEOUT_MS\", \"5000\");");
        assertEquals("5000", found.get("TIMEOUT_MS"));
    }

    @Test
    void environmentGetProperty() {
        Map<String, String> found = scan("String u = environment.getProperty(\"SERVICE_URL\");");
        assertTrue(found.containsKey("SERVICE_URL"));
    }

    @Test
    void valueAnnotationPlaceholders() {
        Map<String, String> found = scan("@Value(\"${DB_HOST:localhost}\") private String host;");
        assertEquals("localhost", found.get("DB_HOST"));
    }

    @Test
    void commentsAreIgnored() {
        Map<String, String> found = scan(String.join("\n",
                "// System.getenv(\"COMMENTED_OUT\")",
                "/* @Value(\"${ALSO_COMMENTED:1}\") */",
                "String s = System.getenv(\"REAL_ONE\");"));
        assertEquals(1, found.size());
        assertTrue(found.containsKey("REAL_ONE"));
    }

    @Test
    void urlsInStringsAreNotTreatedAsComments() {
        Map<String, String> found = scan("String u = \"http://x//y\"; String s = System.getenv(\"AFTER_URL\");");
        assertTrue(found.containsKey("AFTER_URL"));
    }
}
