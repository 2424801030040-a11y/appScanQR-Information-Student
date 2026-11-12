package com.example.studentattendance;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;

/** Animated splash: logo pops in (R.anim.logo_pop), texts fade in, then cross-fades to MainActivity. */
public class SplashScreenActivity extends AppCompatActivity {
    private static final long SPLASH_DELAY_MS = 2200;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // ANIMATION TRIGGER: logo scales up with overshoot as soon as the screen is created
        findViewById(R.id.splash_logo).startAnimation(AnimationUtils.loadAnimation(this, R.anim.logo_pop));
        fadeIn(findViewById(R.id.splash_title), 500);
        fadeIn(findViewById(R.id.splash_tagline), 800);

        handler.postDelayed(() -> {
            startActivity(new Intent(this, MainActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out); // fade transition to dashboard
            finish();
        }, SPLASH_DELAY_MS);
    }

    private void fadeIn(View v, long delay) {
        v.setAlpha(0f);
        v.animate().alpha(1f).setStartDelay(delay).setDuration(600).start();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
