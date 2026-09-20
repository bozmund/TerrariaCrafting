# Lesson 3 — Open it with a key

**At the end of this lesson:** you press **B** anywhere in the world and your GUI opens.

**Prerequisite:** Lesson 2 complete and `/craft` working.

---

## The idea, and why this is harder than it looks

You might expect the keybind to just call `openScreen(new CraftBrowserScreen(...))` on the
client. That is how you would do it in a normal app, and it is wrong here.

A screen is only a *view*. Its menu must exist on the server, because the server owns the items.
If the client conjured up its own screen, the server would know nothing about it: every slot
would be empty, every click would be rejected, and nothing you did would persist.

**Only the server can open a menu.** So the flow has to be:

```
   CLIENT                                    SERVER
   ------                                    ------
   press B
      |
      v
   send OpenBrowserPayload  ------------->  receiver runs
                                                |
                                                v
                                           player.openMenu(...)
                                                |
   screen opens      <-------------------  "open menu type X, id N"
```

The client's only job is to say *"I pressed the key."* The server decides everything else.

> **This is the security model, not bureaucracy.** A modified client can send any packet it
> likes. Because our packet carries no data — it is literally an empty message — there is
> nothing a cheater can lie about. Every time you are tempted to send a value from the client,
> ask what a hacked client would send instead.

Four things to write:

1. `OpenBrowserPayload` — the empty message
2. `ModNetworking` — registers the message and handles it server-side
3. `ModKeyMappings` — the key itself
4. tick polling in `TerraCraftClient` — detect the press and send

---

## Step 3.1 — The payload

**File:** `src/main/java/com/eboac/terracraft/net/OpenBrowserPayload.java`

`src/main`, not client — both sides must know the message format.

```java
package com.eboac.terracraft.net;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenBrowserPayload() implements CustomPacketPayload {

    public static final Type<OpenBrowserPayload> TYPE =
            new Type<>(TerraCraft.id("open_browser"));

    public static final StreamCodec<Object, OpenBrowserPayload> CODEC =
            StreamCodec.unit(new OpenBrowserPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

### Line by line

**`public record OpenBrowserPayload()`** — a Java record with **no components**: it carries no
data. Records are the natural fit for packets, which are pure data. Empty is deliberate here.

**`implements CustomPacketPayload`** — Minecraft's interface for mod-defined packets. Vanilla
bundles them all into one "custom payload" packet type and routes by id.

**`Type<OpenBrowserPayload> TYPE`** — the id, `terracraft:open_browser`, again namespaced so two
mods can both have an "open_browser" packet.

**`StreamCodec CODEC`** — a codec tells Minecraft how to write the message to bytes and read it
back. Since there is no data to write, `StreamCodec.unit(value)` gives a codec that writes
nothing and always decodes to the value you gave it. For a payload with fields you would build a
real codec describing each one.

**`type()`** — required by the interface so the receiving side can look up which handler to run.

---

## Step 3.2 — Register and handle it

**File:** `src/main/java/com/eboac/terracraft/net/ModNetworking.java`

```java
package com.eboac.terracraft.net;

import com.eboac.terracraft.menu.CraftBrowserMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;

public class ModNetworking {

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(OpenBrowserPayload.TYPE, OpenBrowserPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenBrowserPayload.TYPE, (payload, context) ->
                context.player().openMenu(new SimpleMenuProvider(
                        (containerId, inventory, p) -> new CraftBrowserMenu(containerId, inventory),
                        Component.translatable("container.terracraft.craft_browser"))));
    }
}
```

### Line by line

**`PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC)`** — declares "a packet with this
id exists, going client-to-server, during normal play, encoded like this."

`serverbound` = travelling *to* the server. `Play` = the normal in-game phase (as opposed to
login or configuration). This runs from the **common** entrypoint, so both sides learn the
format — the client needs it to encode, the server to decode.

> Older tutorials call this `PayloadTypeRegistry.playC2S()`. That name is gone in this version.

**`ServerPlayNetworking.registerGlobalReceiver(TYPE, handler)`** — what to do when the packet
arrives. "Global" means it applies to every player connection, present and future.

**`context.player()`** — the `ServerPlayer` who sent it. You did not have to trust the client to
tell you who it is; the connection already knows. This is why the packet needs no data.

**`openMenu(new SimpleMenuProvider(...))`** — identical to what `/craft` does in Lesson 2. Both
entry points converge on the same server-side call.

> **A thread note you will need later.** Fabric runs this handler on the server's main thread
> for you, so touching game state here is safe. That is not automatic in all networking APIs —
> if you ever see `context.server().execute(() -> ...)` in other mods, that is manually hopping
> to the main thread. Doing game logic on a network thread causes rare, impossible-to-reproduce
> corruption.

Now call it from **`TerraCraft.java`**:

```java
import com.eboac.terracraft.net.ModNetworking;
```
```java
		ModMenus.init();
		ModCommands.init();
		ModNetworking.init();
```

---

## Step 3.3 — The keybind

**File:** `src/client/java/com/eboac/terracraft/client/ModKeyMappings.java`

```java
package com.eboac.terracraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public class ModKeyMappings {

    public static KeyMapping openBrowser;

    public static void init() {
        openBrowser = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.terracraft.open_browser",
                InputConstants.KEY_B,
                KeyMapping.Category.INVENTORY
        ));
    }
}
```

### Line by line

**`KeyMappingHelper.registerKeyMapping(...)`** — Fabric's hook to add your key to the vanilla
Controls list, so players can rebind it. Vanilla has no mod-facing API for this.

**`"key.terracraft.open_browser"`** — a translation key, already in your `en_us.json` as
"Open Crafting Browser". It is what appears in the Controls screen.

**`InputConstants.KEY_B`** — the default key. **Never hardcode a raw number.** 26.3 replaced
GLFW with SDL and the numeric key codes changed; `InputConstants` gives you the correct ones for
this version.

**`KeyMapping.Category.INVENTORY`** — which section of the Controls screen it appears under. In
26.x this is an object, not a string as in older versions. You can make your own with
`KeyMapping.Category.register(Identifier)`.

**Why is `openBrowser` not `final`?** It is assigned inside `init()` rather than at declaration,
because registration must happen at the right point in client startup, not at class-load time.

---

## Step 3.4 — Detect the press and send

**File:** `src/client/java/com/eboac/terracraft/client/TerraCraftClient.java`

```java
package com.eboac.terracraft.client;

import com.eboac.terracraft.client.screen.CraftBrowserScreen;
import com.eboac.terracraft.net.OpenBrowserPayload;
import com.eboac.terracraft.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class TerraCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.CRAFT_BROWSER, CraftBrowserScreen::new);
		ModKeyMappings.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ModKeyMappings.openBrowser.consumeClick()) {
				ClientPlayNetworking.send(new OpenBrowserPayload());
			}
		});
	}
}
```

### Line by line

**`ClientTickEvents.END_CLIENT_TICK`** — runs 20 times a second on the client. Keybinds do not
fire callbacks; you have to *ask* them each tick whether they were pressed.

**`while (... .consumeClick())`** — the part everyone gets wrong. `consumeClick()` returns true
once per unprocessed press and decrements an internal counter. If the game lagged and the key
was pressed three times since the last tick, it returns true three times. A `while` loop drains
the queue; an `if` would silently swallow presses. `isDown()` is different — that is "is the key
held right now", which would fire every tick while held.

**`ClientPlayNetworking.send(new OpenBrowserPayload())`** — sends it. Fire and forget; the reply
is the server opening the menu.

---

## Verify

```
gradlew.bat runClient
```

1. New creative world.
2. Press **B**. The GUI opens.
3. `/craft` still works — two entry points, one server-side path.
4. Check **Options → Controls → Inventory**: "Open Crafting Browser" is listed and rebindable.

Then the test people skip:

```
gradlew.bat runServer
```

Let it generate a world, then connect from the client to `localhost`. Press B. It must work
there too. This is the real proof the client/server split is right — a mod that only works in
singleplayer usually has logic on the wrong side.

### If something goes wrong

- **Nothing happens on B** — is `ModKeyMappings.init()` called? Is the tick handler registered?
- **Crash: "unknown payload"** — `ModNetworking.init()` not called, or registered in the client
  entrypoint instead of the common one.
- **Works in singleplayer, not on the server** — something client-only is being relied on for
  game state. That is exactly the bug this architecture exists to prevent.

---

## Commit

```
git add -A
git commit -m "Lesson 3: keybind and networking to open the browser"
```

---

## M1 complete

You now have a working custom GUI, opened from a keybind, correct on a dedicated server. The
structure you built is what everything else hangs off:

| You built | You will use it for |
|---|---|
| `CraftBrowserMenu` | holding the craftable list, handling craft clicks (M4, M5) |
| `CraftBrowserScreen` | the scrollable grid, greyed-out items, tooltips (M4) |
| server-authoritative flow | making crafting dupe-proof (M5) |
| the keybind | craft-anywhere, Terraria-style (already done) |

**Next:** M2 — work out what the player actually has, so we can work out what they can craft.
