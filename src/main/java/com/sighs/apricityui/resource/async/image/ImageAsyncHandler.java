package com.sighs.apricityui.resource.async.image;

import com.sighs.apricityui.init.*;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.resource.Image;
import com.sighs.apricityui.resource.async.network.NetworkAsyncHandler;
import com.sighs.apricityui.style.Background;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageAsyncHandler extends AbstractAsyncHandler<ImageAsyncHandler.ApplyTask> {
    public static final ImageAsyncHandler INSTANCE = new ImageAsyncHandler();

    private static final long FAILED_RETRY_MS = 5_000L;
    private static final Map<String, ImageHandle> HANDLES = new ConcurrentHashMap<>();

    private ImageAsyncHandler() {
        super("image", 256, 1, 1_500_000L, "ApricityUI-ImageWorker");
    }

    public ImageHandle request(String path) {
        return request(path, null, false);
    }

    public static void prefetchImages(Document document) {
        if (document == null) return;
        Set<String> paths = new HashSet<>();
        for (Element element : document.getElements()) {
            String src = element.getAttribute("src");
            if (!src.isEmpty() && "IMG".equals(element.tagName)) {
                addIfValid(paths, Loader.resolve(document.getPath(), src));
            }

            Style style = element.getRawComputedStyle();
            if (style == null) continue;
            for (String backgroundPath : Background.resolveImagePaths(document.getPath(), style.backgroundImage)) {
                addIfValid(paths, backgroundPath);
            }
            String borderSource = firstNonUnset(style.borderImageSource, style.borderImage);
            addIfValid(paths, resolveCssUrl(document.getPath(), borderSource));
        }
        INSTANCE.prefetch(paths);
    }

    private static ImageHandle prepareHandle(ImageHandle existing, String path, long generation, long now) {
        ImageHandle handle = existing;
        if (handle == null || handle.generation() != generation || handle.state() == AsyncState.STALE) {
            if (handle != null) handle.destroyTextureIfPresent();
            return new ImageHandle(path, generation);
        }
        if (handle.state() == AsyncState.FAILED && now - handle.failedAtMs() >= FAILED_RETRY_MS) {
            handle.reset(generation);
        }
        return handle;
    }

    private static void addIfValid(Set<String> target, String path) {
        if (path == null || path.isBlank() || "unset".equals(path)) return;
        target.add(path);
    }

    private static String firstNonUnset(String first, String second) {
        if (first != null && !first.isBlank() && !"unset".equals(first)) return first;
        if (second != null && !second.isBlank() && !"unset".equals(second)) return second;
        return null;
    }

    private static String resolveCssUrl(String contextPath, String cssValue) {
        if (cssValue == null || cssValue.isBlank() || "unset".equals(cssValue)) return null;
        int start = cssValue.indexOf("url(");
        if (start < 0) return null;
        int end = cssValue.indexOf(')', start + 4);
        if (end < 0) return null;
        String raw = cssValue.substring(start + 4, end).replace("\"", "").replace("'", "").trim();
        if (raw.isBlank()) return null;
        return Loader.resolve(contextPath, raw);
    }

    public ImageHandle request(String path, Element requester, boolean needRelayout) {
        if (path == null || path.isBlank() || "unset".equals(path)) return null;

        long generation = currentGeneration();
        long now = System.currentTimeMillis();
        ImageHandle handle = HANDLES.compute(path, (key, existing) -> prepareHandle(existing, key, generation, now));
        if (requester != null && handle.state() != AsyncState.READY) {
            handle.addRequester(requester, needRelayout);
        }
        submitDecodeIfNeeded(handle);
        return handle;
    }

    public void prefetch(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) return;
        HashSet<String> uniquePaths = new HashSet<>(paths);
        for (String path : uniquePaths) {
            request(path);
        }
    }

    private void submitDecodeIfNeeded(ImageHandle handle) {
        if (handle == null || !handle.tryEnterLoading()) return;
        submitWorker(() -> decodeOnWorker(handle), ex -> handle.markFailed(ex, System.currentTimeMillis()));
    }

    private void decodeOnWorker(ImageHandle handle) {
        DecodedImage decodedImage = null;
        try {
            byte[] bytes = readResourceBytes(handle.path());
            decodedImage = Image.decode(handle.path(), bytes);
            if (decodedImage == null) {
                handle.markFailed(new IllegalStateException("图片解码失败: " + handle.path()), System.currentTimeMillis());
                return;
            }
        } catch (Exception exception) {
            if (decodedImage != null) decodedImage.close();
            handle.markFailed(exception, System.currentTimeMillis());
            return;
        }

        if (handle.generation() != currentGeneration()) {
            decodedImage.close();
            handle.markStale();
            return;
        }
        if (!handle.tryEnterApplying()) {
            decodedImage.close();
            return;
        }
        enqueueApplyTask(new ApplyTask(handle, decodedImage, handle.generation()));
    }

    private byte[] readResourceBytes(String path) throws Exception {
        if (Loader.isRemotePath(path)) {
            return NetworkAsyncHandler.INSTANCE.fetchBytes(path);
        }
        try (InputStream stream = Loader.getResourceStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("未找到图片资源: " + path);
            }
            return stream.readAllBytes();
        }
    }

    @Override
    protected void applyOnMainThread(ApplyTask task, long currentGeneration) {
        if (task.generation != currentGeneration || task.handle.generation() != task.generation) {
            task.decodedImage.close();
            task.handle.markStale();
            return;
        }

        Image.ITexture texture;
        try {
            texture = Image.uploadDecoded(task.handle.path(), task.decodedImage);
        } catch (Exception exception) {
            task.handle.markFailed(exception, System.currentTimeMillis());
            return;
        }
        if (texture == null) {
            task.handle.markFailed(new IllegalStateException("上传纹理失败: " + task.handle.path()), System.currentTimeMillis());
            return;
        }

        if (task.handle.state() != AsyncState.APPLYING) {
            texture.destroy();
            return;
        }
        task.handle.markReady(texture);

        for (ImageHandle.RequesterRef requesterRef : task.handle.drainRequesters()) {
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
    protected void onDiscardApplyTask(ApplyTask task) {
        if (task == null || task.decodedImage == null) return;
        task.decodedImage.close();
    }

    public record ApplyTask(ImageHandle handle, DecodedImage decodedImage, long generation) {
    }
}
