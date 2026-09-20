package com.eboac.terracraft.registry;

import com.eboac.terracraft.TerraCraft;
import com.eboac.terracraft.menu.CraftBrowserMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

public class ModMenus {

    public static final MenuType<CraftBrowserMenu> CRAFT_BROWSER = Registry.register(
            BuiltInRegistries.MENU,
            TerraCraft.id("craft_browser"),
            new MenuType<>(CraftBrowserMenu::new, FeatureFlagSet.of())
    );

    public static void init() {
    }
}
