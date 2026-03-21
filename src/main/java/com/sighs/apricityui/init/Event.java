package com.sighs.apricityui.init;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Event {
    public Element target;
    public Element currentTarget;
    public String type;
    public Consumer<Event> listener;
    public boolean useCapture;
    public boolean stoppedPropagation = false;

    public Event(Element currentTarget, String type, Consumer<Event> listener, boolean useCapture) {
        this.target = currentTarget;
        this.currentTarget = currentTarget;
        this.type = type;
        this.listener = listener;
        this.useCapture = useCapture;
    }

    public void stopPropagation() {
        stoppedPropagation = true;
    }

    public static ArrayList<Element> getRoute(Element element) {
        ArrayList<Element> result = new ArrayList<>();
        Element parent = element.parentElement;
        while (parent != null) {
            result.add(parent);
            parent = parent.parentElement;
        }
        return result;
    }

    public static boolean tiggerEvent(Event targetEvent) {
        Element target = targetEvent.target;
        if (target == null) return false;

        String type = targetEvent.type;
        ArrayList<Element> route = target.getRoute();
        route.remove(target);
        AtomicBoolean listenerTriggered = new AtomicBoolean(false);

        // 捕获阶段，从body一直传递到目标元素的父元素。
        Collections.reverse(route);
        for (Element element : route) {
            element.triggerEvent(event -> {
                if (event.type.equals(type) && event.useCapture) {
                    targetEvent.currentTarget = element;
                    targetEvent.listener = event.listener;
                    listenerTriggered.set(true);
                    event.listener.accept(targetEvent);
                }
            });
        }
        if (targetEvent.stoppedPropagation) return listenerTriggered.get();

        target.triggerEvent(event -> {
            if (event.type.equals(type)) {
                targetEvent.currentTarget = target;
                targetEvent.listener = event.listener;
                listenerTriggered.set(true);
                event.listener.accept(targetEvent);
            }
        });
        // 冒泡阶段，事件从目标元素的父元素开始向上冒泡回body。
        if (targetEvent.stoppedPropagation) return listenerTriggered.get();

        Collections.reverse(route);
        for (Element element : route) {
            AtomicBoolean stoppedPropagation = new AtomicBoolean(false);
            element.triggerEvent(event -> {
                if (event.type.equals(type) && !event.useCapture) {
                    targetEvent.currentTarget = element;
                    targetEvent.listener = event.listener;
                    listenerTriggered.set(true);
                    event.listener.accept(targetEvent);
                    stoppedPropagation.set(targetEvent.stoppedPropagation);
                }
            });
            if (stoppedPropagation.get()) break;
        }

        return listenerTriggered.get();
    }

    public static boolean triggerSingle(Event targetEvent) {
        Element target = targetEvent.target;
        if (target == null) return false;

        String type = targetEvent.type;
        AtomicBoolean listenerTriggered = new AtomicBoolean(false);
        target.triggerEvent(event -> {
            if (event.type.equals(type)) {
                targetEvent.currentTarget = target;
                targetEvent.listener = event.listener;
                listenerTriggered.set(true);
                event.listener.accept(targetEvent);
            }
        });
        return listenerTriggered.get();
    }
}
