package ru.joktery;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import ru.joktery.core.EnvFileMerger;
import ru.joktery.core.JavaSourceScanner;
import ru.joktery.core.Placeholder;
import ru.joktery.core.PlaceholderExtractor;

/**
 * Scans project configs for env placeholders ({@code ${VAR}} / {@code ${VAR:default}}) and keeps
 * an up-to-date {@code .env.example}: alphabetically sorted, defaults filled in, user-entered
 * values never overwritten. Test sources/resources are not scanned.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public class EnvGeneratorMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /** Output file to maintain. */
    @Parameter(property = "envgen.outputFile", defaultValue = "${project.basedir}/.env.example")
    private File outputFile;

    /** Directories to scan for config files. Default: {@code src/main/resources} (no tests). */
    @Parameter(property = "envgen.sourceDirs", defaultValue = "${project.basedir}/src/main/resources")
    private List<File> sourceDirs;

    /** Ant-style file patterns to include. */
    @Parameter(property = "envgen.includes", defaultValue = "**/*.properties,**/*.yml,**/*.yaml")
    private List<String> includes;

    /** Ant-style file patterns to exclude. */
    @Parameter(property = "envgen.excludes", defaultValue = "**/target/**")
    private List<String> excludes;

    /** Also scan {@code src/main/java} for System.getenv/@Value usages. */
    @Parameter(property = "envgen.scanJavaSources", defaultValue = "false")
    private boolean scanJavaSources;

    /** Regex a placeholder name must match to be considered an env variable (UPPER_SNAKE by default). */
    @Parameter(property = "envgen.envNamePattern", defaultValue = PlaceholderExtractor.DEFAULT_ENV_NAME_PATTERN)
    private String envNamePattern;

    /** Prefix every found variable with {@code PREFIX_} (e.g. {@code DB_HOST} -> {@code MYAPP_DB_HOST});
     *  names already starting with the prefix are left as is. Empty = prefixing off. */
    @Parameter(property = "envgen.keyPrefix")
    private String keyPrefix;

    /** Write placeholder defaults as values; when false, all entries are written empty. */
    @Parameter(property = "envgen.includeDefaults", defaultValue = "true")
    private boolean includeDefaults;

    /** Header comment written at the top of the file; empty string disables it. */
    @Parameter(property = "envgen.headerComment", defaultValue = EnvFileMerger.DEFAULT_HEADER)
    private String headerComment;

    /** Encoding for reading configs and writing the output file. */
    @Parameter(property = "envgen.encoding", defaultValue = "${project.build.sourceEncoding}")
    private String encoding;

    /** Log every found variable with the file it was found in. */
    @Parameter(property = "envgen.verbose", defaultValue = "false")
    private boolean verbose;

    /** Skip execution entirely ({@code -Denvgen.skip=true}). */
    @Parameter(property = "envgen.skip", defaultValue = "false")
    private boolean skip;

    private final EnvFileMerger merger = new EnvFileMerger();

    @Override
    public void execute() throws MojoExecutionException {
        Log log = getLog();
        if (skip) {
            log.info("env-generator: skipped (envgen.skip=true)");
            return;
        }

        Charset cs = resolveCharset();
        String prefix = normalizeKeyPrefix();
        PlaceholderExtractor extractor;
        try {
            extractor = new PlaceholderExtractor(envNamePattern);
        } catch (Exception e) {
            throw new MojoExecutionException("Invalid envgen.envNamePattern: " + envNamePattern, e);
        }

        // HashMap, not TreeMap: values may be null (variable without a default); sorting happens in the merger
        Map<String, String> defaults = new HashMap<>();
        SortedMap<String, TreeSet<String>> sources = new TreeMap<>();

        JavaSourceScanner javaScanner = scanJavaSources ? new JavaSourceScanner() : null;
        for (Path file : collectFiles(sourceDirs, includes, excludes)) {
            scanConfig(readFile(file, cs), extractor, defaults, sources, relativeToBasedir(file), prefix);
        }

        if (scanJavaSources) {
            File mainJava = new File(project.getBuild().getSourceDirectory());
            List<String> javaExcludes = new ArrayList<>(excludes);
            for (Path file : collectFiles(listOf(mainJava), listOf("**/*.java"), javaExcludes)) {
                scanJava(readFile(file, cs), javaScanner, extractor, defaults, sources, relativeToBasedir(file), prefix);
            }
        }

        Path output = outputFile.toPath();
        if (defaults.isEmpty()) {
            log.info("env-generator: no env variables found in " + describeInputs() + ".");
            if (!Files.isRegularFile(output)) {
                return;
            }
        }

        SortedMap<String, String> existing;
        EnvFileMerger.Result result;
        try {
            existing = merger.parseExisting(output, cs);
            result = merger.merge(existing, defaults, includeDefaults, prefix);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + output, e);
        }

        report(result, sources, log);

        try {
            boolean changed = merger.writeIfChanged(output, merger.render(result.entries, headerComment), cs);
            if (changed) {
                log.info("env-generator: " + outputFile.getName() + " updated (" + result.added.size()
                        + " added, " + result.kept.size() + " kept"
                        + (result.renamed.isEmpty() ? "" : ", " + result.renamed.size() + " renamed") + ").");
            } else {
                log.info("env-generator: " + outputFile.getName() + " is up to date.");
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write " + output, e);
        }
    }

    private void scanConfig(String rawText, PlaceholderExtractor extractor,
                            Map<String, String> defaults, SortedMap<String, TreeSet<String>> sources,
                            String displayPath, String prefix) {
        boolean yaml = displayPath.endsWith(".yml") || displayPath.endsWith(".yaml");
        List<Placeholder> found = extractor.extract(PlaceholderExtractor.stripCommentLines(rawText, yaml));
        accumulate(found, defaults, sources, displayPath, prefix);
    }

    private void scanJava(String rawText, JavaSourceScanner javaScanner, PlaceholderExtractor extractor,
                          Map<String, String> defaults, SortedMap<String, TreeSet<String>> sources,
                          String displayPath, String prefix) {
        List<Placeholder> found = new ArrayList<>();
        javaScanner.scan(rawText, found, extractor);
        accumulate(found, defaults, sources, displayPath, prefix);
    }

    private void accumulate(List<Placeholder> found, Map<String, String> defaults,
                            SortedMap<String, TreeSet<String>> sources, String displayPath, String prefix) {
        for (Placeholder p : found) {
            String name = EnvFileMerger.applyPrefix(p.name(), prefix);
            // A real default beats a previous no-default occurrence of the same variable.
            if (p.defaultValue() != null || !defaults.containsKey(name)) {
                defaults.put(name, p.defaultValue());
            }
            sources.computeIfAbsent(name, k -> new TreeSet<>()).add(displayPath);
        }
    }

    /** Trims the configured prefix and strips trailing underscores; {@code null} means prefixing is off. */
    private String normalizeKeyPrefix() throws MojoExecutionException {
        String prefix = keyPrefix == null ? "" : keyPrefix.trim();
        while (prefix.endsWith("_")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        if (prefix.isEmpty()) {
            return null;
        }
        if (!prefix.matches("[A-Za-z][A-Za-z0-9_]*")) {
            throw new MojoExecutionException("Invalid envgen.keyPrefix: '" + keyPrefix
                    + "' (expected letters/digits/underscores, starting with a letter, e.g. MYAPP)");
        }
        return prefix;
    }

    private void report(EnvFileMerger.Result result, SortedMap<String, TreeSet<String>> sources, Log log) {
        for (String name : result.added) {
            log.info("env-generator: + " + name + "=" + result.entries.get(name)
                    + (verbose ? "  [" + String.join(", ", sources.getOrDefault(name, new TreeSet<>())) + "]" : ""));
        }
        for (String name : result.kept) {
            log.debug("env-generator: = " + name + " kept existing value");
        }
        for (String rename : result.renamed) {
            log.info("env-generator: " + rename + " (keyPrefix enabled); value carried over");
        }
        for (String name : result.obsolete) {
            log.warn("env-generator: '" + name + "' is present in " + outputFile.getName()
                    + " but no longer found in the project (kept as is).");
        }
    }

    private List<Path> collectFiles(List<File> dirs, List<String> inc, List<String> exc) {
        List<Path> files = new ArrayList<>();
        for (File dir : dirs) {
            if (dir == null || !dir.isDirectory()) {
                if (dir != null) {
                    getLog().debug("env-generator: source dir does not exist: " + dir);
                }
                continue;
            }
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(dir);
            scanner.setIncludes(inc == null || inc.isEmpty() ? new String[] {"**/*"} : toArray(inc));
            scanner.setExcludes(exc == null ? new String[0] : toArray(exc));
            scanner.setFollowSymlinks(false);
            scanner.scan();
            for (String f : scanner.getIncludedFiles()) {
                files.add(dir.toPath().resolve(f));
            }
        }
        return files;
    }

    private String readFile(Path file, Charset cs) throws MojoExecutionException {
        try {
            return new String(Files.readAllBytes(file), cs);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read " + file, e);
        }
    }

    private String relativeToBasedir(Path file) {
        Path basedir = project.getBasedir().toPath();
        Path rel = basedir.relativize(file.toAbsolutePath());
        return rel.toString().replace(File.separatorChar, '/');
    }

    private String describeInputs() {
        if (scanJavaSources) {
            return "configs and Java sources";
        }
        return "configs";
    }

    private Charset resolveCharset() throws MojoExecutionException {
        String name = encoding != null ? encoding : StandardCharsets.UTF_8.name();
        try {
            return Charset.forName(name);
        } catch (Exception e) {
            throw new MojoExecutionException("Unsupported encoding: " + name, e);
        }
    }

    private static List<File> listOf(File f) {
        List<File> list = new ArrayList<>();
        if (f != null) {
            list.add(f);
        }
        return list;
    }

    private static List<String> listOf(String s) {
        List<String> list = new ArrayList<>();
        list.add(s);
        return list;
    }

    private static String[] toArray(List<String> list) {
        return list.toArray(new String[0]);
    }
}
