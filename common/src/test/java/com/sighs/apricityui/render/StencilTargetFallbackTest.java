package com.sighs.apricityui.render;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归守卫：legacy 目标的 {@code currentTargetHasStencil()} 不能对主渲染目标自锁。
 *
 * <p>背景（资源管理器文件卡片的三角形角标变成正方形）：{@code Mask.stencilUsable()}
 * 依赖该判定来决定 clip-path 是走 stencil 还是退化成矩形 scissor。登记表
 * {@code STENCIL_TARGETS} 只由 {@code enableStencil()} 填充，而 {@code enableStencil()}
 * 只在 stencil 分支被选中时才调用。若判定在“没登记”时直接返回 false，就形成自锁：
 * 永远走 scissor 分支，clip-path 的三角形被裁成正方形，且再无机会登记。</p>
 *
 * <p>这些实现位于各 loader 目标里，common 的测试无法实例化它们，因此按源码断言
 * 该回退分支存在（同目录的 FilterRendererOptimizationTest 也是这种形式）。</p>
 */
class StencilTargetFallbackTest {

    private static final List<String> LEGACY_RENDER_SERVICES = List.of(
            "../../targets/forge-1.20.1/src/main/java/com/sighs/apricityui/forge/RenderService.java",
            "../../targets/neoforge-1.21.1/src/main/java/com/sighs/apricityui/neoforge/RenderService.java",
            "../../targets/fabric-1.20.1/src/main/java/com/sighs/apricityui/fabric/RenderService.java",
            "../../targets/fabric-1.21.1/src/main/java/com/sighs/apricityui/fabric/RenderService.java"
    );

    @Test
    void legacyStencilProbeFallsBackToMainTargetInsteadOfLockingOut() throws Exception {
        for (String service : LEGACY_RENDER_SERVICES) {
            String source = Files.readString(Path.of(service));
            int probe = source.indexOf("public boolean currentTargetHasStencil()");
            assertTrue(probe >= 0, service + " must implement currentTargetHasStencil()");

            String body = source.substring(probe, source.indexOf("\n    }", probe));
            assertTrue(body.contains("isMainFramebuffer("),
                    service + ": currentTargetHasStencil() must fall back to the main render "
                            + "target, otherwise stencilUsable() is permanently false and every "
                            + "clip-path degrades into a rectangular scissor");

            // 登记表扫描之后不允许直接 return false —— 那正是自锁的形态。
            int scan = body.indexOf("STENCIL_TARGETS.values()");
            assertTrue(scan >= 0, service + " must still consult the registered stencil targets");
            String afterScan = body.substring(scan);
            assertTrue(!afterScan.contains("return false;"),
                    service + ": returning false after the registry scan locks the stencil path "
                            + "out forever (triangles render as squares)");
        }
    }
}
