# AGENTS.md

Rules for AI coding assistants working in this repository (a Maven plugin, Java 11,
base package `ru.joktery`).

1. **Bump the project version on every change.** When you modify the plugin sources,
   configuration, or pom in any user-visible way, raise `<version>` in `pom.xml` and update
   the same version everywhere it is referenced (`src/it/demo/pom.xml`, both READMEs).
   The versioning scheme is plain incrementing: `0.1` → `0.2` → `0.3`.

2. **Keep both READMEs in sync.** Documentation lives in two files: `README.md` (English)
   and `README.ru.md` (Russian). Any documentation change — new options, behavior changes,
   version references — must be applied to both files, keeping them equivalent in content.
   Preserve the language switcher links at the top of each file.

3. **Verify changes on the demo project.** After any change, rebuild the plugin and exercise
   it end-to-end through the demo project:

   ```bash
   mvn clean install
   rm -f src/it/demo/.env.example && rm -rf src/it/demo/target
   mvn -f src/it/demo/pom.xml compile
   ```

   Then check `src/it/demo/.env.example`: keys sorted alphabetically, defaults carried over,
   previously hand-edited values untouched, disappeared keys logged as WARNING.
   Unit tests (`mvn test`) must pass as well.
