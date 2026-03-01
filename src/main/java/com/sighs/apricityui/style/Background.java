package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Style;
import com.sighs.apricityui.instance.Loader;

public class Background {
    public String repeat = "no-repeat";
    public String size = "auto";
    public String position = "0 0";
    public String imagePath = "unset";
    public String color = "unset";
    public Gradient gradient = null;

    public static Background of(Element element) {
        Background cache = element.getRenderer().background.get();
        if (cache != null) {
            cache.syncAnimatedFields(element.getComputedStyle());
            return cache;
        }

        Style style = element.getComputedStyle();
        Background bg = new Background();
        bg.repeat = valid(style.backgroundRepeat) ? style.backgroundRepeat : "no-repeat";
        bg.size = valid(style.backgroundSize) ? style.backgroundSize : "auto";
        bg.position = valid(style.backgroundPosition) ? style.backgroundPosition : "0 0";
        bg.color = valid(style.backgroundColor) ? style.backgroundColor : "unset";
        String img = valid(style.backgroundImage) ? style.backgroundImage : "unset";
        if (valid(style.background) && img.equals("unset")) {
            parseBackgroundShorthand(element, style.background, bg);
        }
        if (valid(img)) {
            bg.resolveBackgroundImage(element, img);
        }

        element.getRenderer().background.set(bg);
        return bg;
    }

    // 背景对象会被缓存，动画帧里需要同步会变化的字段。
    private void syncAnimatedFields(Style style) {
        if (style == null) return;
        this.position = style.backgroundPosition;
    }

    private static boolean valid(String s) {
        return s != null && !s.equals("unset") && !s.isBlank();
    }

    private static void parseBackgroundShorthand(Element element, String shorthand, Background bg) {
        if (shorthand == null || shorthand.equals("none")) return;
        shorthand = shorthand.trim();
        if (shorthand.startsWith("linear-gradient") || shorthand.startsWith("radial-gradient")) {
            bg.gradient = Gradient.parse(shorthand);
        } else if (shorthand.contains("url(")) {
            String path = shorthand.substring(shorthand.indexOf("(") + 1, shorthand.lastIndexOf(")")).replace("\"", "").replace("'", "");
            bg.imagePath = Loader.resolve(element.document.getPath(), path);
        } else if (shorthand.startsWith("#") || shorthand.startsWith("rgb") || shorthand.matches("^[a-zA-Z]+$")) {
            bg.color = shorthand;
        }
    }

    public void resolveBackgroundImage(Element element, String image) {
        if (image == null || image.equals("unset")) return;

        if (image.contains("url(")) {
            String path = image.substring(image.indexOf("(") + 1, image.lastIndexOf(")")).replace("\"", "").replace("'", "");
            this.imagePath = Loader.resolve(element.document.getPath(), path);
        } else if (image.startsWith("linear-gradient")) {
            this.gradient = Gradient.parse(image);
        }
    }
}
