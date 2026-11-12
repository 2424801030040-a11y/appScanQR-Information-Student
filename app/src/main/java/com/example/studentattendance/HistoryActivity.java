package com.example.studentattendance;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Lịch sử điểm danh: every session saved with "Lưu buổi điểm danh". Tap a session to see who was
 * absent, export it as CSV or delete it.
 */
public class HistoryActivity extends BaseActivity {

    private SessionAdapter adapter;
    private TextView tvEmpty;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        setupToolbar(findViewById(R.id.toolbar), true);

        tvEmpty = findViewById(R.id.tv_history_empty);
        RecyclerView rv = findViewById(R.id.rv_sessions);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SessionAdapter(this::showDetail);
        rv.setAdapter(adapter);
    }

    @Override protected void onResume() {
        super.onResume();
        loadSessions();
    }

    /** Save / import / reset make no sense on this screen. */
    @Override protected boolean supportsStudentTools() { return false; }

    private void loadSessions() {
        List<AttendanceSession> list = db.getSessions();
        adapter.setData(list);
        tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(getString(R.string.history_count, list.size()));
    }

    private void showDetail(AttendanceSession s) {
        List<Student> records = db.getSessionRecords(s.getId());

        StringBuilder absent = new StringBuilder();
        int absentCount = 0;
        for (Student r : records) {
            if (r.isPresent()) continue;
            absentCount++;
            absent.append("• ").append(r.getCode()).append(" - ").append(r.getName());
            if (r.getClassName() != null && !r.getClassName().isEmpty()) {
                absent.append(" (").append(r.getClassName()).append(')');
            }
            absent.append('\n');
        }

        String message = getString(R.string.history_detail_summary,
                s.getCreatedAt(), s.getPresent(), s.getTotal(), s.getRatePercent()) + "\n\n"
                + (absentCount == 0 ? getString(R.string.history_all_present)
                : getString(R.string.history_absent_list, absentCount) + "\n" + absent.toString().trim());

        new MaterialAlertDialogBuilder(this).setTitle(s.getName()).setMessage(message)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.history_export, (d, w) -> exportSession(s, records))
                .setNegativeButton(R.string.delete, (d, w) -> confirmDelete(s))
                .show();
    }

    private void exportSession(AttendanceSession s, List<Student> records) {
        List<String[]> rows = new ArrayList<>();
        for (Student r : records) {
            rows.add(new String[]{r.getCode(), r.getName(), r.getClassName(),
                    getString(r.isPresent() ? R.string.status_present : R.string.status_absent), r.getTimestamp()});
        }
        shareCsv("buoi_diem_danh_" + s.getId(), getString(R.string.csv_header_session).split(","), rows,
                getString(R.string.history_export_subject, s.getName()),
                getString(R.string.history_detail_summary, s.getCreatedAt(), s.getPresent(), s.getTotal(), s.getRatePercent()));
    }

    private void confirmDelete(AttendanceSession s) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.history_delete_title)
                .setMessage(getString(R.string.history_delete_msg, s.getName()))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    db.deleteSession(s.getId());
                    loadSessions();
                    Snackbar.make(findViewById(R.id.rv_sessions), R.string.history_deleted, Snackbar.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null).show();
    }

    // ------------------------------------------------------------------ adapter

    private static class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.Holder> {
        interface OnClick { void onClick(AttendanceSession s); }

        private final List<AttendanceSession> items = new ArrayList<>();
        private final OnClick onClick;

        SessionAdapter(OnClick onClick) { this.onClick = onClick; }

        void setData(List<AttendanceSession> data) {
            items.clear();
            items.addAll(data);
            notifyDataSetChanged();
        }

        @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_session, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull Holder h, int position) {
            AttendanceSession s = items.get(position);
            android.content.Context c = h.itemView.getContext();
            h.name.setText(s.getName());
            h.time.setText(s.getCreatedAt());
            h.counts.setText(c.getString(R.string.history_counts, s.getPresent(), s.getAbsent(), s.getTotal()));
            h.rate.setText(c.getString(R.string.rate_pct, s.getRatePercent()));
            h.rate.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c,
                    s.getRatePercent() >= 75 ? R.color.present : R.color.absent)));
            h.itemView.setOnClickListener(v -> onClick.onClick(s));
        }

        @Override public int getItemCount() { return items.size(); }

        static class Holder extends RecyclerView.ViewHolder {
            final TextView name, time, counts, rate;
            Holder(@NonNull View v) {
                super(v);
                name = v.findViewById(R.id.tv_session_name);
                time = v.findViewById(R.id.tv_session_time);
                counts = v.findViewById(R.id.tv_session_counts);
                rate = v.findViewById(R.id.tv_session_rate);
            }
        }
    }
}
