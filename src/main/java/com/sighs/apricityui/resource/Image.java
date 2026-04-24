package com.sighs.apricityui.resource;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.ApricityUI;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Image {
    public static ITexture loadTexture(String cacheKey, InputStream is) {
        if (is == null) return null;
        try {
            byte[] bytes = is.readAllBytes();
            DecodedImage decodedImage = decode(cacheKey, bytes);
            return uploadDecoded(cacheKey, decodedImage);
        } catch (IOException e) {
            ApricityUI.LOGGER.warn("[Image] Failed to read texture stream, path={}", cacheKey, e);
            return null;
        }
    }

    public static DecodedImage decode(String cacheKey, byte[] data) {
        if (data == null || data.length == 0) return null;
        String fileName = cacheKey == null ? "" : cacheKey.toLowerCase(Locale.ROOT);
        String detectedFormat = detectFormat(data);
        if ("gif".equals(detectedFormat) || fileName.endsWith(".gif")) {
            return loadGifTexture(data);
        }
        if (fileName.endsWith(".cur")) {
            return loadCurTexture(data);
        }
        if (fileName.endsWith(".ani")) {
            return loadAniTexture(data);
        }
        if ("png".equals(detectedFormat)) {
            return loadStaticTexturePng(cacheKey, data);
        }

        DecodedImage decodedImage = loadStaticTextureWithImageIo(cacheKey, data, detectedFormat);
        if (decodedImage != null) return decodedImage;

        ApricityUI.LOGGER.warn(
                "[Image] Failed to decode image, path={}, detectedFormat={}, header={}",
                cacheKey,
                detectedFormat,
                previewHeader(data)
        );
        return null;
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
                    new AnimatedTexture(
                            frames,
                            decodedImage.getWidth(),
                            decodedImage.getHeight(),
                            decodedImage.getHotspotX(),
                            decodedImage.getHotspotY()
                    ).destroy();
                    throw e;
                }
                return new AnimatedTexture(
                        frames,
                        decodedImage.getWidth(),
                        decodedImage.getHeight(),
                        decodedImage.getHotspotX(),
                        decodedImage.getHotspotY()
                );
            }

            NativeImage imageData = decodedImage.getStaticImage();
            if (imageData == null) return null;
            TextureInfo info = uploadImage(cacheKey, imageData);
            return new StaticTexture(
                    info.textureId,
                    info.location,
                    info.width,
                    info.height,
                    decodedImage.getHotspotX(),
                    decodedImage.getHotspotY()
            );
        } catch (Exception e) {
            ApricityUI.LOGGER.warn("[Image] Failed to upload decoded texture, path={}", cacheKey, e);
            return null;
        } finally {
            decodedImage.close();
        }
    }

    private static DecodedImage loadStaticTexturePng(String cacheKey, byte[] data) {
        try (InputStream bis = new ByteArrayInputStream(data)) {
            NativeImage image = NativeImage.read(bis);
            return DecodedImage.ofStatic(image);
        } catch (IOException e) {
            ApricityUI.LOGGER.warn(
                    "[Image] PNG decode failed, path={}, header={}",
                    cacheKey,
                    previewHeader(data),
                    e
            );
            return null;
        }
    }

    private static DecodedImage loadStaticTextureWithImageIo(String cacheKey, byte[] data, String detectedFormat) {
        try (InputStream bis = new ByteArrayInputStream(data)) {
            BufferedImage bufferedImage = ImageIO.read(bis);
            if (bufferedImage == null) {
                return null;
            }
            ApricityUI.LOGGER.info(
                    "[Image] Decoded non-PNG static image via ImageIO fallback, path={}, detectedFormat={}",
                    cacheKey,
                    detectedFormat
            );
            return DecodedImage.ofStatic(convertToNative(bufferedImage));
        } catch (IOException e) {
            ApricityUI.LOGGER.warn(
                    "[Image] Non-PNG static decode failed, path={}, detectedFormat={}, header={}",
                    cacheKey,
                    detectedFormat,
                    previewHeader(data),
                    e
            );
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
            ApricityUI.LOGGER.warn("[Image] GIF decode failed", e);
            return null;
        }
    }

    private static String detectFormat(byte[] data) {
        if (startsWith(data, (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A)) {
            return "png";
        }
        if (startsWithAscii(data, "GIF87a") || startsWithAscii(data, "GIF89a")) {
            return "gif";
        }
        if (startsWith(data, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)) {
            return "jpeg";
        }
        if (startsWithAscii(data, "BM")) {
            return "bmp";
        }
        if (data.length >= 12
                && startsWithAscii(data, "RIFF")
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P') {
            return "webp";
        }
        String ascii = previewAscii(data).toLowerCase(Locale.ROOT);
        if (ascii.startsWith("<!doctype") || ascii.startsWith("<html") || ascii.startsWith("<?xml")) {
            return "html";
        }
        if (ascii.startsWith("{") || ascii.startsWith("[")) {
            return "json";
        }
        return "unknown";
    }

    private static boolean startsWith(byte[] data, byte... prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] data, String prefix) {
        byte[] bytes = prefix.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        return startsWith(data, bytes);
    }

    private static String previewHeader(byte[] data) {
        return "hex=" + previewHex(data, 12) + ", ascii=" + previewAscii(data);
    }

    private static String previewHex(byte[] data, int limit) {
        StringBuilder builder = new StringBuilder();
        int actualLimit = Math.min(limit, data.length);
        for (int i = 0; i < actualLimit; i++) {
            if (i > 0) builder.append(' ');
            builder.append(String.format("%02x", data[i] & 0xFF));
        }
        return builder.toString();
    }

    private static String previewAscii(byte[] data) {
        StringBuilder builder = new StringBuilder();
        int actualLimit = Math.min(16, data.length);
        for (int i = 0; i < actualLimit; i++) {
            int b = data[i] & 0xFF;
            builder.append(b >= 32 && b <= 126 ? (char) b : '.');
        }
        return builder.toString();
    }

    private static DecodedImage loadCurTexture(byte[] data) {
        CursorFrame frame = decodeCursorContainer(data);
        if (frame == null || frame.image == null) return null;
        return DecodedImage.ofStatic(frame.image, frame.hotspotX, frame.hotspotY);
    }

    private static DecodedImage loadAniTexture(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.remaining() < 12) return null;
        if (buffer.getInt() != fourCC("RIFF")) return null;
        buffer.getInt();
        if (buffer.getInt() != fourCC("ACON")) return null;

        ArrayList<byte[]> iconChunks = new ArrayList<>();
        ArrayList<Integer> sequence = new ArrayList<>();
        ArrayList<Integer> rates = new ArrayList<>();
        int defaultRateJiffies = 6;

        while (buffer.remaining() >= 8) {
            int chunkId = buffer.getInt();
            int chunkSize = buffer.getInt();
            if (chunkSize < 0 || chunkSize > buffer.remaining()) break;

            int chunkEnd = buffer.position() + chunkSize;
            if (chunkId == fourCC("anih")) {
                if (chunkSize >= 36) {
                    buffer.getInt(); // cbSizeOf
                    buffer.getInt(); // cFrames
                    buffer.getInt(); // cSteps
                    buffer.getInt(); // cx
                    buffer.getInt(); // cy
                    buffer.getInt(); // cBitCount
                    buffer.getInt(); // cPlanes
                    defaultRateJiffies = Math.max(1, buffer.getInt());
                    buffer.getInt(); // flags
                }
            } else if (chunkId == fourCC("rate")) {
                while (buffer.position() + 4 <= chunkEnd) {
                    rates.add(buffer.getInt());
                }
            } else if (chunkId == fourCC("seq ")) {
                while (buffer.position() + 4 <= chunkEnd) {
                    sequence.add(buffer.getInt());
                }
            } else if (chunkId == fourCC("LIST") && chunkSize >= 4) {
                int listType = buffer.getInt();
                if (listType == fourCC("fram")) {
                    while (buffer.position() + 8 <= chunkEnd) {
                        int subChunkId = buffer.getInt();
                        int subChunkSize = buffer.getInt();
                        if (subChunkSize < 0 || buffer.position() + subChunkSize > chunkEnd) break;
                        if (subChunkId == fourCC("icon")) {
                            byte[] iconData = new byte[subChunkSize];
                            buffer.get(iconData);
                            iconChunks.add(iconData);
                        } else {
                            buffer.position(buffer.position() + subChunkSize);
                        }
                        if ((subChunkSize & 1) != 0 && buffer.position() < chunkEnd) {
                            buffer.get();
                        }
                    }
                }
            }

            buffer.position(chunkEnd);
            if ((chunkSize & 1) != 0 && buffer.hasRemaining()) {
                buffer.get();
            }
        }

        if (iconChunks.isEmpty()) return null;

        ArrayList<NativeImage> frames = new ArrayList<>();
        ArrayList<Integer> delays = new ArrayList<>();
        int hotspotX = 0;
        int hotspotY = 0;

        try {
            int steps = sequence.isEmpty() ? iconChunks.size() : sequence.size();
            for (int i = 0; i < steps; i++) {
                int iconIndex = sequence.isEmpty() ? i : sequence.get(i);
                if (iconIndex < 0 || iconIndex >= iconChunks.size()) continue;
                CursorFrame frame = decodeCursorContainer(iconChunks.get(iconIndex));
                if (frame == null || frame.image == null) continue;
                if (frames.isEmpty()) {
                    hotspotX = frame.hotspotX;
                    hotspotY = frame.hotspotY;
                }
                frames.add(frame.image);
                int rate = i < rates.size() ? rates.get(i) : defaultRateJiffies;
                delays.add(Math.max(20, Math.max(1, rate) * 1000 / 60));
            }

            DecodedImage decodedImage = DecodedImage.ofAnimated(frames, delays, hotspotX, hotspotY);
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

    private static CursorFrame decodeCursorContainer(byte[] data) {
        if (data == null || data.length < 22) return null;
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int reserved = Short.toUnsignedInt(buffer.getShort());
        int type = Short.toUnsignedInt(buffer.getShort());
        int count = Short.toUnsignedInt(buffer.getShort());
        if (reserved != 0 || (type != 1 && type != 2) || count <= 0) return null;

        CursorDirEntry selected = null;
        for (int i = 0; i < count; i++) {
            if (buffer.remaining() < 16) return null;
            int widthRaw = Byte.toUnsignedInt(buffer.get());
            int heightRaw = Byte.toUnsignedInt(buffer.get());
            buffer.get(); // color count
            buffer.get(); // reserved
            int hotspotX = Short.toUnsignedInt(buffer.getShort());
            int hotspotY = Short.toUnsignedInt(buffer.getShort());
            int bytesInRes = buffer.getInt();
            int imageOffset = buffer.getInt();

            int width = widthRaw == 0 ? 256 : widthRaw;
            int height = heightRaw == 0 ? 256 : heightRaw;
            CursorDirEntry entry = new CursorDirEntry(width, height, hotspotX, hotspotY, bytesInRes, imageOffset);
            if (selected == null || (entry.width * entry.height) > (selected.width * selected.height)) {
                selected = entry;
            }
        }

        if (selected == null) return null;
        int end = selected.imageOffset + selected.bytesInRes;
        if (selected.imageOffset < 0 || end > data.length || selected.imageOffset >= end) return null;

        byte[] imageData = new byte[selected.bytesInRes];
        System.arraycopy(data, selected.imageOffset, imageData, 0, selected.bytesInRes);
        NativeImage image = decodeCursorImageData(imageData, selected.width, selected.height);
        if (image == null) return null;
        return new CursorFrame(image, selected.hotspotX, selected.hotspotY);
    }

    private static NativeImage decodeCursorImageData(byte[] imageData, int entryWidth, int entryHeight) {
        if (imageData.length >= 8
                && (imageData[0] & 0xFF) == 0x89
                && imageData[1] == 0x50
                && imageData[2] == 0x4E
                && imageData[3] == 0x47) {
            try (InputStream input = new ByteArrayInputStream(imageData)) {
                return NativeImage.read(input);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        ByteBuffer dib = ByteBuffer.wrap(imageData).order(ByteOrder.LITTLE_ENDIAN);
        if (dib.remaining() < 40) return null;

        int headerSize = dib.getInt();
        if (headerSize < 40 || imageData.length < headerSize) return null;

        int width = dib.getInt();
        int storedHeight = dib.getInt();
        dib.getShort(); // planes
        int bitCount = Short.toUnsignedInt(dib.getShort());
        int compression = dib.getInt();
        dib.getInt(); // size image
        dib.getInt(); // x ppm
        dib.getInt(); // y ppm
        dib.getInt(); // clr used
        dib.getInt(); // clr important

        if (width <= 0) width = entryWidth;
        int height = storedHeight > 0 ? storedHeight / 2 : entryHeight;
        if (height <= 0) height = entryHeight;

        int xorOffset = headerSize;
        if (bitCount <= 8) {
            int colorCount = 1 << bitCount;
            xorOffset += colorCount * 4;
        }
        if (xorOffset >= imageData.length) return null;

        int rowStride;
        boolean hasAlpha = false;
        if (compression == 0 && bitCount == 32) {
            rowStride = width * 4;
            hasAlpha = scanForAlpha(imageData, xorOffset, width, height, rowStride);
        } else if (compression == 0 && bitCount == 24) {
            rowStride = align4(width * 3);
        } else if (compression == 0 && bitCount == 8) {
            rowStride = align4(width);
        } else {
            return null;
        }

        int andOffset = xorOffset + rowStride * height;
        int andStride = align4((width + 7) / 8);
        NativeImage image = new NativeImage(width, height, false);

        for (int y = 0; y < height; y++) {
            int srcY = height - 1 - y;
            int xorRow = xorOffset + srcY * rowStride;
            int andRow = andOffset + srcY * andStride;
            for (int x = 0; x < width; x++) {
                int a = 255;
                int r;
                int g;
                int b;
                if (bitCount == 32) {
                    int pixelOffset = xorRow + x * 4;
                    if (pixelOffset + 3 >= imageData.length) {
                        image.close();
                        return null;
                    }
                    b = imageData[pixelOffset] & 0xFF;
                    g = imageData[pixelOffset + 1] & 0xFF;
                    r = imageData[pixelOffset + 2] & 0xFF;
                    a = imageData[pixelOffset + 3] & 0xFF;
                    if (!hasAlpha && isMaskTransparent(imageData, andRow, x)) {
                        a = 0;
                    }
                } else if (bitCount == 24) {
                    int pixelOffset = xorRow + x * 3;
                    if (pixelOffset + 2 >= imageData.length) {
                        image.close();
                        return null;
                    }
                    b = imageData[pixelOffset] & 0xFF;
                    g = imageData[pixelOffset + 1] & 0xFF;
                    r = imageData[pixelOffset + 2] & 0xFF;
                    if (isMaskTransparent(imageData, andRow, x)) {
                        a = 0;
                    }
                } else {
                    int pixelOffset = xorRow + x;
                    if (pixelOffset >= imageData.length) {
                        image.close();
                        return null;
                    }
                    int paletteOffset = headerSize + (imageData[pixelOffset] & 0xFF) * 4;
                    if (paletteOffset + 3 >= imageData.length) {
                        image.close();
                        return null;
                    }
                    b = imageData[paletteOffset] & 0xFF;
                    g = imageData[paletteOffset + 1] & 0xFF;
                    r = imageData[paletteOffset + 2] & 0xFF;
                    if (isMaskTransparent(imageData, andRow, x)) {
                        a = 0;
                    }
                }
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, abgr);
            }
        }

        return image;
    }

    private static boolean scanForAlpha(byte[] imageData, int xorOffset, int width, int height, int rowStride) {
        for (int y = 0; y < height; y++) {
            int row = xorOffset + y * rowStride;
            for (int x = 0; x < width; x++) {
                int pixelOffset = row + x * 4 + 3;
                if (pixelOffset >= imageData.length) return false;
                if ((imageData[pixelOffset] & 0xFF) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isMaskTransparent(byte[] imageData, int andRow, int x) {
        int byteOffset = andRow + (x / 8);
        if (byteOffset < 0 || byteOffset >= imageData.length) return false;
        int bit = 7 - (x % 8);
        return ((imageData[byteOffset] >> bit) & 1) != 0;
    }

    private static int align4(int value) {
        return (value + 3) & ~3;
    }

    private static int fourCC(String value) {
        return value.charAt(0)
                | (value.charAt(1) << 8)
                | (value.charAt(2) << 16)
                | (value.charAt(3) << 24);
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
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath("apricityui", "dynamic/" + sanitizedPath);

        Minecraft.getInstance().getTextureManager().register(location, new SimpleTextureWrapper(textureId));
        return new TextureInfo(textureId, location, image.getWidth(), image.getHeight());
    }

    private record TextureInfo(int textureId, ResourceLocation location, int width, int height) {
    }

    public interface ITexture {
        ResourceLocation getLocation();

        int getWidth();

        int getHeight();

        default int getHotspotX() {
            return 0;
        }

        default int getHotspotY() {
            return 0;
        }

        void destroy();
    }

    public static class StaticTexture implements ITexture {
        private final int textureId;
        private final ResourceLocation location;
        private final int width;
        private final int height;
        private final int hotspotX;
        private final int hotspotY;

        public StaticTexture(int textureId, ResourceLocation location, int width, int height, int hotspotX, int hotspotY) {
            this.textureId = textureId;
            this.location = location;
            this.width = width;
            this.height = height;
            this.hotspotX = hotspotX;
            this.hotspotY = hotspotY;
        }

        @Override
        public ResourceLocation getLocation() {
            return location;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getHotspotX() {
            return hotspotX;
        }

        @Override
        public int getHotspotY() {
            return hotspotY;
        }

        @Override
        public void destroy() {
            RenderSystem.recordRenderCall(() -> TextureUtil.releaseTextureId(textureId));
        }
    }

    public static class AnimatedTexture implements ITexture {
        public record Frame(ResourceLocation location, int textureId, int durationMs) {
        }

        private final List<Frame> frames;
        private final int width;
        private final int height;
        private final int totalDuration;
        private final int hotspotX;
        private final int hotspotY;

        public AnimatedTexture(List<Frame> frames, int width, int height, int hotspotX, int hotspotY) {
            this.frames = frames;
            this.width = width;
            this.height = height;
            this.totalDuration = frames.stream().mapToInt(Frame::durationMs).sum();
            this.hotspotX = hotspotX;
            this.hotspotY = hotspotY;
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

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getHotspotX() {
            return hotspotX;
        }

        @Override
        public int getHotspotY() {
            return hotspotY;
        }

        @Override
        public void destroy() {
            for (Frame frame : frames) {
                RenderSystem.recordRenderCall(() -> TextureUtil.releaseTextureId(frame.textureId));
            }
        }
    }

    public static class SimpleTextureWrapper extends AbstractTexture {
        private final int textureId;

        public SimpleTextureWrapper(int textureId) {
            this.textureId = textureId;
        }

        @Override
        public int getId() {
            return textureId;
        }

        @Override
        public void load(ResourceManager manager) {
        }
    }

    private record CursorDirEntry(int width, int height, int hotspotX, int hotspotY, int bytesInRes, int imageOffset) {
    }

    private record CursorFrame(NativeImage image, int hotspotX, int hotspotY) {
    }
}
