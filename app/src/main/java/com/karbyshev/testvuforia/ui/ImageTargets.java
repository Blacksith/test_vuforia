package com.karbyshev.testvuforia.ui;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.view.ViewGroup.LayoutParams;

import com.karbyshev.testvuforia.R;
import com.karbyshev.testvuforia.SampleApplication.SampleApplicationControl;
import com.karbyshev.testvuforia.SampleApplication.SampleApplicationException;
import com.karbyshev.testvuforia.SampleApplication.SampleApplicationSession;
import com.karbyshev.testvuforia.SampleApplication.utils.LoadingDialogHandler;
import com.karbyshev.testvuforia.SampleApplication.utils.SampleApplicationGLView;
import com.karbyshev.testvuforia.SampleApplication.utils.Texture;
import com.vuforia.CameraDevice;
import com.vuforia.DataSet;
import com.vuforia.DeviceTracker;
import com.vuforia.FUSION_PROVIDER_TYPE;
import com.vuforia.ObjectTracker;
import com.vuforia.PositionalDeviceTracker;
import com.vuforia.STORAGE_TYPE;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.Tracker;
import com.vuforia.TrackerManager;
import com.vuforia.Vuforia;

import java.util.ArrayList;
import java.util.Vector;

public class ImageTargets extends AppCompatActivity implements SampleApplicationControl {

    private static final String LOGTAG = "ImageTargets";

    private SampleApplicationSession vuforiaAppSession;

    private DataSet mCurrentDataset;
    private int mCurrentDatasetSelectionIndex = 0;
    private int mStartDatasetsIndex = 0;
    private int mDatasetsNumber = 0;
    private final ArrayList<String> mDatasetStrings = new ArrayList<>();

    // Our OpenGL view:
    private SampleApplicationGLView mGlView;

    // Our renderer:
    private ImageTargetRenderer mRenderer;

    private GestureDetector mGestureDetector;

    // The textures we will use for rendering:
    private Vector<Texture> mTextures;

    private boolean mSwitchDatasetAsap = false;
    private boolean mFlash = false;
    private boolean mContAutofocus = true;
    private boolean mDeviceTracker = false;

    private View mFocusOptionView;
    private View mFlashOptionView;

    private RelativeLayout mUILayout;

//    private SampleAppMenu mSampleAppMenu;

    final LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);

    // Alert Dialog used to display SDK errors
    private AlertDialog mErrorDialog;

    private boolean mIsDroidDevice = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_image_targets);

        vuforiaAppSession = new SampleApplicationSession(this);

        startLoadingAnimation();

        mDatasetStrings.add("StonesAndChips.xml");

        vuforiaAppSession.initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mTextures = new Vector<>();

        loadTextures();

        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");
    }

    // Called when the activity will start interacting with the user.
    @Override
    protected void onResume() {
        Log.d(LOGTAG, "onResume");
        super.onResume();

        showProgressIndicator(true);

        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        vuforiaAppSession.onResume();
    }

    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config) {
        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);

        vuforiaAppSession.onConfigurationChanged();
    }

    // Called when the system is about to start resuming a previous activity.
    @Override
    protected void onPause() {
        Log.d(LOGTAG, "onPause");
        super.onPause();

        if (mGlView != null) {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }

        vuforiaAppSession.onPause();
    }

    // The final call you receive before your activity is destroyed.
    @Override
    protected void onDestroy() {
        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();

        try {
            vuforiaAppSession.stopAR();
        } catch (SampleApplicationException e) {
            Log.e(LOGTAG, e.getString());
        }

        // Unload texture:
        mTextures.clear();
        mTextures = null;

        System.gc();
    }

    private void loadTextures() {
        mTextures.add(Texture.loadTextureFromApk("Buildings.jpeg", getAssets()));
    }

    private void startLoadingAnimation() {
        mUILayout = (RelativeLayout) View.inflate(getApplicationContext(), R.layout.camera_overlay, null);

        mUILayout.setVisibility(View.VISIBLE);
        mUILayout.setBackgroundColor(Color.BLACK);

        // Gets a reference to the loading dialog
        loadingDialogHandler.mLoadingDialogContainer = mUILayout
                .findViewById(R.id.loading_indicator);

        // Shows the loading indicator at start
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);

        // Adds the inflated layout to the view
        addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));

    }

    // Initializes AR application components.
    private void initApplicationAR() {
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();

        mGlView = new SampleApplicationGLView(getApplicationContext());
        mGlView.init(translucent, depthSize, stencilSize);

        mRenderer = new ImageTargetRenderer(this, vuforiaAppSession);
        mRenderer.setTextures(mTextures);
        mGlView.setRenderer(mRenderer);
    }

    @Override
    public boolean doInitTrackers() {
        // Indicate if the trackers were initialized correctly
        boolean result = true;

        // To get the best performance for device tracking in this application
        // we ensure that the most optimal fusion provider is being used.

        int provider = Vuforia.getActiveFusionProvider();

        // For ImageTargets, the recommended fusion provider mode is
        // the one recommended by the FUSION_OPTIMIZE_IMAGE_TARGETS_AND_VUMARKS enum
        if ((provider & ~FUSION_PROVIDER_TYPE.FUSION_OPTIMIZE_IMAGE_TARGETS_AND_VUMARKS) != 0) {
            if (Vuforia.setAllowedFusionProviders(FUSION_PROVIDER_TYPE.FUSION_OPTIMIZE_IMAGE_TARGETS_AND_VUMARKS) == FUSION_PROVIDER_TYPE.FUSION_PROVIDER_INVALID_OPERATION) {
                Log.e(LOGTAG, "Failed to select the recommended fusion provider mode (FUSION_OPTIMIZE_IMAGE_TARGETS_AND_VUMARKS).");
                return false;
            }
        }

        TrackerManager tManager = TrackerManager.getInstance();

        // Trying to initialize the image tracker
        Tracker tracker = tManager.initTracker(ObjectTracker.getClassType());
        if (tracker == null) {
            Log.e(
                    LOGTAG,
                    "Tracker not initialized. Tracker already initialized or the camera is already started");
            result = false;
        } else {
            Log.i(LOGTAG, "Tracker successfully initialized");
        }

        // Initialize the Positional Device Tracker
        DeviceTracker deviceTracker = (PositionalDeviceTracker)
                tManager.initTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null) {
            Log.i(LOGTAG, "Successfully initialized Device Tracker");
        } else {
            Log.e(LOGTAG, "Failed to initialize Device Tracker");
        }

        return result;
    }

    @Override
    public boolean doLoadTrackersData() {
        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (mCurrentDataset == null)
            mCurrentDataset = objectTracker.createDataSet();

        if (mCurrentDataset == null)
            return false;

        if (!mCurrentDataset.load(
                mDatasetStrings.get(mCurrentDatasetSelectionIndex),
                STORAGE_TYPE.STORAGE_APPRESOURCE))
            return false;

        if (!objectTracker.activateDataSet(mCurrentDataset))
            return false;

        int numTrackables = mCurrentDataset.getNumTrackables();
        for (int count = 0; count < numTrackables; count++) {
            Trackable trackable = mCurrentDataset.getTrackable(count);
            String name = "Current Dataset : " + trackable.getName();
            trackable.setUserData(name);
            Log.d(LOGTAG, "UserData:Set the following user data "
                    + trackable.getUserData());
        }

        return true;
    }

    @Override
    public boolean doStartTrackers() {
        // Indicate if the trackers were started correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());

        if (objectTracker != null && objectTracker.start()) {
            Log.i(LOGTAG, "Successfully started Object Tracker");
        } else {
            Log.e(LOGTAG, "Failed to start Object Tracker");
            result = false;
        }

        if (isDeviceTrackingActive()) {
            PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker) trackerManager
                    .getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null && deviceTracker.start()) {
                Log.i(LOGTAG, "Successfully started Device Tracker");
            } else {
                Log.e(LOGTAG, "Failed to start Device Tracker");
            }
        }

        return result;
    }

    @Override
    public boolean doStopTrackers() {
        // Indicate if the trackers were stopped correctly
        boolean result = true;

        TrackerManager trackerManager = TrackerManager.getInstance();

        Tracker objectTracker = trackerManager.getTracker(ObjectTracker.getClassType());
        if (objectTracker != null) {
            objectTracker.stop();
            Log.i(LOGTAG, "Successfully stopped object tracker");
        } else {
            Log.e(LOGTAG, "Failed to stop object tracker");
            result = false;
        }

        // Stop the device tracker
        if (isDeviceTrackingActive()) {

            Tracker deviceTracker = trackerManager.getTracker(PositionalDeviceTracker.getClassType());

            if (deviceTracker != null) {
                deviceTracker.stop();
                Log.i(LOGTAG, "Successfully stopped device tracker");
            } else {
                Log.e(LOGTAG, "Could not stop device tracker");
            }
        }

        return result;
    }

    @Override
    public boolean doUnloadTrackersData() {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;

        TrackerManager tManager = TrackerManager.getInstance();
        ObjectTracker objectTracker = (ObjectTracker) tManager
                .getTracker(ObjectTracker.getClassType());
        if (objectTracker == null)
            return false;

        if (mCurrentDataset != null && mCurrentDataset.isActive()) {
            if (objectTracker.getActiveDataSet(0).equals(mCurrentDataset)
                    && !objectTracker.deactivateDataSet(mCurrentDataset)) {
                result = false;
            } else if (!objectTracker.destroyDataSet(mCurrentDataset)) {
                result = false;
            }

            mCurrentDataset = null;
        }

        return result;
    }

    @Override
    public boolean doDeinitTrackers() {
        TrackerManager tManager = TrackerManager.getInstance();

        // Indicate if the trackers were deinitialized correctly
        boolean result = tManager.deinitTracker(ObjectTracker.getClassType());
        tManager.deinitTracker(PositionalDeviceTracker.getClassType());

        return result;
    }

    @Override
    public void onInitARDone(SampleApplicationException exception) {

        if (exception == null) {
            initApplicationAR();

            mRenderer.setActive(true);

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT));

            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();

            // Sets the layout background to transparent
            mUILayout.setBackgroundColor(Color.TRANSPARENT);

//            mSampleAppMenu = new SampleAppMenu(this, this, "Image Targets",
//                    mGlView, mUILayout, null);
//            setSampleAppMenuSettings();
            deviceTracker();

            vuforiaAppSession.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT);
        } else {
            Log.e(LOGTAG, exception.getString());
            showToast(exception.getString());
//            showInitializationErrorMessage(exception.getString());
        }
    }

    @Override
    public void onVuforiaUpdate(State state) {

        if (mSwitchDatasetAsap) {
            mSwitchDatasetAsap = false;
            TrackerManager tm = TrackerManager.getInstance();
            ObjectTracker ot = (ObjectTracker) tm.getTracker(ObjectTracker
                    .getClassType());
            if (ot == null || mCurrentDataset == null
                    || ot.getActiveDataSet(0) == null) {
                Log.d(LOGTAG, "Failed to swap datasets");
                return;
            }

            doUnloadTrackersData();
            doLoadTrackersData();
        }
    }

    @Override
    public void onVuforiaResumed() {

        if (mGlView != null) {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }

    @Override
    public void onVuforiaStarted() {

        mRenderer.updateConfiguration();

        if (mContAutofocus) {
            // Set camera focus mode
            if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)) {
                // If continuous autofocus mode fails, attempt to set to a different mode
                if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)) {
                    CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
                }

                // Update Toggle state
//                setMenuToggle(mFocusOptionView, false);
            } else {
                // Update Toggle state
//                setMenuToggle(mFocusOptionView, true);
            }
        } else {
//            setMenuToggle(mFocusOptionView, false);
        }

        showProgressIndicator(false);
    }

    private void showProgressIndicator(boolean show) {
        if (show) {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
        } else {
            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        }
    }

    boolean isDeviceTrackingActive() {
        return mDeviceTracker;
    }

    public void setmDeviceTracker(boolean mDeviceTracker) {
        this.mDeviceTracker = mDeviceTracker;
    }

    private boolean deviceTracker() {
        boolean result = true;
        TrackerManager trackerManager = TrackerManager.getInstance();
        PositionalDeviceTracker deviceTracker = (PositionalDeviceTracker)
                trackerManager.getTracker(PositionalDeviceTracker.getClassType());

        if (deviceTracker != null) {
            if (!mDeviceTracker) {
                if (!deviceTracker.start()) {
                    Log.e(LOGTAG,
                            "Failed to start device tracker");
                    showToast("Failed to start device tracker");
                    result = false;
                } else {
                    Log.d(LOGTAG,
                            "Successfully started device tracker");
                    showToast("Successfully started device tracker");
                }
            } else {
                deviceTracker.stop();
                Log.d(LOGTAG,
                        "Successfully stopped device tracker");
                showToast("Successfully stopped device tracker");
            }
        } else {
            Log.e(LOGTAG, "Device tracker is null!");
            showToast("Device tracker is null!");
            result = false;
        }

        if (result)
            mDeviceTracker = !mDeviceTracker;

        return result;
    }

    private void showToast(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
    }
}
