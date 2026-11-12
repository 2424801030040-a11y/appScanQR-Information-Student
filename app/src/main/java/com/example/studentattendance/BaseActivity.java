package com.example.studentattendance;

import android.animation.ValueAnimator;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shared behaviour: Options Menu quick tools (export, reset, theme, app info, filter SubMenu),
 * BottomNavigationView wiring, screen transitions, FAB animations and implicit-intent helpers,
 * plus CSV student import and saving attendance sessions.
 */
public abstract class BaseActivity extends AppCompatActivity {

    static final String PREFS = "attendance_prefs";
    static final String KEY_NIGHT = "night_mode";

    private static final int MAX_IMPORT_BYTES = 5 * 1024 * 1024;   // 5 MB is far beyond any class list
    private static final int MAX_ERROR_LINES_SHOWN = 6;

    protected DatabaseHelper db;
    private boolean playExitAnimation = true;

    /** File reading / parsing / bulk insert run off the UI thread. */
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    /** Storage Access Framework picker: no storage permission needed. */
    private final ActivityResultLauncher<String[]> importPicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) readImportFile(uri);
            });

    /** Called by AttendanceApp on start-up. */
    static void applySavedTheme(Context c) {
        int mode = c.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getInt(KEY_NIGHT, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = DatabaseHelper.getInstance(this);
    }

    @Override protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    protected void setupToolbar(MaterialToolbar toolbar, boolean showBack) {
        setSupportActionBar(toolbar);
        if (showBack && getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    // ------------------------------------------------------------ Options menu

    protected boolean supportsSearch() { return false; }
    protected boolean supportsFilter() { return false; }
    /** Import / save session / history / export / reset. Screens that do not need them return false. */
    protected boolean supportsStudentTools() { return true; }
    /** @param itemId R.id.filter_all / filter_present / filter_absent */
    protected void onFilterSelected(int itemId) { }
    /** Called after data was changed by a menu action (e.g. attendance reset). */
    protected void onDataChanged() { }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem search = menu.findItem(R.id.menu_search);
        MenuItem filter = menu.findItem(R.id.menu_filter);
        if (search != null) search.setVisible(supportsSearch());
        if (filter != null) filter.setVisible(supportsFilter());
        MenuItem classFilter = menu.findItem(R.id.menu_class_filter);
        if (classFilter != null) classFilter.setVisible(supportsFilter());
        int[] tools = {R.id.menu_import, R.id.menu_export, R.id.menu_save_session, R.id.menu_history, R.id.menu_reset};
        for (int id : tools) {
            MenuItem item = menu.findItem(id);
            if (item != null) item.setVisible(supportsStudentTools());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
        } else if (id == R.id.menu_export) {
            exportReport();
        } else if (id == R.id.menu_import) {
            startImportFlow();
        } else if (id == R.id.menu_save_session) {
            showSaveSessionDialog();
        } else if (id == R.id.menu_history) {
            openActivity(new Intent(this, HistoryActivity.class));
        } else if (id == R.id.menu_reset) {
            confirmReset();
        } else if (id == R.id.menu_theme) {
            toggleTheme();
        } else if (id == R.id.menu_app_info) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.app_name)
                    .setMessage(R.string.app_info_message).setPositiveButton(R.string.ok, null).show();
        } else if (id == R.id.filter_all || id == R.id.filter_present || id == R.id.filter_absent) {
            item.setChecked(true);          // single-choice SubMenu item
            onFilterSelected(id);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /** Saves the opposite of the current mode; AppCompat recreates every Activity automatically. */
    private void toggleTheme() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int mode = night ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_NIGHT, mode).apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.reset_title).setMessage(R.string.reset_msg)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    db.resetAttendance();
                    Toast.makeText(this, R.string.reset_done, Toast.LENGTH_SHORT).show();
                    onDataChanged();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    // ------------------------------------------------------- Navigation + transitions

    /** Explicit-intent navigation with slide-in-right / slide-out-left. */
    protected void openActivity(Intent intent) {
        startActivity(intent);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    /** BottomNavigationView (OnItemSelectedListener): Trang chủ / Danh sách / Quét mã. */
    protected void setupBottomNav(BottomNavigationView nav, int selectedId) {
        nav.setSelectedItemId(selectedId); // before the listener, so it does not fire
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedId) return true;
            Class<?> target = id == R.id.nav_home ? MainActivity.class
                    : id == R.id.nav_list ? StudentListActivity.class : ScanBarcodeActivity.class;
            Intent intent = new Intent(this, target);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            if (id == R.id.nav_home) {
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            } else {
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                if (!(this instanceof MainActivity)) finishQuietly(); // keep stack: Main -> current tab
            }
            return true;
        });
    }

    @Override public void finish() {
        super.finish();
        if (playExitAnimation) overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }

    protected void finishQuietly() {
        playExitAnimation = false;
        finish();
    }

    // --------------------------------------------------------------- Animations

    protected void popIn(View fab) {
        fab.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_enter));
    }

    protected void pressAnimate(View v, Runnable action) {
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).withEndAction(action).start()).start();
    }

    protected void animateCount(TextView tv, int target) {
        ValueAnimator a = ValueAnimator.ofInt(0, target);
        a.setDuration(700);
        a.addUpdateListener(anim -> tv.setText(String.valueOf(anim.getAnimatedValue())));
        a.start();
    }

    // --------------------------------------------------------- Report export (CSV)

    /** Exports today's attendance of every student as CSV and shares it with ACTION_SEND. */
    protected void exportReport() {
        List<Student> list = db.getStudents("", -1);
        if (list.isEmpty()) { toast(R.string.export_empty); return; }
        List<String[]> rows = new ArrayList<>();
        for (Student s : list) {
            rows.add(new String[]{s.getCode(), s.getName(), s.getClassName(), s.getBarcode(), s.getEmail(), s.getPhone(),
                    getString(s.isPresent() ? R.string.status_present : R.string.status_absent), s.getTimestamp()});
        }
        int total = list.size(), present = db.countPresent();
        String date = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(new Date());
        shareCsv("bao_cao_diem_danh", getString(R.string.csv_header).split(","), rows,
                getString(R.string.export_subject, date),
                getString(R.string.export_summary, date, total, present, total - present));
    }

    /**
     * Writes a UTF-8 CSV (with BOM so Excel shows Vietnamese correctly) to the cache and shares it.
     * @param text optional body text of the share intent
     */
    protected void shareCsv(String prefix, String[] header, List<String[]> rows, String subject, @Nullable String text) {
        try {
            File dir = new File(getCacheDir(), "reports");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("mkdirs failed");
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            File file = new File(dir, prefix + "_" + stamp + ".csv");

            try (OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                w.write('\uFEFF');
                writeCsvLine(w, header);
                for (String[] row : rows) writeCsvLine(w, row);
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);

            // IMPLICIT INTENT: ACTION_SEND (summary text + CSV attachment)
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/csv");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_SUBJECT, subject);
            if (text != null) send.putExtra(Intent.EXTRA_TEXT, text);
            send.setClipData(ClipData.newRawUri("", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startSafely(Intent.createChooser(send, getString(R.string.export_chooser)));
        } catch (IOException | IllegalArgumentException e) {
            toast(R.string.export_error);
        }
    }

    private void writeCsvLine(Writer w, String[] cells) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csv(cells[i]));
        }
        sb.append("\r\n");
        w.write(sb.toString());
    }

    private String csv(@Nullable String v) {
        return "\"" + (v == null ? "" : v.replace("\"", "\"\"")) + "\"";
    }

    // --------------------------------------------------------- Import students (CSV)

    /** Step 1: explain the format, then let the user pick a file or share a ready-made template. */
    protected void startImportFlow() {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.import_title).setMessage(R.string.import_help)
                .setPositiveButton(R.string.import_choose, (d, w) -> importPicker.launch(new String[]{
                        "text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"}))
                .setNeutralButton(R.string.import_template, (d, w) -> shareImportTemplate())
                .setNegativeButton(R.string.cancel, null).show();
    }

    private void shareImportTemplate() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"SV101", "Nguyễn Văn Minh", "CNTT03", "", "minh@example.com", "0902000001"});
        rows.add(new String[]{"SV102", "Trần Thị Hoa", "CNTT03", "", "", "0902000002"});
        shareCsv("mau_nhap_sinh_vien", getString(R.string.import_template_header).split(","), rows,
                getString(R.string.import_template_subject), null);
    }

    /** Step 2: read + parse on a worker thread so a big file never freezes the UI. */
    private void readImportFile(Uri uri) {
        final String fileName = displayName(uri);
        io.execute(() -> {
            CsvStudentParser.Result parsed = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("no stream");
                parsed = CsvStudentParser.parse(CsvStudentParser.decode(readLimited(in)));
            } catch (IOException | RuntimeException e) {   // unreadable file, revoked permission, bad content
                parsed = null;
            }
            final CsvStudentParser.Result result = parsed;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (result == null) {
                    new MaterialAlertDialogBuilder(this).setTitle(R.string.import_title)
                            .setMessage(R.string.import_read_error).setPositiveButton(R.string.ok, null).show();
                } else {
                    showImportPreview(fileName, result);
                }
            });
        });
    }

    private byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
            if (out.size() > MAX_IMPORT_BYTES) throw new IOException("file too large");
        }
        return out.toByteArray();
    }

    private String displayName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (RuntimeException ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? "file.csv" : last;
    }

    /** Step 3: show what will happen (new / existing / invalid rows) before touching the database. */
    private void showImportPreview(String fileName, CsvStudentParser.Result r) {
        if (r.fatal != CsvStudentParser.FATAL_NONE) {
            int msg = r.fatal == CsvStudentParser.FATAL_MISSING_COLUMNS ? R.string.import_err_columns
                    : r.fatal == CsvStudentParser.FATAL_TOO_MANY ? R.string.import_err_too_many
                    : R.string.import_err_empty;
            new MaterialAlertDialogBuilder(this).setTitle(R.string.import_title)
                    .setMessage(msg).setPositiveButton(R.string.ok, null).show();
            return;
        }

        Set<String> existing = db.getAllCodes();
        int already = 0;
        for (Student s : r.students) if (existing.contains(s.getCode())) already++;
        int fresh = r.students.size() - already;

        StringBuilder text = new StringBuilder(getString(R.string.import_summary, fileName,
                r.students.size(), fresh, already, r.errors.size()));
        if (!r.errors.isEmpty()) {
            text.append("\n\n").append(getString(R.string.import_errors_header));
            int shown = Math.min(r.errors.size(), MAX_ERROR_LINES_SHOWN);
            for (int i = 0; i < shown; i++) {
                CsvStudentParser.RowError e = r.errors.get(i);
                text.append("\n• ").append(getString(R.string.import_error_line, e.line, errorText(e.reason)));
            }
            if (r.errors.size() > shown) {
                text.append("\n").append(getString(R.string.import_errors_more, r.errors.size() - shown));
            }
        }

        if (r.students.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.import_title)
                    .setMessage(text.toString()).setPositiveButton(R.string.ok, null).show();
            return;
        }

        View v = getLayoutInflater().inflate(R.layout.dialog_import_preview, null);
        ((TextView) v.findViewById(R.id.tv_import_summary)).setText(text.toString());
        CheckBox cbUpdate = v.findViewById(R.id.cb_update_existing);
        cbUpdate.setVisibility(already > 0 ? View.VISIBLE : View.GONE);

        new MaterialAlertDialogBuilder(this).setTitle(R.string.import_preview_title).setView(v)
                .setPositiveButton(R.string.import_action, (d, w) -> runImport(r.students, cbUpdate.isChecked()))
                .setNegativeButton(R.string.cancel, null).show();
    }

    private String errorText(int reason) {
        switch (reason) {
            case CsvStudentParser.ERR_MISSING_CODE: return getString(R.string.import_err_row_code);
            case CsvStudentParser.ERR_MISSING_NAME: return getString(R.string.import_err_row_name);
            case CsvStudentParser.ERR_BAD_EMAIL: return getString(R.string.import_err_row_email);
            case CsvStudentParser.ERR_DUP_CODE: return getString(R.string.import_err_row_dup_code);
            default: return getString(R.string.import_err_row_dup_barcode);
        }
    }

    /** Step 4: single-transaction insert on the worker thread, then report the outcome. */
    private void runImport(List<Student> students, boolean updateExisting) {
        io.execute(() -> {
            DatabaseHelper.ImportSummary sum = db.importStudents(students, updateExisting);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                onDataChanged();
                new MaterialAlertDialogBuilder(this).setTitle(R.string.import_done_title)
                        .setMessage(getString(R.string.import_result, sum.added, sum.updated, sum.skipped, sum.conflicts))
                        .setPositiveButton(R.string.ok, null).show();
            });
        });
    }

    // ------------------------------------------------------------ Save session

    /** Archives the current attendance as a named session (optionally starting a fresh one). */
    protected void showSaveSessionDialog() {
        if (db.countAll() == 0) { toast(R.string.export_empty); return; }
        final String defaultName = getString(R.string.session_default_name,
                new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(new Date()));

        View v = getLayoutInflater().inflate(R.layout.dialog_save_session, null);
        TextInputEditText etName = v.findViewById(R.id.et_session_name);
        CheckBox cbReset = v.findViewById(R.id.cb_reset_after);
        etName.setText(defaultName);

        new MaterialAlertDialogBuilder(this).setTitle(R.string.session_save_title).setView(v)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String name = etName.getText() == null ? "" : etName.getText().toString().trim();
                    db.saveSession(name.isEmpty() ? defaultName : name, cbReset.isChecked());
                    Snackbar.make(findViewById(android.R.id.content), R.string.session_saved, Snackbar.LENGTH_LONG).show();
                    onDataChanged();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    // ----------------------------------------------------------- Implicit intents

    protected void dialPhone(@Nullable String phone) {
        if (TextUtils.isEmpty(phone)) { toast(R.string.no_phone); return; }
        startSafely(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(phone))));
    }

    protected void composeEmail(@Nullable String email) {
        if (TextUtils.isEmpty(email)) { toast(R.string.no_email); return; }
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + email));
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.email_subject));
        startSafely(i);
    }

    protected void startSafely(Intent intent) {
        try { startActivity(intent); } catch (ActivityNotFoundException e) { toast(R.string.no_app); }
    }

    protected void toast(int resId) { Toast.makeText(this, resId, Toast.LENGTH_SHORT).show(); }
}
