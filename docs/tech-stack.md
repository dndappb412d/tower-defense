# Minecraft Tower Defense Mod — Tech Stack Decisions

_Decided: 2026-10-26_

## Target platform
- **Minecraft:** 26.1.2 (unobfuscated — Mojang official names, no Yarn/remapping)
- **Loader:** Fabric (Fabric Loader + Fabric API + Fabric Loom for 26.1.2)
- **Mappings:** Mojmap only (Yarn discontinued after 1.21.11)
- **Java:** JDK 25 (required for 26.x)
- **Build:** Gradle wrapper — `./gradlew build`, `./gradlew runClient`
- **Testing:** Fabric GameTest for automated checks (e.g. "tower kills zombie within N ticks")
- **Upgrade path:** 26.1.2 -> 26.2 later is a small jump; use minecraft-dev-mcp `compare_versions` to find breaks.

Why not 1.21.11: last obfuscated release; mods for it don't run on 26.1 even as compile-only deps, and Yarn would force a double migration.
Why not 26.2 yet: NeoForge 26.2 still beta/unstable; 26.1.2 is the stable baseline.

## MCP servers (Claude tooling)

### 1. minecraft-dev-mcp (MCDxAI) — code knowledge
- Decompiled MC source, full-text search, mixin / access-widener validation against real bytecode, version diffs.
- 26.1+ supported via Mojmap. Tested list stops at 26.1 snapshots — verify on first run against 26.1.2.
- Requires Node 18+, Java 17+. First decompile ~5 min, cache ~400-500 MB per version.
- Repo: https://github.com/MCDxAI/minecraft-dev-mcp

```json
"minecraft-dev": { "command": "npx", "args": ["-y", "@mcdxai/minecraft-dev-mcp"] }
```

### 2. minecraft-mcp Fabric bridge (InventivetalentDev) — runtime / in-game testing
- Fabric client mod built for MC 26.1.2 / Java 25 + Node MCP server over WebSocket (ws://localhost:25576).
- Tools: chat/commands, player state & movement, block get/place/break, nearby entities, screenshots, client reflection.
- Use: spawn waves via commands, verify tower targeting/kills, screenshot results.
- Build mod from source (`minecraft/fabric-mod`, `./gradlew build`), drop jar into dev client `mods/`. Dev-only, never ship.
- Binds 127.0.0.1; reflection gated by allow/block lists. Small repo (~11 stars) — review code before running.
- Requires Node 20+, tsx.
- Repo: https://github.com/InventivetalentDev/minecraft-mcp

```json
"minecraft-fabric": {
  "command": "node",
  "args": ["--import", "tsx/esm", "/path/to/minecraft-mcp/packages/mcp-server-fabric/src/index.ts"],
  "env": { "FABRIC_WS_URL": "ws://localhost:25576" }
}
```

### 3. universal-modder (rehan-remade) — Claude Code plugin, knowledge and release checks
- Repo: https://github.com/rehan-remade/universal-modder. Install: `/plugin marketplace add rehan-remade/universal-modder`, then `/plugin install universal-modder@universal-modder`.
- **What we use:**
  - The `mod-any-game` skill (Minecraft playbook) and its knowledge base: `um kb search "minecraft"`, `um kb search "tower defense"`.
  - Field notes, as a source of gotchas and testing ideas. First read: `knowledge/games/minecraft/bloons-td-6-in-minecraft.md`. That mod is Forge 1.20.1, so every API in it is checked against 26.1.2 with minecraft-dev before use. A note is a source of ideas, never a reason to change our architecture (server-authoritative match, vanilla navigation + `HeartSeekGoal`, data-driven content).
  - Its safety rules for publishing, and `um publish check` before any release build.
- **What we skip:** the reverse-engineering, Windows automation and game-recon skills; they do not apply to a mod we write from source.
- **Optional and paid:** fal asset generation. Never call it without asking first. Default to vanilla-based models and textures (reused vanilla renderers and models with retextures or tints).
- **Field notes:** do not write or PR one mid-milestone; propose one at the end of the milestone.
- **Installed:** `uv tool install git+https://github.com/rehan-remade/universal-modder` (v0.2.0, pure Python: hatchling build, pillow, numpy, pyyaml; no hooks or post-install steps). The executable is `C:\Users\MIXPC\.local\bin\um.exe`, which is not on PATH; call it by full path or run `uv tool update-shell`.
- **Notes worth rereading:** `games/minecraft/bloons-td-6-in-minecraft.md` (tower defense), `games/left-4-dead/left-4-dead-infected-and-tank-in-minecraft.md` (custom mobs and a boss), `games/fpv-logic/...` (Fabric on 26.x).

## Testing workflow

### Stage 1 — dev loop: Gradle `runClient` (primary)
- Launches dev client from source: debugger, fast rebuilds, readable logs.
- minecraft-mcp bridge mod runs here so Claude can test in-game without packaging.
- Fabric GameTest for automated regression checks.

### Stage 2 — release / integration: MultiMC 5
- **Release check:** built `.jar` in a clean Fabric 26.1.2 instance — catches missing deps, broken `fabric.mod.json`, things dev env hides.
- **Compatibility:** separate instance with popular mods (Sodium, JEI/REI, etc.) to check for conflicts.
- **Multiplayer:** two instances + local server to verify client/server sync (e.g. both players see towers fire).
- **Setup notes:**
  - Set Java 25 per instance.
  - If the game exits silently with no crash report, add JVM args Mojang's launcher passes but MultiMC doesn't:
    `-XX:StackShadowPages=32 --enable-native-access=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED`
    (reported for 26.3; apply on 26.1.2 if symptoms appear).
  - Fallback: Prism Launcher (maintained MultiMC fork, imports same instances) if MultiMC struggles with 26.x.

## Deliberately skipped
- mcmodding-mcp, chouzz minecraft-mod-dev plugin, minecraft-fabric-dev / skillshub skills — generic or Yarn-era; replaced by a project CLAUDE.md + Fabric docs (docs.fabricmc.net, 26.1.2).
- Fabric API MCP Server — overlaps with the bridge.
- mcdev-mcp (embeddedt) — NeoForge-oriented alternative, not needed on Fabric.

## Environment
- Primary harness: Claude Code in the project folder on the dev machine (MCP servers run locally).
- Prereqs: JDK 25, Node 20+, IntelliJ 2025.3+ (Java 25 support), MultiMC 5 for stage-2 testing.

## Decided since this document was written
- Mod ID `heartguard`, base package `dev.vadim.heartguard`. Project scaffold, `.mcp.json` and `CLAUDE.md` are done.
- The `minecraft-mcp` bridge is built (with hardening patches: no browser origins, per-launch token, no JDK reflection) but not wired into `runClient`; assertions live in GameTests, not in bridge sessions.
- Current development status: [status.md](status.md).
