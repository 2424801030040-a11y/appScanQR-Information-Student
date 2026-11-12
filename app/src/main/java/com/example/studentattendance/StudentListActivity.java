package com.example.studentattendance;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.List;

/** Danh sách sinh viên: live search, filter SubMenu, ContextMenu, detail card with QR/Barcode. */
public class StudentListActivity extends BaseActivity implements StudentAdapter.Listener {

    private StudentAdapter adapter;
    private TextView tvEmpty;
    private String query = "";
    private int statusFilter = -1; // -1 = Tất cả
    private String classFilter = null; // null = tất cả các lớp

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_list);
        setupToolbar(findViewById(R.id.toolbar), false);
        setupBottomNav(findViewById(R.id.bottom_nav), R.id.nav_list);

        tvEmpty = findViewById(R.id.tv_empty);
        RecyclerView rv = findViewById(R.id.rv_students);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StudentAdapter(this);
        rv.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fab_add);
        popIn(fab);
        // EVENT: OnClickListener -> explicit Intent without extras = "add" mode
        fab.setOnClickListener(v -> pressAnimate(v, () -> openActivity(new Intent(this, StudentEditActivity.class))));
    }

    @Override protected void onResume() {
        super.onResume();
        loadStudents(true);
    }

    @Override protected void onDataChanged() { loadStudents(false); }

    private void loadStudents(boolean replayAnimation) {
        List<Student> list = db.getStudents(query, statusFilter, classFilter);
        adapter.setData(list, replayAnimation);
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        if (getSupportActionBar() != null) {
            String subtitle = getString(R.string.list_count, list.size());
            if (classFilter != null) subtitle += " · " + classFilter;
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    // ----------------------------------------------- Options menu: SearchView + SubMenu

    @Override protected boolean supportsSearch() { return true; }
    @Override protected boolean supportsFilter() { return true; }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.search_hint));
            searchView.setMaxWidth(Integer.MAX_VALUE);
            // EVENT: real-time search while typing
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override public boolean onQueryTextSubmit(String text) { return false; }
                @Override public boolean onQueryTextChange(String text) {
                    query = text == null ? "" : text;
                    loadStudents(false);
                    return true;
                }
            });
        }
        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_class_filter) {
            showClassFilter();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Single-choice dialog listing every class found in the database. */
    private void showClassFilter() {
        List<String> classes = db.getClasses();
        if (classes.isEmpty()) { toast(R.string.class_filter_none); return; }
        String[] items = new String[classes.size() + 1];
        items[0] = getString(R.string.class_filter_all);
        for (int i = 0; i < classes.size(); i++) items[i + 1] = classes.get(i);
        int checked = classFilter == null ? 0 : classes.indexOf(classFilter) + 1;   // vanished class -> "all"

        new MaterialAlertDialogBuilder(this).setTitle(R.string.class_filter_title)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    classFilter = which == 0 ? null : classes.get(which - 1);
                    dialog.dismiss();
                    loadStudents(true);
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    @Override protected void onFilterSelected(int itemId) {
        statusFilter = itemId == R.id.filter_present ? Student.STATUS_PRESENT
                : itemId == R.id.filter_absent ? Student.STATUS_ABSENT : -1;
        loadStudents(true);
    }

    // ------------------------------------------------------- StudentAdapter.Listener

    @Override public void onStudentClick(Student s) { showDetail(s); }

    @Override public void onEdit(Student s) {
        // EXPLICIT INTENT: pass the whole Student object as an extra
        Intent i = new Intent(this, StudentEditActivity.class);
        i.putExtra(StudentEditActivity.EXTRA_STUDENT, s);
        openActivity(i);
    }

    /** Manual override for unreadable cards: toggles Có mặt / Vắng mặt. */
    @Override public void onToggleAttendance(Student s) {
        boolean makePresent = !s.isPresent();
        db.setAttendance(s.getId(), makePresent);
        loadStudents(false);
        Snackbar.make(findViewById(R.id.rv_students),
                getString(makePresent ? R.string.manual_present : R.string.manual_absent, s.getName()),
                Snackbar.LENGTH_SHORT).show();
    }

    @Override public void onDelete(Student s) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.confirm_delete_title)
                .setMessage(getString(R.string.confirm_delete_msg, s.getName()))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    db.deleteStudent(s.getId());
                    loadStudents(false);
                    Snackbar.make(findViewById(R.id.rv_students), getString(R.string.student_deleted, s.getName()),
                            Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    @Override public void onViewCode(Student s) { showDetail(s); }

    // ----------------------------------------------------- Detail card + QR/Barcode

    private void showDetail(Student s) {
        View v = getLayoutInflater().inflate(R.layout.dialog_student_detail, null);
        ImageView img = v.findViewById(R.id.img_code);
        ((TextView) v.findViewById(R.id.tv_detail_name)).setText(s.getName());
        ((TextView) v.findViewById(R.id.tv_detail_meta)).setText(getString(R.string.student_meta, s.getCode(), s.getClassName()));
        ((TextView) v.findViewById(R.id.tv_detail_contact)).setText(
                getString(R.string.student_meta, nonNull(s.getEmail()), nonNull(s.getPhone())));

        TextView tvStatus = v.findViewById(R.id.tv_detail_status);
        tvStatus.setText(getString(R.string.detail_status,
                getString(s.isPresent() ? R.string.status_present : R.string.status_absent)));
        tvStatus.setTextColor(getColor(s.isPresent() ? R.color.present : R.color.absent));
        ((TextView) v.findViewById(R.id.tv_detail_time)).setText(getString(R.string.detail_time,
                s.isPresent() ? s.getTimestamp() : getString(R.string.not_checked_in)));

        // Generate the code image (QR by default; toggle to Code-128 barcode)
        renderCode(img, s.getBarcode(), BarcodeFormat.QR_CODE);
        MaterialButtonToggleGroup toggle = v.findViewById(R.id.toggle_format);
        toggle.check(R.id.btn_qr);
        toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) renderCode(img, s.getBarcode(),
                    checkedId == R.id.btn_qr ? BarcodeFormat.QR_CODE : BarcodeFormat.CODE_128);
        });

        // IMPLICIT INTENTS: ACTION_DIAL / ACTION_SENDTO
        v.findViewById(R.id.btn_call).setOnClickListener(b -> dialPhone(s.getPhone()));
        v.findViewById(R.id.btn_email).setOnClickListener(b -> composeEmail(s.getEmail()));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(v).setPositiveButton(R.string.close, null).create();
        dialog.show();
        v.startAnimation(android.view.animation.AnimationUtils.loadAnimation(this, R.anim.bounce));
    }

    private void renderCode(ImageView img, String content, BarcodeFormat format) {
        try {
            boolean qr = format == BarcodeFormat.QR_CODE;
            Bitmap bmp = new BarcodeEncoder().encodeBitmap(content, format, qr ? 640 : 900, qr ? 640 : 300);
            img.setImageBitmap(bmp);
        } catch (WriterException | IllegalArgumentException e) {
            img.setImageDrawable(null);
            toast(R.string.code_error);
        }
    }

    private String nonNull(String s) { return s == null ? "" : s; }
}
