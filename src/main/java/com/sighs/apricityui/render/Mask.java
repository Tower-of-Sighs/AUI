package com.sighs.apricityui.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.style.Size;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.StencilManager;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Mask {
    private static final ByteBufferBuilder STENCIL_BYTE_BUFFER = new ByteBufferBuilder(262144);
    private static final Map<StencilPipelineKey, RenderType> STENCIL_TYPES = new ConcurrentHashMap<>();

    /**
     * {@link ArrayDeque} does not allow {@code null} elements; we use this sentinel to represent a missing scissor.
     * This is an in-memory-only value (not serialized) and is safe to compare by reference.
     */
    private static final AABB NO_SCISSOR = new AABB(Float.NaN, Float.NaN, Float.NaN, Float.NaN);

    private static final Deque<AABB> clipStack = new ArrayDeque<>();
    private static final Deque<AABB> scissorStack = new ArrayDeque<>();
    private static final Deque<Boolean> maskScissorStack = new ArrayDeque<>();

    private static final Deque<Integer> stencilBitStack = new ArrayDeque<>();
    private static int activeStencilMask = 0;

    private static int clipDepth = 0;
    private static AABB currentScissor = null;
    private static AABB currentClip = new AABB(0, 0, 100000, 100000);

    private Mask() {
    }

    public static void resetDepth() {
        clipDepth = 0;
        clipStack.clear();
        scissorStack.clear();
        maskScissorStack.clear();
        currentScissor = null;
        disableScissor();

        for (Integer bit : stencilBitStack) {
            if (bit != null) {
                StencilManager.releaseBit(bit);
            }
        }
        stencilBitStack.clear();
        activeStencilMask = 0;

        int screenWidth = (int) Size.getWindowSize().width();
        int screenHeight = (int) Size.getWindowSize().height();
        currentClip = new AABB(0, 0, screenWidth, screenHeight);
    }

    public static MaskState suspendForOffscreen() {
        MaskState saved = capture();

        clipDepth = 0;
        clipStack.clear();
        scissorStack.clear();
        maskScissorStack.clear();
        currentScissor = null;
        disableScissor();

        stencilBitStack.clear();
        activeStencilMask = 0;

        int screenWidth = (int) Size.getWindowSize().width();
        int screenHeight = (int) Size.getWindowSize().height();
        currentClip = new AABB(0, 0, screenWidth, screenHeight);

        return saved;
    }

    public static void restore(MaskState state) {
        if (state == null) return;

        clipDepth = state.clipDepth;
        clipStack.clear();
        clipStack.addAll(state.clipStack);
        scissorStack.clear();
        scissorStack.addAll(state.scissorStack);
        maskScissorStack.clear();
        maskScissorStack.addAll(state.maskScissorStack);

        stencilBitStack.clear();
        stencilBitStack.addAll(state.stencilBitStack);
        activeStencilMask = state.activeStencilMask;

        currentScissor = state.currentScissor;
        currentClip = state.currentClip;

        if (currentScissor == null) disableScissor();
        else applyScissor(currentScissor);
    }

    public static MaskState capture() {
        return new MaskState(
                new ArrayDeque<>(clipStack),
                new ArrayDeque<>(scissorStack),
                new ArrayDeque<>(maskScissorStack),
                new ArrayDeque<>(stencilBitStack),
                activeStencilMask,
                clipDepth,
                currentScissor,
                currentClip
        );
    }

    public static AABB getCurrentClip() {
        return currentClip;
    }

    public static boolean isActive() {
        return clipDepth > 0;
    }

    public static int getActiveStencilMask() {
        return activeStencilMask;
    }

    public static void pushMask(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, float width, float height, float[] radii) {
        boolean useScissor = isRectMask(radii);
        maskScissorStack.push(useScissor);

        AABB newMask = new AABB(x, y, width, height);
        clipStack.push(currentClip);
        currentClip = currentClip.intersection(newMask);

        if (useScissor) {
            ImageDrawer.flushBatch();
            Graph.endBatch();
            scissorStack.push(encodeNullableScissor(currentScissor));
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            clipDepth++;
            return;
        }

        ImageDrawer.flushBatch();
        Graph.endBatch();
        if (!beginStencilIfPossible()) {
            // Fallback: no stencil available; use rectangular scissor.
            maskScissorStack.pop();
            maskScissorStack.push(true);
            scissorStack.push(encodeNullableScissor(currentScissor));
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            clipDepth++;
            return;
        }

        int bitIndex = StencilManager.reserveBit();
        if (bitIndex < 0) {
            // No bits available; fallback to scissor.
            maskScissorStack.pop();
            maskScissorStack.push(true);
            scissorStack.push(encodeNullableScissor(currentScissor));
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            clipDepth++;
            return;
        }

        int bitMask = 1 << bitIndex;
        int requiredMask = activeStencilMask;

        RenderType stencilType = getStencilType(requiredMask, bitMask, bitMask);
        drawRoundedToStencil(stencilType, pose.last().pose(), x, y, width, height, radii);

        stencilBitStack.push(bitIndex);
        activeStencilMask |= bitMask;
        clipDepth++;
    }

    public static void popMask(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, float width, float height, float[] radii) {
        boolean usedScissor = !maskScissorStack.isEmpty() && maskScissorStack.pop();

        if (!clipStack.isEmpty()) {
            currentClip = clipStack.pop();
        }

        if (usedScissor) {
            currentScissor = popNullableScissor();
            if (currentScissor == null) disableScissor();
            else applyScissor(currentScissor);
            clipDepth = Math.max(0, clipDepth - 1);
            return;
        }

        if (!beginStencilIfPossible()) {
            clipDepth = Math.max(0, clipDepth - 1);
            return;
        }

        Integer bitIndex = stencilBitStack.poll();
        if (bitIndex == null) {
            clipDepth = Math.max(0, clipDepth - 1);
            activeStencilMask = 0;
            return;
        }

        int bitMask = 1 << bitIndex;

        // Clear this bit only where all currently-active bits are set.
        int requiredMask = activeStencilMask;
        RenderType clearType = getStencilType(requiredMask, bitMask, 0);
        drawRoundedToStencil(clearType, pose.last().pose(), x, y, width, height, radii);

        StencilManager.releaseBit(bitIndex);
        activeStencilMask &= ~bitMask;
        clipDepth = Math.max(0, clipDepth - 1);
    }

    public static void pushClipPath(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, float width, float height, String clipPathValue) {
        clipStack.push(currentClip);
        AABB newMask = new AABB(x, y, width, height);
        currentClip = currentClip.intersection(newMask);

        ImageDrawer.flushBatch();
        Graph.endBatch();
        if (!beginStencilIfPossible()) {
            // Fallback to rectangular scissor if stencil unavailable.
            maskScissorStack.push(true);
            scissorStack.push(encodeNullableScissor(currentScissor));
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            clipDepth++;
            return;
        }

        int bitIndex = StencilManager.reserveBit();
        if (bitIndex < 0) {
            maskScissorStack.push(true);
            scissorStack.push(encodeNullableScissor(currentScissor));
            currentScissor = currentScissor == null ? newMask : currentScissor.intersection(newMask);
            applyScissor(currentScissor);
            clipDepth++;
            return;
        }

        maskScissorStack.push(false);
        int bitMask = 1 << bitIndex;
        int requiredMask = activeStencilMask;

        RenderType stencilType = getStencilType(requiredMask, bitMask, bitMask);
        drawClipToStencil(stencilType, pose.last().pose(), x, y, width, height, clipPathValue);

        stencilBitStack.push(bitIndex);
        activeStencilMask |= bitMask;
        clipDepth++;
    }

    public static void popClipPath(com.mojang.blaze3d.vertex.PoseStack pose, float x, float y, float width, float height, String clipPathValue) {
        boolean usedScissor = !maskScissorStack.isEmpty() && maskScissorStack.pop();

        if (!clipStack.isEmpty()) {
            currentClip = clipStack.pop();
        }

        if (usedScissor) {
            currentScissor = popNullableScissor();
            if (currentScissor == null) disableScissor();
            else applyScissor(currentScissor);
            clipDepth = Math.max(0, clipDepth - 1);
            return;
        }

        if (!beginStencilIfPossible()) {
            clipDepth = Math.max(0, clipDepth - 1);
            return;
        }

        Integer bitIndex = stencilBitStack.poll();
        if (bitIndex == null) {
            clipDepth = Math.max(0, clipDepth - 1);
            activeStencilMask = 0;
            return;
        }

        int bitMask = 1 << bitIndex;
        int requiredMask = activeStencilMask;
        RenderType clearType = getStencilType(requiredMask, bitMask, 0);
        drawClipToStencil(clearType, pose.last().pose(), x, y, width, height, clipPathValue);

        StencilManager.releaseBit(bitIndex);
        activeStencilMask &= ~bitMask;
        clipDepth = Math.max(0, clipDepth - 1);
    }

    private static boolean beginStencilIfPossible() {
        GpuTexture depthTex = getCurrentStencilTexture();
        if (depthTex == null) return false;

        if (activeStencilMask == 0) {
            try {
                RenderSystem.getDevice().createCommandEncoder().clearStencilTexture(depthTex, 0);
            } catch (Throwable ignored) {
                return false;
            }
        }

        return true;
    }

    private static GpuTexture getCurrentStencilTexture() {
        if (RenderSystem.outputDepthTextureOverride != null) {
            return RenderSystem.outputDepthTextureOverride.texture();
        }
        var main = Minecraft.getInstance().getMainRenderTarget();
        return main.getDepthTexture();
    }

    private static RenderType getStencilType(int requiredMask, int writeMask, int writeValue) {
        boolean isClear = writeValue == 0;
        CompareOp compare = requiredMask == 0 ? CompareOp.ALWAYS_PASS : CompareOp.EQUAL;
        int readMask = requiredMask == 0 ? StencilTest.DEFAULT_READ_MASK : requiredMask;

        // Stencil test uses a single reference value for both comparison and REPLACE. We use readMask to ensure
        // comparison only considers the required bits, while REPLACE updates only the written bit(s).
        int referenceValue;
        StencilOperation passOp;
        if (isClear) {
            referenceValue = requiredMask;
            passOp = StencilOperation.ZERO;
        } else {
            referenceValue = requiredMask == 0 ? writeMask : (requiredMask | writeMask);
            passOp = StencilOperation.REPLACE;
        }

        StencilPerFaceTest face = new StencilPerFaceTest(StencilOperation.KEEP, StencilOperation.KEEP, passOp, compare);
        StencilTest stencil = new StencilTest(face, readMask, writeMask, referenceValue);

        StencilPipelineKey key = new StencilPipelineKey(compare, readMask, writeMask, referenceValue, writeValue);
        return STENCIL_TYPES.computeIfAbsent(key, k -> {
            ColorTargetState colorTargetState = new ColorTargetState(java.util.Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.WRITE_NONE);
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("apricityui",
                            "pipeline/stencil/" + compare.name().toLowerCase(Locale.ROOT) + "/" + readMask + "/" + writeMask + "/" + referenceValue + "/" + writeValue))
                    .withVertexShader(Identifier.withDefaultNamespace("core/gui"))
                    .withFragmentShader(Identifier.withDefaultNamespace("core/gui"))
                    .withCull(false)
                    .withColorTargetState(colorTargetState)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withStencilTest(stencil)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                    .build();

            // Use a RenderType wrapper so we can use RenderType.draw(mesh) convenience.
            RenderSetup setup = RenderSetup.builder(pipeline).bufferSize(RenderType.TRANSIENT_BUFFER_SIZE).createRenderSetup();
            return RenderType.create("apricityui_stencil", setup);
        });
    }

    private static void drawRoundedToStencil(RenderType stencilType, Matrix4f matrix, float x, float y, float width, float height, float[] radii) {
        BufferBuilder buf = new BufferBuilder(STENCIL_BYTE_BUFFER, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        Graph.addUnifiedRoundedRectVertices(buf, matrix, x, y, width, height, radii, 0xFFFFFFFF);
        drawStencilMesh(stencilType, buf);
    }

    private static void drawClipToStencil(RenderType stencilType, Matrix4f matrix, float x, float y, float width, float height, String clipPath) {
        BufferBuilder buf = new BufferBuilder(STENCIL_BYTE_BUFFER, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        ClipPath.drawToStencil(buf, matrix, x, y, width, height, clipPath);
        drawStencilMesh(stencilType, buf);
    }

    private static void drawStencilMesh(RenderType stencilType, BufferBuilder buf) {
        MeshData mesh = buf.build();
        if (mesh == null) return;
        stencilType.draw(mesh);
    }

    public static void enableScissor(double x, double y, double width, double height) {
        Window window = Minecraft.getInstance().getWindow();
        double scale = window.getGuiScale();

        // When rendering into an offscreen target (e.g. Picture-in-Picture), the current render target height may be
        // smaller than Window#getHeight() because it's derived from guiScaledHeight * guiScale and guiScaledHeight is
        // floored. Using the window height here can shift the scissor by 1px on odd resolutions.
        int targetPixelHeight = window.getHeight();
        if (RenderSystem.outputColorTextureOverride != null) {
            targetPixelHeight = RenderSystem.outputColorTextureOverride.getHeight(0);
        }

        double left = x * scale;
        double top = y * scale;
        double right = (x + width) * scale;
        double bottom = (y + height) * scale;

        int scissorX = (int) Math.floor(left);
        int scissorY = (int) Math.floor(targetPixelHeight - bottom);
        int scissorRight = (int) Math.ceil(right);
        int scissorTop = (int) Math.ceil(targetPixelHeight - top);
        int scissorW = Math.max(0, scissorRight - scissorX);
        int scissorH = Math.max(0, scissorTop - scissorY);

        RenderSystem.enableScissorForRenderTypeDraws(scissorX, scissorY, scissorW, scissorH);
    }

    public static void disableScissor() {
        RenderSystem.disableScissorForRenderTypeDraws();
    }

    /**
     * Applies the current rectangular scissor (if any) to a {@link RenderPass}.
     *
     * <p>Important: {@link RenderSystem#enableScissorForRenderTypeDraws(int, int, int, int)} only affects
     * {@link net.minecraft.client.renderer.rendertype.RenderType} draws. Filter/backdrop rendering uses direct
     * {@link RenderPass} rendering and must be configured explicitly, otherwise the pass may inherit a stale scissor
     * from previous GUI rendering and draw nothing.
     */
    public static void applyScissorToRenderPass(RenderPass pass, int targetPixelHeight, double guiScale) {
        if (pass == null) return;
        AABB rect = currentScissor;
        if (rect == null || !rect.isValid()) {
            pass.disableScissor();
            return;
        }

        double left = rect.x() * guiScale;
        double top = rect.y() * guiScale;
        double right = (rect.x() + rect.width()) * guiScale;
        double bottom = (rect.y() + rect.height()) * guiScale;

        int scissorX = (int) Math.floor(left);
        int scissorY = (int) Math.floor(targetPixelHeight - bottom);
        int scissorRight = (int) Math.ceil(right);
        int scissorTop = (int) Math.ceil(targetPixelHeight - top);
        int scissorW = Math.max(0, scissorRight - scissorX);
        int scissorH = Math.max(0, scissorTop - scissorY);

        pass.enableScissor(scissorX, scissorY, scissorW, scissorH);
    }

    private static void applyScissor(AABB rect) {
        if (rect == null || !rect.isValid()) {
            disableScissor();
            return;
        }
        enableScissor(rect.x(), rect.y(), rect.width(), rect.height());
    }

    private static boolean isRectMask(float[] radii) {
        if (radii == null) return true;
        for (float r : radii) {
            if (r > 0.001f) return false;
        }
        return true;
    }

    private static AABB encodeNullableScissor(AABB scissor) {
        return scissor == null ? NO_SCISSOR : scissor;
    }

    private static AABB decodeNullableScissor(AABB encoded) {
        return encoded == NO_SCISSOR ? null : encoded;
    }

    private static AABB popNullableScissor() {
        if (scissorStack.isEmpty()) return null;
        return decodeNullableScissor(scissorStack.pop());
    }

    private record StencilPipelineKey(CompareOp compare, int readMask, int writeMask, int reference, int writeValue) {
    }

    public record MaskState(
            Deque<AABB> clipStack,
            Deque<AABB> scissorStack,
            Deque<Boolean> maskScissorStack,
            Deque<Integer> stencilBitStack,
            int activeStencilMask,
            int clipDepth,
            AABB currentScissor,
            AABB currentClip
    ) {
    }
}
