package com.sighs.apricityui.resource.async.image;

import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.Image;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ImageHandle {
    private final String path;
    private volatile AbstractAsyncHandler.AsyncState state = AbstractAsyncHandler.AsyncState.NEW;
    private volatile long generation;
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

    public synchronized boolean transition(AbstractAsyncHandler.AsyncState expected, AbstractAsyncHandler.AsyncState next) {
        if (state != expected) return false;
        state = next;
        return true;
    }

    public synchronized void resetForRetry(long newGeneration) {
        this.generation = newGeneration;
        this.state = AbstractAsyncHandler.AsyncState.NEW;
        this.error = null;
        this.failedAtMs = 0L;
    }

    public synchronized boolean markReady(Image.ITexture loadedTexture) {
        if (state != AbstractAsyncHandler.AsyncState.APPLYING) return false;
        this.texture = loadedTexture;
        this.error = null;
        this.state = AbstractAsyncHandler.AsyncState.READY;
        return true;
    }

    public synchronized void markFailed(Throwable throwable, long nowMs) {
        this.error = throwable;
        this.failedAtMs = nowMs;
        this.state = AbstractAsyncHandler.AsyncState.FAILED;
    }

    public synchronized void markStale() {
        this.state = AbstractAsyncHandler.AsyncState.STALE;
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
            oldValue.upgradeRelayout(needRelayout);
            return oldValue;
        });
    }

    public List<RequesterRef> drainRequesters() {
        List<RequesterRef> refs = new ArrayList<>(requesters.values());
        requesters.clear();
        return refs;
    }

    public static class RequesterRef {
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

        private void upgradeRelayout(boolean value) {
            this.needRelayout = this.needRelayout || value;
        }
    }
}
