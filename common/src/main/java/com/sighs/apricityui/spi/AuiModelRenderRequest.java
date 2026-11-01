package com.sighs.apricityui.spi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sighs.apricityui.model.ModelSpec;

public record AuiModelRenderRequest(PoseStack poseStack, ModelSpec spec, float x, float y,
                                    float width, float height) { }
