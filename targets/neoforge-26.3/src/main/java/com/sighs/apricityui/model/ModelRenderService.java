package com.sighs.apricityui.model;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.neoforge.ItemRenderService;
import com.sighs.apricityui.render.Base;
import com.sighs.apricityui.render.OutputTargets;
import com.sighs.apricityui.render.PoseMatrices;
import com.sighs.apricityui.spi.AuiModelRenderRequest;
import com.sighs.apricityui.spi.AuiModelRenderService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Matrix4f;

import java.util.UUID;

public final class ModelRenderService implements AuiModelRenderService {
    public static final ModelRenderService INSTANCE = new ModelRenderService();
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
                    if (spec.type().equals("gecko") && !net.neoforged.fml.ModList.get().isLoaded("geckolib"))
                        throw new IllegalArgumentException("GeckoLib is not loaded");
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

    private static final class Session implements Preview {
        private final ModelSpec source;
        private final UUID playerId;
        private final ResolvableProfile playerProfile;
        private final EntityType<?> entityType;
        private ClientLevel level;
        private Entity entity;
        private PlayerSkinRenderCache.RenderInfo skin;
        private ModelMesh<RenderType> mesh;
        private int seenGeneration = -1;
        private long seenAnimationTime = Long.MIN_VALUE;
        private boolean closed, failed;

        Session(ModelSpec source, UUID playerId, EntityType<?> entityType) {
            this.source = source;
            this.playerId = playerId;
            this.playerProfile = playerId == null ? null : ResolvableProfile.createUnresolved(playerId);
            this.entityType = entityType;
        }

        @Override
        public boolean tick() {
            if (closed) return false;
            Minecraft client = Minecraft.getInstance();
            ClientLevel current = client.level;
            boolean changed = seenGeneration != generation;
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
            if (playerProfile != null) {
                PlayerSkinRenderCache.RenderInfo currentSkin = client.playerSkinRenderCache().getOrDefault(playerProfile);
                if (!currentSkin.equals(skin)) {
                    skin = currentSkin;
                    changed = true;
                }
            }
            if (changed) {
                seenGeneration = generation;
                mesh = null;
                failed = false;
            }
            return changed;
        }

        @Override
        public void render(AuiModelRenderRequest request) {
            if (closed) return;
            tick();
            if (failed || playerId == null && level == null) return;
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
            if (playerId != null) {
                var playerSkin = skin.playerSkin();
                boolean slim = playerSkin.model() == PlayerModelType.SLIM;
                PlayerModel model = new PlayerModel(client.getEntityModels()
                        .bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
                model.root().getAllParts().forEach(part -> part.visible = true);
                pose.scale(1, -1, -1);
                model.renderToBuffer(pose, output.getBuffer(skin.renderType()), LightCoordsUtil.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY, -1);
            } else {
                if (entity == null) {
                    entity = entityType.create(level, EntitySpawnReason.COMMAND);
                    if (!(entity instanceof LivingEntity) && !source.type().equals("gecko"))
                        throw new IllegalArgumentException("Not a living entity: " + source.source());
                    entity.setCustomName(null);
                    entity.setCustomNameVisible(false);
                    entity.setSilent(true);
                    entity.setNoGravity(true);
                }
                EntityRenderer<? super Entity, ?> renderer = client.getEntityRenderDispatcher().getRenderer(entity);
                captureNative(renderer, entity, pose, output, source.type().equals("gecko"));
            }
            output.finish();
            return output.mesh;
        }

        @Override
        public void close() {
            closed = true;
            entity = null;
            level = null;
            skin = null;
            mesh = null;
        }
    }

    private static <S extends net.minecraft.client.renderer.entity.state.EntityRenderState> void captureNative(
            EntityRenderer<? super Entity, S> renderer, Entity entity, PoseStack pose, ModelVertexCapture output, boolean gecko) {
        S state = renderer.createRenderState(entity, 0);
        state.ageInTicks = gecko ? entity.tickCount : 0;
        state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        state.nameTag = null;
        state.displayFireAnimation = false;
        NativeModelCapture submissions = new NativeModelCapture(output);
        renderer.submit(state, pose, submissions, new CameraRenderState());
        submissions.finish();
    }

    private static void draw(ModelMesh<RenderType> mesh, AuiModelRenderRequest request) {
        Matrix4f transform = new Matrix4f(PoseMatrices.of(request.poseStack()))
                .translate(0, 0, Base.getGuiItemModelZ());
        var faces = mesh.layout(request.spec(), request.x(), request.y(), request.width(), request.height(),
                transform, PoseMatrices.normal(request.poseStack()));
        ItemRenderService.submitImmediately(OutputTargets.currentTarget(), true, collector -> {
            for (var face : faces) {
                collector.submitCustomGeometry(new PoseStack(), face.material(), (pose, vertices) -> {
                    for (var v : face.vertices()) {
                        vertices.addVertex(v.x(), v.y(), v.z()).setColor(v.color()).setUv(v.u(), v.v())
                                .setOverlay(v.overlay()).setLight(v.light()).setNormal(v.nx(), v.ny(), v.nz());
                    }
                });
            }
        });
    }
}
