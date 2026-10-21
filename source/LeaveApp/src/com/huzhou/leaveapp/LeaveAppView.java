package com.huzhou.leaveapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Build;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.widget.Scroller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Hand-painted, resolution independent recreation of the leave flow in the
 * supplied recording.  Everything is drawn locally; there is no WebView and no
 * network use.
 */
public final class LeaveAppView extends View {
    public static final int SCREEN_WORKBENCH = 0;
    public static final int SCREEN_SYSTEM = 1;
    public static final int SCREEN_LIST = 2;
    public static final int SCREEN_DETAIL = 3;
    public static final int SCREEN_LOADING = 4;

    public interface ProfileEditor {
        void open(int recordIndex, String start, String end, String days,
                  String apply, String approve);
    }

    private static final int C_BLACK = 0xFF000000;
    private static final int C_DARK_BG = 0xFF19191B;
    private static final int C_DARK_CARD = 0xFF27272A;
    private static final int C_DARK_LINE = 0xFF303034;
    private static final int C_LIGHT_TEXT = 0xFFEDEDEF;
    private static final int C_MUTED_DARK = 0xFF9A9AA1;
    private static final int C_PAGE = 0xFFF2F2F3;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_TEXT = 0xFF1C1C1E;
    private static final int C_VALUE = 0xFF4C4C4C;
    private static final int C_LABEL = 0xFF9A9A9F;
    private static final int C_DIVIDER = 0xFFEEEEEE;
    private static final int C_BLUE = 0xFF2B7CF6;
    private static final int C_GREEN = 0xFF2DBB74;
    private static final int C_ORANGE = 0xFFE29B3F;

    private static final Typeface TF_REG = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final Typeface TF_MED = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface TF_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface TF_LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    /** Small typographic correction toward the supplied screenshots. */
    private static final float TEXT_SCALE = 1.09f;

    interface ShellNavListener {
        void onShellTab(int tab);
    }

    private final ProfileStore store;
    private final ProfileEditor editor;
    private ShellNavListener shellNavListener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final List<Hotspot> hotspots = new ArrayList<>();
    private final Scroller scroller;
    private final Bitmap workbenchContent;
    private final Bitmap workbenchNav;
    private final Bitmap avatarMale;
    private final Bitmap avatarFemale;

    private float density;
    private float u = 1f;
    private float contentW;
    private float contentLeft;
    private float topInset;
    private float statusH;
    private float headerH;
    private int batteryPercent = 86;

    private int screen = SCREEN_WORKBENCH;
    private int selectedProfile = 0;
    private int selectedRecord = 0;
    private final List<LeaveRecord> records = new ArrayList<>();
    private boolean detailExpanded;
    private float scroll;
    private float maxScroll;
    private float contentHeight;
    private float pendingScroll;

    private float downX;
    private float downY;
    private float downScroll;
    private boolean dragging;
    private int touchSlop;
    private VelocityTracker velocityTracker;

    private boolean loadingWhite;
    private long loadingStart;
    private Runnable loadingWhiteRunnable;
    private Runnable loadingDoneRunnable;

    private String leaveStartText;
    private String leaveEndText;
    private String leaveDaysText;
    private String maleApplyText;
    private String maleApproveText;
    private String femaleApplyText;
    private String femaleApproveText;
    private String academicYear;
    private String term;

    public LeaveAppView(Context context, ProfileStore store, ProfileEditor editor) {
        super(context);
        this.store = store;
        this.editor = editor;
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        scroller = new Scroller(context);
        setClickable(true);
        setFocusable(true);
        setBackgroundColor(C_BLACK);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        workbenchContent = BitmapFactory.decodeResource(
                getResources(), R.drawable.workbench_content, options);
        workbenchNav = BitmapFactory.decodeResource(
                getResources(), R.drawable.workbench_nav, options);
        avatarMale = BitmapFactory.decodeResource(
                getResources(), R.drawable.avatar_male, options);
        avatarFemale = BitmapFactory.decodeResource(
                getResources(), R.drawable.avatar_female, options);
        queryBattery();
        refreshTimes();
    }

    public void setInitialState(int screen, float scroll, boolean expanded) {
        this.screen = Math.max(SCREEN_WORKBENCH, Math.min(SCREEN_DETAIL, screen));
        // The former intermediate page is no longer part of the flow, and the
        // loading screen is transient (restore straight to the list).
        if (this.screen == SCREEN_SYSTEM) {
            this.screen = SCREEN_LIST;
        }
        this.detailExpanded = expanded;
        this.pendingScroll = Math.max(0f, scroll);
    }

    public int getScreen() {
        return screen == SCREEN_LOADING ? SCREEN_LIST : screen;
    }

    public float getScrollYPosition() {
        return scroll;
    }

    public boolean isDetailExpanded() {
        return detailExpanded;
    }

    public boolean canGoBack() {
        return screen != SCREEN_WORKBENCH;
    }

    public void goBack() {
        if (screen == SCREEN_DETAIL) {
            openScreen(SCREEN_LIST);
        } else if (screen == SCREEN_LIST) {
            openScreen(SCREEN_WORKBENCH);
        } else if (screen == SCREEN_SYSTEM) {
            openScreen(SCREEN_WORKBENCH);
        } else if (screen == SCREEN_LOADING) {
            cancelLoading();
            openScreen(SCREEN_WORKBENCH);
        }
    }

    public void onHostResume() {
        queryBattery();
        refreshTimes();
        if (screen == SCREEN_DETAIL) {
            // A refreshed time must remain valid inside the current card.
            detailExpanded = detailExpanded;
        }
        invalidate();
    }

    public void onProfilesChanged() {
        refreshTimes();
        academicYear = store.getAcademicYear();
        term = store.getSemester();
        invalidate();
    }

    private void openScreen(int target) {
        if (target != SCREEN_LOADING) {
            cancelLoading();
        }
        screen = target;
        scroll = 0f;
        pendingScroll = 0f;
        maxScroll = 0f;
        scroller.forceFinished(true);
        if (target == SCREEN_LIST || target == SCREEN_SYSTEM || target == SCREEN_WORKBENCH) {
            detailExpanded = false;
        }
        getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    /**
     * The short browser-like loading transition before the leave list:
     * 1 s black with a blue progress bar, then 2 s white with the address
     * "学生请假", then the real leave records.
     */
    private void startLoading() {
        cancelLoading();
        screen = SCREEN_LOADING;
        loadingWhite = false;
        loadingStart = System.currentTimeMillis();
        scroll = 0f;
        maxScroll = 0f;
        scroller.forceFinished(true);
        getParent().requestDisallowInterceptTouchEvent(false);
        loadingWhiteRunnable = () -> {
            if (screen == SCREEN_LOADING) {
                loadingWhite = true;
                invalidate();
            }
        };
        loadingDoneRunnable = () -> {
            if (screen == SCREEN_LOADING) {
                openScreen(SCREEN_LIST);
            }
        };
        postDelayed(loadingWhiteRunnable, 1000);
        postDelayed(loadingDoneRunnable, 3000);
        invalidate();
    }

    private void cancelLoading() {
        if (loadingWhiteRunnable != null) {
            removeCallbacks(loadingWhiteRunnable);
            loadingWhiteRunnable = null;
        }
        if (loadingDoneRunnable != null) {
            removeCallbacks(loadingDoneRunnable);
            loadingDoneRunnable = null;
        }
        loadingWhite = false;
    }

    private void queryBattery() {
        try {
            BatteryManager manager =
                    (BatteryManager) getContext().getSystemService(Context.BATTERY_SERVICE);
            if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                int value = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (value >= 0 && value <= 100) {
                    batteryPercent = value;
                }
            }
        } catch (Throwable ignored) {
            // The static fallback keeps the status bar useful on unusual devices.
        }
    }

    private void refreshTimes() {
        long now = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(now);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat minute = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        SimpleDateFormat second = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);

        records.clear();
        for (int i = 0; i < 7; i++) {
            LeaveRecord record = new LeaveRecord(i);
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(now);
            day.set(Calendar.SECOND, 0);
            day.set(Calendar.MILLISECOND, 0);
            if (i == 0) {
                // The current leave starts one day before "now", as requested.
                day.add(Calendar.DAY_OF_YEAR, -1);
                day.set(Calendar.MINUTE, 0);
                day.set(Calendar.HOUR_OF_DAY, 17);
            } else {
                day.add(Calendar.DAY_OF_YEAR, -(i + 1));
                day.set(Calendar.HOUR_OF_DAY, 17);
                day.set(Calendar.MINUTE, 0);
                record.cancelled = true;
            }
            long leaveStart = day.getTimeInMillis();
            long leaveEnd = leaveStart + 2L * 60L * 60L * 1000L;
            record.start = minute.format(new Date(leaveStart));
            record.end = minute.format(new Date(leaveEnd));
            record.days = "0.08";
            record.apply = second.format(new Date(leaveStart - 3L * 60L * 60L * 1000L));
            record.approve = second.format(new Date(leaveStart - 60L * 60L * 1000L));
            if (record.cancelled) {
                Calendar cancel = Calendar.getInstance();
                cancel.setTimeInMillis(leaveEnd);
                cancel.add(Calendar.DAY_OF_YEAR, 1);
                cancel.set(Calendar.HOUR_OF_DAY, 7);
                cancel.set(Calendar.MINUTE, 46);
                record.cancelTime = minute.format(cancel.getTime());
            }
            // Per-record overrides from the detail page's custom-time editor.
            String value = store.getRecordValue(i, "start");
            if (!value.isEmpty()) {
                record.start = value;
            }
            value = store.getRecordValue(i, "end");
            if (!value.isEmpty()) {
                record.end = value;
            }
            value = store.getRecordValue(i, "days");
            if (!value.isEmpty()) {
                record.days = value;
            }
            value = store.getRecordValue(i, "apply");
            if (!value.isEmpty()) {
                record.apply = value;
            }
            value = store.getRecordValue(i, "approve");
            if (!value.isEmpty()) {
                record.approve = value;
            }
            records.add(record);
        }

        academicYear = store.getAcademicYear();
        term = store.getSemester();
    }

    private static final class LeaveRecord {
        final int index;
        String start = "";
        String end = "";
        String days = "";
        String apply = "";
        String approve = "";
        boolean cancelled;
        String cancelTime = "";
        String cancelType = "定位销假";
        String cancelRemark = "已回学校";

        LeaveRecord(int index) {
            this.index = index;
        }
    }

    private String profileLeaveStart(Profile profile) {
        return profile.leaveStartTime == null || profile.leaveStartTime.isEmpty()
                ? leaveStartText : profile.leaveStartTime;
    }

    private String profileLeaveEnd(Profile profile) {
        return profile.leaveEndTime == null || profile.leaveEndTime.isEmpty()
                ? leaveEndText : profile.leaveEndTime;
    }

    private String profileLeaveDays(Profile profile) {
        return profile.leaveDays == null || profile.leaveDays.isEmpty()
                ? leaveDaysText : profile.leaveDays;
    }

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        if (topInset != 0f) {
            topInset = 0f;
            computeMetrics();
            invalidate();
        }
        return insets;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeMetrics();
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelLoading();
        super.onDetachedFromWindow();
        if (workbenchContent != null && !workbenchContent.isRecycled()) {
            workbenchContent.recycle();
        }
        if (workbenchNav != null && !workbenchNav.isRecycled()) {
            workbenchNav.recycle();
        }
        if (avatarMale != null && !avatarMale.isRecycled()) {
            avatarMale.recycle();
        }
        if (avatarFemale != null && !avatarFemale.isRecycled()) {
            avatarFemale.recycle();
        }
    }

    private void computeMetrics() {
        density = getResources().getDisplayMetrics().density;
        int width = getWidth();
        if (width <= 0) {
            return;
        }
        contentW = Math.min(width, 500f * density);
        u = contentW / 393f;
        float maxU = density * 1.18f;
        if (u > maxU) {
            u = maxU;
            contentW = 393f * u;
        }
        contentLeft = (width - contentW) / 2f;
        statusH = topInset;
        headerH = 54f * u;
        if (pendingScroll > 0f) {
            scroll = pendingScroll;
            pendingScroll = 0f;
        }
    }

    private float viewportTop() {
        return screen == SCREEN_WORKBENCH ? statusH : headerH;
    }

    private float bottomSafe() {
        return 18f * u;
    }

    private float workbenchNavHeight() {
        if (workbenchNav == null || getWidth() == 0) {
            return 0f;
        }
        return getWidth() * (float) workbenchNav.getHeight() / workbenchNav.getWidth();
    }

    private float viewportBottom() {
        if (screen == SCREEN_WORKBENCH) {
            return getHeight() - workbenchNavHeight();
        }
        if (screen == SCREEN_DETAIL) {
            return getHeight() - (detailHasActions() ? 72f * u : bottomSafe());
        }
        return getHeight() - bottomSafe();
    }

    private boolean detailHasActions() {
        return !records.isEmpty() && !selectedLeave().cancelled;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) {
            return;
        }
        hotspots.clear();
        if (screen == SCREEN_LOADING) {
            drawLoading(canvas);
            return;
        }
        int color = screen == SCREEN_WORKBENCH || screen == SCREEN_SYSTEM
                ? C_DARK_BG : C_PAGE;
        canvas.drawColor(color);
        if (screen == SCREEN_WORKBENCH && statusH > 0f) {
            // Keep any display-cutout safe area visually merged with the black
            // screenshot header instead of looking like a fake status bar.
            fillRect(canvas, 0f, 0f, getWidth(), statusH, C_BLACK);
        }

        float top = viewportTop();
        float bottom = viewportBottom();
        float height = Math.max(0f, bottom - top);
        contentHeight = 0f;
        canvas.save();
        canvas.clipRect(0f, top, getWidth(), bottom);
        canvas.translate(0f, top - scroll);
        if (screen == SCREEN_WORKBENCH) {
            drawWorkbench(canvas);
        } else if (screen == SCREEN_SYSTEM) {
            drawSystem(canvas);
        } else if (screen == SCREEN_LIST) {
            drawLeaveList(canvas);
        } else {
            drawLeaveDetail(canvas);
        }
        canvas.restore();

        maxScroll = Math.max(0f, contentHeight - height);
        if (scroll > maxScroll) {
            scroll = maxScroll;
        } else if (scroll < 0f) {
            scroll = 0f;
        }

        if (screen == SCREEN_WORKBENCH) {
            drawWorkbenchBottomBar(canvas);
        } else if (screen == SCREEN_DETAIL) {
            if (detailHasActions()) {
                drawDetailActionBar(canvas);
            }
        } else {
            drawBottomSafe(canvas, screen == SCREEN_SYSTEM);
        }

        if (screen != SCREEN_WORKBENCH) {
            drawHeader(canvas);
        }
    }

    // ---------------------------------------------------------------------
    // Status and header
    // ---------------------------------------------------------------------

    /**
     * 1 s black loading screen with a blue progress bar that stops at 50%,
     * then 2 s white page with "学生请假" in the address bar, then the list.
     */
    private void drawLoading(Canvas c) {
        long elapsed = System.currentTimeMillis() - loadingStart;
        boolean white = loadingWhite || elapsed >= 1000L;
        if (!white) {
            c.drawColor(C_BLACK);
            drawLoadingHeader(c, false);
            float progress = Math.min(0.5f, elapsed / 1000f * 0.5f);
            fillRect(c, 0f, headerH - 2.2f * u,
                    getWidth() * progress, headerH, 0xFF2F7BF6);
            postInvalidateOnAnimation();
        } else {
            c.drawColor(0xFFF1F1F1);
            drawLoadingHeader(c, true);
        }
    }

    private void drawLoadingHeader(Canvas c, boolean whiteStage) {
        fillRect(c, 0f, 0f, getWidth(), headerH, C_BLACK);
        float centerY = headerH / 2f;
        drawBack(c, 19f * u, centerY);
        addHotspot(2f * u, 0f, 44f * u, headerH, this::goBack);

        paint.reset();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE9E9EA);
        float moreX = getWidth() - 19f * u;
        for (int i = -1; i <= 1; i++) {
            c.drawCircle(moreX, centerY + i * 5.5f * u, 1.9f * u, paint);
        }

        if (whiteStage) {
            drawText(c, "学生请假", 50f * u, centerY + 6f * u,
                    18f * u, 0xFFF7F7F8, TF_REG, Paint.Align.LEFT);
        } else {
            drawText(c, "学生请假", getWidth() / 2f, centerY + 6f * u,
                    17.2f * u, 0xFFF7F7F8, TF_MED, Paint.Align.CENTER);
        }
    }

    private void drawHeader(Canvas c) {
        fillRect(c, 0f, 0f, getWidth(), headerH, C_BLACK);
        String title;
        if (screen == SCREEN_WORKBENCH) {
            title = "工作台";
        } else if (screen == SCREEN_SYSTEM) {
            title = "学生请假";
        } else if (screen == SCREEN_LIST) {
            title = "请假";
        } else {
            title = "请假详情";
        }
        float centerY = headerH / 2f;
        drawText(c, title, getWidth() / 2f, centerY + 6f * u,
                17.2f * u, 0xFFF7F7F8, TF_MED, Paint.Align.CENTER);

        if (screen == SCREEN_WORKBENCH) {
            float searchX = getWidth() - 45f * u;
            drawSearch(c, searchX, centerY, 9.5f * u);
            drawFilter(c, getWidth() - 18f * u, centerY);
        } else {
            drawBack(c, 19f * u, centerY);
            addHotspot(2f * u, 0f,
                    44f * u, headerH, this::goBack);

            float moreX = getWidth() - 19f * u;
            paint.reset();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFE9E9EA);
            for (int i = -1; i <= 1; i++) {
                c.drawCircle(moreX, centerY + i * 5.5f * u, 1.9f * u, paint);
            }
            if (screen == SCREEN_DETAIL) {
                final LeaveRecord record = selectedLeave();
                addHotspot(getWidth() - 42f * u, 0f,
                        getWidth() - 2f * u, headerH,
                        () -> editor.open(record.index, record.start, record.end,
                                record.days, record.apply, record.approve));
            }
        }
    }

    private void drawBack(Canvas c, float cx, float cy) {
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFFF2F2F3);
        path.reset();
        path.moveTo(cx + 4f * u, cy - 10f * u);
        path.lineTo(cx - 6f * u, cy);
        path.lineTo(cx + 4f * u, cy + 10f * u);
        c.drawPath(path, paint);
    }

    private void drawSearch(Canvas c, float cx, float cy, float radius) {
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.7f * u);
        paint.setColor(0xFFE7E7E9);
        c.drawCircle(cx, cy - 1f * u, radius, paint);
        c.drawLine(cx + radius * 0.7f, cy + radius * 0.7f - 1f * u,
                cx + radius * 1.5f, cy + radius * 1.5f, paint);
    }

    private void drawFilter(Canvas c, float cx, float cy) {
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(0xFFE7E7E9);
        c.drawLine(cx - 8f * u, cy - 5f * u, cx + 8f * u, cy - 5f * u, paint);
        c.drawLine(cx - 5f * u, cy, cx + 5f * u, cy, paint);
        c.drawLine(cx - 2.5f * u, cy + 5f * u, cx + 2.5f * u, cy + 5f * u, paint);
        fillCircle(c, cx - 5.2f * u, cy - 5f * u, 1.8f * u, 0xFFE7E7E9);
        fillCircle(c, cx + 2.3f * u, cy + 5f * u, 1.8f * u, 0xFFE7E7E9);
    }

    // ---------------------------------------------------------------------
    // Level 1: workbench
    // ---------------------------------------------------------------------

    private void drawWorkbench(Canvas c) {
        if (workbenchContent == null || getWidth() == 0) {
            contentHeight = 0f;
            return;
        }
        // The workbench is the supplied screenshot itself.  Nothing on this
        // level is hand-drawn; only one transparent hotspot is placed over
        // the "学工系统" tile.
        float imageHeight = getWidth()
                * (float) workbenchContent.getHeight() / workbenchContent.getWidth();
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        c.drawBitmap(workbenchContent, null,
                new RectF(0f, 0f, getWidth(), imageHeight), paint);
        contentHeight = imageHeight;

        float scale = getWidth() / (float) workbenchContent.getWidth();
        // Both visible leave entries lead to the same second-level menu.  No
        // other tile on the screenshot reacts to touch.
        addContentHotspot(500f * scale, 680f * scale,
                700f * scale, 920f * scale,
                this::startLoading);
        addContentHotspot(80f * scale, 680f * scale,
                300f * scale, 920f * scale,
                this::startLoading);
    }

    private void drawDarkCard(Canvas c, float l, float t, float r, float b) {
        fillRoundRect(c, l, t, r, b, 10f * u, C_DARK_CARD);
    }

    private void drawTodoGlyph(Canvas c, float cx, float cy) {
        fillRoundRect(c, cx - 8f * u, cy - 9f * u, cx + 8f * u, cy + 9f * u,
                3f * u, 0xFF43A85E);
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f * u);
        paint.setColor(0xFFFFFFFF);
        for (int i = -1; i <= 1; i++) {
            c.drawLine(cx - 4.5f * u, cy + i * 3.6f * u,
                    cx + 4.5f * u, cy + i * 3.6f * u, paint);
        }
    }

    private void drawPersonalRow(Canvas c, float x, float y, String label,
                                 String count, int kind) {
        fillCircle(c, x + 7f * u, y - 3.5f * u, 8f * u, 0xFF333338);
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * u);
        paint.setColor(0xFFBFC0C6);
        if (kind == 0) {
            c.drawCircle(x + 7f * u, y - 6f * u, 2f * u, paint);
            c.drawArc(new RectF(x + 2f * u, y - 2f * u, x + 12f * u, y + 8f * u),
                    200f, 140f, false, paint);
        } else {
            c.drawRect(x + 2f * u, y - 9f * u, x + 12f * u, y + 1f * u, paint);
            c.drawLine(x + 5f * u, y - 6f * u, x + 10f * u, y - 6f * u, paint);
            c.drawLine(x + 5f * u, y - 3f * u, x + 10f * u, y - 3f * u, paint);
        }
        drawText(c, label, x + 24f * u, y + 1f * u,
                12.3f * u, 0xFFE0E0E3, TF_REG, Paint.Align.LEFT);
        drawText(c, count, contentLeft + contentW - 56f * u, y + 1f * u,
                11.5f * u, 0xFF8E8E95, TF_REG, Paint.Align.RIGHT);
        drawChevronRight(c, contentLeft + contentW - 34f * u, y - 3f * u,
                10f * u, 0xFF74747B);
    }

    private void drawAppTile(Canvas c, float cx, float top, int kind) {
        float half = 21f * u;
        fillRoundRect(c, cx - half, top, cx + half, top + 42f * u,
                10f * u, 0xFF304B79);
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.55f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFFF8FAFF);
        float cy = top + 21f * u;
        if (kind == 0) {
            c.drawCircle(cx - 2f * u, cy - 7f * u, 4f * u, paint);
            c.drawArc(new RectF(cx - 10f * u, cy - 1f * u, cx + 6f * u, cy + 12f * u),
                    200f, 140f, false, paint);
            c.drawCircle(cx + 7.5f * u, cy + 7f * u, 5f * u, paint);
            c.drawLine(cx + 7.5f * u, cy + 7f * u, cx + 7.5f * u, cy + 4.2f * u, paint);
            c.drawLine(cx + 7.5f * u, cy + 7f * u, cx + 9.5f * u, cy + 8.3f * u, paint);
        } else if (kind == 1) {
            c.drawLine(cx - 8f * u, cy + 8f * u, cx + 7f * u, cy - 7f * u, paint);
            c.drawLine(cx + 2f * u, cy - 8f * u, cx + 8f * u, cy - 2f * u, paint);
            c.drawLine(cx - 8f * u, cy - 7f * u, cx + 7f * u, cy + 8f * u, paint);
            c.drawCircle(cx - 8f * u, cy + 8f * u, 3.2f * u, paint);
        } else if (kind == 2) {
            path.reset();
            path.moveTo(cx - 11f * u, cy - 2f * u);
            path.lineTo(cx, cy - 8f * u);
            path.lineTo(cx + 11f * u, cy - 2f * u);
            path.lineTo(cx, cy + 4f * u);
            path.close();
            c.drawPath(path, paint);
            c.drawLine(cx - 10f * u, cy, cx - 10f * u, cy + 8f * u, paint);
            c.drawLine(cx, cy + 4f * u, cx, cy + 11f * u, paint);
            c.drawLine(cx + 10f * u, cy, cx + 10f * u, cy + 8f * u, paint);
            c.drawLine(cx - 10f * u, cy + 8f * u, cx, cy + 12f * u, paint);
            c.drawLine(cx + 10f * u, cy + 8f * u, cx, cy + 12f * u, paint);
        } else if (kind == 3) {
            c.drawCircle(cx, cy - 2f * u, 4.2f * u, paint);
            c.drawArc(new RectF(cx - 8f * u, cy + 2f * u, cx + 8f * u, cy + 13f * u),
                    200f, 140f, false, paint);
            paint.setStrokeWidth(1.2f * u);
            path.reset();
            path.moveTo(cx - 11f * u, cy - 7f * u);
            path.lineTo(cx - 11f * u, cy - 11f * u);
            path.lineTo(cx - 7f * u, cy - 11f * u);
            path.moveTo(cx + 11f * u, cy - 7f * u);
            path.lineTo(cx + 11f * u, cy - 11f * u);
            path.lineTo(cx + 7f * u, cy - 11f * u);
            c.drawPath(path, paint);
        } else if (kind == 4) {
            c.drawCircle(cx - 5f * u, cy - 7f * u, 3.4f * u, paint);
            c.drawArc(new RectF(cx - 12f * u, cy - 2f * u, cx + 2f * u, cy + 10f * u),
                    200f, 140f, false, paint);
            c.drawRect(cx + 2f * u, cy - 6f * u, cx + 12f * u, cy + 2f * u, paint);
            c.drawLine(cx + 2f * u, cy + 8f * u, cx + 12f * u, cy + 8f * u, paint);
            c.drawLine(cx + 4f * u, cy + 8f * u, cx + 4f * u, cy + 11f * u, paint);
            c.drawLine(cx + 10f * u, cy + 8f * u, cx + 10f * u, cy + 11f * u, paint);
        } else if (kind == 5) {
            path.reset();
            path.moveTo(cx - 11f * u, cy - 6f * u);
            path.lineTo(cx - 2f * u, cy - 8f * u);
            path.lineTo(cx - 2f * u, cy + 8f * u);
            path.lineTo(cx - 11f * u, cy + 6f * u);
            path.close();
            path.moveTo(cx + 11f * u, cy - 6f * u);
            path.lineTo(cx + 2f * u, cy - 8f * u);
            path.lineTo(cx + 2f * u, cy + 8f * u);
            path.lineTo(cx + 11f * u, cy + 6f * u);
            path.close();
            c.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            c.drawRect(cx + 5.5f * u, cy - 2f * u, cx + 7.5f * u, cy, paint);
            c.drawRect(cx + 8f * u, cy + 1f * u, cx + 10f * u, cy + 3f * u, paint);
            c.drawRect(cx + 5.5f * u, cy + 4f * u, cx + 7.5f * u, cy + 6f * u, paint);
        } else {
            path.reset();
            path.moveTo(cx - 11f * u, cy + 8f * u);
            path.lineTo(cx - 11f * u, cy - 2f * u);
            path.lineTo(cx - 3f * u, cy - 8f * u);
            path.lineTo(cx + 3f * u, cy - 8f * u);
            path.lineTo(cx + 11f * u, cy - 2f * u);
            path.lineTo(cx + 11f * u, cy + 8f * u);
            path.close();
            c.drawPath(path, paint);
            c.drawRect(cx - 4f * u, cy - 1f * u, cx - 1f * u, cy + 2f * u, paint);
            c.drawRect(cx + 1f * u, cy - 1f * u, cx + 4f * u, cy + 2f * u, paint);
        }
    }

    private void drawWorkbenchBottomBar(Canvas c) {
        if (workbenchNav == null) {
            return;
        }
        float navHeight = workbenchNavHeight();
        float top = getHeight() - navHeight;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        c.drawBitmap(workbenchNav, null,
                new RectF(0f, top, getWidth(), getHeight()), paint);

        float tabW = getWidth() / 5f;
        addHotspot(0f, top, tabW, getHeight(),
                () -> notifyShellTab(0));
        addHotspot(tabW, top, tabW * 2f, getHeight(),
                () -> notifyShellTab(1));
        addHotspot(tabW * 2f, top, tabW * 3f, getHeight(),
                () -> notifyShellTab(2));
        addHotspot(tabW * 3f, top, tabW * 4f, getHeight(),
                () -> notifyShellTab(3));
        addHotspot(tabW * 4f, top, tabW * 5f, getHeight(),
                () -> notifyShellTab(4));
    }

    void setShellNavListener(ShellNavListener listener) {
        shellNavListener = listener;
    }

    private void notifyShellTab(int tab) {
        if (shellNavListener != null) {
            shellNavListener.onShellTab(tab);
        }
    }

    private void drawBottomIcon(Canvas c, float cx, float cy, int kind, int color) {
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        if (kind == 0) {
            c.drawRoundRect(new RectF(cx - 9f * u, cy - 8f * u,
                    cx + 9f * u, cy + 6f * u), 6f * u, 6f * u, paint);
            path.reset();
            path.moveTo(cx - 4f * u, cy + 6f * u);
            path.lineTo(cx - 6f * u, cy + 10f * u);
            path.lineTo(cx + 1f * u, cy + 6f * u);
            c.drawPath(path, paint);
        } else if (kind == 1) {
            c.drawRect(cx - 7f * u, cy - 9f * u, cx + 7f * u, cy + 9f * u, paint);
            c.drawLine(cx - 6f * u, cy - 3f * u, cx + 2f * u, cy - 3f * u, paint);
            c.drawLine(cx + 5f * u, cy - 4f * u, cx + 9f * u, cy - 4f * u, paint);
            c.drawLine(cx - 6f * u, cy + 1f * u, cx + 1f * u, cy + 1f * u, paint);
        } else if (kind == 2) {
            c.drawCircle(cx, cy - 6f * u, 3f * u, paint);
            c.drawCircle(cx - 8f * u, cy + 7f * u, 3f * u, paint);
            c.drawCircle(cx + 8f * u, cy + 7f * u, 3f * u, paint);
            c.drawLine(cx, cy - 3f * u, cx, cy + 1f * u, paint);
            c.drawLine(cx - 8f * u, cy + 4f * u, cx + 8f * u, cy + 4f * u, paint);
            c.drawLine(cx - 8f * u, cy + 4f * u, cx - 8f * u, cy + 4f * u, paint);
        } else if (kind == 3) {
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    c.drawRoundRect(new RectF(cx - 8f * u + col * 9f * u,
                            cy - 8f * u + row * 9f * u,
                            cx - 2f * u + col * 9f * u,
                            cy - 2f * u + row * 9f * u), 1.5f * u, 1.5f * u, paint);
                }
            }
        } else {
            c.drawCircle(cx, cy - 6f * u, 3.4f * u, paint);
            c.drawArc(new RectF(cx - 8f * u, cy, cx + 8f * u, cy + 13f * u),
                    200f, 140f, false, paint);
        }
    }

    // ---------------------------------------------------------------------
    // Level 2: student affairs menu
    // ---------------------------------------------------------------------

    private void drawSystem(Canvas c) {
        float left = contentLeft + 12f * u;
        float right = contentLeft + contentW - 12f * u;
        float width = right - left;
        float top = 12f * u;

        // School / system header.
        float heroH = 112f * u;
        canvasRoundClip(c, left, top, right, top + heroH, 12f * u);
        paint.reset();
        paint.setShader(new LinearGradient(left, top, right, top + heroH,
                new int[]{0xFF143F69, 0xFF1F5B91, 0xFF123755},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(left, top, right, top + heroH, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * u);
        paint.setColor(0x2AFFFFFF);
        c.drawCircle(right - 18f * u, top + 20f * u, 54f * u, paint);
        c.drawCircle(right - 18f * u, top + 20f * u, 34f * u, paint);
        c.restore();

        drawSchoolGlyph(c, left + 30f * u, top + 54f * u);
        drawText(c, "湖州学院", left + 52f * u, top + 45f * u,
                19f * u, 0xFFF5FAFF, TF_MED, Paint.Align.LEFT);
        drawText(c, "学工系统 · 学生请假", left + 52f * u, top + 66f * u,
                11.5f * u, 0xFFB8D5EE, TF_REG, Paint.Align.LEFT);
        fillRoundRect(c, left + 52f * u, top + 78f * u, left + 106f * u,
                top + 99f * u, 10.5f * u, 0x3323D18A);
        fillCircle(c, left + 61f * u, top + 88.5f * u, 2.2f * u, 0xFF48D89B);
        drawText(c, "已登录", left + 67f * u, top + 92.5f * u,
                9.8f * u, 0xFF8BE8C1, TF_MED, Paint.Align.LEFT);

        float sectionY = top + heroH + 24f * u;
        drawText(c, "请假服务", left + 4f * u, sectionY,
                11.8f * u, 0xFF818188, TF_REG, Paint.Align.LEFT);

        float menuTop = sectionY + 10f * u;
        float menuH = 76f * u;
        fillRoundRect(c, left, menuTop, right, menuTop + menuH,
                11f * u, 0xFF252528);
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f * u);
        paint.setColor(0x553C6FA5);
        c.drawRoundRect(new RectF(left, menuTop, right, menuTop + menuH),
                11f * u, 11f * u, paint);
        fillCircle(c, left + 34f * u, menuTop + 38f * u, 18f * u, 0xFF2A5F97);
        drawLeaveGlyph(c, left + 34f * u, menuTop + 38f * u);
        drawText(c, "学生请假", left + 62f * u, menuTop + 33f * u,
                15f * u, 0xFFF2F2F4, TF_MED, Paint.Align.LEFT);
        drawText(c, "请假申请与请假记录", left + 62f * u, menuTop + 54f * u,
                11f * u, 0xFF9999A0, TF_REG, Paint.Align.LEFT);
        drawChevronRight(c, right - 20f * u, menuTop + 38f * u,
                11f * u, 0xFF8C9AA8);
        addContentHotspot(left, menuTop, right, menuTop + menuH,
                this::startLoading);

        float disabledTop = menuTop + menuH + 10f * u;
        String[] titles = {"请假记录", "个人信息"};
        String[] subtitles = {"查看历史请假与销假记录", "维护个人基础资料"};
        for (int i = 0; i < 2; i++) {
            float rowTop = disabledTop + i * 64f * u;
            fillRoundRect(c, left, rowTop, right, rowTop + 58f * u,
                    10f * u, 0xFF202023);
            fillCircle(c, left + 30f * u, rowTop + 29f * u, 14f * u, 0xFF2B2B2F);
            drawText(c, titles[i], left + 52f * u, rowTop + 26f * u,
                    13f * u, 0xFF7C7C83, TF_REG, Paint.Align.LEFT);
            drawText(c, subtitles[i], left + 52f * u, rowTop + 44f * u,
                    10.5f * u, 0xFF5F5F65, TF_REG, Paint.Align.LEFT);
        }

        drawText(c, "本地离线数据 · 仅用于演示", left + contentW / 2f - 12f * u,
                disabledTop + 154f * u, 10.5f * u,
                0xFF68686E, TF_REG, Paint.Align.CENTER);
        contentHeight = disabledTop + 174f * u;
    }

    private void drawSchoolGlyph(Canvas c, float cx, float cy) {
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFFE8F5FF);
        path.reset();
        path.moveTo(cx - 11f * u, cy + 7f * u);
        path.lineTo(cx - 11f * u, cy - 4f * u);
        path.lineTo(cx, cy - 10f * u);
        path.lineTo(cx + 11f * u, cy - 4f * u);
        path.lineTo(cx + 11f * u, cy + 7f * u);
        path.close();
        c.drawPath(path, paint);
        for (int i = -1; i <= 1; i++) {
            c.drawLine(cx + i * 6f * u, cy - 1f * u,
                    cx + i * 6f * u, cy + 6f * u, paint);
        }
        c.drawLine(cx - 5f * u, cy + 11f * u, cx + 5f * u, cy + 11f * u, paint);
    }

    private void drawLeaveGlyph(Canvas c, float cx, float cy) {
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFFEAF5FF);
        path.reset();
        path.moveTo(cx - 9f * u, cy - 8f * u);
        path.lineTo(cx + 7f * u, cy - 8f * u);
        path.lineTo(cx + 10f * u, cy - 4f * u);
        path.lineTo(cx + 10f * u, cy + 8f * u);
        path.lineTo(cx - 9f * u, cy + 8f * u);
        path.close();
        c.drawPath(path, paint);
        c.drawLine(cx - 5f * u, cy - 2f * u, cx + 5f * u, cy - 2f * u, paint);
        c.drawLine(cx - 5f * u, cy + 3f * u, cx + 2f * u, cy + 3f * u, paint);
    }

    // ---------------------------------------------------------------------
    // Level 3: leave records
    // ---------------------------------------------------------------------

    private void drawLeaveList(Canvas c) {
        float left = contentLeft;
        float right = contentLeft + contentW;
        float width = contentW;
        float top = 0f;

        // Notice banner.  Font and line spacing both grew with the list.
        float bannerH = 100f * u;
        fillRect(c, left, top, right, top + bannerH, 0xFFFFF8E8);
        paint.reset();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFF1E3C7);
        c.drawRect(left, top + bannerH - 0.7f * u, right, top + bannerH, paint);
        drawText(c, "学生请假分事假、病假、丧假、公假四种。",
                left + 24f * u, top + 34f * u,
                15.6f * u, 0xFF333333, TF_REG, Paint.Align.LEFT);
        float detailWidth = 37f * u;
        String second = ellipsize(
                "（一）事假:指学生因本人或家庭特殊原因，无法参加正常...",
                width - 48f * u - detailWidth, 15.6f * u, TF_REG);
        drawText(c, second, left + 24f * u, top + 70f * u,
                15.6f * u, 0xFF333333, TF_REG, Paint.Align.LEFT);
        drawText(c, "详情", right - 24f * u - detailWidth / 2f, top + 70f * u,
                15.6f * u, C_BLUE, TF_REG, Paint.Align.CENTER);

        // Count row.
        float countTop = top + bannerH;
        float countH = 60f * u;
        float countBaseline = countTop + 38f * u;
        float countX = left + 24f * u;
        String countLabel = "共";
        drawText(c, countLabel, countX, countBaseline,
                16.5f * u, C_TEXT, TF_MED, Paint.Align.LEFT);
        float widthOfGong = measure(countLabel, 16.5f * u, TF_MED);
        String countNumber = String.valueOf(records.size());
        drawText(c, countNumber, countX + widthOfGong + 7f * u, countBaseline,
                17.5f * u, C_BLUE, TF_MED, Paint.Align.LEFT);
        float widthOfNumber = measure(countNumber, 17.5f * u, TF_MED);
        drawText(c, "条请假记录", countX + widthOfGong + widthOfNumber + 16f * u,
                countBaseline, 16.5f * u, C_TEXT, TF_MED, Paint.Align.LEFT);

        float addTextSize = 15.5f * u;
        float addTextWidth = measure("新增", addTextSize, TF_REG);
        float addTextRight = right - 24f * u;
        drawCirclePlus(c, addTextRight - addTextWidth - 18f * u,
                countTop + 32f * u, 9f * u, 0xFF5E5E63);
        drawText(c, "新增", addTextRight, countBaseline - 4f * u,
                addTextSize, 0xFF55555A, TF_REG, Paint.Align.RIGHT);

        // One approved record followed by six cancelled records.
        float cardTop = countTop + countH;
        float cardH = 238f * u;
        for (int i = 0; i < records.size(); i++) {
            LeaveRecord record = records.get(i);
            drawLeaveCard(c, left + 24f * u, cardTop, right - 24f * u,
                    cardTop + cardH, record);
            final int index = i;
            addContentHotspot(left + 24f * u, cardTop, right - 24f * u,
                    cardTop + cardH, () -> openDetail(index));
            cardTop += cardH + 20f * u;
        }

        contentHeight = cardTop + 16f * u;
    }

    private void drawLeaveCard(Canvas c, float l, float t, float r, float b,
                               LeaveRecord record) {
        fillRoundRect(c, l, t, r, b, 10f * u, C_WHITE);
        drawText(c, "事假", l + 28f * u, t + 42f * u,
                19f * u, C_TEXT, TF_MED, Paint.Align.LEFT);
        float labelX = l + 28f * u;
        float valueX = l + 132f * u;
        drawText(c, "请假天数", labelX, t + 88f * u,
                15.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
        drawText(c, record.days + " 天", valueX, t + 88f * u,
                16f * u, C_VALUE, TF_REG, Paint.Align.LEFT);
        drawText(c, "开始时间", labelX, t + 132f * u,
                15.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
        drawText(c, record.start, valueX, t + 132f * u,
                16f * u, C_VALUE, TF_REG, Paint.Align.LEFT);
        drawText(c, "结束时间", labelX, t + 176f * u,
                15.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
        drawText(c, record.end, valueX, t + 176f * u,
                16f * u, C_VALUE, TF_REG, Paint.Align.LEFT);

        if (record.cancelled) {
            drawBadge(c, l + 28f * u, t + 196f * u, "已销假",
                    0xFFE9F8F0, 0xFF29A96C);
        } else {
            float firstWidth = drawBadge(c, l + 28f * u, t + 196f * u, "未销假",
                    0xFFFFF4E4, 0xFFC98C36);
            drawBadge(c, l + 28f * u + firstWidth + 10f * u, t + 196f * u,
                    "已通过", 0xFFE9F8F0, 0xFF29A96C);
        }
    }

    private float drawBadge(Canvas c, float l, float t, String label,
                            int background, int textColor) {
        float width = measure(label, 12.5f * u, TF_REG) + 20f * u;
        fillRoundRect(c, l, t, l + width, t + 28f * u, 4f * u, background);
        drawText(c, label, l + width / 2f, t + 20f * u,
                12.5f * u, textColor, TF_REG, Paint.Align.CENTER);
        return width;
    }

    private void drawCirclePlus(Canvas c, float cx, float cy, float r, int color) {
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f * u);
        paint.setColor(color);
        c.drawCircle(cx, cy, r, paint);
        c.drawLine(cx - r * 0.55f, cy, cx + r * 0.55f, cy, paint);
        c.drawLine(cx, cy - r * 0.55f, cx, cy + r * 0.55f, paint);
    }

    private void openDetail(int recordIndex) {
        if (records.isEmpty()) {
            return;
        }
        selectedRecord = Math.max(0, Math.min(records.size() - 1, recordIndex));
        selectedProfile = store.getGender();
        detailExpanded = false;
        openScreen(SCREEN_DETAIL);
    }

    private LeaveRecord selectedLeave() {
        if (records.isEmpty()) {
            refreshTimes();
        }
        return records.get(Math.max(0, Math.min(records.size() - 1, selectedRecord)));
    }

    // ---------------------------------------------------------------------
    // Level 4: leave details
    // ---------------------------------------------------------------------

    private void drawLeaveDetail(Canvas c) {
        Profile profile = store.getUser();
        LeaveRecord record = selectedLeave();
        float left = contentLeft;
        float right = contentLeft + contentW;
        float width = contentW;
        // A narrow page-coloured gap above the blue cover matches the source.
        float top = 9.5f * u;

        if (record.cancelled) {
            float barH = 34f * u;
            fillRect(c, left, 0f, right, barH, 0xFFEAF7EF);
            drawCheckNode(c, left + 24f * u, barH / 2f);
            drawText(c, "已销假", left + 44f * u, barH / 2f + 6f * u,
                    15f * u, C_GREEN, TF_MED, Paint.Align.LEFT);
            top = barH + 9.5f * u;
        }

        // Blue cover.
        float coverH = 77f * u;
        paint.reset();
        paint.setShader(new LinearGradient(left, top, right, top + coverH,
                new int[]{0xFF2F86F6, 0xFF2F76EA},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(left, top, right, top + coverH, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f * u);
        paint.setColor(0x20FFFFFF);

        float cardL = left + 12f * u;
        float cardR = right - 12f * u;
        float cardTop = top + 60f * u;
        float cardH = 139f * u;
        fillRoundRect(c, cardL, cardTop, cardR, cardTop + cardH,
                9f * u, C_WHITE);

        drawAvatarImage(c, cardL, cardTop, profile);

        drawTextFit(c, profile.studentId, cardR - 16f * u, cardTop + 36f * u,
                cardR - 16f * u - (cardL + 104f * u), 17f * u, 13f * u,
                0xFFB7BFD1, TF_MED, Paint.Align.RIGHT);

        float nameMax = cardR - cardL - 156f * u;
        float nameSize = fitSize(profile.name, nameMax, 26f * u, 18f * u, TF_BOLD);
        drawText(c, ellipsize(profile.name, nameMax, nameSize, TF_BOLD),
                cardL + 18f * u, cardTop + 80f * u,
                nameSize, C_TEXT, TF_BOLD, Paint.Align.LEFT);
        float nameWidth = Math.min(measure(profile.name, nameSize, TF_BOLD), nameMax);
        drawGradeBadge(c, cardL + 18f * u + nameWidth + 8f * u,
                cardTop + 60f * u, profile.grade);

        drawTextFit(c, profile.className, cardL + 18f * u,
                cardTop + 111f * u, cardR - cardL - 36f * u,
                11.2f * u, 9.5f * u, 0xFF8C8C91, TF_REG, Paint.Align.LEFT);

        float infoTop = cardTop + cardH + 12f * u;
        float infoBottom = drawLeaveInfo(c, cardL, cardR, infoTop);

        float saleTop = infoBottom + 12f * u;
        float saleH = record.cancelled ? 500f * u : 130f * u;
        fillRoundRect(c, cardL, saleTop, cardR, saleTop + saleH,
                9f * u, C_WHITE);
        drawText(c, "销假信息", cardL + 20f * u, saleTop + 29f * u,
                18f * u, C_TEXT, TF_MED, Paint.Align.LEFT);
        drawDivider(c, cardL + 20f * u, cardR - 16f * u, saleTop + 44f * u);
        if (record.cancelled) {
            float row = saleTop + 84f * u;
            drawLabelValue(c, "销假状态", "已销假",
                    cardL + 20f * u, cardL + 160f * u, row);
            row += 54f * u;
            drawLabelValue(c, "销假类型", record.cancelType,
                    cardL + 20f * u, cardL + 160f * u, row);
            row += 54f * u;
            drawLabelValue(c, "销假时间", record.cancelTime,
                    cardL + 20f * u, cardL + 160f * u, row);
            row += 54f * u;
            drawLabelValue(c, "销假人", profile.name,
                    cardL + 20f * u, cardL + 160f * u, row);
            row += 54f * u;
            drawLabelValue(c, "销假说明", record.cancelRemark,
                    cardL + 20f * u, cardL + 160f * u, row);

            float photoLabel = saleTop + 354f * u;
            drawText(c, "合照样例", cardL + 20f * u, photoLabel,
                    17.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
            drawText(c, "暂无合照样例 ~", cardL + 20f * u, photoLabel + 32f * u,
                    16f * u, 0xFF606065, TF_REG, Paint.Align.LEFT);
            drawText(c, "学生合照", cardL + 20f * u, photoLabel + 78f * u,
                    17.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
            drawText(c, "暂无学生合照 ~", cardL + 20f * u, photoLabel + 110f * u,
                    16f * u, 0xFF606065, TF_REG, Paint.Align.LEFT);
        } else {
            drawText(c, "销假状态", cardL + 20f * u, saleTop + 90f * u,
                    16.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
            drawText(c, "未销假", cardL + 147f * u, saleTop + 90f * u,
                    16.6f * u, C_VALUE, TF_REG, Paint.Align.LEFT);
        }

        float approvalTop = saleTop + saleH + 12f * u;
        float approvalBottom = drawApproval(c, cardL, cardR, approvalTop, profile, record);

        contentHeight = approvalBottom + (record.cancelled ? 24f * u : 34f * u);
    }

    private float drawLeaveInfo(Canvas c, float l, float r, float top) {
        Profile profile = store.getUser();
        LeaveRecord record = selectedLeave();
        float labelX = l + 20f * u;
        float valueX = l + 160f * u;
        float bottom;
        if (!detailExpanded) {
            bottom = top + 560f * u;
        } else {
            bottom = top + 985f * u;
        }
        fillRoundRect(c, l, top, r, bottom, 9f * u, C_WHITE);
        drawText(c, "请假信息", labelX, top + 35.5f * u,
                18f * u, C_TEXT, TF_MED, Paint.Align.LEFT);
        drawDivider(c, l + 20f * u, r - 16f * u, top + 49f * u);

        float row = top + 110f * u;
        drawLabelValue(c, "请假开始时间", record.start,
                labelX, valueX, row);
        row += 54f * u;
        drawLabelValue(c, "请假结束时间", record.end,
                labelX, valueX, row);
        row += 54f * u;
        drawLabelValue(c, "请假天数", record.days,
                labelX, valueX, row);
        row += 54f * u;
        drawLabelValue(c, "是否出校", "是", labelX, valueX, row);
        row += 54f * u;
        drawText(c, "是否离开学校", labelX, row,
                17.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
        drawText(c, "所在地", labelX, row + 38f * u,
                17.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
        drawText(c, "否", valueX, row, 16f * u,
                C_VALUE, TF_REG, Paint.Align.LEFT);
        row += 92f * u;
        drawLabelValue(c, "请假事由", store.getLeaveReason(), labelX, valueX, row);
        row += 54f * u;
        drawLabelValue(c, "手机号", profile.phone, labelX, valueX, row);

        if (detailExpanded) {
            row += 54f * u;
            drawLabelValue(c, "学年", academicYear, labelX, valueX, row);
            row += 54f * u;
            drawLabelValue(c, "学期", term, labelX, valueX, row);
            row += 54f * u;
            drawLabelValue(c, "请假类型", "事假", labelX, valueX, row);
            row += 54f * u;
            drawLabelValue(c, "请假性质", store.getLeaveNature(), labelX, valueX, row);
            row += 54f * u;
            drawLabelValue(c, "来源类型", "学生申请", labelX, valueX, row);
            row += 54f * u;
            drawText(c, "证明材料", labelX, row,
                    17.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
            drawText(c, "暂无证明材料 ~", labelX, row + 26f * u,
                    16f * u, 0xFF606065, TF_REG, Paint.Align.LEFT);
            drawExpandRow(c, l, r, top + 930f * u, true);
            addContentHotspot(l, top + 916f * u, r, bottom,
                    () -> {
                        detailExpanded = false;
                        invalidate();
                    });
        } else {
            drawExpandRow(c, l, r, top + 525f * u, false);
            addContentHotspot(l, top + 510f * u, r, bottom,
                    () -> {
                        detailExpanded = true;
                        invalidate();
                    });
        }
        return bottom;
    }

    private void drawExpandRow(Canvas c, float l, float r, float y, boolean up) {
        float cx = (l + r) / 2f;
        String label = up ? "收起" : "展开";
        float size = 16f * u;
        float textW = measure(label, size, TF_REG);
        drawText(c, label, cx - 6f * u, y, size,
                0xFF4F8FEA, TF_REG, Paint.Align.CENTER);
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFF4F8FEA);
        float x = cx + textW / 2f + 1f * u;
        path.reset();
        if (up) {
            path.moveTo(x - 4f * u, y - 1f * u);
            path.lineTo(x, y - 6f * u);
            path.lineTo(x + 4f * u, y - 1f * u);
        } else {
            path.moveTo(x - 4f * u, y - 5f * u);
            path.lineTo(x, y);
            path.lineTo(x + 4f * u, y - 5f * u);
        }
        c.drawPath(path, paint);
    }

    private void drawLabelValue(Canvas c, String label, String value,
                                float labelX, float valueX, float baseline) {
        drawText(c, label, labelX, baseline,
                17.5f * u, C_LABEL, TF_REG, Paint.Align.LEFT);
        drawTextFit(c, value, valueX, baseline,
                contentLeft + contentW - 28f * u - valueX,
                16f * u, 12f * u, C_VALUE, TF_REG, Paint.Align.LEFT);
    }

    private void drawGradeBadge(Canvas c, float l, float t, String grade) {
        float width = measure(grade, 10.5f * u, TF_REG) + 14f * u;
        fillRoundRect(c, l, t, l + width, t + 22f * u,
                2f * u, 0xFFF1F2F4);
        drawText(c, grade, l + width / 2f, t + 15f * u,
                10.5f * u, 0xFF77777C, TF_REG, Paint.Align.CENTER);
    }

    private float drawApproval(Canvas c, float l, float r, float top,
                               Profile p, LeaveRecord record) {
        float bottom = top + 400f * u;
        fillRoundRect(c, l, top, r, bottom, 9f * u, C_WHITE);
        drawText(c, "审核流程", l + 20f * u, top + 31f * u,
                18f * u, C_TEXT, TF_MED, Paint.Align.LEFT);
        drawDivider(c, l + 16f * u, r - 16f * u, top + 44f * u);

        float cx = l + 28f * u;
        float firstCy = top + 82f * u;
        float secondCy = top + 245f * u;
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f * u);
        paint.setColor(0xFFE5E5E5);
        c.drawLine(cx, firstCy + 11f * u, cx, secondCy - 11f * u, paint);

        drawCheckNode(c, cx, firstCy);
        drawText(c, "发起申请", l + 47f * u, firstCy + 6f * u,
                17f * u, C_TEXT, TF_MED, Paint.Align.LEFT);
        String apply = record.apply;
        drawTimestampPill(c, l + 47f * u, firstCy + 14f * u, apply);

        drawCheckNode(c, cx, secondCy);
        drawText(c, "辅导员审核", l + 47f * u, secondCy + 6f * u,
                17f * u, C_TEXT, TF_MED, Paint.Align.LEFT);
        drawText(c, "审核人：" + store.getCounselorName(),
                l + 47f * u, secondCy + 40f * u,
                15f * u, 0xFF5F5F5F, TF_REG, Paint.Align.LEFT);
        drawText(c, "审核意见：同意", l + 47f * u, secondCy + 67f * u,
                15f * u, 0xFF5F5F5F, TF_REG, Paint.Align.LEFT);
        String approve = record.approve;
        drawTimestampPill(c, l + 47f * u, secondCy + 85f * u, approve);
        return bottom;
    }

    private void drawCheckNode(Canvas c, float cx, float cy) {
        fillCircle(c, cx, cy, 11f * u, C_GREEN);
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFFFFFFFF);
        path.reset();
        path.moveTo(cx - 5f * u, cy);
        path.lineTo(cx - 1.5f * u, cy + 3.5f * u);
        path.lineTo(cx + 5.5f * u, cy - 4f * u);
        c.drawPath(path, paint);
    }

    private void drawTimestampPill(Canvas c, float l, float top, String value) {
        float textSize = 12.5f * u;
        float width = measure(value, textSize, TF_REG) + 16f * u;
        fillRoundRect(c, l, top, l + width, top + 22f * u,
                2f * u, 0xFFF7F7F7);
        drawText(c, value, l + 8f * u, top + 15f * u,
                textSize, 0xFFA2A2A7, TF_REG, Paint.Align.LEFT);
    }

    /**
     * Draws the avatar crop taken directly from the supplied screenshot.  The
     * crop includes the original circular illustration and its gender badge.
     */
    private void drawAvatarImage(Canvas c, float cardLeft, float cardTop, Profile profile) {
        Bitmap avatar = profile.female ? avatarFemale : avatarMale;
        if (avatar == null) {
            return;
        }
        float targetWidth = 100f * u;
        float targetHeight = targetWidth * avatar.getHeight() / avatar.getWidth();
        float left = cardLeft + 13f * u;
        // Straddle the top edge of the white card: the upper half sits on the
        // blue cover, the lower half inside the profile card, as in the source.
        float top = cardTop - 48f * u;
        paint.reset();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        c.drawBitmap(avatar, null,
                new RectF(left, top, left + targetWidth, top + targetHeight), paint);
    }

    private void drawDetailActionBar(Canvas c) {
        float safe = bottomSafe();
        float barH = 72f * u;
        float top = getHeight() - barH;
        fillRect(c, 0f, top, getWidth(), getHeight(), C_WHITE);
        paint.reset();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE7E7E7);
        c.drawRect(0f, top, getWidth(), top + 0.7f * u, paint);

        float left = contentLeft + 12f * u;
        float right = contentLeft + contentW - 12f * u;
        float buttonTop = top + 14f * u;
        float buttonH = 44f * u;
        float gap = 10f * u;
        float buttonW = (right - left - gap) / 2f;

        strokeRoundRect(c, left, buttonTop, left + buttonW, buttonTop + buttonH,
                5f * u, 0xFFD8D8DA, 1f * u);
        drawText(c, "定位销假", left + buttonW / 2f, buttonTop + 29f * u,
                15f * u, 0xFF444449, TF_REG, Paint.Align.CENTER);

        fillRoundRect(c, left + buttonW + gap, buttonTop,
                right, buttonTop + buttonH, 5f * u, C_BLUE);
        drawText(c, "续假申请", left + buttonW + gap + buttonW / 2f,
                buttonTop + 29f * u, 15f * u, C_WHITE, TF_REG, Paint.Align.CENTER);
    }

    private void drawBottomSafe(Canvas c, boolean dark) {
        float safe = bottomSafe();
        float top = getHeight() - safe;
        fillRect(c, 0f, top, getWidth(), getHeight(), dark ? C_DARK_BG : C_PAGE);
    }

    private void drawPill(Canvas c, float cx, float cy, int color) {
        fillRoundRect(c, cx - 35f * u, cy - 2f * u,
                cx + 35f * u, cy + 2f * u, 2f * u, color);
    }

    // ---------------------------------------------------------------------
    // Input and scrolling
    // ---------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                scroller.forceFinished(true);
                downX = event.getX();
                downY = event.getY();
                downScroll = scroll;
                dragging = false;
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float dy = event.getY() - downY;
                if (!dragging && Math.abs(dy) > touchSlop) {
                    dragging = true;
                }
                if (dragging) {
                    scroll = clamp(downScroll - dy, 0f, maxScroll);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (!dragging && Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) {
                    handleTap(event.getX(), event.getY());
                } else if (dragging && maxScroll > 0f && velocityTracker != null) {
                    velocityTracker.computeCurrentVelocity(1000);
                    int velocity = (int) velocityTracker.getYVelocity();
                    if (Math.abs(velocity) > 250) {
                        scroller.fling(0, (int) scroll, 0, -velocity,
                                0, 0, 0, (int) maxScroll);
                        postInvalidateOnAnimation();
                    }
                }
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (velocityTracker != null) {
                    velocityTracker.recycle();
                    velocityTracker = null;
                }
                dragging = false;
                return true;
            }
            default:
                return true;
        }
    }

    private void handleTap(float x, float y) {
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            Hotspot hotspot = hotspots.get(i);
            if (hotspot.bounds.contains(x, y)) {
                hotspot.action.run();
                return;
            }
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scroll = clamp(scroller.getCurrY(), 0f, maxScroll);
            postInvalidateOnAnimation();
        }
    }

    private void addHotspot(float l, float t, float r, float b, Runnable action) {
        hotspots.add(new Hotspot(new RectF(l, t, r, b), action));
    }

    private void addContentHotspot(float l, float t, float r, float b, Runnable action) {
        float offset = viewportTop() - scroll;
        hotspots.add(new Hotspot(new RectF(l, t + offset, r, b + offset), action));
    }

    // ---------------------------------------------------------------------
    // Painting helpers
    // ---------------------------------------------------------------------

    private void drawText(Canvas c, String value, float x, float baseline,
                          float size, int color, Typeface typeface, Paint.Align align) {
        textPaint.reset();
        textPaint.setAntiAlias(true);
        textPaint.setSubpixelText(true);
        textPaint.setColor(color);
        textPaint.setTextSize(size * TEXT_SCALE);
        textPaint.setTypeface(typeface);
        textPaint.setLetterSpacing(0.035f);
        textPaint.setHinting(Paint.HINTING_OFF);
        textPaint.setSubpixelText(true);
        textPaint.setTextAlign(align);
        textPaint.setStyle(Paint.Style.FILL);
        c.drawText(value, x, baseline, textPaint);
    }

    private void drawTextFit(Canvas c, String value, float x, float baseline,
                             float maxWidth, float maxSize, float minSize,
                             int color, Typeface typeface, Paint.Align align) {
        if (maxWidth <= 0f) {
            return;
        }
        float size = fitSize(value, maxWidth, maxSize, minSize, typeface);
        drawText(c, ellipsize(value, maxWidth, size, typeface),
                x, baseline, size, color, typeface, align);
    }

    private float fitSize(String value, float maxWidth, float maxSize,
                          float minSize, Typeface typeface) {
        float size = maxSize;
        while (size > minSize && measure(value, size, typeface) > maxWidth) {
            size -= 0.4f * u;
        }
        return size;
    }

    private float measure(String value, float size, Typeface typeface) {
        textPaint.reset();
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(size * TEXT_SCALE);
        textPaint.setTypeface(typeface);
        textPaint.setLetterSpacing(0.035f);
        textPaint.setHinting(Paint.HINTING_OFF);
        textPaint.setSubpixelText(true);
        textPaint.setStyle(Paint.Style.FILL);
        return textPaint.measureText(value);
    }

    private String ellipsize(String value, float maxWidth, float size, Typeface typeface) {
        if (value == null) {
            return "";
        }
        if (measure(value, size, typeface) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        float ellipsisWidth = measure(ellipsis, size, typeface);
        int end = value.length();
        while (end > 0 && measure(value.substring(0, end), size, typeface)
                + ellipsisWidth > maxWidth) {
            end--;
        }
        return value.substring(0, Math.max(0, end)) + ellipsis;
    }

    private void fillRect(Canvas c, float l, float t, float r, float b, int color) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRect(l, t, r, b, paint);
    }

    private void fillRoundRect(Canvas c, float l, float t, float r, float b,
                               float radius, int color) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
    }

    private void strokeRoundRect(Canvas c, float l, float t, float r, float b,
                                 float radius, int color, float strokeWidth) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(color);
        c.drawRoundRect(new RectF(l, t, r, b), radius, radius, paint);
    }

    private void fillCircle(Canvas c, float cx, float cy, float radius, int color) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawCircle(cx, cy, radius, paint);
    }

    private void drawDivider(Canvas c, float l, float r, float y) {
        paint.reset();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(C_DIVIDER);
        c.drawRect(l, y, r, y + 0.8f * u, paint);
    }

    private void drawChevronRight(Canvas c, float x, float cy, float size, int color) {
        paint.reset();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f * u);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
        path.reset();
        path.moveTo(x - size * 0.34f, cy - size * 0.52f);
        path.lineTo(x + size * 0.16f, cy);
        path.lineTo(x - size * 0.34f, cy + size * 0.52f);
        c.drawPath(path, paint);
    }

    private void canvasRoundClip(Canvas c, float l, float t, float r, float b, float radius) {
        c.save();
        path.reset();
        path.addRoundRect(new RectF(l, t, r, b), radius, radius, Path.Direction.CW);
        c.clipPath(path);
    }

    private float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    private static final class Hotspot {
        final RectF bounds;
        final Runnable action;

        Hotspot(RectF bounds, Runnable action) {
            this.bounds = bounds;
            this.action = action;
        }
    }
}
