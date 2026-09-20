package com.eboac.terracraft.menu;

import com.eboac.terracraft.craft.CraftAttempt;
import com.eboac.terracraft.craft.CraftEntry;
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

    // ---- server-side state ----
    private List<CraftEntry> entries = List.of();
    private IngredientPool pool;

    // ---- shared state (client mirrors it via BrowserStatePayload) ----
    private int scrollRow;
    private boolean showUncraftable = true;
    private String search = "";
    private int totalEntries;
    private long craftableMask;

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
            refresh();
        }
    }

    // ------------------------------------------------------------------
    // Server-side recomputation
    // ------------------------------------------------------------------

    /** Rebuilds the ingredient pool, rescans recipes, and pushes the result to the client. */
    public void refresh() {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        pool = IngredientPool.gather(serverPlayer);
        entries = RecipeScanner.scan(serverPlayer.level().getServer(), serverPlayer.level(),
                pool, showUncraftable, search);
        totalEntries = entries.size();

        int maxRow = Math.max(0, (totalEntries + COLUMNS - 1) / COLUMNS - VISIBLE_ROWS);
        scrollRow = Math.clamp(scrollRow, 0, maxRow);

        craftableMask = 0L;
        int first = scrollRow * COLUMNS;
        for (int i = 0; i < DISPLAY_SLOTS; i++) {
            int index = first + i;
            if (index < entries.size()) {
                CraftEntry entry = entries.get(index);
                display.setItem(i, entry.result().copy());
                if (entry.craftable()) {
                    craftableMask |= 1L << i;
                }
            } else {
                display.setItem(i, ItemStack.EMPTY);
            }
        }

        broadcastChanges();
        ServerPlayNetworking.send(serverPlayer,
                new BrowserStatePayload(totalEntries, scrollRow, showUncraftable, craftableMask));
    }

    /** Applies the display controls the client asked for. None of these affect game state. */
    public void applyClientState(int requestedRow, boolean requestedShowUncraftable, String requestedSearch) {
        this.scrollRow = Math.max(0, requestedRow);
        this.showUncraftable = requestedShowUncraftable;
        this.search = requestedSearch.length() > 50 ? requestedSearch.substring(0, 50) : requestedSearch;
        refresh();
    }

    @Override
    public boolean clickMenuButton(Player clickingPlayer, int id) {
        if (id == BUTTON_TOGGLE_FILTER) {
            showUncraftable = !showUncraftable;
            refresh();
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
                craft(serverPlayer, slotId, input == ContainerInput.QUICK_MOVE);
            }
            return;
        }
        super.clicked(slotId, button, input, clickingPlayer);
    }

    /**
     * @param toInventory true for shift-click (straight to the inventory), false for a normal
     *                    click (onto the cursor, the way Terraria hands you the item)
     */
    private void craft(ServerPlayer serverPlayer, int displaySlot, boolean toInventory) {
        int index = scrollRow * COLUMNS + displaySlot;
        if (index < 0 || index >= entries.size()) {
            return;
        }

        CraftEntry entry = entries.get(index);
        CraftingRecipe recipe = entry.holder().value();

        // Never trust the cached craftable flag -- the pool is rebuilt and re-checked here, so a
        // client spamming clicks faster than the UI updates still cannot craft what it cannot pay for.
        pool = IngredientPool.gather(serverPlayer);
        CraftAttempt attempt = RecipeScanner.attempt(recipe, pool);
        if (attempt == null || !recipe.matches(attempt.input(), serverPlayer.level())) {
            refresh();
            return;
        }

        ItemStack result = recipe.assemble(attempt.input());
        if (result.isEmpty()) {
            refresh();
            return;
        }

        ItemStack carried = getCarried();
        if (!toInventory) {
            // Cursor route: only legal if the cursor is empty or already holds the same thing
            // with room to spare. Otherwise the player would silently lose what they were holding.
            if (!carried.isEmpty()
                    && (!ItemStack.isSameItemSameComponents(carried, result)
                    || carried.getCount() + result.getCount() > carried.getMaxStackSize())) {
                return;
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
        serverPlayer.awardRecipes(List.<net.minecraft.world.item.crafting.RecipeHolder<?>>of(entry.holder()));

        if (toInventory) {
            giveToPlayer(serverPlayer, result);
        } else if (carried.isEmpty()) {
            setCarried(result);
        } else {
            carried.grow(result.getCount());
            setCarried(carried);
        }

        refresh();
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

    public void acceptState(int totalEntries, int scrollRow, boolean showUncraftable, long craftableMask) {
        this.totalEntries = totalEntries;
        this.scrollRow = scrollRow;
        this.showUncraftable = showUncraftable;
        this.craftableMask = craftableMask;
    }

    public boolean isDisplaySlotCraftable(int displaySlot) {
        return (craftableMask & (1L << displaySlot)) != 0L;
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
