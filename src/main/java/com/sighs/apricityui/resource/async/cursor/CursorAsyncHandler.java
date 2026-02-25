package com.sighs.apricityui.resource.async.cursor;

import com.mojang.blaze3d.platform.NativeImage;
import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * cursor: url(...) 的异步加载。
 * <p>
 * Worker 线程负责 IO+解码，主线程负责创建 GLFW Cursor。
 */
public class CursorAsyncHandler extends AbstractAsyncHandler<CursorAsyncHandler.CursorApplyTask> {
    public static final CursorAsyncHandler INSTANCE = new CursorAsyncHandler();

    private static final long FAILED_TTL_MS = 5_000L;
    private static final Map<String, CursorHandle> HANDLES = new ConcurrentHashMap<>();

    private CursorAsyncHandler() {
        super("cursor", 128, 1, 1_500_000L, "ApricityUI-CursorWorker");
    }

    public CursorHandle request(String path, int hotspotX, int hotspotY) {
        if (path == null || path.isBlank() || "unset".equals(path)) return null;

        long now = System.currentTimeMillis();
        long generation = currentGeneration();
        String key = buildKey(path, hotspotX, hotspotY);

        CursorHandle handle = HANDLES.compute(key, (k, current) -> {
            CursorHandle target = current;
            if (target == null || target.generation() != generation) {
                if (target != null) target.destroyCursorIfPresent();
                target = new CursorHandle(k, path, hotspotX, hotspotY, generation);
            }
            if (target.state() == AsyncState.FAILED && now - target.failedAtMs() >= FAILED_TTL_MS) {
                target.resetForRetry(generation);
            }
            return target;
        });

        submitIfNeeded(handle);
        return handle;
    }

    private static String buildKey(String path, int hotspotX, int hotspotY) {
        return path + "#" + hotspotX + "," + hotspotY;
    }

    private void submitIfNeeded(CursorHandle handle) {
        if (handle == null) return;
        if (!handle.transition(AsyncState.NEW, AsyncState.LOADING)) return;

        submitWorker(() -> decodeOnWorker(handle), ex -> handle.markFailed(ex, System.currentTimeMillis()));
    }

    private void decodeOnWorker(CursorHandle handle) {
        NativeImage image = null;
        try {
            if (Loader.isRemotePath(handle.path())) {
                byte[] bytes = NetworkAsyncHandler.INSTANCE.fetchBytes(handle.path());
                image = decodePng(bytes);
            } else {
                try (InputStream is = Loader.getResourceStream(handle.path())) {
                    if (is == null) {
                        handle.markFailed(new IllegalStateException("未找到光标资源: " + handle.path()), System.currentTimeMillis());
                        return;
                    }
                    byte[] bytes = is.readAllBytes();
                    image = decodePng(bytes);
                }
            }

            if (image == null) {
                handle.markFailed(new IllegalStateException("光标图片解码失败: " + handle.path()), System.currentTimeMillis());
                return;
            }
        } catch (Exception ex) {
            if (image != null) image.close();
            handle.markFailed(ex, System.currentTimeMillis());
            return;
        }

        if (handle.generation() != currentGeneration()) {
            image.close();
            handle.markStale();
            return;
        }
        if (!handle.transition(AsyncState.LOADING, AsyncState.APPLYING)) {
            image.close();
            return;
        }

        enqueueApplyTask(new CursorApplyTask(handle, image, handle.generation()));
    }

    private static NativeImage decodePng(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes)) {
            return NativeImage.read(bis);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    protected void applyOnMainThread(CursorApplyTask task, long currentGeneration) {
        if (task.generation() != currentGeneration || task.handle().generation() != task.generation()) {
            task.image().close();
            task.handle().markStale();
            return;
        }

        CursorHandle handle = task.handle();
        NativeImage image = task.image();

        long glfwCursor = 0L;
        ByteBuffer pixels = null;
        GLFWImage glfwImage = null;
        try {
            int w = image.getWidth();
            int h = image.getHeight();

            // 限制热点范围
            int hx = Math.max(0, Math.min(handle.hotspotX(), w - 1));
            int hy = Math.max(0, Math.min(handle.hotspotY(), h - 1));

            pixels = MemoryUtil.memAlloc(w * h * 4);
            // NativeImage.getPixelRGBA 返回 ABGR（参照 Image.convertToNative 的 setPixelRGBA 用法）
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int a = (abgr >> 24) & 0xFF;
                    int b = (abgr >> 16) & 0xFF;
                    int g = (abgr >> 8) & 0xFF;
                    int r = (abgr) & 0xFF;
                    pixels.put((byte) r);
                    pixels.put((byte) g);
                    pixels.put((byte) b);
                    pixels.put((byte) a);
                }
            }
            pixels.flip();

            glfwImage = GLFWImage.malloc();
            glfwImage.width(w);
            glfwImage.height(h);
            glfwImage.pixels(pixels);

            glfwCursor = GLFW.glfwCreateCursor(glfwImage, hx, hy);
            if (glfwCursor == 0L) {
                handle.markFailed(new IllegalStateException("glfwCreateCursor 失败: " + handle.path()), System.currentTimeMillis());
                return;
            }

            if (!handle.markReady(glfwCursor)) {
                // 状态已变化，销毁新创建的 cursor
                GLFW.glfwDestroyCursor(glfwCursor);
            }
        } catch (Exception ex) {
            handle.markFailed(ex, System.currentTimeMillis());
            if (glfwCursor != 0L) {
                try {
                    GLFW.glfwDestroyCursor(glfwCursor);
                } catch (Throwable ignored) {
                }
            }
        } finally {
            image.close();
            if (glfwImage != null) glfwImage.free();
            if (pixels != null) MemoryUtil.memFree(pixels);
        }
    }

    @Override
    protected void onBeforeClear(long nextGeneration) {
        for (CursorHandle handle : HANDLES.values()) {
            handle.destroyCursorIfPresent();
            handle.markStale();
        }
        HANDLES.clear();
    }

    @Override
    protected void onDiscardApplyTask(CursorApplyTask task) {
        if (task == null) return;
        if (task.image() != null) {
            task.image().close();
        }
    }

    public record CursorApplyTask(CursorHandle handle, NativeImage image, long generation) {}
}
