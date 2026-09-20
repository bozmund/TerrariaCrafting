package com.eboac.terracraft;

import com.eboac.terracraft.command.ModCommands;
import com.eboac.terracraft.net.ModNetworking;
import com.eboac.terracraft.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint: runs on both the client and the dedicated server.
 */
public class TerraCraft implements ModInitializer {
	public static final String MOD_ID = "terracraft";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModMenus.init();
		ModNetworking.init();
		ModCommands.init();
		LOGGER.info("Terraria Crafting ready.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
