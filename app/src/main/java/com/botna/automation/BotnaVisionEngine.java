package com.botna.automation;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.accessibility.AccessibilityEvent;

public final class BotnaVisionEngine {
    private static long lastCaptureAt;
    private static int[] previousSample;
    private static volatile String lastStatus = "استخدام شجرة إمكانية الوصول ومطابقة OpenCV كمسار احتياطي.";

    private BotnaVisionEngine() { }

    public static synchronized boolean shouldCaptureOnEvent(Context context, int eventType, long now) {
        if (context == null || !BotStudioStore.visionStreamEnabled(context) || !BotStudioStore.smartSnapshotsEnabled(context)) return false;
        boolean relevant = eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
                || eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                || eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
                || eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
        if (!relevant || now - lastCaptureAt < 950) return false;
        lastCaptureAt = now;
        return true;
    }

    public static synchronized boolean hasSignificantVisualChange(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() < 2 || bitmap.getHeight() < 2) return false;
        final int cols = 12, rows = 20;
        int[] sample = new int[cols * rows];
        for (int y = 0; y < rows; y++) {
            int py = Math.min(bitmap.getHeight() - 1, (int) ((y + 0.5f) * bitmap.getHeight() / rows));
            for (int x = 0; x < cols; x++) {
                int px = Math.min(bitmap.getWidth() - 1, (int) ((x + 0.5f) * bitmap.getWidth() / cols));
                int color = bitmap.getPixel(px, py);
                int gray = (((color >> 16) & 255) * 30 + ((color >> 8) & 255) * 59 + (color & 255) * 11) / 100;
                sample[y * cols + x] = gray;
            }
        }
        if (previousSample == null) { previousSample = sample; return true; }
        int changed = 0, totalDelta = 0;
        for (int i = 0; i < sample.length; i++) {
            int delta = Math.abs(sample[i] - previousSample[i]);
            totalDelta += delta;
            if (delta >= 28) changed++;
        }
        previousSample = sample;
        boolean changedEnough = changed >= sample.length * 0.11 || totalDelta >= sample.length * 18;
        if (changedEnough) lastStatus = "رُصد تغير بصري في الشاشة؛ تم حفظ إشارة التغيير محليًا.";
        return changedEnough;
    }

    public static String status(Context context) {
        boolean yolo = context != null && BotStudioStore.yoloEnabled(context);
        boolean ocr = context != null && BotStudioStore.ocrEnabled(context);
        if (yolo || ocr) return "نموذج YOLO/OCR غير مضمّن في هذه النسخة؛ رجوع تلقائي إلى OpenCV وشجرة إمكانية الوصول والمزوّد الحالي.";
        return lastStatus;
    }

    public static synchronized void resetComparison() { previousSample = null; }
}
