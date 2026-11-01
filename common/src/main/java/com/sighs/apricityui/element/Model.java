package com.sighs.apricityui.element;

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
    private ModelSpec observed;
    private AuiModelRenderService.Preview preview;

    public Model(Document document) {
        super(document, TAG_NAME);
    }

    public ModelSpec getModelSpec() {
        return ModelSpec.parse(getAttribute("type"), getTextContent(), getAttribute("flat"),
                getAttribute("yaw"), getAttribute("pitch"), getAttribute("roll"));
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
        releasePreview();
        observed = null;
    }

    private void releasePreview() {
        if (preview != null) preview.close();
        preview = null;
    }
}
