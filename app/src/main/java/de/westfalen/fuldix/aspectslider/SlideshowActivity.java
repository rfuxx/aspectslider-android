package de.westfalen.fuldix.aspectslider;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Vector;

import de.westfalen.fuldix.aspectslider.swipe.SwipeGestureFilter;
import de.westfalen.fuldix.aspectslider.swipe.SwipeGestureListener;
import de.westfalen.fuldix.aspectslider.uithread.VisibilityRunnable;
import de.westfalen.fuldix.aspectslider.util.BitmapUtils;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

public class SlideshowActivity extends Activity {
    private final Slideshow slideshow = new Slideshow(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_slideshow);
        final Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        slideshow.onCreate();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        slideshow.onStartFromScratch();
    }

    @Override
    public void onPause() {
        super.onPause();
        slideshow.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        slideshow.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        slideshow.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        slideshow.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return slideshow.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return slideshow.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        return slideshow.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
        slideshow.onOptionsMenuClosed(menu);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        return slideshow.onTrackballEvent(event);
    }

    @TargetApi(12)
    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        return slideshow.onGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent me) {
        slideshow.dispatchTouchEvent(me);
        return super.dispatchTouchEvent(me);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean ret = slideshow.onKeyDown(keyCode, event);
        return ret || super.onKeyDown(keyCode, event);
    }
}
