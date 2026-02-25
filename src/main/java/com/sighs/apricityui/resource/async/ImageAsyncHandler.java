package com.sighs.apricityui.resource.async;

import com.sighs.apricityui.async.Asynchronous;
import com.sighs.apricityui.async.Asynchronous.AsyncTaskRole;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.registry.annotation.AsyncTask;
import com.sighs.apricityui.registry.annotation.AsyncTaskClass;
import com.sighs.apricityui.resource.Image;
import com.sighs.apricityui.resource.UrlFetch;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@AsyncTaskClass(id = "image", workerThreadName = "ApricityUI-ImageWorker")
public class ImageAsyncHandler {
    public static final ImageAsyncHandler INSTANCE = new ImageAsyncHandler();

    private static final long FAILED_TTL_MS = 5_000L;
    private static final Map<String, ImageEntry> ENTRIES = new ConcurrentHashMap<>();

    private ImageAsyncHandler() {
        Asynchronous.register(this);
    }

    public void clearAndBumpGeneration() {
        Asynchronous.clearAndBumpGeneration(this);
    }

    public Image.ITexture request(String path) {
        return request(path, null, false);
    }

    public Image.ITexture request(String path, Element requester, boolean needRelayout) {
        if (path == null || path.isBlank() || "unset".equals(path)) return null;

        long now = System.currentTimeMillis();
        long currentGeneration = Asynchronous.currentGeneration(this);

        ImageEntry entry = ENTRIES.compute(path, (key, current) -> {
            ImageEntry target = current;
            if (target == null || target.generation() != currentGeneration) {
                if (target != null) target.destroyTextureIfPresent();
                target = new ImageEntry(key, currentGeneration);
            }
            if (requester != null && target.texture() == null) {
                target.addRequester(requester, needRelayout);
            }
            if (target.failedAtMs() > 0L && now - target.failedAtMs() >= FAILED_TTL_MS) {
                target.resetFailure(currentGeneration);
            }
            return target;
        });

        submitIfNeeded(entry, now);
        return entry.texture();
    }

    public static void prefetchImages(Document document) {
        Set<String> paths = new HashSet<>();
        for (Element element : document.getElements()) {
            String src = element.getAttribute("src");
            if (!src.isEmpty() && "IMG".equals(element.tagName)) {
                String resolved = Loader.resolve(document.getPath(), src);
                if (isImagePathValid(resolved)) paths.add(resolved);
            }

            Style style = element.getRawComputedStyle();
            if (style == null) continue;

            String backgroundPath = resolveCssUrl(document.getPath(), style.backgroundImage);
            if (isImagePathValid(backgroundPath)) paths.add(backgroundPath);

            String borderImageSource = resolveFirstNonUnset(style.borderImageSource, style.borderImage);
            String borderImagePath = resolveCssUrl(document.getPath(), borderImageSource);
            if (isImagePathValid(borderImagePath)) paths.add(borderImagePath);
        }
        ImageAsyncHandler.INSTANCE.prefetch(paths);
    }

    public void prefetch(Collection<String> paths) {
        if (paths == null || paths.isEmpty()) return;
        HashSet<String> unique = new HashSet<>(paths);
        for (String path : unique) request(path, null, false);
    }

    private void submitIfNeeded(ImageEntry entry, long nowMs) {
        if (entry == null) return;
        if (!entry.tryStartLoading(nowMs, FAILED_TTL_MS)) return;
        Asynchronous.submitWorker(this, "decode", entry);
    }

    @AsyncTask(role = AsyncTaskRole.WORKER, value = "decode")
    private ImageApplyTask decodeOnWorker(ImageEntry entry) {
        Image.DecodedImage decodedImage = null;
        try {
            if (Loader.isRemotePath(entry.path())) {
                byte[] bytes = UrlFetch.INSTANCE.fetchBytes(entry.path());
                decodedImage = Image.decode(entry.path(), bytes);
            } else {
                try (InputStream is = Loader.getResourceStream(entry.path())) {
                    if (is == null) {
                        entry.markFailed(new IllegalStateException("未找到图片资源: " + entry.path()), System.currentTimeMillis());
                        return null;
                    }
                    byte[] bytes = is.readAllBytes();
                    decodedImage = Image.decode(entry.path(), bytes);
                }
            }
            if (decodedImage == null) {
                entry.markFailed(new IllegalStateException("图片解码失败: " + entry.path()), System.currentTimeMillis());
                return null;
            }
        } catch (Exception ex) {
            if (decodedImage != null) decodedImage.close();
            entry.markFailed(ex, System.currentTimeMillis());
            return null;
        }

        if (entry.generation() != Asynchronous.currentGeneration(this)) {
            decodedImage.close();
            entry.markStale();
            return null;
        }
        if (!entry.markApplying()) {
            decodedImage.close();
            return null;
        }
        return new ImageApplyTask(entry, decodedImage, entry.generation());
    }

    @AsyncTask(role = AsyncTaskRole.APPLY)
    private void applyOnMainThread(ImageApplyTask task, long currentGeneration) {
        if (task.generation() != currentGeneration || task.entry().generation() != task.generation()) {
            task.decodedImage().close();
            task.entry().markStale();
            return;
        }

        Image.ITexture texture;
        try {
            texture = Image.uploadDecoded(task.entry().path(), task.decodedImage());
        } catch (Exception ex) {
            task.entry().markFailed(ex, System.currentTimeMillis());
            return;
        }
        if (texture == null) {
            task.entry().markFailed(new IllegalStateException("上传纹理失败: " + task.entry().path()), System.currentTimeMillis());
            return;
        }
        if (!task.entry().markReady(texture)) {
            texture.destroy();
            return;
        }

        for (RequesterRef requesterRef : task.entry().drainRequesters()) {
            Element element = requesterRef.getElement();
            if (element == null || element.document == null) continue;
            int dirtyMask = requesterRef.needRelayout() ? Drawer.RELAYOUT : Drawer.REPAINT;
            element.document.markDirty(element, dirtyMask);
        }
    }

    @AsyncTask(role = AsyncTaskRole.DISCARD)
    private void onDiscardApplyTask(ImageApplyTask task) {
        if (task == null || task.decodedImage() == null) return;
        task.decodedImage().close();
    }

    @AsyncTask(role = AsyncTaskRole.ON_CLEAR)
    private void onBeforeClear(long nextGeneration) {
        for (ImageEntry entry : ENTRIES.values()) {
            entry.destroyTextureIfPresent();
            entry.markStale();
        }
        ENTRIES.clear();
    }

    @AsyncTask(role = AsyncTaskRole.ON_ERROR)
    private void onAsyncError(AsyncTaskRole role, Throwable error, Object context) {
        long now = System.currentTimeMillis();
        if (context instanceof ImageEntry entry) {
            entry.markFailed(error, now);
            return;
        }
        if (context instanceof ImageApplyTask applyTask) {
            applyTask.entry().markFailed(error, now);
            if (applyTask.decodedImage() != null) applyTask.decodedImage().close();
        }
    }

    private static boolean isImagePathValid(String path) {
        return path != null && !path.isBlank() && !"unset".equals(path);
    }

    private static String resolveFirstNonUnset(String primary, String fallback) {
        if (primary != null && !primary.isBlank() && !"unset".equals(primary)) return primary;
        if (fallback != null && !fallback.isBlank() && !"unset".equals(fallback)) return fallback;
        return null;
    }

    private static String resolveCssUrl(String contextPath, String cssValue) {
        if (cssValue == null || cssValue.isBlank() || "unset".equals(cssValue)) return null;
        int start = cssValue.indexOf("url(");
        if (start < 0) return null;
        int end = cssValue.indexOf(')', start + 4);
        if (end < 0) return null;
        String raw = cssValue.substring(start + 4, end).replace("\"", "").replace("'", "").trim();
        if (raw.isEmpty()) return null;
        return Loader.resolve(contextPath, raw);
    }

    private static final class ImageEntry {
        private final String path;
        private volatile long generation;
        private volatile Image.ITexture texture;
        private volatile Throwable error;
        private volatile long failedAtMs;
        private volatile boolean loading;
        private volatile boolean applying;
        private volatile boolean stale;
        private final ConcurrentHashMap<UUID, RequesterRef> requesters = new ConcurrentHashMap<>();

        private ImageEntry(String path, long generation) {
            this.path = path;
            this.generation = generation;
        }

        private String path() { return path; }
        private long generation() { return generation; }
        private synchronized Image.ITexture texture() { return texture; }
        private synchronized long failedAtMs() { return failedAtMs; }

        private synchronized void addRequester(Element element, boolean needRelayout) {
            if (element == null) return;
            requesters.compute(element.uuid, (uuid, oldValue) -> {
                if (oldValue == null) return new RequesterRef(element, needRelayout);
                oldValue.upgradeRelayout(needRelayout);
                return oldValue;
            });
        }

        private synchronized boolean tryStartLoading(long nowMs, long failedTtlMs) {
            if (stale) return false;
            if (texture != null) return false;
            if (loading || applying) return false;
            if (failedAtMs > 0L && nowMs - failedAtMs < failedTtlMs) return false;
            loading = true;
            applying = false;
            return true;
        }

        private synchronized boolean markApplying() {
            if (stale || !loading) return false;
            loading = false;
            applying = true;
            return true;
        }

        private synchronized boolean markReady(Image.ITexture loadedTexture) {
            if (stale || !applying) return false;
            texture = loadedTexture;
            error = null;
            failedAtMs = 0L;
            loading = false;
            applying = false;
            return true;
        }

        private synchronized void markFailed(Throwable throwable, long nowMs) {
            error = throwable;
            failedAtMs = nowMs;
            loading = false;
            applying = false;
        }

        private synchronized void resetFailure(long newGeneration) {
            generation = newGeneration;
            error = null;
            failedAtMs = 0L;
            loading = false;
            applying = false;
            stale = false;
        }

        private synchronized void markStale() {
            stale = true;
            loading = false;
            applying = false;
        }

        private synchronized void destroyTextureIfPresent() {
            if (texture == null) return;
            texture.destroy();
            texture = null;
        }

        private synchronized List<RequesterRef> drainRequesters() {
            List<RequesterRef> refs = new ArrayList<>(requesters.values());
            requesters.clear();
            return refs;
        }
    }

    private static final class RequesterRef {
        private final WeakReference<Element> elementRef;
        private volatile boolean needRelayout;

        private RequesterRef(Element element, boolean needRelayout) {
            this.elementRef = new WeakReference<>(element);
            this.needRelayout = needRelayout;
        }

        public Element getElement() { return elementRef.get(); }
        public boolean needRelayout() { return needRelayout; }
        private void upgradeRelayout(boolean value) { this.needRelayout = this.needRelayout || value; }
    }

    private record ImageApplyTask(ImageEntry entry, Image.DecodedImage decodedImage, long generation) {}
}
