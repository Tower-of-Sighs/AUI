package com.sighs.apricityui.resource;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class DerivedFontCacheTest {
    static class CountingFont extends java.awt.Font {
        int metadataReads;
        CountingFont() { super("Dialog", PLAIN, 48); }
        @Override public String getFontName(Locale locale) { metadataReads++; return super.getFontName(locale); }
        @Override public String getFamily(Locale locale) { metadataReads++; return super.getFamily(locale); }
    }

    @Test
    void cacheLookupDoesNotReadFontNameTablesAndSeparatesBaseIdentity() throws Exception {
        Method derive = Font.class.getDeclaredMethod("deriveCachedFont", java.awt.Font.class, int.class, float.class);
        derive.setAccessible(true);
        CountingFont base = new CountingFont();
        Object first = derive.invoke(null, base, java.awt.Font.PLAIN, 16f);
        int readsAfterDerivation = base.metadataReads;
        assertSame(first, derive.invoke(null, base, java.awt.Font.PLAIN, 16f));
        assertEquals(readsAfterDerivation, base.metadataReads);
        assertNotSame(first, derive.invoke(null, base, java.awt.Font.BOLD, 16f));
        assertNotSame(first, derive.invoke(null, base, java.awt.Font.PLAIN, 20f));
        assertNotSame(first, derive.invoke(null, new CountingFont(), java.awt.Font.PLAIN, 16f));
        Method clear = Font.class.getDeclaredMethod("clearResolutionCaches");
        clear.setAccessible(true);
        clear.invoke(null);
        assertNotSame(first, derive.invoke(null, base, java.awt.Font.PLAIN, 16f));
    }
}
