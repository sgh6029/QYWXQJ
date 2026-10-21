package com.huzhou.leaveapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The one-time, locally verified water-blue HEX gate. */
final class GatePanel extends FrameLayout {
    private static final String PASSWORD = "#66CCFF";
    private static final String SECONDARY_KEY = "2513";

    interface ResultListener {
        void onResult(boolean activated);
    }

    private final ProfileStore store;
    private final ResultListener listener;
    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private EditText input;
    private TextView error;
    private TextView verifyButton;
    private boolean accepted;

    GatePanel(Context context, ProfileStore store, ResultListener listener) {
        super(context);
        this.store = store;
        this.listener = listener;
        setWillNotDraw(false);
        setClipToPadding(false);
        setBackgroundColor(Color.TRANSPARENT);
        buildContent();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void buildContent() {
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LinearLayout stage = new LinearLayout(getContext());
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        stage.setPadding(dp(22), dp(24), dp(22), dp(24));
        scroll.addView(stage, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        MaxWidthCard card = new MaxWidthCard(getContext(), dp(350));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(24), dp(24), dp(24), dp(22));

        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(0xF7FFFFFF);
        cardBackground.setCornerRadius(dp(26));
        cardBackground.setStroke(dp(1), 0x7066CCFF);
        card.setBackground(cardBackground);
        card.setElevation(dp(14));
        stage.addView(card, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ImageView logo = new ImageView(getContext());
        logo.setImageResource(R.drawable.icon_source);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        logo.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable logoBackground = new GradientDrawable();
        logoBackground.setColor(Color.WHITE);
        logoBackground.setCornerRadius(dp(20));
        logoBackground.setStroke(dp(1), 0x3366CCFF);
        logo.setBackground(logoBackground);
        card.addView(logo, new LinearLayout.LayoutParams(dp(80), dp(80)));

        TextView title = new TextView(getContext());
        title.setText("完蛋企业");
        title.setTextColor(0xFF0A2B3D);
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(16);
        card.addView(title, titleParams);

        TextView heading = new TextView(getContext());
        heading.setText("验证口令");
        heading.setTextColor(0xFF14384A);
        heading.setTextSize(19);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headingParams.topMargin = dp(20);
        card.addView(heading, headingParams);

        TextView hint = new TextView(getContext());
        hint.setText("水蓝琉璃？");
        hint.setTextColor(0xFF4F8FEA);
        hint.setTextSize(13);
        hint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(5);
        card.addView(hint, hintParams);

        TextView instruction = new TextView(getContext());
        instruction.setText("未激活时每次进入都需验证，激活后不再弹出");
        instruction.setTextColor(0xFF668494);
        instruction.setTextSize(12);
        instruction.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams instructionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        instructionParams.topMargin = dp(7);
        card.addView(instruction, instructionParams);

        input = new EditText(getContext());
        input.setHint("请输入口令");
        input.setHintTextColor(0xFF9AB8C8);
        input.setTextColor(0xFF153B4D);
        input.setTextSize(16);
        input.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable inputBackground = new GradientDrawable();
        inputBackground.setColor(0xFFF0F8FD);
        inputBackground.setCornerRadius(dp(14));
        inputBackground.setStroke(dp(1), 0xFFB7E0F6);
        input.setBackground(inputBackground);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16)});
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        inputParams.topMargin = dp(22);
        card.addView(input, inputParams);

        error = new TextView(getContext());
        error.setTextColor(0xFFD54545);
        error.setTextSize(11.5f);
        error.setGravity(Gravity.CENTER);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams errorParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        errorParams.topMargin = dp(7);
        card.addView(error, errorParams);

        verifyButton = new TextView(getContext());
        verifyButton.setText("进入程序");
        verifyButton.setTextColor(Color.WHITE);
        verifyButton.setTextSize(16);
        verifyButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        verifyButton.setGravity(Gravity.CENTER);
        verifyButton.setClickable(true);
        verifyButton.setFocusable(true);
        GradientDrawable buttonBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF66CCFF, 0xFF2E98EA});
        buttonBackground.setCornerRadius(dp(16));
        verifyButton.setBackground(buttonBackground);
        verifyButton.setElevation(dp(5));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        buttonParams.topMargin = dp(13);
        card.addView(verifyButton, buttonParams);

        TextView skip = new TextView(getContext());
        skip.setText("不激活，进入试用（信息只读）");
        skip.setTextColor(0xFF4F8FEA);
        skip.setTextSize(12.5f);
        skip.setGravity(Gravity.CENTER);
        skip.setClickable(true);
        skip.setFocusable(true);
        skip.setPadding(dp(8), dp(10), dp(8), dp(10));
        LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        skipParams.topMargin = dp(2);
        card.addView(skip, skipParams);

        TextView footer = new TextView(getContext());
        footer.setText("激活后不再弹出 · LV.999.1");
        footer.setTextColor(0xFF8FA9B7);
        footer.setTextSize(10.5f);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = dp(13);
        card.addView(footer, footerParams);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (error.getVisibility() == View.VISIBLE) {
                    error.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                attemptVerify();
                return true;
            }
            return false;
        });
        verifyButton.setOnClickListener(v -> attemptVerify());
        skip.setOnClickListener(v -> skipActivation());
    }

    private void attemptVerify() {
        if (accepted) {
            return;
        }
        String typed = input.getText() == null ? "" : input.getText().toString().trim();
        if (typed.isEmpty()) {
            showError("请输入口令");
            return;
        }
        // No prefix is inserted by the interface.  Both the original colour
        // password and the additional short key are accepted.
        if (!typed.equalsIgnoreCase(PASSWORD) && !typed.equals(SECONDARY_KEY)) {
            showError("口令不正确");
            return;
        }
        accepted = true;
        hideKeyboard();
        verifyButton.setText("验证通过");
        verifyButton.setAlpha(0.85f);
        verifyButton.setClickable(false);
        input.setEnabled(false);
        finishGate(true);
    }

    private void skipActivation() {
        if (accepted) {
            return;
        }
        accepted = true;
        hideKeyboard();
        verifyButton.setClickable(false);
        finishGate(false);
    }

    private void finishGate(boolean activated) {
        animate().alpha(0.98f).setDuration(120)
                .withEndAction(() -> listener.onResult(activated)).start();
    }

    private void showError(String message) {
        error.setText(message);
        error.setVisibility(View.VISIBLE);
        TranslateAnimation shake = new TranslateAnimation(0, dp(7), 0, 0);
        shake.setDuration(44);
        shake.setRepeatCount(5);
        shake.setRepeatMode(Animation.REVERSE);
        input.startAnimation(shake);
        input.requestFocus();
    }

    private void hideKeyboard() {
        InputMethodManager manager =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
        input.clearFocus();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }
        backgroundPaint.setShader(new LinearGradient(
                0, 0, width, height,
                new int[]{0xFF04121F, 0xFF0A2A3D, 0xFF071B2B},
                new float[]{0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, backgroundPaint);

        glowPaint.setShader(new RadialGradient(
                width * 0.82f, height * 0.14f, width * 0.72f,
                new int[]{0x5588E5FF, 0x2266CCFF, 0x0066CCFF},
                new float[]{0f, 0.44f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(width * 0.82f, height * 0.14f, width * 0.72f, glowPaint);

        glowPaint.setShader(new RadialGradient(
                width * 0.10f, height * 0.92f, width * 0.62f,
                new int[]{0x3348BCE8, 0x1448BCE8, 0x0048BCE8},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(width * 0.10f, height * 0.92f, width * 0.62f, glowPaint);
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        int top = 0;
        int bottom = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (insets.getDisplayCutout() != null) {
                top = insets.getDisplayCutout().getSafeInsetTop();
            }
            bottom = insets.getInsets(WindowInsets.Type.ime()).bottom;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (insets.getDisplayCutout() != null) {
                top = insets.getDisplayCutout().getSafeInsetTop();
            }
            bottom = insets.getSystemWindowInsetBottom();
        } else {
            top = insets.getSystemWindowInsetTop();
            bottom = insets.getSystemWindowInsetBottom();
        }
        setPadding(0, top, 0, bottom);
        return insets;
    }

    /** A card that remains comfortable to read on tablets without stretching. */
    private static final class MaxWidthCard extends LinearLayout {
        private final int maxWidth;

        MaxWidthCard(Context context, int maxWidth) {
            super(context);
            this.maxWidth = maxWidth;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int mode = MeasureSpec.getMode(widthMeasureSpec);
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (mode != MeasureSpec.UNSPECIFIED && width > maxWidth) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, mode);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
