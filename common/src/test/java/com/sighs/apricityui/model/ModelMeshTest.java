package com.sighs.apricityui.model;

import com.sighs.apricityui.render.GuiItemDepths;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelMeshTest {
    private ModelMesh.Vertex vertex(float x, float y, float z) {
        return new ModelMesh.Vertex(x, y, z, 0xffaabbcc, .25F, .75F, 7, 15, 0, 0, 1);
    }

    private ModelMesh<String> mesh() {
        ModelMesh<String> mesh = new ModelMesh<>();
        mesh.add("front", List.of(vertex(-1, -1, 1), vertex(1, -1, 1), vertex(1, 1, 1), vertex(-1, 1, 1)));
        mesh.add("back", List.of(vertex(-1, -1, -1), vertex(1, -1, -1), vertex(1, 1, -1), vertex(-1, 1, -1)));
        return mesh;
    }

    private ModelMesh<String> tallMesh() {
        ModelMesh<String> mesh = new ModelMesh<>();
        mesh.add("front", List.of(vertex(-.5F, -2, .25F), vertex(.5F, -2, .25F),
                vertex(.5F, 2, .25F), vertex(-.5F, 2, .25F)));
        mesh.add("back", List.of(vertex(-.5F, -2, -.25F), vertex(.5F, -2, -.25F),
                vertex(.5F, 2, -.25F), vertex(-.5F, 2, -.25F)));
        return mesh;
    }

    private double maxScreenExtent(List<ModelMesh.Face<String>> faces) {
        var vertices = faces.stream().flatMap(face -> face.vertices().stream()).toList();
        double minX = Double.POSITIVE_INFINITY, minY = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (var vertex : vertices) {
            minX = Math.min(minX, vertex.x()); maxX = Math.max(maxX, vertex.x());
            minY = Math.min(minY, vertex.y()); maxY = Math.max(maxY, vertex.y());
        }
        return Math.max(maxX - minX, maxY - minY);
    }

    @Test
    void flatGeometryIsCoplanarAndPreservesMaterialsUvAndFiniteNormals() {
        var faces = mesh().layout(ModelSpec.parse("bakedmodel", "minecraft:zombie", "true", "30", "10", "0"),
                0, 0, 100, 200, new Matrix4f(), new Matrix3f());
        assertEquals(List.of("back", "front"), faces.stream().map(ModelMesh.Face::material).toList());
        for (var face : faces) for (var v : face.vertices()) {
            assertEquals(0, v.z());
            assertEquals(.25F, v.u());
            assertEquals(0xffaabbcc, v.color());
            assertTrue(Float.isFinite(v.nx()) && Float.isFinite(v.ny()) && Float.isFinite(v.nz()));
            assertEquals(1, v.nx()*v.nx()+v.ny()*v.ny()+v.nz()*v.nz(), 1e-5);
            assertTrue(v.x() >= -1e-4 && v.x() <= 100.0001);
            assertTrue(v.y() >= -1e-4 && v.y() <= 200.0001);
        }
    }

    @Test
    void normalModeRetainsDepthAndFitsWithoutStretching() {
        var faces = mesh().layout(ModelSpec.parse("bakedmodel", "minecraft:zombie", null, null, null, null),
                10, 20, 100, 200, new Matrix4f(), new Matrix3f());
        var front = faces.get(0).vertices();
        double width = front.stream().mapToDouble(ModelMesh.Vertex::x).max().orElseThrow()
                - front.stream().mapToDouble(ModelMesh.Vertex::x).min().orElseThrow();
        double height = front.stream().mapToDouble(ModelMesh.Vertex::y).max().orElseThrow()
                - front.stream().mapToDouble(ModelMesh.Vertex::y).min().orElseThrow();
        assertEquals(width, height, 1e-4);
        assertEquals(60, front.stream().mapToDouble(ModelMesh.Vertex::x).average().orElseThrow(), 1e-4);
        assertEquals(120, front.stream().mapToDouble(ModelMesh.Vertex::y).average().orElseThrow(), 1e-4);
        assertTrue(front.get(0).z() > 0 && front.get(0).z() < 25);
        assertEquals(front.get(0).z(), -faces.get(1).vertices().get(0).z(), 1e-4);
    }

    @Test
    void normalModelDepthStaysBehindGuiItemDecorations() {
        var faces = mesh().layout(ModelSpec.parse("bakedmodel", "minecraft:iron_golem", null, "18", "-6", null),
                0, 0, 300, 300, new Matrix4f().translate(0, 0, GuiItemDepths.SCREEN_ITEM_MODEL_Z), new Matrix3f());
        double maxZ = faces.stream().flatMap(face -> face.vertices().stream())
                .mapToDouble(ModelMesh.Vertex::z).max().orElseThrow();
        assertTrue(maxZ < GuiItemDepths.SCREEN_ITEM_DECORATION_Z,
                "rotated model overlaps the GUI decoration depth layer: " + maxZ);
    }

    @Test
    void rotationDoesNotRefitTheModelToItsProjectedBounds() {
        var upright = tallMesh().layout(new ModelSpec("bakedmodel", "minecraft:iron_golem", false, 0, 0, 0),
                0, 0, 300, 300, new Matrix4f(), new Matrix3f());
        var edgeOn = tallMesh().layout(new ModelSpec("bakedmodel", "minecraft:iron_golem", false, 0, 90, 0),
                0, 0, 300, 300, new Matrix4f(), new Matrix3f());
        assertTrue(maxScreenExtent(edgeOn) < maxScreenExtent(upright) * 0.5,
                "edge-on model was enlarged to refill its preview box");
    }

    @Test
    void flatteningHappensBeforeDocumentRotation() {
        var faces = mesh().layout(ModelSpec.parse("bakedmodel", "minecraft:zombie", "true", "0", "0", "0"),
                -50, -50, 100, 100, new Matrix4f().rotateY((float)Math.PI / 2), new Matrix3f());
        for (var face : faces) for (var v : face.vertices()) assertEquals(0, v.x(), 1e-4);
        assertNotEquals(faces.get(0).vertices().get(0).z(), faces.get(0).vertices().get(1).z());
    }

    @Test
    void rotationsApplyYawThenPitchThenRollAndKeepCoincidentFacesStable() {
        var spec = new ModelSpec("bakedmodel", "minecraft:zombie", true, 90, 90, 90);
        var faces = mesh().layout(spec, 0, 0, 100, 100, new Matrix4f(), new Matrix3f());
        var front = faces.stream().filter(face -> face.material().equals("front")).findFirst().orElseThrow();
        // +Z -> +X (yaw) -> +X (pitch) -> +Y (roll), then GUI Y inversion.
        var normal = front.vertices().get(0);
        assertEquals(0, normal.nx(), 1e-5);
        assertEquals(-1, normal.ny(), 1e-5);
        assertEquals(0, normal.nz(), 1e-5);
        ModelMesh<String> layers = new ModelMesh<>();
        var quad = List.of(vertex(-1, -1, 0), vertex(1, -1, 0), vertex(1, 1, 0), vertex(-1, 1, 0));
        layers.add("body", quad);
        layers.add("skin", quad);
        assertEquals(List.of("body", "skin"), layers.layout(spec, 0, 0, 100, 100,
                new Matrix4f(), new Matrix3f()).stream().map(ModelMesh.Face::material).toList());
    }

    @Test
    void invalidNumbersAndEmptyBoundsAreSafe() {
        var spec = ModelSpec.parse(" BAKEDMODEL ", " minecraft:zombie ", "false", "NaN", "Infinity", "bad");
        assertEquals(new ModelSpec("bakedmodel", "minecraft:zombie", false, 0, 0, 0), spec);
        assertTrue(mesh().layout(spec, 0, 0, 0, 100, new Matrix4f(), new Matrix3f()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> mesh().add("bad", List.of(vertex(Float.NaN, 0, 0))));
    }
}
