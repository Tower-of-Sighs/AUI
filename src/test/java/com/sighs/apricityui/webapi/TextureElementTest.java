package com.sighs.apricityui.webapi;

import com.sighs.apricityui.element.Texture;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.resource.HTML;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextureElementTest {
    @BeforeAll
    static void registerTextureElement() {
        Element.register(Texture.TAG_NAME, (document, tagName) -> new Texture(document));
    }

    @Test
    void htmlTextureBehavesAsVoidElement() {
        Document document = TestDocumentFactory.createDocument();

        Element root = HTML.createElement(document, """
                <div><texture><span>tail</span></div>
                """);

        assertNotNull(root);
        assertEquals(2, root.getChildren().size());
        assertInstanceOf(Texture.class, root.getChildren().get(0));
        assertEquals("SPAN", root.getChildren().get(1).getNodeName());
    }

    @Test
    void sourceInitializesFromHtmlAndTracksRuntimeAttributeChanges() {
        assumeMinecraftResourceRuntime();
        Document document = TestDocumentFactory.createDocument();
        Texture texture = assertInstanceOf(
                Texture.class,
                HTML.createElement(document, "<texture src=\"superbwarfare:textures/gun_icon/ak47.png\">")
        );

        assertEquals("superbwarfare:textures/gun_icon/ak47.png", texture.getCurrentSrc());

        texture.setAttribute("src", "minecraft:textures/gui/icons.png");
        assertEquals("minecraft:textures/gui/icons.png", texture.getCurrentSrc());

        texture.setAttribute("src", "not a valid resource location");
        assertEquals("", texture.getCurrentSrc());

        texture.removeAttribute("src");
        assertEquals("", texture.getCurrentSrc());
    }

    private static void assumeMinecraftResourceRuntime() {
        Assumptions.assumeTrue(isClassPresent("net.minecraft.resources.ResourceLocation"));
    }

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
