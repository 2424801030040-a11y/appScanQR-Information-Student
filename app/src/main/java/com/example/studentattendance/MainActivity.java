package com.example.studentattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Trang chủ: live statistics (donut chart + counters), quick actions and BottomNavigationView. */
public class MainActivity extends BaseActivity {

    private TextView tvTotal, tvPresent, tvAbsent, tvRate;
    private PieChartView pieChart;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupToolbar(findViewById(R.id.toolbar), false);
        setupBottomNav(findViewById(R.id.bottom_nav), R.id.nav_home);

        ((TextView) findViewById(R.id.tv_date)).setText(
                new SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(new Date()));

        tvTotal = bindStat(R.id.card_total, R.string.stat_total, R.color.accent_text);
        tvPresent = bindStat(R.id.card_present, R.string.stat_present, R.color.present);
        tvAbsent = bindStat(R.id.card_absent, R.string.stat_absent, R.color.absent);
        tvRate = findViewById(R.id.tv_rate);
        pieChart = findViewById(R.id.pie_chart);

        // EVENT: OnClickListener -> press animation, then explicit Intent
        findViewById(R.id.btn_start_scan).setOnClickListener(v ->
                pressAnimate(v, () -> openActivity(new Intent(this, ScanBarcodeActivity.class))));
        findViewById(R.id.btn_export).setOnClickListener(v -> pressAnimate(v, this::exportReport));
        findViewById(R.id.btn_import).setOnClickListener(v -> pressAnimate(v, this::startImportFlow));
        findViewById(R.id.btn_save_session).setOnClickListener(v -> pressAnimate(v, this::showSaveSessionDialog));
        findViewById(R.id.btn_history).setOnClickListener(v ->
                pressAnimate(v, () -> openActivity(new Intent(this, HistoryActivity.class))));
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStats();
    }

    @Override protected void onDataChanged() { refreshStats(); }

    private TextView bindStat(int cardId, int labelRes, int colorRes) {
        View card = findViewById(cardId);
        ((TextView) card.findViewById(R.id.stat_label)).setText(labelRes);
        TextView value = card.findViewById(R.id.stat_value);
        value.setTextColor(getColor(colorRes));
        return value;
    }

    private void refreshStats() {
        int total = db.countAll();
        int present = db.countPresent();
        int absent = total - present;
        animateCount(tvTotal, total);
        animateCount(tvPresent, present);
        animateCount(tvAbsent, absent);
        tvRate.setText(getString(R.string.rate_fmt, total == 0 ? 0 : present * 100 / total));
        pieChart.setData(present, absent);
    }
}
