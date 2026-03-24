package com.sighs.apricityui.resource.async.image;

import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.Image;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ImageHandle {
    private final String path;
    private volatile long generation;
    private volatile AbstractAsyncHandler.AsyncState state = AbstractAsyncHandler.AsyncState.NEW;
    private volatile Image.ITexture texture;
    private volatile Throwable error;
    private volatile long failedAtMs;

    private final ConcurrentHashMap<UUID, RequesterRef> requesters = new ConcurrentHashMap<>();

    public ImageHandle(String path, long generation) {
        this.path = path;
        this.generation = generation;
    }

    public String path() {
        return path;
    }

    public long generation() {
        return generation;
    }

    public AbstractAsyncHandler.AsyncState state() {
        return state;
    }

    public Image.ITexture texture() {
        return texture;
    }

    public Throwable error() {
        return error;
    }

    public long failedAtMs() {
        return failedAtMs;
    }

    public synchronized void reset(long newGeneration) {
        generation = newGeneration;
        state = AbstractAsyncHandler.AsyncState.NEW;
        error = null;
        failedAtMs = 0L;
    }

    public synchronized boolean tryEnterLoading() {
        if (state != AbstractAsyncHandler.AsyncState.NEW) return false;
        state = AbstractAsyncHandler.AsyncState.LOADING;
        return true;
    }

    public synchronized boolean tryEnterApplying() {
        if (state != AbstractAsyncHandler.AsyncState.LOADING) return false;
        state = AbstractAsyncHandler.AsyncState.APPLYING;
        return true;
    }

    public synchronized void markReady(Image.ITexture readyTexture) {
        texture = readyTexture;
        error = null;
        state = AbstractAsyncHandler.AsyncState.READY;
    }

    public synchronized void markFailed(Throwable throwable, long nowMs) {
        error = throwable;
        failedAtMs = nowMs;
        state = AbstractAsyncHandler.AsyncState.FAILED;
    }

    public synchronized void markStale() {
        state = AbstractAsyncHandler.AsyncState.STALE;
    }

    public synchronized void destroyTextureIfPresent() {
        if (texture == null) return;
        texture.destroy();
        texture = null;
    }

    public void addRequester(Element element, boolean needRelayout) {
        if (element == null) return;
        requesters.compute(element.uuid, (uuid, oldValue) -> {
            if (oldValue == null) {
                return new RequesterRef(element, needRelayout);
            }
            oldValue.needRelayout = oldValue.needRelayout || needRelayout;
            return oldValue;
        });
    }

    public List<RequesterRef> drainRequesters() {
        ArrayList<RequesterRef> values = new ArrayList<>(requesters.values());
        requesters.clear();
        return values;
    }

    public static final class RequesterRef {
        private final WeakReference<Element> elementRef;
        private volatile boolean needRelayout;

        private RequesterRef(Element element, boolean needRelayout) {
            this.elementRef = new WeakReference<>(element);
            this.needRelayout = needRelayout;
        }

        public Element getElement() {
            return elementRef.get();
        }

        public boolean needRelayout() {
            return needRelayout;
        }
    }
}
