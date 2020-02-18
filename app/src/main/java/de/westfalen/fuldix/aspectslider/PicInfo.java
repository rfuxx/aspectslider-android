package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.Build;

import de.westfalen.fuldix.aspectslider.util.BitmapUtils;

class PicInfo implements Comparable {
    final String picSource;
    private int fileWidth;
    private int fileHeight;
    private Integer fileOrientation;
    private int width;
    private int height;
    Bitmap bitmap;

    PicInfo(final String picSource) {
        this(picSource, 0, 0, null);
    }

    PicInfo(final String picSource, final int width, final int height, final Integer orientation) {
        this.picSource = picSource;
        this.fileOrientation = orientation;
        this.fileWidth = width;
        this.fileHeight = height;
        evaluateOrientation();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public int compareTo(final Object another) {
        if(another instanceof PicInfo) {
            return picSource.compareTo(((PicInfo) another).picSource);
        } else {
            return 1;
        }
    }

    private void evaluateOrientation() {
        if(fileOrientation != null) {
            switch (fileOrientation) {
                case ExifInterface.ORIENTATION_TRANSPOSE:
                case ExifInterface.ORIENTATION_ROTATE_90:
                case ExifInterface.ORIENTATION_TRANSVERSE:
                case ExifInterface.ORIENTATION_ROTATE_270:
                    //noinspection SuspiciousNameCombination,SuspiciousNameCombination,SuspiciousNameCombination,SuspiciousNameCombination,SuspiciousNameCombination
                    width = fileHeight;
                    //noinspection SuspiciousNameCombination
                    height = fileWidth;
                    break;
                default:
                    width = fileWidth;
                    height = fileHeight;
            }
        }
    }

    public boolean isChecked() {
        return fileOrientation != null;
    }

    public boolean checkPicture() {
        if(fileOrientation != null) {
            return true;
        }
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(picSource, options);
        if (options.outMimeType != null) {
            fileOrientation = BitmapUtils.getOrientationFromExif(picSource);
            fileWidth = options.outWidth;
            fileHeight = options.outHeight;
            evaluateOrientation();
            return true;
        } else {
            return false;
        }
    }

    public void loadBitmap(final int sw, final int sh) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        if (Build.VERSION.SDK_INT >= 10) {
            setPreferQuality(o);
        }
        // we have to use the file measures because others may have been rotated for layout
        final int size = (sw > sh) ? sw : sh;
        if (fileWidth < size && fileHeight < size) {
            o.inSampleSize = 1;
        } else if (fileWidth < fileHeight) {
            // to crop for "overscan" worst case is to put the shorter side of the image at the longer side of the screen
            o.inSampleSize = fileWidth / size;
        } else {
            o.inSampleSize = fileHeight / size;
        }
        bitmap = BitmapUtils.rotateBitmapExif(BitmapFactory.decodeFile(picSource, o), fileOrientation);
    }

    @TargetApi(10)
    private static void setPreferQuality(final BitmapFactory.Options o) {
        o.inPreferQualityOverSpeed = true;
    }
}
