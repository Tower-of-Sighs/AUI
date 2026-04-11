package com.sighs.apricityui.resource.async.image;

import com.sighs.apricityui.init.AbstractAsyncHandler;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.Image;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
        this.generation = newGeneration;
        this.state = AbstractAsyncHandler.AsyncState.NEW;
        this.error = null;
        this.failedAtMs = 0L;
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
        this.texture = readyTexture;
        this.error = null;
        this.state = AbstractAsyncHandler.AsyncState.READY;
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

        requesters.compute(element.uuid, (_, oldValue) -> {
            if (oldValue == null) {
                return new RequesterRef(element, needRelayout);
            }
            if (needRelayout) {
                oldValue.needRelayout.set(true);
            }
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
        private final AtomicBoolean needRelayout;

        private RequesterRef(Element element, boolean needRelayout) {
            this.elementRef = new WeakReference<>(element);
            this.needRelayout = new AtomicBoolean(needRelayout);
        }

        public Element getElement() {
            return elementRef.get();
        }

        public boolean needRelayout() {
            return needRelayout.get();
        }

        public void setNeedRelayout(boolean value) {
            this.needRelayout.set(value);
        }
    }
}