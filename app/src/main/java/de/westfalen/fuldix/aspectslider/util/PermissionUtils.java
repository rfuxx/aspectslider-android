package de.westfalen.fuldix.aspectslider.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.service.dreams.DreamService;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import de.westfalen.fuldix.aspectslider.R;

public class PermissionUtils {
    public static final int PERM_DEFAULT_REQUEST_CODE = 1;
    private static final String TAG = PermissionUtils.class.getSimpleName();
    private static final String KEY_GRANT_RESULTS = TAG + "_GRANT_RESULTS";
    private static final String KEY_PERMISSIONS = TAG + "_PERMISSIONS";
    private static final String KEY_REQUEST_CODE = TAG + "_REQUEST_CODE";
    private static final String KEY_RESULT_RECEIVER = TAG + "_RESULT_RECEIVER";

    public interface PermissionResultReceiverSupport {
        void setPermissionResultReceiver(final int requestCode, final ResultReceiver resultReceiver);
    }
    public interface PermissionResultReceiver {
        void onRequestPermissionsResult(final int requestCode, final String permissions[], final int[] grantResults);
    }
    public static class PermissionResultAdapter implements PermissionResultReceiver {
        public void onRequestPermissionsGranted(final int resultCode) {
        }
        public void onRequestPermissionsDenied(final int resultCode) {
        }
        @Override
        public void onRequestPermissionsResult(final int requestCode, final String permissions[], final int[] grantResults) {
            final List<String> missingPermissions = getMissingPermissions(permissions, grantResults);
            if(missingPermissions.isEmpty()) {
                onRequestPermissionsGranted(requestCode);
            } else {
                onRequestPermissionsDenied(requestCode);
            }
        }
    }

    public static List<String> getMissingPermissions(final String permissions[], final int[] grantResults) {
        final List<String> missingPermissions = new ArrayList<>();
        for(int i=0; i<grantResults.length; i++) {
            if(grantResults[i] != PackageManager.PERMISSION_GRANTED && i < permissions.length) {
                missingPermissions.add(permissions[i]);
            }
        }
        return missingPermissions;
    }

    @TargetApi(23)
    public static List<String> getMissingPermissions(final Context context, final String permissions[]) {
        final List<String> missingPermissions = new ArrayList<>();
        for (final String permission : permissions) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        return missingPermissions;
    }

    public static boolean checkOrRequestPermissions(final Context context, final String[] permissions, final PermissionResultReceiver permissionResultReceiver) {
        return checkOrRequestPermissions(context, PERM_DEFAULT_REQUEST_CODE, permissions, permissionResultReceiver);
    }

    public static boolean checkOrRequestPermissions(final Context context, final int requestCode, final String[] permissions, final PermissionResultReceiver permissionResultReceiver) {
        if (Build.VERSION.SDK_INT < 23) {
            return true; // older Android versions generally have all Manifest permissions
        } else {
            final List<String> missingPermissions = getMissingPermissions(context, permissions);
            if(missingPermissions.isEmpty()) {
                return true;
            } else {
                requestPermissions(context, requestCode, missingPermissions.toArray(new String[0]), permissionResultReceiver);
                return false;
            }
        }
    }

    public static boolean checkOrRequestPermissions(final Activity activity, final String[] permissions) {
        return checkOrRequestPermissions(activity, PERM_DEFAULT_REQUEST_CODE, permissions);
    }

    public static boolean checkOrRequestPermissions(final Activity activity, final int requestCode, final String[] permissions) {
        if (Build.VERSION.SDK_INT < 23) {
            return true; // older Android versions generally have all Manifest permissions
        } else {
            final List<String> missingPermissions = getMissingPermissions(activity, permissions);
            if(missingPermissions.isEmpty()) {
                return true;
            } else {
                boolean shouldShowRationale = false;
                for(final String permission: missingPermissions) {
                    if(activity.shouldShowRequestPermissionRationale(permission)) {
                        shouldShowRationale = true;
                        break;
                    }
                }
                if(shouldShowRationale) {
                    requestPermissions(activity, requestCode, missingPermissions.toArray(new String[0]), new PermissionResultReceiver() {
                        @Override
                        public void onRequestPermissionsResult(final int requestCode, final String permissions[], final int[] grantResults) {
                            activity.onRequestPermissionsResult(requestCode, permissions, grantResults);
                        }
                    });
                } else {
                    activity.requestPermissions(permissions, requestCode);
                }
                return false;
            }
        }
    }

    public static void toastDenied(final Context context) {
        Toast.makeText(context, context.getString(R.string.permission_missing), Toast.LENGTH_LONG).show();
    }

    @TargetApi(23)
    public static void requestPermissions(final Context context, final int requestCode, final String[] permissions, final PermissionResultReceiver permissionResultReceiver) {
        final ResultReceiver resultReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult (final int resultCode, final Bundle resultData) {
                final String[] permissions = resultData.getStringArray(KEY_PERMISSIONS);
                final int[] grantResults = resultData.getIntArray(KEY_GRANT_RESULTS);
                permissionResultReceiver.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        };

        if(context instanceof Activity && context instanceof PermissionResultReceiverSupport)  {
            boolean shouldShowRationale = false;
            for(final String permission : permissions) {
                if (((Activity) context).shouldShowRequestPermissionRationale(permission)) {
                    shouldShowRationale = true;
                }
            }
            if(!shouldShowRationale) {
                ((PermissionResultReceiverSupport) context).setPermissionResultReceiver(requestCode, resultReceiver);
                ((Activity) context).requestPermissions(permissions, requestCode);
                return;
            }
        }
        final Intent permissionRequestIntent = new Intent(context, PermissionRequestActivity.class);
        permissionRequestIntent.putExtra(KEY_RESULT_RECEIVER, resultReceiver);
        permissionRequestIntent.putExtra(KEY_PERMISSIONS, permissions);
        permissionRequestIntent.putExtra(KEY_REQUEST_CODE, requestCode);
        context.startActivity(permissionRequestIntent);
    }


    public static void requestPermissionsResultToResultReceiver(final int requestCode, final String permissions[], final int[] grantResults, final ResultReceiver resultReceiver) {
        final Bundle resultData = new Bundle();
        resultData.putStringArray(KEY_PERMISSIONS, permissions);
        resultData.putIntArray(KEY_GRANT_RESULTS, grantResults);
        resultReceiver.send(requestCode, resultData);
    }


    @TargetApi(23)
    public static class PermissionRequestActivity extends Activity {
        ResultReceiver resultReceiver;
        String[] permissions;
        int requestCode;

        @Override
        public void onRequestPermissionsResult(final int requestCode, final String permissions[], final int[] grantResults) {
            requestPermissionsResultToResultReceiver(requestCode, permissions, grantResults, resultReceiver);
            finish();
        }

        @Override
        protected void onStart() {
            super.onStart();

            resultReceiver = this.getIntent().getParcelableExtra(KEY_RESULT_RECEIVER);
            permissions = this.getIntent().getStringArrayExtra(KEY_PERMISSIONS);
            requestCode = this.getIntent().getIntExtra(KEY_REQUEST_CODE, 0);

            requestPermissions(permissions, requestCode);
        }

        @Override
        protected void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_permissionrequest);
        }
    }
}
