package com.morphwatch.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Passes vertices through but paints them all one colour, which turns a mob model into a glowing silhouette. */
final class HoloVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int r;
    private final int g;
    private final int b;
    private final int a;

    HoloVertexConsumer(VertexConsumer delegate, int r, int g, int b, int a) {
        this.delegate = delegate;
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(r, g, b, alpha * a / 255);
        return this;
    }

    @Override
    public VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor(r, g, b, a);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }

    /** The fast path model parts use: swap in our colour and keep everything else. */
    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha,
                       float u, float v, int overlay, int light, float nx, float ny, float nz) {
        delegate.vertex(x, y, z, r / 255.0F, g / 255.0F, b / 255.0F, alpha * (a / 255.0F),
                u, v, overlay, light, nx, ny, nz);
    }
}
