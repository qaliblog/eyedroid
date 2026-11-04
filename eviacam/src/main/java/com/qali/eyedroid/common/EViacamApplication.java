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

import android.app.Application;
import android.os.Process;
import android.util.Log;

import com.qali.eyedroid.learning.LearningManager;

/**
 * Customized application class
 */
public class EViacamApplication extends Application {

    public void onCreate() {
        super.onCreate();

        Log.i(EVIACAM.TAG, "Application EVA Facial Mouse started");

        if (EVIACAM.isACRAProcess()) {
            /* ACRA crash report process. Nothing else to do */
            Log.i(EVIACAM.TAG, "ACRA crash report process");
            return;
        }

        /*
         * Regular application start up. Register the uncaught exception handler, which
         * filters some exceptions before letting the default handler run.
         */
        UncaughtExceptionHandler.init(this);

        if (EVIACAM.isSoftkeyboardProcess()) {
            /* Softkeyboard started. Nothing else to do */
            Log.i(EVIACAM.TAG, "Softkeyboard process");
            return;
        }

        /*
         * EVA service regular start up (main process)
         */

        Analytics.init(this);

        /*
         * Keep an application context for the learning subsystem so that the settings
         * and debug screens can report what has been learned even while the
         * accessibility service is not connected.
         */
        LearningManager.get().attach(this);

        // Raise priority to improve responsiveness
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY);
    }
}
