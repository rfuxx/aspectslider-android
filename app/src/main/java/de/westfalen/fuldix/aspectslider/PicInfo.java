package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.Build;

import java.io.File;

import de.westfalen.fuldix.aspectslider.util.BitmapUtils;

class PicInfo implements Comparable {
    final File picFile;
    private final int fileWidth;
    private final int fileHeight;
    final int width;
    final int height;
    private final int orientation;
    Bitmap bitmap;

    // we want the constructor to give the width/height as the file has, and the orientation.
    // it is our responsibility here to switch width/height if the orietation demands,
    // because the information are used for layout.
    PicInfo(final File picFile, final int width, final int height, final int orientation) {
        this.picFile = picFile;
        this.orientation = orientation;
        this.fileWidth = width;
        this.fileHeight = height;
        switch (orientation) {
            case ExifInterface.ORIENTATION_TRANSPOSE:
            case ExifInterface.ORIENTATION_ROTATE_90:
            case ExifInterface.ORIENTATION_TRANSVERSE:
            case ExifInterface.ORIENTATION_ROTATE_270:
                //noinspection SuspiciousNameCombination,SuspiciousNameCombination,SuspiciousNameCombination,SuspiciousNameCombination,SuspiciousNameCombination
                this.width = fileHeight;
                //noinspection SuspiciousNameCombination
                this.height = fileWidth;
                break;
            default:
                this.width = fileWidth;
                this.height = fileHeight;
        }
    }

    @Override
    public int compareTo(final Object another) {
        if(another instanceof PicInfo) {
            return picFile.compareTo(((PicInfo) another).picFile);
        } else {
            return 1;
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
        bitmap = BitmapUtils.rotateBitmapExif(BitmapFactory.decodeFile(picFile.getAbsolutePath(), o), orientation);
    }

    @TargetApi(10)
    private static void setPreferQuality(final BitmapFactory.Options o) {
        o.inPreferQualityOverSpeed = true;
    }
}
