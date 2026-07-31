package com.sighs.apricityui.instance;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.WorldWindowRenderContext;
import com.sighs.apricityui.render.WorldPaintDepth;
import com.sighs.apricityui.layout.Position;
import net.minecraft.client.Camera;
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
    /** Optional per-instance camera-distance limit; null means use the client config default. */
    private Integer maxDisplayDistanceOverride;
    /** Display mode; AUTO follows the global LOD setting unless explicitly configured. */
    private WorldWindowDisplayPrecision displayPrecision = WorldWindowDisplayPrecision.AUTO;
    /** Whether this instance explicitly opted into a display precision policy. */
    private boolean displayPrecisionOverride;
    /** Optional per-instance LOD thresholds; null means use the client config values. */
    private Integer fullDetailDistanceOverride;
    private Integer reducedDetailDistanceOverride;
    private float nearDepthStep = DEFAULT_NEAR_DEPTH_STEP;
    private float farDepthStep = DEFAULT_FAR_DEPTH_STEP;
    private float depthNearDistance = DEFAULT_DEPTH_NEAR_DISTANCE;
    private float depthFarDistance;

    /**
     * The last transform used to paint this window. World rendering happens before
     * input events, so keeping the exact frame transform makes picking agree with
     * the pixels on screen even for rotated and perspective-projected windows.
     */
    private Matrix4f interactionClipMatrix;
    private Matrix4f interactionWorldMatrix;

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

    /**
     * Returns the effective camera-distance limit. An instance override takes
     * precedence; otherwise the current client config default is used.
     */
    public int getMaxDisplayDistance() {
        return WorldWindowVisibility.resolveDisplayDistance(
                ApricityUIConfig.CLIENT.worldWindowMaxDisplayDistance(),
                maxDisplayDistanceOverride);
    }

    /** Sets the per-instance maximum camera distance at which this window is rendered and interactive. */
    public void setMaxDisplayDistance(int maxDisplayDistance) {
        this.maxDisplayDistanceOverride = sanitizeDistance(maxDisplayDistance);
    }

    /** Returns whether this window overrides the configured global display-distance default. */
    public boolean hasMaxDisplayDistanceOverride() {
        return maxDisplayDistanceOverride != null;
    }

    /** Clears the per-instance limit so this window follows the client config default again. */
    public void clearMaxDisplayDistanceOverride() {
        maxDisplayDistanceOverride = null;
    }

    public WorldWindowDisplayPrecision getDisplayPrecision() {
        return displayPrecision;
    }

    /**
     * Sets the configured display precision. {@code AUTO} follows the global LOD
     * setting, while an explicit level always applies to this instance.
     */
    public void setDisplayPrecision(WorldWindowDisplayPrecision displayPrecision) {
        WorldWindowDisplayPrecision mode = displayPrecision == null
                ? WorldWindowDisplayPrecision.AUTO : displayPrecision;
        this.displayPrecision = mode;
        this.displayPrecisionOverride = mode != WorldWindowDisplayPrecision.AUTO;
        if (mode == WorldWindowDisplayPrecision.AUTO) {
            this.fullDetailDistanceOverride = null;
            this.reducedDetailDistanceOverride = null;
        }
    }

    /** Rhino/KubeJS-friendly overload accepting {@code auto}, {@code full}, {@code reduced} or {@code minimal}. */
    public void setDisplayPrecision(String displayPrecision) {
        setDisplayPrecision(WorldWindowDisplayPrecision.parse(displayPrecision));
    }

    /** Returns the currently effective level for the main render camera. */
    public WorldWindowDisplayPrecision getEffectiveDisplayPrecision() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) return WorldWindowDisplayPrecision.MINIMAL;
        Vec3 cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
        if (!isWithinDisplayDistance(cameraPosition)) return WorldWindowDisplayPrecision.MINIMAL;
        return resolveDisplayPrecision(cameraPosition);
    }

    /**
     * Enables distance-based LOD for this instance. Up to
     * {@code fullDetailDistance} the window is FULL, then REDUCED until
     * {@code reducedDetailDistance}, and MINIMAL afterwards. Both values are
     * inclusive and measured in blocks.
     */
    public void setDisplayPrecisionDistances(int fullDetailDistance, int reducedDetailDistance) {
        int full = sanitizeDistance(fullDetailDistance);
        int reduced = sanitizeDistance(reducedDetailDistance);
        if (reduced < full) reduced = full;
        this.fullDetailDistanceOverride = full;
        this.reducedDetailDistanceOverride = reduced;
        this.displayPrecisionOverride = true;
        this.displayPrecision = WorldWindowDisplayPrecision.AUTO;
    }

    public int getFullDetailDistance() {
        return fullDetailDistanceOverride != null
                ? fullDetailDistanceOverride
                : ApricityUIConfig.CLIENT.worldWindowFullDetailDistance();
    }

    public int getReducedDetailDistance() {
        return reducedDetailDistanceOverride != null
                ? reducedDetailDistanceOverride
                : ApricityUIConfig.CLIENT.worldWindowReducedDetailDistance();
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
        clearInteractionTransform();
        Minecraft mc = Minecraft.getInstance();
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        if (!isWithinDisplayDistance(cameraPos)) return;
        WorldWindowDisplayPrecision precision = resolveDisplayPrecision(cameraPos);
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

        // Avoid entering the expensive document/stencil path when the complete panel is
        // outside the camera frustum. The test is conservative for panels crossing a plane.
        if (!isQuadVisible(poseStack.last().pose(), projectionMatrix, getWidth(), getHeight())) {
            poseStack.popPose();
            return;
        }

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
        float documentZOffset = documentDepthBudget / safeScale;
        Base.pushDocumentZOffset(documentZOffset);
        WorldPaintDepth.pushFlatTransforms(true);
        Mask.resetDepth(getWidth(), getHeight());
        Mask.pushForceStencil();
        try {
            captureInteractionTransform(projectionMatrix, poseStack.last().pose(), renderScale, documentZOffset);
            try (WorldWindowRenderContext.Scope ignored = WorldWindowRenderContext.push(precision)) {
                Base.drawDocument(poseStack, document);
            }
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

    /**
     * Converts a GUI mouse position to this window's document coordinates and
     * applies the same distance/occlusion checks as normal world interaction.
     */
    public Position getDocumentPositionAtScreen(Position screenPosition) {
        if (screenPosition == null || !Double.isFinite(screenPosition.x) || !Double.isFinite(screenPosition.y)
                || interactionClipMatrix == null || interactionWorldMatrix == null) return null;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) return null;
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera == null) return null;
        Vec3 rayOrigin = camera.getPosition();
        if (!isWithinDisplayDistance(rayOrigin)) return null;

        Position localHit = unprojectToDocument(screenPosition);
        if (localHit == null) return null;
        double width = getWidth();
        double height = getHeight();
        if (localHit.x < 0.0 || localHit.x > width || localHit.y < 0.0 || localHit.y > height) return null;

        Vector4f worldHit = interactionWorldMatrix.transform(
                new Vector4f((float) localHit.x, (float) localHit.y, 0.0f, 1.0f));
        if (!Float.isFinite(worldHit.x) || !Float.isFinite(worldHit.y) || !Float.isFinite(worldHit.z)) return null;
        Vec3 intersection = new Vec3(worldHit.x, worldHit.y, worldHit.z);
        double windowDistance = rayOrigin.distanceTo(intersection);
        if (!Double.isFinite(windowDistance) || windowDistance > maxDistance) return null;
        if (depthTest && isOccluded(minecraft, rayOrigin, intersection, windowDistance)) return null;
        return localHit;
    }

    /** Returns the GUI-space projection of a document-local point, or null if it is not visible. */
    public Position projectDocumentPosition(Position documentPosition) {
        if (documentPosition == null || !Double.isFinite(documentPosition.x) || !Double.isFinite(documentPosition.y)
                || interactionClipMatrix == null) return null;
        Vector4f clip = interactionClipMatrix.transform(
                new Vector4f((float) documentPosition.x, (float) documentPosition.y, 0.0f, 1.0f));
        if (!Float.isFinite(clip.x) || !Float.isFinite(clip.y)
                || !Float.isFinite(clip.w) || clip.w <= 1.0e-6f) return null;
        double ndcX = clip.x / clip.w;
        double ndcY = clip.y / clip.w;
        if (!Double.isFinite(ndcX) || !Double.isFinite(ndcY)) return null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return null;
        double guiWidth = Math.max(1.0, minecraft.getWindow().getGuiScaledWidth());
        double guiHeight = Math.max(1.0, minecraft.getWindow().getGuiScaledHeight());
        return new Position((ndcX + 1.0) * 0.5 * guiWidth,
                (1.0 - ndcY) * 0.5 * guiHeight);
    }

    /** Projects a document-local rectangle into a conservative GUI-space bounding box. */
    public ScreenRect projectDocumentRect(double x, double y, double width, double height) {
        if (!Double.isFinite(x) || !Double.isFinite(y)
                || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0.0 || height <= 0.0) return null;
        Position[] corners = {
                projectDocumentPosition(new Position(x, y)),
                projectDocumentPosition(new Position(x + width, y)),
                projectDocumentPosition(new Position(x + width, y + height)),
                projectDocumentPosition(new Position(x, y + height))
        };
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (Position corner : corners) {
            if (corner == null) return null;
            left = Math.min(left, corner.x);
            top = Math.min(top, corner.y);
            right = Math.max(right, corner.x);
            bottom = Math.max(bottom, corner.y);
        }
        return new ScreenRect(left, top, Math.max(0.0, right - left), Math.max(0.0, bottom - top));
    }

    /** Returns the window associated with a world document, if it is currently registered. */
    public static WorldWindow findByDocument(Document document) {
        if (document == null) return null;
        for (WorldWindow window : windows) {
            if (window != null && window.document == document) return window;
        }
        return null;
    }

    /** Returns the current mouse position mapped to document coordinates for world events. */
    public Position getRealPos() {
        return getRealPos(Client.getMousePositionDirectly());
    }

    /** Returns a screen position mapped to this window's event coordinate space. */
    public Position getRealPos(Position screenPosition) {
        Position documentPosition = getDocumentPositionAtScreen(screenPosition);
        return documentPosition == null || document == null
                ? null : document.documentToScreenPosition(documentPosition);
    }

    private Position unprojectToDocument(Position screenPosition) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return null;
        double guiWidth = Math.max(1.0, minecraft.getWindow().getGuiScaledWidth());
        double guiHeight = Math.max(1.0, minecraft.getWindow().getGuiScaledHeight());
        float ndcX = (float) (screenPosition.x / guiWidth * 2.0 - 1.0);
        float ndcY = (float) (1.0 - screenPosition.y / guiHeight * 2.0);

        Matrix4f inverse;
        try {
            inverse = new Matrix4f(interactionClipMatrix).invert();
        } catch (RuntimeException invalidMatrix) {
            return null;
        }
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, -1.0f, 1.0f));
        Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, 1.0f, 1.0f));
        if (!perspectiveDivide(near) || !perspectiveDivide(far)) return null;

        double dz = far.z - near.z;
        if (!Double.isFinite(dz) || Math.abs(dz) < 1.0e-7) return null;
        double amount = -near.z / dz;
        if (!Double.isFinite(amount) || amount < 0.0 || amount > 1.0) return null;
        return new Position(
                near.x + (far.x - near.x) * amount,
                near.y + (far.y - near.y) * amount
        );
    }

    private static boolean perspectiveDivide(Vector4f point) {
        if (point == null || !Float.isFinite(point.w) || Math.abs(point.w) < 1.0e-6f) return false;
        point.x /= point.w;
        point.y /= point.w;
        point.z /= point.w;
        point.w = 1.0f;
        return Float.isFinite(point.x) && Float.isFinite(point.y) && Float.isFinite(point.z);
    }

    private void captureInteractionTransform(Matrix4f projectionMatrix, Matrix4f modelViewMatrix,
                                              float renderScale, float documentZOffset) {
        if (projectionMatrix == null || modelViewMatrix == null || position == null) return;
        Matrix4f documentModelView = new Matrix4f(modelViewMatrix).translate(0.0f, 0.0f, documentZOffset);
        interactionClipMatrix = new Matrix4f(projectionMatrix).mul(documentModelView);
        interactionWorldMatrix = new Matrix4f()
                .translate((float) position.x, (float) position.y, (float) position.z)
                .rotate(rotation)
                .scale(renderScale, -renderScale, renderScale)
                .translate(-getWidth() / 2.0f, -getHeight() / 2.0f, 0.0f)
                .translate(0.0f, 0.0f, documentZOffset);
    }

    private void clearInteractionTransform() {
        interactionClipMatrix = null;
        interactionWorldMatrix = null;
    }

    public record ScreenRect(double x, double y, double width, double height) {
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

    private boolean isWithinDisplayDistance(Vec3 cameraPosition) {
        if (cameraPosition == null || position == null) return false;
        return WorldWindowVisibility.isWithinDisplayDistance(
                cameraPosition.distanceToSqr(position), getMaxDisplayDistance());
    }

    private WorldWindowDisplayPrecision resolveDisplayPrecision(Vec3 cameraPosition) {
        if (cameraPosition == null || position == null) return WorldWindowDisplayPrecision.MINIMAL;
        return WorldWindowVisibility.resolveDisplayPrecision(
                cameraPosition.distanceToSqr(position),
                displayPrecision,
                displayPrecisionOverride || ApricityUIConfig.CLIENT.worldWindowLodEnabled(),
                getFullDetailDistance(),
                getReducedDetailDistance()
        );
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

    static boolean isQuadVisible(Matrix4f modelViewMatrix, Matrix4f projectionMatrix,
                                  float width, float height) {
        if (modelViewMatrix == null || projectionMatrix == null
                || !Float.isFinite(width) || !Float.isFinite(height)
                || width <= 0.0f || height <= 0.0f) {
            return true;
        }

        Matrix4f clip = new Matrix4f(projectionMatrix).mul(modelViewMatrix);
        float halfWidth = width * 0.5f;
        float halfHeight = height * 0.5f;

        float centerX = clip.m30();
        float centerY = clip.m31();
        float centerZ = clip.m32();
        float centerW = clip.m33();
        float xAxisX = clip.m00() * halfWidth;
        float xAxisY = clip.m01() * halfWidth;
        float xAxisZ = clip.m02() * halfWidth;
        float xAxisW = clip.m03() * halfWidth;
        float yAxisX = clip.m10() * halfHeight;
        float yAxisY = clip.m11() * halfHeight;
        float yAxisZ = clip.m12() * halfHeight;
        float yAxisW = clip.m13() * halfHeight;

        // A panel entirely behind the camera cannot contribute any fragments.
        if (centerW + Math.abs(xAxisW) + Math.abs(yAxisW) <= 0.0f) return false;

        // Each expression below checks whether all four corners are outside one
        // homogeneous clip plane. If no plane excludes the whole quad, keep it.
        if (outsideLeft(centerX, centerW, xAxisX, xAxisW, yAxisX, yAxisW)) return false;
        if (outsideRight(centerX, centerW, xAxisX, xAxisW, yAxisX, yAxisW)) return false;
        if (outsideLeft(centerY, centerW, xAxisY, xAxisW, yAxisY, yAxisW)) return false;
        if (outsideRight(centerY, centerW, xAxisY, xAxisW, yAxisY, yAxisW)) return false;
        if (outsideLeft(centerZ, centerW, xAxisZ, xAxisW, yAxisZ, yAxisW)) return false;
        if (outsideRight(centerZ, centerW, xAxisZ, xAxisW, yAxisZ, yAxisW)) return false;
        return true;
    }

    private static boolean outsideLeft(float valueCenter, float wCenter,
                                       float xAxis, float wAxis,
                                       float yAxis, float yWAxis) {
        float center = valueCenter + wCenter;
        float radius = Math.abs(xAxis + wAxis) + Math.abs(yAxis + yWAxis);
        return center + radius < 0.0f;
    }

    private static boolean outsideRight(float valueCenter, float wCenter,
                                        float xAxis, float wAxis,
                                        float yAxis, float yWAxis) {
        float center = valueCenter - wCenter;
        float radius = Math.abs(xAxis - wAxis) + Math.abs(yAxis - yWAxis);
        return center - radius > 0.0f;
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
