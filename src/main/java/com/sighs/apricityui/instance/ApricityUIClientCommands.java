package com.sighs.apricityui.instance;

import com.mojang.brigadier.CommandDispatcher;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only commands used to exercise ApricityUI's in-world surfaces. */
@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class ApricityUIClientCommands {
    private static final String TEST_DOCUMENT_PATH = "tests/world-window-command.html";
    private static final int TEST_MAX_DISTANCE = 32;
    private static final double TEST_FORWARD_DISTANCE = 2.0d;
    private static WorldWindow testWindow;

    private ApricityUIClientCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("aui")
                .then(Commands.literal("worldwindow")
                        .executes(context -> spawnTestWindow(context.getSource()))));
    }

    private static int spawnTestWindow(CommandSourceStack source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            source.sendFailure(Component.literal("AUI worldwindow requires an active client world."));
            return 0;
        }

        if (testWindow != null) {
            WorldWindow.removeWindow(testWindow);
        }

        Vec3 player = minecraft.player.position();
        Vec3 position = player
                .add(0.0d, minecraft.player.getEyeHeight(), 0.0d)
                .add(minecraft.player.getLookAngle().scale(TEST_FORWARD_DISTANCE));
        testWindow = new FollowFacingWorldWindow(
                TEST_DOCUMENT_PATH, position, TEST_MAX_DISTANCE, 0.0f);
        WorldWindow.addWindow(testWindow);

        source.sendSuccess(() -> Component.literal("Spawned AUI test worldwindow."), false);
        return 1;
    }
}
