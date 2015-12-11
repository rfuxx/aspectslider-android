package de.westfalen.fuldix.aspectslider.swipe;

public interface SwipeGestureListener {
    enum Direction { SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT }
    void onSwipe(final Direction direction);
    void onDoubleTap();
}
