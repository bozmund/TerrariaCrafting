package com.eboac.terracraft.net;

import com.eboac.terracraft.TerraCraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenBrowserPayload() implements CustomPacketPayload {

    public static final Type<OpenBrowserPayload> TYPE =
            new Type<>(TerraCraft.id("open_browser"));

    public static final StreamCodec<Object, OpenBrowserPayload> CODEC =
            StreamCodec.unit(new OpenBrowserPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
