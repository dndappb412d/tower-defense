# Heartguard

Fabric mod for Minecraft 26.1.2: tower defense with an FPS mode. Build towers from a top-down commander view, then drop into first person to fight, mine and repair. Defend the Heart.

- Game design: [docs/design.md](docs/design.md)
- Stack decisions and tooling: [docs/tech-stack.md](docs/tech-stack.md)

Mod ID `heartguard`, base package `dev.vadim.heartguard`. Source sets: `src/main` (common), `src/client` (client only), `src/gametest` (tests, not shipped).

## Stack rules

- Minecraft 26.1.2, Fabric (Loader 0.20.1, Fabric API 0.155.3+26.1.2, Loom 1.19.3). Versions live in `gradle.properties`.
- Mojang official mappings only. MC 26.x is unobfuscated: no Yarn, no `mappings` line, no `modImplementation`.
- Java 25 toolchain. Gradle via the wrapper (9.8.2), never a system Gradle.

## Architecture rules

- **Server-authoritative.** Waves, economy, pathing, tower logic and Heart HP live on the server. The client renders and sends intents ("place tower X at Y"). Never trust client state.
- **Data-driven content.** Waves, tower stats, shop prices and map metadata live in JSON / datapacks, not Java constants.
- Client-only code (screens, camera, rendering) goes in `src/client`; it must not be referenced from `src/main`.

## Game systems (milestone 2)

Everything below is server-side and covered by GameTests. Code lives under `dev.vadim.heartguard`.

- `match`: `MatchData` is the whole match as an immutable record with pure transition functions (phases `IDLE`, `BUILD`, `WAVE`, `PAYOUT`, `WON`, `LOST`). `Match` stores it as a persistent attachment on the overworld and ticks it. `Match.damageHeart` is the one way to hurt the Heart.
- `wave`: wave sets are JSON in `data/<namespace>/waves/<name>.json`, loaded on data reload, so datapacks can add or override them. `WaveSpawner` spawns on a schedule driven by `MatchData.waveTick` and ends a wave when everything spawned is dead.
- `ai`: `HeartSeekGoal` sends wave mobs to the Heart and makes them hit it; it talks to the match through a small `Target` interface so tests can use a fake.
- `economy`: `Coins` (player balance, synced to its owner) and `Rewards` (kill rewards, wave-clear bonus).
- `block`: the Heart block is only a marker; Heart HP lives in `MatchData`. `ModBlocks` also registers the Command Post, Quartermaster, the three tower blocks and the vein blocks.
- `placement`: building intents and their server-side rules (`PlacementService`), the Command Post rule (`BuildRules`), and the one-line refusal messages (`Feedback`).
- `tower`: tower types are data (`TowerDef`, `data/<ns>/towers/*.json`), a placed tower is a `TowerBlock` plus `TowerBlockEntity` (owner, tier, targeting, HP), `TowerCombat` and `TowerTargeting` decide who is shot, `TowerService` upgrades, sells and changes mode, and `TowerSync` keeps every client's tower list equal to the server's.
- `vein`: gold veins pay coins straight to the miner (`Veins`), leave a depleted block, and regrow when a wave ends. Kinds and payouts are data (`VeinDef`, `data/<ns>/veins/*.json`).
- `shop`: the Quartermaster. Items and prices are data (`ShopDef`, `data/<ns>/shop/*.json`); `Shop` is the only place a purchase is decided. `item` holds the Heart repair kit.
- `map`: a playable map is a structure template plus a data file (`MapDef`, `data/<ns>/maps/*.json`) with every position relative to the template origin. `MapLoader.play` builds it into the arena dimension, points the match at it, resets the player's coins, and teleports them in; `MapLoader.build` always clears the area first, so playing again is a reset. `ReturnPoint` remembers where the player came from for `leave`. `Match.level(server)` is the level the match runs in (the stored dimension, falling back to the overworld).
- `data`: `DataRegistry` is the one loader for datapack JSON (waves, towers, veins, shop all use it). New content types should use it too.

Wave file format (all of `interval_ticks`, `delay_ticks`, `reward`, `clear_bonus` are optional):

```json
{ "waves": [ { "clear_bonus": 50, "spawns": [
  { "spawn": 0, "mob": "minecraft:husk", "count": 5, "interval_ticks": 40, "delay_ticks": 0, "reward": 5 } ] } ] }
```

Tower, vein and shop files are validated at load, so a bad file is rejected with a logged error instead of half-loading:

```json
{ "kind": "single_target | area | slow", "cost": 50, "sell_refund_percent": 70, "hp": 40,
  "tiers": [ { "damage": 4.0, "range": 14.0, "fire_interval_ticks": 20, "radius": 0, "slow_percent": 0, "slow_ticks": 0 },
             { "upgrade_cost": 40, "damage": 6.0, "range": 16.0, "fire_interval_ticks": 16 } ] }
```
```json
{ "payout": 10 }
{ "items": [ { "item": "minecraft:iron_pickaxe", "count": 1, "price": 90, "category": "tools" } ] }
```

Map file (all positions relative to the template origin; `starting_coins` and `veins` are optional):

```json
{ "template": "heartguard:meadow", "waves": "heartguard:survival", "starting_coins": 120,
  "player_start": [51, 1, 20], "heart": [56, 1, 20], "spawns": [[4, 1, 12], [4, 1, 28]],
  "veins": [ { "pos": [30, 1, 3], "kind": "heartguard:rich" } ],
  "command_post": [50, 1, 23], "quartermaster": [50, 1, 17] }
```

Real play (gamemaster permission): `/heartguard play <map> [survival]` builds the map in the arena and starts a match, `/heartguard leave` stops it and returns you. Dev commands, kept for tests: `/heartguard setheart | addspawn | clearspawns | shop | vein poor|rich | start | skip | stop | status`. They place the Heart and spawn points at the executor position, standing in for map metadata until milestone 4.

## Commands

```bash
./gradlew build          # compile, run GameTests, build the jar
./gradlew runClient      # dev client (opens a game window)
./gradlew runGameTest    # server GameTests only (headless)
./gradlew runClientGameTest   # client GameTests: real client, injected input, screenshots (opens a window briefly)
```

## Verification

Verify automatically; do not ask the user to eyeball things a test can check.
- Server logic: server GameTests in `src/gametest` (`@GameTest`).
- Client behaviour (camera, screens, input): a `FabricClientGameTest` that drives the real client and asserts on state. Screenshots land in `build/run/clientGameTest/screenshots/` and should be looked at, but assertions carry the pass/fail.
- Run `./gradlew build` and `./gradlew runClientGameTest` before pushing client changes. CI only runs `build` (client tests need a display). The client suite takes about 6 minutes, most of it `FullLoopClientTest`, which plays the whole shipped Survival set (mine, shop, build, upgrade, three waves) and logs each wave as `[full loop]`.
- `BalanceGameTest` holds the rules the data files must obey (a wave pays for another tower, upgrades beat new towers, selling loses money, every upgrade is stronger). It cannot say the game is fun; the `[full loop]` log is where to read how hard the waves actually are.

Commander controls: G toggles the view, WASD pans, scroll zooms, 1 to 3 pick the tower, left click builds, right click selects a tower, then U upgrades, X sells and T changes the targeting mode. During a wave the server only accepts these from a player standing within 4 blocks of a Command Post.

## Gotchas learned the hard way

- **`modifyAttached` returns the old value.** `Match.update` re-reads the attachment afterwards. Never compare a before and an after taken from that return value.
- **Force-load test arenas.** A GameTest structure lands at a random offset in its chunk, so mobs and block entities outside it sometimes sit in a chunk that never ticks. Use `TestArena.forceLoad` in tests that need real ticking.
- **Towers shoot on their own.** A tower left standing by a test shoots other tests' mobs and steals their kill rewards. Tests that build towers implement `CustomTestMethodInvoker` and call `TowerCleanup.clear` when done; a test that needs a tower to tick over many ticks lives in its own class (`TowerTickGameTest`).
- **Vanilla AI interferes with mob tests.** Wave-mob tests remove the other goals or use `setNoAi(true)`, otherwise husks chase mock players that earlier tests left in the world.
- **Vanilla `Inventory.add` swallows items for creative players with a full inventory.** `Shop.buy` checks for room itself and drops the rest at the buyer's feet.
- **The GameTest world has a fixed set of dimensions.** The datapack dimension `heartguard:arena` exists in a real new world (client tests, singleplayer) but not in the server GameTest world, so server GameTests run in the overworld and anything that needs the arena is a client test. Code that needs "the match level" goes through one seam so both work.
- **`Path.canReach()` is false for a good path to a solid block.** The Heart is a block, so a vanilla path to it ends beside it. Check that the path's end node is within `HeartSeekGoal.REACH`, not `canReach()`. The same goes for any "is the Heart blocked" test.
- **Wait for chunks in tests that build into the GameTest world.** `setChunkForced` only registers a ticket; entities and navigation need the chunk entity-ticking (`level.isPositionEntityTicking`). Use `succeedWhen` and a run-once flag. The overworld there is real generated terrain, so loading can take a while.
- **The arena is always day:** its dimension type has `has_fixed_time`, no `default_clock` and an empty `timelines` list.
- **Structure templates come from `tools/gen_structure.py`.** It writes the `.nbt` (gzipped NBT, `DataVersion` 4790 for 26.1.2) from a plain layout, so a map is reviewable as text and rebuilds byte for byte: `python tools/gen_structure.py <layout> src/main/resources/data/heartguard/structure/<name>.nbt`. Bump `DATA_VERSION` when the game version changes.
- **`ENTITY_LOAD` fires inside `addFreshEntity`.** Add tags and attachments after spawning, never from a load handler (from the universal-modder field notes).
- **Vanilla hurt immunity swallows a second hit.** After a mob is hurt it ignores weaker damage for about 10 ticks, so two towers on one mob dealt the damage of one. `TowerCombat.hit` clears `invulnerableTime` before every tower hit; anything else that deals scripted damage should do the same.
- **Mobs shove the player.** A client test that mines or builds after a wave has to walk the player back first.

- **Register attachments at startup.** A Fabric attachment type is registered when its class loads. If that happens after a world is read, saved data is skipped with "unknown attachment type". Every class holding an `AttachmentType` has an `init()` that `Heartguard.onInitialize` calls.
- **Saved data needs a data-fixer type in 26.1.2** (`null` crashes on load), so persistent state uses attachments, not `SavedData`.
- **Access wideners use the `official` namespace** (`accessWidener v2 official`); this MC version is unobfuscated.
- **GameTests share one server, so one global match.** Server tests that touch `Match` must finish within one tick and restore `MatchData.EMPTY` in a `finally`. Anything that needs real time (walking mobs, a whole match) is a client GameTest in its own world, or uses a fake `HeartSeekGoal.Target`.
- **Keep GameTest arenas small.** A mob path ends where loaded chunks end; a 29-block floor fell outside the chunks a test keeps loaded.
- **Do not run GameTests that need a window in CI.** CI runs `./gradlew build` only.
- **Commits have no `Co-Authored-By` trailer.**

## Tooling rules

- Before writing a mixin or access widener, check the target against the decompiled source with the `minecraft-dev` MCP server. Do not guess signatures.
- The `minecraft-mcp` bridge mod (`tools/minecraft-mcp`, gitignored) is dev-only. Never put it in the release jar, never add it as a normal dependency, and never ship it in `mods/` of a release instance. It exposes an unauthenticated reflection API on `ws://127.0.0.1:25576`, so run it only during dev sessions.
