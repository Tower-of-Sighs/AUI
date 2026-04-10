package com.sighs.apricityui.instance;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;

public final class DebugAIScreenshotTicker {
    private static final long CAPTURE_INTERVAL_MS = 1000L;
    private static final int MAX_SCREENSHOTS = 20;
    private static long lastCaptureMs = 0L;
    private static long startMs = 0L;

    private DebugAIScreenshotTicker() {
    }

    public static void tick() {
        if (!ApricityUIConfig.CLIENT.aiAutoScreenshot.get()) {
            startMs = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        if (startMs == 0L) {
            startMs = now;
        }
        if (now - lastCaptureMs < CAPTURE_INTERVAL_MS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget target = minecraft.getMainRenderTarget();

        lastCaptureMs = now;
        File baseDir = new File(minecraft.gameDirectory, "screenshots");
        File screenshotDir = new File(baseDir, "aui");
        if (!screenshotDir.exists() && !screenshotDir.mkdirs()) {
            return;
        }

        cleanupOldScreenshots(screenshotDir);
        Screenshot.grab(
                minecraft.gameDirectory,
                target,
                (Component _) -> {
                    moveLatestScreenshot(baseDir, screenshotDir);
//                    ApricityUI.LOGGER.info("[AIDebug] Screenshot saved: {}", message.getString());
                    cleanupOldScreenshots(screenshotDir);
                }
        );
    }

    private static void moveLatestScreenshot(File baseDir, File targetDir) {
        File[] files = baseDir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null || files.length == 0) {
            return;
        }
        Arrays.sort(files, Comparator
                .comparingLong(File::lastModified)
                .thenComparing(File::getName)
                .reversed());
        File newest = files[0];
        Path dest = new File(targetDir, newest.getName()).toPath();
        try {
            Files.move(newest.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            ApricityUI.LOGGER.warn("[AIDebug] Failed to move screenshot: {}", newest.getAbsolutePath());
        }
    }

    private static void cleanupOldScreenshots(File dir) {
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files == null || files.length <= MAX_SCREENSHOTS) {
            return;
        }
        Arrays.sort(files, Comparator
                .comparingLong(File::lastModified)
                .thenComparing(File::getName));
        int remaining = files.length;
        for (int i = 0; i < files.length && remaining > MAX_SCREENSHOTS; i++) {
            if (files[i].delete()) {
                remaining--;
            } else {
                ApricityUI.LOGGER.warn("[AIDebug] Failed to delete old screenshot: {}", files[i].getAbsolutePath());
            }
        }
    }
}
