package de.westfalen.fuldix.aspectslider.swipe;

import android.view.MotionEvent;

public interface SwipeGestureListener {
    enum Direction { SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_RIGHT };
    void onSwipe(Direction direction);
    boolean onDoubleTap(MotionEvent me);
}
