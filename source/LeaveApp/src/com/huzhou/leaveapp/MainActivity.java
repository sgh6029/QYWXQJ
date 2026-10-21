package com.huzhou.leaveapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Single-activity, fully offline application shell.
 *
 * Message / Me / Workbench are three top-level pages.  Workbench contains the
 * original leave application; Message and Me use the supplied screenshots.
 */
public final class MainActivity extends Activity {
    private static final String STATE_SCREEN = "screen";
    private static final String STATE_SCROLL = "scroll";
    private static final String STATE_EXPANDED = "expanded";
    private static final String STATE_TAB = "tab";
    private static final String STATE_ENTERPRISE = "enterprise";

    private static final int TAB_MESSAGE = 0;
    private static final int TAB_DOCUMENT = 1;
    private static final int TAB_CONTACTS = 2;
    private static final int TAB_WORKBENCH = 3;
    private static final int TAB_ME = 4;
    private static final int REQ_AVATAR = 7001;

    private FrameLayout root;
    private ProfileStore store;
    private LeaveAppView appView;
    private GatePanel gatePanel;
    private ImageView splashOverlay;
    private Bitmap splashBitmap;
    private Runnable splashFade;
    private boolean sessionActivated;
    private boolean showingEnterprise;
    private int currentTab = TAB_MESSAGE;

    private int restoreScreen = LeaveAppView.SCREEN_WORKBENCH;
    private float restoreScroll = 0f;
    private boolean restoreExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        store = new ProfileStore(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        hideSystemBars();

        sessionActivated = store.isActivated();
        if (savedInstanceState != null) {
            restoreScreen = savedInstanceState.getInt(STATE_SCREEN, LeaveAppView.SCREEN_WORKBENCH);
            restoreScroll = savedInstanceState.getFloat(STATE_SCROLL, 0f);
            restoreExpanded = savedInstanceState.getBoolean(STATE_EXPANDED, false);
            currentTab = savedInstanceState.getInt(STATE_TAB, TAB_MESSAGE);
            showingEnterprise = savedInstanceState.getBoolean(STATE_ENTERPRISE, false);
            if (!sessionActivated) {
                showGate();
            } else {
                restoreCurrentPage();
            }
        } else {
            startSplash();
        }
    }

    private void configureWindow() {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(attributes);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private void hideSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            View decor = window.getDecorView();
            WindowInsetsController controller = decor.getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    /**
     * Shows the enterprise launch screen as a static picture, with the real
     * page already rendered underneath.  After a short hold the picture fades
     * away as a whole; nothing moves, so the logo never drifts out of place.
     */
    private void startSplash() {
        if (store.isActivated()) {
            sessionActivated = true;
            showMessagePage();
        } else {
            showGate();
        }

        splashBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.splash_enterprise);
        splashOverlay = new ImageView(this);
        splashOverlay.setBackgroundColor(Color.BLACK);
        splashOverlay.setScaleType(ImageView.ScaleType.CENTER_CROP);
        splashOverlay.setImageBitmap(splashBitmap);
        splashOverlay.setClickable(true);
        // Swallow taps while the launch screen is still covering the page.
        splashOverlay.setOnTouchListener((view, event) -> true);
        root.addView(splashOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        splashFade = this::fadeOutSplash;
        // Start the hold timer on the first drawn frame, not when the view is
        // merely attached: on a slow cold start the window can stay hidden
        // behind the system launch screen for a while.
        splashOverlay.postOnAnimation(() -> {
            if (splashOverlay != null) {
                splashOverlay.postDelayed(splashFade, 820);
            }
        });
    }

    private void fadeOutSplash() {
        final ImageView view = splashOverlay;
        if (view == null) {
            return;
        }
        view.removeCallbacks(splashFade);
        view.animate()
                .alpha(0f)
                .setDuration(460)
                .withEndAction(() -> removeSplashView(view))
                .start();
    }

    private void removeSplashView(ImageView view) {
        if (view.getParent() == root) {
            root.removeView(view);
        }
        view.setImageDrawable(null);
        if (splashBitmap != null && !splashBitmap.isRecycled()) {
            splashBitmap.recycle();
        }
        splashBitmap = null;
        if (splashOverlay == view) {
            splashOverlay = null;
        }
    }

    private void discardSplash() {
        final ImageView view = splashOverlay;
        if (view == null) {
            return;
        }
        view.removeCallbacks(splashFade);
        view.animate().cancel();
        removeSplashView(view);
    }

    private void showGate() {
        gatePanel = new GatePanel(this, store, activated -> {
            sessionActivated = activated;
            if (activated) {
                store.markActivated(true);
            }
            final GatePanel current = gatePanel;
            if (current != null) {
                current.animate().alpha(0f).setDuration(180).withEndAction(() -> {
                    root.removeView(current);
                    if (gatePanel == current) {
                        gatePanel = null;
                    }
                    currentTab = TAB_MESSAGE;
                    showMessagePage();
                }).start();
            }
        });
        root.addView(gatePanel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void restoreCurrentPage() {
        if (showingEnterprise) {
            showEnterprisePage();
        } else if (currentTab == TAB_WORKBENCH) {
            showWorkbench(restoreScreen, restoreScroll, restoreExpanded);
        } else if (currentTab == TAB_ME) {
            showMePage();
        } else if (currentTab == TAB_DOCUMENT) {
            showDocumentPage();
        } else if (currentTab == TAB_CONTACTS) {
            showContactsPage();
        } else {
            showMessagePage();
        }
    }

    private void clearPage() {
        appView = null;
        root.removeAllViews();
    }

    private void showMessagePage() {
        currentTab = TAB_MESSAGE;
        showingEnterprise = false;
        clearPage();
        ImagePageView page = new ImagePageView(this, ImagePageView.TYPE_MESSAGE, store,
                new ImagePageView.Listener() {
                    @Override
                    public void onShellTab(int tab) {
                        MainActivity.this.onShellTab(tab);
                    }

                    @Override
                    public void onBack() {
                        showMessagePage();
                    }

                    @Override
                    public void onPageItem(int type, int index) {
                    }
                });
        root.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void showDocumentPage() {
        currentTab = TAB_DOCUMENT;
        showingEnterprise = false;
        clearPage();
        ImagePageView page = new ImagePageView(this, ImagePageView.TYPE_DOCUMENT, store,
                new ImagePageView.Listener() {
                    @Override
                    public void onShellTab(int tab) {
                        MainActivity.this.onShellTab(tab);
                    }

                    @Override
                    public void onBack() {
                        showMessagePage();
                    }

                    @Override
                    public void onPageItem(int type, int index) {
                        MainActivity.this.onPageItem(type, index);
                    }
                });
        root.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void showContactsPage() {
        currentTab = TAB_CONTACTS;
        showingEnterprise = false;
        clearPage();
        ImagePageView page = new ImagePageView(this, ImagePageView.TYPE_CONTACTS, store,
                new ImagePageView.Listener() {
                    @Override
                    public void onShellTab(int tab) {
                        MainActivity.this.onShellTab(tab);
                    }

                    @Override
                    public void onBack() {
                        showMessagePage();
                    }

                    @Override
                    public void onPageItem(int type, int index) {
                        MainActivity.this.onPageItem(type, index);
                    }
                });
        root.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void showMePage() {
        currentTab = TAB_ME;
        showingEnterprise = false;
        clearPage();
        MyPageView page = new MyPageView(this, store, new MyPageView.Listener() {
            @Override
            public void onShellTab(int tab) {
                MainActivity.this.onShellTab(tab);
            }

            @Override
            public void onEnterprise() {
                showEnterprisePage();
            }

            @Override
            public void onEditName() {
                editMyName();
            }

            @Override
            public void onEditAvatar() {
                editMyAvatar();
            }

            @Override
            public void onMenu(int index) {
                MainActivity.this.onMeMenu(index);
            }
        });
        root.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void showWorkbench() {
        showWorkbench(LeaveAppView.SCREEN_WORKBENCH, 0f, false);
    }

    private void showWorkbench(int screen, float scroll, boolean expanded) {
        currentTab = TAB_WORKBENCH;
        showingEnterprise = false;
        clearPage();
        appView = new LeaveAppView(this, store, this::editProfiles);
        appView.setShellNavListener(this::onShellTab);
        appView.setInitialState(screen, scroll, expanded);
        appView.setAlpha(1f);
        root.addView(appView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void showEnterprisePage() {
        showingEnterprise = true;
        clearPage();
        ImagePageView page = new ImagePageView(this, ImagePageView.TYPE_ENTERPRISE, store,
                new ImagePageView.Listener() {
                    @Override
                    public void onShellTab(int tab) {
                        MainActivity.this.onShellTab(tab);
                    }

                    @Override
                    public void onBack() {
                        showMePage();
                    }

                    @Override
                    public void onPageItem(int type, int index) {
                    }
                });
        root.addView(page, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void onShellTab(int tab) {
        if (tab == TAB_MESSAGE) {
            showMessagePage();
        } else if (tab == TAB_DOCUMENT) {
            showDocumentPage();
        } else if (tab == TAB_CONTACTS) {
            showContactsPage();
        } else if (tab == TAB_ME) {
            showMePage();
        } else if (tab == TAB_WORKBENCH) {
            showWorkbench();
        }
    }

    private void onPageItem(int type, int index) {
        String message;
        if (type == ImagePageView.TYPE_CONTACTS) {
            switch (index) {
                case 0:
                    message = "智能机器人：离线演示";
                    break;
                case 1:
                    message = "外部联系人：暂未添加";
                    break;
                case 2:
                    message = "添加外部联系人：离线演示";
                    break;
                case 3:
                    message = "搜索联系人：离线演示";
                    break;
                default:
                    message = "添加联系人：离线演示";
                    break;
            }
        } else {
            switch (index) {
                case 0:
                case 1:
                    message = "离线演示：文档暂不支持打开";
                    break;
                case 2:
                    message = "搜索文档：离线演示";
                    break;
                default:
                    message = "新建文档：离线演示";
                    break;
            }
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void onMeMenu(int index) {
        if (index == 0) {
            Toast.makeText(this, "收藏为空", Toast.LENGTH_SHORT).show();
        } else if (index == 1) {
            Toast.makeText(this, "暂无红包", Toast.LENGTH_SHORT).show();
        } else if (index == 2) {
            Toast.makeText(this, "通勤信息未配置", Toast.LENGTH_SHORT).show();
        } else if (index == 3) {
            new AlertDialog.Builder(this)
                    .setTitle("使用说明")
                    .setMessage("本程序为离线演示，全部资料只保存在本机。\n\n"
                            + "【一级页面】\n"
                            + "· 消息 / 文档 / 通讯录 / 工作台 / 我 均可点击。\n"
                            + "· 工作台点「学生请假」或「学工系统」后，先显示 1 秒黑屏和蓝色进度条，"
                            + "再显示 2 秒白屏（地址栏为「学生请假」），然后进入请假列表。\n"
                            + "· 文档、通讯录里的条目、搜索、新增按钮均有点击反馈。\n\n"
                            + "【通讯录水印】\n"
                            + "自动读取「我 → 设置」里的姓名和学号：\n"
                            + "第一行：姓名@荆州学院\n"
                            + "第二行：学号 yyyy-MM-dd HH:mm\n"
                            + "修改资料后重新进入通讯录即可更新。\n\n"
                            + "【我的设置】\n"
                            + "从 我 → 设置 进入（未激活会先要求激活码）：\n"
                            + "· 性别：男生 / 女生\n"
                            + "· 个人信息：姓名、学号、学院·专业·班级、手机号\n"
                            + "· 请假设置：理由、学年、学期、性质、辅导员\n"
                            + "保存后同步到我的页、请假列表/详情和通讯录水印。\n"
                            + "自定义时间不在设置里，在请假详情右上角 ⋮，只改当前这一条。\n\n"
                            + "【请假】\n"
                            + "列表共 7 条：1 条「已通过、未销假」+ 6 条「已销假」。\n"
                            + "未销假：前一天 17:00-19:00；已销假：2 天前到 7 天前，"
                            + "结束时间 +2 小时，按 0.08 天计算。\n"
                            + "每张卡片都能点开；已销假详情有绿色状态条、销假信息、"
                            + "合照样例/学生合照占位，没有底部两个按钮。\n\n"
                            + "【隐私与安装】\n"
                            + "真实姓名、学号、手机号、头像和水印均未打包，默认示例资料可在设置修改。\n"
                            + "APK 不申请权限、不联网、不读取相册；安装时如提示未知来源，选「仍要安装」。")
                    .setPositiveButton("知道了", null)
                    .show();
        } else if (index == 4) {
            openSettings();
        }
    }

    private void openSettings() {
        ensureActivated(() -> SettingsDialog.show(this, store, () -> {
            if (appView != null) {
                appView.onProfilesChanged();
            }
            if (currentTab == TAB_ME) {
                showMePage();
            }
        }));
    }

    private void ensureActivated(Runnable action) {
        if (sessionActivated) {
            action.run();
            return;
        }
        ActivationDialog.show(this, () -> {
            sessionActivated = true;
            store.markActivated(true);
            action.run();
        });
    }

    private void editMyName() {
        ensureActivated(() -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setText(store.getMeName());
            input.setHint("输入姓名");
            int pad = Math.round(18 * getResources().getDisplayMetrics().density);
            FrameLayout box = new FrameLayout(this);
            box.setPadding(pad, pad / 2, pad, 0);
            box.addView(input, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT));
            new AlertDialog.Builder(this)
                    .setTitle("修改姓名")
                    .setView(box)
                    .setPositiveButton("保存", (dialog, which) -> {
                        String name = input.getText().toString().trim();
                        store.saveMeName(name);
                        if (!name.isEmpty()) {
                            Profile profile = store.getUser();
                            profile.name = name;
                            store.save(profile);
                        }
                        showMePage();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void editMyAvatar() {
        ensureActivated(() -> {
            String[] choices = {"女生头像（默认）", "男生头像", "自定义上传"};
            int[] values = {2, 1, 3};
            new AlertDialog.Builder(this)
                    .setTitle("更换头像")
                    .setItems(choices, (dialog, which) -> {
                        if (values[which] == 3) {
                            pickAvatarImage();
                        } else {
                            store.saveMeAvatar(values[which]);
                            showMePage();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void pickAvatarImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQ_AVATAR);
        } catch (Exception ex) {
            Toast.makeText(this, "无法打开相册", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_AVATAR || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            Bitmap source;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                source = BitmapFactory.decodeStream(in, null, options);
            }
            if (source == null) {
                Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
                return;
            }
            // The original avatar is 224 x 200 (aspect 1.12); crop centrally to
            // that aspect, then compress to twice the display resolution.
            float targetAspect = 224f / 200f;
            int cropW = source.getWidth();
            int cropH = source.getHeight();
            if (cropW / (float) cropH > targetAspect) {
                cropW = Math.round(cropH * targetAspect);
            } else {
                cropH = Math.round(cropW / targetAspect);
            }
            int cropX = (source.getWidth() - cropW) / 2;
            int cropY = (source.getHeight() - cropH) / 2;
            Bitmap cropped = Bitmap.createBitmap(source, cropX, cropY, cropW, cropH);
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, 448, 400, true);
            File file = new File(getFilesDir(), "me_avatar.jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out);
            }
            if (source != cropped && !source.isRecycled()) {
                source.recycle();
            }
            if (cropped != scaled && !cropped.isRecycled()) {
                cropped.recycle();
            }
            if (!scaled.isRecycled()) {
                scaled.recycle();
            }
            store.saveMeAvatarPath(file.getAbsolutePath());
            store.saveMeAvatar(3);
            showMePage();
        } catch (Exception ex) {
            Toast.makeText(this, "头像处理失败", Toast.LENGTH_SHORT).show();
        }
    }

    void editProfiles(int recordIndex, String start, String end, String days,
                      String apply, String approve) {
        if (!sessionActivated) {
            ActivationDialog.show(this, () -> {
                sessionActivated = true;
                store.markActivated(true);
                openTimeDialog(recordIndex, start, end, days, apply, approve);
            });
            return;
        }
        openTimeDialog(recordIndex, start, end, days, apply, approve);
    }

    private void openTimeDialog(int recordIndex, String start, String end, String days,
                                String apply, String approve) {
        TimeDialog.show(this, store, recordIndex, start, end, days, apply, approve, () -> {
            if (appView != null) {
                appView.onProfilesChanged();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
        if (appView != null) {
            appView.onHostResume();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onBackPressed() {
        if (showingEnterprise) {
            showMePage();
            return;
        }
        if (gatePanel != null) {
            super.onBackPressed();
            return;
        }
        if (appView != null && appView.canGoBack()) {
            appView.goBack();
            return;
        }
        if (currentTab == TAB_WORKBENCH || currentTab == TAB_ME
                || currentTab == TAB_DOCUMENT || currentTab == TAB_CONTACTS) {
            showMessagePage();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        discardSplash();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, currentTab);
        outState.putBoolean(STATE_ENTERPRISE, showingEnterprise);
        if (appView != null) {
            outState.putInt(STATE_SCREEN, appView.getScreen());
            outState.putFloat(STATE_SCROLL, appView.getScrollYPosition());
            outState.putBoolean(STATE_EXPANDED, appView.isDetailExpanded());
        }
    }
}
