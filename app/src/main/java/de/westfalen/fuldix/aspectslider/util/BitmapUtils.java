package de.westfalen.fuldix.aspectslider.util;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.IOException;

public class BitmapUtils {
    public static int getOrientationFromExif(String file) {
        try {
            ExifInterface exif = new ExifInterface(file);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
        } catch (IOException e) {
            System.err.println(e.getClass().getName() + " (" + e.getMessage() + ") for " + file);
            return ExifInterface.ORIENTATION_UNDEFINED;
        }
    }

    public static int degreesToExif(int degrees) {
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

    public static Bitmap rotateBitmapDegrees(Bitmap bitmap, int orientation) {
        if(orientation == 0) {
            return bitmap;
        }

        Matrix matrix = new Matrix();
        matrix.setRotate(orientation);
        Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        return bmRotated;
    }

    public static Bitmap rotateBitmapExif(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
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
        Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();
        return bmRotated;
    }
}
