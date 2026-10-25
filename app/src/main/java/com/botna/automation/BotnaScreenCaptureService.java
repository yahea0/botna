package com.botna.automation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.WindowManager;
import android.content.pm.ServiceInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public final class BotnaScreenCaptureService extends Service {
    public static final String ACTION_START_CAPTURE = "com.botna.automation.SCREEN_CAPTURE_START";
    public static final String ACTION_START_STILL_CAPTURE = "com.botna.automation.SCREEN_CAPTURE_START_STILLS";
    public static final String ACTION_CAPTURE_STILL = "com.botna.automation.SCREEN_CAPTURE_STILL_REQUEST";
    public static final String ACTION_CAPTURE_STILL_READY = "com.botna.automation.SCREEN_CAPTURE_STILL_READY";
    public static final String ACTION_REQUEST_CLIP = "com.botna.automation.SCREEN_CAPTURE_REQUEST_CLIP";
    public static final String ACTION_STOP_CAPTURE = "com.botna.automation.SCREEN_CAPTURE_STOP";
    public static final String ACTION_CAPTURE_READY = "com.botna.automation.SCREEN_CAPTURE_READY";
    public static final String ACTION_CAPTURE_CLIP_READY = "com.botna.automation.SCREEN_CAPTURE_CLIP_READY";
    public static final String ACTION_CAPTURE_FAILED = "com.botna.automation.SCREEN_CAPTURE_FAILED";
    public static final String EXTRA_TASK_ID = "screen_capture_task_id";
    public static final String EXTRA_REQUEST_ID = "screen_capture_request_id";
    public static final String EXTRA_OUTPUT_PATH = "screen_capture_output_path";
    public static final String EXTRA_RESULT_CODE = "screen_capture_result_code";
    public static final String EXTRA_RESULT_DATA = "screen_capture_result_data";
    public static final String EXTRA_SUCCESS = "screen_capture_success";
    public static final String EXTRA_ERROR = "screen_capture_error";
    public static final String EXTRA_CAPTURE_MODE = "screen_capture_mode";
    public static final String CAPTURE_MODE_VIDEO = "video";
    public static final String CAPTURE_MODE_STILLS = "stills";

    private static final String MIME = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final String CHANNEL_ID = "botna_screen_capture";
    private static final int NOTIFICATION_ID = 23614;
    private static final long CLIP_DURATION_US = 2_000_000L;
    private static final long RING_BUFFER_US = 5_000_000L;
    private final Object lock = new Object();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<EncodedSample> recentSamples = new ArrayDeque<>();
    private volatile boolean recording;
    private volatile boolean stillCaptureMode;
    private ImageReader stillImageReader;
    private String pendingStillTaskId = "";
    private String pendingStillRequestId = "";
    private String pendingStillOutputPath = "";
    private Runnable pendingStillTimeout;
    private volatile boolean requestSyncFrame;
    private volatile boolean readyBroadcastSent;
    private volatile boolean releasing;
    private volatile String captureTaskId = "";
    private volatile Thread recordingThread;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private MediaCodec encoder;
    private Surface inputSurface;
    private MediaFormat outputFormat;
    private ActiveClip activeClip;
    private int videoWidth;
    private int videoHeight;
    private int densityDpi;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_NOT_STICKY;
        String action = intent.getAction();
        if (ACTION_START_CAPTURE.equals(action) || ACTION_START_STILL_CAPTURE.equals(action)) {
            boolean requestedStillMode = ACTION_START_STILL_CAPTURE.equals(action);
            captureTaskId = safe(intent.getStringExtra(EXTRA_TASK_ID));
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
            Intent resultData = parcelableIntent(intent, EXTRA_RESULT_DATA);
            if (recording) {
                if (stillCaptureMode == requestedStillMode) {
                    if (readyBroadcastSent) sendReady(captureTaskId);
                    else handler.postDelayed(() -> {
                        if (recording && stillCaptureMode == requestedStillMode && readyBroadcastSent) sendReady(captureTaskId);
                    }, 400L);
                } else {
                    notifyFailure(captureTaskId, "", "جلسة التقاط الشاشة الحالية تعمل بوضع مختلف.");
                }
                return START_NOT_STICKY;
            }
            try {
                stillCaptureMode = requestedStillMode;
                startForegroundNotification();
                releasing = false;
                recording = true;
                readyBroadcastSent = false;
                String threadName = requestedStillMode ? "BotnaStillCapture" : "BotnaScreenCapture";
                Thread thread = new Thread(() -> {
                    if (requestedStillMode) initializeAndCaptureStills(resultCode, resultData);
                    else initializeAndRecord(resultCode, resultData);
                }, threadName);
                recordingThread = thread;
                thread.start();
            } catch (Exception e) {
                recording = false;
                notifyFailure(captureTaskId, "", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                stopSelf();
            }
            return START_NOT_STICKY;
        }
        if (ACTION_CAPTURE_STILL.equals(action)) {
            requestStill(intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_REQUEST_ID), intent.getStringExtra(EXTRA_OUTPUT_PATH), 0);
            return START_NOT_STICKY;
        }
        if (ACTION_REQUEST_CLIP.equals(action)) {
            requestClip(intent.getStringExtra(EXTRA_TASK_ID), intent.getStringExtra(EXTRA_REQUEST_ID), intent.getStringExtra(EXTRA_OUTPUT_PATH));
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_CAPTURE.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    private void startForegroundNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Botna screen capture", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("التقاط الشاشة لتحليل خطوات المهمة الآلية");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle(stillCaptureMode ? "Botna يلتقط صور تحليل الشاشة" : "Botna يسجل الشاشة")
                .setContentText(stillCaptureMode ? "تجهيز صور قبل الإجراء وأثناءه وبعده دون حفظ فيديو" : "تسجيل مستمر لتجهيز مقطع تحليل مدته ثانيتان")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(NOTIFICATION_ID, notification);
    }

    private void initializeAndRecord(int resultCode, Intent resultData) {
        try {
            Thread.sleep(650L);
            if (!recording) return;
            if (resultData == null || resultCode != android.app.Activity.RESULT_OK) throw new IllegalStateException("لم يتم منح إذن تسجيل الشاشة.");
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException("تعذر بدء جلسة تسجيل الشاشة.");
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            densityDpi = metrics.densityDpi;
            videoWidth = Math.max(2, metrics.widthPixels & ~1);
            videoHeight = Math.max(2, metrics.heightPixels & ~1);
            MediaFormat format = MediaFormat.createVideoFormat(MIME, videoWidth, videoHeight);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(700000, Math.min(4000000, videoWidth * videoHeight * 3)));
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            encoder = MediaCodec.createEncoderByType(MIME);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = encoder.createInputSurface();
            encoder.start();
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    if (releasing) return;
                    recording = false;
                    failActiveClip("انتهت صلاحية إذن تسجيل الشاشة.");
                    stopSelf();
                }
            }, handler);
            virtualDisplay = projection.createVirtualDisplay("BotnaLiveScreen", videoWidth, videoHeight, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, inputSurface, null, handler);
            if (virtualDisplay == null) throw new IllegalStateException("تعذر بدء عرض الشاشة إلى مرمّز الفيديو.");
            while (recording) {
                if (requestSyncFrame) {
                    requestSyncFrame = false;
                    try {
                        Bundle parameters = new Bundle();
                        parameters.putInt("request-sync", 0);
                        encoder.setParameters(parameters);
                    } catch (Exception ignored) { }
                }
                drainEncoder();
                checkClipTimeout();
            }
        } catch (Exception e) {
            recording = false;
            notifyFailure(captureTaskId, "", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            releaseCaptureResources();
            stopSelf();
        }
    }

    private void initializeAndCaptureStills(int resultCode, Intent resultData) {
        try {
            Thread.sleep(250L);
            if (!recording) return;
            if (resultData == null || resultCode != android.app.Activity.RESULT_OK) throw new IllegalStateException("لم يتم منح إذن التقاط الشاشة.");
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = manager.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException("تعذر بدء جلسة التقاط الشاشة.");
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            densityDpi = metrics.densityDpi;
            videoWidth = Math.max(2, metrics.widthPixels);
            videoHeight = Math.max(2, metrics.heightPixels);
            stillImageReader = ImageReader.newInstance(videoWidth, videoHeight, PixelFormat.RGBA_8888, 2);
            stillImageReader.setOnImageAvailableListener(this::updateLatestStillFrame, handler);
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() {
                    if (releasing) return;
                    recording = false;
                    stopSelf();
                }
            }, handler);
            virtualDisplay = projection.createVirtualDisplay("BotnaStillFrames", videoWidth, videoHeight, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, stillImageReader.getSurface(), null, handler);
            if (virtualDisplay == null) throw new IllegalStateException("تعذر بدء التقاط إطارات الشاشة.");
            readyBroadcastSent = true;
            sendReady(captureTaskId);
            while (recording) Thread.sleep(100L);
        } catch (Exception e) {
            recording = false;
            notifyFailure(captureTaskId, "", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        } finally {
            releaseCaptureResources();
            stopSelf();
        }
    }

    private void updateLatestStillFrame(ImageReader reader) {
        Image image = null;
        Bitmap frame = null;
        String taskId = "";
        String requestId = "";
        String outputPath = "";
        try {
            if (!recording || reader == null) return;
            image = reader.acquireLatestImage();
            if (image == null) return;
            synchronized (lock) {
                if (pendingStillRequestId.isEmpty()) return;
                taskId = pendingStillTaskId;
                requestId = pendingStillRequestId;
                outputPath = pendingStillOutputPath;
                clearPendingStillLocked();
            }
            Image.Plane[] planes = image.getPlanes();
            if (planes == null || planes.length == 0) throw new IllegalStateException("تعذر قراءة بيانات إطار الشاشة.");
            Image.Plane plane = planes[0];
            ByteBuffer sourceBuffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int width = image.getWidth();
            int height = image.getHeight();
            if (pixelStride < 4 || rowStride < pixelStride * width) throw new IllegalStateException("صيغة إطارات الشاشة غير مدعومة.");
            byte[] source = new byte[sourceBuffer.remaining()];
            sourceBuffer.get(source);
            byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
            for (int y = 0; y < height; y++) {
                int sourceRow = y * rowStride;
                int targetRow = y * width * 4;
                for (int x = 0; x < width; x++) {
                    int sourceOffset = sourceRow + x * pixelStride;
                    int targetOffset = targetRow + x * 4;
                    if (sourceOffset + 4 > source.length) throw new IllegalStateException("بيانات إطار الشاشة غير مكتملة.");
                    System.arraycopy(source, sourceOffset, pixels, targetOffset, 4);
                }
            }
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            frame.copyPixelsFromBuffer(ByteBuffer.wrap(pixels));
        } catch (Exception e) {
            if (!requestId.isEmpty()) notifyFailure(taskId, requestId, e.getMessage() == null ? "تعذر تحويل إطار الشاشة إلى صورة." : e.getMessage());
        } finally {
            if (image != null) try { image.close(); } catch (Exception ignored) { }
        }
        if (frame != null) writeStillFrameAsync(taskId, requestId, outputPath, frame);
    }

    private void requestStill(String taskId, String requestId, String outputPath, int attempt) {
        String requestedTask = safe(taskId);
        String requestedId = safe(requestId);
        if (!recording || !stillCaptureMode || stillImageReader == null) {
            notifyFailure(requestedTask, requestedId, "جلسة التقاط الصور الثابتة ليست جاهزة.");
            return;
        }
        File output;
        try {
            output = validateStillOutputPath(outputPath);
        } catch (Exception e) {
            notifyFailure(requestedTask, requestedId, e.getMessage() == null ? "مسار صورة الشاشة غير صالح." : e.getMessage());
            return;
        }
        synchronized (lock) {
            if (!pendingStillRequestId.isEmpty()) {
                notifyFailure(requestedTask, requestedId, "يوجد طلب آخر لالتقاط صورة الشاشة قيد التنفيذ.");
                return;
            }
            pendingStillTaskId = requestedTask;
            pendingStillRequestId = requestedId;
            pendingStillOutputPath = output.getAbsolutePath();
            pendingStillTimeout = () -> {
                String timedOutTask = "";
                String timedOutRequest = "";
                synchronized (lock) {
                    if (!requestedId.equals(pendingStillRequestId)) return;
                    timedOutTask = pendingStillTaskId;
                    timedOutRequest = pendingStillRequestId;
                    clearPendingStillLocked();
                }
                notifyFailure(timedOutTask, timedOutRequest, "انتهت مهلة انتظار إطار حديث من الشاشة.");
            };
            handler.postDelayed(pendingStillTimeout, 2500L);
        }
    }

    private void clearPendingStillLocked() {
        if (pendingStillTimeout != null) handler.removeCallbacks(pendingStillTimeout);
        pendingStillTimeout = null;
        pendingStillTaskId = "";
        pendingStillRequestId = "";
        pendingStillOutputPath = "";
    }

    private void writeStillFrameAsync(String taskId, String requestId, String outputPath, Bitmap frame) {
        File output;
        try {
            output = validateStillOutputPath(outputPath);
        } catch (Exception e) {
            if (frame != null && !frame.isRecycled()) frame.recycle();
            notifyFailure(taskId, requestId, e.getMessage() == null ? "مسار صورة الشاشة غير صالح." : e.getMessage());
            return;
        }
        Thread writer = new Thread(() -> {
            boolean success = false;
            String error = "";
            try {
                File parent = output.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد صور التحليل.");
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    if (!frame.compress(Bitmap.CompressFormat.JPEG, 92, stream)) throw new IllegalStateException("تعذر ترميز صورة الشاشة.");
                    stream.flush();
                    stream.getFD().sync();
                }
                success = output.isFile() && output.length() > 0L;
                if (!success) throw new IllegalStateException("ملف صورة الشاشة فارغ أو غير مكتمل.");
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (output.exists()) output.delete();
            } finally {
                if (!frame.isRecycled()) frame.recycle();
            }
            if (success) {
                Intent result = new Intent(ACTION_CAPTURE_STILL_READY).setPackage(getPackageName());
                result.putExtra(EXTRA_TASK_ID, taskId);
                result.putExtra(EXTRA_REQUEST_ID, requestId);
                result.putExtra(EXTRA_OUTPUT_PATH, output.getAbsolutePath());
                result.putExtra(EXTRA_SUCCESS, true);
                sendBroadcast(result);
            } else {
                notifyFailure(taskId, requestId, error.isEmpty() ? "تعذر حفظ صورة الشاشة." : error);
            }
        }, "BotnaWriteStillFrame");
        writer.start();
    }

    private File validateStillOutputPath(String outputPath) throws Exception {
        if (outputPath == null || outputPath.trim().isEmpty()) throw new IllegalArgumentException("لم يُحدد مسار صورة الشاشة.");
        File directory = new File(new File(getFilesDir(), "bot_studio_files"), "ai_share").getCanonicalFile();
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد صور التحليل.");
        File output = new File(outputPath).getCanonicalFile();
        if (output.getParentFile() == null || !output.getParentFile().equals(directory) || !output.getName().endsWith(".jpg")) {
            throw new IllegalArgumentException("مسار صورة الشاشة خارج مجلد المشاركة المسموح.");
        }
        return output;
    }

    private void drainEncoder() throws Exception {
        if (encoder == null) return;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outputIndex = encoder.dequeueOutputBuffer(info, 100000L);
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER || outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) return;
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            synchronized (lock) { outputFormat = encoder.getOutputFormat(); }
            if (!readyBroadcastSent) {
                readyBroadcastSent = true;
                sendReady(captureTaskId);
            }
            return;
        }
        if (outputIndex < 0) return;
        EncodedSample sample = null;
        try {
            ByteBuffer buffer = encoder.getOutputBuffer(outputIndex);
            if (buffer != null && info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                ByteBuffer copy = buffer.duplicate();
                copy.position(info.offset);
                copy.limit(info.offset + info.size);
                byte[] bytes = new byte[info.size];
                copy.get(bytes);
                sample = new EncodedSample(bytes, info.presentationTimeUs, info.flags & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        } finally {
            encoder.releaseOutputBuffer(outputIndex, false);
        }
        if (sample != null) acceptSample(sample);
    }

    private void acceptSample(EncodedSample sample) {
        ActiveClip readyClip = null;
        String clipTimeoutId = "";
        synchronized (lock) {
            recentSamples.addLast(sample);
            long cutoff = sample.presentationTimeUs - RING_BUFFER_US;
            while (!recentSamples.isEmpty() && recentSamples.peekFirst().presentationTimeUs < cutoff) recentSamples.removeFirst();
            ActiveClip clip = activeClip;
            if (clip == null) return;
            if (clip.startPtsUs < 0L) {
                if ((sample.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                    clip.startPtsUs = sample.presentationTimeUs;
                    clip.samples.add(sample);
                }
            } else {
                long relativePts = sample.presentationTimeUs - clip.startPtsUs;
                if (relativePts < CLIP_DURATION_US) {
                    clip.samples.add(sample);
                } else {
                    activeClip = null;
                    readyClip = clip;
                }
            }
        }
        if (readyClip != null) writeClipAsync(readyClip);
    }

    private void requestClip(String taskId, String requestId, String outputPath) {
        String requestedTask = safe(taskId);
        String requestedId = safe(requestId);
        File output;
        try {
            output = validateOutputPath(outputPath);
        } catch (Exception e) {
            notifyFailure(requestedTask, requestedId, e.getMessage() == null ? "مسار الفيديو غير صالح." : e.getMessage());
            return;
        }
        synchronized (lock) {
            if (!recording || stillCaptureMode || encoder == null || outputFormat == null || !readyBroadcastSent) {
                notifyFailure(requestedTask, requestedId, "خدمة تسجيل الشاشة ليست جاهزة بعد.");
                return;
            }
            if (activeClip != null) {
                notifyFailure(requestedTask, requestedId, "يوجد تسجيل فيديو آخر قيد التجهيز.");
                return;
            }
            activeClip = new ActiveClip(requestedTask, requestedId, output.getAbsolutePath());
            requestSyncFrame = true;
        }
    }

    private void writeClipAsync(ActiveClip clip) {
        MediaFormat format;
        synchronized (lock) { format = outputFormat; }
        Thread writer = new Thread(() -> {
            File output = new File(clip.outputPath);
            boolean success = false;
            String error = "";
            MediaMuxer muxer = null;
            boolean started = false;
            try {
                File parent = output.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد الفيديو.");
                if (output.exists() && !output.delete()) throw new IllegalStateException("تعذر استبدال ملف الفيديو السابق.");
                if (format == null || clip.samples.size() < 2) throw new IllegalStateException("لم يجمع تسجيل الشاشة إطارات فيديو كافية.");
                muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int track = muxer.addTrack(format);
                muxer.start();
                started = true;
                for (EncodedSample sample : clip.samples) {
                    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                    info.set(0, sample.bytes.length, Math.max(0L, sample.presentationTimeUs - clip.startPtsUs), sample.flags);
                    muxer.writeSampleData(track, ByteBuffer.wrap(sample.bytes), info);
                }
                muxer.stop();
                started = false;
                success = output.isFile() && output.length() > 1024L;
                if (!success) throw new IllegalStateException("ملف الفيديو الناتج فارغ أو غير مكتمل.");
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (output.exists()) output.delete();
            } finally {
                if (muxer != null) {
                    if (started) try { muxer.stop(); } catch (Exception ignored) { }
                    try { muxer.release(); } catch (Exception ignored) { }
                }
            }
            if (success) {
                Intent result = new Intent(ACTION_CAPTURE_CLIP_READY).setPackage(getPackageName());
                result.putExtra(EXTRA_TASK_ID, clip.taskId);
                result.putExtra(EXTRA_REQUEST_ID, clip.requestId);
                result.putExtra(EXTRA_OUTPUT_PATH, output.getAbsolutePath());
                result.putExtra(EXTRA_SUCCESS, true);
                sendBroadcast(result);
            } else {
                notifyFailure(clip.taskId, clip.requestId, error.isEmpty() ? "تعذر تجهيز مقطع MP4." : error);
            }
        }, "BotnaWriteScreenClip");
        writer.start();
    }

    private void checkClipTimeout() {
        ActiveClip timedOut = null;
        synchronized (lock) {
            if (activeClip != null && System.currentTimeMillis() - activeClip.requestedAtMs > 8000L) {
                timedOut = activeClip;
                activeClip = null;
            }
        }
        if (timedOut != null) {
            String error = timedOut.startPtsUs < 0L
                    ? "لم يصل إطار مفتاح صالح لبدء فيديو الشاشة خلال المهلة المحددة."
                    : "لم يكتمل تسجيل فيديو الشاشة خلال المهلة المحددة.";
            notifyFailure(timedOut.taskId, timedOut.requestId, error);
        }
    }

    private File validateOutputPath(String outputPath) throws Exception {
        if (outputPath == null || outputPath.trim().isEmpty()) throw new IllegalArgumentException("لم يُحدد مسار ملف الفيديو.");
        File directory = new File(new File(getFilesDir(), "bot_studio_files"), "ai_share").getCanonicalFile();
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد فيديو المهمة.");
        File output = new File(outputPath).getCanonicalFile();
        if (output.getParentFile() == null || !output.getParentFile().equals(directory) || !output.getName().endsWith(".mp4")) {
            throw new IllegalArgumentException("مسار ملف الفيديو خارج مجلد المشاركة المسموح.");
        }
        return output;
    }

    private void sendReady(String taskId) {
        Intent result = new Intent(ACTION_CAPTURE_READY).setPackage(getPackageName());
        result.putExtra(EXTRA_TASK_ID, safe(taskId));
        result.putExtra(EXTRA_SUCCESS, true);
        sendBroadcast(result);
    }

    private void notifyFailure(String taskId, String requestId, String error) {
        Intent result = new Intent(ACTION_CAPTURE_FAILED).setPackage(getPackageName());
        result.putExtra(EXTRA_TASK_ID, safe(taskId));
        result.putExtra(EXTRA_REQUEST_ID, safe(requestId));
        result.putExtra(EXTRA_SUCCESS, false);
        result.putExtra(EXTRA_ERROR, safe(error));
        sendBroadcast(result);
    }

    private void failActiveClip(String error) {
        ActiveClip clip;
        synchronized (lock) {
            clip = activeClip;
            activeClip = null;
        }
        if (clip != null) notifyFailure(clip.taskId, clip.requestId, error);
    }

    private synchronized void releaseCaptureResources() {
        if (releasing) return;
        releasing = true;
        recording = false;
        if (virtualDisplay != null) {
            try { virtualDisplay.release(); } catch (Exception ignored) { }
            virtualDisplay = null;
        }
        if (stillImageReader != null) {
            try { stillImageReader.setOnImageAvailableListener(null, null); } catch (Exception ignored) { }
            try { stillImageReader.close(); } catch (Exception ignored) { }
            stillImageReader = null;
        }
        synchronized (lock) {
            String taskId = pendingStillTaskId;
            String requestId = pendingStillRequestId;
            clearPendingStillLocked();
            if (!requestId.isEmpty()) notifyFailure(taskId, requestId, "توقفت جلسة التقاط الشاشة قبل اكتمال الصورة.");
        }
        if (projection != null) {
            try { projection.stop(); } catch (Exception ignored) { }
            projection = null;
        }
        if (encoder != null) {
            try { encoder.stop(); } catch (Exception ignored) { }
            try { encoder.release(); } catch (Exception ignored) { }
            encoder = null;
        }
        if (inputSurface != null) {
            try { inputSurface.release(); } catch (Exception ignored) { }
            inputSurface = null;
        }
        synchronized (lock) {
            activeClip = null;
            recentSamples.clear();
            outputFormat = null;
        }
    }

    @Override public void onDestroy() {
        recording = false;
        failActiveClip("توقف تسجيل الشاشة قبل اكتمال الفيديو.");
        Thread thread = recordingThread;
        if (thread != null && thread != Thread.currentThread()) {
            try { thread.join(900L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
        releaseCaptureResources();
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private static Intent parcelableIntent(Intent source, String key) {
        if (source == null) return null;
        if (Build.VERSION.SDK_INT >= 33) return source.getParcelableExtra(key, Intent.class);
        return (Intent) source.getParcelableExtra(key);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static final class EncodedSample {
        final byte[] bytes;
        final long presentationTimeUs;
        final int flags;
        EncodedSample(byte[] bytes, long presentationTimeUs, int flags) {
            this.bytes = bytes;
            this.presentationTimeUs = presentationTimeUs;
            this.flags = flags;
        }
    }

    private static final class ActiveClip {
        final String taskId;
        final String requestId;
        final String outputPath;
        final long requestedAtMs = System.currentTimeMillis();
        final List<EncodedSample> samples = new ArrayList<>();
        long startPtsUs = -1L;
        ActiveClip(String taskId, String requestId, String outputPath) {
            this.taskId = safe(taskId);
            this.requestId = safe(requestId);
            this.outputPath = outputPath;
        }
    }
}
