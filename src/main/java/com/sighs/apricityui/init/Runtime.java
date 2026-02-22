package com.sighs.apricityui.init;

public class Runtime {
    public static void tick() {
        for (Document document : Document.getAll()) {
            for (Element element : document.getElements()) {
                element.tick();
            }
        }
        // 这里还需要一个定期清理垃圾缓存的，因为目前没写元素移除和document关闭时清理缓存
    }
}
