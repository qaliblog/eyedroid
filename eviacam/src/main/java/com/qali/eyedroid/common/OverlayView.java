/*
 * Enable Viacam for Android, a camera based mouse emulator
 *
 * Copyright (C) 2015 Cesar Mauri Loba (CREA Software Systems)
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

 package com.qali.eyedroid.common;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import com.qali.eyedroid.debug.Diagnostics;
import com.qali.eyedroid.debug.EyeDroidLog;

/***
 * Root relative layout which is overlaid over the entire screen of the device an to which
 * other views can be added.
 *
 * On modern Android versions a plain application overlay is not enough: the window is
 * drawn using the accessibility overlay type whenever the view is created from the
 * accessibility service. That type is designed exactly for this use case, is shown above
 * every application without requiring SYSTEM_ALERT_WINDOW, and is not affected by the
 * restrictions added to old system overlay types.
 */
public class OverlayView extends RelativeLayout {

    /* whether the window has been added to the window manager */
    private boolean mAttached = false;

    /* why the window could not be added, when that is the case */
    private String mAttachError;

    /* type of the window which was requested */
    private int mWindowType;

    OverlayView(Context c) {
        super(c);

        WindowManager.LayoutParams feedbackParams = new WindowManager.LayoutParams();

        feedbackParams.setTitle("EyeDroidOverlay");

        // Transparent background
        feedbackParams.format = PixelFormat.TRANSLUCENT;

        /*
         * Type of window
         *
         * TYPE_ACCESSIBILITY_OVERLAY (API 22+): overlay created by an accessibility
         *      service. Shown on top of everything, no SYSTEM_ALERT_WINDOW permission
         *      required and the recommended way to draw the pointer.
         *
         * TYPE_APPLICATION_OVERLAY (API 26+): overlay of a regular service, used by
         *      slave mode.
         *
         * TYPE_PHONE: fallback for old versions.
         *
         * The pointer and the docking panel are drawn only: the whole input of the user
         * is dispatched through the accessibility service, so the window must never
         * consume touches.
         */
        feedbackParams.type = resolveWindowType(c);
        mWindowType = feedbackParams.type;

        /*
         * FLAG_NOT_FOCUSABLE: this window won't ever get key input focus, so the user can
         *      not send key or other button events to it. It can use the full screen for
         *      its content and cover the input method if needed
         *
         * FLAG_LAYOUT_IN_SCREEN: place the window within the entire screen, ignoring
         *      decorations around the border (such as the status bar)
         *
         * FLAG_NOT_TOUCHABLE: the overlay only draws, it must not steal the touches of
         *      the application below
         */
        feedbackParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;

        feedbackParams.width = WindowManager.LayoutParams.MATCH_PARENT;
        feedbackParams.height = WindowManager.LayoutParams.MATCH_PARENT;

        WindowManager wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);

        if (wm == null) {
            mAttachError = "no window manager";
            EyeDroidLog.e(EyeDroidLog.TAG_OVERLAY, "Cannot attach overlay: no window manager");
        }
        else {
            try {
                wm.addView(this, feedbackParams);
                mAttached = true;
                EyeDroidLog.i(EyeDroidLog.TAG_OVERLAY,
                        "Overlay attached using " + windowTypeName(feedbackParams.type));
            }
            catch (WindowManager.BadTokenException | SecurityException |
                   IllegalArgumentException | IllegalStateException error) {
                /*
                 * The overlay could not be added (permission revoked, service token not
                 * valid anymore, ...). Keep the reason so that it is visible in the
                 * debug screen instead of silently leaving the user without a cursor.
                 */
                mAttached = false;
                mAttachError = error.getClass().getSimpleName() + ": " + error.getMessage();
                Diagnostics.get().setOverlayAttachError(mAttachError);
                EyeDroidLog.e(EyeDroidLog.TAG_OVERLAY, "Cannot attach overlay window", error);
            }
        }

        Diagnostics.get().setOverlayAttached(mAttached, mWindowType);
    }

    private static int resolveWindowType(Context c) {
        if (c instanceof AccessibilityService) {
            return WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        }

        return WindowManager.LayoutParams.TYPE_PHONE;
    }

    private static String windowTypeName(int type) {
        if (type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY) {
            return "TYPE_ACCESSIBILITY_OVERLAY";
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
            return "TYPE_APPLICATION_OVERLAY";
        }
        if (type == WindowManager.LayoutParams.TYPE_PHONE) return "TYPE_PHONE";
        if (type == WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY) return "TYPE_SYSTEM_OVERLAY";
        return String.valueOf(type);
    }

    /**
     * @return true when the overlay window is attached to the window manager
     */
    public boolean isAttached() {
        return mAttached;
    }

    /**
     * @return why the overlay could not be attached, or null
     */
    public String getAttachError() {
        return mAttachError;
    }

    public int getWindowType() {
        return mWindowType;
    }

    /**
     * Show or hide the overlay and keep the diagnostics in sync.
     */
    public void setOverlayVisible(boolean visible) {
        setVisibility(visible ? View.VISIBLE : View.INVISIBLE);

        final boolean shown = visible && mAttached;
        Diagnostics.get().setOverlayVisible(shown);

        EyeDroidLog.d(EyeDroidLog.TAG_OVERLAY, shown ? "Overlay visible" : "Overlay hidden");
    }

    void cleanup() {
        if (!mAttached) {
            Diagnostics.get().setOverlayAttached(false, mWindowType);
            return;
        }

        WindowManager wm = (WindowManager) this.getContext()
                .getSystemService(Context.WINDOW_SERVICE);

        if (wm != null) {
            try {
                wm.removeViewImmediate(this);
            }
            catch (Throwable error) {
                /* the window can already be gone (e.g. the service is being torn down) */
                EyeDroidLog.w(EyeDroidLog.TAG_OVERLAY, "Cannot remove the overlay window", error);
            }
        }

        mAttached = false;
        Diagnostics.get().setOverlayAttached(false, mWindowType);

        EyeDroidLog.i(EyeDroidLog.TAG_OVERLAY, "Overlay removed");
    }

    public void addFullScreenLayer (View v) {
        RelativeLayout.LayoutParams lp= new RelativeLayout.LayoutParams(this.getWidth(), this.getHeight());
        lp.width= RelativeLayout.LayoutParams.MATCH_PARENT;
        lp.height= RelativeLayout.LayoutParams.MATCH_PARENT;
        
        v.setLayoutParams(lp);
        this.addView(v);
    }
}
