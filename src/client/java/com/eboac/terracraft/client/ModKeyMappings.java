package com.eboac.terracraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public class ModKeyMappings {

    public static KeyMapping openBrowser;

    public static void init() {
        openBrowser = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.terracraft.open_browser",
                InputConstants.KEY_B,
                KeyMapping.Category.INVENTORY
        ));
    }
}
