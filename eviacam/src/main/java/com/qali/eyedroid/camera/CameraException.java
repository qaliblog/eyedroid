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

/**
 * Error raised while opening, starting or stopping the camera
 */
public class CameraException extends Exception {

    /* camera could not be opened/configured for an unspecified reason */
    public static final int CAMERA_ERROR= 0;

    /* camera is being used by another application */
    public static final int CAMERA_IN_USE= 1;

    /* camera is disabled by the device policy */
    public static final int CAMERA_DISABLED= 2;

    /* the device does not provide any usable camera */
    public static final int NO_CAMERAS_AVAILABLE= 3;

    private final int mProblem;

    public CameraException(int problem) {
        this(problem, null);
    }

    public CameraException(int problem, String detailMessage) {
        super(detailMessage);
        mProblem= problem;
    }

    public CameraException(int problem, String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
        mProblem= problem;
    }

    /**
     * @return one of the CAMERA_* constants defined in this class
     */
    public int getProblem() {
        return mProblem;
    }
}
