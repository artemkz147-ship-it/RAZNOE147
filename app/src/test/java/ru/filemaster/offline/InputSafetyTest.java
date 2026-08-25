package ru.filemaster.offline;

import org.junit.Test;

import static org.junit.Assert.*;

public class InputSafetyTest {
    @Test public void keepsSimpleExtension() {
        assertEquals(".pdf", InputSafety.safeTempSuffix("Документ.PDF", ".bin"));
    }

    @Test public void rejectsPathLikeExtension() {
        assertEquals(".bin", InputSafety.safeTempSuffix("evil.a/../../x", ".bin"));
    }

    @Test public void normalizesFallback() {
        assertEquals(".jpg", InputSafety.safeTempSuffix("без_расширения", "jpg"));
        assertEquals(".bin", InputSafety.safeTempSuffix(null, "../bad"));
    }

    @Test public void requiresCacheHeadroom() {
        long size = 100L * 1024L * 1024L;
        assertFalse(InputSafety.enoughCache(size, size));
        assertTrue(InputSafety.enoughCache(size + InputSafety.CACHE_HEADROOM, size));
        assertTrue(InputSafety.enoughCache(1L, -1L));
    }

    @Test public void sanitizesSubfolder() {
        String value = InputSafety.safeSubfolder("../bad\\folder/name");
        assertNotNull(value);
        assertFalse(value.contains(".."));
        assertFalse(value.contains("/"));
        assertFalse(value.contains("\\"));
    }
}
