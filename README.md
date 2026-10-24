# SneakyVenusTrap

A manually spawned Darkvale boss encounter for Paper 26.2 / Java 25. Built from the Team-Sneakymouse EmptyPlugin template; requires the pinned SneakyUtils runtime plugin.

## Developer commands

- `/sneakyvenustrap spawn` — operators only, in-game. Look at solid ground within 30 blocks. Spawns one stationary plant and its hunting garden. Gardens must be at least 40 blocks apart; maximum eight. Leave a clear area around and above the plant.
- `/sneakyvenustrap remove` — operators only, in-game. Look at the ground beneath the encounter within 30 blocks. Removes the nearby garden and attempts to evacuate swallowed players. Failed evacuations retain recovery information for retry.
- `/sneakyvenustrap reload` — permission `sneakyvenustrap.command.reload` (operators by default). Reloads configuration, not plugin code.

**No automatic spawn or respawn.** Killing the plant, chunk/world unload, removal and disable remove its transient entities. The developer must spawn it again. Do not use hot reload as a deployment method.

## Encounter

- One main textured head: normal PAPER ItemDisplays with `item_model=dev:venus_trap/{body,upperjaw,lowerjaw,leaf}`; six leaves, nine stable visual entities plus one main Interaction. `visuals.mode: textured` is the default and **requires the separately installed client pack**. This plugin neither installs nor hosts it. Select `visuals.mode: block` explicitly for a no-pack BlockDisplay sculpture. Changes apply to newly spawned gardens.
- Shared scale 2, body lift 14.08 model units, jaw offset 10 and hinge [0,0,5]; hinge compensation is `p-Rp`. Jaws rest +26/-22 degrees and stop the bite at +8/-8. Client NONE-context placement and fang collision still require in-game verification.
- Running and microphone activity make players more attractive targets; sneaking lowers their noise score, not immunity. Creative/spectator players are not hunted.
- Close targets within seven blocks trigger a locked-aim head lunge and damaging snap. Every second attack is a distinctly telegraphed swallow (50-tick windup versus 30 for a bite); dodging avoids contact. Only this single head can swallow.
- Distant targets out to 24 blocks draw mouthless tunnelling roots. A loaded-terrain sampled root-to-target trail progressively appears using `trail_segment` and `trail_front`, then two `vine_segment` parts and one `vine_tip` erupt. A 20-tick escape window precedes a bounded 0.35-block/tick velocity pull. Walls, liquids, unsupported terrain, invalid targets or a 220-tick timeout cancel. No production blocks are changed, chunks forced, or players teleported by vines. One vine per garden, 100-tick retry cooldown, at most 19 trail pieces + front + three vine pieces. All are owned by the garden and included in unload cleanup.
- The exposed main throat shares 400 health; accepted hits deal 10 damage with a half-second player attack cooldown. Successful damage emits localized green sap, shrieks and a decaying 40-tick head/stem/leaf stagger; protected or armored-rejected strikes do not emit successful-hit feedback.
- Sneak/right-click a mouth while holding the configured bribe item. One item is consumed per admitted attempt; success is exactly one in five. Success grants 15 seconds of truce. Each player has a 60-second bribe cooldown per garden.
- Ordinary right-click attempts to claim configured bait. A genuine payout is rare and bounded; unconfigured bait is disabled rather than inventing a server collectible or executing a currency command.
- Successful main-head swallow strikes transfer the target into a dedicated greenhouse-stomach room. Remember the original cry/visual soul cue and strike its flower. Correct flowers break throat knots; wrong flowers cause damage and digestion pressure. Three knots force escape. Allies hitting the exposed plant relieve pressure; solo escape is possible.

## Configuration and assets

The default `config.yml` deliberately has `bribe.item: null` and `lure.item: null`. Supply genuine Bukkit-serialized ItemStacks (including the real item's model/PDC) to enable those paths. No specific rare server item or economic value has been selected. No money integration or currency payout is included.

`sounds.*` accepts Minecraft/resource-pack sound event identifiers. Vanilla sounds are the defaults. Final female/monstrous/stolen-voice performed audio is **not included**. No player voices are recorded, retained, cloned or replayed.

Optional microphone detection integrates with Simple Voice Chat (API 2.5.27). It uses nearby public microphone activity metadata only; group conversations and empty/end packets do not attract the plant. Install a compatible `voicechat` runtime plugin and confirm startup logs; absence falls back to movement hunting. Microphone activity is not speech recognition or recording.

## Player safety and persistence

- Stomach construction writes only to the dedicated `sneakyvenustrap_stomach` void world, never the exterior map. The plugin refuses to take over an existing world without its ownership marker.
- Return locations are journalled to `plugins/SneakyVenusTrap/captives.yml` before transfer. Failed evacuations retain recovery information. Keep this file and the stomach world until all captives are recovered.
- Pull cancellation removes still-owned horizontal motion, including same-heading friction attenuation, while preserving vertical velocity and different/stronger impulses. Ownership is a bounded heuristic: a later weaker impulse in exactly the same direction cannot be distinguished from decay. Live movement, terrain and protection-plugin behavior still need dev testing.
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

- Check the ONE main head, teeth, stem and leaves from player height; verify jaw pivots, lunge/contact alignment and the mouthless vines.
- With the new dev resource-pack assets loaded, inspect all eight item models for missing textures. Use `visuals.mode: block` only when intentionally testing the no-pack fallback.
- Test tunnelling over slabs/stairs and near walls, liquids, cliffs and unloaded chunks; dodge eruption, then allow a catch and confirm safe pull, cancellation, and main-head handoff.
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
