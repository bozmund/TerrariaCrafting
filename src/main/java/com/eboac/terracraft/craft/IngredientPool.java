package com.eboac.terracraft.craft;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the player can currently craft *from*: their own inventory plus any
 * container block entities within {@link #SCAN_RADIUS} blocks.
 *
 * <p>This is built fresh on the server every time the browser opens or an item is
 * crafted. It is never built on the client -- the client does not know what is in
 * nearby chests, and trusting it to would be a duplication exploit.
 */
public final class IngredientPool {

    /** How far around the player we look for chests, barrels and other containers. */
    public static final int SCAN_RADIUS = 8;

    /** Safety cap so a room full of chests cannot make every craft click expensive. */
    private static final int MAX_CONTAINERS = 48;

    /** One slot somewhere that holds items we may consume. */
    public record Source(Container container, int slot) {
    }

    private final List<Source> sources = new ArrayList<>();
    private final List<ItemStack> stacks = new ArrayList<>();
    private boolean craftingTableNearby;

    private IngredientPool() {
    }

    public static IngredientPool gather(Player player) {
        IngredientPool pool = new IngredientPool();
        pool.addPlayerInventory(player.getInventory());
        pool.addNearbyContainers(player);
        return pool;
    }

    private void addPlayerInventory(Inventory inventory) {
        // Only the 36 main slots. Armour and the offhand are deliberately excluded --
        // silently eating the player's equipped boots to craft something would be rude.
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            add(inventory, slot);
        }
    }

    private void addNearbyContainers(Player player) {
        Level level = player.level();
        BlockPos origin = player.blockPosition();
        int containers = 0;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                origin.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {

            // betweenClosed reuses a mutable position, so anything we keep must be copied.
            if (!level.isLoaded(pos)) {
                continue;
            }

            if (level.getBlockState(pos).getBlock() instanceof CraftingTableBlock) {
                craftingTableNearby = true;
            }

            if (containers >= MAX_CONTAINERS) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof Container container)) {
                continue;
            }
            if (!container.stillValid(player)) {
                continue;
            }

            containers++;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                add(container, slot);
            }
        }
    }

    private void add(Container container, int slot) {
        ItemStack stack = container.getItem(slot);
        if (!stack.isEmpty()) {
            sources.add(new Source(container, slot));
            stacks.add(stack);
        }
    }

    public boolean hasCraftingTableNearby() {
        return craftingTableNearby;
    }

    public int sourceCount() {
        return sources.size();
    }

    public ItemStack stackAt(int sourceIndex) {
        return stacks.get(sourceIndex);
    }

    public Source sourceAt(int sourceIndex) {
        return sources.get(sourceIndex);
    }

    /** How many sources could satisfy this ingredient. Used to allocate scarce items first. */
    public int candidateCount(Ingredient ingredient) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (ingredient.test(stack)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Finds a source holding an item matching {@code ingredient} that still has stock left
     * after {@code claimed} items have already been earmarked.
     *
     * @return the source index, or -1 if nothing suitable is available
     */
    public int findAvailable(Ingredient ingredient, int[] claimed) {
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (stack.getCount() - claimed[i] > 0 && ingredient.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    /** Actually removes the allocated items. Only called once a craft is committed. */
    public void consume(int[] claimed) {
        for (int i = 0; i < claimed.length; i++) {
            if (claimed[i] <= 0) {
                continue;
            }
            Source source = sources.get(i);
            source.container().removeItem(source.slot(), claimed[i]);
            source.container().setChanged();
        }
    }
}
