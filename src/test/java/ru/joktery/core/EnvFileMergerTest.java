package ru.joktery.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvFileMergerTest {

    private final EnvFileMerger merger = new EnvFileMerger();

    @Test
    void parsesKeysCommentsAndExportPrefix(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve(".env.example");
        Files.writeString(file, String.join("\n",
                "# comment",
                "",
                "DB_HOST=localhost",
                "export DB_PORT = 5432",
                "EMPTY_VAR=",
                "not-a-valid-line",
                ""));

        SortedMap<String, String> parsed = merger.parseExisting(file, StandardCharsets.UTF_8);

        assertEquals("localhost", parsed.get("DB_HOST"));
        assertEquals("5432", parsed.get("DB_PORT"));
        assertEquals("", parsed.get("EMPTY_VAR"));
        assertEquals(3, parsed.size());
    }

    @Test
    void newFileGetsAllVarsSortedWithDefaults() {
        SortedMap<String, String> existing = new TreeMap<>();
        Map<String, String> defaults = new TreeMap<>();
        defaults.put("DB_HOST", "localhost");
        defaults.put("API_KEY", null);

        EnvFileMerger.Result result = merger.merge(existing, defaults, true);

        assertEquals(2, result.entries.size());
        assertEquals("localhost", result.entries.get("DB_HOST"));
        assertEquals("", result.entries.get("API_KEY"));
        assertEquals("API_KEY", result.entries.firstKey());
        assertEquals(2, result.added.size());
        assertTrue(result.kept.isEmpty());
        assertTrue(result.obsolete.isEmpty());
    }

    @Test
    void existingValuesWinOverDefaults() {
        SortedMap<String, String> existing = new TreeMap<>();
        existing.put("DB_HOST", "my-custom-host");
        Map<String, String> defaults = Map.of("DB_HOST", "localhost");

        EnvFileMerger.Result result = merger.merge(existing, defaults, true);

        assertEquals("my-custom-host", result.entries.get("DB_HOST"));
        assertTrue(result.added.isEmpty());
        assertEquals(1, result.kept.size());
    }

    @Test
    void obsoleteKeysAreReportedButKept() {
        SortedMap<String, String> existing = new TreeMap<>();
        existing.put("GONE_VAR", "x");
        existing.put("KEPT_VAR", "y");

        Map<String, String> defaults = Map.of("KEPT_VAR", "z");

        EnvFileMerger.Result result = merger.merge(existing, defaults, true);

        assertEquals("x", result.entries.get("GONE_VAR"));
        assertEquals(1, result.obsolete.size());
        assertEquals("GONE_VAR", result.obsolete.get(0));
    }

    @Test
    void includeDefaultsFalseWritesEmptyValues() {
        EnvFileMerger.Result result = merger.merge(new TreeMap<>(), Map.of("DB_HOST", "localhost"), false);
        assertEquals("", result.entries.get("DB_HOST"));
    }

    @Test
    void valuesWithSpacesOrHashAreQuoted() {
        assertEquals("\"hello world\"", EnvFileMerger.quote("hello world"));
        assertEquals("\"a#b\"", EnvFileMerger.quote("a#b"));
        assertEquals("plain", EnvFileMerger.quote("plain"));
        assertEquals("\"say \\\"hi\\\"\"", EnvFileMerger.quote("say \"hi\""));
    }

    @Test
    void renderSortsAlphabeticallyAndAddsHeader() {
        SortedMap<String, String> entries = new TreeMap<>();
        entries.put("B_VAR", "2");
        entries.put("A_VAR", "1");

        String content = merger.render(entries, "Header line");

        assertEquals(String.join("\n",
                "# Header line",
                "",
                "A_VAR=1",
                "B_VAR=2",
                ""), content);
    }

    @Test
    void writeIfChangedSkipsIdenticalContent(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("out/.env.example");
        String content = "A=1\n";

        assertTrue(merger.writeIfChanged(file, content, StandardCharsets.UTF_8));
        assertFalse(merger.writeIfChanged(file, content, StandardCharsets.UTF_8));
        assertEquals(content, Files.readString(file));
    }
}
