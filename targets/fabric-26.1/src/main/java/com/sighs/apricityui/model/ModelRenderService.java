package com.sighs.apricityui.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.PoseMatrices;
import com.sighs.apricityui.spi.AuiModelRenderRequest;
import com.sighs.apricityui.spi.AuiModelRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ModelRenderService implements AuiModelRenderService {
    public static final ModelRenderService INSTANCE = new ModelRenderService();
    private static final Cache<UUID, Skin> SKINS = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterAccess(15, TimeUnit.MINUTES).build();
    private static final Cache<String, Boolean> WARNINGS = CacheBuilder.newBuilder().maximumSize(256).build();
    private static int generation;

    private ModelRenderService() { }

    public static void invalidate() {
        generation++;
        WARNINGS.invalidateAll();
    }

    @Override
    public Preview create(ModelSpec spec) {
        if (spec.source().isEmpty()) return null;
        try {
            return switch (spec.type()) {
                case "player" -> new Session(spec, UUID.fromString(spec.source()), null);
                case "bakedmodel" -> {
                    Identifier id = Identifier.tryParse(spec.source());
                    if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id))
                        throw new IllegalArgumentException("Unknown entity type: " + spec.source());
                    yield new Session(spec, null, BuiltInRegistries.ENTITY_TYPE.getValue(id));
                }
                default -> throw new IllegalArgumentException("Unknown model type: " + spec.type());
            };
        } catch (IllegalArgumentException failure) {
            warn(spec, failure);
            return null;
        }
    }

    private static void warn(ModelSpec spec, Exception failure) {
        String key = spec.type() + ":" + spec.source() + ":" + failure.getMessage();
        if (WARNINGS.asMap().putIfAbsent(key, true) == null)
            ApricityUI.LOGGER.warn("Cannot render <model type='{}'>{}: {}", spec.type(), spec.source(), failure.toString());
    }

    private static final class Skin {
        volatile Identifier texture;
        volatile boolean slim;
        volatile int revision;

        Skin(UUID uuid) {
            var fallback = DefaultPlayerSkin.get(uuid);
            texture = fallback.body().texturePath();
            slim = fallback.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
            Minecraft client = Minecraft.getInstance();
            java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> client.services().profileResolver().fetchById(uuid), net.minecraft.util.Util.backgroundExecutor())
                    .thenCompose(profile -> client.getSkinManager().get(profile.orElseGet(() -> new GameProfile(uuid, ""))))
                    .thenAcceptAsync(result -> {
                        var loaded = result.orElse(fallback);
                        texture = loaded.body().texturePath();
                        slim = loaded.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
                        revision++;
                    }, client).exceptionally(failure -> null);
        }
    }

    private static final class Session implements Preview {
        private final ModelSpec source;
        private final EntityType<?> entityType;
        private final Skin skin;
        private ClientLevel level;
        private Entity entity;
        private ModelMesh<RenderType> mesh;
        private int seenGeneration = -1, skinRevision = -1;
        private boolean closed, failed;

        Session(ModelSpec source, UUID uuid, EntityType<?> entityType) {
            this.source = source;
            this.entityType = entityType;
            this.skin = uuid == null ? null : SKINS.asMap().computeIfAbsent(uuid, Skin::new);
        }

        @Override
        public boolean tick() {
            if (closed) return false;
            ClientLevel current = Minecraft.getInstance().level;
            boolean changed = seenGeneration != generation || skin != null && skinRevision != skin.revision;
            if (level != current) {
                level = current;
                entity = null;
                changed = true;
            }
            if (changed) {
                seenGeneration = generation;
                skinRevision = skin == null ? 0 : skin.revision;
                mesh = null;
                failed = false;
            }
            return changed;
        }

        @Override
        public void render(AuiModelRenderRequest request) {
            if (closed) return;
            tick();
            if (failed || skin == null && level == null) return;
            try {
                if (mesh == null) mesh = capture();
                if (mesh == null || mesh.isEmpty()) return;
                draw(mesh, request);
            } catch (Exception failure) {
                failed = true;
                mesh = null;
                warn(source, failure);
            }
        }

        private ModelMesh<RenderType> capture() {
            Minecraft client = Minecraft.getInstance();
            ModelVertexCapture output = new ModelVertexCapture();
            PoseStack pose = new PoseStack();
            if (skin != null) {
                PlayerModel model = new PlayerModel(client.getEntityModels()
                        .bakeLayer(skin.slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), skin.slim);
                model.root().getAllParts().forEach(part -> part.visible = true);
                pose.scale(1, -1, -1);
                model.renderToBuffer(pose, output.getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.entityTranslucent(skin.texture)),
                        LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
            } else {
                if (entity == null) {
                    entity = entityType.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
                    if (!(entity instanceof LivingEntity)) throw new IllegalArgumentException("Not a living entity: " + source.source());
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                    entity.setSilent(true);
                    entity.setNoGravity(true);
                }
                EntityRenderer<? super Entity, ?> nativeRenderer = client.getEntityRenderDispatcher().getRenderer(entity);
                captureNative(nativeRenderer, entity, pose, output);
            }
            output.finish();
            return output.mesh;
        }

        @Override
        public void close() {
            closed = true;
            entity = null;
            level = null;
            mesh = null;
        }
    }

    private static <S extends net.minecraft.client.renderer.entity.state.EntityRenderState> void captureNative(
            EntityRenderer<? super Entity, S> renderer, Entity entity, PoseStack pose, ModelVertexCapture output) {
        S state = renderer.createRenderState(entity, 0);
        state.ageInTicks = 0;
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        state.nameTag = null;
        state.displayFireAnimation = false;
        NativeModelCapture submissions = new NativeModelCapture(output);
        renderer.submit(state, pose, submissions, new net.minecraft.client.renderer.state.level.CameraRenderState());
        submissions.finish();
    }

    private static void draw(ModelMesh<RenderType> mesh, AuiModelRenderRequest request) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        buffers.endBatch();
        Matrix4f transform = new Matrix4f(PoseMatrices.of(request.poseStack())).translate(0, 0, Base.getGuiItemModelZ());
        var faces = mesh.layout(request.spec(), request.x(), request.y(), request.width(), request.height(),
                transform, PoseMatrices.normal(request.poseStack()));
        RenderType previous = null;
        Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        var target = com.sighs.apricityui.render.OutputTargets.pushBufferTarget(Minecraft.getInstance().getMainRenderTarget());
        try {
            for (var face : faces) {
                // Preserve painter order when native translucent batches would re-sort flattened faces.
                if (previous != null && (request.spec().flat() || previous != face.material())) buffers.endBatch();
                previous = face.material();
                VertexConsumer vertices = buffers.getBuffer(face.material());
                for (var v : face.vertices()) vertices.addVertex(v.x(), v.y(), v.z()).setColor(v.color())
                        .setUv(v.u(), v.v()).setOverlay(v.overlay()).setLight(v.light())
                        .setNormal(v.nx(), v.ny(), v.nz());
            }
        } finally {
            try { buffers.endBatch(); }
            finally { target.close(); }
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
        }
    }
}
