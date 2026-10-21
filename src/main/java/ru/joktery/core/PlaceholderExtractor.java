package ru.joktery.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Finds Spring-style placeholders {@code ${NAME}} / {@code ${NAME:default}} in raw config text,
 * including nested ones such as {@code ${DB_HOST:${DEFAULT_HOST:localhost}}}.
 *
 * A placeholder is treated as an env variable when its name matches {@code envNamePattern}
 * (default {@code [A-Z][A-Z0-9_]*}); everything else (e.g. {@code ${spring.datasource.url}})
 * is considered a property reference and ignored, though its nested content is still scanned.
 */
public final class PlaceholderExtractor {

    public static final String DEFAULT_ENV_NAME_PATTERN = "[A-Z][A-Z0-9_]*";

    private final Pattern envNamePattern;

    public PlaceholderExtractor(String envNamePattern) {
        this.envNamePattern = Pattern.compile(envNamePattern);
    }

    public List<Placeholder> extract(String text) {
        List<Placeholder> result = new ArrayList<>();
        collect(text, result);
        return result;
    }

    private void collect(String text, List<Placeholder> out) {
        int i = 0;
        while (true) {
            int start = text.indexOf("${", i);
            if (start < 0) {
                return;
            }
            int contentStart = start + 2;
            int depth = 0;
            int end = -1;
            for (int p = contentStart; p < text.length(); p++) {
                char c = text.charAt(p);
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    if (depth == 0) {
                        end = p;
                        break;
                    }
                    depth--;
                }
            }
            if (end < 0) {
                // Unclosed placeholder — Spring would not resolve it either, but a valid
                // nested ${...} inside may still be worth finding, so keep scanning inside.
                i = contentStart;
                continue;
            }
            processContent(text.substring(contentStart, end), out);
            i = end + 1;
        }
    }

    private void processContent(String content, List<Placeholder> out) {
        int colon = topLevelIndexOf(content, ':');
        String name = (colon >= 0 ? content.substring(0, colon) : content).trim();
        String defaultPart = colon >= 0 ? content.substring(colon + 1) : null;

        if (!name.isEmpty() && envNamePattern.matcher(name).matches()) {
            String def = null;
            if (defaultPart != null) {
                String trimmed = defaultPart.trim();
                // A default that itself contains placeholders cannot be written as a literal
                // value; the variables it references are extracted below instead.
                if (!trimmed.isEmpty() && !trimmed.contains("${")) {
                    def = trimmed;
                }
            }
            out.add(new Placeholder(name, def));
        }

        // Scan the content again so nested placeholders in both the name and the default
        // (e.g. ${ignored.prop:${DB_URL:jdbc:x}}) are picked up.
        collect(content, out);
    }

    private static int topLevelIndexOf(String s, char ch) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == ch && depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Drops comments so commented-out placeholders do not end up in the generated file.
     * Full-line comments ({@code # ...}) are always dropped (properties and YAML syntax);
     * when {@code yamlInline} is set, trailing {@code  # ...} comments are dropped too,
     * respecting quotes — in .properties an inline {@code #} is part of the value.
     */
    public static String stripCommentLines(String text, boolean yamlInline) {
        StringBuilder sb = new StringBuilder(text.length());
        for (String line : text.split("\r?\n", -1)) {
            if (line.trim().startsWith("#")) {
                continue;
            }
            sb.append(yamlInline ? stripYamlInlineComment(line) : line).append('\n');
        }
        return sb.toString();
    }

    private static String stripYamlInlineComment(String line) {
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDouble) {
                inSingle = !inSingle;
            } else if (c == '"' && !inSingle) {
                inDouble = !inDouble;
            } else if (c == '#' && !inSingle && !inDouble
                    && (i == 0 || Character.isWhitespace(line.charAt(i - 1)))) {
                return line.substring(0, i);
            }
        }
        return line;
    }
}
