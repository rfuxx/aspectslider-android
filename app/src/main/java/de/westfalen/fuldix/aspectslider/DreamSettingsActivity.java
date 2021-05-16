package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.util.List;

public class DreamSettingsActivity extends PreferenceActivity {
    private static final boolean ALWAYS_SIMPLE_PREFS = false;

    private static final String DREAM_PREFIX = "dream_";

    public static final String PREF_DELAY = DREAM_PREFIX + SettingsActivity.PREF_DELAY;
    public static final String PREF_SPACE_BETWEEN_SLIDES = DREAM_PREFIX + SettingsActivity.PREF_SPACE_BETWEEN_SLIDES;
    public static final String PREF_ALLOW_OVERSCAN = DREAM_PREFIX + SettingsActivity.PREF_ALLOW_OVERSCAN;
    public static final String PREF_KEEP_BRIGHT = DREAM_PREFIX + "keep_bright";
    public static final String PREF_RANDOM = DREAM_PREFIX + SettingsActivity.PREF_RANDOM;
    public static final String PREF_RANDOM_AGAIN = DREAM_PREFIX + SettingsActivity.PREF_RANDOM_AGAIN;
    public static final String PREF_RECURSE = DREAM_PREFIX + SettingsActivity.PREF_RECURSE;
    public static final String PREF_SIZE_FILTER = DREAM_PREFIX + SettingsActivity.PREF_SIZE_FILTER;
    public static final String PREF_REMEMBER_COLLECTION = DREAM_PREFIX + SettingsActivity.PREF_REMEMBER_COLLECTION;
    public static final String PREF_IGNORE_MEDIA_STORE = DREAM_PREFIX + SettingsActivity.PREF_IGNORE_MEDIA_STORE;

    public static final String PREF_MEDIA_URI = DREAM_PREFIX + SettingsActivity.PREF_MEDIA_URI;
    public static final String PREF_DIR_PATH = DREAM_PREFIX + SettingsActivity.PREF_DIR_PATH;
    public static final String PREF_MEDIA_SELECTION = DREAM_PREFIX + SettingsActivity.PREF_MEDIA_SELECTION;

    private static final String PREF_ALL_GALLERY_PICTURES = "dream_all_gallery_pictures";
    private static final String PREF_DIR_PICTURES = "dream_dir_pictures";
    private static final String PREF_DIR_EXTERNAL = "dream_dir_external";
    private static final String PREF_DIR_ROOT = "dream_dir_root";

    private final Handler uiHandler = new Handler();
    private final Runnable setupSimplePreferencesScreenRunnable = this::setupSimplePreferencesScreen;

    private CheckBoxPreference prefDirPictures;
    private CheckBoxPreference prefDirExternal;
    private CheckBoxPreference prefDirRoot;
    private Preference prefDirPath;
    private CheckBoxPreference prefAllGalleryPictures;
    private Preference prefMediaSelection;

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        setupSimplePreferencesScreen();
    }

    private void setupSimplePreferencesScreen() {
        if (isFragmentCapablePreferences(this)) {
            return;
        }

        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        final PreferenceScreen ps = getPreferenceScreen();
        if(ps != null) {
            ps.removeAll();
        }
        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_fakeroot);
        if((PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_IGNORE_MEDIA_STORE, false))) {
            addPreferencesFromResource(R.xml.pref_dream_fileselect);
            prefDirPath = findPreference(PREF_DIR_PATH);
            prefDirPictures = (CheckBoxPreference) findPreference(PREF_DIR_PICTURES);
            prefDirExternal = (CheckBoxPreference) findPreference(PREF_DIR_EXTERNAL);
            prefDirRoot = (CheckBoxPreference) findPreference(PREF_DIR_ROOT);
            setFileSelectSummary();
            if (Build.VERSION.SDK_INT >= 8) {
                prefDirPictures.setOnPreferenceChangeListener(sBindDirPath);
            } else {
                prefDirPictures.setEnabled(false);
            }
            prefDirExternal.setOnPreferenceChangeListener(sBindDirPath);
            prefDirRoot.setOnPreferenceChangeListener(sBindDirPath);
            prefDirPath.setOnPreferenceClickListener(sBindDirSelect);
        } else {
            addPreferencesFromResource(R.xml.pref_dream_galleryselect);
            prefMediaSelection = findPreference(PREF_MEDIA_SELECTION);
            prefAllGalleryPictures = (CheckBoxPreference) findPreference(PREF_ALL_GALLERY_PICTURES);
            setGallerySummary();
            prefAllGalleryPictures.setOnPreferenceChangeListener(sBindAllGalleries);
            prefMediaSelection.setOnPreferenceClickListener(sBindGalleryName);
        }
        PreferenceCategory fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_slide_show);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_dream_slides);
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_scan_files);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_dream_scan_files);

        bindPreferenceSummaryToValue(findPreference(PREF_DELAY));
        bindPreferenceSummaryToValue(findPreference(PREF_SPACE_BETWEEN_SLIDES));
        bindPreferenceSummaryToValue(findPreference(PREF_SIZE_FILTER));
        findPreference(PREF_IGNORE_MEDIA_STORE).setOnPreferenceChangeListener(sBindIgnoreMediaStore);
    }

    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && isFragmentCapablePreferences(this);
    }

    private static boolean isXLargeTablet(final Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    private static boolean isFragmentCapablePreferences(final Context context) {
        return !ALWAYS_SIMPLE_PREFS
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB
                && isXLargeTablet(context);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(final List<Header> target) {
        if (isFragmentCapablePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_dream_headers, target);
        }
    }

    private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = (preference, value) -> {
        final String stringValue = value.toString();

        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            final ListPreference listPreference = (ListPreference) preference;
            int index = listPreference.findIndexOfValue(stringValue);

            CharSequence summary = index >= 0
                    ? listPreference.getEntries()[index]
                    : null;
            if (summary != null) {
                switch (preference.getKey()) {
                    case PREF_DELAY:
                        summary = preference.getContext().getString(R.string.pref_description_delay, summary);
                        break;
                    case PREF_SPACE_BETWEEN_SLIDES:
                        summary = preference.getContext().getString(R.string.pref_description_space_between_slides, summary);
                        break;
                    case PREF_SIZE_FILTER:
                        if(index > 0) {
                            summary = preference.getContext().getString(R.string.pref_description_size_filter, summary);
                        }
                        break;
                }
            }
            // Set the summary to reflect the new value.
            preference.setSummary(summary);

        } else {
            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
        }
        return true;
    };

    private static void bindPreferenceSummaryToValue(final Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }


    private final Preference.OnPreferenceChangeListener sBindIgnoreMediaStore = (preference, value) -> {
        if(PREF_IGNORE_MEDIA_STORE.equals(preference.getKey())) {
            uiHandler.postAtFrontOfQueue(setupSimplePreferencesScreenRunnable);
        }
        return true;
    };

    private Uri getMediaUri() {
        final SharedPreferences sharedPrefs = (PreferenceManager.getDefaultSharedPreferences(this));
        Uri mediaUri = Uri.parse(sharedPrefs.getString(PREF_MEDIA_URI, MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()));
        if(mediaUri == null) {
            mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        return mediaUri;
    }

    private void setGallerySummary() {
        if(prefMediaSelection != null) {
            final SharedPreferences sharedPrefs = (PreferenceManager.getDefaultSharedPreferences(this));
            final String mediaSelection = sharedPrefs.getString(PREF_MEDIA_SELECTION, null);
            final boolean allPictures = mediaSelection == null || mediaSelection.equals("");
            prefAllGalleryPictures.setChecked(allPictures);
            if(allPictures) {
                prefMediaSelection.setSummary(null);
            } else {
                Uri mediaUri = getMediaUri();
                final String[] columns = {"distinct " + MediaStore.Images.Media.BUCKET_DISPLAY_NAME};
                final Cursor c = getContentResolver().query(mediaUri, columns, mediaSelection, null, null);
                if (c != null && c.moveToNext()) {
                    prefMediaSelection.setSummary(c.getString(c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)));
                    c.close();
                }
            }
        }
    }

    private final Preference.OnPreferenceClickListener sBindGalleryName = preference -> {
        if(PREF_MEDIA_SELECTION.equals(preference.getKey())) {
            final Intent intent = new Intent(DreamSettingsActivity.this, MediaStoreSelector.class);
            intent.putExtra(MediaStoreSelector.START_URI, getMediaUri());
            startActivityForResult(intent, MediaStoreSelector.SELECT_MEDIA);
            return true;
        }
        return false;
    };

    private final Preference.OnPreferenceChangeListener sBindAllGalleries = (preference, value) -> {
        if(PREF_ALL_GALLERY_PICTURES.equals(preference.getKey())) {
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(DreamSettingsActivity.this);
            sharedPreferences.edit()
                    .putString(PREF_MEDIA_SELECTION, null)
                    .commit();
            setGallerySummary();
        }
        return value.equals(true); // not really de-selectable, this checkmark :-)
    };

    private final Preference.OnPreferenceChangeListener sBindDirPath = (preference, value) -> {
        final File dirPath;
        switch(preference.getKey()) {
            case PREF_DIR_PICTURES:
                if(Build.VERSION.SDK_INT >= 8) {
                    dirPath = getExternalPicturesDir();
                } else {
                    dirPath = null;
                }
                break;
            case PREF_DIR_EXTERNAL:
                dirPath = Environment.getExternalStorageDirectory();
                break;
            case PREF_DIR_ROOT:
                dirPath = Environment.getRootDirectory();
                break;
            default:
                dirPath = null;
                break;
        }
        if(dirPath != null) {
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(DreamSettingsActivity.this);
            sharedPreferences.edit()
                    .putString(PREF_DIR_PATH, dirPath.getAbsolutePath())
                    .commit();
            setFileSelectSummary();
        }
        return value.equals(true); // not really de-selectable, this checkmark :-)
    };

    private void setFileSelectSummary() {
        if(prefDirPath != null) {
            final SharedPreferences sharedPrefs = (PreferenceManager.getDefaultSharedPreferences(this));
            final String dirPath = sharedPrefs.getString(PREF_DIR_PATH, "");
            final boolean isPicturesDir = Build.VERSION.SDK_INT >= 8 && dirPath!= null && dirPath.equals(getExternalPicturesDir().getAbsolutePath());
            prefDirPictures.setChecked(isPicturesDir);
            final boolean isExternalDir = dirPath != null && dirPath.equals(Environment.getExternalStorageDirectory().getAbsolutePath());
            prefDirExternal.setChecked(isExternalDir);
            final boolean isRootDir = dirPath != null && dirPath.equals(Environment.getRootDirectory().getAbsolutePath());
            prefDirRoot.setChecked(isRootDir);
            prefDirPath.setSummary(dirPath);
        }
    }

    @TargetApi(8)
    private File getExternalPicturesDir() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
    }

    private final Preference.OnPreferenceClickListener sBindDirSelect = preference -> {
        if(PREF_DIR_PATH.equals(preference.getKey())) {
            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(DreamSettingsActivity.this);
            final String dirPath = sharedPreferences.getString(PREF_DIR_PATH, Environment.getExternalStorageDirectory().getAbsolutePath());
            final Intent intent = new Intent(DreamSettingsActivity.this, DirectorySelector.class);
            intent.putExtra(DirectorySelector.START_DIR, dirPath);
            intent.putExtra(DirectorySelector.SHOW_HIDDEN, false);
            intent.putExtra(DirectorySelector.ONLY_DIRS, true);
            intent.putExtra(DirectorySelector.ALLOW_UP, true);
            startActivityForResult(intent, DirectorySelector.SELECT_DIRECTORY);
            return true;
        }
        return false;
    };

    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case DirectorySelector.SELECT_DIRECTORY: {
                    final Bundle extras = data.getExtras();
                    if(extras != null) {
                        final String fileName = extras.getString(DirectorySelector.RETURN_DIRECTORY);
                        if (fileName != null) {
                            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                            sharedPreferences.edit()
                                    .putString(PREF_DIR_PATH, fileName)
                                    .commit();
                            setFileSelectSummary();
                        }
                    }
                    break;
                }
                case MediaStoreSelector.SELECT_MEDIA: {
                    final Bundle extras = data.getExtras();
                    if(extras != null) {
                        final Uri mediaUri = (Uri) extras.get(MediaStoreSelector.RETURN_URI);
                        if(mediaUri != null) {
                            String mediaSelection = extras.getString(MediaStoreSelector.RETURN_SELECTION);
                            if (mediaSelection == null) {
                                mediaSelection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString();
                            }
                            final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                            sharedPreferences.edit()
                                    .putString(PREF_MEDIA_URI, mediaUri.toString())
                                    .putString(PREF_MEDIA_SELECTION, mediaSelection)
                                    .commit();
                            setGallerySummary();
                        }
                    }
                    break;
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SelectionPreferenceFragment extends PreferenceFragment {
        private DreamSettingsActivity dreamSettingsActivity;

        @Override
        public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
            dreamSettingsActivity = (DreamSettingsActivity) getActivity();
            setup();
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        public void setup() {
            final PreferenceScreen ps = getPreferenceScreen();
            if(ps != null) {
                ps.removeAll();
            }
            if((PreferenceManager.getDefaultSharedPreferences(dreamSettingsActivity).getBoolean(PREF_IGNORE_MEDIA_STORE, false))) {
                addPreferencesFromResource(R.xml.pref_dream_fileselect);
                dreamSettingsActivity.prefDirPath = findPreference(PREF_DIR_PATH);
                dreamSettingsActivity.prefDirPictures = (CheckBoxPreference) findPreference(PREF_DIR_PICTURES);
                dreamSettingsActivity.prefDirExternal = (CheckBoxPreference) findPreference(PREF_DIR_EXTERNAL);
                dreamSettingsActivity.prefDirRoot = (CheckBoxPreference) findPreference(PREF_DIR_ROOT);
                dreamSettingsActivity.setFileSelectSummary();
                if (Build.VERSION.SDK_INT >= 8) {
                    dreamSettingsActivity.prefDirPictures.setOnPreferenceChangeListener(dreamSettingsActivity.sBindDirPath);
                } else {
                    dreamSettingsActivity.prefDirPictures.setEnabled(false);
                }
                dreamSettingsActivity.prefDirExternal.setOnPreferenceChangeListener(dreamSettingsActivity.sBindDirPath);
                dreamSettingsActivity.prefDirRoot.setOnPreferenceChangeListener(dreamSettingsActivity.sBindDirPath);
                dreamSettingsActivity.prefDirPath.setOnPreferenceClickListener(dreamSettingsActivity.sBindDirSelect);
            } else {
                addPreferencesFromResource(R.xml.pref_dream_galleryselect);
                dreamSettingsActivity.prefMediaSelection = findPreference(PREF_MEDIA_SELECTION);
                dreamSettingsActivity.prefAllGalleryPictures = (CheckBoxPreference) findPreference(PREF_ALL_GALLERY_PICTURES);
                dreamSettingsActivity.setGallerySummary();
                dreamSettingsActivity.prefAllGalleryPictures.setOnPreferenceChangeListener(dreamSettingsActivity.sBindAllGalleries);
                dreamSettingsActivity.prefMediaSelection.setOnPreferenceClickListener(dreamSettingsActivity.sBindGalleryName);
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SlideShowPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_dream_slides);

            bindPreferenceSummaryToValue(findPreference(PREF_DELAY));
            bindPreferenceSummaryToValue(findPreference(PREF_SPACE_BETWEEN_SLIDES));
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class ScanFilesPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_dream_scan_files);

            bindPreferenceSummaryToValue(findPreference(PREF_SIZE_FILTER));
        }
    }

    @Override
    protected boolean isValidFragment(final String fragmentName) {
        return fragmentName.equals(SelectionPreferenceFragment.class.getName())
                || fragmentName.equals(SlideShowPreferenceFragment.class.getName())
                || fragmentName.equals(ScanFilesPreferenceFragment.class.getName());
    }
}
