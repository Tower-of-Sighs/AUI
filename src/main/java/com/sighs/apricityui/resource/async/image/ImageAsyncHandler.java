package com.sighs.apricityui.resource.async.image;

import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.Image;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImageAsyncHandler extends AbstractAsyncHandler<ImageAsyncHandler.ImageApplyTask> {
    public static final ImageAsyncHandler INSTANCE = new ImageAsyncHandler();

    private static final long FAILED_TTL_MS = 5_000L;
    private static final Map<String, ImageHandle> HANDLES = new ConcurrentHashMap<>();

    private ImageAsyncHandler() {
        super("image", 256, 1, 1_500_000L, "ApricityUI-ImageWorker");
    }

    public ImageHandle request(String path) {
        return request(path, null, false);
    }

    public ImageHandle request(String path, Element requester, boolean needRelayout) {
        if (path == null || path.isBlank() || "unset".equals(path)) return null;

        long now = System.currentTimeMillis();
        long generation = currentGeneration();

        ImageHandle handle = HANDLES.compute(path, (key, current) -> {
            ImageHandle target = current;
            if (target == null || target.generation() != generation) {
                if (target != null) target.destroyTextureIfPresent();
                target = new ImageHandle(key, generation);
            }
            if (requester != null && target.state() != AsyncState.READY) {
                target.addRequester(requester, needRelayout);
            }
            if (target.state() == AsyncState.FAILED && now - target.failedAtMs() >= FAILED_TTL_MS) {
                target.resetForRetry(generation);
            }
            return target;
        });

        submitIfNeeded(handle);
        return handle;
    }

    public void prefetch(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) return;
        HashSet<String> unique = new HashSet<>(paths);
        for (String path : unique) {
            request(path, null, false);
        }
    }

    private void submitIfNeeded(ImageHandle handle) {
        if (handle == null) return;
        if (!handle.transition(AsyncState.NEW, AsyncState.LOADING)) return;

        submitWorker(() -> decodeOnWorker(handle), ex -> handle.markFailed(ex, System.currentTimeMillis()));
    }

    private void decodeOnWorker(ImageHandle handle) {
        DecodedImage decodedImage = null;
        try {
            if (Loader.isRemotePath(handle.path())) {
                byte[] bytes = NetworkAsyncHandler.INSTANCE.fetchBytes(handle.path());
                decodedImage = Image.decode(handle.path(), bytes);
            } else {
                try (InputStream is = Loader.getResourceStream(handle.path())) {
                    if (is == null) {
                        handle.markFailed(new IllegalStateException("未找到图片资源: " + handle.path()), System.currentTimeMillis());
                        return;
                    }
                    byte[] bytes = is.readAllBytes();
                    decodedImage = Image.decode(handle.path(), bytes);
                }
            }
            if (decodedImage == null) {
                handle.markFailed(new IllegalStateException("图片解码失败: " + handle.path()), System.currentTimeMillis());
                return;
            }
        } catch (Exception ex) {
            if (decodedImage != null) decodedImage.close();
            handle.markFailed(ex, System.currentTimeMillis());
            return;
        }

        if (handle.generation() != currentGeneration()) {
            decodedImage.close();
            handle.markStale();
            return;
        }
        if (!handle.transition(AsyncState.LOADING, AsyncState.APPLYING)) {
            decodedImage.close();
            return;
        }
        enqueueApplyTask(new ImageApplyTask(handle, decodedImage, handle.generation()));
    }

    @Override
    protected void applyOnMainThread(ImageApplyTask task, long currentGeneration) {
        if (task.generation() != currentGeneration || task.handle().generation() != task.generation()) {
            task.decodedImage().close();
            task.handle().markStale();
            return;
        }

        Image.ITexture texture;
        try {
            texture = Image.uploadDecoded(task.handle().path(), task.decodedImage());
        } catch (Exception ex) {
            task.handle().markFailed(ex, System.currentTimeMillis());
            return;
        }
        if (texture == null) {
            task.handle().markFailed(new IllegalStateException("上传纹理失败: " + task.handle().path()), System.currentTimeMillis());
            return;
        }
        if (!task.handle().markReady(texture)) {
            texture.destroy();
            return;
        }

        for (ImageHandle.RequesterRef requesterRef : task.handle().drainRequesters()) {
            Element element = requesterRef.getElement();
            if (element == null || element.document == null) continue;
            int dirtyMask = requesterRef.needRelayout() ? Drawer.RELAYOUT : Drawer.REPAINT;
            element.document.markDirty(element, dirtyMask);
        }
    }

    @Override
    protected void onBeforeClear(long nextGeneration) {
        for (ImageHandle handle : HANDLES.values()) {
            handle.destroyTextureIfPresent();
            handle.markStale();
        }
        HANDLES.clear();
    }

    @Override
    protected void onDiscardApplyTask(ImageApplyTask task) {
        if (task == null) return;
        if (task.decodedImage() != null) {
            task.decodedImage().close();
        }
    }

    public record ImageApplyTask(ImageHandle handle, DecodedImage decodedImage, long generation) {}
}
