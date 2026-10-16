package com.botna.automation;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.List;

/**
 * محرك مطابقة بصري تقريبي دون مكتبات خارجية.
 *
 * النتيجة تقدير للتشابه وليست ضمانًا لهوية الهدف.
 */
public final class ImageMatcher {

    private static final int FEATURE = 10;

    public static final class Result {
        public final int score;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final String templateName;

        Result(
                int score,
                int x,
                int y,
                int width,
                int height,
                String name
        ) {
            this.score = score;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.templateName = name;
        }
    }

    public static final class NamedBitmap {
        public final String name;
        public final Bitmap bitmap;

        public NamedBitmap(String name, Bitmap bitmap) {
            this.name = name;
            this.bitmap = bitmap;
        }
    }

    private ImageMatcher() {
    }

    public static Result find(
            Bitmap screen,
            List<NamedBitmap> templates,
            int threshold
    ) {
        if (screen == null ||
                templates == null ||
                templates.isEmpty()) {
            return null;
        }

        Bitmap scaledScreen = scaleToWidth(screen, 360);
        Result best = null;

        try {
            for (NamedBitmap named : templates) {
                if (named == null ||
                        named.bitmap == null ||
                        named.bitmap.isRecycled()) {
                    continue;
                }

                Bitmap source = scaleToWidth(named.bitmap, 100);

                try {
                    float[] feature = feature(source);
                    int baseW = source.getWidth();
                    int baseH = source.getHeight();

                    if (baseW < 8 || baseH < 8) {
                        continue;
                    }

                    float[] scales = new float[]{
                            0.30f,
                            0.40f,
                            0.50f,
                            0.65f,
                            0.80f,
                            1.00f,
                            1.20f,
                            1.40f
                    };

                    for (float scale : scales) {
                        int w = Math.max(
                                12,
                                Math.round(baseW * scale)
                        );

                        int h = Math.max(
                                12,
                                Math.round(baseH * scale)
                        );

                        if (w >= scaledScreen.getWidth() ||
                                h >= scaledScreen.getHeight()) {
                            continue;
                        }

                        int maxX = scaledScreen.getWidth() - w;
                        int maxY = scaledScreen.getHeight() - h;

                        int stride = Math.max(
                                3,
                                Math.min(w, h) / FEATURE / 2
                        );

                        for (int y = 0; y <= maxY; y += stride) {
                            for (int x = 0; x <= maxX; x += stride) {
                                float diff = compare(
                                        scaledScreen,
                                        x,
                                        y,
                                        w,
                                        h,
                                        feature
                                );

                                int score = Math.max(
                                        0,
                                        Math.min(
                                                100,
                                                Math.round(
                                                        100f *
                                                        (1f - diff / 1.5f)
                                                )
                                        )
                                );

                                if (best == null ||
                                        score > best.score) {

                                    float factorX =
                                            screen.getWidth() /
                                            (float) scaledScreen.getWidth();

                                    float factorY =
                                            screen.getHeight() /
                                            (float) scaledScreen.getHeight();

                                    best = new Result(
                                            score,
                                            Math.round(x * factorX),
                                            Math.round(y * factorY),
                                            Math.round(w * factorX),
                                            Math.round(h * factorY),
                                            named.name
                                    );
                                }
                            }
                        }
                    }

                } finally {
                    if (source != named.bitmap &&
                            !source.isRecycled()) {
                        source.recycle();
                    }
                }
            }

        } finally {
            if (scaledScreen != screen &&
                    !scaledScreen.isRecycled()) {
                scaledScreen.recycle();
            }
        }

        if (best != null && best.score < threshold) {
            return best;
        }

        return best;
    }

    private static Bitmap scaleToWidth(
            Bitmap input,
            int maxWidth
    ) {
        if (input.getWidth() <= maxWidth) {
            return input;
        }

        int h = Math.max(
                1,
                Math.round(
                        input.getHeight() *
                        (maxWidth / (float) input.getWidth())
                )
        );

        return Bitmap.createScaledBitmap(
                input,
                maxWidth,
                h,
                true
        );
    }

    private static float[] feature(Bitmap bitmap) {
        float[] values = new float[FEATURE * FEATURE];
        int k = 0;

        for (int gy = 0; gy < FEATURE; gy++) {
            for (int gx = 0; gx < FEATURE; gx++) {
                int x = Math.min(
                        bitmap.getWidth() - 1,
                        (int) ((gx + 0.5f) *
                                bitmap.getWidth() / FEATURE)
                );

                int y = Math.min(
                        bitmap.getHeight() - 1,
                        (int) ((gy + 0.5f) *
                                bitmap.getHeight() / FEATURE)
                );

                values[k++] = luminance(
                        bitmap.getPixel(x, y)
                );
            }
        }

        normalize(values);
        return values;
    }

    private static float compare(
            Bitmap screen,
            int left,
            int top,
            int width,
            int height,
            float[] reference
    ) {
        float[] patch = new float[FEATURE * FEATURE];
        int k = 0;

        for (int gy = 0; gy < FEATURE; gy++) {
            for (int gx = 0; gx < FEATURE; gx++) {
                int x = left + Math.min(
                        width - 1,
                        (int) ((gx + 0.5f) *
                                width / FEATURE)
                );

                int y = top + Math.min(
                        height - 1,
                        (int) ((gy + 0.5f) *
                                height / FEATURE)
                );

                patch[k++] = luminance(
                        screen.getPixel(x, y)
                );
            }
        }

        normalize(patch);

        float total = 0f;

        for (int i = 0; i < patch.length; i++) {
            total += Math.abs(
                    patch[i] - reference[i]
            );
        }

        return total / patch.length;
    }

    private static float luminance(int color) {
        return (
                0.299f * Color.red(color) +
                0.587f * Color.green(color) +
                0.114f * Color.blue(color)
        ) / 255f;
    }

    private static void normalize(float[] values) {
        float mean = 0f;

        for (float v : values) {
            mean += v;
        }

        mean /= values.length;

        float variance = 0f;

        for (float v : values) {
            float d = v - mean;
            variance += d * d;
        }

        float std = (float) Math.sqrt(
                variance / values.length
        );

        if (std < 0.025f) {
            std = 0.025f;
        }

        for (int i = 0; i < values.length; i++) {
            values[i] = (values[i] - mean) / std;
        }
    }
}
