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

## Releasing

Pushing a tag beginning with `v` builds the mod and publishes it to Modrinth and
GitHub Releases. The version is taken from the tag, so nothing in the repository
needs editing to cut a release:

```
git tag -a v0.2.0 -m "What changed in this release"
git push origin v0.2.0
```

The tag's message becomes the changelog, so annotate it (`-a -m`) rather than
using a lightweight tag. A tag containing a hyphen, such as `v0.3.0-beta.1`, is
published as a pre-release.

### One-time setup

Two things have to exist before the first release, and both have to be done by
hand because they involve credentials:

1. **A Modrinth project.** Create it at <https://modrinth.com/dashboard/projects>,
   then copy its ID (Settings → General) into a repository *variable* named
   `MODRINTH_PROJECT_ID` (Settings → Secrets and variables → Actions → Variables).
2. **A Modrinth token.** Generate one at <https://modrinth.com/settings/pats> with
   the `Create versions` scope, then store it as a repository *secret* named
   `MODRINTH_TOKEN` on the same page, under Secrets.

`GITHUB_TOKEN` is provided automatically; nothing is needed for the GitHub
Release half.

The loader and supported Minecraft version are read from `fabric.mod.json`, so
they stay correct when the mod is ported without anyone remembering to update the
workflow.
