package com.sighs.apricityui.client;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
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
import com.sighs.apricityui.form.FormData;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class ClientRuntimeSelfTest {
    private static final String ENABLE_PROPERTY = "apricityui.clientSelfTest";
    private static final String EXIT_PROPERTY = "apricityui.clientSelfTest.exitOnFinish";
    private static final String RESULT_PROPERTY = "apricityui.clientSelfTest.resultFile";
    private static final String DOCUMENT_PATH_PROPERTY = "apricityui.clientSelfTest.documentPath";
    private static final String MAX_FIRST_CREATE_MILLIS_PROPERTY = "apricityui.clientSelfTest.maxFirstCreateMillis";
    private static final String LIFECYCLE_DOC_PATH = "tests/lifecycle-event-test.html";
    private static final String RUNTIME_DOC_PATH = "tests/client-runtime-self-test.html";
    private static final long START_DELAY_TICKS = 10L;
    private static final long ASSERT_TIMEOUT_TICKS = 120L;

    private static State state = State.IDLE;
    private static long tickCounter = 0L;
    private static long startTick = -1L;
    private static Document lifecycleDocument;
    private static Document runtimeDocument;
    private static Document firstCreateDocument;
    private static String firstCreatePath = "";
    private static long firstCreateNanos = -1L;

    private ClientRuntimeSelfTest() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        tickCounter++;

        switch (state) {
            case IDLE -> maybeStart();
            case WAITING -> maybeAssert();
            case DONE -> {
            }
        }
    }

    private static void maybeStart() {
        if (tickCounter < START_DELAY_TICKS) return;
        if (HTML.getTemple(LIFECYCLE_DOC_PATH) == null || HTML.getTemple(RUNTIME_DOC_PATH) == null) return;
        String requestedPath = System.getProperty(DOCUMENT_PATH_PROPERTY, "").trim();
        if (!requestedPath.isEmpty() && HTML.getTemple(requestedPath) == null) return;

        Document.remove(LIFECYCLE_DOC_PATH);
        Document.remove(RUNTIME_DOC_PATH);
        if (!requestedPath.isEmpty()) {
            firstCreatePath = requestedPath;
            Document.remove(firstCreatePath);
            long startNs = System.nanoTime();
            firstCreateDocument = Document.create(firstCreatePath);
            firstCreateNanos = System.nanoTime() - startNs;
            ApricityUI.LOGGER.info(
                    "[AUI SelfTest] first-create path={} cost={}us documents={} elements={}",
                    firstCreatePath,
                    firstCreateNanos / 1_000L,
                    Document.get(firstCreatePath).size(),
                    firstCreateDocument == null ? 0 : firstCreateDocument.getElements().size()
            );
        }
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
        try {
            validateFirstCreate(failures);
        } catch (Throwable failure) {
            failures.add("first-create assertion threw " + failure.getClass().getSimpleName() + ": " + safe(failure.getMessage()));
        }

        if (failures.isEmpty()) {
            ApricityUI.LOGGER.info("[AUI SelfTest] PASS client runtime self-test");
        } else {
            ApricityUI.LOGGER.error("[AUI SelfTest] FAIL client runtime self-test: {}", String.join(" | ", failures));
        }
        writeResult(failures);

        Document.remove(LIFECYCLE_DOC_PATH);
        Document.remove(RUNTIME_DOC_PATH);
        if (!firstCreatePath.isEmpty()) Document.remove(firstCreatePath);
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

    private static void validateFirstCreate(List<String> failures) {
        if (firstCreatePath.isEmpty()) return;
        if (firstCreateDocument == null || firstCreateDocument.body == null) {
            failures.add("first-create document was not created path=" + firstCreatePath);
            return;
        }

        int documentCount = Document.get(firstCreatePath).size();
        if (documentCount != 1) {
            failures.add("first-create expected one document path=" + firstCreatePath + " actual=" + documentCount);
        }

        long maxMillis = Long.getLong(MAX_FIRST_CREATE_MILLIS_PROPERTY, 250L);
        long elapsedMillis = firstCreateNanos / 1_000_000L;
        if (elapsedMillis > maxMillis) {
            failures.add("first-create exceeded budget path=" + firstCreatePath
                    + " actual=" + elapsedMillis + "ms max=" + maxMillis + "ms");
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
            if (!firstCreatePath.isEmpty() && firstCreateNanos >= 0L) {
                output.append("first-create path=").append(firstCreatePath)
                        .append(" cost=").append(firstCreateNanos / 1_000L).append("us")
                        .append(" documents=").append(Document.get(firstCreatePath).size())
                        .append('\n');
            }
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
