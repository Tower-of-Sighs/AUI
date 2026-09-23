package com.sighs.apricityui.webapi;

import com.sighs.apricityui.canvas.BrowserImage;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.element.Svg;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.resource.Font;
import com.sighs.apricityui.render.Drawer;
import com.sighs.apricityui.render.RenderNode;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.script.ecmascript.EcmaEventListener;
import com.sighs.apricityui.spi.AuiScriptService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.util.DataUri;
import dev.latvian.mods.rhino.Callable;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.ScriptableObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.InputStream;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.SAME_THREAD)
class McUiComponentCompatibilityTest {
    private static final String THEME = "assets/apricityui/apricity/apricityui/theme/ore/";
    private static final String RUNTIME = THEME + "runtime/";
    private AuiScriptService previousScriptService;

    @BeforeEach
    void captureScriptService() {
        previousScriptService = AuiServices.script();
    }

    @AfterEach
    void restoreScriptService() {
        AuiServices.setScript(previousScriptService);
        Font.clear();
    }

    @Test
    void allThirtyTwoComponentsRegisterAndRenderOnTheGenericDom() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);

        Object result;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, read("vue.aui.js"), "vue.aui.js", 1, null);
            context.evaluateString(scope, read("mcui-oreui.aui.js"), "mcui-oreui.aui.js", 1, null);
            result = context.evaluateString(scope, ""
                    + "var componentNames = ["
                    + "'mc-icon','mc-button','mc-card','mc-panel','mc-tooltip','mc-progress','mc-spinner',"
                    + "'mc-checkbox','mc-radio','mc-radio-group','mc-form-field','mc-switch','mc-dropdown',"
                    + "'mc-text-field','mc-slider','mc-layout','mc-header','mc-appbar','mc-appbar-button',"
                    + "'mc-appbar-icon','mc-tabs','mc-button-tabs','mc-list','mc-list-item','mc-scroll-view',"
                    + "'mc-modal','mc-confirm','mc-drawer','mc-loading-mask','mc-pop-host',"
                    + "'mc-tcode','mc-formatted-text'];"
                    + "var root = document.createElement('div'); root.id = 'component-showcase'; document.body.appendChild(root);"
                    + "function componentProps(name){var base={title:'Title',label:'Label',text:'Hello \\u00a7aOre UI',name:'accessibility',open:false,visible:false};"
                    + "if(name==='mc-checkbox'||name==='mc-switch')base.modelValue=false;"
                    + "if(name==='mc-radio')return {modelValue:'one',value:'one'};"
                    + "if(name==='mc-radio-group')return {modelValue:'one',options:[{label:'One',value:'one'},{label:'Two',value:'two'}]};"
                    + "if(name==='mc-dropdown')return {modelValue:1,options:['One','Two']};"
                    + "if(name==='mc-text-field')return {modelValue:'Hello',hint:'Type here'};"
                    + "if(name==='mc-slider')return {modelValue:25,min:0,max:100,step:5};"
                    + "if(name==='mc-progress')return {value:64,max:100,label:'Progress'};"
                    + "if(name==='mc-tabs'||name==='mc-button-tabs')return {modelValue:'one',title:'Tabs',items:[{label:'One',value:'one'},{label:'Two',value:'two'}]};"
                    + "if(name==='mc-list')return {modelValue:'one',mode:'single'};"
                    + "if(name==='mc-list-item')return {label:'One',value:'one'};return base;}"
                    + "var Root = { render:function(){ return Vue.h('div',{class:'ore-theme'},componentNames.map(function(name){"
                    + " return Vue.h(Vue.resolveComponent(name),componentProps(name),{default:function(){return name;},"
                    + " label:function(){return name;},trigger:function(){return name;}}); })); } };"
                    + "var app = Vue.createApp(Root); app.config.throwUnhandledErrorInProduction=true; app.use(McUIVue.default);"
                    + "var registered = componentNames.filter(function(name){return !!app.component(name);}).length;"
                    + "app.mount(root); function countNodes(node){var total=1; var children=node.childNodes||[];"
                    + " for(var i=0;i<children.length;i++) total+=countNodes(children[i]); return total;}"
                    + "var rendered = countNodes(root); app.unmount();"
                    + "registered + '|' + rendered;",
                    "mcui-all-components", 1, null);
        }

        String[] values = String.valueOf(result).split("\\|");
        assertEquals("32", values[0]);
        assertTrue(Integer.parseInt(values[1]) > 10, "component render tree was empty: " + result);
    }

    @Test
    void buttonAndCheckboxEventsFlowThroughTheGenericEventBridge() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);
        CountDownLatch switchUpdated = new CountDownLatch(1);
        ScriptableObject.putProperty(scope, "__auiSwitchUpdated",
                RhinoTestSupport.wrap(context, scope, switchUpdated), context);

        Object result;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, read("vue.aui.js"), "vue.aui.js", 1, null);
            context.evaluateString(scope, read("mcui-oreui.aui.js"), "mcui-oreui.aui.js", 1, null);
            result = context.evaluateString(scope, ""
                    + "var clicks=0, checked=false, switched=Vue.ref(false); var root=document.createElement('div'); document.body.appendChild(root);"
                    + "var Root={render:function(){return Vue.h('div',null,["
                    + "Vue.h(McUIVue.McButton,{onClick:function(){clicks++;}},{default:function(){return 'Click';}}),"
                    + "Vue.h(McUIVue.McCheckbox,{modelValue:checked,'onUpdate:modelValue':function(v){checked=v;}}),"
                    + "Vue.h(McUIVue.McSwitch,{modelValue:switched.value,'onUpdate:modelValue':function(v){switched.value=v;}})"
                    + "]);}}; var app=Vue.createApp(Root); app.config.throwUnhandledErrorInProduction=true; app.mount(root);"
                            + "root.getElementsByTagName('button').item(0).click(); var checkbox=root.querySelector('.custom-checkbox');"
                    + "checkbox.focus({preventScroll:true}); checkbox.click();"
                    + "var optionHits=0, passivePrevented=true; function optionListener(event){optionHits++; event.preventDefault(); passivePrevented=event.defaultPrevented;}"
                    + "checkbox.addEventListener('mcui-options',optionListener,{passive:true});"
                    + "var optionEvent=window.createEvent('mcui-options',false); optionEvent.cancelable=true; checkbox.dispatchEvent(optionEvent);"
                    + "checkbox.removeEventListener('mcui-options',optionListener,{passive:true});"
                    + "checkbox.dispatchEvent(window.createEvent('mcui-options',false));"
                    + "var switchControl=root.querySelector('.switch_content'); switchControl.click();"
                    + "var answer=clicks+'|'+checked+'|'+(document.activeElement===checkbox)+'|'+optionHits+'|'+passivePrevented"
                    + "+'|'+switched.value; Vue.nextTick(function(){__auiSwitchUpdated.countDown();}); answer;",
                    "mcui-basic-events", 1, null);
        }

        assertEquals("1|true|true|1|false|true", result);
        assertTrue(switchUpdated.await(2, TimeUnit.SECONDS), "Switch update did not reach Vue's next tick");
        assertEquals("true", document.querySelector(".switch").getAttribute("aria-checked"));
    }

    @Test
    void confirmCancelButtonActivatesThroughVisualHitTesting() throws Exception {
        Size.setViewportOverride(800, 600);
        Document document = TestDocumentFactory.createDocument();
        for (String[] face : new String[][]{
                {"NotoSans Bold", "fonts/noto-sans-bold.ttf"},
                {"Minecraft Seven", "fonts/minecraft-seven.otf"},
                {"Minecraft Ten", "fonts/minecraft-ten.otf"}
        }) {
            try (InputStream font = McUiComponentCompatibilityTest.class.getClassLoader()
                    .getResourceAsStream(THEME + face[1])) {
                assertNotNull(font, face[1]);
                assertTrue(Font.registerFont(face[0], font));
            }
        }
        document.body.setAttribute("class", "ore-theme");
        document.body.setAttribute("style", "width:800px;height:600px;");
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(readTheme("ore.css"), cache, "ore/ore.css");
        CSS.readCSS(readTheme("ore-components.css"), cache, "ore/ore-components.css");
        CSS.readCSS(readTheme("mcui.css"), cache, "ore/mcui.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        Element root = document.createElement("div");
        document.body.appendChild(root);

        CountDownLatch mounted = new CountDownLatch(1);
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);
        ScriptableObject.putProperty(scope, "root",
                RhinoTestSupport.wrap(context, scope, root), context);
        ScriptableObject.putProperty(scope, "__auiConfirmMounted",
                RhinoTestSupport.wrap(context, scope, mounted), context);

        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, read("vue.aui.js"), "vue.aui.js", 1, null);
            context.evaluateString(scope, read("mcui-oreui.aui.js"), "mcui-oreui.aui.js", 1, null);
            context.evaluateString(scope,
                    "var confirmOpen=Vue.ref(true);"
                            + "var Root={render:function(){return Vue.h('div',null,["
                            + "Vue.h('output',{id:'confirm-state'},String(confirmOpen.value)),"
                            + "Vue.h(McUIVue.McConfirm,{open:confirmOpen.value,title:'保存设置?',"
                            + "'onUpdate:open':function(value){confirmOpen.value=value;}}"
                            + ",{default:function(){return '是否保存当前游戏设置?';}})"
                            + "]);}};"
                            + "var app=Vue.createApp(Root);app.config.throwUnhandledErrorInProduction=true;"
                            + "app.mount(root);Vue.nextTick(function(){__auiConfirmMounted.countDown();});",
                    "mcui-confirm-hit-test", 1, null);
        }
        assertTrue(mounted.await(2, TimeUnit.SECONDS));
        document.tickFrame();

        Element modalArea = document.querySelector("modal_area");
        Element cancel = modalArea == null ? null : modalArea.querySelector("button");
        assertNotNull(modalArea);
        assertNotNull(cancel);
        Rect modalRect = modalArea.getRenderer().getCommittedRect();
        Rect buttonRect = cancel.getRenderer().getCommittedRect();
        double x = buttonRect.position.x + buttonRect.getElementSize().width() / 2.0
                - modalRect.getElementSize().width() / 2.0;
        double y = buttonRect.position.y + buttonRect.getElementSize().height() / 2.0
                - modalRect.getElementSize().height() / 2.0;

        assertEquals(cancel, document.hitTest(new Position(x, y)));
        MouseEvent.tiggerEvent(new MouseEvent("mousemove", new Position(x, y), -1, false), document);
        document.tickFrame();
        assertEquals(cancel, document.hitTest(new Position(x, y)),
                "The real McConfirm visual button must retain its transformed hit box after hover");
        cancel.getRenderer().clearCommittedWorldTransformSubtree();
        document.markHitTestDirtyAll();
        assertEquals(cancel, document.hitTest(new Position(x, y)),
                "The real McConfirm hit box must not fall back to its down-right raw layout position");
        MouseEvent.tiggerEvent(new MouseEvent("mousedown", new Position(x, y), 0, false), document);
        MouseEvent.tiggerEvent(new MouseEvent("mouseup", new Position(x, y), 0, false), document);
        assertEquals("false", document.querySelector("#confirm-state").getTextContent());
    }

    @Test
    void numberTextFieldFiltersInvalidCharactersOnRhino() throws Exception {
        Element.register(TextArea.TAG_NAME, (owner, tag) -> new TextArea(owner));

        Document document = TestDocumentFactory.createDocument();
        Element root = document.createElement("div");
        document.body.appendChild(root);

        CountDownLatch updated = new CountDownLatch(1);
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);
        ScriptableObject.putProperty(scope, "root",
                RhinoTestSupport.wrap(context, scope, root), context);
        ScriptableObject.putProperty(scope, "__auiNumberUpdated",
                RhinoTestSupport.wrap(context, scope, updated), context);

        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, read("vue.aui.js"), "vue.aui.js", 1, null);
            context.evaluateString(scope, read("mcui-oreui.aui.js"), "mcui-oreui.aui.js", 1, null);
            context.evaluateString(scope,
                    "var numberModel=Vue.ref('');"
                            + "var Root={render:function(){return Vue.h('div',null,["
                            + "Vue.h('output',{id:'number-model'},numberModel.value),"
                            + "Vue.h(McUIVue.McTextField,{modelValue:numberModel.value,type:'number',"
                            + "'onUpdate:modelValue':function(value){numberModel.value=value;}})"
                            + "]);}};"
                            + "var app=Vue.createApp(Root);"
                            + "app.config.throwUnhandledErrorInProduction=true;"
                            + "app.mount(root);"
                            + "var numberInput=root.querySelector('.input');"
                            + "numberInput.value='12a3';"
                            + "numberInput.dispatchEvent(window.createEvent('input',false));"
                            + "Vue.nextTick(function(){__auiNumberUpdated.countDown();});",
                    "mcui-number-text-field", 1, null);
        }

        assertTrue(updated.await(2, TimeUnit.SECONDS),
                "number TextField update did not reach Vue's next tick");

        Element numberInput = root.querySelector(".input");
        Element modelValue = root.querySelector("#number-model");
        assertNotNull(numberInput);
        assertNotNull(modelValue);
        assertEquals("123", numberInput.value, "display value was not filtered");
        assertEquals("123", modelValue.getTextContent(), "model value was not filtered");
    }

    @Test
    void setSliderBuildsEverySegmentAndLabelOnRhino() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);

        Object result;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, read("vue.aui.js"), "vue.aui.js", 1, null);
            context.evaluateString(scope, read("mcui-oreui.aui.js"), "mcui-oreui.aui.js", 1, null);
            result = context.evaluateString(scope, ""
                    + "var root=document.createElement('div');document.body.appendChild(root);"
                    + "var Root={render:function(){return Vue.h(McUIVue.McSlider,{modelValue:2,min:0,max:4,segments:4,type:'set'});}};"
                    + "var app=Vue.createApp(Root);app.config.throwUnhandledErrorInProduction=true;app.mount(root);"
                    + "root.querySelectorAll('.slider_segment').length+'|'+root.querySelectorAll('.slider_value_info').length;",
                    "mcui-set-slider", 1, null);
        }

        assertEquals("3|5", result);
    }

    @Test
    void iconBasicDemoKeepsAllFiveIconsIncludingEmbeddedKeyImage() throws Exception {
        Element.register(Svg.TAG_NAME, (owner, tag) -> new Svg(owner));
        Document document = TestDocumentFactory.createDocument();
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);

        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, read("vue.aui.js"), "vue.aui.js", 1, null);
            context.evaluateString(scope, read("mcui-oreui.aui.js"), "mcui-oreui.aui.js", 1, null);
            context.evaluateString(scope, ""
                            + "var root=document.createElement('div');document.body.appendChild(root);"
                            + "var names=['mc-add','mc-save','mc-delete','mc-key-a','mc-x-creative'];"
                            + "var Root={render:function(){return Vue.h('div',null,names.map(function(name){"
                            + "return Vue.h(McUIVue.McIcon,{name:name});}));}};"
                            + "var app=Vue.createApp(Root);app.config.throwUnhandledErrorInProduction=true;app.mount(root);",
                    "mcui-five-icons", 1, null);
        }

        assertEquals(5, document.querySelectorAll(".mc-icon").size());
        Element keyIcon = document.querySelector(".mc-icon--key");
        assertNotNull(keyIcon, "mc-key-a was not resolved from the bundled registry");
        Element embeddedImage = keyIcon.querySelector("svg image");
        assertNotNull(embeddedImage, "mc-key-a lost its embedded PNG image node");
        assertTrue(embeddedImage.getAttribute("href").startsWith("data:image/png;base64,"));
    }

    @Test
    void bundledShowcaseMountsTheRealComponentCatalog() throws Exception {
        Element.register(TextArea.TAG_NAME, (owner, tag) -> new TextArea(owner));
        Element.register(Svg.TAG_NAME, (owner, tag) -> new Svg(owner));
        Document document = TestDocumentFactory.createDocument();
        try (InputStream font = McUiComponentCompatibilityTest.class.getClassLoader()
                .getResourceAsStream(THEME + "fonts/noto-sans-bold.ttf")) {
            assertNotNull(font, "fonts/noto-sans-bold.ttf");
            assertTrue(Font.registerFont("NotoSans Bold", font));
        }
        Element root = document.createElement("div");
        root.setAttribute("id", "showcase-root");
        document.body.setAttribute("class", "ore-theme");
        document.body.setAttribute("style", "width:2048px;height:1152px;");
        Map<String, Map<String, CSS.Declaration>> cache = new LinkedHashMap<>();
        CSS.readCSS(readTheme("ore.css"), cache, "ore/ore.css");
        CSS.readCSS(readTheme("ore-components.css"), cache, "ore/ore-components.css");
        CSS.readCSS(readTheme("mcui.css"), cache, "ore/mcui.css");
        CSS.readCSS(readTheme("overview.css"), cache, "ore/overview.css");
        document.CSSCache.putAll(cache);
        document.rebuildSelectorIndex();
        document.body.appendChild(root);
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);

        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, read("vue.aui.js"), "vue.aui.js", 1, null);
            context.evaluateString(scope, read("mcui-oreui.aui.js"), "mcui-oreui.aui.js", 1, null);
            context.evaluateString(scope, read("showcase.aui.js"), "showcase.aui.js", 1, null);
        }
        document.flushPendingStyleUpdates();
        document.documentElement.getRenderer().invalidateLayoutSubtree();

        assertEquals("32", document.body.getAttribute("data-mcui-components"));
        assertEquals(32, root.querySelectorAll("[data-component]").size(),
                "the key example must render every retained component");
        java.util.List<Element> localLinks = root.querySelectorAll(".ore-component-link");
        assertEquals(32, localLinks.size());
        assertTrue(localLinks.stream().allMatch(link -> {
            String href = link.getAttribute("href");
            return href != null && href.startsWith("#mc-");
        }), "every example title must stay within the single-page showcase");
        assertNull(root.querySelector("[data-component=mc-skin-viewer]"));
        Element cardDemo = root.querySelector("[data-component=mc-card]");
        assertNotNull(cardDemo);
        assertNull(cardDemo.querySelector(".link_title"),
                "The fixed overview uses its published text/description example verbatim");
        assertEquals("了解更多信息获取帮助",
                cardDemo.querySelector(".mc-demo").getTextContent().replaceAll("\\s+", ""));
        Element firstCard = cardDemo.querySelector("link-block");
        assertNotNull(firstCard);
        assertEquals("#about", firstCard.getAttribute("href"));
        assertEquals("link", firstCard.getAttribute("role"));
        assertEquals("0", firstCard.getAttribute("tabindex"));
        Element panelDemo = root.querySelector("[data-component=mc-panel] .mc-demo");
        Element panelSurface = panelDemo == null ? null : panelDemo.querySelector(".mc-panel");
        assertNotNull(panelSurface, "Panel surface did not render");
        document.tickFrame();
        assertEquals(124.0, panelSurface.getBoundingClientRect().width, 0.01,
                "Panel demo must retain the fixed-upstream fit-content width after frame commit");
        Element textFieldDemo = root.querySelector("[data-component=mc-text-field] .mc-demo");
        Element textFieldSurface = textFieldDemo == null ? null : textFieldDemo.querySelector("text-field");
        assertNotNull(textFieldSurface, "TextField surface did not render");
        assertEquals(96.0, textFieldDemo.getBoundingClientRect().height, 0.001,
                "TextField demo must keep the fixed overview cross-size");
        Element textFieldInput = textFieldSurface.querySelector("textarea");
        assertNotNull(textFieldInput);
        assertEquals(51.0, textFieldSurface.getBoundingClientRect().height, 0.001,
                "TextField host must include the browser inline control baseline box; textarea="
                        + textFieldInput.getBoundingClientRect().height
                        + " scrollHeight=" + textFieldInput.getScrollHeight()
                        + " inlineHeight=" + textFieldInput.getInlineStylePropertyValue("height"));
        assertEquals(45.0, textFieldInput.getBoundingClientRect().height, 0.001,
                "TextArea must use the browser autoResize height");
        Object scrollHeight = context.evaluateString(scope,
                "document.querySelector('.input').scrollHeight", "textarea-scroll-height", 1, null);
        assertEquals(45.0, ((Number) scrollHeight).doubleValue(), 0.001,
                "TextArea scrollHeight must be synchronous and include padding");
        Element disabledDropdownArrow = root.querySelector(
                "[data-component=mc-dropdown] .disabled_dropdown_arrow");
        assertNotNull(disabledDropdownArrow, "disabled Dropdown arrow did not render");
        assertEquals("invert(100%) grayscale(100%) brightness(50%)",
                disabledDropdownArrow.getComputedStyle().filter,
                "disabled Dropdown arrow must receive the authored filter chain");
        assertTrue(Drawer.createPaintList(document.body).stream().anyMatch(
                        node -> node instanceof RenderNode.FilterPushNode push
                                && push.target() == disabledDropdownArrow),
                "disabled Dropdown arrow must render inside its filter layer");
        Element layoutDemo = root.querySelector("[data-component=mc-layout] .mc-layout-demo");
        Element layoutAppbar = layoutDemo == null ? null : layoutDemo.querySelector(".mc-appbar");
        Element layoutScroll = layoutDemo == null ? null : layoutDemo.querySelector("scroll-view");
        Element layoutPanel = layoutDemo == null ? null : layoutDemo.querySelector(".mc-panel");
        assertNotNull(layoutAppbar);
        assertNotNull(layoutScroll);
        assertNotNull(layoutPanel);
        assertEquals(36.234375, layoutAppbar.getBoundingClientRect().height, 0.01,
                "the Appbar must use the content-box scaled flex-shrink factor");
        assertEquals(319.765625, layoutScroll.getBoundingClientRect().height, 0.01,
                "the remaining Layout scroll surface must receive the flex main size");
        assertTrue(layoutPanel.getBoundingClientRect().width <= layoutScroll.getBoundingClientRect().width,
                "the implicit auto Grid column must not overflow the Layout scroll surface");
        assertNotNull(root.querySelector("[data-component=mc-appbar-icon]"));
        assertNotNull(root.querySelector("[data-component=mc-tcode]"));
        assertNotNull(root.querySelector("[data-component=mc-formatted-text]"));
        java.util.List<Element> checkboxImages = root.querySelectorAll("[data-component=mc-checkbox] .custom-checkbox img");
        assertEquals(3, checkboxImages.size());
        assertEquals(16.0, checkboxImages.get(1).getBoundingClientRect().width, 0.01);
        assertEquals(27.0, checkboxImages.get(1).getBoundingClientRect().height, 0.01);
        assertEquals(16.0, checkboxImages.get(2).getBoundingClientRect().width, 0.01);
        assertEquals(27.0, checkboxImages.get(2).getBoundingClientRect().height, 0.01);
        java.util.List<Element> switchImages = root.querySelectorAll("[data-component=mc-switch] .switch_style img");
        assertEquals(6, switchImages.size());
        for (Element image : switchImages) {
            assertEquals(16.0, image.getBoundingClientRect().width, 0.01);
            assertEquals(16.0, image.getBoundingClientRect().height, 0.01);
        }
        java.util.List<Element> switchSurfaces = root.querySelectorAll("[data-component=mc-switch] .switch");
        java.util.List<Element> switchSliders = root.querySelectorAll("[data-component=mc-switch] .switch_slider");
        assertEquals(3, switchSurfaces.size());
        assertEquals(3, switchSliders.size());
        for (int index = 0; index < switchSurfaces.size(); index++) {
            Element.DOMRect surfaceRect = switchSurfaces.get(index).getBoundingClientRect();
            Element.DOMRect sliderRect = switchSliders.get(index).getBoundingClientRect();
            assertEquals(52.0, surfaceRect.width, 0.01);
            assertEquals(24.0, surfaceRect.height, 0.01);
            assertEquals(28.0, sliderRect.width, 0.01);
            assertEquals(28.0, sliderRect.height, 0.01);
            assertEquals(index == 1 ? 0.0 : 24.0, sliderRect.x - surfaceRect.x, 0.01,
                    "Switch thumb escaped its authored border-box on/off x offset at index " + index);
            assertEquals(-4.0, sliderRect.y - surfaceRect.y, 0.01,
                    "Switch thumb escaped its authored y offset at index " + index);
        }
        java.util.List<Element> iconDemoItems = root.querySelectorAll("[data-component=mc-icon] .mc-demo > .mc-icon");
        assertEquals(8, iconDemoItems.size());
        assertEquals(24.0, iconDemoItems.get(0).getBoundingClientRect().width, 0.01);
        assertEquals(16.0, iconDemoItems.get(1).getBoundingClientRect().width, 0.01);
        assertEquals(12.0, iconDemoItems.get(1).getBoundingClientRect().height, 0.01);
        for (int index = 2; index < iconDemoItems.size(); index++) {
            assertEquals(24.0, iconDemoItems.get(index).getBoundingClientRect().width, 0.01);
            assertEquals(24.0, iconDemoItems.get(index).getBoundingClientRect().height, 0.01);
        }
        Element sliderValue = root.querySelector("[data-component=mc-slider] .slider_tooltip");
        assertNotNull(sliderValue);
        assertEquals("50.00", sliderValue.getTextContent(),
                "DOM text conversion must preserve the Slider's formatted two-decimal string");
        assertTrue(root.getTextContent().length() > 100, "showcase did not create a component tree");
        Element modal = document.querySelector("modal_area");
        assertNotNull(modal);
        assertTrue(modal.getBoundingClientRect().width <= 604.1,
                "fixed modal ignored its upstream max-width: " + modal.getBoundingClientRect().width);
        Element modalSurface = modal.querySelector("modal");
        assertNotNull(modalSurface);
        assertTrue(modalSurface.getBoundingClientRect().width <= 604.1,
                "modal surface escaped its fixed containing block: " + modalSurface.getBoundingClientRect().width);
        Element appbarPath = document.querySelector(".mc-appbar-icon path");
        assertNotNull(appbarPath);
        assertEquals("currentColor", appbarPath.getComputedStyle().fill);
        assertEquals("#3C3C3C", appbarPath.getComputedStyle().color);
        Element textArea = document.querySelector(".input");
        assertNotNull(textArea);
        assertEquals("", textArea.value);
        assertEquals("", textArea.getTextContent());
    }

    @Test
    void detachedBrowserImageDecodesPercentEncodedSvgDataUrl() {
        BrowserImage image = new BrowserImage();
        image.setSrc("data:image/svg+xml,%3csvg%20xmlns='http://www.w3.org/2000/svg'%20"
                + "viewBox='0%200%2016%2016'%3e%3cpath%20d='M2%204h12v2H2zM4%206h8v2H4z"
                + "M6%208h4v2H6zM7%2010h2v2H7z'%20fill='%231E1E1F'/%3e%3c/svg%3e");

        assertEquals(16, image.getNaturalWidth());
        assertEquals(16, image.getNaturalHeight());
    }

    @Test
    void pixelIconCompletesTheBlobImageCanvasPipeline() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Element root = document.createElement("div");
        document.body.appendChild(root);
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);
        CountDownLatch completed = new CountDownLatch(1);
        ScriptableObject.putProperty(scope, "__auiPixelDone",
                RhinoTestSupport.wrap(context, scope, completed), context);

        try (Document.ContextScope ignored = Document.withContext(document)) {
            context.evaluateString(scope, read("vue.aui.js"), "vue.aui.js", 1, null);
            context.evaluateString(scope, read("mcui-oreui.aui.js"), "mcui-oreui.aui.js", 1, null);
            context.evaluateString(scope,
                    "var app=Vue.createApp({render:function(){return Vue.h(McUIVue.McIcon,{"
                            + "path:'M2 2h20v20H2z',viewBox:'0 0 24 24',pixelSize:24,color:'#35d04f'});}});"
                            + "app.config.throwUnhandledErrorInProduction=true;app.mount(__auiTestDocument.body.firstChild);"
                            + "(function waitForPixel(){var image=__auiTestDocument.body.firstChild.querySelector('img');"
                            + "if(image&&image.getAttribute('src')){__auiPixelDone.countDown();return;}"
                            + "setTimeout(waitForPixel,10);}());",
                    "mcui-pixel-icon", 1, null);
        }

        assertTrue(completed.await(10, TimeUnit.SECONDS), "pixel icon callback did not complete");
        Element image = root.getElementsByTagName("img").stream().findFirst().orElse(null);
        assertNotNull(image);
        assertTrue(image.getAttribute("src").startsWith("data:image/png;base64,"),
                () -> "unexpected pixel icon src: " + image.getAttribute("src"));
        DataUri.Decoded decoded = DataUri.decode(image.getAttribute("src"));
        var raster = ImageIO.read(new ByteArrayInputStream(decoded.bytes()));
        assertNotNull(raster);
        int center = raster.getRGB(raster.getWidth() / 2, raster.getHeight() / 2) & 0x00FFFFFF;
        assertEquals(0x35D04F, center, "CSS fill: currentColor did not reach the SVG rasterizer");
    }

    private static ScriptableObject browserScope(Context context, Document document) throws Exception {
        ScriptableObject scope = context.initStandardObjects();
        AuiServices.setScript(new RhinoTestScriptService(context, scope));
        ScriptableObject.putProperty(scope, "__auiTestDocument",
                RhinoTestSupport.wrap(context, scope, document), context);
        ScriptableObject.putProperty(scope, "__auiTestWindow",
                RhinoTestSupport.wrap(context, scope, Window.window), context);
        String bootstrap = Loader.readGlobalJS()
                .replace("let document = ApricityUI.getDocumentByUUID(\"__AUI_DOCUMENT_UUID__\");",
                        "let document = __auiTestDocument;")
                .replace("let window = ApricityUI.getWindow();", "let window = __auiTestWindow;");
        context.evaluateString(scope, bootstrap, "global.js", 1, null);
        return scope;
    }

    private static String read(String name) throws Exception {
        try (InputStream stream = McUiComponentCompatibilityTest.class.getClassLoader()
                .getResourceAsStream(RUNTIME + name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }


    private static String readTheme(String name) throws Exception {
        try (InputStream stream = McUiComponentCompatibilityTest.class.getClassLoader()
                .getResourceAsStream(THEME + name)) {
            assertNotNull(stream, name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Object evaluateScriptTask(Context context, ScriptableObject scope,
                                             String script, String source) {
        Window.window.beginScriptTask();
        try {
            return context.evaluateString(scope, script, source, 1, null);
        } finally {
            Window.window.endScriptTask();
        }
    }

    private record RhinoTestScriptService(Context context, Scriptable scope) implements AuiScriptService {
        @Override
        public void eval(String code, Event event, String source) {
        }

        @Override
        public void reload() {
        }

        @Override
        public Consumer<Object> createCallback(Object callback) {
            if (!(callback instanceof Callable callable)) return null;
            return new EcmaEventListener(callable, scope, context);
        }

        @Override
        public Object wrapHostObject(Object value) {
            return RhinoTestSupport.wrap(context, scope, value);
        }
    }
}
