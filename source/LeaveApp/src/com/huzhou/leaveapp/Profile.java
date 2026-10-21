package com.huzhou.leaveapp;

/** A locally stored student profile used by one of the two leave records. */
public final class Profile {
    public final int index;
    public final boolean female;
    public String name;
    public String studentId;
    public String className;
    public final String grade;
    public String phone;
    /** Empty means use the automatic leave timestamps. */
    public String leaveStartTime = "";
    public String leaveEndTime = "";
    public String leaveDays = "";
    /** Empty means use the automatic review-timeline timestamps. */
    public String applyTime = "";
    public String approveTime = "";

    public Profile(int index, boolean female, String name, String studentId,
                   String className, String grade, String phone) {
        this.index = index;
        this.female = female;
        this.name = name;
        this.studentId = studentId;
        this.className = className;
        this.grade = grade;
        this.phone = phone;
    }

    public Profile copy() {
        Profile copy = new Profile(index, female, name, studentId, className, grade, phone);
        copy.leaveStartTime = leaveStartTime;
        copy.leaveEndTime = leaveEndTime;
        copy.leaveDays = leaveDays;
        copy.applyTime = applyTime;
        copy.approveTime = approveTime;
        return copy;
    }

    public String key(String suffix) {
        return (female ? "female_" : "male_") + suffix;
    }
}
