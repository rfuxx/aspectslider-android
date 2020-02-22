package de.westfalen.fuldix.aspectslider;

import android.content.ContentResolver;
import android.net.Uri;

public class ContentResolverAndUri {
    public final ContentResolver contentResolver;
    public final Uri uri;
    public ContentResolverAndUri(final ContentResolver contentResolver, final Uri uri) {
        this.contentResolver = contentResolver;
        this.uri = uri;
    }
}
