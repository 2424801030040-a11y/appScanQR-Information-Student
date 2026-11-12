package com.example.studentattendance;

import android.app.Application;

/** Applies the saved light/dark preference before any screen is drawn (no flash on start-up). */
public class AttendanceApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        BaseActivity.applySavedTheme(this);
    }
}
