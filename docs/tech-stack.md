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

## Open items
- Mod ID and base package name — not chosen yet.
- Scaffold project + `.mcp.json` + `CLAUDE.md`.
