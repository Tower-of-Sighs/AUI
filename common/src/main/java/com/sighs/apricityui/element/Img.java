package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.task.AbstractAsyncHandler;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.ImageDrawer;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.resource.async.image.ImageAsyncHandler;
import com.sighs.apricityui.resource.async.image.ImageHandle;

@ElementRegister(Img.TAG_NAME)
public class Img extends Element {
    public static final String TAG_NAME = "IMG";
    private ResourceState resourceState = ResourceState.IDLE;
    private String observedResolvedSrc = "";

    public Img(Document document) {
        super(document, TAG_NAME);
    }

    public String getCurrentSrc() {
        String src = getAttribute("src");
        if (src == null || src.isBlank() || document == null) return "";
        return Loader.resolve(document.getPath(), src);
    }

    public int getNaturalWidth() {
        ImageHandle handle = resolveCurrentHandle();
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) return 0;
        return handle.texture().getWidth();
    }

    public int getNaturalHeight() {
        ImageHandle handle = resolveCurrentHandle();
        if (handle == null || handle.state() != AbstractAsyncHandler.AsyncState.READY || handle.texture() == null) return 0;
        return handle.texture().getHeight();
    }

    public boolean isComplete() {
        String currentSrc = getCurrentSrc();
        if (currentSrc.isBlank()) return true;
        ImageHandle handle = resolveCurrentHandle();
        if (handle == null) return false;
        AbstractAsyncHandler.AsyncState handleState = handle.state();
        return handleState == AbstractAsyncHandler.AsyncState.READY || handleState == AbstractAsyncHandler.AsyncState.FAILED;
    }

    @Override
    public void tick() {
        super.tick();
        String src = getAttribute("src");
        if (src == null || src.isBlank()) {
            resetResourceObservation();
            return;
        }

        String resolvedSrc = Loader.resolve(document.getPath(), src);
        if (!resolvedSrc.equals(observedResolvedSrc)) {
            observedResolvedSrc = resolvedSrc;
            resourceState = ResourceState.IDLE;
        }

        updateResourceState(resolvedSrc, ImageAsyncHandler.INSTANCE.request(resolvedSrc, this, false));
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        Rect rectRenderer = Rect.of(this);
        switch (phase) {
            case SHADOW -> rectRenderer.drawShadow(poseStack);
            case BODY -> {
                rectRenderer.drawBody(poseStack);
                ImageDrawer.draw(poseStack, this, rectRenderer);
            }
            case BORDER -> rectRenderer.drawBorder(poseStack);
        }
    }

    private void updateResourceState(String resolvedSrc, ImageHandle handle) {
        if (resolvedSrc == null || resolvedSrc.isBlank() || handle == null) return;
        AbstractAsyncHandler.AsyncState handleState = handle.state();
        if (handleState == AbstractAsyncHandler.AsyncState.READY) {
            if (resourceState != ResourceState.LOADED) {
                resourceState = ResourceState.LOADED;
                dispatchResourceEvent("load");
            }
            return;
        }
        if (handleState == AbstractAsyncHandler.AsyncState.FAILED) {
            if (resourceState != ResourceState.FAILED) {
                resourceState = ResourceState.FAILED;
                dispatchResourceEvent("error");
            }
            return;
        }
        resourceState = ResourceState.LOADING;
    }

    private void dispatchResourceEvent(String type) {
        Event event = new Event(this, type, null, false);
        event.bubbles = false;
        Event.tiggerEvent(event);
    }

    public void testUpdateResourceState(String resolvedSrc, ImageHandle handle) {
        updateResourceState(resolvedSrc, handle);
    }

    public void testResetResourceObservation() {
        resetResourceObservation();
    }

    protected ImageHandle resolveCurrentHandle() {
        String currentSrc = getCurrentSrc();
        if (currentSrc.isBlank()) return null;
        return ImageAsyncHandler.INSTANCE.request(currentSrc, this, false);
    }

    private void resetResourceObservation() {
        observedResolvedSrc = "";
        resourceState = ResourceState.IDLE;
    }

    private enum ResourceState {
        IDLE,
        LOADING,
        LOADED,
        FAILED
    }
}
