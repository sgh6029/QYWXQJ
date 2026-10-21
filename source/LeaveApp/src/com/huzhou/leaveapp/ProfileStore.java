package com.huzhou.leaveapp;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/** Offline persistence for the activation gate and the editable records. */
public final class ProfileStore {
    private static final String PREFS = "leave_app_private";
    private static final String KEY_VERIFIED = "first_run_verified";
    /** New key: activation is permanent until the app data is cleared. */
    private static final String KEY_ACTIVATED = "activation_persisted_v2";
    private static final String DEMO_PHONE = "12345678901";
    private static final String KEY_COUNSELOR = "counselor_name";
    private static final String KEY_REASON = "leave_reason";
    private static final String KEY_ACADEMIC_YEAR = "academic_year";
    private static final String KEY_SEMESTER = "semester";
    private static final String KEY_NATURE = "leave_nature";
    private static final String KEY_ME_NAME = "me_display_name";
    private static final String KEY_ME_AVATAR = "me_avatar_choice";
    private static final String KEY_ME_AVATAR_PATH = "me_avatar_path";
    private static final String KEY_GENDER = "active_gender";
    private static final String KEY_RECORD_PREFIX = "record_";

    private final SharedPreferences preferences;
    public final Profile male;
    public final Profile female;
    private String counselorName;
    private String leaveReason;
    private String academicYear;
    private String semester;
    private String leaveNature;
    private String meName;
    private int meAvatar;
    private int gender;

    public ProfileStore(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Neutral demo data only. Personal names, real student numbers and
        // telephone numbers are intentionally not shipped with the app.
        male = new Profile(
                0,
                false,
                "示例男生",
                "2025000001",
                "健康医学院 · 医学检验技术 · 示例班级",
                "2025级",
                DEMO_PHONE);
        female = new Profile(
                1,
                true,
                "示例女生",
                "2025000002",
                "健康医学院 · 医学检验技术 · 示例班级",
                "2025级",
                DEMO_PHONE);
        load(male);
        load(female);
        counselorName = preferences.getString(KEY_COUNSELOR, "辅导员");
        leaveReason = preferences.getString(KEY_REASON, "拿快递");
        String[] academic = defaultAcademicPeriod();
        academicYear = preferences.getString(KEY_ACADEMIC_YEAR, academic[0]);
        semester = preferences.getString(KEY_SEMESTER, academic[1]);
        leaveNature = preferences.getString(KEY_NATURE, "因私请假");
        meName = preferences.getString(KEY_ME_NAME, "示例用户");
        meAvatar = preferences.getInt(KEY_ME_AVATAR, 2);
        gender = preferences.getInt(KEY_GENDER, 1) == 0 ? 0 : 1;
    }

    /** 0 = male, 1 = female. */
    public int getGender() {
        return gender;
    }

    public void saveGender(int value) {
        gender = value == 0 ? 0 : 1;
        preferences.edit().putInt(KEY_GENDER, gender).apply();
    }

    /** The profile that the watermark, leave list and detail use. */
    public Profile getUser() {
        return get(gender);
    }

    public String getRecordValue(int index, String field) {
        return preferences.getString(KEY_RECORD_PREFIX + index + "_" + field, "");
    }

    public void saveRecordValue(int index, String field, String value) {
        preferences.edit()
                .putString(KEY_RECORD_PREFIX + index + "_" + field,
                        value == null ? "" : value.trim())
                .apply();
    }

    public String getMeAvatarPath() {
        return preferences.getString(KEY_ME_AVATAR_PATH, "");
    }

    public void saveMeAvatarPath(String path) {
        preferences.edit().putString(KEY_ME_AVATAR_PATH, path == null ? "" : path).apply();
    }

    public String getMeName() {
        return meName;
    }

    public void saveMeName(String name) {
        meName = name == null ? "" : name.trim();
        preferences.edit().putString(KEY_ME_NAME, meName).apply();
    }

    public int getMeAvatar() {
        return meAvatar;
    }

    public void saveMeAvatar(int choice) {
        meAvatar = choice;
        preferences.edit().putInt(KEY_ME_AVATAR, choice).apply();
    }

    private String[] defaultAcademicPeriod() {
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        if (month >= 9) {
            return new String[]{year + "-" + (year + 1) + "学年", "第一学期"};
        }
        if (month >= 2) {
            return new String[]{(year - 1) + "-" + year + "学年", "第二学期"};
        }
        return new String[]{(year - 1) + "-" + year + "学年", "第一学期"};
    }

    private void load(Profile p) {
        p.name = preferences.getString(p.key("name"), p.name);
        p.studentId = preferences.getString(p.key("id"), p.studentId);
        p.className = preferences.getString(p.key("class"), p.className);
        p.phone = preferences.getString(p.key("phone"), p.phone);
        // Remove old masked demo numbers from earlier builds and keep the
        // default number fully visible and editable.
        if (p.phone == null || p.phone.trim().isEmpty() || p.phone.contains("*")) {
            p.phone = DEMO_PHONE;
            preferences.edit().putString(p.key("phone"), p.phone).apply();
        }
        p.leaveStartTime = preferences.getString(p.key("leave_start"), "");
        p.leaveEndTime = preferences.getString(p.key("leave_end"), "");
        p.leaveDays = preferences.getString(p.key("leave_days"), "");
        p.applyTime = preferences.getString(p.key("apply_time"), "");
        p.approveTime = preferences.getString(p.key("approve_time"), "");
    }

    public Profile get(int index) {
        return index == 1 ? female : male;
    }

    public String getCounselorName() {
        return counselorName;
    }

    public void saveCounselor(String name) {
        String value = name == null ? "" : name.trim();
        counselorName = value.isEmpty() ? "辅导员" : value;
        preferences.edit().putString(KEY_COUNSELOR, counselorName).apply();
    }

    public String getLeaveReason() {
        return leaveReason;
    }

    public void saveLeaveReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        leaveReason = value.isEmpty() ? "拿快递" : value;
        preferences.edit().putString(KEY_REASON, leaveReason).apply();
    }

    public String getAcademicYear() {
        return academicYear;
    }

    public String getSemester() {
        return semester;
    }

    public void saveAcademicPeriod(String year, String term) {
        String yearValue = year == null ? "" : year.trim();
        String termValue = term == null ? "" : term.trim();
        academicYear = yearValue.isEmpty() ? defaultAcademicPeriod()[0] : yearValue;
        semester = termValue.isEmpty() ? defaultAcademicPeriod()[1] : termValue;
        preferences.edit()
                .putString(KEY_ACADEMIC_YEAR, academicYear)
                .putString(KEY_SEMESTER, semester)
                .apply();
    }

    public String getLeaveNature() {
        return leaveNature;
    }

    public void saveLeaveNature(String nature) {
        String value = nature == null ? "" : nature.trim();
        leaveNature = value.isEmpty() ? "因私请假" : value;
        preferences.edit().putString(KEY_NATURE, leaveNature).apply();
    }

    public boolean isVerified() {
        return preferences.getBoolean(KEY_VERIFIED, false);
    }

    public void markVerified() {
        preferences.edit().putBoolean(KEY_VERIFIED, true).apply();
    }

    /** Once this is true the activation gate is skipped on future launches. */
    public boolean isActivated() {
        return preferences.getBoolean(KEY_ACTIVATED, false);
    }

    public void markActivated(boolean activated) {
        preferences.edit().putBoolean(KEY_ACTIVATED, activated).apply();
    }

    public void save(Profile... profiles) {
        SharedPreferences.Editor editor = preferences.edit();
        for (Profile draft : profiles) {
            // Update the in-memory profile as well, so the visible detail page
            // refreshes immediately without waiting for the next launch.
            Profile target = get(draft.index);
            target.name = draft.name.trim();
            target.studentId = draft.studentId.trim();
            target.className = draft.className.trim();
            target.phone = draft.phone.trim();
            target.leaveStartTime = draft.leaveStartTime == null ? "" : draft.leaveStartTime.trim();
            target.leaveEndTime = draft.leaveEndTime == null ? "" : draft.leaveEndTime.trim();
            target.leaveDays = draft.leaveDays == null ? "" : draft.leaveDays.trim();
            target.applyTime = draft.applyTime == null ? "" : draft.applyTime.trim();
            target.approveTime = draft.approveTime == null ? "" : draft.approveTime.trim();
            editor.putString(target.key("name"), target.name);
            editor.putString(target.key("id"), target.studentId);
            editor.putString(target.key("class"), target.className);
            editor.putString(target.key("phone"), target.phone);
            editor.putString(target.key("leave_start"), target.leaveStartTime);
            editor.putString(target.key("leave_end"), target.leaveEndTime);
            editor.putString(target.key("leave_days"), target.leaveDays);
            editor.putString(target.key("apply_time"), target.applyTime);
            editor.putString(target.key("approve_time"), target.approveTime);
        }
        editor.apply();
    }
}
