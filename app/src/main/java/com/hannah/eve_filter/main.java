package com.hannah.eve_filter;

import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

public class main extends AppCompatActivity {
    private Session session;
    private boolean mUserRequestedInstall;

    @Override
    protected void onResume() {
        super.onResume();

        // ARCore requires camera permission to operate.
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            try {
                Session session = new Session(this);
                session.resume();
                Frame frame = session.update();
                Camera camera = frame.getCamera();
            } catch (CameraNotAvailableException e) {
                Toast.makeText(this, "Camera not available" + e, Toast.LENGTH_LONG)
                        .show();
            } catch (UnavailableApkTooOldException | UnavailableSdkTooOldException | UnavailableArcoreNotInstalledException | UnavailableDeviceNotCompatibleException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }

        // Make sure Google Play Services for AR is installed and up to date.
        try {
            if (session == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        // Success, create the AR session.
                        session = new Session(this);
                        break;
                    case INSTALL_REQUESTED:
                        // Ensures next invocation of requestInstall() will either return
                        // INSTALLED or throw an exception.
                        mUserRequestedInstall = false;
                        return;
                }
            }
        } catch (UnavailableUserDeclinedInstallationException e) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "AR not available" + e, Toast.LENGTH_LONG)
                    .show();
            return;
        } catch (Exception e) {  // Current catch statements.0
            return;  // mSession is still null.
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        session.pause();
    }
}