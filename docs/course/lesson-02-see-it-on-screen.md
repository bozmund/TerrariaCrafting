# Lesson 2 — See it on screen

**At the end of this lesson:** you type `/craft` in game and your own GUI opens, showing your
inventory. This is the first time you see your mod.

**Prerequisite:** Lesson 1 complete and logging `terracraft:craft_browser`.

---

## The idea

In Lesson 1 you built the **menu** — the server-side object that owns slots and logic. It has
no idea what it looks like. Something has to draw it.

That is the **screen**: a client-only class that turns the menu's slots into pixels and turns
your mouse clicks into packets back to the server.

```
   SERVER                              CLIENT
   ------                              ------
   CraftBrowserMenu   <--- packets --->   CraftBrowserMenu   (mirror copy)
   (owns the items)                             |
                                                v
                                        CraftBrowserScreen   (draws it)
```

Both sides build a `CraftBrowserMenu`. The server's is authoritative; the client's is a mirror
kept in sync by packets. Only the client additionally builds a `CraftBrowserScreen`.

Two things to write, plus one to open it:

1. `CraftBrowserScreen` — the drawing
2. one line in `TerraCraftClient` — linking menu type to screen class
3. `ModCommands` — a `/craft` command so we have a way to open it today

> **Why a command and not the keybind?** A keybind needs networking, which is a whole concept.
> A command runs on the server already, so it can open a menu directly with zero networking.
> This gets you a working GUI one lesson earlier. Lesson 3 replaces it with the real keybind.

---

## Step 2.1 — `CraftBrowserScreen`

**File:** `src/client/java/com/eboac/terracraft/client/screen/CraftBrowserScreen.java`

Note the path: **`src/client`**, not `src/main`. Drawing code must not exist on a dedicated
server.

```java
package com.eboac.terracraft.client.screen;

import com.eboac.terracraft.menu.CraftBrowserMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class CraftBrowserScreen extends AbstractContainerScreen<CraftBrowserMenu> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    private static final int WIDTH = 176;
    private static final int HEIGHT = 114;

    public CraftBrowserScreen(CraftBrowserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.inventoryLabelY = HEIGHT - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, 17, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                this.leftPos, this.topPos + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
    }
}
```

### Line by line

**`extends AbstractContainerScreen<CraftBrowserMenu>`** — the generic parameter says which menu
this screen draws. That is what gives you a typed `this.menu` field later, so when you add
craftable-item logic you can read it without casting.

**The constructor signature `(CraftBrowserMenu, Inventory, Component)`** — required, exactly like
the menu's constructor was. Minecraft calls it with these three when the GUI opens. `Component`
is the window title (Minecraft's text type — supports translation keys and formatting; it is not
a `String`).

**`super(menu, inventory, title, WIDTH, HEIGHT)`** — the 5-argument version. In 26.3
`imageWidth`/`imageHeight` are `final` fields, so you *must* pass the size here; you cannot
assign them in the body. Older tutorials do `this.imageHeight = ...` — that no longer compiles.

- `176` is the standard Minecraft GUI width.
- `114` is the height for "no extra rows" — vanilla chests compute `114 + rows * 18`. We have no
  container slots of our own yet, only the player inventory, so `rows = 0`.

**`this.inventoryLabelY = HEIGHT - 94`** — where the word "Inventory" is drawn. Vanilla's formula.
Without it the label sits in the wrong place.

**`extractBackground(...)`** — this is the 26.3 drawing hook, and it is a real break from older
versions. Minecraft moved to a **render-state extraction** architecture for the Vulkan backend:
instead of drawing immediately, you *describe* what should be drawn into a
`GuiGraphicsExtractor`, and the renderer draws it later. The old `renderBg(GuiGraphics, ...)`
no longer exists. Vanilla's chest screen (`ContainerScreen`) overrides exactly this method — you
can Ctrl+N to it and compare.

**`super.extractBackground(...)`** — draws the dimmed/blurred world behind the GUI. Skip it and
your GUI floats over an undimmed world.

**The two `blit` calls** — `blit` copies a rectangle from a texture onto the screen. The
arguments are: render pipeline, texture, destination x, destination y, source u, source v,
width, height, full texture width, full texture height.

We use the vanilla chest texture and take two slices from it:
- rows `0..17` of the texture = the top border with the title area
- rows `126..222` of the texture = the player-inventory section

Stitching those two gives a correct-looking window with nothing in the middle. `256, 256` is the
size of the PNG file itself, which `blit` needs to convert pixel coordinates into texture
coordinates.

**`RenderPipelines.GUI_TEXTURED`** — tells the renderer "this is a flat textured GUI quad".
Different pipelines exist for text, untextured fills, etc.

---

## Step 2.2 — Link the menu type to the screen

**File:** `src/client/java/com/eboac/terracraft/client/TerraCraftClient.java`

```java
package com.eboac.terracraft.client;

import com.eboac.terracraft.client.screen.CraftBrowserScreen;
import com.eboac.terracraft.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class TerraCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.CRAFT_BROWSER, CraftBrowserScreen::new);
	}
}
```

**`MenuScreens.register(menuType, screenConstructor)`** — the lookup table the client uses when
the server says "open menu of type X": it finds the matching screen class and builds it.
`CraftBrowserScreen::new` is a method reference to your 3-argument constructor — which is
exactly why that constructor had to have that shape.

This goes in the **client** entrypoint. A dedicated server must never run this line; the classes
it mentions do not exist there.

> **Worth knowing:** `MenuScreens.register` is `private` in vanilla 26.3. It works for you only
> because Fabric API ships a *classtweaker* that re-opens it for mods. Classtweakers are the
> successor to access wideners — when you need something vanilla keeps private, that is the
> mechanism.

---

## Step 2.3 — A command to open it

**File:** `src/main/java/com/eboac/terracraft/command/ModCommands.java`

```java
package com.eboac.terracraft.command;

import com.eboac.terracraft.menu.CraftBrowserMenu;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public class ModCommands {

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("craft").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    player.openMenu(new SimpleMenuProvider(
                            (containerId, inventory, p) -> new CraftBrowserMenu(containerId, inventory),
                            Component.translatable("container.terracraft.craft_browser")));
                    return 1;
                })));
    }
}
```

### Line by line

**`CommandRegistrationCallback.EVENT.register(...)`** — Fabric's event for "the server is
building its command list, add yours". Events are how Fabric lets many mods hook the same point
without conflicting.

**`Commands.literal("craft")`** — a literal word in the command. `.executes(...)` attaches what
runs when someone types `/craft`.

**`context.getSource().getPlayerOrException()`** — commands can come from a command block or the
server console, which have no player. This throws a clean error message in that case instead of
crashing. Returning a `ServerPlayer` (not `Player`) matters: `ServerPlayer` is the server-side
subclass, and only it can open menus.

**`player.openMenu(...)`** — the one call that opens a GUI. It assigns a `containerId`, builds
the menu, and sends the client a packet saying "open menu type X, id N".

**`new SimpleMenuProvider(factory, title)`** — `openMenu` needs an object that can (a) build the
menu and (b) supply a title. `SimpleMenuProvider` is vanilla's ready-made pairing of those two.

**`(containerId, inventory, p) -> new CraftBrowserMenu(containerId, inventory)`** — the factory.
It receives three arguments (the third is the player) but we only need two, so `p` is ignored.

**`Component.translatable("container.terracraft.craft_browser")`** — a translation key, not
literal text, so the title can be localised. That key already exists in your
`assets/terracraft/lang/en_us.json` from the scaffold, mapped to "Crafting".

**`return 1`** — Brigadier (the command library) uses an int result; `1` means success, `0`
failure. Redstone comparators can read it.

---

## Step 2.4 — Call it

**File:** `src/main/java/com/eboac/terracraft/TerraCraft.java`

Add the import and the call:

```java
import com.eboac.terracraft.command.ModCommands;
```

```java
	@Override
	public void onInitialize() {
		ModMenus.init();
		ModCommands.init();
		LOGGER.info("Menu type registered as: {}", BuiltInRegistries.MENU.getKey(ModMenus.CRAFT_BROWSER));
	}
```

Same reason as `ModMenus.init()` — nothing else references `ModCommands`, so without this call
the class never loads and the command never registers.

---

## Verify

```
gradlew.bat runClient
```

1. Create a new creative world.
2. Type `/craft` and press Enter.

Your GUI opens: a window with a title bar reading "Crafting" and your full inventory below.
Drag items between inventory slots — that works, because the menu's slots are real slots and
the server is syncing them.

Shift-clicking does nothing. That is your `quickMoveStack` stub from Lesson 1, behaving exactly
as written.

### If something goes wrong

- **Command not found** — `ModCommands.init()` is not being called from `onInitialize`.
- **Crash on opening, "no screen registered"** — `MenuScreens.register` missing or in the wrong
  entrypoint.
- **GUI opens but is invisible / garbled** — check the `blit` coordinates against the snippet.
- **Slots in the wrong place** — the `addStandardInventorySlots(playerInventory, 8, 32)` offset
  in Lesson 1 must match this screen's `HEIGHT`.

---

## Commit

```
git add -A
git commit -m "Lesson 2: screen, screen registration, and /craft command"
```

---

**Next:** Lesson 3 — replace `/craft` with a real keybind. This is where you learn Minecraft's
networking, because a client is not allowed to open its own GUI.
