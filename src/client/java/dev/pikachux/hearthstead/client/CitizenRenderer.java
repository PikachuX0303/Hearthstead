package dev.pikachux.hearthstead.client;

import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

/**
 * Renders citizens with the humanoid model, using the built-in default player skins
 * for variety.
 */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, CitizenRenderState, HumanoidModel<CitizenRenderState>> {
    private static final String[] SKINS = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
    private static final Identifier[] TEXTURES = new Identifier[SKINS.length];

    static {
        for (int i = 0; i < SKINS.length; i++) {
            TEXTURES[i] = Identifier.withDefaultNamespace("textures/entity/player/wide/" + SKINS[i] + ".png");
        }
    }

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public CitizenRenderState createRenderState() {
        return new CitizenRenderState();
    }

    @Override
    public void extractRenderState(CitizenEntity entity, CitizenRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.skin = entity.skin();
    }

    @Override
    public Identifier getTextureLocation(CitizenRenderState state) {
        return TEXTURES[Math.floorMod(state.skin, TEXTURES.length)];
    }
}
