package com.sighs.apricityui.client;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Locale;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class ClientWptSnapshotRunner {
    private static final String INPUT = "AUI_WPT_CLIENT_INPUT";
    private static final String OUTPUT = "AUI_WPT_CLIENT_OUTPUT";
    private static final String EXIT = "AUI_WPT_CLIENT_EXIT_ON_FINISH";
    private static final String TIMEOUT_SECONDS = "AUI_WPT_CLIENT_TIMEOUT_SECONDS";
    private static final String STALL_TIMEOUT_SECONDS = "AUI_WPT_CLIENT_STALL_TIMEOUT_SECONDS";
    private static final ArrayDeque<Case> CASES = new ArrayDeque<>();
    private static boolean initialized;
    private static boolean completed;
    private static long ticks;
    private static volatile int totalCases;
    private static volatile int processedCases;
    private static volatile long activeCaseStartedAt;
    private static volatile String activeCaseId;
    private static BufferedWriter output;

    private ClientWptSnapshotRunner() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || completed || !enabled() || Minecraft.getInstance() == null) return;
        if (!initialized) {
            if (++ticks < 10) return;
            initialize();
            return;
        }
        for (int index = 0; index < 10 && !CASES.isEmpty(); index++) {
            Case testCase = CASES.removeFirst();
            activeCaseId = testCase.id;
            activeCaseStartedAt = System.nanoTime();
            capture(testCase);
            activeCaseStartedAt = 0;
            activeCaseId = null;
            processedCases++;
            if (processedCases % 500 == 0 || CASES.isEmpty()) {
                ApricityUI.LOGGER.info("[AUI WPT] progress processed={} total={} remaining={}",
                        processedCases, totalCases, CASES.size());
            }
        }
        if (CASES.isEmpty()) finish();
    }

    private static boolean enabled() {
        return System.getenv(INPUT) != null && System.getenv(OUTPUT) != null;
    }

    private static void initialize() {
        initialized = true;
        try {
            Size.setViewportOverride(
                    environmentInt("AUI_WPT_VIEWPORT_WIDTH", 800),
                    environmentInt("AUI_WPT_VIEWPORT_HEIGHT", 600)
            );
            for (String line : Files.readAllLines(Path.of(System.getenv(INPUT)), StandardCharsets.UTF_8)) {
                int tab = line.indexOf('\t');
                if (tab > 0) CASES.add(new Case(line.substring(0, tab), Path.of(line.substring(tab + 1))));
            }
            totalCases = CASES.size();
            Path target = Path.of(System.getenv(OUTPUT));
            Files.createDirectories(target.getParent());
            output = Files.newBufferedWriter(target, StandardCharsets.UTF_8);
            ApricityUI.LOGGER.info("[AUI WPT] client batch started: {} cases", CASES.size());
            startWatchdog();
        } catch (Exception exception) {
            ApricityUI.LOGGER.error("[AUI WPT] client batch initialization failed", exception);
            finish();
        }
    }

    private static void startWatchdog() {
        int timeoutSeconds = environmentInt(TIMEOUT_SECONDS, 900);
        int stallTimeoutSeconds = environmentInt(STALL_TIMEOUT_SECONDS, 15);
        Thread watchdog = new Thread(() -> {
            long batchStartedAt = System.nanoTime();
            String reason = null;
            while (!completed && reason == null) {
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ignored) {
                    return;
                }
                long now = System.nanoTime();
                if (activeCaseStartedAt != 0 && now - activeCaseStartedAt >= stallTimeoutSeconds * 1_000_000_000L) {
                    reason = "case stalled for " + stallTimeoutSeconds + " seconds: " + activeCaseId;
                } else if (now - batchStartedAt >= timeoutSeconds * 1_000_000_000L) {
                    reason = "batch timed out after " + timeoutSeconds + " seconds";
                }
            }
            if (completed) return;
            ApricityUI.LOGGER.error("[AUI WPT] client watchdog stopped at {}/{} cases: {}",
                    processedCases, totalCases, reason);
            try {
                if (output != null) output.flush();
            } catch (IOException ignored) {
            }
            Runtime.getRuntime().halt(124);
        }, "aui-wpt-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void capture(Case testCase) {
        try {
            String path = "wpt-client/" + testCase.id;
            HTML.putTemple(path, Files.readString(testCase.source, StandardCharsets.UTF_8));
            Document document = new Document(path, false);
            document.refresh();
            String snapshot = document == null ? "{\"nodes\":[]}" : snapshot(document);
            write(testCase.id, "pass", Base64.getUrlEncoder().withoutPadding().encodeToString(snapshot.getBytes(StandardCharsets.UTF_8)));
        } catch (Throwable throwable) {
            write(testCase.id, "aui-runtime-unsupported", throwable.getClass().getSimpleName());
        }
    }

    private static String snapshot(Document document) {
        StringBuilder value = new StringBuilder("{\"nodes\":[");
        boolean first = true;
        for (Element element : document.querySelectorAll("*")) {
            if (!first) value.append(',');
            first = false;
            Element.DOMRect rect = element.getBoundingClientRect();
            value.append("{\"tag\":\"").append(escape(element.getNodeName().toLowerCase()))
                    .append("\",\"id\":\"").append(escape(element.getAttribute("id")))
                    .append("\",\"rect\":[").append(number(rect.x)).append(',').append(number(rect.y))
                    .append(',').append(number(rect.width)).append(',').append(number(rect.height)).append("]}");
        }
        return value.append("]}").toString();
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void write(String id, String status, String detail) {
        try {
            if (output == null) return;
            output.write(id.replace('\t', ' '));
            output.write('\t');
            output.write(status);
            output.write('\t');
            output.write(detail == null ? "" : detail.replace('\t', ' ').replace('\n', ' '));
            output.newLine();
            output.flush();
        } catch (IOException exception) {
            ApricityUI.LOGGER.error("[AUI WPT] result write failed", exception);
        }
    }

    private static void finish() {
        if (completed) return;
        completed = true;
        try {
            if (output != null) output.close();
        } catch (IOException ignored) {
        }
        Size.clearViewportOverride();
        ApricityUI.LOGGER.info("[AUI WPT] client batch complete");
        if (Boolean.parseBoolean(System.getenv(EXIT)) && Minecraft.getInstance() != null) Minecraft.getInstance().stop();
    }

    private static int environmentInt(String name, int fallback) {
        try {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record Case(String id, Path source) {
    }
}
