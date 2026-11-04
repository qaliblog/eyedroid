/*
 * Enable Viacam for Android, a camera based mouse emulator
 *
 * Copyright (C) 2015-17 Cesar Mauri Loba (CREA Software Systems)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.qali.eyedroid.camera;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import androidx.annotation.NonNull;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.qali.eyedroid.R;
import com.qali.eyedroid.common.EVIACAM;
import com.qali.eyedroid.common.Preferences;
import com.qali.eyedroid.debug.Diagnostics;
import com.qali.eyedroid.debug.EyeDroidLog;
import com.qali.eyedroid.util.FlipDirection;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Camera1 based capture which provides plain {@link Bitmap} frames to the frame
 * processor. No OpenCV (nor any other native library) is needed: the luminance
 * plane of the preview frame is copied into a bitmap which MediaPipe can consume.
 *
 * Capture works on its own thread: the camera callback only converts the frame,
 * while the actual image analysis runs on a single worker thread. When the worker
 * is still busy the next frames are dropped.
 */
@SuppressWarnings("deprecation")
class CameraListener implements Camera.PreviewCallback, SurfaceHolder.Callback {
    private static final String TAG= "CameraListener";

    private final Context mContext;
    private final FrameProcessor mFrameProcessor;

    // surface on which the camera image is displayed
    private final SurfaceView mCameraView;

    // physical mounting rotation of the camera (whether the frame needs a flip)
    private final FlipDirection mCameraFlip;

    // physical orientation of the camera (0, 90, 180, 270)
    private final int mCameraOrientation;

    // rotation to apply to the preview so that it is shown upright
    private final int mDisplayOrientation;

    // desired capture size
    private final int mDesiredWidth;
    private final int mDesiredHeight;

    // preferences where the capture size and the frame rate are stored
    private final SharedPreferences mPreferences;

    /*
     * Changing the capture size or the frame rate requires reopening the camera. The
     * work is posted to the view thread, where the preview lives.
     */
    private final SharedPreferences.OnSharedPreferenceChangeListener mSettingsListener=
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                    if (Preferences.KEY_CAPTURE_FPS.equals(key) ||
                            Preferences.KEY_CAPTURE_RESOLUTION.equals(key)) {
                        restartPreview();
                    }
                }
            };

    // camera id and capture parameters
    private final int mCameraId;
    private android.hardware.Camera.Size mPreviewSize;

    private Camera mCamera;
    private byte[] mPreviewBuffer;

    /* reusable bitmap/argb buffers handed to the frame processor */
    private Bitmap mFrameBitmap;
    private int[] mArgbBuffer;

    // true while the worker thread is processing the bitmap
    private final AtomicBoolean mFrameBusy= new AtomicBoolean(false);

    // analysis thread (MediaPipe runs here)
    private final ExecutorService mWorker= Executors.newSingleThreadExecutor();

    // whether the caller wants the preview running
    private volatile boolean mWantsPreview= false;

    // whether the preview is currently running
    private volatile boolean mPreviewRunning= false;

    // whether onCameraStarted has been notified for the current run
    private boolean mStartedNotified= false;

    /**
     * Constructor
     *
     * @param c context
     * @param fp object that will receive the camera callbacks
     */
    CameraListener(@NonNull Context c, @NonNull FrameProcessor fp,
                   int desiredCaptureWidth, int desiredCaptureHeight) throws CameraException {
        mContext= c;
        mFrameProcessor= fp;
        mDesiredWidth= desiredCaptureWidth;
        mDesiredHeight= desiredCaptureHeight;

        /*
         * For some devices, notably the Lenovo YT3-X50L, there is only one camera
         * which can be rotated to frame the user's face. Such a camera is reported
         * as facing back, so it needs a vertical flip and a facing front camera is
         * preferred when available.
         */
        final int numCameras= Camera.getNumberOfCameras();
        if (numCameras < 1) {
            Log.e(EVIACAM.TAG, "No cameras available");
            throw new CameraException(CameraException.NO_CAMERAS_AVAILABLE,
                    c.getResources().getString(R.string.service_camera_no_available));
        }

        int bestCamera= 0;
        CameraInfo cameraInfo= new CameraInfo();
        for (int i= 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                bestCamera= i;
                break;
            }
        }

        Camera.getCameraInfo(bestCamera, cameraInfo);
        mCameraId= bestCamera;
        mCameraOrientation= cameraInfo.orientation;

        final boolean isFront= cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT;
        mCameraFlip= isFront ? FlipDirection.NONE : FlipDirection.VERTICAL;
        mDisplayOrientation= computeDisplayOrientation(
                cameraInfo.orientation, isFront, getDisplayRotationDegrees(c));

        Log.i(EVIACAM.TAG, (isFront ? "Front" : "Back") + " camera detected. Orientation: " +
                cameraInfo.orientation);

        mCameraView= new SurfaceView(c);
        mCameraView.getHolder().addCallback(this);

        final Preferences preferences= Preferences.get();
        mPreferences= (preferences != null) ? preferences.getSharedPreferences() : null;
        if (mPreferences != null) {
            mPreferences.registerOnSharedPreferenceChangeListener(mSettingsListener);
        }
    }

    SurfaceView getCameraSurface() {
        return mCameraView;
    }

    FlipDirection getCameraFlip() {
        return mCameraFlip;
    }

    int getCameraOrientation() {
        return mCameraOrientation;
    }

    void startCamera() {
        mWantsPreview= true;
        if (!mPreviewRunning && mCameraView.getHolder().getSurface().isValid()) {
            openCamera();
        }
    }

    void stopCamera() {
        mWantsPreview= false;
        releaseCamera();
    }

    /**
     * Stop the camera and release all the resources (including the analysis thread)
     */
    void cleanup() {
        stopCamera();

        if (mPreferences != null) {
            mPreferences.unregisterOnSharedPreferenceChangeListener(mSettingsListener);
        }

        mWorker.shutdownNow();

        try {
            mWorker.awaitTermination(1, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // do not recycle the bitmap: a native inference still in flight could be
        // reading it. Dropping the reference is enough, the GC will reclaim it.
        mFrameBitmap= null;
        mArgbBuffer= null;
    }

    /* SurfaceHolder.Callback */

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (mWantsPreview) openCamera();
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // preview keeps working across size changes, nothing to do
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        // the surface is gone, the preview cannot continue
        releaseCamera();
    }

    /* Camera.PreviewCallback */

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (data == null || mPreviewSize == null || mArgbBuffer == null || mFrameBitmap == null ||
                !mPreviewRunning) {
            if (camera != null && data != null) camera.addCallbackBuffer(data);
            return;
        }

        if (!mFrameBusy.compareAndSet(false, true)) {
            // analysis thread still busy, drop this frame
            if (camera != null) camera.addCallbackBuffer(data);
            return;
        }

        Diagnostics.get().noteCameraFrame();

        final int width= mPreviewSize.width;
        final int height= mPreviewSize.height;
        final int pixels= width * height;

        if (data.length < pixels) {
            mFrameBusy.set(false);
            if (camera != null) camera.addCallbackBuffer(data);
            return;
        }

        /*
         * The first width*height bytes of a NV21 frame are the luminance plane.
         * MediaPipe only needs a recognizable image, so the gray value is
         * replicated into the three color channels. This is much cheaper than a
         * full YUV to RGB conversion and works well for face landmarks.
         */
        final int[] argb= mArgbBuffer;
        for (int i= 0; i < pixels; i++) {
            final int y= data[i] & 0xff;
            argb[i]= 0xff000000 | (y << 16) | (y << 8) | y;
        }
        mFrameBitmap.setPixels(argb, 0, width, 0, 0, width, height);

        // the camera buffer can be reused immediately, the bitmap holds a copy
        if (camera != null) camera.addCallbackBuffer(data);

        if (mWorker.isShutdown()) {
            mFrameBusy.set(false);
            return;
        }

        mWorker.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mFrameProcessor.processFrame(mFrameBitmap);
                }
                catch (Throwable e) {
                    Log.e(EVIACAM.TAG, "Frame processing failed", e);
                }
                finally {
                    mFrameBusy.set(false);
                }
            }
        });
    }

    /**
     * Open the camera and start the preview
     */
    private void openCamera() {
        if (mCamera != null) return;

        Camera camera= null;
        try {
            camera= Camera.open(mCameraId);
            camera.setDisplayOrientation(mDisplayOrientation);

            final Camera.Parameters params= camera.getParameters();

            /*
             * The capture size and the frame rate are read on every open, so changing
             * them in the settings takes effect when the camera is reopened.
             */
            final int desiredWidth= getDesiredCaptureWidth();
            final int desiredHeight= getDesiredCaptureHeight();
            final int targetFps= getTargetCaptureFps();

            android.hardware.Camera.Size size= choosePreviewSize(
                    params.getSupportedPreviewSizes(), desiredWidth, desiredHeight);
            params.setPreviewSize(size.width, size.height);
            params.setPreviewFormat(ImageFormat.NV21);
            chooseFpsRange(params, targetFps);
            camera.setParameters(params);

            mPreviewSize= size;

            final int bufferSize= size.width * size.height *
                    ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;
            mPreviewBuffer= new byte[bufferSize];
            mArgbBuffer= new int[size.width * size.height];

            if (mFrameBitmap == null || mFrameBitmap.isRecycled() ||
                    mFrameBitmap.getWidth() != size.width ||
                    mFrameBitmap.getHeight() != size.height) {
                mFrameBitmap= Bitmap.createBitmap(size.width, size.height,
                        Bitmap.Config.ARGB_8888);
            }

            camera.addCallbackBuffer(mPreviewBuffer);
            camera.setPreviewCallbackWithBuffer(this);
            camera.setPreviewDisplay(mCameraView.getHolder());

            mCamera= camera;
            camera.startPreview();

            mPreviewRunning= true;
            Diagnostics.get().setCameraRunning(true);
            Diagnostics.get().setCameraInitialized(true);
            EyeDroidLog.i(EyeDroidLog.TAG_CAMERA, "Camera preview started ("
                    + size.width + "x" + size.height + ", target " + targetFps + " fps)");

            if (!mStartedNotified) {
                mStartedNotified= true;
                mFrameProcessor.onCameraStarted();
            }
        }
        catch (IOException | RuntimeException e) {
            Diagnostics.get().setCameraRunning(false);
            Diagnostics.get().setLastCameraFailure("cannot open the camera: " + e);
            EyeDroidLog.e(EyeDroidLog.TAG_CAMERA, "Cannot start camera", e);
            if (camera != null) {
                try {
                    camera.release();
                }
                catch (RuntimeException ignored) { /* nothing to do */ }
            }
            mCamera= null;
            mPreviewRunning= false;
            mFrameProcessor.onCameraError(new CameraException(
                    CameraException.CAMERA_IN_USE,
                    mContext.getResources().getString(R.string.service_camera_error), e));
        }
    }

    /**
     * Stop the preview and release the camera device
     */
    private void releaseCamera() {
        final Camera camera= mCamera;
        final boolean wasRunning= mPreviewRunning;
        mCamera= null;
        mPreviewRunning= false;

        if (wasRunning) {
            Diagnostics.get().setCameraRunning(false);
            EyeDroidLog.i(EyeDroidLog.TAG_CAMERA, "Camera preview stopped");
        }

        if (camera != null) {
            try {
                camera.setPreviewCallbackWithBuffer(null);
            }
            catch (RuntimeException ignored) { /* nothing to do */ }
            try {
                camera.stopPreview();
            }
            catch (RuntimeException ignored) { /* nothing to do */ }
            try {
                camera.release();
            }
            catch (RuntimeException ignored) { /* nothing to do */ }
        }

        if (mStartedNotified) {
            mStartedNotified= false;
            mFrameProcessor.onCameraStopped();
        }
    }

    /**
     * Desired capture width from the settings, falling back to the value given when
     * the camera was created
     */
    private int getDesiredCaptureWidth() {
        final Preferences preferences= Preferences.get();
        return (preferences != null) ? preferences.getCaptureWidth() : mDesiredWidth;
    }

    /**
     * Desired capture height from the settings, falling back to the value given when
     * the camera was created
     */
    private int getDesiredCaptureHeight() {
        final Preferences preferences= Preferences.get();
        return (preferences != null) ? preferences.getCaptureHeight() : mDesiredHeight;
    }

    /**
     * Desired frame rate from the settings, falling back to 15 fps
     */
    private int getTargetCaptureFps() {
        final Preferences preferences= Preferences.get();
        return (preferences != null) ? preferences.getCaptureFps() : 15;
    }

    /**
     * Reopen the camera so that a new capture size or frame rate takes effect
     */
    private void restartPreview() {
        mCameraView.post(new Runnable() {
            @Override
            public void run() {
                if (!mWantsPreview || !mPreviewRunning) return;
                EyeDroidLog.i(EyeDroidLog.TAG_CAMERA,
                        "Capture settings changed, reopening the camera");
                releaseCamera();
                openCamera();
            }
        });
    }

    /**
     * Select the supported preview size closest to the desired capture size,
     * ignoring the tiny sizes that do not provide enough detail for the face mesh.
     */
    private android.hardware.Camera.Size choosePreviewSize(
            List<android.hardware.Camera.Size> sizes, int desiredWidth, int desiredHeight) {
        android.hardware.Camera.Size best= null;
        long bestScore= Long.MAX_VALUE;

        for (android.hardware.Camera.Size size : sizes) {
            if (size.width < 160 || size.height < 120) continue;

            long score= Math.abs(size.width - desiredWidth) +
                    Math.abs(size.height - desiredHeight);

            if (score < bestScore) {
                bestScore= score;
                best= size;
            }
        }

        if (best == null && !sizes.isEmpty()) best= sizes.get(0);

        return best;
    }

    /**
     * Pick the preview rate closest to the configured one. A lower rate leaves more
     * CPU for the face detector, a higher one makes the pointer more responsive.
     */
    private static void chooseFpsRange(Camera.Parameters params, int targetFps) {
        final List<int[]> ranges= params.getSupportedPreviewFpsRange();
        if (ranges == null || ranges.isEmpty()) return;

        final int target= targetFps * 1000;
        int[] best= null;

        for (int[] range : ranges) {
            if (best == null) {
                best= range;
                continue;
            }

            final int bestDistance= Math.abs(best[1] - target);
            final int distance= Math.abs(range[1] - target);

            if (distance < bestDistance ||
                    (distance == bestDistance && range[1] < best[1])) {
                best= range;
            }
        }

        try {
            params.setPreviewFpsRange(best[0], best[1]);
        }
        catch (RuntimeException e) {
            Log.w(EVIACAM.TAG, "Cannot set preview fps range", e);
        }
    }

    /**
     * Rotation (clockwise) that makes the preview upright on screen
     *
     * Uses the formulas from the Camera.setDisplayOrientation documentation. The
     * previous version had them swapped, which rotated the preview 180 degrees on
     * devices whose front camera is mounted at 90 or 270 (the small preview appeared
     * upside down). This only affects the preview surface, not the frames handed to
     * the face detector.
     */
    private static int computeDisplayOrientation(int cameraOrientation, boolean front,
                                                 int displayRotationDegrees) {
        if (front) {
            final int result= (cameraOrientation + displayRotationDegrees) % 360;
            return (360 - result) % 360;
        }
        return (cameraOrientation - displayRotationDegrees + 360) % 360;
    }

    private static int getDisplayRotationDegrees(@NonNull Context c) {
        final WindowManager wm= (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null || wm.getDefaultDisplay() == null) return 0;

        switch (wm.getDefaultDisplay().getRotation()) {
            case Surface.ROTATION_90: return 90;
            case Surface.ROTATION_180: return 180;
            case Surface.ROTATION_270: return 270;
            default: return 0;
        }
    }
}
