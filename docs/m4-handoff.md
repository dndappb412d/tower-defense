# Milestone 4 hand-off: field note proposal and milestone 5 plan

State: milestone 4 is merged to `main` (map, mobs, pathing, sandbox, preview, end screens, 15 Survival waves). 148 server GameTests and the full client suite pass. Nothing has been played by a person yet, and no release jar has been built.

## 1. Proposed field note (not written, not PR'd)

Suggested path: `knowledge/games/minecraft/server-authoritative-tower-defense-on-fabric-26-1.md`

**Why it is worth a note:** most of the cost of this milestone was not game design, it was things the 26.1.2 API and the GameTest framework do that are not obvious. Each item below cost at least one failed run.

Draft body, grouped by area:

**State and data**
- Persistent state is a Fabric attachment, never `SavedData` (26.1.2 needs a data-fixer type; `null` crashes on load).
- An attachment type registers when its class loads. If that happens after a world is read, saved data is skipped with "unknown attachment type". Give every class that holds one an `init()` called from `onInitialize`.
- `modifyAttached` returns the old value; re-read after it.
- `ENTITY_LOAD` fires inside `addFreshEntity`, so tag and stamp entities after spawning.
- Content as datapack JSON through one `DataRegistry<T>`; validate at load and reject bad files with a logged error.

**Testing**
- Server GameTests share one server and one global match: finish in one tick and restore state in `finally`. Anything needing real time is a client GameTest in its own world.
- The GameTest world has a fixed dimension set; a datapack dimension (our arena) exists only in real new worlds, so arena tests are client tests.
- A GameTest structure lands at a random offset in its chunk: force-load, and wait for `isPositionEntityTicking`, not just a chunk ticket.
- The framework walls each test box in with barriers. Fliers hit them; use a bigger box.
- Towers left standing shoot other tests' mobs and steal rewards. Clean up, or give ticking tests their own class.
- Client GameTests can drive the real UI: `context.getInput().setCursorPos/pressMouse`, `lookAt`, `holdMouse`. Screens expose a `windowCentreOf(...)` helper for tests.
- `WaveSpawner.countAlive` is global; parallel tests must check their own mob.
- A "full loop" bot that plays the shipped data is the only automated signal about difficulty, and it only says whether this bot survives.

**Mobs and pathing**
- `Path.canReach()` is false for a good path to a solid block (the Heart). Check the end node distance instead.
- A phantom cannot be steered by its own AI here; replace the move control (`Mob.moveControl` via access widener) and fly straight lines at a cruise height.
- Vanilla hurt immunity (about 10 ticks) swallows a second scripted hit; clear `invulnerableTime` before scripted damage.
- Reach and size matter: a 1.3x mob is 2.5 tall, so a 2-high hole never opens a path, and a wall-breaker must measure reach from the eye or it stalls forever on the top row.
- Fire resistance removes sun damage but not the flames.

**Maps**
- Generate structure templates from a text layout (`DataVersion` 4790 for 26.1.2) so maps review as text and rebuild byte for byte.
- Always clear the whole map box before placing: "play again" is then a reset.
- Make the arena dimension always day with a fixed-time dimension type and no timelines.

**Process:** the end-screen change broke an old client test only because a new screen takes the mouse; any new modal screen needs the long-running client tests to dismiss it.

## 2. Milestone 5 plan (proposal)

Theme: make it a game someone else can play and enjoy, not just a system that passes tests.

What the milestone 4 data says: with 15 maxed towers the bot wins every wave untouched; with 10 it won 14 waves untouched and then lost to wave 15 in one go. From about wave 8 the bot holds thousands of unspent coins. So the curve is flat then a cliff, and the economy has no late sink.

Branches, in order:

1. **`m5-release`**: a jar anyone can install.
   - CI uploads the jar per build; a tag-triggered workflow attaches it to a GitHub Release.
   - Bump `mod_version`, add license and mod metadata (description, icon), confirm the jar excludes the dev bridge, run `um publish check`.
   - Done when: a tag produces a downloadable jar that starts in a clean MultiMC instance (26.1.2, Java 25, Fabric API 0.155.3+26.1.2).
2. **`m5-playtest`**: get a person's data first, before more tuning.
   - A start menu or item so non-operators can start a match without commands (today `/heartguard play` needs permission level 2).
   - Log each wave's Heart loss, coins and tower count to a file so a playtest produces numbers.
   - Done when: someone starts, plays and finishes (or loses) a match without typing a command.
3. **`m5-economy`**: a late coin sink and a real difficulty curve.
   - Options for the sink: repair costs scaling with wave, tower tier 4, Heart upgrades, paid abilities. Planner to choose.
   - A curve with several hard waves rather than one cliff; use the playtest data.
   - Extend `BalanceGameTest` with a rule that the bot's unspent coins stay below a bound.
   - Done when: the full-loop bot loses Heart HP in several waves and ends with a small stockpile, and a human's rough opinion agrees.
4. **`m5-wear`**: repair pressure. Towers take damage from more than sappers (ranged mobs or a siege mob), so repairing by hand and the FPS mode matter mid-wave.
5. **`m5-map2`**: a second map with a different shape (one spawn, one choke versus open ground) to test whether the rules hold; wall breaking against player walls on a real map.
6. **`m5-perf`**: 200 mobs in one wave on a real map: tick time, entity count, client frame rate. Cap or pool if needed.

Test-time note: the client suite now takes about 15 minutes, nearly all in the full loop. Proposal: keep a short full-loop smoke (first 5 waves plus the boss waves via jump) in the default run and run the full 15-wave loop in a separate task or on tags.

## 3. Open questions for the planner

- Is the economy sink better as repair cost, a fourth tier, or Heart upgrades?
- Do we want co-op (the design says commander plus FPS player) in milestone 5, or still single player?
- Is the target a Modrinth release, or GitHub Releases only?
- What should the first human playtest measure: fun, difficulty, or clarity of the UI?

## 4. Not verified

- No person has played it. Difficulty claims come from one bot.
- Not run in a real MultiMC instance by me (the user was setting one up).
- No release jar built; `um publish check` not run.
- Wall breaking was tested in a test box and not on the meadow itself, whose walls are deliberately unbreakable stone bricks.
