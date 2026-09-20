# Terraria-Style Crafting — Fabric mod for Minecraft 26.3

## Context

You want to learn Fabric modding by building a real mod, writing the code yourself. The mod replaces
Minecraft's grid crafting with a Terraria-style browser: a chest-like GUI listing every item you can
currently craft. Left-click a result → it lands on your cursor. Shift-click → straight to inventory.
A toggle switches between "only craftable" and "everything, uncraftable greyed out", like the recipe
book's filter. Vanilla grid crafting is removed entirely.

Decisions you made:
- **Craft anywhere.** A keybind opens the browser wherever you are. A nearby crafting table acts as a
  *station* that unlocks the recipes requiring it (Terraria semantics), not as the only entry point.
- **Ingredients come from your inventory *and* nearby containers** (chests, barrels, shulkers).
- Project lives at `C:\MyRepositories\TerrariaCrafting`.
- **Workflow:** I build the template/scaffold. Then, per step, I give you a brief + hints + which
  vanilla classes to read. You write the code. You hand it back, I review and rate it. If you'd rather
  I do a particular step, say so and I'll do it.

## Environment facts (verified on this machine)

| Thing | Value |
|---|---|
| Minecraft | 26.3 "Wilderness Bound" (released 2026-09-15) |
| Fabric Loom | 1.17 |
| Gradle | 9.6.0 (via wrapper) |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | **25 required** — Temurin 25.0.1 is installed and on PATH ✅ |
| Git | installed ✅ |

**Two gotchas that will bite immediately and that the plan handles up front:**

1. `JAVA_HOME` on this machine points at `C:\Program Files\Java\jdk-21`. Gradle would pick 21 and fail.
   Fix: pin `org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-25.0.1.8-hotspot` in
   `gradle.properties` (safer than changing your system env var).
2. **Minecraft 26.1+ is not obfuscated.** Yarn is dead; we use official Mojang names
   (`AbstractContainerMenu`, not `ScreenHandler`). Loom no longer remaps: the build script uses
   `implementation` instead of `modImplementation`, and the plain `jar` task instead of `remapJar`.
   Most tutorials and StackOverflow answers you find will be pre-26.1 Yarn and will *not* compile.
   Upside: you can read real Minecraft source with real parameter names in the IDE — which is how
   we'll actually learn the API, instead of trusting blog posts.

Because the API moved this much, every signature in this plan is **to be confirmed against decompiled
sources in the IDE** before you rely on it. I'll verify each one as we reach its step rather than
guessing now.

## Architecture

Server-authoritative, built on `AbstractContainerMenu` — the same machinery the chest uses. This is
not optional: `RecipeManager` only exists server-side, and a client-side implementation would be
trivially dupe-able and would break on multiplayer servers.

```
Keybind (client) ──C2S packet──► Server: player.openMenu(new SimpleMenuProvider(...))
                                   │
                                   ├─ scan ingredient pool (inventory + nearby containers)
                                   ├─ ask RecipeManager for all RecipeType.CRAFTING recipes
                                   ├─ test each against the pool
                                   └─ write the visible page of results into a display Container
                                          │
                                          ▼
                              vanilla slot sync ──► Screen renders it (free)
                                          ▲
Scroll / toggle filter ──clickMenuButton──┘
Click a result ──► menu.clicked() intercept ──► consume ingredients ──► setCarried() or inventory.add()
```

**Key insight for the GUI:** the result cells are *real* `Slot`s backed by a server-side display
`Container` that we refill as you scroll. That buys us vanilla's slot syncing, tooltips and rendering
for free. We then override `AbstractContainerMenu#clicked` to intercept clicks on those slots so they
craft instead of picking up the display stack.

**Reference classes to read (your primary learning material):** `StonecutterMenu` is the closest
vanilla analogue — a scrollable list of recipes, selection sent via `clickMenuButton`, result slot
that only gives you the item when you can pay for it. `LoomMenu` and `MerchantMenu` are the other two.
`StonecutterScreen` shows the scrollbar + button-grid rendering.

## Milestones

Each is a working, runnable state. You do not move on until the previous one runs.

### M0 — Scaffold *(I do this, autonomously)*
`C:\MyRepositories\TerrariaCrafting`, git-initialised, with:
- `build.gradle` (Loom 1.17, `implementation`, `jar`), `gradle.properties` (versions above + the
  `org.gradle.java.home` pin), Gradle 9.6.0 wrapper, `settings.gradle`
- `src/main/java/.../TerraCraft.java` (ModInitializer, `LOGGER`, `id(String)` helper)
- `src/client/java/.../TerraCraftClient.java` (ClientModInitializer)
- `fabric.mod.json`, `terracraft.mixins.json` + `terracraft.client.mixins.json`, icon, `.gitignore`
- Verified by actually running `./gradlew runClient` and confirming the game boots with the mod loaded.

Mod id: `terracraft`. Package: `com.eboac.terracraft`.

### M1 — An empty menu that opens *(you)*
Registry of a `MenuType`, an `AbstractContainerMenu` subclass with only the player-inventory slots, an
`AbstractContainerScreen` subclass with a chest texture, `MenuScreens.register`, a `KeyMapping` via
`KeyBindingHelper`, and a C2S `CustomPayload` that makes the server call `player.openMenu`.
*Learning:* registries, menu/screen split, client↔server boundary, `PayloadTypeRegistry`.

### M2 — The ingredient pool *(you)*
A class that walks the player's inventory and produces a counted multiset of available items.
*Learning:* `ItemStack` identity vs. components (two stacks are only interchangeable if
`isSameItemSameComponents`), and vanilla's own tool for this — `StackedItemContents` (the recipe
book's engine; I'll confirm its 26.3 name). Not a plain `Map<Item,Integer>`.

### M3 — Recipe matching *(you)*
Pull every `RecipeType.CRAFTING` recipe from the server's recipe manager, test each against the pool,
produce the ordered craftable list. Terraria ignores grid *shape*, so a shaped recipe is treated as
its flattened ingredient list.
*Learning:* `RecipeHolder`, `RecipeManager`, why this is server-only, `Ingredient` matching.

### M4 — The browser GUI *(you, with a worked hint on the scrollbar)*
9-wide grid of display slots, scrollbar, refill-on-scroll, `clickMenuButton` for scroll + the
craftable-only toggle, greyed-out rendering for uncraftable entries, tooltip showing the ingredient
list with have/need counts.
*Learning:* custom `Slot` subclasses, `quickMoveStack`, `broadcastChanges`, GUI rendering in the
post-Blaze3D/SDL world (26.3 replaced GLFW with SDL and moved render pipelines).

### M5 — Actually crafting *(you)*
Intercept `clicked`, consume ingredients, honour crafting remainders (bucket → empty bucket), place
the result on the cursor or into the inventory for `ClickType.QUICK_MOVE`, then recompute the pool
and the list. Fire the vanilla craft criterion/stats so advancements still work.
*Learning:* the hard part — atomic state changes, validating server-side that the client's click is
legal, and never trusting the client's index.

### M6 — Delete vanilla crafting *(you, mixins)*
Mixin `CraftingTableBlock#useWithoutItem` to open our menu; make `InventoryMenu`'s 2×2 slots refuse
items. Crafting tables in the world become stations.
*Learning:* Mixin — `@Inject`, `@Redirect`, `CallbackInfoReturnable`, and why IntelliJ 2025.3+ is
required for mixins to work in 26.x.

### M7 — Nearby containers + stations *(you)*
Scan a radius around the player for `Container` block entities, merge into the pool, consume from them
when crafting, and gate station-requiring recipes on a nearby crafting table.
*Learning:* `BlockPos.betweenClosed`, block-entity access, chunk-load safety, and keeping the scan
cheap enough to run on every craft.

## Files (after M0)

```
C:\MyRepositories\TerrariaCrafting\
├─ build.gradle, gradle.properties, settings.gradle, gradlew.bat
├─ src/main/java/com/eboac/terracraft/
│   ├─ TerraCraft.java                         M0
│   ├─ registry/ModMenus.java                  M1
│   ├─ menu/TerraCraftMenu.java                M1→M5
│   ├─ menu/CraftResultSlot.java               M4
│   ├─ craft/IngredientPool.java               M2, M7
│   ├─ craft/RecipeScanner.java                M3
│   ├─ net/OpenBrowserPayload.java             M1
│   └─ mixin/CraftingTableBlockMixin.java      M6
├─ src/client/java/com/eboac/terracraft/client/
│   ├─ TerraCraftClient.java                   M0→M1
│   ├─ ModKeybinds.java                        M1
│   └─ screen/TerraCraftScreen.java            M1→M4
└─ src/main/resources/ (fabric.mod.json, mixins json, lang, textures)
```

## Verification

- Per milestone: `./gradlew runClient` from the project root, open a creative world, exercise the
  feature by hand. `./gradlew build` must stay green.
- M5 onward also needs a **dedicated-server check**: `./gradlew runServer`, connect with the client,
  and confirm crafting works and does not dupe when you spam-click. Client-only correctness is a lie.
- I review each hand-off: correctness, does it match how vanilla does it, dupe/desync holes,
  performance of the per-craft rescan — with a rating and specific line-level notes.

## Review contract

When you hand me code, I rate it on: **correctness**, **API idiom** (is this how Mojang/Fabric does
it), **security/dupe-safety**, **performance**, **readability**. I'll tell you plainly what's wrong
and why, point at the vanilla class that does it properly, and won't rewrite your code unless you ask.

## First action after approval

Move this session to `C:\MyRepositories\TerrariaCrafting` (via the directory tool — you'll approve the
folder), scaffold M0, run `runClient` to prove it boots, then hand you the M1 brief.
Per your global convention, this plan gets saved to the repo's `plans/` folder once created.
