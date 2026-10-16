package com.botna.automation;

import android.graphics.Bitmap;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

/**
 * OpenCV native template matching. Image processing runs in optimized native code;
 * callers should invoke find() from a background thread.
 */
public final class ImageMatcher {
    private static volatile boolean initialized;
    private static volatile boolean initializationAttempted;

    public static final class Result {
        public final int score;
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        public final String templateName;

        Result(int score, int x, int y, int width, int height, String name) {
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

    private ImageMatcher() { }

    public static synchronized boolean ensureLoaded() {
        if (initializationAttempted) return initialized;
        initializationAttempted = true;
        try {
            initialized = OpenCVLoader.initLocal();
        } catch (Throwable t) {
            initialized = false;
        }
        return initialized;
    }

    public static Result find(Bitmap screen, List<NamedBitmap> templates, int threshold) {
        if (screen == null || screen.isRecycled() || templates == null || templates.isEmpty()) {
            return null;
        }
        if (!ensureLoaded()) {
            throw new IllegalStateException("OpenCV لم يتمكن من تحميل المكتبة الأصلية");
        }

        Mat screenRgba = new Mat();
        Mat screenGrayFull = new Mat();
        Mat screenGray = new Mat();
        Result best = null;

        try {
            Utils.bitmapToMat(screen, screenRgba);
            if (screenRgba.channels() == 4) {
                Imgproc.cvtColor(screenRgba, screenGrayFull, Imgproc.COLOR_RGBA2GRAY);
            } else if (screenRgba.channels() == 3) {
                Imgproc.cvtColor(screenRgba, screenGrayFull, Imgproc.COLOR_RGB2GRAY);
            } else {
                screenRgba.copyTo(screenGrayFull);
            }

            final int maxScreenWidth = 720;
            double resizeFactor = screenGrayFull.cols() > maxScreenWidth
                    ? maxScreenWidth / (double) screenGrayFull.cols()
                    : 1.0;
            int scaledWidth = Math.max(1, (int) Math.round(screenGrayFull.cols() * resizeFactor));
            int scaledHeight = Math.max(1, (int) Math.round(screenGrayFull.rows() * resizeFactor));
            if (scaledWidth != screenGrayFull.cols() || scaledHeight != screenGrayFull.rows()) {
                Imgproc.resize(screenGrayFull, screenGray, new Size(scaledWidth, scaledHeight), 0, 0, Imgproc.INTER_AREA);
            } else {
                screenGrayFull.copyTo(screenGray);
            }

            double mapX = screen.getWidth() / (double) screenGray.cols();
            double mapY = screen.getHeight() / (double) screenGray.rows();
            double[] scaleFactors = new double[]{0.82, 0.91, 1.0, 1.10, 1.20};

            for (NamedBitmap named : templates) {
                if (named == null || named.bitmap == null || named.bitmap.isRecycled()) continue;

                Mat templateRgba = new Mat();
                Mat templateGrayFull = new Mat();
                try {
                    Utils.bitmapToMat(named.bitmap, templateRgba);
                    if (templateRgba.channels() == 4) {
                        Imgproc.cvtColor(templateRgba, templateGrayFull, Imgproc.COLOR_RGBA2GRAY);
                    } else if (templateRgba.channels() == 3) {
                        Imgproc.cvtColor(templateRgba, templateGrayFull, Imgproc.COLOR_RGB2GRAY);
                    } else {
                        templateRgba.copyTo(templateGrayFull);
                    }

                    int baseW = Math.max(1, (int) Math.round(templateGrayFull.cols() / mapX));
                    int baseH = Math.max(1, (int) Math.round(templateGrayFull.rows() / mapY));

                    if (baseW < 8 || baseH < 8) continue;

                    MatOfDouble mean = new MatOfDouble();
                    MatOfDouble std = new MatOfDouble();
                    try {
                        Core.meanStdDev(templateGrayFull, mean, std);
                        double[] sd = std.toArray();
                        if (sd.length > 0 && sd[0] < 1.5) {
                            // Nearly uniform templates generate unreliable high scores.
                            continue;
                        }
                    } finally {
                        mean.release();
                        std.release();
                    }

                    for (double scale : scaleFactors) {
                        int w = Math.max(8, (int) Math.round(baseW * scale));
                        int h = Math.max(8, (int) Math.round(baseH * scale));
                        if (w >= screenGray.cols() || h >= screenGray.rows()) continue;

                        Mat templ = new Mat();
                        Mat result = new Mat();
                        try {
                            Imgproc.resize(
                                    templateGrayFull,
                                    templ,
                                    new Size(w, h),
                                    0,
                                    0,
                                    scale < 1.0 ? Imgproc.INTER_AREA : Imgproc.INTER_LINEAR
                            );

                            Imgproc.matchTemplate(
                                    screenGray,
                                    templ,
                                    result,
                                    Imgproc.TM_CCOEFF_NORMED
                            );

                            Core.MinMaxLocResult mm = Core.minMaxLoc(result);
                            int score = (int) Math.round(Math.max(0.0, Math.min(1.0, mm.maxVal)) * 100.0);

                            if (best == null || score > best.score) {
                                int x = (int) Math.round(mm.maxLoc.x * mapX);
                                int y = (int) Math.round(mm.maxLoc.y * mapY);
                                int originalW = (int) Math.round(w * mapX);
                                int originalH = (int) Math.round(h * mapY);

                                best = new Result(
                                        score,
                                        Math.max(0, x),
                                        Math.max(0, y),
                                        Math.max(1, originalW),
                                        Math.max(1, originalH),
                                        named.name
                                );
                            }
                        } finally {
                            templ.release();
                            result.release();
                        }
                    }
                } finally {
                    templateRgba.release();
                    templateGrayFull.release();
                }
            }

            return best;
        } finally {
            screenRgba.release();
            screenGrayFull.release();
            screenGray.release();
        }
    }
}
