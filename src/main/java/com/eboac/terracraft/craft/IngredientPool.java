package com.eboac.terracraft.craft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything the player can currently craft *from*: their own inventory, plus any containers
 * wired to a nearby crafting table.
 *
 * <p>Containers are not simply "anything within range". A container counts only if it is part of
 * a chain of containers touching a crafting table: the table's six neighbours, whatever touches
 * those, and so on outward. That makes a storage room an explicit build -- chests you want the
 * table to reach have to physically connect to it -- rather than a radius that quietly swallows
 * every chest in the basement.
 *
 * <p>This is built fresh on the server every time the browser opens or an item is crafted. It is
 * never built on the client -- the client does not know what is in those chests, and trusting it
 * to would be a duplication exploit.
 */
public final class IngredientPool {

    /** How far around the player we look for a crafting table to act as the station. */
    public static final int SCAN_RADIUS = 8;

    /** Safety cap so a warehouse of chests cannot make every craft click expensive. */
    private static final int MAX_CONTAINERS = 64;

    /** How far a container chain may run from its table, so one long line cannot reach a vault. */
    private static final int MAX_CHAIN_DISTANCE = 16;

    /** One slot somewhere that holds items we may consume. */
    public record Source(Container container, int slot) {
    }

    private final List<Source> sources = new ArrayList<>();
    private final List<ItemStack> stacks = new ArrayList<>();

    /** The containers found by the world scan, kept so {@link #reread} need not scan again. */
    private final List<Container> containers = new ArrayList<>();
    private Player owner;
    private boolean craftingTableNearby;

    private IngredientPool() {
    }

    public static IngredientPool gather(Player player) {
        IngredientPool pool = new IngredientPool();
        pool.owner = player;
        pool.findContainers(player);
        pool.reread();
        return pool;
    }

    /**
     * Re-reads every known container's contents without scanning the world again.
     *
     * <p>A chained craft runs several recipes back to back, and each one changes what is in the
     * player's inventory. Repeating the block scan between steps would mean thousands of
     * {@code getBlockEntity} calls per click; the set of containers has not moved, only their
     * contents have.
     */
    public void reread() {
        sources.clear();
        stacks.clear();
        addPlayerInventory(owner.getInventory());
        for (Container container : containers) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                add(container, slot);
            }
        }
    }

    private void addPlayerInventory(Inventory inventory) {
        // Only the 36 main slots. Armour and the offhand are deliberately excluded --
        // silently eating the player's equipped boots to craft something would be rude.
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            add(inventory, slot);
        }
    }

    private void findContainers(Player player) {
        Level level = player.level();
        BlockPos origin = player.blockPosition();

        // Step one: locate the crafting tables in range. These are the roots of the chest network
        // and also what unlocks recipes bigger than the 2x2 hand grid.
        List<BlockPos> tables = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-SCAN_RADIUS, -SCAN_RADIUS, -SCAN_RADIUS),
                origin.offset(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS))) {

            if (!level.isLoaded(pos) || !(level.getBlockState(pos).getBlock() instanceof CraftingTableBlock)) {
                continue;
            }
            craftingTableNearby = true;
            // betweenClosed reuses one mutable position, so anything kept must be copied.
            tables.add(pos.immutable());
        }

        if (tables.isEmpty()) {
            return;
        }

        // Step two: flood outwards from the tables through touching containers. A chest beside the
        // table is in; a chest beside that chest is in; a chest across the room on its own is not.
        Set<BlockPos> visited = new HashSet<>(tables);
        Deque<BlockPos> queue = new ArrayDeque<>(tables);

        while (!queue.isEmpty() && containers.size() < MAX_CONTAINERS) {
            BlockPos current = queue.poll();

            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);

                if (!visited.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                if (tables.stream().noneMatch(table -> table.distManhattan(next) <= MAX_CHAIN_DISTANCE)) {
                    continue;
                }
                // Cheap early-out: most blocks have no block entity, and reading the state is far
                // cheaper than a block-entity lookup.
                if (!level.getBlockState(next).hasBlockEntity()) {
                    continue;
                }
                if (!(level.getBlockEntity(next) instanceof Container container)) {
                    continue;
                }
                if (!container.stillValid(player)) {
                    continue;
                }

                containers.add(container);
                // Containers conduct: the chain continues through this one.
                queue.add(next);
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
