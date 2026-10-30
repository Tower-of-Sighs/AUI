package com.sighs.apricityui.parser;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SelectorSiblingStructureRegressionTest {
    @Test
    void structuralPseudoClassesTrackInsertionsRemovalsAndDetachedElements() {
        Document document = new Document("test://selector-structure", false);
        Element parent = new Element(document, "div");
        Element first = new Element(document, "p");
        Element second = new Element(document, "span");
        Element last = new Element(document, "p");
        parent.appendChild(first);
        parent.appendChild(second);
        parent.appendChild(last);

        assertTrue(Selector.matches(first, ":first-child"));
        assertFalse(Selector.matches(first, ":last-child"));
        assertTrue(Selector.matches(last, ":last-child"));
        assertTrue(Selector.matches(first, ":first-of-type"));
        assertTrue(Selector.matches(last, ":last-of-type"));

        parent.removeChild(first);
        assertNull(first.parentElement);
        assertFalse(Selector.matches(first, ":first-child"));
        assertFalse(Selector.matches(first, ":first-of-type"));
        assertTrue(Selector.matches(second, ":first-child"));
        assertTrue(Selector.matches(last, ":first-of-type"));
        assertTrue(Selector.matches(last, ":last-child"));

        Element inserted = new Element(document, "p");
        parent.insertBefore(inserted, last);
        assertTrue(Selector.matches(inserted, ":first-of-type"));
        assertFalse(Selector.matches(inserted, ":last-of-type"));
        assertTrue(Selector.matches(last, ":last-of-type"));
        parent.removeChild(inserted);
        assertTrue(Selector.matches(last, ":last-of-type"));
    }

    @Test
    void staleParentMembershipDoesNotMakeElementAChildOrOfTypeBoundary() {
        Document document = new Document("test://selector-stale-parent", false);
        Element parent = new Element(document, "div");
        Element realChild = new Element(document, "p");
        Element stale = new Element(document, "p");
        parent.appendChild(realChild);
        stale.parentElement = parent;

        assertFalse(Selector.matches(stale, ":first-child"));
        assertFalse(Selector.matches(stale, ":last-child"));
        assertFalse(Selector.matches(stale, ":first-of-type"));
        assertFalse(Selector.matches(stale, ":last-of-type"));
        assertTrue(Selector.matches(realChild, ":first-child"));
        assertTrue(Selector.matches(realChild, ":last-of-type"));
    }

    @Test
    void detachedElementsDoNotMatchStructuralPseudoClasses() {
        Document document = new Document("test://selector-detached", false);
        Element detached = new Element(document, "p");
        assertFalse(Selector.matches(detached, ":first-child"));
        assertFalse(Selector.matches(detached, ":last-child"));
        assertFalse(Selector.matches(detached, ":first-of-type"));
        assertFalse(Selector.matches(detached, ":last-of-type"));
    }
}
