package com.eboac.terracraft.net;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: "I pressed the crafting-grid button in the browser; open a real grid for me."
 *
 * <p>Carries no data, same as {@link OpenBrowserPayload}. The client is only allowed to ask; the
 * server decides whether a crafting table is actually nearby before honouring it, exactly the way
 * it never trusts the client's view of what is craftable.
 */
public record OpenManualCraftingPayload() implements CustomPacketPayload {

    public static final Type<OpenManualCraftingPayload> TYPE = new Type<>(TerraCraft.id("open_manual_crafting"));

    public static final StreamCodec<Object, OpenManualCraftingPayload> CODEC =
            StreamCodec.unit(new OpenManualCraftingPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
