package com.sighs.apricityui.instance;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class WorldWindowTestSpawner {
    private static final String TEST_DOC_PATH = "tests/world-window-acceptance.html";
    private static final float TEST_WIDTH = 150.0f;
    private static final float TEST_HEIGHT = 100.0f;
    private static final int TEST_MAX_DISTANCE = 8;
    private static final float TEST_FOLLOW_FACTOR = 0.3f;
    private static WorldWindow lastWindow;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vec3 playerPos = mc.player.position();
        AABB search = new AABB(playerPos, playerPos).inflate(64.0);
        ArmorStand target = null;
        for (ArmorStand stand : mc.level.getEntitiesOfClass(ArmorStand.class, search)) {
            if (stand.getName() != null && "auitest".equals(stand.getName().getString())) {
                target = stand;
                break;
            }
        }

        if (target == null) {
            if (lastWindow != null) {
                WorldWindow.removeWindow(lastWindow);
                lastWindow = null;
            }
            return;
        }

        Vec3 base = target.position().add(0.0, 1.5, 0.0);
        if (lastWindow == null) {
            WorldWindow window = new FollowFacingWorldWindow(TEST_DOC_PATH, base, TEST_WIDTH, TEST_HEIGHT, TEST_MAX_DISTANCE, TEST_FOLLOW_FACTOR);
            WorldWindow.addWindow(window);
            lastWindow = window;
        }
    }
}
