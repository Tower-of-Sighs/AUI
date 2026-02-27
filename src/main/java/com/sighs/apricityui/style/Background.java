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
        if (cache != null) return cache;

        Style style = element.getComputedStyle();
        Background bg = new Background();
        bg.repeat = style.backgroundRepeat;
        bg.size = style.backgroundSize;
        bg.position = style.backgroundPosition;
        bg.color = style.backgroundColor;
        bg.resolveBackgroundImage(element, style.backgroundImage);

        element.getRenderer().background.set(bg);
        return bg;
    }

    public void resolveBackgroundImage(Element element, String image) {
        if (image == null || image.equals("unset")) return;

        if (image.contains("url(")) {
            String path = image.substring(image.indexOf("(") + 1, image.lastIndexOf(")")).replace("\"", "").replace("'", "");
            this.imagePath = Loader.resolve(element.document.getPath(), path);
        }
        else if (image.startsWith("linear-gradient")) {
            this.gradient = Gradient.parse(image);
        }
    }
}
