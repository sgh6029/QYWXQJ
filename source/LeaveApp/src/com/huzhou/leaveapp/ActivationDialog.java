package com.huzhou.leaveapp;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Short activation prompt used when a trial session tries to edit data. */
final class ActivationDialog extends Dialog {
    private static final String ACTIVATION_KEY = "#66CCFF";
    private static final String SECONDARY_ACTIVATION_KEY = "2513";

    private final Runnable onActivated;

    private ActivationDialog(Context context, Runnable onActivated) {
        super(context);
        this.onActivated = onActivated;
    }

    static void show(Context context, Runnable onActivated) {
        ActivationDialog dialog = new ActivationDialog(context, onActivated);
        dialog.show();
    }

    private int dp(float value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
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

        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(20));
        box.setBackground(background);

        TextView title = new TextView(getContext());
        title.setText("输入激活码");
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
        input.setHint("请输入激活码");
        input.setHintTextColor(0xFFABB6C0);
        input.setTextColor(0xFF1D2A34);
        input.setTextSize(15);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable fieldBackground = new GradientDrawable();
        fieldBackground.setColor(0xFFF5F8FA);
        fieldBackground.setCornerRadius(dp(11));
        fieldBackground.setStroke(dp(1), 0xFFE2E8EE);
        input.setBackground(fieldBackground);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        inputParams.topMargin = dp(14);
        box.addView(input, inputParams);

        TextView error = new TextView(getContext());
        error.setTextColor(0xFFD54545);
        error.setTextSize(11);
        error.setVisibility(ViewGroup.GONE);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        errorParams.topMargin = dp(5);
        box.addView(error, errorParams);

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
        cancel.setOnClickListener(v -> dismiss());
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
                error.setText("激活码不正确");
                error.setVisibility(ViewGroup.VISIBLE);
                return;
            }
            dismiss();
            if (onActivated != null) {
                onActivated.run();
            }
        });
        actions.addView(activate, new LinearLayout.LayoutParams(0, dp(44), 1.35f));

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                activate.performClick();
                return true;
            }
            return false;
        });

        setContentView(box);
        if (getWindow() != null) {
            int width = getContext().getResources().getDisplayMetrics().widthPixels;
            int target = Math.min(Math.round(width * 0.88f), dp(360));
            getWindow().setLayout(target, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        input.requestFocus();
        InputMethodManager manager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }
    }
}
