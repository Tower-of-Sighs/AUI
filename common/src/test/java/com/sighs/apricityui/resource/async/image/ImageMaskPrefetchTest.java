package com.sighs.apricityui.resource.async.image;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ImageAsyncHandler#prefetchImages} 覆盖的 CSS 图像源：
 * img[src] / background-image / border-image / mask-image。
 *
 * <p>t11 的 {@code .card::after}、{@code .panel::after} 用 mask-image 画横线；遮罩纹理没进
 * 预取时首帧会先按「没有遮罩」画出一块实心色，纹理到位后才切成线条。这里断言预取请求确实
 * 下发（{@link ImageAsyncHandler} 的 HANDLES 里出现该路径），同时守住 CSS Masking 的语义：
 * {@code mask-image: none}、无效值、渐变都不产生需要下载的图像。</p>
 *
 * <p>生成伪元素不是 DOM 子节点，{@code document.getElements()} 里没有它们，所以预取要跟着
 * 绘制路径取 {@code getRenderChildren()}；{@code .panel::after} 用例专门盯这条路径。</p>
 *
 * <p>断言用「调用前后 HANDLES 的差集」而不是直接查表，避免其他用例已装过同名句柄造成假绿；
 * 差集反过来要求用例之间不留残留，所以 {@link #prefetchedPaths} 会在收尾时摘掉自己新增的句柄。</p>
 */
class ImageMaskPrefetchTest {
    /**
     * 文档路径前缀：相对 url 会被解析到它下面，断言因此能精确定位本用例的路径。
     * 只写一个斜杠是刻意的：{@link com.sighs.apricityui.loader.Loader#resolve} 按 {@code /}
     * 切分再拼回，{@code probe://x/a.html} 里的空段会被丢掉，解析结果固定成
     * {@code probe:/x/...}；这里直接采用归一化后的形状，期望值才能写成字面量。
     */
    private static final String DOC_PREFIX = "probe:/image-prefetch/";
    private static final String MASK_PATH = DOC_PREFIX + "mask-lines.svg";

    /** t11 主题 .panel::after 的声明形状：变量间接 + 生成伪元素 + mask-repeat。 */
    private static final String PSEUDO_MASK_HTML =
            "<html><head><style>"
                    + ".panel{position:relative;width:200px;height:80px;background:#fffef5;}"
                    + ".panel::after{content:\"\";position:absolute;top:12px;left:12px;right:10px;bottom:10px;"
                    + "background-color:#dbece5;mask-image:url(\"mask-lines.svg\");"
                    + "mask-size:256px 28px;mask-repeat:repeat;}"
                    + "</style></head><body><div class='panel'></div></body></html>";

    /** 与主题同形，但遮罩 url 藏在自定义属性里（t11-hand-drawn-doodle.css 的 --notebook-mask）。 */
    private static final String VAR_INDIRECTED_MASK_HTML =
            "<html><head><style>"
                    + ".theme{--notebook-mask:url(\"mask-lines.svg\");}"
                    + ".panel{position:relative;width:200px;height:80px;}"
                    + ".panel::after{content:\"\";position:absolute;top:0;left:0;right:0;bottom:0;"
                    + "background-color:#dbece5;mask-image:var(--notebook-mask);mask-repeat:repeat;}"
                    + "</style></head><body><div class='theme'><div class='panel'></div></div></body></html>";

    /** 元素自身的 mask-image（不依赖生成伪元素）。 */
    private static final String ELEMENT_MASK_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;background-color:#3f6d60;"
                    + "mask-image:url(\"mask-lines.svg\");}"
                    + "</style></head><body><div id='box'></div></body></html>";

    /** 多层遮罩：none 层按 CSS Masking 不参与，url 层照旧要下载。 */
    private static final String MULTI_LAYER_MASK_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;mask-image:none,url(\"mask-lines.svg\");}"
                    + "</style></head><body><div id='box'></div></body></html>";

    /** mask-image 初始值：没有遮罩，也就没有图像要预取。 */
    private static final String MASK_NONE_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;mask-image:none;}"
                    + "</style></head><body><div id='box'></div></body></html>";

    /** unset 在 mask-image 上回到初始值 none。 */
    private static final String MASK_UNSET_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;mask-image:unset;}"
                    + "</style></head><body><div id='box'></div></body></html>";

    /** 无效 url()：解析不出路径，没有图像可下载。 */
    private static final String MASK_INVALID_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;mask-image:url();}"
                    + "</style></head><body><div id='box'></div></body></html>";

    /** 渐变的 mask 层不产生图像请求。 */
    private static final String MASK_GRADIENT_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;mask-image:linear-gradient(#000,#000);}"
                    + "</style></head><body><div id='box'></div></body></html>";

    /** ../ 形式的相对 url：预取键必须是归一化后的路径，否则和绘制期请求的键对不上。 */
    private static final String RELATIVE_MASK_HTML =
            "<html><head><style>"
                    + "#box{width:200px;height:80px;mask-image:url(\"../mask-lines.svg\");}"
                    + "</style></head><body><div id='box'></div></body></html>";

    /** 预取原有覆盖面的回归护栏：img[src] / background-image / border-image。 */
    private static final String EXISTING_SOURCES_HTML =
            "<html><head><style>"
                    + "#bg{width:100px;height:40px;background-image:url(\"bg.png\");}"
                    + "#border{width:100px;height:40px;border-image-source:url(\"border.png\") 30;}"
                    + "</style></head><body>"
                    + "<img src=\"photo.png\">"
                    + "<div id='bg'></div><div id='border'></div>"
                    + "</body></html>";

    @Test
    void elementMaskImageIsPrefetched() {
        Set<String> requested = prefetchedPaths("element-mask", ELEMENT_MASK_HTML);

        assertTrue(requested.contains(MASK_PATH),
                "元素自身的 mask-image 必须进预取，否则首帧先画出未遮罩的实心块: " + requested);
    }

    @Test
    void generatedPseudoElementMaskImageIsPrefetched() {
        Set<String> requested = prefetchedPaths("pseudo-mask", PSEUDO_MASK_HTML);

        assertTrue(requested.contains(MASK_PATH),
                "t11 的遮罩声明在 ::after 上，预取必须覆盖生成伪元素: " + requested);
    }

    @Test
    void variableIndirectedPseudoElementMaskImageIsPrefetched() {
        Set<String> requested = prefetchedPaths("var-mask", VAR_INDIRECTED_MASK_HTML);

        assertTrue(requested.contains(MASK_PATH),
                "mask-image:var(--x) 要与主题里一样按解析后的 url 预取: " + requested);
    }

    @Test
    void noneLayerOfAMultiLayerMaskIsSkipped() {
        Set<String> requested = prefetchedPaths("multi-layer-mask", MULTI_LAYER_MASK_HTML);

        assertTrue(requested.contains(MASK_PATH), "多层里可用的 url 层仍要预取: " + requested);
    }

    @Test
    void relativeMaskUrlIsPrefetchedInNormalizedForm() {
        // 文档在 nested/ 下，../ 归一化回 DOC_PREFIX 根，正好等于 MASK_PATH。
        Set<String> requested = prefetchedPaths("nested/url-normalization", RELATIVE_MASK_HTML);

        assertTrue(requested.contains(MASK_PATH),
                "相对 url 要按归一化后的路径预取，绘制期 request 用的也是这个键: " + requested);
    }

    @Test
    void maskImageNonePrefetchesNothing() {
        assertTrue(prefetchedPaths("mask-none", MASK_NONE_HTML).isEmpty(),
                "mask-image:none 是初始值，不是遮罩图像源");
    }

    @Test
    void maskImageUnsetPrefetchesNothing() {
        assertTrue(prefetchedPaths("mask-unset", MASK_UNSET_HTML).isEmpty(),
                "mask-image:unset 回到初始值 none");
    }

    @Test
    void invalidMaskImagePrefetchesNothing() {
        assertTrue(prefetchedPaths("mask-invalid", MASK_INVALID_HTML).isEmpty(),
                "url() 没有路径，解析不出图像源");
    }

    @Test
    void gradientMaskImagePrefetchesNothing() {
        assertTrue(prefetchedPaths("mask-gradient", MASK_GRADIENT_HTML).isEmpty(),
                "渐变遮罩不产生图像请求");
    }

    @Test
    void existingImageSourcesAreStillPrefetched() {
        Set<String> requested = prefetchedPaths("existing-sources", EXISTING_SOURCES_HTML);

        assertTrue(requested.contains(DOC_PREFIX + "photo.png"), "img[src] 仍要预取: " + requested);
        assertTrue(requested.contains(DOC_PREFIX + "bg.png"), "background-image 仍要预取: " + requested);
        assertTrue(requested.contains(DOC_PREFIX + "border.png"), "border-image 仍要预取: " + requested);
    }

    /** 不带 mask 的文档不该因为这次改动多出任何请求。 */
    @Test
    void documentWithoutImageSourcesPrefetchesNothing() {
        Set<String> requested = prefetchedPaths("plain",
                "<html><head><style>#box{width:10px;height:10px;background-color:#123;}</style></head>"
                        + "<body><div id='box'></div></body></html>");

        assertTrue(requested.isEmpty(), "没有图像源就没有预取请求: " + requested);
    }

    /**
     * 本用例文档路径前缀下的新预取请求（refresh() 内会调用 prefetchImages）。
     *
     * <p>差集断言有个前提：HANDLES 在这个用例开始时不含本用例的路径。所以新增的句柄在
     * finally 里全部摘掉——否则先跑的用例把 {@code mask-lines.svg} 留在表里，后面期望同一个
     * 路径的用例拿到的差集恒为空，断言就成了假红。</p>
     */
    private static Set<String> prefetchedPaths(String name, String html) {
        Set<String> before = new LinkedHashSet<>(imageHandles().keySet());
        String path = DOC_PREFIX + name + ".html";
        HTML.putTemple(path, html);
        Document document = new Document(path, false);
        Set<String> added = new LinkedHashSet<>();
        try {
            document.refresh();
            added.addAll(imageHandles().keySet());
            added.removeAll(before);
        } finally {
            document.remove();
            for (String key : added) {
                imageHandles().remove(key);
            }
        }
        added.removeIf(key -> !key.startsWith(DOC_PREFIX));
        return added;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ImageHandle> imageHandles() {
        try {
            Field field = ImageAsyncHandler.class.getDeclaredField("HANDLES");
            field.setAccessible(true);
            return (Map<String, ImageHandle>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** 守住例外：预取路径不能为空串（Loader.resolve 对空 url 返回 ""）。 */
    @Test
    void emptyResolvedPathIsNeverRequested() {
        Set<String> requested = prefetchedPaths("empty-url",
                "<html><head><style>#box{width:10px;height:10px;mask-image:url(\"\");}</style></head>"
                        + "<body><div id='box'></div></body></html>");

        assertFalse(requested.contains(""), "空路径不能进 HANDLES");
        assertTrue(requested.isEmpty(), "空 url 没有图像可下载: " + requested);
    }
}
