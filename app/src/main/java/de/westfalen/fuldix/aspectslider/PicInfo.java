package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.media.ExifInterface;
import android.os.Build;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import de.westfalen.fuldix.aspectslider.util.BitmapUtils;

class PicInfo implements Comparable<PicInfo> {
    final Object picSource;
    private int fileWidth;
    private int fileHeight;
    private Integer fileOrientation;
    private int width;
    private int height;
    Bitmap bitmap;

    PicInfo(final Object picSource) {
        this(picSource, 0, 0, null);
    }

    PicInfo(final Object picSource, final int width, final int height, final Integer orientation) {
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
    public int compareTo(final PicInfo another) {
        return getComparableString().compareTo(another.getComparableString());
    }

    private String getComparableString() {
        if(picSource instanceof ContentResolverAndUri) {
            return ((ContentResolverAndUri) picSource).uri.toString();
        } else if(picSource != null) {
            return picSource.toString();
        } else {
            return "";
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
        if(picSource instanceof ContentResolverAndUri) {
            final ContentResolverAndUri cu = (ContentResolverAndUri) picSource;
            try {
                final InputStream inputStream = cu.contentResolver.openInputStream(cu.uri);
                BitmapFactory.decodeStream(inputStream, null, options);
            } catch (final FileNotFoundException e) {
                e.printStackTrace();
            }
        } else if(picSource instanceof FileDescriptor) {
            BitmapFactory.decodeFileDescriptor((FileDescriptor) picSource, null, options);
        } else if(picSource instanceof String) {
            BitmapFactory.decodeFile((String) picSource, options);
        } else if(picSource instanceof File) {
            BitmapFactory.decodeFile(((File) picSource).getPath(), options);
        } else if(picSource instanceof InputStream) {
            BitmapFactory.decodeStream((InputStream) picSource, null, options);
        } else {
            return false;
        }
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
        if (Build.VERSION.SDK_INT >= 28) {
            bitmap = loadBitmap_28(sw, sh); // rotation according to exif data is handled automatically by the API28 functions
        } else {
            final Bitmap fileBitmap = loadBitmap_legacy(sw, sh);
            bitmap = BitmapUtils.rotateBitmapExif(fileBitmap, fileOrientation);
        }
    }

    @TargetApi(28)
    private Bitmap loadBitmap_28(final int sw, final int sh) {
        final ImageDecoder.Source source;
        if(picSource instanceof ContentResolverAndUri) {
            final ContentResolverAndUri cu = (ContentResolverAndUri) picSource;
            source = ImageDecoder.createSource(cu.contentResolver, cu.uri);
        } else if(picSource instanceof String) {
            source = ImageDecoder.createSource(new File((String) picSource));
        } else if(picSource instanceof File) {
            source = ImageDecoder.createSource((File) picSource);
        } else {
            return null;
        }
        try {
            return ImageDecoder.decodeBitmap(source, (decoder, info, source1) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                decoder.setTargetSampleSize(getTargetSampleSize(sw, sh));
            });
        } catch (final IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    private Bitmap loadBitmap_legacy(final int sw, final int sh) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        if (Build.VERSION.SDK_INT >= 10) {
            setPreferQuality(o);
        }
        o.inSampleSize = getTargetSampleSize(sw, sh);
        if(picSource instanceof FileDescriptor) {
            return BitmapFactory.decodeFileDescriptor((FileDescriptor) picSource, null, o);
        } else if(picSource instanceof String) {
            return BitmapFactory.decodeFile((String) picSource, o);
        } else if(picSource instanceof File) {
            return BitmapFactory.decodeFile(((File) picSource).getPath(), o);
        } else if(picSource instanceof InputStream) {
            return BitmapFactory.decodeStream((InputStream) picSource, null, o);
        } else {
            return null;
        }
    }

    private int getTargetSampleSize(int sw, int sh) {
        // we have to use the file measures because others may have been rotated for layout
        final int size = Math.max(sw, sh);
        if (fileWidth < fileHeight) {
            // to crop for "overscan" worst case is to put the shorter side of the image at the longer side of the screen
            return Math.max(fileWidth / size, 1);
        } else {
            return Math.max(fileHeight / size, 1);
        }
    }

    @TargetApi(10)
    private static void setPreferQuality(final BitmapFactory.Options o) {
        o.inPreferQualityOverSpeed = true;
    }
}
