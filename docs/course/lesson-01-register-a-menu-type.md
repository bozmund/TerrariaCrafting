# Lesson 1 — Register a menu type

**At the end of this lesson:** your project compiles with zero errors, the game starts, and
your log prints `Menu type registered as: terracraft:craft_browser`.

**You will not see a GUI yet.** That is Lesson 2. This lesson is about making the game *know
your menu exists*. Nothing can open a menu the game has never heard of.

---

## The idea

Minecraft keeps central lists called **registries** — one for blocks, one for items, one for
"kinds of GUI". Before anything can use your GUI, its *kind* must be in the registry under a
unique name, so the client and the server both agree that `terracraft:craft_browser` means the
same thing.

Think of it like this:

| Concept | Analogy | Class |
|---|---|---|
| The **kind** of GUI | The blueprint | `MenuType<CraftBrowserMenu>` |
| One **open** GUI | A house built from it | `CraftBrowserMenu` instance |

You register the blueprint once, at startup. The game builds instances from it whenever a
player opens the GUI.

You are writing three things:

1. `CraftBrowserMenu` — the class describing one open GUI
2. `ModMenus` — a holder that registers the blueprint
3. one line in `TerraCraft` — to make sure #2 actually runs

---

## Step 1.1 — `CraftBrowserMenu`

**File:** `src/main/java/com/eboac/terracraft/menu/CraftBrowserMenu.java`

```java
package com.eboac.terracraft.menu;

import com.eboac.terracraft.registry.ModMenus;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class CraftBrowserMenu extends AbstractContainerMenu {

    public CraftBrowserMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.CRAFT_BROWSER, containerId);
        addStandardInventorySlots(playerInventory, 8, 32);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
```

`ModMenus` does not exist yet, so this file will be red until Step 1.2. That is expected and
it is the last time in this course I will leave you sitting on an error between steps.

### Line by line

**`extends AbstractContainerMenu`** — Minecraft's base class for every slot-based GUI. Chests,
furnaces and crafting tables all extend it. You inherit the slot list, the item on your cursor,
and all the click plumbing, so you never write that yourself.

**`public CraftBrowserMenu(int containerId, Inventory playerInventory)`** — this exact shape is
required. In Step 1.2 you hand Minecraft a reference to this constructor, and it calls it with
exactly these two arguments whenever a menu of this type is created.

- `containerId` — a small number the server assigns each time a GUI opens, so packets saying
  "slot 3 of container 2" are unambiguous. You never invent it; it arrives as a parameter.
- `playerInventory` — the inventory of the player who opened this GUI.

It is `public`, not `protected`, because `ModMenus` lives in a different package and Java's
`protected` would block it.

**`super(ModMenus.CRAFT_BROWSER, containerId)`** — tells the parent class "I am a menu of kind
`CRAFT_BROWSER`, open-instance number `containerId`". The parent stores the kind and later uses
it to tell the client which screen to draw.

**`addStandardInventorySlots(playerInventory, 8, 32)`** — a helper on the parent class that adds
all 36 player inventory slots (3 rows + hotbar) in one call, positioned with their top-left at
pixel `(8, 32)` inside the GUI. Without this your GUI would have no slots at all.

> **Slot order matters enormously.** The client and the server each build their own instance of
> this menu and must add identical slots in an identical order, because packets refer to slots
> by *index*. If they disagree, clicking slot 7 moves a different item on each side. Adding all
> slots in the constructor — which both sides run — is what keeps them in sync.

**`quickMoveStack`** — called when you shift-click a slot. Vanilla calls it repeatedly until it
returns `ItemStack.EMPTY`, which means "nothing more to move". Returning `EMPTY` immediately is
a correct, deliberate "shift-click does nothing for now". You will write the real version in M5.

**`stillValid`** — asked every tick: should this GUI stay open? A chest returns false when you
walk too far away. Our browser can be opened anywhere, so `true` is right.

---

## Step 1.2 — `ModMenus`

**File:** `src/main/java/com/eboac/terracraft/registry/ModMenus.java`

```java
package com.eboac.terracraft.registry;

import com.eboac.terracraft.TerraCraft;
import com.eboac.terracraft.menu.CraftBrowserMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

public class ModMenus {

    public static final MenuType<CraftBrowserMenu> CRAFT_BROWSER = Registry.register(
            BuiltInRegistries.MENU,
            TerraCraft.id("craft_browser"),
            new MenuType<>(CraftBrowserMenu::new, FeatureFlagSet.of())
    );

    public static void init() {
    }
}
```

After saving this, the red error in `CraftBrowserMenu` disappears.

### Line by line

**`BuiltInRegistries.MENU`** — the game's master list of GUI kinds.

**`TerraCraft.id("craft_browser")`** — builds `terracraft:craft_browser`. The `terracraft:`
prefix is what stops your menu colliding with another mod that also wants "craft_browser".

**`new MenuType<>(CraftBrowserMenu::new, FeatureFlagSet.of())`** — the blueprint object.
- `CraftBrowserMenu::new` is a **method reference**: a value meaning "the constructor of
  `CraftBrowserMenu`". Minecraft stores it and calls it later. This is exactly why the
  constructor had to be `(int, Inventory)` — that is the shape `MenuType` calls.
- `FeatureFlagSet.of()` — an empty set meaning "not locked behind an experimental toggle".
  Vanilla uses this for features gated in snapshots. You want empty.

**`Registry.register(registry, id, thing)`** — does the registering, and returns the thing back,
which is why the whole call can be one expression assigned to `CRAFT_BROWSER`.

**`public static final`** — `static` because there is exactly one blueprint shared by the whole
game; `final` because re-registering would be a bug; `public` so `CraftBrowserMenu` can read it.

**The empty `init()` — the part that looks pointless and is not.** Java only runs a class's
`static` field initialisers the first time something *touches* that class. If nothing ever
mentions `ModMenus`, the JVM never loads it, `Registry.register` never runs, and your menu is
silently absent — you would not find out until a player opened it and the game crashed.
Calling `ModMenus.init()` at startup forces the class to load at a moment you control.

---

## Step 1.3 — Call it, and prove it worked

**File:** `src/main/java/com/eboac/terracraft/TerraCraft.java`

```java
package com.eboac.terracraft;

import com.eboac.terracraft.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerraCraft implements ModInitializer {
	public static final String MOD_ID = "terracraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModMenus.init();
		LOGGER.info("Menu type registered as: {}", BuiltInRegistries.MENU.getKey(ModMenus.CRAFT_BROWSER));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
```

The log line is a **read-back**: instead of trusting that registration worked, it asks the
registry "what name is this object filed under?" If registration had failed, this would print
`null`. Getting the right name back is proof.

Registration happens here, in the **common** entrypoint, not the client one, because a
dedicated server needs the menu type too — the server is the side that actually creates menus.

---

## Verify

```
gradlew.bat runClient
```

In the console output, find:

```
[Render thread/INFO] (terracraft) Menu type registered as: terracraft:craft_browser
```

If it says `null`, registration did not happen — check that `ModMenus.init()` is actually
called. If the game crashes at startup, read the stack trace from the top; the first line
mentioning `com.eboac.terracraft` is almost always the real cause.

Close the game.

---

## Commit

```
git add -A
git commit -m "Lesson 1: register the craft browser menu type"
```

---

**Next:** Lesson 2 — write the screen that draws this menu, and a `/craft` command to open it.
At the end of Lesson 2 you will be looking at your own GUI in-game.
