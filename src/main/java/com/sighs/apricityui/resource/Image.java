package com.sighs.apricityui.resource;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.resource.async.image.DecodedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
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

public class Image {
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
        try {
            if (decodedImage.isAnimated()) {
                List<AnimatedTexture.Frame> frames = new ArrayList<>();
                int[] delays = decodedImage.getFrameDelaysMs();
                try {
                    int index = 0;
                    for (NativeImage frameImage : decodedImage.getFrames()) {
                        TextureInfo info = uploadImage(cacheKey + "_frame_" + index, frameImage);
                        int delay = index < delays.length ? delays[index] : 100;
                        frames.add(new AnimatedTexture.Frame(info.location, info.textureId, Math.max(delay, 20)));
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
            return new StaticTexture(info.textureId, info.location, info.width, info.height);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            decodedImage.close();
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
                nativeImage.setPixelRGBA(x, y, abgr);
            }
        }
        return nativeImage;
    }

    // 获取GIF延迟
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
        int textureId = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(textureId, image.getWidth(), image.getHeight());
        image.upload(0, 0, 0, false);

        // 防止 ResourceLocation 报错
        String sanitizedPath = cacheKey.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
        ResourceLocation location = new ResourceLocation("apricityui", "dynamic/" + sanitizedPath);

        Minecraft.getInstance().getTextureManager().register(location, new SimpleTextureWrapper(textureId));
        return new TextureInfo(textureId, location, image.getWidth(), image.getHeight());
    }

    private record TextureInfo(int textureId, ResourceLocation location, int width, int height) {}

    public interface ITexture {
        ResourceLocation getLocation();
        int getWidth();
        int getHeight();
        void destroy();
    }

    public static class StaticTexture implements ITexture {
        private final int textureId;
        private final ResourceLocation location;
        private final int width;
        private final int height;

        public StaticTexture(int textureId, ResourceLocation location, int width, int height) {
            this.textureId = textureId;
            this.location = location;
            this.width = width;
            this.height = height;
        }

        @Override public ResourceLocation getLocation() { return location; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public void destroy() {
            RenderSystem.recordRenderCall(() -> TextureUtil.releaseTextureId(textureId));
        }
    }

    public static class AnimatedTexture implements ITexture {
        public record Frame(ResourceLocation location, int textureId, int durationMs) {}
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
        public ResourceLocation getLocation() {
            if (frames.isEmpty()) return null;
            if (totalDuration == 0) return frames.get(0).location;
            long now = System.currentTimeMillis();
            long cycleTime = now % totalDuration;
            int currentTimer = 0;
            for (Frame frame : frames) {
                currentTimer += frame.durationMs;
                if (cycleTime < currentTimer) return frame.location;
            }
            return frames.get(0).location;
        }

        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public void destroy() {
            for (Frame frame : frames) {
                RenderSystem.recordRenderCall(() -> TextureUtil.releaseTextureId(frame.textureId));
            }
        }
    }

    public static class SimpleTextureWrapper extends AbstractTexture {
        private final int textureId;
        public SimpleTextureWrapper(int textureId) { this.textureId = textureId; }
        @Override public int getId() { return textureId; }
        @Override public void load(ResourceManager manager) { }
    }
}
