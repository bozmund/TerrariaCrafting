package com.eboac.terracraft.net;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the display controls changed (scrolled, toggled the filter, typed a search).
 *
 * <p>Nothing here can be abused. Scroll position and search text only decide what the player is
 * shown; whether a craft is legal is decided from scratch on the server every time.
 */
public record BrowserActionPayload(int scrollRow, boolean showUncraftable, String search)
        implements CustomPacketPayload {

    public static final Type<BrowserActionPayload> TYPE = new Type<>(TerraCraft.id("browser_action"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, BrowserActionPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BrowserActionPayload::scrollRow,
                    ByteBufCodecs.BOOL, BrowserActionPayload::showUncraftable,
                    ByteBufCodecs.stringUtf8(64), BrowserActionPayload::search,
                    BrowserActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
