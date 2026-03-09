package com.sighs.apricityui.util.kjs;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Window;
import com.sighs.apricityui.instance.container.bind.ContainerBindType;
import com.sighs.apricityui.instance.container.bind.OpenBindPlan;
import com.sighs.apricityui.instance.element.Container;
import com.sighs.apricityui.instance.network.handler.ApricityScreenNetworkHandler;
import com.sighs.apricityui.registry.annotation.KJSBindings;

import java.util.ArrayList;
import java.util.List;

@KJSBindings(value = "ApricityUI", isClient = true)
public class ApricityUIClientUtil {
    public static Window getWindow() {
        return Window.window;
    }

    public static Document createDocument(String path) {
        return Document.create(path);
    }

    public static void removeDocument(String path) {
        Document.remove(path);
    }

    public static ArrayList<Document> getDocument(String path) {
        return Document.get(path);
    }

    public static Document getDocumentByUUID(String uuid) {
        return Document.getByUUID(uuid);
    }

    public static List<Document> getAllDocument() {
        return Document.getAll();
    }

    public static void openScreen(String path) {
        ApricityScreenNetworkHandler.requestOpenScreen(path);
    }

    public static void closeScreen() {
        ApricityScreenNetworkHandler.requestCloseScreen();
    }

    public static OpenBindPlan.Builder bind() {
        return OpenBindPlan.builder();
    }

    public static boolean hasDataSource(ContainerBindType bindType) {
        return Container.hasBindingDataSource(bindType);
    }
}
