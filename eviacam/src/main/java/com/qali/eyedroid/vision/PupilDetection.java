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

/**
 * Result of a single face detection
 *
 * The pupil coordinates are normalized to the camera frame (i.e. in the range 0 to 1)
 * and are relative to the image as captured by the camera (not mirrored).
 */
public class PupilDetection {

    /* center of both irises (mean of left and right pupil) */
    public final float x;
    public final float y;

    /* eyelid closure for each eye, from 0 (eye opened) to 1 (eye closed) */
    public final float blinkLeft;
    public final float blinkRight;

    /*
     * Head direction obtained from the face mesh, both normalized by the size of the
     * face: yaw is 0 when the face is frontal and grows when the head turns, pitch is
     * the vertical position of the nose relative to the middle of the face.
     */
    public final float headYaw;
    public final float headPitch;

    /*
     * Center of the face in the frame, normalized to the frame size (0 to 1), and the
     * width of the face normalized by the frame width. The position provides the head
     * translation (moving the head left/right/up/down) and the size provides the
     * distance between the head and the camera.
     */
    public final float faceX;
    public final float faceY;
    public final float faceSize;

    /* time stamp of the detection in milliseconds */
    public final long timestamp;

    public PupilDetection(float x, float y, float blinkLeft, float blinkRight,
                          float headYaw, float headPitch,
                          float faceX, float faceY, float faceSize, long timestamp) {
        this.x= x;
        this.y= y;
        this.blinkLeft= blinkLeft;
        this.blinkRight= blinkRight;
        this.headYaw= headYaw;
        this.headPitch= headPitch;
        this.faceX= faceX;
        this.faceY= faceY;
        this.faceSize= faceSize;
        this.timestamp= timestamp;
    }
}
