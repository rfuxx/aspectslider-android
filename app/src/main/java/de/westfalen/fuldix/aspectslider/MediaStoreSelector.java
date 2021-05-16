package de.westfalen.fuldix.aspectslider;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import de.westfalen.fuldix.aspectslider.util.BitmapUtils;
import de.westfalen.fuldix.aspectslider.util.PermissionUtils;

public class MediaStoreSelector extends Activity {
    public static final String START_URI = "startUri";
    public static final String RETURN_URI = "returnUri";
    public static final String RETURN_SELECTION = "returnBucketId";
    public static final int SELECT_MEDIA = 12;

    private ThreadPoolExecutor executor;
    private Handler uiHandler;
    private Uri mediaUri;
    private GalleryAdapter galleryAdapter;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = new ThreadPoolExecutor(0, Runtime.getRuntime().availableProcessors(), 1, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        uiHandler = new Handler();
        final String[] requiredPermissions = new String[] { Manifest.permission.READ_EXTERNAL_STORAGE };
        if(PermissionUtils.checkOrRequestPermissions(this, requiredPermissions)) {
            setupUI();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
        if(PermissionUtils.getMissingPermissions(permissions, grantResults).isEmpty()) {
            setupUI();
        } else {
            PermissionUtils.toastDenied(this);
            finish();
        }
    }

    public void setupUI() {
        setTitle(R.string.select_gallery);

        final Bundle extras = getIntent().getExtras();
        if(extras == null) {
            setContentView(R.layout.activity_mediaselect_no_media);
            return;
        }

        mediaUri = (Uri) extras.get(START_URI);

        final Point realSize = new Point();
        if (Build.VERSION.SDK_INT >= 17) {
            getWindowManager().getDefaultDisplay().getRealSize(realSize);
        } else {
            realSize.x = getWindowManager().getDefaultDisplay().getWidth();
            realSize.y = getWindowManager().getDefaultDisplay().getHeight();
        }


        setContentView(R.layout.activity_mediaselect);
        final GridView av = findViewById(R.id.grid);
        galleryAdapter = new GalleryAdapter(this, mediaUri, executor);
        av.setAdapter(galleryAdapter);
        final Resources res = getResources();
        final int cellWidth = res.getDimensionPixelSize(R.dimen.gallery_column_width_intended);
        av.setNumColumns(realSize.x/cellWidth);
        av.setOnItemClickListener((parent, view, position, id) -> returnSelection(id));

        executor.execute(new MediaStoreGalleryQueryRunnable(this, mediaUri, galleryAdapter, uiHandler));
    }

    private class NoPicturesRunnable implements Runnable {
        @Override
        public void run() {
            setContentView(R.layout.activity_mediaselect_no_media);
        }
    }

    private static class MediaStoreGalleryQueryRunnable implements Runnable {
        private final MediaStoreSelector context;
        private final Uri mediaUri;
        private final GalleryAdapter galleryAdapter;
        private final Handler uiHandler;

        public MediaStoreGalleryQueryRunnable(final MediaStoreSelector context, final Uri mediaUri, final GalleryAdapter galleryAdapter, final Handler uiHandler) {
            this.context = context;
            this.mediaUri = mediaUri;
            this.galleryAdapter = galleryAdapter;
            this.uiHandler = uiHandler;
        }

        @Override
        public void run() {
            final String[] galleryQueryColumns = {MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.Images.Media._ID, MediaStore.Images.Media.ORIENTATION};
            final Cursor cursor = context.getContentResolver().query(mediaUri, galleryQueryColumns, null, null, MediaStore.Images.Media.BUCKET_DISPLAY_NAME + "," + MediaStore.Images.Media.DISPLAY_NAME + "," + MediaStore.Images.Media._ID);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        long bucketId = cursor.getLong(0);
                        String bucketName = cursor.getString(1);
                        final List<Long> ids = new ArrayList<>();
                        final List<Integer> orientations = new ArrayList<>();
                        do {
                            long currentBucketId = cursor.getLong(0);
                            if (currentBucketId == bucketId) {
                                ids.add(cursor.getLong(2));
                                orientations.add(cursor.getInt(3));
                            }
                            if (currentBucketId != bucketId || cursor.isLast()) {
                                final int numPics = ids.size();
                                final long[] thumbs;
                                final int[] thumbOrientations;
                                switch (numPics) {
                                    case 1:
                                        thumbs = new long[]{ids.get(0)};
                                        thumbOrientations = new int[]{orientations.get(0)};
                                        break;
                                    case 2:
                                        thumbs = new long[]{ids.get(0), ids.get(1)};
                                        thumbOrientations = new int[]{orientations.get(0), orientations.get(1)};
                                        break;
                                    default:
                                        thumbs = new long[]{ids.get(0), ids.get(numPics / 2), ids.get(numPics - 1)};
                                        thumbOrientations = new int[]{orientations.get(0), orientations.get(numPics / 2), orientations.get(numPics - 1)};
                                        break;
                                }
                                if(Thread.interrupted()) {
                                    return;
                                }
                                final Gallery gallery = new Gallery(bucketId, bucketName, numPics, thumbs, thumbOrientations);
                                uiHandler.post(galleryAdapter.new AddGalleryRunnable(gallery));

                                if (!cursor.isLast()) {
                                    bucketId = currentBucketId;
                                    bucketName = cursor.getString(1);
                                    ids.clear();
                                    orientations.clear();
                                    ids.add(cursor.getLong(2));
                                    orientations.add(cursor.getInt(3));
                                }
                            }
                            cursor.moveToNext();
                        } while (!cursor.isAfterLast());
                        return; // galleries were found and handed over to the galleryAdapter - return successfully
                    }
                } finally {
                    cursor.close();
                }
                uiHandler.post(context.new NoPicturesRunnable());
            }
        }
    }

    private void returnSelection(final long bucketId) {
        final Intent result = new Intent();
        final String selection = MediaStore.Images.Media.BUCKET_ID + "=" + bucketId;
        result.putExtra(RETURN_SELECTION, selection);
        result.putExtra(RETURN_URI, mediaUri);
        setResult(RESULT_OK, result);
    	finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        if(galleryAdapter != null) {
            galleryAdapter.cleanup();
        }
    }

    private static class Gallery {
        public final long id;
        public final String name;
        public final int numPics;
        public final long[] thumbnailIds;
        public final int[] thumbnailOrientations;
        public Gallery(final long id, final String name, final int numPics, final long[] thumbnailIds, final int[] thumbnailOrientations) {
            this.id = id;
            this.name = name;
            this.numPics = numPics;
            this.thumbnailIds = thumbnailIds;
            this.thumbnailOrientations = thumbnailOrientations;
        }
    }

    private static class GalleryAdapter extends BaseAdapter {
        private static final int[] iconItems = { R.id.galleryicon1, R.id.galleryicon2, R.id.galleryicon3 };

        private final List<Gallery> galleries = new ArrayList<>();
        private final Map<Long, Bitmap> bitmaps = new HashMap<>();
        private final int iconSize;
        private final Size iconSizeSize;
        private final int iconKind;
        private final LayoutInflater inflater;
        private final Uri mediaUri;
        private final Executor executor;

        public GalleryAdapter(final Context context, final Uri mediaUri, final Executor executor) {
            final Resources res = context.getResources();
            iconSize = res.getDimensionPixelSize(R.dimen.gallery_icon_size);
            iconSizeSize = Build.VERSION.SDK_INT >= 21 ? new Size(iconSize, iconSize) : null;
            iconKind = iconSize <= 96 ? MediaStore.Images.Thumbnails.MICRO_KIND : MediaStore.Images.Thumbnails.MINI_KIND;
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            this.mediaUri = mediaUri;
            this.executor = executor;
        }

        @Override
        public int getCount() {
            return galleries.size();
        }

        @Override
        public Object getItem(int position) {
            return galleries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return galleries.get(position).id;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if(convertView == null) {
                convertView = inflater.inflate(R.layout.medialist_item, parent, false);
            }
            final TextView nameView = convertView.findViewById(R.id.galleryname);
            final TextView infoView = convertView.findViewById(R.id.galleryinfo);
            final ImageView[] imageViews = new ImageView[iconItems.length];
            for(int i=0; i<iconItems.length; i++) {
                imageViews[i] = convertView.findViewById(iconItems[i]);
            }
            final Gallery gallery = galleries.get(position);
            nameView.setText(gallery.name);
            final int numPics = gallery.numPics;
            infoView.setText(convertView.getResources().getQuantityString(R.plurals.select_gallery_num_pictures, numPics, numPics));
            int v=0;
            for(; v<gallery.thumbnailIds.length; v++) {
                final long thumbnailId = gallery.thumbnailIds[v];
                final ImageView imageView = imageViews[v];
                synchronized(bitmaps) {
                    imageView.setTag(thumbnailId);
                    final Bitmap bitmap = bitmaps.get(thumbnailId);
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                        imageView.setVisibility(View.VISIBLE);
                    } else if (!bitmaps.containsKey(thumbnailId)) { // null in the map marks that loading is already in progress, no second loaded should be started
                        bitmaps.put(thumbnailId, null);
                        imageView.setVisibility(View.INVISIBLE);
                        executor.execute(new ThumbnailLoader(imageView, thumbnailId, gallery.thumbnailOrientations[v]));
                    }
                }
            }
            for(; v<imageViews.length; v++) {
                final ImageView imageView = imageViews[v];
                imageView.setVisibility(View.INVISIBLE);
            }
            return convertView;
        }

        public class AddGalleryRunnable implements Runnable {
            private final Gallery gallery;
            public AddGalleryRunnable(final Gallery gallery) {
                this.gallery = gallery;
            }

            @Override
            public void run() {
                galleries.add(gallery);
                notifyDataSetChanged();
            }
        }

        void cleanup() {
            galleries.clear();
            synchronized(bitmaps) {
                for (final Bitmap bitmap : bitmaps.values()) {
                    if (bitmap != null) {
                        bitmap.recycle();
                    }
                }
                bitmaps.clear();
            }
        }

        public class ThumbnailLoader implements Runnable {
            private final ImageView imageView;
            private final long imageId;
            private final int orientation;

            public ThumbnailLoader(final ImageView imageView, final long imageId, final int orientation) {
                this.imageView = imageView;
                this.imageId = imageId;
                this.orientation = orientation;
            }

            @Override
            public void run() {
                final ContentResolver contentResolver = imageView.getContext().getContentResolver();
                Bitmap bitmap;
                if (Build.VERSION.SDK_INT >= 29) {
                    try {
                        bitmap = contentResolver.loadThumbnail(ContentUris.withAppendedId(mediaUri, imageId), iconSizeSize, null); // rotation according to orientation is handled automatically by this API29 function
                    } catch (IOException e) {
                        e.printStackTrace();
                        bitmap = null;
                    }
                } else if (Build.VERSION.SDK_INT >= 8) {
                    bitmap = ThumbnailUtils.extractThumbnail(BitmapUtils.rotateBitmapDegrees(MediaStore.Images.Thumbnails.getThumbnail(contentResolver, imageId, iconKind, null), orientation), iconSize, iconSize, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
                } else { // old versions use micro bitmap without scaling
                    bitmap = BitmapUtils.rotateBitmapDegrees(MediaStore.Images.Thumbnails.getThumbnail(contentResolver, imageId, iconKind, null), orientation);
                }
                if(bitmap != null) {
                    synchronized(bitmaps) {
                        if (bitmaps.containsKey(imageId) && bitmaps.get(imageId) == null) {
                            bitmaps.put(imageId, bitmap); // this was a legit thumbnail read
                            imageView.post(new LazyLoadThumbnailUpdateRunnable(imageView, imageId, bitmap));
                        } else {
                            bitmap.recycle(); // duplicate read or maybe the read was canceled. hence recycle the bitmap
                        }
                    }
                }
            }
        }
    }

    public static class LazyLoadThumbnailUpdateRunnable implements Runnable {
        private final ImageView imageView;
        private final long imageId;
        private final Bitmap bitmap;

        public LazyLoadThumbnailUpdateRunnable(final ImageView imageView, final long imageId, final Bitmap bitmap) {
            this.imageView = imageView;
            this.imageId = imageId;
            this.bitmap = bitmap;
        }

        public void run() {
            final Object tag = imageView.getTag();
            if(tag instanceof Long && (Long) tag == imageId) { // update only if the imageview has not been reused for some other element already
                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(View.VISIBLE);
                imageView.invalidate();
            }
        }
    }
}
