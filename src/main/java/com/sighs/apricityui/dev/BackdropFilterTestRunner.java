package com.sighs.apricityui.dev;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.network.chat.Component;

import java.io.File;

public final class BackdropFilterTestRunner {
    private static final String TEST_PATH = "tests/backdrop-filter-test.html";
    private static final int WAIT_LOG_INTERVAL_TICKS = 40;
    private static final int SCREENSHOT_DELAY_TICKS = 200; // 10s at 20 TPS
    private static final int SHUTDOWN_DELAY_TICKS = 100; // 5s at 20 TPS
    private static final boolean AUTO_SCREENSHOT = false;
    private static final boolean AUTO_SHUTDOWN = false;

    private static Phase phase = Phase.WAIT_FOR_WORLD;
    private static int timerTicks = 0;
    private static int waitLogTicks = 0;
    private static boolean pendingScreenshot = false;

    private enum Phase {
        WAIT_FOR_WORLD,
        OPENED,
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
                    } else {
                        ApricityUI.LOGGER.warn("[BackdropTest] Document create failed path={}", TEST_PATH);
                    }
                } else {
                    ApricityUI.LOGGER.info("[BackdropTest] Document already open path={}", TEST_PATH);
                }
                timerTicks = 0;
                if (AUTO_SCREENSHOT) {
                    phase = Phase.OPENED;
                    ApricityUI.LOGGER.info(
                            "[BackdropTest] Opened test document, scheduling screenshot in {} ticks.",
                            SCREENSHOT_DELAY_TICKS
                    );
                } else {
                    phase = Phase.DONE;
                    ApricityUI.LOGGER.info("[BackdropTest] Opened test document, auto screenshot/shutdown disabled.");
                }
            }
            case OPENED -> {
                if (!AUTO_SCREENSHOT) {
                    phase = Phase.DONE;
                    return;
                }
                timerTicks++;
                if (timerTicks < SCREENSHOT_DELAY_TICKS) return;
                pendingScreenshot = true;
                timerTicks = 0;
                phase = Phase.SCREENSHOT_PENDING;
                ApricityUI.LOGGER.info("[BackdropTest] Screenshot pending on next GUI render.");
            }
            case SCREENSHOT_PENDING -> {
                if (!AUTO_SCREENSHOT) {
                    pendingScreenshot = false;
                    phase = Phase.DONE;
                    return;
                }
                timerTicks++;
                if (timerTicks % WAIT_LOG_INTERVAL_TICKS == 0) {
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
}
