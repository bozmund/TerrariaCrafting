package com.eboac.terracraft.net;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Server to client: everything the browser screen needs that slot syncing cannot carry --
 * how many recipes matched, where we are scrolled to, which visible cells are affordable, and
 * what each of them costs.
 *
 * <p>The craftable flags are a bitmask because there are exactly 45 visible cells, which fits in
 * a single long.
 *
 * <p>{@code lockedMask} marks cells a knowledge gate forbids this player to craft. It is a fourth
 * mask rather than a tweak to {@code craftableMask} because the two questions are independent: a
 * gated recipe is usually perfectly affordable, so it would sit in {@code craftableMask} and render
 * as craftable while the server refuses it. Keeping locked separate is what lets the screen show
 * the recipe as present-but-locked instead of either hiding it or lying about it.
 *
 * <p>{@code ingredients} holds one list per visible cell, each entry an item with its count set
 * to how many the recipe needs. The client cannot work this out for itself -- recipes live on the
 * server -- and sending it with the page means a tooltip can appear instantly on hover rather
 * than after a round trip.
 */
public record BrowserStatePayload(int totalEntries, int scrollRow, boolean showUncraftable,
                                  long craftableMask, long chainMask, long specialMask,
                                  long lockedMask,
                                  boolean tableNearby, List<List<ItemStack>> ingredients)
        implements CustomPacketPayload {

    public static final Type<BrowserStatePayload> TYPE = new Type<>(TerraCraft.id("browser_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BrowserStatePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BrowserStatePayload::totalEntries,
                    ByteBufCodecs.VAR_INT, BrowserStatePayload::scrollRow,
                    ByteBufCodecs.BOOL, BrowserStatePayload::showUncraftable,
                    ByteBufCodecs.VAR_LONG, BrowserStatePayload::craftableMask,
                    ByteBufCodecs.VAR_LONG, BrowserStatePayload::chainMask,
                    ByteBufCodecs.VAR_LONG, BrowserStatePayload::specialMask,
                    ByteBufCodecs.VAR_LONG, BrowserStatePayload::lockedMask,
                    ByteBufCodecs.BOOL, BrowserStatePayload::tableNearby,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()),
                    BrowserStatePayload::ingredients,
                    BrowserStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
