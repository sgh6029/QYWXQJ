package com.huzhou.leaveapp;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Top-level "My settings" dialog.  It owns the personal information, the
 * gender switch and the shared leave settings.  The custom leave timestamps
 * deliberately live in {@link TimeDialog}, reached from the detail page menu.
 */
final class SettingsDialog extends Dialog {
    interface Callback {
        void onSaved();
    }

    private final ProfileStore store;
    private final Callback callback;
    private int gender;
    private boolean loading;

    private EditText nameInput;
    private EditText idInput;
    private EditText classInput;
    private EditText phoneInput;
    private EditText reasonInput;
    private EditText yearInput;
    private EditText termInput;
    private EditText natureInput;
    private EditText counselorInput;
    private TextView error;

    private SettingsDialog(Context context, ProfileStore store, Callback callback) {
        super(context);
        this.store = store;
        this.callback = callback;
        this.gender = store.getGender();
    }

    static void show(Context context, ProfileStore store, Callback callback) {
        new SettingsDialog(context, store, callback).show();
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.35f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        setContentView(build());
        if (window != null) {
            int width = getContext().getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.round(width * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    private LinearLayout build() {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(16));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(22));
        card.setBackground(background);

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(getContext());
        title.setText("我的设置");
        title.setTextSize(18);
        title.setTextColor(0xFF17212B);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(title);

        TextView subtitle = new TextView(getContext());
        subtitle.setText("个人信息会同步到请假记录与通讯录水印");
        subtitle.setTextSize(11.5f);
        subtitle.setTextColor(0xFF8A98A6);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
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

        ScrollView scroll = new ScrollView(getContext());
        scroll.setVerticalScrollBarEnabled(true);
        scroll.addView(form(), new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360));
        scrollParams.topMargin = dp(10);
        card.addView(scroll, scrollParams);

        error = new TextView(getContext());
        error.setTextColor(0xFFD54545);
        error.setTextSize(11.5f);
        error.setVisibility(ViewGroup.GONE);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        errorParams.topMargin = dp(6);
        card.addView(error, errorParams);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        actionsParams.topMargin = dp(12);
        card.addView(actions, actionsParams);

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
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(46), 1f);
        cancelParams.rightMargin = dp(8);
        actions.addView(cancel, cancelParams);

        TextView save = new TextView(getContext());
        save.setText("保存设置");
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
        save.setOnClickListener(v -> save());
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(46), 1.35f));

        loadFields();
        return card;
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(1), dp(2), dp(1), dp(2));

        form.addView(section("性别"));
        RadioGroup group = new RadioGroup(getContext());
        group.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton male = genderTab("男生");
        male.setId(2000);
        RadioButton female = genderTab("女生");
        female.setId(2001);
        group.addView(male, new RadioGroup.LayoutParams(0, dp(40), 1f));
        group.addView(female, new RadioGroup.LayoutParams(0, dp(40), 1f));
        (gender == 0 ? male : female).setChecked(true);
        group.setOnCheckedChangeListener((g, id) -> {
            if (loading) {
                return;
            }
            int next = id == 2001 ? 1 : 0;
            if (next != gender) {
                gender = next;
                loadFields();
            }
        });
        form.addView(group);

        form.addView(section("个人信息"));
        nameInput = field(form, "姓名", false);
        idInput = field(form, "学号", false);
        idInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        classInput = field(form, "学院 · 专业 · 班级", false);
        phoneInput = field(form, "手机号", false);
        phoneInput.setInputType(InputType.TYPE_CLASS_PHONE);

        form.addView(section("请假设置"));
        reasonInput = field(form, "请假理由（事由）", false);
        yearInput = field(form, "学年，如 2026-2027学年", false);
        termInput = field(form, "学期，如 第一学期", false);
        natureInput = field(form, "请假性质，如 因私请假", false);
        counselorInput = field(form, "辅导员姓名（审核人）", false);

        TextView note = new TextView(getContext());
        note.setText("资料只保存在本机。自定义时间请到请假详情右上角修改。");
        note.setTextSize(10.5f);
        note.setTextColor(0xFF98A5B0);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(10);
        form.addView(note, noteParams);
        return form;
    }

    private RadioButton genderTab(String label) {
        RadioButton button = new RadioButton(getContext());
        button.setText(label);
        button.setTextSize(12.5f);
        button.setGravity(Gravity.CENTER);
        button.setTextColor(0xFF35414D);
        button.setButtonTintList(new android.content.res.ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{0xFF2A8FE0, 0xFFB5C0CA}));
        return button;
    }

    private TextView section(String text) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(11);
        label.setTextColor(0xFF2A8FE0);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        label.setLayoutParams(params);
        return label;
    }

    private EditText field(LinearLayout parent, String label, boolean multiline) {
        TextView fieldLabel = new TextView(getContext());
        fieldLabel.setText(label);
        fieldLabel.setTextSize(11.5f);
        fieldLabel.setTextColor(0xFF607080);
        fieldLabel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(10);
        labelParams.bottomMargin = dp(5);
        parent.addView(fieldLabel, labelParams);

        EditText input = new EditText(getContext());
        input.setTextColor(0xFF1D2A34);
        input.setHintTextColor(0xFFABB6C0);
        input.setTextSize(13.5f);
        input.setSingleLine(!multiline);
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF5F8FA);
        background.setCornerRadius(dp(11));
        background.setStroke(dp(1), 0xFFE2E8EE);
        input.setBackground(background);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        parent.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private void loadFields() {
        loading = true;
        Profile p = store.get(gender);
        nameInput.setText(p.name);
        idInput.setText(p.studentId);
        classInput.setText(p.className);
        phoneInput.setText(p.phone);
        reasonInput.setText(store.getLeaveReason());
        yearInput.setText(store.getAcademicYear());
        termInput.setText(store.getSemester());
        natureInput.setText(store.getLeaveNature());
        counselorInput.setText(store.getCounselorName());
        error.setVisibility(ViewGroup.GONE);
        loading = false;
    }

    private void save() {
        String name = text(nameInput);
        String id = text(idInput);
        String className = text(classInput);
        String phone = text(phoneInput);
        if (name.isEmpty() || id.isEmpty() || className.isEmpty() || phone.isEmpty()) {
            showError("姓名、学号、班级、手机号均不能为空");
            return;
        }
        if (text(yearInput).isEmpty() || text(termInput).isEmpty()) {
            showError("学年和学期不能为空");
            return;
        }
        if (text(reasonInput).isEmpty() || text(natureInput).isEmpty()
                || text(counselorInput).isEmpty()) {
            showError("请假理由、性质和辅导员不能为空");
            return;
        }

        Profile target = store.get(gender);
        target.name = name;
        target.studentId = id;
        target.className = className;
        target.phone = phone;
        store.save(target);
        store.saveGender(gender);
        store.saveMeName(name);
        if (store.getMeAvatar() != 3) {
            store.saveMeAvatar(gender == 0 ? 1 : 2);
        }
        store.saveLeaveReason(text(reasonInput));
        store.saveAcademicPeriod(text(yearInput), text(termInput));
        store.saveLeaveNature(text(natureInput));
        store.saveCounselor(text(counselorInput));
        dismiss();
        if (callback != null) {
            callback.onSaved();
        }
    }

    private String text(EditText input) {
        return input == null || input.getText() == null
                ? "" : input.getText().toString().trim();
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(ViewGroup.VISIBLE);
    }
}
