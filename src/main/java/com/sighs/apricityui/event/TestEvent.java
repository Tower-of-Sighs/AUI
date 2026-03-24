package com.sighs.apricityui.event;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

@EventBusSubscriber(modid = ApricityUI.MOD_ID)
public class TestEvent {

    @SubscribeEvent
    public static void tick(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof LocalPlayer) {
//            String path = "test/index.html";
            String path = "devtools/example.html";
            Document.refreshAll();
            if (Document.get(path).isEmpty()) Document.create(path);
        }
    }
}
