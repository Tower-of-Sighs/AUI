package com.sighs.apricityui.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.registry.Keybindings;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.util.LocalStorage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Render smoke test for the 26.1 migration.
 *
 * <p>Enabled with {@code -Dapricityui.clientSelfTest=true}. First it drives the
 * <em>real</em> shortcut path — it binds the resource-manager key mapping to a
 * free key, checks that the loader key service reports the new binding, then
 * feeds a press through the same entry point the Fabric keyboard mixin uses —
 * so a regression that makes the panel unopenable in game (a key service that
 * only ever reports the default binding, a shortcut that is never dispatched)
 * fails the test instead of silently passing.</p>
 *
 * <p>It then waits for the built-in resource manager (a persistent overlay
 * document — exactly the path the new PIP overlay renderer exercises), lets it
 * render for a few seconds, validates the document structure, captures a
 * screenshot of the main render target and writes a PASS/FAIL result file.</p>
 *
 * <p>System properties:</p>
 * <ul>
 *   <li>{@code apricityui.clientSelfTest} — master switch.</li>
 *   <li>{@code apricityui.clientSelfTest.exitOnFinish} — stop the game afterwards.</li>
 *   <li>{@code apricityui.clientSelfTest.resultFile} — where to write PASS/FAIL.</li>
 *   <li>{@code apricityui.clientSelfTest.screenshotFile} — where to write the PNG.</li>
 *   <li>{@code apricityui.clientSelfTest.scenario} — {@code metaDialog} opens
 *       DevTools on the resource manager and clicks the title-bar meta button.</li>
 * </ul>
 */
public final class ClientRuntimeSelfTest {
    private static final String ENABLE_PROPERTY = "apricityui.clientSelfTest";
    private static final String EXIT_PROPERTY = "apricityui.clientSelfTest.exitOnFinish";
    private static final String RESULT_PROPERTY = "apricityui.clientSelfTest.resultFile";
    private static final String SCREENSHOT_PROPERTY = "apricityui.clientSelfTest.screenshotFile";
    private static final String SCENARIO_PROPERTY = "apricityui.clientSelfTest.scenario";
    private static final String SCENARIO_META_DIALOG = "metaDialog";
    private static final String RESOURCE_MANAGER_PATH = "devtools/resource.html";

    /** Free key the self test binds the resource-manager shortcut to. */
    private static final int SELFTEST_BOUND_KEY = org.lwjgl.glfw.GLFW.GLFW_KEY_F9;

    private static final long START_DELAY_TICKS = 10L;
    private static final long TEMPLATE_WAIT_TICKS = 200L;
    private static final long RENDER_WARMUP_TICKS = 120L;
    private static final long DIALOG_WAIT_TICKS = 60L;

    private static State state = State.IDLE;
    private static long tickCounter;
    private static long openTick = -1L;
    private static String openFailure;

    private ClientRuntimeSelfTest() {
    }

    /** Called from {@link Client#tick} on the client thread. */
    public static void tick() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        if (Minecraft.getInstance() == null) return;
        tickCounter++;

        switch (state) {
            case IDLE -> maybeOpen();
            case OPENING_DIALOG -> maybeOpenDialog();
            case WAITING -> maybeAssert();
            case DONE -> {
            }
        }
    }

    private static boolean isMetaDialogScenario() {
        return SCENARIO_META_DIALOG.equals(System.getProperty(SCENARIO_PROPERTY));
    }

    private static void maybeOpen() {
        if (tickCounter < START_DELAY_TICKS) return;
        if (HTML.getTemple(RESOURCE_MANAGER_PATH) == null
                && tickCounter < START_DELAY_TICKS + TEMPLATE_WAIT_TICKS) return;

        try {
            if (openThroughKeybinding()) {
                ApricityUI.LOGGER.info("[AUI SelfTest] resource manager opened through the bound key");
            } else {
                // Still open it directly so the structural assertions and the
                // screenshot below have something to look at; openFailure keeps
                // the run marked as failed.
                ResourceManager.close();
                ResourceManager.open();
                ApricityUI.LOGGER.warn("[AUI SelfTest] falling back to ResourceManager.open(): {}", openFailure);
            }
            if (isMetaDialogScenario()) {
                openDevToolsOnResourceManager();
            }
        } catch (Throwable failure) {
            openFailure = "resource manager open threw " + failure.getClass().getSimpleName()
                    + ": " + safe(failure.getMessage());
        }

        openTick = tickCounter;
        state = isMetaDialogScenario() ? State.OPENING_DIALOG : State.WAITING;
    }

    /**
     * Reproduces what a player does: bind the shortcut, then press it. The press
     * goes through {@link Client#handleKeyInput}, the same entry point the Fabric
     * keyboard mixin funnels native key events into.
     */
    private static boolean openThroughKeybinding() {
        KeyMapping mapping = Keybindings.RESOURCE_MANAGER;
        mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(SELFTEST_BOUND_KEY));

        int reported = AuiServices.keys().resourceManagerKey();
        if (reported != SELFTEST_BOUND_KEY) {
            openFailure = "key service reported key " + reported + " for a resource-manager binding of "
                    + SELFTEST_BOUND_KEY + " (the bound key is not what common reads)";
            return false;
        }

        ResourceManager.close();
        Client.handleKeyInput(SELFTEST_BOUND_KEY, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, 0);
        if (!ResourceManager.isOpen()) {
            openFailure = "pressing the bound resource-manager key did not open the panel";
            return false;
        }
        return true;
    }

    /** Opens DevTools with the resource manager as the inspected document. */
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

    /** Clicks the title-bar meta button once DevTools has rendered. */
    private static void maybeOpenDialog() {
        if (tickCounter - openTick < RENDER_WARMUP_TICKS) return;

        Document toolDocument = com.sighs.apricityui.dev.DevTools.getToolDocument();
        Element metaButton = toolDocument == null ? null : toolDocument.querySelector("#metaButton");
        if (metaButton == null) {
            openFailure = "devtools #metaButton not found";
        } else {
            try {
                captureScreenshot("-predialog");
                Document inspected = latestDocument(RESOURCE_MANAGER_PATH);
                ApricityUI.LOGGER.info("[AUI SelfTest] pre-click: resource manager doc={} disposed={}",
                        inspected != null, inspected != null && inspected.isDisposed());
                runInspectPickTest();
                metaButton.click();
                ApricityUI.LOGGER.info("[AUI SelfTest] clicked devtools #metaButton");
            } catch (Throwable failure) {
                openFailure = "metaButton click threw " + failure.getClass().getSimpleName()
                        + ": " + safe(failure.getMessage());
            }
        }
        openTick = tickCounter;
        state = State.WAITING;
    }

    /** Turns on devtools element-pick mode and parks the cursor over the target
     *  document centre. On Fabric {@code ScreenEvents.afterExtract} reaches
     *  {@link Client#drawScreenLike}, which drives {@code handleInspectMouseMove}
     *  while a Screen is open (the HUD layer path only runs without a Screen). */
    private static void runInspectPickTest() {
        try {
            Document tool = com.sighs.apricityui.dev.DevTools.getToolDocument();
            if (tool == null) return;
            Element pickBtn = tool.querySelector("#pickBtn");
            if (pickBtn == null) return;
            pickBtn.click();
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            long handle = mc.getWindow().handle();
            double guiScale = mc.getWindow().getGuiScale();
            double cx = mc.getWindow().getGuiScaledWidth() / 2.0 * guiScale;
            double cy = mc.getWindow().getGuiScaledHeight() / 2.0 * guiScale;
            org.lwjgl.glfw.GLFW.glfwSetCursorPos(handle, cx, cy);
        } catch (Throwable failure) {
            ApricityUI.LOGGER.error("[AUI SelfTest] inspect pick test failed", failure);
        }
    }

    private static void maybeAssert() {
        if (tickCounter - openTick < (isMetaDialogScenario() ? DIALOG_WAIT_TICKS : RENDER_WARMUP_TICKS)) return;

        List<String> failures = new ArrayList<>();
        if (openFailure != null) failures.add(openFailure);
        if (openFailure == null && !ResourceManager.isOpen()) {
            failures.add("resource manager did not remain open");
        }
        checkLocalStorage(failures);
        if (isMetaDialogScenario()) validateMetaDialog(failures);
        else validateResourceManagerDocument(failures);

        captureScreenshot("");

        if (failures.isEmpty()) {
            ApricityUI.LOGGER.info("[AUI SelfTest] PASS 26.1 render smoke test");
        } else {
            ApricityUI.LOGGER.error("[AUI SelfTest] FAIL 26.1 render smoke test: {}",
                    String.join(" | ", failures));
        }
        writeResult(failures);
        state = State.DONE;

        if (Boolean.getBoolean(EXIT_PROPERTY)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) minecraft.stop();
        }
    }

    /**
     * Round-trips the NBT-backed localStorage.
     *
     * <p>The persistence layer reaches {@code NbtIo}/{@code CompoundTag} through
     * reflection, so a rename on the Minecraft side only shows up as an ERROR
     * every 5000 ticks (and as silently lost data). Writing a key, reloading it
     * through a fresh store and reading it back turns that drift into a normal
     * test failure.</p>
     */
    private static void checkLocalStorage(List<String> failures) {
        String key = "__aui_selftest__";
        String expected = "round-trip";
        try {
            Window.window.localStorage.setItem(key, expected);
            File storageFile = LocalStorage.getStorageFile();
            if (storageFile == null || !storageFile.isFile()) {
                failures.add("localStorage.nbt was not written to " + (storageFile == null ? "<null>" : storageFile.getAbsolutePath()));
                return;
            }
            LocalStorage reloaded = new LocalStorage();
            reloaded.load();
            String actual = reloaded.getItem(key);
            if (!expected.equals(actual)) {
                failures.add("localStorage round-trip expected=" + expected + " actual=" + safe(actual));
            }
            Window.window.localStorage.removeItem(key);
        } catch (Throwable failure) {
            failures.add("localStorage round-trip threw " + failure.getClass().getSimpleName()
                    + ": " + safe(failure.getMessage()));
        }
    }

    /** Structural check that the meta dialog actually opened (visual check is the screenshot). */
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
        if (highlight != null) {
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

    /**
     * Grabs the main render target. Tick phase runs before this frame's render,
     * so the target still holds the previous fully-composited frame — including
     * the PIP overlay — which is exactly what we want to eyeball.
     */
    private static void captureScreenshot(String suffix) {
        String rawPath = System.getProperty(SCREENSHOT_PROPERTY);
        if (rawPath == null || rawPath.isBlank()) return;
        if (suffix != null && !suffix.isEmpty()) {
            rawPath = rawPath.endsWith(".png")
                    ? rawPath.substring(0, rawPath.length() - 4) + suffix + ".png"
                    : rawPath + suffix;
        }
        Path target = Path.of(rawPath).toAbsolutePath().normalize();
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (mainTarget == null) return;
        try {
            Screenshot.takeScreenshot(mainTarget, image -> {
                try {
                    Path parent = target.getParent();
                    if (parent != null) Files.createDirectories(parent);
                    image.writeToFile(target);
                    ApricityUI.LOGGER.info("[AUI SelfTest] screenshot written to {}", target);
                } catch (Exception writeFailure) {
                    ApricityUI.LOGGER.error("[AUI SelfTest] could not write screenshot", writeFailure);
                } finally {
                    image.close();
                }
            });
        } catch (Throwable failure) {
            ApricityUI.LOGGER.error("[AUI SelfTest] screenshot capture failed", failure);
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

    private static String safe(String value) {
        return value == null ? "<null>" : value;
    }

    private static void writeResult(List<String> failures) {
        String rawPath = System.getProperty(RESULT_PROPERTY);
        if (rawPath == null || rawPath.isBlank()) return;
        try {
            Path result = Path.of(rawPath).toAbsolutePath().normalize();
            Path parent = result.getParent();
            if (parent != null) Files.createDirectories(parent);
            StringBuilder output = new StringBuilder(failures.isEmpty() ? "PASS\n" : "FAIL\n");
            for (String failure : failures) output.append(failure).append('\n');
            Files.writeString(result, output.toString(), StandardCharsets.UTF_8);
        } catch (Exception writeFailure) {
            ApricityUI.LOGGER.error("[AUI SelfTest] could not write result file", writeFailure);
        }
    }

    private enum State {
        IDLE,
        OPENING_DIALOG,
        WAITING,
        DONE
    }
}
