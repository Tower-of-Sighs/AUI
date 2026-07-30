package com.sighs.apricityui.instance;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.WorldPaintDepth;
import com.sighs.apricityui.layout.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class WorldWindow {
    static final List<WorldWindow> windows = new ArrayList<>();
    private static final float DEFAULT_NEAR_DEPTH_STEP = 0.00035f;
    private static final float DEFAULT_FAR_DEPTH_STEP = 0.0030f;
    private static final float DEFAULT_DEPTH_NEAR_DISTANCE = 2.0f;
    private static final float DEFAULT_DEPTH_OFFSET_SCALE = 0.0005f;
    private static final float POLYGON_OFFSET = -1.0f;
    private static final float DEFAULT_VIEWPORT_FILL = 0.8f;
    private static final float FALLBACK_WORLD_SCALE = 0.02f;
    private static final double OCCLUSION_DISTANCE_EPSILON = 1.0e-4d;

    public Document document;
    private Vec3 position;
    private final Quaternionf rotation;
    /** Legacy explicit world units represented by one logical CSS pixel. */
    private Float scaleOverride;
    /** Scale used by rendering and interaction hit testing. */
    private float resolvedScale = FALLBACK_WORLD_SCALE;
    private long resolvedViewportVersion = Long.MIN_VALUE;
    private boolean depthTest = true;
    private Float widthOverride;
    private Float heightOverride;
    private int maxDistance;
    private float nearDepthStep = DEFAULT_NEAR_DEPTH_STEP;
    private float farDepthStep = DEFAULT_FAR_DEPTH_STEP;
    private float depthNearDistance = DEFAULT_DEPTH_NEAR_DISTANCE;
    private float depthFarDistance;

    /** Creates a world window whose logical size comes from the document viewport. */
    public WorldWindow(String documentPath, Vec3 position, int maxDistance) {
        this.document = Document.createInWorld(documentPath);
        this.position = position;
        this.widthOverride = null;
        this.heightOverride = null;
        this.rotation = new Quaternionf().rotationY((float) Math.toRadians(180.0f));
        this.scaleOverride = null;
        this.maxDistance = sanitizeDistance(maxDistance);
        this.depthFarDistance = Math.max(DEFAULT_DEPTH_NEAR_DISTANCE + 1.0f, this.maxDistance);
    }

    public WorldWindow(String documentPath, double x, double y, double z, int maxDistance) {
        this(documentPath, new Vec3(x, y, z), maxDistance);
    }

    /** Creates a world window with an explicit fixed orientation. */
    public WorldWindow(String documentPath, Vec3 position, int maxDistance, float yaw, float pitch) {
        this(documentPath, position, maxDistance);
        setRotation(yaw, pitch);
    }

    public WorldWindow(String documentPath, Vec3 position, int maxDistance,
                       float yaw, float pitch, float roll) {
        this(documentPath, position, maxDistance);
        setRotation(yaw, pitch, roll);
    }

    /** Creates a world window from Euler angles in degrees: {@code (pitch, yaw, roll)}. */
    public WorldWindow(String documentPath, Vec3 position, int maxDistance, Vec3 eulerDegrees) {
        this(documentPath, position, maxDistance);
        setRotation(eulerDegrees);
    }

    /** Creates a world window from a JOML orientation quaternion. */
    public WorldWindow(String documentPath, Vec3 position, int maxDistance, Quaternionf orientation) {
        this(documentPath, position, maxDistance);
        setOrientation(orientation);
    }

    /**
     * @deprecated The document viewport is the single source of logical width and height.
     *             Use {@link #WorldWindow(String, Vec3, int)} and configure the viewport in
     *             {@code <meta name="aui-viewport">}.
     */
    @Deprecated
    public WorldWindow(String documentPath, Vec3 position, float width, float height, int maxDistance) {
        this(documentPath, position, maxDistance);
        this.widthOverride = sanitizeDimension(width);
        this.heightOverride = sanitizeDimension(height);
        this.scaleOverride = FALLBACK_WORLD_SCALE;
    }

    public void setPosition(Vec3 position) {
        this.position = position;
        if (scaleOverride == null) resolvedViewportVersion = Long.MIN_VALUE;
    }

    protected Vec3 getPosition() {
        return position;
    }

    public void setRotation(float yRot, float xRot) {
        setRotation(yRot, xRot, 0.0f);
    }

    /** Sets Euler angles in degrees: yaw, pitch, roll. */
    public void setRotation(float yaw, float pitch, float roll) {
        float safeYaw = Float.isFinite(yaw) ? yaw : 0.0f;
        float safePitch = Float.isFinite(pitch) ? pitch : 0.0f;
        float safeRoll = Float.isFinite(roll) ? roll : 0.0f;
        rotation.identity()
                .rotateY((float) Math.toRadians(180.0f - safeYaw))
                .rotateX((float) Math.toRadians(safePitch))
                .rotateZ((float) Math.toRadians(safeRoll));
    }

    /** Sets Euler angles in degrees as {@code (pitch, yaw, roll)}. */
    public void setRotation(Vec3 eulerDegrees) {
        if (eulerDegrees == null) {
            setRotation(0.0f, 0.0f, 0.0f);
            return;
        }
        setRotation((float) eulerDegrees.y, (float) eulerDegrees.x, (float) eulerDegrees.z);
    }

    /** Replaces the world orientation with a copy of the supplied quaternion. */
    public void setOrientation(Quaternionf orientation) {
        if (orientation == null) {
            setRotation(0.0f, 0.0f, 0.0f);
            return;
        }
        rotation.set(orientation);
    }

    public Quaternionf getOrientation() {
        return new Quaternionf(rotation);
    }

    /**
     * Sets an explicit legacy world scale without changing the document viewport.
     * New windows normally fit their document viewport to the camera automatically.
     */
    public void setScale(float scale) {
        if (Float.isFinite(scale) && scale > 0.0f) {
            this.scaleOverride = scale;
            this.resolvedScale = scale;
        }
    }

    /** Enables or disables occlusion by world geometry for this window. */
    public void setDepthTest(boolean depthTest) {
        this.depthTest = depthTest;
    }

    public boolean isDepthTestEnabled() {
        return depthTest;
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    /** Updates the maximum ray distance used by world-window interaction. */
    public void setMaxDistance(int maxDistance) {
        this.maxDistance = sanitizeDistance(maxDistance);
        this.depthFarDistance = Math.max(this.depthNearDistance + 0.001f, this.maxDistance);
    }

    public void setDynamicDepthStep(float nearDepthStep, float farDepthStep, float nearDistance, float farDistance) {
        this.nearDepthStep = Math.max(0.0f, nearDepthStep);
        this.farDepthStep = Math.max(this.nearDepthStep, farDepthStep);
        this.depthNearDistance = Math.max(0.0f, nearDistance);
        this.depthFarDistance = Math.max(this.depthNearDistance + 0.001f, farDistance);
    }

    public float getWidth() {
        if (widthOverride != null) return widthOverride;
        return documentViewportWidth();
    }

    public float getHeight() {
        if (heightOverride != null) return heightOverride;
        return documentViewportHeight();
    }

    public void render(PoseStack poseStack, Matrix4f projectionMatrix, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        float documentScale = worldDocumentScale();
        float worldScale = resolveRenderScale(cameraPos, projectionMatrix);
        float renderScale = worldScale * documentScale;
        resolvedScale = worldScale;

        poseStack.pushPose();
        poseStack.translate(
                position.x - cameraPos.x,
                position.y - cameraPos.y,
                position.z - cameraPos.z
        );

        poseStack.mulPose(new Quaternionf(rotation));

        poseStack.scale(renderScale, -renderScale, renderScale);
        poseStack.translate(-getWidth() / 2.0f, -getHeight() / 2.0f, 0);

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.last().pose().set(poseStack.last().pose());
        poseStack.last().normal().set(poseStack.last().normal());

        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        if (depthTest) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            GL11.glDepthMask(true);
        } else {
            RenderSystem.disableDepthTest();
            GL11.glDepthMask(false);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Depth bias to avoid z-fighting with world geometry.
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(POLYGON_OFFSET, POLYGON_OFFSET);

        float documentDepthBudget = computeDepthStep(cameraPos);
        float safeScale = Math.max(1.0e-4f, renderScale);
        int paintNodeCount = document == null ? 1 : Math.max(1, document.getPaintList().size());
        float localStep = documentDepthBudget / paintNodeCount / safeScale;
        if (localStep > 0.2f) localStep = 0.2f;
        Base.pushDepthStep(localStep);
        Base.pushDepthMode(true);
        Base.pushDepthTest(depthTest);
        Base.pushDocumentZOffset(documentDepthBudget / safeScale);
        WorldPaintDepth.pushFlatTransforms(true);
        Mask.resetDepth(getWidth(), getHeight());
        Mask.pushForceStencil();
        try {
            Base.drawDocument(poseStack, document);
        } finally {
            Mask.popForceStencil();
            WorldPaintDepth.popFlatTransforms();
            Base.popDepthTest();
            Base.popDepthMode();
            Base.popDepthStep();
            Base.popDocumentZOffset();
        }

        bufferSource.endBatch();
        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();
        if (previousDepthTest) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        GL11.glDepthMask(previousDepthMask);

        poseStack.popPose();
    }

    /** Returns the total world-space depth budget for this document. */
    private float computeDepthStep(Vec3 cameraPos) {
        double distance = cameraPos.distanceTo(position);
        double t = Mth.inverseLerp(distance, depthNearDistance, depthFarDistance);
        float depth = (float) Mth.clampedLerp(nearDepthStep, farDepthStep, t);
        return depth * ApricityUIConfig.CLIENT.worldWindowDepthOffsetScale() / DEFAULT_DEPTH_OFFSET_SCALE;
    }

    public static void addWindow(WorldWindow window) {
        windows.add(window);
    }

    public static void removeWindow(WorldWindow window) {
        window.document.remove();
        windows.remove(window);
    }

    public static void clear() {
        for (WorldWindow window : new ArrayList<>(windows)) {
            removeWindow(window);
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            if (windows.isEmpty()) return;

            for (WorldWindow window : windows) {
                window.render(event.getPoseStack(), event.getProjectionMatrix(), event.getPartialTick());
            }
        }
    }

    public Position getRealPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;

        Vec3 rayOrigin = mc.player.getEyePosition(mc.getPartialTick());
        Vec3 rayDir = mc.player.getViewVector(mc.getPartialTick());

        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.translate((float) position.x, (float) position.y, (float) position.z);
        modelMatrix.rotate(rotation);
        float documentScale = worldDocumentScale();
        modelMatrix.scale(resolvedScale * documentScale, -resolvedScale * documentScale, resolvedScale * documentScale);

        Vector4f centerWorld = modelMatrix.transform(new Vector4f(0, 0, 0, 1));
        Vector4f normalWorld = new Vector4f(0, 0, 1, 0);
        modelMatrix.transform(normalWorld);
        Vec3 planeNormal = new Vec3(normalWorld.x, normalWorld.y, normalWorld.z).normalize();
        Vec3 planeCenter = new Vec3(centerWorld.x, centerWorld.y, centerWorld.z);

        double denominator = planeNormal.dot(rayDir);

        if (Math.abs(denominator) < 1e-6) return null;

        Vec3 toCenter = planeCenter.subtract(rayOrigin);
        double t = toCenter.dot(planeNormal) / denominator;

        if (t < 0) return null;

        Vec3 intersection = rayOrigin.add(rayDir.scale(t));
        double windowDistance = rayOrigin.distanceTo(intersection);
        if (windowDistance > maxDistance) return null;
        if (depthTest && isOccluded(mc, rayOrigin, intersection, windowDistance)) return null;

        Matrix4f inverseMatrix = new Matrix4f(modelMatrix).invert();
        Vector4f localHit = new Vector4f((float) intersection.x, (float) intersection.y, (float) intersection.z, 1.0f);
        inverseMatrix.transform(localHit);

        double localX = localHit.x + getWidth() / 2.0;
        double localY = localHit.y + getHeight() / 2.0;

        if (localX >= 0 && localX <= getWidth() && localY >= 0 && localY <= getHeight()) {
            return document.documentToScreenPosition(new Position(localX, localY));
        }

        return null;
    }

    private boolean isOccluded(Minecraft minecraft, Vec3 rayOrigin, Vec3 intersection, double windowDistance) {
        if (minecraft.level == null || minecraft.player == null) return true;

        BlockHitResult blockHit = minecraft.level.clip(new ClipContext(
                rayOrigin,
                intersection,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                minecraft.player
        ));
        if (blockHit.getType() == HitResult.Type.MISS) return false;

        double blockDistance = rayOrigin.distanceTo(blockHit.getLocation());
        return blockDistance + OCCLUSION_DISTANCE_EPSILON < windowDistance;
    }

    private float documentViewportWidth() {
        if (document == null || document.getViewport() == null) return 1.0f;
        return Math.max(1.0f, document.getViewport().layoutWidth());
    }

    private float documentViewportHeight() {
        if (document == null || document.getViewport() == null) return 1.0f;
        return Math.max(1.0f, document.getViewport().layoutHeight());
    }

    private float resolveRenderScale(Vec3 cameraPos, Matrix4f projectionMatrix) {
        if (scaleOverride != null) return scaleOverride;

        long viewportVersion = document == null ? 0L : document.getViewportVersion();
        // Fit once for this world placement. A world object must keep its physical size
        // while the camera moves; only a viewport change or repositioning invalidates it.
        if (resolvedViewportVersion == viewportVersion) return resolvedScale;

        // Use the actual camera-to-window distance. Camera look-vector conventions
        // differ between Minecraft's render and entity cameras; distance is stable
        // for fitting and cannot incorrectly trigger the legacy fallback scale.
        double viewDepth = cameraPos.distanceTo(position);
        float fittedScale = computeViewportScale(
                getWidth() * worldDocumentScale(),
                getHeight() * worldDocumentScale(),
                viewDepth,
                projectionMatrix.m00(),
                projectionMatrix.m11(),
                resolvedScale
        );
        if (viewDepth > 0.0d && Double.isFinite(viewDepth)) {
            resolvedViewportVersion = viewportVersion;
        }
        return fittedScale;
    }

    private float documentRenderScale() {
        if (document == null || document.getViewport() == null) return 1.0f;
        float scale = document.getViewport().renderScale();
        return Float.isFinite(scale) && scale > 0.0f ? scale : 1.0f;
    }

    private float worldDocumentScale() {
        if (scaleOverride != null) return 1.0f;
        Minecraft minecraft = Minecraft.getInstance();
        double guiScale = minecraft.getWindow().getGuiScale();
        if (!(guiScale > 0.0d) || !Double.isFinite(guiScale)) guiScale = 1.0d;
        return (float) Math.max(0.0001d, documentRenderScale() * guiScale);
    }

    static float computeViewportScale(float viewportWidth, float viewportHeight,
                                      double viewDepth, float projectionX, float projectionY,
                                      float fallbackScale) {
        if (!(viewportWidth > 0.0f) || !(viewportHeight > 0.0f)
                || !(viewDepth > 0.0d) || !Double.isFinite(viewDepth)
                || !Float.isFinite(projectionX) || !Float.isFinite(projectionY)
                || Math.abs(projectionX) < 1.0e-6f || Math.abs(projectionY) < 1.0e-6f) {
            return sanitizeScale(fallbackScale);
        }

        double visibleWorldWidth = 2.0d * viewDepth / Math.abs(projectionX);
        double visibleWorldHeight = 2.0d * viewDepth / Math.abs(projectionY);
        double fittedScale = Math.min(
                visibleWorldWidth * DEFAULT_VIEWPORT_FILL / viewportWidth,
                visibleWorldHeight * DEFAULT_VIEWPORT_FILL / viewportHeight
        );
        if (!(fittedScale > 0.0d) || !Double.isFinite(fittedScale)) {
            return sanitizeScale(fallbackScale);
        }
        return (float) fittedScale;
    }

    private static float sanitizeScale(float value) {
        return Float.isFinite(value) && value > 0.0f ? value : FALLBACK_WORLD_SCALE;
    }

    private static float sanitizeDimension(float value) {
        return Float.isFinite(value) && value > 0.0f ? value : 1.0f;
    }

    private static int sanitizeDistance(int value) {
        return Math.max(0, value);
    }
}
