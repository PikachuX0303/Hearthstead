package dev.pikachux.hearthstead.client.buildtool;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.pikachux.hearthstead.blueprint.Blueprint;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Draws the build tool preview: the blueprint as translucent blocks plus an outline
 * of the area the builder will use.
 */
public final class PreviewRenderer {
    private static final int GHOST_ALPHA = 150;
    private static final int GHOST_TINT = 0xC8E6FF;
    private static final int OUTLINE_COLOR = 0xFF5FD4FF;
    /** Blocks that would be built over something else are tinted red. */
    private static final int BLOCKED_TINT = 0xFF8080;

    private PreviewRenderer() {
    }

    public static void render(WorldRenderContext context) {
        BuildPreview preview = BuildPreview.current();
        Minecraft minecraft = Minecraft.getInstance();
        if (preview == null || minecraft.level == null) {
            return;
        }
        Vec3 camera = context.worldState().cameraRenderState.pos;
        PoseStack poseStack = context.matrices();
        BlockRenderDispatcher blocks = minecraft.getBlockRenderer();
        Blueprint blueprint = preview.blueprint();
        Rotation rotation = blueprint.rotationFor(preview.facing());

        VertexConsumer buffer = context.consumers().getBuffer(RenderTypes.translucentMovingBlock());
        VertexConsumer ghost = new GhostVertexConsumer(buffer, GHOST_ALPHA, GHOST_TINT);
        VertexConsumer blocked = new GhostVertexConsumer(buffer, GHOST_ALPHA, BLOCKED_TINT);
        for (Blueprint.Entry entry : blueprint.entries()) {
            BlockState state = entry.state().rotate(rotation);
            if (state.isAir() || state.is(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            BlockPos pos = preview.pos().offset(entry.offset().rotate(rotation));
            BlockState existing = minecraft.level.getBlockState(pos);
            if (existing.getBlock() == state.getBlock()) {
                continue;
            }
            boolean obstructed = !existing.canBeReplaced() && !preview.isUpgrade();
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            ModelBlockRenderer.renderModel(poseStack.last(), obstructed ? blocked : ghost, blocks.getBlockModel(state),
                    1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        BoundingBox box = blueprint.bounds(preview.pos(), preview.facing());
        AABB outline = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1)
                .move(-camera.x, -camera.y, -camera.z);
        ShapeRenderer.renderShape(poseStack, context.consumers().getBuffer(RenderTypes.lines()), Shapes.create(outline),
                0, 0, 0, OUTLINE_COLOR, 2.0F);
    }
}
