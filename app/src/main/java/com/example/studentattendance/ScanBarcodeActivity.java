package com.example.studentattendance;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.BarcodeView;

import java.util.List;

/**
 * Live scanner (ZXing BarcodeView). A laser line sweeps the viewfinder; every decoded code is matched
 * against SQLite, the student is marked "Có mặt" with the exact time, and a bounce-animated dialog,
 * beep and vibration give feedback.
 */
public class ScanBarcodeActivity extends BaseActivity {

    private static final int SUCCESS = 0, ALREADY = 1, FAILED = 2;

    private BarcodeView barcodeView;
    private View laserLine;
    private boolean busy = false;   // ignore new scans while a dialog is open
    private boolean torchOn = false;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startScanner();
                } else {
                    Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override public void barcodeResult(BarcodeResult result) {
            if (busy || result.getText() == null) return;
            busy = true;
            barcodeView.stopDecoding();
            handleScan(result.getText().trim());
        }
        @Override public void possibleResultPoints(List<ResultPoint> resultPoints) { }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        setupToolbar(findViewById(R.id.toolbar), true);
        setupBottomNav(findViewById(R.id.bottom_nav), R.id.nav_scan);

        barcodeView = findViewById(R.id.barcode_view);
        laserLine = findViewById(R.id.laser_line);

        FloatingActionButton fabTorch = findViewById(R.id.fab_torch);
        popIn(fabTorch);
        fabTorch.setOnClickListener(v -> pressAnimate(v, () -> {
            torchOn = !torchOn;
            barcodeView.setTorch(torchOn);
        }));

        if (!hasCameraPermission()) permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override protected void onResume() {
        super.onResume();
        if (hasCameraPermission()) startScanner();
    }

    @Override protected void onPause() {
        super.onPause();
        barcodeView.pause();
        laserLine.clearAnimation();
    }

    private void startScanner() {
        barcodeView.resume();
        if (!busy) barcodeView.decodeContinuous(callback);
        // ANIMATION TRIGGER: laser line moves up and down while the scanner is active
        laserLine.startAnimation(AnimationUtils.loadAnimation(this, R.anim.laser_scan));
    }

    /** Real-time processing: match -> update DB -> feedback. */
    private void handleScan(String code) {
        Student student = db.findByBarcode(code);
        if (student == null) {
            vibrate(250);
            showResult(FAILED, getString(R.string.scan_unknown_title), getString(R.string.scan_unknown_msg, code));
        } else if (student.isPresent()) {
            vibrate(150);
            showResult(ALREADY, getString(R.string.scan_already_title),
                    getString(R.string.scan_already_msg, student.getName(), student.getTimestamp()));
        } else {
            String time = db.setAttendance(student.getId(), true);
            beep();
            vibrate(80);
            showResult(SUCCESS, student.getName(),
                    getString(R.string.scan_success_msg, student.getCode(), student.getClassName(), time));
        }
    }

    /** Feedback popup with scale/bounce entrance (R.anim.bounce). */
    private void showResult(int type, String title, String message) {
        View view = getLayoutInflater().inflate(R.layout.dialog_scan_result, null);
        ImageView icon = view.findViewById(R.id.result_icon);
        ((TextView) view.findViewById(R.id.result_title)).setText(title);
        ((TextView) view.findViewById(R.id.result_message)).setText(message);

        int color = type == SUCCESS ? R.color.present : type == ALREADY ? R.color.secondary : R.color.absent;
        icon.setImageResource(type == FAILED ? R.drawable.ic_cancel : R.drawable.ic_check_circle);
        icon.setColorFilter(getColor(color), PorterDuff.Mode.SRC_IN);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setPositiveButton(R.string.scan_next, null)
                .setOnDismissListener(d -> resumeScanning())
                .create();
        dialog.show();

        // ANIMATION TRIGGER: whole card bounces in, icon bounces again a moment later
        view.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce));
        icon.postDelayed(() -> icon.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce)), 150);
    }

    private void resumeScanning() {
        busy = false;
        if (!isFinishing() && hasCameraPermission()) barcodeView.decodeContinuous(callback);
    }

    private void beep() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
            new Handler(Looper.getMainLooper()).postDelayed(tone::release, 400);
        } catch (RuntimeException ignored) {
            // some devices cannot create a ToneGenerator; vibration still gives feedback
        }
    }

    private void vibrate(long ms) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }
}
