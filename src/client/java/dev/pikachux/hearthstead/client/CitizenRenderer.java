package dev.pikachux.hearthstead.client;

import dev.pikachux.hearthstead.Hearthstead;
import dev.pikachux.hearthstead.entity.CitizenEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders citizens with the player model, so skins show their outer layer too. Citizens with a
 * job outfit wear it (the job skins by FallenStar114, in a male and a female version); everyone
 * else wears one of the built-in default player skins for variety.
 */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, CitizenRenderState, HumanoidModel<CitizenRenderState>> {
    private static final String[] SKINS = {"steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri"};
    private static final Identifier[] TEXTURES = new Identifier[SKINS.length];
    private static final Map<String, Identifier> OUTFITS = new HashMap<>();

    static {
        for (int i = 0; i < SKINS.length; i++) {
            TEXTURES[i] = Identifier.withDefaultNamespace("textures/entity/player/wide/" + SKINS[i] + ".png");
        }
    }

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
    }

    @Override
    public CitizenRenderState createRenderState() {
        return new CitizenRenderState();
    }

    @Override
    public void extractRenderState(CitizenEntity entity, CitizenRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.skin = entity.skin();
        state.outfit = entity.outfit();
        state.female = entity.female();
    }

    @Override
    public Identifier getTextureLocation(CitizenRenderState state) {
        if (!state.outfit.isEmpty()) {
            return OUTFITS.computeIfAbsent(state.outfit + (state.female ? "_female" : "_male"),
                    name -> Hearthstead.id("textures/entity/citizen/" + name + ".png"));
        }
        return TEXTURES[Math.floorMod(state.skin, TEXTURES.length)];
    }
}
