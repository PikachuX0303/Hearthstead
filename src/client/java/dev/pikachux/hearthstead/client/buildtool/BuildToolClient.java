package dev.pikachux.hearthstead.client.buildtool;

import dev.pikachux.hearthstead.block.HutBlock;
import dev.pikachux.hearthstead.colony.BuildingType;
import dev.pikachux.hearthstead.network.BlueprintPayload;
import dev.pikachux.hearthstead.network.BlueprintRequestPayload;
import dev.pikachux.hearthstead.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Client side wiring of the build tool.
 */
public final class BuildToolClient {
    private BuildToolClient() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(PreviewRenderer::render);

        ClientPlayNetworking.registerGlobalReceiver(BlueprintPayload.TYPE, (payload, context) -> {
            BuildingType type = BuildingType.byId(payload.buildingType());
            BuildPreview preview;
            if (payload.existingHut().isPresent()) {
                BlockPos hut = payload.existingHut().get();
                preview = new BuildPreview(type, payload.level(), payload.blueprint(), hut, payload.hutFacing(), hut,
                        payload.style(), payload.styles());
            } else {
                BlockPos pos = PendingPlacement.pos() != null ? PendingPlacement.pos() : context.player().blockPosition();
                preview = new BuildPreview(type, payload.level(), payload.blueprint(), pos, PendingPlacement.facing(), null,
                        payload.style(), payload.styles());
            }
            BuildPreview.set(preview);
            context.client().setScreen(new BuildToolScreen());
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || !player.getItemInHand(hand).is(ModItems.BUILD_TOOL)) {
                return InteractionResult.PASS;
            }
            Minecraft minecraft = Minecraft.getInstance();
            BlockState clicked = level.getBlockState(hit.getBlockPos());
            if (clicked.getBlock() instanceof HutBlock hut) {
                ClientPlayNetworking.send(new BlueprintRequestPayload(hut.type().id(), Optional.of(hit.getBlockPos()), ""));
                return InteractionResult.SUCCESS;
            }
            BlockPos target = clicked.canBeReplaced() ? hit.getBlockPos() : hit.getBlockPos().relative(hit.getDirection());
            BuildPreview preview = BuildPreview.current();
            if (preview != null && !preview.isUpgrade()) {
                preview.moveTo(target);
                minecraft.setScreen(new BuildToolScreen());
            } else {
                minecraft.setScreen(new HutSelectScreen(target, player.getDirection().getOpposite()));
            }
            return InteractionResult.SUCCESS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || !player.getItemInHand(hand).is(ModItems.BUILD_TOOL)) {
                return InteractionResult.PASS;
            }
            if (BuildPreview.current() != null) {
                Minecraft.getInstance().setScreen(new BuildToolScreen());
            } else {
                player.displayClientMessage(Component.translatable("gui.hearthstead.build_tool.click_block"), true);
            }
            return InteractionResult.SUCCESS;
        });
    }
}
