package de.westfalen.fuldix.aspectslider.uithread;

import android.view.View;

public class VisibilityRunnable implements Runnable {
    private final View view;
    private final int visibility;

    public VisibilityRunnable(final View view, final int visibility) {
        this.view = view;
        this.visibility = visibility;
    }

    @Override
    public void run() {
        view.setVisibility(visibility);
    }
}
