package com.edupulse.teacher;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;

public class EduPulseApp extends Application {

    private static final String PREFS_CRASH = "edupulse_crash";
    private static final String KEY_CRASH_LOG = "last_crash";

    @Override
    public void onCreate() {
        super.onCreate();

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                throwable.printStackTrace(pw);
                String stackTrace = sw.toString();

                SharedPreferences prefs = getSharedPreferences(PREFS_CRASH, MODE_PRIVATE);
                prefs.edit().putString(KEY_CRASH_LOG, stackTrace).apply();
                Log.e("EduPulse", "Crash: " + stackTrace);
            } catch (Exception ignored) {}

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    public static String getLastCrash(SharedPreferences prefs) {
        return prefs.getString(KEY_CRASH_LOG, "");
    }

    public static void clearCrash(SharedPreferences prefs) {
        prefs.edit().remove(KEY_CRASH_LOG).apply();
    }
}
