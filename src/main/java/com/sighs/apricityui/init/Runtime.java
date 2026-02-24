package com.sighs.apricityui.init;

public class Runtime {
    public static void tick() {
        long frameTime = System.currentTimeMillis();
        for (Document document : Document.getAll()) {
            document.setAnimationFrameTime(frameTime);
            for (Element element : document.getElements()) {
                element.tick();
            }
            for (Element element : document.getElements()) {
                element.advanceFrameStyle(frameTime);
            }
        }
        // 这里还需要一个定期清理垃圾缓存的，因为目前没写元素移除和document关闭时清理缓存
    }
}
