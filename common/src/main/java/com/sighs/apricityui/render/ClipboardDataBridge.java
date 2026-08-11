package com.sighs.apricityui.render;

/**
 * 浏览器 ClipboardEvent.clipboardData 的最小 AUI 桥：getData/setData 对接
 * AUI 剪贴板（text/plain 与 text/html）。供 copy/cut/paste 事件的 JS 处理器读取/写入。
 */
public class ClipboardDataBridge {

    public String getData(String type) {
        if (type == null) return null;
        if ("text/plain".equalsIgnoreCase(type)) return Operation.getClipboardText();
        if ("text/html".equalsIgnoreCase(type)) return Operation.getInternalClipboardHtml();
        return null;
    }

    public void setData(String type, String value) {
        if (type == null) return;
        if ("text/plain".equalsIgnoreCase(type)) Operation.setClipboardText(value);
        else if ("text/html".equalsIgnoreCase(type)) Operation.setInternalClipboardHtml(value);
    }
}
