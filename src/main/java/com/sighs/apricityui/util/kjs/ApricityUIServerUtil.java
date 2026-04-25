package com.sighs.apricityui.util.kjs;

import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.container.bind.ScreenOpenRequest;
import com.sighs.apricityui.registry.annotation.KJSBindings;
import net.minecraft.server.level.ServerPlayer;

@KJSBindings(value = "ApricityUI")
public class ApricityUIServerUtil {

    public static ScreenOpenRequest screen(String path) {
        return ScreenOpenRequest.of(path);
    }

    /**
     * @deprecated 请改用 {@link #screen(String)} 链式接口。
     */
    @Deprecated
    public static void openScreen(ServerPlayer player, String path, OpenBindPlan plan) {
        screen(path).withPlan(plan).open(player);
    }

    public static OpenBindPlan.Builder bind() {
        return OpenBindPlan.builder();
    }
}
