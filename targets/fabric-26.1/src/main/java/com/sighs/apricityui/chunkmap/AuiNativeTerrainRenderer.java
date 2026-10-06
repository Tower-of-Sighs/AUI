package com.sighs.apricityui.chunkmap;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sighs.apricityui.ApricityUI;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DynamicUniformStorage;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;

/** A native map scene with reusable geometry and shared entity depth. */
public final class AuiNativeTerrainRenderer implements AutoCloseable {
    public record Surface(long x, long z, int width, int depth, int step, byte[] rgba, int[] heights, long revision) { }
    public record Statistics(int visible, int resident, int loading, long residentBytes, int drawCalls,
                             int minecraftFps, double minecraftFrameMillis, double cpuMillis) { }
    private static final long OFFSCREEN_LIMIT = 768L << 20, UPLOAD_BUDGET = 8L << 20;
    private static final int COVERAGE_SIZE = 200;
    private static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("Color", VertexFormatElement.COLOR)
            .add("UV0", VertexFormatElement.UV0)
            .add("UV2", VertexFormatElement.UV2)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .build();
    private static final RenderPipeline OPAQUE = pipeline(false), TRANSPARENT = pipeline(true);
    private final ExecutorService decoding = Executors.newFixedThreadPool(
            Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 8), task -> {
                Thread thread = new Thread(task, "AUI native map geometry"); thread.setDaemon(true); return thread;
            });
    private final AtomicBoolean closed = new AtomicBoolean();
    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>(128, 0.75f, true);
    private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("AUI map projection");
    private final DynamicUniformStorage<Parameters> parameters = new DynamicUniformStorage<>("AUI map parameters", 16, 1024);
    private final AuiSurfaceRenderer surfaceRenderer = new AuiSurfaceRenderer();
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

    private static RenderPipeline pipeline(boolean transparent) {
        var shader = Identifier.fromNamespaceAndPath("apricityui", "core/native_map");
        return RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("apricityui", transparent ? "native_map_transparent" : "native_map_opaque"))
                .withVertexShader(shader).withFragmentShader(shader)
                .withUniform("MapParams", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexFormat(FORMAT, VertexFormat.Mode.TRIANGLES)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .withColorTargetState(new ColorTargetState(transparent ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.empty(),
                        15))
                .withCull(true)
                .build();
    }

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
                    GpuBuffer gpu = bytes == 0 ? null : RenderSystem.getDevice().createBuffer(() -> "AUI terrain vertices",
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices);
                    entry.gpu = new GpuMesh(gpu, mesh.opaque().count(), mesh.translucent().start(),
                            mesh.translucent().count(), bytes, mesh.minY(), mesh.maxY());
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
        updateCoverage(camera, source, masks);
        parameters.endFrame();
        var draws = new ArrayList<Draw>();
        for (Placed placed : geometries) {
            var transform = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(camera.viewMatrix()).translate(
                    (float)(placed.tile.x()*32.0-camera.x()),(float)-camera.y(),(float)(placed.tile.z()*32.0-camera.z())),
                    new Vector4f(1,1,1,1),new Vector3f(),new Matrix4f());
            var clip = parameters.writeUniform(new Parameters(placed.mask, brightness));
            draws.add(new Draw(placed.mesh,transform,clip));
        }
        frameCamera = camera; frameTarget = target; frameDraws = List.copyOf(draws);
        surfaceRenderer.update(surface);
        boolean surfaceDrawn = surface != null && surfaceRenderer.draw(camera,target,coverage.getTextureView(),
                coverageX,coverageZ,brightness,true,underground);
        render(false,!surfaceDrawn,underground);
        int calls = (surfaceDrawn ? 1 : 0) + (int)draws.stream().filter(draw -> draw.mesh.opaqueCount>0).count()
                + (int)draws.stream().filter(draw -> draw.mesh.translucentCount>0).count();
        statistics = new Statistics(geometries.size(),(int)entries.values().stream().filter(e -> e.gpu!=null).count(),
                (int)entries.values().stream().filter(e -> e.work!=null).count(),residentBytes,calls,
                Minecraft.getInstance().getFps(),Minecraft.getInstance().getFrameTimeNs()/1_000_000.0,(System.nanoTime()-started)/1_000_000.0);
        return true;
    }

    public void drawTransparent() {
        if (frameTarget == null || closed.get()) return;
        try { render(true,false,false); } finally { suspend(); }
    }

    private void render(boolean transparent, boolean clear, boolean underground) {
        var previousProjection = RenderSystem.getProjectionMatrixBuffer();
        var previousType = RenderSystem.getProjectionType();
        RenderSystem.setProjectionMatrix(projection.getBuffer(frameCamera.nativeProjectionMatrix(
                RenderSystem.getDevice().isZZeroToOne())),
                frameCamera.mode().equals("street") ? ProjectionType.PERSPECTIVE : ProjectionType.ORTHOGRAPHIC);
        int clearColor = (255 << 24) | ((int) ((underground ? 0 : 0.46F) * 255) << 16)
                | ((int) ((underground ? 0 : 0.67F) * 255) << 8) | (int) ((underground ? 0 : 0.82F) * 255);
        OptionalInt color = clear ? OptionalInt.of(clearColor) : OptionalInt.empty();
        var atlas = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "AUI native terrain",frameTarget.getColorTextureView(),color,frameTarget.getDepthTextureView(),
                clear ? OptionalDouble.of(1) : OptionalDouble.empty())) {
            pass.setPipeline(transparent ? TRANSPARENT : OPAQUE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("Sampler0",atlas.getTextureView(),atlas.getSampler());
            for (Draw draw : frameDraws) {
                int count = transparent ? draw.mesh.translucentCount : draw.mesh.opaqueCount;
                if (count == 0) continue;
                pass.setUniform("DynamicTransforms",draw.transform);
                pass.setUniform("MapParams",draw.parameters);
                pass.setVertexBuffer(0,draw.mesh.vertices);
                pass.draw(transparent ? draw.mesh.translucentStart : 0, count);
            }
        } finally { RenderSystem.setProjectionMatrix(previousProjection,previousType); }
    }

    private void updateCoverage(AuiMapCamera camera, AuiChunkTileSource source, Map<AuiChunkTiles.Tile,Integer> masks) {
        if (coverage == null) coverage = new DynamicTexture(() -> "AUI map coverage",COVERAGE_SIZE,COVERAGE_SIZE,true);
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
        for(int cz=0;cz<COVERAGE_SIZE;cz++) for(int cx=0;cx<COVERAGE_SIZE;cx++) pixels.setPixel(cx,cz,0);
        for(Placed placed:loaded) for(int q=0;q<4;q++) if((placed.mask&(1<<q))!=0) {
            int cx=placed.tile.x()*2+(q&1)-x,cz=placed.tile.z()*2+(q>>1)-z;
            if(cx>=0&&cx<COVERAGE_SIZE&&cz>=0&&cz<COVERAGE_SIZE) pixels.setPixel(cx,cz,0xFFFFFFFF);
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
        entries.clear(); surfaceRenderer.close();
        if(coverage!=null) coverage.close();
        projection.close(); parameters.close();
    }

    private static final class Entry {
        CompletableFuture<AuiNativeMesh> work;
        GpuMesh gpu;
        boolean failed;
        volatile boolean abandoned;
    }
    private record GpuMesh(GpuBuffer vertices,int opaqueCount,int translucentStart,int translucentCount,long bytes,float minY,float maxY) implements AutoCloseable {
        @Override public void close(){if(vertices!=null) vertices.close();}
    }
    private record Placed(AuiChunkTiles.Tile tile,int mask,GpuMesh mesh){}
    private record Draw(GpuMesh mesh,GpuBufferSlice transform,GpuBufferSlice parameters){}
    private record Parameters(float mask,float brightness) implements DynamicUniformStorage.DynamicUniform {
        @Override public void write(ByteBuffer buffer){buffer.putFloat(mask).putFloat(brightness).putFloat(0).putFloat(0);}
    }
}
