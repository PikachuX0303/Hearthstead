package dev.pikachux.hearthstead.client.buildtool;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Passes vertices through while forcing a fixed transparency and tint, so blocks
 * render as see-through "ghosts".
 */
final class GhostVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int alpha;
    private final int tint;

    GhostVertexConsumer(VertexConsumer delegate, int alpha, int tint) {
        this.delegate = delegate;
        this.alpha = alpha;
        this.tint = tint;
    }

    private int tinted(int channel, int shift) {
        return channel * ((tint >> shift) & 0xFF) / 255;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int ignoredAlpha) {
        delegate.setColor(tinted(red, 16), tinted(green, 8), tinted(blue, 0), alpha);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return setColor((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, 0);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light,
                          float normalX, float normalY, float normalZ) {
        int r = tinted((color >> 16) & 0xFF, 16);
        int g = tinted((color >> 8) & 0xFF, 8);
        int b = tinted(color & 0xFF, 0);
        delegate.addVertex(x, y, z, (alpha << 24) | (r << 16) | (g << 8) | b, u, v, overlay, light, normalX, normalY, normalZ);
    }
}
