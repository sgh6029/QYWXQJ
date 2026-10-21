package com.huzhou.leaveapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Full-screen image page used for the message list, document list, contacts and
 * the enterprise card.  The contacts page paints a live watermark built from
 * the profile fields instead of the watermark that was baked into the source
 * screenshot.
 */
final class ImagePageView extends View {
    static final int TYPE_MESSAGE = 0;
    static final int TYPE_ENTERPRISE = 1;
    static final int TYPE_DOCUMENT = 2;
    static final int TYPE_CONTACTS = 3;

    interface Listener {
        void onShellTab(int tab);

        void onBack();

        /** A row or header action on the document / contacts page. */
        void onPageItem(int type, int index);
    }

    private final Bitmap image;
    private final int type;
    private final ProfileStore store;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint watermarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private String watermarkLine1;
    private String watermarkLine2;
    private long watermarkStamp;

    ImagePageView(Context context, int type, ProfileStore store, Listener listener) {
        super(context);
        this.type = type;
        this.store = store;
        this.listener = listener;
        int res = R.drawable.page_message;
        if (type == TYPE_ENTERPRISE) {
            res = R.drawable.page_enterprise;
        } else if (type == TYPE_DOCUMENT) {
            res = R.drawable.page_docs;
        } else if (type == TYPE_CONTACTS) {
            res = R.drawable.page_contacts;
        }
        image = BitmapFactory.decodeResource(getResources(), res);
        setBackgroundColor(0xFF000000);
        watermarkPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        watermarkPaint.setColor(0x26FFFFFF);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (image == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }
        // The supplied screenshots include the phone status bar.  Crop it and
        // stretch the remaining page to the full display.
        src.set(0, 100, image.getWidth(), image.getHeight());
        dst.set(0f, 0f, getWidth(), getHeight());
        canvas.drawBitmap(image, src, dst, paint);
        if (type == TYPE_CONTACTS) {
            drawWatermark(canvas);
        }
    }

    private void drawWatermark(Canvas canvas) {
        if (store == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (watermarkLine1 == null || now - watermarkStamp > 60000L) {
            watermarkStamp = now;
            String name = store.getMeName();
            if (name == null || name.trim().isEmpty()) {
                name = "示例用户";
            }
            Profile profile = store.getUser();
            String studentId = profile == null || profile.studentId == null
                    ? "" : profile.studentId.trim();
            watermarkLine1 = name.trim() + "@荆州学院";
            watermarkLine2 = (studentId.isEmpty() ? "未填写学号" : studentId)
                    + " " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                    .format(new Date(now));
        }

        float width = getWidth();
        float height = getHeight();
        float scale = width / 1260f;
        float textSize = 23.5f * scale;
        watermarkPaint.setTextSize(textSize);
        float lineGap = textSize * 1.35f;
        canvas.save();
        canvas.clipRect(0f, height * 0.11f, width, height * 0.90f);
        canvas.rotate(-27f, width / 2f, height / 2f);
        float stepX = width * 0.60f;
        float stepY = height * 0.16f;
        for (float y = height * 0.19f; y < height * 1.05f; y += stepY) {
            boolean rowOffset = ((int) (y / stepY)) % 2 != 0;
            for (float x = -width * 0.25f + (rowOffset ? stepX / 2f : 0f);
                 x < width * 1.25f; x += stepX) {
                canvas.drawText(watermarkLine1, x, y, watermarkPaint);
                canvas.drawText(watermarkLine2, x, y + lineGap, watermarkPaint);
            }
        }
        canvas.restore();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (image != null && !image.isRecycled()) {
            image.recycle();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_UP) {
            return true;
        }
        float x = event.getX();
        float y = event.getY();
        float nx = x / Math.max(1f, getWidth());
        float ny = y / Math.max(1f, getHeight());
        if (type == TYPE_ENTERPRISE) {
            if (x < getWidth() * 0.14f && y < getHeight() * 0.12f) {
                if (listener != null) {
                    listener.onBack();
                }
            }
            return true;
        }
        // The screenshot already contains the five-item bottom navigation.
        if (ny > 0.92f) {
            int tab = (int) (x / (getWidth() / 5f));
            if (tab < 0) {
                tab = 0;
            }
            if (tab > 4) {
                tab = 4;
            }
            if (listener != null) {
                listener.onShellTab(tab);
            }
            return true;
        }
        if (type == TYPE_CONTACTS) {
            if (ny > 0.085f && ny < 0.155f) {
                notifyItem(0);
            } else if (ny > 0.20f && ny < 0.27f) {
                notifyItem(1);
            } else if (ny > 0.275f && ny < 0.345f) {
                notifyItem(2);
            } else if (ny < 0.10f && nx > 0.74f) {
                notifyItem(nx > 0.84f ? 4 : 3);
            }
            return true;
        }
        if (type == TYPE_DOCUMENT) {
            if (ny > 0.11f && ny < 0.19f) {
                notifyItem(0);
            } else if (ny > 0.195f && ny < 0.275f) {
                notifyItem(1);
            } else if (ny < 0.10f && nx > 0.74f) {
                notifyItem(nx > 0.84f ? 3 : 2);
            }
            return true;
        }
        return true;
    }

    private void notifyItem(int index) {
        if (listener != null) {
            listener.onPageItem(type, index);
        }
    }
}
