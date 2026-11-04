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
package com.qali.eyedroid.vision;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import com.qali.eyedroid.BuildConfig;
import com.qali.eyedroid.common.EVIACAM;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.util.List;
import java.util.Optional;

/**
 * Pupil detector based on the MediaPipe face landmarker
 *
 * The face mesh of the face landmarker contains five landmarks for each iris
 * (indices 468-472 for one eye and 473-477 for the other one). Their centroid is
 * used as the pupil position. Eyelid closure is taken from the "eyeBlinkLeft" and
 * "eyeBlinkRight" blendshapes, which MediaPipe defines relative to the subject's
 * face (not relative to the image), so the frame is deliberately NOT mirrored here.
 *
 * Detections are throttled to limit CPU usage. The detector must only be used from
 * a single thread (the camera frame processing thread).
 */
public class MediaPipePupilDetector implements PupilDetector {

    /* asset with the face landmarker model */
    private static final String MODEL_ASSET= "face_landmarker.task";

    /* minimum time between two consecutive detections (milliseconds) */
    private static final long DETECTION_INTERVAL_MS= 100;

    /* iris landmarks */
    private static final int[] IRIS_LEFT= {468, 469, 470, 471, 472};
    private static final int[] IRIS_RIGHT= {473, 474, 475, 476, 477};

    /* minimum number of landmarks required to have iris information */
    private static final int MIN_LANDMARKS= 478;

    /* eyelid landmarks used when blendshapes are not available */
    private static final int LEFT_EYE_TOP= 159, LEFT_EYE_BOTTOM= 145;
    private static final int LEFT_EYE_CORNER_A= 33, LEFT_EYE_CORNER_B= 133;
    private static final int RIGHT_EYE_TOP= 386, RIGHT_EYE_BOTTOM= 374;
    private static final int RIGHT_EYE_CORNER_A= 362, RIGHT_EYE_CORNER_B= 263;

    /* eyelid opening (height / width) considered as fully open */
    private static final float EYE_OPEN_RATIO= 0.25f;

    private FaceLandmarker mLandmarker;

    /* reusable frame to avoid allocating a bitmap for each detection */
    private Bitmap mFrameBitmap;

    private long mLastDetectionTimeStamp= 0;

    @Override
    public boolean initialize(@NonNull Context context) {
        if (mLandmarker != null) return true;

        try {
            BaseOptions baseOptions= BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(Delegate.CPU)
                    .build();

            FaceLandmarker.FaceLandmarkerOptions options=
                    FaceLandmarker.FaceLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.IMAGE)
                            .setNumFaces(1)
                            .setOutputFaceBlendshapes(true)
                            .build();

            mLandmarker= FaceLandmarker.createFromOptions(context, options);
            if (BuildConfig.DEBUG) Log.d(EVIACAM.TAG, "MediaPipe pupil detector initialized");
            return true;
        }
        catch (Throwable e) {
            Log.e(EVIACAM.TAG, "MediaPipe pupil detector could not be initialized", e);
            mLandmarker= null;
            return false;
        }
    }

    @Override
    public boolean isReady() {
        return mLandmarker != null;
    }

    @Override
    @Nullable
    public PupilDetection detect(@NonNull Mat rgba, int rotationDegrees) {
        final FaceLandmarker landmarker= mLandmarker;
        if (landmarker == null) return null;
        if (rgba.empty() || rgba.width() <= 0 || rgba.height() <= 0) return null;

        final long now= System.currentTimeMillis();
        if (now - mLastDetectionTimeStamp < DETECTION_INTERVAL_MS) return null;
        mLastDetectionTimeStamp= now;

        try {
            Bitmap frame= getFrameBitmap(rgba.width(), rgba.height());
            Utils.matToBitmap(rgba, frame);

            MPImage image= buildMpImage(frame, rotationDegrees);
            if (image == null) return null;

            FaceLandmarkerResult result= landmarker.detect(image);
            return extract(result, now);
        }
        catch (Throwable e) {
            Log.e(EVIACAM.TAG, "MediaPipe pupil detection failed", e);
            return null;
        }
    }

    @NonNull
    private Bitmap getFrameBitmap(int width, int height) {
        Bitmap bmp= mFrameBitmap;
        if (bmp == null || bmp.getWidth() != width || bmp.getHeight() != height) {
            bmp= Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            mFrameBitmap= bmp;
        }
        return bmp;
    }

    @Nullable
    private static MPImage buildMpImage(@NonNull Bitmap src, int rotationDegrees) {
        Bitmap bmp= src;
        int rotation= ((rotationDegrees % 360) + 360) % 360;
        if (rotation != 0) {
            Matrix m= new Matrix();
            m.postRotate(rotation);
            bmp= Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        }
        return new BitmapImageBuilder(bmp).build();
    }

    @Nullable
    private static PupilDetection extract(@Nullable FaceLandmarkerResult result, long timestamp) {
        if (result == null) return null;

        List<List<NormalizedLandmark>> faces= result.faceLandmarks();
        if (faces == null || faces.isEmpty()) return null;

        List<NormalizedLandmark> landmarks= faces.get(0);
        if (landmarks == null || landmarks.size() < MIN_LANDMARKS) return null;

        float pupilX= (centroidX(landmarks, IRIS_LEFT) + centroidX(landmarks, IRIS_RIGHT)) * 0.5f;
        float pupilY= (centroidY(landmarks, IRIS_LEFT) + centroidY(landmarks, IRIS_RIGHT)) * 0.5f;

        List<List<Category>> blendshapes= null;
        Optional<List<List<Category>>> optShapes= result.faceBlendshapes();
        if (optShapes != null && optShapes.isPresent()) blendshapes= optShapes.get();

        float blinkLeft= findScore(blendshapes, "eyeBlinkLeft");
        float blinkRight= findScore(blendshapes, "eyeBlinkRight");

        if (blinkLeft < 0f) {
            blinkLeft= eyelidClosure(landmarks,
                    LEFT_EYE_TOP, LEFT_EYE_BOTTOM, LEFT_EYE_CORNER_A, LEFT_EYE_CORNER_B);
        }
        if (blinkRight < 0f) {
            blinkRight= eyelidClosure(landmarks,
                    RIGHT_EYE_TOP, RIGHT_EYE_BOTTOM, RIGHT_EYE_CORNER_A, RIGHT_EYE_CORNER_B);
        }

        return new PupilDetection(pupilX, pupilY, clampUnit(blinkLeft), clampUnit(blinkRight),
                timestamp);
    }

    private static float centroidX(@NonNull List<NormalizedLandmark> lm, @NonNull int[] idx) {
        float sum= 0f;
        for (int i : idx) sum+= lm.get(i).x();
        return sum / idx.length;
    }

    private static float centroidY(@NonNull List<NormalizedLandmark> lm, @NonNull int[] idx) {
        float sum= 0f;
        for (int i : idx) sum+= lm.get(i).y();
        return sum / idx.length;
    }

    /**
     * Get the score of a blendshape
     *
     * @return score in the range 0 to 1 or -1 when the blendshape is not available
     */
    private static float findScore(@Nullable List<List<Category>> blendshapes,
                                   @NonNull String name) {
        if (blendshapes == null || blendshapes.isEmpty()) return -1f;

        List<Category> categories= blendshapes.get(0);
        if (categories == null) return -1f;

        for (Category c : categories) {
            if (name.equals(c.categoryName())) return c.score();
        }
        return -1f;
    }

    /**
     * Estimate eyelid closure from the distance between the eyelids
     *
     * Fallback used when MediaPipe does not provide blendshapes.
     */
    private static float eyelidClosure(@NonNull List<NormalizedLandmark> lm,
                                       int top, int bottom, int cornerA, int cornerB) {
        float height= Math.abs(lm.get(top).y() - lm.get(bottom).y());
        float width= Math.abs(lm.get(cornerA).x() - lm.get(cornerB).x());
        if (width <= 0f) return 0f;

        float ratio= height / width;
        float closure= 1.0f - (ratio / EYE_OPEN_RATIO);
        return clampUnit(closure);
    }

    private static float clampUnit(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    @Override
    public void cleanup() {
        if (mLandmarker != null) {
            try {
                mLandmarker.close();
            }
            catch (Exception e) {
                Log.w(EVIACAM.TAG, "Error closing MediaPipe face landmarker", e);
            }
            mLandmarker= null;
        }

        if (mFrameBitmap != null) {
            mFrameBitmap.recycle();
            mFrameBitmap= null;
        }
    }
}
