package com.sighs.apricityui.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Mask;
import com.sighs.apricityui.render.WorldWindowRenderContext;
import com.sighs.apricityui.render.WorldPaintDepth;
import com.sighs.apricityui.layout.Position;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.spi.AuiServices;

public class WorldWindow {
    public static final List<WorldWindow> windows = new ArrayList<>();
    private static final float DEFAULT_NEAR_DEPTH_STEP = 0.00035f;
    private static final float DEFAULT_FAR_DEPTH_STEP = 0.0030f;
    private static final float DEFAULT_DEPTH_NEAR_DISTANCE = 2.0f;
    private static final float DEFAULT_DEPTH_OFFSET_SCALE = 0.0005f;
    private static final float POLYGON_OFFSET = -1.0f;
    private static final float DEFAULT_VIEWPORT_FILL = 0.8f;
    private static final float FALLBACK_WORLD_SCALE = 0.02f;
    private static final double OCCLUSION_DISTANCE_EPSILON = 1.0e-4d;
    private static final float DEFAULT_FOLLOW_FACTOR = 0.3f;
    private static final float ITEM_MODEL_DEPTH_FRACTION = 0.25f;
    private static final float ITEM_DECORATION_DEPTH_FRACTION = 0.5f;
    private static final float[] VIEWPORT_CLIP_RADIUS = new float[]{0, 0, 0, 0};

    public Document document;
    private Vec3 position;
    private final Quaternionf rotation;
    /** Legacy explicit world units represented by one logical CSS pixel. */
    private Float scaleOverride;
    /** Scale used by rendering and interaction hit testing. */
    private float resolvedScale = FALLBACK_WORLD_SCALE;
    private long resolvedViewportVersion = Long.MIN_VALUE;
    private boolean depthTest = true;
    /** Whether this window follows the camera's view plane from its base position. */
    private boolean followEnabled;
    /** Whether this window rotates to face the active camera each frame. */
    private boolean facingEnabled;
    /** Interpolation amount for follow, where 0 keeps the base position and 1 fully follows. */
    private float followFactor = DEFAULT_FOLLOW_FACTOR;
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
    private Vec3 interactionPosition;

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

    /** Enables or disables camera-plane following while preserving the base position. */
    public void setFollow(boolean follow) {
        if (this.followEnabled != follow) {
            resolvedViewportVersion = Long.MIN_VALUE;
        }
        this.followEnabled = follow;
    }

    public boolean isFollowEnabled() {
        return followEnabled;
    }

    /** Alias for callers that prefer an explicit enabled suffix. */
    public void setFollowEnabled(boolean follow) {
        setFollow(follow);
    }

    public boolean isFollow() {
        return isFollowEnabled();
    }

    /** Enables or disables camera-facing rotation independently from following. */
    public void setFacing(boolean facing) {
        this.facingEnabled = facing;
    }

    public boolean isFacingEnabled() {
        return facingEnabled;
    }

    /** Alias for callers that prefer an explicit enabled suffix. */
    public void setFacingEnabled(boolean facing) {
        setFacing(facing);
    }

    public boolean isFacing() {
        return isFacingEnabled();
    }

    /** Sets the follow interpolation factor in the inclusive range {@code [0, 1]}. */
    public void setFollowFactor(float followFactor) {
        this.followFactor = sanitizeFollowFactor(followFactor);
    }

    public float getFollowFactor() {
        return followFactor;
    }

    private Vec3 resolveRenderPosition(Vec3 cameraPosition, Vector3f lookVector) {
        if (position == null || !followEnabled || cameraPosition == null) return position;
        Vec3 look = new Vec3(lookVector.x, lookVector.y, lookVector.z);
        return resolveFollowPosition(position, cameraPosition, look, followFactor);
    }

    private Quaternionf resolveRenderRotation(Vec3 cameraPosition, Vec3 renderPosition) {
        if (!facingEnabled || cameraPosition == null || renderPosition == null) {
            return new Quaternionf(rotation);
        }
        return faceCamera(cameraPosition, renderPosition);
    }

    private Quaternionf faceCamera(Vec3 cameraPosition, Vec3 windowPosition) {
        if (cameraPosition == null || windowPosition == null) return new Quaternionf(rotation);
        Vec3 toCamera = cameraPosition.subtract(windowPosition);
        double horizontal = Math.sqrt(toCamera.x * toCamera.x + toCamera.z * toCamera.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(toCamera.z, toCamera.x)) - 90.0);
        float pitch = (float) (Math.toDegrees(Math.atan2(toCamera.y, horizontal)));
        // Keep the configured orientation untouched; facing is a frame-local rotation.
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yaw))
                .rotateX((float) Math.toRadians(-pitch));
    }

    /** Resolves a base point toward the camera view ray using the requested factor. */
    static Vec3 resolveFollowPosition(Vec3 basePosition, Vec3 cameraPosition,
                                      Vec3 lookVector, float followFactor) {
        if (basePosition == null || cameraPosition == null || lookVector == null) return basePosition;
        double lookLength = Math.sqrt(
                lookVector.x * lookVector.x
                        + lookVector.y * lookVector.y
                        + lookVector.z * lookVector.z
        );
        if (!(lookLength > 1.0e-8) || !Double.isFinite(lookLength)) return basePosition;

        Vec3 normalizedLook = lookVector.scale(1.0 / lookLength);
        Vec3 toBase = basePosition.subtract(cameraPosition);
        double depth = toBase.dot(normalizedLook);
        if (!(depth > 0.0) || !Double.isFinite(depth)) return basePosition;

        Vec3 targetPosition = cameraPosition.add(normalizedLook.scale(depth));
        float factor = sanitizeFollowFactor(followFactor);
        return basePosition.add(targetPosition.subtract(basePosition).scale(factor));
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

    /** Returns whether this instance uses an explicit world scale instead of camera fitting. */
    public boolean hasScaleOverride() {
        return scaleOverride != null;
    }

    /** Returns the explicit scale, or the last camera-fitted scale when no override is set. */
    public float getScale() {
        return resolvedScale;
    }

    /** Clears an explicit world scale and lets the next frame fit the viewport to the camera. */
    public void clearScaleOverride() {
        scaleOverride = null;
        resolvedViewportVersion = Long.MIN_VALUE;
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
                AuiServices.config().worldWindowMaxDisplayDistance(),
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

    /** Returns whether this instance overrides the global LOD policy or thresholds. */
    public boolean hasDisplayPrecisionOverride() {
        return displayPrecisionOverride;
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
        Vec3 cameraPosition = AuiServices.client().getCameraPosition();
        Vec3 renderPosition = resolveRenderPosition(cameraPosition, AuiServices.client().getCameraLookVector());
        if (!isWithinDisplayDistance(cameraPosition, renderPosition)) return WorldWindowDisplayPrecision.MINIMAL;
        return resolveDisplayPrecision(cameraPosition, renderPosition);
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
                : AuiServices.config().worldWindowFullDetailDistance();
    }

    public int getReducedDetailDistance() {
        return reducedDetailDistanceOverride != null
                ? reducedDetailDistanceOverride
                : AuiServices.config().worldWindowReducedDetailDistance();
    }

    public void setDynamicDepthStep(float nearDepthStep, float farDepthStep, float nearDistance, float farDistance) {
        this.nearDepthStep = Math.max(0.0f, nearDepthStep);
        this.farDepthStep = Math.max(this.nearDepthStep, farDepthStep);
        this.depthNearDistance = Math.max(0.0f, nearDistance);
        this.depthFarDistance = Math.max(this.depthNearDistance + 0.001f, farDistance);
    }

    public float getNearDepthStep() {
        return nearDepthStep;
    }

    public float getFarDepthStep() {
        return farDepthStep;
    }

    public float getDepthNearDistance() {
        return depthNearDistance;
    }

    public float getDepthFarDistance() {
        return depthFarDistance;
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
        Vec3 cameraPos = AuiServices.client().getCameraPosition();
        Vec3 renderPosition = resolveRenderPosition(cameraPos, AuiServices.client().getCameraLookVector());
        if (!isWithinDisplayDistance(cameraPos, renderPosition)) return;
        WorldWindowDisplayPrecision precision = resolveDisplayPrecision(cameraPos, renderPosition);
        float documentScale = worldDocumentScale();
        float worldScale = resolveRenderScale(cameraPos, projectionMatrix, renderPosition);
        float renderScale = worldScale * documentScale;
        float viewportWidth = getWidth();
        float viewportHeight = getHeight();
        resolvedScale = worldScale;
        Quaternionf renderRotation = resolveRenderRotation(cameraPos, renderPosition);

        poseStack.pushPose();
        poseStack.translate(
                renderPosition.x - cameraPos.x,
                renderPosition.y - cameraPos.y,
                renderPosition.z - cameraPos.z
        );

        poseStack.mulPose(new Quaternionf(renderRotation));

        poseStack.scale(renderScale, -renderScale, renderScale);

        // Avoid entering the expensive document/stencil path when the complete panel is
        // outside the camera frustum. The test is conservative for panels crossing a plane.
        if (!isQuadVisible(poseStack.last().pose(), projectionMatrix, viewportWidth, viewportHeight)) {
            poseStack.popPose();
            return;
        }

        poseStack.translate(-viewportWidth / 2.0f, -viewportHeight / 2.0f, 0);

        poseStack.last().pose().set(poseStack.last().pose());
        poseStack.last().normal().set(poseStack.last().normal());

        boolean previousDepthTest = AuiServices.render().isDepthTestEnabled();
        boolean previousDepthMask = AuiServices.render().isDepthMaskEnabled();
        if (depthTest) {
            AuiServices.render().enableDepthTest();
            AuiServices.render().setDepthFunc(GL11.GL_LEQUAL);
            AuiServices.render().setDepthMask(true);
        } else {
            AuiServices.render().disableDepthTest();
            AuiServices.render().setDepthMask(false);
        }
        AuiServices.render().enableBlend();
        AuiServices.render().setBlendFuncSeparate(
                GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA
        );
        // Depth bias to avoid z-fighting with world geometry.
        AuiServices.render().enablePolygonOffset();
        AuiServices.render().polygonOffset(POLYGON_OFFSET, POLYGON_OFFSET);

        float documentDepthBudget = computeDepthStep(cameraPos, renderPosition);
        float safeScale = Math.max(1.0e-4f, renderScale);
        int paintNodeCount = document == null ? 1 : Math.max(1, document.getPaintList().size());
        float localStep = documentDepthBudget / paintNodeCount / safeScale;
        if (localStep > 0.2f) localStep = 0.2f;
        Base.pushDepthStep(localStep);
        Base.pushDepthMode(true);
        Base.pushDepthTest(depthTest);
        Base.pushGuiItemZ(
                localStep * ITEM_MODEL_DEPTH_FRACTION,
                localStep * ITEM_DECORATION_DEPTH_FRACTION
        );
        float documentZOffset = documentDepthBudget / safeScale;
        Base.pushDocumentZOffset(documentZOffset);
        WorldPaintDepth.pushFlatTransforms(true);
        Mask.resetDepth(viewportWidth, viewportHeight);
        Mask.pushForceStencil();
        // The document clip used to be only a logical culling rectangle. A
        // root stencil is required here because the world transform can make
        // a partially visible child draw outside the WorldWindow quad.
        Mask.pushMask(poseStack, 0, 0, viewportWidth, viewportHeight, VIEWPORT_CLIP_RADIUS, true);
        try {
            captureInteractionTransform(
                    projectionMatrix,
                    poseStack.last().pose(),
                    renderScale,
                    documentZOffset,
                    renderPosition,
                    renderRotation
            );
            try (WorldWindowRenderContext.Scope ignored = WorldWindowRenderContext.push(precision)) {
                Base.drawDocument(poseStack, document);
                ResourcePreviewDialog.drawInWorld(poseStack, document);
            }
        } finally {
            Mask.popMask(poseStack, 0, 0, viewportWidth, viewportHeight, VIEWPORT_CLIP_RADIUS);
            Mask.popForceStencil();
            WorldPaintDepth.popFlatTransforms();
            Base.popGuiItemZ();
            Base.popDepthTest();
            Base.popDepthMode();
            Base.popDepthStep();
            Base.popDocumentZOffset();
        }

        AuiServices.render().flushSharedBuffers();
        AuiServices.render().polygonOffset(0.0f, 0.0f);
        AuiServices.render().disablePolygonOffset();
        if (previousDepthTest) AuiServices.render().enableDepthTest();
        else AuiServices.render().disableDepthTest();
        AuiServices.render().setDepthMask(previousDepthMask);

        poseStack.popPose();
    }

    /** Returns the total world-space depth budget for this document. */
    private float computeDepthStep(Vec3 cameraPos, Vec3 renderPosition) {
        double distance = cameraPos.distanceTo(renderPosition);
        double t = Mth.inverseLerp(distance, depthNearDistance, depthFarDistance);
        float depth = (float) Mth.clampedLerp(nearDepthStep, farDepthStep, t);
        return depth * AuiServices.config().worldWindowDepthOffsetScale() / DEFAULT_DEPTH_OFFSET_SCALE;
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

    /**
     * Converts a GUI mouse position to this window's document coordinates and
     * applies the same distance/occlusion checks as normal world interaction.
     */
    public Position getDocumentPositionAtScreen(Position screenPosition) {
        if (screenPosition == null || !Double.isFinite(screenPosition.x) || !Double.isFinite(screenPosition.y)
                || interactionClipMatrix == null || interactionWorldMatrix == null) return null;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) return null;
        Vec3 rayOrigin = AuiServices.client().getCameraPosition();
        Vec3 renderPosition = interactionPosition == null ? position : interactionPosition;
        if (!isWithinDisplayDistance(rayOrigin, renderPosition)) return null;

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
        // A grabbed cursor reports virtual look coordinates; world picking uses the crosshair.
        return getRealPos(com.sighs.apricityui.spi.AuiServices.client().getMousePositionForWorldInteraction());
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
                                              float renderScale, float documentZOffset,
                                              Vec3 renderPosition, Quaternionf renderRotation) {
        if (projectionMatrix == null || modelViewMatrix == null
                || renderPosition == null || renderRotation == null) return;
        Matrix4f documentModelView = new Matrix4f(modelViewMatrix).translate(0.0f, 0.0f, documentZOffset);
        interactionClipMatrix = new Matrix4f(projectionMatrix).mul(documentModelView);
        interactionPosition = renderPosition;
        interactionWorldMatrix = new Matrix4f()
                .translate((float) renderPosition.x, (float) renderPosition.y, (float) renderPosition.z)
                .rotate(renderRotation)
                .scale(renderScale, -renderScale, renderScale)
                .translate(-getWidth() / 2.0f, -getHeight() / 2.0f, 0.0f)
                .translate(0.0f, 0.0f, documentZOffset);
    }

    private void clearInteractionTransform() {
        interactionClipMatrix = null;
        interactionWorldMatrix = null;
        interactionPosition = null;
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
        return isWithinDisplayDistance(cameraPosition, position);
    }

    private boolean isWithinDisplayDistance(Vec3 cameraPosition, Vec3 renderPosition) {
        if (cameraPosition == null || renderPosition == null) return false;
        return WorldWindowVisibility.isWithinDisplayDistance(
                cameraPosition.distanceToSqr(renderPosition), getMaxDisplayDistance());
    }

    private WorldWindowDisplayPrecision resolveDisplayPrecision(Vec3 cameraPosition) {
        return resolveDisplayPrecision(cameraPosition, position);
    }

    private WorldWindowDisplayPrecision resolveDisplayPrecision(Vec3 cameraPosition, Vec3 renderPosition) {
        if (cameraPosition == null || renderPosition == null) return WorldWindowDisplayPrecision.MINIMAL;
        return WorldWindowVisibility.resolveDisplayPrecision(
                cameraPosition.distanceToSqr(renderPosition),
                displayPrecision,
                displayPrecisionOverride || AuiServices.config().worldWindowLodEnabled(),
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

    private float resolveRenderScale(Vec3 cameraPos, Matrix4f projectionMatrix, Vec3 renderPosition) {
        if (scaleOverride != null) return scaleOverride;

        long viewportVersion = document == null ? 0L : document.getViewportVersion();
        // Fit once for this world placement. A world object must keep its physical size
        // while the camera moves; only a viewport change or repositioning invalidates it.
        // Follow changes the frame-local render position, not the window's physical
        // size. Keep the fitted scale cached until the document viewport changes or
        // the world placement is explicitly changed.
        if (resolvedViewportVersion == viewportVersion) return resolvedScale;

        // Use the actual camera-to-window distance. Camera look-vector conventions
        // differ between Minecraft's render and entity cameras; distance is stable
        // for fitting and cannot incorrectly trigger the legacy fallback scale.
        double viewDepth = cameraPos.distanceTo(renderPosition);
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

    private static float sanitizeFollowFactor(float value) {
        return Float.isFinite(value) ? Mth.clamp(value, 0.0f, 1.0f) : DEFAULT_FOLLOW_FACTOR;
    }

    private static float sanitizeDimension(float value) {
        return Float.isFinite(value) && value > 0.0f ? value : 1.0f;
    }

    private static int sanitizeDistance(int value) {
        return Math.max(0, value);
    }
}
