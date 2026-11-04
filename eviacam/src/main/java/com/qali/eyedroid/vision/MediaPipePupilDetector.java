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
import com.qali.eyedroid.debug.Diagnostics;
import com.qali.eyedroid.debug.EyeDroidLog;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Category;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker;
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult;

import java.util.List;
import java.util.Optional;

/**
 * Face detector based on the MediaPipe face landmarker
 *
 * The face mesh contains five landmarks for each iris (indices 468-472 for one eye
 * and 473-477 for the other one). Their centroid is used as the pupil position.
 * Eyelid closure is taken from the "eyeBlinkLeft" and "eyeBlinkRight" blendshapes,
 * which MediaPipe defines relative to the subject's face (not relative to the image),
 * so the frame is deliberately NOT mirrored here.
 *
 * The head direction (yaw and pitch) is computed from the face mesh: the position of
 * the nose tip relative to the middle of the face, normalized by the size of the face.
 * This replaces the old OpenCV based optical flow pipeline, which required native
 * libraries and was the reason the pointer did not work when they were missing.
 *
 * The detector runs synchronously on the camera analysis thread and is not thread safe.
 */
public class MediaPipePupilDetector implements PupilDetector {

    /* asset with the face landmarker model */
    private static final String MODEL_ASSET= "face_landmarker.task";

    /* iris landmarks */
    private static final int[] IRIS_LEFT= {468, 469, 470, 471, 472};
    private static final int[] IRIS_RIGHT= {473, 474, 475, 476, 477};

    /* minimum number of landmarks required to have iris information */
    private static final int MIN_LANDMARKS= 478;

    /* face mesh landmarks used to estimate the head direction */
    private static final int NOSE_TIP= 1;
    private static final int FACE_LEFT= 234;
    private static final int FACE_RIGHT= 454;
    private static final int FOREHEAD= 10;
    private static final int CHIN= 152;

    /* eyelid landmarks used when blendshapes are not available */
    private static final int LEFT_EYE_TOP= 159, LEFT_EYE_BOTTOM= 145;
    private static final int LEFT_EYE_CORNER_A= 33, LEFT_EYE_CORNER_B= 133;
    private static final int RIGHT_EYE_TOP= 386, RIGHT_EYE_BOTTOM= 374;
    private static final int RIGHT_EYE_CORNER_A= 362, RIGHT_EYE_CORNER_B= 263;

    /* eyelid opening (height / width) considered as fully open */
    private static final float EYE_OPEN_RATIO= 0.25f;

    private FaceLandmarker mLandmarker;

    /* number of failed detections, used to rate limit the diagnostics */
    private int mDetectFailures= 0;

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
            if (BuildConfig.DEBUG) Log.d(EVIACAM.TAG, "MediaPipe face detector initialized");
            Diagnostics.get().setMediaPipeReady(true);
            EyeDroidLog.i(EyeDroidLog.TAG_MEDIAPIPE, "MediaPipe face landmarker initialized");
            return true;
        }
        catch (Throwable e) {
            Diagnostics.get().setMediaPipeReady(false);
            Diagnostics.get().setLastMediaPipeFailure(e.toString());
            EyeDroidLog.e(EyeDroidLog.TAG_MEDIAPIPE,
                    "MediaPipe face detector could not be initialized", e);
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
    public PupilDetection detect(@NonNull Bitmap frame, int rotationDegrees) {
        final FaceLandmarker landmarker= mLandmarker;
        if (landmarker == null || frame.isRecycled()) return null;

        Bitmap upright= null;

        try {
            upright= rotate(frame, rotationDegrees);
            MPImage image= new BitmapImageBuilder(upright).build();

            FaceLandmarkerResult result= landmarker.detect(image);

            PupilDetection detection= extract(result, System.currentTimeMillis());
            if (detection != null) Diagnostics.get().noteMediaPipeDetection();

            return detection;
        }
        catch (Throwable e) {
            /*
             * A failure per frame would flood the log, so only the first one and then
             * every hundredth is reported. The debug screen always shows the last one.
             */
            mDetectFailures++;
            if (mDetectFailures == 1 || mDetectFailures % 100 == 0) {
                Diagnostics.get().setLastMediaPipeFailure(e.toString());
                EyeDroidLog.e(EyeDroidLog.TAG_MEDIAPIPE, "Face detection failed ("
                        + mDetectFailures + " times)", e);
            }
            return null;
        }
        finally {
            if (upright != null && upright != frame && !upright.isRecycled()) {
                upright.recycle();
            }
        }
    }

    @NonNull
    private static Bitmap rotate(@NonNull Bitmap src, int rotationDegrees) {
        final int rotation= ((rotationDegrees % 360) + 360) % 360;
        if (rotation == 0) return src;

        Matrix m= new Matrix();
        m.postRotate(rotation);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
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

        float yaw= headYaw(landmarks);
        float pitch= headPitch(landmarks);

        return new PupilDetection(pupilX, pupilY, clampUnit(blinkLeft), clampUnit(blinkRight),
                yaw, pitch, timestamp);
    }

    /**
     * Horizontal direction of the head: the nose tip relative to the middle of the
     * cheeks, normalized by the distance between the cheeks.
     */
    private static float headYaw(@NonNull List<NormalizedLandmark> lm) {
        final float width= Math.abs(lm.get(FACE_RIGHT).x() - lm.get(FACE_LEFT).x());
        if (width < 1e-4f) return 0f;

        final float middle= (lm.get(FACE_LEFT).x() + lm.get(FACE_RIGHT).x()) * 0.5f;
        return (lm.get(NOSE_TIP).x() - middle) / width;
    }

    /**
     * Vertical direction of the head: the nose tip relative to the middle of the face
     * (forehead to chin), normalized by the height of the face.
     */
    private static float headPitch(@NonNull List<NormalizedLandmark> lm) {
        final float height= Math.abs(lm.get(CHIN).y() - lm.get(FOREHEAD).y());
        if (height < 1e-4f) return 0f;

        final float middle= (lm.get(FOREHEAD).y() + lm.get(CHIN).y()) * 0.5f;
        return (lm.get(NOSE_TIP).y() - middle) / height;
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
                EyeDroidLog.w(EyeDroidLog.TAG_MEDIAPIPE,
                        "Error closing the MediaPipe face landmarker", e);
            }
            mLandmarker= null;
        }

        Diagnostics.get().setMediaPipeReady(false);
    }
}
