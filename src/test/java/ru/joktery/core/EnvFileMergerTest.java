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
    void applyPrefixAddsPrefixUnlessAlreadyPresent() {
        assertEquals("MYAPP_DB_HOST", EnvFileMerger.applyPrefix("DB_HOST", "MYAPP"));
        // already prefixed (or exactly the prefix) — left as is, never double-prefixed
        assertEquals("MYAPP_DB_HOST", EnvFileMerger.applyPrefix("MYAPP_DB_HOST", "MYAPP"));
        assertEquals("MYAPP", EnvFileMerger.applyPrefix("MYAPP", "MYAPP"));
        // a similar-looking but different prefix is not treated as already present
        assertEquals("MYAPP_MYAPP2_FOO", EnvFileMerger.applyPrefix("MYAPP2_FOO", "MYAPP"));
        // lowercase and dotted getenv names are prefixed like any other
        assertEquals("MYAPP_awsRegion", EnvFileMerger.applyPrefix("awsRegion", "MYAPP"));
        // null/empty prefix disables prefixing
        assertEquals("DB_HOST", EnvFileMerger.applyPrefix("DB_HOST", null));
        assertEquals("DB_HOST", EnvFileMerger.applyPrefix("DB_HOST", ""));
    }

    @Test
    void mergeWithPrefixWritesPrefixedKeys() {
        // defaults arrive already prefixed (the Mojo applies keyPrefix while accumulating)
        SortedMap<String, String> existing = new TreeMap<>();
        Map<String, String> defaults = new TreeMap<>();
        defaults.put("MYAPP_DB_HOST", "localhost");
        defaults.put("MYAPP_ALREADY_SET", "yes");

        EnvFileMerger.Result result = merger.merge(existing, defaults, true, "MYAPP");

        assertEquals("localhost", result.entries.get("MYAPP_DB_HOST"));
        assertEquals("yes", result.entries.get("MYAPP_ALREADY_SET"));
        assertEquals(2, result.entries.size());
    }

    @Test
    void prefixMigrationRenamesLegacyEntryAndCarriesValueOver() {
        SortedMap<String, String> existing = new TreeMap<>();
        existing.put("DB_HOST", "prod-db.example.com");
        existing.put("GONE_VAR", "x");
        Map<String, String> defaults = new TreeMap<>();
        defaults.put("MYAPP_DB_HOST", "localhost");

        EnvFileMerger.Result result = merger.merge(existing, defaults, true, "MYAPP");

        assertEquals("prod-db.example.com", result.entries.get("MYAPP_DB_HOST"));
        assertEquals(1, result.renamed.size());
        assertEquals("DB_HOST -> MYAPP_DB_HOST", result.renamed.get(0));
        // the renamed key is live, only the genuinely disappeared one is obsolete
        assertEquals(1, result.obsolete.size());
        assertEquals("GONE_VAR", result.obsolete.get(0));
    }

    @Test
    void prefixedExistingEntryIsKeptWithoutRename() {
        SortedMap<String, String> existing = new TreeMap<>();
        existing.put("MYAPP_DB_HOST", "prod-db.example.com");
        Map<String, String> defaults = Map.of("MYAPP_DB_HOST", "localhost");

        EnvFileMerger.Result result = merger.merge(existing, defaults, true, "MYAPP");

        assertEquals("prod-db.example.com", result.entries.get("MYAPP_DB_HOST"));
        assertTrue(result.renamed.isEmpty());
        assertTrue(result.added.isEmpty());
        assertTrue(result.kept.contains("MYAPP_DB_HOST"));
    }

    @Test
    void migrationSkippedWhenBothPrefixedAndLegacyEntriesExist() {
        SortedMap<String, String> existing = new TreeMap<>();
        existing.put("DB_HOST", "plain");
        existing.put("MYAPP_DB_HOST", "prefixed");
        Map<String, String> defaults = Map.of("MYAPP_DB_HOST", "localhost");

        EnvFileMerger.Result result = merger.merge(existing, defaults, true, "MYAPP");

        assertEquals("plain", result.entries.get("DB_HOST"));
        assertEquals("prefixed", result.entries.get("MYAPP_DB_HOST"));
        assertTrue(result.renamed.isEmpty());
        assertEquals(1, result.obsolete.size());
        assertEquals("DB_HOST", result.obsolete.get(0));
    }

    @Test
    void migrationSkippedWhenPlainVarIsStillFound() {
        SortedMap<String, String> existing = new TreeMap<>();
        existing.put("DB_HOST", "plain");
        Map<String, String> defaults = new TreeMap<>();
        defaults.put("DB_HOST", null);
        defaults.put("MYAPP_DB_HOST", "localhost");

        EnvFileMerger.Result result = merger.merge(existing, defaults, true, "MYAPP");

        assertEquals("plain", result.entries.get("DB_HOST"));
        assertEquals("localhost", result.entries.get("MYAPP_DB_HOST"));
        assertTrue(result.renamed.isEmpty());
        assertTrue(result.obsolete.isEmpty());
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
