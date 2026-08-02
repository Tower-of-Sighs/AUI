package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class ClientRuntimeSelfTest {
    private static final String ENABLE_PROPERTY = "apricityui.clientSelfTest";
    private static final String EXIT_PROPERTY = "apricityui.clientSelfTest.exitOnFinish";
    private static final String ITEM_RENDER_REGRESSION_PROPERTY = "apricityui.itemRenderRegression";
    private static final String RESULT_PROPERTY = "apricityui.clientSelfTest.resultFile";
    private static final String LIFECYCLE_DOC_PATH = "tests/lifecycle-event-test.html";
    private static final String RUNTIME_DOC_PATH = "tests/client-runtime-self-test.html";
    private static final String ITEM_RENDER_REGRESSION_DOC_PATH = "tests/item-render-regression-test.html";
    private static final long START_DELAY_TICKS = 10L;
    private static final long ASSERT_TIMEOUT_TICKS = 120L;

    private static State state = State.IDLE;
    private static long tickCounter = 0L;
    private static long startTick = -1L;
    private static Document lifecycleDocument;
    private static Document runtimeDocument;
    private static boolean itemRenderRegressionOpened;

    private ClientRuntimeSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        maybeOpenItemRenderRegression(minecraft);
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;
        tickCounter++;

        switch (state) {
            case IDLE -> maybeStart();
            case WAITING -> maybeAssert();
            case DONE -> {
            }
        }
    }

    private static void maybeOpenItemRenderRegression(Minecraft minecraft) {
        if (!Boolean.getBoolean(ITEM_RENDER_REGRESSION_PROPERTY) || itemRenderRegressionOpened) return;
        if (HTML.getTemple(ITEM_RENDER_REGRESSION_DOC_PATH) == null) return;

        minecraft.setScreen(new ApricityScreen(ITEM_RENDER_REGRESSION_DOC_PATH));
        itemRenderRegressionOpened = true;
        ApricityUI.LOGGER.info("[AUI SelfTest] opened item render regression screen");
    }

    private static void maybeStart() {
        if (tickCounter < START_DELAY_TICKS) return;
        if (HTML.getTemple(LIFECYCLE_DOC_PATH) == null || HTML.getTemple(RUNTIME_DOC_PATH) == null) return;

        Document.remove(LIFECYCLE_DOC_PATH);
        Document.remove(RUNTIME_DOC_PATH);
        lifecycleDocument = Document.create(LIFECYCLE_DOC_PATH);
        runtimeDocument = Document.create(RUNTIME_DOC_PATH);
        startTick = tickCounter;
        state = State.WAITING;
        ApricityUI.LOGGER.info("[AUI SelfTest] started client runtime self-test");
    }

    private static void maybeAssert() {
        if (tickCounter - startTick < ASSERT_TIMEOUT_TICKS) return;

        List<String> failures = new ArrayList<>();
        try {
            validateLifecycleDocument(failures);
        } catch (Throwable failure) {
            failures.add("lifecycle assertion threw " + failure.getClass().getSimpleName() + ": " + safe(failure.getMessage()));
        }
        try {
            validateRuntimeDocument(failures);
        } catch (Throwable failure) {
            failures.add("runtime assertion threw " + failure.getClass().getSimpleName() + ": " + safe(failure.getMessage()));
        }
        // Slot/Item 迁移不在此自检中扩展行为断言；仅保持既有生命周期检查。

        if (failures.isEmpty()) {
            ApricityUI.LOGGER.info("[AUI SelfTest] PASS client runtime self-test");
        } else {
            ApricityUI.LOGGER.error("[AUI SelfTest] FAIL client runtime self-test: {}", String.join(" | ", failures));
        }
        writeResult(failures);

        Document.remove(LIFECYCLE_DOC_PATH);
        Document.remove(RUNTIME_DOC_PATH);
        state = State.DONE;

        if (Boolean.getBoolean(EXIT_PROPERTY)) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) minecraft.stop();
        }
    }

    private static void validateLifecycleDocument(List<String> failures) {
        if (lifecycleDocument == null || lifecycleDocument.body == null) {
            failures.add("lifecycle document was not created");
            return;
        }
        Element snapshot = lifecycleDocument.querySelector("#snapshot");
        Element log = lifecycleDocument.querySelector("#log");
        if (snapshot == null) {
            failures.add("lifecycle snapshot node missing");
        } else {
            String text = snapshot.getTextContent();
            if (text == null || !text.contains("load | readyState=complete")) {
                failures.add("lifecycle snapshot unexpected: " + safe(text));
            }
        }
        if (log == null) {
            failures.add("lifecycle log node missing");
        } else {
            String text = log.getTextContent();
            String lateResult = lifecycleDocument.body.getAttribute("data-late-listener-result");
            if (!"no".equals(lateResult)) {
                failures.add("lifecycle late-listener behavior unexpected: result="
                        + safe(lateResult) + " log=" + safe(text));
            }
        }
    }

    private static void validateRuntimeDocument(List<String> failures) {
        if (runtimeDocument == null || runtimeDocument.body == null) {
            failures.add("runtime document was not created");
            return;
        }
        Element body = runtimeDocument.body;
        expectAttr(body, "data-initial-ready-state", "interactive", failures, "initial readyState");
        expectAttr(body, "data-domcontentloaded-ready-state", "interactive", failures, "DOMContentLoaded readyState");
        expectAttr(body, "data-load-ready-state", "complete", failures, "load readyState");
        expectAttr(body, "data-timeout", "done", failures, "setTimeout");
        expectAttr(body, "data-late-load-replay", "no", failures, "late load replay");
        expectAttr(body, "data-urlsearchparams", "1,2", failures, "URLSearchParams");
        String actualFormData = body.getAttribute("data-formdata");
        if (!"alpha=1&beta=x&beta=y".equals(actualFormData)) {
            failures.add("FormData expected=alpha=1&beta=x&beta=y actual=" + safe(actualFormData)
                    + " select.multiple=" + safe(body.getAttribute("data-form-select-multiple"))
                    + " options=" + safe(body.getAttribute("data-form-select-options"))
                    + " selectedFlags=" + safe(body.getAttribute("data-form-select-selected-flags")));
        }
        expectAttr(body, "data-form-field-name", "alpha", failures, "form field name");
        expectAttr(body, "data-form-select-multiple", "true", failures, "select.multiple");
        expectAttr(body, "data-load-handler-entered", "yes", failures, "load handler");
        expectAttr(body, "data-location-type", "object", failures, "document.location typeof");

        String pathname = body.getAttribute("data-location-pathname");
        if (pathname == null || !pathname.endsWith(RUNTIME_DOC_PATH)) {
            failures.add("location.pathname unexpected: " + safe(pathname)
                    + " href=" + safe(body.getAttribute("data-location-href")));
        }
    }

    private static void expectAttr(Element body, String name, String expected, List<String> failures, String label) {
        String actual = body.getAttribute(name);
        if (!expected.equals(actual)) {
            failures.add(label + " expected=" + expected + " actual=" + safe(actual));
        }
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
        WAITING,
        DONE
    }
}
