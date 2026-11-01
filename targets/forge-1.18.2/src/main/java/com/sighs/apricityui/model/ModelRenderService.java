package com.sighs.apricityui.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.PoseMatrices;
import com.sighs.apricityui.spi.AuiModelRenderRequest;
import com.sighs.apricityui.spi.AuiModelRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
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
                case "bakedmodel", "gecko" -> {
                    if (spec.type().equals("gecko") && !net.minecraftforge.fml.ModList.get().isLoaded("geckolib"))
                        throw new IllegalArgumentException("GeckoLib is not loaded");
                    ResourceLocation id = ResourceLocation.tryParse(spec.source());
                    if (id == null || !Registry.ENTITY_TYPE.containsKey(id))
                        throw new IllegalArgumentException("Unknown entity type: " + spec.source());
                    yield new Session(spec, null, Registry.ENTITY_TYPE.get(id));
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
        volatile ResourceLocation texture;
        volatile boolean slim;
        volatile int revision;

        Skin(UUID uuid) {
            texture = DefaultPlayerSkin.getDefaultSkin(uuid);
            slim = "slim".equals(DefaultPlayerSkin.getSkinModelName(uuid));
            Minecraft.getInstance().getSkinManager().registerSkins(new GameProfile(uuid, ""), (type, location, skin) -> {
                if (type != MinecraftProfileTexture.Type.SKIN) return;
                Minecraft.getInstance().execute(() -> {
                    texture = location;
                    slim = "slim".equals(skin.getMetadata("model"));
                    revision++;
                });
            }, true);
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
        private long seenAnimationTime = Long.MIN_VALUE;
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
            long gameTime = current == null ? Long.MIN_VALUE : current.getGameTime();
            if (source.type().equals("gecko") && gameTime != Long.MIN_VALUE && gameTime != seenAnimationTime) {
                if (entity != null) entity.tickCount++;
                seenAnimationTime = gameTime;
                mesh = null;
                failed = false;
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
                PlayerModel<LivingEntity> model = new PlayerModel<>(client.getEntityModels()
                        .bakeLayer(skin.slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), skin.slim);
                model.setAllVisible(true);
                pose.scale(1, -1, -1);
                model.renderToBuffer(pose, output.getBuffer(RenderType.entityTranslucent(skin.texture)),
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            } else {
                if (entity == null) {
                    entity = entityType.create(level);
                    if (!(entity instanceof LivingEntity) && !source.type().equals("gecko"))
                        throw new IllegalArgumentException("Not a living entity: " + source.source());
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                    entity.setSilent(true);
                    entity.setNoGravity(true);
                }
                EntityRenderer<? super Entity> nativeRenderer = client.getEntityRenderDispatcher().getRenderer(entity);
                nativeRenderer.render(entity, 0, 0, pose, output, LightTexture.FULL_BRIGHT);
            }
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

    private static void draw(ModelMesh<RenderType> mesh, AuiModelRenderRequest request) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        buffers.endBatch();
        Matrix4f transform = new Matrix4f(PoseMatrices.of(request.poseStack())).translate(0, 0, Base.getGuiItemModelZ());
        var faces = mesh.layout(request.spec(), request.x(), request.y(), request.width(), request.height(),
                transform, PoseMatrices.normal(request.poseStack()));
        RenderType previous = null;
        Lighting.setupFor3DItems();
        try {
            for (var face : faces) {
                // Preserve painter order when native translucent batches would re-sort flattened faces.
                if (previous != null && (request.spec().flat() || previous != face.material())) buffers.endBatch();
                previous = face.material();
                VertexConsumer vertices = buffers.getBuffer(face.material());
                for (var v : face.vertices()) vertices.vertex(v.x(), v.y(), v.z()).color(v.color())
                        .uv(v.u(), v.v()).overlayCoords(v.overlay()).uv2(v.light())
                        .normal(v.nx(), v.ny(), v.nz()).endVertex();
            }
        } finally {
            buffers.endBatch();
            Lighting.setupFor3DItems();
        }
    }
}
