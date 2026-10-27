# Heartguard development status

As of `main` @ `7f380cb` (https://github.com/dndappb412d/tower-defense). Written to be pasted into the planning chat.

## Done: milestones 1 to 3

All merged to `main`.

- **Milestone 1, commander view:** G toggles a top-down camera, with a free cursor, WASD panning, scroll zoom, a block raycast and server-validated placement.
- **Milestone 2, core systems:** the match phases, the Heart block with HP and a loss condition, waves from JSON datapacks, a Heart-seeking mob goal, and a persistent coin balance with kill and wave-clear rewards.
- **Milestone 3, content systems:**
  - Three data-driven towers (arrow, cannon, frost) with three tiers each, targeting modes, upgrade and sell.
  - A Command Post rule: during a wave, building and tower actions need the player within 4 blocks of one.
  - Gold veins that pay coins directly, deplete, and regrow after each wave.
  - A Quartermaster shop with 11 data-driven items and a custom Heart repair kit.

## Verification

96 server GameTests and 8 client GameTests, including a full-loop test that mines, shops, builds and wins all three shipped waves on a real client.

## Decisions to confirm

- **Commander rule:** free switching between waves, Command Post only during a wave. This is the open question from the design doc.
- **Heart HP** lives in the match state, and the Heart block is only a marker.
- **Kill credit:** towers pay their owner, and a kill by anything else pays nobody.
- **Veins** pay coins directly, with no items.
- **Stack:** MC 26.1.2, Fabric, Mojmap, Java 25. Mod ID `heartguard`, package `dev.vadim.heartguard`.

## Findings and risks

- **Waves are far too easy.** The full-loop test never lets the Heart drop below 100/100 and ends with unspent coins. Balance needs a person playing.
- **Still on the overworld**, not an arena dimension.
- **Not checked:** no GitHub Actions result seen yet, and the `minecraft-mcp` bridge is built but not wired in.

## Milestone 4 in progress

- **`m4-pathing` done.** Heavy mobs (brutes, `breaks_walls`) follow the hybrid rule: when no path to the Heart exists they break the nearest player-built wall (blocks in the `heartguard:breakable_walls` tag: cobblestone, deepslate cobble, planks; the map's stone bricks never) or hit the nearest tower. Towers now take damage (`structure_damage` per hit, data), fall at 0 HP with no refund, and anyone repairs one by right-clicking with an empty hand (`repair_hp`/`repair_cost` in the tower file, default 10 HP for 5 coins). The new sapper (creeper base, `explodes`) goes for the nearest tower, else a wall that blocks the way, else blows up on the Heart; its blast hurts towers, breaks only breakable walls and pays no reward. The commander screen shows tower HP. 14 new server tests.

- **`m4-mobs` done.** Mob types are data (`data/heartguard/mobs/*.json`) and `MobFactory` is the one place a wave mob is made. Four are shipped: runner, brute, flyer (flies over walls), warlord (boss bar, summons runners). Wave files can name them. Server tests cover the data rules, stat application, armor, a runner beating a brute to the Heart, a flyer crossing a wall that stops ground mobs, and the boss summoning within its cap. The Survival wave file does not use them yet (that is `m4-waves`).

- **`m4-map` done.** `/heartguard play meadow survival` builds the 64x8x40 meadow in the arena (terrain from `tools/gen_structure.py`, everything else from `data/heartguard/maps/meadow.json`), starts a match there, and moves the player in; `/heartguard leave` returns them; playing again resets the map. A client test plays it end to end (two arrow towers hold wave 1, Heart 100/100). Match state now carries a dimension, and `Match.level` is the one place that resolves it.
- **`m4-map` spike: GO.** In a real new world the datapack dimension `heartguard:arena` exists (flat generator with no layers, so it is void), a structure template written by `tools/gen_structure.py` loads from the datapack and places exactly, a player can be teleported in and the client follows, and a tower kills a mob there by itself. Finding: the server GameTest world has a fixed set of dimensions and does not include the arena, so arena tests are client tests.
- universal-modder is installed (`um` 0.2.0) and its knowledge base was searched; the notes are summarised in `docs/tech-stack.md`.

## Next, proposed (milestone 4)

Four mob types and a boss as data-driven vanilla mobs, tower HP with repair and wall breaking, a Sandbox mode, a next-wave preview, an arena dimension with one handcrafted map, win and lose screens, and a 15-wave Survival set.

## Questions for planning

- What map layout do you want?
- Should towers be repaired by right-click for coins?
- Should the boss summon runners?
