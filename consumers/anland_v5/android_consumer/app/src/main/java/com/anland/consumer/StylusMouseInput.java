package com.anland.consumer;

/** Stateful S Pen to mouse mapping used only by the explicit compatibility mode. */
final class StylusMouseInput {
    static final int BUTTON_NONE = 0;
    static final int BUTTON_LEFT = 0x110;
    static final int BUTTON_RIGHT = 0x111;

    static final class Update {
        final int button;
        final boolean buttonChanged;
        final float verticalScroll;

        Update(int button, boolean buttonChanged, float verticalScroll) {
            this.button = button;
            this.buttonChanged = buttonChanged;
            this.verticalScroll = verticalScroll;
        }
    }

    private int button = BUTTON_NONE;
    private boolean scrolling;
    private float lastY;

    Update update(boolean touching, boolean hovering, boolean cancelled,
                  boolean sideButtonDown, boolean sideButtonRightClick,
                  boolean hoverScrollEnabled, float y, float sensitivity) {
        int nextButton = cancelled ? BUTTON_NONE : touching
                ? (sideButtonDown && sideButtonRightClick ? BUTTON_RIGHT : BUTTON_LEFT)
                : BUTTON_NONE;
        boolean buttonChanged = button != nextButton;
        button = nextButton;

        if (cancelled || !hovering || !sideButtonDown || !hoverScrollEnabled
                || !Float.isFinite(y) || !Float.isFinite(sensitivity)) {
            scrolling = false;
            return new Update(button, buttonChanged, 0f);
        }
        if (!scrolling) {
            scrolling = true;
            lastY = y;
            return new Update(button, buttonChanged, 0f);
        }
        float delta = y - lastY;
        lastY = y;
        return new Update(button, buttonChanged, -delta * sensitivity);
    }

    int heldButton() {
        return button;
    }
}
