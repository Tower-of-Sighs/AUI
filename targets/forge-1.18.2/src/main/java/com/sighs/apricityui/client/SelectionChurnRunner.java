package com.sighs.apricityui.client;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.behavior.SelectionUnits;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 开发期验收：用脚本驱动"持续拖选"。
 *
 * <p>为什么需要它：AUI 的 JS 侧没有 selection API，外部调试协议也只有 hover/click/fill、
 * 没有拖拽，所以只在"选中时"出现的问题（比如整行频闪）没法用外部工具复现。
 * 这里每几个 tick 把选区从某个文本单元的开头延伸到递增的偏移，等价于一直在拖选。</p>
 *
 * <p>属性：{@code apricityui.devOverlay.selectionChurn=true} 启用；
 * {@code apricityui.devOverlay.selectionTarget=<css 选择器>} 可指定拖选哪个元素，
 * 不指定就用文档里第一个有可选文本的单元。
 * gradle 映射：{@code -PauiSelectionChurn=true} / {@code -PauiSelectionTarget=...}。</p>
 */
@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class SelectionChurnRunner {
    private static final String ENABLE_PROPERTY = "apricityui.devOverlay.selectionChurn";
    private static final String TARGET_PROPERTY = "apricityui.devOverlay.selectionTarget";
    private static final int STEP_TICKS = 5;

    private static int ticks;
    private static int offset;
    private static boolean started;
    private static Element unit;
    private static int unitLength;

    private SelectionChurnRunner() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        if (Minecraft.getInstance() == null) return;
        if (++ticks % STEP_TICKS != 0) return;

        Document document = pickDocument();
        if (document == null) return;
        var selection = document.getDocumentSelection();
        if (selection == null) return;

        if (!started && !begin(document, selection)) return;
        if (unit == null || unitLength <= 0) return;

        offset++;
        if (offset > unitLength) {
            // 到头就重来，制造"选区来回变化"的持续负载。
            offset = 0;
            selection.clear();
            selection.collapse(unit, 0);
            return;
        }
        selection.extendTo(unit, offset);
    }

    private static boolean begin(Document document, com.sighs.apricityui.behavior.DocumentSelection selection) {
        String target = System.getProperty(TARGET_PROPERTY, "").trim();
        Element candidate = null;
        String source = "target";
        if (!target.isEmpty()) {
            Element found = document.querySelector(target);
            candidate = found == null ? null : SelectionUnits.resolveUnit(found);
        }
        if (candidate == null) {
            source = "unit";
            for (Element element : SelectionUnits.enumerateUnits(document)) {
                if (element != null && !SelectionUnits.ownSelectableText(element).isEmpty()) {
                    candidate = element;
                    break;
                }
            }
        }
        if (candidate == null) {
            // 回退：不挑"选择单元"（isSelectionUnit 会要求 user-select 等条件），
            // 直接取第一个自带文本的元素，保证能把选区驱动起来。
            source = "fallback";
            for (Element element : document.getElements()) {
                if (element != null && !SelectionUnits.ownSelectableText(element).isEmpty()) {
                    candidate = element;
                    break;
                }
            }
        }
        if (candidate == null) {
            ApricityUI.LOGGER.warn("[AUI DevOverlay] selection churn: no candidate elements={} units={}",
                    document.getElements().size(), SelectionUnits.enumerateUnits(document).size());
            return false;
        }
        unit = candidate;
        unitLength = SelectionUnits.ownSelectableText(candidate).length();
        if (unitLength <= 0) return false;
        started = true;
        offset = 0;
        selection.clear();
        selection.collapse(unit, 0);
        ApricityUI.LOGGER.info("[AUI DevOverlay] selection churn on unit=<{}> textLength={} via={}",
                unit.getNodeName(), unitLength, source);
        return true;
    }

    private static Document pickDocument() {
        for (Document document : Document.getAll()) {
            if (document != null && document.documentElement != null) return document;
        }
        return null;
    }
}
