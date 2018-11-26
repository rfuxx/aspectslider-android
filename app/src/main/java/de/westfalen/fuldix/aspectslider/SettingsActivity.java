package de.westfalen.fuldix.aspectslider;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import java.util.List;

public class SettingsActivity extends PreferenceActivity {
    private static final boolean ALWAYS_SIMPLE_PREFS = false;

    public static final String REMEMBER_COLLECTION_DESCRIPTION_DIR = "pref_description_remember_dir";
    public static final String REMEMBER_COLLECTION_DESCRIPTION_SELECTION = "pref_description_remember_selection";

    public static final String PREF_DELAY = "delay";
    public static final String PREF_SPACE_BETWEEN_SLIDES = "space_between_slides";
    public static final String PREF_ALLOW_OVERSCAN = "allow_overscan";
    public static final String PREF_RANDOM = "random";
    public static final String PREF_RANDOM_AGAIN = "random_again";
    public static final String PREF_RECURSE = "recurse";
    public static final String PREF_SIZE_FILTER = "size_filter";
    public static final String PREF_REMEMBER_COLLECTION = "remember_collection";
    public static final String PREF_IGNORE_MEDIA_STORE = "ignore_media_store";

    public static final String PREF_MEDIA_URI = "media_uri";
    public static final String PREF_DIRPATH = "dirpath";
    public static final String PREF_MEDIA_SELECTION = "media_selection";

    private String rememberDirDescription;
    private String rememberSelectionDescription;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle extras = getIntent().getExtras();
        if (extras != null) {
            rememberDirDescription = extras.getString(REMEMBER_COLLECTION_DESCRIPTION_DIR);
            rememberSelectionDescription = extras.getString(REMEMBER_COLLECTION_DESCRIPTION_SELECTION);
        }
    }

    @Override
    protected void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        setupSimplePreferencesScreen();
    }

    private void setupSimplePreferencesScreen() {
        if (!isSimplePreferences(this)) {
            return;
        }

        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_fakeroot);
        PreferenceCategory fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_slide_show);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_slides);
        fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_scan_files);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_scan_files);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference(PREF_DELAY));
        bindPreferenceSummaryToValue(findPreference(PREF_SPACE_BETWEEN_SLIDES));
        bindPreferenceSummaryToValue(findPreference(PREF_SIZE_FILTER));
        linkIgnoreMediaStoreWithRememberCollection((CheckBoxPreference) findPreference(PREF_IGNORE_MEDIA_STORE), findPreference(PREF_REMEMBER_COLLECTION), rememberDirDescription, rememberSelectionDescription);
    }

    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this) && !isSimplePreferences(this);
    }

    /**
     * Helper method to determine if the device has an extra-large screen. For
     * example, 10" tablets are extra-large.
     */
    private static boolean isXLargeTablet(final Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    /**
     * Determines whether the simplified settings UI should be shown. This is
     * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
     * doesn't have newer APIs like {@link PreferenceFragment}, or the device
     * doesn't have an extra-large screen. In these cases, a single-pane
     * "simplified" settings UI should be shown.
     */
    private static boolean isSimplePreferences(final Context context) {
        return ALWAYS_SIMPLE_PREFS
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
                || !isXLargeTablet(context);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void onBuildHeaders(final List<Header> target) {
        if (!isSimplePreferences(this)) {
            loadHeadersFromResource(R.xml.pref_headers, target);
        }
    }

    private static final Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(final Preference preference, final Object value) {
            final String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                final ListPreference listPreference = (ListPreference) preference;
                final int index = listPreference.findIndexOfValue(stringValue);

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
        }
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

    private static void linkIgnoreMediaStoreWithRememberCollection(final CheckBoxPreference ignoreMediaStore, final Preference rememberCollection, final String rememberDirDescription, final String rememberSelectionDescription) {
        final Preference.OnPreferenceChangeListener listener = new LinkIgnoreMediaStoreWithRememberCollectionListener(rememberCollection, rememberDirDescription, rememberSelectionDescription);
        ignoreMediaStore.setOnPreferenceChangeListener(listener);
        listener.onPreferenceChange(ignoreMediaStore,
                PreferenceManager
                        .getDefaultSharedPreferences(ignoreMediaStore.getContext())
                        .getBoolean(ignoreMediaStore.getKey(), false));
    }

    private static class LinkIgnoreMediaStoreWithRememberCollectionListener implements Preference.OnPreferenceChangeListener {
        private final Preference rememberCollection;
        private final String rememberDirDescription;
        private final String rememberSelectionDescription;

        LinkIgnoreMediaStoreWithRememberCollectionListener(final Preference rememberCollection, final String rememberDirDescription, final String rememberSelectionDescription) {
            this.rememberCollection = rememberCollection;
            this.rememberDirDescription = rememberDirDescription;
            this.rememberSelectionDescription = rememberSelectionDescription;
        }
        @Override
        public boolean onPreferenceChange(final Preference preference, final Object newValue) {
            if (preference.getKey().equals(PREF_IGNORE_MEDIA_STORE)) {
                rememberCollection.setSummary((Boolean) newValue ? rememberDirDescription : rememberSelectionDescription);
                return true;
            }
            return false;
        }
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class SlideShowPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_slides);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference(PREF_DELAY));
            bindPreferenceSummaryToValue(findPreference(PREF_SPACE_BETWEEN_SLIDES));
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class ScanFilesPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.pref_scan_files);

            bindPreferenceSummaryToValue(findPreference(PREF_SIZE_FILTER));
            String rememberDirDescription = ((SettingsActivity) this.getActivity()).rememberDirDescription;
            String rememberSelectionDescription = ((SettingsActivity) this.getActivity()).rememberSelectionDescription;
            linkIgnoreMediaStoreWithRememberCollection((CheckBoxPreference) findPreference(PREF_IGNORE_MEDIA_STORE), findPreference(PREF_REMEMBER_COLLECTION), rememberDirDescription, rememberSelectionDescription);
        }
    }

    @Override
    protected boolean isValidFragment(final String fragmentName) {
        return fragmentName.equals(SlideShowPreferenceFragment.class.getName())
               || fragmentName.equals(ScanFilesPreferenceFragment.class.getName());
    }
}
