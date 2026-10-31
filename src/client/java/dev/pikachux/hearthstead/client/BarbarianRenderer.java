package dev.pikachux.hearthstead.client;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.entity.raider.AbstractBarbarian;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;

/**
 * Renders barbarians with the humanoid model and the mod's own barbarian skins.
 */
public class BarbarianRenderer extends HumanoidMobRenderer<AbstractBarbarian, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private final Identifier texture;
    private final float scale;

    public BarbarianRenderer(EntityRendererProvider.Context context, String skin, float scale) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F * scale);
        this.texture = Hearthstead.id("textures/entity/barbarian/" + skin + ".png");
        this.scale = scale;
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public void extractRenderState(AbstractBarbarian entity, HumanoidRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.scale = scale;
    }

    @Override
    protected HumanoidModel.ArmPose getArmPose(AbstractBarbarian entity, HumanoidArm arm) {
        if (entity.getMainArm() == arm && entity.isAggressive() && entity.getMainHandItem().is(Items.BOW)) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        return super.getArmPose(entity, arm);
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return texture;
    }
}
