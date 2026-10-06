package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.ApricityUI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** A native map scene with reusable geometry and shared entity depth. */
public final class AuiNativeTerrainRenderer implements AutoCloseable {
    public record Surface(long x, long z, int width, int depth, int step, byte[] rgba, int[] heights, long revision) { }
    public record Statistics(int visible, int resident, int loading, long residentBytes, int drawCalls,
                             int minecraftFps, double minecraftFrameMillis, double cpuMillis) { }
    private static final long OFFSCREEN_LIMIT = 768L << 20, UPLOAD_BUDGET = 8L << 20;
    private static final int COVERAGE_SIZE = 200;
    private final AuiMapGlProgram shader = new AuiMapGlProgram("native_map");
    private final ExecutorService decoding = Executors.newFixedThreadPool(
            net.minecraft.util.Mth.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 8), task -> {
                Thread thread = new Thread(task, "AUI native map geometry"); thread.setDaemon(true); return thread;
            });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(128, 0.75f, true);
    private final AuiSurfaceRenderer surfaceRenderer = new AuiSurfaceRenderer();
    private final AuiMapDepthTarget sceneDepth = new AuiMapDepthTarget();
    private DynamicTexture coverage;
    private long residentBytes, coverageRevision, installedCoverageRevision = -1;
    private int coverageX = Integer.MIN_VALUE, coverageZ = Integer.MIN_VALUE;
    private RenderTarget frameTarget;
    private AuiMapCamera frameCamera;
    private List<Draw> frameDraws = List.of();
    private volatile Statistics statistics = new Statistics(0,0,0,0,0,0,0,0);

    public AuiNativeTerrainRenderer() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Create map scenes on the client thread");
    }

    public AuiMapDepthTarget depthTarget() { return sceneDepth; }

    public boolean drawOpaque(AuiMapCamera camera, Surface surface, AuiChunkTileSource source,
                              Map<AuiChunkTiles.Tile,Integer> masks, RenderTarget target, float brightness, boolean underground) {
        return drawOpaque(camera, surface, source, masks, target, brightness, underground, false);
    }

    public boolean drawOpaque(AuiMapCamera camera, Surface surface, AuiChunkTileSource source,
                              Map<AuiChunkTiles.Tile,Integer> masks, RenderTarget target, float brightness,
                              boolean underground, boolean requirePrecise) {
        if (closed.get()) return false;
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Draw map scenes on the client thread");
        long started = System.nanoTime();
        suspend();
        var frustum = new FrustumIntersection(new Matrix4f(camera.projectionMatrix()).mul(camera.viewMatrix()));
        var wanted = new ArrayList<AuiChunkTiles.Tile>();
        var requestedKeys = new HashSet<String>();
        if (source != null) for (var tile : masks.keySet()) {
            String key = source.contentKey(tile);
            if (key == null) continue;
            Entry cached = entries.get(key);
            float minY = cached != null && cached.gpu != null ? cached.gpu.minY : source.minY();
            float maxY = cached != null && cached.gpu != null ? cached.gpu.maxY : source.maxY();
            if (frustum.testAab((float)(tile.x()*32.0-camera.x()), minY-(float)camera.y(),
                    (float)(tile.z()*32.0-camera.z()), (float)(tile.x()*32.0+32-camera.x()),
                    maxY-(float)camera.y(), (float)(tile.z()*32.0+32-camera.z()))) {
                wanted.add(tile); requestedKeys.add(key);
            }
        }
        wanted.sort(Comparator.comparingDouble(tile -> Math.hypot(tile.x()*32+16-camera.x(),tile.z()*32+16-camera.z())));
        var obsolete = entries.entrySet().iterator();
        while (obsolete.hasNext()) {
            var value = obsolete.next(); Entry entry = value.getValue();
            if (entry.work == null || requestedKeys.contains(value.getKey())) continue;
            entry.abandoned = true;
            entry.work.whenComplete((data,failure) -> { if (data != null) data.close(); });
            obsolete.remove();
        }
        int pending = (int)entries.values().stream().filter(entry -> entry.work != null).count();
        long uploaded = 0;
        var geometries = new ArrayList<Placed>();
        for (var tile : wanted) {
            String key = source.contentKey(tile);
            if (key == null) continue;
            Entry entry = entries.get(key);
            if (entry == null && pending < 8) {
                Entry scheduled = new Entry();
                scheduled.work = CompletableFuture.supplyAsync(() -> {
                    if (scheduled.abandoned || closed.get()) return null;
                    var data = source.tile(tile);
                    if (data == null || !key.equals(source.contentKey(tile))) return null;
                    try {
                        var mesh = AuiNativeMesh.decode(data.model());
                        if (closed.get() || scheduled.abandoned) { mesh.close(); return null; }
                        return mesh;
                    } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                }, decoding);
                entries.put(key, scheduled); entry = scheduled; pending++;
            }
            if (entry == null || entry.failed) continue;
            if (entry.gpu == null && entry.work.isDone() && uploaded < UPLOAD_BUDGET) {
                try (var mesh = entry.work.join()) {
                    if (mesh == null) { entries.remove(key); continue; }
                    ByteBuffer vertices = mesh.vertices();
                    long bytes = vertices.remaining();
                    entry.gpu = GpuMesh.upload(mesh);
                    uploaded += bytes; residentBytes += bytes; coverageRevision++;
                } catch (RuntimeException failure) {
                    entry.failed = true;
                    ApricityUI.LOGGER.error("Cannot upload AUI map tile {},{}", tile.x(), tile.z(), failure);
                } finally { entry.work = null; }
            }
            if (entry.gpu != null) geometries.add(new Placed(tile, masks.get(tile), entry.gpu));
        }
        trim(requestedKeys, source != null && source.persistentCache());
        if (requirePrecise && geometries.isEmpty() || surface == null && geometries.isEmpty()) return false;
        var draws = new ArrayList<Draw>();
        for (Placed placed : geometries) {
            Matrix4f transform = new Matrix4f(camera.viewMatrix()).translate(
                    (float) (placed.tile.x() * 32.0 - camera.x()), (float) -camera.y(),
                    (float) (placed.tile.z() * 32.0 - camera.z()));
            draws.add(new Draw(placed.mesh, transform, placed.mask, brightness));
        }
        frameCamera = camera; frameTarget = target; frameDraws = List.copyOf(draws);
        boolean surfaceDrawn;
        try (var state = new AuiMapRenderState(target, sceneDepth)) {
            updateCoverage(camera, source, masks);
            surfaceRenderer.update(surface);
            prepareState(false);
            GlStateManager._clearColor(underground ? 0 : 0.46F, underground ? 0 : 0.67F, underground ? 0 : 0.82F, 1);
            GlStateManager._clearDepth(1);
            GlStateManager._clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            surfaceDrawn = surface != null && surfaceRenderer.draw(camera, coverage.getId(), coverageX, coverageZ, brightness);
            renderMeshes(false);
        }
        int calls = (surfaceDrawn ? 1 : 0) + (int)draws.stream().filter(draw -> draw.mesh.opaqueCount>0).count()
                + (int)draws.stream().filter(draw -> draw.mesh.translucentCount>0).count();
        var frameTimer = Minecraft.getInstance().getFrameTimer();
        var frameLog = frameTimer.getLog();
        double frameMillis = frameLog[Math.floorMod(frameTimer.getLogEnd() - 1, frameLog.length)] / 1_000_000.0;
        statistics = new Statistics(geometries.size(),(int)entries.values().stream().filter(e -> e.gpu!=null).count(),
                (int)entries.values().stream().filter(e -> e.work!=null).count(),residentBytes,calls,
                Minecraft.fps, frameMillis,
                (System.nanoTime() - started) / 1_000_000.0);
        return true;
    }

    public void drawTransparent() {
        if (frameTarget == null || closed.get()) return;
        try (var state = new AuiMapRenderState(frameTarget, sceneDepth)) {
            prepareState(true);
            renderMeshes(true);
        } finally { suspend(); }
    }

    private static void prepareState(boolean transparent) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        if (transparent) {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        else RenderSystem.disableBlend();
    }

    private void renderMeshes(boolean transparent) {
        shader.bind();
        shader.matrix("ProjMat", frameCamera.projectionMatrix());
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);
        GlStateManager._bindTexture(Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS).getId());
        int previousMin = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER);
        int previousMag = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER);
        int minification = GL11.glGetTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL) > 0
                ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST;
        try {
            // Atlas animation uploads and other render types can replace its filtering between frames.
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minification);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            shader.sampler("Sampler0", 0);
            for (Draw draw : frameDraws) {
                int count = transparent ? draw.mesh.translucentCount : draw.mesh.opaqueCount;
                if (count == 0) continue;
                shader.matrix("ModelViewMat", draw.transform);
                shader.vector("Region", draw.mask, draw.brightness, transparent ? 1 : 0, 0);
                GlStateManager._glBindVertexArray(draw.mesh.array);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, transparent ? draw.mesh.translucentStart : 0, count);
            }
        } finally {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, previousMin);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, previousMag);
        }
    }

    private void updateCoverage(AuiMapCamera camera, AuiChunkTileSource source, Map<AuiChunkTiles.Tile,Integer> masks) {
        if (coverage == null) coverage = new DynamicTexture(COVERAGE_SIZE,COVERAGE_SIZE,true);
        int x=Math.floorDiv((int)Math.floor(camera.x()),16)-COVERAGE_SIZE/2;
        int z=Math.floorDiv((int)Math.floor(camera.z()),16)-COVERAGE_SIZE/2;
        var loaded = new ArrayList<Placed>();
        long key=coverageRevision;
        if (source!=null) for (var item:masks.entrySet()) {
            Entry entry=entries.get(source.contentKey(item.getKey()));
            if (entry==null || entry.gpu==null) continue;
            loaded.add(new Placed(item.getKey(),item.getValue(),entry.gpu));
            key+=31L*item.getKey().hashCode()+item.getValue();
        }
        if (coverageX==x && coverageZ==z && installedCoverageRevision==key) return;
        coverageX=x; coverageZ=z; installedCoverageRevision=key;
        var pixels=coverage.getPixels();
        for(int cz=0;cz<COVERAGE_SIZE;cz++) for(int cx=0;cx<COVERAGE_SIZE;cx++) pixels.setPixelRGBA(cx,cz,0);
        for(Placed placed:loaded) for(int q=0;q<4;q++) if((placed.mask&(1<<q))!=0) {
            int cx=placed.tile.x()*2+(q&1)-x,cz=placed.tile.z()*2+(q>>1)-z;
            if(cx>=0&&cx<COVERAGE_SIZE&&cz>=0&&cz<COVERAGE_SIZE) pixels.setPixelRGBA(cx,cz,0xFFFFFFFF);
        }
        coverage.upload();
    }

    private void trim(Set<String> active, boolean retain) {
        long bytes=entries.entrySet().stream().filter(e -> !active.contains(e.getKey())&&e.getValue().gpu!=null)
                .mapToLong(e -> e.getValue().gpu.bytes).sum();
        var iterator=entries.entrySet().iterator();
        while(bytes>(retain?OFFSCREEN_LIMIT:0)&&iterator.hasNext()) {
            var item=iterator.next(); Entry entry=item.getValue();
            if(active.contains(item.getKey())||entry.gpu==null) continue;
            long removed=entry.gpu.bytes; entry.gpu.close(); iterator.remove(); residentBytes-=removed; bytes-=removed;
        }
    }

    public void suspend() { frameTarget=null; frameDraws=List.of(); }
    public void resetSurface() { suspend(); surfaceRenderer.reset(); }
    public Statistics statistics() { return statistics; }

    @Override public void close() {
        if(!closed.compareAndSet(false,true)) return;
        suspend(); decoding.shutdownNow();
        for(Entry entry:entries.values()) {
            entry.abandoned=true;
            if(entry.gpu!=null) entry.gpu.close();
            if(entry.work!=null) entry.work.whenComplete((mesh,failure) -> {if(mesh!=null) mesh.close();});
        }
        entries.clear(); surfaceRenderer.close(); sceneDepth.close();
        if(coverage!=null) coverage.close();
        shader.close();
    }

    private static final class Entry {
        CompletableFuture<AuiNativeMesh> work;
        GpuMesh gpu;
        boolean failed;
        volatile boolean abandoned;
    }
    private record GpuMesh(int array, int buffer, int opaqueCount, int translucentStart,
                           int translucentCount, long bytes, float minY, float maxY) implements AutoCloseable {
        static GpuMesh upload(AuiNativeMesh mesh) {
            ByteBuffer vertices = mesh.vertices();
            int bytes = vertices.remaining();
            if (bytes == 0) return new GpuMesh(0, 0, 0, 0, 0, 0, mesh.minY(), mesh.maxY());
            int previousArray = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
            int array = GL30.glGenVertexArrays(), buffer = GL15.glGenBuffers();
            try {
                GlStateManager._glBindVertexArray(array);
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
                GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 32, 0L);
                GL20.glVertexAttribPointer(1, 4, GL11.GL_UNSIGNED_BYTE, true, 32, 12L);
                GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, 32, 16L);
                GL30.glVertexAttribIPointer(3, 2, GL11.GL_SHORT, 32, 24L);
                GL20.glVertexAttribPointer(4, 4, GL11.GL_BYTE, true, 32, 28L);
                for (int index = 0; index < 5; index++) GL20.glEnableVertexAttribArray(index);
                return new GpuMesh(array, buffer, mesh.opaque().count(), mesh.translucent().start(),
                        mesh.translucent().count(), bytes, mesh.minY(), mesh.maxY());
            } catch (RuntimeException failure) {
                GL15.glDeleteBuffers(buffer); GL30.glDeleteVertexArrays(array);
                throw failure;
            } finally {
                GlStateManager._glBindVertexArray(previousArray);
                GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
            }
        }
        @Override public void close() {
            if (buffer != 0) GL15.glDeleteBuffers(buffer);
            if (array != 0) GL30.glDeleteVertexArrays(array);
        }
    }
    private record Placed(AuiChunkTiles.Tile tile, int mask, GpuMesh mesh) { }
    private record Draw(GpuMesh mesh, Matrix4f transform, int mask, float brightness) { }
}
