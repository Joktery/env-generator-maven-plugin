# env-generator-maven-plugin

[English](README.md) | **Русский**

Maven-плагин, который сканирует конфигурацию проекта на env-плейсхолдеры (`${VAR}`, `${VAR:default}`)
и поддерживает актуальный файл `.env.example`: по алфавиту, с перенесёнными дефолтами,
без перезаписи значений, введённых вручную.

Запускается на фазе `generate-resources` — то есть при обычном `mvn compile`.

## Что сканируется

- `src/main/resources` (настраивается): `*.properties`, `*.yml`, `*.yaml`
- опционально `src/main/java`: `System.getenv("X")`, `System.getProperty("X", "default")`,
  `Environment.getProperty(...)`, `@Value("${X:default}")`
- тестовые классы и `src/test/resources` **не сканируются никогда**

Правила:

- плейсхолдер считается env-переменной, если имя матчит `envNamePattern`
  (по умолчанию `[A-Z][A-Z0-9_]*`); ссылки вида `${spring.datasource.url}` игнорируются
- дефолт переносится в значение: `${DB_PORT:5432}` → `DB_PORT=5432`
- вложенные конструкции распутываются: `${DB_HOST:${DEFAULT_HOST:localhost}}` →
  `DB_HOST=` (литерального дефолта нет) + `DEFAULT_HOST=localhost`
- значения с пробелами/`#` записываются в кавычках
- комментарии (`#` в начале строки, инлайн-комментарии в yaml) не сканируются
- ключ, уже существующий в `.env.example`, **никогда не перезаписывается** — каким бы ни был дефолт;
  исчезнувшие из кода ключи остаются в файле, но логируются WARNING-ом

## Подключение

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

Теперь `mvn compile` поддерживает `.env.example` в актуальном состоянии.

## Опции

| Параметр | Свойство | Default | Описание |
|---|---|---|---|
| `outputFile` | `envgen.outputFile` | `${project.basedir}/.env.example` | выходной файл |
| `sourceDirs` | — | `src/main/resources` | директории сканирования |
| `includes` | — | `**/*.properties,**/*.yml,**/*.yaml` | Ant-паттерны включения |
| `excludes` | — | `**/target/**` | Ant-паттерны исключения |
| `scanJavaSources` | `envgen.scanJavaSources` | `false` | скан `System.getenv`/`@Value` в `src/main/java` |
| `envNamePattern` | `envgen.envNamePattern` | `[A-Z][A-Z0-9_]*` | regex, по которому имя плейсхолдера считается env-переменной |
| `includeDefaults` | `envgen.includeDefaults` | `true` | писать дефолты; `false` — все значения пустые |
| `headerComment` | `envgen.headerComment` | авто-текст | заголовок файла (многострочно через `\n`); пустая строка — без заголовка |
| `encoding` | `envgen.encoding` | `${project.build.sourceEncoding}` / UTF-8 | кодировка чтения и записи |
| `verbose` | `envgen.verbose` | `false` | логировать место обнаружения каждой переменной |
| `skip` | `envgen.skip` | `false` | пропустить выполнение (`-Denvgen.skip=true`) |

Пример с настройками:

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

## Пример

`src/main/resources/application.yml`:

```yaml
app:
  datasource:
    url: jdbc:postgresql://${DB_HOST:${DEFAULT_DB_HOST:localhost}}:${DB_PORT:5432}/${DB_NAME}
    password: ${DB_PASSWORD:}
  welcome-message: ${WELCOME_MESSAGE:Hello world}
```

сгенерирует `.env.example`:

```properties
DB_HOST=
DB_NAME=
DB_PASSWORD=
DB_PORT=5432
DEFAULT_DB_HOST=localhost
WELCOME_MESSAGE="Hello world"
```

## Известные ограничения (MVP)

- файл переписывается целиком: комментарии и группировка, добавленные вручную внутри
  `.env.example`, не сохраняются (значения сохраняются всегда)
- `${VAR}` внутри незакавыченных значений в yaml-многострочных блоках (`|`, `>`)
  сканируются по всему тексту — структурно yaml не парсится
- в `.properties` инлайн `#` считается частью значения (так работает сам формат)

## Разработка

- `mvn clean install` — сборка, тесты, установка в локальный репозиторий
- `src/it/demo` — демо-проект: `mvn -f src/it/demo/pom.xml compile` генерирует
  `src/it/demo/.env.example` и проверяет весь цикл

Идеи следующих версий: сохранение пользовательских комментариев, группировка по файлам-источникам,
`removeObsolete`-опция, поддержка `${env.VAR}`-префиксов Spring.

## Лицензия

Распространяется по [лицензии MIT](LICENSE).
