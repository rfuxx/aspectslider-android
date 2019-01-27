package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;

import java.util.HashMap;
import java.util.Map;

import de.westfalen.fuldix.aspectslider.util.PermissionUtils;

public class SlideshowActivity extends Activity implements PermissionUtils.PermissionResultReceiverSupport {
    private final Slideshow slideshow = new Slideshow(this);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_slideshow);
        final Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        slideshow.onCreate();
    }

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
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
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        slideshow.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        slideshow.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        return slideshow.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        slideshow.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        slideshow.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public void onOptionsMenuClosed(final Menu menu) {
        super.onOptionsMenuClosed(menu);
        slideshow.onOptionsMenuClosed();
    }

    @Override
    public boolean onTrackballEvent(final MotionEvent event) {
        return slideshow.onTrackballEvent(event);
    }

    @TargetApi(12)
    @Override
    public boolean onGenericMotionEvent(final MotionEvent event) {
        return slideshow.onGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent me) {
        slideshow.dispatchTouchEvent(me);
        return super.dispatchTouchEvent(me);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        final boolean ret = slideshow.onKeyDown(keyCode);
        return ret || super.onKeyDown(keyCode, event);
    }

    private final SparseArray<ResultReceiver> permissionResultReceivers = new SparseArray<>();

    @Override
    public void setPermissionResultReceiver(final int requestCode, final ResultReceiver resultReceiver) {
        permissionResultReceivers.put(requestCode, resultReceiver);
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String permissions[], final int[] grantResults) {
        final ResultReceiver resultReceiver = permissionResultReceivers.get(requestCode);
        if(resultReceiver != null) {
            PermissionUtils.requestPermissionsResultToResultReceiver(requestCode, permissions, grantResults, resultReceiver);
        }
    }
}
