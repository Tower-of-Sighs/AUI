package com.sighs.apricityui.resource;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.TextureFormat;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.resource.async.image.DecodedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class Image {
    private Image() {
    }

    public static ITexture loadTexture(String cacheKey, InputStream is) {
        if (is == null) return null;
        try {
            byte[] bytes = is.readAllBytes();
            DecodedImage decodedImage = decode(cacheKey, bytes);
            return uploadDecoded(cacheKey, decodedImage);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static DecodedImage decode(String cacheKey, byte[] data) {
        if (data == null || data.length == 0) return null;
        String fileName = cacheKey == null ? "" : cacheKey.toLowerCase();
        if (fileName.endsWith(".gif")) {
            return loadGifTexture(data);
        }
        return loadStaticTexture(data);
    }

    public static ITexture uploadDecoded(String cacheKey, DecodedImage decodedImage) {
        if (decodedImage == null) return null;
        try (decodedImage) {
            if (decodedImage.isAnimated()) {
                List<AnimatedTexture.Frame> frames = new ArrayList<>();
                int[] delays = decodedImage.getFrameDelaysMs();
                try {
                    int index = 0;
                    for (NativeImage frameImage : decodedImage.getFrames()) {
                        TextureInfo info = uploadImage(cacheKey + "_frame_" + index, frameImage);
                        int delay = index < delays.length ? delays[index] : 100;
                        frames.add(new AnimatedTexture.Frame(info.identifier, Math.max(delay, 20)));
                        index++;
                    }
                } catch (Exception e) {
                    new AnimatedTexture(frames, decodedImage.getWidth(), decodedImage.getHeight()).destroy();
                    throw e;
                }
                return new AnimatedTexture(frames, decodedImage.getWidth(), decodedImage.getHeight());
            }

            NativeImage imageData = decodedImage.getStaticImage();
            if (imageData == null) return null;
            TextureInfo info = uploadImage(cacheKey, imageData);
            return new StaticTexture(info.identifier, info.width, info.height);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static DecodedImage loadStaticTexture(byte[] data) {
        try (InputStream bis = new ByteArrayInputStream(data)) {
            NativeImage image = NativeImage.read(bis);
            return DecodedImage.ofStatic(image);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static DecodedImage loadGifTexture(byte[] data) {
        List<NativeImage> frames = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();

        try (InputStream bis = new ByteArrayInputStream(data);
             ImageInputStream stream = ImageIO.createImageInputStream(bis)) {
            var readers = ImageIO.getImageReadersBySuffix("gif");
            if (!readers.hasNext()) return null;

            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                int count = reader.getNumImages(true);
                for (int i = 0; i < count; i++) {
                    BufferedImage frameImage = reader.read(i);
                    NativeImage nativeImage = convertToNative(frameImage);
                    frames.add(nativeImage);
                    delays.add(getFrameDelay(reader, i));
                }
            } finally {
                reader.dispose();
            }
            DecodedImage decodedImage = DecodedImage.ofAnimated(frames, delays);
            if (decodedImage == null) {
                frames.forEach(NativeImage::close);
            }
            return decodedImage;
        } catch (Exception e) {
            frames.forEach(NativeImage::close);
            e.printStackTrace();
            return null;
        }
    }

    private static NativeImage convertToNative(BufferedImage bi) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        NativeImage nativeImage = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = bi.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = (argb) & 0xFF;
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                nativeImage.setPixelABGR(x, y, abgr);
            }
        }
        return nativeImage;
    }

    private static int getFrameDelay(ImageReader reader, int imageIndex) throws IOException {
        int delayTime = 100;
        IIOMetadata metadata = reader.getImageMetadata(imageIndex);
        String metaFormatName = metadata.getNativeMetadataFormatName();
        Node root = metadata.getAsTree(metaFormatName);
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeName().equalsIgnoreCase("GraphicControlExtension")) {
                NamedNodeMap attributes = node.getAttributes();
                Node delayNode = attributes.getNamedItem("delayTime");
                if (delayNode != null) {
                    delayTime = Integer.parseInt(delayNode.getNodeValue()) * 10;
                }
            }
        }
        return Math.max(delayTime, 20);
    }

    private static TextureInfo uploadImage(String cacheKey, NativeImage image) {
        // Sanitize to avoid Identifier errors.
        String sanitizedPath = (cacheKey == null ? "" : cacheKey)
                .toLowerCase()
                .replaceAll("[^a-z0-9/._-]", "_");
        Identifier identifier = Identifier.fromNamespaceAndPath(ApricityUI.MODID, "dynamic/" + sanitizedPath);

        // Allocate GPU texture and upload pixels. We intentionally do not keep the NativeImage alive after upload,
        // because DecodedImage will dispose of it after this call.
        UploadedTexture texture = new UploadedTexture(() -> "AUI " + identifier, image);
        Minecraft.getInstance().getTextureManager().register(identifier, texture);
        return new TextureInfo(identifier, image.getWidth(), image.getHeight());
    }

    private record TextureInfo(Identifier identifier, int width, int height) {
    }

    public interface ITexture {
        Identifier identifier();

        int width();

        int height();

        void destroy();
    }

    public record StaticTexture(Identifier identifier, int width, int height) implements ITexture {

        @Override
        public void destroy() {
            Minecraft.getInstance().getTextureManager().release(identifier);
        }
    }

    public static final class AnimatedTexture implements ITexture {
        public record Frame(Identifier identifier, int durationMs) {
        }

        private final List<Frame> frames;
        private final int width;
        private final int height;
        private final int totalDuration;

        public AnimatedTexture(List<Frame> frames, int width, int height) {
            this.frames = frames;
            this.width = width;
            this.height = height;
            this.totalDuration = frames.stream().mapToInt(Frame::durationMs).sum();
        }

        @Override
        public Identifier identifier() {
            if (frames.isEmpty()) return null;
            if (totalDuration == 0) return frames.getFirst().identifier;
            long now = System.currentTimeMillis();
            long cycleTime = now % totalDuration;
            int currentTimer = 0;
            for (Frame frame : frames) {
                currentTimer += frame.durationMs;
                if (cycleTime < currentTimer) return frame.identifier;
            }
            return frames.getFirst().identifier;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void destroy() {
            for (Frame frame : frames) {
                Minecraft.getInstance().getTextureManager().release(frame.identifier);
            }
        }
    }

    private static final class UploadedTexture extends AbstractTexture {
        private UploadedTexture(java.util.function.Supplier<String> label, NativeImage image) {
            var device = RenderSystem.getDevice();
            this.texture = device.createTexture(label, 5, TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1, 1);
            this.textureView = device.createTextureView(this.texture);
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
            device.createCommandEncoder().writeToTexture(this.texture, image);
            image.close();
        }
    }
}

