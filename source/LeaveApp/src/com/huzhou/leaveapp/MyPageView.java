package com.huzhou.leaveapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/** The "Me" page uses the supplied screenshot, with editable name/avatar overlays. */
final class MyPageView extends View {
    interface Listener {
        void onShellTab(int tab);
        void onEnterprise();
        void onEditName();
        void onEditAvatar();
        void onMenu(int index);
    }

    private static final float IMAGE_W = 1260f;
    private static final float IMAGE_H = 2800f;
    private static final float STATUS_CROP = 100f;

    private final Bitmap page;
    private final Bitmap avatarMale;
    private final Bitmap avatarFemale;
    private Bitmap customAvatar;
    private final ProfileStore store;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final Path clip = new Path();

    MyPageView(Context context, ProfileStore store, Listener listener) {
        super(context);
        this.store = store;
        this.listener = listener;
        page = BitmapFactory.decodeResource(getResources(), R.drawable.page_me_blank);
        avatarMale = BitmapFactory.decodeResource(getResources(), R.drawable.avatar_male);
        avatarFemale = BitmapFactory.decodeResource(getResources(), R.drawable.avatar_female);
        String path = store.getMeAvatarPath();
        if (path != null && !path.isEmpty()) {
            customAvatar = BitmapFactory.decodeFile(path);
        }
        textPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        textPaint.setColor(0xFFFFFFFF);
        setBackgroundColor(0xFF000000);
    }

    private float sx() {
        return getWidth() / IMAGE_W;
    }

    private float sy() {
        return getHeight() / (IMAGE_H - STATUS_CROP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (page == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        src.set(0, (int) STATUS_CROP, page.getWidth(), page.getHeight());
        dst.set(0f, 0f, getWidth(), getHeight());
        canvas.drawBitmap(page, src, dst, paint);

        int avatarChoice = store.getMeAvatar();
        if (avatarChoice == 3) {
            drawAvatarOverlay(canvas, customAvatar != null ? customAvatar : avatarFemale);
        } else if (avatarChoice == 1 || avatarChoice == 2 || avatarChoice == 0) {
            drawAvatarOverlay(canvas, avatarChoice == 1 ? avatarMale : avatarFemale);
        }

        String name = store.getMeName();
        if (name != null && !name.isEmpty()) {
            drawNameOverlay(canvas, name);
        }
    }

    private void drawNameOverlay(Canvas canvas, String value) {
        float sx = sx();
        float sy = sy();
        float left = 80f * sx;
        float top = (690f - STATUS_CROP) * sy;
        float right = 520f * sx;
        float bottom = (870f - STATUS_CROP) * sy;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF18191B);
        canvas.drawRect(left, top, right, bottom, paint);

        float base = (805f - STATUS_CROP) * sy;
        float size = 64f * sy;
        textPaint.setTextSize(size);
        float max = right - 110f * sx;
        float width = textPaint.measureText(value);
        if (width > max && width > 0f) {
            textPaint.setTextSize(size * max / width);
        }
        canvas.drawText(value, 110f * sx, base, textPaint);
    }

    private void drawAvatarOverlay(Canvas canvas, Bitmap avatar) {
        if (avatar == null) {
            return;
        }
        float sx = sx();
        float sy = sy();
        float left = 928f * sx;
        float top = (420f - STATUS_CROP) * sy;
        float right = 1152f * sx;
        float bottom = (620f - STATUS_CROP) * sy;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF18191B);
        canvas.drawRect(left, top, right, bottom, paint);

        clip.reset();
        RectF area = new RectF(left, top, right, bottom);
        clip.addRoundRect(area, 14f * sx, 14f * sx, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        float scale = Math.min(area.width() / avatar.getWidth(),
                area.height() / avatar.getHeight());
        float w = avatar.getWidth() * scale;
        float h = avatar.getHeight() * scale;
        float dx = area.centerX() - w / 2f;
        float dy = area.centerY() - h / 2f;
        canvas.drawBitmap(avatar, null,
                new RectF(dx, dy, dx + w, dy + h), paint);
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (page != null && !page.isRecycled()) {
            page.recycle();
        }
        if (customAvatar != null && !customAvatar.isRecycled()) {
            customAvatar.recycle();
        }
        if (avatarMale != null && !avatarMale.isRecycled()) {
            avatarMale.recycle();
        }
        if (avatarFemale != null && !avatarFemale.isRecycled()) {
            avatarFemale.recycle();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        if (y > getHeight() * 0.92f) {
            int tab = (int) (x / (getWidth() / 5f));
            if (tab < 0) {
                tab = 0;
            }
            if (tab > 4) {
                tab = 4;
            }
            if (listener != null && (tab == 0 || tab == 1 || tab == 2 || tab == 3)) {
                listener.onShellTab(tab);
            }
            return true;
        }

        float ix = x / sx();
        float iy = y / sy() + STATUS_CROP;
        if (iy >= 1115f && iy <= 1280f) {
            if (listener != null) {
                listener.onMenu(0);
            }
            return true;
        }
        if (iy >= 1325f && iy <= 1490f) {
            if (listener != null) {
                listener.onMenu(1);
            }
            return true;
        }
        if (iy >= 1535f && iy <= 1700f) {
            if (listener != null) {
                listener.onMenu(2);
            }
            return true;
        }
        if (iy >= 1790f && iy <= 1955f) {
            if (listener != null) {
                listener.onMenu(3);
            }
            return true;
        }
        if (iy >= 2005f && iy <= 2175f) {
            if (listener != null) {
                listener.onMenu(4);
            }
            return true;
        }
        if (ix >= 70f && ix <= 900f && iy >= 880f && iy <= 1000f) {
            if (listener != null) {
                listener.onEnterprise();
            }
            return true;
        }
        if (ix >= 80f && ix <= 560f && iy >= 390f && iy <= 530f) {
            if (listener != null) {
                listener.onEnterprise();
            }
            return true;
        }
        if (ix >= 70f && ix <= 780f && iy >= 690f && iy <= 870f) {
            if (listener != null) {
                listener.onEditName();
            }
            return true;
        }
        if (ix >= 915f && ix <= 1165f && iy >= 405f && iy <= 635f) {
            if (listener != null) {
                listener.onEditAvatar();
            }
            return true;
        }
        return true;
    }
}
