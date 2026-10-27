package com.anland.consumer;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StylusPointerIconPolicyTest {
    private static final int TOOL_STYLUS = 2;
    private static final int TOOL_ERASER = 4;
    private static final int TOOL_FINGER = 1;

    @Test
    public void hidesStylusAndEraserIconsForHoverSources() {
        assertTrue(StylusPointerIconPolicy.shouldHide(false, TOOL_STYLUS,
                TOOL_STYLUS, TOOL_ERASER));
        assertTrue(StylusPointerIconPolicy.shouldHide(false, TOOL_ERASER,
                TOOL_STYLUS, TOOL_ERASER));
    }

    @Test
    public void preservesMouseAndFingerPointerResolution() {
        assertFalse(StylusPointerIconPolicy.shouldHide(true, TOOL_STYLUS,
                TOOL_STYLUS, TOOL_ERASER));
        assertFalse(StylusPointerIconPolicy.shouldHide(false, TOOL_FINGER,
                TOOL_STYLUS, TOOL_ERASER));
    }
}
