package com.sighs.apricityui.chunkmap;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Shared world/screen projection for native terrain, entities and map controls. */
public record AuiMapCamera(double x, double y, double z, double pixelsPerBlock,
                               double yaw, double angle, String mode, int width, int height, double unit, double fieldOfView) {
    public AuiMapCamera(double x, double y, double z, double pixelsPerBlock,
                            double yaw, String mode, int width, int height, double unit, double fieldOfView) {
        this(x, y, z, pixelsPerBlock, yaw, mode.equals("top") ? 0 : mode.equals("street")
                ? Math.PI / 2 : Math.acos(1 / Math.sqrt(3)), mode, width, height, unit, fieldOfView);
    }

    private Matrix4f matrix() {
        return projectionMatrix().mul(viewMatrix());
    }

    public Vector3f eyeOffset() {
        float halfHeight = (float) (height / (2 * pixelsPerBlock));
        float cameraAngle = mode.equals("top") ? 0.0001f : (float) Math.clamp(angle, 0.0001, Math.PI - 0.0001);
        float distance = mode.equals("street") ? 0.0001f * (float) unit : Math.max(300 * (float) unit,
                halfHeight / (float) Math.tan(Math.toRadians(37.5)));
        return new Vector3f(-(float) Math.sin(yaw) * (float) Math.sin(cameraAngle) * distance,
                (float) Math.cos(cameraAngle) * distance, (float) Math.cos(yaw) * (float) Math.sin(cameraAngle) * distance);
    }

    public Matrix4f viewMatrix() {
        return new Matrix4f().lookAt(eyeOffset(), new Vector3f(), new Vector3f(0, 1, 0));
    }

    public Matrix4f projectionMatrix() {
        float halfHeight = (float) (height / (2 * pixelsPerBlock));
        float halfWidth = halfHeight * width / height;
        return mode.equals("street")
                ? new Matrix4f().perspective((float) Math.toRadians(fieldOfView), (float) width / height,
                        0.01f * (float) unit, 2000 * (float) unit)
                : new Matrix4f().ortho(-halfWidth, halfWidth, -halfHeight, halfHeight, (float) unit, 100000 * (float) unit);
    }

    /** Converts the shared projection to Minecraft's reverse-Z device clip range. */
    public Matrix4f nativeProjectionMatrix(boolean zeroToOne) {
        return new Matrix4f().m22(zeroToOne ? -0.5F : -1F).m32(zeroToOne ? 0.5F : 0F).mul(projectionMatrix());
    }

    public Vector3f project(double worldX, double worldY, double worldZ) {
        Vector4f projected = matrix().transform(new Vector4f((float) (worldX - x),
                (float) (worldY - y), (float) (worldZ - z), 1));
        if (projected.w <= 0) return new Vector3f(Float.NaN, Float.NaN, Float.NaN);
        projected.div(projected.w);
        return new Vector3f((projected.x + 1) * width / 2, (1 - projected.y) * height / 2, projected.z);
    }

    public Vector3f[] ray(double screenX, double screenY) {
        Matrix4f inverse = matrix().invert();
        float nx = (float) (screenX / width * 2 - 1), ny = (float) (1 - screenY / height * 2);
        Vector4f near = inverse.transform(new Vector4f(nx, ny, -1, 1)); near.div(near.w);
        Vector4f far = inverse.transform(new Vector4f(nx, ny, 1, 1)); far.div(far.w);
        return new Vector3f[]{new Vector3f(near.x, near.y, near.z),
                new Vector3f(far.x - near.x, far.y - near.y, far.z - near.z).normalize()};
    }
}
