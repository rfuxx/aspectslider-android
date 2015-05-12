package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import de.westfalen.fuldix.aspectslider.util.BitmapUtils;

public class MediaStoreSelector extends Activity {
    public static final String START_URI = "startUri";
    public static final String RETURN_URI = "returnUri";
    public static final String RETURN_SELECTION = "returnBucketId";
    public static final int SELECT_MEDIA = 12;

    private Uri mediaUri;
    private Cursor cursor;
    private final Map<Long, Bitmap> bitmaps = new HashMap<>();
    private int iconSize;
    private int iconKind;

    private class MediaCursorAdapter extends CursorAdapter
    {
        MediaCursorAdapter() {
            super(MediaStoreSelector.this, cursor, false);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            return inflater.inflate(R.layout.medialist_item, null);
        }

        @Override
        public void bindView(final View view, Context context, Cursor cursor) {
            TextView nameView = (TextView) view.findViewById(R.id.galleryname);
            TextView infoView = (TextView) view.findViewById(R.id.galleryinfo);
            final int[] iconItems = { R.id.galleryicon1, R.id.galleryicon2, R.id.galleryicon3 };
            final ImageView[] imageViews = new ImageView[iconItems.length];
            for(int i=0; i<iconItems.length; i++) {
                imageViews[i] = (ImageView) view.findViewById(iconItems[i]);
            }

            final String[] infoColumns = { "count(" + MediaStore.Images.Media._ID + ")", MediaStore.Images.Media.BUCKET_DISPLAY_NAME };
            final String[] selectArgs = { cursor.getString(0) };
            Cursor buckCursor = getContentResolver().query(mediaUri, infoColumns, MediaStore.Images.Media.BUCKET_ID+"=?", selectArgs, null);
            if(buckCursor != null && buckCursor.moveToFirst()) {
                nameView.setText(buckCursor.getString(buckCursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)));
                int numPics = buckCursor.getInt(0);
                infoView.setText(getResources().getQuantityString(R.plurals.select_gallery_num_pictures, numPics, numPics));
                final String[] thmColumns = { MediaStore.Images.Media._ID, MediaStore.Images.Media.ORIENTATION };
                Cursor thmCursor = getContentResolver().query(mediaUri, thmColumns, MediaStore.Images.Media.BUCKET_ID + "=?", selectArgs, MediaStore.Images.Media.DISPLAY_NAME + " limit 3");
                if (thmCursor != null && thmCursor.moveToFirst()) {
                    while (!thmCursor.isAfterLast()) {
                        final long imageId = thmCursor.getInt(thmCursor.getColumnIndex(MediaStore.Images.Media._ID));
                        final int orientation = thmCursor.getInt(thmCursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION));
                        final ImageView imageView = imageViews[thmCursor.getPosition()];
                        synchronized (bitmaps) {
                            imageView.setTag(imageId);
                            if (bitmaps.containsKey(imageId)) {
                                imageView.setImageBitmap(bitmaps.get(imageId));
                                imageView.setVisibility(View.VISIBLE);
                            } else {
                                imageView.setVisibility(View.INVISIBLE);
                                bitmaps.put(imageId, null);
                                AsyncTask<ImageView, Void, Bitmap> loader = new AsyncTask<ImageView, Void, Bitmap>() {
                                    @Override
                                    protected Bitmap doInBackground(ImageView... params) {
                                        final Bitmap bitmap;
                                        if (Build.VERSION.SDK_INT >= 8) {
                                            bitmap = scaleDownBitmap(BitmapUtils.rotateBitmapDegrees(MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), imageId, iconKind, null), orientation));
                                        } else { // old versions use micro bitmap without scaling
                                            bitmap = BitmapUtils.rotateBitmapDegrees(MediaStore.Images.Thumbnails.getThumbnail(getContentResolver(), imageId, iconKind, null), orientation);
                                        }
                                        synchronized (bitmaps) {
                                            bitmaps.put(imageId, bitmap);
                                        }
                                        return bitmap;
                                    }
                                    protected void onPostExecute(Bitmap result) {
                                        Object tag = imageView.getTag();
                                        if(tag instanceof Long && (Long) tag == imageId) {
                                            imageView.setImageBitmap(result);
                                            imageView.setVisibility(View.VISIBLE);
                                        } else {
                                            MediaCursorAdapter.this.notifyDataSetChanged();
                                        }
                                    }
                                };
                                loader.execute(imageView);
                            }
                        }
                        thmCursor.moveToNext();
                    }
                    for(int i=thmCursor.getCount(); i<iconItems.length; i++) {
                        imageViews[i].setTag(null);
                        imageViews[i].setVisibility(View.INVISIBLE);
                    }
                    thmCursor.close();
                } else {
                    for(int i=0; i<iconItems.length; i++) {
                        imageViews[i].setTag(null);
                        imageViews[i].setVisibility(View.INVISIBLE);
                    }
                }
                buckCursor.close();
            } else {
                for(int i=0; i<iconItems.length; i++) {
                    imageViews[i].setTag(null);
                    imageViews[i].setVisibility(View.INVISIBLE);
                }
            }
        }
    }

	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        mediaUri = (Uri) extras.get(START_URI);

        Point realSize = new Point();
        if (Build.VERSION.SDK_INT >= 17) {
            getRealSize(realSize);
        } else {
            realSize.x = getWindowManager().getDefaultDisplay().getWidth();
            realSize.y = getWindowManager().getDefaultDisplay().getHeight();
        }

        setTitle(R.string.select_gallery);

        final String[] columns = { "distinct " + MediaStore.Images.Media.BUCKET_ID + " as _id" };
        cursor = getContentResolver().query(mediaUri, columns, null, null, MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
        if(cursor == null || !cursor.moveToFirst()) {
            setContentView(R.layout.activity_mediaselect_no_media);
            return;
        }
        setContentView(R.layout.activity_mediaselect);
        GridView av = (GridView) findViewById(R.id.grid);
        av.setAdapter(new MediaCursorAdapter());
        Resources res = getResources();
        iconSize = res.getDimensionPixelSize(R.dimen.gallery_icon_size);
        iconKind = iconSize <= 96 ? MediaStore.Images.Thumbnails.MICRO_KIND : MediaStore.Images.Thumbnails.MINI_KIND;
        int cellWidth = res.getDimensionPixelSize(R.dimen.gallery_column_width_intended);
        av.setNumColumns(realSize.x/cellWidth);
        av.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                returnSelection(id);
            }
        });
    }

    private void returnSelection(long bucketId) {
    	Intent result = new Intent();
        String selection = MediaStore.Images.Media.BUCKET_ID + "=" + bucketId;
        result.putExtra(RETURN_SELECTION, selection);
        result.putExtra(RETURN_URI, mediaUri);
        setResult(RESULT_OK, result);
    	finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synchronized (bitmaps) {
            for(Bitmap b : bitmaps.values()) {
                if(b != null) {
                    b.recycle();
                }
            }
            bitmaps.clear();
        }
    }

    @TargetApi(17)
    private void getRealSize(Point realSize) {
        getWindowManager().getDefaultDisplay().getRealSize(realSize);
    }

    @TargetApi(8)
    private Bitmap scaleDownBitmap(Bitmap bitmap) {
        return ThumbnailUtils.extractThumbnail(bitmap, iconSize, iconSize, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
    }
}
