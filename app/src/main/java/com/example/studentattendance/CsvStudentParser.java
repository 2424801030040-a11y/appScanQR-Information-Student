package com.example.studentattendance;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a student list from CSV text. Deliberately free of Android classes so it can be unit-tested.
 *
 * Supported input:
 *  - delimiter auto-detected (comma, semicolon or tab; Excel's "sep=;" line is honoured)
 *  - quoted cells, escaped quotes (""), line breaks inside quotes, \r\n / \n / \r
 *  - UTF-8 (with or without BOM), UTF-16 (BOM) and a Windows-1258 fallback for old Excel files
 *  - Vietnamese or English headers in any order; no header = default column order
 *    (MSSV, Họ tên, Lớp, Mã vạch, Email, SĐT). The app's own exported report can be re-imported.
 */
public final class CsvStudentParser {

    public static final int MAX_ROWS = 5000;

    // fatal problems (nothing can be imported)
    public static final int FATAL_NONE = 0;
    public static final int FATAL_EMPTY = 1;
    public static final int FATAL_MISSING_COLUMNS = 2;
    public static final int FATAL_TOO_MANY = 3;

    // per-row problems (the row is skipped, the rest is still imported)
    public static final int ERR_MISSING_CODE = 1;
    public static final int ERR_MISSING_NAME = 2;
    public static final int ERR_BAD_EMAIL = 3;
    public static final int ERR_DUP_CODE = 4;
    public static final int ERR_DUP_BARCODE = 5;

    public static final class RowError {
        public final int line;      // 1-based line in the file
        public final int reason;    // ERR_*
        RowError(int line, int reason) { this.line = line; this.reason = reason; }
    }

    public static final class Result {
        /** Valid rows. Barcode is "" when the file gave none (the database then uses the MSSV). */
        public final List<Student> students = new ArrayList<>();
        public final List<RowError> errors = new ArrayList<>();
        public int fatal = FATAL_NONE;
        public boolean hasHeader;
        public int dataRows;
    }

    private static final int F_CODE = 0, F_NAME = 1, F_CLASS = 2, F_BARCODE = 3, F_EMAIL = 4, F_PHONE = 5;

    // normalised header text (no accents, lower-case, punctuation -> space) per field
    private static final String[][] ALIASES = {
            {"mssv", "ma so sinh vien", "ma sinh vien", "ma sv", "masv", "ma so sv", "code",
                    "student id", "studentid", "student code", "id"},
            {"ho va ten", "ho ten", "hoten", "ten sinh vien", "ten", "name", "full name", "fullname",
                    "student name"},
            {"lop", "lop hoc", "ma lop", "class", "class name", "classname"},
            {"ma vach", "barcode", "qr", "qr code", "qrcode", "ma qr", "ma vach qr", "chuoi ma vach",
                    "chuoi ma vach qr"},
            {"email", "e mail", "mail", "thu dien tu"},
            {"so dien thoai", "dien thoai", "sdt", "so dt", "phone", "tel", "mobile"}
    };

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private CsvStudentParser() { }

    // ------------------------------------------------------------------ decoding

    /** Bytes -> text. Handles BOMs and falls back to Windows-1258 when the bytes are not valid UTF-8. */
    public static String decode(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB && (b[2] & 0xFF) == 0xBF) {
            return new String(b, 3, b.length - 3, StandardCharsets.UTF_8);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16LE);
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            try { return new String(b, Charset.forName("windows-1258")); }
            catch (RuntimeException ex) { return new String(b, StandardCharsets.ISO_8859_1); }
        }
    }

    // ------------------------------------------------------------------- parsing

    public static Result parse(String text) {
        Result res = new Result();
        if (text == null) { res.fatal = FATAL_EMPTY; return res; }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1);

        char delim = detectDelimiter(text);
        // Excel writes "sep=;" as the first line when the list separator is not a comma
        if (text.regionMatches(true, 0, "sep=", 0, 4)) {
            int eol = indexOfEol(text);
            if (eol > 0 && eol <= 6) {
                delim = text.charAt(4);
                text = text.substring(Math.min(text.length(), eol + 1));
            }
        }

        List<Row> rows = tokenize(text, delim);
        if (rows.isEmpty()) { res.fatal = FATAL_EMPTY; return res; }

        // ---- header detection
        int[] col = {-1, -1, -1, -1, -1, -1};
        int recognised = 0;
        List<String> first = rows.get(0).cells;
        for (int i = 0; i < first.size(); i++) {
            int f = fieldFor(first.get(i));
            if (f >= 0 && col[f] < 0) { col[f] = i; recognised++; }
        }
        int start;
        if (recognised > 0) {
            if (col[F_CODE] < 0 || col[F_NAME] < 0) { res.fatal = FATAL_MISSING_COLUMNS; return res; }
            res.hasHeader = true;
            start = 1;
        } else {
            for (int i = 0; i < col.length; i++) col[i] = i;   // default column order
            start = 0;
        }

        res.dataRows = rows.size() - start;
        if (res.dataRows <= 0) { res.fatal = FATAL_EMPTY; return res; }
        if (res.dataRows > MAX_ROWS) { res.fatal = FATAL_TOO_MANY; return res; }

        // ---- rows
        Set<String> seenCodes = new HashSet<>();
        Set<String> seenBarcodes = new HashSet<>();
        for (int r = start; r < rows.size(); r++) {
            Row row = rows.get(r);
            String code = cell(row, col[F_CODE]);
            String name = cell(row, col[F_NAME]).replaceAll("\\s+", " ");
            String cls = cell(row, col[F_CLASS]);
            String barcode = cell(row, col[F_BARCODE]);
            String email = cell(row, col[F_EMAIL]);
            String phone = normalizePhone(cell(row, col[F_PHONE]));

            if (code.isEmpty()) { res.errors.add(new RowError(row.line, ERR_MISSING_CODE)); continue; }
            if (name.isEmpty()) { res.errors.add(new RowError(row.line, ERR_MISSING_NAME)); continue; }
            if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
                res.errors.add(new RowError(row.line, ERR_BAD_EMAIL)); continue;
            }
            String effectiveBarcode = barcode.isEmpty() ? code : barcode;
            if (!seenCodes.add(code)) { res.errors.add(new RowError(row.line, ERR_DUP_CODE)); continue; }
            if (!seenBarcodes.add(effectiveBarcode)) {
                seenCodes.remove(code);
                res.errors.add(new RowError(row.line, ERR_DUP_BARCODE)); continue;
            }
            res.students.add(new Student(0, code, name, cls, barcode, email, phone, Student.STATUS_ABSENT, null));
        }
        return res;
    }

    /** Excel drops the leading zero of phone numbers (901000001); "+84" numbers become 0xxxxxxxxx. */
    static String normalizePhone(String p) {
        if (p.isEmpty()) return p;
        String d = p.replaceAll("[\\s.\\-()]", "");
        if (d.startsWith("+84") && d.length() > 3) return "0" + d.substring(3);
        if (d.matches("[1-9]\\d{8}")) return "0" + d;
        return d;
    }

    // ----------------------------------------------------------------- internals

    private static final class Row {
        final int line;
        final List<String> cells;
        Row(int line, List<String> cells) { this.line = line; this.cells = cells; }
    }

    private static String cell(Row row, int index) {
        if (index < 0 || index >= row.cells.size()) return "";
        return row.cells.get(index).replace('\u00A0', ' ').trim();
    }

    private static int fieldFor(String header) {
        String h = foldHeader(header);
        if (h.isEmpty()) return -1;
        for (int f = 0; f < ALIASES.length; f++) {
            for (String alias : ALIASES[f]) if (alias.equals(h)) return f;
        }
        return -1;
    }

    static String foldHeader(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.replace('\uFEFF', ' '), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        n = n.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT);
        return n.replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static int indexOfEol(String t) {
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\n' || c == '\r') return i;
        }
        return t.length();
    }

    /** Looks at the first non-empty line (outside quotes) and picks the most frequent delimiter. */
    private static char detectDelimiter(String text) {
        int comma = 0, semi = 0, tab = 0;
        boolean inQuotes = false, seenContent = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') { inQuotes = !inQuotes; seenContent = true; continue; }
            if (inQuotes) continue;
            if (c == '\n' || c == '\r') {
                if (seenContent) break;
                continue;
            }
            seenContent = true;
            if (c == ',') comma++; else if (c == ';') semi++; else if (c == '\t') tab++;
        }
        if (semi > comma && semi >= tab) return ';';
        if (tab > comma && tab > semi) return '\t';
        return ',';
    }

    private static List<Row> tokenize(String text, char delim) {
        List<Row> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        int line = 1, rowLine = 1, n = text.length();

        for (int i = 0; i < n; i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') { cur.append('"'); i++; }
                    else inQuotes = false;
                } else {
                    if (ch == '\n') line++;
                    cur.append(ch);
                }
            } else if (ch == '"') {
                inQuotes = true;
            } else if (ch == delim) {
                cells.add(cur.toString());
                cur.setLength(0);
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
                cells.add(cur.toString());
                cur.setLength(0);
                addRow(rows, cells, rowLine);
                cells = new ArrayList<>();
                line++;
                rowLine = line;
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0 || !cells.isEmpty()) {
            cells.add(cur.toString());
            addRow(rows, cells, rowLine);
        }
        return rows;
    }

    private static void addRow(List<Row> rows, List<String> cells, int line) {
        for (String c : cells) if (!c.trim().isEmpty()) { rows.add(new Row(line, cells)); return; }
        // completely blank line: ignored
    }
}
