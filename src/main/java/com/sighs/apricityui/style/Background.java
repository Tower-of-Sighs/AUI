package com.sighs.apricityui.style;

import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.instance.Loader;
import com.sighs.apricityui.init.Style;

public class Background {
    public String repeat = "no-repeat";
    public String size = "auto";
    public String position = "0 0";
    public String imagePath = "unset";
    public String color = "unset";

    public static Background of(Element element) {
        Background cache = element.getRenderer().background.get();
        if (cache != null) return cache;

        Style style = element.getComputedStyle();
        Background bg = new Background();
        bg.repeat = style.backgroundRepeat;
        bg.size = style.backgroundSize;
        bg.position = style.backgroundPosition;
        bg.color = style.backgroundColor;
        String imagePath = bg.getResolvedPath(element, style.backgroundImage);
        if (imagePath != null) bg.imagePath = imagePath;

        element.getRenderer().background.set(bg);
        return bg;
    }

    // 解析 url("...") 中的路径
    public String getResolvedPath(Element element, String image) {
        if (image == null || image.equals("unset") || !image.contains("url(")) return null;
        String path = image.substring(image.indexOf("(") + 1, image.lastIndexOf(")")).replace("\"", "").replace("'", "").trim();
        if (path.isEmpty()) return null;
        return Loader.resolve(element.document.getPath(), path);
    }
}
