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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.SurfaceView;

import com.qali.eyedroid.util.FlipDirection;

/**
 * Camera interface class
 *
 * Frames are captured with the platform camera API (no OpenCV) and delivered as
 * bitmaps, so the only image processing dependency is the MediaPipe face landmarker.
 */
public class Camera {
    private static final String TAG= "camera.Camera";

    /* Desired capture size */
    static final int DESIRED_CAPTURE_WIDTH = 352;
    static final int DESIRED_CAPTURE_HEIGHT = 288;

    private final CameraListener mCamera;

    /**
     * Constructs and detects the best camera available.
     *
     * @param c context
     * @param fp frame processor listener
     * @throws CameraException when error
     */
    public Camera (@NonNull Context c, @NonNull FrameProcessor fp) throws CameraException {
        mCamera = new CameraListener(c, fp, DESIRED_CAPTURE_WIDTH, DESIRED_CAPTURE_HEIGHT);
    }

    /**
     * Cleanup
     */
    public void cleanup() {
        mCamera.cleanup();
        Log.d(TAG, "cleanup: completed");
    }

    /**
     * Return whether the frame needs a flip operation. For instance, this is needed for those
     * devices with rotating camera such as the Lenovo YT3-X50L which are reported as BACK camera.
     *
     * @return NONE, HORIZONTAL or VERTICAL
     */
    public @NonNull FlipDirection getCameraFlip() {
        return mCamera.getCameraFlip();
    }

    /**
     * Get the physical orientation of the camera
     * @return 0, 90, 180 or 270
     */
    public int getCameraOrientation() {
        return mCamera.getCameraOrientation();
    }

    /**
     * Start camera capture
     *
     * Once started, the client is notified using the onCameraStarted callback. When
     * error, the onCameraError is called.
     */
    public void startCamera() {
        mCamera.startCamera();
    }

    /**
     * Stop the camera capture.
     */
    public void stopCamera() {
        mCamera.stopCamera();
    }

    /**
     * Return surface view on which camera image will be drawn
     *
     * @return the surface view or null
     */
    public @Nullable SurfaceView getCameraSurface() {
        return mCamera.getCameraSurface();
    }
}
