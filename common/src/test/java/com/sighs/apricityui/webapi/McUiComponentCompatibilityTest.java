package com.sighs.apricityui.webapi;

import com.sighs.apricityui.canvas.BrowserImage;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.element.Img;
import com.sighs.apricityui.element.Input;
import com.sighs.apricityui.element.Option;
import com.sighs.apricityui.element.Path;
import com.sighs.apricityui.element.Select;
import com.sighs.apricityui.element.Svg;
import com.sighs.apricityui.element.TextArea;
import com.sighs.apricityui.event.Event;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.parser.CSS;
import com.sighs.apricityui.script.ecmascript.EcmaEventListener;
import com.sighs.apricityui.spi.AuiScriptService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.style.Background;
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

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Execution(ExecutionMode.SAME_THREAD)
class McUiComponentCompatibilityTest {
    private static final String RESOURCE_ROOT =
            "assets/apricityui/apricity/apricityui/runtime/";
    private AuiScriptService previousScriptService;

    @BeforeEach
    void captureScriptService() {
        previousScriptService = AuiServices.script();
        registerBrowserElements();
    }

    @AfterEach
    void restoreScriptService() {
        AuiServices.setScript(previousScriptService);
    }

    @Test
    void latestPluginRegistersSixtyEightComponentsAndStylesCheckedControl() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Map<String, Map<String, CSS.Declaration>> css = new LinkedHashMap<>();
        CSS.readCSS(read("mcui/components.css"), css, "runtime/mcui/components.css");
        document.CSSCache.putAll(css);
        document.rebuildSelectorIndex();
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);

        Object result;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            load(context, scope, "mcui/mcui-oreui.aui.js", "mcui/mcui-icons-normal.aui.js");
            result = context.evaluateString(scope, """
                    var root = document.createElement('div'); document.body.appendChild(root);
                    var Root = {render:function(){return Vue.h(McUIVue.McApp,null,{
                      default:function(){return Vue.h(McUIVue.McCheckbox,{modelValue:true,label:'Checked'});}
                    });}};
                    var app = Vue.createApp(Root);
                    app.config.throwUnhandledErrorInProduction = true;
                    app.use(McUIVue.createMcUI({sounds:{enabled:false},
                      icons:{sets:{mc:McUINormalIcons.mcNormalIconSet}}}));
                    app.mount(root);
                    var names = Object.keys(McUIVue).filter(function(name){return /^Mc[A-Z]/.test(name);});
                    names.length + '|' + (typeof McUIVue.McSkinViewer) + '|'
                      + !!root.querySelector('.mc-app') + '|'
                      + !!root.querySelector('.mc-checkbox__mark img');
                    """, "mcui2-checkbox", 1, null);
        }
        try {
            assertEquals("68|undefined|true|true", result);
            Element control = document.querySelector(".mc-checkbox__control");
            Element mark = document.querySelector(".mc-checkbox__mark");
            assertNotNull(control);
            assertNotNull(mark);
            assertEquals("#3c8527", Background.of(control).color);
            assertEquals("1", mark.getComputedStyle().opacity);
            document.querySelector(".mc-checkbox").setHover(true);
            document.flushPendingStyleUpdates();
            String hover = Background.of(control).color;
            assertEquals(0xFF316D20, com.sighs.apricityui.parser.Color.parse(hover), hover);
        } finally {
            try (Document.ContextScope ignored = Document.withContext(document)) {
                context.evaluateString(scope, "app.unmount();", "mcui2-unmount", 1, null);
            }
        }
    }

    @Test
    void latestUpstreamGalleryMountsAllRetainedComponents() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        document.body.setAttribute("style", "width:1280px;height:720px");
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);

        Object result;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            load(context, scope, "mcui/mcui-oreui.aui.js", "mcui/mcui-icons-normal.aui.js",
                    "mcui/gallery.aui.js");
            result = context.evaluateString(scope, """
                    var root=document.createElement('div');document.body.appendChild(root);
                    var app=Vue.createApp(McUIVisualGallery.default);
                    var errors=[];
                    app.config.errorHandler=function(error,vm,info){
                      var options=vm&&vm.$options;
                      errors.push((options&&(options.name||options.__name)||'unknown')+':'+info+':'+error);
                    };
                    app.use(McUIVue.createMcUI({sounds:{enabled:false},
                      icons:{sets:{mc:McUINormalIcons.mcNormalIconSet}}}));
                    app.mount(root);
                    var count=document.querySelectorAll('[data-gallery-component]').length;
                    var virtual=document.querySelector('.gallery-virtual-row');
                    var virtualText=virtual ? virtual.textContent : '<missing>';
                    var mapped=Array.from({length:3},function(_,i){return 'Chunk '+(i+1)});
                    var panels=document.querySelectorAll('.mc-tabs__panel');
                    var tabsState=panels.length === 2
                      ? panels[0].hasAttribute('hidden')+'/'+panels[1].hasAttribute('hidden')
                        +'/'+panels[0].getAttribute('hidden')
                        +'/'+panels[0].textContent : '<missing>';
                    app.unmount();
                    count+'|'+errors.slice(0,3).join(';')+'|'+virtualText+'|'+mapped.join(',')+'|'+tabsState;
                    """, "mcui2-gallery", 1, null);
        }
        assertEquals("68||Chunk 1|Chunk 1,Chunk 2,Chunk 3|false/true/null/Selected: worlds", result);
    }

    @Test
    void nativeButtonCheckboxAndSwitchEventsReachVue() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);
        CountDownLatch updated = new CountDownLatch(1);
        ScriptableObject.putProperty(scope, "__auiUpdated",
                RhinoTestSupport.wrap(context, scope, updated), context);

        Object result;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            load(context, scope, "mcui/mcui-oreui.aui.js");
            result = context.evaluateString(scope, """
                    var clicks=0,checked=Vue.ref(false),switched=Vue.ref(false);
                    var root=document.createElement('div');document.body.appendChild(root);
                    var Root={render:function(){return Vue.h(McUIVue.McApp,null,{
                      default:function(){return Vue.h('div',null,[
                        Vue.h(McUIVue.McButton,{onClick:function(){clicks++;}},{default:function(){return 'Click';}}),
                        Vue.h(McUIVue.McCheckbox,{modelValue:checked.value,
                          'onUpdate:modelValue':function(v){checked.value=v;}}),
                        Vue.h(McUIVue.McSwitch,{modelValue:switched.value,
                          'onUpdate:modelValue':function(v){switched.value=v;}})
                      ]);}
                    });}};
                    var app=Vue.createApp(Root);
                    app.config.throwUnhandledErrorInProduction=true;
                    app.use(McUIVue.createMcUI({sounds:{enabled:false}}));app.mount(root);
                    root.querySelector('.mc-button').click();
                    var checkbox=root.querySelector('.mc-checkbox__input');
                    checkbox.focus({preventScroll:true});checkbox.click();
                    var hits=0,prevented=true;
                    function passive(event){hits++;event.preventDefault();prevented=event.defaultPrevented;}
                    checkbox.addEventListener('mcui-options',passive,{passive:true});
                    var event=window.createEvent('mcui-options',false);event.cancelable=true;
                    checkbox.dispatchEvent(event);
                    checkbox.removeEventListener('mcui-options',passive,{passive:true});
                    checkbox.dispatchEvent(window.createEvent('mcui-options',false));
                    root.querySelector('.mc-switch__input').click();
                    var answer=clicks+'|'+checked.value+'|'+switched.value+'|'
                      +(document.activeElement===checkbox)+'|'+hits+'|'+prevented;
                    Vue.nextTick(function(){__auiUpdated.countDown();});answer;
                    """, "mcui2-control-events", 1, null);
        }
        assertEquals("1|true|true|true|1|false", result);
        assertTrue(updated.await(2, TimeUnit.SECONDS));
        assertTrue(document.querySelector(".mc-switch__input").isChecked());
    }

    @Test
    void numberTextFieldFiltersInvalidCharacters() throws Exception {
        Document document = TestDocumentFactory.createDocument();
        Context context = RhinoTestSupport.enterContext();
        ScriptableObject scope = browserScope(context, document);

        Object result;
        try (Document.ContextScope ignored = Document.withContext(document)) {
            load(context, scope, "mcui/mcui-oreui.aui.js");
            result = context.evaluateString(scope, """
                    var value=Vue.ref(''),invalid=0;
                    var root=document.createElement('div');document.body.appendChild(root);
                    var Root={render:function(){return Vue.h(McUIVue.McApp,null,{
                      default:function(){return Vue.h(McUIVue.McTextField,{
                        modelValue:value.value,filter:'number',
                        'onUpdate:modelValue':function(v){value.value=v;},
                        onInvalidInput:function(){invalid++;}
                      });}
                    });}};
                    var app=Vue.createApp(Root);
                    app.config.throwUnhandledErrorInProduction=true;
                    app.use(McUIVue.createMcUI({sounds:{enabled:false}}));app.mount(root);
                    var input=root.querySelector('input.mc-input');
                    input.value='12a3';input.dispatchEvent(window.createEvent('input',true));
                    value.value+'|'+invalid+'|'+input.value;
                    """, "mcui2-number-filter", 1, null);
        }
        assertEquals("123|1|123", result);
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
            load(context, scope, "mcui/mcui-oreui.aui.js");
            context.evaluateString(scope, """
                    var Root={render:function(){return Vue.h(McUIVue.McIcon,{
                      path:'M2 2h20v20H2z',viewBox:'0 0 24 24',pixelSize:24,color:'#35d04f'});}};
                    var app=Vue.createApp(Root);
                    app.config.throwUnhandledErrorInProduction=true;
                    app.use(McUIVue.createMcUI({sounds:{enabled:false}}));app.mount(__auiTestDocument.body.firstChild);
                    (function waitForPixel(){
                      var image=__auiTestDocument.body.firstChild.querySelector('img');
                      if(image&&image.getAttribute('src')){__auiPixelDone.countDown();return;}
                      setTimeout(waitForPixel,10);
                    }());
                    """, "mcui2-pixel-icon", 1, null);
        }
        assertTrue(completed.await(10, TimeUnit.SECONDS), "pixel icon callback did not complete");
        Element image = root.getElementsByTagName("img").stream().findFirst().orElse(null);
        assertNotNull(image);
        assertTrue(image.getAttribute("src").startsWith("data:image/png;base64,"));
        DataUri.Decoded decoded = DataUri.decode(image.getAttribute("src"));
        var raster = ImageIO.read(new ByteArrayInputStream(decoded.bytes()));
        assertNotNull(raster);
        assertEquals(0x35D04F,
                raster.getRGB(raster.getWidth() / 2, raster.getHeight() / 2) & 0x00FFFFFF);
    }

    @Test
    void detachedBrowserImageDecodesPercentEncodedSvgDataUrl() {
        BrowserImage image = new BrowserImage();
        image.setSrc("data:image/svg+xml,%3csvg%20xmlns='http://www.w3.org/2000/svg'%20"
                + "viewBox='0%200%2016%2016'%3e%3cpath%20d='M2%204h12v2H2z'%20fill='%23FFFFFF'/%3e%3c/svg%3e");
        assertEquals(16, image.getNaturalWidth());
        assertEquals(16, image.getNaturalHeight());
    }

    private static void registerBrowserElements() {
        Element.register(Input.TAG_NAME, (owner, tag) -> new Input(owner));
        Element.register(TextArea.TAG_NAME, (owner, tag) -> new TextArea(owner));
        Element.register(Select.TAG_NAME, (owner, tag) -> new Select(owner));
        Element.register(Option.TAG_NAME, (owner, tag) -> new Option(owner));
        Element.register(Img.TAG_NAME, (owner, tag) -> new Img(owner));
        Element.register(Svg.TAG_NAME, (owner, tag) -> new Svg(owner));
        Element.register(Path.TAG_NAME, (owner, tag) -> new Path(owner));
        Element.register(Canvas.TAG_NAME, (owner, tag) -> new Canvas(owner));
    }

    private static void load(Context context, ScriptableObject scope, String... optional) throws Exception {
        context.evaluateString(scope, read("vue.aui.js"), "vue.aui.js", 1, null);
        for (String name : optional) {
            context.evaluateString(scope, read(name), name, 1, null);
        }
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
        try (InputStream input = McUiComponentCompatibilityTest.class.getClassLoader()
                .getResourceAsStream(RESOURCE_ROOT + name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
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
