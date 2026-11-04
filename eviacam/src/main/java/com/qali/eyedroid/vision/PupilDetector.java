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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Detects the pupils, the eyelid closure of each eye and the head direction
 *
 * Implementations are not required to run a detection for each call. They should
 * instead decide, according to their own time budget, whether it is time to perform
 * a new detection. In that case they return null.
 */
public interface PupilDetector {

    /**
     * Load the required resources
     *
     * @param context a valid android context
     * @return true if the detector is ready to be used
     */
    boolean initialize(@NonNull Context context);

    /**
     * @return true when the detector has been successfully initialized
     */
    boolean isReady();

    /**
     * Detect the face on the given frame
     *
     * NOTE: this method is blocking and must be called from a secondary thread
     *
     * @param frame camera frame
     * @param rotationDegrees rotation that needs to be applied to the frame in order
     *                        to get an upright face
     * @return the detection or null if there is no face (or no new detection available)
     */
    @Nullable
    PupilDetection detect(@NonNull Bitmap frame, int rotationDegrees);

    /**
     * Release all the resources used by the detector
     */
    void cleanup();
}
