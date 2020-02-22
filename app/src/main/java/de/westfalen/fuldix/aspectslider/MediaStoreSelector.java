package de.westfalen.fuldix.aspectslider;

import android.Manifest;
import android.app.Activity;
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
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executor = new ThreadPoolExecutor(0, Runtime.getRuntime().availableProcessors(), 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
        uiHandler = new Handler();
        final String[] requiredPermissions = new String[] { Manifest.permission.READ_EXTERNAL_STORAGE };
        if(PermissionUtils.checkOrRequestPermissions(this, requiredPermissions)) {
            setupUI();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final String permissions[], final int[] grantResults) {
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
        final GridView av = (GridView) findViewById(R.id.grid);
        final GalleryAdapter galleryAdapter = new GalleryAdapter(this);
        av.setAdapter(galleryAdapter);
        final Resources res = getResources();
        final int cellWidth = res.getDimensionPixelSize(R.dimen.gallery_column_width_intended);
        av.setNumColumns(realSize.x/cellWidth);
        av.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                returnSelection(id);
            }
        });

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
                        final Resources res = context.getResources();
                        final int iconSize = res.getDimensionPixelSize(R.dimen.gallery_icon_size);
                        final int iconKind = iconSize <= 96 ? MediaStore.Images.Thumbnails.MICRO_KIND : MediaStore.Images.Thumbnails.MINI_KIND;

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
                                final long[] thmbs;
                                final int[] thmbOrientations;
                                switch (numPics) {
                                    case 1:
                                        thmbs = new long[]{ids.get(0)};
                                        thmbOrientations = new int[]{orientations.get(0)};
                                        break;
                                    case 2:
                                        thmbs = new long[]{ids.get(0), ids.get(1)};
                                        thmbOrientations = new int[]{orientations.get(0), orientations.get(1)};
                                        break;
                                    default:
                                        thmbs = new long[]{ids.get(0), ids.get((numPics + 1) / 2), ids.get(numPics - 1)};
                                        thmbOrientations = new int[]{orientations.get(0), orientations.get((numPics + 1) / 2), orientations.get(numPics - 1)};
                                        break;
                                }
                                final Bitmap[] bitmaps = new Bitmap[thmbs.length];
                                final Gallery gallery = new Gallery(bucketId, bucketName, numPics, bitmaps);
                                for (int t = 0; t < thmbs.length; t++) {
                                    final long imageId = thmbs[t];
                                    final int orientation = thmbOrientations[t];
                                    if (Build.VERSION.SDK_INT >= 29) {
                                        try {
                                            bitmaps[t] = BitmapUtils.rotateBitmapDegrees(context.getContentResolver().loadThumbnail(ContentUris.withAppendedId(mediaUri, imageId), new Size(iconSize, iconSize), null), orientation);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    } else if (Build.VERSION.SDK_INT >= 8) {
                                        bitmaps[t] = ThumbnailUtils.extractThumbnail(BitmapUtils.rotateBitmapDegrees(MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), imageId, iconKind, null), orientation), iconSize, iconSize, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
                                    } else { // old versions use micro bitmap without scaling
                                        bitmaps[t] = BitmapUtils.rotateBitmapDegrees(MediaStore.Images.Thumbnails.getThumbnail(context.getContentResolver(), imageId, iconKind, null), orientation);
                                    }
                                }
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
    }

    private static class Gallery {
        public final long id;
        public final String name;
        public final int numPics;
        public final Bitmap[] bitmaps;
        public Gallery(final long id, final String name, final int numPics, final Bitmap[] bitmaps) {
            this.id = id;
            this.name = name;
            this.numPics = numPics;
            this.bitmaps = bitmaps;
        }
    }

    private static class GalleryAdapter extends BaseAdapter {
        private static final int[] iconItems = { R.id.galleryicon1, R.id.galleryicon2, R.id.galleryicon3 };

        private final List<Gallery> galleries = new ArrayList<Gallery>();
        private final LayoutInflater inflater;

        public GalleryAdapter(final Context context) {
            inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if(convertView == null) {
                convertView = inflater.inflate(R.layout.medialist_item, null);
            }
            final TextView nameView = convertView.findViewById(R.id.galleryname);
            final TextView infoView = convertView.findViewById(R.id.galleryinfo);
            final ImageView[] imageViews = new ImageView[iconItems.length];
            for(int i=0; i<iconItems.length; i++) {
                imageViews[i] = (ImageView) convertView.findViewById(iconItems[i]);
            }
            final Gallery gallery = galleries.get(position);
            nameView.setText(gallery.name);
            final int numPics = gallery.numPics;
            infoView.setText(convertView.getResources().getQuantityString(R.plurals.select_gallery_num_pictures, numPics, numPics));
            for(int v=0; v<imageViews.length; v++) {
                final ImageView imageView = imageViews[v];
                final Bitmap bitmap = v < gallery.bitmaps.length ? gallery.bitmaps[v] : null;
                imageView.setImageBitmap(bitmap);
                imageView.setVisibility(bitmap != null ? View.VISIBLE : View.INVISIBLE);
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

    }
}
