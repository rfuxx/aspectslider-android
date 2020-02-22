package de.westfalen.fuldix.aspectslider.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Build;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;

import de.westfalen.fuldix.aspectslider.ContentResolverAndUri;

public class BitmapUtils {
    public static int getOrientationFromExif(final Object pic) {
        try {
            final ExifInterface exif;
            if(Build.VERSION.SDK_INT >= 24 && pic instanceof ContentResolverAndUri) {
                final ContentResolverAndUri cu = (ContentResolverAndUri) pic;
                try {
                    final InputStream inputStream = cu.contentResolver.openInputStream(cu.uri);
                    if(inputStream != null) {
                        exif = new ExifInterface(inputStream);
                    } else {
                        return ExifInterface.ORIENTATION_UNDEFINED;
                    }
                } catch (final IOException e) {
                    e.printStackTrace();
                    return ExifInterface.ORIENTATION_UNDEFINED;
                }
            } else if(Build.VERSION.SDK_INT >= 24 && pic instanceof FileDescriptor) {
                exif = new ExifInterface((FileDescriptor) pic);
            } else if(pic instanceof String) {
                exif = new ExifInterface((String) pic);
            } else if(Build.VERSION.SDK_INT >= 29 && pic instanceof File) {
                exif = new ExifInterface((File) pic);
            } else if(Build.VERSION.SDK_INT >= 24 && pic instanceof InputStream) {
                exif = new ExifInterface((InputStream) pic);
            } else {
               return ExifInterface.ORIENTATION_UNDEFINED;
            }
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        } catch (final IOException e) {
            System.err.println(e.getClass().getName() + " (" + e.getMessage() + ") for " + pic);
            return ExifInterface.ORIENTATION_UNDEFINED;
        }
    }

    public static int degreesToExif(final int degrees) {
        switch(degrees) {
            case 0:
            case 360:
                return ExifInterface.ORIENTATION_NORMAL;
            case 90:
            case -270:
                return ExifInterface.ORIENTATION_ROTATE_90;
            case 180:
            case -180:
                return ExifInterface.ORIENTATION_ROTATE_180;
            case -90:
            case 270:
                return ExifInterface.ORIENTATION_ROTATE_270;
            default:
                return ExifInterface.ORIENTATION_UNDEFINED;

        }
    }

    public static Bitmap rotateBitmapDegrees(final Bitmap bitmap, final int orientation) {
        if(orientation == 0) {
            return bitmap;
        }

        final Matrix matrix = new Matrix();
        matrix.setRotate(orientation);
        final Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        return bmRotated;
    }

    public static Bitmap rotateBitmapExif(final Bitmap bitmap, final int orientation) {
        final Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_NORMAL:
                return bitmap;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        final Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        return bmRotated;
    }
}
