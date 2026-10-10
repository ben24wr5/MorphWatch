package com.morphwatch.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.List;

/** Passes vertices straight through, and remembers where each one was (to find a model's real shape). */
final class CapturingVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final List<float[]> points;

    CapturingVertexConsumer(VertexConsumer delegate, List<float[]> points) {
        this.delegate = delegate;
        this.points = points;
    }

    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        if (points.size() < 20000) points.add(new float[]{(float) x, (float) y, (float) z});
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(red, green, blue, alpha);
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
        delegate.defaultColor(red, green, blue, alpha);
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }

    /** The fast path model parts use. */
    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha,
                       float u, float v, int overlay, int light, float nx, float ny, float nz) {
        if (points.size() < 20000) points.add(new float[]{x, y, z});
        delegate.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, nx, ny, nz);
    }
}
