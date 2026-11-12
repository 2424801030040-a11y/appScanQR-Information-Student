# StudentAttendance v1.1 – what's new

## 1. Import students from CSV (menu ⋮ → "Nhập danh sách sinh viên", or the dashboard button)
- Pick a file with the system file picker (no storage permission needed); "Tải tệp mẫu" shares a ready-made template.
- Columns are recognised by header (Vietnamese or English, any order): MSSV, Họ và tên, Lớp, Mã vạch, Email, Số điện thoại.
  No header row = default order. The app's own exported report can be re-imported.
- Auto-detects `,` `;` tab delimiters, quoted cells, UTF-8 / UTF-16 / old Windows-1258 Excel files.
- Fixes Excel's dropped leading zero in phone numbers (901000001 -> 0901000001) and +84 numbers.
- Preview before saving: valid / new / already-existing / invalid rows with line numbers and reasons.
- Option to update students that already exist (matched by MSSV; empty cells never erase data; attendance is never touched).
- Runs off the UI thread, single DB transaction, max 5 MB / 5000 rows.

## 2. Save sessions + history (menu ⋮ → "Lưu buổi điểm danh" / "Lịch sử điểm danh", or dashboard buttons)
- Snapshot today's attendance under a name; optionally reset to start the next class period.
- New History screen: rate per session, list of absent students, export a session as CSV, delete.
- Database upgraded to v2 with a real migration (existing students are preserved).

## 3. Filter student list by class (list screen → menu ⋮ → "Lọc theo lớp")
Combines with the existing search and present/absent filter.

## Files
New:      CsvStudentParser, AttendanceSession, HistoryActivity, layouts (activity_history, item_session,
          dialog_import_preview, dialog_save_session), sample_students.csv
Modified: DatabaseHelper, BaseActivity, StudentListActivity, MainActivity, options_menu.xml, activity_main.xml,
          strings.xml, AndroidManifest.xml, app/build.gradle (versionName 1.1)
