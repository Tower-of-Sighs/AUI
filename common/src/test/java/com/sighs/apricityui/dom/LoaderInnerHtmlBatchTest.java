package com.sighs.apricityui.dom;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.init.Node;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.registry.annotation.GenericStackElementType;
import com.sighs.apricityui.spi.AuiClassScanService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.stack.GenericStackTypes;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/** Exercises the real target expander; standalone common has no loader classes. */
public class LoaderInnerHtmlBatchTest {
    @org.junit.jupiter.api.Test
    void realLoaderBatchAndSpecialHooks() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(loaderAvailable(), "Standalone common has no loader expander");
        verify();
    }

    public static void main(String[] args) throws Exception {
        verify();
        System.out.println("LoaderInnerHtmlBatchTest: PASS (real loader, ordinary batching, observer order, special hooks, partial failure)");
    }

    private static boolean loaderAvailable() {
        try {
            loaderType();
            return true;
        } catch (ClassNotFoundException missing) {
            return false;
        }
    }

    private static Class<?> loaderType() throws ClassNotFoundException {
        String configured = System.getProperty("aui.test.loaderExpander");
        if (configured != null) return Class.forName(configured);
        try {
            return Class.forName("com.sighs.apricityui.dom.ForgeDocumentExpander");
        } catch (ClassNotFoundException missing) {
            return Class.forName("com.sighs.apricityui.fabric.FabricDocumentExpander");
        }
    }

    private static void verify() throws Exception {
        var registryField = Element.class.getDeclaredField("REGISTRY");
        registryField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var registry = (java.util.Map<String, java.util.function.BiFunction<Document, String, ? extends Element>>) registryField.get(null);
        var registrations = new java.util.HashMap<>(registry);
        DocumentExpander previous = AuiServices.expander();
        AuiClassScanService previousClasses = AuiServices.classes();
        Document document = null;
        try {
            DocumentExpander loader = (DocumentExpander) loaderType().getConstructor().newInstance();
            Class<?> slotType = Class.forName("com.sighs.apricityui.element.Slot");
            Class<?> itemType = Class.forName("com.sighs.apricityui.element.Item");
            // Do not rely on static initializers: another test may have restored REGISTRY.
            for (Class<?> type : List.of(slotType, itemType)) {
                Element.register(type.getSimpleName(), (owner, tag) -> {
                    try {
                        return instantiateElement(type, owner);
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException(failure);
                    }
                });
            }
            Class<?> fluidType = optionalClass("com.sighs.apricityui.element.Fluid");
            AuiServices.setClasses(new AuiClassScanService() {
                @Override
                public void addScanPackage(String basePackage) {
                }

                @Override
                public void scanAnnotationClasses(Class<? extends Annotation> annotationClass,
                                                  java.util.function.Predicate<java.util.Map<String, Object>> predicate,
                                                  java.util.function.Consumer<Class<?>> consumer,
                                                  Runnable onFinished) {
                    if (annotationClass == GenericStackElementType.class) {
                        consumer.accept(itemType);
                        if (fluidType != null) consumer.accept(fluidType);
                    }
                    onFinished.run();
                }
            });
            GenericStackTypes.scanElementTypes();
            AuiServices.setExpander(loader);
            String path = "test://loader-batch/" + java.util.UUID.randomUUID();
            HTML.putTemple(path, "<html><head></head><body><div id='host'><i id='old-a'></i><b id='old-b'></b></div></body></html>");
            document = Document.create(path);
            Element host = document.getElementById("host");
            check(loader.supportsInnerHtmlBatch(document, host), "ordinary DIV must opt in");
            check(!loader.supportsInnerHtmlBatch(document, null), "null is not an eligible parent");
            check(!loader.supportsInnerHtmlBatch(document, document.createDocumentFragment()), "fragment is not an eligible parent");
            List<Node> removed = new ArrayList<>(host.childNodes);
            Document.MutationObserver observer = document.createMutationObserver(records -> {});
            observer.observe(host, true, false, false, false, false, false, null);
            host.setInnerHTML("text<!--comment--><span id='new-a'></span><b id='new-b'></b>");
            List<Document.MutationRecord> records = observer.takeRecords();
            check(records.size() == 2, "real loader must emit one removal and one insertion record, not per-root records");
            check(records.get(0).removedNodes.equals(removed), "removal order");
            check(records.get(1).addedNodes.equals(host.childNodes), "mixed-node insertion order");
            check(host.childNodes.get(0).getNodeType() == Node.TEXT_NODE, "text preserved");
            check(host.childNodes.get(1).getNodeType() == Node.COMMENT_NODE, "comment preserved");
            check(document.getElementById("old-a") == null, "old index entry removed");
            check(document.getElementById("new-a") == host.children.get(0), "new index entry attached");
            check(removed.stream().noneMatch(Node::isConnected), "removed nodes disconnected");
            host.setInnerHTML("");
            check(observer.takeRecords().size() == 1 && host.childNodes.isEmpty(), "empty replacement batches removal");
            observer.disconnect();

            Element parent = instantiateElement(slotType, document);
            document.body.appendChild(parent);
            check(!loader.supportsInnerHtmlBatch(document, parent), "SLOT must retain sequential replacement semantics");
            parent.setInnerHTML("<item id='first'></item><item id='last'></item>");
            check(parent.children.size() == 1 && "last".equals(parent.children.get(0).id), "special sibling replacement remains sequential");
            check(document.getElementById("first") == null, "replaced special sibling disconnected");
            parent.setInnerHTML("");
            check(parent.children.size() == 1 && itemType.isInstance(parent.children.get(0)), "empty SLOT restores required item");
            boolean slotRejected = false;
            try {
                parent.setInnerHTML("<item id='valid'></item><div id='illegal'></div><item id='unreached'></item>");
            } catch (IllegalArgumentException expected) {
                slotRejected = true;
            }
            check(slotRejected, "illegal SLOT child rejected");
            check(parent.children.size() == 1 && "valid".equals(parent.children.get(0).id), "earlier valid SLOT insertion survives failure");
            Element illegal = document.getElementById("illegal");
            Element unreached = document.getElementById("unreached");
            check(illegal == null || !illegal.isConnected(), "illegal root never attached");
            check(unreached == null || !unreached.isConnected(), "later root never attached");
            parent.remove();

            Element item = instantiateElement(itemType, document);
            document.body.appendChild(item);
            check(!loader.supportsInnerHtmlBatch(document, item), "ITEM must retain partial-failure semantics too");
            boolean rejected = false;
            try {
                item.setInnerHTML("accepted<div id='illegal-item-child'></div>unreached");
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "ITEM rejects element child");
            check(item.childNodes.size() == 1 && "accepted".equals(item.childNodes.get(0).getTextContent()), "ITEM preserves earlier text on failure");
            Element illegalItemChild = document.getElementById("illegal-item-child");
            check(illegalItemChild == null || !illegalItemChild.isConnected(), "invalid ITEM child not attached");
        } finally {
            if (document != null) document.remove();
            AuiServices.setExpander(previous);
            AuiServices.setClasses(previousClasses);
            registry.clear();
            registry.putAll(registrations);
        }
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Element instantiateElement(Class<?> type, Document document) throws ReflectiveOperationException {
        try {
            return (Element) type.getConstructor(Document.class).newInstance(document);
        } catch (NoSuchMethodException missingDocumentConstructor) {
            Class<? extends Annotation> genericTypeAnnotation = (Class<? extends Annotation>) Class.forName(
                    "com.sighs.apricityui.registry.annotation.GenericStackElementType");
            Annotation declaration = type.getAnnotation(genericTypeAnnotation);
            if (declaration == null) throw missingDocumentConstructor;
            Class<?> stackTypeClass = (Class<?>) declaration.annotationType().getMethod("value").invoke(declaration);
            Object stackType = stackTypeClass.getConstructor().newInstance();
            for (var constructor : type.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 2 && parameters[0] == Document.class
                        && parameters[1].isInstance(stackType)) {
                    return (Element) constructor.newInstance(document, stackType);
                }
            }
            throw missingDocumentConstructor;
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
