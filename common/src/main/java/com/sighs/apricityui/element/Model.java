package com.sighs.apricityui.element;

import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.model.ModelSpec;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.BodyRenderNodeProvider;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.spi.AuiModelRenderService;
import com.sighs.apricityui.spi.AuiServices;

import java.util.List;

@ElementRegister(Model.TAG_NAME)
public final class Model extends Element implements BodyRenderNodeProvider {
    public static final String TAG_NAME = "MODEL";
    private static final float DEGREES_PER_PIXEL = 0.5F;
    private ModelSpec observed;
    private AuiModelRenderService.Preview preview;
    private boolean rotating;
    private double pointerX;
    private double pointerY;
    private float dragYaw;
    private float dragPitch;

    public Model(Document document) {
        super(document, TAG_NAME);
        addInternalEventListener("mousedown", this::beginRotation);
        addInternalEventListener("mousemove", this::rotate);
        addInternalEventListener("mouseup", this::endRotation);
    }

    public ModelSpec getModelSpec() {
        ModelSpec spec = ModelSpec.parse(getAttribute("type"), getTextContent(), getAttribute("flat"),
                getAttribute("yaw"), getAttribute("pitch"), getAttribute("roll"));
        return dragYaw == 0 && dragPitch == 0 ? spec : new ModelSpec(spec.type(), spec.source(), spec.flat(),
                (spec.yaw() + dragYaw) % 360, (spec.pitch() + dragPitch) % 360, spec.roll());
    }

    private void beginRotation(Event event) {
        if (!(event instanceof MouseEvent mouse) || mouse.button != 0) return;
        rotating = true;
        pointerX = mouse.clientX;
        pointerY = mouse.clientY;
    }

    private void rotate(Event event) {
        if (!rotating || !(event instanceof MouseEvent mouse)) return;
        double deltaX = mouse.clientX - pointerX;
        double deltaY = mouse.clientY - pointerY;
        pointerX = mouse.clientX;
        pointerY = mouse.clientY;
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY) || deltaX == 0 && deltaY == 0) return;
        dragYaw = (float) ((dragYaw + deltaX * DEGREES_PER_PIXEL) % 360);
        dragPitch = (float) ((dragPitch - deltaY * DEGREES_PER_PIXEL) % 360);
        if (document != null) document.markDirty(this, Drawer.REPAINT);
    }

    private void endRotation(Event event) {
        if (event instanceof MouseEvent mouse && mouse.button == 0) rotating = false;
    }

    @Override
    public String getInnerText() { return getTextContent(); }

    public AuiModelRenderService.Preview getPreview() {
        ModelSpec next = getModelSpec();
        if (!next.sameSource(observed)) {
            releasePreview();
            preview = AuiServices.models().create(next);
        }
        if (!next.equals(observed) && document != null) document.markDirty(this, Drawer.REPAINT);
        observed = next;
        return preview;
    }

    @Override
    public List<Element> getRenderChildren() { return List.of(); }

    @Override
    public List<Element> getExistingLayoutChildren() { return List.of(); }

    @Override
    public List<Node> getRenderChildNodes() { return List.of(); }

    @Override
    public List<RenderNode> createBodyRenderNodes() {
        return List.of(new RenderNode.ElementBackgroundNode(this), new RenderNode.ModelNode(this));
    }

    @Override
    public void tick() {
        super.tick();
        AuiModelRenderService.Preview current = getPreview();
        if (current != null && current.tick() && document != null) document.markDirty(this, Drawer.REPAINT);
    }

    @Override
    public void onDisconnectedFromDocument() {
        rotating = false;
        releasePreview();
        observed = null;
    }

    private void releasePreview() {
        if (preview != null) preview.close();
        preview = null;
    }
}
