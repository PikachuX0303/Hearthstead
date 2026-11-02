package dev.pikachux.hearthstead.client;

import dev.pikachux.hearthstead.client.buildtool.BuildToolClient;
import dev.pikachux.hearthstead.client.gui.BuildingScreen;
import dev.pikachux.hearthstead.client.gui.CitizenScreen;
import dev.pikachux.hearthstead.client.gui.ClipboardScreen;
import dev.pikachux.hearthstead.network.BuildingViewPayload;
import dev.pikachux.hearthstead.network.CitizenViewPayload;
import dev.pikachux.hearthstead.network.RequestsViewPayload;
import dev.pikachux.hearthstead.registry.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.entity.EntityRenderers;

public class HearthsteadClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry.registerModelLayer(CitizenChestLayer.LAYER,
                CitizenChestLayer::create);
        EntityRenderers.register(ModEntities.CITIZEN, CitizenRenderer::new);
        EntityRenderers.register(ModEntities.BARBARIAN, context -> new BarbarianRenderer(context, "barbarian", 1.0F));
        EntityRenderers.register(ModEntities.ARCHER_BARBARIAN, context -> new BarbarianRenderer(context, "archer_barbarian", 1.0F));
        EntityRenderers.register(ModEntities.CHIEF_BARBARIAN, context -> new BarbarianRenderer(context, "chief_barbarian", 1.15F));
        EntityRenderers.register(ModEntities.PIRATE, context -> new BarbarianRenderer(context, "pirate", 1.0F));
        EntityRenderers.register(ModEntities.PIRATE_ARCHER, context -> new BarbarianRenderer(context, "pirate_archer", 1.0F));
        EntityRenderers.register(ModEntities.PIRATE_CAPTAIN, context -> new BarbarianRenderer(context, "pirate_captain", 1.15F));

        BuildToolClient.register();
        UpdateClient.register();

        ClientPlayNetworking.registerGlobalReceiver(BuildingViewPayload.TYPE, (payload, context) -> {
            if (context.client().screen instanceof BuildingScreen screen && screen.pos().equals(payload.pos())) {
                screen.update(payload);
            } else if (!payload.updateOnly()) {
                context.client().setScreen(new BuildingScreen(payload));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(RequestsViewPayload.TYPE, (payload, context) -> {
            if (context.client().screen instanceof ClipboardScreen screen) {
                screen.update(payload);
            } else if (!payload.updateOnly()) {
                context.client().setScreen(new ClipboardScreen(payload));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(dev.pikachux.hearthstead.network.PermissionsViewPayload.TYPE, (payload, context) -> {
            if (context.client().screen instanceof dev.pikachux.hearthstead.client.gui.PermissionsScreen screen) {
                screen.update(payload);
            } else if (!payload.updateOnly()) {
                context.client().setScreen(new dev.pikachux.hearthstead.client.gui.PermissionsScreen(payload));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CitizenViewPayload.TYPE,
                (payload, context) -> context.client().setScreen(new CitizenScreen(payload)));
    }
}
