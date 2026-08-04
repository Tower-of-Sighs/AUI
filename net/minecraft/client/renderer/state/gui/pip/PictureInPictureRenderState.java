package net.minecraft.client.renderer.state.gui.pip;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.ScreenArea;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface PictureInPictureRenderState extends ScreenArea {
    Matrix3x2f IDENTITY_POSE = new Matrix3x2f();

    int x0();

    int x1();

    int y0();

    int y1();

    float scale();

    default Matrix3x2f pose() {
        return IDENTITY_POSE;
    }

    @Nullable ScreenRectangle scissorArea();

    static @Nullable ScreenRectangle getBounds(int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle bounds = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
        return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
    }
}
