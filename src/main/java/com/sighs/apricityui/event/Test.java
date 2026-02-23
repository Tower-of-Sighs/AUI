package com.sighs.apricityui.event;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.instance.WorldWindow;
import com.sighs.apricityui.style.Animation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class Test {
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
//        if (Document.get("test/index.html").isEmpty()) {
//            Document.create("test/index.html");
//        }
//        if (Document.get("apricityui/reload.html").isEmpty()) {
//            Document.create("apricityui/reload.html");
//        } else {
//            Document document = Document.get("apricityui/reload.html").get(0);
////            System.out.println(Animation.isActive(document.querySelector(".text")));
//        }
    }
    @SubscribeEvent
    public static void playerTick(PlayerInteractEvent.RightClickBlock event) {
//        if (event.getSide().isClient() && event.getEntity().isShiftKeyDown()) {
//            // 在某个初始化或交互事件中
//            Vec3 pos = event.getPos().getCenter(); // 世界坐标
//            WorldWindow myWindow = new WorldWindow("apricityui/reload.html", pos, 700, 700, 10);
//
//// 设置朝向（例如朝南）
//            myWindow.setRotation(0, 0);
//// 调整大小 (0.01f 意味着 400px = 4 blocks 宽)
//            myWindow.setScale(0.01f);
//            WorldWindow.addWindow(myWindow);
//        }
    }
}
