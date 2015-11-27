package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
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
import android.service.dreams.DreamService;
import android.view.Display;
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
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

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

public class Slideshow {
    public static final String PREF_SIZEFILTER_NONE = "none";
    public static final String PREF_SIZEFILTER_FITSCREEN = "fitscreen";
    public static final String PREF_SIZEFILTER_ASLARGEASSCREEN = "aslargeasscreen";
    public static final String PREF_SIZEFILTER_WRONGASPECT = "wrongaspect";
    public static final String PREF_SIZEFILTER_HD720P = "hd720p";
    public static final String PREF_SIZEFILTER_HD1080P = "hd1080p";
    public static final String PREF_SIZEFILTER_VIDEO4K = "video4k";
    static final String PREF_MEDIA_URI = "media_uri";
    static final String PREF_DIRPATH = "dirpath";
    static final String PREF_MEDIA_SELECTION = "media_selection";
    static final int constAnimTimeToSlide = 400;
    static final int constAnimFps = 30;

    private final Context context;
    private int settingNextSlideAfter = 5000;
    private int settingSpaceBetweenSlides = 0;
    private boolean settingAllowOverscan = false;
    private boolean settingRandom = false;
    private boolean settingRandomAgain = false;
    private boolean settingRecurse = true;
    private String settingSizeFilter = Slideshow.PREF_SIZEFILTER_HD720P;
    private boolean settingRememberCollection = false;
    private boolean settingIgnoreMediaStore = false;

    private SwipeGestureFilter swipeDetector;
    private VisibilityRunnable scanningShow;
    private VisibilityRunnable scanningHide;
    private VisibilityRunnable nopicsShow;
    private VisibilityRunnable nopicsHide;
    private Handler slideshowHandler;
    private Handler preloadHandler;
    private Handler fileHandler;
    private final Handler uiHandler = new Handler();
    private File path = new File("");
    private Uri mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    private String mediaSelection;
    private final Vector<PicInfo> pictures = new Vector<>();
    private int refusedPics;
    private int sw, sh;
    private float screenRatio;
    private Rect oldPicPos;
    private Rect currentPicPos;
    private int currentPicture;
    private PicInfo currentPic;
    private SurfaceView canvasView;
    private Menu menu;
    private final Collection<Bitmap> beforeBitmaps = new LinkedList<>();
    private final Collection<Bitmap> afterBitmaps = new LinkedList<>();
    private final Collection<Bitmap> oldBitmaps = new LinkedList<>();
    private boolean vertical;
    private boolean deviceIsVertical;
    private final List<Runnable> uiAnimateTasks = new ArrayList<>();
    private boolean slideshowRunning = false;
    private boolean paused = false;
    private boolean scanRunning = false;
    private final Collection<Preloader> scheduledPreloaders = new LinkedList<>();
    private final Random random = new Random();
    private final SettingsListener settingsListener = new SettingsListener();
    private final ClearScreenRunnable clearScreenRunnable = new ClearScreenRunnable();
    private HideSystemBarAndNavHoneycomb hideSystemBarAndNavHoneycomb;

    private class InitialHideUIRunnable implements Runnable {
        private Runnable runnable;
        public InitialHideUIRunnable(Runnable runnable) {
            this.runnable = runnable;
        }
        public void run() {
            runnable.run();
        }
    }
    @TargetApi(11)
    private class HideSystemBarAndNavHoneycomb implements Runnable {
        boolean isVisible = true;

        public void run() {
            final View contentView = findViewById(R.id.fullscreen_content);
            if(contentView != null) {
                contentView.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                | View.SYSTEM_UI_FLAG_LOW_PROFILE
                );
            }
            if(context instanceof Activity) {
                ActionBar ab = ((Activity) context).getActionBar();
                if (ab != null) {
                    ab.hide();
                }
            }
            isVisible = false;
        }
    }

    private class ClearScreenRunnable implements Runnable {
        public void run() {
            final SurfaceHolder holder = canvasView.getHolder();
            Canvas c = holder.lockCanvas();
            if (c != null) {
                c.drawColor(context.getResources().getColor(R.color.solid_black));
                holder.unlockCanvasAndPost(c);
            }
        }
    }

    private class UpdateScreenRunnable implements Runnable {
        private final Rect animatePos;
        private final Bitmap currentBitmap;
        private final Collection<Bitmap> beforeBitmaps;
        private final Collection<Bitmap> afterBitmaps;

        UpdateScreenRunnable(Rect animatePos, Bitmap currentBitmap, Collection<Bitmap> beforeBitmaps, Collection<Bitmap> afterBitmaps) {
            this.animatePos = animatePos;
            this.currentBitmap = currentBitmap;
            this.beforeBitmaps = beforeBitmaps;
            this.afterBitmaps = afterBitmaps;
        }

        @Override
        public void run() {
            final SurfaceHolder holder = canvasView.getHolder();
            Canvas c = holder.lockCanvas();
            if (c == null) {
                System.err.println("UpdateScreenRunnable's SurfaceHolder.lockCanvas() got null");
                return;
            }
            c.drawColor(context.getResources().getColor(R.color.solid_black));
            if (currentBitmap != null) {
                synchronized (currentBitmap) {
                    if (!currentBitmap.isRecycled()) {
                        c.drawBitmap(currentBitmap, null, animatePos, null);
                    }
                }
            }
            // after
            Rect pos = new Rect(animatePos);
            for (Bitmap b : afterBitmaps) {
                float imgRatio = (float) b.getWidth() / (float) b.getHeight();
                int pw, ph;
                if (shouldAlignToWidth(imgRatio)) {
                    ph = Math.round(sw / imgRatio);
                    pw = sw;
                } else {
                    pw = Math.round(sh * imgRatio);
                    ph = sh;
                }
                if (vertical) {
                    pos.top = pos.bottom + settingSpaceBetweenSlides;
                    pos.left = (sw - pw) / 2;
                } else {
                    pos.left = pos.right + settingSpaceBetweenSlides;
                    pos.top = (sh - ph) / 2;
                }
                pos.right = pos.left + pw;
                pos.bottom = pos.top + ph;
                if (vertical ? pos.top < sh : pos.left < sw) {
                    synchronized (b) {
                        if (!b.isRecycled()) {
                            c.drawBitmap(b, null, pos, null);
                        }
                    }
                }
            }
            // before
            pos.set(animatePos);
            for (Bitmap b : beforeBitmaps) {
                float imgRatio = (float) b.getWidth() / (float) b.getHeight();
                int pw, ph;
                if (shouldAlignToWidth(imgRatio)) {
                    ph = Math.round(sw / imgRatio);
                    pw = sw;
                } else {
                    pw = Math.round(sh * imgRatio);
                    ph = sh;
                }
                if (vertical) {
                    pos.bottom = pos.top - settingSpaceBetweenSlides;
                    pos.left = (sw - pw) / 2;
                    pos.top = pos.bottom - ph;
                    pos.right = pos.left + pw;
                } else {
                    pos.right = pos.left - settingSpaceBetweenSlides;
                    pos.top = (sh - ph) / 2;
                    pos.left = pos.right - pw;
                    pos.bottom = pos.top + ph;
                }
                if (vertical ? pos.bottom >= 0 : pos.right >= 0) {
                    synchronized (b) {
                        if (!b.isRecycled()) {
                            c.drawBitmap(b, null, pos, null);
                        }
                    }
                }
            }
            holder.unlockCanvasAndPost(c);
        }
    }

    private class SettingsListener implements SharedPreferences.OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPref, String key) {
            switch (key) {
                case SettingsActivity.PREF_DELAY:
                    applyDelay(sharedPref);
                    break;
                case SettingsActivity.PREF_SPACE_BETWEEN_SLIDES:
                    applySpaceBetweenSlides(sharedPref);
                    break;
                case SettingsActivity.PREF_ALLOW_OVERSCAN:
                    applyAllowOverscan(sharedPref);
                    break;
                case SettingsActivity.PREF_RANDOM:
                    applyRandom(sharedPref);
                    break;
                case SettingsActivity.PREF_RANDOM_AGAIN:
                    applyRandomAgain(sharedPref);
                    break;
                case SettingsActivity.PREF_RECURSE:
                    applyRecurse(sharedPref);
                    break;
                case SettingsActivity.PREF_SIZE_FILTER:
                    applySizeFilter(sharedPref);
                    break;
                case SettingsActivity.PREF_REMEMBER_COLLECTION:
                    applyRememberCollection(sharedPref);
                    break;
                case SettingsActivity.PREF_IGNORE_MEDIA_STORE:
                    applyIgnoreMediaStore(sharedPref);
                    break;
            }
        }

        void applyDelay(SharedPreferences sharedPref) {
            String delayStr = sharedPref.getString(SettingsActivity.PREF_DELAY, "5000");
            try {
                int delayVal = Integer.parseInt(delayStr);
                if (delayVal < 1000) {
                    delayVal = 1000;
                }
                settingNextSlideAfter = delayVal;
            } catch (NumberFormatException e) {
                System.err.println("NumberFormatException on pref delay: " + delayStr);
            }
        }

        void applySpaceBetweenSlides(SharedPreferences sharedPref) {
            String spaceStr = sharedPref.getString(SettingsActivity.PREF_SPACE_BETWEEN_SLIDES, "min_space");
            Resources res = context.getResources();
            int dim = res.getIdentifier(spaceStr, "dimen", context.getPackageName());
            if (dim != 0) {
                float spaceVal = res.getDimension(dim);
                settingSpaceBetweenSlides = (int) Math.ceil(spaceVal);
            } else {
                System.err.println("Pref resource for <dimen> not found: " + spaceStr);
            }
            if (slideshowHandler != null) {
                slideshowHandler.postAtFrontOfQueue(slideshowJustRedrawRunnable);
            }
        }

        void applyAllowOverscan(SharedPreferences sharedPref) {
            settingAllowOverscan = sharedPref.getBoolean(SettingsActivity.PREF_ALLOW_OVERSCAN, false);
            slideshowHandler.postAtFrontOfQueue(slideshowJustRedrawRunnable);
        }

        void applyRandom(SharedPreferences sharedPref) {
            settingRandom = sharedPref.getBoolean(SettingsActivity.PREF_RANDOM, false);
            synchronized (pictures) {
                if (settingRandom) {
                    Collections.shuffle(pictures, random);
                } else {
                    Collections.sort(pictures);
                }
            }
            slideshowHandler.postAtFrontOfQueue(slideshowJustRedrawRunnable);
        }

        void applyRandomAgain(SharedPreferences sharedPref) {
            settingRandomAgain = sharedPref.getBoolean(SettingsActivity.PREF_RANDOM_AGAIN, false);
        }

        void applyRecurse(SharedPreferences sharedPref) {
            settingRecurse = sharedPref.getBoolean(SettingsActivity.PREF_RECURSE, false);
            fileHandler.post(slideshowEndRunnable);
            scanRunning = false;
            fileHandler.post(picFinderRunnable);
        }

        void applySizeFilter(SharedPreferences sharedPref) {
            settingSizeFilter = sharedPref.getString(SettingsActivity.PREF_SIZE_FILTER, Slideshow.PREF_SIZEFILTER_NONE);
            fileHandler.post(slideshowEndRunnable);
            scanRunning = false;
            fileHandler.post(picFinderRunnable);
        }

        void applyRememberCollection(SharedPreferences sharedPref) {
            settingRememberCollection = sharedPref.getBoolean(SettingsActivity.PREF_REMEMBER_COLLECTION, false);
            saveRememberCollection();
        }

        void applyIgnoreMediaStore(SharedPreferences sharedPref) {
            settingIgnoreMediaStore = sharedPref.getBoolean(SettingsActivity.PREF_IGNORE_MEDIA_STORE, false);
            adjustOptionsMenu();
            fileHandler.post(slideshowEndRunnable);
            scanRunning = false;
            fileHandler.post(picFinderRunnable);
        }
    }

    private class Preloader implements Runnable {
        private final PicInfo pic;

        Preloader(PicInfo pic) {
            this.pic = pic;
        }

        @Override
        public void run() {
            if (sw > 0 && sh > 0) {
                synchronized (pic) {
                    if (pic.bitmap == null || pic.bitmap.isRecycled()) {
                        System.out.println("preload: " + pic.picFile);
                        pic.loadBitmap(sw, sh);
                        if (pic.bitmap != null) {
                            synchronized (oldBitmaps) {
                                oldBitmaps.add(pic.bitmap);
                            }
                        }
                    }
                }
            }
        }
    }

    private final SwipeGestureListener swipeListener = new SwipeGestureListener() {
        @Override
        public void onSwipe(Direction direction) {
            if (!slideshowRunning) {
                return;
            }
            switch (direction) {
                case SWIPE_UP:
                    doUp();
                    break;
                case SWIPE_DOWN:
                    doDown();
                    break;
                case SWIPE_LEFT:
                    doLeft();
                    break;
                case SWIPE_RIGHT:
                    doRight();
                    break;
            }
        }

        @Override
        public boolean onDoubleTap(MotionEvent me) {
            if(context instanceof Activity) {
                ((Activity) context).openOptionsMenu();
            }
            return true;
        }
    };

    private final Runnable picFinderRunnable = new Runnable() {
        @Override
        public void run() {
            uiHandler.post(scanningShow);
            uiHandler.post(nopicsHide);
            pictures.clear();
            refusedPics = 0;

            scanRunning = true;
            if (settingIgnoreMediaStore) {
                if (path == null || path.getName().equals("")) {
                    if (Build.VERSION.SDK_INT >= 8) {
                        path = getExternalPicturesDir();
                        getFilesFromDir(path, pictures);
                        if (pictures.size() == 0) {
                            path = Environment.getDataDirectory();
                            getFilesFromDir(path, pictures);
                        }
                    } else {
                        path = Environment.getDataDirectory();
                        getFilesFromDir(path, pictures);
                    }
                    if (pictures.size() == 0) {
                        path = Environment.getExternalStorageDirectory();
                        getFilesFromDir(path, pictures);
                    }
                    if (pictures.size() == 0) {
                        path = Environment.getRootDirectory();
                        getFilesFromDir(path, pictures);
                    }
                } else {
                    getFilesFromDir(path, pictures);
                }
            } else {
                getFilesFromMediaStore(pictures);
            }
            if(scanRunning && refusedPics > 0) {
                showCentralToast(context.getResources().getQuantityString(R.plurals.refused_pictures, refusedPics, refusedPics, getNameOfSizeFilter()));
            }
            scanRunning = false;
            if(pictures.size() <= 0) {
                uiHandler.post(nopicsShow);
            }
            uiHandler.post(scanningHide);
        }
    };

    private final Runnable slideshowStarterRunnable = new Runnable() {
        @Override
        public void run() {
            currentPicture = -1;
            oldPicPos = new Rect();
            currentPicPos = new Rect(4 * sw, 4 * sh, 5 * sw, 5 * sh);
            slideshowRunning = true;
            paused = false;

            // first image load immediate without before and after
            PicInfo picture;
            do {
                synchronized (pictures) {
                    currentPicture++;
                    if (currentPicture >= pictures.size()) {
                        if (settingRandom && settingRandomAgain) {
                            Collections.shuffle(pictures, random);
                        }
                        currentPicture = 0;
                    }
                    picture = getPicInfo(currentPicture);
                }
                synchronized (picture) {
                    if (picture.bitmap == null || picture.bitmap.isRecycled()) {
                        System.out.println("first: #" + currentPicture + " " + picture.picFile);
                        picture.loadBitmap(sw, sh);
                        if (picture.bitmap == null) {
                            System.err.println("Failedfist: #" + currentPicture + " " + picture.picFile);
                            synchronized (picture) {
                                pictures.remove(picture);
                                currentPicture--;
                                if (pictures.size() <= 0) {
                                    break;
                                }
                            }
                        }
                    }
                }
            } while (picture.bitmap == null);
            if (picture.bitmap != null) {
                final Rect imgRect = calcPicRectInDisplay(picture);
                if (vertical) {
                    oldPicPos.set(imgRect.left, currentPicPos.bottom + settingSpaceBetweenSlides, imgRect.right, currentPicPos.bottom + settingSpaceBetweenSlides + imgRect.bottom - imgRect.top);
                } else {
                    oldPicPos.set(currentPicPos.right + settingSpaceBetweenSlides, imgRect.top, currentPicPos.right + settingSpaceBetweenSlides + imgRect.right - imgRect.left, imgRect.bottom);
                }
                currentPic = picture;
                currentPicPos = imgRect;

            }

            slideshowHandler.post(animateRunnable);
            slideshowHandler.postDelayed(slideshowJustRedrawRunnable, Slideshow.constAnimTimeToSlide);
            slideshowHandler.postDelayed(slideshowRunnable, settingNextSlideAfter);
        }
    };

    private final Runnable slideshowEndSyncer = new Runnable() {
        @Override
        public synchronized void run() {
            this.notifyAll();
        }
    };

    private final Runnable slideshowEndRunnable = new Runnable() {
        @Override
        public void run() {
            slideshowHandler.removeCallbacks(slideshowRunnable);
            slideshowHandler.removeCallbacks(slideshowBackRunnable);
            slideshowHandler.removeCallbacks(slideshowJustRedrawRunnable);
            slideshowHandler.removeCallbacks(animateRunnable);
            synchronized (slideshowEndSyncer) {
                try {
                    slideshowHandler.post(slideshowEndSyncer);
                    slideshowEndSyncer.wait();
                    slideshowRunning = false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            uiHandler.post(clearScreenRunnable);
            for (PicInfo pic : pictures) {
                synchronized (pic) {
                    if (pic.bitmap != null) {
                        pic.bitmap.recycle();
                        pic.bitmap = null;
                    }
                }
            }
            beforeBitmaps.clear();
            afterBitmaps.clear();
            System.gc();
        }
    };

    private final Runnable animateRunnable = new Runnable() {
        @Override
        public void run() {
            for (Runnable uiTask : uiAnimateTasks) {
                uiHandler.removeCallbacks(uiTask);
            }
            uiAnimateTasks.clear();

            final int animSteps = Slideshow.constAnimTimeToSlide * Slideshow.constAnimFps / 1000;
            final LinkedList<Bitmap> taskBeforeBitmaps = new LinkedList<>(beforeBitmaps);
            final LinkedList<Bitmap> taskAfterBitmaps = new LinkedList<>(afterBitmaps);
            final int distanceLeft = currentPicPos.left - oldPicPos.left;
            final int distanceTop = currentPicPos.top - oldPicPos.top;
            final int distanceRight = currentPicPos.right - oldPicPos.right;
            final int distanceBottom = currentPicPos.bottom - oldPicPos.bottom;
            final Rect animatePos = new Rect();
            for (int i = animSteps - 1; i >= 0; i--) {
                animatePos.set(
                        currentPicPos.left - i * distanceLeft / animSteps,
                        currentPicPos.top - i * distanceTop / animSteps,
                        currentPicPos.right - i * distanceRight / animSteps,
                        currentPicPos.bottom - i * distanceBottom / animSteps
                );
                // only prepare uiAnimateTasks
                uiAnimateTasks.add(new UpdateScreenRunnable(new Rect(animatePos), currentPic.bitmap, taskBeforeBitmaps, taskAfterBitmaps));
            }
            // now post uiAnimateTasks
            for (int i = 0; i < uiAnimateTasks.size(); i++) {
                uiHandler.postDelayed(uiAnimateTasks.get(i), i * 20);
            }
        }
    };

    private final Runnable slideshowJustRedrawRunnable = new Runnable() {
        @Override
        public void run() {
            unschedulePreloaders(); // if the preloaders were too slow, cancel them and try "here" directly
            if (pictures.size() == 0 || currentPicture == -1) {
                return;
            }
            PicInfo currentPic = Slideshow.this.currentPic;
            if (currentPic != null) {
                slideshowHandler.removeCallbacks(animateRunnable);
                for (Runnable uiTask : uiAnimateTasks) {
                    uiHandler.removeCallbacks(uiTask);
                }
                uiAnimateTasks.clear();

                markOldBitmaps();

                final Rect imgRect = calcPicRectInDisplay(currentPic);
                oldPicPos = imgRect;
                currentPicPos = imgRect;
                loadRequiredAfterBitmaps();
                Runnable uiTask = new UpdateScreenRunnable(new Rect(currentPicPos), currentPic.bitmap, new LinkedList<>(beforeBitmaps), new LinkedList<>(afterBitmaps));
                uiHandler.post(uiTask);

                loadRequiredBeforeBitmaps();
                recycleUnneededBitmaps();

                uiTask = new UpdateScreenRunnable(new Rect(currentPicPos), currentPic.bitmap, new LinkedList<>(beforeBitmaps), new LinkedList<>(afterBitmaps));
                uiHandler.post(uiTask);
            }
        }
    };

    private final Runnable slideshowRunnable = new Runnable() {
        @Override
        public void run() {
            unschedulePreloaders(); // if the preloaders were too slow, cancel them and try "here" directly
            if (pictures.size() <= 0) {
                // May happen if over runtime, all images turned out as non decodeable or have disappeared
                fileHandler.post(slideshowEndRunnable);
                return;
            }

            if (sw > 0 && sh > 0) {
                markOldBitmaps();

                PicInfo picture;
                do {
                    synchronized (pictures) {
                        currentPicture++;
                        if (currentPicture >= pictures.size()) {
                            if (settingRandom && settingRandomAgain) {
                                Collections.shuffle(pictures, random);
                            }
                            currentPicture = 0;
                        }
                        picture = getPicInfo(currentPicture);
                    }
                    synchronized (picture) {
                        if (picture.bitmap == null || picture.bitmap.isRecycled()) {
                            System.out.println("slidefwd: #" + currentPicture + " " + picture.picFile);
                            picture.loadBitmap(sw, sh);
                            if (picture.bitmap == null) {
                                System.err.println("Failedfwd: #" + currentPicture + " " + picture.picFile);
                                synchronized (picture) {
                                    pictures.remove(picture);
                                    currentPicture--;
                                    if (pictures.size() <= 0) {
                                        break;
                                    }
                                }
                            }
                        }
                    }
                } while (picture.bitmap == null);
                if (picture.bitmap != null) {
                    final Rect imgRect = calcPicRectInDisplay(picture);
                    if (vertical) {
                        oldPicPos.set(imgRect.left, currentPicPos.bottom + settingSpaceBetweenSlides, imgRect.right, currentPicPos.bottom + settingSpaceBetweenSlides + imgRect.bottom - imgRect.top);
                    } else {
                        oldPicPos.set(currentPicPos.right + settingSpaceBetweenSlides, imgRect.top, currentPicPos.right + settingSpaceBetweenSlides + imgRect.right - imgRect.left, imgRect.bottom);
                    }
                    currentPic = picture;
                    currentPicPos = imgRect;
                    loadRequiredAfterBitmaps();
                    loadRequiredBeforeBitmaps();
                    recycleUnneededBitmaps();
                }

                slideshowHandler.post(animateRunnable);
            }

            slideshowHandler.removeCallbacks(slideshowRunnable);
            slideshowHandler.removeCallbacks(slideshowBackRunnable);
            if (!paused) {
                slideshowHandler.postDelayed(slideshowRunnable, settingNextSlideAfter);
            }
        }
    };

    private final Runnable slideshowBackRunnable = new Runnable() {
        @Override
        public void run() {
            unschedulePreloaders(); // if the preloaders were too slow, cancel them and try "here" directly
            if (pictures.size() <= 0) {
                return;
            }

            if (sw > 0 && sh > 0) {
                markOldBitmaps();

                PicInfo picture;
                do {
                    synchronized (pictures) {
                        currentPicture--;
                        if (currentPicture < 0) {
                            if (settingRandom && settingRandomAgain) {
                                Collections.shuffle(pictures, random);
                            }
                            currentPicture = pictures.size() - 1;
                        }
                        picture = getPicInfo(currentPicture);
                    }
                    synchronized (picture) {
                        if (picture.bitmap == null || picture.bitmap.isRecycled()) {
                            System.out.println("slidebck: #" + currentPicture + " " + picture.picFile);
                            picture.loadBitmap(sw, sh);
                            if (picture.bitmap == null) {
                                System.err.println("Failedbck: #" + currentPicture + " " + picture.picFile);
                                synchronized (pictures) {
                                    pictures.remove(picture);
                                }
                            }
                        }
                    }
                } while (picture.bitmap == null);
                if (picture.bitmap != null) {
                    final Rect imgRect = calcPicRectInDisplay(picture);
                    if (vertical) {
                        oldPicPos.set(imgRect.left, currentPicPos.top - settingSpaceBetweenSlides - imgRect.bottom + imgRect.top, imgRect.right, currentPicPos.top - settingSpaceBetweenSlides);
                    } else {
                        oldPicPos.set(currentPicPos.left - settingSpaceBetweenSlides - imgRect.right + imgRect.left, imgRect.top, currentPicPos.left - settingSpaceBetweenSlides, imgRect.bottom);
                    }
                    currentPic = picture;
                    currentPicPos = imgRect;
                    loadRequiredBeforeBitmaps();
                    loadRequiredAfterBitmaps();
                    recycleUnneededBitmaps();
                }

                slideshowHandler.post(animateRunnable);
            }

            slideshowHandler.removeCallbacks(slideshowRunnable);
            slideshowHandler.removeCallbacks(slideshowBackRunnable);
            if (!paused) {
                slideshowHandler.postDelayed(slideshowBackRunnable, settingNextSlideAfter);
            }
        }
    };

    public Slideshow(final Context context) {
        this.context = context;
    }

    private boolean onMoveXY(float xMovement, float yMovement) {
        if (xMovement > 0 && (xMovement > yMovement || (!vertical && xMovement == yMovement))) {
            doRight();
            return true;
        } else if (xMovement < 0 && (xMovement < yMovement || (!vertical && xMovement == yMovement))) {
            doLeft();
            return true;
        } else if (yMovement > 0 && (yMovement > xMovement || (vertical && yMovement == xMovement))) {
            doDown();
            return true;
        } else if (yMovement < 0 && (yMovement < xMovement || (vertical && yMovement == xMovement))) {
            doUp();
            return true;
        }
        return false;
    }

    public boolean onTrackballEvent(MotionEvent event) {
        return onMoveXY(event.getX(), event.getY());
    }

    public boolean onGenericMotionEvent(MotionEvent event) {
        switch (event.getSource()) {
            case InputDevice.SOURCE_GAMEPAD:
            case InputDevice.SOURCE_JOYSTICK:
                // opposite direction because joystick does not swipe but rather
                // want next/prev semantics (like keyboard arrow keys)
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    return onMoveXY(-event.getAxisValue(MotionEvent.AXIS_X), -event.getAxisValue(MotionEvent.AXIS_Y));
                }
                break;
            case InputDevice.SOURCE_TRACKBALL:
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    return onMoveXY(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y));
                }
                break;
            case InputDevice.SOURCE_MOUSE:
                if (event.getAction() == MotionEvent.ACTION_SCROLL) {
                    return onMoveXY(event.getAxisValue(MotionEvent.AXIS_HSCROLL), event.getAxisValue(MotionEvent.AXIS_VSCROLL));
                }
        }
        return false;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // left/right and up/down looks swapped,
        // but that's because on keyboard there is no swipe semantics,
        // and we rather want next/prev semantics
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                doDown();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                doUp();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                doRight();
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                doLeft();
                return true;
            case KeyEvent.KEYCODE_SPACE:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_PLUS:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                doNext();
                return true;
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                doPrev();
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                paused = false;
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                paused = true;
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                paused = !paused;
                return true;
        }
        return false;
    }

    private void doLeft() {
        vertical = false;
        doNext();
    }

    private void doRight() {
        vertical = false;
        doPrev();
    }

    private void doUp() {
        vertical = true;
        doNext();
    }

    private void doDown() {
        vertical = true;
        doPrev();
    }

    private void doPrev() {
        slideshowHandler.removeCallbacks(slideshowRunnable);
        slideshowHandler.removeCallbacks(slideshowBackRunnable);
        slideshowHandler.post(slideshowBackRunnable);
        paused = false;
    }

    private void doNext() {
        slideshowHandler.removeCallbacks(slideshowRunnable);
        slideshowHandler.removeCallbacks(slideshowBackRunnable);
        slideshowHandler.post(slideshowRunnable);
        paused = false;
    }

    private Rect calcPicRectInDisplay(PicInfo picture) {
        if (sw > 0 && sh > 0) {
            float imgRatio = (float) picture.width / (float) picture.height;
            if (shouldAlignToWidth(imgRatio)) {
                int ph = Math.round(sw / imgRatio);
                return new Rect(0, (sh - ph) / 2, sw, (sh - ph) / 2 + ph);
            } else {
                int pw = Math.round(sh * imgRatio);
                return new Rect((sw - pw) / 2, 0, (sw - pw) / 2 + pw, sh);
            }
        } else {
            return new Rect();
        }
    }

    private boolean shouldAlignToWidth(float imgRatio) {
        if (settingAllowOverscan) {
            return vertical; // when overscan, then it depends only on the scroll direction, because rest gets filled up from scroll.
        } else {
            return imgRatio > screenRatio; // this is the "normal" case, fitting into the bounds and possibly have remaining black borders.
        }
    }

    private void markOldBitmaps() {
        synchronized (oldBitmaps) {
            oldBitmaps.addAll(beforeBitmaps);
            if (currentPic != null) {
                oldBitmaps.add(currentPic.bitmap);
            }
            oldBitmaps.addAll(afterBitmaps);
        }
    }

    private void recycleUnneededBitmaps() {
        synchronized (oldBitmaps) {
            PicInfo currentPic = this.currentPic;
            for (Bitmap b : oldBitmaps) {
                if (b != null && !b.isRecycled() && !afterBitmaps.contains(b) && !beforeBitmaps.contains(b) && currentPic != null && currentPic.bitmap != b) {
                    synchronized (b) {
                        b.recycle();
                    }
                    System.out.println("recycl");
                }
            }
            oldBitmaps.clear();
        }
    }

    private void loadRequiredAfterBitmaps() {
        if (sw > 0 && sh > 0) {
            int nextPicture = currentPicture;
            int nextLeftOrTop = vertical ? currentPicPos.bottom + settingSpaceBetweenSlides : currentPicPos.right + settingSpaceBetweenSlides;
            afterBitmaps.clear();
            while (vertical ? nextLeftOrTop < 2 * sh : nextLeftOrTop < 2 * sw) {
                PicInfo nextPic;
                synchronized (pictures) {
                    nextPicture++;
                    if (pictures.size() <= 0) {
                        return;
                    }
                }
                nextPic = getPicInfo(nextPicture);
                synchronized (nextPic) {
                    if (vertical ? nextLeftOrTop < sh : nextLeftOrTop < sw) {
                        // in first "screen" which we will just display, load now
                        if (nextPic.bitmap == null || nextPic.bitmap.isRecycled()) {
                            System.out.println("next: #" + nextPicture + " nextLeftOrTop=" + nextLeftOrTop + " " + nextPic.picFile);
                            nextPic.loadBitmap(sw, sh);
                            if (nextPic.bitmap == null) {
                                System.err.println("Failednxt: #" + nextPicture + " nextLeftOrTop=" + nextLeftOrTop + " " + nextPic.picFile);
                                removePicture(nextPic);
                                nextPicture--;
                                continue;
                            }
                        }
                        afterBitmaps.add(nextPic.bitmap);
                    } else {
                        // in off-"screen" which we might display later, schedule delayed load
                        if (nextPic.bitmap != null && !nextPic.bitmap.isRecycled()) {
                            afterBitmaps.add(nextPic.bitmap);
                        } else {
                            schedulePreload(nextPic);
                        }
                    }
                }
                float imgRatio = (float) nextPic.width / (float) nextPic.height;
                if (vertical) {
                    if (shouldAlignToWidth(imgRatio)) {
                        nextLeftOrTop += Math.round(sw / imgRatio);
                    } else {
                        nextLeftOrTop += sh;
                    }
                } else {
                    if (shouldAlignToWidth(imgRatio)) {
                        nextLeftOrTop += sw;
                    } else {
                        nextLeftOrTop += Math.round(sh * imgRatio) - 1;
                    }
                }
                nextLeftOrTop += settingSpaceBetweenSlides;
            }
        }
    }

    private void loadRequiredBeforeBitmaps() {
        if (sw > 0 && sh > 0) {
            int nextPicture = currentPicture;
            int nextRightOrBottom = vertical ? currentPicPos.top - settingSpaceBetweenSlides : currentPicPos.left - settingSpaceBetweenSlides;
            beforeBitmaps.clear();
            while (vertical ? nextRightOrBottom > -sh : nextRightOrBottom > -sw) {
                PicInfo nextPic;
                synchronized (pictures) {
                    nextPicture--;
                    if (pictures.size() <= 0) {
                        return;
                    }
                    if (nextPicture < 0) {
                        nextPicture = pictures.size() - 1;
                    }
                    nextPic = getPicInfo(nextPicture);
                }
                synchronized (nextPic) {
                    if (nextRightOrBottom >= 0) {
                        // in first "screen" which we will just display, load now
                        if (nextPic.bitmap == null || nextPic.bitmap.isRecycled()) {
                            System.out.println("prev: #" + nextPicture + " nextRightOrBottom=" + nextRightOrBottom + " " + nextPic.picFile);
                            nextPic.loadBitmap(sw, sh);
                            if (nextPic.bitmap == null) {
                                System.err.println("Failedprv: #" + nextPicture + " nextRightOrBottom=" + nextRightOrBottom + " " + nextPic.picFile);
                                removePicture(nextPic);
                                continue;
                            }
                        }
                        beforeBitmaps.add(nextPic.bitmap);
                    } else {
                        // in off-"screen" which we might display later, schedule delayed load
                        if (nextPic.bitmap != null && !nextPic.bitmap.isRecycled()) {
                            beforeBitmaps.add(nextPic.bitmap);
                        } else {
                            schedulePreload(nextPic);
                        }
                    }
                }
                float imgRatio = (float) nextPic.width / (float) nextPic.height;
                if (vertical) {
                    if (shouldAlignToWidth(imgRatio)) {
                        nextRightOrBottom -= Math.round(sw / imgRatio) - 1;
                    } else {
                        nextRightOrBottom -= sh - 1;
                    }
                } else {
                    if (shouldAlignToWidth(imgRatio)) {
                        nextRightOrBottom -= sw - 1;
                    } else {
                        nextRightOrBottom -= Math.round(sh * imgRatio) - 1;
                    }
                }
                nextRightOrBottom -= settingSpaceBetweenSlides;
            }
        }
    }

    private void removePicture(PicInfo which) {
        synchronized (pictures) {
            int whichPos = pictures.indexOf(which);
            pictures.remove(which);
            if (currentPicture > whichPos) {
                currentPicture--;
            }
        }
    }

    private void schedulePreload(PicInfo pic) {
        Preloader preloader = new Preloader(pic);
        scheduledPreloaders.add(preloader);
        preloadHandler.post(preloader);
    }

    private void unschedulePreloaders() {
        for (Preloader preloader : scheduledPreloaders) {
            preloadHandler.removeCallbacks(preloader);
        }
        scheduledPreloaders.clear();
    }

    protected void onCreate() {
        if (Build.VERSION.SDK_INT >= 11) {
            hideSystemBarAndNavHoneycomb = new HideSystemBarAndNavHoneycomb();
            doActionBarMenuUIVisibility();
            // Use the InitialHideUIRunnable so that show() from onPrepareOptionsMenu
            // (see the comment there)
            // will not unschedule this hide task.
            uiHandler.postDelayed(new InitialHideUIRunnable(hideSystemBarAndNavHoneycomb), 500);
        }
        swipeDetector = new SwipeGestureFilter(context, swipeListener);

        final View contentView = findViewById(R.id.fullscreen_content);
        if(contentView != null) {
            contentView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (Build.VERSION.SDK_INT >= 11) {
                        toggleTouchInSystemBarHoneycomb();
                    }
                }
            });
        }

        canvasView = (SurfaceView) findViewById(R.id.canvasView);
        if (Build.VERSION.SDK_INT >= 17) {
            useRealSize();
        } else {
            final WindowManager windowManager = ((Activity) context).getWindowManager();    // on older API this can be nothing else but only Activity, because DreamServices requires API17
            final Display defaultDisplay = windowManager.getDefaultDisplay();
            sw = defaultDisplay.getWidth();
            sh = defaultDisplay.getHeight();
        }
        if (sh > 0) {
            screenRatio = (float) sw / (float) sh;
        }
        if(canvasView != null) {
            final SurfaceHolder holder = canvasView.getHolder();
            holder.addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    sw = width;
                    sh = height;
                    screenRatio = (float) sw / (float) sh;
                    PicInfo currentPic = Slideshow.this.currentPic;
                    if (slideshowRunning && slideshowHandler != null && currentPic != null) {
                        // update as immediate as possble
                        final Rect imgRect = calcPicRectInDisplay(currentPic);
                        oldPicPos = imgRect;
                        currentPicPos = imgRect;
                        Runnable uiTask = new UpdateScreenRunnable(new Rect(currentPicPos), currentPic.bitmap, new LinkedList<>(beforeBitmaps), new LinkedList<>(afterBitmaps));
                        uiTask.run();
                        // post to update, as the change may imply we have to load new before/afterBitmaps
                        slideshowHandler.postAtFrontOfQueue(slideshowJustRedrawRunnable);
                    } else {
                        clearScreenRunnable.run();
                    }
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                }
            });
        }

        final View scanningView = findViewById(R.id.scanning);
        scanningShow = new VisibilityRunnable(scanningView, View.VISIBLE);
        scanningHide = new VisibilityRunnable(scanningView, View.GONE);
        final View nopicsView = findViewById(R.id.no_pictures);
        nopicsShow = new VisibilityRunnable(nopicsView, View.VISIBLE);
        nopicsHide = new VisibilityRunnable(nopicsView, View.GONE);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPref.registerOnSharedPreferenceChangeListener(settingsListener);
        settingsListener.applyDelay(sharedPref);
        settingsListener.applySpaceBetweenSlides(sharedPref);
        settingAllowOverscan = sharedPref.getBoolean(SettingsActivity.PREF_ALLOW_OVERSCAN, false);
        settingRandom = sharedPref.getBoolean(SettingsActivity.PREF_RANDOM, false);
        settingRandomAgain = sharedPref.getBoolean(SettingsActivity.PREF_RANDOM_AGAIN, false);
        settingRecurse = sharedPref.getBoolean(SettingsActivity.PREF_RECURSE, true);
        settingSizeFilter = sharedPref.getString(SettingsActivity.PREF_SIZE_FILTER, Slideshow.PREF_SIZEFILTER_NONE);
        settingRememberCollection = sharedPref.getBoolean(SettingsActivity.PREF_REMEMBER_COLLECTION, false);
        if (settingRememberCollection) {
            path = new File(sharedPref.getString(Slideshow.PREF_DIRPATH, ""));
            mediaUri = Uri.parse(sharedPref.getString(Slideshow.PREF_MEDIA_URI, MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()));
            mediaSelection = sharedPref.getString(Slideshow.PREF_MEDIA_SELECTION, null);
        }
        settingIgnoreMediaStore = sharedPref.getBoolean(SettingsActivity.PREF_IGNORE_MEDIA_STORE, false);
        adjustOptionsMenu();
    }

    public void dispatchTouchEvent(MotionEvent me) {
        // Call onTouchEvent of SimpleGestureFilter class
        swipeDetector.onTouchEvent(me);
    }

    protected void onStartFromScratch() {
        vertical = deviceIsVertical = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

        HandlerThread bgThread = new HandlerThread("Slideshow Mover");
        bgThread.start();
        slideshowHandler = new Handler(bgThread.getLooper());
        bgThread = new HandlerThread("Preloader");
        bgThread.start();
        preloadHandler = new Handler(bgThread.getLooper());
        bgThread = new HandlerThread("File Finder");
        bgThread.start();
        fileHandler = new Handler(bgThread.getLooper());
        fileHandler.post(picFinderRunnable);
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if (!deviceIsVertical && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            vertical = deviceIsVertical = true;
        } else if (deviceIsVertical && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            vertical = deviceIsVertical = false;
        }
    }

    private PicInfo getPicInfo(int num) {
        int s = pictures.size();
        num %= s;
        return pictures.get(num);
    }

    private boolean acceptFileSize(PicInfo pic) {
        boolean accepted = acceptFileSizeInternalChecking(pic);
        if(!accepted) {
            refusedPics++;
        }
        return accepted;
    }

    private boolean acceptFileSizeInternalChecking(PicInfo pic) {
        final int longerside, shorterside;
        if (pic.width > pic.height) {
            longerside = pic.width;
            shorterside = pic.height;
        } else {
            longerside = pic.height;
            shorterside = pic.width;
        }
        switch (settingSizeFilter) {
            case Slideshow.PREF_SIZEFILTER_VIDEO4K:
                return longerside > 3840 && shorterside > 2160;
            case Slideshow.PREF_SIZEFILTER_HD1080P:
                return longerside > 1920 && shorterside > 1080;
            case Slideshow.PREF_SIZEFILTER_HD720P:
                return longerside > 1280 && shorterside > 720;
            case Slideshow.PREF_SIZEFILTER_NONE:
                return longerside > 0 && shorterside > 0;
        }
        final int longerScreenside, shorterScreenside;
        if (sw > sh) {
            longerScreenside = sw;
            shorterScreenside = sh;
        } else {
            longerScreenside = sh;
            shorterScreenside = sw;
        }
        switch (settingSizeFilter) {
            case Slideshow.PREF_SIZEFILTER_FITSCREEN:
                return longerside >= longerScreenside || shorterside >= shorterScreenside;
            case Slideshow.PREF_SIZEFILTER_ASLARGEASSCREEN:
                return longerside >= longerScreenside && shorterside >= shorterScreenside;
            case Slideshow.PREF_SIZEFILTER_WRONGASPECT:
                return longerside >= shorterScreenside;
            default:
                return longerside > 0 && shorterside > 0;
        }
    }

    private void insertNewPic(List<PicInfo> result, PicInfo newPicInfo) {
        if (acceptFileSize(newPicInfo)) {
            synchronized (result) {
                if (settingRandom) {
                    int insertPos = random.nextInt(result.size() + 1);
                    result.add(insertPos, newPicInfo);
                    if (insertPos <= currentPicture) {
                        currentPicture++;
                    }
                } else {
                    result.add(newPicInfo);
                }
            }
            if (!slideshowRunning) {
                slideshowHandler.post(slideshowStarterRunnable);
                slideshowRunning = true;
            }
        }
    }

    private void getFilesFromDir(File path, List<PicInfo> result) {
        File[] files = path.listFiles();
        if (files != null) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            if (!settingRandom) {
                Arrays.sort(files);
            }
            for (File file : files) {
                if (!scanRunning) {
                    return;
                }
                if (file.isDirectory() && !file.isHidden()) {
                    if (settingRecurse) {
                        getFilesFromDir(file, result);
                    }
                } else if (file.isFile() && !file.isHidden()) {
                    BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                    if (options.outMimeType != null) {
                        int orientation = BitmapUtils.getOrientationFromExif(file.getAbsolutePath());
                        PicInfo newPicInfo = new PicInfo(file, options.outWidth, options.outHeight, orientation);
                        insertNewPic(result, newPicInfo);
                    }
                }
            }
        }
    }

    private void getFilesFromMediaStore(Vector<PicInfo> result) {
        final String[] columnsV16 = { MediaStore.Images.Media.DATA, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT, MediaStore.Images.Media.ORIENTATION };
        final String[] columnsOld = { MediaStore.Images.Media.DATA };
        String[] columns = (Build.VERSION.SDK_INT >= 16) ? columnsV16 : columnsOld;
        String sort = settingRandom ? null : MediaStore.MediaColumns.DATA;  // is then compatible with sort()ing the PicInfos
        Cursor cursor = context.getContentResolver().query(mediaUri, columns, mediaSelection, null, sort);
        if(cursor != null) {
            if(cursor.moveToFirst()) {
                while(!cursor.isAfterLast()) {
                    String fileName = cursor.getString(0);
                    int width;
                    int height;
                    int orientation;
                    if(Build.VERSION.SDK_INT >= 16) {
                        width = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media.WIDTH));
                        height = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT));
                        orientation = cursor.getInt(cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION));
                        orientation = BitmapUtils.degreesToExif(orientation);
                    } else {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(fileName, options);
                        if (options.outMimeType != null) {
                            width = options.outWidth;
                            height = options.outHeight;
                        } else {
                            cursor.moveToNext();
                            continue;
                        }
                        orientation = BitmapUtils.getOrientationFromExif(fileName);
                    }
                    PicInfo newPic = new PicInfo(new File(fileName), width, height, orientation);
                    insertNewPic(result, newPic);
                    cursor.moveToNext();
                }
            }
            cursor.close();
        }
    }

    public void onPause() {
        paused = true;
        slideshowHandler.removeCallbacks(slideshowRunnable);
        slideshowHandler.removeCallbacks(slideshowBackRunnable);
        uiHandler.removeCallbacks(hideSystemBarAndNavHoneycomb);
        System.out.println("Paused");
    }

    public void onResume() {
        System.out.println("Resuming");
        paused = false;
        if (slideshowRunning) {
            slideshowHandler.post(slideshowJustRedrawRunnable);
            slideshowHandler.postDelayed(slideshowRunnable, settingNextSlideAfter);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        final Activity activity = (Activity) context;
        boolean changedPath = false;
        switch (item.getItemId()) {
            case R.id.usepicturesdir: {
                if (Build.VERSION.SDK_INT >= 8) {
                    path = getExternalPicturesDir();
                    changedPath = true;
                }
                break;
            }
            case R.id.useextstoragedir: {
                path = Environment.getExternalStorageDirectory();
                changedPath = true;
                break;
            }
            case R.id.userootdir: {
                path = Environment.getRootDirectory();
                changedPath = true;
                break;
            }
            case R.id.choosedir: {
                Intent intent = new Intent(activity, DirectorySelector.class);
                intent.putExtra(DirectorySelector.START_DIR, path.getAbsolutePath());
                intent.putExtra(DirectorySelector.SHOW_HIDDEN, false);
                intent.putExtra(DirectorySelector.ONLY_DIRS, true);
                intent.putExtra(DirectorySelector.ALLOW_UP, true);
                activity.startActivityForResult(intent, DirectorySelector.SELECT_DIRECTORY);
                break;
            }
            case R.id.allpictures: {
                mediaSelection = null;
                changedPath = true;
                break;
            }
            case R.id.choosemediastore: {
                Intent intent = new Intent(activity, MediaStoreSelector.class);
                intent.putExtra(MediaStoreSelector.START_URI, mediaUri);
                activity.startActivityForResult(intent, MediaStoreSelector.SELECT_MEDIA);
                break;
            }
            case R.id.settings:
                Intent intent = new Intent(activity, SettingsActivity.class);
                if(path != null) {
                    intent.putExtra(SettingsActivity.REMEMBER_COLLECTION_DESCRIPTION_DIR, path.getAbsolutePath());
                } else {
                    intent.putExtra(SettingsActivity.REMEMBER_COLLECTION_DESCRIPTION_DIR, context.getString(R.string.pref_description_remember_collection_unknown));
                }
                if(mediaSelection == null || mediaSelection.equals("")) {
                    intent.putExtra(SettingsActivity.REMEMBER_COLLECTION_DESCRIPTION_SELECTION, context.getString(R.string.menu_allpictures));
                } else {
                    String[] columns = { "distinct " + MediaStore.Images.Media.BUCKET_DISPLAY_NAME };
                    Cursor c = context.getContentResolver().query(mediaUri, columns, mediaSelection, null, null);
                    if(c != null && c.moveToNext()) {
                        intent.putExtra(SettingsActivity.REMEMBER_COLLECTION_DESCRIPTION_SELECTION, c.getString(c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)));
                        c.close();
                    } else {
                        intent.putExtra(SettingsActivity.REMEMBER_COLLECTION_DESCRIPTION_SELECTION, context.getString(R.string.pref_description_remember_collection_unknown));
                    }
                }
                activity.startActivity(intent);
                break;
        }
        if (changedPath) {
            fileHandler.post(slideshowEndRunnable);
            scanRunning = false;
            fileHandler.post(picFinderRunnable);
            saveRememberCollection();
        }
        return false; // to allow normal menu processing to proceed, true to consume it here
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch(requestCode) {
                case DirectorySelector.SELECT_DIRECTORY: {
                    Bundle extras = data.getExtras();
                    String fileName = extras.getString(DirectorySelector.RETURN_DIRECTORY);
                    if(fileName != null) {
                        path = new File(fileName);
                    }
                    break;
                }
                case MediaStoreSelector.SELECT_MEDIA: {
                    Bundle extras = data.getExtras();
                    mediaUri = (Uri) extras.get(MediaStoreSelector.RETURN_URI);
                    mediaSelection = extras.getString(MediaStoreSelector.RETURN_SELECTION);
                    break;
                }
                default:
                    return;
            }
            fileHandler.post(slideshowEndRunnable);
            scanRunning = false;
            fileHandler.post(picFinderRunnable);
            saveRememberCollection();
        }
    }

    private void adjustOptionsMenu() {
        if(menu != null) {
            menu.clear();
            MenuInflater inflater = ((Activity) context).getMenuInflater();
            inflater.inflate(R.menu.options, menu);
            if (settingIgnoreMediaStore) {
                menu.removeItem(R.id.allpictures);
                menu.removeItem(R.id.choosemediastore);
            } else {
                menu.removeItem(R.id.usepicturesdir);
                menu.removeItem(R.id.useextstoragedir);
                menu.removeItem(R.id.userootdir);
                menu.removeItem(R.id.choosedir);
            }
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        this.menu = menu;
        adjustOptionsMenu();
        return true;
    }

    public boolean onPrepareOptionsMenu(Menu menu) {
        if (Build.VERSION.SDK_INT >= 11) {
            // some Android versions(?) or environments(?) may call this actually just after the menu was created,
            // despite it is not going to be displayed
            showTouchInSystemBarHoneycomb();
        }
        if (settingIgnoreMediaStore) {
            MenuItem usepicturesdir = menu.findItem(R.id.usepicturesdir);
            MenuItem useextstoragedir = menu.findItem(R.id.useextstoragedir);
            MenuItem userootdir = menu.findItem(R.id.userootdir);
            if(path == null) {
                usepicturesdir.setChecked(false);
                useextstoragedir.setChecked(false);
                userootdir.setChecked(false);
            } else {
                usepicturesdir.setChecked(Build.VERSION.SDK_INT >= 8 && path.equals(getExternalPicturesDir()));
                useextstoragedir.setChecked(path.equals(Environment.getExternalStorageDirectory()));
                userootdir.setChecked(path.equals(Environment.getRootDirectory()));
            }
            if (!(Build.VERSION.SDK_INT >= 8)) {
                usepicturesdir.setEnabled(false);
            }
        } else {
            MenuItem allpictures = menu.findItem(R.id.allpictures);
            allpictures.setChecked(mediaSelection == null);
        }
        return true;
    }

    private void saveRememberCollection() {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        if (settingRememberCollection) {
            editor.putString(Slideshow.PREF_DIRPATH, path.getAbsolutePath());
            editor.putString(Slideshow.PREF_MEDIA_URI, mediaUri.toString());
            editor.putString(Slideshow.PREF_MEDIA_SELECTION, mediaSelection);
        } else {
            editor.remove(Slideshow.PREF_DIRPATH);
            editor.remove(Slideshow.PREF_MEDIA_URI);
            editor.remove(Slideshow.PREF_MEDIA_SELECTION);
        }
        editor.commit();
    }

    private void showCentralToast(String text) {
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        View textView = toast.getView().findViewById(android.R.id.message);
        if(textView instanceof TextView) {
            ((TextView) textView).setGravity(Gravity.CENTER);
        }
        toast.show();
    }

    private String getNameOfSizeFilter() {
        Resources res = context.getResources();
        String[] filterIds = res.getStringArray(R.array.pref_size_filter_list_values);
        String[] filterNames = res.getStringArray(R.array.pref_size_filter_list_titles);
        int filter = filterIds.length -1;
        while(filter >= 0) {
            if(settingSizeFilter.equals(filterIds[filter])) {
                break;
            }
            filter--;
        }
        String filterText;
        if(filter >= 0) {
            filterText = filterNames [filter];
        } else {
            filterText = context.getString(R.string.refused_filter_name_when_not_availabe);
        }
        return filterText;
    }

    public void onOptionsMenuClosed(Menu menu) {
        if (Build.VERSION.SDK_INT >= 11) {
            uiHandler.postDelayed(hideSystemBarAndNavHoneycomb, 500);
        }
    }

    @TargetApi(8)
    private File getExternalPicturesDir() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    }

    @TargetApi(11)
    private void doActionBarMenuUIVisibility() {
        if(context instanceof Activity) {
            ActionBar ab = ((Activity) context).getActionBar();
            if (ab != null) {
                ab.addOnMenuVisibilityListener(new ActionBar.OnMenuVisibilityListener() {
                    @Override
                    public void onMenuVisibilityChanged(boolean isVisible) {
                        if (isVisible) {
                            showTouchInSystemBarHoneycomb();
                        } else {
                            uiHandler.postDelayed(hideSystemBarAndNavHoneycomb, 500);
                        }
                    }
                });
            }
        }
    }

    @TargetApi(11)
    private void showTouchInSystemBarHoneycomb() {
        uiHandler.removeCallbacks(hideSystemBarAndNavHoneycomb);
        final View contentView = findViewById(R.id.fullscreen_content);
        if(contentView != null) {
            contentView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
        if(context instanceof Activity) {
            ActionBar ab = ((Activity) context).getActionBar();
            if (ab != null) {
                ab.show();
            }
        }
        hideSystemBarAndNavHoneycomb.isVisible = true;
    }

    @TargetApi(11)
    private void toggleTouchInSystemBarHoneycomb() {
        uiHandler.removeCallbacks(hideSystemBarAndNavHoneycomb);
        if (hideSystemBarAndNavHoneycomb.isVisible) {
            hideSystemBarAndNavHoneycomb.run();
        } else {
            showTouchInSystemBarHoneycomb();
            uiHandler.postDelayed(hideSystemBarAndNavHoneycomb, 4000);
        }
    }

    @TargetApi(17)
    private void useRealSize() {
        final Point realSize = new Point();
        final WindowManager windowManager;
        if(context instanceof Activity) {
            windowManager = ((Activity) context).getWindowManager();
        } else if(context instanceof DreamService) {
            windowManager = ((DreamService) context).getWindowManager();
        } else {
            System.err.println("useRealSize neither Activity nor DreamService");
            return;
        }
        windowManager.getDefaultDisplay().getRealSize(realSize);
        sw = realSize.x;
        sh = realSize.y;
    }

    private View findViewById(final int id) {
        if(context instanceof Activity) {
            return ((Activity) context).findViewById(id);
        } else {
            return findViewById17(id);
        }
    }

    @TargetApi(17)
    private View findViewById17(final int id) {
        if(context instanceof DreamService) {
            return ((DreamService) context).findViewById(id);
        } else {
            return null;
        }
    }
}
