package com.sighs.apricityui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuiLoggingTest {
    @Test
    void derivesAuiPathsBesideLatestLog() {
        assertEquals("logs/aui.log", AuiLogging.auiFileName("logs/latest.log"));
        assertEquals("logs\\aui.log", AuiLogging.auiFileName("logs\\latest.log"));
    }
}
