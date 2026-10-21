package ru.joktery.core;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional scanner for Java sources: finds {@code System.getenv("X")},
 * {@code System.getProperty("X")} / {@code Environment.getProperty("X", "default")}
 * and {@code @Value("${X:default}")} placeholders.
 *
 * Unlike config placeholders, names from {@code getenv}/{@code getProperty} calls are accepted
 * as is — a literal string argument is an explicit intent, whatever its casing.
 */
public final class JavaSourceScanner {

    private static final Pattern GETENV =
            Pattern.compile("System\\s*\\.\\s*getenv\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");

    private static final Pattern GET_PROPERTY =
            Pattern.compile("(?:System\\s*\\.\\s*getProperty|(?i:Environment)\\s*\\.\\s*getProperty)"
                    + "\\s*\\(\\s*\"([^\"]+)\"\\s*(?:,\\s*\"([^\"]*)\")?");

    public void scan(String javaText, List<Placeholder> out, PlaceholderExtractor extractor) {
        String code = stripComments(javaText);

        Matcher m = GETENV.matcher(code);
        while (m.find()) {
            out.add(new Placeholder(m.group(1).trim(), null));
        }
        m = GET_PROPERTY.matcher(code);
        while (m.find()) {
            String def = m.group(2);
            out.add(new Placeholder(m.group(1).trim(),
                    def == null || def.trim().isEmpty() ? null : def.trim()));
        }

        // @Value("${...}") — the extractor applies the env-name pattern itself.
        out.addAll(extractor.extract(code));
    }

    /** Strips javadoc/block comments and full-line {@code //} comments (string literals may contain "//"). */
    static String stripComments(String text) {
        String noBlock = text.replaceAll("(?s)/\\*.*?\\*/", "");
        StringBuilder sb = new StringBuilder(noBlock.length());
        for (String line : noBlock.split("\n", -1)) {
            if (!line.trim().startsWith("//")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
}
