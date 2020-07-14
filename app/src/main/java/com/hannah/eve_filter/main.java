package com.hannah.eve_filter;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;


import androidx.appcompat.app.AppCompatActivity;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.hannah.eve_filter.rendering.BackgroundRenderer;
import com.hannah.eve_filter.rendering.ObjectRenderer;
import com.hannah.eve_filter.rendering.PlaneRenderer;
import com.hannah.eve_filter.rendering.PointCloudRenderer;
import com.hannah.eve_filter.rendering.Texture;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class main extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = main.class.getSimpleName();
    private Session session;
    private boolean mUserRequestedInstall;

    private GLSurfaceView surfaceView;
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final ObjectRenderer virtualObject = new ObjectRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();
    private final Texture depthTexture = new Texture();
    private boolean calculateUVTransform = true;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);

        // Set up renderer.
        //stolen from arcore_java
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);


    }

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

        try {
            session = new Session(/* context= */ this);
        } catch (UnavailableArcoreNotInstalledException | UnavailableSdkTooOldException | UnavailableDeviceNotCompatibleException | UnavailableApkTooOldException e) {
            e.printStackTrace();
        }
        Config config = session.getConfig();
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
        session.configure(config);


        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            session = null;
            return;
        }
        surfaceView.onResume();
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

    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update().
            depthTexture.createOnGlThread();
            backgroundRenderer.createOnGlThread(/*context=*/ this, depthTexture.getTextureId());
            planeRenderer.createOnGlThread(/*context=*/ this, "models/trigrid.png");
            pointCloudRenderer.createOnGlThread(/*context=*/ this);

            virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png");
            virtualObject.setBlendMode(ObjectRenderer.BlendMode.AlphaBlending);
            virtualObject.setDepthTexture(
                    depthTexture.getTextureId(), depthTexture.getWidth(), depthTexture.getHeight());
            virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {

    }

    @Override
    public void onDrawFrame(GL10 gl10) {
        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = session.update();
            Camera camera = frame.getCamera();
        } catch (CameraNotAvailableException e) {
            Toast.makeText(this, "Camera not available" + e, Toast.LENGTH_LONG)
                    .show();
        }
    }
}