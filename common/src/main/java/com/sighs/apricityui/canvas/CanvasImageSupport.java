package com.sighs.apricityui.canvas;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Canvas;
import com.sighs.apricityui.element.Img;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.loader.Loader;
import com.sighs.apricityui.util.AuiLog;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

public final class CanvasImageSupport {
    private CanvasImageSupport() {
    }

    public static BufferedImage resolveImageSource(Object image) {
        if (image instanceof Canvas sourceCanvas) {
            BufferedImage source = sourceCanvas.getSurface();
            BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D copyGraphics = copy.createGraphics();
            try {
                copyGraphics.drawImage(source, 0, 0, null);
            } finally {
                copyGraphics.dispose();
            }
            return copy;
        }
        if (image instanceof BufferedImage bufferedImage) {
            return bufferedImage;
        }
        if (image instanceof CanvasImageBitmap bitmap) {
            return bitmap.image();
        }
        if (image instanceof CanvasImageData imageData) {
            return fromImageData(imageData);
        }
        if (image instanceof Window.FetchResponse response) {
            return readImageBytes(response.bytes());
        }
        if (image instanceof byte[] bytes) {
            return readImageBytes(bytes);
        }
        if (image instanceof String text) {
            return resolveStringSource(text);
        }
        if (image instanceof Img img) {
            String src = img.getAttribute("src");
            if (src == null || src.isBlank() || img.document == null) {
                ApricityUI.LOGGER.warn("[AUI Canvas] image element has no usable src element={}", AuiLog.element(img));
                return null;
            }
            String resolvedPath = Loader.resolve(img.document.getPath(), src);
            try (InputStream stream = Loader.getResourceStream(resolvedPath)) {
                if (stream == null) {
                    ApricityUI.LOGGER.warn("[AUI Canvas] image resource is missing path={}", resolvedPath);
                    return null;
                }
                BufferedImage result = ImageIO.read(stream);
                if (result == null) {
                    ApricityUI.LOGGER.warn("[AUI Canvas] ImageIO could not decode path={}", resolvedPath);
                }
                return result;
            } catch (IOException exception) {
                ApricityUI.LOGGER.error("[AUI Canvas] failed to read image path={}", resolvedPath, exception);
                return null;
            }
        }
        return null;
    }

    private static BufferedImage fromImageData(CanvasImageData imageData) {
        if (imageData == null) return null;
        BufferedImage bufferedImage = new BufferedImage(imageData.width, imageData.height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < imageData.height; y++) {
            for (int x = 0; x < imageData.width; x++) {
                int index = (y * imageData.width + x) * 4;
                if (index + 3 >= imageData.data.length) continue;
                int r = CanvasStyleUtil.clampChannel(imageData.data[index]);
                int g = CanvasStyleUtil.clampChannel(imageData.data[index + 1]);
                int b = CanvasStyleUtil.clampChannel(imageData.data[index + 2]);
                int a = CanvasStyleUtil.clampChannel(imageData.data[index + 3]);
                bufferedImage.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return bufferedImage;
    }

    private static BufferedImage resolveStringSource(String text) {
        if (text == null || text.isBlank()) return null;
        String trimmed = text.trim();
        if (trimmed.regionMatches(true, 0, "data:", 0, 5)) {
            int comma = trimmed.indexOf(',');
            if (comma < 0) {
                ApricityUI.LOGGER.warn("[AUI Canvas] malformed data image URI");
                return null;
            }
            String meta = trimmed.substring(0, comma);
            String body = trimmed.substring(comma + 1);
            if (!meta.toLowerCase().contains(";base64")) {
                ApricityUI.LOGGER.warn("[AUI Canvas] unsupported non-base64 data image URI");
                return null;
            }
            try {
                return readImageBytes(Base64.getDecoder().decode(body));
            } catch (IllegalArgumentException exception) {
                ApricityUI.LOGGER.warn("[AUI Canvas] invalid base64 image URI", exception);
                return null;
            }
        }
        try (InputStream stream = Loader.getResourceStream(trimmed)) {
            if (stream == null) {
                ApricityUI.LOGGER.warn("[AUI Canvas] image resource is missing path={}", trimmed);
                return null;
            }
            BufferedImage result = ImageIO.read(stream);
            if (result == null) {
                ApricityUI.LOGGER.warn("[AUI Canvas] ImageIO could not decode path={}", trimmed);
            }
            return result;
        } catch (IOException exception) {
            ApricityUI.LOGGER.error("[AUI Canvas] failed to read image path={}", trimmed, exception);
            return null;
        }
    }

    private static BufferedImage readImageBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(stream);
        } catch (IOException exception) {
            ApricityUI.LOGGER.error("[AUI Canvas] failed to decode image bytes size={}", bytes.length, exception);
            return null;
        }
    }

    static BufferedImage tintImageAlpha(BufferedImage source, Color tint) {
        if (source == null) return null;
        BufferedImage tinted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int tr = tint.getRed();
        int tg = tint.getGreen();
        int tb = tint.getBlue();
        int ta = tint.getAlpha();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int alpha = ((argb >>> 24) & 0xFF) * ta / 255;
                tinted.setRGB(x, y, (alpha << 24) | (tr << 16) | (tg << 8) | tb);
            }
        }
        return tinted;
    }
}
