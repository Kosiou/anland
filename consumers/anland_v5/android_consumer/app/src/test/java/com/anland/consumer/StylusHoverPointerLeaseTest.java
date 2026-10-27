package com.anland.consumer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class StylusHoverPointerLeaseTest {
    @Test
    public void buildsPerUserSystemSettingCommands() {
        assertEquals("settings --user 10 get system pen_hovering_pointer",
                StylusHoverPointerLease.getCommand(10));
        assertEquals("settings --user 10 put system pen_hovering_pointer 0",
                StylusHoverPointerLease.putCommand(10, "0"));
    }

    @Test
    public void restoresPreviousValueOnlyWhileAnlandValueIsStillSet() {
        assertEquals("settings --user 0 put system pen_hovering_pointer 1",
                StylusHoverPointerLease.restoreCommand(0, "0", "1"));
        assertEquals("settings --user 0 put system pen_hovering_pointer 0",
                StylusHoverPointerLease.restoreCommand(0, "0", "0"));
        assertNull(StylusHoverPointerLease.restoreCommand(0, "1", "0"));
    }

    @Test
    public void deletesPreviouslyUnsetSettingWhenRestoring() {
        assertEquals("settings --user 0 delete system pen_hovering_pointer",
                StylusHoverPointerLease.restoreCommand(0, "0", "null"));
    }
}
