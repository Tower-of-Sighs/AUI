package com.sighs.apricityui.world;

import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class WorldWindowTestSpawner {
    private static final String TEST_DOC_PATH = "tests/world-window-acceptance.html";
    private static final int TEST_MAX_DISTANCE = 8;
    private static final float TEST_FOLLOW_FACTOR = 0.3f;
    private static WorldWindow lastWindow;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Vector3d playerPos = mc.player.position();
        AxisAlignedBB search = new AxisAlignedBB(playerPos, playerPos).inflate(64.0);
        ArmorStandEntity target = null;
        for (ArmorStandEntity stand : mc.level.getEntitiesOfClass(ArmorStandEntity.class, search)) {
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

        Vector3d base = target.position().add(0.0, 1.5, 0.0);
        if (lastWindow == null) {
            WorldWindow window = new WorldWindow(TEST_DOC_PATH, base, TEST_MAX_DISTANCE);
            window.setFollow(true);
            window.setFollowFactor(TEST_FOLLOW_FACTOR);
            window.setFacing(true);
            WorldWindow.addWindow(window);
            lastWindow = window;
        }
    }
}
