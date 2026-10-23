# SneakyVenusTrap

A manually spawned Darkvale boss encounter for Paper 26.2 / Java 25. Built from the Team-Sneakymouse EmptyPlugin template; requires the pinned SneakyUtils runtime plugin.

## Developer commands

- `/sneakyvenustrap spawn` — operators only, in-game. Look at solid ground within 30 blocks. Spawns one stationary plant and its hunting garden. Gardens must be at least 40 blocks apart; maximum eight. Leave a clear area around and above the plant.
- `/sneakyvenustrap remove` — operators only, in-game. Look at the ground beneath the encounter within 30 blocks. Removes the nearby garden and attempts to evacuate swallowed players. Failed evacuations retain recovery information for retry.
- `/sneakyvenustrap reload` — permission `sneakyvenustrap.command.reload` (operators by default). Reloads configuration, not plugin code.

**No automatic spawn or respawn.** Killing the plant, chunk/world unload, removal and disable remove its transient entities. The developer must spawn it again. Do not use hot reload as a deployment method.

## Encounter

- Giant vanilla BlockDisplay sculpture with green jaws, red mouth and pale teeth; no custom resource pack required. This is a functional prototype sculpture, not a finished bespoke animated character asset.
- Running and microphone activity make players more attractive targets; sneaking lowers their noise score, not immunity. Creative/spectator players are not hunted.
- Roots telegraph an eruption before a smaller mouth appears. Move away or attack during the shared throat-vulnerability window. All mouths share 400 health; accepted hits deal 10 damage with a half-second attack cooldown.
- Sneak/right-click a mouth while holding the configured bribe item. One item is consumed per admitted attempt; success is exactly one in five. Success grants 15 seconds of truce. Each player has a 60-second bribe cooldown per garden.
- Ordinary right-click attempts to claim configured bait. A genuine payout is rare and bounded; unconfigured bait is disabled rather than inventing a server collectible or executing a currency command.
- Some successful hunting strikes swallow the target into a dedicated greenhouse-stomach room. Remember the original cry/visual soul cue and strike its flower. Correct flowers break throat knots; wrong flowers cause damage and digestion pressure. Three knots force escape. Allies hitting the exposed plant relieve pressure; solo escape is possible.

## Configuration and assets

The default `config.yml` deliberately has `bribe.item: null` and `lure.item: null`. Supply genuine Bukkit-serialized ItemStacks (including the real item's model/PDC) to enable those paths. No specific rare server item or economic value has been selected. No money integration or currency payout is included.

`sounds.*` accepts Minecraft/resource-pack sound event identifiers. Vanilla sounds are the defaults. Final female/monstrous/stolen-voice performed audio is **not included**. No player voices are recorded, retained, cloned or replayed.

Optional microphone detection integrates with Simple Voice Chat (API 2.5.27). It uses nearby public microphone activity metadata only; group conversations and empty/end packets do not attract the plant. Install a compatible `voicechat` runtime plugin and confirm startup logs; absence falls back to movement hunting. Microphone activity is not speech recognition or recording.

## Player safety and persistence

- Stomach construction writes only to the dedicated `sneakyvenustrap_stomach` void world, never the exterior map. The plugin refuses to take over an existing world without its ownership marker.
- Return locations are journalled to `plugins/SneakyVenusTrap/captives.yml` before transfer. Failed evacuations retain recovery information. Keep this file and the stomach world until all captives are recovered.
- Player inventories are never replaced. If a server stops with a captive inside, join/respawn recovery attempts a safe return.
- Rewards reserve their global cap before inventory mutation. Keep `lure-ledger.yml` and player data; deleting them resets anti-farming history.

## Build and verification

```bash
./gradlew check jar sourcesJar generatePomFileForMavenPublication
```

Output: `~/.cache/SneakyVenusTrap-gradle-build/libs/SneakyVenusTrap-<version>.jar`. Install alongside SneakyUtils on a test server first. Do not select an older JAR left in this directory.

Tests use JUnit and MockBukkit; they are not proof of client visuals, audible sound design, or a live multiplayer encounter. Inspect JUnit XML for aborted/skipped tests, not only Gradle's exit status.

Existing local smoke tooling: `./gradlew resolveRunServerPlugins` checks staged dependencies; `./gradlew runServer` starts Paper with matching plugin dependencies. Paper's EULA must be accepted in the local run directory.

### In-game acceptance checklist

- Check the main head, teeth, stem and leaves from player height; verify reach/hitboxes and smaller mouth readability.
- Spawn/remove as operator; verify non-operators cannot use either command. Check completions.
- Sprint, walk, sneak and speak near the garden, separately. Confirm genuine microphone activity only when integration is available.
- Dodge eruption telegraphs; damage an exposed mouth, kill the whole garden, and confirm no respawn.
- Configure real test bait/bribe ItemStacks; verify one-in-five bribes, consumption/cooldowns and bounded rewards. Review economic values before production.
- Get swallowed, follow original/echo cues, make a wrong choice, escape solo and receive outside help.
- Exercise death, logout/rejoin, remove, chunk/world unload and server restart with a captive. Confirm safe recovery and no orphan displays.
- Test a deliberately cancelled return teleport, restore teleport permission and confirm recovery retries.

## Publication

Repository: https://github.com/Team-Sneakymouse/SneakyVenusTrap

Maven coordinate: `io.github.team-sneakymouse:sneakyvenustrap:<version>` from `https://maven.sneakyrp.com/releases`. Versions derive from commit UTC date and 12-character hash. Build again after committing for a matching artifact filename.

**Pushing `main` triggers publishing.** Feature work goes through PRs; merge/deployment require explicit authorization. Maven/Infisical credentials remain environment-only. Read [AGENTS.md](AGENTS.md) and its shared policy pointer before contributing.
