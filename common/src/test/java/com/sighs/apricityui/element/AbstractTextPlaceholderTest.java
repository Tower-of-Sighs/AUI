package com.sighs.apricityui.element;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * issue#87 回归：元素经 DOM 升级（runInitFromDomOnce）时，若标签上没有 placeholder 属性，
 * getAttribute 返回 null，不得把字段初始值 "" 覆盖为 null（渲染侧 placeholder.isEmpty() 会 NPE）。
 */
class AbstractTextPlaceholderTest {

    @Test
    void inputWithoutPlaceholderAttributeKeepsEmptyPlaceholderAfterDomInit() throws Exception {
        Document document = new Document("test://abstract-text-placeholder", false);
        Input input = new Input(document);
        input.runInitFromDomOnce(new Element(document, "input"));

        assertEquals("", placeholderOf(input));
    }

    @Test
    void textareaWithoutPlaceholderAttributeKeepsEmptyPlaceholderAfterDomInit() throws Exception {
        Document document = new Document("test://abstract-text-placeholder", false);
        TextArea textarea = new TextArea(document);
        textarea.runInitFromDomOnce(new Element(document, "textarea"));

        assertEquals("", placeholderOf(textarea));
    }

    @Test
    void placeholderAttributeStillSyncsAfterDomInit() throws Exception {
        Document document = new Document("test://abstract-text-placeholder", false);
        Input input = new Input(document);
        input.setAttribute("placeholder", "hint");
        input.runInitFromDomOnce(new Element(document, "input"));

        assertEquals("hint", placeholderOf(input));
    }

    private static String placeholderOf(AbstractText element) throws Exception {
        Field field = AbstractText.class.getDeclaredField("placeholder");
        field.setAccessible(true);
        return (String) field.get(element);
    }
}
