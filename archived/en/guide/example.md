---
title: Implementing Player Chat Bubbles With AUI
description: This example uses AUI's WorldWindow class and ClientChatReceivedEvent to show chat bubbles above player heads. It is intended as a reference only.
last_update:
  date: 3/15/2026
  author:
---

# Implementing Player Chat Bubbles With AUI

This example uses AUI's `WorldWindow` class. By listening to `ClientChatReceivedEvent`, it reads player chat messages and shows a chat bubble [in-world image UI](./ui-types.md#3-in-world-image-ui) above the sender's head. This is for reference only.

`PlayerMessageDisplay.java`

```java
package com.sighs.apricityui.dev;

import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.init.Drawer;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.WorldWindow;
import com.sighs.apricityui.style.Size;
import com.sighs.apricityui.style.Text;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(modid = ApricityUI.MODID, value = Dist.CLIENT)
public class PlayerMessageDisplay {
    private static final Map<UUID, WorldWindow> messageWindows = new HashMap<>();
    private static final Map<UUID, Player> trackedPlayers = new HashMap<>();
    private static final Map<UUID, Long> messageExpireAt = new HashMap<>();
    private static final Minecraft mc = Minecraft.getInstance();
    /**
     * Maximum bubble width
     */
    private static final float WRAP_WIDTH = 250.0f;
    private static final float BORDER_TOP = 8.5f;
    private static final float BORDER_RIGHT = 17.25f;
    private static final float BORDER_BOTTOM = 13.0f;
    private static final float BORDER_LEFT = 22.0f;
    private static final float PADDING_H = 20.0f;
    private static final float Y_OFFSET = 0.5f;
    private static final float BUBBLE_SCALE = 2.5f;
    private static final int MAX_DISTANCE = 32;
    private static final int MAX_DISTANCE_SQUARED = MAX_DISTANCE * MAX_DISTANCE;

    private static final long MESSAGE_VISIBLE_MS = 10_000L;
    private static final long MESSAGE_FADE_MS = 2_000L;
    private static final long MESSAGE_TOTAL_MS = MESSAGE_VISIBLE_MS + MESSAGE_FADE_MS;

    private static final String documentPath = ApricityUI.MODID + "/player_message.html";

    private record ContentSize(float textWidth, float textHeight) {
    }

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        if (event.isSystem()) {
            return;
        }
        UUID senderUuid = event.getSender();
        if (Util.NIL_UUID.equals(senderUuid)) {
            return;
        }
        Component message = event.getMessage();
        String text = message.getString();
        if (text.isBlank()) {
            return;
        }
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null) {
            return;
        }
        Level level = localPlayer.level();
        Player sender = findPlayerByUuid(level, senderUuid);
        if (sender == null) {
            return;
        }
        String contentOnly = stripChatPrefix(message);
        int width = mc.font.width(contentOnly);
        // Add padding manually so short text does not create an undersized bubble.
        if (width < 26) {
            int spaceWidth = mc.font.width(" ");
            if (spaceWidth > 0) {
                int totalSpaces = (int) Math.ceil((26 - width) / (double) spaceWidth);
                int spacesBefore = totalSpaces / 2;
                int spacesAfter = totalSpaces - spacesBefore;
                contentOnly = " ".repeat(spacesBefore) + contentOnly + " ".repeat(spacesAfter);
            }
        }
        showMessageAbovePlayer(senderUuid, sender, contentOnly);
    }

    private static String stripChatPrefix(Component message) {
        if (message.getContents() instanceof TranslatableContents contents) {
            Object[] args = contents.getArgs();
            if (args.length == 0) {
                return message.getString();
            } else if (args.length == 1 && args[0] instanceof MutableComponent content) {
                return content.getString();
            } else {
                MutableComponent component = Component.literal("");
                for (int i = 0; i < args.length; i++) {
                    if (i == 0) continue;
                    Object object = args[i];
                    if (object instanceof Component content) {
                        component.append(content);
                    } else {
                        component.append(object.toString());
                    }
                }
                return component.getString();
            }
        }
        return message.getString();
    }

    private static Player findPlayerByUuid(Level level, UUID uuid) {
        if (mc.player != null && mc.player.getUUID().equals(uuid)) {
            return mc.player;
        }
        for (Player player : level.players()) {
            if (player.getUUID().equals(uuid)) {
                return player;
            }
        }
        return null;
    }

    private static void showMessageAbovePlayer(UUID playerUuid, Player player, String messageText) {
        if (player == null || !player.isAlive()) return;
        WorldWindow existingWindow = messageWindows.get(playerUuid);
        if (existingWindow == null) {
            Vec3 pos = player.getBoundingBox().getCenter();
            double headY = player.getBoundingBox().maxY + Y_OFFSET;
            existingWindow = new WorldWindow(documentPath, new Vec3(pos.x, headY, pos.z), 150, 60, MAX_DISTANCE);
            if (existingWindow.document == null) return;
            WorldWindow.addWindow(existingWindow);
            messageWindows.put(playerUuid, existingWindow);
        }
        ContentSize size = computeContentSize(existingWindow, messageText);
        if (size == null) return;
        float contentW = size.textWidth() + PADDING_H + BORDER_LEFT + BORDER_RIGHT;
        float contentH = size.textHeight() + BORDER_TOP + BORDER_BOTTOM;
        boolean needRecreate = existingWindow.getWidth() != contentW || existingWindow.getHeight() != contentH;
        WorldWindow window;
        if (needRecreate) {
            Vec3 pos = player.getBoundingBox().getCenter();
            double headY = player.getBoundingBox().maxY + Y_OFFSET;
            window = new WorldWindow(documentPath, new Vec3(pos.x, headY, pos.z), contentW, contentH, MAX_DISTANCE);
            if (window.document == null) return;
            WorldWindow.removeWindow(existingWindow);
            WorldWindow.addWindow(window);
            messageWindows.put(playerUuid, window);
        } else {
            window = existingWindow;
        }
        trackedPlayers.put(playerUuid, player);
        window.setScale(BUBBLE_SCALE / contentW);
        updateMessageContent(window, messageText);
        updateBodySize(window, contentW, contentH);
        updateMessageOpacity(window, 1f);
        messageExpireAt.put(playerUuid, System.currentTimeMillis() + MESSAGE_TOTAL_MS);
    }

    private static void updateMessageContent(WorldWindow window, String text) {
        if (window == null || window.document == null) return;
        var linesEl = window.document.getElementById("message-lines");
        var measureEl = window.document.getElementById("message-measure");
        if (linesEl == null || measureEl == null) return;
        linesEl.setAttribute("style", "display:flex;flex-direction:column;align-items:center;text-align:center;gap:0;direction:ltr;");
        var lines = wrapText(measureEl, text);
        var children = new ArrayList<>(linesEl.children);
        for (var c : children) if (c != measureEl) window.document.removeElement(c);
        for (var line : lines) {
            var span = window.document.createElement("SPAN");
            span.setAttribute("class", "bubble-txt");
            span.innerText = line;
            linesEl.append(span);
        }
        window.document.markDirty(linesEl, Drawer.RELAYOUT | Drawer.REPAINT);
    }

    private static void updateBodySize(WorldWindow window, float w, float h) {
        if (window == null || window.document == null || window.document.body == null) return;
        String style = window.document.body.getAttribute("style");
        String base = style == null || style.isBlank() ? "" : style;
        String cleaned = base.replaceAll("width:\\s*[^;]+;?", "")
                .replaceAll("height:\\s*[^;]+;?", "")
                .replaceAll("overflow:\\s*[^;]+;?", "").trim();
        String newStyle = (cleaned.isEmpty() ? "" : cleaned + ";") + "width:" + w + "px;height:" + h + "px;overflow:visible;";
        window.document.body.setAttribute("style", newStyle);
        window.document.markDirty(window.document.body, Drawer.RELAYOUT | Drawer.REPAINT);
    }

    private static ContentSize computeContentSize(WorldWindow window, String text) {
        if (window == null || window.document == null) return null;
        var measureEl = window.document.getElementById("message-measure");
        if (measureEl == null) return null;
        var lines = wrapText(measureEl, text);
        float maxW = 0;
        for (var line : lines) {
            float lineW = (float) Size.measureText(measureEl, line);
            if (lineW > maxW) maxW = lineW;
        }
        float lineH = (float) Text.of(measureEl).lineHeight;
        return new ContentSize(maxW, lines.size() * lineH);
    }

    private static List<String> wrapText(Element measureEl, String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        double w = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(cur.toString());
                cur.setLength(0);
                w = 0;
                continue;
            }
            double cw = Size.measureText(measureEl, String.valueOf(c));
            if (!cur.isEmpty() && w + cw > PlayerMessageDisplay.WRAP_WIDTH) {
                lines.add(cur.toString());
                cur.setLength(0);
                w = 0;
            }
            cur.append(c);
            w += cw;
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static void updateMessageOpacity(WorldWindow window, float opacity) {
        if (window == null || window.document == null) return;
        var container = window.document.getElementById("message-container");
        if (container != null) {
            String style = container.getAttribute("style");
            String base = (style == null || style.isBlank()) ? "" : style;
            String cleaned = base.replaceAll("opacity:\\s*[\\d.]+;?\\s*", "").trim();
            String newStyle = (cleaned.isEmpty() ? "" : cleaned + ";") + "opacity:" + String.format("%.2f", Math.max(0, Math.min(1, opacity))) + ";";
            container.setAttribute("style", newStyle);
            window.document.markDirty(container, Drawer.REPAINT);
        }
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Player player) {
            removeMessage(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Vec3 playerPos = player.position();

        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : messageExpireAt.entrySet()) {
            long expireAt = entry.getValue();
            if (expireAt <= now) {
                toRemove.add(entry.getKey());
            } else {
                WorldWindow w = messageWindows.get(entry.getKey());
                if (w != null) {
                    long fadeStart = expireAt - MESSAGE_FADE_MS;
                    if (now >= fadeStart) {
                        float progress = (float) (now - fadeStart) / MESSAGE_FADE_MS;
                        updateMessageOpacity(w, 1f - progress);
                    }
                }
            }
        }
        for (Map.Entry<UUID, Player> entry : trackedPlayers.entrySet()) {
            Player p = entry.getValue();
            if (p == null || !p.isAlive() ||
                    p.position().distanceToSqr(playerPos) > MAX_DISTANCE_SQUARED) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            removeMessage(uuid);
        }
    }

    public static void removeMessage(UUID uuid) {
        WorldWindow window = messageWindows.remove(uuid);
        if (window != null) WorldWindow.removeWindow(window);
        trackedPlayers.remove(uuid);
        messageExpireAt.remove(uuid);
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != Stage.AFTER_TRANSLUCENT_BLOCKS || messageWindows.isEmpty()) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        for (Map.Entry<UUID, Player> entry : trackedPlayers.entrySet()) {
            Player entity = entry.getValue();
            if (entity == null) continue;
            WorldWindow window = messageWindows.get(entry.getKey());
            if (window == null || window.document == null) continue;
            double w = window.getWidth();
            double h = window.getHeight();
            double scale = BUBBLE_SCALE / w;
            Vec3 entityPos = entity.position();
            double entityHeight = entity.getBbHeight();
            double y = entityPos.y;
            double centerY = y + entityHeight + Y_OFFSET + (h * scale) / 2.0;
            Vec3 bubblePos = new Vec3(entityPos.x, centerY, entityPos.z);
            window.setPosition(bubblePos);
            Vec3 direction = cameraPos.subtract(bubblePos).normalize();
            float yaw = (float) Math.atan2(-direction.x, direction.z) + 180;
            window.setRotation(yaw, 0);
        }
    }
}
```

`assets/apricityui/apricity/player_message.html`

```html
<body>
<div id="message-container" class="bubble">
    <div id="message-lines" class="bubble-txt">
        <span id="message-measure" class="bubble-txt" style="display:none"></span>
        <span class="bubble-txt">Meow~Meow~Meow~</span>
    </div>
</div>
</body>

<link rel="stylesheet" href="global.css">

<style>
    body {
        margin: 0;
        padding: 0;
        overflow: visible;
        display: flex;
        justify-content: center;
        align-items: center;
    }

    .bubble {
        width: fit-content;
        border: 9px solid transparent;
        border-image: url('https://f.loli.ly/snowflake_cat.png') 34 69 52 88 fill / 8.5px 17.25px 13px 22px stretch;
        color: #fff;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        line-height: 9px;
        transition: opacity 0.1s linear;
    }

    .bubble-txt {
        color: inherit;
        font-size: 16px;
        padding: 0 10px;
    }

    #message-lines {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0;
    }
</style>
```
