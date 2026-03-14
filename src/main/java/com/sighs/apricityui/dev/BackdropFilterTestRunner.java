package com.sighs.apricityui.dev;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Operation;
import com.sighs.apricityui.instance.Client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.network.chat.Component;
import com.sighs.apricityui.render.Rect;
import com.sighs.apricityui.style.Position;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class BackdropFilterTestRunner {
    private static final String TEST_PATH = "tests/backdrop-filter-test.html";
    private static final String SCROLL_TARGET_ID = "scroll-area";
    private static final int TICKS_PER_SECOND = 20;
    private static final int WAIT_LOG_INTERVAL_TICKS = 40;
    private static final int SCROLL_START_DELAY_TICKS = 2 * TICKS_PER_SECOND;
    private static final int SCROLL_REPEAT_INTERVAL_TICKS = 10;
    private static final int SCROLL_REPEAT_COUNT = 3;
    private static final int SCREENSHOT_DELAY_TICKS = 3 * TICKS_PER_SECOND;
    private static final int SHUTDOWN_DELAY_TICKS = 3 * TICKS_PER_SECOND;
    private static final int MAX_PENDING_SCREENSHOT_TICKS = 6 * TICKS_PER_SECOND;
    private static final boolean AUTO_SCREENSHOT = false;
    private static final boolean AUTO_SHUTDOWN = false;
    private static final double SCROLL_DELTA = -1.0; // negative to scroll down (delta -> scrollDelta = -delta * 50)

    private static Phase phase = Phase.WAIT_FOR_WORLD;
    private static int timerTicks = 0;
    private static int waitLogTicks = 0;
    private static boolean pendingScreenshot = false;
    private static int scrollCount = 0;
    private static int pendingScreenshotTicks = 0;
    private static Position cachedScrollPos = null;
    private static final Map<String, Long> LOG_TIMES = new HashMap<>();
    private static final long LOG_INTERVAL_MS = 2000L;

    private enum Phase {
        WAIT_FOR_WORLD,
        OPENED_WAIT_SCROLL,
        SCROLLING,
        SCREENSHOT_PENDING,
        SCREENSHOT_TAKEN,
        SHUTDOWN_SCHEDULED,
        DONE
    }

    private BackdropFilterTestRunner() {
    }

    public static void tick() {
        if (phase == Phase.DONE) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;

        switch (phase) {
            case WAIT_FOR_WORLD -> {
                if (!isReadyToOpen(minecraft)) {
                    waitLogTicks++;
                    if (waitLogTicks >= WAIT_LOG_INTERVAL_TICKS) {
                        waitLogTicks = 0;
                        ApricityUI.LOGGER.info(
                                "[BackdropTest] Waiting for ready state: level={} screen={}",
                                minecraft.level != null, minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName()
                        );
                    }
                    return;
                }
                waitLogTicks = 0;
                Document existing = Document.get(TEST_PATH).isEmpty() ? null : Document.get(TEST_PATH).get(0);
                if (existing == null) {
                    Document created = Document.create(TEST_PATH);
                    if (created != null) {
                        ApricityUI.LOGGER.info("[BackdropTest] Document created path={}", TEST_PATH);
                        cachedScrollPos = resolveScrollTargetPosition(created);
                    } else {
                        ApricityUI.LOGGER.warn("[BackdropTest] Document create failed path={}", TEST_PATH);
                    }
                } else {
                    ApricityUI.LOGGER.info("[BackdropTest] Document already open path={}", TEST_PATH);
                    cachedScrollPos = resolveScrollTargetPosition(existing);
                }
                timerTicks = 0;
                scrollCount = 0;
                pendingScreenshotTicks = 0;
                phase = Phase.OPENED_WAIT_SCROLL;
                ApricityUI.LOGGER.info(
                        "[BackdropTest] Opened test document, scheduling scroll in {} ticks.",
                        SCROLL_START_DELAY_TICKS
                );
            }
            case OPENED_WAIT_SCROLL -> {
                timerTicks++;
                if (timerTicks < SCROLL_START_DELAY_TICKS) return;
                timerTicks = 0;
                phase = Phase.SCROLLING;
                ApricityUI.LOGGER.info(
                        "[BackdropTest] Starting scroll simulation count={} intervalTicks={}.",
                        SCROLL_REPEAT_COUNT, SCROLL_REPEAT_INTERVAL_TICKS
                );
            }
            case SCROLLING -> {
                if (scrollCount >= SCROLL_REPEAT_COUNT) {
                    timerTicks = 0;
                    if (AUTO_SCREENSHOT) {
                        phase = Phase.SCREENSHOT_PENDING;
                        pendingScreenshot = false;
                        pendingScreenshotTicks = 0;
                        ApricityUI.LOGGER.info(
                                "[BackdropTest] Scroll complete, screenshot scheduled in {} ticks.",
                                SCREENSHOT_DELAY_TICKS
                        );
                    } else {
                        phase = Phase.DONE;
                        ApricityUI.LOGGER.info("[BackdropTest] Scroll complete, auto screenshot/shutdown disabled.");
                    }
                    return;
                }

                timerTicks++;
                if (timerTicks < SCROLL_REPEAT_INTERVAL_TICKS) return;

                timerTicks = 0;
                scrollCount++;
                ensureScrollPosition();
                Operation.scroll(SCROLL_DELTA);
                ApricityUI.LOGGER.info(
                        "[BackdropTest] Simulated scroll {}/{} delta={} mousePos={}",
                        scrollCount, SCROLL_REPEAT_COUNT, SCROLL_DELTA,
                        cachedScrollPos == null ? "null" : String.format("(%.1f, %.1f)", cachedScrollPos.x, cachedScrollPos.y)
                );
            }
            case SCREENSHOT_PENDING -> {
                if (!AUTO_SCREENSHOT) {
                    pendingScreenshot = false;
                    phase = Phase.DONE;
                    return;
                }
                timerTicks++;
                pendingScreenshotTicks++;
                if (timerTicks == SCREENSHOT_DELAY_TICKS) {
                    pendingScreenshot = true;
                    ApricityUI.LOGGER.info("[BackdropTest] Screenshot pending on next GUI render.");
                }
                if (pendingScreenshotTicks >= MAX_PENDING_SCREENSHOT_TICKS) {
                    ApricityUI.LOGGER.warn("[BackdropTest] Screenshot timeout after {} ticks; proceeding to shutdown.", MAX_PENDING_SCREENSHOT_TICKS);
                    pendingScreenshot = false;
                    timerTicks = 0;
                    phase = Phase.SCREENSHOT_TAKEN;
                } else if (timerTicks % WAIT_LOG_INTERVAL_TICKS == 0) {
                    ApricityUI.LOGGER.info("[BackdropTest] Waiting for GUI render to capture screenshot...");
                }
            }
            case SCREENSHOT_TAKEN -> {
                if (!AUTO_SHUTDOWN) {
                    phase = Phase.DONE;
                    return;
                }
                timerTicks++;
                if (timerTicks < SHUTDOWN_DELAY_TICKS) return;
                ApricityUI.LOGGER.info(
                        "[BackdropTest] Shutdown triggered {} ticks after screenshot.",
                        SHUTDOWN_DELAY_TICKS
                );
                phase = Phase.SHUTDOWN_SCHEDULED;
                minecraft.stop();
            }
            case SHUTDOWN_SCHEDULED -> {
                if (AUTO_SHUTDOWN) {
                    ApricityUI.LOGGER.info("[BackdropTest] Waiting for shutdown.");
                } else {
                    phase = Phase.DONE;
                }
            }
            case DONE -> {
            }
        }
    }

    private static void takeScreenshot(Minecraft minecraft) {
        RenderTarget target = minecraft.getMainRenderTarget();
        File screenshotDir = minecraft.gameDirectory;
        ApricityUI.LOGGER.info(
                "[BackdropTest] Taking screenshot gameDir={} target={}x{}",
                screenshotDir.getAbsolutePath(), target.width, target.height
        );

        Screenshot.grab(
                screenshotDir,
                target,
                (Component message) -> ApricityUI.LOGGER.info("[BackdropTest] Screenshot result: {}", message.getString())
        );
    }

    public static void onRenderGuiPost() {
        if (!AUTO_SCREENSHOT || !pendingScreenshot) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return;
        pendingScreenshot = false;
        takeScreenshot(minecraft);
        timerTicks = 0;
        phase = Phase.SCREENSHOT_TAKEN;
    }

    private static boolean isReadyToOpen(Minecraft minecraft) {
        if (minecraft.level != null && minecraft.screen == null) return true;
        if (minecraft.screen != null && minecraft.screen.getClass().getSimpleName().equals("TitleScreen")) return true;
        return false;
    }

    private static void ensureScrollPosition() {
        if (cachedScrollPos != null) {
            Operation.cachedMousePosition = cachedScrollPos;
            return;
        }
        Position fallback = getWindowCenterPosition();
        cachedScrollPos = fallback;
        Operation.cachedMousePosition = fallback;
        if (shouldLog("scroll.fallback", LOG_INTERVAL_MS)) {
            ApricityUI.LOGGER.warn(
                    "[BackdropTest] Scroll target not found, using window center pos={}",
                    fallback == null ? "null" : String.format("(%.1f, %.1f)", fallback.x, fallback.y)
            );
        }
    }

    private static Position resolveScrollTargetPosition(Document document) {
        if (document == null) return null;
        Element target = document.getElementById(SCROLL_TARGET_ID);
        if (target == null) {
            if (shouldLog("scroll.target.missing", LOG_INTERVAL_MS)) {
                ApricityUI.LOGGER.warn("[BackdropTest] Scroll target id={} not found.", SCROLL_TARGET_ID);
            }
            return null;
        }
        Rect rect = Rect.of(target);
        Position p = rect.getBodyRectPosition();
        com.sighs.apricityui.style.Size s = rect.getBodyRectSize();
        Position center = new Position(p.x + s.width() / 2.0, p.y + s.height() / 2.0);
        if (shouldLog("scroll.target", LOG_INTERVAL_MS)) {
            ApricityUI.LOGGER.info(
                    "[BackdropTest] Scroll target resolved id={} center=({}, {}) size=({}, {})",
                    SCROLL_TARGET_ID, center.x, center.y, s.width(), s.height()
            );
        }
        return center;
    }

    private static Position getWindowCenterPosition() {
        try {
            double w = Client.getWindow().getGuiScaledWidth();
            double h = Client.getWindow().getGuiScaledHeight();
            return new Position(w / 2.0, h / 2.0);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean shouldLog(String key, long intervalMs) {
        long now = System.currentTimeMillis();
        Long last = LOG_TIMES.get(key);
        if (last == null || now - last >= intervalMs) {
            LOG_TIMES.put(key, now);
            return true;
        }
        return false;
    }
}
