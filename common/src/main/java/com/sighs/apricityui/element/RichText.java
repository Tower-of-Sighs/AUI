package com.sighs.apricityui.element;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.behavior.TextSelection;
import com.sighs.apricityui.behavior.richtext.RichTextNavigation;
import com.sighs.apricityui.behavior.richtext.RichTextSelection;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.registry.annotation.ElementRegister;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.Graph;
import com.sighs.apricityui.style.Text;

/**
 * 富文本可编辑元素（{@code <richtext>}）：内容保留为子节点树（TextNode + 行内元素），
 * 不扁平化。提供鼠标点击定位、拖拽选区与聚焦光标渲染；编辑选区状态由
 * {@link RichTextSelection} 维护，高亮由 Element 的 run 绘制路径（经 Document
 * 的选区来源解析）渲染。
 * <p>
 * Phase 1：只做选择/定位/移动；键盘输入、删除、格式命令在后续阶段接入。
 */
@ElementRegister(RichText.TAG_NAME)
public class RichText extends Element {
    public static final String TAG_NAME = "RICHTEXT";
    private long lastBlinkTime;

    public RichText(Document document) {
        super(document, TAG_NAME);
        lastBlinkTime = System.currentTimeMillis();
        addInternalEventListener("mousedown", event -> {
            if (!(event instanceof MouseEvent mouse) || document == null) return;
            if (mouse.button != 0 && mouse.button != -1) return;
            document.clearAllTextSelectionsExcept(this);
            document.getRichTextSelection().setFromPoint(this, mouse, mouse.shiftKey);
            document.setFocusedElement(this);
            event.preventDefault();
        });
        addInternalEventListener("mousemove", event -> {
            if (!(event instanceof MouseEvent mouse) || document == null) return;
            RichTextSelection selection = document.getRichTextSelection();
            if (!selection.isSelecting() || document.getPressedElement() != this) return;
            SelectionUnits.UnitOffset target = TextSelection.resolveUnitOffset(this, mouse.clientX, mouse.clientY);
            if (target != null) {
                selection.extendTo(target.unit(), target.offset());
            }
        });
        addInternalEventListener("mouseup", event -> {
            if (document == null) return;
            document.getRichTextSelection().setSelecting(false);
        });
        addInternalEventListener("blur", event -> {
            if (document == null) return;
            document.getRichTextSelection().setSelecting(false);
        });
    }

    @Override
    public boolean canFocus() {
        return true;
    }

    @Override
    public void drawPhase(PoseStack poseStack, Base.RenderPhase phase) {
        super.drawPhase(poseStack, phase);
        if (phase != Base.RenderPhase.BODY || document == null) return;
        if (!Element.isElementFocusing(this)) return;
        RichTextSelection selection = document.getRichTextSelection();
        if (selection == null || !selection.hasAnchor() || !selection.collapsed()) return;
        if (selection.getAnchorUnit() != this) return;

        RichTextNavigation.Caret caret = RichTextNavigation.caretPosition(this, selection.getAnchorOffset());
        Text text = Text.of(this);
        Graph.drawCursor(poseStack.last().pose(), (float) caret.x(), (float) caret.y(),
                (float) Math.max(caret.lineHeight(), Size.DEFAULT_LINE_HEIGHT),
                Text.getFontColor(this), lastBlinkTime);
    }
}
