# Tower Defense Mod — Game Design

Oct 27, 2026 · @Vadim

## Vision

A Fabric mod for Minecraft 26.1.2 that mixes tower defense with first-person combat: you build towers from a top-down commander view, then drop into FPS to fight, mine and repair.

The design rule behind every mechanic: **the player can't be everywhere at once.** Mining pays, fighting saves the base, building scales the defense, and each one pulls you away from the other two. References: Orcs Must Die, Dungeon Defenders, Sanctum.

## Core loop

Each wave is a build phase followed by a fight phase; gold from the fight funds the next build.

1. **Build phase** (timer, or skip with a ready-up): see the next-wave preview, place and upgrade towers, shop, mine.
2. **Wave phase**: monsters spawn and head for the Heart. You fight in FPS, mine risky veins, repair walls.
3. **Payout**: wave bonus plus kill rewards; veins regenerate. Back to 1.

```
Build phase  -->  Wave phase  -->  Payout
     ^                                |
     +------------ next wave ---------+
```

The wave phase is where the tension lives: every minute spent mining is a minute not defending.

### Commander view vs FPS

- **Commander view**: top-down camera, free cursor, grid placement of towers and traps, map overview, wave info.
- **FPS view**: vanilla first-person play — combat, mining, repairs, shopping.
- **Switching must cost something**, or it stops mattering. Default: commander view only works while standing at a **Command Post** block, so your body is parked and vulnerable. During the build phase switching is free from anywhere.
- Alternative to playtest: free switching during waves with a cooldown or gold cost.

## Economy

Gold comes from mining veins (main income) and kills (secondary), and is spent at the shop and on towers.

- **Gold veins** are custom blocks placed by the map, not vanilla ore. Rich veins sit outside the walls or along monster lanes; safe veins near the Heart yield little. Mining mid-wave is tempting and dangerous — that's the point.
- **Veins regenerate** between waves, so income doesn't run dry and players can't strip the map in wave 1.
- **Custom currency**: mined gold chunks convert into a coin balance stored on the player (data attachment), not as vanilla items. This keeps vanilla gold farms, piglin bartering and loot chests out of the economy.
- **Kill rewards** are smaller than mining income, giving two playstyles: miner/builder and fighter. These become co-op roles later.
- **Wave-clear bonus** scales with wave number and Heart HP left, rewarding clean defense.
- **Better pickaxes** from the shop speed up mining, so tool upgrades are an investment in income.

## Defense objective and pathing

You defend a **Heart** (nexus) block with HP; if it's destroyed, the run is lost.

Pathing is the biggest design-and-tech decision. Vanilla mob AI targets players, not objectives, so monsters need custom goals either way.

| Approach | Feel | Cost to build | Risk |
| --- | --- | --- | --- |
| Fixed lanes | Classic TD, predictable | Low | Less Minecraft-y |
| Player-built mazes | Orcs Must Die, very Minecraft-y | High (own pathfinding to the Heart) | Players wall off the Heart completely |
| Hybrid | Fixed spawns and Heart, open terrain, walls shape the route | Medium | Needs wall-breaking mobs to stay fair |

Working assumption: **hybrid**. Mobs path toward the Heart around player blocks; if no path exists, sappers and brutes attack the nearest wall. This keeps building meaningful without allowing a perfect wall.

## Towers

Towers are placed and upgraded from commander view, three tiers each; selling refunds part of the cost.

| Tower | Role | Counters | MVP |
| --- | --- | --- | --- |
| Arrow | Cheap single-target damage | Runners | Yes |
| Cannon | Area damage, no terrain damage | Groups | Yes |
| Frost | Slows in a radius | Runners, bosses | Yes |
| Tesla | Chain lightning between targets | Dense waves | Later |
| Fire / lava trap | Damage over time on a tile | Mazes, chokepoints | Later |
| Beacon | Buffs nearby towers or the player | Supports the build | Later |
| Anti-air ballista | Targets flyers only | Phantoms | Later |

- **Targeting modes** per tower: first, strongest, closest, flying.
- Towers have HP; sappers and brutes can damage them, and the player repairs them in FPS.

## Shop

The shop is a **Quartermaster** block (or NPC) near the Heart, open in any phase; buying mid-wave means walking back to it.

| Category | Examples |
| --- | --- |
| Weapons | Better swords, crossbows, special arrows (frost, explosive) |
| Armor | Armor sets, shields |
| Traps | Spikes, tar (slow), launch pads, tripwire mines |
| Tools | Faster pickaxes (more income) |
| Consumables | Healing, Heart repair kit, wall blocks, recall-to-Heart item |

Prices rise slightly each wave in Infinite mode so late-game gold stays meaningful.

## Monsters and waves

Variety matters more than count: each monster type should demand a different answer from the player.

| Monster | Behavior | Forces the player to | MVP |
| --- | --- | --- | --- |
| Runner | Fast, fragile | Build slows and early damage | Yes |
| Brute | Armored, slow, hits walls when blocked | Focus fire | Yes |
| Sapper | Creeper-like, targets walls and towers | Protect key towers | Yes |
| Flyer | Phantom, ignores the maze | Build anti-air | Yes |
| Boss | Every 5–10 waves, special ability | Save gold, combine towers and FPS | Yes (one) |
| Tunneler | Silverfish-style, digs under walls | Watch the ground | Later |
| Thief | Targets veins or your stash, not the Heart | Defend the economy | Later |
| Splitter | Splits into smaller mobs on death | Use area damage | Later |

- **Next-wave preview** during the build phase, so players plan rather than guess.
- Waves are defined in **data files** (JSON): spawn point, mob type, count, delay. Easy to balance without recompiling, and datapack-friendly.
- Mobs are custom entity types (or tagged vanilla mobs) with a Heart-seeking goal; they don't burn in daylight.

## Game modes

Survival ships first; Infinite follows almost for free because it reuses the same systems with a scaling wave generator.

| Mode | Rules | Win / end | Priority |
| --- | --- | --- | --- |
| A. Survival (campaign) | Fixed 15–30 waves on a handcrafted map | Clear the last wave | MVP |
| B. Infinite (endless) | Waves scale forever; a mutator every 10 waves | Heart falls; score and personal best | Next |
| G. Sandbox | Unlimited gold, spawn any wave | None | Next (needed for balancing) |
| C. Hardcore | One life, no Heart repair, no sell refunds | As Survival | Later |
| D. Co-op | 2–4 players; builder, miner, fighter roles | As Survival or Infinite | Later |
| F. Wilderness | Place a Heart in a normal survival world; waves at night | Heart falls | Later |
| E. Versus / Siege | One team defends, the other spends gold to send monsters | Heart falls or timer ends | Stretch |

**Mutator examples** (Infinite): mobs +50% speed, towers cost double, night only, no FPS damage (towers only), flyers-only wave.

## Arena and maps

Matches run in a **custom dimension**, loading handcrafted maps from structure templates.

- Keeps the player's real world clean; a reset is just reloading the template.
- Handcrafted maps make balancing possible: known spawn points, Heart position, vein locations, Command Post and Quartermaster.
- Map metadata (spawns, Heart, veins, wave file) lives in a data file next to the structure, so new maps need no code.
- A lobby block or command starts a match: pick map and mode.
- Procedural maps come later, with Infinite mode.

## MVP scope and milestones

The MVP is one map in Survival mode with 15–20 waves — enough to prove the core loop is fun.

- [ ] One handcrafted map in a custom dimension
- [ ] Heart block, one or two spawn points, regenerating gold veins
- [ ] Coin balance, kill rewards, wave-clear bonus
- [ ] Command Post and commander view with tower placement
- [ ] Three towers: arrow, cannon, frost
- [ ] Four mob types (runner, brute, sapper, flyer) and one boss
- [ ] Quartermaster shop with about 10 items
- [ ] Waves loaded from JSON, next-wave preview
- [ ] Survival mode: win and lose screens

### Milestones

1. **Camera spike** — commander view with free cursor and block raycast on 26.1.2. Highest technical risk, so first.
2. **Core systems** — Heart, wave spawner from JSON, Heart-seeking mob goal, coin balance.
3. **Towers and economy** — three towers, gold veins, shop.
4. **Content and balance** — four mobs, boss, one map, Survival mode end-to-end, Sandbox for tuning.
5. **Infinite mode** — scaling generator, mutators, score.
6. **Co-op and polish** — multiplayer sync, roles, more towers and mobs.

## Technical notes

Stack: Fabric on Minecraft 26.1.2, Mojmap, Java 25 (see docs/tech-stack.md).

- **Top-down camera** is the riskiest client piece: a camera mixin, an unlocked cursor (custom screen over the world), and raycasting from the mouse for placement. Validate against real bytecode with minecraft-dev-mcp.
- **Server-authoritative from day one**: waves, economy, pathing, tower targeting and Heart HP live on the server; the client only renders and sends intents ("place tower X at Y"). Co-op later then needs no rewrite.
- **Data-driven content**: waves, shop prices, tower stats and map metadata in JSON / datapacks, so balancing doesn't need a rebuild.
- **Pathfinding**: a custom Heart-seeking goal on top of vanilla navigation first; a flow field toward the Heart if many mobs make that too slow.
- **Fabric GameTest** checks, e.g. arrow tower kills a runner within N ticks, frost slows a runner below X speed, wave 10 spawns what its JSON says.
- **In-game testing** with the minecraft-mcp bridge in `runClient`: spawn waves by command, screenshot results.

## Open decisions

- [ ] Single-player first, or co-op as a core goal from the start? (Assumed: single-player first, server-authoritative so co-op is cheap later.)
- [ ] Arena dimension only, or also Wilderness mode in normal worlds? (Assumed: arena first.)
- [ ] Fixed lanes, player-built mazes or hybrid? (Assumed: hybrid.)
- [ ] Commander view: Command Post only, or free switching with a cooldown/cost?
- [ ] Mod ID and base package name.
