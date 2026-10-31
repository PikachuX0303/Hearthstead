package dev.pikachux.hearthstead;

import dev.pikachux.hearthstead.command.ColonyCommands;
import dev.pikachux.hearthstead.event.ColonyEvents;
import dev.pikachux.hearthstead.event.WelcomeMessage;
import dev.pikachux.hearthstead.network.ModNetworking;
import dev.pikachux.hearthstead.registry.LegacyIds;
import dev.pikachux.hearthstead.registry.ModBlocks;
import dev.pikachux.hearthstead.registry.ModEntities;
import dev.pikachux.hearthstead.registry.ModItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point of Hearthstead, a colony-building mod inspired by MineColonies.
 */
public class Hearthstead implements ModInitializer {
    public static final String MOD_ID = "hearthstead";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModItems.register();
        ModEntities.register();
        LegacyIds.register();
        ColonyEvents.register();
        WelcomeMessage.register();
        ColonyCommands.register();
        ModNetworking.register();
        LOGGER.info("Hearthstead initialized");
    }
}
