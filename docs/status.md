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

## Next, proposed (milestone 4)

Four mob types and a boss as data-driven vanilla mobs, tower HP with repair and wall breaking, a Sandbox mode, a next-wave preview, an arena dimension with one handcrafted map, win and lose screens, and a 15-wave Survival set.

## Questions for planning

- What map layout do you want?
- Should towers be repaired by right-click for coins?
- Should the boss summon runners?
