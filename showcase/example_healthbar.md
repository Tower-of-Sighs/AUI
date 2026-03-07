---
title: 使用 AUI 实现生物血量显示功能
description: 这个示例使用了 AUI 中的 WorldWindow 类，是通过在每个需要显示血量的生物上添加一个影像血条来实现的，仅做参考。
last_update:
  date: 2/27/2026
  author: Terry_MC
---

# 使用 AUI 实现生物血量显示功能

这个示例使用了 AUI 中的`WorldWindow`类，是通过在每个需要显示血量的生物上添加一个[影像](/ApricityUI/guide/ui-types#影像)血条来实现的，仅做参考。

`MobHealthDisplay.java`

```java
package top.terry_mc.mobhealthdisplay;

import com.sighs.apricityui.instance.WorldWindow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent;
//import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = Entrypoint.MODID, value = Dist.CLIENT)
public class MobHealthDisplay {
    private static final Map<UUID, WorldWindow> healthBars = new HashMap<>();
    private static final Map<UUID, LivingEntity> trackedEntities = new HashMap<>();
    private static final Minecraft mc = Minecraft.getInstance();
    private static final float BAR_WIDTH = 100.0f;
    private static final float BAR_HEIGHT = 20.0f;
    private static final float SCALE = 1.5f / BAR_WIDTH;//1px对应多少Block
    private static final float Y_OFFSET = 0.8f;
    private static final int MAX_DISTANCE = 16;
    private static final int MAX_DISTANCE_SQUARED = MAX_DISTANCE * MAX_DISTANCE;

//    // 最好不要用这个，不然加载世界时的帧数爆降然后再逐渐升高
//    @SubscribeEvent
//    public static void onEntityJoin(EntityJoinLevelEvent event) {
//        if (event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity entity) {
//            if(entity instanceof LocalPlayer) return;
//            createHealthBar(entity);
//        }
//    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity entity) {
            removeHealthBar(entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        Level level = player.level();
        Vec3 playerPos = player.position();

        // 更新血量显示（每tick更新一次，不要放render里会很卡）
        for (Map.Entry<UUID, WorldWindow> entry : healthBars.entrySet()) {
            LivingEntity entity = trackedEntities.get(entry.getKey());
            if (entity != null) {
                updateHealthBarContent(entry.getValue(), entity);
            }
        }

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(MAX_DISTANCE))) {
            if (entity instanceof LocalPlayer) {
                continue;
            }
            UUID uuid = entity.getUUID();
            if (!healthBars.containsKey(uuid)) {
                createHealthBar(entity);
            }
        }
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, LivingEntity> entry : trackedEntities.entrySet()) {
            LivingEntity entity = entry.getValue();
            if (entity == null ||
                entity.position().distanceToSqr(playerPos) > MAX_DISTANCE_SQUARED) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            removeHealthBar(uuid);
        }
    }

    private static void createHealthBar(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return;
        }
        UUID uuid = entity.getUUID();
        WorldWindow window = new WorldWindow("mobhealthdisplay/mob_health.html",
                entity.position().add(0, entity.getBbHeight() + Y_OFFSET, 0),
                BAR_WIDTH, BAR_HEIGHT, MAX_DISTANCE);
        window.setScale(SCALE);
        WorldWindow.addWindow(window);
        healthBars.put(uuid, window);
        trackedEntities.put(uuid, entity);
    }

    private static void updateHealthBarContent(WorldWindow window, LivingEntity entity) {
        if (window == null || window.document == null) {
            return;
        }
        float maxHealth = entity.getMaxHealth();
        float currentHealth = entity.getHealth();
        float healthPercentage = ((currentHealth / maxHealth) * 100);
        // 文本直接放过来了，写cache没用，优化不了一点
        window.document.getElementById("health-bar").setAttribute("style", String.format("width: %.2f%%;", healthPercentage));
        window.document.getElementById("health-txt").innerText = String.format("%.1f/%.1f", currentHealth, maxHealth);
    }


    public static void removeHealthBar(UUID uuid) {
        WorldWindow window = healthBars.remove(uuid);
        if (window != null) {
            WorldWindow.removeWindow(window);
        }
        trackedEntities.remove(uuid);
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS || healthBars.isEmpty()) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        Vec3 playerPos = player.position();
        for (Map.Entry<UUID, LivingEntity> entry : trackedEntities.entrySet()) {
            LivingEntity entity = entry.getValue();
            if (entity == null) {
                continue;
            }
            Vec3 entityPos = entity.position();
            WorldWindow window = healthBars.get(entry.getKey());
            if (window != null) {
                double entityHeight = entity.getBbHeight();
                window.setPosition(entityPos.add(0, entityHeight + Y_OFFSET, 0));
                Vec3 direction = playerPos.subtract(entityPos).normalize();
                float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z)) + 180;
                window.setRotation(yaw, 0);
            }
        }
    }
}
```

`assets/apricityui/apricity/mobhealthdisplay/mob_health.html`

```html
<body>
<div class="health-container">
    <div id="health-bar" class="health-bar" style="width: 100%;"></div>
    <div id="health-text" class="health-text"><span id="health-txt" class="health-txt">20/20</span></div>
</div>
</body>

<link rel="stylesheet" href="global.css">

<style>
    body {
        margin: 0;
        padding: 0;
        overflow: hidden;
    }

    .health-container {
        width: 100px;
        height: 20px;
        background-color: rgba(0, 0, 0, 0.6);
        border-radius: 5px;
        padding: 1px;
        position: relative;
        overflow: hidden;
    }

    .health-bar {
        height: 100%;
        background-color: #ff3333;
        border-radius: 5px;
        /*transition似乎不大好用，没有效果*/
        transition: width 0.15s ease;
    }

    .health-text {
        position: absolute;
        width: 100%;
        height: 100%;
        top: 0;
        left: 0;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .health-txt {
        color: #ffffff;
        font-size: 20px;
        line-height: 100%;
        font-weight: bold;
    }
</style>
```
