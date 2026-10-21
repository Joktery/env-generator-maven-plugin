# env-generator-maven-plugin

**English** | [Русский](README.ru.md)

A Maven plugin that scans project configuration for env placeholders (`${VAR}`, `${VAR:default}`)
and keeps an up-to-date `.env.example` file: alphabetically sorted, defaults carried over,
never overwriting values you have filled in manually.

Binds to the `generate-resources` phase — in other words, it runs on a plain `mvn compile`.

## What gets scanned

- `src/main/resources` (configurable): `*.properties`, `*.yml`, `*.yaml`
- optionally `src/main/java`: `System.getenv("X")`, `System.getProperty("X", "default")`,
  `Environment.getProperty(...)`, `@Value("${X:default}")`
- test classes and `src/test/resources` are **never scanned**

Rules:

- a placeholder counts as an env variable when its name matches `envNamePattern`
  (default `[A-Z][A-Z0-9_]*`); property references like `${spring.datasource.url}` are ignored
- the default is carried into the value: `${DB_PORT:5432}` → `DB_PORT=5432`
- nested constructs are unwrapped: `${DB_HOST:${DEFAULT_HOST:localhost}}` →
  `DB_HOST=` (no literal default) + `DEFAULT_HOST=localhost`
- values containing spaces or `#` are written quoted
- comments (full-line `#`, inline yaml comments) are not scanned
- a key that already exists in `.env.example` is **never overwritten**, whatever the default says;
  keys that disappeared from the code stay in the file but are logged with a WARNING

## Usage

```xml
<plugin>
    <groupId>org.example</groupId>
    <artifactId>env-generator-maven-plugin</artifactId>
    <version>0.2</version>
    <executions>
        <execution>
            <goals>
                <goal>generate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

From now on `mvn compile` keeps your `.env.example` up to date.

## Options

| Parameter | Property | Default | Description |
|---|---|---|---|
| `outputFile` | `envgen.outputFile` | `${project.basedir}/.env.example` | output file |
| `sourceDirs` | — | `src/main/resources` | directories to scan |
| `includes` | — | `**/*.properties,**/*.yml,**/*.yaml` | Ant-style include patterns |
| `excludes` | — | `**/target/**` | Ant-style exclude patterns |
| `scanJavaSources` | `envgen.scanJavaSources` | `false` | scan `src/main/java` for `System.getenv`/`@Value` |
| `envNamePattern` | `envgen.envNamePattern` | `[A-Z][A-Z0-9_]*` | regex a placeholder name must match to be treated as an env variable |
| `includeDefaults` | `envgen.includeDefaults` | `true` | write defaults as values; `false` — all values empty |
| `headerComment` | `envgen.headerComment` | auto text | file header (multiline via `\n`); empty string disables it |
| `encoding` | `envgen.encoding` | `${project.build.sourceEncoding}` / UTF-8 | encoding for reading and writing |
| `verbose` | `envgen.verbose` | `false` | log where each variable was found |
| `skip` | `envgen.skip` | `false` | skip execution (`-Denvgen.skip=true`) |

Example with configuration:

```xml
<configuration>
    <outputFile>${project.basedir}/.env.sample</outputFile>
    <sourceDirs>
        <dir>${project.basedir}/src/main/resources</dir>
        <dir>${project.basedir}/src/main/docker</dir>
    </sourceDirs>
    <scanJavaSources>true</scanJavaSources>
    <includes>
        <include>**/*.properties</include>
        <include>**/*.yml</include>
    </includes>
</configuration>
```

## Example

`src/main/resources/application.yml`:

```yaml
app:
  datasource:
    url: jdbc:postgresql://${DB_HOST:${DEFAULT_DB_HOST:localhost}}:${DB_PORT:5432}/${DB_NAME}
    password: ${DB_PASSWORD:}
  welcome-message: ${WELCOME_MESSAGE:Hello world}
```

generates `.env.example`:

```properties
DB_HOST=
DB_NAME=
DB_PASSWORD=
DB_PORT=5432
DEFAULT_DB_HOST=localhost
WELCOME_MESSAGE="Hello world"
```

## Known limitations (MVP)

- the file is rewritten as a whole: comments and grouping added by hand inside
  `.env.example` are not preserved (values always are)
- `${VAR}` inside unquoted yaml block scalars (`|`, `>`) is scanned as plain text —
  yaml is not parsed structurally
- in `.properties` an inline `#` is treated as part of the value (that is how the format works)

## Development

- `mvn clean install` — build, tests, install into the local repository
- `src/it/demo` — demo project: `mvn -f src/it/demo/pom.xml compile` generates
  `src/it/demo/.env.example` and exercises the whole cycle

Ideas for future versions: preserving user comments, grouping by source file,
a `removeObsolete` option, Spring `${env.VAR}` prefix support.

## License

Released under the [MIT License](LICENSE).
