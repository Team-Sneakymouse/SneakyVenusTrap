# SneakyVenusTrap

Paper plugin for Team-Sneakymouse / Lords of Minecraft, created from the [EmptyPlugin template](https://github.com/Team-Sneakymouse/EmptyPlugin).

Initial setup only: gameplay requirements have not been supplied, and no gameplay mechanics are implemented. The existing configuration reload scaffold is retained.

## Requirements and setup

- Paper 26.2 and Java 25.
- Install SneakyUtils alongside this plugin; the pinned build dependency is in `build.gradle.kts`.
- Build with `./gradlew jar`, then install the plugin JAR from `~/.cache/SneakyVenusTrap-gradle-build/libs/` in the server's `plugins/` folder.
- Startup creates `plugins/SneakyVenusTrap/config.yml` from the currently empty default configuration.

## Configuration reload

Run `/sneakyvenustrap reload` in-game or `sneakyvenustrap reload` from the console. Permission: `sneakyvenustrap.command.reload` (operators by default). This reloads configuration only, not plugin code or the server. Startup and the command share `SneakyVenusTrap.reloadPlugin()`.

## Build and verification

```bash
./gradlew test jar sourcesJar generatePomFileForMavenPublication publishPreflight
./gradlew check
```

Tests cover plugin lifecycle, reload permissions, command completion and configuration reload, plus SneakyUtils text-helper smoke tests. They use JUnit 5 and MockBukkit, not a live server.

Gradle output is under `~/.cache/SneakyVenusTrap-gradle-build`, avoiding FUSE/NTFS checkout issues. For a local Paper smoke test, `./gradlew runServer` stages required plugin dependencies from matching `compileOnly` JARs. Prefer a native-filesystem `run/` directory (or a symlink to `~/.cache/sneakyvenustrap-run`).

Initial setup is pending approval to update protected `AGENTS.md`: it still contains template identity and the EmptyPlugin-only testing exception, which intentionally blocks `templatePolicyPreflight` and therefore `check`. Do not bypass that guard or publish until the approved cleanup is complete.

## Versioning and publishing

Builds require a git checkout with at least one commit. Versions use the UTC commit date and 12-character HEAD hash: `yyyy.MM.dd-<12-char-git-hash>`. Uncommitted setup changes do not change that version.

- Plugin JAR: `SneakyVenusTrap-<version>.jar`.
- Maven coordinates: `io.github.team-sneakymouse:sneakyvenustrap:<version>`.
- Repository: https://github.com/Team-Sneakymouse/SneakyVenusTrap
- Maven releases: https://maven.sneakyrp.com/releases

Pushes to `main` trigger `.github/workflows/publish.yml`: Java 25 build/check, Infisical OIDC Maven credentials, then `publishMavenPublicationToSneakyrpRepository`. The publication name remains `maven`; the POM marks this artifact as `paper-plugin`.

Public Team-Sneakymouse repositories inherit organization secrets `INFISICAL_IDENTITY_ID` and `INFISICAL_DOMAIN`. An authorized operator must confirm Infisical OIDC permits `Team-Sneakymouse/SneakyVenusTrap`; this initial setup does not modify or verify Infisical. Maven credentials are supplied only through environment/Gradle properties, never committed.

`./gradlew publishPreflight` checks the wrapper executable mode, tracked wrapper JAR and publish workflow. `./gradlew check` additionally rejects the template-only AGENTS exception in renamed projects.

## Contributor guidance

Read [AGENTS.md](./AGENTS.md), including its shared policy pointer, before contributing. Gameplay specification and live-server verification remain pending.
