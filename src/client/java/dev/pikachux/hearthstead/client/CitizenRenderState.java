package dev.pikachux.hearthstead.client;

import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class CitizenRenderState extends HumanoidRenderState {
    public int skin;
    /** The job outfit, or "" for an everyday skin. */
    public String outfit = "";
    public boolean female;
    /** Whether the skin has slim (3 pixel) arms. */
    public boolean slim;
}
