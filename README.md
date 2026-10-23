# EmptyPlugin

Standard **Paper** plugin template for Team-Sneakymouse / Lords of Minecraft.

Clone this repo (or use it as a GitHub template), rename the project, and start implementing.

## What’s included

- Kotlin + Java 25 toolchain, Gradle wrapper (9.3+)
- `run-paper` for a local Paper `26.2` server (`./gradlew runServer` stages jars for every `paper-plugin.yml` server dependency by matching `compileOnly` plugin jars)
- JUnit 5 + MockBukkit (`mockbukkit-v26.2`) unit test setup under `src/test/kotlin`
- Git-derived versions and `maven-publish` to `https://maven.sneakyrp.com/releases`
- GitHub Actions CI (`check`) and publish workflow (Infisical OIDC → Maven credentials)
- **[AGENTS.md](./AGENTS.md)** — shared instructions for LLM coding agents (Cursor, Claude Code, Codex, etc.)

## Plugin reload

Run `/emptyplugin reload` in-game or `emptyplugin reload` from the console to re-read `plugins/EmptyPlugin/config.yml`. The permission is `emptyplugin.command.reload` (default: operators). This reloads configuration only, not plugin code or the server.

Startup and the command share `EmptyPlugin.reloadPlugin()` as the entry point for applying configuration. The command uses Brigadier to simplify native target selector usage in future commands.

## Versioning

Builds **require a git checkout** with at least one commit. The version is:

```text
yyyy.MM.dd-<12-char-git-hash>
```

derived from `git show -s --format=%ct:%h --abbrev=12 HEAD` (UTC date + abbreviated hash).

Plugin JAR name: `EmptyPlugin-<version>.jar`.

Maven coordinates (after publish):

```text
io.github.team-sneakymouse:emptyplugin:<version>
```

## Publish flow

On every push to `main`, `.github/workflows/publish.yml`:

1. Builds with Java 25
2. Fetches `MAVEN_USERNAME` / `MAVEN_PASSWORD` from Infisical via OIDC
3. Runs `./gradlew publishMavenPublicationToSneakyrpRepository`

### GitHub secrets (repository-level setup only for private repos)

Public Team-Sneakymouse repositories inherit the following organization secrets; no repository-level setup is needed. Private repositories must configure these secrets in the repository's GitHub Actions settings.

| Secret | Purpose |
|--------|---------|
| `INFISICAL_IDENTITY_ID` | Infisical machine identity for OIDC |
| `INFISICAL_DOMAIN` | Infisical domain (e.g. `https://…`) |

The Infisical identity must allow OIDC from this repository (`Team-Sneakymouse/EmptyPlugin`). Allowlist the new repo in Infisical if the first Actions run fails at the secrets step.

Credentials are passed to Gradle as `sneakyrpUsername` / `sneakyrpPassword` (`PasswordCredentials` for the `sneakyrp` Maven repository).

## Internal dependencies

The template enables `https://maven.sneakyrp.com/releases` and depends on **SneakyUtils**
(`compileOnly` + required `paper-plugin.yml` server dependency, load `BEFORE`) for shared
helpers such as `com.sneakyutils.util.TextUtility`. Do not vendor a local copy of those
utilities — bump the `sneakyUtilsVersion` in `build.gradle.kts` when you need a newer build.

Add further plugin dependencies the same way: `compileOnly("group:artifact:version")` plus a
`dependencies.server.<PluginName>` entry in `paper-plugin.yml`. `./gradlew runServer` stages
matching jars automatically (by reading each jar’s `paper-plugin.yml` / `plugin.yml` `name:`);
required server deps with no matching `compileOnly` jar fail the task.

## Rename checklist (new plugin from this template)

1. Rename the GitHub repo / local folder.
2. `settings.gradle.kts` → `rootProject.name = "YourPlugin"`.
3. `build.gradle.kts` → update POM name/description/URLs; set `artifactId` (usually lowercase, e.g. `yourplugin`).
4. Move `com.emptyplugin` → your package (sibling plugins often use `com.yourplugin`).
5. Update `paper-plugin.yml` (`name`, `main`, `description`, `website`, and permissions), `EmptyPlugin.IDENTIFIER`, and the reload command docs/tests for the new plugin identity. Keep the `SneakyUtils` server dependency unless you deliberately remove that shared util usage.
6. Update JAR naming if you keep `archiveFileName` tied to `rootProject.name`.
7. Point workflow publish task at the same publication name (`maven`) unless you rename it.
8. Ensure Infisical OIDC allowlists the new repo. Public repos inherit the organization secrets; only private repos need repository-level `INFISICAL_IDENTITY_ID` and `INFISICAL_DOMAIN` secrets.
9. Run `./gradlew publishPreflight` (or `bash scripts/publish-preflight.sh`) before the first publish push — catches non-executable `gradlew`, missing wrapper jar, and missing `workflow_dispatch`.
10. Replace this `README.md` with documentation for your plugin (description, setup, usage, and publishing details).
11. In `AGENTS.md`, remove the EmptyPlugin-only local testing exception and any other template-only instructions. Follow the shared policy unless the new repository has a separately approved, accurately scoped exception. After changing `rootProject.name`, `./gradlew check` verifies that the EmptyPlugin-only exception marker is gone.

### Publish preflight (agents / CI)

`scripts/publish-preflight.sh` is wired into `./gradlew check`. The same check also runs a template-policy preflight that catches the EmptyPlugin-only agent exception if it was copied into a renamed plugin. Run the publish preflight against any plugin checkout:

```bash
bash /path/to/EmptyPlugin/scripts/publish-preflight.sh /path/to/OtherPlugin
```

## Local build

```bash
./gradlew build
```

Gradle writes under `~/.cache/EmptyPlugin-gradle-build` so checkouts on NTFS/FUSE (e.g. `/mnt/files`) do not wedge `test-results`. A leftover repo `build/` directory is unused; delete it if present. If `./gradlew check` fails deleting `test-results`, run `./gradlew --stop` and retry.

For local `runServer`, prefer `run/` as a symlink to a native path (e.g. `~/.cache/emptyplugin-run`) — Paper world COW/file locks break on FUSE mounts.

## Testing

```bash
./gradlew test          # unit tests only
./gradlew check         # tests + other verification (same gate as CI)
```

Example tests: `TextUtilityTest` (SneakyUtils `TextUtility` smoke), `CommandBaseTest` (permissions), `EmptyPluginTest` (MockBukkit load/enable).

## Agent / LLM instructions

Point your tool’s project instructions at **[AGENTS.md](./AGENTS.md)**. Cursor also loads `.cursor/rules/`; Claude Code reads `CLAUDE.md`; GitHub Copilot can use `.github/copilot-instructions.md`.
