# M1 — An empty menu that opens

**Goal:** press a key, a chest-like GUI opens showing your inventory and nothing else.
No crafting yet. When this works you understand the single most important structure in
Minecraft modding: the menu/screen split.

## The concept you must understand first

A "GUI with slots" in Minecraft is **two objects on two sides of the network**:

| | `AbstractContainerMenu` (menu) | `AbstractContainerScreen` (screen) |
|---|---|---|
| Lives in | `src/main` — **both sides** | `src/client` — **client only** |
| Knows about | slots, items, game logic | pixels, mouse, textures |
| Who decides what happens | this one | never this one |

The server owns the truth. When you click a slot, the client sends a packet; the **server**
runs `menu.clicked(...)`, changes the items, and syncs the result back. The screen only draws.

This is why our mod must be built this way: a client-only implementation would let anyone with
a modified client craft anything for free, and `RecipeManager` does not exist client-side in a
form we can trust.

Both sides construct a menu instance. The server's instance is the real one. The client's is a
mirror, created from the packet the server sends when the menu opens.

## What you are building

1. `registry/ModMenus.java` — registers a `MenuType`
2. `../../src/main/java/com/eboac/terracraft/menu` — extends `AbstractContainerMenu`
3. `client/screen/CraftBrowserScreen.java` — extends `AbstractContainerScreen<CraftBrowserMenu>`
4. `net/OpenBrowserPayload.java` — a client-to-server packet
5. `client/ModKeyMappings.java` — the keybind
6. wire-up in `TerraCraft.onInitialize()` and `TerraCraftClient.onInitializeClient()`

## Verified 26.3 signatures

I read these out of the decompiled jars in this project — **not** from a tutorial. Anything you
find online from 1.21 or earlier will differ.

```java
// net.minecraft.world.inventory.AbstractContainerMenu
protected Slot      addSlot(Slot slot);
protected void      addStandardInventorySlots(Container container, int x, int y); // player inv + hotbar in one call
public abstract ItemStack quickMoveStack(Player player, int slotIndex);           // shift-click; MUST override
public abstract boolean   stillValid(Player player);                              // MUST override
public void         clicked(int slotId, int button, ContainerInput input, Player player);
public boolean      clickMenuButton(Player player, int id);
public void         setCarried(ItemStack stack);   // the stack on the cursor
public ItemStack    getCarried();
public void         broadcastChanges();

// net.minecraft.world.inventory.MenuType -- the constructor is PRIVATE in vanilla, but
// fabric-menu-api-v1 ships a classtweaker that re-opens it, so this compiles:
new MenuType<>(MenuType.MenuSupplier<T>, FeatureFlagSet.of())
// MenuSupplier is:  T create(int containerId, Inventory playerInventory)

// net.minecraft.client.gui.screens.MenuScreens -- also re-opened by that classtweaker
MenuScreens.register(MenuType<T>, MenuScreens.ScreenConstructor<T, U>);
// ScreenConstructor is:  U create(T menu, Inventory inventory, Component title)

// net.minecraft.client.KeyMapping
new KeyMapping(String translationKey, int keyCode, KeyMapping.Category category);
public boolean consumeClick();   // true once per press; call it in a tick loop

// net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
static KeyMapping registerKeyMapping(KeyMapping);

// net.minecraft.world.entity.player.Player
public OptionalInt openMenu(MenuProvider provider);   // server-side call
// net.minecraft.world.SimpleMenuProvider
new SimpleMenuProvider(MenuConstructor, Component displayName)
```

### Things that CHANGED in 26.x — do not trust old code

- `ClickType` **no longer exists**. It is now `ContainerInput`
  (`PICKUP`, `QUICK_MOVE`, `SWAP`, `CLONE`, `THROW`, `QUICK_CRAFT`, `PICKUP_ALL`).
- `ResourceLocation` is now `Identifier` (`net.minecraft.resources.Identifier`).
  Use `TerraCraft.id("...")`.
- `KeyMapping`'s category is a `KeyMapping.Category` object, not a `String`.
  `KeyMapping.Category.INVENTORY` exists, or register your own with
  `KeyMapping.Category.register(Identifier)`.
- GLFW was replaced with **SDL**. Never hardcode a key number — use
  `com.mojang.blaze3d.platform.InputConstants`.
- Fabric's screen-handler API is now the **menu** API: `net.fabricmc.fabric.api.menu.v1`, and
  `ExtendedScreenHandlerType` is `ExtendedMenuType`. You do **not** need it for M1 — only when
  the server must send extra data at open time.

## Hints, in the order you will need them

**1. Registration.** Everything registered in Minecraft goes into a registry under a namespaced
id. The menu registry is `BuiltInRegistries.MENU`. The pattern is
`Registry.register(registry, id, thing)`, and it must happen from the **common** entrypoint,
because the server needs it too. Make the `MenuType` a `public static final` field, and make
sure the class is actually loaded — a static field in a class nobody touches never initializes.
(A common trick: an empty `static void init() {}` you call from `onInitialize`.)

**2. The menu's two constructors.** You need two:
- `(int containerId, Inventory playerInventory)` — the one `MenuSupplier` calls. This is the
  **client**-side constructor.
- optionally one taking extra server-side context, which delegates to the first.

Both must add the *same slots in the same order*, or client and server will disagree about what
slot index 7 is and you will get baffling bugs.

**3. `quickMoveStack` is abstract — you must implement it.** Returning `ItemStack.EMPTY` means
"nothing moved" and is the correct way to stop vanilla's loop, which calls it repeatedly until
it gets `EMPTY`. Read `ChestMenu#quickMoveStack` to see the real pattern. For M1, "shift-click
does nothing" is a fine answer — but write it deliberately, not by accident.

**4. `stillValid`** decides whether the menu stays open. For a craft-anywhere menu, `return
true;` is defensible — but think about whether it should close when the player dies or changes
dimension.

**5. The screen.** `AbstractContainerScreen` has `imageWidth`/`imageHeight` (the GUI texture
size) and `leftPos`/`topPos` (computed for you in `init()`). Set `imageHeight` in the
constructor *before* `init()` runs, and remember `inventoryLabelY` needs adjusting when you
change the height. For M1, reuse a vanilla texture:
`Identifier.withDefaultNamespace("textures/gui/container/generic_54.png")`.

**6. The packet.** Fabric's networking:
- Make a `record` implementing `CustomPayload` with a `CustomPayload.Type<...>` id.
- Register it on **both** sides with `PayloadTypeRegistry.playC2S().register(id, codec)` — from
  the common entrypoint, not the client one.
- Handle it with `ServerPlayNetworking.registerGlobalReceiver(...)`.
- **Critical:** the handler runs on the netty thread. You must hop to the server thread before
  touching game state. Fabric's context object gives you the player and a way to do this — look
  at what it offers.
- For a payload with no fields, `StreamCodec.unit(...)` is your friend.

**7. Security.** The packet carries no data, and that is deliberate: the client is only allowed
to say *"I pressed the key"*. The server decides everything else. Every time you are tempted to
send a value from the client, ask what a hacked client would send instead.

## How to know it works

`gradlew.bat runClient`, new creative world, press your key. A chest-like window with your
inventory should open. Items should be draggable between your inventory slots. Escape closes it.

Then, the test people skip: `gradlew.bat runServer`, connect to `localhost`, and do it again.

## Hand it to me when

...it opens, or when you are stuck and want a nudge rather than an answer. Tell me which you
want — I will review without rewriting unless you ask.
