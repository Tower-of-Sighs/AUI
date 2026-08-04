package com.sighs.apricityui.client;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.dev.ResourceManager;
import com.sighs.apricityui.dev.resource.ResourcePreviewDialog;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.event.MouseEvent;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.loader.ClientLoader;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.parser.HTML;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Render smoke test for the 26.1 GUI/PIP bridge.
 *
 * <p>The 26.1 target intentionally has no JavaScript service, so the shared
 * lifecycle and form tests are not meaningful here. This test exercises the
 * actual resource-manager document that is rendered by the PIP path.</p>
 */
@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class ClientRuntimeSelfTest {
    private static final String ENABLE_PROPERTY = "apricityui.clientSelfTest";
    private static final String EXIT_PROPERTY = "apricityui.clientSelfTest.exitOnFinish";
    private static final String RESULT_PROPERTY = "apricityui.clientSelfTest.resultFile";
    private static final String OPEN_RESOURCE_MANAGER_PROPERTY = "apricityui.clientSelfTest.openResourceManager";
    private static final String OPEN_CANVAS_PREVIEW_PROPERTY = "apricityui.clientSelfTest.openCanvasPreview";
    private static final String PREVIEW_PATH_PROPERTY = "apricityui.clientSelfTest.previewPath";
    private static final String RESOURCE_MANAGER_PATH = "devtools/resource.html";
    private static final String DEFAULT_CANVAS_PREVIEW_PATH = "tests/canvas-doodle-board.html";
    private static final long START_DELAY_TICKS = 10L;
    private static final long RESOURCE_WAIT_TICKS = 100L;
    private static final long PREVIEW_DELAY_TICKS = 60L;
    private static final long PREVIEW_HOLD_TICKS = 600L;
    private static final long ASSERT_TIMEOUT_TICKS = PREVIEW_DELAY_TICKS + PREVIEW_HOLD_TICKS;

    private static State state = State.IDLE;
    private static long tickCounter;
    private static long startTick = -1L;
    private static String canvasPreviewPath = DEFAULT_CANVAS_PREVIEW_PATH;
    private static boolean resourceManagerRequested;
    private static boolean canvasPreviewRequested;
    private static boolean canvasPreviewAttempted;
    private static ResourcePreviewDialog canvasPreview;
    private static String startFailure;
    private static String previewFailure;

    private ClientRuntimeSelfTest() {
    }

    /** Called from the target's established client-tick path. */
    public static void tick() {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        tickCounter++;

        switch (state) {
            case IDLE -> maybeStart();
            case WAITING -> {
                maybeOpenCanvasPreview();
                maybeAssert();
            }
            case DONE -> {
            }
        }
    }

    private static void maybeStart() {
        if (tickCounter < START_DELAY_TICKS) return;
        if (HTML.getTemple(RESOURCE_MANAGER_PATH) == null
                && tickCounter < START_DELAY_TICKS + RESOURCE_WAIT_TICKS) return;

        resourceManagerRequested = Boolean.getBoolean(OPEN_RESOURCE_MANAGER_PROPERTY);
        // This smoke test verifies the manager-to-preview flow, so preview is
        // part of the test whenever the manager itself was requested.
        canvasPreviewRequested = resourceManagerRequested;
        canvasPreviewPath = System.getProperty(PREVIEW_PATH_PROPERTY, DEFAULT_CANVAS_PREVIEW_PATH);
        if (!resourceManagerRequested) {
            startFailure = "openResourceManager property must be true for the render smoke test";
        } else {
            try {
                ResourceManager.close();
                ResourceManager.open();
                ApricityUI.LOGGER.info("[AUI SelfTest] opened resource manager for render smoke test");
            } catch (Throwable failure) {
                startFailure = "resource manager open threw " + failure.getClass().getSimpleName()
                        + ": " + safe(failure.getMessage());
            }
        }

        startTick = tickCounter;
        state = State.WAITING;
        ApricityUI.LOGGER.info("[AUI SelfTest] started 26.1 render smoke test");
    }

    private static void maybeOpenCanvasPreview() {
        if (!canvasPreviewRequested || canvasPreviewAttempted || startFailure != null
                || tickCounter - startTick < PREVIEW_DELAY_TICKS) return;
        canvasPreviewAttempted = true;

        Document owner = latestDocument(RESOURCE_MANAGER_PATH);
        Loader.StaticResourceEntry entry = ClientLoader.listFinalStaticResources().stream()
                .filter(candidate -> canvasPreviewPath.equals(safe(candidate.path())))
                .findFirst()
                .orElse(null);
        if (owner == null) {
            previewFailure = "resource manager document was unavailable for canvas preview";
            return;
        }
        if (entry == null) {
            previewFailure = "canvas preview resource was not found: " + canvasPreviewPath;
            return;
        }

        try {
            canvasPreview = new ResourcePreviewDialog();
            canvasPreview.open(owner, entry);
            if (!canvasPreview.isOpen()) {
                previewFailure = "canvas preview dialog did not open";
            } else {
                drawDoodleSmokeStroke();
                ApricityUI.LOGGER.info("[AUI SelfTest] opened canvas preview after resource manager");
            }
        } catch (Throwable failure) {
            previewFailure = "canvas preview open threw " + failure.getClass().getSimpleName()
                    + ": " + safe(failure.getMessage());
        }
    }

    /**
     * The doodle page intentionally starts with a cleared canvas. Paint one
     * stroke through its DOM listeners so the screenshot also verifies the
     * preview input path and the Canvas texture upload path.
     */
    private static void drawDoodleSmokeStroke() {
        if (!canvasPreviewPath.endsWith("canvas-doodle-board.html")) return;
        Document document = latestDocument(canvasPreviewPath);
        Element board = document == null ? null : document.querySelector("#board");
        if (!(board instanceof Canvas)) {
            previewFailure = "doodle preview board was not created";
            return;
        }

        Element.DOMRect bounds = board.getBoundingClientRect();
        if (bounds.width <= 0.0 || bounds.height <= 0.0) {
            previewFailure = "doodle preview board has empty layout bounds";
            return;
        }

        double startX = bounds.width * 0.18d;
        double startY = bounds.height * 0.78d;
        double middleX = bounds.width * 0.5d;
        double middleY = bounds.height * 0.32d;
        double endX = bounds.width * 0.82d;
        double endY = bounds.height * 0.68d;
        dispatchCanvasMouse(document, board, "mousedown", startX, startY, 0, 1);
        dispatchCanvasMouse(document, board, "mousemove", middleX, middleY, -1, 1);
        dispatchCanvasMouse(document, board, "mousemove", endX, endY, -1, 1);
        dispatchCanvasMouse(document, board, "mouseup", endX, endY, 0, 0);
    }

    private static void dispatchCanvasMouse(Document document, Element board, String type,
                                             double offsetX, double offsetY, int button, int buttons) {
        MouseEvent event = new MouseEvent(type, new Position(offsetX, offsetY), button, false);
        event.offsetX = offsetX;
        event.offsetY = offsetY;
        event.buttons = buttons;
        MouseEvent.dispatchToTarget(event, document, board);
    }

    private static void maybeAssert() {
        if (tickCounter - startTick < ASSERT_TIMEOUT_TICKS) return;

        List<String> failures = new ArrayList<>();
        if (startFailure != null) failures.add(startFailure);
        if (resourceManagerRequested) {
            if (!ResourceManager.isOpen()) {
                failures.add("resource manager did not remain open");
            }
            validateResourceManagerDocument(failures);
            if (canvasPreviewRequested) validateCanvasPreview(failures);
        }
        if (canvasPreviewRequested) {
            if (previewFailure != null) {
                failures.add(previewFailure);
            } else if (canvasPreview == null || !canvasPreview.isOpen()) {
                failures.add("canvas preview dialog did not remain open");
            }
        }

        if (failures.isEmpty()) {
            ApricityUI.LOGGER.info("[AUI SelfTest] PASS 26.1 render smoke test");
        } else {
            ApricityUI.LOGGER.error("[AUI SelfTest] FAIL 26.1 render smoke test: {}",
                    String.join(" | ", failures));
        }
        writeResult(failures);

        if (canvasPreview != null) canvasPreview.close();
        if (resourceManagerRequested) ResourceManager.close();
        state = State.DONE;

        if (Boolean.getBoolean(EXIT_PROPERTY)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) minecraft.stop();
        }
    }

    private static void validateResourceManagerDocument(List<String> failures) {
        Document document = latestDocument(RESOURCE_MANAGER_PATH);
        if (document == null || document.body == null) {
            failures.add("resource manager document/body was not created");
            return;
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

    private static void validateCanvasPreview(List<String> failures) {
        Document document = latestDocument(canvasPreviewPath);
        if (document == null || document.body == null) {
            failures.add("canvas preview document/body was not created: " + canvasPreviewPath);
            return;
        }

        List<Element> canvases = document.querySelectorAll("canvas");
        if (canvases.isEmpty()) {
            failures.add("canvas preview contains no canvas elements: " + canvasPreviewPath);
            return;
        }

        boolean painted = false;
        for (Element element : canvases) {
            if (!(element instanceof Canvas canvas)) continue;
            BufferedImage surface = canvas.getSurface();
            for (int y = 0; y < surface.getHeight() && !painted; y++) {
                for (int x = 0; x < surface.getWidth(); x++) {
                    if ((surface.getRGB(x, y) >>> 24) != 0) {
                        painted = true;
                        break;
                    }
                }
            }
            if (painted) break;
        }
        if (!painted) {
            failures.add("canvas preview surfaces are empty; page JavaScript did not draw: " + canvasPreviewPath);
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
            output.append("resourceManagerRequested=").append(resourceManagerRequested).append('\n');
            output.append("canvasPreviewRequested=").append(canvasPreviewRequested).append('\n');
            output.append("canvasPreviewOpen=").append(canvasPreview != null && canvasPreview.isOpen()).append('\n');
            if (previewFailure != null) output.append("previewFailure=").append(previewFailure).append('\n');
            for (String failure : failures) output.append(failure).append('\n');
            Files.writeString(result, output.toString(), StandardCharsets.UTF_8);
        } catch (Exception writeFailure) {
            ApricityUI.LOGGER.error("[AUI SelfTest] could not write result file", writeFailure);
        }
    }

    private enum State {
        IDLE,
        WAITING,
        DONE
    }
}
