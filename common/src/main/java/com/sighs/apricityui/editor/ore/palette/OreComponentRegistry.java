package com.sighs.apricityui.editor.ore.palette;

import java.util.List;
import com.sighs.apricityui.style.Text;

public final class OreComponentRegistry {
    private static final List<OreComponentDefinition> DEFINITIONS = List.of(
            new OreComponentDefinition("row", "ore_editor.apricityui.container.row", "ore_editor.apricityui.container.row.description", true, null, null),
            new OreComponentDefinition("column", "ore_editor.apricityui.container.column", "ore_editor.apricityui.container.column.description", true, null, null),
            new OreComponentDefinition("button", "ore_editor.apricityui.component.button", "ore_editor.apricityui.component.button.description", false, "button", "Button"),
            new OreComponentDefinition("heading", "ore_editor.apricityui.component.heading", "ore_editor.apricityui.component.heading.description", false, "h2", "Heading"),
            new OreComponentDefinition("text", "ore_editor.apricityui.component.text", "ore_editor.apricityui.component.text.description", false, "p", "Text")
    );

    private OreComponentRegistry() { }
    public static List<OreComponentDefinition> definitions() { return DEFINITIONS; }
}
