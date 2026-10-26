We're starting a new Minecraft mod project in this directory: a tower defense game with an FPS mode.
Read docs/design.md (game design) and docs/tech-stack.md (decided stack and tooling) first. They are the source of truth.

Goal of this session: a working, empty project skeleton, ready for milestone 1 (camera spike).
Do NOT implement gameplay yet.

1. Ask me for the mod ID and base Java package before creating any files.
   Suggest 2–3 options if I don't have one.

2. Scaffold a Fabric mod for Minecraft 26.1.2:
   - Look up the current versions of Fabric Loader, Fabric API and Fabric Loom for 26.1.2
     (fabricmc.net/develop, meta.fabricmc.net). Don't guess versions.
   - Mojang official mappings only (MC 26.x is unobfuscated; no Yarn).
   - Java 25 toolchain, Gradle wrapper.
   - Split client/common source sets (Fabric's splitEnvironmentSourceSets).
   - Minimal entrypoints (main + client) that just log on init.
   - Fabric GameTest set up with one trivial passing test.
   - fabric.mod.json, .gitignore for Gradle/IntelliJ/run/.

3. Verify the setup: `./gradlew build` and the GameTest pass.
   Tell me when it's ready for me to run `./gradlew runClient`, since that opens a game window on my machine.

4. Create CLAUDE.md with:
   - Project summary and a pointer to docs/design.md and docs/tech-stack.md.
   - Stack rules: MC 26.1.2, Fabric, Mojmap only, Java 25.
   - Architecture rules:
     - Server-authoritative. Waves, economy, pathing, tower logic and Heart HP live on the server; the client renders and sends intents.
     - Content is data-driven (waves, tower stats, shop prices and map metadata in JSON/datapacks).
   - Commands: build, runClient, GameTest.
   - Before writing a mixin or access widener, check the target against the decompiled source with the minecraft-dev MCP server.
   - Keep the minecraft-mcp bridge mod dev-only; never include it in the release jar.

5. Create .mcp.json with the two servers from docs/tech-stack.md:
   - minecraft-dev (npx @mcdxai/minecraft-dev-mcp)
   - minecraft-fabric (InventivetalentDev/minecraft-mcp bridge)
   For the bridge, clone the repo into tools/minecraft-mcp (gitignored) and point the server path there.
   Show me a short summary of what its Fabric mod and MCP server do (network, reflection, file access) before building it.
   I want to review it before it runs.

6. Initialize git and make the first commit.

7. Finish with a short summary: what was created, versions used, anything you couldn't verify.
   Then propose a plan for milestone 1 (commander view: top-down camera, free cursor, block raycast for placement) and wait for my go-ahead.
