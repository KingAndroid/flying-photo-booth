/*
 * Copyright (C) 2013 Benedict Lau
 * 
 * All rights reserved.
 */
package com.groundupworks.partyphotobooth.fragments;

import java.lang.ref.WeakReference;
import java.util.List;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable.Callback;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.groundupworks.lib.photobooth.helpers.CameraHelper;
import com.groundupworks.lib.photobooth.helpers.ImageHelper;
import com.groundupworks.lib.photobooth.views.AnimationDrawableCallback;
import com.groundupworks.lib.photobooth.views.CenteredPreview;
import com.groundupworks.partyphotobooth.R;
import com.groundupworks.partyphotobooth.helpers.PreferencesHelper;
import com.groundupworks.partyphotobooth.helpers.PreferencesHelper.PhotoBoothMode;

/**
 * Ui for the camera preview and capture screen.
 * 
 * @author Benedict Lau
 */
public class CaptureFragment extends Fragment {

    /**
     * Invalid camera id.
     */
    private static final int INVALID_CAMERA_ID = -1;

    /**
     * The default captured Jpeg quality.
     */
    private static final int CAPTURED_JPEG_QUALITY = 100;

    /**
     * Callbacks for this fragment.
     */
    private WeakReference<CaptureFragment.ICallbacks> mCallbacks = null;

    /**
     * Id of the selected camera.
     */
    private int mCameraId = INVALID_CAMERA_ID;

    /**
     * The selected camera.
     */
    private Camera mCamera = null;

    /**
     * The preview display orientation.
     */
    private int mPreviewDisplayOrientation = CameraHelper.CAMERA_SCREEN_ORIENTATION_0;

    /**
     * Flag to indicate whether the camera image is reflected.
     */
    private boolean mIsReflected = false;

    //
    // Views.
    //

    private CenteredPreview mPreview;

    private Button mStartButton;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mCallbacks = new WeakReference<CaptureFragment.ICallbacks>((CaptureFragment.ICallbacks) activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        /*
         * Inflate views from XML.
         */
        View view = inflater.inflate(R.layout.fragment_capture, container, false);

        mPreview = (CenteredPreview) view.findViewById(R.id.camera_preview);
        mStartButton = (Button) view.findViewById(R.id.capture_button);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Context appContext = getActivity().getApplicationContext();

        /*
         * Select camera from preference.
         */
        // Get from preference.
        PreferencesHelper preferencesHelper = new PreferencesHelper();
        PhotoBoothMode mode = preferencesHelper.getPhotoBoothMode(appContext);
        final boolean isSelfServeMode = PhotoBoothMode.SELF_SERVE.equals(mode);

        int cameraPreference = CameraInfo.CAMERA_FACING_FRONT;
        if (!isSelfServeMode) {
            cameraPreference = CameraInfo.CAMERA_FACING_BACK;
        }

        // Default to first camera available.
        final int numCameras = Camera.getNumberOfCameras();
        if (numCameras > 0) {
            mCameraId = 0;
        }

        // Select preferred camera.
        CameraInfo cameraInfo = new CameraInfo();
        for (int cameraId = 0; cameraId < numCameras; cameraId++) {
            Camera.getCameraInfo(cameraId, cameraInfo);

            // Set flag to indicate whether the camera image is reflected.
            mIsReflected = isCameraImageReflected(cameraInfo);

            // Break on finding the preferred camera.
            if (cameraInfo.facing == cameraPreference) {
                mCameraId = cameraId;
                break;
            }
        }

        /*
         * Functionalize views.
         */
        // Configure start button behaviour.
        mStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCamera != null) {
                    mStartButton.setEnabled(false);

                    if (isSelfServeMode) {
                        // Start auto-focus.
                        mCamera.autoFocus(null);

                        // Start animation. Take picture when count down animation completes.
                        AnimationDrawable countdownAnimation = (AnimationDrawable) mStartButton.getBackground();
                        countdownAnimation.setCallback(new TakePictureAnimationDrawableCallback(countdownAnimation,
                                mStartButton));
                        countdownAnimation.start();
                    } else {
                        // Start auto-focus. Take picture when auto-focus completes.
                        mCamera.autoFocus(new TakePictureAutoFocusCallback());
                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mCameraId != INVALID_CAMERA_ID) {
            try {
                mCamera = Camera.open(mCameraId);

                /*
                 * Configure camera parameters.
                 */
                Parameters params = mCamera.getParameters();

                // Set auto white balance if supported.
                List<String> whiteBalances = params.getSupportedWhiteBalance();
                if (whiteBalances != null) {
                    for (String whiteBalance : whiteBalances) {
                        if (whiteBalance.equals(Camera.Parameters.WHITE_BALANCE_AUTO)) {
                            params.setWhiteBalance(whiteBalance);
                        }
                    }
                }

                // Set auto antibanding if supported.
                List<String> antibandings = params.getSupportedAntibanding();
                if (antibandings != null) {
                    for (String antibanding : antibandings) {
                        if (antibanding.equals(Camera.Parameters.ANTIBANDING_AUTO)) {
                            params.setAntibanding(antibanding);
                        }
                    }
                }

                // Set macro focus mode if supported.
                List<String> focusModes = params.getSupportedFocusModes();
                if (focusModes != null) {
                    for (String focusMode : focusModes) {
                        if (focusMode.equals(Camera.Parameters.FOCUS_MODE_MACRO)) {
                            params.setFocusMode(focusMode);
                        }
                    }
                }

                // Set quality for Jpeg capture.
                params.setJpegQuality(CAPTURED_JPEG_QUALITY);

                // Set optimal size for Jpeg capture.
                Size pictureSize = CameraHelper.getOptimalPictureSize(params.getSupportedPreviewSizes(),
                        params.getSupportedPictureSizes(), ImageHelper.IMAGE_SIZE, ImageHelper.IMAGE_SIZE);
                params.setPictureSize(pictureSize.width, pictureSize.height);

                mCamera.setParameters(params);

                /*
                 * Setup preview.
                 */
                mPreviewDisplayOrientation = CameraHelper.getCameraScreenOrientation(getActivity(), mCameraId);
                mCamera.setDisplayOrientation(mPreviewDisplayOrientation);
                mPreview.setCamera(mCamera, pictureSize.width, pictureSize.height, mPreviewDisplayOrientation);
            } catch (RuntimeException e) {
                // Call to client.
                ICallbacks callbacks = getCallbacks();
                if (callbacks != null) {
                    callbacks.onErrorCameraInUse();
                }
            }
        } else {
            // Call to client.
            ICallbacks callbacks = getCallbacks();
            if (callbacks != null) {
                callbacks.onErrorCameraNone();
            }
        }
    }

    @Override
    public void onPause() {
        if (mCamera != null) {
            mPreview.setCamera(null, 0, 0, CameraHelper.CAMERA_SCREEN_ORIENTATION_0);
            mCamera.release();
            mCamera = null;
        }

        super.onPause();
    }

    //
    // Private inner classes.
    //

    /**
     * Take picture when count down completes.
     */
    private class TakePictureAnimationDrawableCallback extends AnimationDrawableCallback {

        /**
         * Constructor.
         * 
         * @param animationDrawable
         *            the {@link AnimationDrawable}.
         * @param callback
         *            the client's {@link Callback} implementation. This is usually the {@link View} the has the
         *            {@link AnimationDrawable} as background.
         */
        public TakePictureAnimationDrawableCallback(AnimationDrawable animationDrawable, Callback callback) {
            super(animationDrawable, callback);
        }

        @Override
        public void onAnimationComplete() {
            takePicture();
        }
    }

    /**
     * Take picture when focus is ready.
     */
    private class TakePictureAutoFocusCallback implements AutoFocusCallback {

        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            takePicture();
        }
    }

    /**
     * Callback when captured Jpeg is ready.
     */
    private class JpegPictureCallback implements Camera.PictureCallback {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            if (isActivityAlive()) {
                // Call to client.
                ICallbacks callbacks = getCallbacks();
                if (callbacks != null) {
                    callbacks.onPictureTaken(data, mPreviewDisplayOrientation, mIsReflected);
                }
            }
        }
    }

    //
    // Private methods.
    //

    /**
     * Gets the callbacks for this fragment.
     * 
     * @return the callbacks; or null if not set.
     */
    private CaptureFragment.ICallbacks getCallbacks() {
        CaptureFragment.ICallbacks callbacks = null;
        if (mCallbacks != null) {
            callbacks = mCallbacks.get();
        }
        return callbacks;
    }

    /**
     * Checks whether the {@link Activity} is attached and not finishing. This should be used as a validation check in a
     * runnable posted to the ui thread, and the {@link Activity} may be have detached by the time the runnable
     * executes. This method should be called on the ui thread.
     * 
     * @return true if {@link Activity} is still alive; false otherwise.
     */
    private boolean isActivityAlive() {
        Activity activity = getActivity();
        return activity != null && !activity.isFinishing();
    }

    /**
     * Takes picture.
     */
    private void takePicture() {
        if (isActivityAlive() && mCamera != null) {
            try {
                mCamera.takePicture(null, null, new JpegPictureCallback());
            } catch (RuntimeException e) {
                // Call to client.
                ICallbacks callbacks = getCallbacks();
                if (callbacks != null) {
                    callbacks.onErrorCameraCrashed();
                }
            }
        }
    }

    /**
     * Checks whether the camera image is reflected.
     * 
     * @return true if the camera image is reflected; false otherwise.
     */
    private boolean isCameraImageReflected(CameraInfo cameraInfo) {
        return cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT;
    }

    //
    // Public methods.
    //

    /**
     * Creates a new {@link CaptureFragment} instance.
     * 
     * @return the new {@link CaptureFragment} instance.
     */
    public static CaptureFragment newInstance() {
        CaptureFragment fragment = new CaptureFragment();
        return fragment;
    }

    //
    // Interfaces.
    //

    /**
     * Callbacks for this fragment.
     */
    public interface ICallbacks {

        /**
         * A picture is taken.
         * 
         * @param data
         *            the picture data.
         * @param rotation
         *            clockwise rotation applied to image in degrees.
         * @param reflection
         *            horizontal reflection applied to image.
         */
        public void onPictureTaken(byte[] data, float rotation, boolean reflection);

        /**
         * No camera.
         */
        public void onErrorCameraNone();

        /**
         * Camera in use.
         */
        public void onErrorCameraInUse();

        /**
         * Camera crashed.
         */
        public void onErrorCameraCrashed();
    }
}
