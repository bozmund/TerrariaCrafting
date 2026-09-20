package com.eboac.terracraft.menu;

import com.eboac.terracraft.craft.CraftAttempt;
import com.eboac.terracraft.craft.CraftEntry;
import com.eboac.terracraft.craft.CraftingPlanner;
import com.eboac.terracraft.craft.IngredientPool;
import com.eboac.terracraft.craft.RecipeScanner;
import com.eboac.terracraft.net.BrowserStatePayload;
import com.eboac.terracraft.registry.ModMenus;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * The Terraria-style crafting browser.
 *
 * <p>The grid at the top is not storage -- it is a *view* of the recipes the player could make.
 * Each cell holds a copy of a recipe's result purely so vanilla's slot syncing will draw it for
 * us. Clicking a cell does not pick the item up; it is intercepted in {@link #clicked} and turned
 * into an actual craft.
 *
 * <p>Everything that decides what exists or what is affordable runs on the server. The client
 * gets told the answers and draws them.
 */
public class CraftBrowserMenu extends AbstractContainerMenu {

    public static final int COLUMNS = 9;
    public static final int VISIBLE_ROWS = 5;
    public static final int DISPLAY_SLOTS = COLUMNS * VISIBLE_ROWS;

    /** Button id sent by the client when the "hide uncraftable" toggle is clicked. */
    public static final int BUTTON_TOGGLE_FILTER = 0;

    private final Player player;

    /** Backing store for the 45 display cells. Server writes it; client receives it. */
    private final SimpleContainer display = new SimpleContainer(DISPLAY_SLOTS);

    /**
     * Upper bound on one shift-click. Without it, a chest wall of logs would run thousands of
     * crafts inside a single packet handler and stall the server tick.
     */
    private static final int MAX_BULK_CRAFTS = 256;

    // ---- server-side state ----
    private List<CraftEntry> entries = List.of();
    private RecipeScanner.ScanResult scan;
    private IngredientPool pool;

    // ---- shared state (client mirrors it via BrowserStatePayload) ----
    private int scrollRow;
    private boolean showUncraftable = false;
    private String search = "";
    private int totalEntries;
    /** Visible cells that can be made right now. */
    private long craftableMask;
    /** Visible cells reachable only by running intermediate crafts first. */
    private long chainMask;

    public CraftBrowserMenu(int containerId, Inventory playerInventory) {
        super(ModMenus.CRAFT_BROWSER, containerId);
        this.player = playerInventory.player;

        for (int row = 0; row < VISIBLE_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                addSlot(new CraftResultSlot(display, row * COLUMNS + column,
                        8 + column * 18, 32 + row * 18));
            }
        }

        addStandardInventorySlots(playerInventory, 8, 140);

        if (!player.level().isClientSide()) {
            rebuild();
        }
    }

    // ------------------------------------------------------------------
    // Server-side recomputation
    // ------------------------------------------------------------------

    /**
     * Full rescan: rebuilds the recipe list and its order from scratch.
     *
     * <p>Deliberately NOT called after crafting. Re-sorting mid-session would slide every icon to
     * a new position under the player's cursor, which makes crafting several of something a game
     * of hunt-the-icon. The list is settled when the screen opens and stays put until it closes.
     */
    public void rebuild() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        long startedAt = System.nanoTime();
        pool = IngredientPool.gather(serverPlayer);
        scan = RecipeScanner.scan(serverPlayer.level().getServer(), serverPlayer.level(),
                pool, showUncraftable, search);
        entries = scan.entries();
        com.eboac.terracraft.TerraCraft.LOGGER.info(
                "browser rebuild: {} entries from {} item stacks, table={}, search='{}', showUncraftable={}, {} ms",
                entries.size(), pool.sourceCount(), pool.hasCraftingTableNearby(), search, showUncraftable,
                (System.nanoTime() - startedAt) / 1_000_000L);

        repaginate();
    }

    /**
     * Re-checks what is still affordable without touching the list or its order.
     *
     * <p>This is what runs after a craft. Entries keep their slots; ones the player can no longer
     * afford simply stop being marked craftable and grey out where they are.
     */
    public void revalidate() {
        if (!(player instanceof ServerPlayer serverPlayer) || scan == null) {
            return;
        }

        pool = IngredientPool.gather(serverPlayer);
        CraftingPlanner planner = new CraftingPlanner(pool, scan.byOutput(), scan.results());

        List<CraftEntry> updated = new ArrayList<>(entries.size());
        for (CraftEntry entry : entries) {
            updated.add(new CraftEntry(entry.holder(), entry.result(),
                    planner.plan(entry.holder().value())));
        }
        entries = updated;

        repaginate();
    }

    /** Refills the visible page from the current list and tells the client about it. */
    public void repaginate() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        totalEntries = entries.size();
        int maxRow = Math.max(0, (totalEntries + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS);
        scrollRow = Math.clamp(scrollRow, 0, maxRow);

        craftableMask = 0L;
        chainMask = 0L;
        int first = scrollRow * COLUMNS;
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            int index = first + i;
            if (index < entries.size()) {
                CraftEntry entry = entries.get(index);
                display.setItem(i, entry.result().copy());
                switch (entry.tier()) {
                    case 0 -> craftableMask |= 1L << i;
                    case 1 -> chainMask |= 1L << i;
                    default -> {
                    }
                }
            } else {
                display.setItem(i, ItemStack.EMPTY);
            }
        }

        broadcastChanges();
        ServerPlayNetworking.send(serverPlayer, new BrowserStatePayload(
                totalEntries, scrollRow, showUncraftable, craftableMask, chainMask));
    }

    /** Applies the display controls the client asked for. None of these affect game state. */
    public void applyClientState(int requestedRow, boolean requestedShowUncraftable, String requestedSearch) {
        String trimmed = requestedSearch.length() > 50 ? requestedSearch.substring(0, 50) : requestedSearch;
        // Changing the filter or the search changes which recipes belong in the list at all, so
        // those need a rescan. Scrolling only moves a window over a list we already have.
        boolean listChanged = requestedShowUncraftable != showUncraftable || !trimmed.equals(search);

        this.scrollRow = Math.max(0, requestedRow);
        this.showUncraftable = requestedShowUncraftable;
        this.search = trimmed;

        if (listChanged || scan == null) {
            rebuild();
        } else {
            repaginate();
        }
    }

    @Override
    public boolean clickMenuButton(Player clickingPlayer, int id) {
        if (id == BUTTON_TOGGLE_FILTER) {
            showUncraftable = !showUncraftable;
            rebuild();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Clicking a result = crafting it
    // ------------------------------------------------------------------

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player clickingPlayer) {
        if (slotId >= 0 && slotId < DISPLAY_SLOTS) {
            if (clickingPlayer instanceof ServerPlayer serverPlayer) {
                boolean shift = input == ContainerInput.QUICK_MOVE;
                boolean rightButton = button == 1;
                // shift+right: make as many as the materials allow.
                // shift+left: exactly one, into the inventory, so repeated clicks count out.
                // plain click: exactly one, onto the cursor.
                craft(serverPlayer, slotId, shift, shift && rightButton);
            }
            return;
        }
        super.clicked(slotId, button, input, clickingPlayer);
    }

    /**
     * @param toInventory where the result goes: true for the inventory, false for the cursor
     * @param bulk        true to repeat until the materials run out
     */
    private void craft(ServerPlayer serverPlayer, int displaySlot, boolean toInventory, boolean bulk) {
        int index = scrollRow * COLUMNS + displaySlot;
        if (index < 0 || index >= entries.size()) {
            return;
        }
        if (!entries.get(index).obtainable()) {
            return;
        }

        CraftEntry entry = entries.get(index);

        // Never trust the flags the client was last told -- everything is recomputed from the
        // live world here, so clicking faster than the UI updates cannot produce a free item.
        pool = IngredientPool.gather(serverPlayer);

        int made = 0;
        while (true) {
            pool.reread();

            // Re-plan every round. After the first craft the pool has changed, so the chain that
            // was valid a moment ago may be shorter, longer, or unnecessary.
            List<RecipeHolder<CraftingRecipe>> steps =
                    new CraftingPlanner(pool, scan.byOutput(), scan.results()).plan(entry.holder().value());
            if (steps == null) {
                break;
            }

            boolean prerequisitesOk = true;
            for (RecipeHolder<CraftingRecipe> step : steps) {
                if (!runOnce(serverPlayer, step, true)) {
                    prerequisitesOk = false;
                    break;
                }
            }
            if (!prerequisitesOk) {
                break;
            }


            if (!runOnce(serverPlayer, entry.holder(), toInventory)) {
                break;
            }
            made++;

            if (!bulk || made >= MAX_BULK_CRAFTS || !hasRoomFor(serverPlayer, entry.result())) {
                break;
            }
        }

        // Re-check affordability only. Rebuilding here would re-sort the grid under the cursor.
        revalidate();
    }

    /**
     * Whether the inventory can take another batch without items ending up on the floor. Bulk
     * crafting stops here rather than spraying the result around the player's feet.
     */
    private boolean hasRoomFor(ServerPlayer serverPlayer, ItemStack result) {
        Inventory inventory = serverPlayer.getInventory();
        return inventory.getFreeSlot() != -1 || inventory.getSlotWithRemainingSpace(result) != -1;
    }

    /**
     * Performs exactly one craft of one recipe against the current pool.
     *
     * @param toInventory where the result goes; false means the cursor
     * @return false if the recipe turned out not to be makeable after all
     */
    private boolean runOnce(ServerPlayer serverPlayer, RecipeHolder<CraftingRecipe> holder, boolean toInventory) {
        CraftingRecipe recipe = holder.value();
        pool.reread();

        CraftAttempt attempt = RecipeScanner.attempt(recipe, pool);
        if (attempt == null || !recipe.matches(attempt.input(), serverPlayer.level())) {
            return false;
        }

        ItemStack result = recipe.assemble(attempt.input());
        if (result.isEmpty()) {
            return false;
        }

        ItemStack carried = getCarried();
        if (!toInventory) {
            // Cursor route: only legal if the cursor is empty or already holds the same thing
            // with room to spare. Otherwise the player would silently lose what they held.
            if (!carried.isEmpty()
                    && (!ItemStack.isSameItemSameComponents(carried, result)
                    || carried.getCount() + result.getCount() > carried.getMaxStackSize())) {
                return false;
            }
        }

        pool.consume(attempt.claimed());

        NonNullList<ItemStack> remainders = recipe.getRemainingItems(attempt.input());
        for (ItemStack remainder : remainders) {
            if (!remainder.isEmpty()) {
                giveToPlayer(serverPlayer, remainder.copy());
            }
        }

        result.onCraftedBy(serverPlayer, result.getCount());
        serverPlayer.awardRecipes(List.<RecipeHolder<?>>of(holder));

        if (toInventory) {
            giveToPlayer(serverPlayer, result);
        } else if (carried.isEmpty()) {
            setCarried(result);
        } else {
            carried.grow(result.getCount());
            setCarried(carried);
        }
        return true;
    }

    private void giveToPlayer(ServerPlayer serverPlayer, ItemStack stack) {
        if (!serverPlayer.getInventory().add(stack)) {
            serverPlayer.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    // ------------------------------------------------------------------
    // Vanilla plumbing
    // ------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player quickMovingPlayer, int slotIndex) {
        // Shift-clicking a *display* cell is handled in clicked() and never reaches here.
        // Shift-clicking an inventory slot has nowhere useful to go, so report "nothing moved".
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player validatingPlayer) {
        return validatingPlayer.isAlive();
    }

    // ------------------------------------------------------------------
    // Client-side accessors, fed by BrowserStatePayload
    // ------------------------------------------------------------------

    public void acceptState(int totalEntries, int scrollRow, boolean showUncraftable,
                            long craftableMask, long chainMask) {
        this.totalEntries = totalEntries;
        this.scrollRow = scrollRow;
        this.showUncraftable = showUncraftable;
        this.craftableMask = craftableMask;
        this.chainMask = chainMask;
    }

    /** True if the player can get this item, directly or by running intermediate crafts. */
    public boolean isDisplaySlotObtainable(int displaySlot) {
        long bit = 1L << displaySlot;
        return (craftableMask & bit) != 0L || (chainMask & bit) != 0L;
    }

    /** True if getting this item needs intermediate crafts first. */
    public boolean isDisplaySlotChained(int displaySlot) {
        return (chainMask & (1L << displaySlot)) != 0L;
    }

    public int totalEntries() {
        return totalEntries;
    }

    public int scrollRow() {
        return scrollRow;
    }

    public boolean showUncraftable() {
        return showUncraftable;
    }

    public String search() {
        return search;
    }

    public void setSearchClientSide(String search) {
        this.search = search;
    }

    public int maxScrollRow() {
        return Math.max(0, (totalEntries + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS);
    }
}
