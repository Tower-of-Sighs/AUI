package com.sighs.apricityui.client;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Render smoke test for the 26.3 migration.
 *
 * <p>Enabled with {@code -Dapricityui.clientSelfTest=true}. Scenarios are
 * {@code resourceManager} (default), {@code metaDialog} and {@code renderCompat}.
 * The latter explicitly loads an isolated 640 x 400 fixture; it is never opened
 * during normal startup. DOM checks are followed by asynchronous screenshot
 * pixel checks and PNG write. Only their completion permits PASS and exit.
 * A missing capture, failed write or 30-second readback timeout is FAIL.</p>
 *
 * <p>System properties:</p>
 * <ul>
 *   <li>{@code apricityui.clientSelfTest} — master switch.</li>
 *   <li>{@code apricityui.clientSelfTest.exitOnFinish} — stop the game afterwards.</li>
 *   <li>{@code apricityui.clientSelfTest.resultFile} — where to write PASS/FAIL.</li>
 *   <li>{@code apricityui.clientSelfTest.screenshotFile} — required PNG destination.</li>
 *   <li>{@code apricityui.clientSelfTest.scenario} — one of the three names above; unknown names fail.</li>
 *   <li>{@code apricityui.clientSelfTest.width}/{@code height}/{@code guiScale} — optional isolated window setup.</li>
 * </ul>
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = ApricityUI.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class ClientRuntimeSelfTest {
    /** Controlled pointer input avoids dependence on which desktop window owns OS focus. */
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void stableInspectPointer(net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre event) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || !isMetaDialogScenario()) return;
        if (state != State.PREPARING_DIALOG && state != State.CAPTURING_BEFORE
                && state != State.WAITING && state != State.CAPTURING_FINAL) return;
        var window = Minecraft.getInstance().getWindow();
        com.sighs.apricityui.dev.DevTools.handleInspectMouseMove(new com.sighs.apricityui.layout.Position(
                window.getScreenWidth() * .1 / window.getGuiScale(), window.getScreenHeight() * .5 / window.getGuiScale()));
    }
    private static final String ENABLE_PROPERTY = "apricityui.clientSelfTest";
    private static final String EXIT_PROPERTY = "apricityui.clientSelfTest.exitOnFinish";
    private static final String RESULT_PROPERTY = "apricityui.clientSelfTest.resultFile";
    private static final String SCREENSHOT_PROPERTY = "apricityui.clientSelfTest.screenshotFile";
    private static final String SCENARIO_PROPERTY = "apricityui.clientSelfTest.scenario";
    private static final String SCENARIO_META_DIALOG = "metaDialog";
    private static final String RESOURCE_MANAGER_PATH = "devtools/resource.html";

    private static final long START_DELAY_TICKS = 10L;
    private static final long TEMPLATE_WAIT_TICKS = 200L;
    private static final long RENDER_WARMUP_TICKS = 120L;
    private static final long DIALOG_WAIT_TICKS = 60L;

    private static final String RENDER_COMPAT_PATH = "selftest/render-compat.html";
    private static final long CAPTURE_TIMEOUT_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
    private static volatile State state = State.IDLE;
    private static long tickCounter;
    private static long openTick = -1L;
    private static String openFailure;
    private static long captureStarted;
    private static volatile CaptureResult captured;
    private static final List<String> failures = new ArrayList<>();
    private static final List<String> details = new ArrayList<>();
    private static int[] beforeDialog;
    private static int beforeWidth;
    private static int beforeHeight;

    private record CaptureResult(List<String> failures, List<String> details) { }

    private static String scenario() {
        return System.getProperty(SCENARIO_PROPERTY, "resourceManager");
    }

    private static boolean isRenderCompatScenario() {
        return "renderCompat".equals(scenario());
    }

    private static Integer originalGuiScale;

    private static void configureWindow() {
        Minecraft minecraft = Minecraft.getInstance();
        String width = System.getProperty(ENABLE_PROPERTY + ".width");
        String height = System.getProperty(ENABLE_PROPERTY + ".height");
        if (width != null || height != null) {
            if (width == null || height == null) throw new IllegalArgumentException("selftest width and height must be specified together");
            minecraft.getWindow().setWindowed(Integer.parseInt(width), Integer.parseInt(height));
        }
        String scale = System.getProperty(ENABLE_PROPERTY + ".guiScale");
        if (scale != null) {
            originalGuiScale = minecraft.options.guiScale().get();
            minecraft.options.guiScale().set(Integer.parseInt(scale));
            minecraft.resizeGui();
        }
    }

    private ClientRuntimeSelfTest() {
    }

    /**
     * Called from {@link Client#tick} on the client thread.
     */
    public static void tick() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        if (Minecraft.getInstance() == null) return;
        tickCounter++;

        try {
            if (tickCounter == 1) configureWindow();
            switch (state) {
                case IDLE -> maybeOpen();
                case OPENING_DIALOG -> maybeOpenDialog();
                case PREPARING_DIALOG -> {
                    if (tickCounter - openTick >= DIALOG_WAIT_TICKS) captureScreenshot(true);
                }
                case WAITING -> maybeAssert();
                case CAPTURING_BEFORE, CAPTURING_FINAL -> awaitCapture();
                case DONE -> { }
            }
        } catch (Throwable failure) {
            failures.add("selftest exception: " + failure);
            finish();
        }
    }

    private static boolean isMetaDialogScenario() {
        return SCENARIO_META_DIALOG.equals(System.getProperty(SCENARIO_PROPERTY));
    }

    private static void maybeOpen() throws Exception {
        if (tickCounter < START_DELAY_TICKS) return;
        if (!List.of("resourceManager", "metaDialog", "renderCompat").contains(scenario())) {
            failures.add("unknown scenario: " + scenario());
            finish();
            return;
        }
        if (HTML.getTemple(RESOURCE_MANAGER_PATH) == null
                && tickCounter < START_DELAY_TICKS + TEMPLATE_WAIT_TICKS) return;

        ResourceManager.close();
        if (isRenderCompatScenario()) {
            // Outside the normal asset/template scan: loaded only by this explicit scenario.
            try (var stream = ClientRuntimeSelfTest.class.getResourceAsStream("/aui-selftest/render-compat.html")) {
                if (stream == null) throw new IllegalStateException("renderCompat fixture missing");
                HTML.putTemple(RENDER_COMPAT_PATH, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            Document document = Document.create(RENDER_COMPAT_PATH);
            if (document == null) throw new IllegalStateException("renderCompat document missing");
            document.resetViewportZoom();
            setTestStack(document, "#cube", fixtureStack(net.minecraft.world.item.Items.DIAMOND_BLOCK, "diamond_block", 1));
            setTestStack(document, "#flat", fixtureStack(net.minecraft.world.item.Items.DIAMOND_SWORD, "diamond_sword", 1));
            setTestStack(document, "#count", fixtureStack(net.minecraft.world.item.Items.DIAMOND_SWORD, "diamond_sword", 37));
            var glint = fixtureStack(net.minecraft.world.item.Items.DIAMOND_SWORD, "diamond_sword", 1);
            glint.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            setTestStack(document, "#glint", glint);
            setTestStack(document, "#plain", fixtureStack(net.minecraft.world.item.Items.DIAMOND_SWORD, "diamond_sword", 1));
            setTestStack(document, "#clippedItem", fixtureStack(net.minecraft.world.item.Items.DIAMOND_BLOCK, "diamond_block", 1));
        } else {
            ResourceManager.open();
            if (isMetaDialogScenario()) openDevToolsOnResourceManager();
        }
        openTick = tickCounter;
        state = isMetaDialogScenario() ? State.OPENING_DIALOG : State.WAITING;
    }

    static net.minecraft.world.item.ItemStack fixtureStack(net.minecraft.world.item.Item item, String model, int count) {
        // 26.3 binds registry components only after joining a server. A direct holder with
        // explicit visual components is a real ItemStack without mutating global registries.
        var components = net.minecraft.core.component.DataComponentMap.builder()
                .set(net.minecraft.core.component.DataComponents.ITEM_MODEL, net.minecraft.resources.Identifier.withDefaultNamespace(model))
                .set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64)
                .build();
        return new net.minecraft.world.item.ItemStack(net.minecraft.core.Holder.direct(item, components), count);
    }

    private static void setTestStack(Document document, String selector, net.minecraft.world.item.ItemStack stack) {
        if (!(document.querySelector(selector) instanceof com.sighs.apricityui.element.Item item)) {
            throw new IllegalStateException("missing Item node " + selector);
        }
        item.setIngredientStack(stack);
    }

    /**
     * Opens DevTools with the resource manager as the inspected document.
     */
    private static void openDevToolsOnResourceManager() {
        Document target = latestDocument(RESOURCE_MANAGER_PATH);
        if (target == null) {
            openFailure = "resource manager document not found for devtools selection";
            return;
        }
        if (!com.sighs.apricityui.dev.DevTools.selectDocument(target)) {
            openFailure = "DevTools.selectDocument(resource manager) returned false";
            return;
        }
        ApricityUI.LOGGER.info("[AUI SelfTest] devtools opened on resource manager");
    }

    /**
     * Clicks the title-bar meta button once DevTools has rendered.
     */
    private static void maybeOpenDialog() {
        if (tickCounter - openTick < RENDER_WARMUP_TICKS) return;
        if (openFailure != null) throw new IllegalStateException(openFailure);
        Document tool = com.sighs.apricityui.dev.DevTools.getToolDocument();
        if (tool == null || tool.querySelector("#metaButton") == null) {
            throw new IllegalStateException("devtools #metaButton not found");
        }
        // Establish identical pick/hover state before both screenshots, not after baseline.
        runInspectPickTest();
        openTick = tickCounter;
        state = State.PREPARING_DIALOG;
    }

    private static void runInspectPickTest() {
        Document tool = com.sighs.apricityui.dev.DevTools.getToolDocument();
        if (tool == null) throw new IllegalStateException("devtools document missing for inspect pick");
        Element pickBtn = tool.querySelector("#pickBtn");
        if (pickBtn == null) throw new IllegalStateException("devtools #pickBtn missing");
        pickBtn.click();
        var window = Minecraft.getInstance().getWindow();
        // SDL uses window coordinates, not framebuffer pixels (different under HiDPI).
        org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(window.handle(),
                window.getScreenWidth() * 0.1f, window.getScreenHeight() * 0.5f);
    }

    private static void maybeAssert() {
        if (tickCounter - openTick < (isMetaDialogScenario() ? DIALOG_WAIT_TICKS : RENDER_WARMUP_TICKS)) return;
        if (openFailure != null) failures.add(openFailure);
        if (isRenderCompatScenario()) {
            Document document = latestDocument(RENDER_COMPAT_PATH);
            if (document == null || document.body == null || document.getPaintList().isEmpty()) {
                failures.add("renderCompat document/body/paint list missing");
            }
        } else {
            if (!ResourceManager.isOpen()) failures.add("resource manager did not remain open");
            if (isMetaDialogScenario()) validateMetaDialog(failures);
            else validateResourceManagerDocument(failures);
        }
        captureScreenshot(false);
    }

    private static void awaitCapture() {
        CaptureResult result = captured;
        if (result == null) {
            if (System.nanoTime() - captureStarted > CAPTURE_TIMEOUT_NANOS) {
                failures.add("screenshot callback timed out after 30 seconds");
                finish();
            }
            return;
        }
        failures.addAll(result.failures());
        details.addAll(result.details());
        captured = null;
        if (state == State.CAPTURING_BEFORE && failures.isEmpty()) {
            Document tool = com.sighs.apricityui.dev.DevTools.getToolDocument();
            Element button = tool == null ? null : tool.querySelector("#metaButton");
            if (button == null) throw new IllegalStateException("devtools #metaButton disappeared");
            button.click();
            openTick = tickCounter;
            state = State.WAITING;
        } else {
            finish();
        }
    }

    private static void finish() {
        state = State.DONE;
        writeResult(failures);
        if (failures.isEmpty()) ApricityUI.LOGGER.info("[AUI SelfTest] PASS scenario={}", scenario());
        else ApricityUI.LOGGER.error("[AUI SelfTest] FAIL scenario={}: {}", scenario(), String.join(" | ", failures));
        if (originalGuiScale != null) {
            Minecraft.getInstance().options.guiScale().set(originalGuiScale);
            originalGuiScale = null;
        }
        if (Boolean.getBoolean(EXIT_PROPERTY)) Minecraft.getInstance().stop();
    }

    /**
     * Structural prerequisites; the readback callback separately checks the dialog's pixel ROI.
     */
    private static void validateMetaDialog(List<String> failures) {
        Document toolDocument = com.sighs.apricityui.dev.DevTools.getToolDocument();
        if (toolDocument == null || toolDocument.body == null) {
            failures.add("devtools document/body missing at assert time");
            return;
        }
        if (toolDocument.querySelector(".dialog-overlay") == null) {
            failures.add("meta dialog overlay (.dialog-overlay) not present in devtools document");
        }
        if (toolDocument.querySelector(".dialog") == null) {
            failures.add("meta dialog window (.dialog) not present in devtools document");
        }
        // Element-pick regression check: with pick mode enabled and the cursor
        // parked over the target, the box-model highlight must be showing.
        Element highlight = toolDocument.querySelector("#inspectHighlight");
        if (highlight == null) {
            failures.add("devtools #inspectHighlight missing");
        } else {
            String cls = highlight.getAttribute("class");
            if (cls == null || !cls.contains("show")) {
                failures.add("inspect highlight (#inspectHighlight) not shown (class='" + cls + "')");
            }
        }
    }

    private static void validateResourceManagerDocument(List<String> failures) {
        Document document = latestDocument(RESOURCE_MANAGER_PATH);
        if (document == null || document.body == null) {
            failures.add("resource manager document/body was not created");
            return;
        }
        if (document.getPaintList().isEmpty()) {
            failures.add("resource manager document has an empty paint list");
        }

        requireElement(document, "#navPath", failures);
        requireElement(document, "#treeContainer", failures);
        requireElement(document, "#fileGrid", failures);
        requireElement(document, "#detailPanel", failures);

        Element main = document.querySelector(".main");
        if (main == null) {
            failures.add("resource manager main layout node missing");
        } else {
            Element.DOMRect rect = main.getBoundingClientRect();
            if (rect.width <= 0.0 || rect.height <= 0.0) {
                failures.add("resource manager main layout is empty: " + rect.width + "x" + rect.height);
            }
        }
    }

    /** Readback is fenced/asynchronous; only awaitCapture may publish the final result. */
    private static void captureScreenshot(boolean before) {
        String rawPath = System.getProperty(SCREENSHOT_PROPERTY);
        if (rawPath == null || rawPath.isBlank()) throw new IllegalStateException("screenshotFile property missing");
        if (before) rawPath = rawPath.endsWith(".png")
                ? rawPath.substring(0, rawPath.length() - 4) + "-predialog.png" : rawPath + "-predialog.png";
        Path target = Path.of(rawPath).toAbsolutePath().normalize();
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
        if (mainTarget == null) throw new IllegalStateException("main render target missing");
        Document document = isRenderCompatScenario() ? latestDocument(RENDER_COMPAT_PATH)
                : isMetaDialogScenario() ? com.sighs.apricityui.dev.DevTools.getToolDocument() : latestDocument(RESOURCE_MANAGER_PATH);
        if (document == null) throw new IllegalStateException("capture document missing");
        var window = minecraft.getWindow();
        double sx = document.getViewportScaleX() * window.getGuiScale() * mainTarget.width / window.getScreenWidth();
        double sy = document.getViewportScaleY() * window.getGuiScale() * mainTarget.height / window.getScreenHeight();
        String surface = "window=" + window.getScreenWidth() + "x" + window.getScreenHeight()
                + " framebuffer=" + mainTarget.width + "x" + mainTarget.height + " guiScale=" + window.getGuiScale()
                + " gui=" + window.getGuiScaledWidth() + "x" + window.getGuiScaledHeight();
        details.add(surface);
        Element node = document.querySelector(isMetaDialogScenario() ? (before ? ".side-panel" : ".dialog") : ".main");
        Element.DOMRect rect = node == null ? null : node.getBoundingClientRect();
        State expected = before ? State.CAPTURING_BEFORE : State.CAPTURING_FINAL;
        captured = null;
        captureStarted = System.nanoTime();
        state = expected;
        Screenshot.takeScreenshot(mainTarget, image -> {
            if (state != expected) { image.close(); return; } // A timed-out request cannot overwrite FAIL.
            List<String> captureFailures = new ArrayList<>();
            List<String> captureDetails = new ArrayList<>();
            try (image) {
                int width = image.getWidth(), height = image.getHeight();
                int[] pixels = image.getPixels(); // NativeImage exposes ARGB, not its internal ABGR storage.
                captureDetails.add("screenshot=" + target + " dimensions=" + width + "x" + height + " CSS_to_framebuffer=" + sx + "," + sy);
                RenderCompatPixels.Pixels view = new RenderCompatPixels.Pixels() {
                    public int width() { return width; }
                    public int height() { return height; }
                    public int rgb(int x, int y) { return pixels[y * width + x]; }
                };
                if (before) {
                    beforeDialog = pixels;
                    beforeWidth = width;
                    beforeHeight = height;
                    validateDocumentPixels(view, rect, sx, sy, false, captureFailures, captureDetails);
                } else if (isRenderCompatScenario()) {
                    new RenderCompatPixels(view, sx, sy, captureFailures, captureDetails).check();
                } else {
                    validateDocumentPixels(view, rect, sx, sy, isMetaDialogScenario(), captureFailures, captureDetails);
                }
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                image.writeToFile(target);
                if (!Files.isRegularFile(target) || Files.size(target) == 0) throw new IllegalStateException("screenshot file missing/empty");
                captureDetails.add("screenshot write complete");
            } catch (Throwable failure) {
                captureFailures.add("screenshot readback/assert/write failed: " + failure);
            }
            if (System.nanoTime() - captureStarted > CAPTURE_TIMEOUT_NANOS) {
                captureFailures.add("screenshot callback/write exceeded 30-second deadline");
            }
            // Volatile publication happens after pixel validation, PNG write and image close.
            if (state == expected) captured = new CaptureResult(captureFailures, captureDetails);
        });
    }

    private static void validateDocumentPixels(RenderCompatPixels.Pixels image, Element.DOMRect rect,
            double sx, double sy, boolean compareBefore, List<String> errors, List<String> measurements) {
        if (rect == null || rect.width <= 0 || rect.height <= 0) {
            errors.add("document pixel ROI missing/empty");
            return;
        }
        int x0 = (int) Math.ceil((rect.x + 4) * sx), y0 = (int) Math.ceil((rect.y + 4) * sy);
        int x1 = (int) Math.floor((rect.x + rect.width - 4) * sx), y1 = (int) Math.floor((rect.y + rect.height - 4) * sy);
        if (x0 < 0 || y0 < 0 || x1 > image.width() || y1 > image.height() || x1 <= x0 || y1 <= y0) {
            errors.add("document pixel ROI out of bounds/empty");
            return;
        }
        int changed = 0, ink = 0, total = 0;
        int reference = image.rgb(x0, y0);
        if (compareBefore && (beforeDialog == null || beforeWidth != image.width() || beforeHeight != image.height())) {
            errors.add("predialog pixels missing or screenshot size changed");
            return;
        }
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int rgb = image.rgb(x, y);
            total++;
            if (RenderCompatPixels.distance(rgb, reference) > 24) ink++;
            if (compareBefore && RenderCompatPixels.distance(rgb, beforeDialog[y * beforeWidth + x]) > 24) changed++;
        }
        String roi = "framebuffer_ROI=" + x0 + "," + y0 + "," + (x1 - x0) + "," + (y1 - y0);
        measurements.add("document nonuniform pixels " + roi + " fraction=" + (double) ink / total);
        if ((double) ink / total < .01) errors.add("document screenshot ROI is blank/uniform: " + roi);
        if (compareBefore) {
            measurements.add("dialog changed pixels " + roi + " fraction=" + (double) changed / total);
            RenderCompatPixels.Pixels baseline = new RenderCompatPixels.Pixels() {
                public int width() { return beforeWidth; }
                public int height() { return beforeHeight; }
                public int rgb(int x, int y) { return beforeDialog[y * beforeWidth + x]; }
            };
            new RenderCompatPixels(image, sx, sy, errors, measurements)
                    .dialog((int) Math.round(rect.x), (int) Math.round(rect.y), (int) Math.round(rect.width), baseline);
        }
    }

    private static void requireElement(Document document, String selector, List<String> failures) {
        if (document.querySelector(selector) == null) failures.add("missing render node " + selector);
    }

    private static Document latestDocument(String path) {
        List<Document> documents = Document.get(path);
        for (int index = documents.size() - 1; index >= 0; index--) {
            Document document = documents.get(index);
            if (document != null && !document.isDisposed()) return document;
        }
        return null;
    }

    private static void writeResult(List<String> failures) {
        String rawPath = System.getProperty(RESULT_PROPERTY);
        if (rawPath == null || rawPath.isBlank()) {
            failures.add("resultFile property missing");
            return;
        }
        try {
            Path result = Path.of(rawPath).toAbsolutePath().normalize();
            Path parent = result.getParent();
            if (parent != null) Files.createDirectories(parent);
            StringBuilder output = new StringBuilder(failures.isEmpty() ? "PASS\n" : "FAIL\n");
            output.append("scenario=").append(scenario()).append('\n');
            for (String failure : failures) output.append(failure).append('\n');
            for (String detail : details) output.append(detail).append('\n');
            Files.writeString(result, output.toString(), StandardCharsets.UTF_8);
        } catch (Exception writeFailure) {
            failures.add("could not write result file: " + writeFailure);
            ApricityUI.LOGGER.error("[AUI SelfTest] could not write result file", writeFailure);
        }
    }

    private enum State {
        IDLE,
        OPENING_DIALOG,
        PREPARING_DIALOG,
        WAITING,
        CAPTURING_BEFORE,
        CAPTURING_FINAL,
        DONE
    }
}
