package com.example.studentattendance;

import java.io.Serializable;

/** Student model. Serializable so whole objects travel between Activities as Intent extras. */
public class Student implements Serializable {
    public static final int STATUS_ABSENT = 0;   // Vắng mặt
    public static final int STATUS_PRESENT = 1;  // Có mặt

    private final long id;
    private final String code;        // MSSV
    private final String name;
    private final String className;
    private final String barcode;     // string encoded in the student's QR / barcode
    private final String email;
    private final String phone;
    private final int status;
    private final String timestamp;   // time of attendance, null when absent

    public Student(long id, String code, String name, String className, String barcode,
                   String email, String phone, int status, String timestamp) {
        this.id = id; this.code = code; this.name = name; this.className = className;
        this.barcode = barcode; this.email = email; this.phone = phone;
        this.status = status; this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getClassName() { return className; }
    public String getBarcode() { return barcode; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public int getStatus() { return status; }
    public String getTimestamp() { return timestamp; }
    public boolean isPresent() { return status == STATUS_PRESENT; }
}
