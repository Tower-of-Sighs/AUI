package com.sighs.apricityui.element;

import com.sighs.apricityui.container.bind.ContainerBindType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerDeclarationTest {
    @Test
    void genericResourceOptionsAreNormalizedAndRemainOptIn() {
        ContainerDeclaration legacy = new ContainerDeclaration(
                " storage ", ContainerBindType.BLOCK_ENTITY, -1, true);
        assertEquals("storage", legacy.id());
        assertEquals(0, legacy.capacity());
        assertEquals("all", legacy.resourceType());
        assertFalse(legacy.merge());

        ContainerDeclaration fluid = new ContainerDeclaration(
                "tank", ContainerBindType.BLOCK_ENTITY, 9, false, " FLUID ", true);
        assertEquals("fluid", fluid.resourceType());
        assertTrue(fluid.merge());

        ContainerDeclaration invalid = new ContainerDeclaration(
                "mixed", ContainerBindType.ENTITY, 3, false, "energy", false);
        assertEquals("all", invalid.resourceType());
    }
}
