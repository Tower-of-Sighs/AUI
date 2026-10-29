package com.sighs.apricityui.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real selftest state transitions without constructing a Minecraft client/GPU. */
class ClientRuntimeSelfTestTest {
    @TempDir Path directory;
    private final Map<String, String> previous = new HashMap<>();
    private static final String PREFIX = "apricityui.clientSelfTest.";

    @BeforeEach void reset() throws Exception {
        for (String key : List.of("exitOnFinish", "resultFile", "screenshotFile", "scenario")) {
            previous.put(key, System.getProperty(PREFIX + key));
            System.clearProperty(PREFIX + key);
        }
        System.setProperty(PREFIX + "exitOnFinish", "false");
        System.setProperty(PREFIX + "resultFile", result().toString());
        System.setProperty(PREFIX + "scenario", "renderCompat");
        list("failures").clear();
        list("details").clear();
        field("captured").set(null, null);
        state("IDLE");
    }

    @AfterEach void restore() throws Exception {
        for (var entry : previous.entrySet()) {
            if (entry.getValue() == null) System.clearProperty(PREFIX + entry.getKey());
            else System.setProperty(PREFIX + entry.getKey(), entry.getValue());
        }
        list("failures").clear();
        list("details").clear();
        field("captured").set(null, null);
        state("IDLE");
    }

    @Test void waitingForReadbackDoesNotPublishPassOrFinish() throws Exception {
        state("CAPTURING_FINAL");
        field("captureStarted").setLong(null, System.nanoTime());
        invoke("awaitCapture");
        assertEquals("CAPTURING_FINAL", field("state").get(null).toString());
        assertFalse(Files.exists(result()));
    }

    @Test void callbackCompletionIsRequiredBeforeFinalResult() throws Exception {
        state("CAPTURING_FINAL");
        complete(List.of(), List.of("PNG write complete", "dimensions=960x600"));
        invoke("awaitCapture");
        assertEquals("DONE", field("state").get(null).toString());
        String output = Files.readString(result());
        assertTrue(output.startsWith("PASS\nscenario=renderCompat\n"));
        assertTrue(output.contains("PNG write complete"));
        assertTrue(output.contains("dimensions=960x600"));
    }

    @Test void callbackWriteOrPixelFailureCannotPublishPass() throws Exception {
        state("CAPTURING_FINAL");
        complete(List.of("screenshot write failed", "opacity ROI mismatch"), List.of());
        invoke("awaitCapture");
        String output = Files.readString(result());
        assertTrue(output.startsWith("FAIL\n"));
        assertTrue(output.contains("screenshot write failed"));
        assertTrue(output.contains("opacity ROI mismatch"));
    }

    @Test void missingCallbackTimesOutAsFail() throws Exception {
        state("CAPTURING_FINAL");
        field("captureStarted").setLong(null, System.nanoTime() - java.util.concurrent.TimeUnit.SECONDS.toNanos(31));
        invoke("awaitCapture");
        assertTrue(Files.readString(result()).startsWith("FAIL\n"));
        assertTrue(Files.readString(result()).contains("callback timed out"));
        assertEquals("DONE", field("state").get(null).toString());
    }

    @Test void unknownScenarioFailsInsteadOfFallingBackToResourceManager() throws Exception {
        System.setProperty(PREFIX + "scenario", "typo");
        field("tickCounter").setLong(null, 20);
        invoke("maybeOpen");
        assertTrue(Files.readString(result()).startsWith("FAIL\n"));
        assertTrue(Files.readString(result()).contains("unknown scenario: typo"));
    }

    @Test void titleScreenFixtureNeverFallsBackToUnboundRegistryItemLiterals() throws Exception {
        // 26.3 registry components are unavailable before joining a server. Even if
        // Java stack injection fails, the next document tick must not parse literals
        // and crash the client a second time instead of preserving the FAIL result.
        try (var stream = getClass().getResourceAsStream("/aui-selftest/render-compat.html")) {
            assertNotNull(stream);
            String html = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var items = java.util.regex.Pattern.compile("<item\\b[^>]*>(.*?)</item>", java.util.regex.Pattern.DOTALL).matcher(html);
            int count = 0;
            while (items.find()) {
                assertTrue(items.group(1).isBlank(), "title-screen items must be Java-driven, not registry-parsed");
                count++;
            }
            assertEquals(6, count);
        }
    }

    @Test void missingScreenshotDestinationIsAnError() throws Exception {
        Method method = ClientRuntimeSelfTest.class.getDeclaredMethod("captureScreenshot", boolean.class);
        method.setAccessible(true);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(null, false));
        assertInstanceOf(IllegalStateException.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("screenshotFile property missing"));
    }

    private Path result() { return directory.resolve("result.txt"); }
    private static Field field(String name) throws Exception {
        Field field = ClientRuntimeSelfTest.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    @SuppressWarnings("unchecked")
    private static List<String> list(String name) throws Exception { return (List<String>) field(name).get(null); }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void state(String name) throws Exception {
        Field field = field("state");
        field.set(null, Enum.valueOf((Class<Enum>) field.getType(), name));
    }
    private static void complete(List<String> failures, List<String> details) throws Exception {
        var type = Class.forName(ClientRuntimeSelfTest.class.getName() + "$CaptureResult");
        var constructor = type.getDeclaredConstructor(List.class, List.class);
        constructor.setAccessible(true);
        field("captured").set(null, constructor.newInstance(failures, details));
    }
    private static void invoke(String name) throws Exception {
        Method method = ClientRuntimeSelfTest.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(null);
    }
}
