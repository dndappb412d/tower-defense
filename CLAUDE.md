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
- Run `./gradlew build` and `./gradlew runClientGameTest` before pushing client changes. CI only runs `build` (client tests need a display).

## Tooling rules

- Before writing a mixin or access widener, check the target against the decompiled source with the `minecraft-dev` MCP server. Do not guess signatures.
- The `minecraft-mcp` bridge mod (`tools/minecraft-mcp`, gitignored) is dev-only. Never put it in the release jar, never add it as a normal dependency, and never ship it in `mods/` of a release instance. It exposes an unauthenticated reflection API on `ws://127.0.0.1:25576`, so run it only during dev sessions.
