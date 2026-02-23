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

    public static void tiggerEvent(Event targetEvent) {
        Element target = targetEvent.target;
        String type = targetEvent.type;
        ArrayList<Element> route = target.getRoute();
        route.remove(target);

        // 捕获阶段，从body一直传递到目标元素的父元素。
        Collections.reverse(route);
        for (Element element : route) {
            element.triggerEvent(event -> {
                if (event.type.equals(type) && event.useCapture) {
                    targetEvent.currentTarget = element;
                    targetEvent.listener = event.listener;
                    event.listener.accept(targetEvent);
                }
            });
        }
        if (targetEvent.stoppedPropagation) return;
        // 目标阶段
        // triggerSingle(targetEvent);
        target.triggerEvent(event -> {
            if (event.type.equals(type)) {
                targetEvent.currentTarget = target;
                targetEvent.listener = event.listener;
                event.listener.accept(targetEvent);
            }
        });
        if (targetEvent.stoppedPropagation) return;
        // 冒泡阶段，事件从目标元素的父元素开始向上冒泡回body。
        Collections.reverse(route);
        for (Element element : route) {
            AtomicBoolean stoppedPropagation = new AtomicBoolean(false);
            element.triggerEvent(event -> {
                if (event.type.equals(type) && !event.useCapture) {
                    targetEvent.currentTarget = element;
                    targetEvent.listener = event.listener;
                    event.listener.accept(targetEvent);
                    stoppedPropagation.set(targetEvent.stoppedPropagation);
                }
            });
            if (stoppedPropagation.get()) break;
        }
    }

    public static void triggerSingle(Event targetEvent) {
        Element target = targetEvent.target;
        String type = targetEvent.type;
        target.triggerEvent(event -> {
            if (event.type.equals(type)) {
                targetEvent.currentTarget = target;
                targetEvent.listener = event.listener;
                event.listener.accept(targetEvent);
            }
        });
    }
}
