package com.example.studentattendance;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** SQLite storage: CRUD, bulk import, attendance updates, statistics and saved sessions (history). */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "diem_danh.db";
    private static final int DB_VERSION = 2;
    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) instance = new DatabaseHelper(context.getApplicationContext());
        return instance;
    }

    private DatabaseHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE students (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "code TEXT NOT NULL UNIQUE, name TEXT NOT NULL, class_name TEXT, "
                + "barcode TEXT NOT NULL UNIQUE, email TEXT, phone TEXT, "
                + "status INTEGER NOT NULL DEFAULT 0, timestamp TEXT)");
        createSessionTables(db);
        seed(db);
    }

    /** Incremental migration: existing students are kept (the old version dropped the table). */
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) createSessionTables(db);   // v2: attendance history
    }

    private void createSessionTables(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "name TEXT NOT NULL, created_at TEXT NOT NULL, total INTEGER NOT NULL, present INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS session_records (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id INTEGER NOT NULL, code TEXT NOT NULL, name TEXT NOT NULL, class_name TEXT, "
                + "status INTEGER NOT NULL, timestamp TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_session ON session_records(session_id)");
    }

    /** Sample students so the app is usable right away (barcode = MSSV by default). */
    private void seed(SQLiteDatabase db) {
        String[][] rows = {
                {"SV001", "Nguyễn Văn An", "CNTT01", "an@example.com", "0901000001"},
                {"SV002", "Trần Thị Bình", "CNTT01", "binh@example.com", "0901000002"},
                {"SV003", "Lê Hoàng Cường", "CNTT02", "cuong@example.com", "0901000003"},
                {"SV004", "Phạm Minh Đức", "CNTT02", "duc@example.com", "0901000004"},
                {"SV005", "Võ Thanh Hà", "KTPM01", "ha@example.com", "0901000005"},
                {"SV006", "Đặng Quốc Khánh", "KTPM01", "khanh@example.com", "0901000006"}};
        for (String[] r : rows) {
            ContentValues cv = new ContentValues();
            cv.put("code", r[0]); cv.put("name", r[1]); cv.put("class_name", r[2]);
            cv.put("barcode", r[0]); cv.put("email", r[3]); cv.put("phone", r[4]);
            db.insert("students", null, cv);
        }
    }

    // ------------------------------------------------------------------ helpers

    public static String nowStamp() {
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US).format(new Date());
    }

    /** Lower-cases and strips Vietnamese diacritics so "nguyen" matches "Nguyễn". */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
    }

    private Student read(Cursor c) {
        return new Student(c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("code")),
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getString(c.getColumnIndexOrThrow("class_name")),
                c.getString(c.getColumnIndexOrThrow("barcode")),
                c.getString(c.getColumnIndexOrThrow("email")),
                c.getString(c.getColumnIndexOrThrow("phone")),
                c.getInt(c.getColumnIndexOrThrow("status")),
                c.getString(c.getColumnIndexOrThrow("timestamp")));
    }

    /** Profile fields only: attendance status is changed through setAttendance(). */
    private ContentValues profileValues(Student s) {
        ContentValues cv = new ContentValues();
        cv.put("code", s.getCode()); cv.put("name", s.getName()); cv.put("class_name", s.getClassName());
        cv.put("barcode", s.getBarcode()); cv.put("email", s.getEmail()); cv.put("phone", s.getPhone());
        return cv;
    }

    // --------------------------------------------------------------------- CRUD

    /** @return false when the MSSV or barcode already exists. */
    public boolean addStudent(Student s) {
        try { return getWritableDatabase().insertOrThrow("students", null, profileValues(s)) != -1; }
        catch (SQLiteConstraintException e) { return false; }
    }

    public boolean updateStudent(Student s) {
        try {
            return getWritableDatabase().update("students", profileValues(s), "id = ?",
                    new String[]{String.valueOf(s.getId())}) > 0;
        } catch (SQLiteConstraintException e) { return false; }
    }

    public void deleteStudent(long id) {
        getWritableDatabase().delete("students", "id = ?", new String[]{String.valueOf(id)});
    }

    public List<Student> getStudents(@Nullable String query, int statusFilter) {
        return getStudents(query, statusFilter, null);
    }

    /**
     * @param query        search text (name or MSSV, accent-insensitive); empty = no search
     * @param statusFilter -1 = all, otherwise Student.STATUS_*
     * @param className    null = all classes, otherwise the exact class name
     */
    public List<Student> getStudents(@Nullable String query, int statusFilter, @Nullable String className) {
        List<Student> list = new ArrayList<>();
        String q = fold(query == null ? "" : query.trim());
        List<String> conds = new ArrayList<>();
        List<String> args = new ArrayList<>();
        if (statusFilter >= 0) { conds.add("status = ?"); args.add(String.valueOf(statusFilter)); }
        if (className != null) { conds.add("class_name = ?"); args.add(className); }
        String sel = conds.isEmpty() ? null : TextUtilsJoin.and(conds);
        String[] selArgs = args.isEmpty() ? null : args.toArray(new String[0]);
        try (Cursor c = getReadableDatabase().query("students", null, sel, selArgs, null, null, "name COLLATE NOCASE")) {
            while (c.moveToNext()) {
                Student s = read(c);
                if (q.isEmpty() || fold(s.getName()).contains(q) || fold(s.getCode()).contains(q)) list.add(s);
            }
        }
        return list;
    }

    /** Distinct, non-empty class names (for the class filter). */
    public List<String> getClasses() {
        List<String> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT DISTINCT class_name FROM students "
                + "WHERE class_name IS NOT NULL AND class_name <> '' ORDER BY class_name COLLATE NOCASE", null)) {
            while (c.moveToNext()) list.add(c.getString(0));
        }
        return list;
    }

    /** Matches a scanned string against the barcode field or the MSSV. */
    @Nullable public Student findByBarcode(String text) {
        try (Cursor c = getReadableDatabase().query("students", null, "barcode = ? OR code = ?",
                new String[]{text, text}, null, null, null)) {
            return c.moveToFirst() ? read(c) : null;
        }
    }

    // ------------------------------------------------------------ Attendance + stats

    /** Sets status and returns the timestamp that was stored (null when marking absent). */
    @Nullable public String setAttendance(long id, boolean present) {
        String ts = present ? nowStamp() : null;
        ContentValues cv = new ContentValues();
        cv.put("status", present ? Student.STATUS_PRESENT : Student.STATUS_ABSENT);
        if (ts == null) cv.putNull("timestamp"); else cv.put("timestamp", ts);
        getWritableDatabase().update("students", cv, "id = ?", new String[]{String.valueOf(id)});
        return ts;
    }

    /** Starts a new session: everybody becomes absent again. */
    public void resetAttendance() {
        ContentValues cv = new ContentValues();
        cv.put("status", Student.STATUS_ABSENT);
        cv.putNull("timestamp");
        getWritableDatabase().update("students", cv, null, null);
    }

    public int countAll() { return count(null, null); }

    public int countPresent() { return count("status = ?", new String[]{String.valueOf(Student.STATUS_PRESENT)}); }

    private int count(@Nullable String where, @Nullable String[] args) {
        String sql = "SELECT COUNT(*) FROM students" + (where == null ? "" : " WHERE " + where);
        try (Cursor c = getReadableDatabase().rawQuery(sql, args)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }

    /** Joins SQL conditions with AND (kept here so DatabaseHelper has no extra dependencies). */
    private static final class TextUtilsJoin {
        static String and(List<String> parts) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) { if (i > 0) sb.append(" AND "); sb.append(parts.get(i)); }
            return sb.toString();
        }
    }

    // -------------------------------------------------------------- Bulk import

    public static final class ImportSummary {
        public int added, updated, skipped, conflicts;
    }

    /** All MSSV currently stored (used by the import preview to tell new rows from existing ones). */
    public Set<String> getAllCodes() {
        Set<String> codes = new HashSet<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT code FROM students", null)) {
            while (c.moveToNext()) codes.add(c.getString(0));
        }
        return codes;
    }

    /**
     * Imports parsed students in ONE transaction (fast, and all-or-nothing on I/O failure).
     * New MSSV -> inserted (barcode defaults to the MSSV). Existing MSSV -> updated when
     * updateExisting is true (empty cells never erase stored data), otherwise skipped.
     * A barcode already used by a different student counts as a conflict and that row is skipped.
     * Attendance status is never touched.
     */
    public ImportSummary importStudents(List<Student> rows, boolean updateExisting) {
        ImportSummary sum = new ImportSummary();
        SQLiteDatabase w = getWritableDatabase();
        w.beginTransaction();
        try {
            for (Student s : rows) {
                boolean exists;
                try (Cursor c = w.query("students", new String[]{"id"}, "code = ?", new String[]{s.getCode()},
                        null, null, null)) {
                    exists = c.moveToFirst();
                }
                if (!exists) {
                    ContentValues cv = profileValues(s);
                    if (blank(s.getBarcode())) cv.put("barcode", s.getCode());
                    if (w.insert("students", null, cv) != -1) sum.added++; else sum.conflicts++;
                } else if (updateExisting) {
                    ContentValues cv = new ContentValues();
                    cv.put("name", s.getName());
                    if (!blank(s.getClassName())) cv.put("class_name", s.getClassName());
                    if (!blank(s.getBarcode())) cv.put("barcode", s.getBarcode());
                    if (!blank(s.getEmail())) cv.put("email", s.getEmail());
                    if (!blank(s.getPhone())) cv.put("phone", s.getPhone());
                    try {
                        w.update("students", cv, "code = ?", new String[]{s.getCode()});
                        sum.updated++;
                    } catch (SQLiteConstraintException e) { sum.conflicts++; }
                } else {
                    sum.skipped++;
                }
            }
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
        return sum;
    }

    private static boolean blank(@Nullable String s) { return s == null || s.isEmpty(); }

    // ------------------------------------------------------ Sessions (history)

    /**
     * Snapshots the current attendance of every student into a new session.
     * @param resetAfter true = everybody becomes absent again afterwards (new class period)
     * @return the new session id
     */
    public long saveSession(String name, boolean resetAfter) {
        SQLiteDatabase w = getWritableDatabase();
        w.beginTransaction();
        try {
            List<Student> all = getStudents("", -1);
            int present = 0;
            for (Student s : all) if (s.isPresent()) present++;

            ContentValues sv = new ContentValues();
            sv.put("name", name); sv.put("created_at", nowStamp());
            sv.put("total", all.size()); sv.put("present", present);
            long sid = w.insertOrThrow("sessions", null, sv);

            for (Student s : all) {
                ContentValues r = new ContentValues();
                r.put("session_id", sid); r.put("code", s.getCode()); r.put("name", s.getName());
                r.put("class_name", s.getClassName()); r.put("status", s.getStatus());
                if (s.getTimestamp() == null) r.putNull("timestamp"); else r.put("timestamp", s.getTimestamp());
                w.insertOrThrow("session_records", null, r);
            }
            if (resetAfter) resetAttendance();
            w.setTransactionSuccessful();
            return sid;
        } finally {
            w.endTransaction();
        }
    }

    /** Newest first. */
    public List<AttendanceSession> getSessions() {
        List<AttendanceSession> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("sessions", null, null, null, null, null, "id DESC")) {
            while (c.moveToNext()) {
                list.add(new AttendanceSession(c.getLong(c.getColumnIndexOrThrow("id")),
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getString(c.getColumnIndexOrThrow("created_at")),
                        c.getInt(c.getColumnIndexOrThrow("total")),
                        c.getInt(c.getColumnIndexOrThrow("present"))));
            }
        }
        return list;
    }

    /** Per-student result of one saved session (Student objects with only code/name/class/status/time set). */
    public List<Student> getSessionRecords(long sessionId) {
        List<Student> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query("session_records", null, "session_id = ?",
                new String[]{String.valueOf(sessionId)}, null, null, "name COLLATE NOCASE")) {
            while (c.moveToNext()) {
                list.add(new Student(0, c.getString(c.getColumnIndexOrThrow("code")),
                        c.getString(c.getColumnIndexOrThrow("name")),
                        c.getString(c.getColumnIndexOrThrow("class_name")), null, null, null,
                        c.getInt(c.getColumnIndexOrThrow("status")),
                        c.getString(c.getColumnIndexOrThrow("timestamp"))));
            }
        }
        return list;
    }

    public void deleteSession(long sessionId) {
        SQLiteDatabase w = getWritableDatabase();
        w.beginTransaction();
        try {
            String[] arg = {String.valueOf(sessionId)};
            w.delete("session_records", "session_id = ?", arg);
            w.delete("sessions", "id = ?", arg);
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }
}
