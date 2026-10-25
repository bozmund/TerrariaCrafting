package com.eboac.terracraft.menu;

import com.eboac.terracraft.api.TerraCraftGate;
import com.eboac.terracraft.craft.CraftAttempt;
import com.eboac.terracraft.craft.CraftEntry;
import com.eboac.terracraft.craft.CraftingPlanner;
import com.eboac.terracraft.craft.IngredientPool;
import com.eboac.terracraft.craft.RecipeScanner;
import com.eboac.terracraft.net.BrowserStatePayload;
import com.eboac.terracraft.registry.ModMenus;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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

    /** Menu index of the ingredient filter slot: after the display cells and the inventory. */
    public static final int FILTER_SLOT = DISPLAY_SLOTS + 36;

    public static final int FILTER_X = 150;
    public static final int FILTER_Y = 13;

    private final Player player;

    /** Backing store for the 45 display cells. Server writes it; client receives it. */
    private final SimpleContainer display = new SimpleContainer(DISPLAY_SLOTS);

    /**
     * Drop an item here to see only the recipes that consume it. Never consumed itself, and
     * handed back when the screen closes.
     */
    private final SimpleContainer ingredientFilter = new SimpleContainer(1);

    /**
     * Upper bound on one shift-click. Without it, a chest wall of logs would run thousands of
     * crafts inside a single packet handler and stall the server tick.
     */
    private static final int MAX_BULK_CRAFTS = 256;

    /** How often an open browser re-reads what the player is carrying. Ten ticks is half a second. */
    private static final int CARRY_CHECK_INTERVAL_TICKS = 10;

    /**
     * Upper bound on what one refresh may add. Picking up a stack of a brand-new modded log can
     * unlock a whole family of recipes at once, and dumping all of them in one frame is both a
     * stutter and a wall of icons the player did not ask for.
     */
    private static final int MAX_APPENDS_PER_REFRESH = 48;

    // ---- server-side state ----
    private List<CraftEntry> entries = List.of();
    private RecipeScanner.ScanResult scan;
    private IngredientPool pool;

    // ---- shared state (client mirrors it via BrowserStatePayload) ----
    private int scrollRow;
    private boolean showUncraftable = false;
    private String search = "";
    private int totalEntries;
    /** What each visible cell costs, one list per cell. Mirrored on the client for tooltips. */
    private List<List<ItemStack>> visibleIngredients = List.of();
    /** Visible cells that can be made right now. */
    private long craftableMask;
    /** Visible cells reachable only by running intermediate crafts first. */
    private long chainMask;
    /** Visible cells that open a real crafting grid instead of crafting directly. */
    private long specialMask;
    /**
     * Visible cells a knowledge gate forbids this player to craft. Separate from
     * {@link #craftableMask} on purpose: a gated recipe is normally perfectly affordable, so it
     * belongs in craftableMask (it CAN be made) and in lockedMask (it MAY NOT be).
     */
    private long lockedMask;
    /** Whether a crafting table is currently within range, same rule as the station check. */
    private boolean tableNearby;

    // ---- incremental refresh state (server only) ----
    /**
     * Ticks until the next carry check. {@code broadcastChanges} runs once per tick for the open
     * menu, so counting down from the interval is enough and avoids depending on a level clock
     * accessor.
     */
    private int carryCheckCountdown = CARRY_CHECK_INTERVAL_TICKS;
    private Map<Item, Integer> lastCarriedCounts = Map.of();
    /** Where the player was the last time the station was re-checked, so standing still costs nothing. */
    private BlockPos lastStationCheckPos = null;
    /** The station state the current list was built under. Changing it changes eligibility, not affordability. */
    private boolean lastStationKnown;
    /** Detects a datapack reload: the held scan then describes recipes that no longer exist. */
    private long lastScanEpoch;
    /** Recipe ids already in {@link #entries}, so a refresh never lists the same recipe twice. */
    private final Set<ResourceKey<Recipe<?>>> listedRecipeIds = new HashSet<>();
    /** Guards against re-entry: {@link #repaginate} calls {@code broadcastChanges}, which drives the refresh. */
    private boolean refreshing;

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
        addSlot(new Slot(ingredientFilter, 0, FILTER_X, FILTER_Y));

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
                pool, serverPlayer, showUncraftable, search, ingredientFilter.getItem(0));
        entries = scan.entries();
        rememberListState(serverPlayer);
        com.eboac.terracraft.TerraCraft.LOGGER.debug(
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
            // A special entry has no "steps" to re-plan -- its one bit of state is the icon,
            // which the planner knows nothing about and would only wipe.
            updated.add(entry.special() ? entry
                    : CraftEntry.normal(entry.holder(), entry.result(),
                            planner.plan(entry.holder().value()), entry.known()));
        }
        entries = updated;

        repaginate();
    }

    /**
     * Records what the current list was built from, so {@link #tickRefresh} can tell an actual
     * change apart from a tick that changed nothing.
     */
    private void rememberListState(ServerPlayer serverPlayer) {
        listedRecipeIds.clear();
        for (CraftEntry entry : entries) {
            listedRecipeIds.add(entry.holder().id());
        }
        lastCarriedCounts = IngredientPool.carriedCounts(serverPlayer);
        lastStationKnown = pool != null && pool.hasCraftingTableNearby();
        lastStationCheckPos = serverPlayer.blockPosition().immutable();
        lastScanEpoch = RecipeScanner.epoch();
        carryCheckCountdown = CARRY_CHECK_INTERVAL_TICKS;
    }

    /**
     * Keeps an open browser honest about what the player can currently make.
     *
     * <p>{@link #rebuild()} decides which recipes are in the list at all, and it used to run only
     * when the screen opened. Anything the player reached later -- picking up a modded log, walking
     * to a crafting table -- therefore stayed invisible until the screen was closed and reopened.
     * {@link #revalidate()} after a craft could only re-price entries that were already listed; it
     * cannot add one.
     *
     * <p>This adds the missing membership updates without giving up the reason {@code rebuild()} is
     * avoided mid-session: the settled block of entries keeps its positions and new recipes are only
     * ever appended at the end, so icons never slide out from under the cursor.
     *
     * <p>Called from the {@code broadcastChanges} injection once per player tick, and it deliberately
     * does almost nothing on most calls: comparing 36 slots is the only work done while nothing has
     * changed, and the station is only re-checked when the player has actually moved.
     */
    public void tickRefresh() {
        // repaginate() calls broadcastChanges(), and broadcastChanges() is what drives this, so a
        // refresh must not be able to re-enter itself.
        if (refreshing || !(player instanceof ServerPlayer serverPlayer) || scan == null) {
            return;
        }
        refreshing = true;
        try {
            refreshNow(serverPlayer);
        } finally {
            refreshing = false;
        }
    }

    private void refreshNow(ServerPlayer serverPlayer) {
        // A reload replaced the recipe set this list was built from. Nothing held is trustworthy.
        if (RecipeScanner.epoch() != lastScanEpoch) {
            rebuild();
            return;
        }

        if (carryCheckCountdown > 0) {
            carryCheckCountdown--;
            return;
        }
        carryCheckCountdown = CARRY_CHECK_INTERVAL_TICKS;

        // Eligibility, not affordability: without a station in range the recipes too big for the
        // hand grid were never added to the scan at all, so there is nothing incremental to find.
        // Only re-check that when the player has moved, since it means scanning around them.
        BlockPos position = serverPlayer.blockPosition();
        if (lastStationCheckPos == null || !lastStationCheckPos.equals(position)) {
            lastStationCheckPos = position.immutable();
            if (IngredientPool.craftingTableInRange(serverPlayer) != lastStationKnown) {
                rebuild();
                return;
            }
        }

        Map<Item, Integer> carried = IngredientPool.carriedCounts(serverPlayer);
        if (carried.equals(lastCarriedCounts)) {
            return;
        }

        List<Item> changed = new ArrayList<>();
        for (Map.Entry<Item, Integer> difference : carried.entrySet()) {
            if (!lastCarriedCounts.getOrDefault(difference.getKey(), 0).equals(difference.getValue())) {
                changed.add(difference.getKey());
            }
        }
        for (Item gone : lastCarriedCounts.keySet()) {
            if (!carried.containsKey(gone)) {
                changed.add(gone);
            }
        }
        lastCarriedCounts = carried;

        if (changed.isEmpty()) {
            return;
        }

        pool = IngredientPool.gather(serverPlayer);
        appendNewlyReachable(serverPlayer, changed);
    }

    /**
     * Adds the recipes this refresh made possible, at the end of the list.
     *
     * <p>Only recipes that consume something which just changed are considered. An item appearing
     * in the pool cannot unlock a recipe that does not use it, so {@code byIngredient} turns this
     * from a second full scan into a handful of planner runs.
     */
    private void appendNewlyReachable(ServerPlayer serverPlayer, List<Item> changed) {
        String query = search.trim().toLowerCase(Locale.ROOT);
        ItemStack ingredientFilterStack = ingredientFilter.getItem(0);
        CraftingPlanner planner = new CraftingPlanner(pool, scan.byOutput(), scan.results());
        List<CraftEntry> added = new ArrayList<>();

        for (Item item : changed) {
            List<RecipeHolder<CraftingRecipe>> candidates = scan.byIngredient().get(item);
            if (candidates == null) {
                continue;
            }
            for (RecipeHolder<CraftingRecipe> holder : candidates) {
                if (added.size() >= MAX_APPENDS_PER_REFRESH) {
                    break;
                }
                if (listedRecipeIds.contains(holder.id())) {
                    continue;
                }

                // Same membership rules a full scan applies, or the appended rows would disagree
                // with the ones rebuild() produced.
                ItemStack result = scan.results().get(holder);
                if (result == null || result.isEmpty()) {
                    continue;
                }
                if (!query.isEmpty()
                        && !result.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) {
                    continue;
                }
                if (!ingredientFilterStack.isEmpty()
                        && !RecipeScanner.usesIngredient(holder.value(), ingredientFilterStack)) {
                    continue;
                }

                List<RecipeHolder<CraftingRecipe>> steps = planner.plan(holder.value());
                if (steps == null) {
                    continue;
                }

                added.add(CraftEntry.normal(holder, result, steps,
                        TerraCraftGate.knows(serverPlayer, holder, result)));
                listedRecipeIds.add(holder.id());
            }
            if (added.size() >= MAX_APPENDS_PER_REFRESH) {
                break;
            }
        }

        if (added.isEmpty()) {
            return;
        }

        List<CraftEntry> grown = new ArrayList<>(entries);
        grown.addAll(added);
        entries = grown;

        com.eboac.terracraft.TerraCraft.LOGGER.info(
                "browser refresh: {} new entries from {} changed items, {} total",
                added.size(), changed.size(), entries.size());

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
        tableNearby = pool != null && pool.hasCraftingTableNearby();

        craftableMask = 0L;
        chainMask = 0L;
        specialMask = 0L;
        lockedMask = 0L;
        List<List<ItemStack>> costs = new ArrayList<>(DISPLAY_SLOTS);
        int first = scrollRow * COLUMNS;
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            int index = first + i;
            if (index < entries.size()) {
                CraftEntry entry = entries.get(index);
                display.setItem(i, entry.result().copy());
                costs.add(entry.special() ? List.of() : RecipeScanner.ingredientCounts(entry.holder().value()));
                if (!entry.known()) {
                    // Marked locked, and left out of the affordability masks. A cell in craftableMask
                    // that the server then refuses is the worst version of this: the browser promises
                    // a craft and takes the click back.
                    lockedMask |= 1L << i;
                    continue;
                }
                switch (entry.tier()) {
                    case 0 -> craftableMask |= 1L << i;
                    case 1 -> chainMask |= 1L << i;
                    case 3 -> specialMask |= 1L << i;
                    default -> {
                    }
                }
            } else {
                display.setItem(i, ItemStack.EMPTY);
                costs.add(List.of());
            }
        }
        visibleIngredients = costs;

        broadcastChanges();
        ServerPlayNetworking.send(serverPlayer, new BrowserStatePayload(
                totalEntries, scrollRow, showUncraftable, craftableMask, chainMask, specialMask,
                lockedMask, tableNearby, visibleIngredients));
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
        if (slotId == FILTER_SLOT) {
            super.clicked(slotId, button, input, clickingPlayer);
            // The filter decides which recipes belong in the list at all, so this needs a rescan.
            rebuild();
            return;
        }

        if (slotId >= 0 && slotId < DISPLAY_SLOTS) {
            // Only the two gestures we actually mean may craft. Everything else that can arrive
            // as a slot click -- throwing with Q, double-click gather, click-drag, number-key
            // swap, middle-click clone -- is swallowed. Treating any click as a craft let a
            // stray gesture, or another mod synthesising clicks, craft by accident.
            boolean pickup = input == ContainerInput.PICKUP;
            boolean quickMove = input == ContainerInput.QUICK_MOVE;
            if ((!pickup && !quickMove) || button < 0 || button > 1) {
                return;
            }

            if (clickingPlayer instanceof ServerPlayer serverPlayer) {
                int index = scrollRow * COLUMNS + slotId;
                if (index >= 0 && index < entries.size() && entries.get(index).special()) {
                    tryOpenManualCrafting(serverPlayer);
                    return;
                }
                // shift+right: make as many as the materials allow.
                // shift+left: exactly one, into the inventory, so repeated clicks count out.
                // plain click: exactly one, onto the cursor.
                craft(serverPlayer, slotId, quickMove, quickMove && button == 1);
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

        // The gate is asked again here rather than trusted from the scan that built this list. A
        // recipe can have become disallowed since the list was made, and a modified client can
        // click any index it likes. Same rule as the affordability recomputation below: nothing
        // the client was last told is treated as evidence.
        if (!TerraCraftGate.knows(serverPlayer, entry.holder(), entry.result())) {
            return;
        }

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
        // Deliberately not awarding the recipe. The crafting recipe book is disabled, so
        // unlocking would only spend a packet per craft and pop a "New Recipes Unlocked" toast
        // for a book the player cannot open.

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

    /**
     * Entry point for the explicit "crafting grid" button, reached via {@code ModNetworking}.
     *
     * <p>The button is only drawn client-side when {@link #tableNearby} was true as of the last
     * page sent -- but that can be seconds old, and a modified client could send the request
     * regardless of what it draws. {@link #tryOpenManualCrafting} re-checks against the live world
     * before honouring it, the same "never trust the client's last-known state" rule the craft
     * path itself follows.
     */
    public void requestManualCrafting(ServerPlayer serverPlayer) {
        tryOpenManualCrafting(serverPlayer);
    }

    /**
     * Opens a genuine vanilla crafting grid for recipes the browser cannot represent, or for
     * players who just want the classic grid back -- but only if a crafting table is still
     * actually nearby right now, regardless of whether it was a moment ago.
     */
    private void tryOpenManualCrafting(ServerPlayer serverPlayer) {
        if (!IngredientPool.gather(serverPlayer).hasCraftingTableNearby()) {
            return;
        }
        serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (id, inventory, p) -> new net.minecraft.world.inventory.CraftingMenu(id, inventory),
                net.minecraft.network.chat.Component.translatable("container.crafting")));
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

    @Override
    public void removed(Player removingPlayer) {
        super.removed(removingPlayer);
        // The filter item is the player's, not ours -- never swallow it on close.
        ItemStack held = ingredientFilter.removeItemNoUpdate(0);
        if (!held.isEmpty() && removingPlayer instanceof ServerPlayer serverPlayer) {
            giveToPlayer(serverPlayer, held);
        }
    }

    public ItemStack ingredientFilter() {
        return ingredientFilter.getItem(0);
    }

    // ------------------------------------------------------------------
    // Client-side accessors, fed by BrowserStatePayload
    // ------------------------------------------------------------------

    public void acceptState(int totalEntries, int scrollRow, boolean showUncraftable,
                            long craftableMask, long chainMask, long specialMask, long lockedMask,
                            boolean tableNearby, List<List<ItemStack>> ingredients) {
        this.totalEntries = totalEntries;
        this.scrollRow = scrollRow;
        this.showUncraftable = showUncraftable;
        this.craftableMask = craftableMask;
        this.chainMask = chainMask;
        this.specialMask = specialMask;
        this.lockedMask = lockedMask;
        this.tableNearby = tableNearby;
        this.visibleIngredients = ingredients;
    }

    /** Whether to show the "open a real crafting grid" button at all. */
    public boolean tableNearby() {
        return tableNearby;
    }

    /** What the recipe in this visible cell costs, for the hover tooltip. */
    public List<ItemStack> ingredientsFor(int displaySlot) {
        return displaySlot >= 0 && displaySlot < visibleIngredients.size()
                ? visibleIngredients.get(displaySlot)
                : List.of();
    }

    /** True if the player can get this item, directly or by running intermediate crafts. */
    public boolean isDisplaySlotObtainable(int displaySlot) {
        long bit = 1L << displaySlot;
        return !isDisplaySlotLocked(displaySlot)
                && ((craftableMask & bit) != 0L || (chainMask & bit) != 0L);
    }

    /**
     * True if a knowledge gate forbids this cell. The recipe is still listed and still shows its
     * icon and cost -- locked is not absent, and a player who cannot see what they are missing has
     * no reason to go looking for it.
     */
    public boolean isDisplaySlotLocked(int displaySlot) {
        return (lockedMask & (1L << displaySlot)) != 0L;
    }

    /** True if getting this item needs intermediate crafts first. */
    public boolean isDisplaySlotChained(int displaySlot) {
        return (chainMask & (1L << displaySlot)) != 0L;
    }

    /** True if clicking this slot opens a real crafting grid instead of crafting directly. */
    public boolean isDisplaySlotSpecial(int displaySlot) {
        return (specialMask & (1L << displaySlot)) != 0L;
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
