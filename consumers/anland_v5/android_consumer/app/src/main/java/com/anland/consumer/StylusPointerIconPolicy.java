package com.anland.consumer;

/** Selects when Android's separate stylus hover icon should be suppressed. */
final class StylusPointerIconPolicy {
    private StylusPointerIconPolicy() {}

    static boolean shouldHide(boolean fromMouse, int toolType,
                              int stylusToolType, int eraserToolType) {
        return !fromMouse && (toolType == stylusToolType || toolType == eraserToolType);
    }
}
