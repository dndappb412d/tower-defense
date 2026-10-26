We're starting milestone 4 (Content and balance) of Heartguard.
Read CLAUDE.md, docs/status.md, docs/design.md and docs/tech-stack.md first. They are the source of truth.

## 0. New tool: universal-modder

This session adds the universal-modder Claude Code plugin (https://github.com/rehan-remade/universal-modder).
If `/plugin` doesn't list it, ask me to install it:
  /plugin marketplace add rehan-remade/universal-modder
  /plugin install universal-modder@universal-modder

How we use it here:
- Read its `mod-any-game` skill (Minecraft playbook) and run `um kb search "minecraft"` and `um kb search "tower defense"`.
- Read the field note `knowledge/games/minecraft/bloons-td-6-in-minecraft.md`. Treat it as a source of
  gotchas and testing ideas, NOT as a reason to re-architect: our design (server-authoritative match,
  vanilla navigation + HeartSeekGoal, data-driven content) stays. That mod is Forge 1.20.1; check every
  API against 26.1.2 with the minecraft-dev MCP server.
- Skip its reverse-engineering, Windows automation and game-recon skills; they don't apply.
- fal assets are optional and paid: don't call fal without asking me. Default to vanilla-based models
  and textures (vanilla renderers, retextures or tints).
- Follow its safety rules for publishing; run `um publish check` before any release build.
- Do not write or PR a field note yet; propose one at the end of the milestone instead.

Update docs/tech-stack.md to list universal-modder as the third tool (what we use, what we skip),
and clear the stale "open items" (mod ID and scaffold are done).

## 1. Housekeeping from docs/status.md (do first)

- Check the latest GitHub Actions result for `main`; fix CI if red.
- Wire in the minecraft-mcp bridge: it's built in tools/minecraft-mcp but not connected. Make the dev
  client load it in runClient only (never in the release jar), confirm the `minecraft-fabric` MCP server
  connects, and do one smoke check (start a match by command, take a screenshot).

## 2. Decisions (confirmed by me, 2026-10-27)

- **Commander view:** free switching from anywhere, also during waves, with a **cooldown**.
  This replaces "Command Post only during a wave". Cooldown length is data/config, not a constant.
  Decide what the Command Post block is still for (e.g. no cooldown / instant switch when standing at one)
  and propose it in the plan. Update BuildRules, tests and CLAUDE.md accordingly.
- **Tower repair:** a **repair kit item** bought at the Quartermaster and used on a damaged tower
  (separate from the Heart repair kit). Price and HP restored are data.
- **Boss:** **summons runners** (reuses the runner type). Summon cadence/count are data.
- **First map:** **two lanes merging** — two spawns, lanes join before the Heart, rich veins on the
  outer lanes, safe poor veins near the Heart, Command Post and Quartermaster near the Heart.
- Kept as is: Heart HP in match state (block is a marker), tower kills pay the owner and other kills pay
  nobody, veins pay coins directly, mobs are **data-driven vanilla mobs** (status.md proposal) unless
  you find a hard blocker — then tell me before switching to custom EntityTypes.

## 3. Plan before code

Propose a plan for milestone 4 and wait for my go-ahead. Scope:
- Four mob types (runner, brute, sapper, flyer) + the boss, as data-driven vanilla mobs. Each must
  demand a different answer.
- Tower HP with damage from brutes/sappers, wall breaking when no path to the Heart exists (hybrid
  pathing), flyers ignore the maze; tower repair kit.
- Commander cooldown rule (above).
- Next-wave preview during BUILD.
- Survival win and lose screens.
- Arena dimension with the two-lanes map, loaded from a structure template, with map metadata
  (Heart, spawns, veins, Command Post, Quartermaster, wave file) in a data file — replacing
  /heartguard setheart/addspawn for real play (keep the commands for tests).
- Sandbox mode for balancing (unlimited coins, spawn any wave).
- A 15-wave Survival set using the new mobs.

**Balance:** status.md shows waves are far too easy (Heart never below 100/100, coins left unspent).
Add BalanceGameTest rules that make difficulty measurable (e.g. Heart must take damage by wave N with a
"reasonable" build; coins must be tight), and use the `[full loop]` log plus Sandbox to tune. Final
tuning needs me playing — tell me when a build is ready for a playtest and what to watch for.

Split the work into branches like m3 (`m4-mobs`, `m4-tower-hp`, `m4-commander-cooldown`, `m4-preview`,
`m4-endscreens`, `m4-arena`, `m4-sandbox`, `m4-waves`), one merge per branch, in an order you justify.
Flag the riskiest piece (likely arena dimension + structure loading on 26.1.2) and spike it first if so.

## 4. Rules

- Every new system gets server GameTests; anything with real time or rendering gets a client GameTest.
- Check mixin / access-widener targets with minecraft-dev before writing them.
- Use the minecraft-mcp bridge in runClient for in-game checks (spawn waves, screenshots),
  but assertions in tests carry pass/fail.
- New datapack content goes through DataRegistry.
- Add any new gotcha to CLAUDE.md as you hit it.
- Commits have no Co-Authored-By trailer.
- Update docs/status.md at the end of each merged branch (keep its current format).

## 5. Finish

Summarize what was built, test results (`./gradlew build`, `./gradlew runClientGameTest`), what you
couldn't verify, and the hardest wave in the `[full loop]` log. Then propose milestone 5.
