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
 * Render smoke test for the 26.1 migration.
 *
 * <p>Enabled with {@code -Dapricityui.clientSelfTest=true}. Waits for the dev
 * templates to load, opens the built-in resource manager (a persistent overlay
 * document — exactly the path the new PIP overlay renderer exercises), lets it
 * render for a few seconds, then validates the document structure, captures a
 * screenshot of the main render target and writes a PASS/FAIL result file.</p>
 *
 * <p>System properties:</p>
 * <ul>
 *   <li>{@code apricityui.clientSelfTest} — master switch.</li>
 *   <li>{@code apricityui.clientSelfTest.exitOnFinish} — stop the game afterwards.</li>
 *   <li>{@code apricityui.clientSelfTest.resultFile} — where to write PASS/FAIL.</li>
 *   <li>{@code apricityui.clientSelfTest.screenshotFile} — where to write the PNG.</li>
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
            ResourceManager.close();
            ResourceManager.open();
            ApricityUI.LOGGER.info("[AUI SelfTest] opened resource manager");
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

    private static void maybeAssert() {
        if (tickCounter - openTick < (isMetaDialogScenario() ? DIALOG_WAIT_TICKS : RENDER_WARMUP_TICKS)) return;

        List<String> failures = new ArrayList<>();
        if (openFailure != null) failures.add(openFailure);
        if (openFailure == null && !ResourceManager.isOpen()) {
            failures.add("resource manager did not remain open");
        }
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
