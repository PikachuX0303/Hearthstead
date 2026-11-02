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
 * else wears one of the built-in default player skins that fits them. Grown-up women get a small
 * chest shape ({@link CitizenChestLayer}).
 */
public class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, CitizenRenderState, HumanoidModel<CitizenRenderState>> {
    /** The built-in default skins, split so that men and women get a fitting one. */
    private static final Identifier[] MALE_SKINS = defaults("steve", "kai", "sunny", "zuri");
    private static final Identifier[] FEMALE_SKINS = defaults("alex", "ari", "efe", "makena", "noor");
    private static final Map<String, Identifier> OUTFITS = new HashMap<>();

    private static Identifier[] defaults(String... names) {
        Identifier[] textures = new Identifier[names.length];
        for (int i = 0; i < names.length; i++) {
            textures[i] = Identifier.withDefaultNamespace("textures/entity/player/wide/" + names[i] + ".png");
        }
        return textures;
    }

    private final HumanoidModel<CitizenRenderState> wide;
    private final HumanoidModel<CitizenRenderState> slim;

    public CitizenRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        wide = model;
        slim = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM));
        addLayer(new CitizenChestLayer(this, context.bakeLayer(CitizenChestLayer.LAYER), this::getTextureLocation));
    }

    /** Draws slim-armed skins with the slim player model, all others with the wide one. */
    @Override
    public void submit(CitizenRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack,
                       net.minecraft.client.renderer.SubmitNodeCollector collector,
                       net.minecraft.client.renderer.state.CameraRenderState camera) {
        model = state.slim ? slim : wide;
        super.submit(state, poseStack, collector, camera);
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
        state.slim = !state.outfit.isEmpty()
                && CitizenEntity.SLIM_OUTFITS.contains(state.outfit + (state.female ? "_female" : "_male"));
    }

    @Override
    public Identifier getTextureLocation(CitizenRenderState state) {
        if (!state.outfit.isEmpty()) {
            return OUTFITS.computeIfAbsent(state.outfit + (state.female ? "_female" : "_male"),
                    name -> Hearthstead.id("textures/entity/citizen/" + name + ".png"));
        }
        Identifier[] defaults = state.female ? FEMALE_SKINS : MALE_SKINS;
        return defaults[Math.floorMod(state.skin, defaults.length)];
    }
}
