package com.sighs.apricityui.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code aui-preload} meta 控制启动阶段的模板预构建。
 */
class HTMLPreloadTest {

    @Test
    void startupPreparationOnlyIncludesMarkedTemplates() {
        String preloaded = "test://preload/marked";
        String lazy = "test://preload/unmarked";
        String disabled = "test://preload/disabled";
        HTML.putTemple(preloaded, """
                <html><head><meta name="aui-preload" content="true"></head>
                <body><main>marked</main></body></html>
                """);
        HTML.putTemple(lazy, """
                <html><body><main>lazy</main></body></html>
                """);
        HTML.putTemple(disabled, """
                <html><head><meta name="aui-preload" content="none"></head>
                <body><main>disabled</main></body></html>
                """);
        HTML.invalidatePreparedTemplates(List.of(preloaded, lazy, disabled));

        HTML.prepareTemplates();

        assertTrue(HTML.isTemplatePrepared(preloaded), "声明 aui-preload 的页面应在启动阶段预构建");
        assertFalse(HTML.isTemplatePrepared(lazy), "未声明的页面应留到首次打开时懒构建");
        assertFalse(HTML.isTemplatePrepared(disabled), "content=none 应显式关闭预构建");
    }

    @Test
    void preloadMetaIsPresenceBasedAndIgnoresComments() {
        assertTrue(HTML.isPreloadEnabledInMarkup(
                "<head><meta name=\"aui-preload\"></head>"),
                "没有 content 时，标签存在即开启");
        assertFalse(HTML.isPreloadEnabledInMarkup(
                "<head><meta charset=\"UTF-8\"></head>"));
        assertFalse(HTML.isPreloadEnabledInMarkup(
                "<head><!-- <meta name=\"aui-preload\"> --></head>"),
                "注释里的 meta 不应生效");
    }
}
