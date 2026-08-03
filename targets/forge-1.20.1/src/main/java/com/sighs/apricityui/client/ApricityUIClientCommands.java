package com.sighs.apricityui.client;

import com.mojang.brigadier.CommandDispatcher;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;
import java.util.function.Consumer;
import com.sighs.apricityui.world.WorldWindow;
import com.sighs.apricityui.world.WorldWindowDisplayPrecision;

/** Client-only commands used to exercise ApricityUI's in-world surfaces. */
@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public final class ApricityUIClientCommands {
    private static final String TEST_DOCUMENT_PATH = "tests/world-window-command.html";
    private static final int TEST_MAX_DISTANCE = 32;
    private static final double TEST_FORWARD_DISTANCE = 2.0d;
    private static final float DEFAULT_NEAR_DEPTH_STEP = 0.00035f;
    private static final float DEFAULT_FAR_DEPTH_STEP = 0.003f;
    private static final float DEFAULT_DEPTH_NEAR_DISTANCE = 2.0f;
    private static final float DEFAULT_FOLLOW_FACTOR = 0.3f;
    private static WorldWindow testWindow;
    private static Document boundDocument;
    private static final Consumer<Event> DEBUG_EVENT_LISTENER = ApricityUIClientCommands::handleDebugEvent;
    private static final Consumer<Event> DEBUG_LIFECYCLE_LISTENER = ignored -> syncControls();

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
            testWindow = null;
            boundDocument = null;
        }

        Vec3 player = minecraft.player.position();
        Vec3 position = player
                .add(0.0d, minecraft.player.getEyeHeight(), 0.0d)
                .add(minecraft.player.getLookAngle().scale(TEST_FORWARD_DISTANCE));
        testWindow = new WorldWindow(TEST_DOCUMENT_PATH, position, TEST_MAX_DISTANCE);
        WorldWindow.addWindow(testWindow);
        bindDebugger(testWindow.document);

        source.sendSuccess(() -> Component.literal("Spawned AUI test worldwindow."), false);
        return 1;
    }

    private static void bindDebugger(Document document) {
        if (document == null || document.body == null) return;
        if (boundDocument != document) {
            document.body.addEventListener("input", DEBUG_EVENT_LISTENER);
            document.body.addEventListener("change", DEBUG_EVENT_LISTENER);
            document.body.addEventListener("click", DEBUG_EVENT_LISTENER);
            document.addEventListener("DOMContentLoaded", DEBUG_LIFECYCLE_LISTENER);
            boundDocument = document;
        }
        syncControls();
    }

    private static void handleDebugEvent(Event event) {
        if (testWindow == null || event == null) return;
        if ("DOMContentLoaded".equals(event.type)) {
            syncControls();
            return;
        }
        Element target = event.target instanceof Element element ? eventControl(element) : null;
        if (target != null
                && "click".equals(event.type)
                && "reset-settings".equals(target.getAttribute("id"))) {
            resetSettings();
            return;
        }
        if (target == null || !("input".equals(event.type) || "change".equals(event.type))) return;

        String id = target.getAttribute("id");
        if (id == null || id.isBlank()) return;
        switch (id) {
            case "max-display-distance" -> {
                // Editing the distance directly opts this test instance into its
                // override instead of silently discarding the slider value while
                // the source selector still says "global".
                Element mode = control("display-distance-mode");
                if (mode != null && !"instance".equalsIgnoreCase(value(mode, "global"))) {
                    mode.setValue("instance");
                }
                applyDisplayDistance();
            }
            case "display-distance-mode" -> applyDisplayDistance();
            case "max-distance" -> testWindow.setMaxDistance(readInt(target, testWindow.getMaxDistance()));
            case "depth-test" -> testWindow.setDepthTest(target.isChecked());
            case "lod-policy", "lod-custom", "lod-full-distance", "lod-reduced-distance" -> applyLodSettings();
            case "scale-mode", "world-scale" -> applyScaleSettings();
            case "near-depth-step", "far-depth-step", "depth-near-distance", "depth-far-distance" ->
                    applyDepthSettings();
            case "follow-enabled" -> testWindow.setFollow(target.isChecked());
            case "facing-enabled" -> testWindow.setFacing(target.isChecked());
            case "follow-factor" -> testWindow.setFollowFactor(
                    readFloat(target, testWindow.getFollowFactor()));
            default -> {
                return;
            }
        }
        syncControls();
    }

    private static void applyDisplayDistance() {
        String mode = value(control("display-distance-mode"), "global").toLowerCase(Locale.ROOT);
        if ("global".equals(mode)) {
            testWindow.clearMaxDisplayDistanceOverride();
        } else if ("unlimited".equals(mode)) {
            testWindow.setMaxDisplayDistance(Integer.MAX_VALUE);
        } else {
            Element distance = control("max-display-distance");
            testWindow.setMaxDisplayDistance(readInt(distance, testWindow.getMaxDisplayDistance()));
        }
    }

    private static void applyLodSettings() {
        Element policyControl = control("lod-policy");
        String policy = value(policyControl, "auto").toLowerCase(Locale.ROOT);
        if (!"auto".equals(policy)) {
            testWindow.setDisplayPrecision(policy);
            return;
        }

        Element customControl = control("lod-custom");
        if (customControl != null && customControl.isChecked()) {
            testWindow.setDisplayPrecisionDistances(
                    readInt(control("lod-full-distance"), testWindow.getFullDetailDistance()),
                    readInt(control("lod-reduced-distance"), testWindow.getReducedDetailDistance())
            );
        } else {
            testWindow.setDisplayPrecision(WorldWindowDisplayPrecision.AUTO);
        }
    }

    private static void applyScaleSettings() {
        String mode = value(control("scale-mode"), "auto").toLowerCase(Locale.ROOT);
        if ("manual".equals(mode)) {
            testWindow.setScale(readFloat(control("world-scale"), testWindow.getScale()));
        } else {
            testWindow.clearScaleOverride();
        }
    }

    private static void applyDepthSettings() {
        testWindow.setDynamicDepthStep(
                readFloat(control("near-depth-step"), testWindow.getNearDepthStep()),
                readFloat(control("far-depth-step"), testWindow.getFarDepthStep()),
                readFloat(control("depth-near-distance"), testWindow.getDepthNearDistance()),
                readFloat(control("depth-far-distance"), testWindow.getDepthFarDistance())
        );
    }

    private static void resetSettings() {
        if (testWindow == null) return;
        testWindow.setMaxDistance(TEST_MAX_DISTANCE);
        testWindow.clearMaxDisplayDistanceOverride();
        testWindow.setDepthTest(true);
        testWindow.setFollow(false);
        testWindow.setFollowFactor(DEFAULT_FOLLOW_FACTOR);
        testWindow.setFacing(false);
        testWindow.setDisplayPrecision(WorldWindowDisplayPrecision.AUTO);
        testWindow.clearScaleOverride();
        testWindow.setDynamicDepthStep(
                DEFAULT_NEAR_DEPTH_STEP,
                DEFAULT_FAR_DEPTH_STEP,
                DEFAULT_DEPTH_NEAR_DISTANCE,
                TEST_MAX_DISTANCE
        );
        syncControls();
    }

    private static void syncControls() {
        if (testWindow == null || boundDocument == null || boundDocument.body == null) return;

        int effectiveDisplayDistance = testWindow.getMaxDisplayDistance();
        setValue("max-display-distance", Integer.toString(Math.min(256, Math.max(4, effectiveDisplayDistance))));
        String displayMode = !testWindow.hasMaxDisplayDistanceOverride()
                ? "global" : effectiveDisplayDistance == Integer.MAX_VALUE ? "unlimited" : "instance";
        setValue("display-distance-mode", displayMode);
        // The slider is an explicit instance-edit affordance. Dragging it opts
        // into the instance source, so it must remain usable in global mode.
        setDisabled("max-display-distance", false);
        setValue("max-distance", Integer.toString(testWindow.getMaxDistance()));
        setChecked("depth-test", testWindow.isDepthTestEnabled());
        setText("display-distance-value", effectiveDisplayDistance == Integer.MAX_VALUE
                ? "UNLIMITED" : effectiveDisplayDistance + " BLOCKS");
        setText("max-distance-value", testWindow.getMaxDistance() + " BLOCKS");

        WorldWindowDisplayPrecision policy = testWindow.getDisplayPrecision();
        setValue("lod-policy", policy.toString());
        boolean customLod = policy == WorldWindowDisplayPrecision.AUTO
                && testWindow.hasDisplayPrecisionOverride();
        setChecked("lod-custom", customLod);
        setDisabled("lod-custom", policy != WorldWindowDisplayPrecision.AUTO);
        setDisabled("lod-full-distance", !customLod);
        setDisabled("lod-reduced-distance", !customLod);
        setValue("lod-full-distance", Integer.toString(testWindow.getFullDetailDistance()));
        setValue("lod-reduced-distance", Integer.toString(testWindow.getReducedDetailDistance()));
        setText("lod-full-value", testWindow.getFullDetailDistance() + " BLOCKS");
        setText("lod-reduced-value", testWindow.getReducedDetailDistance() + " BLOCKS");

        setValue("scale-mode", testWindow.hasScaleOverride() ? "manual" : "auto");
        setDisabled("world-scale", !testWindow.hasScaleOverride());
        setValue("world-scale", formatDecimal(testWindow.getScale(), 5));
        setText("world-scale-value", formatDecimal(testWindow.getScale(), 4));
        setValue("near-depth-step", formatDecimal(testWindow.getNearDepthStep(), 5));
        setValue("far-depth-step", formatDecimal(testWindow.getFarDepthStep(), 5));
        setValue("depth-near-distance", formatDecimal(testWindow.getDepthNearDistance(), 2));
        setValue("depth-far-distance", formatDecimal(testWindow.getDepthFarDistance(), 2));
        setText("near-depth-value", formatDecimal(testWindow.getNearDepthStep(), 5));
        setText("far-depth-value", formatDecimal(testWindow.getFarDepthStep(), 5));
        setText("depth-near-value", formatDecimal(testWindow.getDepthNearDistance(), 1) + " BLOCKS");
        setText("depth-far-value", formatDecimal(testWindow.getDepthFarDistance(), 1) + " BLOCKS");

        setChecked("follow-enabled", testWindow.isFollowEnabled());
        setValue("follow-factor", formatDecimal(testWindow.getFollowFactor(), 2));
        setDisabled("follow-factor", !testWindow.isFollowEnabled());
        setText("follow-factor-value", formatDecimal(testWindow.getFollowFactor(), 2));
        setChecked("facing-enabled", testWindow.isFacingEnabled());

        setText("window-status", "LIVE / RAY " + testWindow.getMaxDistance() + " BLOCKS");
        setText("display-status", effectiveDisplayDistance == Integer.MAX_VALUE
                ? "DISPLAY UNLIMITED"
                : "DISPLAY " + effectiveDisplayDistance + " BLOCKS");
        setText("lod-status", "EFFECTIVE LOD: "
                + testWindow.getEffectiveDisplayPrecision().toString().toUpperCase(Locale.ROOT));
        setText("scale-status", "SCALE " + formatDecimal(testWindow.getScale(), 4)
                + (testWindow.hasScaleOverride() ? " / MANUAL" : " / AUTO"));
    }

    private static Element control(String id) {
        return boundDocument == null ? null : boundDocument.getElementById(id);
    }

    private static Element eventControl(Element target) {
        for (Element current = target; current != null; current = current.parentElement) {
            if (current.getAttribute("id") != null && !current.getAttribute("id").isBlank()) return current;
        }
        return target;
    }

    private static void setValue(String id, String value) {
        Element element = control(id);
        if (element != null) element.setValue(value);
    }

    private static void setChecked(String id, boolean checked) {
        Element element = control(id);
        if (element != null) element.setChecked(checked);
    }

    private static void setDisabled(String id, boolean disabled) {
        Element element = control(id);
        if (element != null) element.setDisabled(disabled);
    }

    private static void setText(String id, String value) {
        Element element = control(id);
        if (element != null) element.setTextContent(value);
    }

    private static String value(Element element, String fallback) {
        if (element == null) return fallback;
        String value = element.getValue();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int readInt(Element element, int fallback) {
        try {
            return Integer.parseInt(value(element, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static float readFloat(Element element, float fallback) {
        try {
            float parsed = Float.parseFloat(value(element, Float.toString(fallback)));
            return Float.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String formatDecimal(float value, int precision) {
        return String.format(Locale.ROOT, "%." + precision + "f", value);
    }
}
