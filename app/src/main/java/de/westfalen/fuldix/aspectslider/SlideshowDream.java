package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.service.dreams.DreamService;
import android.view.MotionEvent;

@TargetApi(17)
public class SlideshowDream extends DreamService {
    private final Slideshow slideshow = new Slideshow(this);

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(true);
        setFullscreen(true);
        setContentView(R.layout.activity_slideshow);
        slideshow.onCreate();
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        slideshow.onStartFromScratch();
    }

    @Override
    public void onDreamingStopped() {
        super.onDreamingStopped();
        slideshow.onPause();
    }

/*
    @Override
    public void onDetachedFromWindow() {
    }
*/

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        slideshow.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent me) {
        slideshow.dispatchTouchEvent(me);
        return super.dispatchTouchEvent(me);
    }
}
