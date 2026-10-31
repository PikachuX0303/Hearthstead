package dev.pikachux.hearthstead.client;

import dev.pikachux.hearthstead.client.buildtool.BuildToolClient;
import dev.pikachux.hearthstead.client.gui.BuildingScreen;
import dev.pikachux.hearthstead.client.gui.CitizenScreen;
import dev.pikachux.hearthstead.network.BuildingViewPayload;
import dev.pikachux.hearthstead.network.CitizenViewPayload;
import dev.pikachux.hearthstead.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class HearthsteadClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRenderers.register(ModEntities.CITIZEN, CitizenRenderer::new);
        EntityRenderers.register(ModEntities.BARBARIAN, context -> new BarbarianRenderer(context, "barbarian", 1.0F));
        EntityRenderers.register(ModEntities.ARCHER_BARBARIAN, context -> new BarbarianRenderer(context, "archer_barbarian", 1.0F));
        EntityRenderers.register(ModEntities.CHIEF_BARBARIAN, context -> new BarbarianRenderer(context, "chief_barbarian", 1.15F));

        BuildToolClient.register();

        ClientPlayNetworking.registerGlobalReceiver(BuildingViewPayload.TYPE, (payload, context) -> {
            if (context.client().screen instanceof BuildingScreen screen && screen.pos().equals(payload.pos())) {
                screen.update(payload);
            } else if (!payload.updateOnly()) {
                context.client().setScreen(new BuildingScreen(payload));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CitizenViewPayload.TYPE,
                (payload, context) -> context.client().setScreen(new CitizenScreen(payload)));
    }
}
