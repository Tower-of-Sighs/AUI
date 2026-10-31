package com.sighs.apricityui.dom;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.parser.HTML;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

class ElementTreeUpdateElementIndexTest {
    private static final String INIT_PROBE_TAG = "element-tree-index-init-probe";
    private Document document;

    @AfterEach
    void cleanup() {
        Element.register(INIT_PROBE_TAG, Element::new);
        if (document != null) document.remove();
    }

    @Test
    void updateReplacesOnlyMatchingEntryAndLeavesOrderUnchanged() {
        Document document = indexedDocument();
        Element first = append(document, "first");
        Element target = append(document, "target");
        Element last = append(document, "last");
        ArrayList<Element> elements = document.getElements();
        List<Element> before = new ArrayList<>(elements);
        Element replacement = replacementFor(target);

        document.updateElement(replacement);

        assertEquals(before.size(), elements.size());
        assertSame(first, elements.get(before.indexOf(first)));
        assertSame(replacement, elements.get(before.indexOf(target)));
        assertSame(last, elements.get(before.indexOf(last)));
        assertEquals(before.indexOf(target), elements.indexOf(replacement));
    }

    @Test
    void duplicateUuidUpdatesLastMatchingEntryAndUnknownUuidIsNoOp() {
        Document document = indexedDocument();
        Element first = append(document, "first");
        Element duplicate = append(document, "duplicate");
        duplicate.uuid = first.uuid;
        Element last = append(document, "last");
        document.getTree().rebuildFromRoot(document.documentElement);
        ArrayList<Element> elements = document.getElements();
        List<Element> before = new ArrayList<>(elements);
        Element replacement = replacementFor(first);

        document.updateElement(replacement);

        assertSame(first, elements.get(before.indexOf(first)));
        assertSame(replacement, elements.get(before.indexOf(duplicate)));
        assertSame(last, elements.get(before.indexOf(last)));
        List<Element> after = new ArrayList<>(elements);
        document.updateElement(new Element(document, "unknown"));
        assertEquals(after, elements);
    }

    @Test
    void fragmentInsertDeleteClearAndRebuildKeepElementListInTreeOrder() {
        Document document = indexedDocument();
        Element first = append(document, "first");
        Element last = append(document, "last");
        DocumentFragment fragment = document.createDocumentFragment();
        Element middleA = new Element(document, "middle-a");
        Element middleB = new Element(document, "middle-b");
        fragment.appendChild(middleA);
        fragment.appendChild(middleB);
        document.body.insertBefore(fragment, last);
        assertBodyOrder(document, first, middleA, middleB, last);

        document.body.removeChild(middleA);
        assertBodyOrder(document, first, middleB, last);
        document.body.clearChildren();
        assertBodyOrder(document);

        Element rebuiltA = append(document, "rebuilt-a");
        Element rebuiltB = append(document, "rebuilt-b");
        document.getTree().rebuildFromRoot(document.documentElement);
        assertElementOrder(document, document.documentElement, document.head, document.body, rebuiltA, rebuiltB);
    }

    @Test
    void updateAfterDirectListMutationUsesCurrentListContents() {
        Document document = indexedDocument();
        Element target = append(document, "target");
        Element other = append(document, "other");
        ArrayList<Element> elements = document.getElements();
        elements.remove(target);
        Element replacement = replacementFor(target);

        document.updateElement(replacement);

        assertSame(other, elements.get(elements.size() - 1));
        assertEquals(-1, elements.indexOf(replacement));
        elements.add(0, target);
        document.updateElement(replacement);
        assertSame(replacement, elements.get(0));

        Element duplicate = replacementFor(target);
        elements.set(elements.size() - 1, duplicate);
        Element lastReplacement = replacementFor(target);
        document.updateElement(lastReplacement);
        assertSame(replacement, elements.get(0));
        assertSame(lastReplacement, elements.get(elements.size() - 1));
    }

    @Test
    void directUuidChangesCannotHideLaterMatchBehindValidCachedHit() {
        Document document = indexedDocument();
        Element first = append(document, "first");
        Element later = append(document, "later");
        document.getTree().rebuildFromRoot(document.documentElement);
        later.uuid = first.uuid;
        Element replacement = replacementFor(first);

        document.updateElement(replacement);

        assertSame(first, document.getElements().get(3));
        assertSame(replacement, document.getElements().get(4));
    }

    @Test
    void removeNodeAfterUuidChangeDoesNotDependOnCachedUuid() {
        Document document = indexedDocument();
        Element target = append(document, "target");
        document.getTree().rebuildFromRoot(document.documentElement);
        target.uuid = java.util.UUID.randomUUID();
        // removeAll must remove every occurrence, including a live-list duplicate.
        document.getElements().add(0, target);

        document.body.removeChild(target);

        assertElementOrder(document, document.documentElement, document.head, document.body);
        assertEquals(false, document.getNodes().contains(target));
    }

    @Test
    void clearChildrenRemovesEntriesWithChangedOrNullUuid() {
        Document document = indexedDocument();
        Element changed = append(document, "changed");
        Element nulled = append(document, "nulled");
        document.getTree().rebuildFromRoot(document.documentElement);
        changed.uuid = java.util.UUID.randomUUID();
        nulled.uuid = null;
        document.getElements().add(0, changed);

        document.body.clearChildren();

        assertElementOrder(document, document.documentElement, document.head, document.body);
        assertBodyOrder(document);
    }

    @Test
    void removingLastDuplicateRestoresEarlierUuidMatch() {
        Document document = indexedDocument();
        Element first = append(document, "first");
        Element container = append(document, "container");
        Element duplicate = new Element(document, "duplicate");
        duplicate.uuid = first.uuid;
        container.appendChild(duplicate);
        document.getTree().rebuildFromRoot(document.documentElement);

        container.clearChildren();
        Element replacement = replacementFor(first);
        document.updateElement(replacement);

        assertElementOrder(document, document.documentElement, document.head, document.body, replacement, container);
    }

    @Test
    void sortAndSubListSortPreserveLastUuidMatchSemantics() {
        Document document = indexedDocument();
        Element first = append(document, "a-first");
        Element later = append(document, "z-later");
        later.uuid = first.uuid;
        document.getTree().rebuildFromRoot(document.documentElement);
        ArrayList<Element> elements = document.getElements();
        elements.sort(java.util.Comparator.comparing((Element element) -> element.tagName).reversed());
        Element replacement = replacementFor(first);
        document.updateElement(replacement);
        assertSame(later, elements.get(elements.indexOf(later)));
        assertEquals(-1, elements.indexOf(first));

        elements.subList(0, elements.size()).sort(java.util.Comparator.comparing((Element element) -> element.tagName));
        Element lastReplacement = replacementFor(first);
        document.updateElement(lastReplacement);
        assertEquals(-1, elements.indexOf(later));
        assertSame(lastReplacement, elements.get(elements.size() - 1));
    }

    @Test
    void trackedMutationsRefreshIdentityMembershipForInitialization() {
        List<java.util.function.BiConsumer<ArrayList<Element>, Element>> mutations = List.of(
                (list, value) -> list.set(1, value),
                (list, value) -> list.replaceAll(old -> old == list.get(1) ? value : old),
                (list, value) -> list.subList(1, 2).set(0, value),
                (list, value) -> list.subList(0, 3).subList(1, 2).set(0, value),
                (list, value) -> list.subList(1, 2).replaceAll(old -> value),
                (list, value) -> { var iterator = list.listIterator(1); iterator.next(); iterator.set(value); },
                (list, value) -> { var iterator = list.subList(1, 2).listIterator(); iterator.next(); iterator.set(value); },
                (list, value) -> { var iterator = list.subList(1, 2).listIterator(); iterator.next(); iterator.remove(); iterator.add(value); },
                (list, value) -> { var view = list.subList(1, 2); view.clear(); view.add(value); },
                (list, value) -> { var view = list.subList(1, 2); view.removeIf(old -> true); view.add(value); },
                (list, value) -> { var view = list.subList(1, 2); view.removeAll(new ArrayList<>(view)); view.add(value); },
                (list, value) -> { var view = list.subList(1, 2); view.retainAll(List.of()); view.add(value); }
        );
        Document document = indexedDocument();
        for (var mutation : mutations) {
            ElementTree tree = new ElementTree(document);
            ArrayList<Element> elements = tree.getElements();
            Element left = new Element(document, "left");
            Element old = new Element(document, "old");
            Element right = new Element(document, "right");
            elements.addAll(List.of(left, old, right));
            tree.updateInitializedElement(new Element(document, "unknown"), new Element(document, "unknown"));
            Element added = new Element(document, "added");
            mutation.accept(elements, added);
            // Check removal before any successful replacement can invalidate the index.
            left.uuid = old.uuid;
            Element wronglyRegistered = replacementFor(old);
            tree.updateInitializedElement(old, wronglyRegistered);
            assertSame(left, elements.get(0));
            Element replacement = replacementFor(added);
            tree.updateInitializedElement(added, replacement);
            assertSame(replacement, elements.get(1));
        }
    }

    @Test
    void trustedAppendsAndInitializationsDoNotRebuildTheIdentityIndex() throws Exception {
        Document document = indexedDocument();
        ElementTree tree = document.getTree();
        java.lang.reflect.Field rebuildCount = ElementTree.class.getDeclaredField("elementIndexRebuildCount");
        rebuildCount.setAccessible(true);
        int before = rebuildCount.getInt(tree);

        Element first = append(document, "first-trusted");
        Element second = append(document, "second-trusted");
        assertEquals(before, rebuildCount.getInt(tree));
        Element origin = initializableOrigin(document);
        Element secondOrigin = initializableOrigin(document);
        document.body.childNodes.add(origin);
        document.body.childNodes.add(secondOrigin);
        tree.rebuildFromRoot(document.documentElement);
        int afterRebuild = rebuildCount.getInt(tree);
        origin.uuid = java.util.UUID.randomUUID();
        secondOrigin.uuid = java.util.UUID.randomUUID();
        Element.init(origin);
        Element.init(secondOrigin);
        assertEquals(afterRebuild, rebuildCount.getInt(tree));
        assertSame(first, document.getElements().get(3));
        assertSame(second, document.getElements().get(4));
        assertEquals(before + 1, afterRebuild);
    }

    @Test
    void initializationSkipsUnregisteredOriginsEvenWhenUuidMatchesRegisteredEntry() {
        Document document = indexedDocument();
        Element registered = append(document, "registered");
        Element detached = initializableOrigin(document);
        detached.uuid = registered.uuid;

        Element initialized = Element.init(detached);

        assertNotSame(detached, initialized);
        assertInstanceOf(IndexInitProbe.class, initialized);
        assertSame(registered, document.getElements().get(document.getElements().size() - 1));
        // The public API still finds by UUID, independent of identity registration.
        document.updateElement(initialized);
        assertSame(initialized, document.getElements().get(document.getElements().size() - 1));
    }

    @Test
    void initializationStillReplacesRegisteredOriginsAfterUuidMutation() {
        Document document = indexedDocument();
        Element origin = initializableOrigin(document);
        // Register a raw origin without appendChild eagerly specializing it.
        document.body.childNodes.add(origin);
        document.getTree().rebuildFromRoot(document.documentElement);
        origin.uuid = java.util.UUID.randomUUID();

        Element initialized = Element.init(origin);

        assertNotSame(origin, initialized);
        assertInstanceOf(IndexInitProbe.class, initialized);
        assertSame(initialized, document.getElements().get(document.getElements().size() - 1));
    }

    private static Element initializableOrigin(Document document) {
        // Unit tests do not bootstrap the platform's built-in tag registry.
        Element.register(INIT_PROBE_TAG, IndexInitProbe::new);
        return new Element(document, INIT_PROBE_TAG);
    }

    private static final class IndexInitProbe extends Element {
        private IndexInitProbe(Document document, String tagName) {
            super(document, tagName);
        }
    }

    private Document indexedDocument() {
        String path = "test://element-tree-index/" + java.util.UUID.randomUUID();
        HTML.putTemple(path, "<html><head></head><body></body></html>");
        document = Document.create(path);
        return document;
    }

    private static Element append(Document document, String tag) {
        Element element = new Element(document, tag);
        document.body.appendChild(element);
        return element;
    }

    private static Element replacementFor(Element original) {
        Element replacement = new Element(original.document, original.tagName);
        replacement.uuid = original.uuid;
        return replacement;
    }

    private static void assertElementOrder(Document document, Element... expected) {
        assertEquals(List.of(expected), document.getElements());
    }

    private static void assertBodyOrder(Document document, Element... expected) {
        assertEquals(List.of(expected), document.body.getChildren());
    }
}
