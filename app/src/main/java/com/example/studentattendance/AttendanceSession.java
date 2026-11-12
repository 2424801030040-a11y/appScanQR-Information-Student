package com.example.studentattendance;

/** A saved attendance session (snapshot of who was present on a given day). */
public class AttendanceSession {
    private final long id;
    private final String name;
    private final String createdAt;
    private final int total;
    private final int present;

    public AttendanceSession(long id, String name, String createdAt, int total, int present) {
        this.id = id; this.name = name; this.createdAt = createdAt; this.total = total; this.present = present;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getCreatedAt() { return createdAt; }
    public int getTotal() { return total; }
    public int getPresent() { return present; }
    public int getAbsent() { return total - present; }
    public int getRatePercent() { return total == 0 ? 0 : present * 100 / total; }
}
