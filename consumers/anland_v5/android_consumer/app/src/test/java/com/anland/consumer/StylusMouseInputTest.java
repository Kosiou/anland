package com.anland.consumer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StylusMouseInputTest {
    private StylusMouseInput.Update update(StylusMouseInput state, boolean touching,
            boolean hovering, boolean cancelled, boolean side, boolean rightClick,
            boolean wheel, float y) {
        return state.update(touching, hovering, cancelled, side, rightClick,
                wheel, y, 0.5f);
    }

    @Test
    public void mapsTipToLeftAndSideButtonContactToRight() {
        StylusMouseInput state = new StylusMouseInput();
        assertEquals(StylusMouseInput.BUTTON_LEFT,
                update(state, true, false, false, false, true, true, 0).button);
        assertEquals(StylusMouseInput.BUTTON_RIGHT,
                update(state, true, false, false, true, true, true, 0).button);
        assertEquals(StylusMouseInput.BUTTON_NONE,
                update(state, false, false, false, false, true, true, 0).button);
    }

    @Test
    public void hoverSideButtonScrollsButDoesNotClick() {
        StylusMouseInput state = new StylusMouseInput();
        assertEquals(StylusMouseInput.BUTTON_NONE,
                update(state, false, true, false, true, true, true, 100).button);
        assertEquals(-5f, update(state, false, true, false, true, true, true, 110)
                .verticalScroll, 0.001f);
    }

    @Test
    public void cancelsHeldButtonAndDisabledRightClickKeepsLeftClick() {
        StylusMouseInput state = new StylusMouseInput();
        update(state, true, false, false, true, true, true, 0);
        StylusMouseInput.Update cancelled = update(state, false, false, true,
                true, true, true, 0);
        assertTrue(cancelled.buttonChanged);
        assertEquals(StylusMouseInput.BUTTON_NONE, cancelled.button);
        assertEquals(StylusMouseInput.BUTTON_LEFT,
                update(state, true, false, false, true, false, true, 0).button);
    }
}
