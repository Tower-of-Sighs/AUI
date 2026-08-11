package com.sighs.apricityui.behavior.richtext;

import com.sighs.apricityui.init.Element;

/**
 * 富文本编辑操作（操作日志条目）：描述一次变换的"作用单元 + 做什么 + 影响范围 + 前后光标"。
 * <p>
 * undo/redo 通过重放正/逆操作完成。类型：
 * <ul>
 *   <li>{@code insertText / deleteText}：文本插入/删除（text 携带文本；deleteText 的 text 是被删文本）。</li>
 *   <li>{@code insertHtml / deleteHtml}：HTML 片段插入/删除（html 携带片段；deleteHtml 的 html 是操作前被删片段）。</li>
 *   <li>{@code insertBr / deleteBr}：{@code <br>} 插入/删除（占 1 个归一化字符）。</li>
 *   <li>{@code splitBlock}：块内拆段（unit 是被拆块，start 为块内偏移，html 为新块标签）。</li>
 *   <li>{@code mergeBackward}：当前块并入前一兄弟块（start 为合并点偏移）。</li>
 *   <li>{@code mergeForward}：下一兄弟块并入当前块（start 为被并入块的原始长度）。</li>
 * </ul>
 * 连续 {@code insertText}（位置相邻）可经 {@link #mergeableWith}/{@link #merge} 合并成一条 undo 记录。
 * 块操作（splitBlock/mergeBackward/mergeForward）应用时以焦点单元（selection.anchorUnit）为准。
 */
public record RichTextOperation(Element unit, String type, int start, int end, String text, String html,
                                int cursorBefore, int cursorAfter) {

    public static RichTextOperation insertText(Element unit, int at, String insertedText, int before, int after) {
        return new RichTextOperation(unit, "insertText", at, at, insertedText, null, before, after);
    }

    public static RichTextOperation deleteText(Element unit, int at, String deletedText, int before, int after) {
        return new RichTextOperation(unit, "deleteText", at, at + deletedText.length(), deletedText, null, before, after);
    }

    public static RichTextOperation insertHtml(Element unit, int at, String html, int before, int after) {
        return new RichTextOperation(unit, "insertHtml", at, at, null, html, before, after);
    }

    public static RichTextOperation deleteHtml(Element unit, int at, int end, String deletedHtml, int before, int after) {
        return new RichTextOperation(unit, "deleteHtml", at, end, null, deletedHtml, before, after);
    }

    public static RichTextOperation insertBr(Element unit, int at, int before, int after) {
        return new RichTextOperation(unit, "insertBr", at, at, null, null, before, after);
    }

    public static RichTextOperation deleteBr(Element unit, int at, int before, int after) {
        return new RichTextOperation(unit, "deleteBr", at, at + 1, null, null, before, after);
    }

    /** 块内拆段：unit 内 start 处拆出后半为新块（html = 新块标签）。 */
    public static RichTextOperation splitBlock(Element unit, int at, String newTag, int before, int after) {
        return new RichTextOperation(unit, "splitBlock", at, at, null, newTag, before, after);
    }

    /** 当前块并入前一兄弟块（start = 合并点偏移，即前块合并后光标位置）。 */
    public static RichTextOperation mergeBackward(Element unit, int mergeOffset, int before, int after) {
        return new RichTextOperation(unit, "mergeBackward", mergeOffset, mergeOffset, null, null, before, after);
    }

    /** 下一兄弟块并入当前块（start = 被并入块的原始长度，html = 被并入块标签）。 */
    public static RichTextOperation mergeForward(Element unit, int nextLength, String nextTag, int before, int after) {
        return new RichTextOperation(unit, "mergeForward", nextLength, nextLength, null, nextTag, before, after);
    }

    /** 逆操作：apply(inverse) 把文档恢复到操作前（光标恢复 cursorBefore）。 */
    public RichTextOperation inverse() {
        return switch (type) {
            case "insertText" -> deleteText(unit, start, text, cursorAfter, cursorBefore);
            case "deleteText" -> insertText(unit, start, text, cursorAfter, cursorBefore);
            // insertHtml 的删除终点 = 插入后光标（插入内容末尾）
            case "insertHtml" -> deleteHtml(unit, start, cursorAfter, html, cursorAfter, cursorBefore);
            case "deleteHtml" -> insertHtml(unit, start, html, cursorAfter, cursorBefore);
            case "insertBr" -> deleteBr(unit, start, cursorAfter, cursorBefore);
            case "deleteBr" -> insertBr(unit, start, cursorAfter, cursorBefore);
            // 拆段 ↔ 并入前块；合并点偏移恢复
            case "splitBlock" -> mergeBackward(unit, start, cursorAfter, cursorBefore);
            case "mergeBackward" -> splitBlock(unit, start, blockTagOf(unit), cursorAfter, cursorBefore);
            case "mergeForward" -> splitBlock(unit, start, html, cursorAfter, cursorBefore);
            default -> throw new IllegalStateException("unknown operation type: " + type);
        };
    }

    private static String blockTagOf(Element unit) {
        return unit == null ? "P" : unit.tagName;
    }

    /** 连续输入合并判定：前一条与本条都是 insertText 且位置相邻（同单元）。 */
    public boolean mergeableWith(RichTextOperation previous) {
        return previous != null
                && previous.unit == unit
                && "insertText".equals(previous.type) && "insertText".equals(type)
                && previous.start + previous.text.length() == start;
    }

    /** 合并连续输入为一条（text 拼接，光标取后值）。 */
    public RichTextOperation merge(RichTextOperation previous) {
        return new RichTextOperation(unit, "insertText", previous.start, start,
                previous.text + text, null, previous.cursorBefore, cursorAfter);
    }

    @Override
    public String toString() {
        return type + "(" + (unit == null ? "?" : unit.tagName) + "@" + start + "," + end + ",'" + text + "'," + html + ")";
    }
}

