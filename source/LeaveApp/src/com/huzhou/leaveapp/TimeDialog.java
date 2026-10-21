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
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Editor for the timestamps of one leave record, opened from the detail menu. */
final class TimeDialog extends Dialog {
    interface Callback {
        void onSaved();
    }

    private final ProfileStore store;
    private final int recordIndex;
    private final Callback callback;
    private final String[] initial;
    private EditText startInput;
    private EditText endInput;
    private EditText daysInput;
    private EditText applyInput;
    private EditText approveInput;
    private TextView error;

    private TimeDialog(Context context, ProfileStore store, int recordIndex,
                       String[] initial, Callback callback) {
        super(context);
        this.store = store;
        this.recordIndex = recordIndex;
        this.initial = initial;
        this.callback = callback;
    }

    static void show(Context context, ProfileStore store, int recordIndex,
                     String start, String end, String days,
                     String apply, String approve, Callback callback) {
        new TimeDialog(context, store, recordIndex,
                new String[]{start, end, days, apply, approve}, callback).show();
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
        title.setText("自定义时间");
        title.setTextSize(18);
        title.setTextColor(0xFF17212B);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titles.addView(title);

        TextView subtitle = new TextView(getContext());
        subtitle.setText("只修改当前这一条记录，留空则恢复自动生成");
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
        scroll.addView(form(), new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(350));
        scrollParams.topMargin = dp(8);
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
        save.setText("保存时间");
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
        return card;
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(getContext());
        form.setOrientation(LinearLayout.VERTICAL);
        startInput = field(form, "请假开始时间", "如 2026-10-19 17:00");
        endInput = field(form, "请假结束时间", "留空为开始时间 +2 小时");
        daysInput = field(form, "请假天数", "留空自动按起止时间计算");
        applyInput = field(form, "发起申请时间", "留空自动");
        approveInput = field(form, "辅导员审核时间", "留空自动");

        startInput.setText(initial[0]);
        endInput.setText(initial[1]);
        daysInput.setText(initial[2]);
        applyInput.setText(initial[3]);
        approveInput.setText(initial[4]);

        TextView note = new TextView(getContext());
        note.setText("时间格式：yyyy-MM-dd HH:mm。留空保存后恢复系统自动时间。");
        note.setTextSize(10.5f);
        note.setTextColor(0xFF98A5B0);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(10);
        form.addView(note, noteParams);
        return form;
    }

    private EditText field(LinearLayout parent, String label, String hint) {
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
        input.setHint(hint);
        input.setTextSize(13f);
        input.setSingleLine(true);
        input.setTextColor(0xFF1D2A34);
        input.setHintTextColor(0xFFABB6C0);
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFFF5F8FA);
        background.setCornerRadius(dp(11));
        background.setStroke(dp(1), 0xFFE2E8EE);
        input.setBackground(background);
        parent.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return input;
    }

    private void save() {
        String start = text(startInput);
        String end = text(endInput);
        String days = text(daysInput);
        String apply = text(applyInput);
        String approve = text(approveInput);

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        format.setLenient(false);
        Date startDate = null;
        Date endDate = null;
        if (!start.isEmpty()) {
            try {
                startDate = format.parse(start);
            } catch (ParseException ex) {
                showError("开始时间格式应为 yyyy-MM-dd HH:mm");
                return;
            }
        }
        if (!end.isEmpty()) {
            if (startDate == null) {
                showError("请先填写开始时间");
                return;
            }
            try {
                endDate = format.parse(end);
            } catch (ParseException ex) {
                showError("结束时间格式应为 yyyy-MM-dd HH:mm");
                return;
            }
            if (endDate.getTime() <= startDate.getTime()) {
                showError("结束时间必须晚于开始时间");
                return;
            }
        } else if (startDate != null) {
            endDate = new Date(startDate.getTime() + 2L * 60L * 60L * 1000L);
        }

        if (startDate != null) {
            start = format.format(startDate);
            end = format.format(endDate);
            days = String.format(Locale.CHINA, "%.2f",
                    (endDate.getTime() - startDate.getTime()) / 86400000.0);
        } else {
            start = "";
            end = "";
            days = normalizeDays(days);
            if (days == null) {
                showError("请假天数必须为大于 0 的数字");
                return;
            }
        }

        apply = normalizeTime(apply, format);
        approve = normalizeTime(approve, format);
        if (apply == null || approve == null) {
            showError("申请时间或审核时间格式不正确");
            return;
        }

        store.saveRecordValue(recordIndex, "start", start);
        store.saveRecordValue(recordIndex, "end", end);
        store.saveRecordValue(recordIndex, "days", days);
        store.saveRecordValue(recordIndex, "apply", apply);
        store.saveRecordValue(recordIndex, "approve", approve);
        dismiss();
        if (callback != null) {
            callback.onSaved();
        }
    }

    private String normalizeDays(String value) {
        if (value.isEmpty()) {
            return "";
        }
        try {
            double days = Double.parseDouble(value);
            if (days <= 0) {
                return null;
            }
            return String.format(Locale.CHINA, "%.2f", days);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeTime(String value, SimpleDateFormat format) {
        if (value.isEmpty()) {
            return "";
        }
        String text = value;
        if (text.length() == 10) {
            text = text + " 00:00";
        }
        try {
            Date date = format.parse(text);
            return format.format(date);
        } catch (ParseException ex) {
            return null;
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
