package com.sighs.apricityui.event;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = ApricityUI.MOD_ID)
public class TestEvent {

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        String path = "test/index.html";
        if (Document.get(path).isEmpty()) Document.create(path);
    }
}
