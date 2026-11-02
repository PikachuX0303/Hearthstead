package dev.pikachux.hearthstead.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.pikachux.hearthstead.Hearthstead;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.function.Function;

/**
 * A small, slightly angled chest shape for grown-up women, attached to the body so it follows
 * every pose. Its faces are cut from the skin's own shirt (and jacket layer), so it always
 * matches the outfit.
 */
public class CitizenChestLayer extends RenderLayer<CitizenRenderState, HumanoidModel<CitizenRenderState>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Hearthstead.id("citizen"), "chest");

    private final ModelPart chest;
    private final Function<CitizenRenderState, Identifier> texture;

    public CitizenChestLayer(RenderLayerParent<CitizenRenderState, HumanoidModel<CitizenRenderState>> parent, ModelPart chest,
                             Function<CitizenRenderState, Identifier> texture) {
        super(parent);
        this.chest = chest;
        this.texture = texture;
    }

    /**
     * An 8 by 4 by 1 pixel block just below the collar. Its UVs put the front face on the shirt
     * rows 22 to 25 of the body's front (and of the jacket layer for the outer box), the top,
     * bottom and sides on the shirt around it.
     */
    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("chest", CubeListBuilder.create()
                        .texOffs(19, 21).addBox(-4F, 0F, -1F, 8F, 4F, 1F)
                        .texOffs(19, 37).addBox(-4F, 0F, -1F, 8F, 4F, 1F, new CubeDeformation(0.25F)),
                // Two pixels below the neck, resting against the chest and tilted a little forward at the bottom.
                PartPose.offsetAndRotation(0F, 2F, -1.5F, -0.2F, 0F, 0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CitizenRenderState state, float yRot, float xRot) {
        if (!state.female || state.isBaby || state.isInvisible) {
            return;
        }
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        collector.submitModelPart(chest, poseStack, RenderTypes.entityCutoutNoCull(texture.apply(state)), light,
                LivingEntityRenderer.getOverlayCoords(state, 0F), null);
        poseStack.popPose();
    }
}
