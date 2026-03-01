package com.sighs.apricityui.instance;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.sighs.apricityui.ApricityUI;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = ApricityUI.MOD_ID)
public class ApricityUICommand {

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        if (ApricityUI.isDevEnv()){
            dispatcher.register(Commands.literal(ApricityUI.MOD_ID)
                    .then(Commands.literal("open")
                            .then(Commands.argument("html", StringArgumentType.string())
                                    .executes(ApricityUICommand::openUI)
                            )
                    )
            );
        }
    }

    private static int openUI(CommandContext<CommandSourceStack> context) {
//        CommandSourceStack source = context.getSource();
//        ServerPlayer player = source.getPlayer();
//        if (player != null) {
//            String html = StringArgumentType.getString(context, "html");
//        }
        return 0;
    }
}
