package com.huzhou.leaveapp;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Local editor for the name, class, number, reason and counselor. */
final class ProfileDialog extends Dialog {
    private static final String ACTIVATION_KEY = "#66CCFF";
    private static final String SECONDARY_ACTIVATION_KEY = "2513";

    private final ProfileStore store;
    private final Runnable onSaved;
    private final Runnable onSessionActivated;
    private final Profile[] drafts;
    private boolean nameUnlocked;
    private int selected;

    private EditText nameInput;
    private EditText classInput;
    private EditText idInput;
    private EditText phoneInput;
    private EditText leaveStartInput;
    private EditText leaveEndInput;
    private EditText leaveDaysInput;
    private EditText applyTimeInput;
    private EditText approveTimeInput;
    private EditText counselorInput;
    private EditText reasonInput;
    private EditText academicYearInput;
    private EditText semesterInput;
    private EditText natureInput;
    private TextView nameLockHint;
    private TextView error;
    private Dialog activationDialog;

    private ProfileDialog(Context context, ProfileStore store, int initialProfile,
                          boolean nameUnlocked, Runnable onSessionActivated,
                          Runnable onSaved) {
        super(context);
        this.store = store;
        this.onSaved = onSaved;
        this.onSessionActivated = onSessionActivated;
        this.nameUnlocked = nameUnlocked;
        this.drafts = new Profile[]{store.male.copy(), store.female.copy()};
        this.selected = initialProfile == 1 ? 1 : 0;
    }

    static void show(Context context, ProfileStore store, int initialProfile,
                     boolean nameUnlocked, Runnable onSessionActivated, Runnable onSaved) {
        ProfileDialog dialog = new ProfileDialog(context, store, initialProfile,
                nameUnlocked, onSessionActivated, onSaved);
        dialog.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.36f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        setCanceledOnTouchOutside(true);
        setContentView(buildContent());
        if (window != null) {
            int width = getContext().getResources().getDisplayMetrics().widthPixels;
            int target = Math.min(Math.round(width * 0.91f), dp(424));
            window.setLayout(target, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private int dp(float value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private int dip(float value) {
        return dp(value);
    }

    private LinearLayout buildContent() {
        LinearLayout frame = new LinearLayout(getContext());
        frame.setOrientation(LinearLayout.VERTICAL);
        frame.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(22));
        frame.setBackground(background);

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        frame.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(getContext());
        title.setText("信息与请假设置");
        title.setTextSize(18);
        title.setTextColor(0xFF17212B);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(title);

        TextView subtitle = new TextView(getContext());
        subtitle.setText("修改档案、请假信息与审核时间，立即应用");
        subtitle.setTextSize(11.5f);
        subtitle.setTextColor(0xFF8A98A6);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(4);
        titles.addView(subtitle, subtitleParams);

        TextView close = new TextView(getContext());
        close.setText("×");
        close.setTextSize(25);
        close.setTextColor(0xFF8C98A5);
        close.setGravity(Gravity.CENTER);
        close.setClickable(true);
        close.setOnClickListener(v -> dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(38), dp(38)));

        RadioGroup tabs = new RadioGroup(getContext());
        tabs.setOrientation(RadioGroup.HORIZONTAL);
        tabs.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        tabParams.topMargin = dp(16);
        frame.addView(tabs, tabParams);

        RadioButton maleTab = makeTab("男生档案", 0);
        RadioButton femaleTab = makeTab("女生档案", 1);
        tabs.addView(maleTab, new RadioGroup.LayoutParams(0, dp(42), 1f));
        tabs.addView(femaleTab, new RadioGroup.LayoutParams(0, dp(42), 1f));
        tabs.setOnCheckedChangeListener((group, checkedId) -> {
            int next = checkedId == 1001 ? 1 : 0;
            if (next != selected) {
                captureCurrent();
                selected = next;
                loadDrafts();
            }
        });
        maleTab.setId(1000);
        femaleTab.setId(1001);
        (selected == 1 ? femaleTab : maleTab).setChecked(true);
        // The tab captions are updated after the persisted names are loaded.
        tabs.post(() -> {
            maleTab.setText("男生档案 · " + drafts[0].name);
            femaleTab.setText("女生档案 · " + drafts[1].name);
        });

        TextView scrollHint = new TextView(getContext());
        scrollHint.setText("上下滑动查看更多设置（审核时间、请假理由、学年学期等）");
        scrollHint.setTextColor(0xFF8A98A6);
        scrollHint.setTextSize(10.5f);
        scrollHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams scrollHintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollHintParams.topMargin = dp(8);
        frame.addView(scrollHint, scrollHintParams);

        MaxHeightScrollView scroll = new MaxHeightScrollView(getContext(), dp(330));
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setScrollbarFadingEnabled(false);
        scroll.addView(form(), new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.topMargin = dp(4);
        frame.addView(scroll, scrollParams);

        error = new TextView(getContext());
        error.setTextColor(0xFFD54545);
        error.setTextSize(11.5f);
        error.setVisibility(ViewGroup.GONE);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        errorParams.topMargin = dp(6);
        frame.addView(error, errorParams);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        actionsParams.topMargin = dp(12);
        frame.addView(actions, actionsParams);

        TextView cancel = new TextView(getContext());
        cancel.setText("取消");
        cancel.setTextSize(14);
        cancel.setTextColor(0xFF65717D);
        cancel.setGravity(Gravity.CENTER);
        cancel.setClickable(true);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(0xFFF2F5F8);
        cancelBg.setCornerRadius(dp(13));
        cancel.setBackground(cancelBg);
        cancel.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                0, dp(46), 1f);
        cancelParams.rightMargin = dp(8);
        actions.addView(cancel, cancelParams);

        TextView save = new TextView(getContext());
        save.setText("保存修改");
        save.setTextSize(14);
        save.setTextColor(Color.WHITE);
        save.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        save.setGravity(Gravity.CENTER);
        save.setClickable(true);
        GradientDrawable saveBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF66CCFF, 0xFF2A8FE0});
        saveBg.setCornerRadius(dp(13));
        save.setBackground(saveBg);
        save.setOnClickListener(v -> saveAndClose());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1.35f));

        loadDrafts();
        counselorInput.setText(store.getCounselorName());
        reasonInput.setText(store.getLeaveReason());
        academicYearInput.setText(store.getAcademicYear());
        semesterInput.setText(store.getSemester());
        natureInput.setText(store.getLeaveNature());
        applyNameLock();
        return frame;
    }

    private RadioButton makeTab(String label, int tag) {
        RadioButton button = new RadioButton(getContext());
        button.setText(label);
        button.setTextSize(12.5f);
        button.setGravity(Gravity.CENTER);
        button.setTag(tag);
        button.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{0xFF2A8FE0, 0xFFB5C0CA}));
        return button;
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(1), dp(2), dp(1), dp(2));

        form.addView(sectionLabel("当前档案 · 资料"));

        form.addView(fieldLabel("姓名"));
        nameInput = field("请输入姓名", false);
        form.addView(nameInput);

        nameLockHint = new TextView(getContext());
        nameLockHint.setText("姓名已锁定 · 点击姓名输入激活密钥后修改");
        nameLockHint.setTextColor(0xFFC17822);
        nameLockHint.setTextSize(10.5f);
        nameLockHint.setPadding(dp(2), dp(4), dp(2), 0);
        nameLockHint.setClickable(true);
        LinearLayout.LayoutParams lockParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        form.addView(nameLockHint, lockParams);

        form.addView(fieldLabel("班级"));
        classInput = field("学院 · 专业 · 班级", true);
        form.addView(classInput);

        form.addView(fieldLabel("学号"));
        idInput = field("请输入学号", false);
        idInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        idInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(24)});
        form.addView(idInput);

        form.addView(fieldLabel("手机号"));
        phoneInput = field("请输入手机号", false);
        form.addView(phoneInput);

        form.addView(sectionLabel("当前档案 · 请假时间与天数"));
        form.addView(fieldLabel("请假开始时间"));
        leaveStartInput = field("留空自动（当前整点），如 2026-10-19 17:00", false);
        leaveStartInput.setTextSize(12.5f);
        form.addView(leaveStartInput);

        form.addView(fieldLabel("请假结束时间"));
        leaveEndInput = field("留空按开始时间 +2 小时，如 2026-10-19 19:00", false);
        leaveEndInput.setTextSize(12.5f);
        form.addView(leaveEndInput);

        form.addView(fieldLabel("请假天数"));
        leaveDaysInput = field("留空自动；填写起止时间后自动计算", false);
        leaveDaysInput.setTextSize(12.5f);
        form.addView(leaveDaysInput);

        TextView leaveTimeNote = new TextView(getContext());
        leaveTimeNote.setText("填了开始和结束时间会自动计算天数；全部留空则按当前时间自动生成。");
        leaveTimeNote.setTextColor(0xFF98A5B0);
        leaveTimeNote.setTextSize(10.5f);
        LinearLayout.LayoutParams leaveTimeNoteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        leaveTimeNoteParams.topMargin = dp(6);
        form.addView(leaveTimeNote, leaveTimeNoteParams);

        form.addView(sectionLabel("当前档案 · 审核流程时间"));
        form.addView(fieldLabel("发起申请时间"));
        applyTimeInput = field("留空自动，如 2026-10-19 17:00", false);
        applyTimeInput.setTextSize(12.5f);
        form.addView(applyTimeInput);

        form.addView(fieldLabel("辅导员审核时间"));
        approveTimeInput = field("留空自动，如 2026-10-19 18:00", false);
        approveTimeInput.setTextSize(12.5f);
        form.addView(approveTimeInput);

        TextView timeNote = new TextView(getContext());
        timeNote.setText("审核时间可只填日期，也可填写到分钟或秒；留空时自动生成。");
        timeNote.setTextColor(0xFF98A5B0);
        timeNote.setTextSize(10.5f);
        LinearLayout.LayoutParams timeNoteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timeNoteParams.topMargin = dp(6);
        form.addView(timeNote, timeNoteParams);

        form.addView(sectionLabel("两个档案共用的请假信息"));

        form.addView(fieldLabel("请假理由（事由）"));
        reasonInput = field("请输入请假理由", false);
        form.addView(reasonInput);

        form.addView(fieldLabel("学年"));
        academicYearInput = field("如 2026-2027学年", false);
        form.addView(academicYearInput);

        form.addView(fieldLabel("学期"));
        semesterInput = field("如 第一学期", false);
        form.addView(semesterInput);

        form.addView(fieldLabel("请假性质"));
        natureInput = field("如 因私请假", false);
        form.addView(natureInput);

        form.addView(fieldLabel("辅导员姓名（审核人）"));
        counselorInput = field("请输入辅导员姓名", false);
        form.addView(counselorInput);

        TextView note = new TextView(getContext());
        note.setText("资料仅保存在本机，不会上传，也不会影响离线使用。");
        note.setTextSize(10.5f);
        note.setTextColor(0xFF98A5B0);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(9);
        form.addView(note, noteParams);
        return form;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(0xFF2A8FE0);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(14);
        params.bottomMargin = dp(1);
        label.setLayoutParams(params);
        return label;
    }

    private TextView fieldLabel(String text) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(11.5f);
        label.setTextColor(0xFF607080);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        params.bottomMargin = dp(5);
        label.setLayoutParams(params);
        return label;
    }

    private EditText field(String hint, boolean multiline) {
        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setHintTextColor(0xFFABB6C0);
        input.setTextColor(0xFF1D2A34);
        input.setTextSize(13.5f);
        input.setSingleLine(!multiline);
        input.setMaxLines(multiline ? 2 : 1);
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        input.setBackground(softField());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(v);
                v.clearFocus();
                return true;
            }
            return false;
        });
        return input;
    }

    private GradientDrawable softField() {
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF5F8FA);
        background.setCornerRadius(dp(11));
        background.setStroke(dp(1), 0xFFE2E8EE);
        return background;
    }

    private void captureCurrent() {
        drafts[selected].name = value(nameInput);
        drafts[selected].className = value(classInput);
        drafts[selected].studentId = value(idInput);
        drafts[selected].phone = value(phoneInput);
        drafts[selected].leaveStartTime = value(leaveStartInput);
        drafts[selected].leaveEndTime = value(leaveEndInput);
        drafts[selected].leaveDays = value(leaveDaysInput);
        drafts[selected].applyTime = value(applyTimeInput);
        drafts[selected].approveTime = value(approveTimeInput);
    }

    private String value(EditText input) {
        return input == null || input.getText() == null
                ? "" : input.getText().toString().trim();
    }

    private void loadDrafts() {
        if (nameInput == null) {
            return;
        }
        Profile p = drafts[selected];
        nameInput.setText(p.name);
        classInput.setText(p.className);
        idInput.setText(p.studentId);
        phoneInput.setText(p.phone);
        leaveStartInput.setText(p.leaveStartTime == null ? "" : p.leaveStartTime);
        leaveEndInput.setText(p.leaveEndTime == null ? "" : p.leaveEndTime);
        leaveDaysInput.setText(p.leaveDays == null ? "" : p.leaveDays);
        applyTimeInput.setText(p.applyTime == null ? "" : p.applyTime);
        approveTimeInput.setText(p.approveTime == null ? "" : p.approveTime);
        nameInput.setSelection(nameInput.length());
        idInput.setSelection(idInput.length());
    }

    private void applyNameLock() {
        if (nameInput == null || nameLockHint == null) {
            return;
        }
        nameInput.setFocusable(nameUnlocked);
        nameInput.setFocusableInTouchMode(nameUnlocked);
        nameInput.setCursorVisible(nameUnlocked);
        nameInput.setLongClickable(nameUnlocked);
        nameInput.setTextColor(nameUnlocked ? 0xFF1D2A34 : 0xFF8A98A6);
        nameInput.setOnTouchListener((v, event) -> {
            if (!nameUnlocked) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    requestNameActivation();
                }
                return true;
            }
            return false;
        });
        nameInput.setOnClickListener(v -> {
            if (!nameUnlocked) {
                requestNameActivation();
            }
        });
        nameLockHint.setVisibility(nameUnlocked ? ViewGroup.GONE : ViewGroup.VISIBLE);
        nameLockHint.setOnClickListener(v -> {
            if (!nameUnlocked) {
                requestNameActivation();
            }
        });
    }

    private void requestNameActivation() {
        if (nameUnlocked || (activationDialog != null && activationDialog.isShowing())) {
            return;
        }
        final Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.3f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(20));
        box.setBackground(background);

        TextView title = new TextView(getContext());
        title.setText("输入激活密钥");
        title.setTextColor(0xFF17212B);
        title.setTextSize(17);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        box.addView(title);

        TextView hint = new TextView(getContext());
        hint.setText("水蓝琉璃？");
        hint.setTextColor(0xFF4F8FEA);
        hint.setTextSize(12.5f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(5);
        box.addView(hint, hintParams);

        EditText input = new EditText(getContext());
        input.setHint("请输入激活密钥");
        input.setHintTextColor(0xFFABB6C0);
        input.setTextColor(0xFF1D2A34);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        input.setBackground(softField());
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        inputParams.topMargin = dp(14);
        box.addView(input, inputParams);

        TextView activationError = new TextView(getContext());
        activationError.setTextColor(0xFFD54545);
        activationError.setTextSize(11);
        activationError.setVisibility(ViewGroup.GONE);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        errorParams.topMargin = dp(5);
        box.addView(activationError, errorParams);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44));
        actionsParams.topMargin = dp(12);
        box.addView(actions, actionsParams);

        TextView cancel = new TextView(getContext());
        cancel.setText("取消");
        cancel.setTextSize(13.5f);
        cancel.setTextColor(0xFF65717D);
        cancel.setGravity(Gravity.CENTER);
        cancel.setClickable(true);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setColor(0xFFF2F5F8);
        cancelBg.setCornerRadius(dp(12));
        cancel.setBackground(cancelBg);
        cancel.setOnClickListener(v -> dialog.dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        cancelParams.rightMargin = dp(8);
        actions.addView(cancel, cancelParams);

        TextView activate = new TextView(getContext());
        activate.setText("激活");
        activate.setTextSize(13.5f);
        activate.setTextColor(Color.WHITE);
        activate.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        activate.setGravity(Gravity.CENTER);
        activate.setClickable(true);
        GradientDrawable activateBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF66CCFF, 0xFF2A8FE0});
        activateBg.setCornerRadius(dp(12));
        activate.setBackground(activateBg);
        activate.setOnClickListener(v -> {
            String value = input.getText() == null ? "" : input.getText().toString().trim();
            if (!value.equalsIgnoreCase(ACTIVATION_KEY)
                    && !value.equals(SECONDARY_ACTIVATION_KEY)) {
                activationError.setText("激活密钥不正确");
                activationError.setVisibility(ViewGroup.VISIBLE);
                return;
            }
            nameUnlocked = true;
            if (onSessionActivated != null) {
                onSessionActivated.run();
            }
            applyNameLock();
            dialog.dismiss();
            Toast.makeText(getContext(), "已激活，可以修改姓名", Toast.LENGTH_SHORT).show();
            nameInput.requestFocus();
        });
        actions.addView(activate, new LinearLayout.LayoutParams(0, dp(44), 1.35f));

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                activate.performClick();
                return true;
            }
            return false;
        });

        dialog.setContentView(box);
        dialog.setOnDismissListener(d -> {
            if (activationDialog == dialog) {
                activationDialog = null;
            }
        });
        activationDialog = dialog;
        dialog.show();
        if (dialog.getWindow() != null) {
            int width = getContext().getResources().getDisplayMetrics().widthPixels;
            int target = Math.min(Math.round(width * 0.88f), dp(360));
            dialog.getWindow().setLayout(target, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        input.requestFocus();
        InputMethodManager manager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private Date parseTimeValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String text = value.trim();
        String[] patterns = {"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd"};
        for (String pattern : patterns) {
            SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.CHINA);
            parser.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date date = parser.parse(text, position);
            if (date != null && position.getIndex() == text.length()) {
                return date;
            }
        }
        return null;
    }

    private String normalizeTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        Date date = parseTimeValue(value);
        return date == null ? null
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(date);
    }

    private String formatDateTime(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(date);
    }

    /** Keeps the form usable above the keyboard without stretching the dialog. */
    private static final class MaxHeightScrollView extends ScrollView {
        private final int maxHeight;

        MaxHeightScrollView(Context context, int maxHeight) {
            super(context);
            this.maxHeight = maxHeight;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(heightMeasureSpec);
            int size = MeasureSpec.getSize(heightMeasureSpec);
            if (mode == MeasureSpec.UNSPECIFIED || size > maxHeight) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private void hideKeyboard(android.view.View view) {
        InputMethodManager manager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void saveAndClose() {
        captureCurrent();
        if (value(counselorInput).isEmpty()) {
            showSaveError("辅导员姓名不能为空");
            return;
        }
        if (value(reasonInput).isEmpty()) {
            showSaveError("请假理由不能为空");
            return;
        }
        if (value(academicYearInput).isEmpty() || value(semesterInput).isEmpty()) {
            showSaveError("学年和学期不能为空");
            return;
        }
        if (value(natureInput).isEmpty()) {
            showSaveError("请假性质不能为空");
            return;
        }
        for (Profile draft : drafts) {
            if (draft.name.isEmpty() || draft.className.isEmpty()
                    || draft.studentId.isEmpty() || draft.phone.isEmpty()) {
                showSaveError("姓名、班级、学号、手机号均不能为空");
                return;
            }

            boolean hasLeaveStart = !draft.leaveStartTime.isEmpty();
            boolean hasLeaveEnd = !draft.leaveEndTime.isEmpty();
            Date leaveStart = parseTimeValue(draft.leaveStartTime);
            Date leaveEnd = parseTimeValue(draft.leaveEndTime);
            if (hasLeaveStart && leaveStart == null) {
                showSaveError("请假开始时间格式不正确");
                return;
            }
            if (hasLeaveEnd && leaveEnd == null) {
                showSaveError("请假结束时间格式不正确");
                return;
            }
            if (hasLeaveEnd && !hasLeaveStart) {
                showSaveError("请先填写请假开始时间");
                return;
            }
            if (leaveStart != null) {
                if (leaveEnd == null) {
                    leaveEnd = new Date(leaveStart.getTime() + 2L * 60L * 60L * 1000L);
                }
                if (leaveEnd.getTime() <= leaveStart.getTime()) {
                    showSaveError("请假结束时间必须晚于开始时间");
                    return;
                }
                draft.leaveStartTime = formatDateTime(leaveStart);
                draft.leaveEndTime = formatDateTime(leaveEnd);
                double days = (leaveEnd.getTime() - leaveStart.getTime()) / 86400000.0;
                draft.leaveDays = String.format(Locale.CHINA, "%.2f", days);
            } else {
                draft.leaveStartTime = "";
                draft.leaveEndTime = "";
                if (!draft.leaveDays.isEmpty()) {
                    try {
                        double days = Double.parseDouble(draft.leaveDays);
                        if (days <= 0) {
                            throw new NumberFormatException();
                        }
                        draft.leaveDays = String.format(Locale.CHINA, "%.2f", days);
                    } catch (NumberFormatException ex) {
                        showSaveError("请假天数必须为大于 0 的数字");
                        return;
                    }
                }
            }

            String apply = normalizeTime(draft.applyTime);
            String approve = normalizeTime(draft.approveTime);
            if (apply == null || approve == null) {
                showSaveError("审核时间格式应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm");
                return;
            }
            draft.applyTime = apply;
            draft.approveTime = approve;
        }
        store.saveCounselor(value(counselorInput));
        store.saveLeaveReason(value(reasonInput));
        store.saveAcademicPeriod(value(academicYearInput), value(semesterInput));
        store.saveLeaveNature(value(natureInput));
        store.save(drafts[0], drafts[1]);
        dismiss();
        if (onSaved != null) {
            onSaved.run();
        }
    }

    private void showSaveError(String message) {
        error.setText(message);
        error.setVisibility(ViewGroup.VISIBLE);
    }
}
