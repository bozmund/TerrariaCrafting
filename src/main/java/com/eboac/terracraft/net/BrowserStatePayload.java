package com.eboac.terracraft.net;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: everything the browser screen needs that slot syncing cannot carry --
 * how many recipes matched, where we are scrolled to, and which visible cells are affordable.
 *
 * <p>The craftable flags are a bitmask because there are exactly 45 visible cells, which fits
 * in a single long.
 */
public record BrowserStatePayload(int totalEntries, int scrollRow, boolean showUncraftable, long craftableMask)
        implements CustomPacketPayload {

    public static final Type<BrowserStatePayload> TYPE = new Type<>(TerraCraft.id("browser_state"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, BrowserStatePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BrowserStatePayload::totalEntries,
                    ByteBufCodecs.VAR_INT, BrowserStatePayload::scrollRow,
                    ByteBufCodecs.BOOL, BrowserStatePayload::showUncraftable,
                    ByteBufCodecs.VAR_LONG, BrowserStatePayload::craftableMask,
                    BrowserStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
