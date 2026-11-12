package com.example.studentattendance;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.Menu;

import androidx.annotation.Nullable;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Add / edit a student. Receives the Student object through an explicit Intent extra (null = add). */
public class StudentEditActivity extends BaseActivity {

    public static final String EXTRA_STUDENT = "extra_student";

    private Student existing;
    private TextInputLayout tilCode, tilName, tilEmail;
    private TextInputEditText etCode, etName, etClass, etBarcode, etEmail, etPhone;
    private String lastCode = "";

    /** TextWatcher that only cares about afterTextChanged. */
    private abstract static class SimpleWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_edit);
        setupToolbar(findViewById(R.id.toolbar), true);

        existing = (Student) getIntent().getSerializableExtra(EXTRA_STUDENT);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(existing == null ? R.string.title_add : R.string.title_edit);
        }

        tilCode = findViewById(R.id.til_code);
        tilName = findViewById(R.id.til_name);
        tilEmail = findViewById(R.id.til_email);
        etCode = findViewById(R.id.et_code);
        etName = findViewById(R.id.et_name);
        etClass = findViewById(R.id.et_class);
        etBarcode = findViewById(R.id.et_barcode);
        etEmail = findViewById(R.id.et_email);
        etPhone = findViewById(R.id.et_phone);

        if (existing != null) {
            etCode.setText(existing.getCode());
            etName.setText(existing.getName());
            etClass.setText(existing.getClassName());
            etBarcode.setText(existing.getBarcode());
            etEmail.setText(existing.getEmail());
            etPhone.setText(existing.getPhone());
            lastCode = existing.getCode();
        }

        // EVENT: TextWatcher - the barcode string follows the MSSV until the user edits it manually
        etCode.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable e) {
                String now = e.toString().trim();
                String barcode = text(etBarcode);
                if (barcode.isEmpty() || barcode.equals(lastCode)) etBarcode.setText(now);
                lastCode = now;
                tilCode.setError(null);
            }
        });
        etName.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable e) { tilName.setError(null); }
        });
        etEmail.addTextChangedListener(new SimpleWatcher() {
            @Override public void afterTextChanged(Editable e) { tilEmail.setError(null); }
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> pressAnimate(v, this::save));
    }

    /** The options menu is not needed on the form screen. */
    @Override public boolean onCreateOptionsMenu(Menu menu) { return false; }

    private void save() {
        String code = text(etCode), name = text(etName), email = text(etEmail);
        boolean valid = true;
        if (code.isEmpty()) { tilCode.setError(getString(R.string.err_required)); valid = false; }
        if (name.isEmpty()) { tilName.setError(getString(R.string.err_required)); valid = false; }
        if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.setError(getString(R.string.err_email));
            valid = false;
        }
        if (!valid) return;

        String barcode = text(etBarcode);
        if (barcode.isEmpty()) barcode = code;

        Student s = new Student(existing == null ? 0 : existing.getId(), code, name, text(etClass), barcode,
                email, text(etPhone),
                existing == null ? Student.STATUS_ABSENT : existing.getStatus(),
                existing == null ? null : existing.getTimestamp());
        boolean ok = existing == null ? db.addStudent(s) : db.updateStudent(s);
        if (!ok) { tilCode.setError(getString(R.string.err_code_exists)); return; }

        toast(R.string.student_saved);
        finish();
    }

    private String text(@Nullable TextInputEditText et) {
        return et == null || et.getText() == null ? "" : et.getText().toString().trim();
    }
}
