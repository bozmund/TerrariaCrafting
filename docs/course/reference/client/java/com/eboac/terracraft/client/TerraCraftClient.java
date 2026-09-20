package com.eboac.terracraft.client;

import com.eboac.terracraft.client.screen.CraftBrowserScreen;
import com.eboac.terracraft.net.OpenBrowserPayload;
import com.eboac.terracraft.registry.ModMenus;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

public class TerraCraftClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(ModMenus.CRAFT_BROWSER, CraftBrowserScreen::new);
		ModKeyMappings.init();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (ModKeyMappings.openBrowser.consumeClick()) {
				ClientPlayNetworking.send(new OpenBrowserPayload());
			}
		});
	}
}
