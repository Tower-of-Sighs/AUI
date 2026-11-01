package com.sighs.apricityui.model;

import com.sighs.apricityui.render.GuiItemDepths;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Captured entity geometry, before document transforms and paint depth. */
public final class ModelMesh<M> {
    public record Vertex(float x, float y, float z, int color, float u, float v,
                         int overlay, int light, float nx, float ny, float nz) {
        public Vertex at(Vector3f position, Vector3f normal) {
            return new Vertex(position.x, position.y, position.z, color, u, v, overlay, light,
                    normal.x, normal.y, normal.z);
        }
    }

    public record Face<M>(M material, List<Vertex> vertices, float depth) { }
    private final List<Face<M>> faces = new ArrayList<>();

    public void add(M material, List<Vertex> vertices) {
        if (vertices.isEmpty()) return;
        for (Vertex v : vertices) {
            if (!Float.isFinite(v.x) || !Float.isFinite(v.y) || !Float.isFinite(v.z)) {
                throw new IllegalArgumentException("Non-finite model vertex");
            }
        }
        faces.add(new Face<>(material, List.copyOf(vertices), 0));
    }

    public boolean isEmpty() { return faces.isEmpty(); }

    public List<Face<M>> layout(ModelSpec spec, float x, float y, float width, float height,
                                Matrix4f document, Matrix3f documentNormal) {
        if (faces.isEmpty() || width <= 0 || height <= 0) return List.of();
        float radians = (float) (Math.PI / 180.0);
        // Pre-multiply so the declared application order is yaw, pitch, roll.
        Matrix3f rotation = new Matrix3f().rotateZ(spec.roll() * radians)
                .rotateX(spec.pitch() * radians).rotateY(spec.yaw() * radians);
        List<Face<M>> rotated = new ArrayList<>(faces.size());
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        float sourceMinX = minX, sourceMinY = minX, sourceMinZ = minX;
        float sourceMaxX = maxX, sourceMaxY = maxX, sourceMaxZ = maxX;
        for (Face<M> face : faces) {
            List<Vertex> vertices = new ArrayList<>(face.vertices.size());
            float depth = 0;
            for (Vertex v : face.vertices) {
                sourceMinX = Math.min(sourceMinX, v.x); sourceMaxX = Math.max(sourceMaxX, v.x);
                sourceMinY = Math.min(sourceMinY, v.y); sourceMaxY = Math.max(sourceMaxY, v.y);
                sourceMinZ = Math.min(sourceMinZ, v.z); sourceMaxZ = Math.max(sourceMaxZ, v.z);
                Vector3f p = rotation.transform(new Vector3f(v.x, v.y, v.z));
                Vector3f n = rotation.transform(new Vector3f(v.nx, v.ny, v.nz));
                vertices.add(v.at(p, n));
                minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
                minY = Math.min(minY, p.y); maxY = Math.max(maxY, p.y);
                minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
                depth += p.z;
            }
            rotated.add(new Face<>(face.material, vertices, depth / vertices.size()));
        }
        float extentX = maxX - minX, extentY = maxY - minY;
        if (extentX < 1.0e-6F && extentY < 1.0e-6F) return List.of();
        float sourceExtentX = sourceMaxX - sourceMinX, sourceExtentY = sourceMaxY - sourceMinY;
        float sourceExtentZ = sourceMaxZ - sourceMinZ;
        float modelDiameter = (float) Math.sqrt(sourceExtentX * sourceExtentX
                + sourceExtentY * sourceExtentY + sourceExtentZ * sourceExtentZ);
        if (modelDiameter < 1.0e-6F) return List.of();
        float scale = Math.min(width, height) / modelDiameter;
        float depthScale = Math.min(scale,
                (GuiItemDepths.SCREEN_ITEM_DECORATION_Z - GuiItemDepths.SCREEN_ITEM_MODEL_Z) / modelDiameter);
        float centerX = (minX + maxX) / 2, centerY = (minY + maxY) / 2, centerZ = (minZ + maxZ) / 2;
        // GUI depth grows towards the viewer. Stable sort preserves coincident layer order.
        if (spec.flat()) rotated.sort(Comparator.comparingDouble(Face::depth));
        List<Face<M>> result = new ArrayList<>(rotated.size());
        for (Face<M> face : rotated) {
            List<Vertex> vertices = new ArrayList<>(face.vertices.size());
            for (Vertex v : face.vertices) {
                Vector3f p = new Vector3f(x + width / 2 + (v.x - centerX) * scale,
                        y + height / 2 - (v.y - centerY) * scale,
                        spec.flat() ? 0 : (v.z - centerZ) * depthScale);
                document.transformPosition(p);
                Vector3f n = documentNormal.transform(new Vector3f(v.nx, -v.ny, v.nz));
                if (!n.isFinite() || n.lengthSquared() < 1.0e-12F) n.set(0, 0, 1);
                else n.normalize();
                vertices.add(v.at(p, n));
            }
            result.add(new Face<>(face.material, vertices, face.depth));
        }
        return result;
    }
}
