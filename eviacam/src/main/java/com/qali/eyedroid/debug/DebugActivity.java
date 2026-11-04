/*
 * EyeDroid
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.qali.eyedroid.debug;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.qali.eyedroid.R;
import com.qali.eyedroid.a11yservice.TheAccessibilityService;
import com.qali.eyedroid.learning.LearningManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Debug screen: live application log, real runtime diagnostics and the last errors.
 *
 * The log viewer reads the in-process log written by every EyeDroid subsystem. A normal
 * application cannot read the whole system logcat on modern Android, so the refresh
 * button additionally tries to read the EyeDroid lines available to this process and the
 * screen tells which ADB command provides the complete system log.
 */
public class DebugActivity extends Activity {

    /* Preferences for the minimum displayed level */
    private static final int[] LEVELS = {
            0,
            EyeDroidLog.LEVEL_ERROR,
            EyeDroidLog.LEVEL_WARN,
            EyeDroidLog.LEVEL_INFO,
            EyeDroidLog.LEVEL_DEBUG
    };

    /* Maximum number of entries kept by the viewer */
    private static final int MAX_VIEW_ENTRIES = 800;

    /* Maximum number of characters shown at once */
    private static final int MAX_LOG_CHARS = 40000;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<EyeDroidLog.Entry> mEntries = new ArrayList<>();

    private TextView mLogText;
    private ScrollView mLogScroll;
    private TextView mDiagnosticsText;
    private Button mPauseButton;

    private View mLogPanel;
    private View mDiagnosticsPanel;

    private boolean mPaused;
    private int mMinLevel;
    private String mSystemLog = "";

    private final EyeDroidLog.Listener mLogListener = new EyeDroidLog.Listener() {
        @Override
        public void onLogEntry(final EyeDroidLog.Entry entry) {
            mMainHandler.post(new Runnable() {
                @Override
                public void run() {
                    appendEntry(entry);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        setTitle(R.string.debug_title);

        mLogText = (TextView) findViewById(R.id.debug_log_text);
        mLogScroll = (ScrollView) findViewById(R.id.debug_log_scroll);
        mDiagnosticsText = (TextView) findViewById(R.id.debug_diagnostics_text);
        mPauseButton = (Button) findViewById(R.id.debug_log_pause);
        mLogPanel = findViewById(R.id.debug_log_panel);
        mDiagnosticsPanel = findViewById(R.id.debug_diagnostics_panel);

        /* restore what has been logged before the screen was opened */
        mEntries.addAll(EyeDroidLog.snapshot());
        trimEntries();

        setupTabs();
        setupFilters();
        setupButtons();

        rebuildLogView();
        refreshDiagnostics();

        EyeDroidLog.addListener(mLogListener);
        EyeDroidLog.i(EyeDroidLog.TAG_DEBUG, "Debug screen opened");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDiagnostics();
    }

    @Override
    protected void onDestroy() {
        EyeDroidLog.removeListener(mLogListener);
        super.onDestroy();
    }

    /* ------------------------------------------------------------------------- UI */

    private void setupTabs() {
        Button logsTab = (Button) findViewById(R.id.debug_tab_logs);
        Button diagnosticsTab = (Button) findViewById(R.id.debug_tab_diagnostics);

        logsTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(true);
            }
        });

        diagnosticsTab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(false);
            }
        });
    }

    private void showPanel(boolean logs) {
        mLogPanel.setVisibility(logs ? View.VISIBLE : View.GONE);
        mDiagnosticsPanel.setVisibility(logs ? View.GONE : View.VISIBLE);
        if (!logs) refreshDiagnostics();
    }

    private void setupFilters() {
        Spinner spinner = (Spinner) findViewById(R.id.debug_log_filter);

        String[] labels = new String[]{
                getString(R.string.debug_filter_all),
                getString(R.string.debug_filter_error),
                getString(R.string.debug_filter_warning),
                getString(R.string.debug_filter_info),
                getString(R.string.debug_filter_debug)
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mMinLevel = LEVELS[position];
                rebuildLogView();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void setupButtons() {
        mPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPaused = !mPaused;
                mPauseButton.setText(mPaused ? R.string.debug_resume : R.string.debug_pause);
                if (!mPaused) rebuildLogView();
            }
        });

        findViewById(R.id.debug_log_clear).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mEntries.clear();
                mSystemLog = "";
                EyeDroidLog.clear();
                rebuildLogView();
            }
        });

        findViewById(R.id.debug_log_share).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareLogs();
            }
        });

        findViewById(R.id.debug_log_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshSystemLog();
            }
        });

        findViewById(R.id.debug_diagnostics_refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshDiagnostics();
            }
        });

        findViewById(R.id.debug_retry_engine).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                retryEngine();
            }
        });
    }

    /* -------------------------------------------------------------------- the log */

    private void appendEntry(EyeDroidLog.Entry entry) {
        mEntries.add(entry);
        trimEntries();

        if (mPaused || mLogPanel.getVisibility() != View.VISIBLE) return;
        if (entry.level < mMinLevel) return;

        mLogText.append(entry.toLine());
        mLogText.append("\n");

        if (mLogText.length() > MAX_LOG_CHARS) {
            rebuildLogView();
        }
        else {
            scrollLogToBottom();
        }
    }

    private void trimEntries() {
        while (mEntries.size() > MAX_VIEW_ENTRIES) mEntries.remove(0);
    }

    private void rebuildLogView() {
        final StringBuilder sb = new StringBuilder();

        for (EyeDroidLog.Entry entry : mEntries) {
            if (entry.level < mMinLevel) continue;
            sb.append(entry.toLine()).append('\n');
        }

        if (mMinLevel <= EyeDroidLog.LEVEL_INFO && mSystemLog.length() > 0) {
            sb.append('\n').append(mSystemLog);
        }

        if (sb.length() == 0) {
            sb.append(getString(R.string.debug_no_logs)).append('\n');
        }

        if (sb.length() > MAX_LOG_CHARS) {
            sb.delete(0, sb.length() - MAX_LOG_CHARS);
        }

        mLogText.setText(sb.toString());
        scrollLogToBottom();
    }

    private void scrollLogToBottom() {
        mLogScroll.post(new Runnable() {
            @Override
            public void run() {
                mLogScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void shareLogs() {
        StringBuilder sb = new StringBuilder();
        sb.append(Diagnostics.get().snapshot(this));
        sb.append("\n\n--- LOG ---\n");

        for (EyeDroidLog.Entry entry : mEntries) {
            if (entry.level < mMinLevel) continue;
            sb.append(entry.toLine()).append('\n');
        }

        if (mSystemLog.length() > 0) {
            sb.append("\n--- SYSTEM LOGCAT (EyeDroid lines only) ---\n");
            sb.append(mSystemLog);
        }

        sb.append('\n').append(getString(R.string.debug_adb_help)).append('\n');

        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.debug_share_title));
        intent.putExtra(Intent.EXTRA_TEXT, sb.toString());

        try {
            startActivity(Intent.createChooser(intent, getString(R.string.debug_share)));
        }
        catch (Throwable error) {
            Toast.makeText(this, error.toString(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Read the EyeDroid lines the process is allowed to see. On Android 4.1+ a normal
     * application only gets its own log records, which is exactly what is needed here.
     */
    private void refreshSystemLog() {
        Toast.makeText(this, R.string.debug_refresh, Toast.LENGTH_SHORT).show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final String log = readSystemLogcat();
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSystemLog = log;
                        rebuildLogView();
                    }
                });
            }
        }, "EyeDroid-Logcat").start();
    }

    private static String readSystemLogcat() {
        final StringBuilder sb = new StringBuilder();
        Process process = null;

        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"logcat", "-d", "-v", "time", "-t", "500"});

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            int kept = 0;

            while ((line = reader.readLine()) != null && kept < 500) {
                if (!line.contains("EyeDroid")) continue;
                sb.append(line).append('\n');
                kept++;
            }

            reader.close();
        }
        catch (Throwable error) {
            sb.append("Cannot read the system logcat: ").append(error).append('\n');
        }
        finally {
            if (process != null) {
                try {
                    process.destroy();
                }
                catch (Throwable ignored) { /* nothing to do */ }
            }
        }

        if (sb.length() == 0) {
            sb.append("No EyeDroid lines were available from the system log.\n");
        }

        return sb.toString();
    }

    /* --------------------------------------------------------------- diagnostics */

    private void refreshDiagnostics() {
        final StringBuilder sb = new StringBuilder();

        sb.append(Diagnostics.get().snapshot(this));

        sb.append("\nLEARNING STORAGE\n");
        sb.append("  Learned screens: ").append(LearningManager.get().getLearnedScreenCount())
                .append('\n');
        sb.append("  Recorded interactions: ")
                .append(LearningManager.get().getRecordedInteractionCount()).append('\n');

        sb.append("\nADB\n  ").append(getString(R.string.debug_adb_help)).append('\n');
        sb.append("\n").append(getString(R.string.debug_retry_hint)).append('\n');

        mDiagnosticsText.setText(sb.toString());
    }

    private void retryEngine() {
        TheAccessibilityService service = TheAccessibilityService.get();
        if (service == null) {
            Toast.makeText(this, R.string.debug_retry_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        int result = service.retryEngine();
        if (result == TheAccessibilityService.RETRY_STARTED) {
            Toast.makeText(this, R.string.debug_retry_started, Toast.LENGTH_SHORT).show();
        }
        else if (result == TheAccessibilityService.RETRY_RATE_LIMITED) {
            Toast.makeText(this, R.string.debug_retry_limited, Toast.LENGTH_SHORT).show();
        }
        else {
            Toast.makeText(this, R.string.debug_retry_unavailable, Toast.LENGTH_SHORT).show();
        }

        refreshDiagnostics();
    }
}
