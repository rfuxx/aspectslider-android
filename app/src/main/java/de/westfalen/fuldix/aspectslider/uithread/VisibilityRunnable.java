package de.westfalen.fuldix.aspectslider.uithread;

import android.view.View;

public class VisibilityRunnable implements Runnable {
    View view;
    int visibility;

    public VisibilityRunnable(View view, int visibility) {
        this.view = view;
        this.visibility = visibility;
    }

    @Override
    public void run() {
        view.setVisibility(visibility);
    }
}
