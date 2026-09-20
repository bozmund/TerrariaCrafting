# Terraria Crafting

A Fabric mod for Minecraft 26.3 that replaces grid crafting with a Terraria-style browser:
one screen listing every item you can currently craft from your inventory and nearby containers.
Click a result to put it on your cursor, shift-click to send it straight to your inventory.

## Requirements

- Java 25 (Temurin 25 is pinned in `gradle.properties`)
- IntelliJ IDEA 2025.3 or newer -- required for Mixin's annotation processor on MC 26.x

## Running

```
gradlew.bat runClient
gradlew.bat runServer
```

## Notes for this codebase

Minecraft 26.1+ ships unobfuscated, so we use **official Mojang names** (`AbstractContainerMenu`,
not Yarn's `ScreenHandler`) and Loom does **not** remap the jar -- hence `implementation` instead of
`modImplementation` in `build.gradle`, and the plain `jar` task instead of `remapJar`.
Pre-26.1 tutorials will not compile against this project.

See `plans/` for the implementation roadmap.
