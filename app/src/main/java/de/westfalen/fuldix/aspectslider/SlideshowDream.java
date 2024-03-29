package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.content.res.Configuration;
import android.service.dreams.DreamService;
import android.view.KeyEvent;
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

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        slideshow.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent me) {
        slideshow.dispatchTouchEvent(me);
        return super.dispatchTouchEvent(me);
    }

    @Override
    public boolean dispatchTrackballEvent(final MotionEvent event) {
        final boolean scons = super.dispatchTrackballEvent(event);
        final boolean cons = slideshow.onTrackballEvent(event);
        return scons || cons;
    }

    @Override
    public boolean dispatchGenericMotionEvent (final MotionEvent event) {
        final boolean scons = super.dispatchGenericMotionEvent(event);
        final boolean cons = slideshow.onGenericMotionEvent(event);
        return scons || cons;
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        if(event.getAction() == KeyEvent.ACTION_DOWN) {
            return slideshow.onKeyDown(event.getKeyCode());
        } else {
            return super.dispatchKeyEvent(event);
        }
    }
}
