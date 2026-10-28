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
    <groupId>ru.joktery</groupId>
    <artifactId>env-generator-maven-plugin</artifactId>
    <version>0.3</version>
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
| `keyPrefix` | `envgen.keyPrefix` | — (off) | prefix every variable to make it project-unique, e.g. `MYAPP` → `MYAPP_DB_HOST`; names already starting with the prefix are left as is |
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

## Per-project variable prefix

When several projects share one environment (a single CI secret store, a shared dotenv file),
variable names must not collide. `keyPrefix` makes every variable of the project unique:

```xml
<configuration>
    <keyPrefix>MYAPP</keyPrefix>
</configuration>
```

Behavior:

- every found variable is written as `MYAPP_NAME`: `DB_HOST` → `MYAPP_DB_HOST`; this applies
  to all sources — configs, `@Value`, `System.getenv`/`getProperty` (lowercase names included)
- names that already start with `MYAPP_` are left as is — no double prefix
- when the prefix is enabled for the first time, values already present in `.env.example`
  are carried over: `DB_HOST=prod-db` becomes `MYAPP_DB_HOST=prod-db` (logged as INFO)

The prefix affects only `.env.example`; project sources are not modified.

## Known limitations (MVP)

- the file is rewritten as a whole: comments and grouping added by hand inside
  `.env.example` are not preserved (values always are)
- `${VAR}` inside unquoted yaml block scalars (`|`, `>`) is scanned as plain text —
  yaml is not parsed structurally
- in `.properties` an inline `#` is treated as part of the value (that is how the format works)

## Development

- `mvn clean install` — build, tests, install into the local repository
- `src/it/demo` — demo project (default behavior, no prefix): `mvn -f src/it/demo/pom.xml compile`
  generates `src/it/demo/.env.example` and exercises the whole cycle
- `src/it/demo-prefix` — demo project with `<keyPrefix>MYAPP</keyPrefix>`: all keys come out
  as `MYAPP_*`, already-prefixed names stay single-prefixed

Ideas for future versions: preserving user comments, grouping by source file,
a `removeObsolete` option, Spring `${env.VAR}` prefix support.

## License

Released under the [MIT License](LICENSE).
