package com.botna.automation;

import android.accessibilityservice.AccessibilityService;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.accessibilityservice.GestureDescription;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.view.accessibility.AccessibilityEvent;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.inputmethod.InputMethodManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BotnaAccessibilityService extends AccessibilityService {
    public static final String ACTION_START_SMART_TASK = "com.botna.automation.START_SMART_TASK";
    public static final String ACTION_RESUME_SMART_TASK = "com.botna.automation.RESUME_SMART_TASK";
    public static final String ACTION_CORRECT_SMART_TASK = "com.botna.automation.CORRECT_SMART_TASK";
    public static final String ACTION_OPEN_AGENT_SCRIPT = "com.botna.automation.OPEN_AGENT_SCRIPT";
    public static final String ACTION_RUN_AGENT_SCRIPT = "com.botna.automation.RUN_AGENT_SCRIPT";
    public static final String ACTION_LANGUAGE_CHANGED = "com.botna.automation.LANGUAGE_CHANGED";
    public static final String PREF_PENDING_SMART_TASK = "pending_smart_task_v1";
    public static final String PREF_PENDING_SMART_TASK_ID = "pending_smart_task_id_v1";
    public static final String PREF_PENDING_SMART_RESUME = "pending_smart_task_resume_v1";
    public static final String PREF_AGENT_SERVICE_CONNECTED = "agent_service_connected_v1";
    public static final String PREF_AGENT_SERVICE_HEARTBEAT = "agent_service_heartbeat_v1";
    public static final String PREF_AGENT_SCREEN_AVAILABLE = "agent_screen_available_v1";
    public static final String PREF_AGENT_SCREEN_AT = "agent_screen_captured_at_v1";
    public static final String PREF_AGENT_TASK_ACTIVE = "agent_task_active_v1";

    public static boolean isServiceActuallyConnected(Context context) {
        if (context == null) return false;
        android.content.SharedPreferences p = context.getSharedPreferences(BotnaStore.PREFS, Context.MODE_PRIVATE);
        long heartbeat = p.getLong(PREF_AGENT_SERVICE_HEARTBEAT, 0L);
        return p.getBoolean(PREF_AGENT_SERVICE_CONNECTED, false) && heartbeat > 0L
                && System.currentTimeMillis() - heartbeat >= 0L && System.currentTimeMillis() - heartbeat <= 12000L;
    }

    public static String agentRuntimeStatus(Context context) {
        if (context == null || !isServiceActuallyConnected(context))
            return "حالة وكيل الهاتف: خدمة إمكانية الوصول غير متصلة الآن؛ لا توجد لقطة شاشة حية متاحة ولا يُنفذ أي تحكم مؤكد.";
        android.content.SharedPreferences p = context.getSharedPreferences(BotnaStore.PREFS, Context.MODE_PRIVATE);
        boolean active = p.getBoolean(PREF_AGENT_TASK_ACTIVE, false);
        boolean available = p.getBoolean(PREF_AGENT_SCREEN_AVAILABLE, false);
        long at = p.getLong(PREF_AGENT_SCREEN_AT, 0L);
        long age = at > 0 ? System.currentTimeMillis() - at : Long.MAX_VALUE;
        if (active && available && age >= 0 && age <= 15000L)
            return "حالة وكيل الهاتف: الخدمة متصلة والمهمة فعالة؛ آخر لقطة شاشة ناجحة منذ " + Math.max(0, age / 1000) + " ثانية. هذه اللقطة تخص آخر دورة تنفيذ وليست بثًا مباشرًا مستمرًا.";
        if (active) return "حالة وكيل الهاتف: يوجد علم لمهمة نشطة لكن لا توجد لقطة شاشة حديثة مؤكدة؛ لا يمكنني تأكيد رؤية الشاشة حتى تنجح اللقطة التالية.";
        return "حالة وكيل الهاتف: خدمة إمكانية الوصول متصلة، لكن لا توجد مهمة تحكم نشطة ولا لقطة شاشة حية مرفقة بالمحادثة.";
    }

    public static boolean isLocalSpeedTestRequest(String source) {
        if (source == null) return false;
        String value = BotnaArabicDialect.normalize(source).replace('ة', 'ه');
        return value.contains("سرع") && (value.contains("نت") || value.contains("انترنت")
                || value.contains("internet") || value.contains("wifi") || value.contains("واي فاي"));
    }
    private static final int BG = Color.rgb(5, 7, 12);
    private static final int GLASS = Color.argb(242, 13, 17, 26);
    private static final int BUTTON = Color.argb(225, 19, 31, 47);
    private static final int TEXT = Color.rgb(239, 248, 255);
    private static final int MUTED = Color.rgb(151, 174, 194);
    private static final int CYAN = Color.rgb(35, 226, 244);
    private static final int CONTROL_GAP_MS = 250;
    private static final String TYPE_TEXT = "TEXT_CLICK";
    private static final String TYPE_BACK = "BACK";
    private static final String TYPE_HOME = "HOME";
    private static final String TYPE_WAIT = "WAIT";
    private static final String TYPE_IMAGE = "IMAGE_MATCH";
    private static final String TYPE_IF_SUCCESS = "IF_SUCCESS";
    private static final String TYPE_IF_FAIL = "IF_FAIL";
    private static final String TYPE_ELSE = "ELSE";
    private static final String TYPE_END_IF = "END_IF";
    private static final String TYPE_TAP = "TAP_COORD";
    private static final String TYPE_LONG_PRESS = "LONG_PRESS_COORD";
    private static final String TYPE_SWIPE = "SWIPE";
    private static final String TYPE_SCROLL = "SCROLL";
    private static final String TYPE_TYPE_TEXT = "TYPE_TEXT";
    private static final String TYPE_REPEAT_START = "REPEAT_START";
    private static final String TYPE_REPEAT_END = "REPEAT_END";
    private static final String TYPE_CUSTOM = "CUSTOM";
    private static final String TYPE_LAUNCH_APP = "LAUNCH_APP";
    private static final String TYPE_OPEN_URL = "OPEN_URL";
    private static final String TYPE_CHECK_INTERNET = "CHECK_INTERNET";
    private static final String TYPE_WAIT_INTERNET = "WAIT_INTERNET";
    private static final String TYPE_NOTIFY = "NOTIFY";
    private static final String TYPE_PRESS_ENTER = "PRESS_ENTER";
    private static final String TYPE_WAIT_TEXT = "WAIT_TEXT";
    private static final String TYPE_WAIT_INPUT = "WAIT_INPUT";
    private static final String TYPE_GLOBAL_ACTION = "GLOBAL_ACTION";
    private static final String TYPE_CLEAR_TEXT = "CLEAR_TEXT";
    private static final String TYPE_CLOSE_RECENTS = "CLOSE_RECENTS";
    private static final String TYPE_SPEED_TEST = "SPEED_TEST";
    private static final String TYPE_READ_SCREEN_TEXT = "READ_SCREEN_TEXT";

    private static final int CROP_TARGET = 0;
    private static final int CROP_SEARCH_REGION = 1;
    private static final int CROP_LIBRARY_IMAGE = 2;
    private static final int CROP_LIBRARY_SEARCH_REGION = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable floatingImeAdjustRunnable = () -> adjustFloatingControlsForIme();
    private final ExecutorService matcherExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService accessibilityQueryExecutor = Executors.newFixedThreadPool(2);
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService screenshotExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService screenshotShareExecutor = Executors.newSingleThreadExecutor();
    private static final long SCREENSHOT_MIN_SPACING_MS = 420L;
    private static final int MAX_SCREENSHOT_INTERVAL_RETRIES = 4;
    private static final int MAX_PENDING_SCREENSHOTS = 24;
    private final Deque<ScreenshotRequest> screenshotRequestQueue = new ArrayDeque<>();
    private boolean screenshotRequestInFlight;
    private boolean screenshotPumpScheduled;
    private long lastScreenshotRequestElapsed;
    private long screenshotMinimumSpacingMs = SCREENSHOT_MIN_SPACING_MS;
    private final Runnable screenshotPumpRunnable = () -> {
        screenshotPumpScheduled = false;
        pumpNextScreenshot();
    };
    private final Runnable serviceHeartbeatRunnable = new Runnable() {
        @Override public void run() {
            getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_AGENT_SERVICE_CONNECTED, true)
                    .putLong(PREF_AGENT_SERVICE_HEARTBEAT, System.currentTimeMillis()).apply();
            handler.postDelayed(this, 3000L);
        }
    };
    private WindowManager.LayoutParams runControlsParams;
    private WindowManager wm;
    private View bubble;
    private View panel;
    private View runControls;
    private View runStatusView;
    private TextView runStatusTextView;
    private WindowManager.LayoutParams runStatusParams;
    private boolean runStatusCollapsed;
    private boolean runControlsDragging;
    private ScrollView panelScrollView;
    private CropSelector cropSelector;
    private PointSelector pointSelector;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;
    private boolean panelVisible;
    private boolean runControlsExpanded;
    private boolean running;
    private boolean paused;
    private boolean singleStepTesting;
    private boolean panelWasVisibleAtRunStart;
    private boolean panelWasVisibleBeforeSingleTest;
    private boolean executionInFlight;
    private boolean lastStepSuccess = true;
    private String currentScriptId = "";
    private int currentStepIndex;
    private int executionGeneration;
    private Deque<Branch> currentBranches = new ArrayDeque<>();
    private Deque<LoopFrame> currentLoops = new ArrayDeque<>();
    private JSONObject copiedStep;
    private boolean copiedStepWasCut;
    private String cropStepId = "";
    private boolean replaceImageTarget;
    private boolean cropCreatedNewStep;
    private int cropPurpose = CROP_TARGET;
    private String libraryPendingName = "";
    private String libraryReplaceId = "";
    private String activeLibraryId = "";
    private String activePointStepId = "";
    private JSONArray executionSteps = new JSONArray();
    private boolean customTestMode;
    private TextView resultLabel;
    private LinearLayout stepList;

    private final List<CommandEntry> commandCatalog = new ArrayList<>();
    private int lastActionResult = 0;
    private int panelRestoreY;
    private String panelScriptId = "";
    private AlertDialog compiledBlockDialog;
    private AlertDialog activeCustomDialog;
    private View smartPlanningControls;
    private boolean smartTaskActive;
    private boolean smartTaskReceiverRegistered;
    private String smartTaskGoal = "";
    private String smartTaskId = "";
    private String smartCurrentActionLabel = "";
    private JSONObject smartCurrentActionDefinition;
    private JSONArray smartTaskHistory = new JSONArray();
    private int smartTaskActionCount;
    private int smartGoalRevision;
    private int smartActionGoalRevision;
    private int smartRepeatedActionReplans;
    private long smartLastScreenshotFingerprint;
    private boolean smartTaskWasResumed;
    private int smartPlanRetryCount;
    private boolean smartPlanningInFlight;
    private boolean externalAgentCycleInFlight;
    private boolean smartExternalSequenceActive;
    private int smartExternalSequenceExecutedCount;
    private String externalAgentPackage = "";
    private String externalTargetPackage = "";
    private String externalRequestedTargetPackage = "";
    private boolean externalPromptInjectedIntoComposer;
    private int externalPromptInjectionAttempts;
    private int externalGeminiComposerFocusAttempts;
    private boolean externalCopyAttemptedForResponse;
    private int externalCodeCopyAttemptsForResponse;
    private long externalClipboardTimestampBeforeCopy;
    private String externalCopiedResponseCandidate = "";
    private String externalClipboardBeforeCopy = "";
    private boolean externalTemporalCaptureInProgress;
    private String externalTemporalStillRequestId = "";
    private ExternalStillFrameCallback externalTemporalStillCallback;
    private Runnable externalTemporalStillTimeout;
    private int externalTemporalCaptureToken;
    private String externalTemporalFramePrefix = "";
    private String externalTemporalBeforePath = "";
    private String externalTemporalDuringPath = "";
    private String externalTemporalAfterPath = "";
    private boolean externalTemporalDuringCaptureFinished;
    private int externalTemporalDuringCaptureAttempts;
    private int externalTemporalAfterWaitAttempts;
    private final ArrayList<String> pendingExternalTemporalFramePaths = new ArrayList<>();
    private String externalReferenceAttachmentPath = "";
    private String externalPrompt = "";
    private String externalBaselineUi = "";
    private ArrayList<String> externalBaselineValues = new ArrayList<>();
    private String externalLastCandidate = "";
    private int externalScreenshotWidth;
    private int externalScreenshotHeight;
    private int externalComposerPolls;
    private int externalResponsePolls;
    private int externalSendAttempts;
    private int externalFormatRetries;
    private int externalStablePolls;
    private int externalReturnAttempts;
    private int externalTargetReadAttempts;
    private long externalCycleStartedAt;
    private long externalResponseStartedAt;
    private String externalComposerStateFingerprint = "";
    private String externalResponseStateFingerprint = "";
    private long externalComposerLastProgressAt;
    private long externalResponseLastProgressAt;
    private boolean externalSendVisualProbeInFlight;
    private int externalVisualProbeGeneration;
    private boolean externalSendVisualStateKnown;
    private boolean externalSendButtonLooksBlue;
    private long externalSendVisualProbeAt;
    private boolean externalSendRetryScheduled;
    private boolean externalSendAttemptInProgress;
    private boolean externalSendRetryAwaitingReady;
    private boolean externalVisualComposerSearchInFlight;
    private boolean externalVisualSendSearchInFlight;
    private boolean externalVisualPromptInjected;
    private boolean externalVisualComposerTapped;
    private boolean externalVisualSendButtonMatched;
    private Rect externalVisualSendButtonBounds = new Rect();
    private int externalVisualSendButtonScore;
    private long externalVisualSendButtonMatchedAt;
    private String externalBaselineEditableText = "";
    private boolean externalBaselineHadEditable;
    private int externalResponseNoChangeWaitIndex;
    private int externalResponseReadAttempts;
    private boolean liveBurstRequestedForNextTask;
    private boolean temporalFramesRequestedForNextTask;
    private boolean smartTemporalFramesEnabled;
    private boolean smartLiveBurstEnabled;
    private boolean externalWhatsAppMetaAiTargetClicked;
    private boolean externalWhatsAppSearchActivated;
    private boolean externalWhatsAppSearchQueryEntered;
    private int externalWhatsAppNavigationAttempts;
    private boolean screenCapturePermissionRequestInFlight;
    private boolean screenCaptureServiceReady;
    private boolean screenCaptureServiceStarted;
    private boolean externalLiveClipInFlight;
    private String externalLiveClipRequestId = "";
    private String externalLiveClipTaskId = "";
    private String externalLiveClipPrompt = "";
    private String externalLiveClipGoal = "";
    private int externalLiveClipActionIndex;
    private static final long[] EXTERNAL_RESPONSE_READ_RETRY_DELAYS_MS = new long[]{10000L, 15000L, 20000L};
    private static final long[] EXTERNAL_RESPONSE_NO_CHANGE_DELAYS_MS = new long[]{6000L, 10000L, 20000L, 30000L};
    private static final long EXTERNAL_NO_PROGRESS_IDLE_MS = 300_000L;
    private static final long EXTERNAL_NO_PROGRESS_ACTIVE_MS = 900_000L;
    private static final long EXTERNAL_SEND_VISUAL_PROBE_INTERVAL_MS = 1800L;
    private boolean smartLiveVisionChangedDuringPlanning;
    private boolean liveVisionCaptureInFlight;
    private long smartPlanningScreenFingerprint;
    private int smartLiveVisionReplans;
    private int smartCoordinateRefusalReplans;
    private final Runnable directLiveVisionRunnable = () -> runDirectLiveVisionTick();
    private static final int SMART_PLAN_MAX_RATE_RETRIES = 15;
    private static final long SMART_PLAN_MAX_AUTO_WAIT_MS = 300_000L;
    private BroadcastReceiver smartTaskReceiver;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ensureScript();
        registerSmartTaskReceiver();
        android.content.SharedPreferences prefs = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_AGENT_SERVICE_CONNECTED, true).putLong(PREF_AGENT_SERVICE_HEARTBEAT, System.currentTimeMillis())
                .putBoolean(PREF_AGENT_TASK_ACTIVE, smartTaskActive).putBoolean(PREF_AGENT_SCREEN_AVAILABLE, false).apply();
        handler.removeCallbacks(serviceHeartbeatRunnable);
        handler.postDelayed(serviceHeartbeatRunnable, 3000L);
        showBubble();
        saveResult("Botna جاهز. اضغط B لفتح لوحة الأتمتة.");
        String pending = prefs.getString(PREF_PENDING_SMART_TASK, "");
        String pendingId = prefs.getString(PREF_PENDING_SMART_TASK_ID, "");
        boolean pendingResume = prefs.getBoolean(PREF_PENDING_SMART_RESUME, false);
        if (pending != null && !pending.trim().isEmpty()) {
            final String taskId = pendingId == null ? "" : pendingId;
            final boolean shouldResume = pendingResume;
            handler.postDelayed(() -> {
                android.content.SharedPreferences latestPrefs = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE);
                String latestGoal = latestPrefs.getString(PREF_PENDING_SMART_TASK, "");
                String latestId = latestPrefs.getString(PREF_PENDING_SMART_TASK_ID, "");
                if (pending.equals(latestGoal) && taskId.equals(latestId)) startSmartTask(pending, taskId, shouldResume);
            }, 500);
        }
    }

    private void registerSmartTaskReceiver() {
        if (smartTaskReceiverRegistered) return;
        smartTaskReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
                if (BotnaAiTemplateSettingsActivity.ACTION_SETTINGS_CLOSED.equals(action)) {
                    android.content.SharedPreferences settingsPrefs = getSharedPreferences("botna_external_app_agent", MODE_PRIVATE);
                    boolean reopenPrompt = settingsPrefs.getBoolean("reopen_external_prompt_after_template", false);
                    settingsPrefs.edit().putBoolean("reopen_external_prompt_after_template", false).apply();
                    handler.postDelayed(() -> {
                        if (!smartTaskActive && !running && !singleStepTesting) showBubble();
                        if (reopenPrompt) handler.postDelayed(BotnaAccessibilityService.this::showAutonomousExternalTaskPrompt, 350L);
                    }, 220L);
                    return;
                }
                if (BotnaWebAiActivity.ACTION_RESPONSE.equals(action)) {
                    String responseTaskId = intent.getStringExtra(BotnaWebAiActivity.EXTRA_TASK_ID);
                    String responseText = intent.getStringExtra(BotnaWebAiActivity.EXTRA_TEXT);
                    if (smartTaskActive && externalAgentCycleInFlight
                            && BotnaExternalAppAgent.usesWebAiActivity(externalAgentPackage)
                            && smartTaskId.equals(responseTaskId)) {
                        JSONObject parsed = BotnaExternalAppAgent.parseAction(responseText, externalScreenshotWidth, externalScreenshotHeight);
                        if (parsed == null) failExternalAgent("استلم Botna رد الموقع المخصص، لكنه لا يحتوي أمر JSON صالحًا وفق بروتوكول Botna. لم ينفذ أي إجراء غير مؤكد.");
                        else processExternalAssistantAction(parsed);
                    }
                    return;
                }
                if (BotnaWebAiActivity.ACTION_FAILED.equals(action)) {
                    String failedTaskId = intent.getStringExtra(BotnaWebAiActivity.EXTRA_TASK_ID);
                    String failureText = intent.getStringExtra(BotnaWebAiActivity.EXTRA_TEXT);
                    if (smartTaskActive && externalAgentCycleInFlight
                            && BotnaExternalAppAgent.usesWebAiActivity(externalAgentPackage)
                            && smartTaskId.equals(failedTaskId)) {
                        failExternalAgent(failureText == null || failureText.trim().isEmpty()
                                ? "فشل موقع الذكاء الاصطناعي المخصص." : failureText);
                    }
                    return;
                }
                if (BotnaScreenCaptureService.ACTION_CAPTURE_READY.equals(action)) {
                    String captureTaskId = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_TASK_ID);
                    if (smartTaskActive && (smartLiveBurstEnabled || shouldCaptureExternalActionFrames()) && smartTaskId.equals(captureTaskId)) {
                        screenCaptureServiceReady = true;
                        screenCaptureServiceStarted = true;
                        screenCapturePermissionRequestInFlight = false;
                        handler.postDelayed(BotnaAccessibilityService.this::requestExternalAssistantStep, 650L);
                    }
                    return;
                }
                if (BotnaScreenCaptureService.ACTION_CAPTURE_STILL_READY.equals(action)) {
                    String captureTaskId = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_TASK_ID);
                    String requestId = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_REQUEST_ID);
                    String path = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_OUTPUT_PATH);
                    if (smartTaskActive && smartTaskId.equals(captureTaskId) && requestId != null && requestId.equals(externalTemporalStillRequestId)) {
                        ExternalStillFrameCallback callback = externalTemporalStillCallback;
                        clearExternalTemporalStillRequest();
                        if (callback != null) callback.onResult(path == null ? "" : path, "");
                    }
                    return;
                }
                if (BotnaScreenCaptureService.ACTION_CAPTURE_CLIP_READY.equals(action)) {
                    String captureTaskId = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_TASK_ID);
                    String requestId = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_REQUEST_ID);
                    if (smartTaskActive && externalLiveClipInFlight && smartTaskId.equals(captureTaskId)
                            && externalLiveClipTaskId.equals(captureTaskId) && externalLiveClipRequestId.equals(requestId)) {
                        externalLiveClipInFlight = false;
                        boolean success = intent.getBooleanExtra(BotnaScreenCaptureService.EXTRA_SUCCESS, false);
                        String path = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_OUTPUT_PATH);
                        if (!success || path == null || !new File(path).isFile()) {
                            failExternalAgent("تعذر تجهيز فيديو الشاشة الحقيقي لمدة ثانيتين: " + intent.getStringExtra(BotnaScreenCaptureService.EXTRA_ERROR));
                        } else {
                            File videoFile = new File(path);
                            String prompt = externalLiveClipPrompt;
                            String goal = externalLiveClipGoal;
                            int actionIndex = externalLiveClipActionIndex;
                            handler.postDelayed(() -> takeScreenshot((latestFrame, screenshotError) -> {
                                if (!smartTaskActive || !externalAgentCycleInFlight) {
                                    if (latestFrame != null && !latestFrame.isRecycled()) latestFrame.recycle();
                                    try { videoFile.delete(); } catch (Exception ignored) { }
                                    return;
                                }
                                if (latestFrame == null) {
                                    try { videoFile.delete(); } catch (Exception ignored) { }
                                    failExternalAgent("سُجّل الفيديو لكن تعذر التقاط آخر إطار للتحكم بالإحداثيات: " + screenshotError);
                                    return;
                                }
                                externalScreenshotWidth = latestFrame.getWidth();
                                externalScreenshotHeight = latestFrame.getHeight();
                                smartLastScreenshotFingerprint = smartScreenFingerprint(latestFrame);
                                try {
                                    screenshotShareExecutor.execute(() -> prepareAndLaunchExternalStep(latestFrame, new ArrayList<>(), prompt, goal, actionIndex, videoFile));
                                } catch (Exception e) {
                                    if (!latestFrame.isRecycled()) latestFrame.recycle();
                                    try { videoFile.delete(); } catch (Exception ignored) { }
                                    failExternalAgent("تعذر تجهيز الفيديو للتحليل: " + e.getClass().getSimpleName());
                                }
                            }, "لقطة الشاشة الأحدث بعد الفيديو الحقيقي", false), 120L);
                        }
                    }
                    return;
                }
                if (BotnaScreenCaptureService.ACTION_CAPTURE_FAILED.equals(action)) {
                    String captureTaskId = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_TASK_ID);
                    String requestId = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_REQUEST_ID);
                    String error = intent.getStringExtra(BotnaScreenCaptureService.EXTRA_ERROR);
                    if (smartTaskActive && smartTaskId.equals(captureTaskId) && requestId != null
                            && !requestId.isEmpty() && requestId.equals(externalTemporalStillRequestId)) {
                        ExternalStillFrameCallback callback = externalTemporalStillCallback;
                        clearExternalTemporalStillRequest();
                        if (callback != null) callback.onResult("", error == null ? "تعذر التقاط صورة الشاشة." : error);
                        return;
                    }
                    if (smartTaskActive && (smartLiveBurstEnabled || shouldCaptureExternalActionFrames()) && smartTaskId.equals(captureTaskId)) {
                        if (requestId == null || requestId.isEmpty()) {
                            screenCapturePermissionRequestInFlight = false;
                            screenCaptureServiceReady = false;
                            failExternalAgent("تعذر بدء تسجيل الشاشة. " + (error == null ? "لم يمنح النظام إذن التسجيل." : error));
                        } else if (externalLiveClipInFlight && externalLiveClipRequestId.equals(requestId)) {
                            externalLiveClipInFlight = false;
                            failExternalAgent("تعذر تسجيل فيديو الشاشة لمدة ثانيتين: " + (error == null ? "سبب غير معروف" : error));
                        }
                    }
                    return;
                }
                if (ACTION_OPEN_AGENT_SCRIPT.equals(action)) {
                    String scriptId = intent.getStringExtra("scriptId");
                    handler.post(() -> openAgentScript(scriptId, false));
                    return;
                }
                if (ACTION_RUN_AGENT_SCRIPT.equals(action)) {
                    String scriptId = intent.getStringExtra("scriptId");
                    handler.post(() -> openAgentScript(scriptId, true));
                    return;
                }
                if (ACTION_RESUME_SMART_TASK.equals(action)) {
                    String task = intent.getStringExtra("task");
                    String taskId = intent.getStringExtra("agentTaskId");
                    if (task != null && !task.trim().isEmpty()) handler.post(() -> startSmartTask(task, taskId, true));
                    return;
                }
                if (ACTION_CORRECT_SMART_TASK.equals(action)) {
                    String correction = intent.getStringExtra("correction");
                    if (correction != null && !correction.trim().isEmpty()) handler.post(() -> applySmartTaskCorrection(correction));
                    return;
                }
                if (ACTION_LANGUAGE_CHANGED.equals(action)) {
                    handler.post(() -> refreshVisibleUiForLanguage());
                    return;
                }
                String task = intent.getStringExtra("task");
                String taskId = intent.getStringExtra("agentTaskId");
                if (task == null || task.trim().isEmpty()) task = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getString(PREF_PENDING_SMART_TASK, "");
                if (taskId == null || taskId.trim().isEmpty()) taskId = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getString(PREF_PENDING_SMART_TASK_ID, "");
                final String requested = task == null ? "" : task.trim();
                final String requestedId = taskId == null ? "" : taskId.trim();
                if (!requested.isEmpty()) handler.post(() -> startSmartTask(requested, requestedId));
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_START_SMART_TASK);
        filter.addAction(ACTION_RESUME_SMART_TASK);
        filter.addAction(ACTION_CORRECT_SMART_TASK);
        filter.addAction(ACTION_OPEN_AGENT_SCRIPT);
        filter.addAction(ACTION_RUN_AGENT_SCRIPT);
        filter.addAction(ACTION_LANGUAGE_CHANGED);
        filter.addAction(BotnaAiTemplateSettingsActivity.ACTION_SETTINGS_CLOSED);
        filter.addAction(BotnaWebAiActivity.ACTION_RESPONSE);
        filter.addAction(BotnaWebAiActivity.ACTION_FAILED);
        filter.addAction(BotnaScreenCaptureService.ACTION_CAPTURE_READY);
        filter.addAction(BotnaScreenCaptureService.ACTION_CAPTURE_STILL_READY);
        filter.addAction(BotnaScreenCaptureService.ACTION_CAPTURE_CLIP_READY);
        filter.addAction(BotnaScreenCaptureService.ACTION_CAPTURE_FAILED);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(smartTaskReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(smartTaskReceiver, filter);
            smartTaskReceiverRegistered = true;
        } catch (Exception ignored) { smartTaskReceiverRegistered = false; }
    }

    private void refreshVisibleUiForLanguage() {
        boolean reopen = panelVisible;
        if (panelVisible) hidePanel();
        if (bubble != null) { removeView(bubble); bubble = null; }
        showBubble();
        if (reopen) showPanel();
        if (running) showRunControls();
    }

    private void changeLanguageFromOverlay() {
        BotnaI18n.setLanguage(this, BotnaI18n.isEnglish(this) ? "ar" : "en");
        refreshVisibleUiForLanguage();
    }

    private void openAgentScript(String scriptId, boolean runNow) {
        if (scriptId == null || scriptId.trim().isEmpty()) { report("لا يوجد سكربت مرتبط بهذه المهمة."); return; }
        if (running || singleStepTesting || smartTaskActive) { report("أوقف التنفيذ الحالي قبل فتح سكربت وكيل آخر."); return; }
        JSONObject script = BotnaStore.getScript(this, scriptId);
        if (script == null) { report("تعذر العثور على السكربت المحفوظ لهذه المهمة."); return; }
        BotnaStore.setActiveId(this, scriptId);
        currentScriptId = scriptId;
        hidePanel();
        showBubble();
        if (runNow) runActiveScript();
        else {
            showPanel();
            report("فُتح سكربت الوكيل للتعديل: " + script.optString("name", "مهمة ذكية") + ".");
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (!smartTaskActive && BotnaVisionEngine.shouldCaptureOnEvent(this, type, System.currentTimeMillis())) {
            handler.postDelayed(() -> takeScreenshot((bitmap, error) -> {
                if (bitmap == null) return;
                try {
                    if (BotnaVisionEngine.hasSignificantVisualChange(bitmap)) {
                        BotnaLogStore.add(this, "vision_stream", -1, "تغيّر بصري في الشاشة",
                                "التقط Botna إشارة تغير بعد حدث واجهة، وتمت مقارنة عينة بصرية محليًا.",
                                "حالة المحرك: " + BotnaVisionEngine.status(this),
                                BotnaLogStore.screenshotsEnabled(this) ? bitmap : null);
                    }
                } catch (Exception ignored) { }
                finally { if (!bitmap.isRecycled()) bitmap.recycle(); }
            }), 180);
        }
        if (runControls == null || !running || runControlsDragging) return;
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED
                || type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                || type == AccessibilityEvent.TYPE_VIEW_FOCUSED
                || type == AccessibilityEvent.TYPE_VIEW_CLICKED
                || type == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            handler.removeCallbacks(floatingImeAdjustRunnable);
            handler.postDelayed(floatingImeAdjustRunnable, 110);
        }
    }
    private void runDirectLiveVisionTick() {
        if (!smartTaskActive) { liveVisionCaptureInFlight = false; return; }
        if (!BotStudioStore.directLiveVisionEnabled(this) || liveVisionCaptureInFlight || externalAgentCycleInFlight) {
            handler.postDelayed(directLiveVisionRunnable, 1900L);
            return;
        }
        liveVisionCaptureInFlight = true;
        takeScreenshot((bitmap, error) -> {
            liveVisionCaptureInFlight = false;
            if (!smartTaskActive) { if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle(); return; }
            if (bitmap == null) {
                BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "live_vision_unavailable", smartTaskActionCount,
                        "الرؤية المباشرة", "تعذر التقاط لقطة متابعة: " + (error == null ? "سبب غير معروف" : error), false, null);
            } else {
                long fingerprint = smartScreenFingerprint(bitmap);
                if (smartPlanningInFlight && smartPlanningScreenFingerprint != 0L
                        && Long.bitCount(fingerprint ^ smartPlanningScreenFingerprint) >= 18) {
                    smartLiveVisionChangedDuringPlanning = true;
                    BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "screen_changed_during_planning", smartTaskActionCount,
                            "تغيرت الشاشة أثناء التفكير", "اكتشفت الرؤية المباشرة اختلافًا مرئيًا واضحًا عن الشاشة التي بُنيت عليها الخطة؛ ستُلغى الخطة القديمة ويُعاد التخطيط قبل النقر.", false, null);
                }
                if (!bitmap.isRecycled()) bitmap.recycle();
            }
            handler.postDelayed(directLiveVisionRunnable, 1900L);
        }, "الرؤية المباشرة");
    }

    @Override public void onInterrupt() {
        getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit().putBoolean(PREF_AGENT_SCREEN_AVAILABLE, false).apply();
        saveResult("تمت مقاطعة خدمة إمكانية الوصول؛ لا توجد لقطة شاشة موثوقة حاليًا.");
        if (smartTaskActive) handler.post(() -> endSmartTask("انقطعت خدمة إمكانية الوصول أثناء المهمة. لم أعد أملك لقطة شاشة موثوقة، لذلك أوقفت التنفيذ بدل الادعاء بأنني أرى الشاشة.", false));
    }

    @Override public void onDestroy() {
        if (screenCaptureServiceStarted) {
            try {
                Intent stopCapture = new Intent(this, BotnaScreenCaptureService.class);
                stopCapture.setAction(BotnaScreenCaptureService.ACTION_STOP_CAPTURE);
                startService(stopCapture);
            } catch (Exception ignored) { }
        }
        handler.removeCallbacks(serviceHeartbeatRunnable);
        getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit().putBoolean(PREF_AGENT_SERVICE_CONNECTED, false)
                .putBoolean(PREF_AGENT_SCREEN_AVAILABLE, false).putBoolean(PREF_AGENT_TASK_ACTIVE, false)
                .putLong(PREF_AGENT_SERVICE_HEARTBEAT, 0L).apply();
        if (smartTaskActive && smartTaskId != null && !smartTaskId.isEmpty()) {
            String reason = "انقطعت خدمة إمكانية الوصول أثناء تنفيذ المهمة؛ أُبقيت الخطوات والرسائل المحفوظة للمراجعة.";
            try { BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "interrupted"); } catch (Exception ignored) { }
            BotnaAgentStore.setStatus(this, smartTaskId, "انقطعت الخدمة", reason);
            BotnaAgentStore.addMessage(this, smartTaskId, "system", "انقطعت خدمة بوتنا", reason, smartTaskActionCount, false);
            String studioBotId = BotStudioStore.botForTask(this, smartTaskId);
            if (!studioBotId.isEmpty()) BotStudioStore.get(this).addMessage(studioBotId, "assistant",
                    "⚠️ توقفت المهمة: انقطعت خدمة التحكم.\n" + reason + "\nلا توجد لقطة شاشة حديثة تؤكد الحالة الحالية.", "", BotnaAICompiler.model(this));
        }
        if (smartTaskReceiverRegistered && smartTaskReceiver != null) {
            try { unregisterReceiver(smartTaskReceiver); } catch (Exception ignored) { }
            smartTaskReceiverRegistered = false;
        }
        removeView(panel);
        removeView(runControls);
        removeView(runStatusView);
        removeView(smartPlanningControls);
        removeView(cropSelector);
        removeView(pointSelector);
        removeView(bubble);
        panel = null; runControls = null; runStatusView = null; smartPlanningControls = null; cropSelector = null; pointSelector = null; bubble = null;
        panelVisible = false;
        running = false; paused = false; executionGeneration++;
        currentBranches.clear(); currentLoops.clear();
        try { BotnaStore.saveScripts(this, BotnaStore.scripts(this)); } catch (Exception ignored) { }
        handler.removeCallbacks(screenshotPumpRunnable);
        handler.removeCallbacks(directLiveVisionRunnable);
        liveVisionCaptureInFlight = false;
        smartPlanningInFlight = false;
        screenshotPumpScheduled = false;
        screenshotRequestQueue.clear();
        screenshotRequestInFlight = false;
        matcherExecutor.shutdownNow();
        accessibilityQueryExecutor.shutdownNow();
        aiExecutor.shutdownNow();
        networkExecutor.shutdownNow();
        screenshotExecutor.shutdownNow();
        screenshotShareExecutor.shutdownNow();
        BotnaAgentKeepAliveService.stop(this);
        super.onDestroy();
    }

    private void showBubble() {
        if (wm == null || bubble != null || running || singleStepTesting) return;
        TextView b = new TextView(this);
        b.setText("B"); b.setTextSize(20); b.setTextColor(CYAN);
        b.setGravity(Gravity.CENTER); b.setElevation(dp(12));
        b.setBackground(glassShape(Color.argb(228, 8, 13, 22), Color.argb(240, 35, 226, 244), 2, 36));
        b.setContentDescription(BotnaI18n.translate(this, "بوتنا: افتح لوحة الأتمتة"));
        android.util.DisplayMetrics metrics = realScreenMetrics();
        int size = dp(58);
        android.content.SharedPreferences prefs = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE);
        final int[] side = {prefs.getInt("bubble_side", 1)};
        final int[] savedTop = {clampInt(prefs.getInt("bubble_y", dp(250)), 0, Math.max(0, metrics.heightPixels - size))};
        bubbleParams = new WindowManager.LayoutParams(size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.LEFT;
        bubbleParams.x = side[0] == 0 ? -size / 2 : metrics.widthPixels - size / 2;
        bubbleParams.y = savedTop[0];
        final float[] down = new float[2];
        final int[] origin = new int[2];
        final boolean[] dragged = new boolean[1];
        b.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX(); down[1] = e.getRawY();
                    origin[0] = bubbleParams.x; origin[1] = bubbleParams.y;
                    dragged[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - down[0], dy = e.getRawY() - down[1];
                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) dragged[0] = true;
                    if (dragged[0]) {
                        bubbleParams.x = clampInt(origin[0] + (int) dx, -size / 2, Math.max(-size / 2, metrics.widthPixels - size / 2));
                        bubbleParams.y = clampInt(origin[1] + (int) dy, 0, Math.max(0, metrics.heightPixels - size));
                        try { wm.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragged[0]) {
                        side[0] = bubbleParams.x + size / 2 < metrics.widthPixels / 2 ? 0 : 1;
                        int dockX = side[0] == 0 ? -size / 2 : metrics.widthPixels - size / 2;
                        int dockY = clampInt(bubbleParams.y, 0, Math.max(0, metrics.heightPixels - size));
                        animateBubbleDock(dockX, dockY, side[0]);
                    } else togglePanel();
                    return true;
            }
            return true;
        });
        try { wm.addView(b, bubbleParams); bubble = b; }
        catch (Exception e) { saveResult("تعذر إظهار الزر العائم: " + e.getClass().getSimpleName()); }
    }

    private void animateBubbleDock(int targetX, int targetY, int side) {
        if (bubble == null || bubbleParams == null || wm == null) return;
        final View animatedBubble = bubble;
        final WindowManager.LayoutParams animatedParams = bubbleParams;
        final int startX = animatedParams.x, startY = animatedParams.y;
        ValueAnimator magnet = ValueAnimator.ofFloat(0f, 1f);
        magnet.setDuration(430L);
        magnet.setInterpolator(new OvershootInterpolator(1.65f));
        magnet.addUpdateListener(animation -> {
            if (bubble != animatedBubble || bubbleParams != animatedParams) return;
            float t = (float) animation.getAnimatedValue();
            animatedParams.x = startX + Math.round((targetX - startX) * t);
            animatedParams.y = startY + Math.round((targetY - startY) * t);
            float pulse = (float) Math.sin(Math.min(1f, Math.max(0f, t)) * Math.PI);
            animatedBubble.setScaleX(1f + pulse * 0.075f);
            animatedBubble.setScaleY(1f - pulse * 0.055f);
            try { wm.updateViewLayout(animatedBubble, animatedParams); } catch (Exception ignored) { }
        });
        magnet.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (bubble != animatedBubble || bubbleParams != animatedParams) return;
                animatedParams.x = targetX;
                animatedParams.y = targetY;
                try { wm.updateViewLayout(animatedBubble, animatedParams); } catch (Exception ignored) { }
                animatedBubble.animate().scaleX(1f).scaleY(1f).setDuration(150L).start();
                getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit()
                        .putInt("bubble_side", side).putInt("bubble_y", targetY).apply();
            }
        });
        magnet.start();
    }

    private void revealBubble(Runnable afterReveal) {
        if (bubble == null || bubbleParams == null || wm == null) {
            if (afterReveal != null) afterReveal.run();
            return;
        }
        android.util.DisplayMetrics dm = realScreenMetrics();
        int size = bubbleParams.width;
        int currentX = bubbleParams.x;
        int visibleX = currentX < 0 ? 0 : (currentX > dm.widthPixels - size ? dm.widthPixels - size : currentX);
        if (visibleX == currentX) {
            if (afterReveal != null) afterReveal.run();
            return;
        }
        final View targetView = bubble;
        final WindowManager.LayoutParams targetParams = bubbleParams;
        ValueAnimator reveal = ValueAnimator.ofFloat(0f, 1f);
        int startX = targetParams.x;
        int startY = targetParams.y;
        int endX = visibleX;
        reveal.setDuration(310L);
        reveal.setInterpolator(new OvershootInterpolator(1.35f));
        reveal.addUpdateListener(a -> {
            if (bubble != targetView || bubbleParams != targetParams) return;
            float t = (float) a.getAnimatedValue();
            targetParams.x = startX + Math.round((endX - startX) * t);
            targetParams.y = startY;
            float pulse = (float) Math.sin(Math.min(1f, Math.max(0f, t)) * Math.PI);
            targetView.setScaleX(1f + pulse * 0.08f);
            targetView.setScaleY(1f - pulse * 0.06f);
            try { wm.updateViewLayout(targetView, targetParams); } catch (Exception ignored) { }
        });
        reveal.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (bubble != targetView || bubbleParams != targetParams) return;
                targetParams.x = endX;
                targetView.animate().scaleX(1f).scaleY(1f).setDuration(130L).withEndAction(() -> {
                    if (bubble == targetView && afterReveal != null) afterReveal.run();
                }).start();
                try { wm.updateViewLayout(targetView, targetParams); } catch (Exception ignored) { }
            }
        });
        reveal.start();
    }

    private void dockBubbleToSide() {
        if (bubble == null || bubbleParams == null || wm == null) return;
        android.util.DisplayMetrics dm = realScreenMetrics();
        int size = bubbleParams.width;
        int side = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getInt("bubble_side", bubbleParams.x < 0 ? 0 : 1);
        int dockX = side == 0 ? -size / 2 : dm.widthPixels - size / 2;
        int dockY = clampInt(bubbleParams.y, 0, Math.max(0, dm.heightPixels - size));
        animateBubbleDock(dockX, dockY, side);
    }

    private void togglePanel() {
        if (panelVisible) {
            hidePanel();
            dockBubbleToSide();
        } else {
            revealBubble(this::showPanel);
        }
    }

    private void showPanel() {
        if (wm == null || panelVisible || running || singleStepTesting) return;
        commandCatalog.clear();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        BotnaI18n.applyDirection(this, root);
        root.setBackground(glassShape(GLASS, Color.argb(235, 35, 226, 244), 2, 22));
        root.setElevation(dp(16));

        LinearLayout header = horizontal();
        TextView title = label("BOTNA  •  الأتمتة الذكية", 16, TEXT, true);
        title.setTextSize(13);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button language = button("🌐", false);
        language.setContentDescription(BotnaI18n.isEnglish(this) ? "Switch application language to Arabic" : "تغيير لغة التطبيق إلى الإنجليزية");
        header.addView(language, new LinearLayout.LayoutParams(dp(38), dp(42)));
        language.setOnClickListener(v -> changeLanguageFromOverlay());
        Button hideList = button("إخفاء القائمة", false);
        hideList.setTextSize(11);
        hideList.setContentDescription(BotnaI18n.translate(this, "إخفاء القائمة والعودة إلى الزر العائم"));
        hideList.setOnClickListener(v -> { hidePanel(); dockBubbleToSide(); });
        header.addView(hideList, new LinearLayout.LayoutParams(dp(82), dp(42)));
        Button saveClose = button("حفظ", false);
        saveClose.setContentDescription(BotnaI18n.translate(this, "حفظ السكربت وإغلاق لوحة الأتمتة"));
        saveClose.setOnClickListener(v -> confirmSaveAndClose());
        header.addView(saveClose, new LinearLayout.LayoutParams(dp(50), dp(42)));
        Button close = button("×", false);
        close.setTextSize(20);
        close.setContentDescription(BotnaI18n.translate(this, "إغلاق لوحة الأتمتة بعد تأكيد الحفظ"));
        close.setOnClickListener(v -> confirmSaveAndClose());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(34), dp(42));
        closeLp.setMargins(dp(4), 0, 0, 0);
        header.addView(close, closeLp);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout fixedRunRow = horizontal();
        Button fixedRun = button("▶ بدء", true);
        Button fixedPause = button("Ⅱ إيقاف/استكمال", false);
        Button fixedStop = button("■ إيقاف", false);
        fixedRunRow.addView(fixedRun, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams fixedPauseLp = new LinearLayout.LayoutParams(0, dp(38), 1); fixedPauseLp.setMargins(dp(3), 0, dp(3), 0); fixedRunRow.addView(fixedPause, fixedPauseLp);
        fixedRunRow.addView(fixedStop, new LinearLayout.LayoutParams(0, dp(38), 1));
        Button searchCommands = button("⌕", false);
        searchCommands.setContentDescription(BotnaI18n.translate(this, "البحث عن أمر أو خطوة لإضافتها إلى السكربت"));
        searchCommands.setTooltipText("بحث عن أمر");
        LinearLayout.LayoutParams searchCommandsLp = new LinearLayout.LayoutParams(dp(42), dp(38));
        searchCommandsLp.leftMargin = dp(3);
        fixedRunRow.addView(searchCommands, searchCommandsLp);
        LinearLayout.LayoutParams fixedRunRowLp = new LinearLayout.LayoutParams(-1, dp(38)); fixedRunRowLp.topMargin = dp(5); root.addView(fixedRunRow, fixedRunRowLp);
        fixedRun.setOnClickListener(v -> runActiveScript());
        fixedPause.setOnClickListener(v -> { if (!running) report("لا يوجد سكربت قيد التشغيل."); else if (paused) resumeScript(); else pauseScript(); });
        fixedStop.setOnClickListener(v -> stopScript());
        searchCommands.setOnClickListener(v -> showCommandSearchDialog());

        ScrollView panelScroll = new ScrollView(this);
        this.panelScrollView = panelScroll;
        panelScroll.setFillViewport(false);
        panelScroll.setVerticalScrollBarEnabled(true);
        panelScroll.setSmoothScrollingEnabled(true);
        panelScroll.setClipToPadding(false);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(2), dp(5), dp(2), dp(12));
        panelScroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        LinearLayout.LayoutParams panelScrollLp =
                new LinearLayout.LayoutParams(-1, 0, 1f);
        panelScrollLp.topMargin = dp(5);
        root.addView(panelScroll, panelScrollLp);

        JSONObject script = BotnaStore.activeScript(this);
        panelScriptId = script.optString("id", "");
        TextView activeName = label(
                "السكربت الحالي: " + script.optString("name", "بدون اسم"),
                13, CYAN, true);
        LinearLayout.LayoutParams nameLp = wrap();
        nameLp.topMargin = dp(2);
        content.addView(activeName, nameLp);

        LinearLayout scriptButtons = horizontal();
        Button newScript = button("＋ سكربت جديد", true);
        Button chooseScript = button("تبديل السكربت", false);
        scriptButtons.addView(newScript, new LinearLayout.LayoutParams(0, dp(42), 1));
        scriptButtons.addView(chooseScript, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams scriptLp = wrap();
        scriptLp.topMargin = dp(6);
        content.addView(scriptButtons, scriptLp);
        newScript.setOnClickListener(v -> promptText("اسم السكربت الجديد", "سكربت جديد", name -> {
            BotnaStore.createScript(this, name);
            rebuildPanel();
        }));
        chooseScript.setOnClickListener(v -> chooseScriptDialog());

        Button readScreenButton = button("⌕ قراءة عناصر الشاشة", false);
        LinearLayout.LayoutParams readLp = new LinearLayout.LayoutParams(-1, dp(40));
        readLp.topMargin = dp(6);
        content.addView(readScreenButton, readLp);
        readScreenButton.setOnClickListener(v -> readScreen());
        Button aiSettingsButton = button("⚙ إعدادات مترجم الذكاء الاصطناعي", false);
        LinearLayout.LayoutParams aiSettingsLp = new LinearLayout.LayoutParams(-1, dp(38));
        aiSettingsLp.topMargin = dp(5); content.addView(aiSettingsButton, aiSettingsLp);
        aiSettingsButton.setOnClickListener(v -> BotnaAICompiler.showSettingsDialog(this, true, this::rebuildPanel));
        Button shareScreenAi = button("🤖 مهمة آلية • " + BotnaExternalAppAgent.label(BotnaExternalAppAgent.selectedPackage(this)), true);
        shareScreenAi.setContentDescription("اكتب هدف المهمة لتنفذ آليًا عبر تطبيق الذكاء الاصطناعي المختار. اضغط مطولًا لتغيير التطبيق المختار.");
        LinearLayout.LayoutParams shareScreenAiLp = new LinearLayout.LayoutParams(-1, dp(42));
        shareScreenAiLp.topMargin = dp(6);
        content.addView(shareScreenAi, shareScreenAiLp);
        shareScreenAi.setOnClickListener(v -> promptAutonomousExternalTask());
        shareScreenAi.setOnLongClickListener(v -> {
            if (running || singleStepTesting || smartTaskActive) {
                report("أوقف المهمة الحالية قبل تغيير تطبيق الذكاء الاصطناعي.");
                return true;
            }
            showExternalAgentAppPickerForTask(() -> report("تم حفظ تطبيق الذكاء الاصطناعي: " + BotnaExternalAppAgent.label(BotnaExternalAppAgent.selectedPackage(this)) + ". سيُستخدم تلقائيًا في المهام التالية."));
            return true;
        });

        TextView addTitle = label("إضافة خطوة", 14, TEXT, true);
        LinearLayout.LayoutParams addTitleLp = wrap();
        addTitleLp.topMargin = dp(9);
        content.addView(addTitle, addTitleLp);

        LinearLayout addRow1 = horizontal();
        addButton(addRow1, "＋ قص هدف", this::prepareCropStep);
        addButton(addRow1, "＋ نقر نص", () -> promptText("النص المطلوب النقر عليه", "تطوير", value -> {
            JSONObject step = addStep(TYPE_TEXT, "نقر نص: " + value);
            putString(step, "value", value);
            saveStepObject(step);
            rebuildPanel();
        }));
        addButton(addRow1, "＋ انتظار", () -> promptNumber("مدة الانتظار بالمللي ثانية", 500, 0, 60000, value -> {
            JSONObject step = addStep(TYPE_WAIT, "انتظار " + value + " ms");
            putInt(step, "delay", value);
            saveStepObject(step);
            rebuildPanel();
        }));
        LinearLayout.LayoutParams addLp = wrap();
        addLp.topMargin = dp(5);
        content.addView(addRow1, addLp);

        LinearLayout addRow2 = horizontal();
        addButton(addRow2, "رجوع", () -> { addStep(TYPE_BACK, "رجوع"); rebuildPanel(); });
        addButton(addRow2, "الرئيسية", () -> { addStep(TYPE_HOME, "الرئيسية"); rebuildPanel(); });
        addButton(addRow2, "إذا نجح", () -> { addStep(TYPE_IF_SUCCESS, "إذا نجح الإجراء السابق"); rebuildPanel(); });
        LinearLayout.LayoutParams add2Lp = wrap();
        add2Lp.topMargin = dp(5);
        content.addView(addRow2, add2Lp);

        LinearLayout addRow3 = horizontal();
        addButton(addRow3, "إذا فشل", () -> { addStep(TYPE_IF_FAIL, "إذا فشل الإجراء السابق"); rebuildPanel(); });
        addButton(addRow3, "وإلا", () -> { addStep(TYPE_ELSE, "وإلا"); rebuildPanel(); });
        addButton(addRow3, "نهاية الشرط", () -> { addStep(TYPE_END_IF, "نهاية الشرط"); rebuildPanel(); });
        LinearLayout.LayoutParams add3Lp = wrap();
        add3Lp.topMargin = dp(5);
        content.addView(addRow3, add3Lp);

        LinearLayout addRow4 = horizontal();
        addButton(addRow4, "نقر موقع", () -> {
            JSONObject step = addStep(TYPE_TAP, "نقر موقع — حدد النقطة");
            putInt(step, "xPercent", 50); putInt(step, "yPercent", 50);
            saveStepObject(step);
            openPointSelector(step);
        });
        addButton(addRow4, "سحب الشاشة", () -> promptText("السحب: x1,y1,x2,y2 كنسب مئوية", "50,75,50,25", value -> {
            String[] xy = value.split(",");
            if (xy.length != 4) { report("اكتب أربع قيم مثل 50,75,50,25"); return; }
            try {
                int[] n = new int[4]; for (int i = 0; i < 4; i++) n[i] = Math.max(0, Math.min(100, Integer.parseInt(xy[i].trim())));
                JSONObject step = addStep(TYPE_SWIPE, "سحب الشاشة");
                putInt(step, "x1", n[0]); putInt(step, "y1", n[1]); putInt(step, "x2", n[2]); putInt(step, "y2", n[3]);
                putInt(step, "duration", 450); saveStepObject(step); rebuildPanel();
            } catch (NumberFormatException ex) { report("قيم السحب يجب أن تكون أرقامًا من 0 إلى 100."); }
        }));
        addButton(addRow4, "تمرير", () -> showChoice("اتجاه التمرير", new String[]{"للأسفل", "للأعلى", "لليمين", "لليسار"}, which -> {
            JSONObject step = addStep(TYPE_SCROLL, "تمرير " + new String[]{"للأسفل", "للأعلى", "لليمين", "لليسار"}[which]);
            putString(step, "direction", new String[]{"down", "up", "right", "left"}[which]);
            putInt(step, "duration", 420); saveStepObject(step); rebuildPanel();
        }));
        LinearLayout.LayoutParams add4Lp = wrap(); add4Lp.topMargin = dp(5); content.addView(addRow4, add4Lp);

        LinearLayout addRow5 = horizontal();
        addButton(addRow5, "كتابة نص", () -> promptText("النص الذي سيُكتب في الحقل المركّز", "", value -> {
            JSONObject step = addStep(TYPE_TYPE_TEXT, "كتابة نص: " + value);
            putString(step, "value", value); saveStepObject(step); rebuildPanel();
        }));
        addButton(addRow5, "انتظار صورة", () -> {
            JSONObject step = addStep(TYPE_IMAGE, "انتظار ظهور صورة");
            initializeImageStepDefaults(step, true);
            cropStepId = step.optString("id"); cropCreatedNewStep = true;
            cropPurpose = CROP_TARGET; replaceImageTarget = false;
            saveStepObject(step); beginCaptureForCrop();
        });
        addButton(addRow5, "تأخير عشوائي", () -> promptText("أدنى وأقصى انتظار بالمللي ثانية (min,max)", "500,1200", value -> {
            String[] limits = value.split(",");
            if (limits.length != 2) { report("اكتب الحدين مثل 500,1200"); return; }
            try {
                int min = Math.max(0, Math.min(60000, Integer.parseInt(limits[0].trim())));
                int max = Math.max(min, Math.min(60000, Integer.parseInt(limits[1].trim())));
                JSONObject step = addStep(TYPE_WAIT, "انتظار عشوائي " + min + "–" + max + " ms");
                putInt(step, "minDelay", min); putInt(step, "maxDelay", max); putBoolean(step, "randomDelay", true);
                saveStepObject(step); rebuildPanel();
            } catch (NumberFormatException ex) { report("المدة يجب أن تكون رقمًا."); }
        }));
        LinearLayout.LayoutParams add5Lp = wrap(); add5Lp.topMargin = dp(5); content.addView(addRow5, add5Lp);

        LinearLayout addRow6 = horizontal();
        addButton(addRow6, "ضغط مطوّل", () -> promptText("موقع الضغط المطوّل x,y كنسب مئوية", "50,50", value -> {
            String[] xy=value.split(",");
            try {
                if(xy.length!=2) throw new NumberFormatException();
                int x=Math.max(0,Math.min(100,Integer.parseInt(xy[0].trim())));
                int y=Math.max(0,Math.min(100,Integer.parseInt(xy[1].trim())));
                JSONObject step=addStep(TYPE_LONG_PRESS,"ضغط مطوّل في " + x + "%، " + y + "%");
                putInt(step,"xPercent",x); putInt(step,"yPercent",y); putInt(step,"duration",800);
                saveStepObject(step); rebuildPanel();
            } catch(NumberFormatException ex) { report("أدخل إحداثيين صحيحين مثل 50,50."); }
        }));
        addButton(addRow6, "إعادة قراءة الشاشة", this::readScreen);
        addButton(addRow6, "اختبار آخر خطوة", () -> {
            JSONArray current=BotnaStore.steps(ensureScript());
            if(current.length()==0) report("لا توجد خطوة لاختبارها."); else testStep(current.length()-1);
        });
        LinearLayout.LayoutParams add6Lp = wrap(); add6Lp.topMargin = dp(5); content.addView(addRow6, add6Lp);

        LinearLayout addRow7 = horizontal();
        addButton(addRow7, "بدء تكرار", () -> promptNumber("عدد مرات تكرار المجموعة (أي عدد صحيح موجب)", 3, 1, Integer.MAX_VALUE, count -> {
            JSONObject step = addStep(TYPE_REPEAT_START, "بدء تكرار ×" + count);
            putInt(step, "repeatCount", count); saveStepObject(step); rebuildPanel();
        }));
        addButton(addRow7, "نهاية التكرار", () -> { addStep(TYPE_REPEAT_END, "نهاية التكرار"); rebuildPanel(); });
        addButton(addRow7, "اختبار خطوة محددة", () -> {
            JSONArray current = BotnaStore.steps(ensureScript());
            if (current.length() == 0) { report("لا توجد خطوات لاختبارها."); return; }
            promptNumber("رقم الخطوة للاختبار", current.length(), 1, current.length(), number -> testStep(number - 1));
        });
        LinearLayout.LayoutParams add7Lp = wrap(); add7Lp.topMargin = dp(5); content.addView(addRow7, add7Lp);

        LinearLayout addRow8 = horizontal();
        addButton(addRow8, "فتح تطبيق", () -> promptText("اسم التطبيق المثبت الذي تريد فتحه", "", appName -> {
            if (appName.trim().isEmpty()) { report("اكتب اسم التطبيق أولًا."); return; }
            JSONObject step = addStep(TYPE_LAUNCH_APP, "فتح تطبيق: " + appName.trim());
            putString(step, "appName", appName.trim());
            saveStepObject(step); rebuildPanel();
        }));
        addButton(addRow8, "＋ خطوة مخصصة", this::addCustomStep);
        addButton(addRow8, "مكتبة الصور", this::showImageLibrary);
        LinearLayout.LayoutParams add8Lp = wrap(); add8Lp.topMargin = dp(5); content.addView(addRow8, add8Lp);

        LinearLayout addRow9 = horizontal();
        addButton(addRow9, "فحص الإنترنت", () -> promptText("عدد المحاولات والفاصل بالمللي ثانية (مثال 3,5000)", "3,5000", value -> {
            try {
                String[] parts = value.split(","); if (parts.length != 2) throw new NumberFormatException();
                int attempts = Math.max(1, Integer.parseInt(parts[0].trim()));
                int interval = Math.max(250, Math.min(60000, Integer.parseInt(parts[1].trim())));
                JSONObject step = addStep(TYPE_CHECK_INTERNET, "فحص اتصال الإنترنت ×" + attempts);
                putInt(step, "attempts", attempts); putInt(step, "intervalMs", interval); putInt(step, "timeoutMs", 4000);
                saveStepObject(step); rebuildPanel();
            } catch (NumberFormatException ex) { report("اكتب عدد المحاولات والفاصل هكذا: 3,5000"); }
        }));
        addButton(addRow9, "انتظار عودة الإنترنت", () -> promptText("الحد الأقصى للمحاولات والفاصل بالمللي ثانية", "120,5000", value -> {
            try {
                String[] parts = value.split(","); if (parts.length != 2) throw new NumberFormatException();
                int attempts = Math.max(1, Integer.parseInt(parts[0].trim()));
                int interval = Math.max(500, Math.min(60000, Integer.parseInt(parts[1].trim())));
                JSONObject step = addStep(TYPE_WAIT_INTERNET, "انتظار عودة الإنترنت");
                putInt(step, "attempts", attempts); putInt(step, "intervalMs", interval); putInt(step, "timeoutMs", 4000);
                saveStepObject(step); rebuildPanel();
            } catch (NumberFormatException ex) { report("اكتب المحاولات والفاصل، مثال 120,5000"); }
        }));
        addButton(addRow9, "فتح رابط", () -> promptText("رابط HTTP/HTTPS (مثل صفحة الراوتر)", "http://192.168.1.1", value -> {
            if (!value.trim().startsWith("http://") && !value.trim().startsWith("https://")) { report("يسمح فقط بروابط HTTP أو HTTPS."); return; }
            JSONObject step = addStep(TYPE_OPEN_URL, "فتح رابط: " + value.trim()); putString(step, "url", value.trim());
            saveStepObject(step); rebuildPanel();
        }));
        LinearLayout.LayoutParams add9Lp = wrap(); add9Lp.topMargin = dp(5); content.addView(addRow9, add9Lp);

        LinearLayout addRow10 = horizontal();
        addButton(addRow10, "تنبيه مع نغمة", () -> promptText("نص الرسالة التي ستظهر عند الوصول لهذه الخطوة", "تم تنفيذ الأمر بنجاح", value -> {
            JSONObject step = addStep(TYPE_NOTIFY, "تنبيه: " + value); putString(step, "message", value); putBoolean(step, "sound", true);
            saveStepObject(step); rebuildPanel();
        }));
        addButton(addRow10, "ضغط Enter/بحث", () -> { JSONObject step = addStep(TYPE_PRESS_ENTER, "تنفيذ أمر Enter/بحث"); saveStepObject(step); rebuildPanel(); });
        addButton(addRow10, "انتظار ظهور نص", () -> promptText("النص الذي يجب أن يظهر على الشاشة", "بحث", target -> {
            if (target.trim().isEmpty()) { report("اكتب النص المنتظر."); return; }
            JSONObject step = addStep(TYPE_WAIT_TEXT, "انتظار ظهور النص: " + target.trim()); putString(step, "value", target.trim()); putInt(step, "attempts", 20); putInt(step, "intervalMs", 500);
            saveStepObject(step); rebuildPanel();
        }));
        LinearLayout.LayoutParams add10Lp = wrap(); add10Lp.topMargin = dp(5); content.addView(addRow10, add10Lp);
        LinearLayout addRow11 = horizontal();
        addButton(addRow11, "انتظار حقل إدخال", () -> { JSONObject step=addStep(TYPE_WAIT_INPUT,"انتظار ظهور حقل إدخال قابل للكتابة"); putInt(step,"attempts",20); putInt(step,"intervalMs",500); saveStepObject(step); rebuildPanel(); });
        LinearLayout.LayoutParams add11Lp = wrap(); add11Lp.topMargin = dp(5); content.addView(addRow11, add11Lp);

        LinearLayout addRow12 = horizontal();
        addButton(addRow12, "فتح الإشعارات", () -> addGlobalActionStep("notifications", "فتح لوحة الإشعارات"));
        addButton(addRow12, "الإعدادات السريعة", () -> addGlobalActionStep("quick_settings", "فتح الإعدادات السريعة"));
        addButton(addRow12, "التطبيقات الأخيرة", () -> addGlobalActionStep("recent_apps", "فتح التطبيقات الأخيرة"));
        LinearLayout.LayoutParams add12Lp = wrap(); add12Lp.topMargin = dp(5); content.addView(addRow12, add12Lp);
        LinearLayout addRow13 = horizontal();
        addButton(addRow13, "إغلاق الإشعارات", () -> addGlobalActionStep("dismiss_notifications", "إغلاق لوحة الإشعارات"));
        addButton(addRow13, "إغلاق التطبيقات الأخيرة", () -> { JSONObject step=addStep(TYPE_CLOSE_RECENTS,"إغلاق التطبيقات الأخيرة الظاهرة"); saveStepObject(step); rebuildPanel(); });
        addButton(addRow13, "مسح حقل الكتابة", () -> { JSONObject step=addStep(TYPE_CLEAR_TEXT,"مسح حقل الإدخال المركّز"); saveStepObject(step); rebuildPanel(); });
        LinearLayout.LayoutParams add13Lp = wrap(); add13Lp.topMargin = dp(5); content.addView(addRow13, add13Lp);
        LinearLayout addRow14 = horizontal();
        addButton(addRow14, "قياس سرعة التنزيل", () -> { JSONObject step = addStep(TYPE_SPEED_TEST, "قياس سرعة تنزيل الإنترنت التقريبية"); saveStepObject(step); rebuildPanel(); });
        LinearLayout.LayoutParams add14Lp = wrap(); add14Lp.topMargin = dp(5); content.addView(addRow14, add14Lp);

        TextView stepsTitle = label("الخطوات — اضغط للتعديل، واضغط مطولًا للنسخ/القص/التحريك/اللصق", 12, MUTED, true);
        LinearLayout.LayoutParams stLp = wrap();
        stLp.topMargin = dp(10);
        content.addView(stepsTitle, stLp);

        stepList = new LinearLayout(this);
        stepList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams stepsLp = wrap();
        stepsLp.topMargin = dp(4);
        content.addView(stepList, stepsLp);
        populateSteps(script);
        String scrollKey = "panel_scroll_" + script.optString("id", "default");
        int restoreY = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE)
                .getInt(scrollKey, 0);
        panelScroll.post(() -> panelScroll.scrollTo(0, Math.max(0, restoreY)));
        panelScroll.setOnScrollChangeListener((View v, int sx, int sy, int oldSx, int oldSy) -> {
            panelRestoreY = sy;
            getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit()
                    .putInt(scrollKey, sy).apply();
        });

        resultLabel = label(getLastResult(), 12, MUTED, false);
        LinearLayout.LayoutParams resultLp = wrap();
        resultLp.topMargin = dp(9);
        content.addView(resultLabel, resultLp);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.max(dp(270), Math.min(dp(390), screenWidth - dp(18)));
        int panelHeight = Math.max(dp(360), Math.min(dp(610), screenHeight - dp(140)));

        panelParams = new WindowManager.LayoutParams(
                panelWidth,
                panelHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelParams.y = dp(52);
        try {
            wm.addView(root, panelParams);
            panel = root;
            panelVisible = true;
        } catch (Exception e) {
            saveResult("تعذر فتح اللوحة: " + e.getClass().getSimpleName());
        }
    }

    private void confirmSaveAndClose() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(BotnaI18n.translate(this, "حفظ السكربت والخروج"))
                .setMessage(BotnaI18n.translate(this, "سيتم حفظ التعديلات ثم إغلاق لوحة الأتمتة والزر العائم B معًا. هل تريد المتابعة؟"))
                .setPositiveButton(BotnaI18n.translate(this, "حفظ وخروج"), (d, w) -> {
                    BotnaStore.saveScripts(this, BotnaStore.scripts(this));
                    hidePanel();
                    removeView(bubble); bubble = null;
                    report("تم حفظ السكربت وإغلاق اللوحة والزر العائم.");
                })
                .setNegativeButton(BotnaI18n.translate(this, "إلغاء"), (d, w) -> { })
                .create();
        try { if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); dialog.show(); }
        catch (Exception e) { report("تعذر فتح نافذة تأكيد الخروج."); }
    }

    private void rebuildPanel() {
        hidePanel();
        showPanel();
    }

    private void hidePanel() {
        if (panelScrollView != null) {
            panelRestoreY = panelScrollView.getScrollY();
            String key = "panel_scroll_" + (panelScriptId.isEmpty() ? "default" : panelScriptId);
            getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit()
                    .putInt(key, panelRestoreY).apply();
        }
        removeView(panel);
        panel = null;
        panelScrollView = null;
        panelScriptId = "";
        resultLabel = null;
        stepList = null;
        panelVisible = false;
    }

    private void populateSteps(JSONObject script) {
        if (stepList == null) return;
        JSONArray steps = BotnaStore.steps(script);
        if (steps.length() == 0) {
            TextView empty = label("لا توجد خطوات بعد. أضف خطوة من الأزرار أعلاه.", 12, MUTED, false);
            empty.setPadding(dp(7), dp(10), dp(7), dp(10)); stepList.addView(empty); return;
        }
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i); if (step == null) continue;
            final int index = i;
            LinearLayout card = horizontal();
            card.setPadding(dp(8), dp(7), dp(8), dp(7));
            card.setBackground(glassShape(Color.argb(180, 12, 24, 38), Color.argb(105, 35, 226, 244), 1, 13));
            LinearLayout.LayoutParams cardLp = wrap(); cardLp.topMargin = dp(5); stepList.addView(card, cardLp);
            TextView number = label(String.valueOf(i + 1), 13, CYAN, true);
            number.setGravity(Gravity.CENTER); card.addView(number, new LinearLayout.LayoutParams(dp(25), -1));
            ImageView thumb = null;
            JSONArray images = step.optJSONArray("images");
            if (images != null && images.length() > 0) {
                thumb = new ImageView(this);
                Bitmap bmp = readTemplate(images.optString(0));
                if (bmp != null) { thumb.setImageBitmap(Bitmap.createScaledBitmap(bmp, dp(42), dp(42), true)); bmp.recycle(); }
                card.addView(thumb, new LinearLayout.LayoutParams(dp(42), dp(42)));
            }
            String desc = step.optString("label", step.optString("type", "خطوة"));
            if (TYPE_IMAGE.equals(step.optString("type"))) {
                desc += " • صور: " + (images == null ? 0 : images.length()) + " • افتراضي " + step.optInt("threshold", 85) + "%";
                if (images != null && images.length() > 0) desc += " • الزاوية الأولى " + thresholdForTemplate(step, images.optString(0)) + "%";
            }
            TextView item = label(desc, 12, TEXT, false);
            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, -2, 1); itemLp.gravity = Gravity.CENTER_VERTICAL; card.addView(item, itemLp);
            TextView del = label("×", 22, Color.rgb(255, 115, 133), true); del.setGravity(Gravity.CENTER); card.addView(del, new LinearLayout.LayoutParams(dp(30), dp(42)));
            card.setOnClickListener(v -> editStep(index));
            del.setOnClickListener(v -> confirmDeleteStep(index));
            card.setOnLongClickListener(v -> {
                showStepContextMenu(index);
                return true;
            });
        }
    }

    private JSONObject ensureScript() { return BotnaStore.activeScript(this); }
    private JSONObject addStep(String type, String label) {
        JSONObject script = ensureScript(); JSONArray steps = BotnaStore.steps(script);
        JSONObject step = BotnaStore.newStep(type, label); steps.put(step);
        BotnaStore.saveSteps(this, script, steps); return step;
    }

    private void saveStepObject(JSONObject changedStep) {
        if (changedStep == null) return;
        JSONObject script = ensureScript();
        JSONArray steps = BotnaStore.steps(script);
        String id = changedStep.optString("id", "");
        for (int i = 0; i < steps.length(); i++) {
            JSONObject item = steps.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) {
                try { steps.put(i, changedStep); } catch (JSONException ignored) { }
                BotnaStore.saveSteps(this, script, steps);
                return;
            }
        }
    }

    private void confirmDeleteStep(int index) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(BotnaI18n.translate(this, "حذف الخطوة"))
                .setMessage(BotnaI18n.translate(this, "هل تريد حذف هذه الخطوة نهائيًا؟"))
                .setPositiveButton(BotnaI18n.translate(this, "حذف"), (d, w) -> deleteStep(index))
                .setNegativeButton(BotnaI18n.translate(this, "إلغاء"), (d, w) -> { })
                .create();
        try { if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); dialog.show(); }
        catch (Exception e) { report("تعذر فتح تأكيد الحذف."); }
    }

    private void deleteStep(int index) {
        JSONObject script = ensureScript(); JSONArray old = BotnaStore.steps(script); JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) if (i != index) next.put(old.optJSONObject(i));
        BotnaStore.saveSteps(this, script, next); rebuildPanel();
        report("تم حذف الخطوة.");
    }

    private void showStepContextMenu(int index) {
        JSONObject script = ensureScript();
        JSONArray steps = BotnaStore.steps(script);
        JSONObject step = steps.optJSONObject(index);
        if (step == null) return;
        String[] options = copiedStep == null
                ? new String[]{"إعدادات الخطوة", "اختبار الخطوة", "نسخ الخطوة", "قص الخطوة", "تحريك", "حذف الخطوة"}
                : new String[]{"إعدادات الخطوة", "اختبار الخطوة", "نسخ الخطوة", "قص الخطوة", "تحريك", "اللصق فوق هذه الخطوة", "حذف الخطوة"};
        showChoice("الخطوة " + (index + 1), options, choice -> {
            String selected = options[choice];
            if ("إعدادات الخطوة".equals(selected)) editStep(index);
            else if ("اختبار الخطوة".equals(selected)) testStep(index);
            else if ("نسخ الخطوة".equals(selected)) copyStep(index, false);
            else if ("قص الخطوة".equals(selected)) copyStep(index, true);
            else if ("تحريك".equals(selected)) chooseMoveDestination(index);
            else if ("اللصق فوق هذه الخطوة".equals(selected)) pasteStepAbove(index);
            else if ("حذف الخطوة".equals(selected)) confirmDeleteStep(index);
        });
    }

    private void copyStep(int index, boolean cut) {
        JSONArray steps = BotnaStore.steps(ensureScript());
        JSONObject step = steps.optJSONObject(index);
        if (step == null) return;
        try {
            copiedStep = new JSONObject(step.toString());
            copiedStepWasCut = cut;
        } catch (JSONException e) {
            report("تعذر نسخ الخطوة."); return;
        }
        if (cut) {
            JSONObject script = ensureScript(); JSONArray next = new JSONArray();
            for (int i = 0; i < steps.length(); i++) if (i != index) next.put(steps.optJSONObject(i));
            BotnaStore.saveSteps(this, script, next);
            report("تم قص الخطوة. اضغط مطولًا على موضع الإدراج ثم اختر اللصق.");
            rebuildPanel();
        } else {
            report("تم نسخ الخطوة. اضغط مطولًا على الموضع المطلوب واختر اللصق فوق هذه الخطوة.");
        }
    }

    private void pasteStepAbove(int index) {
        if (copiedStep == null) { report("لا توجد خطوة منسوخة."); return; }
        JSONObject script = ensureScript(); JSONArray old = BotnaStore.steps(script); JSONArray next = new JSONArray();
        JSONObject clone;
        try { clone = new JSONObject(copiedStep.toString()); }
        catch (JSONException e) { report("تعذر لصق الخطوة."); return; }
        putString(clone, "id", "p" + System.nanoTime());
        for (int i = 0; i < old.length(); i++) {
            if (i == index) next.put(clone);
            next.put(old.optJSONObject(i));
        }
        if (index >= old.length()) next.put(clone);
        BotnaStore.saveSteps(this, script, next);
        if (copiedStepWasCut) { copiedStep = null; copiedStepWasCut = false; }
        report("تم لصق الخطوة فوق الموضع المحدد.");
        rebuildPanel();
    }

    private void chooseMoveDestination(int from) {
        JSONArray steps = BotnaStore.steps(ensureScript());
        if (steps.length() < 2) { report("لا يوجد موضع آخر لنقل الخطوة إليه."); return; }
        String[] destinations = new String[steps.length() + 1];
        for (int i = 0; i < steps.length(); i++) destinations[i] = "قبل الخطوة " + (i + 1);
        destinations[steps.length()] = "بعد جميع الخطوات";
        showChoice("حدد مكان الخطوة", destinations, which -> moveStepToPosition(from, which));
    }

    private void moveStepToPosition(int from, int position) {
        JSONObject script = ensureScript(); JSONArray old = BotnaStore.steps(script);
        if (from < 0 || from >= old.length()) return;
        List<JSONObject> items = new ArrayList<>();
        for (int i = 0; i < old.length(); i++) items.add(old.optJSONObject(i));
        JSONObject moving = items.remove(from);
        if (position > from) position--;
        int target = Math.max(0, Math.min(position, items.size()));
        items.add(target, moving);
        JSONArray next = new JSONArray(); for (JSONObject item : items) next.put(item);
        BotnaStore.saveSteps(this, script, next);
        report("تم نقل الخطوة وحفظ ترتيبها.");
        rebuildPanel();
    }

    private void reorderStep(int from, int to) {
        if (from == to) return;
        JSONObject script = ensureScript(); JSONArray old = BotnaStore.steps(script);
        if (from < 0 || to < 0 || from >= old.length() || to >= old.length()) return;
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < old.length(); i++) list.add(old.optJSONObject(i));
        JSONObject moved = list.remove(from); list.add(to, moved);
        JSONArray next = new JSONArray(); for (JSONObject item : list) next.put(item);
        BotnaStore.saveSteps(this, script, next); rebuildPanel();
        report("تم تغيير ترتيب الخطوة.");
    }

    private void editStep(int index) {
        JSONObject script = ensureScript(); JSONArray steps = BotnaStore.steps(script);
        JSONObject step = steps.optJSONObject(index); if (step == null) return;
        String type = step.optString("type");
        if (TYPE_CUSTOM.equals(type)) {
            showChoice("إعدادات الخطوة المخصصة", new String[]{"كتابة/تعديل الأمر العربي", "مكتبة الصور", "إعدادات الذكاء الاصطناعي", "عرض الأوامر المترجمة", "اختبار الخطوة", step.optBoolean("enabled", true) ? "تعطيل الخطوة" : "تفعيل الخطوة", "حذف الخطوة"}, choice -> {
                if (choice == 0) editCustomInstructions(step);
                else if (choice == 1) showImageLibrary();
                else if (choice == 2) BotnaAICompiler.showSettingsDialog(this, true, () -> editCustomInstructions(findStepById(ensureScript(), step.optString("id"))));
                else if (choice == 3) previewCustomCompilation(step);
                else if (choice == 4) testStep(index);
                else if (choice == 5) toggleStep(script, step);
                else confirmDeleteStep(index);
            });
            return;
        }
        if (TYPE_IMAGE.equals(type)) {
            String[] options = new String[]{
                    "عرض الزوايا المحفوظة", "تغيير الهدف الحالي", "قص زاوية إضافية لهذه الخطوة",
                    "تعديل نسبة التطابق", "منطقة البحث", "إعدادات المطابقة المتقدمة", "اختبار الخطوة الآن",
                    step.optBoolean("enabled", true) ? "تعطيل الخطوة" : "تفعيل الخطوة", "حذف الخطوة"
            };
            showChoice("إعدادات خطوة الصورة", options, choice -> {
                if (choice == 0) showImageAngles(script, step, index);
                else if (choice == 1) changeImageTarget(script, step);
                else if (choice == 2) prepareCropAngle(step);
                else if (choice == 3) promptNumber("نسبة التطابق (0–100)", step.optInt("threshold", 85), 0, 100, value -> {
                    putInt(step, "threshold", value); BotnaStore.saveSteps(this, script, steps); rebuildPanel();
                });
                else if (choice == 4) editSearchRegion(script, step);
                else if (choice == 5) editImageAdvancedSettings(script, step, steps);
                else if (choice == 6) testStep(index);
                else if (choice == 7) { putBoolean(step, "enabled", !step.optBoolean("enabled", true)); BotnaStore.saveSteps(this, script, steps); rebuildPanel(); }
                else confirmDeleteStep(index);
            });
            return;
        }
        if (TYPE_TEXT.equals(type)) {
            showChoice("إعدادات خطوة النقر على النص", new String[]{"تغيير النص المستهدف", "عدد المحاولات", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0) promptText("تعديل النص المستهدف", step.optString("value"), value -> {
                    putString(step, "value", value); putString(step, "label", "نقر نص: " + value); BotnaStore.saveSteps(this, script, steps); rebuildPanel();
                });
                else if (choice == 1) promptNumber("عدد المحاولات (أي عدد صحيح موجب)", step.optInt("retries", 1), 1, Integer.MAX_VALUE, value -> saveStepInt(script, step, "retries", value));
                else if (choice == 2) promptNumber("انتظار قبل الخطوة (مللي ثانية)", step.optInt("beforeDelay", 0), 0, 60000, value -> saveStepInt(script, step, "beforeDelay", value));
                else if (choice == 3) promptNumber("انتظار بعد التنفيذ (مللي ثانية)", step.optInt("afterDelay", 0), 0, 60000, value -> saveStepInt(script, step, "afterDelay", value));
                else if (choice == 4) testStep(index);
                else if (choice == 5) toggleStep(script, step);
                else confirmDeleteStep(index);
            });
        } else if (TYPE_WAIT.equals(type)) {
            showChoice("إعدادات الانتظار", new String[]{"تعديل مدة الانتظار", "تبديل عشوائي/ثابت", "تعديل نطاق الانتظار العشوائي", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0) promptNumber("مدة الانتظار بالمللي ثانية", step.optInt("delay", 500), 0, 60000, value -> {
                    putInt(step, "delay", value); putBoolean(step, "randomDelay", false); putString(step, "label", "انتظار " + value + " ms"); BotnaStore.saveSteps(this, script, steps); rebuildPanel();
                });
                else if (choice == 1) { putBoolean(step, "randomDelay", !step.optBoolean("randomDelay", false)); BotnaStore.saveSteps(this, script, steps); report("تم تحديث وضع الانتظار."); rebuildPanel(); }
                else if (choice == 2) promptText("أدنى وأقصى مدة (min,max)", step.optInt("minDelay", 500) + "," + step.optInt("maxDelay", 1200), value -> {
                    String[] bounds = value.split(",");
                    try { if (bounds.length != 2) throw new NumberFormatException(); int min = Math.max(0, Math.min(60000, Integer.parseInt(bounds[0].trim()))); int max = Math.max(min, Math.min(60000, Integer.parseInt(bounds[1].trim()))); putInt(step, "minDelay", min); putInt(step, "maxDelay", max); putBoolean(step, "randomDelay", true); BotnaStore.saveSteps(this, script, steps); rebuildPanel(); }
                    catch (NumberFormatException ex) { report("أدخل قيمتين صحيحتين مثل 500,1200."); }
                });
                else if (choice == 3) testStep(index);
                else if (choice == 4) toggleStep(script, step);
                else confirmDeleteStep(index);
            });
        } else if (TYPE_TAP.equals(type)) {
            showChoice("إعدادات النقر بالموقع", new String[]{"تحديد النقطة على الشاشة", "نسخ الإحداثيات الحالية", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0) openPointSelector(step);
                else if (choice == 1) copyCoordinates(step.optInt("xPercent", 50), step.optInt("yPercent", 50));
                else if (choice == 2) promptNumber("انتظار قبل الخطوة (مللي ثانية)", step.optInt("beforeDelay", 0), 0, 60000, value -> saveStepInt(script, step, "beforeDelay", value));
                else if (choice == 3) promptNumber("انتظار بعد التنفيذ (مللي ثانية)", step.optInt("afterDelay", 0), 0, 60000, value -> saveStepInt(script, step, "afterDelay", value));
                else if (choice == 4) testStep(index);
                else if (choice == 5) toggleStep(script, step);
                else confirmDeleteStep(index);
            });
        } else if (TYPE_LONG_PRESS.equals(type)) {
            showChoice("إعدادات الضغط المطوّل", new String[]{"تغيير الإحداثيات", "مدة الضغط", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if(choice==0) promptText("x,y كنسب مئوية من الشاشة",step.optInt("xPercent",50)+","+step.optInt("yPercent",50),value->{
                    String[] xy=value.split(","); try { if(xy.length!=2)throw new NumberFormatException(); putInt(step,"xPercent",Math.max(0,Math.min(100,Integer.parseInt(xy[0].trim())))); putInt(step,"yPercent",Math.max(0,Math.min(100,Integer.parseInt(xy[1].trim())))); BotnaStore.saveSteps(this,script,steps); rebuildPanel(); }
                    catch(NumberFormatException ex){report("أدخل إحداثيين صحيحين مثل 50,50.");}
                });
                else if(choice==1) promptNumber("مدة الضغط بالمللي ثانية",step.optInt("duration",800),300,5000,value->saveStepInt(script,step,"duration",value));
                else if(choice==2) promptNumber("انتظار قبل التنفيذ (مللي ثانية)",step.optInt("beforeDelay",0),0,60000,value->saveStepInt(script,step,"beforeDelay",value));
                else if(choice==3) promptNumber("انتظار بعد التنفيذ (مللي ثانية)",step.optInt("afterDelay",0),0,60000,value->saveStepInt(script,step,"afterDelay",value));
                else if(choice==4)testStep(index); else if(choice==5)toggleStep(script,step); else confirmDeleteStep(index);
            });
        } else if (TYPE_SWIPE.equals(type) || TYPE_SCROLL.equals(type)) {
            showChoice("إعدادات حركة الشاشة", new String[]{"تعديل الاتجاه/المسار", "تعديل مدة الحركة", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0 && TYPE_SWIPE.equals(type)) promptText("x1,y1,x2,y2 كنسب مئوية", step.optInt("x1", 50)+","+step.optInt("y1",75)+","+step.optInt("x2",50)+","+step.optInt("y2",25), value -> {
                    String[] a=value.split(","); try { if(a.length!=4)throw new NumberFormatException(); String[] keys={"x1","y1","x2","y2"}; for(int j=0;j<4;j++)putInt(step,keys[j],Math.max(0,Math.min(100,Integer.parseInt(a[j].trim())))); BotnaStore.saveSteps(this,script,steps); rebuildPanel(); } catch(NumberFormatException ex){report("أدخل أربع قيم صحيحة.");}
                });
                else if (choice == 0) showChoice("اتجاه التمرير", new String[]{"للأسفل", "للأعلى", "لليمين", "لليسار"}, dir -> { putString(step,"direction",new String[]{"down","up","right","left"}[dir]); BotnaStore.saveSteps(this,script,steps); rebuildPanel(); });
                else if (choice == 1) promptNumber("مدة الحركة بالمللي ثانية", step.optInt("duration", 450), 100, 5000, value -> saveStepInt(script, step, "duration", value));
                else if (choice == 2) promptNumber("انتظار قبل التنفيذ (مللي ثانية)", step.optInt("beforeDelay", 0), 0, 60000, value -> saveStepInt(script, step, "beforeDelay", value));
                else if (choice == 3) promptNumber("انتظار بعد التنفيذ (مللي ثانية)", step.optInt("afterDelay", 0), 0, 60000, value -> saveStepInt(script, step, "afterDelay", value));
                else if (choice == 4) testStep(index);
                else if (choice == 5) toggleStep(script, step);
                else confirmDeleteStep(index);
            });
        } else if (TYPE_TYPE_TEXT.equals(type)) {
            showChoice("إعدادات كتابة النص", new String[]{"تعديل النص", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if(choice==0) promptText("النص الذي سيُكتب",step.optString("value"),value->{putString(step,"value",value);putString(step,"label","كتابة نص: "+value);BotnaStore.saveSteps(this,script,steps);rebuildPanel();});
                else if(choice==1) promptNumber("انتظار قبل التنفيذ (مللي ثانية)",step.optInt("beforeDelay",0),0,60000,value->saveStepInt(script,step,"beforeDelay",value));
                else if(choice==2) promptNumber("انتظار بعد التنفيذ (مللي ثانية)",step.optInt("afterDelay",0),0,60000,value->saveStepInt(script,step,"afterDelay",value));
                else if(choice==3)testStep(index); else if(choice==4)toggleStep(script,step); else confirmDeleteStep(index);
            });
        } else if (TYPE_LAUNCH_APP.equals(type)) {
            showChoice("إعدادات فتح التطبيق", new String[]{"تغيير اسم التطبيق", "اختبار فتح التطبيق الآن", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0) promptText("اسم التطبيق أو اسم الحزمة", step.optString("appName", ""), value -> {
                    if (value.trim().isEmpty()) { report("اسم التطبيق لا يمكن أن يكون فارغًا."); return; }
                    putString(step, "appName", value.trim()); putString(step, "label", "فتح تطبيق: " + value.trim());
                    BotnaStore.saveSteps(this, script, steps); rebuildPanel();
                });
                else if (choice == 1) testStep(index);
                else if (choice == 2) promptNumber("انتظار قبل التنفيذ (مللي ثانية)", step.optInt("beforeDelay", 0), 0, 60000, value -> saveStepInt(script, step, "beforeDelay", value));
                else if (choice == 3) promptNumber("انتظار بعد التنفيذ (مللي ثانية)", step.optInt("afterDelay", 0), 0, 60000, value -> saveStepInt(script, step, "afterDelay", value));
                else if (choice == 4) toggleStep(script, step);
                else confirmDeleteStep(index);
            });
        } else if (TYPE_CHECK_INTERNET.equals(type) || TYPE_WAIT_INTERNET.equals(type)) {
            showChoice("إعدادات فحص الإنترنت", new String[]{"عدد المحاولات", "الفاصل بين المحاولات", "رابط اختبار مخصص", "اختبار اتصال واحد الآن", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0) promptNumber("عدد المحاولات (أي عدد صحيح موجب)", step.optInt("attempts", TYPE_WAIT_INTERNET.equals(type) ? 120 : 3), 1, Integer.MAX_VALUE, value -> saveStepInt(script, step, "attempts", value));
                else if (choice == 1) promptNumber("الفاصل بين المحاولات بالمللي ثانية", step.optInt("intervalMs", 5000), 250, 60000, value -> saveStepInt(script, step, "intervalMs", value));
                else if (choice == 2) promptText("رابط اختبار HTTP/HTTPS؛ اتركه فارغًا لاستخدام فحص اتصال الإنترنت الافتراضي", step.optString("testUrl", ""), value -> {
                    String url = value.trim(); if (!url.isEmpty() && !url.startsWith("http://") && !url.startsWith("https://")) { report("يجب أن يبدأ الرابط بـ http:// أو https://"); return; }
                    if (url.isEmpty()) step.remove("testUrl"); else putString(step, "testUrl", url); BotnaStore.saveSteps(this, script, steps); rebuildPanel();
                });
                else if (choice == 3) { probeInternetAsync(step.optString("testUrl", ""), step.optInt("timeoutMs", 4000), ok -> report(ok ? "نجح اختبار اتصال الإنترنت." : "لم يثبت اتصال الإنترنت. تحقق من الشبكة أو صفحة تسجيل الدخول.")); }
                else if (choice == 4) toggleStep(script, step); else confirmDeleteStep(index);
            });
        } else if (TYPE_WAIT_TEXT.equals(type) || TYPE_WAIT_INPUT.equals(type)) {
            showChoice(TYPE_WAIT_INPUT.equals(type) ? "إعدادات انتظار حقل الإدخال" : "إعدادات انتظار النص", TYPE_WAIT_INPUT.equals(type)
                    ? new String[]{"عدد المحاولات", "الفاصل بين المحاولات", "اختبار الآن", "تفعيل/تعطيل", "حذف"}
                    : new String[]{"تغيير النص المنتظر", "عدد المحاولات", "الفاصل بين المحاولات", "اختبار الآن", "تفعيل/تعطيل", "حذف"}, choice -> {
                if(TYPE_WAIT_INPUT.equals(type) && choice==0) promptNumber("عدد المحاولات (أي عدد صحيح موجب)",step.optInt("attempts",20),1,Integer.MAX_VALUE,v->saveStepInt(script,step,"attempts",v));
                else if(TYPE_WAIT_INPUT.equals(type) && choice==1) promptNumber("الفاصل بالمللي ثانية",step.optInt("intervalMs",500),250,60000,v->saveStepInt(script,step,"intervalMs",v));
                else if(TYPE_WAIT_INPUT.equals(type) && choice==2) { boolean found=hasEditableInputOnScreen(); report(found?"ظهر حقل إدخال قابل للكتابة.":"لم يظهر حقل إدخال قابل للكتابة."); }
                else if(TYPE_WAIT_INPUT.equals(type) && choice==3) toggleStep(script,step);
                else if(TYPE_WAIT_INPUT.equals(type) && choice==4) confirmDeleteStep(index);
                else if(!TYPE_WAIT_INPUT.equals(type) && choice==0) promptText("النص المنتظر",step.optString("value",""),v->{putString(step,"value",v);putString(step,"label","انتظار ظهور النص: "+v);BotnaStore.saveSteps(this,script,steps);rebuildPanel();});
                else if(!TYPE_WAIT_INPUT.equals(type) && choice==1) promptNumber("عدد المحاولات (أي عدد صحيح موجب)",step.optInt("attempts",20),1,Integer.MAX_VALUE,v->saveStepInt(script,step,"attempts",v));
                else if(!TYPE_WAIT_INPUT.equals(type) && choice==2) promptNumber("الفاصل بالمللي ثانية",step.optInt("intervalMs",500),250,60000,v->saveStepInt(script,step,"intervalMs",v));
                else if(!TYPE_WAIT_INPUT.equals(type) && choice==3) { boolean found=containsTextOnScreen(step.optString("value","")); report(found?"النص المنتظر ظاهر حاليًا.":"لم يظهر النص المنتظر في شجرة إمكانية الوصول."); }
                else if(!TYPE_WAIT_INPUT.equals(type) && choice==4) toggleStep(script,step); else if(!TYPE_WAIT_INPUT.equals(type) && choice==5) confirmDeleteStep(index);
            });
        } else if (TYPE_OPEN_URL.equals(type)) {
            showChoice("إعدادات فتح الرابط", new String[]{"تغيير الرابط", "فتح الرابط الآن", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0) promptText("رابط HTTP/HTTPS", step.optString("url", "https://example.com"), value -> {
                    String url=value.trim(); if(!url.startsWith("http://")&&!url.startsWith("https://")){report("يجب أن يبدأ الرابط بـ http:// أو https://");return;} putString(step,"url",url);putString(step,"label","فتح رابط: "+url);BotnaStore.saveSteps(this,script,steps);rebuildPanel();
                }); else if(choice==1) { boolean ok=openHttpUrl(step.optString("url","")); report(ok?"تم فتح الرابط في تطبيق خارجي.":"تعذر فتح الرابط؛ تحقق من صحته ووجود متصفح."); }
                else if(choice==2) promptNumber("انتظار قبل التنفيذ (مللي ثانية)",step.optInt("beforeDelay",0),0,60000,v->saveStepInt(script,step,"beforeDelay",v));
                else if(choice==3) promptNumber("انتظار بعد التنفيذ (مللي ثانية)",step.optInt("afterDelay",0),0,60000,v->saveStepInt(script,step,"afterDelay",v));
                else if(choice==4) toggleStep(script,step); else confirmDeleteStep(index);
            });
        } else if (TYPE_NOTIFY.equals(type)) {
            showChoice("إعدادات التنبيه", new String[]{"تعديل نص الرسالة", "تشغيل/إيقاف النغمة", "اختبار التنبيه", "تفعيل/تعطيل", "حذف"}, choice -> {
                if(choice==0) promptText("رسالة التنبيه",step.optString("message","تم تنفيذ الأمر بنجاح"),v->{putString(step,"message",v);putString(step,"label","تنبيه: "+v);BotnaStore.saveSteps(this,script,steps);rebuildPanel();});
                else if(choice==1){putBoolean(step,"sound",!step.optBoolean("sound",true));BotnaStore.saveSteps(this,script,steps);rebuildPanel();}
                else if(choice==2){notifyUser(step.optString("message","تم تنفيذ الأمر بنجاح"),step.optBoolean("sound",true));report("تم اختبار التنبيه.");}
                else if(choice==3)toggleStep(script,step);else confirmDeleteStep(index);
            });
        } else if (TYPE_PRESS_ENTER.equals(type)) {
            showChoice("إعدادات Enter/بحث", new String[]{"اختبار الآن", "تفعيل/تعطيل", "حذف"}, choice -> { if(choice==0){boolean ok=performImeEnter();report(ok?"تم إرسال أمر Enter/بحث.":"لم يقبل حقل الإدخال أمر Enter. جرّب خطوة نقر على زر بحث ظاهر.");}else if(choice==1)toggleStep(script,step);else confirmDeleteStep(index); });
        } else if (TYPE_GLOBAL_ACTION.equals(type)) {
            showChoice("إعدادات إجراء النظام", new String[]{"تغيير إجراء النظام", "اختبار الخطوة", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0) showChoice("اختر إجراء النظام", new String[]{"فتح الإشعارات", "الإعدادات السريعة", "التطبيقات الأخيرة", "إغلاق الإشعارات"}, selected -> {
                    String[] values={"notifications","quick_settings","recent_apps","dismiss_notifications"};
                    String[] labels={"فتح لوحة الإشعارات","فتح الإعدادات السريعة","فتح التطبيقات الأخيرة","إغلاق لوحة الإشعارات"};
                    putString(step,"systemAction",values[selected]); putString(step,"label",labels[selected]); BotnaStore.saveSteps(this,script,steps); rebuildPanel();
                });
                else if (choice == 1) testStep(index);
                else if (choice == 2) promptNumber("انتظار قبل التنفيذ (مللي ثانية)",step.optInt("beforeDelay",0),0,60000,v->saveStepInt(script,step,"beforeDelay",v));
                else if (choice == 3) promptNumber("انتظار بعد التنفيذ (مللي ثانية)",step.optInt("afterDelay",0),0,60000,v->saveStepInt(script,step,"afterDelay",v));
                else if (choice == 4) toggleStep(script,step); else confirmDeleteStep(index);
            });
        } else if (TYPE_CLOSE_RECENTS.equals(type)) {
            showChoice("إعداد إغلاق التطبيقات الأخيرة", new String[]{"اختبار الآن", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "تفعيل/تعطيل", "حذف"}, choice -> {
                if(choice==0)testStep(index);
                else if(choice==1)promptNumber("انتظار قبل التنفيذ (ms)",step.optInt("beforeDelay",0),0,60000,v->saveStepInt(script,step,"beforeDelay",v));
                else if(choice==2)promptNumber("انتظار بعد التنفيذ (ms)",step.optInt("afterDelay",0),0,60000,v->saveStepInt(script,step,"afterDelay",v));
                else if(choice==3)toggleStep(script,step);else confirmDeleteStep(index);
            });
        } else if (TYPE_CLEAR_TEXT.equals(type)) {
            showChoice("إعداد مسح حقل الإدخال", new String[]{"اختبار الآن", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "تفعيل/تعطيل", "حذف"}, choice -> {
                if(choice==0){boolean ok=clearFocusedField();report(ok?"تم مسح النص من حقل الإدخال المركّز.":"لم يمكن مسح النص؛ انقر حقلًا قابلًا للتحرير أولًا.");}
                else if(choice==1)promptNumber("انتظار قبل التنفيذ (مللي ثانية)",step.optInt("beforeDelay",0),0,60000,v->saveStepInt(script,step,"beforeDelay",v));
                else if(choice==2)promptNumber("انتظار بعد التنفيذ (مللي ثانية)",step.optInt("afterDelay",0),0,60000,v->saveStepInt(script,step,"afterDelay",v));
                else if(choice==3)toggleStep(script,step);else confirmDeleteStep(index);
            });
        } else if (TYPE_BACK.equals(type) || TYPE_HOME.equals(type)) {
            showChoice("إعدادات خطوة النظام", new String[]{"انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if(choice==0) promptNumber("انتظار قبل التنفيذ (مللي ثانية)",step.optInt("beforeDelay",0),0,60000,value->saveStepInt(script,step,"beforeDelay",value));
                else if(choice==1) promptNumber("انتظار بعد التنفيذ (مللي ثانية)",step.optInt("afterDelay",0),0,60000,value->saveStepInt(script,step,"afterDelay",value));
                else if(choice==2)testStep(index); else if(choice==3)toggleStep(script,step); else confirmDeleteStep(index);
            });
        } else if (TYPE_REPEAT_START.equals(type)) {
            showChoice("إعدادات التكرار", new String[]{"عدد التكرارات", "اختبار إعداد التكرار", "تفعيل/تعطيل مجموعة التكرار", "حذف بداية التكرار"}, choice -> {
                if (choice == 0) promptNumber("عدد مرات تكرار المجموعة (أي عدد صحيح موجب)", step.optInt("repeatCount", 2), 1, Integer.MAX_VALUE, value -> {
                    putInt(step, "repeatCount", value); putString(step, "label", "بدء تكرار ×" + value); BotnaStore.saveSteps(this, script, steps); rebuildPanel();
                });
                else if (choice == 1) testStep(index);
                else if (choice == 2) toggleStep(script, step);
                else confirmDeleteStep(index);
            });
        } else if (TYPE_IF_SUCCESS.equals(type) || TYPE_IF_FAIL.equals(type)) {
            showChoice("إعدادات الشرط", new String[]{"تبديل شرط النجاح/الفشل", "تفعيل/تعطيل الكتلة الشرطية", "اختبار الشرط", "حذف"}, choice -> {
                if(choice==0) { String next=TYPE_IF_SUCCESS.equals(type)?TYPE_IF_FAIL:TYPE_IF_SUCCESS; putString(step,"type",next); putString(step,"label",TYPE_IF_SUCCESS.equals(next)?"إذا نجح الإجراء السابق":"إذا فشل الإجراء السابق"); BotnaStore.saveSteps(this,script,steps); rebuildPanel(); }
                else if(choice==1)toggleStep(script,step); else if(choice==2)testStep(index); else confirmDeleteStep(index);
            });
        } else {
            showChoice("إعدادات العلامة التنظيمية", new String[]{"فحص العلامة", "حذف العلامة"}, choice -> {
                if (choice == 0) testStep(index);
                else confirmDeleteStep(index);
            });
        }
    }

    private void saveStepInt(JSONObject script, JSONObject step, String key, int value) {
        putInt(step, key, value); BotnaStore.saveSteps(this, script, BotnaStore.steps(script)); rebuildPanel();
    }

    private void addGlobalActionStep(String action, String label) {
        JSONObject step = addStep(TYPE_GLOBAL_ACTION, label); putString(step, "systemAction", action); saveStepObject(step); rebuildPanel();
    }

    private void addGlobalActionToArray(JSONArray target, String action, String label) {
        JSONObject step = compiledStep(TYPE_GLOBAL_ACTION, label); putString(step, "systemAction", action); target.put(step);
    }

    private interface BooleanCallback { void done(boolean success); }

    private void closeRecentAppsAsync(BooleanCallback callback) {
        if (!performGlobalAction(GLOBAL_ACTION_RECENTS)) { callback.done(false); return; }

        handler.postDelayed(() -> callback.done(clickAnyTextOnScreen("إغلاق الكل", "إغلاق جميع التطبيقات", "مسح الكل", "Close all", "Clear all", "Close all apps")), 850);
    }

    private boolean clickAnyTextOnScreen(String... labels) {
        for (String text : labels) if (clickText(text)) return true;
        return false;
    }

    private boolean performSystemAction(String action) {
        if ("notifications".equals(action)) return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
        if ("quick_settings".equals(action)) return performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
        if ("recent_apps".equals(action)) return performGlobalAction(GLOBAL_ACTION_RECENTS);
        if ("dismiss_notifications".equals(action)) {
            return Build.VERSION.SDK_INT >= 31 && performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE);
        }
        return false;
    }

    private boolean clearFocusedField() {
        AccessibilityNodeInfo root = findTargetRoot();
        if (root == null) return false;
        AccessibilityNodeInfo focused = null;
        try {

            focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null || !focused.isEditable() || !focused.isEnabled()) return false;
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true;
            CharSequence text = focused.getText();
            if (text == null || text.length() == 0) return true;
            return false;
        } finally {
            if (focused != null) try { focused.recycle(); } catch (Exception ignored) { }
            try { root.recycle(); } catch (Exception ignored) { }
        }
    }

    private void toggleStep(JSONObject script, JSONObject step) {
        putBoolean(step, "enabled", !step.optBoolean("enabled", true));
        BotnaStore.saveSteps(this, script, BotnaStore.steps(script)); rebuildPanel();
    }

    private void changeImageTarget(JSONObject script, JSONObject step) {

        cropStepId = step.optString("id");
        cropCreatedNewStep = false;
        cropPurpose = CROP_TARGET;
        replaceImageTarget = true;
        beginCaptureForCrop();
    }

    private void editImageAdvancedSettings(JSONObject script, JSONObject step, JSONArray steps) {
        showChoice("إعدادات المطابقة المتقدمة", new String[]{
                step.optBoolean("tapOnMatch", true) ? "عدم الضغط بعد العثور على الصورة" : "الضغط بعد العثور على الصورة",
                "عدد محاولات المطابقة (أي عدد صحيح موجب)", "فاصل إعادة المحاولة", "انتظار بعد المطابقة/الضغط",
                "انتظار قبل تنفيذ الخطوة", "تعطيل/تفعيل الخطوة"
        }, choice -> {
            if (choice == 0) { putBoolean(step, "tapOnMatch", !step.optBoolean("tapOnMatch", true)); BotnaStore.saveSteps(this, script, steps); rebuildPanel(); }
            else if (choice == 1) promptNumber("عدد محاولات المطابقة (أي عدد صحيح موجب)", step.optInt("retries", 1), 1, Integer.MAX_VALUE, value -> saveStepInt(script, step, "retries", value));
            else if (choice == 2) promptNumber("فاصل إعادة المحاولة بالمللي ثانية", step.optInt("retryDelay", 500), 100, 10000, value -> saveStepInt(script, step, "retryDelay", value));
            else if (choice == 3) promptNumber("انتظار بعد المطابقة بالمللي ثانية", step.optInt("afterDelay", 0), 0, 60000, value -> saveStepInt(script, step, "afterDelay", value));
            else if (choice == 4) promptNumber("انتظار قبل تنفيذ الخطوة بالمللي ثانية", step.optInt("beforeDelay", 0), 0, 60000, value -> saveStepInt(script, step, "beforeDelay", value));
            else toggleStep(script, step);
        });
    }

    private AlertDialog imageAnglesDialog;

    private void showImageAngles(JSONObject script, JSONObject step, int stepIndex) {
        JSONArray images = step.optJSONArray("images");
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(8), dp(6), dp(8), dp(6));
        ScrollView scroller = new ScrollView(this); scroller.addView(list);
        List<Bitmap> loaded = new ArrayList<>();
        if (images == null || images.length() == 0) {
            list.addView(label("لا توجد زوايا محفوظة في هذه الخطوة. أضف زاوية أو قص هدفًا مستقلًا.", 14, MUTED, false));
        } else {
            for (int i = 0; i < images.length(); i++) {
                final int angleIndex = i;
                final String filename = images.optString(i);
                JSONObject angle = findImageAngleSettings(step, filename);
                int threshold = angle == null ? step.optInt("threshold", 85) : angle.optInt("threshold", step.optInt("threshold", 85));
                int retries = angle == null ? step.optInt("retries", 1) : angle.optInt("retries", step.optInt("retries", 1));
                boolean enabled = angle == null || angle.optBoolean("enabled", true);
                LinearLayout row = horizontal(); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(4), dp(5), dp(4), dp(5));
                row.setBackground(glassShape(Color.argb(100, 8, 17, 28), Color.argb(95, Color.red(CYAN), Color.green(CYAN), Color.blue(CYAN)), 1, 10));
                ImageView preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap bitmap = readTemplate(filename);
                if (bitmap != null) {
                    loaded.add(bitmap);
                    Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, dp(58), dp(48), true);
                    if (previewBitmap != bitmap) loaded.add(previewBitmap);
                    preview.setImageBitmap(previewBitmap);
                }
                row.addView(preview, new LinearLayout.LayoutParams(dp(58), dp(48)));
                String angleText = "زاوية " + (i + 1) + "\nتطابق " + threshold + "% • محاولات " + retries + (enabled ? "" : " • معطلة");
                TextView angleLabel = label(angleText, 11, enabled ? TEXT : MUTED, false);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1); labelLp.setMargins(dp(5), 0, dp(5), 0); row.addView(angleLabel, labelLp);
                Button settings = button("⚙", false); settings.setTextSize(12); settings.setContentDescription("إعدادات الزاوية " + (angleIndex + 1));
                row.addView(settings, new LinearLayout.LayoutParams(dp(36), dp(34)));
                Button remove = button("×", false); remove.setTextSize(16); remove.setContentDescription("حذف الزاوية " + (angleIndex + 1));
                row.addView(remove, new LinearLayout.LayoutParams(dp(30), dp(34)));
                settings.setOnClickListener(v -> editImageAngleSettings(script, step, filename, angleIndex, stepIndex));
                remove.setOnClickListener(v -> {
                    AlertDialog confirm = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "حذف الزاوية"))
                            .setMessage("هل تريد حذف الزاوية " + (angleIndex + 1) + " من خطوة الصورة؟")
                            .setPositiveButton(BotnaI18n.translate(this, "حذف"), (d, w) -> {
                                JSONArray current = step.optJSONArray("images"); JSONArray next = new JSONArray();
                                if (current != null) for (int j = 0; j < current.length(); j++) if (j != angleIndex) next.put(current.optString(j));
                                try { step.put("images", next); } catch (JSONException ignored) { }
                                removeImageAngleSettings(step, filename);
                                BotnaStore.saveSteps(this, script, BotnaStore.steps(script));
                                report("تم حذف الزاوية وإعداداتها.");
                                if (imageAnglesDialog != null && imageAnglesDialog.isShowing()) imageAnglesDialog.dismiss();
                                showImageAngles(script, step, stepIndex);
                            }).setNegativeButton(BotnaI18n.translate(this, "إلغاء"), (d, w) -> { }).create();
                    try { if (confirm.getWindow() != null) confirm.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); confirm.show(); }
                    catch (Exception e) { report("تعذر فتح تأكيد حذف الزاوية."); }
                });
                list.addView(row);
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "زوايا الصورة وإعدادات كل زاوية"))
                .setView(scroller).setPositiveButton(BotnaI18n.translate(this, "إغلاق"), (d,w)->{}).create();
        imageAnglesDialog = dialog;
        dialog.setOnDismissListener(d -> { for (Bitmap b : loaded) if (b != null && !b.isRecycled()) b.recycle(); });
        try { if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); dialog.show(); }
        catch (Exception e) { report("تعذر عرض زوايا الصورة."); }
    }

    private JSONObject findImageAngleSettings(JSONObject step, String filename) {
        JSONArray settings = step == null ? null : step.optJSONArray("imageSettings");
        if (settings == null || filename == null) return null;
        for (int i = 0; i < settings.length(); i++) {
            JSONObject item = settings.optJSONObject(i);
            if (item != null && filename.equals(item.optString("fileName", ""))) return item;
        }
        return null;
    }

    private JSONObject ensureImageAngleSettings(JSONObject step, String filename) {
        JSONObject current = findImageAngleSettings(step, filename);
        if (current != null) return current;
        JSONObject created = new JSONObject();
        JSONArray settings = step.optJSONArray("imageSettings"); if (settings == null) settings = new JSONArray();
        try {
            created.put("fileName", filename);
            created.put("threshold", step.optInt("threshold", 85));
            created.put("retries", step.optInt("retries", 1));
            created.put("retryDelay", step.optInt("retryDelay", 500));
            created.put("enabled", true);
            settings.put(created); step.put("imageSettings", settings);
        } catch (JSONException ignored) { }
        return created;
    }

    private void removeImageAngleSettings(JSONObject step, String filename) {
        JSONArray old = step.optJSONArray("imageSettings"); if (old == null) return;
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject setting = old.optJSONObject(i);
            if (setting == null || !filename.equals(setting.optString("fileName", ""))) next.put(setting);
        }
        try { step.put("imageSettings", next); } catch (JSONException ignored) { }
    }

    private void editImageAngleSettings(JSONObject script, JSONObject step, String filename, int angleIndex, int stepIndex) {
        JSONObject setting = ensureImageAngleSettings(step, filename);
        showChoice("إعدادات زاوية " + (angleIndex + 1), new String[]{
                "نسبة التطابق: " + setting.optInt("threshold", step.optInt("threshold", 85)) + "%",
                "عدد المحاولات: " + setting.optInt("retries", step.optInt("retries", 1)),
                "فاصل إعادة المحاولة: " + setting.optInt("retryDelay", step.optInt("retryDelay", 500)) + " ms",
                setting.optBoolean("enabled", true) ? "تعطيل هذه الزاوية" : "تفعيل هذه الزاوية",
                "إرجاع إعدادات هذه الزاوية للافتراضي"
        }, choice -> {
            JSONObject liveStep = findStepById(script, step.optString("id"));
            if (liveStep == null) return;
            JSONObject liveSetting = ensureImageAngleSettings(liveStep, filename);
            if (choice == 0) promptNumber("نسبة التطابق لهذه الزاوية (0–100)", liveSetting.optInt("threshold", liveStep.optInt("threshold", 85)), 0, 100, value -> {
                putInt(liveSetting, "threshold", value); BotnaStore.saveSteps(this, script, BotnaStore.steps(script));
                report("تم حفظ نسبة التطابق لهذه الزاوية فقط."); refreshImageAnglesDialog(script, liveStep, stepIndex);
            });
            else if (choice == 1) promptNumber("عدد المحاولات لهذه الزاوية (أي عدد صحيح موجب)", liveSetting.optInt("retries", liveStep.optInt("retries", 1)), 1, Integer.MAX_VALUE, value -> {
                putInt(liveSetting, "retries", value); BotnaStore.saveSteps(this, script, BotnaStore.steps(script)); refreshImageAnglesDialog(script, liveStep, stepIndex);
            });
            else if (choice == 2) promptNumber("فاصل إعادة المحاولة لهذه الزاوية (ms)", liveSetting.optInt("retryDelay", liveStep.optInt("retryDelay", 500)), 100, 10000, value -> {
                putInt(liveSetting, "retryDelay", value); BotnaStore.saveSteps(this, script, BotnaStore.steps(script)); refreshImageAnglesDialog(script, liveStep, stepIndex);
            });
            else if (choice == 3) {
                putBoolean(liveSetting, "enabled", !liveSetting.optBoolean("enabled", true));
                BotnaStore.saveSteps(this, script, BotnaStore.steps(script)); refreshImageAnglesDialog(script, liveStep, stepIndex);
            } else if (choice == 4) {
                removeImageAngleSettings(liveStep, filename); BotnaStore.saveSteps(this, script, BotnaStore.steps(script));
                report("تمت استعادة إعدادات الزاوية الافتراضية."); refreshImageAnglesDialog(script, liveStep, stepIndex);
            }
        });
    }

    private void refreshImageAnglesDialog(JSONObject script, JSONObject step, int stepIndex) {
        if (imageAnglesDialog != null && imageAnglesDialog.isShowing()) imageAnglesDialog.dismiss();
        showImageAngles(script, step, stepIndex);
    }

    private int thresholdForTemplate(JSONObject step, String filename) {
        JSONObject setting = findImageAngleSettings(step, filename);
        return clampInt(setting == null ? step.optInt("threshold", 85) : setting.optInt("threshold", step.optInt("threshold", 85)), 0, 100);
    }

    private int retriesForTemplate(JSONObject step, String filename) {
        JSONObject setting = findImageAngleSettings(step, filename);
        return Math.max(1, setting == null ? step.optInt("retries", 1) : setting.optInt("retries", step.optInt("retries", 1)));
    }

    private int retryDelayForTemplate(JSONObject step, String filename) {
        JSONObject setting = findImageAngleSettings(step, filename);
        return clampInt(setting == null ? step.optInt("retryDelay", 500) : setting.optInt("retryDelay", step.optInt("retryDelay", 500)), 100, 10000);
    }
    private void chooseScriptDialog() {
        JSONArray all = BotnaStore.scripts(this); String[] names = new String[all.length()];
        for (int i = 0; i < all.length(); i++) { JSONObject s = all.optJSONObject(i); names[i] = s == null ? "سكربت" : s.optString("name", "سكربت"); }
        if (names.length == 0) { BotnaStore.createScript(this, "سكربت الأول"); rebuildPanel(); return; }
        showChoice("اختر سكربت", names, which -> {
            JSONObject chosen = all.optJSONObject(which);
            if (chosen != null) { BotnaStore.setActiveId(this, chosen.optString("id")); rebuildPanel(); }
        });
    }

    private void promptText(String title, String initial, TextCallback callback) {
        EditText input = new EditText(this); input.setSingleLine(true); input.setTextColor(TEXT); input.setHintTextColor(MUTED);
        input.setText(initial); input.setSelectAllOnFocus(true); input.setBackground(glassShape(BUTTON, CYAN, 1, 12));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        showCustomDialog(title, input, () -> callback.done(input.getText().toString().trim()));
    }

    private interface TextCallback { void done(String value); }
    private interface IntCallback { void done(int value); }
    private interface ChoiceCallback { void done(int value); }

    private void promptNumber(String title, int initial, int min, int max, IntCallback callback) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(initial));
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(17);
        input.setSelectAllOnFocus(true);
        input.setHighlightColor(CYAN);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(glassShape(BG, CYAN, 1, 12));
        showCustomDialog(title, input, () -> {
            String number = BotnaArabicDialect.normalize(input.getText().toString()).replaceAll("[^0-9]", "");
            int value = initial;
            try { if (!number.isEmpty()) value = Integer.parseInt(number); } catch (Exception ignored) { }
            callback.done(Math.max(min, Math.min(max, value)));
        });
    }

    private void showCustomDialog(String title, View content, Runnable positive) {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.setBackground(glassShape(GLASS, Color.argb(238, 35, 226, 244), 1, 18));
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        root.setClipToPadding(false);
        if (title != null && title.startsWith("مهمة آلية عبر ")) {
            Button hideKeyboard = button("إخفاء لوحة المفاتيح", false);
            LinearLayout.LayoutParams hideKeyboardLp = new LinearLayout.LayoutParams(-1, dp(40));
            hideKeyboardLp.setMargins(dp(2), dp(2), dp(2), dp(6));
            root.addView(hideKeyboard, hideKeyboardLp);
            hideKeyboard.setOnClickListener(view -> {
                try {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
                } catch (Exception ignored) { }
                root.setFocusableInTouchMode(true);
                root.requestFocus();
            });
        }
        ScrollView body = new ScrollView(this);
        body.setFillViewport(false);
        body.setClipToPadding(false);
        body.setVerticalScrollBarEnabled(true);
        LinearLayout bodyContent = new LinearLayout(this);
        bodyContent.setOrientation(LinearLayout.VERTICAL);
        bodyContent.setClipToPadding(false);
        bodyContent.addView(content, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout footer = horizontal();
        Button cancel = button("إلغاء", false);
        Button save = button("حفظ وخروج", true);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        cancelLp.setMargins(dp(2), dp(5), dp(2), dp(2));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        saveLp.setMargins(dp(2), dp(5), dp(2), dp(2));
        footer.addView(cancel, cancelLp); footer.addView(save, saveLp);
        bodyContent.addView(footer, new LinearLayout.LayoutParams(-1, -2));
        body.addView(bodyContent, new ScrollView.LayoutParams(-1, -2));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(root).create();
        activeCustomDialog = dialog;
        dialog.setOnDismissListener(ignored -> { if (activeCustomDialog == dialog) activeCustomDialog = null; });
        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> { dialog.dismiss(); handler.post(positive); });
        try {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
            dialog.show();
            Window w2 = dialog.getWindow();
            if (w2 != null) {
                w2.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                w2.getDecorView().setBackgroundColor(Color.TRANSPARENT);
                w2.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                android.util.DisplayMetrics dm = realScreenMetrics();
                w2.setLayout(Math.max(dp(260), Math.min(dp(540), dm.widthPixels - dp(24))),
                        Math.max(dp(220), Math.min(dp(620), dm.heightPixels - dp(56))));
            }
            if (content instanceof EditText) {
                content.requestFocus();
                content.postDelayed(() -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(content, InputMethodManager.SHOW_IMPLICIT);
                }, 160);
            }
        } catch (Exception e) { Toast.makeText(this, "تعذر فتح نافذة التعديل", Toast.LENGTH_LONG).show(); }
    }

    private void showChoice(String title, String[] options, ChoiceCallback callback) {
        String[] displayed = options == null ? new String[0] : options.clone();
        for (int i = 0; i < displayed.length; i++) displayed[i] = BotnaI18n.translate(this, displayed[i]);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(5), dp(8), dp(8));
        root.setBackground(glassShape(GLASS, Color.argb(240, 35, 226, 244), 1, 20));
        root.setClipToPadding(false);
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        LinearLayout header = horizontal();
        TextView heading = label(BotnaI18n.translate(this, title), 15, TEXT, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button back = button("↩", false);
        back.setContentDescription(BotnaI18n.translate(this, "رجوع"));
        Button close = button("×", false);
        close.setContentDescription(BotnaI18n.translate(this, "إغلاق"));
        header.addView(back, new LinearLayout.LayoutParams(dp(38), dp(40)));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(38), dp(40));
        closeLp.leftMargin = dp(4);
        header.addView(close, closeLp);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(42)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setClipToPadding(false);
        LinearLayout optionsList = new LinearLayout(this);
        optionsList.setOrientation(LinearLayout.VERTICAL);
        optionsList.setPadding(0, dp(4), 0, dp(8));
        scroll.addView(optionsList, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        AlertDialog dialog = new AlertDialog.Builder(this).setView(root).create();
        back.setOnClickListener(v -> dialog.dismiss());
        close.setOnClickListener(v -> dialog.dismiss());
        for (int i = 0; i < displayed.length; i++) {
            final int index = i;
            Button option = button(displayed[i], true);
            option.setSingleLine(false);
            option.setMaxLines(3);
            option.setMinHeight(dp(46));
            option.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            LinearLayout.LayoutParams optionLp = new LinearLayout.LayoutParams(-1, -2);
            optionLp.topMargin = dp(3);
            optionsList.addView(option, optionLp);
            option.setOnClickListener(v -> {
                dialog.dismiss();
                handler.post(() -> {
                    if (callback != null) callback.done(index);
                });
            });
        }
        try {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
            dialog.show();
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                shown.getDecorView().setBackgroundColor(Color.TRANSPARENT);
                android.util.DisplayMetrics dm = realScreenMetrics();
                int targetHeight = Math.max(dp(240), Math.min(dp(680), dm.heightPixels - dp(44)));
                root.setMinimumHeight(targetHeight - dp(16));
                ViewGroup.LayoutParams rootParams = root.getLayoutParams();
                if (rootParams != null) { rootParams.height = targetHeight - dp(16); root.setLayoutParams(rootParams); }
                shown.setLayout(Math.max(dp(260), Math.min(dp(540), dm.widthPixels - dp(24))), targetHeight);
            }
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح قائمة الخيارات", Toast.LENGTH_LONG).show();
        }
    }

    private void addCustomStep() {
        JSONObject step = addStep(TYPE_CUSTOM, "خطوة مخصصة — تحتاج إعدادًا");
        putString(step, "instructions", "");
        try { step.put("compiledSteps", new JSONArray()); } catch (JSONException ignored) { }
        saveStepObject(step);
        rebuildPanel();
        editCustomInstructions(step);
    }

    private void editCustomInstructions(JSONObject step) {
        if (step == null) return;
        final String stepId = step.optString("id", "");
        final String scriptId = ensureScript().optString("id", "");
        final Runnable[] pendingDraftSave = new Runnable[1];
        final boolean[] compiledPlanInvalidated = new boolean[1];
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(10), dp(6), dp(10), dp(4));
        TextView editorHelp = label("سيُحفظ النص الأصلي أولًا حتى لو فشل التحليل. راجع معاينة الخطوات قبل التشغيل.", 12, MUTED, false);
        content.addView(editorHelp, wrap());

        EditText input = new EditText(this);
        String stagedDraft = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE)
                .getString(customDraftPreferenceKey(scriptId, stepId), null);
        input.setText(stagedDraft == null ? step.optString("instructions", "") : stagedDraft);
        input.setSingleLine(false);
        input.setMinLines(5);
        input.setMaxLines(Integer.MAX_VALUE);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setVerticalScrollBarEnabled(true);
        input.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        input.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        input.setTextSize(15); input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setTextColor(TEXT); input.setHintTextColor(MUTED);
        input.setHint(BotnaI18n.translate(this, "مثال: تابع صورة رقم 4 بالمكتبة، إذا وجدتها انتظر ثانيتين ثم ارجع"));
        input.setBackground(glassShape(Color.rgb(8, 14, 22), CYAN, 1, 12));
        content.addView(input, new LinearLayout.LayoutParams(-1, dp(220)));
        CheckBox includeScreenContext = new CheckBox(this);
        includeScreenContext.setText(BotnaI18n.translate(this, "إرفاق لقطة الشاشة الحالية للمترجم البصري"));
        includeScreenContext.setTextColor(MUTED);
        includeScreenContext.setTextSize(12);
        includeScreenContext.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
        includeScreenContext.setChecked(true);
        includeScreenContext.setEnabled(BotnaAICompiler.isConfigured(this));
        LinearLayout.LayoutParams visionLp = wrap(); visionLp.topMargin = dp(3);
        content.addView(includeScreenContext, visionLp);
        TextView visionPrivacy = label(BotnaAICompiler.isConfigured(this)
                ? "عند التفعيل، تُرسل لقطة الشاشة إلى مزوّد الذكاء الاصطناعي عبر الإنترنت. أوقفه في الشاشات الحساسة."
                : "لقطة الشاشة لن تُرسل لأن مزوّد الذكاء الاصطناعي غير مضبوط. المحلّل المحلي نصّي فقط.", 10, MUTED, false);
        LinearLayout.LayoutParams visionPrivacyLp = wrap(); visionPrivacyLp.topMargin = dp(1);
        content.addView(visionPrivacy, visionPrivacyLp);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable editable) {
                final String draft = editable == null ? "" : editable.toString();
                getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit()
                        .putString(customDraftPreferenceKey(scriptId, stepId), draft).apply();
                if (!compiledPlanInvalidated[0]) {
                    invalidateCustomCompilation(scriptId, stepId);
                    compiledPlanInvalidated[0] = true;
                }
                if (pendingDraftSave[0] != null) handler.removeCallbacks(pendingDraftSave[0]);
                pendingDraftSave[0] = () -> saveCustomDraft(scriptId, stepId, draft);
                handler.postDelayed(pendingDraftSave[0], 350);
            }
        });

        TextView aiStatus = label(BotnaAICompiler.isConfigured(this)
                ? "المزوّد المحدد: " + BotnaAICompiler.providerLabel(BotnaAICompiler.providerId(this)) + " — متاح للتحليل البصري ومراجعة سجل الخطوات عند إرفاق الشاشة."
                : "المحلّل المحلي يستخدم قواعد لغوية ولا يرى الصور أو يتدرّب تلقائيًا؛ لفهم الأوامر المركبة ورؤية الشاشة اضبط مزوّد API.", 12, MUTED, false);
        LinearLayout.LayoutParams statusLp = wrap(); statusLp.topMargin = dp(8); content.addView(aiStatus, statusLp);
        LinearLayout buttons = horizontal();
        Button settings = button("⚙ إعدادات AI", false);
        Button library = button("مكتبة الصور", false);
        buttons.addView(settings, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams libLp = new LinearLayout.LayoutParams(0, dp(38), 1); libLp.leftMargin = dp(6); buttons.addView(library, libLp);
        LinearLayout.LayoutParams buttonsLp = wrap(); buttonsLp.topMargin = dp(8); content.addView(buttons, buttonsLp);

        ScrollView editorScroll = new ScrollView(this);
        editorScroll.setFillViewport(false);
        editorScroll.setNestedScrollingEnabled(true);
        editorScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout editorRoot = new LinearLayout(this);
        editorRoot.setOrientation(LinearLayout.VERTICAL);
        editorRoot.setPadding(dp(4), dp(2), dp(4), dp(4));
        editorRoot.addView(editorScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout editorActions = horizontal();
        Button cancelEditor = button("إلغاء", false);
        Button analyzeEditor = button("تحليل وحفظ", true);
        LinearLayout.LayoutParams editorActionLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        editorActionLp.setMargins(dp(3), dp(5), dp(3), dp(2));
        editorActions.addView(cancelEditor, new LinearLayout.LayoutParams(editorActionLp));
        editorActions.addView(analyzeEditor, new LinearLayout.LayoutParams(editorActionLp));
        editorRoot.addView(editorActions, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(BotnaI18n.translate(this, "اكتب الأمر بالعربية"))
                .setView(editorRoot)
                .create();
        try {
            if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                android.util.DisplayMetrics editorMetrics = realScreenMetrics();
                int editorWidth = Math.min(editorMetrics.widthPixels - dp(24), dp(520));
                int defaultEditorHeight = Math.min((int) (editorMetrics.heightPixels * 0.78f), dp(720));
                dialog.getWindow().setLayout(editorWidth, defaultEditorHeight);
                View decor = dialog.getWindow().getDecorView();
                int[] lastEditorHeight = {defaultEditorHeight};
                decor.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                    if (!dialog.isShowing() || dialog.getWindow() == null) return;
                    android.graphics.Rect visibleFrame = new android.graphics.Rect();
                    decor.getWindowVisibleDisplayFrame(visibleFrame);
                    int coveredHeight = Math.max(0, editorMetrics.heightPixels - visibleFrame.bottom);
                    boolean keyboardVisible = coveredHeight > dp(160);
                    int targetHeight = keyboardVisible
                            ? Math.max(dp(300), Math.min(defaultEditorHeight, editorMetrics.heightPixels - coveredHeight - dp(40)))
                            : defaultEditorHeight;
                    if (Math.abs(lastEditorHeight[0] - targetHeight) > dp(8)) {
                        lastEditorHeight[0] = targetHeight;
                        dialog.getWindow().setLayout(editorWidth, targetHeight);
                    }
                });
            }
            cancelEditor.setOnClickListener(v -> {
                String draft = input.getText().toString().trim();
                if (pendingDraftSave[0] != null) { handler.removeCallbacks(pendingDraftSave[0]); pendingDraftSave[0] = null; }
                saveCustomDraft(scriptId, stepId, draft);
                dialog.dismiss();
                showBubble();
                if (panelWasVisibleAtRunStart) showPanel();
            });
            settings.setOnClickListener(v -> {
                String draft = input.getText().toString().trim();
                if (pendingDraftSave[0] != null) { handler.removeCallbacks(pendingDraftSave[0]); pendingDraftSave[0] = null; }
                saveCustomDraft(scriptId, stepId, draft);
                dialog.dismiss();
                BotnaAICompiler.showSettingsDialog(this, true, () -> {
                    JSONObject refreshed = findStepById(BotnaStore.getScript(this, scriptId), stepId);
                    editCustomInstructions(refreshed);
                });
            });
            library.setOnClickListener(v -> {
                String draft = input.getText().toString().trim();
                if (pendingDraftSave[0] != null) { handler.removeCallbacks(pendingDraftSave[0]); pendingDraftSave[0] = null; }
                saveCustomDraft(scriptId, stepId, draft);
                dialog.dismiss(); showImageLibrary();
            });
            analyzeEditor.setOnClickListener(v -> {
                String command = input.getText().toString().trim();
                if (pendingDraftSave[0] != null) { handler.removeCallbacks(pendingDraftSave[0]); pendingDraftSave[0] = null; }
                if (command.isEmpty()) { report("اكتب الأمر في مربع النص أولًا، ثم اضغط تحليل وحفظ. النص الرمادي مثال توضيحي وليس أمرًا مكتوبًا."); return; }

                JSONObject owningScript = BotnaStore.getScript(this, scriptId);
                JSONObject latest = findStepById(owningScript, stepId);
                if (owningScript == null || latest == null) { report("لم يتم العثور على السكربت أو الخطوة المطلوب حفظها."); return; }
                saveCustomDraft(scriptId, stepId, command);

                JSONArray librarySnapshot = BotnaStore.imageLibrary(this);
                boolean useAI = BotnaAICompiler.isConfigured(this);
                boolean useScreen = useAI && includeScreenContext.isChecked();

                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                input.clearFocus();
                dialog.dismiss();
                hideBotnaOverlaysForCleanScreenshot();
                if (useScreen) {
                    handler.postDelayed(() -> takeScreenshot((screen, error) -> {
                        if (screen == null) saveResult("لم تُلتقط لقطة سياق نظيفة؛ ستتم محاولة الترجمة النصية فقط: " + error);
                        compileCustomInstructionsAsync(command, scriptId, stepId, librarySnapshot, useAI, null, null, screen);
                    }), 500);
                } else {
                    compileCustomInstructionsAsync(command, scriptId, stepId, librarySnapshot, useAI, null, null, null);
                }
            });
        } catch (Exception e) { report("تعذر فتح محرر الخطوة المخصصة."); }
    }

    private void compileCustomInstructionsAsync(String command, String scriptId, String stepId,
                                                JSONArray librarySnapshot, boolean useAI,
                                                Button saveButton, AlertDialog dialog, Bitmap screenContext) {
        try {
            aiExecutor.execute(() -> {
                JSONArray compiled = new JSONArray();
                List<String> warnings = new ArrayList<>();
                String source = useAI ? "ai" : "local";
                String aiFailure = "";
                try {
                    if (useAI) {
                        try {
                            JSONObject plan = BotnaAICompiler.compile(this, command, librarySnapshot, screenContext,
                                    buildBotnaApplicationContext(scriptId, stepId));
                            compiled = compileAIPlan(plan, librarySnapshot, warnings);
                            if (compiled.length() == 0) throw new IllegalStateException("الخطة لم تحتوِ أوامر مدعومة.");
                            JSONArray aiWarnings = plan.optJSONArray("warnings");
                            if (aiWarnings != null) for (int i = 0; i < aiWarnings.length(); i++) {
                                String warning = aiWarnings.optString(i, "").trim();
                                if (!warning.isEmpty()) warnings.add(warning);
                            }
                            String structure = validateScriptStructure(compiled);
                            if (structure != null) throw new IllegalStateException("بنية الخطة غير صالحة: " + structure);
                        } catch (Exception e) {
                            aiFailure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                            BotnaLogStore.add(this, "ai_compile", -1, "ترجمة الأمر المخصص", "فشل طلب الذكاء الاصطناعي؛ ستجري محاولة بالمحلل المحلي.", aiFailure,
                                    screenContext != null && !screenContext.isRecycled() ? screenContext : null);
                            compiled = new JSONArray(); warnings.clear();
                        }
                    }
                    if (compiled.length() == 0) {
                        try {
                            compiled = compileCustomInstructions(command, warnings);
                            String localStructureError = validateScriptStructure(compiled);
                            if (localStructureError != null) {
                                warnings.add("لم تُحفظ الخطة للتنفيذ لأن تركيب الشروط/التكرار غير صحيح: " + localStructureError);
                                compiled = new JSONArray();
                            }
                            source = "local";
                            if (useAI && !aiFailure.isEmpty()) warnings.add("تعذر استخدام AI؛ استُخدم المحلّل المحلي كبديل: " + aiFailure);
                            else if (!useAI) warnings.add("تم استخدام المحلّل المحلي. للأوامر الحرة والمعقدة، اضبط مزوّد AI في الإعدادات.");
                        } catch (Exception e) {
                            String localFailure = "تعذر التحليل المحلي: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
                            warnings.add(localFailure);
                            BotnaLogStore.add(this, "local_compile", -1, "ترجمة الأمر المخصص", "فشل التحليل المحلي أيضًا.", localFailure,
                                    screenContext != null && !screenContext.isRecycled() ? screenContext : null);
                        }
                    }
                    JSONArray finalCompiled = autoTuneCompiledPlan(compiled, command);
                    List<String> finalWarnings = new ArrayList<>(warnings);
                    String finalSource = source;
                    String finalAiFailure = aiFailure;
                    handler.post(() -> {
                        JSONObject targetScript = BotnaStore.getScript(this, scriptId);
                        JSONObject target = findStepById(targetScript, stepId);
                        if (targetScript == null || target == null) {
                            report("تم حفظ النص، لكن تعذر العثور على السكربت أو الخطوة لإكمال الترجمة.");
                            safeDismiss(dialog); rebuildPanel(); showBubble(); return;
                        }
                        putString(target, "instructions", command);
                        try {
                            target.put("compiledSteps", finalCompiled);
                            JSONArray wa = new JSONArray(); for (String warning : finalWarnings) wa.put(warning);
                            target.put("compileWarnings", wa);
                            target.put("compilerProvider", "ai".equals(finalSource) ? BotnaAICompiler.providerId(this) : "local");
                            target.put("compiledAt", System.currentTimeMillis());
                        } catch (JSONException ignored) { }
                        if (finalCompiled.length() == 0) {
                            putString(target, "label", "خطوة مخصصة — نص محفوظ، يحتاج ترجمة");
                            BotnaStore.saveSteps(this, targetScript, BotnaStore.steps(targetScript));
                            safeDismiss(dialog); rebuildPanel(); showBubble();
                            String errorDetail = "سبب AI: " + (finalAiFailure.isEmpty() ? "لم يُرجع خطوات قابلة للتنفيذ." : finalAiFailure)
                                    + "\nتحذيرات المحلل: " + finalWarnings.toString();
                            logFailureWithScreenshot("compile", -1, "ترجمة الأمر: " + command,
                                    "حُفظ النص الأصلي لكن لم تنتج الترجمة خطوات قابلة للتنفيذ.", errorDetail, true);
                            report("حُفظ النص الأصلي كمسودة ولم ينتج التحليل خطوات آمنة قابلة للتنفيذ. " + (finalAiFailure.isEmpty() ? "" : "سبب AI: " + finalAiFailure));
                            return;
                        }
                        putString(target, "label", "خطوة مخصصة • " + finalCompiled.length() + " أمر");
                        BotnaStore.saveSteps(this, targetScript, BotnaStore.steps(targetScript));
                        safeDismiss(dialog); rebuildPanel(); showBubble();
                        StringBuilder message = new StringBuilder("تم حفظ النص وترجمته إلى ").append(finalCompiled.length()).append(" أمرًا.");
                        if (!finalWarnings.isEmpty()) message.append(" توجد ").append(finalWarnings.size()).append(" تنبيهات؛ افتح معاينة الترجمة.");
                        if ("local".equals(finalSource)) message.append(" (المحلّل المحلي)");
                        report(message.toString());
                        previewCustomCompilation(target);
                    });
                } finally {
                    if (screenContext != null && !screenContext.isRecycled()) screenContext.recycle();
                }
            });
        } catch (Exception e) {
            if (screenContext != null && !screenContext.isRecycled()) screenContext.recycle();
            safeDismiss(dialog); rebuildPanel(); showBubble();
            report("تعذر بدء التحليل. تم الاحتفاظ بالنص الأصلي.");
        }
    }

    private JSONArray autoTuneCompiledPlan(JSONArray input, String sourceText) {
        JSONArray tuned = new JSONArray();
        if (input == null) return tuned;
        String text = normalizeArabicCommand(sourceText == null ? "" : sourceText);
        boolean likelySlowLaunch = text.contains("لعبة") || text.contains("اللعبه") || text.contains("تحميل") || text.contains("تنتهي") || text.contains("تسجيل الدخول");
        for (int i=0; i<input.length(); i++) {
            JSONObject original=input.optJSONObject(i); if(original==null)continue;
            JSONObject step;
            try { step=new JSONObject(original.toString()); } catch(JSONException e) { continue; }
            String type=step.optString("type","");
            if (TYPE_LAUNCH_APP.equals(type) && step.optInt("afterDelay",0)<=0) putInt(step,"afterDelay",likelySlowLaunch?5000:3000);
            else if ((TYPE_TAP.equals(type)||TYPE_TEXT.equals(type)||TYPE_TYPE_TEXT.equals(type)||TYPE_PRESS_ENTER.equals(type)||TYPE_GLOBAL_ACTION.equals(type)||TYPE_SWIPE.equals(type)) && step.optInt("afterDelay",0)<=0) putInt(step,"afterDelay",350);
            if (TYPE_IMAGE.equals(type)) {
                putInt(step,"threshold",clampInt(step.optInt("threshold",85),0,100));
                putInt(step,"retries",Math.max(1,step.optInt("retries",3)));
                putInt(step,"retryDelay",clampInt(step.optInt("retryDelay",500),100,10000));
            }
            if ((TYPE_WAIT_TEXT.equals(type)||TYPE_WAIT_INPUT.equals(type)) && !step.has("attempts")) putInt(step,"attempts",30);
            if ((TYPE_WAIT_TEXT.equals(type)||TYPE_WAIT_INPUT.equals(type)) && !step.has("intervalMs")) putInt(step,"intervalMs",500);
            tuned.put(step);
        }
        return tuned;
    }

    private String customDraftPreferenceKey(String scriptId, String stepId) {
        return "custom_draft_v1_" + scriptId + "_" + stepId;
    }

    private void invalidateCustomCompilation(String scriptId, String stepId) {
        JSONObject owningScript = BotnaStore.getScript(this, scriptId);
        if (owningScript == null) return;
        JSONObject step = findStepById(owningScript, stepId);
        if (step == null) return;
        putString(step, "label", "خطوة مخصصة — نص جديد، تحتاج تحليل");
        try {
            step.put("compiledSteps", new JSONArray());
            step.put("compileWarnings", new JSONArray());
        } catch (JSONException ignored) { }
        BotnaStore.saveSteps(this, owningScript, BotnaStore.steps(owningScript));
    }

    private void saveCustomDraft(String scriptId, String stepId, String command) {
        JSONObject owningScript = BotnaStore.getScript(this, scriptId);
        if (owningScript == null) return;
        JSONObject step = findStepById(owningScript, stepId);
        if (step == null) return;
        putString(step, "instructions", command == null ? "" : command);
        putString(step, "label", command == null || command.trim().isEmpty()
                ? "خطوة مخصصة — تحتاج إعدادًا" : "خطوة مخصصة — نص محفوظ، يحتاج تحليل");
        try {

            step.put("compiledSteps", new JSONArray());
            step.put("compileWarnings", new JSONArray());
        } catch (JSONException ignored) { }
        BotnaStore.saveSteps(this, owningScript, BotnaStore.steps(owningScript));
        getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit()
                .remove(customDraftPreferenceKey(scriptId, stepId)).apply();
    }

    private JSONArray compileAIPlan(JSONObject plan, JSONArray library, List<String> warnings) throws JSONException {
        JSONArray output = new JSONArray();
        JSONArray actions = plan == null ? null : plan.optJSONArray("steps");
        if (actions == null) throw new JSONException("لا توجد قائمة خطوات في نتيجة AI.");
        for (int i = 0; i < actions.length() && i < 200; i++) {
            int outputBefore = output.length();
            JSONObject action = actions.optJSONObject(i);
            if (action == null) { warnings.add("تم تجاهل عنصر غير صالح رقم " + (i + 1)); continue; }
            String name = normalizeArabicCommand(action.optString("action", "")).toLowerCase(Locale.ROOT).trim();
            JSONObject step;
            switch (name) {
                case "find_image":
                case "tap_image": {
                    int imageIndex = action.optInt("image_index", action.optInt("imageIndex", 0));
                    if (imageIndex < 1 || imageIndex > library.length()) { warnings.add("الخطوة " + (i + 1) + ": رقم صورة المكتبة غير صالح " + imageIndex); continue; }
                    JSONObject image = library.optJSONObject(imageIndex - 1);
                    if (image == null || image.optString("fileName", "").isEmpty()) { warnings.add("الخطوة " + (i + 1) + ": ملف صورة المكتبة غير متاح."); continue; }
                    addLibraryImageStep(output, image, imageIndex, "tap_image".equals(name));
                    step = output.optJSONObject(output.length() - 1);
                    if (step != null) {
                        putBoolean(step, "tapOnMatch", "tap_image".equals(name));
                        putBoolean(step, "waitForImageOnly", "find_image".equals(name));
                        putString(step, "label", ("tap_image".equals(name) ? "النقر على صورة المكتبة " : "العثور على صورة المكتبة ") + imageIndex + " • " + image.optString("name", "صورة"));
                    }
                    break;
                }
                case "launch_app":
                case "open_app":
                case "start_app":
                case "search_app": {
                    String appName = action.optString("app_name", action.optString("app", "")).trim();
                    if (appName.isEmpty()) { warnings.add("الخطوة " + (i + 1) + ": لم يذكر النموذج اسم التطبيق المراد فتحه."); continue; }
                    step = compiledStep(TYPE_LAUNCH_APP, "فتح تطبيق: " + appName); putString(step, "appName", appName); output.put(step); break;
                }
                case "open_url":
                case "open_link": {
                    String url = action.optString("url", action.optString("text", "")).trim();
                    if (!(url.startsWith("https://") || url.startsWith("http://"))) { warnings.add("الخطوة " + (i + 1) + ": تم تجاهل رابط غير HTTP/HTTPS."); continue; }
                    step = compiledStep(TYPE_OPEN_URL, "فتح الرابط: " + url); putString(step, "url", url); output.put(step); break;
                }
                case "speed_test":
                case "internet_speed_test":
                case "measure_download_speed": {
                    step = compiledStep(TYPE_SPEED_TEST, "قياس سرعة تنزيل الإنترنت التقريبية"); output.put(step); break;
                }
                case "check_internet": {
                    int attempts = Math.max(1, action.optInt("attempts", 3));
                    int interval = clampInt(action.optInt("interval_ms", 5000), 250, 60000);
                    int timeout = clampInt(action.optInt("timeout_ms", 4000), 1000, 12000);
                    step = compiledStep(TYPE_CHECK_INTERNET, "فحص الإنترنت ×" + attempts); putInt(step, "attempts", attempts); putInt(step, "intervalMs", interval); putInt(step, "timeoutMs", timeout);
                    String url = action.optString("url", "").trim(); if (!url.isEmpty() && (url.startsWith("https://") || url.startsWith("http://"))) putString(step, "testUrl", url);
                    output.put(step); break;
                }
                case "wait_for_internet":
                case "wait_until_online": {
                    int attempts = Math.max(1, action.optInt("max_attempts", action.optInt("attempts", 120)));
                    int interval = clampInt(action.optInt("interval_ms", 5000), 500, 60000);
                    int timeout = clampInt(action.optInt("timeout_ms", 4000), 1000, 12000);
                    step = compiledStep(TYPE_WAIT_INTERNET, "انتظار عودة الإنترنت حتى " + (attempts * interval / 1000) + " ثانية"); putInt(step, "attempts", attempts); putInt(step, "intervalMs", interval); putInt(step, "timeoutMs", timeout);
                    putBoolean(step, "notifyOnSuccess", action.optBoolean("notify_on_success", false));
                    putString(step, "successMessage", action.optString("success_message", "عاد الإنترنت بنجاح"));
                    String url = action.optString("url", "").trim(); if (!url.isEmpty() && (url.startsWith("https://") || url.startsWith("http://"))) putString(step, "testUrl", url);
                    output.put(step); break;
                }
                case "notify":
                case "alert": {
                    String message = action.optString("message", "تم تنفيذ الأمر بنجاح").trim();
                    step = compiledStep(TYPE_NOTIFY, "تنبيه: " + message); putString(step, "message", message); putBoolean(step, "sound", action.optBoolean("sound", true)); output.put(step); break;
                }
                case "wait_for_input":
                case "wait_for_editable_field": {
                    int attempts = Math.max(1, action.optInt("max_attempts", action.optInt("attempts", 20)));
                    int interval = clampInt(action.optInt("interval_ms", 500), 250, 60000);
                    step = compiledStep(TYPE_WAIT_INPUT, "انتظار ظهور حقل إدخال قابل للكتابة"); putInt(step,"attempts",attempts); putInt(step,"intervalMs",interval); output.put(step); break;
                }
                case "wait_for_text":
                case "wait_until_text": {
                    String value = action.optString("text", "").trim();
                    if (value.isEmpty()) { warnings.add("الخطوة " + (i + 1) + ": نص الانتظار فارغ."); continue; }
                    int attempts = Math.max(1, action.optInt("max_attempts", action.optInt("attempts", 20)));
                    int interval = clampInt(action.optInt("interval_ms", 500), 250, 60000);
                    step = compiledStep(TYPE_WAIT_TEXT, "انتظار ظهور النص: " + value); putString(step,"value",value); putInt(step,"attempts",attempts); putInt(step,"intervalMs",interval); output.put(step); break;
                }
                case "press_enter":
                case "press_search":
                case "ime_enter": addCompiled(output, TYPE_PRESS_ENTER, "تنفيذ Enter/بحث"); break;
                case "clear_text": addCompiled(output, TYPE_CLEAR_TEXT, "مسح حقل الإدخال المركّز"); break;
                case "open_notifications": addGlobalActionToArray(output, "notifications", "فتح لوحة الإشعارات"); break;
                case "open_quick_settings": addGlobalActionToArray(output, "quick_settings", "فتح الإعدادات السريعة"); break;
                case "open_recent_apps": addGlobalActionToArray(output, "recent_apps", "فتح التطبيقات الأخيرة"); break;
                case "dismiss_notifications": addGlobalActionToArray(output, "dismiss_notifications", "إغلاق لوحة الإشعارات"); break;
                case "close_recent_apps": addCompiled(output, TYPE_CLOSE_RECENTS, "إغلاق التطبيقات الأخيرة الظاهرة"); break;
                case "wait": {
                    int delay = clampInt(action.optInt("delay_ms", action.optInt("delay", 500)), 0, 60000);
                    step = compiledStep(TYPE_WAIT, "انتظار " + delay + " ms"); putInt(step, "delay", delay); output.put(step); break;
                }
                case "tap_at": {
                    int x = clampInt(action.optInt("x_percent", action.optInt("x", 50)), 0, 100);
                    int y = clampInt(action.optInt("y_percent", action.optInt("y", 50)), 0, 100);
                    step = compiledStep(TYPE_TAP, "نقر الموقع " + x + "%، " + y + "%"); putInt(step, "xPercent", x); putInt(step, "yPercent", y); output.put(step); break;
                }
                case "long_press_at": {
                    int x = clampInt(action.optInt("x_percent", 50), 0, 100); int y = clampInt(action.optInt("y_percent", 50), 0, 100);
                    int duration = clampInt(action.optInt("duration_ms", 800), 300, 5000);
                    step = compiledStep(TYPE_LONG_PRESS, "ضغط مطول " + x + "%، " + y + "%"); putInt(step, "xPercent", x); putInt(step, "yPercent", y); putInt(step, "duration", duration); output.put(step); break;
                }
                case "click_text": {
                    String value = action.optString("text", "").trim(); if (value.isEmpty()) { warnings.add("الخطوة " + (i + 1) + ": نص النقر فارغ."); continue; }
                    step = compiledStep(TYPE_TEXT, "نقر نص: " + value); putString(step, "value", value); putInt(step, "retries", 2); output.put(step); break;
                }
                case "type_text": {
                    String value = action.optString("text", ""); if (value.isEmpty()) { warnings.add("الخطوة " + (i + 1) + ": نص الكتابة فارغ."); continue; }
                    step = compiledStep(TYPE_TYPE_TEXT, "كتابة نص: " + value); putString(step, "value", value); output.put(step); break;
                }
                case "swipe": {
                    int x1=clampInt(action.optInt("x1_percent", 50),0,100), y1=clampInt(action.optInt("y1_percent",75),0,100);
                    int x2=clampInt(action.optInt("x2_percent",50),0,100), y2=clampInt(action.optInt("y2_percent",25),0,100);
                    int duration=clampInt(action.optInt("duration_ms",450),100,5000);
                    step=compiledStep(TYPE_SWIPE,"سحب من "+x1+"%, "+y1+"% إلى "+x2+"%, "+y2+"%");
                    putInt(step,"x1",x1);putInt(step,"y1",y1);putInt(step,"x2",x2);putInt(step,"y2",y2);putInt(step,"duration",duration);output.put(step);break;
                }
                case "read_screen_text":
                case "extract_visible_text": {
                    step = compiledStep(TYPE_READ_SCREEN_TEXT, "قراءة النصوص الظاهرة في الشاشة"); output.put(step); break;
                }
                case "check_text":
                case "verify_text": {
                    String value=action.optString("text","").trim();
                    if(value.isEmpty()){warnings.add("الخطوة "+(i+1)+": نص التحقق فارغ.");continue;}
                    step=compiledStep(TYPE_WAIT_TEXT,"التحقق من ظهور النص: "+value);putString(step,"value",value);putInt(step,"attempts",1);putInt(step,"intervalMs",250);output.put(step);break;
                }
                case "scroll": {
                    String direction = action.optString("direction", "up").toLowerCase(Locale.ROOT);
                    if (!direction.equals("up") && !direction.equals("down") && !direction.equals("left") && !direction.equals("right")) direction = "up";
                    step = compiledStep(TYPE_SCROLL, "تمرير " + direction); putString(step, "direction", direction); putInt(step, "duration", clampInt(action.optInt("duration_ms", 450), 100, 5000)); output.put(step); break;
                }
                case "back": addCompiled(output, TYPE_BACK, "رجوع"); break;
                case "home": addCompiled(output, TYPE_HOME, "الرئيسية"); break;
                case "if_success": addCompiled(output, TYPE_IF_SUCCESS, "إذا نجح الإجراء السابق"); break;
                case "if_fail": addCompiled(output, TYPE_IF_FAIL, "إذا فشل الإجراء السابق"); break;
                case "else": addCompiled(output, TYPE_ELSE, "وإلا"); break;
                case "end_if": addCompiled(output, TYPE_END_IF, "نهاية الشرط"); break;
                case "repeat_start":
                    int count = Math.max(1, action.optInt("count", 2)); step = compiledStep(TYPE_REPEAT_START, "بدء تكرار ×" + count); putInt(step, "repeatCount", count); output.put(step); break;
                case "repeat_end": addCompiled(output, TYPE_REPEAT_END, "نهاية التكرار"); break;
                default: warnings.add("الخطوة " + (i + 1) + ": فعل غير مدعوم من مترجم AI: " + name); break;
            }
            if (output.length() > outputBefore) {
                JSONObject produced = output.optJSONObject(output.length() - 1);
                if (produced != null && action.has("before_delay_ms")) putInt(produced, "beforeDelay", clampInt(action.optInt("before_delay_ms", 0), 0, 60000));
                if (produced != null && action.has("after_delay_ms")) putInt(produced, "afterDelay", clampInt(action.optInt("after_delay_ms", 0), 0, 60000));
                if (produced != null && action.has("threshold")) putInt(produced, "threshold", clampInt(action.optInt("threshold", 85), 0, 100));
                if (produced != null && action.has("retries")) putInt(produced, "retries", Math.max(1, action.optInt("retries", 3)));
                if (produced != null && action.has("retry_delay_ms")) putInt(produced, "retryDelay", clampInt(action.optInt("retry_delay_ms", 500), 100, 10000));
                if (produced != null && action.has("retry_on_failure_count")) putInt(produced, "retryOnFailureCount", Math.max(1, action.optInt("retry_on_failure_count", 1)));
                if (produced != null && action.has("retry_on_failure_delay_ms")) putInt(produced, "retryOnFailureDelay", clampInt(action.optInt("retry_on_failure_delay_ms", 700), 100, 60000));
            }
        }
        if (actions.length() > 200) warnings.add("تم الاقتصار على أول 200 خطوة حمايةً من خطة مفرطة الطول.");
        return output;
    }
    private void previewCustomCompilation(JSONObject step) {
        if (step == null) return;
        showCompiledBlockEditor(step);
    }

    private void showCompiledBlockEditor(JSONObject parentStep) {
        if (parentStep == null) return;
        if (compiledBlockDialog != null && compiledBlockDialog.isShowing()) {
            try { compiledBlockDialog.dismiss(); } catch (Exception ignored) { }
        }
        final String parentId = parentStep.optString("id", "");
        JSONObject script = ensureScript();
        JSONObject latestParent = findStepById(script, parentId);
        if (latestParent == null) latestParent = parentStep;
        JSONArray compiled = latestParent.optJSONArray("compiledSteps");
        if (compiled == null) compiled = new JSONArray();
        JSONArray warnings = latestParent.optJSONArray("compileWarnings");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(7), dp(10), dp(8));
        root.setBackground(glassShape(GLASS, Color.argb(245, 35, 226, 244), 1, 20));
        root.setLayoutParams(new ViewGroup.LayoutParams(-1, -1));
        root.setClipToPadding(false);

        LinearLayout header = horizontal();
        TextView heading = label(BotnaI18n.isEnglish(this)
                ? "Translated commands • " + compiled.length()
                : "الأوامر المترجمة • " + compiled.length(), 16, TEXT, true);
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button close = button("×", false);
        close.setContentDescription(BotnaI18n.translate(this, "إغلاق"));
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView source = label((BotnaI18n.isEnglish(this) ? "Original text: " : "النص الأصلي: ")
                + latestParent.optString("instructions", ""), 12, MUTED, false);
        source.setPadding(dp(9), dp(7), dp(9), dp(7));
        source.setBackground(glassShape(Color.argb(135, 6, 18, 31), Color.argb(95, 35, 226, 244), 1, 10));
        LinearLayout.LayoutParams sourceLp = wrap(); sourceLp.topMargin = dp(5); root.addView(source, sourceLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setClipToPadding(false);
        LinearLayout blocks = new LinearLayout(this);
        blocks.setOrientation(LinearLayout.VERTICAL);
        blocks.setPadding(0, dp(4), 0, dp(6));
        if (compiled.length() == 0) {
            TextView empty = label(BotnaI18n.isEnglish(this)
                    ? "No executable commands were produced. The original text is saved; re-analyse it or review warnings."
                    : "لا توجد أوامر مترجمة قابلة للتنفيذ. النص الأصلي محفوظ؛ أعد التحليل أو راجع التحذيرات.", 13, TEXT, false);
            empty.setPadding(dp(10), dp(12), dp(10), dp(12));
            empty.setBackground(glassShape(Color.argb(155, 12, 24, 38), Color.argb(150, 35, 226, 244), 1, 12));
            blocks.addView(empty, wrap());
        }
        for (int i = 0; i < compiled.length(); i++) {
            JSONObject child = compiled.optJSONObject(i);
            if (child == null) continue;
            final int index = i;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            card.setBackground(glassShape(Color.argb(220, 14, 27, 40), Color.argb(190, 35, 226, 244), 1, 12));
            TextView title = label((i + 1) + ". " + child.optString("label", child.optString("type", "خطوة")), 13, TEXT, true);
            card.addView(title, wrap());
            TextView meta = label("النوع: " + child.optString("type", "-") + " • قبل: " + child.optInt("beforeDelay", 0)
                    + " ms • بعد: " + child.optInt("afterDelay", 0) + " ms • محاولات الفشل: "
                    + child.optInt("retryOnFailureCount", 1) + " • " + (child.optBoolean("enabled", true) ? "مفعّل" : "معطّل"), 10, MUTED, false);
            LinearLayout.LayoutParams metaLp = wrap(); metaLp.topMargin = dp(2); card.addView(meta, metaLp);
            Button edit = button(BotnaI18n.translate(this, "تغيير الإعدادات"), false);
            edit.setTextSize(11);
            LinearLayout.LayoutParams editLp = wrap(); editLp.topMargin = dp(5); card.addView(edit, editLp);
            View.OnClickListener opener = v -> editCompiledBlockSettings(parentId, index);
            title.setOnClickListener(opener); card.setOnClickListener(opener); edit.setOnClickListener(opener);
            LinearLayout.LayoutParams cardLp = wrap(); cardLp.topMargin = dp(6); blocks.addView(card, cardLp);
        }
        if (warnings != null && warnings.length() > 0) {
            TextView warningTitle = label(BotnaI18n.isEnglish(this) ? "Translation notes" : "ملاحظات الترجمة", 12, Color.rgb(255, 190, 110), true);
            LinearLayout.LayoutParams wt = wrap(); wt.topMargin = dp(8); blocks.addView(warningTitle, wt);
            for (int i = 0; i < warnings.length(); i++) {
                TextView warning = label("• " + warnings.optString(i), 11, MUTED, false);
                warning.setPadding(dp(3), dp(3), dp(3), dp(3));
                blocks.addView(warning, wrap());
            }
        }
        scroll.addView(blocks, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.topMargin = dp(7); root.addView(scroll, scrollLp);

        LinearLayout footer = horizontal();
        Button reanalyse = button(BotnaI18n.translate(this, "إعادة تحليل النص"), true);
        Button closeBottom = button(BotnaI18n.translate(this, "إغلاق"), false);
        LinearLayout.LayoutParams footerButton = new LinearLayout.LayoutParams(0, dp(44), 1f);
        footerButton.setMargins(dp(3), dp(6), dp(3), dp(1));
        footer.addView(reanalyse, new LinearLayout.LayoutParams(footerButton));
        footer.addView(closeBottom, new LinearLayout.LayoutParams(footerButton));
        root.addView(footer, new LinearLayout.LayoutParams(-1, dp(52)));

        AlertDialog dialog = new AlertDialog.Builder(this).setView(root).create();
        compiledBlockDialog = dialog;
        dialog.setOnDismissListener(d -> { if (compiledBlockDialog == dialog) compiledBlockDialog = null; });
        close.setOnClickListener(v -> dialog.dismiss());
        closeBottom.setOnClickListener(v -> dialog.dismiss());
        reanalyse.setOnClickListener(v -> {
            dialog.dismiss();
            JSONObject now = findStepById(ensureScript(), parentId);
            if (now != null) editCustomInstructions(now);
        });
        try {
            Window w = dialog.getWindow();
            if (w != null) {
                w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
            dialog.show();
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                shown.getDecorView().setBackgroundColor(Color.TRANSPARENT);
                android.util.DisplayMetrics dm = realScreenMetrics();
                int targetHeight = Math.max(dp(320), Math.min(dp(760), dm.heightPixels - dp(42)));
                root.setMinimumHeight(targetHeight - dp(16));
                ViewGroup.LayoutParams rootParams = root.getLayoutParams();
                if (rootParams != null) { rootParams.height = targetHeight - dp(16); root.setLayoutParams(rootParams); }
                shown.setLayout(Math.max(dp(270), Math.min(dp(540), dm.widthPixels - dp(24))), targetHeight);
            }
        } catch (Exception e) {
            report("تعذر عرض الأوامر المترجمة: " + e.getClass().getSimpleName());
        }
    }

    private void editCompiledBlockSettings(String parentId, int blockIndex) {

        if (compiledBlockDialog != null && compiledBlockDialog.isShowing()) {
            try { compiledBlockDialog.dismiss(); } catch (Exception ignored) { }
        }
        JSONObject script=ensureScript(); JSONObject parent=findStepById(script,parentId); if(parent==null)return;
        JSONArray blocks=parent.optJSONArray("compiledSteps"); if(blocks==null||blockIndex<0||blockIndex>=blocks.length())return;
        JSONObject block=blocks.optJSONObject(blockIndex);if(block==null)return;
        String type=block.optString("type","");
        ArrayList<String> options=new ArrayList<>();
        options.add("انتظار قبل التنفيذ"); options.add("انتظار بعد التنفيذ");
        if(TYPE_IMAGE.equals(type)){options.add("دقة مطابقة الصورة");options.add("عدد محاولات العثور على الصورة");options.add("الفاصل بين المحاولات");}
        else if(TYPE_WAIT.equals(type)){options.add("مدة الانتظار");}
        else if(TYPE_LONG_PRESS.equals(type)){options.add("مدة الضغط المطوّل");options.add("إحداثيات النقر");}
        else if(TYPE_TAP.equals(type)){options.add("إحداثيات النقر");}
        else if(TYPE_TYPE_TEXT.equals(type)||TYPE_TEXT.equals(type)){options.add("النص");if(TYPE_TEXT.equals(type))options.add("عدد المحاولات");}
        else if(TYPE_WAIT_TEXT.equals(type)){options.add("النص المنتظر");options.add("عدد المحاولات");options.add("الفاصل بين المحاولات");}
        else if(TYPE_WAIT_INPUT.equals(type)){options.add("عدد المحاولات");options.add("الفاصل بين المحاولات");}
        else if(TYPE_SCROLL.equals(type)){options.add("اتجاه التمرير");options.add("مدة التمرير");}
        else if(TYPE_SWIPE.equals(type)){options.add("إحداثيات السحب");options.add("مدة السحب");}
        else if(TYPE_LAUNCH_APP.equals(type)){options.add("اسم التطبيق");}
        else if(TYPE_OPEN_URL.equals(type)){options.add("الرابط");}
        else if(TYPE_GLOBAL_ACTION.equals(type)){options.add("إجراء النظام");}
        else if(TYPE_CHECK_INTERNET.equals(type)||TYPE_WAIT_INTERNET.equals(type)){options.add("عدد المحاولات");options.add("الفاصل بين المحاولات");}
        else if(TYPE_REPEAT_START.equals(type)){options.add("عدد التكرارات");}
        options.add("إعادة المحاولة عند الفشل");
        options.add("الفاصل بين محاولات الفشل");
        options.add(block.optBoolean("enabled",true)?"تعطيل هذا البلوك":"تفعيل هذا البلوك");
        options.add("حذف هذا البلوك");
        final String[] items=options.toArray(new String[0]);
        showChoice(BotnaI18n.isEnglish(this) ? "Change block settings " + (blockIndex + 1) : "تغيير إعدادات البلوك " + (blockIndex + 1), items, choice -> {
            String selected=items[choice];
            if(selected.equals("انتظار قبل التنفيذ")){promptNumber("انتظار قبل التنفيذ (0–60000 ms)",block.optInt("beforeDelay",0),0,60000,v->updateCompiledBlock(parentId,blockIndex,"beforeDelay",v));return;}
            if(selected.equals("انتظار بعد التنفيذ")){promptNumber("انتظار بعد التنفيذ (0–60000 ms)",block.optInt("afterDelay",0),0,60000,v->updateCompiledBlock(parentId,blockIndex,"afterDelay",v));return;}
            if(selected.equals("دقة مطابقة الصورة")){promptNumber("دقة المطابقة (0–100%)",block.optInt("threshold",85),0,100,v->updateCompiledBlock(parentId,blockIndex,"threshold",v));return;}
            if(selected.equals("عدد محاولات العثور على الصورة")||selected.equals("عدد المحاولات")){promptNumber("عدد المحاولات (أي عدد صحيح موجب)",block.optInt("retries",block.optInt("attempts",3)),1,Integer.MAX_VALUE,v->updateCompiledBlock(parentId,blockIndex,TYPE_IMAGE.equals(type)?"retries":"attempts",v));return;}
            if(selected.equals("الفاصل بين المحاولات")){promptNumber("الفاصل بين المحاولات (ms)",block.optInt("retryDelay",block.optInt("intervalMs",500)),100,60000,v->updateCompiledBlock(parentId,blockIndex,TYPE_IMAGE.equals(type)?"retryDelay":"intervalMs",v));return;}
            if(selected.equals("مدة الانتظار")){promptNumber("مدة الانتظار (ms)",block.optInt("delay",500),0,60000,v->updateCompiledBlock(parentId,blockIndex,"delay",v));return;}
            if(selected.equals("مدة الضغط المطوّل")){promptNumber("مدة الضغط المطوّل (ms)",block.optInt("duration",800),300,5000,v->updateCompiledBlock(parentId,blockIndex,"duration",v));return;}
            if(selected.equals("إحداثيات النقر")){promptText("الإحداثيات النسبية x,y من 0 إلى 100",block.optInt("xPercent",50)+","+block.optInt("yPercent",50),v->{try{String[] xy=v.split(",");if(xy.length!=2)throw new NumberFormatException();JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;putInt(current,"xPercent",clampInt(Integer.parseInt(xy[0].trim()),0,100));putInt(current,"yPercent",clampInt(Integer.parseInt(xy[1].trim()),0,100));saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));}catch(Exception ex){report("اكتب الإحداثيات بالشكل 50,40.");}});return;}
            if(selected.equals("النص")||selected.equals("النص المنتظر")){promptText("النص",block.optString("value",""),v->{JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;putString(current,"value",v);putString(current,"label",(TYPE_WAIT_TEXT.equals(type)?"انتظار ظهور النص: ":TYPE_TEXT.equals(type)?"نقر نص: ":"كتابة نص: ")+v);saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));});return;}
            if(selected.equals("اتجاه التمرير")){showChoice("اتجاه التمرير",new String[]{"للأعلى","للأسفل","لليمين","لليسار"},c->{String[] values={"up","down","right","left"};JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;putString(current,"direction",values[c]);putString(current,"label","تمرير "+values[c]);saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));});return;}
            if(selected.equals("مدة التمرير")){promptNumber("مدة التمرير (ms)",block.optInt("duration",450),100,5000,v->updateCompiledBlock(parentId,blockIndex,"duration",v));return;}
            if(selected.equals("إحداثيات السحب")){promptText("x1,y1,x2,y2 — نسب من 0 إلى 100",block.optInt("x1",50)+","+block.optInt("y1",75)+","+block.optInt("x2",50)+","+block.optInt("y2",25),v->{try{String[] xy=v.split(",");if(xy.length!=4)throw new NumberFormatException();JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;String[] keys={"x1","y1","x2","y2"};for(int j=0;j<4;j++)putInt(current,keys[j],clampInt(Integer.parseInt(xy[j].trim()),0,100));putString(current,"label","سحب من "+current.optInt("x1",50)+"%, "+current.optInt("y1",75)+"% إلى "+current.optInt("x2",50)+"%, "+current.optInt("y2",25)+"%");saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));}catch(Exception ex){report("اكتب أربع قيم بالشكل 50,75,50,25.");}});return;}
            if(selected.equals("مدة السحب")){promptNumber("مدة السحب (ms)",block.optInt("duration",450),100,5000,v->updateCompiledBlock(parentId,blockIndex,"duration",v));return;}
            if(selected.equals("اسم التطبيق")){promptText("اسم التطبيق",block.optString("appName",""),v->{JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;putString(current,"appName",v);putString(current,"label","فتح تطبيق: "+v);saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));});return;}
            if(selected.equals("الرابط")){promptText("رابط HTTP/HTTPS",block.optString("url",""),v->{if(!v.startsWith("https://")&&!v.startsWith("http://")){report("الرابط يجب أن يبدأ بـ HTTP أو HTTPS.");return;}JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;putString(current,"url",v);putString(current,"label","فتح رابط: "+v);saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));});return;}
            if(selected.equals("إجراء النظام")){showChoice("إجراء النظام",new String[]{"الإشعارات","الإعدادات السريعة","التطبيقات الأخيرة","إغلاق الإشعارات"},c->{String[] values={"notifications","quick_settings","recent_apps","dismiss_notifications"};JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;putString(current,"systemAction",values[c]);saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));});return;}
            if(selected.equals("عدد التكرارات")){promptNumber("عدد التكرارات (أي عدد صحيح موجب)",block.optInt("repeatCount",2),1,Integer.MAX_VALUE,v->updateCompiledBlock(parentId,blockIndex,"repeatCount",v));return;}
            if(selected.equals("إعادة المحاولة عند الفشل")){promptNumber("إجمالي المحاولات عند الفشل (1 = بلا إعادة)",block.optInt("retryOnFailureCount",1),1,Integer.MAX_VALUE,v->{JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;putInt(current,"retryOnFailureCount",v);saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));});return;}
            if(selected.equals("الفاصل بين محاولات الفشل")){promptNumber("الفاصل بين محاولات الفشل (ms)",block.optInt("retryOnFailureDelay",700),100,60000,v->{JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;putInt(current,"retryOnFailureDelay",v);saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));});return;}
            JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;
            if(selected.startsWith("تعطيل")||selected.startsWith("تفعيل")){putBoolean(current,"enabled",!current.optBoolean("enabled",true));saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));}
            else if(selected.equals("حذف هذا البلوك")){blocks.remove(blockIndex);try{parent.put("compiledSteps",blocks);}catch(JSONException ignored){}saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));}
                });
    }

    private JSONObject findCompiledBlock(String parentId,int index){JSONObject parent=findStepById(ensureScript(),parentId);JSONArray a=parent==null?null:parent.optJSONArray("compiledSteps");return a==null?null:a.optJSONObject(index);}
    private void saveCompiledBlockParent(String parentId){JSONObject script=ensureScript();JSONObject parent=findStepById(script,parentId);if(parent!=null)BotnaStore.saveSteps(this,script,BotnaStore.steps(script));}
    private void updateCompiledBlock(String parentId,int index,String key,int value){JSONObject block=findCompiledBlock(parentId,index);if(block==null)return;putInt(block,key,value);saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));}

    private String normalizeArabicCommand(String raw) {
        return BotnaArabicDialect.normalize(raw);
    }

    private boolean compileYouTubeSongRequest(String original, JSONArray out, List<String> warnings) {
        if (original == null) return false;
        String norm = normalizeArabicCommand(original);
        boolean youtube = norm.contains("يوتيوب") || norm.contains("youtube");
        boolean search = norm.contains("ابحث") || norm.contains("دور") || norm.contains("فتش") || norm.contains("بحث");
        boolean song = norm.contains("اغنيه") || norm.contains("موسيقى") || norm.contains("انشوده") || norm.contains("نشيد") || norm.contains("song") || norm.contains("music");
        boolean play = norm.contains("شغلها") || norm.contains("شغله") || norm.contains("شغل المقطع") || norm.contains("شغل الاغنيه") || norm.contains("play it") || norm.contains("شغل الفيديو");
        if (!youtube || !search || !song || !play) return false;
        java.util.regex.Matcher titleMatcher = java.util.regex.Pattern.compile(
                "(?iu)(?:أغنية|اغنية|اغنيه|انشودة|نشيد|موسيقى|song|music)\\s*[:：]?\\s*[\\(«\\\"']?\\s*(.+?)\\s*[\\)»\\\"']*\\s*(?:ثم\\s*|و\\s*)?(?:شغّلها|شغلها|شغله|شغّل المقطع|شغل المقطع|شغل الفيديو|شغل|play(?:\\s+it)?)\\s*$")
                .matcher(original);
        if (!titleMatcher.find()) {
            warnings.add("فهمت أنك تريد تشغيل أغنية من يوتيوب، لكن اسم الأغنية لم يُفصل بثقة. اكتبها مثل: «افتح يوتيوب وابحث عن أغنية (اسم الأغنية) وشغلها».");
            return true;
        }
        String title = titleMatcher.group(1).trim().replaceAll("^[\\(«\\\"']+|[\\)»\\\"']+$", "").trim();
        title = title.replaceAll("(?iu)\\s+(?:ثم\\s*)?(?:و)?(?:شغّلها|شغلها|شغله|play(?:\\s+it)?)\\s*$", "").trim();
        if (title.isEmpty()) {
            warnings.add("اسم الأغنية فارغ؛ لم أضف نقرات قد تصيب نتيجة عشوائية.");
            return true;
        }
        JSONObject launch = compiledStep(TYPE_LAUNCH_APP, "فتح تطبيق يوتيوب"); putString(launch, "appName", "YouTube"); putInt(launch, "afterDelay", 2200); out.put(launch);
        JSONObject searchButton = compiledStep(TYPE_TEXT, "فتح حقل البحث في يوتيوب"); putString(searchButton, "value", "بحث|Search|Find"); putInt(searchButton, "retries", 3); putInt(searchButton, "retryDelay", 650); putInt(searchButton, "afterDelay", 350); out.put(searchButton);
        JSONObject input = compiledStep(TYPE_WAIT_INPUT, "انتظار حقل بحث يوتيوب"); putInt(input, "attempts", 16); putInt(input, "intervalMs", 400); out.put(input);
        JSONObject typed = compiledStep(TYPE_TYPE_TEXT, "كتابة اسم الأغنية: " + title); putString(typed, "value", title); out.put(typed);
        JSONObject enter = compiledStep(TYPE_PRESS_ENTER, "تنفيذ البحث عن الأغنية"); putInt(enter, "afterDelay", 500); out.put(enter);
        JSONObject resultsWait = compiledStep(TYPE_WAIT, "انتظار نتائج يوتيوب"); putInt(resultsWait, "delay", 2800); out.put(resultsWait);
        JSONObject resultText = compiledStep(TYPE_WAIT_TEXT, "انتظار ظهور نتيجة باسم الأغنية"); putString(resultText, "value", title); putInt(resultText, "attempts", 12); putInt(resultText, "intervalMs", 500); out.put(resultText);
        JSONObject openResult = compiledStep(TYPE_TEXT, "فتح نتيجة الأغنية: " + title); putString(openResult, "value", title); putInt(openResult, "retries", 3); putInt(openResult, "retryDelay", 750); putInt(openResult, "afterDelay", 1800); out.put(openResult);
        JSONObject readBack = compiledStep(TYPE_READ_SCREEN_TEXT, "قراءة واجهة تشغيل الأغنية للتحقق من النتيجة"); out.put(readBack);
        warnings.add("الخطوات تبحث عن عنوان الأغنية الظاهر وتفتح النتيجة المطابقة نصيًا. قد تختلف تسمية زر البحث أو عنوان النتيجة حسب لغة يوتيوب؛ إذا لم تكشف الواجهة النصوص عبر إمكانية الوصول، راجع لقطة السجل واستخدم الوكيل البصري أو قالب صورة. لا ينقر بوتنا على نتيجة عشوائية عند فشل المطابقة.");
        return true;
    }

    private JSONArray compileCustomInstructions(String original, List<String> warnings) {
        JSONArray result = new JSONArray();
        String clean = original.replace("\r", "\n");
        if (compileYouTubeSongRequest(clean, result, warnings)) return result;

        final String conditionalThenToken = "__botna_conditional_then__";
        clean = clean.replaceAll("(?iu)((?:اذا|إذا)\\s+(?:وجدتها|وجدته|ظهرت|لقيتها|لم\\s+تجدها|لم\\s+تجده|ما\\s+لقيتها|لم\\s+تظهر).*?)\\s+ثم\\s+", "$1 " + conditionalThenToken + " ");
        clean = clean.replaceAll("(?iu)\\s+و(?=(?:ابحث|ابحثلي|ابحث\\s+لي|دور|دورلي|دوّر|فتش|فتشلي|اكتب|اكتبلي|اغلق|اقفل|اسحب|مرر|انقر|انقرلي|اضغط|اكبس|افتح|افتحلي|شغل|شغللي|اعمل|اعمللي|سوي|سويلي|جيبلي|هاتلي|اعطيني|اعطني|عطيني|ورجيني|فرجيني|افحص|افحصلي|اختبر|تحقق|روح|اقرا|اقرأ|اعرض|خبرني|خليك|انتظر|استنى|راقب|تابع|قيس))", ";");
        clean = clean.replaceAll("(?iu)[،,]\\s*(?=(?:اذا|إذا|لو|لما|افتح|افحص|اختبر|ابحث|دور|فتش|اكتب|امسح|اغلق|اضغط|اكبس|انقر|اسحب|مرر|انتظر|استنى|اعطيني|هات|جيب|ورجيني|فرجيني|اقرا|اقرأ|اعرض|خبرني|شغل))", ";");

        String[] clauses = clean.split("(?iu)\\n+|[؛;]+|\\s+(?:ثم|وبعدين|بعدين|بعد ذلك|وبعد ذلك|بعدها|وبعدها|ومن ثم|وعقبها|وبعديها|بعديها|عقبها)\\s+");
        Deque<String> structure = new ArrayDeque<>();
        for (String raw : clauses) {
            String clause = raw.trim(); if (clause.isEmpty()) continue;
            String norm = normalizeArabicCommand(clause);
            if (result.length() > 0 && norm.matches("(?iu).*(?:اذا|لو|لما|اذا ما).*?(?:فشل|فشلت|ما نجح|لم ينجح|ما زبط|ما ضبط|ما اشتغل|ما اشتغلت).*?(?:حاول|حاول كمان|كرر|اعد المحاوله|عيد المحاوله|جرب مرة ثانية|جرب مره ثانيه).*") ) {
                java.util.regex.Matcher requestedCount = java.util.regex.Pattern.compile("(?iu)(\\d+)\\s*(?:مره|مرات|محاوله|محاولات)?").matcher(norm);
                JSONObject previous = result.optJSONObject(result.length() - 1);
                if (requestedCount.find() && previous != null) {
                    int attempts = Math.max(1, parseIntSafe(requestedCount.group(1), 1));
                    String previousType = previous.optString("type", "");
                    if (TYPE_IF_SUCCESS.equals(previousType) || TYPE_IF_FAIL.equals(previousType) || TYPE_ELSE.equals(previousType)
                            || TYPE_END_IF.equals(previousType) || TYPE_REPEAT_START.equals(previousType) || TYPE_REPEAT_END.equals(previousType)) {
                        warnings.add("تعليمة إعادة المحاولة جاءت بعد علامة تنظيمية وليست بعد إجراء. ضعها مباشرة بعد الإجراء الذي تريد إعادته عند الفشل.");
                    } else {
                        putInt(previous, "retryOnFailureCount", attempts);
                        putInt(previous, "retryOnFailureDelay", 700);
                        warnings.add("عند فشل «" + previous.optString("label", previousType) + "» سيعيد بوتنا المحاولة حتى " + attempts + " محاولات إجمالية، بفاصل 700 ms. قد لا يمكن إعادة الإجراء إذا أوقفه تطبيق آخر أو تغيّرت الشاشة بشكل جذري.");
                    }
                } else {
                    warnings.add("فهمت أنك تريد إعادة المحاولة عند الفشل، لكن لم أجد عدد المحاولات. اكتب مثلًا: «إذا فشل، حاول 3 مرات».");
                }
                continue;
            }
            if (norm.equals("وإلا") || norm.equals("والا") || norm.equals("و الا")) {
                if (structure.isEmpty() || !"IF".equals(structure.peek())) warnings.add("وإلا بلا شرط مفتوح: " + clause);
                else addCompiled(result, TYPE_ELSE, "وإلا");
                continue;
            }
            if (norm.contains("نهاية الشرط") || norm.equals("اغلق الشرط")) {
                if (structure.isEmpty() || !"IF".equals(structure.peek())) warnings.add("نهاية شرط بلا بداية: " + clause);
                else { addCompiled(result, TYPE_END_IF, "نهاية الشرط"); structure.pop(); }
                continue;
            }
            if (norm.contains("نهاية التكرار") || norm.contains("انهي التكرار")) {
                if (structure.isEmpty() || !"REPEAT".equals(structure.peek())) warnings.add("نهاية تكرار بلا بداية: " + clause);
                else { addCompiled(result, TYPE_REPEAT_END, "نهاية التكرار"); structure.pop(); }
                continue;
            }
            java.util.regex.Matcher repeat = java.util.regex.Pattern.compile("(?iu)^\\s*كرر\\s*(\\d+)\\s*(?:مرة|مرات|تكرار)?\\s*[:：]?\\s*(.*)$").matcher(norm);
            if (repeat.matches()) {
                int count=Math.max(1,parseIntSafe(repeat.group(1),2));
                addCompiledInt(result,TYPE_REPEAT_START,"بدء تكرار ×"+count,"repeatCount",count); structure.push("REPEAT");
                String rest=repeat.group(2).trim(); if(rest.isEmpty()) continue; norm=rest; clause=rest;
            }

            java.util.regex.Matcher imageRef = java.util.regex.Pattern.compile("(?iu)(?:صورة|الهدف)\\s*(?:رقم\\s*)?(\\d+)").matcher(norm);
            if (imageRef.find()) {
                int number=parseIntSafe(imageRef.group(1),-1);
                JSONArray lib=BotnaStore.imageLibrary(this);
                if(number<1 || number>lib.length()) { warnings.add("رقم صورة غير موجود في المكتبة: "+number); continue; }
                JSONObject image=lib.optJSONObject(number-1);
                if(image==null) { warnings.add("تعذر قراءة صورة المكتبة رقم "+number); continue; }
                boolean successCondition = norm.contains("اذا وجدتها") || norm.contains("اذا وجدته") || norm.contains("اذا ظهرت") || norm.contains("اذا لقيتها");
                boolean failureCondition = norm.contains("اذا لم تجدها") || norm.contains("اذا لم تجده") || norm.contains("اذا ما لقيتها") || norm.contains("اذا لم تظهر");
                boolean hasWait = norm.contains("انتظر") || norm.contains("توقف قليلا") || norm.contains("انتظار");
                boolean hasConditionalAction = hasWait || norm.contains("رجوع") || norm.contains("الرئيسية") || norm.contains("اكتب") || norm.contains("نقر نص") || norm.contains("اضغط على نص") || norm.contains(conditionalThenToken);
                boolean suppressTap = norm.contains("بدون ضغط") || norm.contains("لا تضغط") || ((successCondition || failureCondition) && hasConditionalAction);
                addLibraryImageStep(result,image,number,!suppressTap);
                if ((successCondition || failureCondition) && hasConditionalAction) {
                    addCompiled(result, failureCondition ? TYPE_IF_FAIL : TYPE_IF_SUCCESS,
                            failureCondition ? "إذا لم يُعثر على الصورة" : "إذا نجح العثور على الصورة");
                    structure.push("IF");
                    String action = norm.replaceFirst("(?iu)^.*?(?:اذا\\s+وجدتها|اذا\\s+وجدته|اذا\\s+ظهرت|اذا\\s+لقيتها|اذا\\s+لم\\s+تجدها|اذا\\s+لم\\s+تجده|اذا\\s+ما\\s+لقيتها|اذا\\s+لم\\s+تظهر)", "").trim();
                    String[] actions = action.split("(?iu)\\s*(?:" + conditionalThenToken + "|\\s+ثم\\s+|،|,)\\s*");
                    for (String part : actions) if (!part.trim().isEmpty()) parseCustomAction(part.trim(), result, warnings, lib);
                    addCompiled(result,TYPE_END_IF,"نهاية الشرط"); structure.pop();
                }
                continue;
            }
            if (norm.startsWith("اذا نجح") || norm.startsWith("اذا زبط") || norm.startsWith("اذا ضبط") || norm.startsWith("اذا تمت") || norm.startsWith("لما ينجح") || norm.startsWith("لو نجح") || norm.startsWith("اذا وجدت") || norm.startsWith("اذا تحقق")) {
                addCompiled(result,TYPE_IF_SUCCESS,"إذا نجح الإجراء السابق"); structure.push("IF");
                String rest=norm.replaceFirst("(?iu)^(?:(?:اذا|لو)\\s+(?:نجح(?:ت)?|زبط(?:ت)?|ضبط(?:ت)?|وجدت(?:ها|ه)?|تحقق(?:ت)?|تمت)|لما\\s+ينجح)(?:\\s+الاجراء السابق)?\\s*[:：]?\\s*","").trim();
                if(!rest.isEmpty()) parseCustomAction(rest,result,warnings,BotnaStore.imageLibrary(this));
                continue;
            }
            if (norm.startsWith("اذا فشل") || norm.startsWith("اذا فشلت") || norm.startsWith("لو فشل") || norm.startsWith("لو فشلت") || norm.startsWith("اذا ما اشتغل") || norm.startsWith("لو ما اشتغل") || norm.startsWith("اذا لم تجد") || norm.startsWith("اذا لم تظهر") || norm.startsWith("اذا ما نجح") || norm.startsWith("اذا ما زبط") || norm.startsWith("اذا ما ضبط") || norm.startsWith("اذا ما في انترنت") || norm.startsWith("اذا فش انترنت")) {
                addCompiled(result,TYPE_IF_FAIL,"إذا فشل الإجراء السابق"); structure.push("IF");
                String rest=norm.replaceFirst("(?iu)^(?:(?:اذا|لو)\\s+(?:فشل(?:ت)?|ما\\s+(?:اشتغل|نجح|زبط|ضبط)|ما\\s+في\\s+(?:ال)?انترنت|فش\\s+(?:ال)?انترنت|لم\\s+(?:تجد|تظهر)))(?:\\s+الاجراء السابق)?\\s*[:：]?\\s*", "").trim();
                if (!rest.isEmpty()) {
                    if ((rest.contains("صفحة الراوتر") || rest.contains("صفحه الراوتر") || rest.contains("الراوتر")) && !rest.contains("http://") && !rest.contains("https://")) {
                        warnings.add("لم يذكر الأمر رابط صفحة الراوتر. لم أفترض عنوانًا أو زر إعادة تشغيل؛ أضف رابط الصفحة أو فعّل الوكيل البصري مع لقطة مؤكدة.");
                    } else {
                        parseCustomAction(rest,result,warnings,BotnaStore.imageLibrary(this));
                    }
                }
                continue;
            }
            if (norm.equals("اذا نجح") || norm.equals("اذا فشل")) {
                addCompiled(result,norm.contains("فشل")?TYPE_IF_FAIL:TYPE_IF_SUCCESS,norm); structure.push("IF"); continue;
            }
            parseCustomAction(clause,result,warnings,BotnaStore.imageLibrary(this));
        }
        if (!structure.isEmpty()) {
            int ifCount = 0, repeatCount = 0;
            for (String kind : structure) { if ("IF".equals(kind)) ifCount++; else if ("REPEAT".equals(kind)) repeatCount++; }
            StringBuilder advice = new StringBuilder("كانت هناك كتل مفتوحة في نهاية الأمر. ");
            if (ifCount > 0) advice.append("يلزم إضافة ").append(ifCount).append(" علامة «نهاية الشرط»؛ ");
            if (repeatCount > 0) advice.append("يلزم إضافة ").append(repeatCount).append(" علامة «نهاية التكرار»؛ ");
            advice.append("أضفت العلامات الناقصة تلقائيًا إلى نهاية النص لتفادي بنية مكسورة، لكن راجع موضع كل علامة قبل التشغيل.");
            warnings.add(advice.toString());
        }
        while(!structure.isEmpty()) {
            String kind=structure.pop();
            if("IF".equals(kind)) addCompiled(result,TYPE_END_IF,"نهاية شرط أُكملت تلقائيًا");
            else addCompiled(result,TYPE_REPEAT_END,"نهاية تكرار أُكملت تلقائيًا");
        }
        return result;
    }

    private void parseCustomAction(String command, JSONArray result, List<String> warnings, JSONArray library) {
        if (command == null || command.trim().isEmpty()) return;
        String rawCommand = command.trim();

        rawCommand = rawCommand.replaceFirst("(?iu)^\\s*(?:اعمللي|اعمل\\s+لي|اعملي|اعمل|سويلي|سوي\\s+لي|سوي|سوّي|جيبلي|جيب\\s+لي)\\s+(?=(?:بحث|فحص|اختبار|ابحث|دور|فتش|افحص|اختبر|افتح|افتحلي|انقر|اضغط|اكتب|امسح|اسحب|مرر|انتظر|تحقق|اقرا|اقرأ|قراءة))", "").trim();
        String norm = normalizeArabicCommand(rawCommand);
        if (norm.matches("(?iu)^(?:شو|ما|ماذا|كيف|ليش|لماذا|اشرح|ما معنى|شو يعني|ما فائدة|ماهو|هل)\\b.*")) {
            warnings.add("هذه العبارة تبدو سؤالًا أو طلب شرح وليست أمر تنفيذ: «" + rawCommand + "». استخدم مساعد استخدام بوتنا للإجابة عن الأسئلة، أو أعد صياغتها كإجراء مثل «افتح…» أو «اضغط…».");
            return;
        }
        if (compileNamedLibraryImageAction(norm, result, warnings, library)) return;
        if (norm.contains("زر الاكس") || norm.contains("زر اكس") || norm.contains("علامة اكس") || norm.contains("علامة x") || norm.contains("زر الإغلاق") || norm.contains("زر الاغلاق")) {
            JSONObject close = compiledStep(TYPE_TEXT, "النقر على زر الإغلاق المعرّف نصيًا"); putString(close, "value", "إغلاق|Close|×|Dismiss"); putInt(close, "retries", 2); putInt(close, "retryDelay", 500); result.put(close);
            warnings.add("استخدمت اسمًا نصيًا مثل «إغلاق» بدل تخمين إحداثيات رمز X؛ إذا كان الرمز بلا وصف وصول، يلزم الوكيل البصري أو قالب صورة لتحديده بأمان.");
            return;
        }
        if (norm.contains("ايقونة البحث") || norm.contains("ايقونه البحث") || norm.contains("شكل العدسة") || norm.contains("شكل العدسه") || norm.contains("العدسة المكبرة") || norm.contains("العدسه المكبره") || norm.contains("زر عليه عدسة") || norm.contains("زر عليه عدسه")) {
            JSONObject search = compiledStep(TYPE_TEXT, "النقر على عنصر البحث المسمّى"); putString(search, "value", "بحث|Search|Find"); putInt(search, "retries", 2); result.put(search);
            warnings.add("حوّلت وصف العدسة إلى بحث عن عنصر ميسّر اسمه «بحث». إذا كانت الأيقونة بلا نص/وصف وصول، فلن يخمّن المحلل المحلي مكانها؛ استخدم لقطة حديثة في الوكيل البصري.");
            return;
        }
        if (isLocalSpeedTestRequest(rawCommand)) {
            JSONObject step = compiledStep(TYPE_SPEED_TEST, "قياس سرعة تنزيل الإنترنت التقريبية");
            result.put(step);
            return;
        }
        if (norm.contains("نهاية الشرط") || norm.contains("نهاية التكرار")) { warnings.add("استخدم علامة نهاية الشرط/التكرار في سطر مستقل: "+norm); return; }
        if ((norm.contains("حقل الادخال") || norm.contains("حقل البحث") || norm.contains("مكان الكتابة") || norm.contains("خانة البحث"))
                && (norm.contains("انتظر") || norm.contains("استنى") || norm.contains("حتى يظهر") || norm.contains("ظهور"))) {
            JSONObject st=compiledStep(TYPE_WAIT_INPUT,"انتظار ظهور حقل إدخال قابل للكتابة"); putInt(st,"attempts",20); putInt(st,"intervalMs",500); result.put(st); return;
        }
        java.util.regex.Matcher waitText = java.util.regex.Pattern.compile("(?iu)^(?:انتظر|استنى|استنّى|انتظر حتى)\\s+(?:ظهور|يظهر|حتى يظهر)?\\s*(?:النص|عبارة)?\\s*[:：]?\\s*(.+?)\\s*$").matcher(norm);
        if (waitText.matches() && (norm.contains("ظهور") || norm.contains("النص") || norm.contains("عبارة") || norm.contains("حتى يظهر"))) {
            String value=waitText.group(1).trim().replace("«", "").replace("»", "").replace("\"", "");
            if (!value.isEmpty() && !value.contains("انترنت") && !value.contains("الانترنت") && !value.contains("حقل الادخال")) { JSONObject st=compiledStep(TYPE_WAIT_TEXT,"انتظار ظهور النص: "+value); putString(st,"value",value); putInt(st,"attempts",20); putInt(st,"intervalMs",500); result.put(st); return; }
        }
        if (norm.contains("فحص الانترنت") || norm.contains("افحص الانترنت") || norm.contains("افحصلي الانترنت")
                || norm.contains("افحصلي النت") || norm.contains("افحص النت") || norm.contains("اختبر الاتصال") || norm.contains("اختبر الانترنت")) {
            JSONObject st = compiledStep(TYPE_CHECK_INTERNET, "فحص اتصال الإنترنت"); putInt(st,"attempts",3); putInt(st,"intervalMs",5000); putInt(st,"timeoutMs",4000); result.put(st);
            if (norm.contains("باستمرار") || norm.contains("طول ما") || norm.contains("طالما") || norm.contains("كل ما")) warnings.add("المحلّل المحلي أنشأ فحص اتصال محدودًا، وليس مراقبة مستمرة. لم يفترض عنوان الراوتر أو ينفذ إعادة تشغيل دون عنوان واضح وخطوات شاشة مؤكدة.");
            return;
        }
        if ((norm.contains("انتظر") || norm.contains("استنى") || norm.contains("ضل افحص") || norm.contains("ظل افحص")) && (norm.contains("يعود الانترنت") || norm.contains("يرجع الانترنت") || norm.contains("يتصل بالانترنت") || norm.contains("الانترنت يرجع") || norm.contains("الاتصال يرجع"))) {
            JSONObject st = compiledStep(TYPE_WAIT_INTERNET, "انتظار عودة الإنترنت"); putInt(st,"attempts",120); putInt(st,"intervalMs",5000); putInt(st,"timeoutMs",4000); result.put(st); return;
        }
        if (norm.contains("نبهني") || norm.contains("اظهر رسالة") || norm.contains("أظهر رسالة") || norm.contains("شغل نغمة") || norm.contains("شغل تنبيه") || norm.contains("اعرض رسالة") || norm.contains("خبرني لما") || norm.contains("احكيلي لما")) {
            String message = norm.replaceFirst("(?iu)^.*?(?:نبهني|اظهر رسالة|شغل نغمة|شغل تنبيه|اعرض رسالة)\\s*[:：]?\\s*", "").trim();
            if (message.isEmpty()) message = "تم تنفيذ الأمر بنجاح";
            JSONObject st=compiledStep(TYPE_NOTIFY,"تنبيه: "+message); putString(st,"message",message); putBoolean(st,"sound",true); result.put(st); return;
        }
        if (norm.contains("افتح شريط الاشعارات") || norm.contains("افتح الاشعارات") || norm.contains("اسحب شريط الاشعارات") || norm.contains("نزل شريط الاشعارات")) { addGlobalActionToArray(result,"notifications","فتح لوحة الإشعارات"); return; }
        if (norm.contains("الاعدادات السريعه") || norm.contains("الإعدادات السريعة") || norm.contains("افتح الاعدادات السريعه")) { addGlobalActionToArray(result,"quick_settings","فتح الإعدادات السريعة"); return; }
        if (norm.contains("التطبيقات الاخيره") || norm.contains("التطبيقات الأخيرة") || norm.contains("افتح المهام الاخيره")) { addGlobalActionToArray(result,"recent_apps","فتح التطبيقات الأخيرة"); return; }
        if (norm.contains("اغلق جميع النوافذ") || norm.contains("اغلق كل التطبيقات") || norm.contains("سكر التطبيقات") || norm.contains("اقفل كل التطبيقات")) { addCompiled(result,TYPE_CLOSE_RECENTS,"إغلاق التطبيقات الأخيرة الظاهرة"); return; }
        if (norm.contains("اغلق الاشعارات") || norm.contains("سكر الاشعارات") || norm.contains("اخف الاشعارات") || norm.contains("اغلق شريط الاشعارات")) { addGlobalActionToArray(result,"dismiss_notifications","إغلاق لوحة الإشعارات"); return; }
        if (norm.contains("امسح حقل") || norm.contains("امسح النص") || norm.contains("احذف النص المكتوب") || norm.contains("امسح البحث")) { addCompiled(result,TYPE_CLEAR_TEXT,"مسح حقل الإدخال المركّز"); return; }
        java.util.regex.Matcher verifyText = java.util.regex.Pattern.compile("(?iu)^\\s*(?:تحقق من (?:ظهور|وجود)|تاكد من (?:ظهور|وجود)|تأكد من (?:ظهور|وجود)|افحص ظهور|تأكد ان|تاكد ان)\\s*(?:النص|كلمة|عبارة)?\\s*[:：]?\\s*[«\"']?(.*?)\\s*[»\"']?\\s*$").matcher(rawCommand);
        if(verifyText.matches() && !verifyText.group(1).trim().isEmpty()) { String value=verifyText.group(1).trim(); JSONObject st=compiledStep(TYPE_WAIT_TEXT,"التحقق من ظهور النص: "+value);putString(st,"value",value);putInt(st,"attempts",1);putInt(st,"intervalMs",250);result.put(st);return; }
        if (norm.contains("اضغط انتر") || norm.contains("اضغط enter") || norm.contains("اضغط بحث") || norm.contains("نفذ البحث") || norm.contains("زر البحث") || norm.equals("انتر") || norm.equals("enter") || norm.equals("بحث") || norm.equals("تنفيذ البحث")) { addCompiled(result,TYPE_PRESS_ENTER,"تنفيذ Enter/بحث"); return; }

        boolean explicitWebSearch = norm.contains("عبر الانترنت") || norm.contains("عبر النت") || norm.contains("على الانترنت")
                || norm.contains("على النت") || norm.contains("بالانترنت") || norm.contains("بالنت") || norm.contains("بحث بالويب")
                || norm.contains("عن طريق الانترنت") || norm.contains("عبر الويب");
        boolean searchVerb = norm.contains("ابحث") || norm.contains("دور") || norm.contains("فتش")
                || norm.contains("بحث") || norm.contains("جيبلي نتيجة البحث") || norm.contains("جيب لي نتيجة البحث");
        if (explicitWebSearch && searchVerb) {
            java.util.regex.Matcher topic = java.util.regex.Pattern.compile("(?iu).*?(?:عن|حول|بخصوص)\\s+(.+?)(?:\\s+(?:وجيبلي|و\\s*جيبلي|وجيب\\s+لي|واعطيني|وخبرني|وارسللي|ثم\\s+اعطيني|ثم\\s+خبرني|وجيب\\s+النتيجه|وجيب\\s+النتيجة).*)?$").matcher(rawCommand);
            String query = topic.matches() ? topic.group(1).trim() : "";
            query = query.replaceAll("(?iu)\\s+(?:عبر|على|بال)\\s*(?:ال)?(?:انترنت|نت|الويب)\\s*$", "")
                    .replaceAll("[«»\\\"']", "").trim();
            if (query.isEmpty()) {
                warnings.add("طلب البحث عبر الإنترنت لا يحدد موضوعًا. أضف الكلمات المراد البحث عنها، مثل: ابحثلي عبر الإنترنت عن حالة الطقس وجيبلي النتيجة.");
                return;
            }
            try {
                String url = "https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8");
                JSONObject open = compiledStep(TYPE_OPEN_URL, "البحث عبر الإنترنت عن: " + query); putString(open, "url", url); result.put(open);
                JSONObject wait = compiledStep(TYPE_WAIT, "انتظار تحميل نتائج البحث"); putInt(wait, "delay", 3200); result.put(wait);
                result.put(compiledStep(TYPE_READ_SCREEN_TEXT, "قراءة النصوص الظاهرة في نتائج البحث"));
                warnings.add("سيقرأ بوتنا النصوص التي تعرضها صفحة النتائج عبر إمكانية الوصول. إذا أخفى المتصفح النص أو لم يحمّله، افتح النتائج يدويًا أو استخدم نموذجًا بصريًا.");
            } catch (Exception ex) { warnings.add("تعذر تجهيز رابط البحث: " + ex.getMessage()); }
            return;
        }
        if (norm.contains("اقرا الشاشة") || norm.contains("اقرأ الشاشة") || norm.contains("اقرا النتيجة")
                || norm.contains("اقرا النتائج") || norm.contains("اعطيني نتيجة البحث") || norm.contains("جيبلي النتيجة")
                || norm.contains("جيب لي النتيجة") || norm.contains("خبرني شو طلع") || norm.contains("شو ظاهر على الشاشة")
                || norm.contains("اقرا النص الظاهر") || norm.contains("اعطيني النص الظاهر")
                || norm.contains("جيبلي نتيجة البحث") || norm.contains("جيب لي نتيجة البحث")
                || norm.contains("هات نتيجة البحث") || norm.contains("اعرض نتيجة البحث")) {
            result.put(compiledStep(TYPE_READ_SCREEN_TEXT, "قراءة النصوص الظاهرة في الشاشة الحالية")); return;
        }
        java.util.regex.Matcher inspectUrl = java.util.regex.Pattern.compile("(?iu)^.*?(https?://\\S+).*$").matcher(rawCommand);
        if (inspectUrl.matches() && (norm.contains("محتوى") || norm.contains("داخل الرابط") || norm.contains("شو في")
                || norm.contains("ايش في") || norm.contains("اش في") || norm.contains("اقرا") || norm.contains("افحص")
                || norm.contains("جيب لي") || norm.contains("جيبلي") || norm.contains("خبرني")
                || norm.contains("اعطيني النتيجة") || norm.contains("اعطيني النتيجه") || norm.contains("لخص"))) {
            String url = inspectUrl.group(1).replaceAll("[،,؛;.)]+$", "");
            JSONObject open = compiledStep(TYPE_OPEN_URL, "فتح الرابط لقراءة محتواه: " + url); putString(open, "url", url); result.put(open);
            JSONObject wait = compiledStep(TYPE_WAIT, "انتظار تحميل الصفحة"); putInt(wait, "delay", 3000); result.put(wait);
            result.put(compiledStep(TYPE_READ_SCREEN_TEXT, "قراءة النصوص الظاهرة في الرابط"));
            warnings.add("تُقرأ النصوص التي تتيحها واجهة الوصول فقط؛ قد لا تتوفر النصوص المخفية أو المحتوى المرسوم داخل صورة.");
            return;
        }
        if (norm.startsWith("ابحث عن ") || norm.startsWith("ابحثلي عن ") || norm.startsWith("دور على ") || norm.startsWith("دورلي على ") || norm.startsWith("فتش عن ") || norm.startsWith("دوّر على ")
                || norm.startsWith("بحث عن ") || norm.startsWith("بحث على ") || norm.startsWith("بحث في ")) {
            String value = rawCommand.replaceFirst("(?iu)^\\s*(?:(?:ابحثلي|ابحث\\s+لي|ابحث|دورلي|دور\\s+لي|دور|فتش|دوّر|بحث)\\s+(?:عن|على|في))\\s*[:：]?\\s*", "").trim().replaceAll("[«»\"']", "");
            if (value.isEmpty()) { warnings.add("أمر البحث لا يحتوي على عبارة بحث. أضف الكلمات التي تريد البحث عنها."); return; }
            JSONObject clickSearch = compiledStep(TYPE_TEXT, "فتح/تحديد حقل البحث"); putString(clickSearch, "value", "بحث"); putInt(clickSearch, "retries", 2); result.put(clickSearch);
            JSONObject waitInput = compiledStep(TYPE_WAIT_INPUT, "انتظار حقل البحث القابل للكتابة"); putInt(waitInput, "attempts", 20); putInt(waitInput, "intervalMs", 500); result.put(waitInput);
            JSONObject typed = compiledStep(TYPE_TYPE_TEXT, "كتابة عبارة البحث: " + value); putString(typed, "value", value); result.put(typed);
            addCompiled(result, TYPE_PRESS_ENTER, "تنفيذ البحث");
            return;
        }
        java.util.regex.Matcher openUrl = java.util.regex.Pattern.compile("(?iu)^.*?(https?://\\S+).*$").matcher(rawCommand);
        if (openUrl.matches() && (norm.contains("افتح") || norm.contains("اذهب") || norm.contains("الرابط") || norm.contains("صفحة الراوتر"))) {
            String url=openUrl.group(1).replaceAll("[،,؛;.)]+$", ""); JSONObject st=compiledStep(TYPE_OPEN_URL,"فتح الرابط: "+url); putString(st,"url",url); result.put(st); return;
        }
        if ((norm.contains("انتظر حتى تنتهي اللعبة") || norm.contains("انتظر حتى تخلص اللعبة") || norm.contains("انتظر حتى ينتهي التحميل") || norm.contains("حتى يكتمل التحميل") || norm.contains("حتى تفتح اللعبة") || norm.contains("انتظر حتى تخلص") || norm.contains("لحد ما تفتح اللعبة") || norm.contains("لما تفتح اللعبة") || norm.contains("حتى يخلص تحميل") || norm.contains("حتى ينتهي تحميل") || norm.contains("حتى تكتمل اللعبة") || norm.contains("اكتمال تحميل اللعبة"))) {
            int delay = 15000;
            addCompiledInt(result,TYPE_WAIT,"انتظار تقديري لتحميل اللعبة (15 ثانية)","delay",delay);
            warnings.add("تم وضع انتظار تقديري مدته 15 ثانية لأن اكتمال تحميل اللعبة لا يوفّر دائمًا علامة نصية يمكن للمحلل المحلي التحقق منها. لانتظار أدق، حدّد نصًا أو صورة تظهر عند اكتمال التحميل.");
            return;
        }
        if (norm.contains("انتظر") || norm.contains("انتظار") || norm.contains("توقف قليلا") || norm.contains("استنى") || norm.contains("استنّى")) {
            int delay=parseDelayMs(norm);
            addCompiledInt(result,TYPE_WAIT,"انتظار "+delay+" ms","delay",delay); return;
        }
        java.util.regex.Matcher openApp = java.util.regex.Pattern.compile("(?iu)^(?:افتح|افتحلي|افتح لي|شغّل|شغل|شغللي|شغل لي|ادخل على|روح على|روح ل|روح الى|روح إلى|اذهب الى|اذهب إلى|وديني على)\\s+(?:تطبيق\\s+)?(.+?)\\s*$").matcher(rawCommand);
        if (openApp.matches() && !norm.contains("الشاشة الرئيسية") && !norm.equals("افتح الرئيسية")) {
            String appName = openApp.group(1).trim();
            if (appName.isEmpty()) warnings.add("لم يُذكر اسم التطبيق المراد فتحه.");
            else { JSONObject st = compiledStep(TYPE_LAUNCH_APP, "فتح تطبيق: " + appName); putString(st, "appName", appName); result.put(st); }
            return;
        }
        if (norm.contains("الرئيسية") || norm.contains("الشاشة الرئيسية") || norm.equals("home")) { addCompiled(result,TYPE_HOME,"الرئيسية"); return; }
        if (norm.contains("رجوع") || norm.contains("ارجع") || norm.contains("للخلف") || norm.equals("back")) { addCompiled(result,TYPE_BACK,"رجوع"); return; }
        java.util.regex.Matcher longPress = java.util.regex.Pattern.compile("(?iu).*(?:ضغط\\s*مطول|اضغط\\s*مطولا|اضغط\\s*مطولاً|ضغط\\s*طويل)\\s*(?:على\\s*)?(?:موقع\\s*)?(\\d+)\\s*[,،]\\s*(\\d+)(?:\\s*(?:لمدة|مده)\\s*(\\d+)\\s*(?:ms|مللي\\s*ثانية|ثانية|ثواني)?)?.*").matcher(norm);
        if(longPress.matches()) {
            int x=clampInt(parseIntSafe(longPress.group(1),50),0,100), y=clampInt(parseIntSafe(longPress.group(2),50),0,100);
            int duration=longPress.group(3)==null?800:clampInt(parseIntSafe(longPress.group(3),800),300,5000);
            if(longPress.group(3)!=null && norm.contains("ثان") && !norm.contains("مللي")) duration=clampInt(duration*1000,300,5000);
            JSONObject st=compiledStep(TYPE_LONG_PRESS,"ضغط مطوّل عند "+x+"%، "+y+"%"); putInt(st,"xPercent",x); putInt(st,"yPercent",y); putInt(st,"duration",duration); result.put(st); return;
        }
        java.util.regex.Matcher tap = java.util.regex.Pattern.compile("(?iu).*(?:نقر|اضغط|انقر)\\s*(?:على\\s*)?(?:موقع\\s*)?(\\d+)\\s*[,،]\\s*(\\d+).*?").matcher(norm);
        if(tap.matches()) {
            int x=clampInt(parseIntSafe(tap.group(1),50),0,100), y=clampInt(parseIntSafe(tap.group(2),50),0,100);
            JSONObject st=compiledStep(TYPE_TAP,"نقر الموقع "+x+"%، "+y+"%"); putInt(st,"xPercent",x); putInt(st,"yPercent",y); result.put(st); return;
        }
        if (norm.contains("نقر نص") || norm.contains("اضغط النص") || norm.contains("انقر النص") || norm.contains("اضغط على نص") || norm.contains("انقر على النص")) {
            String value=rawCommand.replaceFirst("(?iu)^.*?(?:نقر نص|اضغط النص|انقر النص|اضغط على نص|انقر على النص)\\s*[:：]?\\s*","").replaceAll("[«»\"']","").trim();
            if(value.isEmpty()) warnings.add("لم يُذكر النص المطلوب للنقر: "+norm);
            else { JSONObject st=compiledStep(TYPE_TEXT,"نقر نص: "+value); putString(st,"value",value); putInt(st,"retries",2); result.put(st); }
            return;
        }
        if (norm.startsWith("اكتب") || norm.startsWith("كتابة نص") || norm.startsWith("اكتب النص")) {
            String value=rawCommand.replaceFirst("(?iu)^(?:اكتب(?:\\s+النص)?|كتابة نص)\\s*[:：]?\\s*","").replaceAll("[«»\"']","").trim();
            if(value.isEmpty()) warnings.add("لم يُذكر النص المراد كتابته: "+norm);
            else { JSONObject st=compiledStep(TYPE_TYPE_TEXT,"كتابة نص: "+value); putString(st,"value",value); result.put(st); }
            return;
        }
        java.util.regex.Matcher swipe = java.util.regex.Pattern.compile("(?iu).*?(?:اسحب|حرك الشاشة|سحب)\\s+(?:من\\s*)?(\\d+)\\s*[,،]\\s*(\\d+)\\s+(?:الي|الى|لحد|لـ|ل)\\s*(\\d+)\\s*[,،]\\s*(\\d+).*?").matcher(norm);
        if(swipe.matches()) { int x1=clampInt(parseIntSafe(swipe.group(1),50),0,100),y1=clampInt(parseIntSafe(swipe.group(2),75),0,100),x2=clampInt(parseIntSafe(swipe.group(3),50),0,100),y2=clampInt(parseIntSafe(swipe.group(4),25),0,100);JSONObject st=compiledStep(TYPE_SWIPE,"سحب من "+x1+"%, "+y1+"% إلى "+x2+"%, "+y2+"%");putInt(st,"x1",x1);putInt(st,"y1",y1);putInt(st,"x2",x2);putInt(st,"y2",y2);putInt(st,"duration",450);result.put(st);return; }
        if (norm.contains("مرر") || norm.contains("تمرير") || norm.contains("اسحب")) {
            String d=norm.contains("للاسفل")||norm.contains("للأسفل")||norm.contains("اسفل")?"down":norm.contains("لليمين")||norm.contains("يمين")?"right":norm.contains("لليسار")||norm.contains("يسار")?"left":"up";
            JSONObject st=compiledStep(TYPE_SCROLL,"تمرير "+d); putString(st,"direction",d); putInt(st,"duration",450); result.put(st); return;
        }
        if (norm.matches(".*(?:الزر|الدائره|الدائرة|المربع|المربع|الشكل|الايقونة|الايقونه|العلامة|العلامه|الرمز|الدائرة الصغيرة).*(?:الازرق|الزرقاء|الاحمر|الحمراء|الاخضر|الخضراء|الاصفر|الصفراء|الابيض|البيضاء|الاسود|السوداء|الرمادي|اليمين|اليسار|شمال|يمين|فوق|تحت|بالاسفل|بالاعلى|اعلى|اسفل|جنب|بجانب).*")) {
            warnings.add("وصفت عنصرًا مرئيًا بالشكل/اللون/الموقع («" + rawCommand + "»). لا يستطيع المحلل المحلي تأكيد موضعه دون لقطة شاشة؛ فعّل إرفاق لقطة الشاشة للوكيل البصري، وسيستخدم نسبة موضع من الشاشة بدل تخمين إحداثيات غير مؤكدة.");
            return;
        }
        warnings.add("تعذّر تحويل العبارة محليًا إلى أمر مدعوم بثقة: «"+rawCommand+"». لم تُنفّذ العبارة بالتخمين. جرّب «افتحلي…»، «روح على…»، «اعمل/سوي…»، أو فعّل الوكيل البصري لوصف الأشكال.");
    }

    private int parseDelayMs(String norm) {
        String source = norm == null ? "" : norm;
        if (source.contains("ثانيتين") || source.contains("ثانيتان")) return 2000;
        if (source.contains("ثانية ونص") || source.contains("ثانيه ونص")) return 1500;
        if (source.contains("نص ثانية") || source.contains("نص ثانيه")) return 500;
        if (source.contains("ثلاث ثوان") || source.contains("ثلاثة ثوان")) return 3000;
        int waitAt = source.indexOf("انتظر");
        String waitToken = "انتظر";
        if (waitAt < 0) { waitAt = source.indexOf("استنى"); waitToken = "استنى"; }
        if (waitAt < 0) { waitAt = source.indexOf("استنّى"); waitToken = "استنّى"; }
        if (waitAt < 0) { waitAt = source.indexOf("انتظار"); waitToken = "انتظار"; }
        if (waitAt >= 0) source = source.substring(waitAt + waitToken.length());
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d+)\\s*(مللي\\s*ثانية|ms|ثانية|ثواني|ثوان|دقيقة|دقائق)?").matcher(source);
        if(m.find()) {
            int n=Math.max(0,Math.min(60000,parseIntSafe(m.group(1),1)));
            String unit=m.group(2)==null?"":m.group(2);
            if(unit.contains("دقيق")) return Math.min(60000,n*60000);
            if(unit.contains("ثان") && !unit.contains("مللي")) return Math.min(60000,n*1000);
            return n;
        }
        if(source.contains("ثانية")) return 1000;
        if(source.contains("قليلا")||source.contains("قليلًا")) return 1000;
        return 500;
    }

    private int parseIntSafe(String value, int fallback) { try { return Integer.parseInt(value.trim()); } catch(Exception e) { return fallback; } }
    private JSONObject compiledStep(String type, String label) { JSONObject step=BotnaStore.newStep(type,label); putInt(step,"beforeDelay",0); putInt(step,"afterDelay",0); return step; }
    private void addCompiled(JSONArray array, String type, String label) { array.put(compiledStep(type,label)); }
    private void addCompiledInt(JSONArray array,String type,String label,String key,int value) { JSONObject step=compiledStep(type,label); putInt(step,key,value); array.put(step); }

    private void addLibraryImageStep(JSONArray compiled, JSONObject image, int number, boolean autoTap) {
        addLibraryImageStep(compiled, image, number, autoTap, false);
    }

    private void addLibraryImageStep(JSONArray compiled, JSONObject image, int number, boolean autoTap, boolean forceTap) {
        JSONObject step=compiledStep(TYPE_IMAGE,"مطابقة صورة المكتبة رقم "+number+": "+image.optString("name","صورة"));
        JSONArray names=new JSONArray(); names.put(image.optString("fileName"));
        try { step.put("images",names); } catch(JSONException ignored) { }
        putInt(step,"threshold",clampInt(image.optInt("threshold",85),0,100));
        putBoolean(step,"tapOnMatch",autoTap && (forceTap || image.optBoolean("tapOnMatch",true)));
        putBoolean(step,"waitForImageOnly",false);
        putInt(step,"retries",Math.max(1,image.optInt("retries",2)));
        putInt(step,"retryDelay",clampInt(image.optInt("retryDelay",500),100,10000));
        putInt(step,"beforeDelay",clampInt(image.optInt("beforeDelay",0),0,60000));
        putInt(step,"afterDelay",clampInt(image.optInt("afterDelay",0),0,60000));
        putString(step,"searchRegionMode",image.optString("searchRegionMode","full"));
        for(String k:new String[]{"searchX","searchY","searchW","searchH","captureX","captureY","captureW","captureH"}) if(image.has(k)) putInt(step,k,image.optInt(k));
        compiled.put(step);
    }

    private boolean compileNamedLibraryImageAction(String norm, JSONArray result,
                                                    List<String> warnings, JSONArray library) {
        if (library == null || library.length() == 0) return false;
        String searchableCommand = normalizeLibraryImageName(norm);
        if (searchableCommand.isEmpty()) return false;
        JSONObject bestImage = null;
        int bestIndex = -1;
        int bestLength = 0;
        int bestMatches = 0;
        for (int i = 0; i < library.length(); i++) {
            JSONObject image = library.optJSONObject(i);
            if (image == null) continue;
            String originalName = image.optString("name", "").trim();
            String imageName = normalizeLibraryImageName(originalName);
            if (imageName.isEmpty() || !containsLibraryImageName(searchableCommand, imageName)) continue;
            if (imageName.length() > bestLength) {
                bestImage = image;
                bestIndex = i;
                bestLength = imageName.length();
                bestMatches = 1;
            } else if (imageName.length() == bestLength) {
                bestMatches++;
            }
        }
        if (bestImage == null) return false;
        if (bestMatches > 1) {
            warnings.add("اسم صورة المكتبة «" + bestImage.optString("name", "") + "» مكرر؛ لم أضغط صورة غير مؤكدة. غيّر اسم إحدى الصورتين ثم أعد التحليل.");
            return true;
        }
        boolean explicitNoTap = containsCommandPhrase(norm, "بدون ضغط", "دون الضغط", "لا تضغط", "ما تضغط", "لا تكبس", "ما تكبس", "لا تنقر", "ما تنقر", "بدون نقر");
        boolean explicitTap = containsCommandPhrase(norm, "اضغط", "انقر", "اكبس", "كبس", "اختار", "اختر", "افتح", "شغل", "شغللي", "اضغط عليها", "انقر عليها", "اكبس عليها");
        boolean observationOnly = containsCommandPhrase(norm, "انتظر", "استنى", "خليك", "راقب", "تحقق من ظهور", "تأكد من ظهور", "تاكد من ظهور", "تحقق من وجود", "تأكد من وجود", "تاكد من وجود", "اعثر على", "ابحث عن", "دور على", "فتش عن", "حدد مكان");
        boolean tap = !explicitNoTap && (explicitTap || !observationOnly);
        addLibraryImageStep(result, bestImage, bestIndex + 1, tap, explicitTap && tap);
        String matchedName = bestImage.optString("name", "صورة");
        if (tap) {
            warnings.add("تعرّف المحلّل المحلي على صورة المكتبة بالاسم «" + matchedName + "» وحوّلها إلى خطوة مطابقة بصرية. ستُلتقط الشاشة الحالية، ويُبحث عن القالب المحفوظ، ثم يُنفّذ النقر فقط إذا اجتازت المطابقة حدّ الثقة.");
        } else {
            warnings.add("تعرّف المحلّل المحلي على صورة المكتبة بالاسم «" + matchedName + "» وحوّلها إلى خطوة بحث بصري دون نقر. ستُلتقط الشاشة الحالية وتُقارن بالقالب المحفوظ.");
        }
        return true;
    }

    private boolean containsCommandPhrase(String value, String... phrases) {
        if (value == null || phrases == null) return false;
        for (String phrase : phrases) {
            if (phrase == null || phrase.isEmpty()) continue;
            int start = 0;
            while (start < value.length()) {
                int index = value.indexOf(phrase, start);
                if (index < 0) break;
                int end = index + phrase.length();
                boolean leftBoundary = index == 0 || !Character.isLetterOrDigit(value.charAt(index - 1));
                boolean rightBoundary = end >= value.length() || !Character.isLetterOrDigit(value.charAt(end));
                if (leftBoundary && rightBoundary) return true;
                start = index + 1;
            }
        }
        return false;
    }

    private String normalizeLibraryImageName(String value) {
        String normalized = BotnaArabicDialect.normalize(value == null ? "" : value).toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("(?iu)\\.(?:png|jpe?g|webp|bmp)$", "");
        normalized = normalized.replaceAll("[^\\p{L}\\p{N}\\s]+", " ");
        return normalized.replaceAll("\\s+", " ").trim();
    }

    private boolean containsLibraryImageName(String command, String name) {
        if (command == null || name == null || command.isEmpty() || name.isEmpty()) return false;
        String expression = "(?iu)(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(name)
                + "(?![\\p{L}\\p{N}])";
        return java.util.regex.Pattern.compile(expression).matcher(command).find();
    }

    private JSONArray expandCustomSteps(JSONArray source) {
        JSONArray output=new JSONArray(); if(source==null) return output;
        for(int i=0;i<source.length();i++) {
            JSONObject item=source.optJSONObject(i); if(item==null) continue;
            if(TYPE_CUSTOM.equals(item.optString("type"))) {
                JSONArray inner=item.optJSONArray("compiledSteps");
                if(inner==null) continue;
                JSONArray expanded=expandCustomSteps(inner);
                boolean parentEnabled=item.optBoolean("enabled",true);
                for(int j=0;j<expanded.length();j++) {
                    JSONObject child=expanded.optJSONObject(j); if(child==null) continue;
                    try { child.put("enabled", parentEnabled && child.optBoolean("enabled",true)); } catch(JSONException ignored) { }
                    output.put(child);
                }
            } else {
                try { output.put(new JSONObject(item.toString())); } catch(JSONException ignored) { }
            }
        }
        return output;
    }

    private JSONArray getLibrary() { return BotnaStore.imageLibrary(this); }

    private JSONObject findLibraryImage(String id) {
        return findLibraryImage(getLibrary(), id);
    }

    private JSONObject findLibraryImage(JSONArray lib, String id) {
        if (lib == null || id == null || id.isEmpty()) return null;
        for (int i = 0; i < lib.length(); i++) {
            JSONObject item = lib.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }
    private void saveLibrary(JSONArray lib) { BotnaStore.saveImageLibrary(this,lib); }

    private void showImageLibrary() {
        JSONArray lib=getLibrary();
        String[] options=new String[lib.length()+1]; options[0]="＋ إضافة صورة جديدة";
        for(int i=0;i<lib.length();i++) { JSONObject item=lib.optJSONObject(i); options[i+1]=(i+1)+". "+(item==null?"صورة":item.optString("name","صورة"))+" • "+(item==null?85:item.optInt("threshold",85))+"%"; }
        showChoice("مكتبة صور الخطوات المخصصة",options,choice->{ if(choice==0) promptText("اسم الصورة الجديدة", "صورة "+(lib.length()+1), name->{ libraryPendingName=name; libraryReplaceId=""; activeLibraryId=""; cropStepId=""; cropCreatedNewStep=false; replaceImageTarget=false; cropPurpose=CROP_LIBRARY_IMAGE; beginCaptureForCrop(); }); else showLibraryImageSettings(choice-1); });
    }

    private void showLibraryImageSettings(int index) {
        JSONArray lib=getLibrary(); JSONObject item=lib.optJSONObject(index); if(item==null) return;
        String id=item.optString("id");
        showChoice("صورة المكتبة: "+item.optString("name"),new String[]{"تعديل الاسم","نسبة التطابق","إعدادات المطابقة","منطقة البحث","استبدال الصورة","حذف الصورة"},choice->{
            JSONArray current = getLibrary(); JSONObject currentItem = findLibraryImage(current, id); if (currentItem == null) return;
            if(choice==0) promptText("اسم الصورة",currentItem.optString("name"),name->{putString(currentItem,"name",name.trim());saveLibrary(current);showImageLibrary();});
            else if(choice==1) promptNumber("نسبة التطابق (0–100)",currentItem.optInt("threshold",85),0,100,value->{putInt(currentItem,"threshold",value);saveLibrary(current);report("تم حفظ نسبة التطابق للصورة.");});
            else if(choice==2) editLibraryAdvancedSettings(currentItem);
            else if(choice==3) editLibrarySearchRegion(currentItem);
            else if(choice==4) { libraryPendingName=currentItem.optString("name"); libraryReplaceId=id; activeLibraryId=id; cropStepId=""; cropCreatedNewStep=false; replaceImageTarget=false; cropPurpose=CROP_LIBRARY_IMAGE; beginCaptureForCrop(); }
            else confirmDeleteLibraryImage(id);
        });
    }

    private void editLibraryAdvancedSettings(JSONObject item) {
        String id=item.optString("id");
        showChoice("إعدادات صورة المكتبة",new String[]{item.optBoolean("tapOnMatch",true)?"عدم الضغط بعد العثور":"الضغط بعد العثور", "عدد المحاولات", "فاصل المحاولة", "انتظار قبل الخطوة", "انتظار بعد الخطوة"},choice->{
            JSONArray lib = getLibrary(); JSONObject target = findLibraryImage(lib, id); if (target == null) return;
            if(choice==0){putBoolean(target,"tapOnMatch",!target.optBoolean("tapOnMatch",true));saveLibrary(lib);editLibraryAdvancedSettings(target);}
            else if(choice==1)promptNumber("عدد المحاولات (أي عدد صحيح موجب)",target.optInt("retries",2),1,Integer.MAX_VALUE,v->{putInt(target,"retries",v);saveLibrary(lib);});
            else if(choice==2)promptNumber("فاصل المحاولة بالمللي ثانية",target.optInt("retryDelay",500),100,10000,v->{putInt(target,"retryDelay",v);saveLibrary(lib);});
            else if(choice==3)promptNumber("انتظار قبل المطابقة بالمللي ثانية",target.optInt("beforeDelay",0),0,60000,v->{putInt(target,"beforeDelay",v);saveLibrary(lib);});
            else promptNumber("انتظار بعد المطابقة بالمللي ثانية",target.optInt("afterDelay",0),0,60000,v->{putInt(target,"afterDelay",v);saveLibrary(lib);});
        });
    }

    private void editLibrarySearchRegion(JSONObject item) {
        String id=item.optString("id");
        showChoice("منطقة بحث قالب المكتبة",new String[]{"الشاشة الكاملة (افتراضي)","مكان القص الأصلي", "تحديد منطقة مخصصة"},choice->{
            JSONArray lib = getLibrary(); JSONObject target = findLibraryImage(lib, id); if (target == null) return;
            if(choice==0){putString(target,"searchRegionMode","full");saveLibrary(lib);report("ستبحث صورة المكتبة عبر الشاشة الكاملة.");}
            else if(choice==1){
                if(!target.has("captureW")){report("لا يوجد موضع قص محفوظ؛ استخدم الشاشة الكاملة أو منطقة مخصصة.");return;}
                putString(target,"searchRegionMode","original");saveLibrary(lib);report("ستبحث صورة المكتبة قرب منطقة القص الأصلية.");
            } else {activeLibraryId=id; cropStepId=id; cropPurpose=CROP_LIBRARY_SEARCH_REGION; cropCreatedNewStep=false; replaceImageTarget=false; beginCaptureForCrop();}
        });
    }

    private void confirmDeleteLibraryImage(String id) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "حذف صورة المكتبة")).setMessage(BotnaI18n.translate(this, "سيتم حذف قالب الصورة من المكتبة. الخطوات المخصصة التي تستخدمها ستعرض تحذيرًا إذا أصبحت الصورة غير متاحة. متابعة؟"))
                .setPositiveButton(BotnaI18n.translate(this, "حذف"),(d,w)->{
                    JSONArray old=getLibrary(),next=new JSONArray(); String filename="";
                    for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i); if(item!=null&&id.equals(item.optString("id"))){filename=item.optString("fileName");continue;} next.put(item);}
                    saveLibrary(next); if(!filename.isEmpty()) {File f=new File(new File(getFilesDir(),"botna_library"),filename); if(f.exists()) f.delete();}
                    report("تم حذف صورة المكتبة."); showImageLibrary();
                }).setNegativeButton(BotnaI18n.translate(this, "إلغاء"),null).create();
        try { if(dialog.getWindow()!=null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); dialog.show(); }
        catch(Exception e) { report("تعذر عرض تأكيد حذف صورة المكتبة."); }
    }

    private android.util.DisplayMetrics realScreenMetrics() {
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        try { if (wm != null) wm.getDefaultDisplay().getRealMetrics(metrics); }
        catch (Exception ignored) { }
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) {
            android.util.DisplayMetrics fallback = getResources().getDisplayMetrics();
            metrics.setTo(fallback);
        }
        return metrics;
    }

    private int coordinateFromScreenshot(JSONObject step, String pixelKey, String sourceDimensionKey,
                                         int displaySize, String percentKey) {
        if (displaySize <= 0) return 0;
        if (step != null && step.has(pixelKey) && step.has(sourceDimensionKey)) {
            int sourceSize = step.optInt(sourceDimensionKey, displaySize);
            int sourcePixel = step.optInt(pixelKey, -1);
            if (sourceSize > 0 && sourcePixel >= 0 && sourcePixel < sourceSize) {
                float scale = (displaySize - 1f) / Math.max(1, sourceSize - 1);
                return Math.max(0, Math.min(displaySize - 1, Math.round(sourcePixel * scale)));
            }
        }
        int percent = Math.max(0, Math.min(100, step == null ? 50 : step.optInt(percentKey, 50)));
        return Math.max(0, Math.min(displaySize - 1, Math.round((displaySize - 1f) * percent / 100f)));
    }

    private void copyCoordinates(int xPercent, int yPercent) {
        android.util.DisplayMetrics metrics = realScreenMetrics();
        int x = Math.max(0, Math.min(metrics.widthPixels - 1, Math.round(metrics.widthPixels * xPercent / 100f)));
        int y = Math.max(0, Math.min(metrics.heightPixels - 1, Math.round(metrics.heightPixels * yPercent / 100f)));
        String value = "x=" + x + ", y=" + y + " px (" + xPercent + "%, " + yPercent + "%)";
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Botna coordinates", value));
            report("نُسخت إحداثيات النقطة: " + value);
            Toast.makeText(this, "تم نسخ الإحداثيات: " + value, Toast.LENGTH_SHORT).show();
        } catch (Exception e) { report("تعذر نسخ الإحداثيات."); }
    }

    private void configureFullScreenOverlay(WindowManager.LayoutParams params) {
        if (params == null) return;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= 28) params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        if (Build.VERSION.SDK_INT >= 30) {
            params.setFitInsetsTypes(0);
            params.setFitInsetsSides(0);
            params.setFitInsetsIgnoringVisibility(true);
        }
        params.gravity = Gravity.TOP | Gravity.START;
    }

    private void openPointSelector(JSONObject step) {
        if (wm == null || step == null) return;
        activePointStepId = step.optString("id", "");
        hidePanel(); removeView(bubble); bubble = null;
        removeView(pointSelector); pointSelector = null;
        PointSelector selector = new PointSelector(step.optInt("xPercent", 50), step.optInt("yPercent", 50));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        configureFullScreenOverlay(params);
        try { wm.addView(selector, params); pointSelector = selector; }
        catch (Exception e) { pointSelector = null; showBubble(); showPanel(); report("تعذر فتح محدد نقطة النقر."); }
    }

    private void finishPointSelection(boolean save) {
        PointSelector selector = pointSelector;
        if (selector == null) return;
        if (save) {
            JSONObject script = ensureScript();
            JSONObject step = findStepById(script, activePointStepId);
            if (step != null) {
                int xp = Math.max(0, Math.min(100, Math.round(selector.pointX / (float)Math.max(1, selector.getWidth()) * 100f)));
                int yp = Math.max(0, Math.min(100, Math.round(selector.pointY / (float)Math.max(1, selector.getHeight()) * 100f)));
                putInt(step, "xPercent", xp); putInt(step, "yPercent", yp);
                putString(step, "label", "نقر الموقع " + xp + "%، " + yp + "%");
                BotnaStore.saveSteps(this, script, BotnaStore.steps(script));
                report("تم حفظ نقطة النقر: " + xp + "% أفقيًا و" + yp + "% عموديًا.");
            }
        }
        removeView(selector); pointSelector = null; activePointStepId = "";
        showBubble(); showPanel();
    }

    private class PointSelector extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density = getResources().getDisplayMetrics().density;
        private float pointX, pointY;
        private final int initialXP, initialYP;
        private float downX, downY;
        private int actionMode;
        PointSelector(int xp, int yp) { super(BotnaAccessibilityService.this); initialXP=xp; initialYP=yp; setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { pointX = w * initialXP / 100f; pointY = h * initialYP / 100f; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float r = 8*density;
            paint.setColor(Color.argb(58, 35, 226, 244)); paint.setStyle(Paint.Style.FILL);
            c.drawCircle(pointX, pointY, r, paint);
            paint.setColor(Color.argb(235, 35, 226, 244)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2*density);
            c.drawCircle(pointX, pointY, r, paint);
            paint.setStyle(Paint.Style.FILL); c.drawCircle(pointX, pointY, 2.2f*density, paint);
            paint.setColor(Color.argb(215, 4, 9, 16));
            c.drawRoundRect(new RectF(8*density,8*density,getWidth()-8*density,45*density),10*density,10*density,paint);
            paint.setColor(TEXT); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(13*density);
            int px = Math.max(0, Math.min(100, Math.round(pointX / Math.max(1f, getWidth()) * 100f)));
            int py = Math.max(0, Math.min(100, Math.round(pointY / Math.max(1f, getHeight()) * 100f)));
            c.drawText("اسحب النقطة للمكان المطلوب  •  " + px + "% / " + py + "%",getWidth()/2f,31*density,paint);
            float top=getHeight()-54*density;
            paint.setColor(Color.argb(235,4,10,18)); c.drawRoundRect(new RectF(8*density,top,getWidth()-8*density,getHeight()-7*density),12*density,12*density,paint);
            paint.setTextSize(12*density);
            paint.setColor(Color.rgb(255,130,153)); c.drawText("× إلغاء",48*density,top+29*density,paint);
            paint.setColor(TEXT); c.drawText("⧉ نسخ",getWidth()/2f,top+29*density,paint);
            paint.setColor(CYAN); c.drawText("✓ حفظ",getWidth()-52*density,top+29*density,paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            float top=getHeight()-54*density;
            if(e.getAction()==MotionEvent.ACTION_DOWN) {
                downX=x; downY=y; actionMode=0;
                if(y>=top) {
                    if(x<94*density) actionMode=3;
                    else if(x>getWidth()-104*density) actionMode=1;
                    else if(Math.abs(x-getWidth()/2f)<74*density) actionMode=2;
                }
                if(actionMode==0) { pointX=x; pointY=y; invalidate(); }
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE && actionMode==0) { pointX=Math.max(0,Math.min(getWidth(),x)); pointY=Math.max(0,Math.min(getHeight(),y)); invalidate(); return true; }
            if(e.getAction()==MotionEvent.ACTION_UP) {
                if(actionMode==1) finishPointSelection(true);
                else if(actionMode==2) {
                    int xp=Math.max(0,Math.min(100,Math.round(pointX/Math.max(1f,getWidth())*100f)));
                    int yp=Math.max(0,Math.min(100,Math.round(pointY/Math.max(1f,getHeight())*100f)));
                    android.util.DisplayMetrics m=realScreenMetrics();
                    copyCoordinates(xp,yp);
                } else if(actionMode==3) finishPointSelection(false);
                else { pointX=Math.max(0,Math.min(getWidth(),x)); pointY=Math.max(0,Math.min(getHeight(),y)); invalidate(); }
                return true;
            }
            return true;
        }
    }

    private void prepareCropAngle(JSONObject step) {
        if (step == null || !TYPE_IMAGE.equals(step.optString("type"))) return;

        cropStepId = step.optString("id");
        cropCreatedNewStep = false;
        replaceImageTarget = false;
        cropPurpose = CROP_TARGET;
        beginCaptureForCrop();
    }

    private void prepareCropStep() {

        JSONObject step = addStep(TYPE_IMAGE, "مطابقة صورة الهدف");
        initializeImageStepDefaults(step, false);
        cropStepId = step.optString("id");
        cropCreatedNewStep = true;
        replaceImageTarget = false;
        cropPurpose = CROP_TARGET;
        saveStepObject(step);
        beginCaptureForCrop();
    }

    private void initializeImageStepDefaults(JSONObject step, boolean waitOnly) {
        putInt(step, "threshold", 85);
        putBoolean(step, "tapOnMatch", !waitOnly);
        putBoolean(step, "waitForImageOnly", waitOnly);
        putInt(step, "retries", waitOnly ? 3 : 1);
        putInt(step, "retryDelay", 500);
        putInt(step, "beforeDelay", 0);
        putInt(step, "afterDelay", 0);
        putString(step, "searchRegionMode", "full");
        try { if (step.optJSONArray("images") == null) step.put("images", new JSONArray()); } catch (JSONException ignored) { }
    }

    private void beginCaptureForCrop() {
        if (Build.VERSION.SDK_INT < 30) {
            replaceImageTarget = false;
            discardEmptyCreatedCropStep();
            report("قص الصور يحتاج Android 11 أو أحدث لدعم لقطة الشاشة عبر خدمة إمكانية الوصول.");
            return;
        }
        hidePanel(); removeView(bubble); bubble = null;
        handler.postDelayed(() -> takeScreenshot(new ScreenshotCallback() {
            @Override public void done(Bitmap screenshot, String error) {
                if (screenshot == null) {
                    replaceImageTarget = false;
                    discardEmptyCreatedCropStep();
                    showBubble(); showPanel(); report("تعذر التقاط الشاشة: " + error); return;
                }
                showCropSelector(screenshot, cropPurpose);
            }
        }), 220);
    }

    private void promptAutonomousExternalTask() {
        if (running || singleStepTesting || smartTaskActive) {
            report("أوقف التنفيذ الحالي قبل بدء مهمة آلية جديدة.");
            return;
        }
        BotnaExternalAppAgent.setEnabled(this, true);
        externalRequestedTargetPackage = "";
        String foregroundTarget = findVisibleTargetPackage();
        if (isValidExternalTargetPackage(foregroundTarget)) externalRequestedTargetPackage = foregroundTarget;
        String packageName = BotnaExternalAppAgent.selectedPackage(this);
        if (BotnaExternalAppAgent.META_AI_WHATSAPP.equals(packageName)) {
            packageName = BotnaExternalAppAgent.META_AI_WEB;
            BotnaExternalAppAgent.setSelectedPackage(this, packageName);
            report("Meta AI لا يظهر كمستلم مشاركة داخل WhatsApp؛ سيستخدم Botna موقع Meta AI مباشرة مع رفع المرفقات وإرسال التعليمات.");
        }
        if (!BotnaExternalAppAgent.hasExplicitlySelectedPackage(this) || !BotnaExternalAppAgent.isInstalled(this, packageName)) {
            showExternalAgentAppPickerForTask(this::showAutonomousExternalTaskPrompt);
            return;
        }
        showAutonomousExternalTaskPrompt();
    }

    private void showExternalAgentAppPickerForTask(Runnable afterSelection) {
        String[] packages = BotnaExternalAppAgent.packages();
        String[] labels = BotnaExternalAppAgent.labels();
        CharSequence[] entries = new CharSequence[labels.length];
        int selected = 0;
        String current = BotnaExternalAppAgent.selectedPackage(this);
        for (int i = 0; i < labels.length; i++) {
            boolean custom = BotnaExternalAppAgent.isCustomWebAi(packages[i]);
            boolean installed = BotnaExternalAppAgent.isInstalled(this, packages[i]);
            entries[i] = labels[i] + (custom && !installed ? " • اضغط لإضافة الرابط" : installed ? " • جاهز" : " • غير مثبت");
            if (packages[i].equals(current)) selected = i;
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("اختر تطبيق الذكاء الاصطناعي للاستخدام التلقائي")
                .setSingleChoiceItems(entries, selected, (choiceDialog, which) -> {
                    String packageName = packages[which];
                    if (BotnaExternalAppAgent.isCustomWebAi(packageName)) {
                        choiceDialog.dismiss();
                        showCustomWebAiUrlDialog(() -> {
                            BotnaExternalAppAgent.setSelectedPackage(this, BotnaExternalAppAgent.CUSTOM_WEB_AI);
                            if (afterSelection != null) handler.post(afterSelection);
                        });
                        return;
                    }
                    if (BotnaExternalAppAgent.META_AI_WHATSAPP.equals(packageName)) {
                        packageName = BotnaExternalAppAgent.META_AI_WEB;
                        BotnaExternalAppAgent.setSelectedPackage(this, packageName);
                        choiceDialog.dismiss();
                        report("سيستخدم Botna موقع Meta AI مباشرة، لأن Meta AI ليس مستلمًا متاحًا في قائمة مشاركة WhatsApp.");
                        if (afterSelection != null) handler.post(afterSelection);
                        return;
                    }
                    if (!BotnaExternalAppAgent.isInstalled(this, packageName)) {
                        report("التطبيق المختار غير مثبت. اختر تطبيقًا مثبتًا.");
                        return;
                    }
                    BotnaExternalAppAgent.setSelectedPackage(this, packageName);
                    choiceDialog.dismiss();
                    if (afterSelection != null) handler.post(afterSelection);
                }).setNegativeButton("إلغاء", null).create();
        try {
            if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.show();
        } catch (Exception e) {
            report("تعذر فتح اختيار تطبيق الذكاء الاصطناعي. افتح Bot Studio ثم الإعدادات واختر تطبيقًا من قسم الرؤية.");
        }
    }

    private void showCustomWebAiUrlDialog(Runnable afterSaved) {
        EditText urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(BotnaExternalAppAgent.customWebAiUrl(this));
        urlInput.setHint("https://www.meta.ai/");
        urlInput.setTextColor(TEXT);
        urlInput.setHintTextColor(MUTED);
        urlInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        urlInput.setBackground(glassShape(BUTTON, CYAN, 1, 12));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("رابط موقع الذكاء الاصطناعي")
                .setMessage("أدخل رابط صفحة المحادثة. يجب أن يبدأ الرابط بـ https:// وأن تكون صفحة المحادثة متاحة بعد التحميل.")
                .setView(urlInput).setPositiveButton("حفظ واختيار الموقع", null)
                .setNegativeButton("إلغاء", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = urlInput.getText() == null ? "" : urlInput.getText().toString().trim();
            if (!BotnaExternalAppAgent.setCustomWebAiUrl(this, value)) {
                urlInput.setError("أدخل رابط HTTPS صالحًا");
                return;
            }
            dialog.dismiss();
            if (afterSaved != null) afterSaved.run();
        }));
        try {
            if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.show();
        } catch (Exception e) {
            report("تعذر فتح إعداد رابط الموقع.");
        }
        urlInput.requestFocus();
    }

    private void showAutonomousExternalTaskPrompt() {
        if (running || singleStepTesting || smartTaskActive) {
            report("أوقف التنفيذ الحالي قبل بدء مهمة آلية جديدة.");
            return;
        }
        String packageName = BotnaExternalAppAgent.selectedPackage(this);
        if (!BotnaExternalAppAgent.isInstalled(this, packageName)) {
            report("تطبيق الذكاء الاصطناعي المختار غير مثبت. اضغط مطولًا على زر المهمة لاختيار تطبيق مثبت.");
            return;
        }
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setHint("مثال: افتح واتساب، ابحث عن جهة الاتصال، ثم نفّذ المطلوب وتحقق من النتيجة.");
        android.content.SharedPreferences externalPrefs = getSharedPreferences("botna_external_app_agent", MODE_PRIVATE);
        String pendingTemplateGoal = externalPrefs.getString("pending_template_goal", "");
        if (pendingTemplateGoal != null && !pendingTemplateGoal.trim().isEmpty()) input.setText(pendingTemplateGoal);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(glassShape(BUTTON, CYAN, 1, 12));
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setFocusable(true);
        form.setFocusableInTouchMode(true);
        form.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        form.addView(input, new LinearLayout.LayoutParams(-1, dp(220)));
        CheckBox liveBurst = new CheckBox(this);
        boolean deepSeekStillsOption = BotnaExternalAppAgent.DEEPSEEK.equals(packageName);
        liveBurst.setText(deepSeekStillsOption
                ? "استخدام ثلاث لقطات قبل/أثناء/بعد بدل الفيديو"
                : "بث مباشر لمدة ثانيتين بدل ثلاث لقطات");
        liveBurst.setTextColor(TEXT);
        liveBurst.setChecked(deepSeekStillsOption || externalPrefs.getBoolean("pending_template_live_burst", false));
        liveBurst.setEnabled(!deepSeekStillsOption);
        liveBurst.setContentDescription(deepSeekStillsOption
                ? "سيستخدم Botna ثلاث صور منفصلة قبل الإجراء وأثناءه وبعده"
                : "تفعيل فيديو شاشة MP4 حقيقي مدته ثانيتان بدل الصور الثلاث");
        liveBurst.setTextSize(14);
        liveBurst.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
        liveBurst.setPadding(dp(6), dp(8), dp(6), dp(4));
        form.addView(liveBurst, new LinearLayout.LayoutParams(-1, -2));
        Button visualTemplateSettings = button("صور حقل الإدخال وزر الإرسال لهذا النموذج", false);
        form.addView(visualTemplateSettings, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView sendSettingsTitle = label("إعدادات الإرسال لهذا النموذج", 12, MUTED, true);
        LinearLayout.LayoutParams sendTitleLp = new LinearLayout.LayoutParams(-1, -2);
        sendTitleLp.topMargin = dp(8);
        form.addView(sendSettingsTitle, sendTitleLp);
        LinearLayout sendSettingsRow = new LinearLayout(this);
        sendSettingsRow.setOrientation(LinearLayout.HORIZONTAL);
        EditText sendDelaySeconds = new EditText(this);
        sendDelaySeconds.setSingleLine(true);
        sendDelaySeconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sendDelaySeconds.setText(String.valueOf(BotnaExternalAppAgent.sendWaitMs(this, packageName) / 1000));
        sendDelaySeconds.setHint("6");
        sendDelaySeconds.setTextColor(TEXT);
        sendDelaySeconds.setHintTextColor(MUTED);
        sendDelaySeconds.setPadding(dp(10), dp(8), dp(10), dp(8));
        sendDelaySeconds.setBackground(glassShape(BUTTON, CYAN, 1, 10));
        EditText sendAttempts = new EditText(this);
        sendAttempts.setSingleLine(true);
        sendAttempts.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        sendAttempts.setText(String.valueOf(BotnaExternalAppAgent.sendMaxAttempts(this, packageName)));
        sendAttempts.setHint("5");
        sendAttempts.setTextColor(TEXT);
        sendAttempts.setHintTextColor(MUTED);
        sendAttempts.setPadding(dp(10), dp(8), dp(10), dp(8));
        sendAttempts.setBackground(glassShape(BUTTON, CYAN, 1, 10));
        LinearLayout delayColumn = new LinearLayout(this);
        delayColumn.setOrientation(LinearLayout.VERTICAL);
        delayColumn.addView(label("الانتظار قبل الإرسال (ثانية، دون حد 60)", 11, TEXT, false), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sendDelayLp = wrap();
        sendDelayLp.topMargin = dp(4);
        delayColumn.addView(sendDelaySeconds, sendDelayLp);
        LinearLayout attemptsColumn = new LinearLayout(this);
        attemptsColumn.setOrientation(LinearLayout.VERTICAL);
        attemptsColumn.addView(label("محاولات الإرسال (1–10)", 11, TEXT, false), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams sendAttemptsLp = wrap();
        sendAttemptsLp.topMargin = dp(4);
        attemptsColumn.addView(sendAttempts, sendAttemptsLp);
        LinearLayout.LayoutParams delayColumnLp = new LinearLayout.LayoutParams(0, -2, 1f);
        delayColumnLp.rightMargin = dp(6);
        sendSettingsRow.addView(delayColumn, delayColumnLp);
        LinearLayout.LayoutParams attemptsColumnLp = new LinearLayout.LayoutParams(0, -2, 1f);
        attemptsColumnLp.leftMargin = dp(6);
        sendSettingsRow.addView(attemptsColumn, attemptsColumnLp);
        LinearLayout.LayoutParams sendSettingsRowLp = wrap();
        sendSettingsRowLp.topMargin = dp(5);
        form.addView(sendSettingsRow, sendSettingsRowLp);
        EditText responseDelaySeconds = new EditText(this);
        responseDelaySeconds.setSingleLine(true);
        responseDelaySeconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        responseDelaySeconds.setText(String.valueOf(BotnaExternalAppAgent.responseWaitMs(this, packageName) / 1000));
        responseDelaySeconds.setHint("15");
        responseDelaySeconds.setTextColor(TEXT);
        responseDelaySeconds.setHintTextColor(MUTED);
        responseDelaySeconds.setPadding(dp(10), dp(8), dp(10), dp(8));
        responseDelaySeconds.setBackground(glassShape(BUTTON, CYAN, 1, 10));
        LinearLayout responseDelayColumn = new LinearLayout(this);
        responseDelayColumn.setOrientation(LinearLayout.VERTICAL);
        responseDelayColumn.addView(label("الانتظار قبل قراءة الرد (ثانية)", 11, TEXT, false), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams responseDelayLp = wrap();
        responseDelayLp.topMargin = dp(4);
        responseDelayColumn.addView(responseDelaySeconds, responseDelayLp);
        LinearLayout.LayoutParams responseRowLp = new LinearLayout.LayoutParams(-1, -2);
        responseRowLp.topMargin = dp(8);
        form.addView(responseDelayColumn, responseRowLp);
        visualTemplateSettings.setOnClickListener(view -> {
            String currentGoal = input.getText() == null ? "" : input.getText().toString().trim();
            long delaySeconds;
            long responseSeconds;
            int attemptCount;
            try { delaySeconds = Long.parseLong(sendDelaySeconds.getText().toString().trim()); } catch (Exception ignored) { delaySeconds = 6L; }
            try { responseSeconds = Long.parseLong(responseDelaySeconds.getText().toString().trim()); } catch (Exception ignored) { responseSeconds = 15L; }
            try { attemptCount = Integer.parseInt(sendAttempts.getText().toString().trim()); } catch (Exception ignored) { attemptCount = 5; }
            int sendWaitMs = delaySeconds <= 0L ? 0 : delaySeconds >= Integer.MAX_VALUE / 1000L ? Integer.MAX_VALUE : (int) (delaySeconds * 1000L);
            int responseWaitMs = responseSeconds <= 0L ? 0 : responseSeconds >= Integer.MAX_VALUE / 1000L ? Integer.MAX_VALUE : (int) (responseSeconds * 1000L);
            BotnaExternalAppAgent.setSendWaitMs(this, packageName, sendWaitMs);
            BotnaExternalAppAgent.setResponseWaitMs(this, packageName, responseWaitMs);
            BotnaExternalAppAgent.setSendMaxAttempts(this, packageName, Math.max(1, Math.min(10, attemptCount)));
            getSharedPreferences("botna_external_app_agent", MODE_PRIVATE).edit()
                    .putString("pending_template_goal", currentGoal)
                    .putBoolean("pending_template_live_burst", !deepSeekStillsOption && liveBurst.isChecked())
                    .putBoolean("reopen_external_prompt_after_template", true).apply();
            if (activeCustomDialog != null) activeCustomDialog.dismiss();
            hideBotnaOverlaysForCleanScreenshot();
            Intent settingsIntent = new Intent(this, BotnaAiTemplateSettingsActivity.class);
            settingsIntent.putExtra(BotnaAiTemplateSettingsActivity.EXTRA_PACKAGE, packageName);
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(settingsIntent); }
            catch (Exception e) {
                showBubble();
                report("تعذر فتح إعداد صور النموذج.");
            }
        });
        showCustomDialog("مهمة آلية عبر " + BotnaExternalAppAgent.label(packageName), form, () -> {
            String goal = input.getText() == null ? "" : input.getText().toString().trim();
            if (goal.isEmpty()) {
                report("اكتب هدف المهمة أولًا. لم يبدأ التنفيذ.");
                return;
            }
            if (!BotnaExternalAppAgent.isInstalled(this, BotnaExternalAppAgent.selectedPackage(this))) {
                report("تطبيق الذكاء الاصطناعي لم يعد متاحًا. لم يبدأ التنفيذ.");
                return;
            }
            long delaySeconds;
            long responseSeconds;
            int attemptCount;
            try { delaySeconds = Long.parseLong(sendDelaySeconds.getText().toString().trim()); }
            catch (Exception ignored) { delaySeconds = 6L; }
            try { responseSeconds = Long.parseLong(responseDelaySeconds.getText().toString().trim()); }
            catch (Exception ignored) { responseSeconds = 15L; }
            try { attemptCount = Integer.parseInt(sendAttempts.getText().toString().trim()); }
            catch (Exception ignored) { attemptCount = 5; }
            int sendWaitMs = delaySeconds <= 0L ? 0 : delaySeconds >= Integer.MAX_VALUE / 1000L ? Integer.MAX_VALUE : (int) (delaySeconds * 1000L);
            int responseWaitMs = responseSeconds <= 0L ? 0 : responseSeconds >= Integer.MAX_VALUE / 1000L ? Integer.MAX_VALUE : (int) (responseSeconds * 1000L);
            BotnaExternalAppAgent.setSendWaitMs(this, packageName, sendWaitMs);
            BotnaExternalAppAgent.setResponseWaitMs(this, packageName, responseWaitMs);
            BotnaExternalAppAgent.setSendMaxAttempts(this, packageName, Math.max(1, Math.min(10, attemptCount)));
            getSharedPreferences("botna_external_app_agent", MODE_PRIVATE).edit()
                    .remove("pending_template_goal").remove("pending_template_live_burst").putBoolean("reopen_external_prompt_after_template", false).apply();
            liveBurstRequestedForNextTask = !deepSeekStillsOption && liveBurst.isChecked();
            temporalFramesRequestedForNextTask = deepSeekStillsOption || !liveBurst.isChecked();
            BotnaExternalAppAgent.setEnabled(this, true);
            startSmartTask(goal, "", false);
        });
        form.requestFocus();
    }

    private void shareCurrentScreenWithAiApp(String instruction) {
        String cleanInstruction = instruction == null ? "" : instruction.trim();
        if (cleanInstruction.isEmpty()) cleanInstruction = "حلل لقطة الشاشة المرفقة وحدد الإجراء التالي بإحداثيات الصورة الأصلية.";
        if (smartTaskActive || running || singleStepTesting) {
            report("أوقف المهمة الحالية أو أوقفها مؤقتًا قبل اختبار مشاركة لقطة الشاشة إلى تطبيق خارجي.");
            return;
        }
        if (Build.VERSION.SDK_INT < 30) {
            report("مشاركة لقطة الشاشة الحالية تحتاج Android 11 أو أحدث وصلاحية لقطة الشاشة من خدمة إمكانية الوصول.");
            return;
        }
        final String taskInstruction = cleanInstruction;
        hidePanel();
        removeView(bubble);
        bubble = null;
        handler.postDelayed(() -> takeScreenshot((bitmap, error) -> {
            if (bitmap == null) {
                showBubble();
                showPanel();
                report("تعذر تجهيز لقطة للمشاركة: " + (error == null ? "سبب غير معروف" : error) + ". لم تُرسل صورة.");
                return;
            }
            try {
                screenshotShareExecutor.execute(() -> prepareAndShareScreenshot(bitmap, taskInstruction));
            } catch (Exception e) {
                if (!bitmap.isRecycled()) bitmap.recycle();
                handler.post(() -> {
                    showBubble();
                    report("تعذر بدء مشاركة الصورة: " + e.getClass().getSimpleName());
                });
            }
        }, "لقطة اختبار مشاركة إلى تطبيق ذكاء اصطناعي"), 480L);
    }

    private void prepareAndShareScreenshot(Bitmap bitmap, String instruction) {
        File destination = null;
        try {
            File directory = new File(new File(getFilesDir(), "bot_studio_files"), "ai_share");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد المشاركة الآمن.");
            destination = new File(directory, "botna_screen_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream output = new FileOutputStream(destination)) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) throw new IllegalStateException("تعذر ضغط لقطة الشاشة.");
                output.flush();
                output.getFD().sync();
            }
            String device = Build.MANUFACTURER + " " + Build.MODEL;
            if ("RMX3363".equalsIgnoreCase(Build.MODEL)) device = "realme GT Master Edition (RMX3363)";
            String prompt = "أنت مساعد بصري ينفذ المهمة على هاتف Android. لا تدّع أنك ضغطت أو نفذت أي شيء؛ أعطِ قرار الخطوة التالية فقط بناءً على الصورة المرفقة.\n"
                    + "الجهاز: " + device + "\n"
                    + "دقة لقطة الشاشة الأصلية: العرض " + bitmap.getWidth() + " بكسل، الارتفاع " + bitmap.getHeight() + " بكسل. نقطة (0,0) أعلى اليسار.\n"
                    + "أعد إجابة قصيرة بإجراء واحد فقط بصيغة JSON: TAP مع x,y أو SWIPE مع x1,y1,x2,y2 أو TYPE مع text أو WAIT مع ms أو DONE. اجعل الإحداثيات ضمن أبعاد الصورة الأصلية.\n"
                    + "المهمة/التعليمات: " + instruction + "\n"
                    + "لا تخمّن إذا كانت الصورة غير واضحة، ولا تكرر إجراءً سبق أن فشل.";
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", destination);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/jpeg");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_TEXT, prompt);
            send.setClipData(ClipData.newUri(getContentResolver(), "Botna screenshot", uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (getPackageManager().queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
                throw new IllegalStateException("لا يوجد تطبيق مثبت يستقبل صورًا عبر المشاركة. افتح التطبيق المستهدف وتأكد أنه يدعم استقبال الصور.");
            }
            pruneAiShareImages(directory, 6);
            Intent chooser = Intent.createChooser(send, "اختر تطبيق الذكاء الاصطناعي لإرفاق لقطة الشاشة");
            chooser.setClipData(ClipData.newUri(getContentResolver(), "Botna screenshot", uri));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            handler.post(() -> {
                try {
                    startActivity(chooser);
                    report("تم تجهيز لقطة شاشة كملف صورة ومشاركتها إلى قائمة التطبيقات. اختر ChatGPT أو Gemini أو DeepSeek أو Copilot، وتأكد من ظهور معاينة الصورة والنص قبل الإرسال؛ لم تُرسل الرسالة تلقائيًا.");
                    handler.postDelayed(this::showBubble, 1100L);
                } catch (Exception e) {
                    showBubble();
                    report("فشل فتح قائمة مشاركة الصورة: " + e.getClass().getSimpleName());
                }
            });
        } catch (Exception e) {
            File failedFile = destination;
            if (failedFile != null && failedFile.exists()) failedFile.delete();
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            handler.post(() -> {
                showBubble();
                report("تعذرت مشاركة لقطة الشاشة: " + reason + ". لم يتم إرسال الصورة.");
            });
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private void pruneAiShareImages(File directory, int keepCount) {
        File[] files = directory.listFiles((dir, name) -> name != null && name.startsWith("botna_screen_") && name.endsWith(".jpg"));
        if (files == null || files.length <= keepCount) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keepCount; i < files.length; i++) {
            try { files[i].delete(); } catch (Exception ignored) { }
        }
    }

    private void pruneAiShareVideos(File directory, int keepCount) {
        File[] files = directory.listFiles((dir, name) -> name != null && name.startsWith("botna_live_") && name.endsWith(".mp4"));
        if (files == null || files.length <= keepCount) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (int i = keepCount; i < files.length; i++) {
            try { files[i].delete(); } catch (Exception ignored) { }
        }
    }

    private interface ScreenshotCallback { void done(Bitmap bitmap, String error); }

    private static final class ScreenshotRequest {
        final ScreenshotCallback callback;
        final String taskId;
        final String taskGoal;
        final int actionIndex;
        final String label;
        final boolean archiveEvidence;
        int intervalRetries;
        ScreenshotRequest(ScreenshotCallback callback, String taskId, String taskGoal, int actionIndex, String label, boolean archiveEvidence) {
            this.callback = callback; this.taskId = taskId; this.taskGoal = taskGoal; this.actionIndex = actionIndex; this.label = label;
            this.archiveEvidence = archiveEvidence;
        }
    }

    private void takeScreenshot(ScreenshotCallback callback) { takeScreenshot(callback, "", true); }

    private void takeScreenshot(ScreenshotCallback callback, String evidenceLabel) { takeScreenshot(callback, evidenceLabel, true); }

    private void takeScreenshotForStateProbe(ScreenshotCallback callback) { takeScreenshot(callback, "فحص حالة زر الإرسال دون أرشفة", false); }

    private void takeScreenshot(ScreenshotCallback callback, String evidenceLabel, boolean archiveEvidence) {
        if (callback == null) return;
        final String requestedTaskId = smartTaskActive ? smartTaskId : "";
        final String requestedTaskGoal = smartTaskActive ? smartTaskGoal : "";
        final int requestedActionIndex = smartTaskActive ? smartTaskActionCount : -1;
        final String requestedLabel = evidenceLabel == null || evidenceLabel.trim().isEmpty()
                ? (smartCurrentActionLabel == null || smartCurrentActionLabel.isEmpty() ? "لقطة ضمن المهمة" : smartCurrentActionLabel)
                : evidenceLabel.trim();
        if (Build.VERSION.SDK_INT < 30) {
            if (archiveEvidence && !requestedTaskId.isEmpty()) BotnaTaskEvidenceStore.recordEvent(this, requestedTaskId, "screenshot_failure", requestedActionIndex, requestedLabel, "نسخة أندرويد لا تدعم واجهة لقطة الشاشة المطلوبة.", false, null);
            handler.post(() -> callback.done(null, "نسخة أندرويد لا تدعم واجهة اللقطة المطلوبة"));
            return;
        }
        handler.post(() -> {
            if (screenshotRequestQueue.size() >= MAX_PENDING_SCREENSHOTS) {
                if (archiveEvidence && !requestedTaskId.isEmpty()) BotnaTaskEvidenceStore.recordEvent(this, requestedTaskId, "screenshot_failure", requestedActionIndex, requestedLabel, "تراكمت طلبات لقطات الشاشة في الطابور.", false, null);
                callback.done(null, "تراكمت طلبات لقطات الشاشة؛ خفّض معدل اللقطات المتتابعة ثم أعد المحاولة.");
                return;
            }
            ScreenshotRequest request = new ScreenshotRequest(callback, requestedTaskId, requestedTaskGoal, requestedActionIndex, requestedLabel, archiveEvidence);
            if (smartTaskActive) screenshotRequestQueue.addFirst(request);
            else screenshotRequestQueue.addLast(request);
            pumpNextScreenshot();
        });
    }

    private void pumpNextScreenshot() {
        if (screenshotRequestInFlight || screenshotRequestQueue.isEmpty() || isServiceShuttingDown()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        long elapsed = lastScreenshotRequestElapsed == 0L ? Long.MAX_VALUE : now - lastScreenshotRequestElapsed;
        long delay = elapsed >= screenshotMinimumSpacingMs ? 0L : screenshotMinimumSpacingMs - Math.max(0L, elapsed);
        if (delay > 0L) {
            if (!screenshotPumpScheduled) {
                screenshotPumpScheduled = true;
                handler.postDelayed(screenshotPumpRunnable, delay);
            }
            return;
        }
        ScreenshotRequest request = screenshotRequestQueue.pollFirst();
        if (request == null) return;
        screenshotRequestInFlight = true;
        lastScreenshotRequestElapsed = now;
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, screenshotExecutor, new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    HardwareBuffer buffer = null;
                    Bitmap wrapped = null;
                    Bitmap copy = null;
                    String failure = null;
                    try {
                        buffer = result.getHardwareBuffer();
                        wrapped = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (wrapped == null) failure = "تعذر تحويل بيانات الصورة";
                        else {
                            copy = wrapped.copy(Bitmap.Config.ARGB_8888, false);
                            if (copy == null) failure = "تعذر نسخ لقطة الشاشة";
                        }
                    } catch (Exception e) {
                        failure = e.getClass().getSimpleName();
                    } finally {
                        if (wrapped != null && !wrapped.isRecycled()) wrapped.recycle();
                        if (buffer != null) buffer.close();
                    }
                    final Bitmap screenshot = copy;
                    final String error = failure;
                    if (request.archiveEvidence && screenshot != null && !request.taskId.isEmpty()) {
                        String savedFrame = BotnaTaskEvidenceStore.saveFrame(getApplicationContext(), request.taskId, request.taskGoal,
                                request.actionIndex, request.label, screenshot);
                        if (savedFrame == null || savedFrame.isEmpty()) {
                            BotnaTaskEvidenceStore.recordEvent(getApplicationContext(), request.taskId, "screenshot_archive_failure",
                                    request.actionIndex, request.label, "نجح التقاط الشاشة، لكن تعذّر حفظ ملف الصورة في أرشيف المهمة.", false, null);
                        }
                    } else if (request.archiveEvidence && screenshot == null && !request.taskId.isEmpty()) {
                        BotnaTaskEvidenceStore.recordEvent(getApplicationContext(), request.taskId, "screenshot_failure",
                                request.actionIndex, request.label, failure == null ? "فشل التقاط الشاشة دون تفاصيل." : failure, false, null);
                    }
                    handler.post(() -> {
                        if (isServiceShuttingDown()) {
                            if (screenshot != null && !screenshot.isRecycled()) screenshot.recycle();
                            return;
                        }
                        android.content.SharedPreferences prefs = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE);
                        boolean available = screenshot != null && smartTaskActive;
                        prefs.edit().putBoolean(PREF_AGENT_SCREEN_AVAILABLE, available)
                                .putLong(PREF_AGENT_SCREEN_AT, screenshot == null ? 0L : System.currentTimeMillis()).apply();
                        completeScreenshotRequest(request, screenshot, error);
                    });
                }

                @Override public void onFailure(int errorCode) {
                    if (request.archiveEvidence && !request.taskId.isEmpty()) BotnaTaskEvidenceStore.recordEvent(getApplicationContext(), request.taskId,
                            "screenshot_failure", request.actionIndex, request.label, screenshotErrorMessage(errorCode, request.intervalRetries), false, null);
                    handler.post(() -> {
                        if (isServiceShuttingDown()) return;
                        if (errorCode == 3 && request.intervalRetries < MAX_SCREENSHOT_INTERVAL_RETRIES) {
                            request.intervalRetries++;
                            screenshotMinimumSpacingMs = Math.min(1800L, Math.max(700L, screenshotMinimumSpacingMs + 350L));
                            screenshotRequestInFlight = false;
                            screenshotRequestQueue.addFirst(request);
                            scheduleScreenshotPump(Math.max(screenshotMinimumSpacingMs, 700L));
                            return;
                        }
                        getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit()
                                .putBoolean(PREF_AGENT_SCREEN_AVAILABLE, false).putLong(PREF_AGENT_SCREEN_AT, 0L).apply();
                        completeScreenshotRequest(request, null, screenshotErrorMessage(errorCode, request.intervalRetries));
                    });
                }
            });
        } catch (Exception e) {
            handler.post(() -> completeScreenshotRequest(request, null, e.getClass().getSimpleName()));
        }
    }

    private void completeScreenshotRequest(ScreenshotRequest request, Bitmap screenshot, String error) {
        screenshotRequestInFlight = false;
        lastScreenshotRequestElapsed = android.os.SystemClock.elapsedRealtime();
        try {
            request.callback.done(screenshot, error);
        } catch (Exception callbackError) {
            if (screenshot != null && !screenshot.isRecycled()) screenshot.recycle();
            saveResult("تعذر إكمال معالجة لقطة الشاشة: " + callbackError.getClass().getSimpleName());
        } finally {
            pumpNextScreenshot();
        }
    }

    private void scheduleScreenshotPump(long delayMs) {
        handler.removeCallbacks(screenshotPumpRunnable);
        screenshotPumpScheduled = true;
        handler.postDelayed(screenshotPumpRunnable, Math.max(1L, delayMs));
    }

    private boolean isServiceShuttingDown() {
        return screenshotExecutor.isShutdown() || screenshotExecutor.isTerminated();
    }

    private String screenshotErrorMessage(int errorCode, int retries) {
        if (errorCode == 1) return "خطأ داخلي من خدمة لقطة الشاشة في أندرويد.";
        if (errorCode == 2) return "خدمة إمكانية الوصول لا تملك صلاحية لقطة الشاشة الآن.";
        if (errorCode == 3) return "رفض أندرويد لقطة الشاشة لأن الطلب جاء بعد لقطة سابقة بفاصل قصير جدًا؛ أُعيدت المحاولة تلقائيًا " + retries + " مرات.";
        if (errorCode == 4) return "الشاشة المطلوبة غير متاحة أو تغيّر معرّف العرض.";
        if (errorCode == 5) return "نافذة إمكانية الوصول الحالية غير صالحة لالتقاط الشاشة.";
        if (errorCode == 6) return "التطبيق الظاهر يمنع تصوير محتواه لأسباب أمنية (FLAG_SECURE).";
        return "رمز خطأ لقطة الشاشة من أندرويد: " + errorCode;
    }

    private void showCropSelector(Bitmap screenshot, int purpose) {
        cropSelector = new CropSelector(screenshot, purpose);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        configureFullScreenOverlay(p);
        try { wm.addView(cropSelector, p); }
        catch (Exception e) { cropSelector = null; replaceImageTarget = false; screenshot.recycle(); showBubble(); showPanel(); report("تعذر فتح أداة القص."); }
    }

    private void finishCrop(Bitmap screenshot, RectF selection, int viewWidth, int viewHeight) {
        Bitmap cropped = null;
        try {
            if (screenshot == null || screenshot.isRecycled()) throw new IllegalStateException("صورة الشاشة غير متاحة");
            float sx = screenshot.getWidth() / (float) Math.max(1, viewWidth);
            float sy = screenshot.getHeight() / (float) Math.max(1, viewHeight);

            if (cropPurpose == CROP_SEARCH_REGION || cropPurpose == CROP_LIBRARY_SEARCH_REGION) {
                int leftPct = clampInt(Math.round(selection.left / Math.max(1f, viewWidth) * 100f), 0, 99);
                int topPct = clampInt(Math.round(selection.top / Math.max(1f, viewHeight) * 100f), 0, 99);
                int rightPct = clampInt(Math.round(selection.right / Math.max(1f, viewWidth) * 100f), leftPct + 1, 100);
                int bottomPct = clampInt(Math.round(selection.bottom / Math.max(1f, viewHeight) * 100f), topPct + 1, 100);
                if (cropPurpose == CROP_SEARCH_REGION) {
                    JSONObject script = ensureScript();
                    JSONObject step = findStepById(script, cropStepId);
                    if (step == null) throw new IllegalStateException("لم يتم العثور على خطوة الصورة التي ستُعدّل منطقة بحثها");
                    putString(step, "searchRegionMode", "custom");
                    putInt(step, "searchX", leftPct); putInt(step, "searchY", topPct);
                    putInt(step, "searchW", Math.max(1, rightPct - leftPct));
                    putInt(step, "searchH", Math.max(1, bottomPct - topPct));
                    BotnaStore.saveSteps(this, script, BotnaStore.steps(script));
                    report("تم حفظ منطقة بحث مخصصة لهذه الخطوة.");
                } else {
                    JSONArray libraryItems = getLibrary();
                    JSONObject item = findLibraryImage(libraryItems, activeLibraryId);
                    if (item == null) throw new IllegalStateException("لم يتم العثور على صورة المكتبة لتعديل منطقة بحثها");
                    putString(item, "searchRegionMode", "custom");
                    putInt(item, "searchX", leftPct); putInt(item, "searchY", topPct);
                    putInt(item, "searchW", Math.max(1, rightPct - leftPct));
                    putInt(item, "searchH", Math.max(1, bottomPct - topPct));
                    saveLibrary(libraryItems);
                    report("تم حفظ منطقة بحث مخصصة لقالب المكتبة.");
                }
            } else {
                int left = Math.max(0, Math.min(screenshot.getWidth() - 1, (int) (selection.left * sx)));
                int top = Math.max(0, Math.min(screenshot.getHeight() - 1, (int) (selection.top * sy)));
                int right = Math.max(left + 1, Math.min(screenshot.getWidth(), (int) Math.ceil(selection.right * sx)));
                int bottom = Math.max(top + 1, Math.min(screenshot.getHeight(), (int) Math.ceil(selection.bottom * sy)));
                cropped = Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top);

                int captureX = clampInt(Math.round(selection.left / Math.max(1f, viewWidth) * 100f) - 1, 0, 99);
                int captureY = clampInt(Math.round(selection.top / Math.max(1f, viewHeight) * 100f) - 1, 0, 99);
                int captureRight = clampInt(Math.round(selection.right / Math.max(1f, viewWidth) * 100f) + 1, captureX + 1, 100);
                int captureBottom = clampInt(Math.round(selection.bottom / Math.max(1f, viewHeight) * 100f) + 1, captureY + 1, 100);
                int captureW = Math.max(1, captureRight - captureX);
                int captureH = Math.max(1, captureBottom - captureY);
                boolean library = cropPurpose == CROP_LIBRARY_IMAGE;
                File folder = new File(getFilesDir(), library ? "botna_library" : "botna_targets");
                if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد الصور");
                String fileName = (library ? "library_" : "target_") + System.currentTimeMillis() + ".png";
                File file = new File(folder, fileName);
                try (FileOutputStream out = new FileOutputStream(file)) {
                    if (!cropped.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new IllegalStateException("فشل ضغط الصورة إلى PNG");
                    out.flush();
                }

                if (library) {
                    JSONArray libraryItems = BotnaStore.imageLibrary(this);
                    JSONObject image = null;
                    if (!libraryReplaceId.isEmpty()) {
                        image = findLibraryImage(libraryItems, libraryReplaceId);
                        if (image != null) {
                            String oldName = image.optString("fileName", "");
                            if (!oldName.isEmpty() && !oldName.equals(fileName)) {
                                File oldFile = new File(new File(getFilesDir(), "botna_library"), oldName);
                                if (oldFile.exists()) oldFile.delete();
                            }
                            putString(image, "fileName", fileName);
                        }
                    }
                    if (image == null) {
                        image = new JSONObject();
                        image.put("id", "img" + System.nanoTime());
                        image.put("name", libraryPendingName == null || libraryPendingName.trim().isEmpty() ? "صورة مكتبة جديدة" : libraryPendingName.trim());
                        image.put("fileName", fileName);
                        image.put("threshold", 85); image.put("tapOnMatch", true);
                        image.put("retries", 2); image.put("retryDelay", 500);
                        image.put("beforeDelay", 0); image.put("afterDelay", 0);
                        image.put("searchRegionMode", "full");
                        libraryItems.put(image);
                    } else putString(image, "name", libraryPendingName == null || libraryPendingName.trim().isEmpty() ? image.optString("name", "صورة") : libraryPendingName.trim());
                    putInt(image, "captureX", captureX); putInt(image, "captureY", captureY);
                    putInt(image, "captureW", captureW); putInt(image, "captureH", captureH);
                    BotnaStore.saveImageLibrary(this, libraryItems);
                    libraryReplaceId = ""; activeLibraryId = "";
                    report("تم حفظ صورة المكتبة بنجاح.");
                } else {
                    JSONObject script = ensureScript();
                    JSONArray steps = BotnaStore.steps(script);
                    JSONObject step = findStepById(script, cropStepId);
                    if (step == null) throw new IllegalStateException("لم يتم العثور على خطوة الهدف");
                    JSONArray images = step.optJSONArray("images");
                    if (replaceImageTarget || images == null) images = new JSONArray();
                    images.put(fileName);
                    step.put("images", images);
                    putInt(step, "captureX", captureX); putInt(step, "captureY", captureY);
                    putInt(step, "captureW", captureW); putInt(step, "captureH", captureH);
                    if (step.optBoolean("waitForImageOnly", false)) putString(step, "label", "انتظار ظهور صورة");
                    else putString(step, "label", "مطابقة صورة الهدف");
                    replaceImageTarget = false;
                    BotnaStore.saveSteps(this, script, steps);
                    cropCreatedNewStep = false;
                    report("تم حفظ هدف الصورة كخطوة مستقلة.");
                }
            }
        } catch (Exception e) {
            replaceImageTarget = false;
            if (cropPurpose == CROP_TARGET) discardEmptyCreatedCropStep();
            report("تعذر حفظ التحديد: " + e.getClass().getSimpleName() + " — " + e.getMessage());
        } finally {
            View oldSelector = cropSelector;
            removeView(oldSelector);
            cropSelector = null;
            if (cropped != null && !cropped.isRecycled()) cropped.recycle();
            final Bitmap sourceToRelease = screenshot;
            if (sourceToRelease != null && !sourceToRelease.isRecycled()) {
                handler.postDelayed(() -> { try { if (!sourceToRelease.isRecycled()) sourceToRelease.recycle(); } catch (Exception ignored) { } }, 500);
            }
            cropPurpose = CROP_TARGET;
            libraryPendingName = ""; libraryReplaceId = ""; activeLibraryId = "";
            showBubble();
            showPanel();
        }
    }

    private JSONObject findStepById(JSONObject script, String id) {
        JSONArray steps = BotnaStore.steps(script);
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step != null && id != null && id.equals(step.optString("id"))) return step;
        }
        return null;
    }

    private int clampInt(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private void editSearchRegion(JSONObject script, JSONObject step) {
        String mode = step.optString("searchRegionMode", "full");
        showChoice("منطقة البحث عن الصورة", new String[]{
                "الشاشة الكاملة (افتراضي)" + ("full".equals(mode) ? " ✓" : ""),
                "مكان القص الأصلي" + ("original".equals(mode) ? " ✓" : ""),
                "تحديد منطقة مخصصة" + ("custom".equals(mode) ? " ✓" : "")
        }, choice -> {
            if (choice == 0 || choice == 1) {
                String selectedMode = choice == 0 ? "full" : "original";
                if ("original".equals(selectedMode) && !step.has("captureW")) {
                    report("لا يوجد موضع قص محفوظ لهذه الخطوة؛ استخدم الشاشة الكاملة أو حدّد منطقة مخصصة."); return;
                }
                putString(step, "searchRegionMode", selectedMode);
                BotnaStore.saveSteps(this, script, BotnaStore.steps(script));
                report(choice == 0 ? "تم ضبط البحث على الشاشة الكاملة." : "سيُحصر البحث في المنطقة التي قُصّ منها الهدف أول مرة/آخر زاوية."); rebuildPanel();
            } else {
                cropStepId = step.optString("id");
                cropCreatedNewStep = false; replaceImageTarget = false;
                cropPurpose = CROP_SEARCH_REGION;
                beginCaptureForCrop();
            }
        });
    }

    private void discardEmptyCreatedCropStep() {
        if (!cropCreatedNewStep || cropStepId == null || cropStepId.isEmpty()) return;
        String id = cropStepId;
        cropCreatedNewStep = false;
        JSONObject script = ensureScript();
        JSONArray old = BotnaStore.steps(script);
        JSONArray next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) {
                JSONArray images = item.optJSONArray("images");
                if (images == null || images.length() == 0) { removed = true; continue; }
            }
            next.put(item);
        }
        if (removed) BotnaStore.saveSteps(this, script, next);
    }

    private void cancelCrop(Bitmap screenshot) {
        replaceImageTarget = false;
        if (cropPurpose == CROP_TARGET) discardEmptyCreatedCropStep();

        View oldSelector = cropSelector;
        removeView(oldSelector);
        cropSelector = null;
        final Bitmap sourceToRelease = screenshot;
        if (sourceToRelease != null && !sourceToRelease.isRecycled()) {
            handler.postDelayed(() -> {
                try {
                    if (!sourceToRelease.isRecycled()) sourceToRelease.recycle();
                } catch (Exception ignored) { }
            }, 500);
        }
        cropPurpose = CROP_TARGET;
        libraryPendingName = ""; libraryReplaceId = ""; activeLibraryId = "";
        showBubble();
        showPanel();
        report("تم إلغاء التحديد.");
    }

    private class CropSelector extends View {
        private final Bitmap screenshot;
        private final int purpose;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF rect = new RectF();
        private int mode = 0;
        private float downX, downY;
        private final RectF initial = new RectF();
        private final float density;
        private final float minimumSize;
        CropSelector(Bitmap bitmap, int purpose) {
            super(BotnaAccessibilityService.this);
            screenshot = bitmap; this.purpose = purpose;
            density = getResources().getDisplayMetrics().density;
            minimumSize = Math.max(1f, 1f * density);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (rect.isEmpty()) {
                float cw = Math.min(w * .62f, Math.max(minimumSize * 2, w * .42f));
                float ch = Math.min(h * .35f, Math.max(minimumSize * 2, h * .28f));
                rect.set((w-cw)/2f, (h-ch)/2f, (w+cw)/2f, (h+ch)/2f);
            }
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.drawBitmap(screenshot, null, new RectF(0, 0, getWidth(), getHeight()), paint);
            paint.setColor(Color.argb(145, 0, 0, 0));
            c.drawRect(0,0,getWidth(),rect.top,paint); c.drawRect(0,rect.bottom,getWidth(),getHeight(),paint);
            c.drawRect(0,rect.top,rect.left,rect.bottom,paint); c.drawRect(rect.right,rect.top,getWidth(),rect.bottom,paint);
            paint.setColor(CYAN); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.1f*density); c.drawRect(rect,paint);

            paint.setStyle(Paint.Style.FILL); float r = 2.8f*density;
            paint.setColor(Color.argb(245, 28, 229, 244));
            c.drawCircle(rect.left,rect.top,r,paint); c.drawCircle(rect.right,rect.top,r,paint);
            c.drawCircle(rect.left,rect.bottom,r,paint); c.drawCircle(rect.right,rect.bottom,r,paint);

            paint.setColor(Color.argb(230,5,7,12)); paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(8*density, 8*density, getWidth()-8*density, 49*density), 11*density,11*density,paint);
            float actionTop = 11*density, actionBottom = 46*density;
            paint.setColor(Color.argb(238,7,15,26));
            c.drawRoundRect(new RectF(12*density,actionTop,86*density,actionBottom),9*density,9*density,paint);
            c.drawRoundRect(new RectF(getWidth()-86*density,actionTop,getWidth()-12*density,actionBottom),9*density,9*density,paint);
            paint.setTextSize(11*density); paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Color.rgb(255,132,152)); c.drawText("إلغاء",49*density,34*density,paint);
            paint.setColor(CYAN); c.drawText("✓ حفظ",getWidth()-49*density,34*density,paint);
            paint.setColor(TEXT); paint.setTextSize(11*density);
            String hint = (purpose == CROP_SEARCH_REGION || purpose == CROP_LIBRARY_SEARCH_REGION) ? "اسحب الإطار حتى حواف الشاشة" :
                    purpose == CROP_LIBRARY_IMAGE ? "قص صورة للمكتبة" : "حرّك الإطار أو اسحب زاوية";
            c.drawText(hint, getWidth()/2f, 34*density, paint);
        }
        private float distance(float x,float y,float a,float b) { float dx=x-a,dy=y-b; return (float)Math.sqrt(dx*dx+dy*dy); }
        private float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
        private int hitHandle(float x, float y) {

            float hit=13*density;
            float[] ds = new float[]{distance(x,y,rect.left,rect.top), distance(x,y,rect.right,rect.top),
                    distance(x,y,rect.left,rect.bottom), distance(x,y,rect.right,rect.bottom)};
            int nearest=0; float nearestDistance=Float.MAX_VALUE;
            for(int i=0;i<ds.length;i++) if(ds[i]<nearestDistance) { nearestDistance=ds[i]; nearest=i+1; }
            if(nearestDistance<hit) return nearest;
            if(Math.abs(y-rect.top)<8*density && x>=rect.left && x<=rect.right) return 6;
            if(Math.abs(y-rect.bottom)<8*density && x>=rect.left && x<=rect.right) return 7;
            if(Math.abs(x-rect.left)<8*density && y>=rect.top && y<=rect.bottom) return 8;
            if(Math.abs(x-rect.right)<8*density && y>=rect.top && y<=rect.bottom) return 9;
            return 0;
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if(e.getAction()==MotionEvent.ACTION_DOWN) {
                downX=x; downY=y; initial.set(rect);
                int handle=hitHandle(x,y);
                if(handle!=0) mode=handle;
                else if(y>=9*density && y<=48*density && x>=getWidth()-90*density) mode=10;
                else if(y>=9*density && y<=48*density && x<=92*density) mode=11;
                else if(y>=9*density && y<=48*density && !rect.contains(x,y)) mode=12;
                else if(rect.contains(x,y)) mode=5;
                else mode=0;
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE) {
                float dx=x-downX, dy=y-downY;
                float w=getWidth(), h=getHeight();
                if(mode==0) rect.set(clamp(Math.min(downX,x),0,w),clamp(Math.min(downY,y),0,h),clamp(Math.max(downX,x),0,w),clamp(Math.max(downY,y),0,h));
                else if(mode==12) return true;
                else if(mode==1) { rect.left=clamp(initial.left+dx,0,initial.right-minimumSize); rect.top=clamp(initial.top+dy,0,initial.bottom-minimumSize); }
                else if(mode==2) { rect.right=clamp(initial.right+dx,initial.left+minimumSize,w); rect.top=clamp(initial.top+dy,0,initial.bottom-minimumSize); }
                else if(mode==3) { rect.left=clamp(initial.left+dx,0,initial.right-minimumSize); rect.bottom=clamp(initial.bottom+dy,initial.top+minimumSize,h); }
                else if(mode==4) { rect.right=clamp(initial.right+dx,initial.left+minimumSize,w); rect.bottom=clamp(initial.bottom+dy,initial.top+minimumSize,h); }
                else if(mode==5) { float rw=initial.width(), rh=initial.height(); float left=clamp(initial.left+dx,0,Math.max(0,w-rw)); float top=clamp(initial.top+dy,0,Math.max(0,h-rh)); rect.set(left,top,left+rw,top+rh); }
                else if(mode==6) rect.top=clamp(initial.top+dy,0,rect.bottom-minimumSize);
                else if(mode==7) rect.bottom=clamp(initial.bottom+dy,rect.top+minimumSize,h);
                else if(mode==8) rect.left=clamp(initial.left+dx,0,rect.right-minimumSize);
                else if(mode==9) rect.right=clamp(initial.right+dx,rect.left+minimumSize,w);
                invalidate(); return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP) {
                if(mode==10) finishCrop(screenshot,new RectF(rect),getWidth(),getHeight());
                else if(mode==11) cancelCrop(screenshot);
                else { invalidate(); }
                return true;
            }
            return true;
        }
    }

    private interface MatchCallback {
        void done(ImageMatcher.Result result, String error);
    }

    private interface ActionResultCallback { void done(boolean success, String message); }

    private void runActiveScript() {
        if (running) { report(paused ? "السكربت متوقف مؤقتًا؛ استخدم الاستكمال." : "السكربت يعمل بالفعل."); return; }
        JSONObject script = ensureScript();
        JSONArray sourceSteps = BotnaStore.steps(script);
        if (sourceSteps.length() == 0) { report("أضف خطوات قبل التشغيل."); return; }
        JSONArray scriptSteps = expandCustomSteps(sourceSteps);
        if (scriptSteps.length() == 0) { report("لا توجد أوامر تنفيذية. راجع الخطوات المخصصة."); return; }
        String structureError = validateScriptStructure(scriptSteps);
        if (structureError != null) { report("لم يبدأ السكربت: " + structureError); return; }
        panelWasVisibleAtRunStart = panelVisible;
        currentScriptId = script.optString("id", "");
        executionSteps = scriptSteps;
        customTestMode = false;
        currentStepIndex = 0;
        currentBranches = new ArrayDeque<>();
        currentLoops = new ArrayDeque<>();
        lastStepSuccess = true;
        paused = false;
        running = true;
        BotnaAgentKeepAliveService.start(this);
        executionInFlight = false;
        executionGeneration++;
        hidePanel();
        removeView(bubble); bubble = null;
        runControlsExpanded = false;
        report("بدأ تنفيذ السكربت من الخطوة الأولى.");
        showRunControls();
        runStep();
    }

    private void pauseScript() {
        if (!running || paused) return;
        paused = true;
        BotnaAgentKeepAliveService.stop(this);
        report("تم الإيقاف المؤقت عند الخطوة " + (currentStepIndex + 1) + ". اضغط استكمال للمتابعة من الموضع نفسه.");
        showRunControls();
    }

    private void resumeScript() {
        if (!running || !paused) return;
        paused = false;
        BotnaAgentKeepAliveService.start(this);
        report("استُكمل السكربت من الخطوة " + (currentStepIndex + 1) + ".");
        showRunControls();
        if (!executionInFlight) runStep();
    }

    private void restartScript() {
        if (!running) { runActiveScript(); return; }
        executionGeneration++;
        currentStepIndex = 0;
        currentBranches = new ArrayDeque<>();
        currentLoops = new ArrayDeque<>();
        lastStepSuccess = true;
        paused = false;
        executionInFlight = false;
        report("إعادة تشغيل السكربت من البداية.");
        showRunControls();
        runStep();
    }

    private void stopScript() {
        if (!running && !singleStepTesting && !smartTaskActive) { report("لا يوجد تنفيذ لإيقافه."); return; }
        if (smartTaskActive) { endSmartTask("أوقف المستخدم الوكيل يدويًا. يمكن مراجعة الرسائل والسكربت الجزئي المحفوظ.", false); return; }
        smartTaskActive = false; smartTaskGoal = ""; smartTaskId = ""; smartCurrentActionLabel = ""; smartCurrentActionDefinition = null; smartTaskHistory = new JSONArray(); smartTaskActionCount = 0; smartActionGoalRevision = smartGoalRevision;
        removeView(smartPlanningControls); smartPlanningControls = null;
        executionGeneration++;
        running = false; paused = false; executionInFlight = false;
        currentBranches.clear(); currentLoops.clear();
        singleStepTesting = false; customTestMode = false;
        BotnaAgentKeepAliveService.stop(this);
        executionSteps = new JSONArray();
        removeView(runControls); runControls = null; runControlsParams = null; runControlsExpanded = false;
        removeView(runStatusView); runStatusView = null; runStatusParams = null;
        report("تم إيقاف التنفيذ نهائيًا.");
        showBubble();
        if (panelWasVisibleAtRunStart) showPanel();
    }

    private void showRunStatus() {
        if (wm == null || !running || runControlsParams == null) return;
        removeView(runStatusView); runStatusView = null; runStatusTextView = null;
        android.content.SharedPreferences prefs = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE);
        runStatusCollapsed = prefs.getBoolean("run_status_collapsed_v1", false);
        String stored = getLastResult(); int divider = stored.indexOf(" — ");
        String message = divider >= 0 ? stored.substring(divider + 3) : stored;
        if (message.length() > 76) message = message.substring(0, 73) + "…";
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(runStatusCollapsed ? 0 : dp(5), 0, runStatusCollapsed ? 0 : dp(3), 0);
        pill.setBackground(glassShape(Color.argb(232, 8, 14, 24), Color.argb(220, 35, 226, 244), 1, runStatusCollapsed ? 18 : 14));
        TextView text = label(message, 10, TEXT, false);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        runStatusTextView = text;
        Button toggle = button(runStatusCollapsed ? "↗" : "⌄", true);
        toggle.setTextSize(13); toggle.setMinHeight(dp(28)); toggle.setMinimumWidth(dp(28));
        toggle.setPadding(0, 0, 0, 0);
        toggle.setBackground(glassShape(Color.argb(210, 17, 32, 48), CYAN, 1, runStatusCollapsed ? 16 : 10));
        if (runStatusCollapsed) {
            pill.setGravity(Gravity.CENTER);
            pill.addView(toggle, new LinearLayout.LayoutParams(dp(32), dp(32)));
        } else {
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, dp(30), 1f);
            textLp.setMargins(dp(2), 0, dp(3), 0);
            pill.addView(text, textLp);
            pill.addView(toggle, new LinearLayout.LayoutParams(dp(28), dp(28)));
        }
        pill.setOnClickListener(v -> {
            runStatusCollapsed = !runStatusCollapsed;
            prefs.edit().putBoolean("run_status_collapsed_v1", runStatusCollapsed).apply();
            showRunStatus();
        });
        toggle.setOnClickListener(v -> {
            runStatusCollapsed = !runStatusCollapsed;
            prefs.edit().putBoolean("run_status_collapsed_v1", runStatusCollapsed).apply();
            showRunStatus();
        });
        int width = runStatusCollapsed ? dp(34) : Math.max(dp(150), Math.min(dp(300), realScreenMetrics().widthPixels - dp(12)));
        int height = runStatusCollapsed ? dp(34) : dp(34);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, height,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        try { wm.addView(pill, params); runStatusView = pill; runStatusParams = params; updateRunStatusPosition(); }
        catch (Exception ignored) { runStatusView = null; runStatusTextView = null; runStatusParams = null; }
    }

    private void updateRunStatusPosition() {
        if (wm == null || runStatusView == null || runStatusParams == null || runControlsParams == null) return;
        android.util.DisplayMetrics dm = realScreenMetrics();
        int mainSize = dp(58);
        int boxWidth = runControlsExpanded ? dp(174) : mainSize;
        int boxHeight = runControlsExpanded ? dp(174) : mainSize;
        int mainLeft = runControlsExpanded
                ? (getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getInt("run_controls_side", 1) == 0 ? 0 : boxWidth - mainSize)
                : 0;
        int mainTop = runControlsExpanded ? (boxHeight - mainSize) / 2 : 0;
        int centerX = runControlsParams.x + mainLeft + mainSize / 2;
        int width = runStatusCollapsed ? dp(34) : Math.max(dp(150), Math.min(dp(300), dm.widthPixels - dp(12)));
        runStatusParams.width = width;
        runStatusParams.height = runStatusCollapsed ? dp(34) : dp(34);
        runStatusParams.x = clampInt(centerX - width / 2, 0, Math.max(0, dm.widthPixels - width));
        runStatusParams.y = Math.max(0, runControlsParams.y + mainTop - runStatusParams.height - dp(4));
        try { wm.updateViewLayout(runStatusView, runStatusParams); } catch (Exception ignored) { }
    }

    private void updateRunStatus() {
        if (!running || runStatusView == null) return;
        String stored = getLastResult(); int divider = stored.indexOf(" — ");
        String message = divider >= 0 ? stored.substring(divider + 3) : stored;
        if (message.length() > 76) message = message.substring(0, 73) + "…";
        if (runStatusTextView != null) runStatusTextView.setText(message);
        updateRunStatusPosition();
    }

    private android.graphics.Rect findInputMethodBounds() {
        if (Build.VERSION.SDK_INT < 21) return null;
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null) return null;
            android.graphics.Rect result = null;
            for (AccessibilityWindowInfo aw : windows) {
                if (aw == null) continue;
                try {
                    if (aw.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                        android.graphics.Rect bounds = new android.graphics.Rect();
                        aw.getBoundsInScreen(bounds);
                        if (bounds.height() > dp(80) && bounds.width() > dp(120)) {
                            if (result == null || bounds.top < result.top) result = bounds;
                        }
                    }
                } finally { try { aw.recycle(); } catch (Exception ignored) { } }
            }
            return result;
        } catch (Exception ignored) { return null; }
    }

    private void adjustFloatingControlsForIme() {
        if (!running || wm == null || runControls == null || runControlsParams == null || runControlsDragging) return;
        android.util.DisplayMetrics dm = realScreenMetrics();
        android.content.SharedPreferences prefs = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE);
        int mainSize = dp(58), boxHeight = runControlsExpanded ? dp(174) : mainSize;
        android.graphics.Rect ime = findInputMethodBounds();
        int desiredY;
        if (ime != null && ime.top > dp(100)) {
            desiredY = Math.max(dp(4), ime.top - boxHeight - dp(12));
        } else {
            int storedTop = prefs.getInt("run_controls_y", Math.max(dp(40), dm.heightPixels - mainSize - dp(92)));
            desiredY = runControlsExpanded ? storedTop - dp(58) : storedTop;
        }
        runControlsParams.y = clampInt(desiredY, 0, Math.max(0, dm.heightPixels - boxHeight));
        try { wm.updateViewLayout(runControls, runControlsParams); } catch (Exception ignored) { }
        updateRunStatusPosition();
    }

    private void showRunControls() {
        if (wm == null || !running) return;
        boolean expanded = runControlsExpanded;
        removeView(runControls); runControls = null;
        removeView(runStatusView); runStatusView = null; runStatusParams = null;

        android.util.DisplayMetrics metrics = realScreenMetrics();
        android.content.SharedPreferences prefs = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE);
        final int side = prefs.getInt("run_controls_side", 1);
        final int mainSize = dp(58), smallSize = dp(42);
        final int boxWidth = expanded ? dp(174) : mainSize;
        final int boxHeight = expanded ? dp(174) : mainSize;
        FrameLayout dock = new FrameLayout(this);
        dock.setElevation(dp(18));
        int mainLeft = side == 0 ? 0 : boxWidth - mainSize;
        int mainTop = (boxHeight - mainSize) / 2;
        if (expanded) {
            int topX1 = side == 0 ? dp(62) : boxWidth - dp(62) - smallSize;
            int topX2 = side == 0 ? dp(94) : boxWidth - dp(94) - smallSize;
            Button restart = radialControl("↻", "إعادة التشغيل من البداية");
            Button pause = radialControl(paused ? "▶" : "Ⅱ", paused ? "استكمال السكربت" : "إيقاف مؤقت");
            Button stop = radialControl("■", "إيقاف السكربت نهائيًا");
            FrameLayout.LayoutParams restartLp = new FrameLayout.LayoutParams(smallSize, smallSize, Gravity.TOP | Gravity.LEFT);
            restartLp.leftMargin = topX1; restartLp.topMargin = dp(3); dock.addView(restart, restartLp);
            FrameLayout.LayoutParams pauseLp = new FrameLayout.LayoutParams(smallSize, smallSize, Gravity.TOP | Gravity.LEFT);
            pauseLp.leftMargin = topX2; pauseLp.topMargin = (boxHeight - smallSize) / 2; dock.addView(pause, pauseLp);
            FrameLayout.LayoutParams stopLp = new FrameLayout.LayoutParams(smallSize, smallSize, Gravity.TOP | Gravity.LEFT);
            stopLp.leftMargin = topX1; stopLp.topMargin = boxHeight - smallSize - dp(3); dock.addView(stop, stopLp);
            restart.setOnClickListener(v -> restartScript());
            pause.setOnClickListener(v -> { if (paused) resumeScript(); else pauseScript(); });
            stop.setOnClickListener(v -> stopScript());
        }
        TextView main = new TextView(this);
        main.setText(paused ? "▶" : "B"); main.setTextSize(paused ? 18 : 20); main.setTextColor(CYAN);
        main.setGravity(Gravity.CENTER); main.setBackground(glassShape(Color.argb(238, 8, 13, 22), Color.argb(245, 35, 226, 244), 2, 36));
        main.setElevation(dp(20));
        main.setContentDescription(expanded ? "إخفاء أدوات التحكم" : "إظهار أدوات تشغيل السكربت وإيقافه");
        FrameLayout.LayoutParams mainLp = new FrameLayout.LayoutParams(mainSize, mainSize, Gravity.TOP | Gravity.LEFT);
        mainLp.leftMargin = mainLeft; mainLp.topMargin = mainTop; dock.addView(main, mainLp);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(boxWidth, boxHeight,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        int storedMainTop = clampInt(prefs.getInt("run_controls_y", Math.max(dp(40), metrics.heightPixels - mainSize - dp(92))),
                0, Math.max(0, metrics.heightPixels - mainSize));
        params.x = expanded ? (side == 0 ? 0 : Math.max(0, metrics.widthPixels - boxWidth))
                : (side == 0 ? -mainSize / 2 : metrics.widthPixels - mainSize / 2);
        params.y = expanded ? clampInt(storedMainTop - mainTop, 0, Math.max(0, metrics.heightPixels - boxHeight)) : storedMainTop;

        final float[] down = new float[2];
        final int[] origin = new int[2];
        final boolean[] dragged = new boolean[1];
        main.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = event.getRawX(); down[1] = event.getRawY();
                    origin[0] = params.x; origin[1] = params.y; dragged[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - down[0], dy = event.getRawY() - down[1];
                    if (Math.abs(dx) + Math.abs(dy) > dp(5)) { dragged[0] = true; runControlsDragging = true; }
                    if (dragged[0]) {
                        params.x = clampInt(origin[0] + Math.round(dx), 0, Math.max(0, metrics.widthPixels - boxWidth));
                        params.y = clampInt(origin[1] + Math.round(dy), 0, Math.max(0, metrics.heightPixels - boxHeight));
                        try { wm.updateViewLayout(dock, params); } catch (Exception ignored) { }
                        updateRunStatusPosition();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    runControlsDragging = false;
                    if (dragged[0]) {
                        int mainCenterX = params.x + (side == 0 ? mainSize / 2 : boxWidth - mainSize / 2);
                        int newSide = mainCenterX < metrics.widthPixels / 2 ? 0 : 1;
                        int newMainTop = params.y + mainTop;
                        newMainTop = clampInt(newMainTop, 0, Math.max(0, metrics.heightPixels - mainSize));
                        runControlsExpanded = false;
                        prefs.edit().putInt("run_controls_side", newSide).putInt("run_controls_y", newMainTop).apply();
                        handler.post(this::showRunControls);
                    } else {
                        int currentMainTop = expanded ? params.y + mainTop : params.y;
                        prefs.edit().putInt("run_controls_y", clampInt(currentMainTop, 0, Math.max(0, metrics.heightPixels - mainSize))).apply();
                        runControlsExpanded = !expanded;
                        handler.post(this::showRunControls);
                    }
                    return true;
            }
            return true;
        });
        try { wm.addView(dock, params); runControls = dock; runControlsParams = params; showRunStatus(); adjustFloatingControlsForIme(); }
        catch (Exception e) { runControls = null; runControlsParams = null; saveResult("تعذر إظهار كرة التحكم أثناء تشغيل السكربت: " + e.getClass().getSimpleName()); }
    }

    private Button radialControl(String symbol, String description) {
        Button b = new Button(this);
        b.setText(symbol); b.setTextSize(15); b.setAllCaps(false); b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0); b.setTextColor(TEXT);
        b.setBackground(glassShape(Color.argb(245, 15, 27, 42), Color.argb(235, 35, 226, 244), 1, 36));
        b.setContentDescription(description); b.setElevation(dp(14));
        return b;
    }

    private void runStep() {
        if (!running) return;
        if (smartTaskActive && smartCurrentActionDefinition != null && smartActionGoalRevision != smartGoalRevision) {
            interruptSmartActionForCorrection();
            return;
        }
        if (paused) { executionInFlight = false; showRunControls(); return; }
        if (executionInFlight) return;
        if (smartExternalSequenceActive && currentStepIndex > 0 && !lastStepSuccess) {
            JSONObject currentSequenceStep = executionSteps == null ? null : executionSteps.optJSONObject(currentStepIndex);
            if (currentSequenceStep == null || currentSequenceStep.optInt("_runtimeRetryCount", 0) == 0) {
                finishScript("تم إيقاف بقية سلسلة الإجراءات لأن إحدى الخطوات السابقة فشلت. سيعيد Botna فحص الشاشة قبل التخطيط التالي.");
                return;
            }
        }
        JSONObject script = BotnaStore.getScript(this, currentScriptId);
        if (script == null) { finishScript("توقف السكربت: لم يتم العثور على تعريفه المحفوظ."); return; }
        JSONArray steps = executionSteps;
        if (steps == null) steps = new JSONArray();
        if (currentStepIndex >= steps.length()) {
            finishScript("اكتمل السكربت من البداية إلى النهاية. آخر خطوة: " + (lastStepSuccess ? "نجاح" : "فشل") + ".");
            return;
        }
        JSONObject step = steps.optJSONObject(currentStepIndex);
        if (step == null) { scheduleNext(executionGeneration, 0); return; }
        String type = step.optString("type", "");
        int generation = executionGeneration;
        boolean enabled = step.optBoolean("enabled", true);
        boolean activeBefore = branchAllowed(currentBranches) && loopsAllowed(currentLoops);

        if (TYPE_IF_SUCCESS.equals(type) || TYPE_IF_FAIL.equals(type)) {
            boolean condition = TYPE_IF_SUCCESS.equals(type) ? lastStepSuccess : !lastStepSuccess;
            currentBranches.push(new Branch(activeBefore && enabled, condition));
            scheduleNext(generation, 0); return;
        }
        if (TYPE_ELSE.equals(type)) {
            if (!currentBranches.isEmpty()) currentBranches.peek().inElse = true;
            scheduleNext(generation, 0); return;
        }
        if (TYPE_END_IF.equals(type)) {
            if (!currentBranches.isEmpty()) currentBranches.pop();
            scheduleNext(generation, 0); return;
        }

        if (TYPE_REPEAT_START.equals(type)) {
            int count = Math.max(1, step.optInt("repeatCount", 2));
            currentLoops.push(new LoopFrame(currentStepIndex, count, activeBefore && enabled));
            scheduleNext(generation, 0); return;
        }
        if (TYPE_REPEAT_END.equals(type)) {
            if (currentLoops.isEmpty()) { finishScript("خطأ في بنية التكرار: لا توجد بداية تكرار لهذا الموضع."); return; }
            LoopFrame frame = currentLoops.peek();
            if (!frame.active) {
                currentLoops.pop();
                scheduleNext(generation, 0); return;
            }
            if (frame.remaining > 1) {
                frame.remaining--;
                currentStepIndex = frame.startIndex;
                scheduleNext(generation, 0); return;
            }
            currentLoops.pop();
            scheduleNext(generation, 0); return;
        }

        if (!enabled) {
            report("تجاوز خطوة معطّلة رقم " + (currentStepIndex + 1) + ".");
            scheduleNext(generation, 0); return;
        }
        if (!activeBefore) { scheduleNext(generation, 0); return; }

        report("الخطوة " + (currentStepIndex + 1) + "/" + steps.length() + " — " + step.optString("label", type));
        executionInFlight = true;

        showRunControls();
        int beforeDelay = Math.max(0, Math.min(60000, step.optInt("beforeDelay", 0)));
        Runnable action = () -> {
            if (generation != executionGeneration || !running) return;
            if (paused) { executionInFlight = false; showRunControls(); return; }
            if (smartExternalSequenceActive && BotStudioStore.safetySandboxEnabled(this)
                    && BotStudioStore.requiresManualApproval(step)) {
                requestSensitiveSequenceStepApproval(step, generation);
                return;
            }
            executeActionStep(step, type, generation);
        };
        if (beforeDelay > 0) handler.postDelayed(action, beforeDelay); else action.run();
    }

    private static final class Branch {
        final boolean parentAllowed;
        final boolean matched;
        boolean inElse;
        Branch(boolean parentAllowed, boolean matched) { this.parentAllowed = parentAllowed; this.matched = matched; }
    }

    private static final class LoopFrame {
        final int startIndex;
        int remaining;
        final boolean active;
        LoopFrame(int startIndex, int remaining, boolean active) {
            this.startIndex = startIndex; this.remaining = remaining; this.active = active;
        }
    }

    private static final class StructureFrame {
        final String type;
        boolean hasElse;
        StructureFrame(String type) { this.type = type; }
    }

    private String validateScriptStructure(JSONArray steps) {
        Deque<StructureFrame> stack = new ArrayDeque<>();
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step == null) return "تعريف الخطوة رقم " + (i + 1) + " غير صالح.";
            String type = step.optString("type", "");
            if (TYPE_IF_SUCCESS.equals(type) || TYPE_IF_FAIL.equals(type)) {
                stack.push(new StructureFrame("IF"));
            } else if (TYPE_REPEAT_START.equals(type)) {
                stack.push(new StructureFrame("REPEAT"));
            } else if (TYPE_ELSE.equals(type)) {
                if (stack.isEmpty() || !"IF".equals(stack.peek().type))
                    return "خطوة «وإلا» رقم " + (i + 1) + " غير مرتبطة بشرط مفتوح أو تأتي بترتيب غير صحيح.";
                if (stack.peek().hasElse)
                    return "يوجد أكثر من «وإلا» لنفس الشرط عند الخطوة رقم " + (i + 1) + ".";
                stack.peek().hasElse = true;
            } else if (TYPE_END_IF.equals(type)) {
                if (stack.isEmpty() || !"IF".equals(stack.peek().type))
                    return "خطوة «نهاية الشرط» رقم " + (i + 1) + " لا تطابق شرطًا مفتوحًا.";
                stack.pop();
            } else if (TYPE_REPEAT_END.equals(type)) {
                if (stack.isEmpty() || !"REPEAT".equals(stack.peek().type))
                    return "خطوة «نهاية التكرار» رقم " + (i + 1) + " لا تطابق بداية تكرار مفتوحة.";
                stack.pop();
            }
        }
        if (!stack.isEmpty()) {
            int missingIfEnds = 0, missingRepeatEnds = 0;
            for (StructureFrame frame : stack) {
                if ("IF".equals(frame.type)) missingIfEnds++;
                else if ("REPEAT".equals(frame.type)) missingRepeatEnds++;
            }
            StringBuilder diagnostic = new StringBuilder("تركيب الخطوات غير مكتمل: ");
            if (missingIfEnds > 0) diagnostic.append("يلزم إضافة ").append(missingIfEnds)
                    .append(" خطوة «نهاية الشرط» (END_IF) عند نهاية كل شرط مفتوح");
            if (missingIfEnds > 0 && missingRepeatEnds > 0) diagnostic.append("، و");
            if (missingRepeatEnds > 0) diagnostic.append("يلزم إضافة ").append(missingRepeatEnds)
                    .append(" خطوة «نهاية التكرار» (REPEAT_END) عند نهاية كل تكرار مفتوح");
            diagnostic.append(". لا تضع العلامات كلها في مكان واحد؛ ضع كل علامة مباشرةً بعد الإجراءات التابعة لكتلتها.");
            return diagnostic.toString();
        }
        return null;
    }

    private boolean branchAllowed(Deque<Branch> branches) {
        for (Branch b : branches) {
            if (!b.parentAllowed) return false;
            if (b.inElse ? b.matched : !b.matched) return false;
        }
        return true;
    }

    private boolean loopsAllowed(Deque<LoopFrame> loops) {
        for (LoopFrame loop : loops) if (!loop.active) return false;
        return true;
    }

    private void executeTextClickWithRetries(JSONObject step, int generation, int attempt) {
        if (!running || generation != executionGeneration) return;
        if (paused) { executionInFlight = false; showRunControls(); return; }
        int retries = Math.max(1, step.optInt("retries", 1));
        String targetLabels = step.optString("value", "");
        try {
            accessibilityQueryExecutor.execute(() -> {
                boolean success = false;
                try {
                    for (String candidate : targetLabels.split("\\|")) {
                        if (!candidate.trim().isEmpty() && clickText(candidate.trim())) { success = true; break; }
                    }
                } catch (Exception ignored) { }
                final boolean matched = success;
                handler.post(() -> {
                    if (!running || generation != executionGeneration) return;
                    if (paused) { executionInFlight = false; showRunControls(); return; }
                    if (matched || attempt + 1 >= retries) {
                        lastStepSuccess = matched;
                        report(matched ? "نجحت خطوة النقر على النص: " + targetLabels
                                : "فشلت خطوة النقر على النص بعد " + (attempt + 1) + " محاولة: " + targetLabels);
                        scheduleAfterAction(generation, step);
                        return;
                    }
                    int delay = Math.max(0, step.optInt("retryDelay", 500));
                    handler.postDelayed(() -> executeTextClickWithRetries(step, generation, attempt + 1), delay);
                });
            });
        } catch (Exception e) {
            lastStepSuccess = false;
            report("تعذر تشغيل فحص النص في الخلفية: " + e.getClass().getSimpleName());
            scheduleAfterAction(generation, step);
        }
    }

    private interface SpeedTestCallback { void done(boolean success, String message); }

    private void measureDownloadSpeedAsync(SpeedTestCallback callback) {
        try {
            networkExecutor.execute(() -> {
                HttpURLConnection connection = null;
                boolean success = false;
                String message;
                long started = System.nanoTime();
                long bytesRead = 0;
                try {
                    URL url = new URL("https://speed.cloudflare.com/__down?bytes=3000000");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(15000);
                    connection.setUseCaches(false);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    connection.setRequestProperty("Accept-Encoding", "identity");
                    connection.setRequestProperty("User-Agent", "Botna-Speed-Test/1.0");
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                    InputStream in = connection.getInputStream();
                    try {
                        byte[] buffer = new byte[8192];
                        while (bytesRead < 3_000_000L) {
                            int max = (int) Math.min(buffer.length, 3_000_000L - bytesRead);
                            int read = in.read(buffer, 0, max);
                            if (read < 0) break;
                            bytesRead += read;
                        }
                    } finally { in.close(); }
                    long elapsedMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
                    if (bytesRead < 32_000L) throw new IllegalStateException("لم يُنزّل الاختبار بيانات كافية لقياس موثوق.");
                    double mbps = (bytesRead * 8.0) / (elapsedMs * 1000.0);
                    success = true;
                    message = String.format(Locale.getDefault(),
                            "سرعة التنزيل التقريبية: %.2f Mbps (%.2f MB/s).\nالبيانات المستلمة: %.2f MB.\nزمن التنزيل: %d ms.\nالطريقة: تنزيل عينة 3 MB من Cloudflare؛ النتيجة تتأثر بالازدحام والخادم وليست اختبار رفع أو قياس ping.",
                            mbps, mbps / 8.0, bytesRead / 1_000_000.0, elapsedMs);
                } catch (Exception e) {
                    message = "فشل قياس سرعة التنزيل: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                } finally { if (connection != null) connection.disconnect(); }
                final boolean result = success;
                final String detail = message;
                handler.post(() -> callback.done(result, detail));
            });
        } catch (Exception e) {
            callback.done(false, "تعذر بدء اختبار سرعة التنزيل: " + e.getClass().getSimpleName());
        }
    }

    private void runSpeedTestStep(JSONObject step, int generation) {
        measureDownloadSpeedAsync((ok, message) -> {
            if (!running || generation != executionGeneration) return;
            lastStepSuccess = ok;
            report(message);
            recordResultWithScreenshot("speed_test", currentStepIndex,
                    step.optString("label", "قياس سرعة التنزيل"),
                    ok ? "تم قياس سرعة التنزيل." : "فشل قياس سرعة التنزيل.", message,
                    () -> { if (running && generation == executionGeneration) scheduleAfterAction(generation, step); });
        });
    }

    private void recordResultWithScreenshot(String source, int stepIndex, String label, String message,
                                            String detail, Runnable afterSave) {
        if (!BotnaLogStore.screenshotsEnabled(this)) {
            BotnaLogStore.add(this, source, stepIndex, label, message, detail, null);
            if (afterSave != null) afterSave.run();
            return;
        }
        boolean restorePanel = panelVisible;
        boolean restoreBubble = bubble != null;
        boolean restoreControls = runControls != null;
        hideBotnaOverlaysForCleanScreenshot();
        handler.postDelayed(() -> takeScreenshot((bitmap, error) -> {
            String savedDetail = detail == null ? "" : detail;
            if (error != null) savedDetail += "\nتعذر إرفاق لقطة شاشة: " + error;
            BotnaLogStore.add(this, source, stepIndex, label, message, savedDetail, bitmap);
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (running || restoreControls) showRunControls();
            else if (!smartTaskActive && restoreBubble) showBubble();
            if (restorePanel && !running && !smartTaskActive) showPanel();
            if (afterSave != null) afterSave.run();
        }), 180);
    }

    private void executeActionStep(JSONObject step, String type, int generation) {
        if (TYPE_TEXT.equals(type)) {
            executeTextClickWithRetries(step, generation, 0);
        } else if (TYPE_LAUNCH_APP.equals(type)) {
            String appName = step.optString("appName", step.optString("value", "")).trim();
            boolean ok = launchInstalledApp(appName);
            lastStepSuccess = ok;
            report(ok ? "تم فتح التطبيق: " + appName : "تعذر العثور على تطبيق قابل للفتح باسم: " + appName);
            scheduleAfterAction(generation, step);
        } else if (TYPE_BACK.equals(type)) {
            lastStepSuccess = performGlobalAction(GLOBAL_ACTION_BACK);
            report(lastStepSuccess ? "نجحت خطوة الرجوع." : "فشلت خطوة الرجوع."); scheduleAfterAction(generation, step);
        } else if (TYPE_HOME.equals(type)) {
            lastStepSuccess = performGlobalAction(GLOBAL_ACTION_HOME);
            report(lastStepSuccess ? "تم أمر الرئيسية." : "فشل أمر الرئيسية."); scheduleAfterAction(generation, step);
        } else if (TYPE_WAIT.equals(type)) {
            int delay;
            if (step.optBoolean("randomDelay", false)) {
                int min = Math.max(0, Math.min(60000, step.optInt("minDelay", 500)));
                int max = Math.max(min, Math.min(60000, step.optInt("maxDelay", min)));
                delay = min + (max == min ? 0 : (int) (Math.random() * (max - min + 1)));
            } else delay = Math.max(0, Math.min(60000, step.optInt("delay", 500)));
            lastStepSuccess = true;
            report("تنفيذ انتظار لمدة " + delay + " ms."); scheduleNext(generation, delay);
        } else if (TYPE_IMAGE.equals(type)) {
            runImageMatch(step, 0, generation, (ok, message) -> {
                if (!running || generation != executionGeneration) return;
                lastStepSuccess = ok; report(message);
                scheduleAfterAction(generation, step);
            });
        } else if (TYPE_TAP.equals(type)) {
            android.util.DisplayMetrics metrics = realScreenMetrics();
            int width = metrics.widthPixels, height = metrics.heightPixels;
            int x = coordinateFromScreenshot(step, "screenX", "screenWidth", width, "xPercent");
            int y = coordinateFromScreenshot(step, "screenY", "screenHeight", height, "yPercent");
            performTap(x, y, ok -> { if (!running || generation != executionGeneration) return; lastStepSuccess = ok; report(ok ? "نجح النقر بالموقع المحدد." : "فشل النقر بالموقع المحدد."); scheduleAfterAction(generation, step); });
        } else if (TYPE_LONG_PRESS.equals(type)) {
            android.util.DisplayMetrics metrics=realScreenMetrics(); int width=metrics.widthPixels, height=metrics.heightPixels;
            int x=coordinateFromScreenshot(step, "screenX", "screenWidth", width, "xPercent");
            int y=coordinateFromScreenshot(step, "screenY", "screenHeight", height, "yPercent");
            performLongPress(x,y,step.optInt("duration",800),ok->{if(!running||generation!=executionGeneration)return;lastStepSuccess=ok;report(ok?"نجح الضغط المطوّل.":"فشل الضغط المطوّل.");scheduleAfterAction(generation,step);});
        } else if (TYPE_SWIPE.equals(type)) {
            GestureCallback swipeCallback = ok -> {
                if (!running || generation != executionGeneration) return; lastStepSuccess = ok; report(ok ? "اكتملت حركة السحب." : "فشلت حركة السحب."); scheduleAfterAction(generation, step);
            };
            if (step.has("screenX1") && step.has("screenY1") && step.has("screenX2") && step.has("screenY2")) {
                android.util.DisplayMetrics metrics = realScreenMetrics();
                int width = metrics.widthPixels, height = metrics.heightPixels;
                performSwipePixels(
                        coordinateFromScreenshot(step, "screenX1", "screenWidth", width, "x1"),
                        coordinateFromScreenshot(step, "screenY1", "screenHeight", height, "y1"),
                        coordinateFromScreenshot(step, "screenX2", "screenWidth", width, "x2"),
                        coordinateFromScreenshot(step, "screenY2", "screenHeight", height, "y2"),
                        step.optInt("duration", 450), swipeCallback);
            } else {
                performSwipe(step.optInt("x1", 50), step.optInt("y1", 75), step.optInt("x2", 50), step.optInt("y2", 25), step.optInt("duration", 450), swipeCallback);
            }
        } else if (TYPE_SCROLL.equals(type)) {
            String direction = step.optString("direction", "down");
            int x1=50,y1=75,x2=50,y2=25;
            if ("up".equals(direction)) { y1=25; y2=75; }
            else if ("right".equals(direction)) { x1=25; x2=75; y1=50; y2=50; }
            else if ("left".equals(direction)) { x1=75; x2=25; y1=50; y2=50; }
            performSwipe(x1,y1,x2,y2,step.optInt("duration",420),ok->{if(!running||generation!=executionGeneration)return;lastStepSuccess=ok;report(ok?"نجح تمرير الشاشة.":"فشل تمرير الشاشة.");scheduleAfterAction(generation,step);});
        } else if (TYPE_TYPE_TEXT.equals(type)) {
            lastStepSuccess = typeTextIntoFocusedField(step.optString("value", ""));
            report(lastStepSuccess ? "تم إدخال النص في حقل الإدخال (إمكانية الوصول/اللصق)." : "تعذرت الكتابة: لا يوجد حقل قابل للتحرير مركّز. انقر حقل الإدخال أولًا."); scheduleAfterAction(generation, step);
        } else if (TYPE_PRESS_ENTER.equals(type)) {
            lastStepSuccess = performImeEnter(); report(lastStepSuccess ? "تم إرسال أمر Enter/بحث." : "تعذر إرسال Enter؛ قد لا يدعم التطبيق إجراء IME. جرّب النقر على زر البحث."); scheduleAfterAction(generation, step);
        } else if (TYPE_CLEAR_TEXT.equals(type)) {
            lastStepSuccess = clearFocusedField(); report(lastStepSuccess ? "تم مسح حقل الإدخال المركّز." : "تعذر مسح النص؛ لا يوجد حقل قابل للتحرير مركّز."); scheduleAfterAction(generation, step);
        } else if (TYPE_GLOBAL_ACTION.equals(type)) {
            lastStepSuccess = performSystemAction(step.optString("systemAction", "notifications"));
            report(lastStepSuccess ? "نُفّذ إجراء النظام: " + step.optString("label", "") : "تعذر تنفيذ إجراء النظام؛ قد لا يدعمه إصدار أندرويد."); scheduleAfterAction(generation, step);
        } else if (TYPE_CLOSE_RECENTS.equals(type)) {
            closeRecentAppsAsync(ok -> {
                lastStepSuccess = ok;
                report(ok ? "تم إغلاق التطبيقات التي أتاح النظام زر إغلاقها." : "فُتحت التطبيقات الأخيرة، لكن لم يظهر زر إغلاق الكل قابل للتعرّف؛ لم تُغلق عناصر عشوائيًا.");
                scheduleAfterAction(generation, step);
            });
        } else if (TYPE_WAIT_TEXT.equals(type)) {
            waitForTextStep(step, generation, 0, Math.max(1, step.optInt("attempts", 20)), Math.max(250, Math.min(60000, step.optInt("intervalMs", 500))));
        } else if (TYPE_WAIT_INPUT.equals(type)) {
            waitForInputStep(step, generation, 0, Math.max(1, step.optInt("attempts", 20)), Math.max(250, Math.min(60000, step.optInt("intervalMs", 500))));
        } else if (TYPE_OPEN_URL.equals(type)) {
            lastStepSuccess = openHttpUrl(step.optString("url", "")); report(lastStepSuccess ? "تم فتح الرابط في التطبيق المناسب." : "تعذر فتح الرابط: " + step.optString("url", "")); scheduleAfterAction(generation, step);
        } else if (TYPE_SPEED_TEST.equals(type)) {
            runSpeedTestStep(step, generation);
        } else if (TYPE_READ_SCREEN_TEXT.equals(type)) {
            String visibleText = readVisibleScreenText(9000);
            lastStepSuccess = !visibleText.trim().isEmpty();
            String detail = lastStepSuccess ? "النصوص المقروءة من الشاشة الحالية:\n" + visibleText
                    : "لم تسترجع خدمة إمكانية الوصول أي نص ظاهر. قد يكون المحتوى صورة/Canvas أو أن التطبيق حجب شجرة الوصول.";
            report(lastStepSuccess ? "تمت قراءة النصوص الظاهرة؛ حُفظت النتيجة في السجل." : "لم يظهر نص قابل للقراءة؛ حُفظ سبب الفشل في السجل.");
            if (smartTaskActive) BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "نتيجة قراءة الشاشة", detail, smartTaskActionCount, lastStepSuccess);
            recordResultWithScreenshot("screen_read", currentStepIndex, step.optString("label", "قراءة نص الشاشة"),
                    lastStepSuccess ? "نجحت قراءة النصوص الظاهرة." : "لم توجد نصوص قابلة للقراءة.", detail,
                    () -> { if (running && generation == executionGeneration) scheduleAfterAction(generation, step); });
        } else if (TYPE_CHECK_INTERNET.equals(type) || TYPE_WAIT_INTERNET.equals(type)) {
            runInternetStep(step, type, generation);
        } else if (TYPE_NOTIFY.equals(type)) {
            notifyUser(step.optString("message", "تم تنفيذ الأمر بنجاح"), step.optBoolean("sound", true)); lastStepSuccess = true;
            report("تنبيه: " + step.optString("message", "تم تنفيذ الأمر بنجاح")); scheduleAfterAction(generation, step);
        } else {
            lastStepSuccess = false; report("نوع خطوة غير معروف: " + type); scheduleNext(generation, 0);
        }
    }

    private void scheduleAfterAction(int generation, JSONObject step) {
        int configured = Math.max(0, Math.min(60000, step.optInt("afterDelay", 0)));
        int totalAttempts = step == null ? 1 : Math.max(1, step.optInt("retryOnFailureCount", 1));
        int alreadyRetried = step == null ? 0 : Math.max(0, step.optInt("_runtimeRetryCount", 0));
        if (step != null && !lastStepSuccess && alreadyRetried < totalAttempts - 1) {
            int nextUsed = alreadyRetried + 1;
            putInt(step, "_runtimeRetryCount", nextUsed);
            int retryDelay = Math.max(100, Math.min(60000, step.optInt("retryOnFailureDelay", 700)));
            report("فشلت الخطوة «" + step.optString("label", step.optString("type", "خطوة"))
                    + "». سأعيد المحاولة " + (alreadyRetried + 2) + "/" + totalAttempts + " بعد " + retryDelay + " ms.");
            executionInFlight = true;
            handler.postDelayed(() -> {
                if (generation != executionGeneration || !running) return;
                if (paused) { executionInFlight = false; showRunControls(); return; }
                executionInFlight = false;
                runStep();
            }, retryDelay);
            return;
        }
        if (step != null) putInt(step, "_runtimeRetryCount", 0);
        if (!lastStepSuccess) {
            String label = step == null ? "خطوة غير معروفة" : step.optString("label", step.optString("type", "خطوة"));
            String details = getLastResult() + "\nالنوع: " + (step == null ? "-" : step.optString("type", "-"))
                    + "\nالإعدادات: " + (step == null ? "{}" : step.toString());
            logFailureWithScreenshot("execution", currentStepIndex, label, "فشلت الخطوة أثناء التنفيذ.", details, true);
        }

        int minimum = lastStepSuccess ? CONTROL_GAP_MS : 750;
        scheduleNext(generation, Math.max(minimum, configured));
    }

    private void scheduleNext(int generation, int delayMs) {
        if (generation != executionGeneration || !running) return;
        if (smartExternalSequenceActive && executionSteps != null && currentStepIndex < executionSteps.length()) {
            smartExternalSequenceExecutedCount++;
        }
        currentStepIndex++;
        executionInFlight = true;
        long delay = Math.max(1, delayMs);
        if (delayMs > 0) showRunControls();
        handler.postDelayed(() -> {
            if (generation != executionGeneration || !running) return;
            executionInFlight = false;
            if (paused) showRunControls(); else runStep();
        }, delay);
    }

    private void finishScript(String message) {
        boolean wasCustomTest = customTestMode;
        boolean wasSmartAction = smartTaskActive;
        boolean wasExternalSequence = smartExternalSequenceActive;
        int completedActionUnits = wasExternalSequence ? Math.max(1, smartExternalSequenceExecutedCount) : 1;
        boolean actionSucceeded = lastStepSuccess;
        String actionLabel = smartCurrentActionLabel;
        running = false; paused = false; executionInFlight = false;
        if (!wasSmartAction) BotnaAgentKeepAliveService.stop(this);
        currentBranches.clear(); currentLoops.clear();
        executionGeneration++;
        removeView(runControls); runControls = null; runControlsParams = null; runControlsExpanded = false;
        removeView(runStatusView); runStatusView = null; runStatusParams = null;
        if (wasCustomTest) { singleStepTesting = false; customTestMode = false; message = "انتهى اختبار الخطوة المخصصة: " + message; }
        executionSteps = new JSONArray();
        if (wasSmartAction) {
            try {
                JSONObject event = new JSONObject();
                event.put("actionNumber", smartTaskActionCount + 1);
                event.put("action", actionLabel);
                event.put("success", actionSucceeded);
                event.put("screenChanged", false);
                event.put("result", message);
                event.put("time", System.currentTimeMillis());
                if (smartCurrentActionDefinition != null) event.put("definition", new JSONObject(smartCurrentActionDefinition.toString()));
                smartTaskHistory.put(event);
                while (smartTaskHistory.length() > 20) smartTaskHistory.remove(0);
                BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "action_result", smartTaskActionCount + 1, actionLabel,
                        message, actionSucceeded, smartCurrentActionDefinition);
            } catch (JSONException ignored) { }
            smartTaskActionCount += completedActionUnits;
            smartExternalSequenceActive = false;
            smartExternalSequenceExecutedCount = 0;
            try {
                String pkg = currentForegroundPackage();
                String memoryType = memoryActionType(actionLabel);
                if (actionSucceeded) BotStudioStore.get(this).recordSuccess(pkg, memoryType, message);
                else BotStudioStore.get(this).recordFailure(pkg, memoryType, message, BotStudioStore.recommendedStrategy(memoryType, actionLabel));
                BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "running");
            } catch (Exception ignored) { }
            smartCurrentActionLabel = "";
            smartCurrentActionDefinition = null;
            if (actionSucceeded) smartRepeatedActionReplans = 0;
            report("الوكيل: " + (actionSucceeded ? (wasExternalSequence ? "اكتملت السلسلة " : "اكتمل الإجراء ") : (wasExternalSequence ? "فشلت السلسلة " : "فشل الإجراء ")) + smartTaskActionCount + " — سيعيد قراءة الشاشة ويقرر الخطوة التالية.");
            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", (actionSucceeded ? (wasExternalSequence ? "نجحت السلسلة " : "نجح الإجراء ") : (wasExternalSequence ? "فشلت السلسلة " : "فشل الإجراء ")) + smartTaskActionCount + ": " + actionLabel,
                    message + "\nسيعيد الوكيل قراءة الشاشة قبل تحديد الخطوة التالية.", smartTaskActionCount, actionSucceeded);
            if (!actionSucceeded) BotnaLogStore.add(this, "smart_agent", currentStepIndex, actionLabel,
                    "فشل إجراء داخل البوت الذكي.", message + "\nالمهمة: " + smartTaskGoal, null);
            if (externalTemporalCaptureInProgress && shouldCaptureExternalActionFrames()) {
                handler.postDelayed(() -> captureExternalTemporalAfterFrame(externalTemporalCaptureToken), 180L);
            } else {
                handler.postDelayed(this::requestSmartNextStep, 450);
            }
            return;
        }
        report(message);
        showBubble();
        if (panelWasVisibleAtRunStart) showPanel();
    }

    private void interruptSmartActionForCorrection() {
        if (!smartTaskActive || smartCurrentActionDefinition == null || smartActionGoalRevision == smartGoalRevision) return;
        String interruptedLabel = smartCurrentActionLabel.isEmpty()
                ? smartCurrentActionDefinition.optString("label", smartCurrentActionDefinition.optString("type", "إجراء"))
                : smartCurrentActionLabel;
        String details = "أوقف الوكيل بقية أجزاء الإجراء المركب القديم عند أقرب نقطة آمنة لأن المستخدم صحح الهدف أثناء التنفيذ. لم أعتبر الإجراء مكتملًا، ولم أتابع بقية الخطة القديمة. سيتم التقاط الشاشة مجددًا والتخطيط حسب التصحيح.";
        running = false;
        paused = false;
        executionInFlight = false;
        executionGeneration++;
        currentBranches.clear();
        currentLoops.clear();
        executionSteps = new JSONArray();
        removeView(runControls); runControls = null; runControlsParams = null; runControlsExpanded = false;
        removeView(runStatusView); runStatusView = null; runStatusParams = null;
        try {
            JSONObject event = new JSONObject();
            event.put("actionNumber", smartTaskActionCount + 1);
            event.put("action", interruptedLabel);
            event.put("success", false);
            event.put("screenChanged", false);
            event.put("interruptedByCorrection", true);
            event.put("result", details);
            event.put("definition", new JSONObject(smartCurrentActionDefinition.toString()));
            event.put("time", System.currentTimeMillis());
            smartTaskHistory.put(event);
            while (smartTaskHistory.length() > 20) smartTaskHistory.remove(0);
        } catch (Exception ignored) { }
        smartTaskActionCount++;
        BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "أوقفت بقية الإجراء بعد تصحيحك",
                details + "\nالإجراء الذي تم إيقاف تكملته: " + interruptedLabel + "\nالهدف المصحح: " + smartTaskGoal,
                smartTaskActionCount, false);
        try {
            BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId,
                    smartTaskActionCount, smartTaskHistory, "running");
        } catch (Exception ignored) { }
        BotnaLogStore.add(this, "smart_agent", smartTaskActionCount, interruptedLabel,
                "أوقف الإجراء المركب بسبب تصحيح المستخدم.", details + "\nالهدف: " + smartTaskGoal, null);
        smartCurrentActionLabel = "";
        smartCurrentActionDefinition = null;
        smartRepeatedActionReplans = 0;
        smartActionGoalRevision = smartGoalRevision;
        report(details);
        handler.postDelayed(() -> {
            if (smartTaskActive) requestSmartNextStep();
        }, 250L);
    }

    private void testStep(int index) {
        if (running || singleStepTesting) { report("أوقف التنفيذ الحالي قبل اختبار خطوة منفردة."); return; }
        JSONObject script = ensureScript(); JSONObject original = BotnaStore.steps(script).optJSONObject(index);
        if (original == null) return;
        final JSONObject step;
        try { step = new JSONObject(original.toString()); }
        catch (JSONException e) { report("تعذر تجهيز اختبار الخطوة."); return; }
        panelWasVisibleBeforeSingleTest = panelVisible;
        singleStepTesting = true;
        executionGeneration++;
        final int generation = executionGeneration;
        hidePanel(); removeView(bubble); bubble = null;
        String type = step.optString("type", "");
        report("اختبار منفرد للخطوة " + (index + 1) + ": " + step.optString("label", type));
        if (TYPE_CUSTOM.equals(type)) {
            JSONArray compiled = step.optJSONArray("compiledSteps");
            if (compiled == null || compiled.length() == 0) {
                finishSingleStepTest(generation, "لا توجد أوامر مترجمة؛ افتح الخطوة المخصصة واكتب أمرًا مفهومًا أولًا.");
                return;
            }
            JSONArray expanded = expandCustomSteps(compiled);
            String validation = validateScriptStructure(expanded);
            if (validation != null) { finishSingleStepTest(generation, "تركيب الخطوة المخصصة غير صالح: " + validation); return; }
            currentScriptId = ensureScript().optString("id", "");
            currentStepIndex = 0; executionSteps = expanded;
            currentBranches.clear(); currentLoops.clear(); lastStepSuccess = true;
            panelWasVisibleAtRunStart = panelWasVisibleBeforeSingleTest;
            customTestMode = true; running = true; paused = false; executionInFlight = false;
            showRunControls(); runStep();
            return;
        }
        Runnable testAction = () -> {
            if (generation != executionGeneration || !singleStepTesting) return;
            if (TYPE_IMAGE.equals(type)) {
                runImageMatch(step, 0, generation, (ok, message) -> finishSingleStepTest(generation, message));
            } else if (TYPE_TEXT.equals(type)) {
                boolean ok = false;
                int retries = Math.max(1, step.optInt("retries", 1));
                for (int i = 0; i < retries && !ok; i++) ok = clickText(step.optString("value", ""));
                finishSingleStepTest(generation, ok ? "نجح اختبار النقر على النص." : "فشل اختبار النقر على النص.");
            } else if (TYPE_LAUNCH_APP.equals(type)) {
                String appName = step.optString("appName", step.optString("value", "")).trim();
                boolean ok = launchInstalledApp(appName);
                finishSingleStepTest(generation, ok ? "نجح فتح التطبيق: " + appName : "فشل فتح التطبيق: " + appName);
            } else if (TYPE_BACK.equals(type)) {
                boolean ok = performGlobalAction(GLOBAL_ACTION_BACK); finishSingleStepTest(generation, ok ? "نجح اختبار الرجوع." : "فشل اختبار الرجوع.");
            } else if (TYPE_HOME.equals(type)) {
                boolean ok = performGlobalAction(GLOBAL_ACTION_HOME); finishSingleStepTest(generation, ok ? "نجح اختبار الرئيسية." : "فشل اختبار الرئيسية.");
            } else if (TYPE_WAIT.equals(type)) {
                int delay;
                if (step.optBoolean("randomDelay", false)) {
                    int min = Math.max(0, Math.min(60000, step.optInt("minDelay", 500)));
                    int max = Math.max(min, Math.min(60000, step.optInt("maxDelay", min)));
                    delay = min + (max == min ? 0 : (int) (Math.random() * (max - min + 1)));
                } else delay = Math.max(0, Math.min(60000, step.optInt("delay", 500)));
                handler.postDelayed(() -> finishSingleStepTest(generation, "نجح اختبار الانتظار (" + delay + " ms)."), delay);
            } else if (TYPE_TAP.equals(type)) {
                android.util.DisplayMetrics metrics=realScreenMetrics(); int width=metrics.widthPixels, height=metrics.heightPixels;
                int x=Math.max(0,Math.min(width-1,width*step.optInt("xPercent",50)/100));
                int y=Math.max(0,Math.min(height-1,height*step.optInt("yPercent",50)/100));
                performTap(x,y,ok->finishSingleStepTest(generation,ok?"نجح اختبار النقر بالموقع.":"فشل اختبار النقر بالموقع."));
            } else if (TYPE_LONG_PRESS.equals(type)) {
                android.util.DisplayMetrics metrics=realScreenMetrics(); int width=metrics.widthPixels, height=metrics.heightPixels;
                int x=Math.max(0,Math.min(width-1,width*step.optInt("xPercent",50)/100));
                int y=Math.max(0,Math.min(height-1,height*step.optInt("yPercent",50)/100));
                performLongPress(x,y,step.optInt("duration",800),ok->finishSingleStepTest(generation,ok?"نجح اختبار الضغط المطوّل.":"فشل اختبار الضغط المطوّل."));
            } else if (TYPE_SWIPE.equals(type)) {
                performSwipe(step.optInt("x1",50),step.optInt("y1",75),step.optInt("x2",50),step.optInt("y2",25),step.optInt("duration",450),ok->finishSingleStepTest(generation,ok?"نجح اختبار السحب.":"فشل اختبار السحب."));
            } else if (TYPE_SCROLL.equals(type)) {
                String d=step.optString("direction","down"); int x1=50,y1=75,x2=50,y2=25;
                if("up".equals(d)){y1=25;y2=75;}else if("right".equals(d)){x1=25;x2=75;y1=50;y2=50;}else if("left".equals(d)){x1=75;x2=25;y1=50;y2=50;}
                performSwipe(x1,y1,x2,y2,step.optInt("duration",420),ok->finishSingleStepTest(generation,ok?"نجح اختبار التمرير.":"فشل اختبار التمرير."));
            } else if (TYPE_TYPE_TEXT.equals(type)) {
                boolean ok=typeTextIntoFocusedField(step.optString("value","")); finishSingleStepTest(generation,ok?"نجح اختبار كتابة النص.":"فشل اختبار كتابة النص.");
            } else if (TYPE_PRESS_ENTER.equals(type)) {
                boolean ok=performImeEnter(); finishSingleStepTest(generation,ok?"نجح اختبار Enter/بحث.":"تعذر تنفيذ Enter/بحث في هذا الحقل.");
            } else if (TYPE_CLEAR_TEXT.equals(type)) {
                boolean ok=clearFocusedField(); finishSingleStepTest(generation,ok?"نجح اختبار مسح النص.":"تعذر مسح النص في حقل مركّز.");
            } else if (TYPE_GLOBAL_ACTION.equals(type)) {
                boolean ok=performSystemAction(step.optString("systemAction","notifications")); finishSingleStepTest(generation,ok?"نجح اختبار إجراء النظام.":"فشل اختبار إجراء النظام.");
            } else if (TYPE_CLOSE_RECENTS.equals(type)) {
                closeRecentAppsAsync(ok -> finishSingleStepTest(generation,ok?"نجح اختبار إغلاق التطبيقات المتاحة من شاشة التطبيقات الأخيرة.":"فُتحت التطبيقات الأخيرة، لكن لم يتم العثور على زر إغلاق الكل."));
            } else if (TYPE_WAIT_TEXT.equals(type)) {
                boolean found=containsTextOnScreen(step.optString("value","")); finishSingleStepTest(generation,found?"النص المنتظر ظاهر حاليًا.":"النص المنتظر غير ظاهر حاليًا.");
            } else if (TYPE_WAIT_INPUT.equals(type)) {
                boolean found=hasEditableInputOnScreen(); finishSingleStepTest(generation,found?"يوجد حقل إدخال قابل للتحرير.":"لم يظهر حقل إدخال قابل للتحرير.");
            } else if (TYPE_OPEN_URL.equals(type)) {
                boolean ok=openHttpUrl(step.optString("url","")); finishSingleStepTest(generation,ok?"تم فتح الرابط.":"تعذر فتح الرابط.");
            } else if (TYPE_READ_SCREEN_TEXT.equals(type)) {
                String text = readVisibleScreenText(5000);
                recordResultWithScreenshot("screen_read", index, step.optString("label", "قراءة نص الشاشة"),
                        text.isEmpty() ? "لم تظهر نصوص قابلة للقراءة." : "تمت قراءة النصوص الظاهرة.", text,
                        () -> finishSingleStepTest(generation, text.isEmpty() ? "لم أجد نصًا قابلًا للقراءة؛ راجع السجل." : "نتيجة قراءة الشاشة محفوظة في السجل:\n" + (text.length() > 1200 ? text.substring(0, 1200) + "…" : text)));
            } else if (TYPE_NOTIFY.equals(type)) {
                notifyUser(step.optString("message","تم تنفيذ الأمر بنجاح"),step.optBoolean("sound",true)); finishSingleStepTest(generation,"تم اختبار التنبيه.");
            } else if (TYPE_SPEED_TEST.equals(type)) {
                measureDownloadSpeedAsync((ok, result) -> {
                    report(result);
                    recordResultWithScreenshot("speed_test", index, step.optString("label", "قياس سرعة التنزيل"),
                            ok ? "تم قياس سرعة التنزيل." : "فشل قياس سرعة التنزيل.", result,
                            () -> finishSingleStepTest(generation, result));
                });
            } else if (TYPE_CHECK_INTERNET.equals(type) || TYPE_WAIT_INTERNET.equals(type)) {
                probeInternetAsync(step.optString("testUrl",""),step.optInt("timeoutMs",4000),ok->finishSingleStepTest(generation,ok?"نجح اختبار اتصال الإنترنت.":"فشل اختبار اتصال الإنترنت."));
            } else if (TYPE_IF_SUCCESS.equals(type) || TYPE_IF_FAIL.equals(type)) {
                boolean condition = TYPE_IF_SUCCESS.equals(type) ? lastStepSuccess : !lastStepSuccess;
                finishSingleStepTest(generation, "اختبار الشرط: " + (condition ? "سيتحقق الشرط بناءً على نتيجة الخطوة السابقة." : "لن يتحقق الشرط بناءً على نتيجة الخطوة السابقة."));
            } else if (TYPE_REPEAT_START.equals(type)) {
                finishSingleStepTest(generation, "تعريف التكرار صالح مبدئيًا: ستتكرر الكتلة " + Math.max(1, step.optInt("repeatCount", 2)) + " مرة عند تشغيل السكربت كاملًا.");
            } else if (TYPE_REPEAT_END.equals(type) || TYPE_ELSE.equals(type) || TYPE_END_IF.equals(type)) {
                finishSingleStepTest(generation, "هذه علامة بنيوية؛ تُختبر مطابقتها عند التحقق من بنية السكربت قبل التشغيل.");
            } else {
                finishSingleStepTest(generation, "هذه علامة تنظيمية لا تنفّذ فعلًا منفردًا؛ تم التحقق من تعريفها.");
            }
        };
        int beforeDelay = Math.max(0, Math.min(60000, step.optInt("beforeDelay", 0)));
        if (beforeDelay > 0) handler.postDelayed(testAction, beforeDelay); else testAction.run();
    }

    private void finishSingleStepTest(int generation, String message) {
        if (generation != executionGeneration || !singleStepTesting) return;
        singleStepTesting = false;
        report(message);
        showBubble();
        if (panelWasVisibleBeforeSingleTest) showPanel();
    }

    private void runImageMatch(JSONObject step, int attempt, int generation, ActionResultCallback callback) {
        if (generation != executionGeneration || !(running || singleStepTesting)) return;
        if (running && paused) { executionInFlight = false; showRunControls(); return; }
        captureAppScreenshot((screen, error) -> {
            if (generation != executionGeneration || !(running || singleStepTesting)) { if (screen != null && !screen.isRecycled()) screen.recycle(); return; }
            if (running && paused) {
                if (screen != null && !screen.isRecycled()) screen.recycle();
                executionInFlight = false; showRunControls(); return;
            }
            if (screen == null) {
                retryImageOrFinish(step, attempt, generation, callback, false, "فشل التقاط الشاشة للمطابقة: " + error);
                return;
            }

            if (running) showRunControls();
            findMatchAsync(screen, step, (match, matchError) -> {
                if (generation != executionGeneration || !(running || singleStepTesting)) return;
                if (running && paused) { executionInFlight = false; showRunControls(); return; }
                if (matchError != null) {
                    retryImageOrFinish(step, attempt, generation, callback, false, "تعذر تشغيل محرك المطابقة: " + matchError); return;
                }
                int requiredThreshold = match == null ? step.optInt("threshold", 85) : thresholdForTemplate(step, match.templateName);
                boolean matched = match != null && match.score >= requiredThreshold;
                if (!matched) {
                    retryImageOrFinish(step, attempt, generation, callback, false,
                            "لم يتحقق تطابق الصورة. أفضل نتيجة " + (match == null ? 0 : match.score) + "% (المطلوب " + requiredThreshold + "%).",
                            match == null ? null : match.templateName);
                    return;
                }
                if (!step.optBoolean("tapOnMatch", true)) {
                    callback.done(true, "تم العثور على الصورة بنسبة " + match.score + "% دون الضغط عليها.");
                } else {
                    tapMatchedTarget(match, ok -> {
                        if (generation != executionGeneration || !(running || singleStepTesting)) return;
                        callback.done(ok, ok ? "تم العثور على الهدف والضغط عليه بنسبة تطابق " + match.score + "% ." : "تم العثور على الصورة لكن فشل الضغط عليها.");
                    });
                }
            });
        });
    }

    private void retryImageOrFinish(JSONObject step, int attempt, int generation, ActionResultCallback callback, boolean unused, String message) {
        retryImageOrFinish(step, attempt, generation, callback, unused, message, null);
    }

    private void retryImageOrFinish(JSONObject step, int attempt, int generation, ActionResultCallback callback, boolean unused, String message, String templateName) {
        if (running && paused) { executionInFlight = false; showRunControls(); return; }
        int retries = templateName == null ? Math.max(1, step.optInt("retries", 1)) : retriesForTemplate(step, templateName);
        if (attempt + 1 < retries) {
            report(message + " إعادة المحاولة " + (attempt + 2) + "/" + retries + "…");
            int delay = templateName == null ? Math.max(100, Math.min(10000, step.optInt("retryDelay", 500))) : retryDelayForTemplate(step, templateName);
            if (running) showRunControls();
            handler.postDelayed(() -> runImageMatch(step, attempt + 1, generation, callback), delay);
        } else callback.done(false, message + " انتهت المحاولات (" + retries + ").");
    }

    private void findMatchAsync(Bitmap screen, JSONObject step, MatchCallback callback) {
        final JSONObject stepCopy;
        try { stepCopy = new JSONObject(step.toString()); }
        catch (JSONException e) { if (screen != null && !screen.isRecycled()) screen.recycle(); callback.done(null, "تعذر قراءة إعدادات الخطوة"); return; }
        try {
            matcherExecutor.execute(() -> {
                ImageMatcher.Result result = null; String error = null;
                try { result = matchStep(screen, stepCopy); }
                catch (Throwable t) { error = t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage()); }
                finally { if (screen != null && !screen.isRecycled()) screen.recycle(); }
                ImageMatcher.Result finalResult = result; String finalError = error;
                handler.post(() -> callback.done(finalResult, finalError));
            });
        } catch (RuntimeException e) {
            if (screen != null && !screen.isRecycled()) screen.recycle(); callback.done(null, "تعذر بدء عامل المطابقة");
        }
    }

    private ImageMatcher.Result matchStep(Bitmap screen, JSONObject step) {
        JSONArray images = step.optJSONArray("images");
        if (images == null || images.length() == 0 || screen == null || screen.isRecycled()) return null;
        List<ImageMatcher.NamedBitmap> templates = new ArrayList<>();
        Map<String, Integer> thresholds = new HashMap<>();
        Bitmap searchBitmap = screen;
        boolean croppedSearch = false;
        int offsetX = 0, offsetY = 0;
        for (int i = 0; i < images.length(); i++) {
            String name = images.optString(i);
            JSONObject angleSettings = findImageAngleSettings(step, name);
            if (angleSettings != null && !angleSettings.optBoolean("enabled", true)) continue;
            Bitmap bitmap = readTemplate(name);
            if (bitmap != null) {
                templates.add(new ImageMatcher.NamedBitmap(name, bitmap));
                thresholds.put(name, thresholdForTemplate(step, name));
            }
        }
        if (templates.isEmpty()) return null;
        try {
            String searchMode = step.optString("searchRegionMode", "full");
            if ("custom".equals(searchMode) || "original".equals(searchMode)) {
                boolean original = "original".equals(searchMode);
                String xKey = original ? "captureX" : "searchX";
                String yKey = original ? "captureY" : "searchY";
                String wKey = original ? "captureW" : "searchW";
                String hKey = original ? "captureH" : "searchH";
                int x = clampInt(Math.round(screen.getWidth() * step.optInt(xKey, 0) / 100f), 0, screen.getWidth() - 1);
                int y = clampInt(Math.round(screen.getHeight() * step.optInt(yKey, 0) / 100f), 0, screen.getHeight() - 1);
                int w = clampInt(Math.round(screen.getWidth() * step.optInt(wKey, 100) / 100f), 1, screen.getWidth() - x);
                int h = clampInt(Math.round(screen.getHeight() * step.optInt(hKey, 100) / 100f), 1, screen.getHeight() - y);
                searchBitmap = Bitmap.createBitmap(screen, x, y, w, h);
                croppedSearch = true; offsetX = x; offsetY = y;
            }
            ImageMatcher.Result result = ImageMatcher.find(searchBitmap, templates, step.optInt("threshold", 85), thresholds);
            if (result == null || !croppedSearch) return result;
            return new ImageMatcher.Result(result.score, result.x + offsetX, result.y + offsetY,
                    result.width, result.height, result.templateName);
        } finally {
            if (croppedSearch && searchBitmap != null && !searchBitmap.isRecycled()) searchBitmap.recycle();
            for (ImageMatcher.NamedBitmap item : templates) if (item.bitmap != null && !item.bitmap.isRecycled()) item.bitmap.recycle();
        }
    }
    private void captureAppScreenshot(ScreenshotCallback callback) {
        if (Build.VERSION.SDK_INT < 30) { callback.done(null, "يتطلب Android 11 أو أحدث"); return; }
        boolean wasPanel = panelVisible;
        boolean shouldRestoreUi = !running && !singleStepTesting;
        hidePanel(); removeView(bubble); bubble = null;
        if (running) { removeView(runControls); runControls = null; removeView(runStatusView); runStatusView = null; runStatusParams = null; }
        handler.postDelayed(() -> takeScreenshot((bitmap,error) -> {
            if (shouldRestoreUi) { showBubble(); if (wasPanel) showPanel(); }
            callback.done(bitmap,error);
        }), 180);
    }

    private interface GestureCallback { void done(boolean success); }

    private void tapMatchedTarget(ImageMatcher.Result match, GestureCallback callback) {
        if (Build.VERSION.SDK_INT < 24 || match == null) { callback.done(false); return; }
        removeView(runControls); runControls = null; removeView(runStatusView); runStatusView = null; runStatusParams = null;
        hidePanel(); removeView(bubble); bubble = null;
        handler.postDelayed(() -> {
            try {
                Path path = new Path(); float x = match.x + match.width / 2f; float y = match.y + match.height / 2f;
                path.moveTo(x, y); path.lineTo(x + 1f, y + 1f);
                GestureDescription gesture = new GestureDescription.Builder()
                        .addStroke(new GestureDescription.StrokeDescription(path, 0, 90)).build();
                boolean dispatched = dispatchGesture(gesture, new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription description) { callback.done(true); }
                    @Override public void onCancelled(GestureDescription description) { callback.done(false); }
                }, handler);
                if (!dispatched) callback.done(false);
            } catch (Exception e) { callback.done(false); }
        }, 100);
    }

    private void performTap(int x, int y, GestureCallback callback) {
        if (Build.VERSION.SDK_INT < 24) { callback.done(false); return; }
        android.util.DisplayMetrics metrics = realScreenMetrics();
        int startX = Math.max(0, Math.min(Math.max(0, metrics.widthPixels - 1), x));
        int startY = Math.max(0, Math.min(Math.max(0, metrics.heightPixels - 1), y));
        int maxX = Math.max(0, metrics.widthPixels - 1);
        int maxY = Math.max(0, metrics.heightPixels - 1);
        int endX = startX < maxX ? startX + 1 : Math.max(0, startX - 1);
        int endY = startY < maxY ? startY + 1 : Math.max(0, startY - 1);
        removeView(runControls); runControls = null; removeView(runStatusView); runStatusView = null; runStatusParams = null;
        hidePanel(); removeView(bubble); bubble = null;
        handler.postDelayed(() -> {
            try {
                Path path = new Path(); path.moveTo(startX, startY); path.lineTo(endX, endY);
                GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
                boolean sent = dispatchGesture(gesture, new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription description) { callback.done(true); }
                    @Override public void onCancelled(GestureDescription description) { callback.done(false); }
                }, handler);
                if (!sent) callback.done(false);
            } catch (Exception e) { callback.done(false); }
        }, 80);
    }

    private void performLongPress(int x, int y, int duration, GestureCallback callback) {
        if (Build.VERSION.SDK_INT < 24) { callback.done(false); return; }
        android.util.DisplayMetrics metrics = realScreenMetrics();
        int startX = Math.max(0, Math.min(Math.max(0, metrics.widthPixels - 1), x));
        int startY = Math.max(0, Math.min(Math.max(0, metrics.heightPixels - 1), y));
        int maxX = Math.max(0, metrics.widthPixels - 1);
        int maxY = Math.max(0, metrics.heightPixels - 1);
        int endX = startX < maxX ? startX + 1 : Math.max(0, startX - 1);
        int endY = startY < maxY ? startY + 1 : Math.max(0, startY - 1);
        removeView(runControls); runControls = null; removeView(runStatusView); runStatusView = null; runStatusParams = null;
        hidePanel(); removeView(bubble); bubble = null;
        handler.postDelayed(() -> {
            try {
                Path path = new Path(); path.moveTo(startX, startY); path.lineTo(endX, endY);
                GestureDescription gesture = new GestureDescription.Builder()
                        .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(300, Math.min(5000, duration))))
                        .build();
                boolean sent = dispatchGesture(gesture, new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription description) { callback.done(true); }
                    @Override public void onCancelled(GestureDescription description) { callback.done(false); }
                }, handler);
                if (!sent) callback.done(false);
            } catch (Exception e) { callback.done(false); }
        }, 80);
    }

    private void performSwipe(int x1p, int y1p, int x2p, int y2p, int duration, GestureCallback callback) {
        android.util.DisplayMetrics metrics = realScreenMetrics();
        int width = metrics.widthPixels, height = metrics.heightPixels;
        int x1 = Math.round((width - 1f) * Math.max(0, Math.min(100, x1p)) / 100f);
        int y1 = Math.round((height - 1f) * Math.max(0, Math.min(100, y1p)) / 100f);
        int x2 = Math.round((width - 1f) * Math.max(0, Math.min(100, x2p)) / 100f);
        int y2 = Math.round((height - 1f) * Math.max(0, Math.min(100, y2p)) / 100f);
        performSwipePixels(x1, y1, x2, y2, duration, callback);
    }

    private void performSwipePixels(int x1, int y1, int x2, int y2, int duration, GestureCallback callback) {
        if (Build.VERSION.SDK_INT < 24) { callback.done(false); return; }
        android.util.DisplayMetrics metrics = realScreenMetrics();
        int maxX = Math.max(0, metrics.widthPixels - 1), maxY = Math.max(0, metrics.heightPixels - 1);
        float startX = Math.max(0, Math.min(maxX, x1));
        float startY = Math.max(0, Math.min(maxY, y1));
        float endX = Math.max(0, Math.min(maxX, x2));
        float endY = Math.max(0, Math.min(maxY, y2));
        removeView(runControls); runControls = null; removeView(runStatusView); runStatusView = null; runStatusParams = null;
        hidePanel(); removeView(bubble); bubble = null;
        handler.postDelayed(() -> {
            try {
                Path path = new Path(); path.moveTo(startX, startY); path.lineTo(endX, endY);
                GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(100, Math.min(5000, duration)))).build();
                boolean sent = dispatchGesture(gesture, new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription d) { callback.done(true); }
                    @Override public void onCancelled(GestureDescription d) { callback.done(false); }
                }, handler);
                if (!sent) callback.done(false);
            } catch (Exception e) { callback.done(false); }
        }, 80);
    }

    private interface NetworkProbeCallback { void done(boolean reachable); }

    private void probeInternetAsync(String customUrl, int timeoutMs, NetworkProbeCallback callback) {
        final String selected = customUrl == null || customUrl.trim().isEmpty()
                ? "https://connectivitycheck.gstatic.com/generate_204" : customUrl.trim();
        final int timeout = Math.max(1000, Math.min(12000, timeoutMs));
        if (!selected.startsWith("https://") && !selected.startsWith("http://")) { callback.done(false); return; }
        try {
            networkExecutor.execute(() -> {
                boolean reachable = false;
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(selected).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(timeout); connection.setReadTimeout(timeout);
                    connection.setUseCaches(false); connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("Cache-Control", "no-cache");
                    connection.setRequestProperty("User-Agent", "Botna-Connectivity-Check/1.0");
                    int code = connection.getResponseCode();
                    if (selected.contains("connectivitycheck.gstatic.com/generate_204") || selected.contains("google.com/generate_204")) {
                        reachable = code == 204;
                    } else {
                        reachable = code >= 200 && code < 300;
                        if (reachable && code != HttpURLConnection.HTTP_NO_CONTENT) {
                            InputStream in = connection.getInputStream();
                            try { byte[] buffer = new byte[256]; in.read(buffer); } finally { in.close(); }
                        }
                    }
                } catch (Exception ignored) { reachable = false; }
                finally { if (connection != null) connection.disconnect(); }
                final boolean result = reachable;
                handler.post(() -> callback.done(result));
            });
        } catch (Exception e) { callback.done(false); }
    }

    private void runInternetStep(JSONObject step, String type, int generation) {
        final boolean waitMode = TYPE_WAIT_INTERNET.equals(type);
        final int maxAttempts = Math.max(1, step.optInt("attempts", waitMode ? 120 : 3));
        final int interval = Math.max(250, Math.min(60000, step.optInt("intervalMs", 5000)));
        final int timeout = Math.max(1000, Math.min(12000, step.optInt("timeoutMs", 4000)));
        runInternetAttempt(step, generation, waitMode, 0, maxAttempts, interval, timeout);
    }

    private void runInternetAttempt(JSONObject step, int generation, boolean waitMode, int attempt, int maxAttempts, int interval, int timeout) {
        if (!running || generation != executionGeneration) return;
        if (paused) { executionInFlight = false; showRunControls(); return; }
        report((waitMode ? "انتظار عودة الإنترنت — " : "فحص الإنترنت — ") + "محاولة " + (attempt + 1) + "/" + maxAttempts);
        probeInternetAsync(step.optString("testUrl", ""), timeout, ok -> {
            if (!running || generation != executionGeneration) return;
            if (paused) { executionInFlight = false; showRunControls(); return; }
            if (ok) {
                lastStepSuccess = true;
                report(waitMode ? "عاد اتصال الإنترنت بنجاح." : "اتصال الإنترنت يعمل؛ نجح إرسال الطلب واستلام الاستجابة.");
                if (waitMode && step.optBoolean("notifyOnSuccess", false)) notifyUser(step.optString("successMessage", "عاد الإنترنت بنجاح"), true);
                scheduleAfterAction(generation, step);
            } else if (attempt + 1 < maxAttempts) {
                handler.postDelayed(() -> runInternetAttempt(step, generation, waitMode, attempt + 1, maxAttempts, interval, timeout), interval);
            } else {
                lastStepSuccess = false;
                report(waitMode ? "انتهت مهلة انتظار الإنترنت بعد " + maxAttempts + " محاولة." : "لم يستجب الإنترنت بعد " + maxAttempts + " محاولات.");
                scheduleAfterAction(generation, step);
            }
        });
    }

    private boolean openHttpUrl(String rawUrl) {
        if (rawUrl == null) return false;
        String url = rawUrl.trim();
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return false;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (Exception e) { return false; }
    }

    private void notifyUser(String message, boolean sound) {
        String safe = message == null || message.trim().isEmpty() ? "تم تنفيذ الأمر بنجاح" : message.trim();
        Toast.makeText(getApplicationContext(), safe, Toast.LENGTH_LONG).show();
        if (sound) {
            try {
                ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85);
                tone.startTone(ToneGenerator.TONE_PROP_ACK, 260);
                handler.postDelayed(tone::release, 500);
            } catch (Exception ignored) { }
        }
    }

    private boolean performImeEnter() {
        AccessibilityNodeInfo root = findTargetRoot();
        AccessibilityNodeInfo focused = null;
        try {
            if (root != null) {
                focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
                if (focused != null) {
                    if (Build.VERSION.SDK_INT >= 30) {
                        try {
                            int imeActionId = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId();
                            for (AccessibilityNodeInfo.AccessibilityAction action : focused.getActionList()) {
                                if (action != null && action.getId() == imeActionId && focused.performAction(imeActionId)) return true;
                            }
                            if (focused.performAction(imeActionId)) return true;
                        } catch (Exception ignored) { }
                    }
                    AccessibilityNodeInfo parent = focused.getParent();
                    while (parent != null) {
                        try {
                            if (Build.VERSION.SDK_INT >= 30) {
                                int imeId = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId();
                                for (AccessibilityNodeInfo.AccessibilityAction action : parent.getActionList()) {
                                    if (action != null && action.getId() == imeId && parent.performAction(imeId)) {
                                        parent.recycle();
                                        return true;
                                    }
                                }
                            }
                        } catch (Exception ignored) { }
                        AccessibilityNodeInfo next = parent.getParent();
                        parent.recycle();
                        parent = next;
                    }
                }
            }
        } finally {
            if (focused != null) try { focused.recycle(); } catch (Exception ignored) { }
            if (root != null) try { root.recycle(); } catch (Exception ignored) { }
        }

        if (clickImeEnterAction()) return true;
        AccessibilityNodeInfo appRoot = findTargetRoot();
        if (appRoot != null) {
            try { if (findAndClickEnterAction(appRoot, 0)) return true; }
            finally { try { appRoot.recycle(); } catch (Exception ignored) { } }
        }
        return false;
    }

    private boolean clickImeEnterAction() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null) return false;
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_INPUT_METHOD) continue;
                AccessibilityNodeInfo root = null;
                try {
                    root = window.getRoot();
                    if (root != null && findAndClickEnterAction(root, 0)) return true;
                } catch (Exception ignored) { }
                finally { if (root != null) try { root.recycle(); } catch (Exception ignored) { } }
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean findAndClickEnterAction(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 32) return false;
        try {
            String text = node.getText() == null ? "" : node.getText().toString();
            String desc = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
            String hint = node.getHintText() == null ? "" : node.getHintText().toString();
            String tip = "";
            if (Build.VERSION.SDK_INT >= 28) {
                try { if (node.getTooltipText() != null) tip = node.getTooltipText().toString(); } catch (Exception ignored) { }
            }
            String label = (text + " " + desc + " " + hint + " " + tip).toLowerCase(Locale.ROOT);
            boolean voiceOnly = label.contains("voice") || label.contains("صوت") || label.contains("ميكروفون");
            boolean candidate = !voiceOnly && (label.contains("بحث") || label.contains("search")
                    || label.contains("submit") || label.contains("enter") || label.contains("إرسال")
                    || label.contains("انتقال") || label.contains("اذهب") || label.matches(".*\\b(go|enter|done)\\b.*")
                    || label.trim().equals("تم") || label.contains("إرسال البحث"));
            if (candidate && node.isEnabled()) {
                if (node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                if (clickNodeOrParent(node)) return true;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) continue;
                boolean clicked;
                try { clicked = findAndClickEnterAction(child, depth + 1); }
                finally { try { child.recycle(); } catch (Exception ignored) { } }
                if (clicked) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean launchInstalledApp(String query) {
        if (query == null || query.trim().isEmpty()) return false;
        String target = normalizeArabicCommand(query).trim();
        PackageManager pm = getPackageManager();
        try {
            Intent direct = pm.getLaunchIntentForPackage(query.trim());
            if (direct != null) {
                direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(direct);
                return true;
            }
        } catch (Exception ignored) { }
        try {
            java.util.List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            ApplicationInfo exact = null, partial = null;
            for (ApplicationInfo info : apps) {
                String packageName = info.packageName == null ? "" : info.packageName;
                String label;
                try { label = String.valueOf(pm.getApplicationLabel(info)); }
                catch (Exception ex) { label = packageName; }
                String normalizedLabel = normalizeArabicCommand(label).trim();
                String normalizedPackage = normalizeArabicCommand(packageName).replace('.', ' ').trim();
                if (normalizedLabel.equals(target) || normalizedPackage.equals(target)) { exact = info; break; }
                if (partial == null && (!target.isEmpty()) && (normalizedLabel.contains(target) || target.contains(normalizedLabel) || normalizedPackage.contains(target))) partial = info;
            }
            ApplicationInfo match = exact != null ? exact : partial;
            if (match == null) return false;
            Intent launch = pm.getLaunchIntentForPackage(match.packageName);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private boolean typeTextIntoFocusedField(String value) {
        AccessibilityNodeInfo root = findTargetRoot();
        if (root == null || value == null) { if (root != null) root.recycle(); return false; }
        AccessibilityNodeInfo target = null;
        try {
            target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (target == null || !target.isEditable()) {
                if (target != null) { target.recycle(); target = null; }
                target = findFirstEditableNode(root);
                if (target != null) {
                    target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
            if (target == null || !target.isEditable() || !target.isEnabled()) return false;
            android.os.Bundle args = new android.os.Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true;

            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) return false;
            clipboard.setPrimaryClip(ClipData.newPlainText("botna_temporary_text", value));
            boolean pasted = target.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            if (pasted) handler.postDelayed(() -> {
                try {
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip != null && clip.getDescription() != null && "botna_temporary_text".contentEquals(clip.getDescription().getLabel()))
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""));
                } catch (Exception ignored) { }
            }, 1800);
            return pasted;
        } catch (Exception e) { return false; }
        finally { if (target != null) target.recycle(); root.recycle(); }
    }

    private AccessibilityNodeInfo findFirstEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        try {
            if (node.isEditable() && node.isEnabled() && node.isFocusable()) return AccessibilityNodeInfo.obtain(node);
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) continue;
                AccessibilityNodeInfo found = null;
                try { found = findFirstEditableNode(child); } finally { child.recycle(); }
                if (found != null) return found;
            }
        } catch (Exception ignored) { }
        return null;
    }

    private void waitForInputStep(JSONObject step, int generation, int attempt, int maxAttempts, int intervalMs) {
        if (!running || generation != executionGeneration) return;
        if (paused) { executionInFlight = false; showRunControls(); return; }
        try {
            accessibilityQueryExecutor.execute(() -> {
                boolean found = false;
                try { found = hasEditableInputOnScreen(); } catch (Exception ignored) { }
                final boolean visible = found;
                handler.post(() -> {
                    if (!running || generation != executionGeneration) return;
                    if (paused) { executionInFlight = false; showRunControls(); return; }
                    if (visible) {
                        lastStepSuccess = true;
                        report("ظهر حقل إدخال قابل للكتابة.");
                        scheduleAfterAction(generation, step);
                    } else if (attempt + 1 >= maxAttempts) {
                        lastStepSuccess = false;
                        report("لم يظهر حقل الإدخال بعد " + maxAttempts + " محاولة.");
                        scheduleAfterAction(generation, step);
                    } else {
                        report("انتظار ظهور حقل الإدخال — محاولة " + (attempt + 1) + "/" + maxAttempts);
                        handler.postDelayed(() -> waitForInputStep(step, generation, attempt + 1, maxAttempts, intervalMs), intervalMs);
                    }
                });
            });
        } catch (Exception e) {
            lastStepSuccess = false;
            report("تعذر فحص حقل الإدخال في الخلفية: " + e.getClass().getSimpleName());
            scheduleAfterAction(generation, step);
        }
    }

    private boolean hasEditableInputOnScreen() {
        AccessibilityNodeInfo root=findTargetRoot(); if(root==null)return false;
        try { return hasEditableRecursive(root,0); } finally { root.recycle(); }
    }

    private boolean hasEditableRecursive(AccessibilityNodeInfo node,int depth) {
        if(node==null||depth>28)return false;
        try {
            if(node.isEditable()&&node.isEnabled()&&node.isVisibleToUser())return true;
            for(int i=0;i<node.getChildCount();i++){ AccessibilityNodeInfo child=node.getChild(i); if(child==null)continue; boolean found; try{found=hasEditableRecursive(child,depth+1);}finally{child.recycle();} if(found)return true; }
        }catch(Exception ignored){}
        return false;
    }

    private void waitForTextStep(JSONObject step, int generation, int attempt, int maxAttempts, int intervalMs) {
        if (!running || generation != executionGeneration) return;
        if (paused) { executionInFlight = false; showRunControls(); return; }
        String target = step.optString("value", "").trim();
        try {
            accessibilityQueryExecutor.execute(() -> {
                boolean found = false;
                try { found = containsTextOnScreen(target); } catch (Exception ignored) { }
                final boolean visible = found;
                handler.post(() -> {
                    if (!running || generation != executionGeneration) return;
                    if (paused) { executionInFlight = false; showRunControls(); return; }
                    if (visible) {
                        lastStepSuccess = true;
                        report("ظهر النص المنتظر: " + target);
                        scheduleAfterAction(generation, step);
                    } else if (attempt + 1 >= maxAttempts) {
                        lastStepSuccess = false;
                        report("لم يظهر النص «" + target + "» بعد " + maxAttempts + " محاولة.");
                        scheduleAfterAction(generation, step);
                    } else {
                        report("انتظار ظهور النص «" + target + "» — محاولة " + (attempt + 1) + "/" + maxAttempts);
                        handler.postDelayed(() -> waitForTextStep(step, generation, attempt + 1, maxAttempts, intervalMs), intervalMs);
                    }
                });
            });
        } catch (Exception e) {
            lastStepSuccess = false;
            report("تعذر فحص النص في الخلفية: " + e.getClass().getSimpleName());
            scheduleAfterAction(generation, step);
        }
    }

    private boolean containsTextOnScreen(String target) {
        if (target == null || target.trim().isEmpty()) return false;
        AccessibilityNodeInfo root = findTargetRoot(); if (root == null) return false;
        try { return containsTextRecursive(root, target.trim().toLowerCase(Locale.ROOT), 0); }
        finally { root.recycle(); }
    }

    private boolean containsTextRecursive(AccessibilityNodeInfo node, String target, int depth) {
        if (node == null || depth > 28) return false;
        try {
            CharSequence text = node.getText(); CharSequence desc = node.getContentDescription(); CharSequence hint = node.getHintText();
            String value = text == null ? "" : text.toString().trim();
            if (value.isEmpty() && desc != null) value = desc.toString().trim();
            if (value.isEmpty() && hint != null) value = hint.toString().trim();
            if (!value.isEmpty() && value.toLowerCase(Locale.ROOT).contains(target)) return true;
            for (int i=0;i<node.getChildCount();i++) {
                AccessibilityNodeInfo child = node.getChild(i); if (child == null) continue;
                boolean found; try { found = containsTextRecursive(child,target,depth+1); } finally { child.recycle(); }
                if (found) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private boolean clickText(String target) {
        if (target == null || target.trim().isEmpty()) return false;
        AccessibilityNodeInfo root = findTargetRoot(); if (root == null) return false;
        try {
            if (findAndClick(root,target.trim(),true)) return true;
            return findAndClick(root,target.trim(),false);
        } finally { root.recycle(); }
    }

    private AccessibilityNodeInfo findTargetRoot() {
        try {
            for (AccessibilityWindowInfo win : getWindows()) {
                if (win == null || win.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = win.getRoot();
                if (root == null) continue;
                CharSequence pkg = root.getPackageName();
                if (pkg != null && getPackageName().contentEquals(pkg)) { root.recycle(); continue; }
                return root;
            }
        } catch (Exception ignored) { }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null && root.getPackageName()!=null && getPackageName().contentEquals(root.getPackageName())) { root.recycle(); return null; }
        return root;
    }

    private boolean findAndClick(AccessibilityNodeInfo node, String target, boolean exact) {
        if (node == null) return false;
        String value = node.getText()==null ? "" : node.getText().toString().trim();
        if(value.isEmpty() && node.getContentDescription()!=null) value=node.getContentDescription().toString().trim();
        if(value.isEmpty() && node.getHintText()!=null) value=node.getHintText().toString().trim();
        boolean hit = exact ? value.equalsIgnoreCase(target) : value.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT));
        if (hit && clickNodeOrParent(node)) return true;
        for (int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo child=node.getChild(i); if(child==null) continue;
            boolean found; try { found=findAndClick(child,target,exact); } finally { child.recycle(); }
            if(found) return true;
        }
        return false;
    }

    private boolean clickNodeOrParent(AccessibilityNodeInfo node) {
        if (node.isClickable()) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        AccessibilityNodeInfo parent=node.getParent();
        while(parent!=null) {
            if(parent.isClickable()) { boolean ok=parent.performAction(AccessibilityNodeInfo.ACTION_CLICK); parent.recycle(); return ok; }
            AccessibilityNodeInfo next=parent.getParent(); parent.recycle(); parent=next;
        }
        return false;
    }

    private String readVisibleScreenText(int maxCharacters) {
        AccessibilityNodeInfo root = findTargetRoot();
        if (root == null) return "";
        List<String> values = new ArrayList<>();
        try { collectText(root, values, 0); } finally { root.recycle(); }
        StringBuilder text = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (text.length() > 0) text.append("\n");
            text.append(value.trim());
            if (text.length() >= maxCharacters) { text.setLength(maxCharacters); break; }
        }
        return text.toString();
    }

    private void readScreen() {
        String text = readVisibleScreenText(3500);
        if (text.isEmpty()) report("لم تظهر نصوص قابلة للقراءة. بعض الألعاب ترسم الأزرار داخل صورة أو مشهد Unity.");
        else report("تمت قراءة النصوص الظاهرة:\n" + (text.length() > 1500 ? text.substring(0, 1500) + "…" : text));
    }

    private void collectText(AccessibilityNodeInfo node, List<String> values, int depth) {
        if (node == null || values.size() >= 35 || depth > 24) return;
        CharSequence t = node.getText(); CharSequence d = node.getContentDescription();
        String value = t == null ? "" : t.toString().trim();
        if (value.isEmpty() && d != null) value = d.toString().trim();
        if (!value.isEmpty() && !values.contains(value)) values.add(value);
        for (int i=0; i<node.getChildCount() && values.size()<35; i++) {
            AccessibilityNodeInfo child = node.getChild(i); if (child == null) continue;
            try { collectText(child, values, depth+1); } finally { child.recycle(); }
        }
    }

    private String joinFirst(List<String> values, int max) {
        StringBuilder out = new StringBuilder();
        int n = Math.min(max, values.size());
        for (int i=0;i<n;i++) { if(i>0) out.append(" • "); out.append(values.get(i)); }
        if (values.size()>n) out.append(" • …");
        return out.toString();
    }

    private void addButton(LinearLayout row, String title, Runnable action) {
        Button b = button(title, true);
        b.setMinHeight(dp(42));
        b.setSingleLine(false);
        b.setMaxLines(3);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(5), dp(5), dp(5), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(b, lp);
        if (action != null) commandCatalog.add(new CommandEntry(title, action));
        b.setOnClickListener(v -> { if (action != null) action.run(); });
    }

    private void showCommandSearchDialog() {
        if (commandCatalog.isEmpty()) {
            report("قائمة الأوامر غير جاهزة بعد. أعد فتح لوحة الأتمتة لتحديث قائمة الأوامر.");
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(4), dp(8), dp(4));
        LinearLayout titleBar = horizontal();
        TextView titleText = label(BotnaI18n.translate(this, "بحث عن أمر لإضافته"), 14, TEXT, true);
        titleBar.addView(titleText, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button backTop = button("↩", false);
        backTop.setContentDescription(BotnaI18n.translate(this, "رجوع"));
        Button closeTop = button("×", false);
        closeTop.setContentDescription(BotnaI18n.translate(this, "إغلاق البحث"));
        titleBar.addView(backTop, new LinearLayout.LayoutParams(dp(38), dp(40)));
        LinearLayout.LayoutParams closeTopLp = new LinearLayout.LayoutParams(dp(38), dp(40));
        closeTopLp.setMargins(dp(4), 0, 0, 0);
        titleBar.addView(closeTop, closeTopLp);
        root.addView(titleBar, new LinearLayout.LayoutParams(-1, dp(42)));
        EditText query = new EditText(this);
        query.setSingleLine(true); query.setTextSize(15); query.setTextColor(TEXT);
        query.setHintTextColor(MUTED);
        query.setHint(BotnaI18n.isEnglish(this) ? "Search commands: search, type, swipe, notifications…" : "ابحث عن أمر: بحث، كتابة، سحب، إشعارات…");
        BotnaI18n.applyDirection(this, query);
        query.setBackground(glassShape(BUTTON, CYAN, 1, 12));
        query.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams queryLp = new LinearLayout.LayoutParams(-1, dp(48));
        queryLp.topMargin = dp(5);
        root.addView(query, queryLp);
        TextView count = label("كل الأوامر المتاحة", 11, MUTED, false);
        LinearLayout.LayoutParams countLp = wrap(); countLp.topMargin = dp(5); root.addView(count, countLp);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false); scroll.setClipToPadding(false);
        LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollLp.topMargin = dp(4); scrollLp.bottomMargin = dp(4); root.addView(scroll, scrollLp);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(root).create();
        final AlertDialog[] owner = new AlertDialog[]{dialog};
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int countChanged) {
                renderCommandSearchResults(s == null ? "" : s.toString(), results, count, owner[0]);
            }
            @Override public void afterTextChanged(Editable editable) { }
        });
        backTop.setOnClickListener(v -> dialog.dismiss());
        closeTop.setOnClickListener(v -> dialog.dismiss());
        renderCommandSearchResults("", results, count, dialog);
        try {
            Window w = dialog.getWindow();
            if (w != null) {

                w.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
                w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
            dialog.show();
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                android.util.DisplayMetrics dm = realScreenMetrics();
                shown.setLayout(Math.max(dp(260), Math.min(dp(520), dm.widthPixels - dp(24))),
                        Math.max(dp(260), Math.min(dp(680), dm.heightPixels - dp(36))));
            }
            query.requestFocus();
            query.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null && query.getWindowToken() != null) imm.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
            }, 180);
        } catch (Exception e) {
            report("تعذر فتح البحث عن الأوامر: " + e.getClass().getSimpleName());
        }
    }

    private void renderCommandSearchResults(String queryText, LinearLayout results, TextView count, AlertDialog owner) {
        results.removeAllViews();
        String q = normalizeCommandSearch(queryText);
        int matches = 0;
        for (CommandEntry entry : new ArrayList<>(commandCatalog)) {
            String titleKey = commandSearchIndex(entry.title);
            if (!q.isEmpty() && !titleKey.contains(q)) continue;
            matches++;
            Button result = button(entry.title, true);
            result.setBackground(glassShape(Color.argb(248, 14, 76, 145), Color.argb(255, 35, 226, 244), 2, 14));
            result.setTextColor(Color.WHITE);
            result.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            result.setSingleLine(false); result.setMaxLines(3); result.setMinHeight(dp(42));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(3);
            results.addView(result, lp);
            result.setOnClickListener(v -> {
                if (owner != null) owner.dismiss();

                handler.post(() -> {
                    try { entry.action.run(); }
                    catch (RuntimeException ex) { report("تعذّر تنفيذ الأمر المحدد: " + ex.getMessage()); }
                });
            });
        }
        count.setText(BotnaI18n.isEnglish(this)
                ? matches + " matching commands. Select one to add it or open its settings."
                : matches + " أمر مطابق. اضغط على أمر لإضافته أو فتح إعداداته.");
        if (matches == 0) {
            TextView empty = label(BotnaI18n.isEnglish(this)
                    ? "No matching commands. Try a shorter term such as tap, type, wait, or search."
                    : "لا توجد أوامر مطابقة. جرّب كلمة أقصر أو مرادفًا مثل: نقر، كتابة، انتظار، بحث.", 12, MUTED, false);
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            results.addView(empty, wrap());
        }
    }

    private String commandSearchIndex(String title) {
        String normalized = normalizeCommandSearch(title);
        StringBuilder index = new StringBuilder(normalized);

        if (normalized.contains("كتابة نص")) index.append(" اكتب كتابة ادخال type text keyboard");
        if (normalized.contains("نقر نص")) index.append(" اضغط انقر كبس اضغط على زر tap click text");
        if (normalized.contains("نقر موقع")) index.append(" اضغط مكان احداثيات لمسة tap coordinate");
        if (normalized.contains("enter") || normalized.contains("بحث")) index.append(" انتر ارسال ارسل تنفيذ بحث submit enter ime");
        if (normalized.contains("فتح تطبيق")) index.append(" افتح شغل تشغيل launch app");
        if (normalized.contains("فتح رابط")) index.append(" موقع افتح صفحة متصفح url website browser");
        if (normalized.contains("انتظار صورة")) index.append(" تعرف على صورة مطابقة الصورة ظهور صورة image match");
        if (normalized.contains("فحص الانترنت")) index.append(" افحص النت الشبكة اتصال wifi network internet");
        if (normalized.contains("سرعة التنزيل")) index.append(" سرعة النت سرعة الانترنت قياس السرعة speed test download");
        if (normalized.contains("الإشعارات") || normalized.contains("الاشعارات")) index.append(" تنبيهات notification notifications");
        if (normalized.contains("الاعدادات السريعة")) index.append(" واي فاي بلوتوث quick settings");
        if (normalized.contains("تمرير")) index.append(" اسكرول scroll نزول طلوع اعلى اسفل");
        if (normalized.contains("سحب الشاشة")) index.append(" جر اسحب swipe drag");
        if (normalized.contains("رجوع")) index.append(" back السابق للخلف");
        if (normalized.contains("الرئيسية")) index.append(" home الصفحة الرئيسية");
        if (normalized.contains("الخطوة المخصصة") || normalized.contains("مخصصة")) index.append(" امر عربي ذكاء اصطناعي custom instruction");
        return normalizeCommandSearch(index.toString());
    }

    private String normalizeCommandSearch(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه')
                .replaceAll("[\u064B-\u065F\u0670]", "")
                .replaceAll("\\s+", " ").trim();
    }

    private static final class CommandEntry {
        final String title;
        final Runnable action;
        CommandEntry(String title, Runnable action) { this.title = title; this.action = action; }
    }
    private LinearLayout horizontal() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); BotnaI18n.applyDirection(this, l); return l; }
    private String decorateButtonText(String original, String displayed) {
        String visible = displayed == null ? (original == null ? "" : original) : displayed;
        int start = 0;
        while (start < visible.length() && Character.isWhitespace(visible.charAt(start))) start++;
        if (start < visible.length()) {
            int cp = visible.codePointAt(start);
            int type = Character.getType(cp);
            if (type == Character.OTHER_SYMBOL || type == Character.MATH_SYMBOL
                    || type == Character.MODIFIER_SYMBOL) return visible;
        }
        String q = original == null ? "" : original.toLowerCase(Locale.ROOT);
        String icon = "";
        if (q.contains("سرعة") || q.contains("speed test") || q.contains("سرعة التنزيل")) icon = "📈 ";
        else if (q.contains("انتظار") || q.contains("wait") || q.contains("تأخير")) icon = "⏳ ";
        else if (q.contains("بحث") || q.contains("search") || q.contains("استعلام")) icon = "🔎 ";
        else if (q.contains("كتابة") || q.contains("اكتب") || q.contains("type") || q.contains("مسح النص")) icon = "⌨️ ";
        else if (q.contains("سحب") || q.contains("تمرير") || q.contains("swipe") || q.contains("scroll")) icon = "↕️ ";
        else if (q.contains("نقر") || q.contains("اضغط") || q.contains("click") || q.contains("tap")) icon = "🎯 ";
        else if (q.contains("قراءة") || q.contains("read screen") || q.contains("نصوص الشاشة")) icon = "👁️ ";
        else if (q.contains("فحص") || q.contains("اختبار") || q.contains("check") || q.contains("test")) icon = "🔬 ";
        else if (q.contains("صورة") || q.contains("image") || q.contains("قالب")) icon = "🖼️ ";
        else if (q.contains("إشعار") || q.contains("اشعار") || q.contains("notification")) icon = "🔔 ";
        else if (q.contains("تطبيق") || q.contains("app")) icon = "📱 ";
        else if (q.contains("رابط") || q.contains("url") || q.contains("link")) icon = "🔗 ";
        else if (q.contains("حفظ") || q.contains("save")) icon = "💾 ";
        else if (q.contains("إغلاق") || q.contains("اغلاق") || q.contains("close")) icon = "✕ ";
        else if (q.contains("حذف") || q.contains("delete")) icon = "🗑️ ";
        else if (q.contains("رجوع") || q.contains("back")) icon = "↩️ ";
        else if (q.contains("الرئيسية") || q.contains("home")) icon = "🏠 ";
        else if (q.contains("تشغيل") || q.contains("بدء") || q.contains("run") || q.contains("start")) icon = "▶️ ";
        else if (q.contains("إيقاف مؤقت") || q.contains("استكمال") || q.contains("pause") || q.contains("resume")) icon = "⏯️ ";
        else if (q.contains("إيقاف") || q.contains("stop")) icon = "⏹️ ";
        else if (q.contains("تكرار") || q.contains("repeat")) icon = "🔁 ";
        else if (q.contains("شرط") || q.contains("if success") || q.contains("if fail")) icon = "🔀 ";
        else if (q.contains("إعداد") || q.contains("setting") || q.contains("ضبط")) icon = "⚙️ ";
        else if (q.contains("سجل") || q.contains("log")) icon = "🧾 ";
        else if (q.contains("سكربت") || q.contains("script")) icon = "📚 ";
        else if (q.contains("وكيل") || q.contains("agent") || q.contains("مهمة") || q.contains("task")) icon = "🤖 ";
        else if (q.contains("لغة") || q.contains("language")) icon = "🌐 ";
        else if (q.contains("خصوصية") || q.contains("privacy")) icon = "🛡️ ";
        else if (q.contains("تعديل") || q.contains("edit")) icon = "✏️ ";
        else if (q.contains("مفتاح") || q.contains("key")) icon = "🔑 ";
        else if (q.contains("تواصل") || q.contains("telegram") || q.contains("contact")) icon = "✉️ ";
        else if (q.contains("إنشاء") || q.contains("انشاء") || q.contains("create") || q.contains("جديد")) icon = "➕ ";
        else if (q.contains("إخفاء") || q.contains("اخفاء") || q.contains("hide")) icon = "🙈 ";
        else if (q.contains("إظهار") || q.contains("اظهار") || q.contains("show")) icon = "👁️ ";
        else if (q.contains("مساعدة") || q.contains("دليل") || q.contains("help") || q.contains("guide")) icon = "❔ ";
        return icon.isEmpty() ? "✧ " + visible : icon + visible;
    }

    private Button button(String title, boolean primary) {
        Button b = new Button(this);
        b.setText(decorateButtonText(title, BotnaI18n.buttonText(this, title))); b.setTextSize(12); b.setAllCaps(false);
        b.setPadding(dp(5), dp(4), dp(5), dp(4)); b.setTextColor(TEXT);
        android.graphics.drawable.GradientDrawable glass = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                primary
                        ? new int[]{Color.argb(238, 19, 116, 150), Color.argb(245, 12, 67, 112)}
                        : new int[]{Color.argb(225, 25, 62, 91), Color.argb(232, 10, 27, 47)});
        glass.setCornerRadius(dp(12));
        glass.setStroke(dp(1), primary ? Color.argb(250, 35, 226, 244) : Color.argb(175, 35, 226, 244));
        b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(95, 115, 239, 255)), glass, null));
        b.setElevation(dp(4));
        return b;
    }
    private TextView label(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(BotnaI18n.headingText(this, value, bold)); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); BotnaI18n.applyDirection(this, t); if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); return t;
    }
    private android.graphics.drawable.GradientDrawable glassShape(int fill,int stroke,int width,int radius) {
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); if(width>0)d.setStroke(dp(width),stroke); return d;
    }
    private LinearLayout.LayoutParams wrap() { return new LinearLayout.LayoutParams(-1,-2); }
    private int dp(float value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private void hideBotnaOverlaysForCleanScreenshot() {
        hidePanel();
        removeView(bubble); bubble = null;
        removeView(runControls); runControls = null; runControlsParams = null;
        removeView(runStatusView); runStatusView = null; runStatusParams = null;
        removeView(smartPlanningControls); smartPlanningControls = null;
        removeView(pointSelector); pointSelector = null;
        removeView(cropSelector); cropSelector = null;
    }

    private void safeDismiss(AlertDialog dialog) { if (dialog != null) try { dialog.dismiss(); } catch (Exception ignored) { } }

    private void removeView(View view) { if(wm!=null&&view!=null)try{wm.removeView(view);}catch(Exception ignored){} }
    private void showSmartPlanningControls() {
        removeView(smartPlanningControls); smartPlanningControls = null;
        if (wm == null || !smartTaskActive) return;
        Button stop = button("إيقاف ✧", false);
        stop.setTextSize(10);
        stop.setSingleLine(true);
        stop.setContentDescription("إيقاف مهمة البوت الذكي");
        stop.setOnClickListener(v -> stopScript());
        int width = dp(88);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, dp(42),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.RIGHT;
        params.x = dp(12);
        params.y = dp(84);
        try { wm.addView(stop, params); smartPlanningControls = stop; }
        catch (Exception e) { smartPlanningControls = null; report("تعذر إظهار زر إيقاف الوكيل أثناء التحليل."); }
    }

    private void applySmartTaskCorrection(String correction) {
        String clean = correction == null ? "" : correction.trim();
        if (!smartTaskActive || clean.isEmpty()) {
            report("لم يتم تطبيق التصحيح لأن المهمة ليست نشطة داخل خدمة الوكيل.");
            return;
        }
        smartTaskGoal = smartTaskGoal + "\n\nتصحيح مباشر من المستخدم أثناء التنفيذ؛ له أولوية على الخطة السابقة: " + clean;
        smartGoalRevision++;
        smartRepeatedActionReplans = 0;
        try { BotnaAgentStore.updateGoal(this, smartTaskId, "مهمة مصححة", smartTaskGoal); } catch (Exception ignored) { }
        try { BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "running"); } catch (Exception ignored) { }
        BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "user_correction", smartTaskActionCount, "تصحيح مباشر من المستخدم", clean, true, null);
        String confirmation = "تم استلام تصحيحك أثناء المهمة، وأصبح جزءًا من الهدف الحالي. سيتم تجاهل أي خطة قديمة لم تكتمل بعد، ثم التقاط الشاشة مجددًا واختيار خطوة تتوافق مع التصحيح.";
        BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "استلم الوكيل تصحيحًا مباشرًا", confirmation + "\nالتصحيح: " + clean, smartTaskActionCount, true);
        report(confirmation);
    }

    private void startSmartTask(String task) { startSmartTask(task, "", false); }

    private void startSmartTask(String task, String requestedTaskId) { startSmartTask(task, requestedTaskId, false); }

    private void startSmartTask(String task, String requestedTaskId, boolean resumeCheckpoint) {
        String requested = task == null ? "" : task.trim();
        if (requested.isEmpty()) return;
        if (running || singleStepTesting || smartTaskActive) {
            boolean duplicateCurrentTask = smartTaskActive && requestedTaskId != null && !requestedTaskId.isEmpty()
                    && requestedTaskId.equals(smartTaskId);
            if (duplicateCurrentTask) {
                clearPendingSmartTaskIfMatches(requestedTaskId, requested);
                report("المهمة نفسها قيد التنفيذ بالفعل؛ تجاهلت طلب بدء/استئناف مكررًا ولم أبدأ نسخة ثانية.");
                return;
            }
            clearPendingSmartTaskIfMatches(requestedTaskId, requested);
            String reason = "هناك تنفيذ جارٍ بالفعل. أوقفه قبل بدء بوت ذكي جديد.";
            report(reason);
            BotnaLogStore.add(this, "smart_agent", -1, "بدء المهمة", "لم تبدأ المهمة الجديدة لأن تنفيذًا آخر ما زال جاريًا.", requested, null);
            if (requestedTaskId != null && !requestedTaskId.isEmpty()) recordTaskFailureToChat(requestedTaskId, "لم تبدأ المهمة", reason);
            return;
        }
        if (isLocalSpeedTestRequest(requested)) {
            startLocalSpeedTestTask(requested, requestedTaskId);
            return;
        }
        boolean externalMode = BotnaExternalAppAgent.enabled(this);
        if (externalMode) {
            String selectedExternalPackage = BotnaExternalAppAgent.selectedPackage(this);
            if (!BotnaExternalAppAgent.isInstalled(this, selectedExternalPackage)) {
                clearPendingSmartTaskIfMatches(requestedTaskId, requested);
                String reason = "تعذر بدء المهمة عبر التطبيق الخارجي " + BotnaExternalAppAgent.label(selectedExternalPackage)
                        + " لأنه غير مثبت أو لا يملك واجهة تشغيل على الهاتف. افتح إعدادات الرؤية واختر تطبيقًا مثبتًا.";
                report(reason);
                if (requestedTaskId != null && !requestedTaskId.isEmpty()) recordTaskFailureToChat(requestedTaskId, "لم تبدأ المهمة", reason);
                return;
            }
        } else if (!BotnaAICompiler.isConfigured(this)) {
            clearPendingSmartTaskIfMatches(requestedTaskId, requested);
            String reason = "لا يمكن بدء البوت الذكي دون مزوّد API ومفتاح محفوظ، أو تفعيل التنفيذ عبر تطبيق ذكاء خارجي من إعدادات الرؤية.";
            report(reason);
            BotnaLogStore.add(this, "smart_agent", -1, "بدء المهمة", "لا يوجد مزود AI مضبوط.", requested, null);
            if (requestedTaskId != null && !requestedTaskId.isEmpty()) {
                BotnaAgentStore.setStatus(this, requestedTaskId, "لم تبدأ", reason);
                recordTaskFailureToChat(requestedTaskId, "لم تبدأ المهمة", reason);
            }
            return;
        }
        smartTaskId = requestedTaskId == null ? "" : requestedTaskId.trim();
        JSONObject savedAgentTask = BotnaAgentStore.getTask(this, smartTaskId);
        if (savedAgentTask == null) {
            if (resumeCheckpoint) {
                clearPendingSmartTaskIfMatches(requestedTaskId, requested);
                String reason = "فشل الاستئناف: لم أجد سجل المهمة في مخزن الوكيل. لم أبدأ التحكم ولم ألتقط لقطة شاشة.";
                report(reason); recordTaskFailureToChat(smartTaskId, "فشل استئناف المهمة", reason); return;
            }
            smartTaskId = BotnaAgentStore.createTask(this, requested);
        } else if (!requested.equals(savedAgentTask.optString("goal", ""))) {
            BotnaAgentStore.updateGoal(this, smartTaskId, savedAgentTask.optString("name", ""), requested);
        }
        JSONObject checkpoint = resumeCheckpoint ? BotStudioStore.get(this).checkpoint(smartTaskId) : null;
        if (resumeCheckpoint && checkpoint == null) {
            clearPendingSmartTaskIfMatches(requestedTaskId, requested);
            String reason = "فشل الاستئناف: لم أجد نقطة تحقق محفوظة لهذه المهمة؛ لم يبدأ التحكم ولم أدّعِ رؤية الشاشة.";
            report(reason); recordTaskFailureToChat(smartTaskId, "فشل استئناف المهمة", reason); return;
        }
        if (resumeCheckpoint) {
            currentScriptId = checkpoint.optString("scriptId", BotnaAgentStore.scriptId(this, smartTaskId));
            JSONArray savedHistory = checkpoint.optJSONArray("history");
            try { smartTaskHistory = savedHistory == null ? new JSONArray() : new JSONArray(savedHistory.toString()); }
            catch (JSONException e) { smartTaskHistory = new JSONArray(); }
            smartTaskActionCount = checkpoint.optInt("actionCount", 0);
            BotnaAgentStore.setStatus(this, smartTaskId, "قيد الاستئناف", "استئناف من نقطة التحقق المحفوظة؛ سيُعاد فحص الشاشة الحالية.");
        } else {
            currentScriptId = BotnaAgentStore.prepareRun(this, smartTaskId);
            smartTaskHistory = new JSONArray(); smartTaskActionCount = 0;
        }
        if (!currentScriptId.isEmpty()) BotnaStore.setActiveId(this, currentScriptId);
        JSONObject script = ensureScript(); currentScriptId = script == null ? currentScriptId : script.optString("id", currentScriptId);
        smartTaskGoal = requested; smartPlanRetryCount = 0;
        smartLiveBurstEnabled = liveBurstRequestedForNextTask;
        smartTemporalFramesEnabled = temporalFramesRequestedForNextTask && !smartLiveBurstEnabled;
        liveBurstRequestedForNextTask = false;
        temporalFramesRequestedForNextTask = false;
        screenCapturePermissionRequestInFlight = false;
        screenCaptureServiceReady = false;
        screenCaptureServiceStarted = false;
        externalLiveClipInFlight = false;
        externalLiveClipRequestId = "";
        externalLiveClipTaskId = "";
        externalLiveClipPrompt = "";
        externalLiveClipGoal = "";
        externalAgentPackage = BotnaExternalAppAgent.enabled(this) ? BotnaExternalAppAgent.selectedPackage(this) : "";
        externalReferenceAttachmentPath = smartTaskId.isEmpty() ? "" : BotnaExternalAppAgent.taskAttachment(this, smartTaskId);
        externalPrompt = ""; externalBaselineUi = ""; externalLastCandidate = "";
        externalComposerPolls = 0; externalResponsePolls = 0; externalSendAttempts = 0; externalFormatRetries = 0;
        externalStablePolls = 0; externalReturnAttempts = 0; externalTargetReadAttempts = 0; externalAgentCycleInFlight = false;
        externalSendRetryScheduled = false; externalSendAttemptInProgress = false; externalSendRetryAwaitingReady = false; externalVisualComposerSearchInFlight = false; externalVisualSendSearchInFlight = false; externalVisualPromptInjected = false; externalVisualComposerTapped = false; externalVisualSendButtonMatched = false; externalVisualSendButtonBounds.setEmpty(); externalVisualSendButtonScore = 0; externalVisualSendButtonMatchedAt = 0L; externalBaselineEditableText = ""; externalBaselineHadEditable = false; externalResponseNoChangeWaitIndex = 0;
        smartGoalRevision++;
        smartActionGoalRevision = smartGoalRevision;
        smartRepeatedActionReplans = 0;
        smartCoordinateRefusalReplans = 0;
        smartLastScreenshotFingerprint = 0L;
        smartCurrentActionDefinition = null;
        smartExternalSequenceActive = false;
        smartTaskWasResumed = resumeCheckpoint;
        smartCurrentActionLabel = ""; smartTaskActive = true;
        getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit().putBoolean(PREF_AGENT_TASK_ACTIVE, true).putBoolean(PREF_AGENT_SCREEN_AVAILABLE, false).apply();
        BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "running");
        BotnaTaskEvidenceStore.ensureTask(this, smartTaskId, smartTaskGoal);
        BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, resumeCheckpoint ? "task_resumed" : "task_started", smartTaskActionCount,
                resumeCheckpoint ? "استئناف المهمة" : "بدء المهمة", "بدأت دورة التنفيذ. ستُحفظ اللقطات والأفعال في أرشيف المهمة.", true, null);
        BotnaAgentKeepAliveService.start(this);
        handler.removeCallbacks(directLiveVisionRunnable);
        handler.postDelayed(directLiveVisionRunnable, 900L);
        panelWasVisibleAtRunStart = panelVisible;
        report("بدأ البوت الذكي. سيحلل لقطة شاشة بعد كل إجراء حتى اكتمال المهمة أو إيقافها يدويًا.");
        BotnaAgentStore.setStatus(this, smartTaskId, "قيد التنفيذ", "بدأ الوكيل المهمة.");
        BotnaAgentStore.addMessage(this, smartTaskId, "assistant", resumeCheckpoint ? "استأنف الوكيل المهمة" : "بدأ الوكيل المهمة",
                "الهدف: " + requested + "\nسيحلل لقطة شاشة بعد كل إجراء، ويسجل الأفعال في سكربت مستقل." + (resumeCheckpoint ? "\nتم استعادة عدد الإجراءات وسجل العمل المحفوظ." : ""), smartTaskActionCount, true);
        BotnaLogStore.add(this, "smart_agent", -1, "بدء المهمة", "بدأ تنفيذ مهمة تفاعلية متتابعة.", requested, null);
        if (BotnaExternalAppAgent.enabled(this)) {
            hideBotnaOverlaysForCleanScreenshot();
            handler.postDelayed(() -> resolveExternalTaskTarget(0), 280L);
        } else requestSmartNextStep();
    }


    private boolean isValidExternalTargetPackage(String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return false;
        String value = packageName.trim().toLowerCase(Locale.ROOT);
        if (value.equals(getPackageName().toLowerCase(Locale.ROOT)) || BotnaExternalAppAgent.isSupportedPackage(value)) return false;
        return !(value.contains("keyboard") || value.contains("inputmethod") || value.contains("input_method")
                || value.contains("gboard") || value.contains("swiftkey") || value.contains("honeyboard")
                || value.contains("latinime") || value.endsWith(".ime"));
    }

    private String findVisibleTargetPackage() {
        String active = activeWindowPackage();
        if (isValidExternalTargetPackage(active)) return active;
        AccessibilityNodeInfo bestRoot = null;
        String bestPackage = "";
        int bestScore = Integer.MIN_VALUE;
        List<AccessibilityWindowInfo> windows = null;
        try {
            windows = getWindows();
            if (windows == null) return "";
            for (AccessibilityWindowInfo window : windows) {
                if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
                AccessibilityNodeInfo root = null;
                try {
                    root = window.getRoot();
                    if (root == null || root.getPackageName() == null) continue;
                    String packageName = root.getPackageName().toString();
                    if (!isValidExternalTargetPackage(packageName)) continue;
                    int score = (window.isActive() ? 1000 : 0) + (window.isFocused() ? 200 : 0);
                    if (score > bestScore) {
                        bestScore = score;
                        bestPackage = packageName;
                        if (bestRoot != null) bestRoot.recycle();
                        bestRoot = root;
                        root = null;
                    }
                } catch (Exception ignored) {
                } finally {
                    if (root != null) root.recycle();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (bestRoot != null) bestRoot.recycle();
        }
        return bestPackage;
    }

    private void resolveExternalTaskTarget(int attempt) {
        if (!smartTaskActive) return;
        String active = findVisibleTargetPackage();
        if (isValidExternalTargetPackage(active)) {
            externalTargetPackage = active;
            externalRequestedTargetPackage = "";
            requestSmartNextStep();
            return;
        }
        if (isValidExternalTargetPackage(externalRequestedTargetPackage)) {
            externalTargetPackage = externalRequestedTargetPackage;
            externalRequestedTargetPackage = "";
            requestSmartNextStep();
            return;
        }
        if (attempt < 5) {
            handler.postDelayed(() -> resolveExternalTaskTarget(attempt + 1), 350L);
            return;
        }
        failExternalAgent("لم أتمكن من تحديد التطبيق الموجود خلف واجهة Botna. لم أفتح الشاشة الرئيسية ولم أبدأ تسجيلًا في تطبيق آخر. افتح الشاشة التي تريد أتمتتها ثم ابدأ المهمة مجددًا.");
    }

    private void startLocalSpeedTestTask(String requested, String requestedTaskId) {
        smartTaskId = requestedTaskId == null ? "" : requestedTaskId.trim();
        JSONObject saved = BotnaAgentStore.getTask(this, smartTaskId);
        if (saved == null) smartTaskId = BotnaAgentStore.createTask(this, requested);
        else if (!requested.equals(saved.optString("goal", ""))) BotnaAgentStore.updateGoal(this, smartTaskId, saved.optString("name", ""), requested);
        currentScriptId = BotnaAgentStore.prepareRun(this, smartTaskId);
        if (!currentScriptId.isEmpty()) BotnaStore.setActiveId(this, currentScriptId);
        smartTaskGoal = requested;
        smartTaskActionCount = 0;
        smartGoalRevision++;
        smartRepeatedActionReplans = 0;
        smartCoordinateRefusalReplans = 0;
        smartLastScreenshotFingerprint = 0L;
        smartCurrentActionDefinition = null;
        smartExternalSequenceActive = false;
        smartExternalSequenceExecutedCount = 0;
        smartCurrentActionLabel = "قياس سرعة التنزيل";
        smartTaskActive = true;
        getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit().putBoolean(PREF_AGENT_TASK_ACTIVE, true).putBoolean(PREF_AGENT_SCREEN_AVAILABLE, false).apply();
        BotnaAgentKeepAliveService.start(this);
        panelWasVisibleAtRunStart = panelVisible;
        BotnaAgentStore.setStatus(this, smartTaskId, "قيد التنفيذ", "قياس محلي دون طلب نموذج ذكاء اصطناعي.");
        BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "بدأ اختبار سرعة الإنترنت",
                "هذا الإجراء يقيس سرعة التنزيل التقريبية من عينة بيانات عامة، ولا يستخدم حصة نموذج الذكاء الاصطناعي.", 0, true);
        report("يجري اختبار سرعة التنزيل محليًا دون إرسال طلب إلى نموذج AI…");
        showSmartPlanningControls();
        final String taskId = smartTaskId;
        measureDownloadSpeedAsync((ok, result) -> {
            if (!smartTaskActive || !taskId.equals(smartTaskId)) return;
            removeView(smartPlanningControls); smartPlanningControls = null;
            smartTaskActionCount = 1;
            smartCurrentActionLabel = "قياس سرعة التنزيل";
            recordResultWithScreenshot("speed_test", 1, "نتيجة اختبار سرعة الإنترنت",
                    ok ? "اكتمل قياس سرعة التنزيل." : "فشل اختبار سرعة التنزيل.", result, () -> {
                        if (!smartTaskActive || !taskId.equals(smartTaskId)) return;
                        BotnaAgentStore.addMessage(this, taskId, "assistant", ok ? "نتيجة اختبار سرعة الإنترنت" : "فشل اختبار سرعة الإنترنت",
                                result, 1, ok);
                        endSmartTask(result, ok);
                    });
        });
    }

    private int safeProviderKeyCount() {
        try { return BotnaKeyVault.loadAll(this, BotnaAICompiler.providerId(this)).size(); }
        catch (Exception ignored) { return 0; }
    }

    private String currentForegroundPackage() {
        AccessibilityNodeInfo root = null;
        try {
            root = findTargetRoot();
            CharSequence pkg = root == null ? null : root.getPackageName();
            return pkg == null ? "unknown" : pkg.toString();
        } catch (Exception ignored) { return "unknown"; }
        finally { if (root != null) root.recycle(); }
    }

    private String buildBotnaApplicationContext(String scriptId, String focusedStepId) {
        StringBuilder out = new StringBuilder(2500);
        out.append("\n\n--- سياق تطبيق Botna الفعلي ---\n")
                .append("مزود الذكاء: ").append(BotnaAICompiler.providerLabel(BotnaAICompiler.providerId(this)))
                .append("؛ النموذج: ").append(BotnaAICompiler.model(this)).append(". لا توجد مفاتيح أو أسرار في هذا السياق.\n")
                .append("حالة الإعداد: API مضبوط=").append(BotnaAICompiler.isConfigured(this))
                .append("؛ عدد المفاتيح المحفوظة لهذا المزوّد=").append(safeProviderKeyCount()).append("؛ قوالب الصور=").append(BotnaStore.imageLibrary(this).length())
                .append("؛ حفظ صور السجل=").append(BotnaLogStore.screenshotsEnabled(this))
                .append("؛ الشركة المصنّعة=").append(Build.MANUFACTURER)
                .append("؛ طراز الجهاز=").append(Build.MODEL)
                .append("؛ Android=").append(Build.VERSION.SDK_INT).append(". لا تشارك محتويات المفاتيح أو أي سر مطلقًا.\n")
                .append("الخدمة تستخدم Android Accessibility لقراءة النصوص القابلة للوصول، النقر على النص/العقدة، تنفيذ إجراءات النظام، الإيماءات، التقاط الشاشة عند منح الصلاحية، مطابقة الصور من مكتبة القوالب، وفحص الإنترنت وقياس سرعة التنزيل التقريبية.\n")
                .append("الأفعال المدعومة: تشغيل تطبيق، فتح رابط HTTPS/HTTP، مطابقة/نقر صورة من المكتبة، نقر نص، كتابة/مسح حقل، Enter/بحث، انتظار، انتظار ظهور نص/حقل، قراءة نصوص الشاشة، نقر/ضغط مطوّل بإحداثيات نسبية، سحب/تمرير، رجوع/الرئيسية، لوحة الإشعارات والإعدادات السريعة والتطبيقات الأخيرة، فحص الاتصال وانتظار عودته، قياس سرعة التنزيل، إشعار، شروط نجاح/فشل/وإلا ونهاية شرط، وتكرار مع نهاية.\n")
                .append("حدود مهمة: قد يتعذر قراءة نص مرسوم داخل صورة أو Canvas إذا لم يكن ظاهرًا بوضوح في لقطة الشاشة، ولا تضمن إمكانية الوصول التحكم بكل تطبيق؛ لا تختلق نجاحًا أو نتيجة غير ظاهرة. استخدم لقطة الشاشة لوصف الأشكال والمواقع بصريًا ونسب 0-100 للشاشة الكاملة.\n");
        try {
            JSONObject script = scriptId == null || scriptId.isEmpty() ? null : BotnaStore.getScript(this, scriptId);
            if (script == null) script = ensureScript();
            out.append("السكربت الجاري: ").append(script.optString("name", "بدون اسم")).append(".\nالخطوات الحالية (بحد أقصى 28):\n");
            JSONArray steps = BotnaStore.steps(script);
            for (int i=0; i<steps.length() && i<28; i++) {
                JSONObject step = steps.optJSONObject(i); if (step == null) continue;
                String id = step.optString("id", "");
                out.append(i + 1).append(focusedStepId != null && focusedStepId.equals(id) ? " [الخطوة محل التحليل] " : ". ")
                        .append(step.optString("label", step.optString("type", "خطوة")))
                        .append(" {type=").append(step.optString("type", "?"))
                        .append(", enabled=").append(step.optBoolean("enabled", true))
                        .append(", beforeDelay=").append(step.optInt("beforeDelay", 0))
                        .append(", afterDelay=").append(step.optInt("afterDelay", 0));
                if (step.has("threshold")) out.append(", threshold=").append(step.optInt("threshold", 85));
                if (step.has("retries")) out.append(", retries=").append(step.optInt("retries", 1));
                if (step.has("repeatCount")) out.append(", repeatCount=").append(step.optInt("repeatCount", 1));
                out.append("}\n");
                JSONArray sub = step.optJSONArray("compiledSteps");
                if (sub != null && sub.length() > 0) {
                    out.append("  أوامر الخطوة المخصصة المترجمة: ");
                    for (int j=0; j<sub.length() && j<8; j++) {
                        JSONObject a = sub.optJSONObject(j); if (a != null) out.append(a.optString("label", a.optString("type", ""))).append("؛ ");
                    }
                    out.append("\n");
                }
                if (out.length() > 8500) { out.append("... تم اختصار السياق لمنع إغراق الطلب بالتفاصيل ..."); break; }
            }
        } catch (Exception ex) { out.append("تعذر قراءة ملخص السكربت: ").append(ex.getClass().getSimpleName()).append(".\n"); }
        try {
            BotStudioStore studio = BotStudioStore.get(this);
            if (BotStudioStore.selectedBotId(this).isEmpty()) studio.bots();
            String selectedBot = BotStudioStore.selectedBotId(this);
            JSONObject bot = studio.bot(selectedBot);
            if (bot != null) {
                out.append("\n--- إعدادات شخصية Bot Studio ---\nاسم البوت: ").append(bot.optString("name", "بوتنا"))
                        .append("؛ الدور: ").append(bot.optString("role", "مساعد عام"))
                        .append("؛ تعليمات الشخصية: ").append(bot.optString("persona", "مساعد دقيق"))
                        .append("\nالمهارات المفعّلة:").append(studio.skillGuidance(selectedBot)).append("\n");
            }
            String pkg = currentForegroundPackage();
            out.append("ذاكرة الأخطاء المرتبطة بواجهة ").append(pkg).append(":");
            out.append(BotStudioStore.get(this).knownStrategy(pkg, "TEXT_CLICK"));
            out.append(BotStudioStore.get(this).knownStrategy(pkg, "IMAGE_MATCH"));
            out.append(BotStudioStore.get(this).knownStrategy(pkg, "WAIT"));
            out.append("\nوضع الرؤية: ").append(BotnaVisionEngine.status(this))
                    .append("؛ الرؤية المباشرة=").append(BotStudioStore.directLiveVisionEnabled(this)).append("\n");
            if (smartTaskActive && !smartTaskId.isEmpty()) out.append(BotnaTaskEvidenceStore.contextSummary(this, smartTaskId, 32)).append("\n");
        } catch (Exception ignored) { }
        return out.toString();
    }

    private String activeWindowPackage() {
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            CharSequence pkg = root == null ? null : root.getPackageName();
            return pkg == null ? "" : pkg.toString();
        } catch (Exception ignored) { return ""; }
        finally { if (root != null) root.recycle(); }
    }

    private void requestExternalAssistantStep() {
        if (!smartTaskActive || externalAgentCycleInFlight) return;
        externalAgentPackage = BotnaExternalAppAgent.selectedPackage(this);
        if (BotnaExternalAppAgent.META_AI_WHATSAPP.equals(externalAgentPackage)) {
            externalAgentPackage = BotnaExternalAppAgent.META_AI_WEB;
            BotnaExternalAppAgent.setSelectedPackage(this, externalAgentPackage);
            report("Meta AI داخل قائمة مشاركة WhatsApp غير متاح كمستلم مباشر؛ سيستخدم Botna موقع Meta AI بدلًا منه.");
        }
        if (!BotnaExternalAppAgent.isInstalled(this, externalAgentPackage)) {
            failExternalAgent("توقف التنفيذ الخارجي: تطبيق " + BotnaExternalAppAgent.label(externalAgentPackage)
                    + " غير مثبت أو لا يملك نشاط تشغيل. لم أرسل الصورة ولم أنفذ أي إجراء.");
            return;
        }
        String foreground = findVisibleTargetPackage();
        if (foreground.isEmpty()) {
            externalTargetReadAttempts++;
            if (externalTargetReadAttempts <= 4) {
                handler.postDelayed(this::requestExternalAssistantStep, 700L);
                return;
            }
            failExternalAgent("لم أستطع تحديد التطبيق الظاهر حاليًا من خدمة إمكانية الوصول بعد أربع محاولات. لم ألتقط صورة على أنها شاشة هدف ولم أنفذ أي نقرة. افتح التطبيق المطلوب وتأكد من أن خدمة إمكانية الوصول مفعّلة ثم استأنف المهمة.");
            return;
        }
        externalTargetReadAttempts = 0;
        if (foreground.equals(externalAgentPackage)) {
            failExternalAgent("توقفت المهمة لأن تطبيق الذكاء الاصطناعي بقي في الواجهة بدل العودة إلى التطبيق المستهدف. لا يمكنني اعتماد لقطة الشاشة الحالية كأنها شاشة الهدف.");
            return;
        }
        if (foreground.equals(getPackageName())) {
            hideBotnaOverlaysForCleanScreenshot();
            externalTargetReadAttempts++;
            if (externalTargetReadAttempts <= 4) {
                handler.postDelayed(this::requestExternalAssistantStep, 450L);
            } else {
                failExternalAgent("ما زالت واجهة Botna في المقدمة بعد إخفاء عناصر التحكم؛ أوقفت المهمة بدل استخدام لقطة خاطئة. افتح التطبيق المستهدف ثم ابدأ المهمة من جديد.");
            }
            return;
        }
        externalTargetPackage = foreground;
        if (smartLiveBurstEnabled && !screenCaptureServiceReady) {
            requestScreenCapturePermission();
            return;
        }
        externalAgentCycleInFlight = true;
        externalCycleStartedAt = System.currentTimeMillis();
        externalComposerPolls = 0; externalResponsePolls = 0; externalSendAttempts = 0;
        externalSendRetryScheduled = false; externalSendAttemptInProgress = false; externalSendRetryAwaitingReady = false; externalVisualComposerSearchInFlight = false; externalVisualSendSearchInFlight = false; externalVisualPromptInjected = false; externalVisualComposerTapped = false; externalVisualSendButtonMatched = false; externalVisualSendButtonBounds.setEmpty(); externalVisualSendButtonScore = 0; externalVisualSendButtonMatchedAt = 0L; externalBaselineEditableText = ""; externalBaselineHadEditable = false; externalResponseNoChangeWaitIndex = 0; externalResponseReadAttempts = 0;
        externalPromptInjectedIntoComposer = false; externalPromptInjectionAttempts = 0; externalGeminiComposerFocusAttempts = 0;
        externalWhatsAppMetaAiTargetClicked = false;
        externalWhatsAppSearchActivated = false;
        externalWhatsAppSearchQueryEntered = false;
        externalWhatsAppNavigationAttempts = 0;
        externalCopyAttemptedForResponse = false; externalCodeCopyAttemptsForResponse = 0;
        externalClipboardTimestampBeforeCopy = 0L; externalCopiedResponseCandidate = ""; externalClipboardBeforeCopy = "";
        externalResponseStartedAt = 0L;
        externalComposerStateFingerprint = "";
        externalResponseStateFingerprint = "";
        externalComposerLastProgressAt = System.currentTimeMillis();
        externalResponseLastProgressAt = 0L;
        externalVisualProbeGeneration++;
        externalSendVisualProbeInFlight = false;
        externalSendVisualStateKnown = false;
        externalSendButtonLooksBlue = false;
        externalSendVisualProbeAt = 0L;
        externalFormatRetries = 0; externalStablePolls = 0; externalLastCandidate = "";
        hideBotnaOverlaysForCleanScreenshot();
        handler.postDelayed(() -> takeScreenshot((bitmap, error) -> {
            if (!smartTaskActive || !externalAgentCycleInFlight) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                return;
            }
            if (bitmap == null) {
                failExternalAgent("لم أتمكن من التقاط الشاشة المستهدفة قبل مشاركة الصورة. السبب: "
                        + (error == null ? "سبب غير معروف" : error) + ". لم أرسل تعليمات مبنية على لقطة قديمة.");
                return;
            }
            externalScreenshotWidth = bitmap.getWidth();
            externalScreenshotHeight = bitmap.getHeight();
            long currentFingerprint = smartScreenFingerprint(bitmap);
            if (smartTaskActionCount > 0 && smartLastScreenshotFingerprint != 0L && smartTaskHistory.length() > 0) {
                boolean unchanged = Long.bitCount(currentFingerprint ^ smartLastScreenshotFingerprint) <= 6;
                JSONObject lastEvent = smartTaskHistory.optJSONObject(smartTaskHistory.length() - 1);
                if (lastEvent != null) {
                    try {
                        lastEvent.put("screenChanged", !unchanged);
                        if (unchanged) lastEvent.put("result", lastEvent.optString("result", "")
                                + "\nملاحظة تحقق آلية: لم تتغير لقطة الشاشة بوضوح بعد هذا الإجراء؛ لا تكرر الإجراء نفسه بالمعلمات نفسها.");
                    } catch (Exception ignored) { }
                    try { BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "running"); } catch (Exception ignored) { }
                }
            }
            smartLastScreenshotFingerprint = currentFingerprint;
            String goal = smartTaskGoal;
            String prompt = buildExternalAgentPrompt(externalScreenshotWidth, externalScreenshotHeight);
            externalPrompt = prompt;
            if (smartLiveBurstEnabled) {
                startExternalLiveBurst(bitmap, prompt, goal, externalTaskIndex());
            } else {
                ArrayList<String> temporalPaths = takePendingExternalTemporalFramePaths();
                try {
                    screenshotShareExecutor.execute(() -> prepareAndLaunchExternalStep(bitmap, new ArrayList<>(), prompt, goal, externalTaskIndex(), null, temporalPaths));
                } catch (Exception e) {
                    if (!bitmap.isRecycled()) bitmap.recycle();
                    cleanupExternalTemporalPaths(temporalPaths);
                    handler.post(() -> failExternalAgent("تعذر تجهيز لقطة المهمة للمشاركة: " + e.getClass().getSimpleName()));
                }
            }
        }, "لقطة قبل إرسالها إلى " + BotnaExternalAppAgent.label(externalAgentPackage)), 520L);
    }

    private void requestScreenCapturePermission() {
        if (!smartTaskActive || !smartLiveBurstEnabled || screenCaptureServiceReady || screenCapturePermissionRequestInFlight) return;
        screenCapturePermissionRequestInFlight = true;
        try {
            Intent request = new Intent(this, BotnaScreenCapturePermissionActivity.class);
            request.putExtra(BotnaScreenCaptureService.EXTRA_TASK_ID, smartTaskId);
            request.putExtra(BotnaScreenCaptureService.EXTRA_CAPTURE_MODE, BotnaScreenCaptureService.CAPTURE_MODE_VIDEO);
            request.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(request);
            report("ستظهر نافذة نظام Android للسماح بتسجيل الشاشة. وافق عليها لتفعيل فيديو حقيقي مدته ثانيتان لكل دورة تحليل.");
        } catch (Exception e) {
            screenCapturePermissionRequestInFlight = false;
            failExternalAgent("تعذر فتح نافذة إذن تسجيل الشاشة: " + e.getClass().getSimpleName());
        }
    }

    private void startExternalLiveBurst(Bitmap firstFrame, String prompt, String goal, int actionIndex) {
        if (firstFrame != null && !firstFrame.isRecycled()) firstFrame.recycle();
        if (!smartTaskActive || !externalAgentCycleInFlight) return;
        if (!screenCaptureServiceReady) {
            failExternalAgent("تسجيل الشاشة الحقيقي غير جاهز. لم أستبدل الفيديو بمجموعة صور.");
            return;
        }
        if (externalLiveClipInFlight) return;
        try {
            File directory = new File(new File(getFilesDir(), "bot_studio_files"), "ai_share");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد فيديو المهمة.");
            String requestId = safeFileToken(smartTaskId) + "_" + System.currentTimeMillis();
            File output = new File(directory, "botna_live_" + requestId + ".mp4");
            externalLiveClipRequestId = requestId;
            externalLiveClipTaskId = smartTaskId;
            externalLiveClipPrompt = prompt;
            externalLiveClipGoal = goal;
            externalLiveClipActionIndex = actionIndex;
            externalLiveClipInFlight = true;
            Intent request = new Intent(this, BotnaScreenCaptureService.class);
            request.setAction(BotnaScreenCaptureService.ACTION_REQUEST_CLIP);
            request.putExtra(BotnaScreenCaptureService.EXTRA_TASK_ID, smartTaskId);
            request.putExtra(BotnaScreenCaptureService.EXTRA_REQUEST_ID, requestId);
            request.putExtra(BotnaScreenCaptureService.EXTRA_OUTPUT_PATH, output.getAbsolutePath());
            startService(request);
            handler.postDelayed(() -> {
                if (smartTaskActive && externalLiveClipInFlight && requestId.equals(externalLiveClipRequestId)) {
                    externalLiveClipInFlight = false;
                    failExternalAgent("انتهت مهلة تسجيل فيديو الشاشة الحقيقي بعد 12 ثانية دون ملف MP4 مكتمل.");
                }
            }, 12000L);
        } catch (Exception e) {
            externalLiveClipInFlight = false;
            failExternalAgent("تعذر بدء تسجيل فيديو الشاشة: " + e.getClass().getSimpleName());
        }
    }

    private void recycleBitmaps(List<Bitmap> bitmaps) {
        if (bitmaps == null) return;
        for (Bitmap bitmap : bitmaps) if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmaps.clear();
    }

    private int externalTaskIndex() { return Math.max(0, smartTaskActionCount); }

    private String buildExternalAgentPrompt(int width, int height) {
        StringBuilder out = new StringBuilder(8000);
        String device = Build.MANUFACTURER + " " + Build.MODEL;
        if ("RMX3363".equalsIgnoreCase(Build.MODEL)) device = "realme GT Master Edition (RMX3363)";
        out.append("أنت الوكيل البصري التنفيذي لتطبيق Botna على هاتف Android. مهمتك إكمال هدف المستخدم بالكامل عبر قرارات صغيرة متتابعة، لا أن تكتفي باقتراح إجراء واحد ثم تتوقف. أجب بالعربية وبإيجاز شديد، ولا تدّع تنفيذ أي فعل قبل أن ينفذه Botna ويتحقق منه.")
                .append("\nالجهاز: ").append(device)
                .append("\nالتطبيق الظاهر في لقطة الشاشة الحالية: ").append(externalTargetPackage.isEmpty() ? "التطبيق الظاهر تحت الوكيل" : externalTargetPackage)
                .append("\nأبعاد لقطة الشاشة الحالية: العرض=").append(width).append(" بكسل، الارتفاع=").append(height).append(" بكسل؛ الأصل (0,0) أعلى اليسار. استخدم إحداثيات بكسل الصورة الأصلية نفسها، ولا تحوّلها إلى dp أو نسب مئوية. النطاق الصالح x من 0 إلى ").append(Math.max(0, width - 1)).append(" وy من 0 إلى ").append(Math.max(0, height - 1)).append(". هذه اللقطة الأحدث وحدها مصدر إحداثيات التنفيذ؛ لا تستخدم إحداثيات من إطار أقدم أو صورة مرجعية.")
                .append(smartLiveBurstEnabled ? "\nأرفق Botna فيديو MP4 قابلًا للتشغيل ومدته ثانيتان بدقة بكسلات شاشة الهاتف الأصلية نفسها، دون تصغير أو تحويل إلى dp. أبعاد إطارات الفيديو تطابق أبعاد لقطة الشاشة الحالية ونظام إحداثياتها. آخر إطار في الفيديو هو الأحدث ويُستخدم وحده لتحديد إحداثيات التنفيذ؛ استخدم الإطارات السابقة لفهم الحركة فقط، ولا تنقل إحداثياتها إلى آخر إطار." : "")
                .append("\nتعليمات تنفيذ خاصة بالمزوّد:\n").append(BotnaExternalAppAgent.promptGuidance(externalAgentPackage, smartLiveBurstEnabled))
                .append("\nهدف المستخدم الكامل: ").append(smartTaskGoal)
                .append("\nعدد الإجراءات التي نفذها Botna حتى الآن: ").append(smartTaskActionCount)
                .append("\nسجل الإجراءات والنتائج المؤكدة من الأقدم للأحدث:");
        int start = Math.max(0, smartTaskHistory.length() - 9);
        for (int i = start; i < smartTaskHistory.length(); i++) {
            JSONObject event = smartTaskHistory.optJSONObject(i);
            if (event == null) continue;
            out.append("\n-").append(i + 1).append(" ")
                    .append(event.optString("label", event.optString("action", "إجراء")))
                    .append(" | نجح=").append(event.optBoolean("success", false))
                    .append(" | تغيرت الشاشة=").append(event.optBoolean("screenChanged", false))
                    .append(" | النتيجة: ").append(shortExternalText(event.optString("result", ""), 190));
        }
        out.append("\n\nأوامر Botna التي تستطيع اختيارها. أعد كائن JSON واحدًا صالحًا. الوضع الافتراضي إجراء واحد عندما يحتاج القرار إلى قراءة شاشة جديدة. إذا كانت هناك سلسلة قصيرة وفورية يمكن تنفيذها بأمان دون الحاجة إلى رؤية وسيطة، أعد مصفوفة actions مرتبة من إجراءين إلى 8 إجراءات؛ سينفذها Botna بالتتابع، وينتظر المهلة المحددة لكل خطوة، ويتوقف فور فشل خطوة، ثم يلتقط الشاشة للتحقق قبل أن يقرر المتابعة. مثال: {\"actions\":[{\"action\":\"TAP\",\"x\":180,\"y\":420},{\"action\":\"TYPE\",\"text\":\"YouTube\"},{\"action\":\"PRESS_ENTER\"}],\"reason\":\"فتح البحث وكتابة الاستعلام وإرساله دون الحاجة إلى قرار بصري وسيط\"}. لا تجمع خطوة تعتمد على شكل شاشة بعد تغييرها؛ نفّذ الإجراء الذي يغيّر الشاشة منفردًا ثم انتظر لقطة جديدة. لا تضع DONE أو STOP داخل سلسلة متعددة؛ أعدهما كأمر مستقل بعد فحص الشاشة الحالية:")
                .append("\n1) TAP: نقر دقيق في لقطة الشاشة الحالية: {\"action\":\"TAP\",\"x\":120,\"y\":360,\"reason\":\"...\"}. x وy بالبكسل ضمن العرض والارتفاع أعلاه.")
                .append("\n2) SWIPE: سحب/تمرير بإحداثيات البكسل: {\"action\":\"SWIPE\",\"x1\":300,\"y1\":900,\"x2\":300,\"y2\":350,\"duration_ms\":450}. يجب أن تكون نقطتا البداية والنهاية داخل الصورة.")
                .append("\n3) LONG_PRESS: ضغط مطوّل: {\"action\":\"LONG_PRESS\",\"x\":200,\"y\":400,\"duration_ms\":850}.")
                .append("\n4) SCROLL: تمرير حسب الاتجاه up أو down أو left أو right: {\"action\":\"SCROLL\",\"direction\":\"down\",\"duration_ms\":450}.")
                .append("\n5) TYPE: إدخال نص في الحقل المركّز: {\"action\":\"TYPE\",\"text\":\"النص المطلوب\"}. لا تستخدمه قبل التأكد أن الحقل الصحيح مركّز؛ انقر الحقل أولًا إن لزم.")
                .append("\n6) CLICK_TEXT: نقر نص ظاهر حرفيًا: {\"action\":\"CLICK_TEXT\",\"text\":\"بحث\"}. يستخدم Botna حدوده المرئية لتحويله إلى نقرة إحداثية؛ إذا لم يجد النص سيرفضه ويعيد التخطيط.")
                .append("\n7) OPEN_APP أو LAUNCH_APP: فتح تطبيق مثبت باسمه أو بمعرّف حزمتِه: {\"action\":\"OPEN_APP\",\"app_name\":\"WhatsApp\"}.")
                .append("\n8) OPEN_URL: فتح رابط http أو https: {\"action\":\"OPEN_URL\",\"url\":\"https://example.com\"}.")
                .append("\n9) BACK، HOME: زر الرجوع أو الرئيسية: {\"action\":\"BACK\"} أو {\"action\":\"HOME\"}.")
                .append("\n10) RECENTS، NOTIFICATIONS، QUICK_SETTINGS، DISMISS_NOTIFICATIONS، CLOSE_RECENTS: إدارة التطبيقات الأخيرة والإشعارات والإعدادات السريعة. مثال: {\"action\":\"RECENTS\"} أو {\"action\":\"GLOBAL_ACTION\",\"system_action\":\"notifications\"}.")
                .append("\n11) PRESS_ENTER: تنفيذ Enter/بحث؛ CLEAR_TEXT: مسح حقل الإدخال المركّز.")
                .append("\n12) WAIT: انتظار محدد بالمللي ثانية: {\"action\":\"WAIT\",\"ms\":1500}. استخدمه عند تحميل الواجهة أو الانتقال بين الشاشات، لكن لا تنتظر عشوائيًا إذا أمكن التحقق من التغير.")
                .append("\n13) WAIT_TEXT: انتظار ظهور نص ظاهر: {\"action\":\"WAIT_TEXT\",\"text\":\"تم الحفظ\",\"attempts\":30,\"interval_ms\":700}. WAIT_INPUT ينتظر ظهور حقل إدخال قابل للتحرير.")
                .append("\n14) IMAGE_MATCH أو WAIT_IMAGE: مطابقة صورة محفوظة في مكتبة Botna بالاسم الدقيق للملف: {\"action\":\"IMAGE_MATCH\",\"image_name\":\"اسم_الملف.png\",\"threshold\":85}. استخدم هذا فقط إذا كان اسم الملف موجودًا في القائمة أدناه.")
                .append("\n15) READ_SCREEN_TEXT: قراءة النصوص التي تتيحها إمكانية الوصول؛ CHECK_INTERNET: فحص الاتصال؛ WAIT_INTERNET: انتظار عودة الاتصال؛ SPEED_TEST: قياس سرعة التنزيل؛ NOTIFY مع message لإظهار تنبيه.")
                .append("\n16) DONE مع reason وverification_evidence لإنهاء المهمة فقط بعد ظهور دليل صريح على تحقق الهدف. STOP مع reason فقط إذا تعذر الاستمرار فعلًا أو لزم تدخل المستخدم.")
                .append("\n17) أولوية التنقل بين التطبيقات: فضّل التنقل المرئي عبر HOME أو RECENTS، ثم البحث عن التطبيق المطلوب من واجهة الهاتف أو درج التطبيقات والتمرير عند الحاجة، ثم اضغط أيقونة التطبيق باستخدام TAP بإحداثيات مأخوذة من أحدث لقطة فقط. لا تستخدم OPEN_APP أو LAUNCH_APP بالاسم إلا إذا تعذّر العثور على التطبيق بالطريقة المرئية أو كانت واجهة البحث غير متاحة. بعد كل انتقال أو تمرير خذ لقطة جديدة ولا تستخدم إحداثيات قديمة.")
                .append("\n18) سياسة الانتهاء: عندما تثبت اللقطة الحالية أن هدف المستخدم اكتمل، أعد DONE فورًا مع verification_evidence يذكر دليلًا محددًا ظاهرًا في اللقطة. لا تعِد تنفيذ خطوات منجزة ولا تطلب لقطات إضافية بعد تحقق الهدف. لا تستخدم DONE لمجرد أن الشاشة لا تتغير، أو لأنك فشلت في العثور على عنصر، أو لأن المهمة تبدو مكتملة دون دليل؛ في هذه الحالات أعد إجراء تصحيح مناسب أو STOP إذا تعذّر الاستمرار فعلًا.")
                .append("\n\nمكتبة الصور المحفوظة في Botna:");
        JSONArray imageLibrary = BotnaStore.imageLibrary(this);
        int imageCount = 0;
        for (int i = 0; i < imageLibrary.length() && imageCount < 80; i++) {
            JSONObject item = imageLibrary.optJSONObject(i);
            if (item == null) continue;
            String fileName = item.optString("fileName", "").trim();
            if (fileName.isEmpty()) continue;
            out.append("\n- الاسم: ").append(shortExternalText(item.optString("name", fileName), 70))
                    .append("؛ image_name: ").append(fileName);
            imageCount++;
        }
        if (imageCount == 0) out.append("\nلا توجد صور محفوظة متاحة حاليًا؛ لا تخترع اسم صورة.");
        out.append("\n\nقواعد الخرج: أعد كائن JSON واحدًا فقط صالحًا، بلا Markdown أو نص قبله أو بعده. للإجراء العادي استخدم الحقل action؛ للسلسلة الفورية استخدم actions مرتبة من 2 إلى 8 عناصر. كل عنصر يجب أن يطابق صيغة أحد الأوامر المذكورة، وأضف reason موجزًا عند الحاجة. لا تخترع أمرًا غير موجود في القائمة.")
                .append("\nفي سلسلة actions ينفذ Botna الخطوات بالترتيب دون إعادة لقطة بين كل خطوة، ثم يأخذ لقطة جديدة بعد نهاية السلسلة. استخدم هذا فقط للإجراءات الفورية التي لا تحتاج إلى تحليل حالة شاشة وسيطة، مثل فتح حقل ظاهر ثم الكتابة ثم Enter. إذا غيّر الإجراء الشاشة أو كان موضع الخطوة التالية يعتمد على شكل الواجهة الجديدة، فأعد إجراءً واحدًا فقط وانتظر لقطة جديدة. إذا فشل إجراء ضمن السلسلة ستتوقف بقيتها تلقائيًا. إذا كان سجل Botna يقول إن إيماءة النقر نجحت، فهذا يعني أن Android قبل تنفيذ الإيماءة عند الإحداثيات المحددة؛ لا تعتبر عدم تغير بكسلات الشاشة وحده دليلًا على أن النقرة أخطأت الهدف. افحص التركيز أو النص أو حالة العنصر، وانتظر عند احتمال تأخر الاستجابة، ولا تكرر النقرة نفسها دون دليل. لا تعلن DONE إلا بدليل محدد ظاهر، ولا تخمّن إذا كانت الصورة غير واضحة.")
                .append("\nللمعرفة: يدعم محرر Botna أيضًا شروط IF_SUCCESS وIF_FAIL وELSE وEND_IF، والتكرار REPEAT_START وREPEAT_END، والخطوات المخصصة. هذه علامات تركيبية يديرها محرر السكربت؛ لا تُرجعها كإجراء مستقل في هذا البروتوكول، إذ يدير الوكيل الدورة خطوة بخطوة تلقائيًا.")
                .append("\nلا تستخدم التحكم النصي المباشر لتخمين مواقع أزرار. فضّل TAP/SWIPE بإحداثيات داخل اللقطة الحالية. استخدم CLICK_TEXT فقط إذا كانت العبارة ظاهرة بوضوح. لا تُغلق تطبيقًا أو تحذف أو ترسل أو تنشر أو تجري عملية مالية إلا إذا طلب المستخدم ذلك صراحةً ضمن هدفه.");
        return out.toString();
    }

    private String shortExternalText(String value, int limit) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() <= limit ? clean : clean.substring(0, limit) + "…";
    }

    private void prepareAndLaunchExternalStep(Bitmap bitmap, ArrayList<Bitmap> previousFrames, String prompt, String goal, int actionIndex) {
        prepareAndLaunchExternalStep(bitmap, previousFrames, prompt, goal, actionIndex, null, new ArrayList<>());
    }

    private void prepareAndLaunchExternalStep(Bitmap bitmap, ArrayList<Bitmap> previousFrames, String prompt, String goal, int actionIndex, File capturedVideoFile) {
        prepareAndLaunchExternalStep(bitmap, previousFrames, prompt, goal, actionIndex, capturedVideoFile, new ArrayList<>());
    }

    private void prepareAndLaunchExternalStep(Bitmap bitmap, ArrayList<Bitmap> previousFrames, String prompt, String goal, int actionIndex, File capturedVideoFile, ArrayList<String> temporalFramePaths) {
        File screenFile = null;
        File videoFile = null;
        File referencePreviewFile = null;
        ArrayList<Bitmap> framesToRecycle = previousFrames == null ? new ArrayList<>() : previousFrames;
        try {
            File directory = new File(new File(getFilesDir(), "bot_studio_files"), "ai_share");
            if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("تعذر إنشاء مجلد المشاركة.");
            long stamp = System.currentTimeMillis();
            ArrayList<Uri> referenceUris = new ArrayList<>();
            String referencePath = externalReferenceAttachmentPath;
            if (referencePath != null && !referencePath.isEmpty() && new File(referencePath).isFile()) {
                if (BotStudioStore.isImageAttachment(referencePath)) {
                    referenceUris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", new File(referencePath)));
                } else if (BotStudioStore.isPdfAttachment(referencePath)) {
                    Bitmap reference = BotStudioStore.loadAttachmentPreview(this, referencePath, 1200);
                    if (reference != null) {
                        referencePreviewFile = new File(directory, "botna_reference_" + safeFileToken(smartTaskId) + ".jpg");
                        try (FileOutputStream output = new FileOutputStream(referencePreviewFile)) {
                            reference.compress(Bitmap.CompressFormat.JPEG, 84, output);
                            output.flush();
                            output.getFD().sync();
                        }
                        if (referencePreviewFile.isFile() && referencePreviewFile.length() > 0) {
                            referenceUris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", referencePreviewFile));
                        } else {
                            referencePreviewFile = null;
                        }
                        if (!reference.isRecycled()) reference.recycle();
                    }
                }
            }
            boolean customWebAi = BotnaExternalAppAgent.usesWebAiActivity(externalAgentPackage);
            ArrayList<String> customAttachmentPaths = new ArrayList<>();
            Intent send;
            int temporalFrameCount = temporalFramePaths != null && temporalFramePaths.size() == 3 ? 3 : framesToRecycle.size();
            if (smartLiveBurstEnabled) {
                videoFile = capturedVideoFile;
                if (videoFile == null || !videoFile.isFile() || videoFile.length() <= 1024L) throw new IllegalStateException("لم يصل ملف فيديو حقيقي مكتمل من خدمة تسجيل الشاشة.");
                if (customWebAi) {
                    customAttachmentPaths.add(videoFile.getAbsolutePath());
                    send = null;
                } else {
                    Uri videoUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", videoFile);
                    send = createExternalVideoShareIntent(videoUri, referenceUris, prompt, externalAgentPackage);
                }
            } else {
                ArrayList<Uri> imageUris = new ArrayList<>();
                boolean temporalActionFrames = temporalFramePaths != null && temporalFramePaths.size() == 3;
                if (temporalActionFrames) {
                    for (int i = 0; i < temporalFramePaths.size(); i++) {
                        File frameFile = new File(temporalFramePaths.get(i));
                        if (!frameFile.isFile() || frameFile.length() <= 0) throw new IllegalStateException("الصورة الزمنية رقم " + (i + 1) + " غير موجودة.");
                        if (customWebAi) customAttachmentPaths.add(frameFile.getAbsolutePath());
                        else imageUris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", frameFile));
                    }
                    prompt += "\nأرفق Botna ثلاث صور منفصلة ومرتبة زمنيًا: الصورة 1 (01_before) قبل تنفيذ الإجراء السابق؛ الصورة 2 (02_during) أثناء الإجراء أو فور بدء الإيماءة؛ الصورة 3 (03_after) بعد اكتمال الإجراء واستقرار الشاشة. اقرأ الصور الثلاث كلها لفهم ما حدث. الصورة الثالثة فقط هي أحدث حالة ويجب اعتماد إحداثيات الإجراء التالي منها؛ لا تستنتج حركة غير مدعومة بالصور.";
                } else {
                    screenFile = new File(directory, "botna_task_" + safeFileToken(smartTaskId) + "_" + stamp + ".jpg");
                    try (FileOutputStream output = new FileOutputStream(screenFile)) {
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) throw new IllegalStateException("تعذر ضغط لقطة الشاشة.");
                        output.flush();
                        output.getFD().sync();
                    }
                    if (customWebAi) customAttachmentPaths.add(screenFile.getAbsolutePath());
                    else imageUris.add(FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", screenFile));
                }
                if (temporalActionFrames) {
                    if (!referenceUris.isEmpty()) prompt += "\nلم أرفق المرجع الإضافي مع هذه الرسالة حتى أضمن وصول الصور الزمنية الثلاث بالترتيب نفسه.";
                } else if (!customWebAi) {
                    imageUris.addAll(referenceUris);
                }
                send = customWebAi ? null : createExternalShareIntent(imageUris, prompt, externalAgentPackage, 0, temporalActionFrames);
            }
            if (customWebAi) {
                String customUrl = BotnaExternalAppAgent.webAiUrl(this, externalAgentPackage);
                if (!BotnaExternalAppAgent.isValidCustomWebAiUrl(customUrl)) throw new IllegalStateException("لم يُحفظ رابط HTTPS صالح لموقع الذكاء الاصطناعي المخصص.");
                if (referencePath != null && !referencePath.isEmpty() && new File(referencePath).isFile()) {
                    if (BotStudioStore.isImageAttachment(referencePath)) {
                        customAttachmentPaths.add(referencePath);
                    } else if (BotStudioStore.isPdfAttachment(referencePath) && referencePreviewFile != null
                            && referencePreviewFile.isFile()) {
                        customAttachmentPaths.add(referencePreviewFile.getAbsolutePath());
                    }
                }
                send = new Intent(this, BotnaWebAiActivity.class);
                send.putExtra(BotnaWebAiActivity.EXTRA_TASK_ID, smartTaskId);
                send.putExtra(BotnaWebAiActivity.EXTRA_MODEL_PACKAGE, externalAgentPackage);
                send.putExtra(BotnaWebAiActivity.EXTRA_URL, customUrl);
                send.putExtra(BotnaWebAiActivity.EXTRA_PROMPT, prompt);
                send.putExtra(BotnaWebAiActivity.EXTRA_IMAGE_WIDTH, externalScreenshotWidth);
                send.putExtra(BotnaWebAiActivity.EXTRA_IMAGE_HEIGHT, externalScreenshotHeight);
                send.putStringArrayListExtra(BotnaWebAiActivity.EXTRA_ATTACHMENTS, customAttachmentPaths);
            }
            BotnaTaskEvidenceStore.saveFrame(this, smartTaskId, goal, actionIndex,
                    smartLiveBurstEnabled ? "آخر إطار من فيديو البث المباشر لمدة ثانيتين" : (temporalFramePaths != null && temporalFramePaths.size() == 3
                            ? "الصورة الثالثة بعد تنفيذ الإجراء وقبل القرار التالي" : "أحدث شاشة قبل قرار " + BotnaExternalAppAgent.label(externalAgentPackage)), bitmap);
            if (send == null) {
                if (smartLiveBurstEnabled) throw new IllegalStateException("تطبيق " + BotnaExternalAppAgent.label(externalAgentPackage) + " لا يقبل مشاركة فيديو MP4 عبر Android؛ لم أستبدل الفيديو بصور منفصلة.");
                throw new IllegalStateException("التطبيق المختار لا يقبل مشاركة الصور بالطريقة المتاحة.");
            }
            pruneAiShareImages(directory, 16);
            pruneAiShareVideos(directory, 4);
            final Intent intentToOpen = send;
            final String packageToOpen = externalAgentPackage;
            final String promptToVerify = prompt;
            final int temporalFramesToShare = temporalFrameCount;
            final boolean sendingVideo = smartLiveBurstEnabled;
            handler.post(() -> {
                if (!smartTaskActive || !externalAgentCycleInFlight) return;
                try {
                    intentToOpen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(intentToOpen);
                    externalCycleStartedAt = System.currentTimeMillis();
                    boolean launchingCustomWebsite = BotnaExternalAppAgent.usesWebAiActivity(packageToOpen);
                    String shareResult = launchingCustomWebsite
                            ? "فتح Botna موقع الذكاء الاصطناعي المخصص داخل WebView وسيبحث عن حقل الإدخال ثم يرفع المرفقات ويرسل التعليمات ويتحقق من رد قابل للتنفيذ."
                            : sendingVideo
                                ? "جهّز Botna ملف فيديو MP4 قابلًا للتشغيل بطول ثانيتين وشاركه إلى " + BotnaExternalAppAgent.label(packageToOpen) + " بدل إرسال الإطارات كصور منفصلة."
                                : "أرسل Botna ملف الصورة وتعليمات الإجراء التالي إلى التطبيق المختار عبر Android Intent.";
                    if (temporalFramesToShare > 0 && sendingVideo) shareResult += " عدد الإطارات المصدرية التي استُخدمت في الفيديو: " + (temporalFramesToShare + 1) + ".";
                    shareResult += " لم يعتبر الإرسال ناجحًا قبل تحقق واجهة التطبيق.";
                    BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "external_ai_share_started",
                            smartTaskActionCount, sendingVideo ? "مشاركة فيديو بث مباشر إلى " + BotnaExternalAppAgent.label(packageToOpen)
                                    : "مشاركة لقطة إلى " + BotnaExternalAppAgent.label(packageToOpen), shareResult, true, null);
                    externalPrompt = promptToVerify;
                    externalPromptInjectedIntoComposer = false;
                    externalPromptInjectionAttempts = 0;
                    externalGeminiComposerFocusAttempts = 0;
                    externalCopyAttemptedForResponse = false;
                    externalCodeCopyAttemptsForResponse = 0;
                    externalClipboardTimestampBeforeCopy = 0L;
                    externalCopiedResponseCandidate = "";
                    externalClipboardBeforeCopy = "";
                    externalComposerPolls = 0;
                    externalLastCandidate = "";
                    externalStablePolls = 0;
                    if (!BotnaExternalAppAgent.usesWebAiActivity(packageToOpen)) {
                        handler.postDelayed(() -> waitForExternalComposer(0),
                                BotnaExternalAppAgent.GEMINI.equals(packageToOpen) ? 2600L : 1250L);
                    }
                } catch (Exception e) {
                    failExternalAgent("فشل فتح تطبيق " + BotnaExternalAppAgent.label(packageToOpen)
                            + (sendingVideo ? " لمشاركة فيديو MP4" : " لمشاركة لقطة الشاشة") + ": " + e.getClass().getSimpleName());
                }
            });
        } catch (Exception e) {
            cleanupExternalTemporalPaths(temporalFramePaths);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            String output = smartLiveBurstEnabled ? "تعذر إنشاء فيديو MP4 أو إرفاقه إلى " : "تعذر تجهيز الصورة أو إرفاقها إلى ";
            handler.post(() -> failExternalAgent(output + BotnaExternalAppAgent.label(externalAgentPackage) + ": " + reason));
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            recycleBitmaps(framesToRecycle);
        }
    }

    private Intent createExternalVideoShareIntent(Uri videoUri, ArrayList<Uri> referenceUris, String prompt, String packageName) {
        if (videoUri == null || !BotnaExternalAppAgent.canShareVideo(this, packageName)) return null;
        ArrayList<Uri> extraUris = referenceUris == null ? new ArrayList<>() : referenceUris;
        if (!extraUris.isEmpty() && BotnaExternalAppAgent.canShareMixedAttachments(this, packageName)) {
            ArrayList<Uri> allUris = new ArrayList<>();
            allUris.add(videoUri);
            allUris.addAll(extraUris);
            Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
            send.setPackage(packageName);
            send.setType("*/*");
            send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, allUris);
            send.putExtra(Intent.EXTRA_TEXT, prompt + "\nأرفقت فيديو MP4 قابلًا للتشغيل مدته ثانيتان، إضافة إلى المراجع المتاحة. آخر إطار في الفيديو هو أحدث حالة للشاشة؛ استخدمه وحده لتحديد إحداثيات التنفيذ.");
            ClipData clip = ClipData.newUri(getContentResolver(), "Botna live screen video", videoUri);
            for (Uri uri : extraUris) clip.addItem(new ClipData.Item(uri));
            send.setClipData(clip);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return send;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setPackage(packageName);
        send.setType("video/mp4");
        send.putExtra(Intent.EXTRA_STREAM, videoUri);
        String effectivePrompt = prompt + "\nأرفقت فيديو MP4 قابلًا للتشغيل مدته ثانيتان. آخر إطار في الفيديو هو أحدث حالة للشاشة؛ استخدمه وحده لتحديد الإحداثيات، والإطارات السابقة لفهم الحركة فقط.";
        if (!extraUris.isEmpty()) effectivePrompt += "\nلم أرفق المرجع الإضافي لأن التطبيق لا يتيح مشاركة الفيديو والمراجع معًا في رسالة واحدة.";
        send.putExtra(Intent.EXTRA_TEXT, effectivePrompt);
        send.setClipData(ClipData.newUri(getContentResolver(), "Botna live screen video", videoUri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            if (getPackageManager().queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) return null;
        } catch (Exception e) { return null; }
        return send;
    }

    private String safeFileToken(String input) {
        String value = input == null ? "task" : input.replaceAll("[^A-Za-z0-9_-]", "_");
        return value.length() > 45 ? value.substring(value.length() - 45) : value;
    }

    private Intent createExternalShareIntent(ArrayList<Uri> imageUris, String prompt, String packageName, int temporalFrameCount) {
        return createExternalShareIntent(imageUris, prompt, packageName, temporalFrameCount, false);
    }

    private Intent createExternalShareIntent(ArrayList<Uri> imageUris, String prompt, String packageName, int temporalFrameCount, boolean temporalActionFrames) {
        if (imageUris == null || imageUris.isEmpty()) return null;
        if (temporalActionFrames && (imageUris.size() < 3 || !BotnaExternalAppAgent.canShareImages(this, packageName, imageUris.size()))) return null;
        if (imageUris.size() > 1 && BotnaExternalAppAgent.canShareImages(this, packageName, imageUris.size())) {
            Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
            send.setPackage(packageName);
            send.setType("image/*");
            send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, imageUris);
            String imageGuidance = temporalActionFrames
                    ? "\nالصور الثلاث الأولى مرتبة زمنيًا: (1) قبل الإجراء، (2) أثناء الإجراء أو فور بدء الإيماءة، (3) بعد اكتمال الإجراء. حلل التغيّر بين الصور الثلاث، لكن استخدم الصورة 3 وحدها لتحديد إحداثيات الخطوة التالية. أي صور تأتي بعدها هي مراجع إضافية للمستخدم."
                    : temporalFrameCount > 0
                    ? "\nالصورة الأولى هي أحدث إطار للشاشة وتُستخدم وحدها للإحداثيات. تليها " + temporalFrameCount + " إطارات أقدم من بث مدته ثانيتان مرتبة من الأقرب زمنيًا إلى الأقدم، ثم قد يأتي مرفق مرجعي للمستخدم. استخدم الإطارات القديمة لفهم الحركة فقط."
                    : "\nالصورة الأولى هي الشاشة الحالية وتُستخدم إحداثياتها فقط. الصور التالية مراجع بصرية إضافية من المستخدم.";
            send.putExtra(Intent.EXTRA_TEXT, prompt + imageGuidance);
            ClipData clip = ClipData.newUri(getContentResolver(), "Botna target screen", imageUris.get(0));
            for (int i = 1; i < imageUris.size(); i++) clip.addItem(new ClipData.Item(imageUris.get(i)));
            send.setClipData(clip);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return send;
        }
        if (temporalActionFrames) return null;
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setPackage(packageName);
        send.setType("image/jpeg");
        send.putExtra(Intent.EXTRA_STREAM, imageUris.get(0));
        String effectivePrompt = prompt;
        if (imageUris.size() > 1 && temporalFrameCount > 0) {
            effectivePrompt += "\nالتطبيق الحالي لا يتيح إرفاق صور متعددة في رسالة واحدة؛ أُرفقت أحدث لقطة فقط، لذلك لا تفترض أنك رأيت الإطارات السابقة ولا تستنتج حركة غير ظاهرة في الصورة الحالية.";
        } else if (imageUris.size() > 1) {
            effectivePrompt += "\nتعذر إرفاق المرجع الإضافي مع الصورة الحالية في هذا التطبيق؛ لا تفترض أنك رأيت المرجع الإضافي.";
        }
        send.putExtra(Intent.EXTRA_TEXT, effectivePrompt);
        send.setClipData(ClipData.newUri(getContentResolver(), "Botna target screen", imageUris.get(0)));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            if (getPackageManager().queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) return null;
        } catch (Exception e) { return null; }
        return send;
    }

    private boolean advanceWhatsAppMetaAiShareFlow(ExternalUiSnapshot snapshot) {
        if (snapshot == null || !BotnaExternalAppAgent.META_AI_WHATSAPP.equals(externalAgentPackage)
                || !BotnaExternalAppAgent.META_AI_WHATSAPP.equals(snapshot.packageName)) return false;
        if (snapshot.hasEditable && externalPromptFullyPresentInComposer(snapshot.editableText) && snapshot.sendButtonFound) return false;
        String lower = snapshot.text == null ? "" : snapshot.text.toLowerCase(Locale.ROOT);
        boolean formalRecipientPicker = containsAny(lower,
                "send to", "share to", "select contact", "select recipient", "forward to",
                "إرسال إلى", "ارسال الى", "اختيار جهة اتصال", "اختر جهة اتصال", "حدد جهة اتصال",
                "إعادة توجيه إلى", "اعادة توجيه الى", "المحادثات الأخيرة", "المحادثات الاخيرة");
        boolean recipientPicker = formalRecipientPicker;
        if (!recipientPicker && !snapshot.hasEditable && !snapshot.sendButtonFound
                && containsAny(lower, "meta ai", "الذكاء الاصطناعي", "اسأل ميتا", "اسأل الذكاء الاصطناعي")) {
            recipientPicker = true;
        }
        if (!recipientPicker) return false;
        if (externalWhatsAppNavigationAttempts >= 24) {
            failExternalAgent("لم يتمكن Botna من الوصول إلى محادثة Meta AI في WhatsApp عبر شاشة المشاركة. لم يرسل المرفق إلى جهة أخرى ولم يضع التعليمات في مربع بحث غير مؤكد.");
            return true;
        }
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null
                    || !BotnaExternalAppAgent.META_AI_WHATSAPP.equals(root.getPackageName().toString())) return false;
            if (!externalWhatsAppMetaAiTargetClicked || !formalRecipientPicker) {
                boolean clicked = clickWhatsAppMetaAiConversationRow(root);
                if (clicked) {
                    externalWhatsAppMetaAiTargetClicked = true;
                    externalWhatsAppNavigationAttempts++;
                    return true;
                }
                if (externalWhatsAppSearchActivated && !externalWhatsAppSearchQueryEntered) {
                    if (setWhatsAppMetaAiSearchQuery(root, 0)) {
                        externalWhatsAppSearchQueryEntered = true;
                        externalWhatsAppNavigationAttempts++;
                        return true;
                    }
                } else if (!externalWhatsAppSearchActivated) {
                    boolean searchOpened = findAndClick(root, "Search contacts", false)
                            || findAndClick(root, "Search", false)
                            || findAndClick(root, "Find", false)
                            || findAndClick(root, "بحث عن جهة اتصال", false)
                            || findAndClick(root, "بحث", false)
                            || findAndClick(root, "البحث", false);
                    if (searchOpened) {
                        externalWhatsAppSearchActivated = true;
                        externalWhatsAppNavigationAttempts++;
                        return true;
                    }
                    if (setWhatsAppMetaAiSearchQuery(root, 0)) {
                        externalWhatsAppSearchActivated = true;
                        externalWhatsAppSearchQueryEntered = true;
                        externalWhatsAppNavigationAttempts++;
                        return true;
                    }
                }
                externalWhatsAppNavigationAttempts++;
                return true;
            }
            String[] continueLabels = new String[]{"Next", "التالي", "متابعة", "Continue", "Forward", "إعادة توجيه", "ارسال", "إرسال"};
            for (String label : continueLabels) {
                if (findAndClick(root, label, true)) {
                    externalWhatsAppNavigationAttempts++;
                    return true;
                }
            }
            externalWhatsAppNavigationAttempts++;
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (root != null) root.recycle();
        }
    }

    private boolean clickWhatsAppMetaAiConversationRow(AccessibilityNodeInfo root) {
        android.util.DisplayMetrics metrics = realScreenMetrics();
        if (root == null || metrics.widthPixels <= 0 || metrics.heightPixels <= 0) return false;
        ArrayList<Rect> candidates = new ArrayList<>();
        collectWhatsAppMetaAiRows(root, 0, metrics, candidates);
        if (candidates.isEmpty()) return false;
        candidates.sort((a, b) -> Integer.compare(a.top, b.top));
        Rect row = candidates.get(0);
        int x = row.left + Math.round(row.width() * 0.43f);
        int y = row.centerY();
        Path path = new Path();
        path.moveTo(Math.max(row.left + dp(24), Math.min(row.right - dp(24), x)), y);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 100L)).build();
        return dispatchGesture(gesture, null, handler);
    }

    private void collectWhatsAppMetaAiRows(AccessibilityNodeInfo node, int depth, android.util.DisplayMetrics metrics, ArrayList<Rect> rows) {
        if (node == null || depth > 35) return;
        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String value = ((text == null ? "" : text.toString()) + " " + (desc == null ? "" : desc.toString())).toLowerCase(Locale.ROOT);
            if (value.contains("meta ai") || value.contains("ميتا ai") || value.contains("الذكاء الاصطناعي")) {
                AccessibilityNodeInfo ancestor = AccessibilityNodeInfo.obtain(node);
                try {
                    for (int level = 0; ancestor != null && level < 7; level++) {
                        Rect bounds = new Rect();
                        ancestor.getBoundsInScreen(bounds);
                        if (!bounds.isEmpty() && bounds.width() >= metrics.widthPixels * 0.66f
                                && bounds.height() >= dp(46) && bounds.height() <= dp(190)
                                && bounds.top >= dp(55) && bounds.bottom <= metrics.heightPixels - dp(95)) {
                            boolean duplicate = false;
                            for (Rect existing : rows) if (Math.abs(existing.top - bounds.top) < dp(8)) duplicate = true;
                            if (!duplicate) rows.add(new Rect(bounds));
                        }
                        AccessibilityNodeInfo parent = ancestor.getParent();
                        ancestor.recycle();
                        ancestor = parent;
                    }
                } finally {
                    if (ancestor != null) ancestor.recycle();
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    if (child != null) collectWhatsAppMetaAiRows(child, depth + 1, metrics, rows);
                } catch (Exception ignored) {
                } finally {
                    if (child != null) child.recycle();
                }
            }
        } catch (Exception ignored) { }
    }

    private boolean setWhatsAppMetaAiSearchQuery(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 30) return false;
        try {
            if (node.isEditable() && node.isEnabled() && node.isVisibleToUser()) {
                CharSequence textValue = node.getText();
                CharSequence description = node.getContentDescription();
                CharSequence hint = node.getHintText();
                String labels = ((textValue == null ? "" : textValue.toString()) + " "
                        + (description == null ? "" : description.toString()) + " "
                        + (hint == null ? "" : hint.toString())).toLowerCase(Locale.ROOT);
                String viewId = "";
                String className = "";
                try { viewId = String.valueOf(node.getViewIdResourceName()).toLowerCase(Locale.ROOT); } catch (Exception ignored) { }
                try { className = String.valueOf(node.getClassName()).toLowerCase(Locale.ROOT); } catch (Exception ignored) { }
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                android.util.DisplayMetrics metrics = realScreenMetrics();
                boolean searchField = containsAny(labels + " " + viewId, "search", "بحث", "البحث", "find", "contact")
                        || (className.contains("edittext") && !bounds.isEmpty() && metrics.heightPixels > 0
                        && bounds.centerY() < metrics.heightPixels * 0.45f);
                if (searchField && !bounds.isEmpty()) {
                    node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    Bundle args = new Bundle();
                    args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Meta AI");
                    if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    if (child != null && setWhatsAppMetaAiSearchQuery(child, depth + 1)) return true;
                } catch (Exception ignored) {
                } finally {
                    if (child != null) child.recycle();
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean focusGeminiComposer() {
        AccessibilityNodeInfo root = null;
        AccessibilityNodeInfo composer = null;
        try {
            root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null
                    && BotnaExternalAppAgent.GEMINI.equals(root.getPackageName().toString())) {
                composer = findExternalComposerNode(root);
                if (composer == null) composer = findFirstEditableNode(root);
                if (composer != null && composer.isVisibleToUser()) {
                    composer.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    composer.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    Rect bounds = new Rect();
                    composer.getBoundsInScreen(bounds);
                    if (!bounds.isEmpty() && bounds.centerY() > realScreenMetrics().heightPixels * 0.48f) return true;
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (composer != null) composer.recycle();
            if (root != null) root.recycle();
        }
        android.util.DisplayMetrics metrics = realScreenMetrics();
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) return false;
        Path path = new Path();
        path.moveTo(Math.round(metrics.widthPixels * 0.66f), Math.round(metrics.heightPixels * 0.785f));
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 90L)).build();
        return dispatchGesture(gesture, null, handler);
    }

    private boolean externalPromptFullyPresentInComposer(String editableText) {
        if (editableText == null || editableText.trim().isEmpty() || externalPrompt == null || externalPrompt.trim().isEmpty()) return false;
        String actual = normalizeExternalPromptText(editableText);
        String expected = normalizeExternalPromptText(externalPrompt);
        int headLength = Math.min(96, expected.length());
        int tailStart = Math.max(0, expected.length() - 120);
        String head = expected.substring(0, headLength);
        String tail = expected.substring(tailStart);
        if (!actual.contains(head) || !actual.contains(tail)) return false;
        if (!actual.contains("أبعاد لقطة الشاشة الحالية: العرض=")
                || !actual.contains("هدف المستخدم الكامل:")
                || !actual.contains("تعليمات تنفيذ خاصة بالمزوّد:")
                || !actual.contains("قواعد الخرج:")) return false;
        return actual.length() >= Math.ceil(expected.length() * 0.90d);
    }

    private String normalizeExternalPromptText(String value) {
        if (value == null) return "";
        return value.replace('\r', '\n').replaceAll("\\s+", " ").trim();
    }

    private interface ExternalTemplateMatchCallback { void done(ImageMatcher.Result result, String error); }

    private void findExternalVisualTemplate(String role, ExternalTemplateMatchCallback callback) {
        if (callback == null) return;
        File templateFile = BotnaExternalAppAgent.visualTemplateFile(this, externalAgentPackage, role);
        if (templateFile == null || !templateFile.isFile() || templateFile.length() <= 0) {
            callback.done(null, "لم تُحفظ صورة مرجعية لهذا العنصر.");
            return;
        }
        takeScreenshotForStateProbe((screen, screenshotError) -> {
            if (screen == null) {
                callback.done(null, screenshotError == null ? "تعذر التقاط الشاشة للبحث البصري." : screenshotError);
                return;
            }
            Bitmap template = BitmapFactory.decodeFile(templateFile.getAbsolutePath());
            if (template == null || template.isRecycled()) {
                if (!screen.isRecycled()) screen.recycle();
                callback.done(null, "تعذر قراءة الصورة المرجعية المحفوظة.");
                return;
            }
            final int threshold = BotnaExternalAppAgent.visualThreshold(this, externalAgentPackage);
            try {
                matcherExecutor.execute(() -> {
                    ImageMatcher.Result found = null;
                    String failure = null;
                    try {
                        ArrayList<ImageMatcher.NamedBitmap> templates = new ArrayList<>();
                        templates.add(new ImageMatcher.NamedBitmap(role, template));
                        found = ImageMatcher.find(screen, templates, threshold);
                        if (found != null && found.score < threshold) found = null;
                    } catch (Throwable error) {
                        failure = error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
                    } finally {
                        if (!screen.isRecycled()) screen.recycle();
                        if (!template.isRecycled()) template.recycle();
                    }
                    ImageMatcher.Result finalFound = found;
                    String finalFailure = failure;
                    handler.post(() -> callback.done(finalFound, finalFailure));
                });
            } catch (RuntimeException error) {
                if (!screen.isRecycled()) screen.recycle();
                if (!template.isRecycled()) template.recycle();
                callback.done(null, error.getMessage() == null ? "تعذر تشغيل مطابقة الصور." : error.getMessage());
            }
        });
    }

    private void searchAndFocusExternalComposer(int attempt) {
        if (externalVisualComposerSearchInFlight || !smartTaskActive || !externalAgentCycleInFlight) return;
        externalVisualComposerSearchInFlight = true;
        findExternalVisualTemplate(BotnaExternalAppAgent.TEMPLATE_COMPOSER, (match, error) -> {
            externalVisualComposerSearchInFlight = false;
            if (!smartTaskActive || !externalAgentCycleInFlight) return;
            if (match == null) {
                handler.postDelayed(() -> waitForExternalComposer(Math.max(attempt + 1, externalComposerPolls)), 1200L);
                return;
            }
            int x = match.x + Math.max(1, match.width / 2);
            int y = match.y + Math.max(1, match.height / 2);
            performTap(x, y, clicked -> {
                if (!clicked || !smartTaskActive || !externalAgentCycleInFlight) {
                    externalVisualComposerTapped = false;
                    handler.postDelayed(() -> waitForExternalComposer(Math.max(attempt + 1, externalComposerPolls)), 900L);
                    return;
                }
                handler.postDelayed(() -> {
                    if (!smartTaskActive || !externalAgentCycleInFlight) return;
                    boolean inserted = setExternalComposerText(externalPrompt);
                    ExternalUiSnapshot check = readExternalUiSnapshot();
                    boolean verified = inserted && externalPromptFullyPresentInComposer(check.editableText);
                    if (verified) {
                        externalVisualComposerTapped = true;
                        externalVisualPromptInjected = true;
                        externalPromptInjectedIntoComposer = true;
                        externalComposerStateFingerprint = "";
                        externalComposerLastProgressAt = System.currentTimeMillis();
                        handler.postDelayed(() -> waitForExternalComposer(Math.max(attempt + 1, externalComposerPolls)), 800L);
                    } else {
                        pasteExternalPromptAtVisualComposer(x, y, 0, attempt);
                    }
                }, 450L);
            });
        });
    }

    private void pasteExternalPromptAtVisualComposer(int x, int y, int pasteAttempt, int composerAttempt) {
        if (!smartTaskActive || !externalAgentCycleInFlight) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            externalVisualComposerTapped = false;
            handler.postDelayed(() -> waitForExternalComposer(Math.max(composerAttempt + 1, externalComposerPolls)), 1000L);
            return;
        }
        try { clipboard.setPrimaryClip(ClipData.newPlainText("Botna AI instructions", externalPrompt)); }
        catch (Exception ignored) { }
        performLongPress(x, y, 650, pressed -> {
            if (!pressed || !smartTaskActive || !externalAgentCycleInFlight) {
                retryVisualComposerPaste(x, y, pasteAttempt, composerAttempt);
                return;
            }
            handler.postDelayed(() -> {
                AccessibilityNodeInfo root = null;
                boolean pasted = false;
                try {
                    root = getRootInActiveWindow();
                    if (root != null && root.getPackageName() != null && externalAgentPackage.equals(root.getPackageName().toString())) {
                        pasted = findAndClick(root, "Paste", false) || findAndClick(root, "لصق", false)
                                || findAndClick(root, "Colar", false) || findAndClick(root, "Coller", false);
                    }
                } catch (Exception ignored) {
                } finally { if (root != null) root.recycle(); }
                if (pasted) {
                    handler.postDelayed(() -> {
                        if (!smartTaskActive || !externalAgentCycleInFlight) return;
                        ExternalUiSnapshot check = readExternalUiSnapshot();
                        boolean promptVerified = externalPromptFullyPresentInComposer(check.editableText);
                        if (promptVerified || !check.hasEditable) {
                            externalVisualComposerTapped = true;
                            externalVisualPromptInjected = true;
                            externalPromptInjectedIntoComposer = true;
                            externalComposerStateFingerprint = "";
                            externalComposerLastProgressAt = System.currentTimeMillis();
                            handler.postDelayed(() -> waitForExternalComposer(Math.max(composerAttempt + 1, externalComposerPolls)), 850L);
                        } else retryVisualComposerPaste(x, y, pasteAttempt, composerAttempt);
                    }, 650L);
                } else retryVisualComposerPaste(x, y, pasteAttempt, composerAttempt);
            }, 500L);
        });
    }

    private void retryVisualComposerPaste(int x, int y, int pasteAttempt, int composerAttempt) {
        externalVisualComposerTapped = false;
        if (pasteAttempt < 5) {
            handler.postDelayed(() -> searchAndFocusExternalComposer(composerAttempt + pasteAttempt + 1), 1000L);
        } else {
            handler.postDelayed(() -> waitForExternalComposer(Math.max(composerAttempt + 1, externalComposerPolls)), 1200L);
        }
    }

    private void probeExternalVisualSendButton(Runnable completion) {
        if (externalVisualSendSearchInFlight || !smartTaskActive || !externalAgentCycleInFlight) {
            if (completion != null) handler.postDelayed(completion, 500L);
            return;
        }
        externalVisualSendSearchInFlight = true;
        findExternalVisualTemplate(BotnaExternalAppAgent.TEMPLATE_SEND, (match, error) -> {
            externalVisualSendSearchInFlight = false;
            if (!smartTaskActive || !externalAgentCycleInFlight) return;
            if (match == null) {
                externalVisualSendButtonMatched = false;
                externalVisualSendButtonBounds.setEmpty();
                externalVisualSendButtonScore = 0;
                externalVisualSendButtonMatchedAt = 0L;
            } else {
                externalVisualSendButtonMatched = true;
                externalVisualSendButtonBounds.set(match.x, match.y, match.x + match.width, match.y + match.height);
                externalVisualSendButtonScore = match.score;
                externalVisualSendButtonMatchedAt = System.currentTimeMillis();
            }
            if (completion != null) handler.postDelayed(completion, 180L);
        });
    }

    private boolean externalVisualSendButtonReady() {
        return externalVisualSendButtonMatched && !externalVisualSendButtonBounds.isEmpty()
                && externalVisualSendButtonScore >= BotnaExternalAppAgent.visualThreshold(this, externalAgentPackage)
                && System.currentTimeMillis() - externalVisualSendButtonMatchedAt <= 2600L;
    }

    private void waitForExternalComposer(int attempt) {
        if (!smartTaskActive || !externalAgentCycleInFlight || externalSendRetryScheduled || externalSendAttemptInProgress) return;
        externalComposerPolls = attempt + 1;
        ExternalUiSnapshot snapshot = readExternalUiSnapshot();
        long now = System.currentTimeMillis();
        String fingerprint = externalUiFingerprint(snapshot);
        if (advanceWhatsAppMetaAiShareFlow(snapshot)) {
            handler.postDelayed(() -> waitForExternalComposer(attempt + 1), 800L);
            return;
        }
        boolean whatsappShareNotAtComposer = BotnaExternalAppAgent.META_AI_WHATSAPP.equals(externalAgentPackage)
                && BotnaExternalAppAgent.META_AI_WHATSAPP.equals(snapshot.packageName)
                && (!snapshot.hasEditable || !snapshot.sendButtonFound)
                && !externalPromptFullyPresentInComposer(snapshot.editableText);
        if (whatsappShareNotAtComposer) {
            externalWhatsAppNavigationAttempts++;
            if (externalWhatsAppNavigationAttempts >= 24) {
                failExternalAgent("فتح Botna مشاركة WhatsApp، لكنه لم يستطع الوصول تلقائيًا إلى محرر محادثة Meta AI. لم ألصق التعليمات في مربع البحث أو جهة اتصال خاطئة. افتح محادثة Meta AI داخل WhatsApp وتأكد من ظهور خيار Meta AI، ثم أعد المهمة.");
                return;
            }
            handler.postDelayed(() -> waitForExternalComposer(attempt + 1), 700L);
            return;
        }
        if (!fingerprint.equals(externalComposerStateFingerprint)) {
            externalComposerStateFingerprint = fingerprint;
            externalComposerLastProgressAt = now;
        } else if (externalComposerLastProgressAt <= 0L) {
            externalComposerLastProgressAt = now;
        }
        boolean composerShowsActiveUpload = snapshot.uploadBusy
                || (snapshot.sendButtonFound && !snapshot.sendButtonEnabled)
                || (BotnaExternalAppAgent.DEEPSEEK.equals(externalAgentPackage)
                    && (!externalSendVisualStateKnown || !externalSendButtonLooksBlue));
        long composerNoProgressLimit = composerShowsActiveUpload ? EXTERNAL_NO_PROGRESS_ACTIVE_MS : EXTERNAL_NO_PROGRESS_IDLE_MS;
        boolean visualComposerWait = BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_COMPOSER)
                && !externalVisualPromptInjected;
        boolean visualSendWait = BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND)
                && externalPromptInjectedIntoComposer && !snapshot.uploadBusy && !snapshot.generating && !externalVisualSendButtonReady();
        if (now - externalComposerLastProgressAt >= composerNoProgressLimit && !visualComposerWait && !visualSendWait) {
            failExternalAgent("توقفت المهمة بأمان لأن واجهة " + BotnaExternalAppAgent.label(externalAgentPackage)
                    + " لم تُظهر تقدمًا جديدًا لمدة " + (composerShowsActiveUpload ? "15 دقيقة رغم بقاء الرفع/زر الإرسال غير جاهز" : "5 دقائق دون أي مؤشر تقدم")
                    + ". لم أفترض اكتمال الرفع ولم أضغط إرسالًا تخمينيًا. تحقق من الشبكة أو تسجيل الدخول أو حالة المرفق، ثم استأنف المهمة.");
            return;
        }
        if (externalSendVisualProbeInFlight) {
            handler.postDelayed(() -> waitForExternalComposer(externalComposerPolls), 900L);
            return;
        }
        boolean rightPackage = externalAgentPackage.equals(snapshot.packageName);
        String editablePromptText = snapshot.editableText == null ? "" : snapshot.editableText;
        boolean promptVisible = externalVisualPromptInjected || externalPromptFullyPresentInComposer(editablePromptText);
        if (rightPackage && BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_COMPOSER)
                && !promptVisible && !externalVisualComposerSearchInFlight && !externalVisualComposerTapped) {
            searchAndFocusExternalComposer(attempt);
            return;
        }
        if (rightPackage && BotnaExternalAppAgent.GEMINI.equals(externalAgentPackage)
                && !promptVisible && externalGeminiComposerFocusAttempts < 10 && attempt < 90) {
            externalGeminiComposerFocusAttempts++;
            focusGeminiComposer();
            handler.postDelayed(() -> {
                if (!smartTaskActive || !externalAgentCycleInFlight) return;
                ExternalUiSnapshot focusedSnapshot = readExternalUiSnapshot();
                if (BotnaExternalAppAgent.GEMINI.equals(focusedSnapshot.packageName) && focusedSnapshot.hasEditable) {
                    externalPromptInjectionAttempts++;
                    setExternalComposerText(externalPrompt);
                }
                waitForExternalComposer(attempt + 1);
            }, 650L + Math.min(700L, externalGeminiComposerFocusAttempts * 60L));
            return;
        }
        if (rightPackage && snapshot.hasEditable && promptVisible) externalPromptInjectedIntoComposer = true;
        if (rightPackage && snapshot.hasEditable && !promptVisible && !externalPrompt.trim().isEmpty()
                && externalPromptInjectionAttempts < 8 && attempt < 90) {
            externalPromptInjectionAttempts++;
            setExternalComposerText(externalPrompt);
            externalComposerStateFingerprint = "";
            externalComposerLastProgressAt = now;
            handler.postDelayed(() -> waitForExternalComposer(attempt + 1), 1000L);
            return;
        }
        if (rightPackage && snapshot.hasEditable && !promptVisible && externalPromptInjectionAttempts >= 8) {
            failExternalAgent("لم أتمكن من إدراج والتحقق من تعليمات Botna كاملة داخل محرر "
                    + BotnaExternalAppAgent.label(externalAgentPackage)
                    + " بعد ثماني محاولات. أوقفت الإرسال حتى لا يصل الفيديو أو الصورة بلا تعليمات أو أبعاد الجهاز وقواعد JSON.");
            return;
        }
        if (rightPackage && attempt >= 80 && !promptVisible) {
            failExternalAgent("لم يظهر محرر رسالة يمكن التحقق من وجود تعليمات Botna الكاملة فيه داخل "
                    + BotnaExternalAppAgent.label(externalAgentPackage)
                    + ". لم أرسل المرفق منفردًا ولم أتابع المهمة بلا تعليمات.");
            return;
        }
        boolean composerAvailable = rightPackage && (snapshot.hasEditable || externalVisualPromptInjected) && promptVisible && externalPromptInjectedIntoComposer;
        boolean hasVisualSendTemplate = BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND);
        if (rightPackage && hasVisualSendTemplate && composerAvailable && !snapshot.uploadBusy && !snapshot.generating
                && (!externalVisualSendButtonReady() || now - externalVisualSendButtonMatchedAt >= 1800L)) {
            probeExternalVisualSendButton(() -> waitForExternalComposer(externalComposerPolls));
            return;
        }
        boolean visualButtonCanSend = hasVisualSendTemplate && externalVisualSendButtonReady();
        boolean buttonCanSend = (snapshot.sendButtonFound && snapshot.sendButtonEnabled) || visualButtonCanSend;
        boolean readyWithoutColor = composerAvailable && !snapshot.uploadBusy && !snapshot.generating && buttonCanSend;
        if (rightPackage && BotnaExternalAppAgent.DEEPSEEK.equals(externalAgentPackage) && !hasVisualSendTemplate
                && snapshot.sendButtonFound && now - externalSendVisualProbeAt >= EXTERNAL_SEND_VISUAL_PROBE_INTERVAL_MS) {
            probeExternalSendButtonColor(snapshot.sendButtonBounds);
            handler.postDelayed(() -> waitForExternalComposer(externalComposerPolls), 1000L);
            return;
        }
        boolean colorReady = BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND)
                || !BotnaExternalAppAgent.DEEPSEEK.equals(externalAgentPackage)
                || (externalSendVisualStateKnown && externalSendButtonLooksBlue);
        boolean ready = readyWithoutColor && colorReady;
        String stableFingerprint = fingerprint + "|ready=" + ready + "|visualKnown=" + externalSendVisualStateKnown
                + "|visualBlue=" + externalSendButtonLooksBlue + "|templateSend=" + visualButtonCanSend + "|templateScore=" + externalVisualSendButtonScore;
        if (ready) {
            if (stableFingerprint.equals(externalLastCandidate)) externalStablePolls++;
            else { externalLastCandidate = stableFingerprint; externalStablePolls = 1; }
            if (externalStablePolls >= 3 && now - externalCycleStartedAt >= 4200L) {
                ExternalUiSnapshot latest = readExternalUiSnapshot();
                boolean latestVisualReady = BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND)
                        && externalVisualSendButtonReady();
                boolean stillReady = externalAgentPackage.equals(latest.packageName)
                        && (latest.hasEditable || externalVisualPromptInjected)
                        && ((latest.sendButtonFound && latest.sendButtonEnabled) || latestVisualReady)
                        && !latest.uploadBusy && !latest.generating;
                boolean hasVisualSend = BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND);
                if (BotnaExternalAppAgent.DEEPSEEK.equals(externalAgentPackage) && !hasVisualSend) {
                    stillReady = stillReady && externalSendVisualStateKnown && externalSendButtonLooksBlue;
                }
                if (stillReady && externalSendAttempts < BotnaExternalAppAgent.sendMaxAttempts(this, externalAgentPackage)
                        && (externalSendAttempts == 0 || externalSendRetryAwaitingReady)) {
                    externalLastCandidate = "";
                    externalStablePolls = 0;
                    externalSendRetryAwaitingReady = false;
                    long waitBeforeSend = BotnaExternalAppAgent.sendWaitMs(this, externalAgentPackage);
                    scheduleExternalSendAttempt(waitBeforeSend);
                    return;
                }
            }
        } else {
            externalStablePolls = 0;
            externalLastCandidate = stableFingerprint;
        }
        handler.postDelayed(() -> waitForExternalComposer(attempt + 1), nextExternalPollDelay(attempt, snapshot.uploadBusy, false));
    }

    private void scheduleExternalSendAttempt(long delayMs) {
        if (!smartTaskActive || !externalAgentCycleInFlight || externalSendRetryScheduled || externalSendAttemptInProgress) return;
        if (externalSendAttempts >= BotnaExternalAppAgent.sendMaxAttempts(this, externalAgentPackage)) {
            failExternalAgent("تعذر إرسال لقطة الشاشة بعد " + externalSendAttempts + " محاولات. لم أتابع المهمة اعتمادًا على رسالة لم يتم إرسالها.");
            return;
        }
        externalSendRetryScheduled = true;
        final String scheduledTaskId = smartTaskId;
        final String scheduledPackage = externalAgentPackage;
        final long scheduledCycleStartedAt = externalCycleStartedAt;
        handler.postDelayed(() -> {
            if (!smartTaskActive || !externalAgentCycleInFlight || !scheduledTaskId.equals(smartTaskId)
                    || !scheduledPackage.equals(externalAgentPackage) || scheduledCycleStartedAt != externalCycleStartedAt) return;
            externalSendRetryScheduled = false;
            attemptExternalSend();
        }, Math.max(0L, delayMs));
    }

    private void attemptExternalSend() {
        if (!smartTaskActive || !externalAgentCycleInFlight || externalSendAttemptInProgress) return;
        externalSendAttemptInProgress = true;
        ExternalUiSnapshot snapshot = readExternalUiSnapshot();
        boolean visualSendReady = BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND)
                && externalVisualSendButtonReady();
        if (!externalAgentPackage.equals(snapshot.packageName) || !(snapshot.hasEditable || externalVisualPromptInjected)
                || (!(snapshot.sendButtonFound && snapshot.sendButtonEnabled) && !visualSendReady)
                || snapshot.uploadBusy || snapshot.generating) {
            externalSendAttemptInProgress = false;
            externalSendRetryAwaitingReady = true;
            handler.postDelayed(() -> waitForExternalComposer(externalComposerPolls), 1100L);
            return;
        }
        if (BotnaExternalAppAgent.DEEPSEEK.equals(externalAgentPackage)
                && !BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND)) {
            externalSendVisualStateKnown = false;
            externalSendButtonLooksBlue = false;
            probeExternalSendButtonColor(snapshot.sendButtonBounds, this::continueExternalSendAfterColorCheck);
            return;
        }
        continueExternalSendAfterColorCheck();
    }

    private void continueExternalSendAfterColorCheck() {
        if (!smartTaskActive || !externalAgentCycleInFlight || !externalSendAttemptInProgress) return;
        ExternalUiSnapshot latest = readExternalUiSnapshot();
        boolean visualSendReady = BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND)
                && externalVisualSendButtonReady();
        boolean ready = externalAgentPackage.equals(latest.packageName) && (latest.hasEditable || externalVisualPromptInjected)
                && ((latest.sendButtonFound && latest.sendButtonEnabled) || visualSendReady)
                && !latest.uploadBusy && !latest.generating;
        if (BotnaExternalAppAgent.DEEPSEEK.equals(externalAgentPackage)
                && !BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND)) {
            ready = ready && externalSendVisualStateKnown && externalSendButtonLooksBlue;
        }
        if (!ready) {
            externalSendAttemptInProgress = false;
            externalSendRetryAwaitingReady = true;
            handler.postDelayed(() -> waitForExternalComposer(externalComposerPolls), 1100L);
            return;
        }
        externalSendAttempts++;
        externalBaselineUi = latest.text;
        externalBaselineEditableText = latest.editableText;
        externalBaselineHadEditable = latest.hasEditable;
        externalBaselineValues = new ArrayList<>(latest.values);
        externalClipboardBeforeCopy = readClipboardTextSafely();
        externalClipboardTimestampBeforeCopy = readClipboardTimestampSafely();
        externalLastCandidate = "";
        externalStablePolls = 0;
        if (!clickExternalSendButton()) {
            externalSendAttemptFailed("تعذر الضغط على زر الإرسال في المحاولة رقم " + externalSendAttempts + ".");
            return;
        }
        final int attemptNumber = externalSendAttempts;
        final String taskId = smartTaskId;
        final String packageName = externalAgentPackage;
        handler.postDelayed(() -> verifyExternalSendAccepted(attemptNumber, taskId, packageName), 1800L);
    }

    private void verifyExternalSendAccepted(int attemptNumber, String taskId, String packageName) {
        if (!smartTaskActive || !externalAgentCycleInFlight || !taskId.equals(smartTaskId)
                || !packageName.equals(externalAgentPackage) || attemptNumber != externalSendAttempts) return;
        ExternalUiSnapshot snapshot = readExternalUiSnapshot();
        String promptMarker = externalPrompt.substring(0, Math.min(48, externalPrompt.length()));
        boolean promptWasInComposer = !externalPrompt.isEmpty() && !externalBaselineEditableText.isEmpty() && externalBaselineEditableText.contains(promptMarker);
        boolean promptStillInComposer = promptWasInComposer && snapshot.editableText.contains(promptMarker);
        if (packageName.equals(snapshot.packageName) && snapshot.uploadBusy && !snapshot.generating) {
            handler.postDelayed(() -> verifyExternalSendAccepted(attemptNumber, taskId, packageName), 1600L);
            return;
        }
        boolean accepted = packageName.equals(snapshot.packageName)
                && (snapshot.generating || (externalBaselineHadEditable && !snapshot.hasEditable)
                    || (externalBaselineHadEditable && !externalBaselineEditableText.trim().isEmpty() && snapshot.editableText.trim().isEmpty())
                    || (snapshot.sendButtonFound && !snapshot.sendButtonEnabled)
                    || !snapshot.text.equals(externalBaselineUi) || (promptWasInComposer && !promptStillInComposer));
        if (accepted) {
            externalSendAttemptInProgress = false;
            externalSendRetryAwaitingReady = false;
            externalResponsePolls = 0;
            externalResponseStartedAt = System.currentTimeMillis();
            externalResponseLastProgressAt = externalResponseStartedAt;
            externalResponseStateFingerprint = "";
            externalResponseNoChangeWaitIndex = 0;
            externalResponseReadAttempts = 1;
            externalLastCandidate = "";
            externalStablePolls = 0;
            handler.postDelayed(() -> pollExternalAssistantResponse(0), BotnaExternalAppAgent.responseWaitMs(this, externalAgentPackage));
            return;
        }
        externalSendAttemptFailed("لم تظهر علامة قبول للإرسال بعد الضغط في المحاولة رقم " + attemptNumber + ".");
    }

    private void externalSendAttemptFailed(String detail) {
        externalSendAttemptInProgress = false;
        BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "external_send_retry", smartTaskActionCount,
                "إعادة محاولة إرسال لقطة الشاشة", detail, false, null);
        int maxAttempts = BotnaExternalAppAgent.sendMaxAttempts(this, externalAgentPackage);
        if (externalSendAttempts >= maxAttempts) {
            failExternalAgent("فشل التأكد من إرسال لقطة الشاشة بعد " + externalSendAttempts + " محاولات. " + detail
                    + " لم أنفذ أي إجراء بناءً على رد غير مؤكد.");
            return;
        }
        long retryDelay = Math.min(15000L, 1500L + (long) externalSendAttempts * 2500L);
        externalSendRetryAwaitingReady = true;
        handler.postDelayed(() -> {
            if (smartTaskActive && externalAgentCycleInFlight && !externalSendAttemptInProgress && !externalSendRetryScheduled) {
                externalVisualSendButtonMatched = false;
                externalVisualSendButtonBounds.setEmpty();
                waitForExternalComposer(externalComposerPolls);
            }
        }, retryDelay);
    }

    private long nextExternalPollDelay(int attempt, boolean activeSignal, boolean responseStage) {
        if (activeSignal) return 1400L;
        if (attempt < 12) return 850L;
        long delay = 900L + Math.min(8, Math.max(0, attempt - 12) / 5) * 400L;
        return Math.min(responseStage ? 5000L : 4200L, delay);
    }

    private String externalUiFingerprint(ExternalUiSnapshot snapshot) {
        if (snapshot == null) return "<null>";
        return snapshot.packageName + "|" + snapshot.text + "|edit=" + snapshot.hasEditable
                + "|upload=" + snapshot.uploadBusy + "|generating=" + snapshot.generating
                + "|sendFound=" + snapshot.sendButtonFound + "|sendEnabled=" + snapshot.sendButtonEnabled
                + "|sendId=" + snapshot.sendButtonViewId + "|sendVisualKnown=" + externalSendVisualStateKnown
                + "|sendVisualBlue=" + externalSendButtonLooksBlue;
    }

    private void probeExternalSendButtonColor(Rect screenBounds) {
        probeExternalSendButtonColor(screenBounds, null);
    }

    private void probeExternalSendButtonColor(Rect screenBounds, Runnable completion) {
        if (externalSendVisualProbeInFlight) {
            if (completion != null) handler.postDelayed(completion, 500L);
            return;
        }
        if (screenBounds == null || screenBounds.isEmpty()) {
            externalSendVisualStateKnown = false;
            externalSendButtonLooksBlue = false;
            if (completion != null) handler.post(completion);
            return;
        }
        externalSendVisualProbeInFlight = true;
        externalSendVisualProbeAt = System.currentTimeMillis();
        final int probeGeneration = externalVisualProbeGeneration;
        final String probeTaskId = smartTaskId;
        final String probePackage = externalAgentPackage;
        Rect bounds = new Rect(screenBounds);
        takeScreenshotForStateProbe((bitmap, error) -> {
            if (probeGeneration != externalVisualProbeGeneration || !smartTaskActive || !externalAgentCycleInFlight
                    || !probeTaskId.equals(smartTaskId) || !probePackage.equals(externalAgentPackage)) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                return;
            }
            try {
                if (bitmap != null && !bitmap.isRecycled()) {
                    externalSendButtonLooksBlue = isBlueSendButton(bitmap, bounds);
                    externalSendVisualStateKnown = true;
                } else {
                    externalSendVisualStateKnown = false;
                    externalSendButtonLooksBlue = false;
                }
            } catch (Exception ignored) {
                externalSendVisualStateKnown = false;
                externalSendButtonLooksBlue = false;
            } finally {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                externalSendVisualProbeInFlight = false;
            }
            if (completion != null) handler.post(completion);
        });
    }

    private boolean isBlueSendButton(Bitmap screenshot, Rect screenBounds) {
        if (screenshot == null || screenshot.isRecycled() || screenBounds == null || screenBounds.isEmpty()) return false;
        android.util.DisplayMetrics metrics = realScreenMetrics();
        float sx = metrics.widthPixels > 0 ? screenshot.getWidth() / (float) metrics.widthPixels : 1f;
        float sy = metrics.heightPixels > 0 ? screenshot.getHeight() / (float) metrics.heightPixels : 1f;
        int left = Math.max(0, Math.min(screenshot.getWidth() - 1, Math.round(screenBounds.left * sx)));
        int right = Math.max(left + 1, Math.min(screenshot.getWidth(), Math.round(screenBounds.right * sx)));
        int top = Math.max(0, Math.min(screenshot.getHeight() - 1, Math.round(screenBounds.top * sy)));
        int bottom = Math.max(top + 1, Math.min(screenshot.getHeight(), Math.round(screenBounds.bottom * sy)));
        int width = Math.max(1, right - left), height = Math.max(1, bottom - top);
        int colorful = 0;
        int samples = 0;
        float[] fractions = new float[]{0.18f, 0.34f, 0.50f, 0.66f, 0.82f};
        for (float fy : fractions) {
            for (float fx : fractions) {
                int px = Math.min(screenshot.getWidth() - 1, left + Math.round((width - 1) * fx));
                int py = Math.min(screenshot.getHeight() - 1, top + Math.round((height - 1) * fy));
                int color = screenshot.getPixel(px, py);
                int red = Color.red(color), green = Color.green(color), blue = Color.blue(color);
                boolean blueTone = blue > 78 && blue - red > 24 && (green - red > 8 || blue - green > 6);
                if (blueTone) colorful++;
                samples++;
            }
        }
        return samples > 0 && colorful >= Math.max(4, samples / 5);
    }

    private static final class ExternalUiSnapshot {
        String packageName = "";
        String text = "";
        String editableText = "";
        boolean hasEditable;
        boolean uploadBusy;
        boolean generating;
        boolean sendButtonFound;
        boolean sendButtonEnabled;
        String sendButtonViewId = "";
        int sendButtonScore = Integer.MIN_VALUE;
        int editableNodeScore = Integer.MIN_VALUE;
        final Rect editableBounds = new Rect();
        final Rect sendButtonBounds = new Rect();
        int nodeCount;
        ArrayList<String> values = new ArrayList<>();
    }

    private ExternalUiSnapshot readExternalUiSnapshot() {
        ExternalUiSnapshot result = new ExternalUiSnapshot();
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root == null) return result;
            CharSequence pkg = root.getPackageName();
            result.packageName = pkg == null ? "" : pkg.toString();
            collectExternalUi(root, result, 0);
            AccessibilityNodeInfo composer = findExternalComposerNode(root);
            if (composer != null) {
                try {
                    CharSequence composerText = composer.getText();
                    result.hasEditable = true;
                    result.editableText = composerText == null ? "" : composerText.toString();
                    composer.getBoundsInScreen(result.editableBounds);
                } finally {
                    composer.recycle();
                }
            }
            SendNodeCandidate sendCandidate = new SendNodeCandidate();
            collectExternalSendCandidate(root, 0, sendCandidate, result.editableBounds, true);
            if (sendCandidate.node != null && (!result.sendButtonFound || sendCandidate.score >= 100000)) {
                result.sendButtonFound = true;
                result.sendButtonEnabled = sendCandidate.node.isEnabled() && sendCandidate.node.isVisibleToUser();
                Rect bounds = new Rect();
                sendCandidate.node.getBoundsInScreen(bounds);
                result.sendButtonBounds.set(bounds);
                result.sendButtonScore = sendCandidate.score;
                try { result.sendButtonViewId = String.valueOf(sendCandidate.node.getViewIdResourceName()); } catch (Exception ignored) { }
            }
            if (sendCandidate.node != null) sendCandidate.node.recycle();
            StringBuilder joined = new StringBuilder();
            for (String value : result.values) {
                if (value == null || value.trim().isEmpty()) continue;
                if (joined.length() > 0) joined.append("\n");
                joined.append(value.trim());
            }
            result.text = joined.toString();
            String lower = result.text.toLowerCase(Locale.ROOT);
            result.uploadBusy = result.uploadBusy || lower.contains("uploading") || lower.contains("upload in progress")
                    || lower.contains("uploading image") || lower.contains("processing image") || lower.contains("جاري الرفع")
                    || lower.contains("جار الرفع") || lower.contains("تحميل الصورة") || lower.contains("جاري تحميل الصورة")
                    || lower.contains("جارٍ تحميل الصورة") || lower.contains("إرفاق الصورة جار") || lower.contains("جارٍ إرفاق")
                    || lower.contains("processing attachment") || lower.contains("preparing image");
            result.generating = result.generating || lower.contains("stop generating") || lower.contains("stop response")
                    || lower.contains("إيقاف التوليد") || lower.contains("إيقاف الإجابة") || lower.contains("إيقاف الرد")
                    || lower.contains("إلغاء التوليد") || lower.contains("cancel response") || lower.contains("generating response")
                    || lower.contains("stop stream");
            if (!result.sendButtonFound && BotnaExternalAppAgent.GEMINI.equals(externalAgentPackage)
                    && result.hasEditable && externalPromptFullyPresentInComposer(result.editableText)
                    && !result.uploadBusy && !result.generating && !result.editableBounds.isEmpty()) {
                android.util.DisplayMetrics metrics = realScreenMetrics();
                if (metrics.widthPixels > 0 && metrics.heightPixels > 0
                        && result.editableBounds.centerY() >= metrics.heightPixels * 0.48f) {
                    int buttonX = BotnaExternalAppAgent.GEMINI.equals(externalAgentPackage)
                            ? Math.max(dp(28), Math.round(metrics.widthPixels * 0.16f))
                            : Math.max(1, metrics.widthPixels - dp(42));
                    int buttonY = Math.max(dp(48), Math.min(metrics.heightPixels - dp(44),
                            Math.round(metrics.heightPixels * 0.88f)));
                    Rect estimatedSendBounds = new Rect(buttonX - dp(18), buttonY - dp(18), buttonX + dp(18), buttonY + dp(18));
                    if (!estimatedSendBounds.isEmpty()) {
                        result.sendButtonFound = true;
                        result.sendButtonEnabled = true;
                        result.sendButtonViewId = "botna_send_coordinate_fallback";
                        result.sendButtonScore = 1;
                        result.sendButtonBounds.set(estimatedSendBounds);
                    }
                }
            }
            return result;
        } catch (Exception ignored) { return result; }
        finally { if (root != null) root.recycle(); }
    }

    private boolean hasStructuredTextDescendant(AccessibilityNodeInfo node, String parentText, int depth) {
        if (node == null || depth > 35 || parentText == null || parentText.length() < 12) return false;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try {
                CharSequence childTextValue = child.getText();
                CharSequence childDescriptionValue = child.getContentDescription();
                String childText = childTextValue == null ? "" : childTextValue.toString().trim();
                String childDescription = childDescriptionValue == null ? "" : childDescriptionValue.toString().trim();
                String normalizedParent = normalizeStructuredText(parentText);
                if (isStructuredJsonFragment(childText) && normalizedParent.contains(normalizeStructuredText(childText))) return true;
                if (!childDescription.equals(childText) && isStructuredJsonFragment(childDescription)
                        && normalizedParent.contains(normalizeStructuredText(childDescription))) return true;
                if (hasStructuredTextDescendant(child, parentText, depth + 1)) return true;
            } finally {
                child.recycle();
            }
        }
        return false;
    }

    private String normalizeStructuredText(String value) {
        if (value == null) return "";
        return value.replace(String.valueOf((char) 0x200E), "")
                .replace(String.valueOf((char) 0x200F), "")
                .replace(String.valueOf((char) 0x202A), "")
                .replace(String.valueOf((char) 0x202B), "")
                .replace(String.valueOf((char) 0x202C), "")
                .replace(String.valueOf((char) 0x2066), "")
                .replace(String.valueOf((char) 0x2067), "")
                .replace(String.valueOf((char) 0x2068), "")
                .replace(String.valueOf((char) 0x2069), "")
                .replaceAll("\\s+", "");
    }

    private boolean isStructuredJsonFragment(String value) {
        if (value == null) return false;
        String clean = value.trim();
        if (clean.isEmpty() || clean.length() > 1200) return false;
        if (clean.equals("{") || clean.equals("}") || clean.equals("[") || clean.equals("]")) return true;
        if (clean.length() < 3) return false;
        return clean.contains("\"action\"") || clean.contains("\"start_x\"")
                || clean.contains("\"end_x\"") || clean.contains("\"duration\"")
                || clean.contains("\"duration_ms\"") || (clean.startsWith("\"") && clean.contains(":"));
    }

    private void collectExternalUi(AccessibilityNodeInfo node, ExternalUiSnapshot snapshot, int depth) {
        collectExternalUi(node, snapshot, depth, false);
    }

    private void collectExternalUi(AccessibilityNodeInfo node, ExternalUiSnapshot snapshot, int depth, boolean insideEditable) {
        if (node == null || depth > 35 || snapshot.nodeCount++ > 1600) return;
        boolean editableNode = node.isEditable() && node.isEnabled();
        if (editableNode) {
            snapshot.hasEditable = true;
            CharSequence value = node.getText();
            Rect editableBounds = new Rect();
            node.getBoundsInScreen(editableBounds);
            android.util.DisplayMetrics metrics = realScreenMetrics();
            int editableScore = editableBounds.centerY() + editableBounds.width() / 3
                    + (node.isFocused() ? 800 : 0)
                    - (metrics.heightPixels > 0 && editableBounds.centerY() < metrics.heightPixels * 0.42f ? 500 : 0);
            if (!editableBounds.isEmpty() && editableScore > snapshot.editableNodeScore) {
                snapshot.editableNodeScore = editableScore;
                snapshot.editableBounds.set(editableBounds);
            }
            if (value != null && value.length() > snapshot.editableText.length()) snapshot.editableText = value.toString();
        }
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String primary = text == null ? "" : text.toString().trim();
        String description = desc == null ? "" : desc.toString().trim();
        String promptMarker = externalPrompt == null ? "" : externalPrompt.substring(0, Math.min(64, externalPrompt.length()));
        boolean currentPromptNode = !promptMarker.isEmpty() && primary.contains(promptMarker);
        boolean structuredParent = !currentPromptNode && (primary.contains("\"action\"") || primary.contains("\"start_x\"")
                || primary.contains("\"end_x\"") || primary.startsWith("[") || primary.startsWith("{"));
        boolean hasStructuredChild = structuredParent && hasStructuredTextDescendant(node, primary, 0);
        if (!insideEditable && !primary.isEmpty() && (currentPromptNode || !hasStructuredChild) && snapshot.values.size() < 240) snapshot.values.add(primary);
        if (!insideEditable && !description.isEmpty() && !description.equals(primary) && !hasStructuredChild && !currentPromptNode && snapshot.values.size() < 240) snapshot.values.add(description);
        String viewId = "";
        String className = "";
        try { viewId = String.valueOf(node.getViewIdResourceName()).toLowerCase(Locale.ROOT); } catch (Exception ignored) { }
        try { className = String.valueOf(node.getClassName()).toLowerCase(Locale.ROOT); } catch (Exception ignored) { }
        String signal = (primary + " " + description + " " + viewId + " " + className).toLowerCase(Locale.ROOT);
        if (containsAny(signal, "stop_generating", "stop-generating", "stop response", "cancel_generation", "cancel-response", "إيقاف التوليد", "إيقاف الرد")) snapshot.generating = true;
        if (containsAny(signal, "upload_progress", "upload-progress", "progressbar", "uploading", "processing_image", "image_upload", "جاري الرفع", "جارٍ إرفاق", "تحميل الصورة")) snapshot.uploadBusy = true;
        if (className.contains("progressbar") && node.isVisibleToUser()) {
            snapshot.uploadBusy = true;
            snapshot.generating = true;
        }
        String accessibleLabel = (primary + " " + description).trim();
        boolean semanticSend = containsAny(viewId, "send_button", "sendbutton", "btn_send", "send_message", "submit_button", "send_btn")
                || (accessibleLabel.length() <= 64 && containsAny(accessibleLabel, "send", "إرسال", "ارسال", "إرسال الرسالة", "send message", "send prompt", "submit"));
        if (semanticSend) {
            AccessibilityNodeInfo clickable = obtainClickableAncestor(node);
            if (clickable != null) {
                try {
                    Rect bounds = new Rect(); clickable.getBoundsInScreen(bounds);
                    android.util.DisplayMetrics metrics = realScreenMetrics();
                    int score = 100 + (metrics.heightPixels > 0 && bounds.centerY() > metrics.heightPixels * 0.55f ? 30 : 0)
                            + (clickable.isEnabled() ? 10 : -100) + (clickable.isVisibleToUser() ? 5 : -40);
                    if (!bounds.isEmpty() && score > snapshot.sendButtonScore) {
                        snapshot.sendButtonFound = true;
                        snapshot.sendButtonEnabled = clickable.isEnabled() && clickable.isVisibleToUser();
                        snapshot.sendButtonViewId = viewId;
                        snapshot.sendButtonBounds.set(bounds);
                        snapshot.sendButtonScore = score;
                    }
                } catch (Exception ignored) { }
                finally { clickable.recycle(); }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            try { collectExternalUi(child, snapshot, depth + 1, insideEditable || editableNode || currentPromptNode); }
            finally { child.recycle(); }
        }
    }

    private static final class SendNodeCandidate {
        AccessibilityNodeInfo node;
        int score = Integer.MIN_VALUE;
    }

    private static final class ExternalEditableCandidate {
        AccessibilityNodeInfo node;
        int score = Integer.MIN_VALUE;
    }

    private AccessibilityNodeInfo findExternalComposerNode(AccessibilityNodeInfo root) {
        if (root == null) return null;
        ExternalEditableCandidate best = new ExternalEditableCandidate();
        collectExternalComposerCandidate(root, 0, best);
        return best.node;
    }

    private void collectExternalComposerCandidate(AccessibilityNodeInfo node, int depth, ExternalEditableCandidate best) {
        if (node == null || depth > 35) return;
        try {
            if (node.isEditable() && node.isEnabled() && node.isVisibleToUser()) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                android.util.DisplayMetrics metrics = realScreenMetrics();
                CharSequence textValue = node.getText();
                CharSequence descValue = node.getContentDescription();
                CharSequence hintValue = node.getHintText();
                String value = ((textValue == null ? "" : textValue.toString()) + " "
                        + (descValue == null ? "" : descValue.toString()) + " "
                        + (hintValue == null ? "" : hintValue.toString())).toLowerCase(Locale.ROOT);
                String viewId = "";
                try { viewId = String.valueOf(node.getViewIdResourceName()).toLowerCase(Locale.ROOT); } catch (Exception ignored) { }
                int score = 100 + bounds.centerY() + bounds.width() / 3 + (node.isFocused() ? 900 : 0);
                if (containsAny(value + " " + viewId, "ask gemini", "ask meta", "message", "prompt", "type a message", "اكتب رسالة", "اسأل", "gemini")) score += 450;
                if (containsAny(value + " " + viewId, "search", "بحث", "search history", "search contacts")) score -= 1200;
                if (metrics.heightPixels > 0 && bounds.centerY() < metrics.heightPixels * 0.42f) score -= 700;
                if (!externalPrompt.isEmpty() && textValue != null) {
                    String actual = textValue.toString();
                    String head = externalPrompt.substring(0, Math.min(48, externalPrompt.length()));
                    String tail = externalPrompt.substring(Math.max(0, externalPrompt.length() - Math.min(48, externalPrompt.length())));
                    if (actual.contains(head) && actual.contains(tail)) score += 12000;
                }
                if (!bounds.isEmpty() && score > best.score) {
                    if (best.node != null) best.node.recycle();
                    best.node = AccessibilityNodeInfo.obtain(node);
                    best.score = score;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    if (child != null) collectExternalComposerCandidate(child, depth + 1, best);
                } catch (Exception ignored) {
                } finally {
                    if (child != null) child.recycle();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean clickExternalSendButton() {
        if (BotnaExternalAppAgent.hasVisualTemplate(this, externalAgentPackage, BotnaExternalAppAgent.TEMPLATE_SEND)
                && externalVisualSendButtonReady()) {
            Rect bounds = new Rect(externalVisualSendButtonBounds);
            externalVisualSendButtonMatched = false;
            externalVisualSendButtonMatchedAt = 0L;
            try {
                Path path = new Path();
                path.moveTo(bounds.centerX(), bounds.centerY());
                GestureDescription gesture = new GestureDescription.Builder()
                        .addStroke(new GestureDescription.StrokeDescription(path, 0, 90L)).build();
                return dispatchGesture(gesture, null, handler);
            } catch (Exception ignored) { return false; }
        }
        AccessibilityNodeInfo root = null;
        SendNodeCandidate best = new SendNodeCandidate();
        try {
            root = getRootInActiveWindow();
            if (root == null) return false;
            CharSequence pkg = root.getPackageName();
            if (pkg == null || !externalAgentPackage.equals(pkg.toString())) return false;
            ExternalUiSnapshot snapshot = readExternalUiSnapshot();
            collectExternalSendCandidate(root, 0, best, snapshot.editableBounds, true);
            if (best.node == null) {
                if (!BotnaExternalAppAgent.GEMINI.equals(externalAgentPackage)
                        || !snapshot.sendButtonFound
                        || !"botna_send_coordinate_fallback".equals(snapshot.sendButtonViewId)
                        || !snapshot.sendButtonEnabled || snapshot.sendButtonBounds.isEmpty()) return false;
                Path fallbackPath = new Path();
                fallbackPath.moveTo(snapshot.sendButtonBounds.centerX(), snapshot.sendButtonBounds.centerY());
                GestureDescription fallbackGesture = new GestureDescription.Builder()
                        .addStroke(new GestureDescription.StrokeDescription(fallbackPath, 0, 90L)).build();
                return dispatchGesture(fallbackGesture, null, handler);
            }
            if (!best.node.isEnabled() || !best.node.isVisibleToUser()) return false;
            if (best.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            Rect bounds = new Rect();
            best.node.getBoundsInScreen(bounds);
            if (bounds.isEmpty()) return false;
            Path path = new Path();
            path.moveTo(bounds.centerX(), bounds.centerY());
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 90L)).build();
            return dispatchGesture(gesture, null, handler);
        } catch (Exception ignored) { return false; }
        finally {
            if (root != null) root.recycle();
            if (best.node != null) best.node.recycle();
        }
    }

    private void collectExternalSendCandidate(AccessibilityNodeInfo node, int depth, SendNodeCandidate best) {
        collectExternalSendCandidate(node, depth, best, new Rect(), true);
    }

    private void collectExternalSendCandidate(AccessibilityNodeInfo node, int depth, SendNodeCandidate best,
                                              Rect editableBounds, boolean allowSpatialFallback) {
        if (node == null || depth > 35) return;
        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();
        String value = ((text == null ? "" : text.toString()) + " " + (desc == null ? "" : desc.toString())).toLowerCase(Locale.ROOT).trim();
        String viewId = "";
        String className = "";
        try { viewId = String.valueOf(node.getViewIdResourceName()).toLowerCase(Locale.ROOT); } catch (Exception ignored) { }
        try { className = String.valueOf(node.getClassName()).toLowerCase(Locale.ROOT); } catch (Exception ignored) { }
        boolean semanticSend = containsAny(viewId, "send_button", "sendbutton", "btn_send", "send_message", "submit_button", "send_btn")
                || (value.length() <= 80 && containsAny(value, "send", "إرسال", "ارسال", "إرسال الرسالة", "send message", "send prompt", "submit", "إرسال إلى Gemini", "إرسال إلى جيمناي"));
        AccessibilityNodeInfo clickable = null;
        try {
            clickable = obtainClickableAncestor(node);
            if (clickable != null && clickable.isEnabled() && clickable.isVisibleToUser()) {
                Rect bounds = new Rect();
                clickable.getBoundsInScreen(bounds);
                android.util.DisplayMetrics metrics = realScreenMetrics();
                boolean spatialSend = false;
                if (!semanticSend && allowSpatialFallback && !bounds.isEmpty() && metrics.widthPixels > 0 && metrics.heightPixels > 0) {
                    boolean rightSide = bounds.centerX() >= metrics.widthPixels * 0.62f;
                    boolean geminiRtlSendSide = BotnaExternalAppAgent.GEMINI.equals(externalAgentPackage)
                            && bounds.centerX() <= metrics.widthPixels * 0.24f;
                    boolean lowerArea = bounds.centerY() >= metrics.heightPixels * 0.54f;
                    boolean nearComposer = editableBounds == null || editableBounds.isEmpty()
                            || (bounds.centerY() >= editableBounds.top - Math.max(150, editableBounds.height() * 2)
                            && bounds.centerY() <= editableBounds.bottom + Math.max(170, editableBounds.height() * 2));
                    boolean plausibleControl = node.isClickable() || className.contains("button") || className.contains("imagebutton")
                            || viewId.contains("send") || viewId.contains("submit");
                    boolean expectedSendSide = BotnaExternalAppAgent.GEMINI.equals(externalAgentPackage)
                            ? geminiRtlSendSide : rightSide;
                    spatialSend = expectedSendSide && lowerArea && nearComposer && plausibleControl;
                }
                if (semanticSend || spatialSend) {
                    int positionScore = Math.round(bounds.centerX() * 60f / Math.max(1, metrics.widthPixels))
                            + Math.round(bounds.centerY() * 100f / Math.max(1, metrics.heightPixels))
                            + (editableBounds != null && !editableBounds.isEmpty() ? Math.max(0, 45 - Math.abs(bounds.centerY() - editableBounds.centerY()) / 5) : 0);
                    int score = semanticSend ? 100000 + positionScore : 100 + positionScore;
                    if (score > best.score) {
                        if (best.node != null) best.node.recycle();
                        best.node = AccessibilityNodeInfo.obtain(clickable);
                        best.score = score;
                    }
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (clickable != null) clickable.recycle();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = null;
            try {
                child = node.getChild(i);
                if (child != null) collectExternalSendCandidate(child, depth + 1, best, editableBounds, allowSpatialFallback);
            } catch (Exception ignored) {
            } finally {
                if (child != null) child.recycle();
            }
        }
    }

    private AccessibilityNodeInfo obtainClickableAncestor(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo current = AccessibilityNodeInfo.obtain(node);
        for (int depth = 0; current != null && depth < 8; depth++) {
            if (current.isClickable()) return current;
            AccessibilityNodeInfo parent = current.getParent();
            current.recycle();
            current = parent;
        }
        return null;
    }

    private boolean containsAny(String value, String... terms) {
        if (value == null) return false;
        for (String term : terms) if (value.contains(term.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private void pollExternalAssistantResponse(int attempt) {
        if (!smartTaskActive || !externalAgentCycleInFlight) return;
        externalResponsePolls = attempt + 1;
        ExternalUiSnapshot snapshot = readExternalUiSnapshot();
        String candidate = externalCopiedResponseCandidate.isEmpty() ? extractExternalResponse(snapshot) : externalCopiedResponseCandidate;
        if (BotnaExternalAppAgent.parseAction(candidate, externalScreenshotWidth, externalScreenshotHeight) == null) {
            String clipboardCandidate = readClipboardTextSafely();
            long clipboardTimestamp = readClipboardTimestampSafely();
            boolean clipboardChanged = !clipboardCandidate.isEmpty() && !clipboardCandidate.equals(externalClipboardBeforeCopy)
                    && (clipboardTimestamp <= 0L || externalClipboardTimestampBeforeCopy <= 0L || clipboardTimestamp > externalClipboardTimestampBeforeCopy);
            if (clipboardChanged && BotnaExternalAppAgent.parseAction(clipboardCandidate, externalScreenshotWidth, externalScreenshotHeight) != null) {
                externalCopiedResponseCandidate = clipboardCandidate;
                candidate = clipboardCandidate;
                externalResponseLastProgressAt = System.currentTimeMillis();
            }
        }
        boolean generating = snapshot.generating || snapshot.uploadBusy;
        String progressFingerprint = externalUiFingerprint(snapshot) + "|candidate=" + candidate;
        long now = System.currentTimeMillis();
        boolean progressChanged = !progressFingerprint.equals(externalResponseStateFingerprint);
        if (progressChanged) {
            externalResponseStateFingerprint = progressFingerprint;
            externalResponseLastProgressAt = now;
            externalResponseNoChangeWaitIndex = 0;
        } else if (externalResponseLastProgressAt <= 0L) {
            externalResponseLastProgressAt = now;
        }
        long responseNoProgressLimit = generating ? EXTERNAL_NO_PROGRESS_ACTIVE_MS : EXTERNAL_NO_PROGRESS_IDLE_MS;
        if (now - externalResponseLastProgressAt >= responseNoProgressLimit) {
            failExternalAgent("توقفت المهمة بأمان لأن " + BotnaExternalAppAgent.label(externalAgentPackage)
                    + " لم يُظهر تغيرًا قابلًا للرصد في الرد لمدة " + (generating ? "15 دقيقة رغم ظهور مؤشر التوليد أو التحميل" : "5 دقائق دون مؤشر توليد أو تغير في الرد")
                    + ". لا يوجد حد أقصى ثابت لوقت الاستجابة؛ هذا تنبيه لغياب التقدم فقط. لم أنفذ أمرًا غير مكتمل. تحقق من الشبكة أو حالة الجلسة ثم استأنف المهمة.");
            return;
        }
        if (!externalAgentPackage.equals(snapshot.packageName)) {
            handler.postDelayed(() -> pollExternalAssistantResponse(attempt + 1), nextExternalResponseObservationDelay(false, true));
            return;
        }
        if (candidate.equals(externalLastCandidate)) externalStablePolls++;
        else { externalLastCandidate = candidate; externalStablePolls = 0; }
        JSONObject parsed = BotnaExternalAppAgent.parseAction(candidate, externalScreenshotWidth, externalScreenshotHeight);
        if (parsed == null && !generating && !candidate.isEmpty()) {
            parsed = BotnaExternalAppAgent.parseLatestAction(snapshot.text, externalScreenshotWidth, externalScreenshotHeight);
        }
        if (parsed == null && !generating && !externalCopyAttemptedForResponse
                && externalCodeCopyAttemptsForResponse < 3 && externalResponsePolls >= 5
                && externalStablePolls >= 2
                && (looksLikeExternalJson(candidate) || looksLikeExternalJson(snapshot.text)
                    || snapshot.text.toLowerCase(Locale.ROOT).contains("json")
                    || snapshot.text.toLowerCase(Locale.ROOT).contains("نسخ"))) {
            externalCodeCopyAttemptsForResponse++;
            if (clickLatestExternalCodeCopyButton()) {
                String copiedTaskId = smartTaskId;
                String copiedPackage = externalAgentPackage;
                long copiedCycleStartedAt = externalCycleStartedAt;
                handler.postDelayed(() -> pollCopiedExternalCode(copiedTaskId, copiedPackage, copiedCycleStartedAt, 0), 250L);
                return;
            }
            externalCopyAttemptedForResponse = false;
            handler.postDelayed(() -> pollExternalAssistantResponse(externalResponsePolls), 500L);
            return;
        }
        if (parsed != null && externalResponsePolls >= 5 && externalStablePolls >= 3 && !generating) {
            externalFormatRetries = 0;
            processExternalAssistantAction(parsed);
            return;
        }
        if (candidate.isEmpty() && !generating) {
            if (externalResponseReadAttempts < EXTERNAL_RESPONSE_READ_RETRY_DELAYS_MS.length) {
                int nextReadAttempt = externalResponseReadAttempts + 1;
                long retryDelay = EXTERNAL_RESPONSE_READ_RETRY_DELAYS_MS[nextReadAttempt - 1];
                externalResponseReadAttempts = nextReadAttempt;
                BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "external_response_read_retry", smartTaskActionCount,
                        "إعادة قراءة رد الذكاء الاصطناعي", "لم يظهر رد قابل للقراءة؛ سيعيد Botna فحص واجهة الرد في المحاولة "
                                + nextReadAttempt + " من 3 بعد " + Math.max(1, retryDelay / 1000L) + " ثانية.", false, null);
                handler.postDelayed(() -> pollExternalAssistantResponse(attempt + 1), retryDelay);
                return;
            }
            failExternalAgent("لم أتمكن من قراءة رد " + BotnaExternalAppAgent.label(externalAgentPackage)
                    + " بعد ثلاث محاولات قراءة متدرجة: بعد 10 ثوانٍ، ثم 15 ثانية، ثم 20 ثانية. لم أنفذ إجراءً اعتمادًا على رد غير ظاهر؛ تحقق من أن الرد اكتمل وأن نصه قابل للقراءة من واجهة التطبيق.");
            return;
        }
        if (!candidate.isEmpty() && externalStablePolls >= 4 && !generating && externalResponsePolls > 24 && parsed == null) {
            if (externalFormatRetries == 0) {
                externalFormatRetries++;
                String correction = "أعد JSON صالحًا فقط بلا Markdown، باستخدام قائمة أوامر Botna المرسلة لك. يمكنك إرجاع إجراء واحد أو مصفوفة actions مرتبة من 2 إلى 8 إجراءات فورية عندما لا تحتاج الإجراءات إلى لقطة وسيطة. كل عنصر داخل المصفوفة يجب أن يكون أمرًا منفردًا بالحقول الأصلية. لا تضع DONE أو STOP في سلسلة متعددة؛ أعدهما منفردين بعد مراجعة الشاشة الحالية. لا تستخدم إحداثيات خارج أبعاد لقطة الشاشة الحالية ولا تكتب أي نص خارج كائن JSON.";
                if (setExternalComposerText(correction)) {
                    ExternalUiSnapshot updated = readExternalUiSnapshot();
                    externalBaselineUi = updated.text;
                    externalBaselineValues = new ArrayList<>(updated.values);
                    externalLastCandidate = ""; externalStablePolls = 0; externalResponsePolls = 0;
                    externalCopiedResponseCandidate = "";
                    externalCopyAttemptedForResponse = false;
                    externalCodeCopyAttemptsForResponse = 0;
                    externalClipboardTimestampBeforeCopy = 0L;
                    externalResponseNoChangeWaitIndex = 0;
                    externalSendAttempts = 0;
                    externalSendRetryScheduled = false;
                    externalSendAttemptInProgress = false;
                    externalSendVisualStateKnown = false;
                    externalSendButtonLooksBlue = false;
                    externalSendVisualProbeAt = 0L;
                    scheduleExternalSendAttempt(BotnaExternalAppAgent.sendWaitMs(this, externalAgentPackage));
                    return;
                }
                failExternalAgent("وصل رد من " + BotnaExternalAppAgent.label(externalAgentPackage) + " لكنه لم يكن إجراء JSON صالحًا، وتعذر طلب تصحيح آلي موثوق. لم أتحكم بالهاتف.");
                return;
            } else {
                failExternalAgent("لم يتمكن Botna من استخراج أمر كامل وصالح من رد "
                        + BotnaExternalAppAgent.label(externalAgentPackage)
                        + " حتى بعد طلب التصحيح وإعادة قراءة بطاقة الكود ونسخ محتواها. لم أنفذ أمرًا مبتورًا أو غير مؤكد.");
                return;
            }
        }
        long delay;
        if (progressChanged || (!candidate.isEmpty() && !generating)) {
            externalResponseNoChangeWaitIndex = 0;
            delay = generating ? 1800L : 1200L;
        } else {
            delay = nextExternalResponseObservationDelay(false, generating);
        }
        handler.postDelayed(() -> pollExternalAssistantResponse(attempt + 1), delay);
    }

    private long nextExternalResponseObservationDelay(boolean progressChanged, boolean generating) {
        if (progressChanged) {
            externalResponseNoChangeWaitIndex = 0;
            return generating ? 1800L : 1200L;
        }
        int index = Math.min(externalResponseNoChangeWaitIndex, EXTERNAL_RESPONSE_NO_CHANGE_DELAYS_MS.length - 1);
        long delay = EXTERNAL_RESPONSE_NO_CHANGE_DELAYS_MS[index];
        if (externalResponseNoChangeWaitIndex < EXTERNAL_RESPONSE_NO_CHANGE_DELAYS_MS.length - 1) externalResponseNoChangeWaitIndex++;
        return delay;
    }

    private boolean looksLikeExternalJson(String value) {
        if (value == null) return false;
        String clean = value.trim();
        return clean.contains("\"action\"") || clean.contains("\"coordinates\"")
                || clean.contains("\"start_x\"") || clean.contains("\"end_x\"")
                || clean.contains("\"screen_state\"") || clean.contains("\"next_step\"");
    }

    private void pollCopiedExternalCode(String taskId, String packageName, long cycleStartedAt, int attempt) {
        if (!smartTaskActive || !externalAgentCycleInFlight || !taskId.equals(smartTaskId)
                || !packageName.equals(externalAgentPackage) || cycleStartedAt != externalCycleStartedAt) return;
        String copied = readClipboardTextSafely();
        long timestamp = readClipboardTimestampSafely();
        boolean changed = !copied.isEmpty() && (!copied.equals(externalClipboardBeforeCopy)
                || (externalClipboardTimestampBeforeCopy > 0L && timestamp > externalClipboardTimestampBeforeCopy));
        if (changed && looksLikeExternalJson(copied)
                && BotnaExternalAppAgent.parseAction(copied, externalScreenshotWidth, externalScreenshotHeight) != null) {
            externalCopiedResponseCandidate = copied;
            externalResponseLastProgressAt = System.currentTimeMillis();
            externalResponseStateFingerprint = "";
            externalCopyAttemptedForResponse = true;
            pollExternalAssistantResponse(externalResponsePolls);
            return;
        }
        if (attempt == 2 && (copied.isEmpty() || changed)) {
            if (pasteClipboardCodeIntoComposer(taskId, packageName, cycleStartedAt)) return;
        }
        if (attempt < 8) {
            handler.postDelayed(() -> pollCopiedExternalCode(taskId, packageName, cycleStartedAt, attempt + 1), 300L);
            return;
        }
        externalCopiedResponseCandidate = "";
        externalCopyAttemptedForResponse = externalCodeCopyAttemptsForResponse >= 3;
        externalResponseStateFingerprint = "";
        pollExternalAssistantResponse(externalResponsePolls);
    }

    private boolean pasteClipboardCodeIntoComposer(String taskId, String packageName, long cycleStartedAt) {
        AccessibilityNodeInfo root = null;
        AccessibilityNodeInfo target = null;
        String previousText = "";
        try {
            if (!smartTaskActive || !externalAgentCycleInFlight || !taskId.equals(smartTaskId)
                    || !packageName.equals(externalAgentPackage) || cycleStartedAt != externalCycleStartedAt) return false;
            root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null || !packageName.equals(root.getPackageName().toString())) return false;
            target = findExternalComposerNode(root);
            if (target == null) target = findFirstEditableNode(root);
            if (target == null || !target.isEnabled()) return false;
            CharSequence existing = target.getText();
            previousText = existing == null ? "" : existing.toString();
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle clear = new Bundle();
            clear.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            boolean cleared = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clear);
            if (!cleared) {
                Bundle selection = new Bundle();
                selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
                selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, previousText.length());
                if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)) return false;
            }
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if (!target.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                restoreExternalComposerText(previousText);
                return false;
            }
        } catch (Exception ignored) {
            restoreExternalComposerText(previousText);
            return false;
        } finally {
            if (target != null) target.recycle();
            if (root != null) root.recycle();
        }
        final String composerBeforeCopy = previousText;
        handler.postDelayed(() -> {
            if (!smartTaskActive || !externalAgentCycleInFlight || !taskId.equals(smartTaskId)
                    || !packageName.equals(externalAgentPackage) || cycleStartedAt != externalCycleStartedAt) return;
            ExternalUiSnapshot snapshot = readExternalUiSnapshot();
            String copiedCode = snapshot.editableText == null ? "" : snapshot.editableText.trim();
            boolean valid = !copiedCode.isEmpty() && !copiedCode.equals(composerBeforeCopy)
                    && looksLikeExternalJson(copiedCode)
                    && BotnaExternalAppAgent.parseAction(copiedCode, externalScreenshotWidth, externalScreenshotHeight) != null;
            if (!restoreExternalComposerText(composerBeforeCopy)) {
                failExternalAgent("تم نسخ بطاقة الأمر لكن تعذّر إعادة محرر " + BotnaExternalAppAgent.label(packageName)
                        + " إلى نصه السابق بعد قراءتها؛ أوقفت التنفيذ حتى لا أترك الأمر ملصقًا في المحرر أو أرسله بالخطأ.");
                return;
            }
            if (valid) {
                externalCopiedResponseCandidate = copiedCode;
                externalResponseLastProgressAt = System.currentTimeMillis();
                externalResponseStateFingerprint = "";
                externalCopyAttemptedForResponse = true;
            } else {
                externalCopiedResponseCandidate = "";
                externalCopyAttemptedForResponse = externalCodeCopyAttemptsForResponse >= 3;
                externalResponseStateFingerprint = "";
            }
            pollExternalAssistantResponse(externalResponsePolls);
        }, 450L);
        return true;
    }

    private boolean restoreExternalComposerText(String value) {
        AccessibilityNodeInfo root = null;
        AccessibilityNodeInfo target = null;
        try {
            root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null || !externalAgentPackage.equals(root.getPackageName().toString())) return false;
            target = findExternalComposerNode(root);
            if (target == null) target = findFirstEditableNode(root);
            if (target == null || !target.isEnabled()) return false;
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value == null ? "" : value);
            return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Exception ignored) {
            return false;
        } finally {
            if (target != null) target.recycle();
            if (root != null) root.recycle();
        }
    }

    private long readClipboardTimestampSafely() {
        if (Build.VERSION.SDK_INT < 28) return 0L;
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) return 0L;
            android.content.ClipDescription description = clipboard.getPrimaryClipDescription();
            return description == null ? 0L : description.getTimestamp();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private boolean clickLatestExternalCodeCopyButton() {
        AccessibilityNodeInfo root = null;
        ExternalCopyCandidate best = new ExternalCopyCandidate();
        try {
            root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null || !externalAgentPackage.equals(root.getPackageName().toString())) return false;
            collectExternalCopyCandidates(root, 0, best);
            if (best.node == null) return false;
            externalClipboardBeforeCopy = readClipboardTextSafely();
            externalClipboardTimestampBeforeCopy = readClipboardTimestampSafely();
            externalCopyAttemptedForResponse = true;
            boolean clicked = best.node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (!clicked) {
                externalCopyAttemptedForResponse = false;
                return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (root != null) root.recycle();
            if (best.node != null) best.node.recycle();
        }
    }

    private static final class ExternalCopyCandidate {
        AccessibilityNodeInfo node;
        int score = Integer.MIN_VALUE;
    }

    private void collectExternalCopyCandidates(AccessibilityNodeInfo node, int depth, ExternalCopyCandidate best) {
        if (node == null || depth > 35) return;
        try {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String primary = text == null ? "" : text.toString().trim().toLowerCase(Locale.ROOT);
            String description = desc == null ? "" : desc.toString().trim().toLowerCase(Locale.ROOT);
            String viewId = "";
            try { viewId = String.valueOf(node.getViewIdResourceName()).toLowerCase(Locale.ROOT); } catch (Exception ignored) { }
            String signal = primary + " " + description + " " + viewId;
            boolean exactCopy = containsAny(signal, "copy code", "copy-code", "نسخ الكود", "نسخ الشفرة", "copy response", "نسخ الرد");
            boolean genericCopy = !containsAny(signal, "clipboard", "copy link", "نسخ الرابط", "copy message link")
                    && (containsAny(signal, "copy", "نسخ") || viewId.contains("copy"));
            if ((exactCopy || genericCopy) && node.isVisibleToUser()) {
                AccessibilityNodeInfo clickable = obtainClickableAncestor(node);
                if (clickable != null) {
                    Rect bounds = new Rect();
                    clickable.getBoundsInScreen(bounds);
                    int score = (exactCopy ? 100000 : 1000) + Math.max(0, bounds.centerY())
                            + (clickable.isEnabled() ? 100 : -10000) + (bounds.isEmpty() ? -50000 : 0);
                    if (score > best.score) {
                        if (best.node != null) best.node.recycle();
                        best.node = clickable;
                        best.score = score;
                    } else clickable.recycle();
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = null;
                try { child = node.getChild(i); if (child != null) collectExternalCopyCandidates(child, depth + 1, best); }
                catch (Exception ignored) { }
                finally { if (child != null) child.recycle(); }
            }
        } catch (Exception ignored) { }
    }

    private String readClipboardTextSafely() {
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) return "";
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null) return "";
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < clip.getItemCount() && out.length() < 20000; i++) {
                CharSequence text = clip.getItemAt(i).coerceToText(this);
                if (text == null) continue;
                if (out.length() > 0) out.append("\n");
                out.append(text);
            }
            String value = out.toString().trim();
            return value.length() > 20000 ? value.substring(0, 20000) : value;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractExternalResponse(ExternalUiSnapshot snapshot) {
        if (!externalCopiedResponseCandidate.isEmpty()) return externalCopiedResponseCandidate;
        if (snapshot == null || snapshot.values.isEmpty()) return "";
        StringBuilder out = new StringBuilder();
        java.util.HashMap<String, Integer> baselineCounts = new java.util.HashMap<>();
        for (String value : externalBaselineValues) {
            String clean = value == null ? "" : value.trim();
            if (!clean.isEmpty()) baselineCounts.put(clean, baselineCounts.getOrDefault(clean, 0) + 1);
        }
        for (String value : snapshot.values) {
            String clean = value == null ? "" : value.trim();
            if (clean.isEmpty()) continue;
            String promptMarker = externalPrompt == null ? "" : externalPrompt.substring(0, Math.min(64, externalPrompt.length()));
            if (!promptMarker.isEmpty() && clean.contains(promptMarker)) continue;
            int remaining = baselineCounts.getOrDefault(clean, 0);
            if (remaining > 0) {
                baselineCounts.put(clean, remaining - 1);
                continue;
            }
            if (externalPrompt.length() > 0 && externalPrompt.equals(clean)) continue;
            if (out.length() > 0) out.append("\n");
            out.append(clean);
        }
        return out.toString().trim();
    }

    private boolean setExternalComposerText(String value) {
        AccessibilityNodeInfo root = null;
        AccessibilityNodeInfo target = null;
        try {
            if (value == null || value.trim().isEmpty()) return false;
            root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null || !externalAgentPackage.equals(root.getPackageName().toString())) return false;
            target = findExternalComposerNode(root);
            if (target == null) target = findFirstEditableNode(root);
            if (target == null || !target.isEnabled()) return false;
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            boolean setText = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            if (setText) {
                boolean refreshed = false;
                try { refreshed = target.refresh(); } catch (Exception ignored) { }
                if (!refreshed) return true;
                CharSequence actual = target.getText();
                String currentValue = actual == null ? "" : actual.toString();
                String head = value.substring(0, Math.min(64, value.length()));
                String tail = value.substring(Math.max(0, value.length() - Math.min(64, value.length())));
                if (currentValue.contains(head) && currentValue.contains(tail)
                        && currentValue.length() >= Math.ceil(value.length() * 0.90d)) return true;
            }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) return setText;
            clipboard.setPrimaryClip(ClipData.newPlainText("Botna complete AI instructions", value));
            CharSequence current = target.getText();
            int currentLength = current == null ? 0 : current.length();
            Bundle selection = new Bundle();
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0);
            selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, currentLength);
            boolean selected = target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection);
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            if (selected && target.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return true;
            Bundle clear = new Bundle();
            clear.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            if (!target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clear)) return false;
            target.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            return target.performAction(AccessibilityNodeInfo.ACTION_PASTE);
        } catch (Exception ignored) { return false; }
        finally { if (target != null) target.recycle(); if (root != null) root.recycle(); }
    }

    private void processExternalAssistantAction(JSONObject action) {
        String kind = action.optString("action", "").toUpperCase(Locale.ROOT);
        String reason = action.optString("reason", "قرار خطوة من التطبيق الخارجي.");
        if ("STOP".equals(kind)) {
            failExternalAgent("طلب " + BotnaExternalAppAgent.label(externalAgentPackage) + " التوقف والتدخل: " + reason);
            return;
        }
        returnFromExternalAssistant(() -> {
            if (!smartTaskActive) return;
            externalAgentCycleInFlight = false;
            if ("DONE".equals(kind)) {
                verifyExternalCompletion(action.optString("verification_evidence", ""), reason);
                return;
            }
            if ("SEQUENCE".equals(kind)) {
                JSONArray sequenceSteps = action.optJSONArray("steps");
                if (shouldCaptureExternalActionFrames()) executeExternalActionWithTemporalFrames(() -> executeSmartPlannedSequence(sequenceSteps, reason));
                else executeSmartPlannedSequence(sequenceSteps, reason);
                return;
            }
            JSONObject next;
            try {
                next = new JSONObject(action.toString());
            } catch (JSONException e) {
                failExternalAgent("تعذر قراءة الإجراء الذي أعاده تطبيق الذكاء الاصطناعي: " + e.getMessage());
                return;
            }
            next.remove("action");
            if (isRepeatOfLastFailedOrUnchangedAction(next)) {
                smartRepeatedActionReplans++;
                BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "external_action_rejected", smartTaskActionCount + 1,
                        "رفض إجراء مكرر من التطبيق الخارجي", "رفض Botna تكرار الإجراء لأنه سبق أن فشل أو لم يغير الشاشة.", false, next);
                if (smartRepeatedActionReplans >= 3) {
                    failExternalAgent("توقفت المهمة بأمان لأن " + BotnaExternalAppAgent.label(externalAgentPackage)
                            + " كرر الإجراء الذي فشل ثلاث مرات. راجع آخر لقطة وصحح التعليمات قبل الاستئناف.");
                } else requestSmartNextStep();
                return;
            }
            smartRepeatedActionReplans = 0;
            String externalDecisionReason = reason + " (القرار صادر من " + BotnaExternalAppAgent.label(externalAgentPackage) + ")";
            if (BotStudioStore.safetySandboxEnabled(this) && BotStudioStore.requiresManualApproval(next)) {
                BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "approval");
                requestSensitiveStepApproval(next, externalDecisionReason);
                return;
            }
            if (shouldCaptureExternalActionFrames()) executeExternalActionWithTemporalFrames(() -> executeSmartPlannedAction(next, externalDecisionReason));
            else executeSmartPlannedAction(next, externalDecisionReason);
        });
    }

    private void executeSmartPlannedSequence(JSONArray rawSteps, String decisionReason) {
        if (!smartTaskActive || rawSteps == null || rawSteps.length() < 2 || rawSteps.length() > 8) {
            failExternalAgent("رفض Botna سلسلة أوامر غير صالحة. يجب أن تحتوي على إجراءين إلى 8 إجراءات واضحة.");
            return;
        }
        JSONArray preparedSteps = new JSONArray();
        try {
            for (int i = 0; i < rawSteps.length(); i++) {
                JSONObject original = rawSteps.optJSONObject(i);
                if (original == null) {
                    failExternalAgent("توقفت السلسلة لأن الإجراء رقم " + (i + 1) + " ليس كائن JSON صالحًا.");
                    return;
                }
                JSONObject step = new JSONObject(original.toString());
                String kind = step.optString("action", "").toUpperCase(Locale.ROOT);
                if ("DONE".equals(kind) || "STOP".equals(kind) || "SEQUENCE".equals(kind)) {
                    failExternalAgent("رفض Botna وضع أمر الإنهاء أو التوقف داخل سلسلة إجراءات؛ يجب التحقق من الشاشة قبل أمر DONE أو STOP.");
                    return;
                }
                step = preferCoordinateTapWhenPossible(step);
                if (step == null) {
                    smartRepeatedActionReplans++;
                    BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "إعادة التخطيط لسلسلة الأوامر",
                            "لم أجد موضعًا مرئيًا آمنًا لخطوة نقر نصية داخل السلسلة. سأطلب قرارًا جديدًا بدل تخمين إحداثيات.", smartTaskActionCount, false);
                    if (smartRepeatedActionReplans >= 3) failExternalAgent("توقفت المهمة بعد ثلاث محاولات لسلاسل تتطلب نقر نص غير ظاهر؛ لم أخمّن موضع النقر.");
                    else handler.postDelayed(this::requestSmartNextStep, 300L);
                    return;
                }
                step.put("enabled", true);
                step.put("sequenceStep", i + 1);
                preparedSteps.put(step);
            }
        } catch (JSONException e) {
            failExternalAgent("تعذر تجهيز سلسلة الإجراءات: " + e.getMessage());
            return;
        }
        if (preparedSteps.length() < 2) {
            failExternalAgent("لم يتبقَّ عدد كافٍ من الإجراءات الصالحة لتنفيذ السلسلة.");
            return;
        }
        if (isRepeatOfLastFailedOrUnchangedAction(preparedSteps.optJSONObject(0))) {
            smartRepeatedActionReplans++;
            String detail = "رفض Botna السلسلة لأن أول إجراء فيها يكرر إجراءً سبق فشله أو لم يغيّر الشاشة.";
            BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "external_sequence_rejected", smartTaskActionCount + 1,
                    "رفض سلسلة مكررة", detail, false, preparedSteps.optJSONObject(0));
            if (smartRepeatedActionReplans >= 3) failExternalAgent(detail + " أوقف التنفيذ بعد ثلاث محاولات لتجنب تكرار غير مجدٍ.");
            else handler.postDelayed(this::requestSmartNextStep, 300L);
            return;
        }
        try {
            JSONObject definition = new JSONObject();
            definition.put("action", "SEQUENCE");
            definition.put("type", "SEQUENCE");
            definition.put("steps", new JSONArray(preparedSteps.toString()));
            definition.put("reason", decisionReason == null ? "" : decisionReason);
            definition.put("label", "سلسلة من " + preparedSteps.length() + " إجراءات");
            smartCurrentActionDefinition = definition;
            smartCurrentActionLabel = definition.optString("label", "سلسلة إجراءات");
            smartActionGoalRevision = smartGoalRevision;
            BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "action_sequence_planned", smartTaskActionCount + 1,
                    smartCurrentActionLabel, decisionReason == null ? "" : decisionReason, false, definition);
            for (int i = 0; i < preparedSteps.length(); i++) {
                BotnaAgentStore.appendStep(this, smartTaskId, preparedSteps.optJSONObject(i));
            }
            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "تنفيذ سلسلة مترابطة من " + preparedSteps.length() + " إجراءات",
                    (decisionReason == null ? "" : decisionReason) + "\nستنفذ الإجراءات بالترتيب. تتوقف السلسلة فور فشل أي خطوة، ثم يعود Botna إلى لقطة الشاشة لتخطيط الخطوة التالية.",
                    smartTaskActionCount + 1, true);
            currentScriptId = BotnaAgentStore.scriptId(this, smartTaskId);
            if (!currentScriptId.isEmpty()) BotnaStore.setActiveId(this, currentScriptId);
            executionSteps = new JSONArray(preparedSteps.toString());
            currentStepIndex = 0;
            currentBranches.clear();
            currentLoops.clear();
            lastStepSuccess = true;
            executionGeneration++;
            running = true;
            paused = false;
            executionInFlight = false;
            smartExternalSequenceActive = true;
            smartExternalSequenceExecutedCount = 0;
            panelWasVisibleAtRunStart = false;
            report("البوت الذكي ينفذ سلسلة من " + preparedSteps.length() + " إجراءات متتابعة.");
            showRunControls();
            runStep();
        } catch (JSONException e) {
            failExternalAgent("تعذر حفظ سلسلة الإجراءات: " + e.getMessage());
        }
    }

    private void requestSensitiveSequenceStepApproval(JSONObject step, int generation) {
        String label = step == null ? "إجراء حساس" : step.optString("label", step.optString("type", "إجراء حساس"));
        String body = "تتضمن سلسلة الأوامر خطوة تحتاج موافقتك قبل التنفيذ:\n\n" + label
                + "\n\nالتفاصيل: " + (step == null ? "{}" : step.toString())
                + "\n\nتستمر السلسلة من هذه الخطوة فقط بعد موافقتك.";
        BotnaAgentStore.addMessage(this, smartTaskId, "system", "بانتظار موافقة على خطوة ضمن السلسلة",
                body, smartTaskActionCount + 1, false);
        try { BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "approval"); } catch (Exception ignored) { }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("تأكيد إجراء حساس")
                .setMessage(body).setPositiveButton("موافقة وتنفيذ", null).setNegativeButton("إيقاف المهمة", null).create();
        if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                if (!smartTaskActive || !running || generation != executionGeneration) return;
                try { BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "running"); } catch (Exception ignored) { }
                JSONObject current = executionSteps == null ? null : executionSteps.optJSONObject(currentStepIndex);
                if (current == null) {
                    endSmartTask("تعذرت استعادة الخطوة التي وافقت عليها؛ أوقفت السلسلة بأمان.", false);
                    return;
                }
                executeActionStep(current, current.optString("type", ""), generation);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                dialog.dismiss();
                endSmartTask("أوقف المستخدم المهمة قبل تنفيذ خطوة حساسة ضمن السلسلة.", false);
            });
        });
        try { dialog.show(); }
        catch (Exception e) { endSmartTask("تعذر عرض تأكيد الإجراء الحساس؛ أوقفت سلسلة الأوامر دون تنفيذ الخطوة.", false); }
    }

    private void returnFromExternalAssistant(Runnable continuation) {
        if (!smartTaskActive) return;
        externalReturnAttempts = 0;
        performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> checkExternalAssistantReturned(continuation), 650L);
    }

    private void checkExternalAssistantReturned(Runnable continuation) {
        if (!smartTaskActive) return;
        String active = activeWindowPackage();
        if (!externalTargetPackage.isEmpty() && externalTargetPackage.equals(active)) {
            handler.postDelayed(continuation, 350L);
            return;
        }
        externalReturnAttempts++;
        if (externalReturnAttempts < 3) {
            performGlobalAction(GLOBAL_ACTION_BACK);
            handler.postDelayed(() -> checkExternalAssistantReturned(continuation), 700L);
            return;
        }
        if (!externalTargetPackage.isEmpty() && !externalTargetPackage.equals(externalAgentPackage)
                && !externalTargetPackage.equals(getPackageName())) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(externalTargetPackage);
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    startActivity(launch);
                    handler.postDelayed(() -> {
                        String restoredPackage = activeWindowPackage();
                        if (!externalTargetPackage.equals(restoredPackage)) {
                            failExternalAgent("فتحت محاولة الرجوع إلى التطبيق المستهدف، لكن لم أستطع تأكيد ظهوره على الشاشة. التطبيق الحالي: "
                                    + (restoredPackage.isEmpty() ? "غير معروف" : restoredPackage)
                                    + ". لم أنفذ الإحداثيات حتى لا أنقر في واجهة خاطئة.");
                            return;
                        }
                        continuation.run();
                    }, 1100L);
                    return;
                }
            } catch (Exception ignored) { }
        }
        failExternalAgent("تعذر الرجوع من تطبيق " + BotnaExternalAppAgent.label(externalAgentPackage)
                + " إلى شاشة المهمة. لم أنفذ إحداثيات بينما تطبيق الذكاء الاصطناعي ما زال في الواجهة.");
    }

    private void verifyExternalCompletion(String evidence, String reason) {
        if (!smartTaskActive) return;
        hideBotnaOverlaysForCleanScreenshot();
        handler.postDelayed(() -> takeScreenshot((screen, error) -> {
            if (!smartTaskActive) { if (screen != null && !screen.isRecycled()) screen.recycle(); return; }
            if (screen == null) {
                String detail = "أصدر " + BotnaExternalAppAgent.label(externalAgentPackage)
                        + " أمر DONE، لكن تعذر التقاط لقطة تحقق من الشاشة المستهدفة: " + error
                        + ". أوقفت الدورة فورًا ولم أرسل لقطة جديدة حتى لا تستمر المهمة بعد إعلان الانتهاء.";
                BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "external_done_unverified", smartTaskActionCount,
                        "توقف أمر DONE دون تحقق مستقل", detail, false, null);
                BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "توقفت المهمة دون تأكيد النجاح", detail, smartTaskActionCount, false);
                endSmartTask(detail, false);
                return;
            }
            BotnaTaskEvidenceStore.saveFrame(this, smartTaskId, smartTaskGoal, smartTaskActionCount, "التحقق من ادعاء اكتمال المهمة", screen);
            String visible = readVisibleScreenText(7000);
            boolean valid = hasVerifiableCompletionEvidence(evidence, visible) || isSpecificExternalCompletionEvidence(evidence);
            if (!screen.isRecycled()) screen.recycle();
            if (valid) {
                String result = "اكتملت المهمة بعد تحقق من لقطة الشاشة.\nالهدف: " + smartTaskGoal + "\nدليل التحقق: " + evidence + "\nملخص النموذج: " + reason;
                BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "اكتملت المهمة عبر " + BotnaExternalAppAgent.label(externalAgentPackage), result, smartTaskActionCount, true);
                BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "external_task_verified_done", smartTaskActionCount,
                        "تم التحقق من اكتمال المهمة", result, true, null);
                endSmartTask(result, true);
            } else {
                String detail = "أصدر التطبيق أمر DONE، لكن دليل الاكتمال كان عامًا أو غير كافٍ للتحقق. أوقفت المهمة ولم ألتقط أو أرسل أي لقطة إضافية.\nالدليل الذي اقترحه النموذج: " + evidence;
                BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "external_done_unverified", smartTaskActionCount,
                        "رفض اكتمال غير متحقق", detail, false, null);
                BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "توقف أمر DONE دون تأكيد النجاح", detail, smartTaskActionCount, false);
                endSmartTask(detail, false);
            }
        }, "التحقق من اكتمال المهمة عبر التطبيق الخارجي"), 450L);
    }

    private boolean isSpecificExternalCompletionEvidence(String evidence) {
        String proof = evidence == null ? "" : evidence.trim();
        if (proof.length() < 24) return false;
        String normalizedProof = BotnaArabicDialect.normalize(proof);
        String[] genericClaims = {"تم بنجاح", "اكتملت المهمه", "تم تنفيذ المطلوب", "الهدف تحقق", "done successfully", "task completed", "successfully completed"};
        for (String generic : genericClaims) {
            if (normalizedProof.equals(BotnaArabicDialect.normalize(generic))) return false;
        }
        return true;
    }

    private void failExternalAgent(String reason) {
        if (!smartTaskActive) return;
        externalAgentCycleInFlight = false;
        smartPlanningInFlight = false;
        String detail = reason == null || reason.trim().isEmpty() ? "فشل مسار الذكاء الاصطناعي الخارجي لسبب غير معروف." : reason.trim();
        BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "external_agent_failure", smartTaskActionCount,
                "فشل التنفيذ عبر التطبيق الخارجي", detail, false, null);
        BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "توقف التنفيذ الخارجي", detail, smartTaskActionCount, false);
        BotnaLogStore.add(this, "external_agent", smartTaskActionCount, "فشل دورة التطبيق الخارجي", detail, "الهدف: " + smartTaskGoal, null);
        notifyUser("توقفت مهمة Botna وتحتاج تدخلك: " + detail, true);
        report(detail);
        endSmartTask(detail, false);
    }

    private void requestSmartNextStep() {
        if (!smartTaskActive) return;
        if (BotnaExternalAppAgent.enabled(this)) {
            requestExternalAssistantStep();
            return;
        }
        hideBotnaOverlaysForCleanScreenshot();
        handler.postDelayed(() -> takeScreenshot((screen, screenshotError) -> {
            if (!smartTaskActive) { if (screen != null && !screen.isRecycled()) screen.recycle(); return; }
            if (screen == null) {
                clearPendingSmartTaskIfMatches(smartTaskId, smartTaskGoal);
                String detail = "تعذر أخذ لقطة شاشة جديدة بعد الإجراء رقم " + smartTaskActionCount + ": " + screenshotError;
                BotnaLogStore.add(this, "smart_agent", smartTaskActionCount, smartCurrentActionLabel,
                        "توقف الوكيل لأن لقطة الشاشة غير متاحة.", detail, null);
                BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "تعذر التقاط الشاشة", detail, smartTaskActionCount, false);
                endSmartTask(detail, false); return;
            }
            clearPendingSmartTaskIfMatches(smartTaskId, smartTaskGoal);
            final Bitmap currentScreen = screen;
            long currentFingerprint = smartScreenFingerprint(currentScreen);
            final boolean screenUnchangedAfterLastAction = smartTaskActionCount > 0
                    && smartLastScreenshotFingerprint != 0L
                    && Long.bitCount(currentFingerprint ^ smartLastScreenshotFingerprint) <= 6;
            smartLastScreenshotFingerprint = currentFingerprint;
            if (smartTaskActionCount > 0 && smartTaskHistory.length() > 0) {
                JSONObject lastEvent = smartTaskHistory.optJSONObject(smartTaskHistory.length() - 1);
                if (lastEvent != null) {
                    try {
                        lastEvent.put("screenChanged", !screenUnchangedAfterLastAction);
                        if (screenUnchangedAfterLastAction) {
                            lastEvent.put("result", lastEvent.optString("result", "")
                                    + "\nملاحظة تحقق آلية: لم يتغير شكل الشاشة بوضوح بعد هذا الإجراء؛ لا تكرر الإجراء نفسه بالمعلمات نفسها.");
                        }
                    } catch (Exception ignored) { }
                }
            }
            showSmartPlanningControls();
            final String goal = smartTaskGoal;
            final String taskIdForCycle = smartTaskId;
            final int goalRevisionForCycle = smartGoalRevision;
            final int actionCountForCycle = smartTaskActionCount;
            final String actionLabelForCycle = smartCurrentActionLabel;
            final String scriptIdForCycle = currentScriptId;
            final JSONArray history;
            try { history = new JSONArray(smartTaskHistory.toString()); }
            catch (JSONException e) { if (!currentScreen.isRecycled()) currentScreen.recycle(); endSmartTask("تعذر تجهيز سجل الخطوات السابق.", false); return; }
            smartPlanningScreenFingerprint = currentFingerprint;
            smartLiveVisionChangedDuringPlanning = false;
            smartPlanningInFlight = true;
            try {
                aiExecutor.execute(() -> {
                    JSONObject plan = null; String error = null; JSONArray executable = new JSONArray();
                    String visibleText = "";
                    String appContext = "";
                    Bitmap historicalContextSheet = null;
                    try {
                        visibleText = readVisibleScreenText(6500);
                        appContext = buildBotnaApplicationContext(scriptIdForCycle, "")
                                + "\nحالة هذا الدور: التقطت خدمة إمكانية الوصول لقطة شاشة فعلية في هذه الدورة."
                                + "\nالنصوص الفعلية التي قرأتها خدمة إمكانية الوصول بعد التقاط الصورة (قد تكون فارغة في الألعاب أو الواجهات المرسومة):\n"
                                + (visibleText.isEmpty() ? "[لم تُستخرج نصوص قابلة للوصول؛ استخدم الصورة الحالية فقط ولا تخترع نصًا]" : visibleText)
                                + "\nالوقت الحالي: " + BotnaWebTools.currentTimeText()
                                + (screenUnchangedAfterLastAction
                                    ? "\nتحذير تحقق بصري: لم يظهر تغير مرئي واضح مقارنة بلقطة ما بعد الإجراء السابق. لا تكرر الإجراء السابق نفسه؛ افحص سبب عدم التغير واختر وسيلة بديلة مدعومة أو أوقف المهمة بصدق إذا لم يوجد بديل موثوق."
                                    : "\nقارن الشاشة الحالية بسجل الإجراء السابق، وتحقق من نجاح الإجراء من تغيير واضح أو نص/عنصر ظاهر.");
                        historicalContextSheet = BotnaTaskEvidenceStore.createVisionContextSheet(this, taskIdForCycle, currentScreen, 3);
                        if (historicalContextSheet != null) appContext += "\nصورة النموذج مركبة للمراجعة: شاشة اللحظة الحالية تشغل كامل اللوحة اليسرى، والعمود الأيمن يحتوي لقطات سابقة محفوظة للمقارنة. عند اختيار tap_at، حدّد x_percent وy_percent كنسب داخل الشاشة الحالية اليسرى فقط، ولا تنقر أي إحداثية من عمود اللقطات المرجعية.";
                    } catch (Exception contextFailure) {
                        appContext = "التقطت خدمة إمكانية الوصول لقطة شاشة فعلية في هذه الدورة، لكن تعذر استخراج شجرة النصوص أو سياق التطبيق: "
                                + (contextFailure.getMessage() == null ? contextFailure.getClass().getSimpleName() : contextFailure.getMessage())
                                + ". اعتمد على الصورة المرفقة نفسها فقط ولا تخترع نصوصًا.";
                    }
                    final String visibleScreenText = visibleText;
                    final String currentAppContext = appContext;
                    if (actionCountForCycle > 0 && BotnaVisionEngine.shouldRecordActionSnapshot(this)) {
                        try { BotnaLogStore.add(this, "vision_action_snapshot", actionCountForCycle, actionLabelForCycle,
                                "لقطة تحقق محفوظة بعد الإجراء.", "تم الاحتفاظ بعينة الشاشة الحالية حسب إعدادات الرؤية.",
                                BotnaLogStore.screenshotsEnabled(this) ? currentScreen : null); } catch (Exception ignored) { }
                    }
                    try {
                        Bitmap screenForModel = historicalContextSheet == null ? currentScreen : historicalContextSheet;
                        plan = BotnaAICompiler.compileSmartNext(this, goal, history, screenForModel, currentAppContext);
                        BotnaTaskEvidenceStore.savePlanIfAbsent(this, taskIdForCycle, goal, plan.optJSONArray("task_plan"));
                        if (plan.has("current_phase")) BotnaTaskEvidenceStore.updatePlanProgress(this, taskIdForCycle,
                                Math.max(0, plan.optInt("current_phase", 0)), plan.optString("reason", ""));
                        if (plan.optBoolean("rollback_previous_step", false)) {
                            if (actionCountForCycle > 0) {
                                JSONObject rollback = new JSONObject();
                                rollback.put("type", "back");
                                rollback.put("label", "تراجع خطوة واحدة عن المسار الخاطئ ثم أعد الفحص");
                                rollback.put("recovery_action", true);
                                JSONArray rollbackSteps = new JSONArray(); rollbackSteps.put(rollback);
                                plan.put("steps", rollbackSteps); plan.put("task_done", false);
                                BotnaTaskEvidenceStore.recordEvent(this, taskIdForCycle, "rollback_requested", actionCountForCycle,
                                        "الرجوع عن المسار الخاطئ", plan.optString("reason", "طلب النموذج الرجوع بعد اكتشاف انحراف."), true, rollback);
                            } else {
                                JSONArray rollbackWarnings = plan.optJSONArray("warnings");
                                if (rollbackWarnings == null) rollbackWarnings = new JSONArray();
                                rollbackWarnings.put("طلب النموذج التراجع قبل تنفيذ أي خطوة؛ تم تجاهل الرجوع لأنه لا توجد خطوة سابقة في هذه المهمة.");
                                plan.put("warnings", rollbackWarnings); plan.put("rollback_previous_step", false);
                            }
                        }
                        if (plan.optJSONArray("task_plan") == null || plan.optJSONArray("task_plan").length() == 0) {
                            BotnaTaskEvidenceStore.ensureFallbackPlan(this, taskIdForCycle, goal);
                        }
                        String decisionReason = plan.optString("reason", "لم يقدم النموذج سببًا نصيًا للقرار.");
                        JSONArray decisionWarnings = plan.optJSONArray("warnings");
                        BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "تحليل الشاشة وسبب القرار",
                                decisionReason + (decisionWarnings == null || decisionWarnings.length() == 0 ? "" : "\nالتحذيرات: " + decisionWarnings.toString()),
                                smartTaskActionCount, true);
                        JSONArray proposed = plan.optJSONArray("steps");
                        if (proposed != null && proposed.length() > 0) {
                            JSONObject firstOnly = proposed.optJSONObject(0);
                            JSONArray oneStep = new JSONArray(); if (firstOnly != null) oneStep.put(firstOnly);
                            plan.put("steps", oneStep);
                            List<String> warnings = new ArrayList<>();
                            executable = autoTuneCompiledPlan(compileAIPlan(plan, BotnaStore.imageLibrary(this), warnings), goal);
                            String structure = validateScriptStructure(executable);
                            if (structure != null) throw new IllegalStateException("تركيب الإجراء التالي غير صالح: " + structure);
                            if (executable.length() == 0) error = "لم ينتج المترجم أمرًا مدعومًا من الإجراء المقترح. التحذيرات: " + warnings;
                        }
                        if (error == null && (plan == null || (!plan.optBoolean("task_done", false)
                                && (plan.optJSONArray("steps") == null || plan.optJSONArray("steps").length() == 0)))) {
                            error = "لم يقدّم النموذج إجراءً تاليًا ولم يؤكد اكتمال المهمة. "
                                    + (plan == null ? "لا يوجد رد مفهوم." : plan.optString("reason", "راجع تحذيرات النموذج."));
                        }
                    } catch (Exception ex) { error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }
                    finally { if (historicalContextSheet != null && historicalContextSheet != currentScreen && !historicalContextSheet.isRecycled()) historicalContextSheet.recycle(); }

                    JSONObject finalPlan = plan; JSONArray finalAction = executable; String finalError = error;
                    handler.post(() -> {
                        removeView(smartPlanningControls); smartPlanningControls = null;
                        if (!smartTaskActive) { smartPlanningInFlight = false; if (!currentScreen.isRecycled()) currentScreen.recycle(); return; }
                        smartPlanningInFlight = false;
                        if (goalRevisionForCycle != smartGoalRevision) {
                            if (!currentScreen.isRecycled()) currentScreen.recycle();
                            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "ألغيت خطة قديمة بعد تصحيح المستخدم",
                                    "وصل تصحيح جديد أثناء التحليل؛ لم أنفّذ الخطة القديمة. سألتقط الشاشة مجددًا وأعيد التخطيط حسب الهدف المصحح.", smartTaskActionCount, false);
                            handler.postDelayed(this::requestSmartNextStep, 220);
                            return;
                        }
                        if (BotStudioStore.directLiveVisionEnabled(this) && smartLiveVisionChangedDuringPlanning) {
                            smartLiveVisionChangedDuringPlanning = false;
                            smartLiveVisionReplans++;
                            BotnaTaskEvidenceStore.recordEvent(this, taskIdForCycle, "stale_plan_discarded", actionCountForCycle,
                                    "أُلغيت خطة قديمة بسبب تغير الشاشة", "تغيرت الشاشة أثناء اتصال النموذج؛ لم أنفذ الإحداثيات القديمة، وسألتقط الشاشة الحالية وأعيد التخطيط.", false, null);
                            if (!currentScreen.isRecycled()) currentScreen.recycle();
                            if (smartLiveVisionReplans >= 3) {
                                endSmartTask("توقفت المهمة بأمان لأن الشاشة استمرت في التغير أثناء التخطيط المباشر ثلاث مرات. لم أنفذ نقرة مبنية على لقطة قديمة؛ أوقف التطبيق حركته أو أعد المحاولة عندما تستقر الشاشة.", false);
                            } else handler.postDelayed(this::requestSmartNextStep, 260);
                            return;
                        }
                        JSONArray proposals = finalPlan == null ? null : finalPlan.optJSONArray("steps");
                        String evaluatedError = finalError;
                        boolean modelClaimsDone = finalPlan != null && finalPlan.optBoolean("task_done", false)
                                && (proposals == null || proposals.length() == 0);
                        String completionEvidence = finalPlan == null ? "" : finalPlan.optString("verification_evidence", "").trim();
                        boolean evidenceValid = hasVerifiableCompletionEvidence(completionEvidence, visibleScreenText);
                        boolean hasPriorAction = smartTaskActionCount > 0 || (smartTaskWasResumed && smartTaskHistory.length() > 0);
                        JSONObject lastActionEvent = smartTaskHistory.length() > 0
                                ? smartTaskHistory.optJSONObject(smartTaskHistory.length() - 1) : null;
                        boolean lastActionSucceeded = lastActionEvent != null && lastActionEvent.optBoolean("success", false);
                        boolean lastActionChangedScreen = lastActionEvent != null && lastActionEvent.optBoolean("screenChanged", false);
                        boolean lastActionWasObservation = lastActionEvent != null
                                && isObservationOnlyAction(lastActionEvent.optJSONObject("definition"));
                        boolean lastActionVerified = lastActionSucceeded && (lastActionChangedScreen || lastActionWasObservation);
                        boolean done = modelClaimsDone && evaluatedError == null && evidenceValid && hasPriorAction && lastActionVerified;
                        if (modelClaimsDone && !done && evaluatedError == null) {
                            if (!hasPriorAction) evaluatedError = "رفض Botna إعلان اكتمال المهمة لأن سجل التنفيذ لا يحتوي على إجراء سابق يثبت حصول تغيير أو تحقق. أوقفت المهمة وسأحفظها غير مكتملة بدل إعلان نجاح بلا دليل.";
                            else if (!lastActionVerified) evaluatedError = "رفض Botna إعلان اكتمال المهمة لأن آخر إجراء لم يُسجل نجاحه أو لم يتبعه تغير مرئي موثوق في الشاشة. لن أعتبر وصف النموذج وحده دليلًا على نجاح التنفيذ.";
                            else if (!evidenceValid) evaluatedError = "رفض Botna إعلان اكتمال المهمة لأن النموذج لم يقدّم دليلًا محددًا يمكن التحقق منه من لقطة الشاشة الحالية. لا تُعتبر المهمة مكتملة بعد.";
                        }
                        if (done) {
                            String finalAnswer = finalPlan.optString("reason", "أفاد النموذج بأن المهمة اكتملت، لكنه لم يقدم شرحًا نهائيًا.").trim();
                            if (finalAnswer.isEmpty()) finalAnswer = "أفاد النموذج بأن المهمة اكتملت دون نص نتيجة واضح.";
                            String answerDetail = "الهدف: " + smartTaskGoal + "\nالنتيجة النهائية من الوكيل: " + finalAnswer
                                    + "\nعدد الإجراءات: " + smartTaskActionCount;
                            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "النتيجة النهائية للمهمة", answerDetail, smartTaskActionCount, true);
                            BotnaLogStore.add(this, "smart_result", smartTaskActionCount, "النتيجة النهائية للمهمة",
                                    "أعلن الوكيل اكتمال المهمة وأرفق خلاصة نصية.", answerDetail,
                                    BotnaLogStore.screenshotsEnabled(this) ? currentScreen : null);
                            if (!currentScreen.isRecycled()) currentScreen.recycle();
                            endSmartTask("النتيجة: " + finalAnswer, true);
                            return;
                        }
                        if (evaluatedError != null && BotnaAICompiler.isTransientRateLimitFailure(evaluatedError)
                                && smartPlanRetryCount < SMART_PLAN_MAX_RATE_RETRIES - 1) {
                            long retryDelay = BotnaAICompiler.retryDelayMillis(evaluatedError, smartPlanRetryCount + 1);
                            if (retryDelay > 0 && retryDelay <= SMART_PLAN_MAX_AUTO_WAIT_MS) {
                                smartPlanRetryCount++;
                                String waitMessage = "وصل حد طلبات مؤقت للمزوّد. سيحافظ بوتنا على المهمة وينتظر "
                                        + Math.max(1, retryDelay / 1000) + " ثانية ثم يعيد قراءة الشاشة. المحاولة التالية "
                                        + (smartPlanRetryCount + 1) + "/" + SMART_PLAN_MAX_RATE_RETRIES + ".";
                                BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "انتظار تلقائي بسبب حد الطلبات",
                                        waitMessage + "\nتفاصيل المزوّد: " + evaluatedError, smartTaskActionCount, false);
                                BotnaLogStore.add(this, "smart_rate_limit", smartTaskActionCount, "انتظار وإعادة المحاولة",
                                        "تأخر التخطيط بسبب حد طلبات مؤقت.", waitMessage + "\n" + evaluatedError,
                                        BotnaLogStore.screenshotsEnabled(this) ? currentScreen : null);
                                report(waitMessage);
                                if (!currentScreen.isRecycled()) currentScreen.recycle();
                                handler.postDelayed(() -> { if (smartTaskActive) requestSmartNextStep(); }, retryDelay);
                                return;
                            }
                        }
                        if (evaluatedError != null || finalAction == null || finalAction.length() == 0) {
                            String reason = evaluatedError == null ? "لم يقدّم النموذج خطوة واحدة واضحة. تحذيرات: " + (finalPlan == null ? "لا يوجد رد" : finalPlan.optJSONArray("warnings")) : evaluatedError;
                            BotnaLogStore.add(this, "smart_agent", smartTaskActionCount, "تخطيط الخطوة التالية",
                                    "لم يستطع الوكيل اختيار إجراء قابل للتنفيذ بأمان.", reason + "\nالمهمة: " + smartTaskGoal,
                                    BotnaLogStore.screenshotsEnabled(this) ? currentScreen : null);
                            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "توقّف التخطيط بسبب خطأ", reason + "\nالمهمة: " + smartTaskGoal, smartTaskActionCount, false);
                            if (!currentScreen.isRecycled()) currentScreen.recycle();
                            endSmartTask("توقف الوكيل بدل التخمين: " + reason, false); return;
                        }
                        smartPlanRetryCount = 0;
                        JSONObject next = finalAction.optJSONObject(0);
                        if (next == null) { if (!currentScreen.isRecycled()) currentScreen.recycle(); endSmartTask("أعاد المترجم إجراءً فارغًا.", false); return; }
                        String decisionReason = finalPlan == null ? "" : finalPlan.optString("reason", "");
                        if (isRepeatOfLastFailedOrUnchangedAction(next)) {
                            smartRepeatedActionReplans++;
                            String duplicateReason = "رفض Botna تكرار الإجراء نفسه لأن سجل التنفيذ يوضح أنه فشل أو لم يغيّر الشاشة. سيعيد التخطيط من لقطة الشاشة الحالية دون تنفيذ هذا الإجراء.";
                            try {
                                JSONObject rejected = new JSONObject();
                                rejected.put("action", "خطة مكررة مرفوضة");
                                rejected.put("success", false);
                                rejected.put("screenChanged", false);
                                rejected.put("result", duplicateReason);
                                rejected.put("definition", new JSONObject(next.toString()));
                                rejected.put("time", System.currentTimeMillis());
                                smartTaskHistory.put(rejected);
                                while (smartTaskHistory.length() > 20) smartTaskHistory.remove(0);
                            } catch (Exception ignored) { }
                            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "منع تكرار إجراء غير مجدٍ",
                                    duplicateReason + "\nالإجراء المرفوض: " + next.toString(), smartTaskActionCount, false);
                            try { BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "running"); } catch (Exception ignored) { }
                            if (!currentScreen.isRecycled()) currentScreen.recycle();
                            if (smartRepeatedActionReplans >= 3) {
                                endSmartTask("توقفت المهمة بأمان لأن النموذج أعاد اقتراح الإجراء الفاشل نفسه ثلاث مرات، ولم أكرر النقر أو الحركة بلا جدوى. راجع آخر لقطة وسجل التنفيذ وصحح الهدف أو أعد المحاولة.", false);
                            } else {
                                handler.postDelayed(this::requestSmartNextStep, 280);
                            }
                            return;
                        }
                        smartRepeatedActionReplans = 0;
                        if (!currentScreen.isRecycled()) currentScreen.recycle();
                        if (BotStudioStore.safetySandboxEnabled(this) && BotStudioStore.requiresManualApproval(next)) {
                            BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "approval");
                            requestSensitiveStepApproval(next, decisionReason);
                            return;
                        }
                        executeSmartPlannedAction(next, decisionReason);
                    });
                });
            } catch (Exception e) {
                if (!currentScreen.isRecycled()) currentScreen.recycle();
                endSmartTask("تعذر بدء مخطط الذكاء الاصطناعي: " + e.getMessage(), false);
            }
        }), 180);
    }

    private boolean isRepeatOfLastFailedOrUnchangedAction(JSONObject candidate) {
        if (candidate == null) return false;
        for (int i = smartTaskHistory.length() - 1; i >= 0; i--) {
            JSONObject event = smartTaskHistory.optJSONObject(i);
            if (event == null) continue;
            JSONObject previous = event.optJSONObject("definition");
            if (previous == null) continue;
            boolean failed = !event.optBoolean("success", false);
            boolean unchanged = event.has("screenChanged") && !event.optBoolean("screenChanged", true);
            if (!failed && !unchanged) return false;
            return sameActionSignature(previous, candidate);
        }
        return false;
    }

    private boolean sameActionSignature(JSONObject first, JSONObject second) {
        if (first == null || second == null) return false;
        String firstType = BotnaArabicDialect.normalize(first.optString("type", first.optString("action", "")));
        String secondType = BotnaArabicDialect.normalize(second.optString("type", second.optString("action", "")));
        if (firstType.isEmpty() || !firstType.equals(secondType)) return false;
        String[] textKeys = {"value", "text", "appName", "app_name", "url", "direction", "image", "imageName", "target"};
        int compared = 0;
        for (String key : textKeys) {
            String a = first.optString(key, "").trim();
            String b = second.optString(key, "").trim();
            if (!a.isEmpty() && !b.isEmpty()) {
                compared++;
                if (!BotnaArabicDialect.normalize(a).equals(BotnaArabicDialect.normalize(b))) return false;
            }
        }
        String[] numberKeys = {"xPercent", "yPercent", "x1", "y1", "x2", "y2", "screenX", "screenY", "screenX1", "screenY1", "screenX2", "screenY2", "x_percent", "y_percent"};
        for (String key : numberKeys) {
            if (first.has(key) && second.has(key)) {
                compared++;
                if (first.optDouble(key, Double.NaN) != second.optDouble(key, Double.NaN)) return false;
            }
        }
        if (compared > 0) return true;
        String aLabel = BotnaArabicDialect.normalize(first.optString("label", ""));
        String bLabel = BotnaArabicDialect.normalize(second.optString("label", ""));
        return !aLabel.isEmpty() && aLabel.equals(bLabel);
    }

    private long smartScreenFingerprint(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() < 9 || bitmap.getHeight() < 8) return 0L;
        long hash = 0L;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int leftX = Math.min(bitmap.getWidth() - 1, x * (bitmap.getWidth() - 1) / 8);
                int rightX = Math.min(bitmap.getWidth() - 1, (x + 1) * (bitmap.getWidth() - 1) / 8);
                int py = Math.min(bitmap.getHeight() - 1, y * (bitmap.getHeight() - 1) / 8);
                int left = bitmap.getPixel(leftX, py);
                int right = bitmap.getPixel(rightX, py);
                int leftGray = (Color.red(left) * 299 + Color.green(left) * 587 + Color.blue(left) * 114) / 1000;
                int rightGray = (Color.red(right) * 299 + Color.green(right) * 587 + Color.blue(right) * 114) / 1000;
                hash = (hash << 1) | (leftGray > rightGray ? 1L : 0L);
            }
        }
        return hash == 0L ? 1L : hash;
    }

    private void clearPendingSmartTaskIfMatches(String taskId, String goal) {
        android.content.SharedPreferences prefs = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE);
        String pendingId = prefs.getString(PREF_PENDING_SMART_TASK_ID, "");
        String pendingGoal = prefs.getString(PREF_PENDING_SMART_TASK, "");
        boolean sameTask = taskId != null && !taskId.isEmpty() ? taskId.equals(pendingId) : goal != null && goal.equals(pendingGoal);
        if (sameTask) prefs.edit().remove(PREF_PENDING_SMART_TASK).remove(PREF_PENDING_SMART_TASK_ID).remove(PREF_PENDING_SMART_RESUME).apply();
    }

    private void recordTaskFailureToChat(String taskId, String title, String reason) {
        if (taskId == null || taskId.isEmpty()) return;
        try {
            BotnaAgentStore.setStatus(this, taskId, "توقفت/فشلت", reason == null ? "تعذر تنفيذ المهمة." : reason);
            BotnaAgentStore.addMessage(this, taskId, "assistant", title == null ? "توقف الوكيل" : title,
                    (reason == null ? "تعذر تنفيذ المهمة." : reason) + "\nلا توجد نتيجة تحكم مؤكدة في هذه المحاولة.", 0, false);
        } catch (Exception ignored) { }
        String botId = BotStudioStore.botForTask(this, taskId);
        if (botId.isEmpty()) botId = BotStudioStore.selectedBotId(this);
        if (!botId.isEmpty()) {
            try { BotStudioStore.get(this).addMessage(botId, "assistant",
                    "⚠️ " + (title == null ? "توقف الوكيل" : title) + "\n" + (reason == null ? "تعذر تنفيذ المهمة." : reason)
                            + "\nلم يتم تأكيد أي إجراء أو رؤية حالية للشاشة.", "", BotnaAICompiler.model(this)); }
            catch (Exception ignored) { }
        }
    }

    private boolean isObservationOnlyAction(JSONObject definition) {
        if (definition == null) return false;
        String type = BotnaArabicDialect.normalize(definition.optString("type", definition.optString("action", "")));
        return type.contains("wait") || type.contains("انتظار") || type.contains("read_text")
                || type.contains("observe") || type.contains("قراءة") || type.contains("مراقبة")
                || type.contains("screenshot") || type.contains("لقطة");
    }

    private boolean hasVerifiableCompletionEvidence(String evidence, String visibleScreenText) {
        String proof = evidence == null ? "" : evidence.trim();
        if (proof.length() < 10) return false;
        String normalizedProof = BotnaArabicDialect.normalize(proof);
        String[] genericClaims = {"تم بنجاح", "اكتملت المهمه", "تم تنفيذ المطلوب", "الهدف تحقق", "done successfully", "task completed", "successfully completed"};
        for (String generic : genericClaims) if (normalizedProof.equals(BotnaArabicDialect.normalize(generic))) return false;
        String visible = visibleScreenText == null ? "" : BotnaArabicDialect.normalize(visibleScreenText);
        if (visible.isEmpty()) return proof.length() >= 24;
        if (visible.contains(normalizedProof) || normalizedProof.contains(visible) && visible.length() >= 12) return true;
        java.util.HashSet<String> ignored = new java.util.HashSet<>(java.util.Arrays.asList(
                "ظهر", "ظهور", "على", "الى", "انه", "انها", "هذا", "هذه", "هناك", "الهدف", "المهمة", "تم", "بعد", "على", "شاشة", "الشاشة", "حاليا", "الحالي", "current", "screen", "button", "visible"));
        int matches = 0;
        for (String token : normalizedProof.split("\\s+")) {
            if (token.length() >= 4 && !ignored.contains(token) && visible.contains(token)) matches++;
        }
        return matches >= 1;
    }

    private void executeSmartPlannedAction(JSONObject next, String decisionReason) {
        if (!smartTaskActive || next == null) return;
        next = preferCoordinateTapWhenPossible(next);
        if (next == null) {
            if (externalTemporalCaptureInProgress && !running) clearExternalTemporalCapture(true);
            smartCoordinateRefusalReplans++;
            String detail = "لم أنفذ نقرة نصية لأن الإحداثيات المرئية لم تتوفر. أعد Botna التخطيط من لقطة جديدة وسيطلب tap_at بإحداثيات؛ لن ينقر على النص مباشرة. المحاولة التصحيحية " + smartCoordinateRefusalReplans + " من 3.";
            BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "coordinate_tap_refused", smartTaskActionCount + 1,
                    "رفض نقرة نصية بلا موضع آمن", detail, false, null);
            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "إعادة التخطيط لتحديد إحداثيات النقر", detail,
                    smartTaskActionCount, false);
            if (smartCoordinateRefusalReplans >= 3) {
                endSmartTask("توقفت المهمة بأمان بعد ثلاث محاولات فاشلة لتحديد إحداثيات العنصر. لم أنفذ نقرة نصية أو إحداثيات مخمّنة.", false);
            } else {
                handler.postDelayed(this::requestSmartNextStep, 320L);
            }
            return;
        }
        smartCoordinateRefusalReplans = 0;
        smartLiveVisionReplans = 0;
        smartPlanningInFlight = false;
        smartCurrentActionLabel = next.optString("label", next.optString("type", "إجراء"));
        try { smartCurrentActionDefinition = new JSONObject(next.toString()); }
        catch (JSONException e) { smartCurrentActionDefinition = null; }
        smartActionGoalRevision = smartGoalRevision;
        BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "action_planned", smartTaskActionCount + 1, smartCurrentActionLabel,
                (decisionReason == null ? "" : decisionReason), false, next);
        BotnaAgentStore.appendStep(this, smartTaskId, next);
        BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "الإجراء التالي: " + smartCurrentActionLabel,
                ((decisionReason == null || decisionReason.isEmpty()) ? "اختير هذا الإجراء بناءً على لقطة الشاشة الحالية." : decisionReason)
                        + "\nتعريف الإجراء المحفوظ للتعديل: " + next.toString(), smartTaskActionCount + 1, true);
        currentScriptId = BotnaAgentStore.scriptId(this, smartTaskId);
        if (!currentScriptId.isEmpty()) BotnaStore.setActiveId(this, currentScriptId);
        executionSteps = new JSONArray().put(next);
        currentStepIndex = 0; currentBranches.clear(); currentLoops.clear();
        lastStepSuccess = true; executionGeneration++; running = true; paused = false; executionInFlight = false;
        panelWasVisibleAtRunStart = false;
        report("البوت الذكي ينفّذ الإجراء " + (smartTaskActionCount + 1) + ": " + smartCurrentActionLabel);
        showRunControls(); runStep();
    }

    private JSONObject preferCoordinateTapWhenPossible(JSONObject original) {
        if (!smartTaskActive || original == null || !TYPE_TEXT.equals(original.optString("type", ""))) return original;
        String target = original.optString("value", "").trim();
        if (target.isEmpty()) {
            BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "coordinate_tap_unavailable", smartTaskActionCount + 1,
                    original.optString("label", "نقر بالموقع"), "إجراء النقر النصي لا يحتوي اسم هدف يمكن تحويله إلى حدود مرئية؛ رُفض دون تنفيذ.", false, original);
            return null;
        }
        Rect bounds = findVisibleTextBounds(target);
        if (bounds == null || bounds.width() < 2 || bounds.height() < 2) {
            BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "coordinate_tap_unavailable", smartTaskActionCount + 1,
                    original.optString("label", "نقر بالموقع"), "تعذر إيجاد حدود مرئية للعنصر؛ رُفضت نقرة النص وفق سياسة النقر بالإحداثيات فقط.", false, original);
            return null;
        }
        android.util.DisplayMetrics metrics = realScreenMetrics();
        if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0 || bounds.centerX() < 0 || bounds.centerY() < 0
                || bounds.centerX() >= metrics.widthPixels || bounds.centerY() >= metrics.heightPixels) {
            BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "coordinate_tap_unavailable", smartTaskActionCount + 1,
                    original.optString("label", "نقر بالموقع"), "إحداثيات حدود العنصر خارج أبعاد الشاشة؛ رُفض التنفيذ دون نقر نصي بديل.", false, original);
            return null;
        }
        int xp = Math.max(0, Math.min(100, Math.round(bounds.centerX() * 100f / metrics.widthPixels)));
        int yp = Math.max(0, Math.min(100, Math.round(bounds.centerY() * 100f / metrics.heightPixels)));
        try {
            JSONObject converted = new JSONObject(original.toString());
            converted.put("type", TYPE_TAP); converted.put("xPercent", xp); converted.put("yPercent", yp);
            converted.put("coordinateOrigin", "accessibility_node_bounds"); converted.put("targetTextHint", target);
            converted.put("label", "نقر بالموقع " + xp + "%، " + yp + "% لتحديد العنصر المرئي: " + target);
            converted.remove("value");
            BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "coordinate_tap_selected", smartTaskActionCount + 1,
                    converted.optString("label", "نقر بالموقع"), "حُوّل النقر الدلالي إلى إيماءة لمس على مركز حدود العنصر المرئي.", true, converted);
            return converted;
        } catch (Exception e) {
            BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "coordinate_tap_unavailable", smartTaskActionCount + 1,
                    original.optString("label", "نقر بالموقع"), "تعذر تحويل العنصر إلى نقرة إحداثية: " + e.getClass().getSimpleName(), false, original);
            return null;
        }
    }

    private Rect findVisibleTextBounds(String labels) {
        AccessibilityNodeInfo root = findTargetRoot();
        if (root == null) return null;
        Rect[] bestBounds = new Rect[1];
        int[] bestScore = new int[]{-1};
        int[] visited = new int[]{0};
        try {
            searchVisibleTextBounds(root, labels == null ? new String[0] : labels.split("\\|"), bestBounds, bestScore, visited, 0);
            return bestBounds[0] == null ? null : new Rect(bestBounds[0]);
        } finally { root.recycle(); }
    }

    private void searchVisibleTextBounds(AccessibilityNodeInfo node, String[] targets, Rect[] bestBounds,
                                         int[] bestScore, int[] visited, int depth) {
        if (node == null || depth > 24 || visited[0]++ > 750) return;
        try {
            if (node.isVisibleToUser()) {
                CharSequence rawText = node.getText();
                CharSequence rawDescription = node.getContentDescription();
                String nodeText = BotnaArabicDialect.normalize((rawText == null ? "" : rawText.toString()) + " " + (rawDescription == null ? "" : rawDescription.toString()));
                if (!nodeText.trim().isEmpty()) {
                    int score = -1;
                    for (String item : targets) {
                        String target = BotnaArabicDialect.normalize(item == null ? "" : item.trim());
                        if (target.isEmpty()) continue;
                        if (nodeText.equals(target)) score = Math.max(score, 120);
                        else if (nodeText.contains(target)) score = Math.max(score, 85);
                        else if (target.contains(nodeText) && nodeText.length() >= 4) score = Math.max(score, 35);
                    }
                    if (score >= 0) {
                        if (node.isClickable()) score += 30;
                        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                        if (bounds.width() > 1 && bounds.height() > 1 && score > bestScore[0]) { bestScore[0] = score; bestBounds[0] = new Rect(bounds); }
                    }
                }
            }
            for (int i = 0; i < node.getChildCount() && visited[0] <= 750; i++) {
                AccessibilityNodeInfo child = null;
                try { child = node.getChild(i); if (child != null) searchVisibleTextBounds(child, targets, bestBounds, bestScore, visited, depth + 1); }
                catch (Exception ignored) { }
                finally { if (child != null) child.recycle(); }
            }
        } catch (Exception ignored) { }
    }

    private interface ExternalStillFrameCallback {
        void onResult(String path, String error);
    }

    private boolean shouldCaptureExternalActionFrames() {
        return smartTaskActive && smartTemporalFramesEnabled && !smartLiveBurstEnabled && BotnaExternalAppAgent.enabled(this)
                && BotnaExternalAppAgent.isSupportedPackage(externalAgentPackage)
                && isValidExternalTargetPackage(externalTargetPackage);
    }

    private void executeExternalActionWithTemporalFrames(Runnable execution) {
        if (!shouldCaptureExternalActionFrames() || execution == null) {
            if (execution != null) execution.run();
            return;
        }
        if (externalTemporalCaptureInProgress) {
            failExternalAgent("توقفت المهمة بأمان لأن تصوير الإجراء السابق لم يكتمل؛ لم أبدأ سلسلة صور متداخلة قد تربك تحليل الذكاء الاصطناعي.");
            return;
        }
        externalTemporalCaptureInProgress = true;
        int token = ++externalTemporalCaptureToken;
        externalTemporalFramePrefix = "botna_temporal_" + safeFileToken(smartTaskId) + "_" + (smartTaskActionCount + 1) + "_" + System.currentTimeMillis();
        externalTemporalBeforePath = "";
        externalTemporalDuringPath = "";
        externalTemporalAfterPath = "";
        externalTemporalDuringCaptureFinished = false;
        externalTemporalDuringCaptureAttempts = 0;
        externalTemporalAfterWaitAttempts = 0;
        captureExternalTemporalBeforeFrame(token, execution, 1);
    }

    private void captureExternalTemporalBeforeFrame(int token, Runnable execution, int attempt) {
        if (!isCurrentExternalTemporalCapture(token)) return;
        requestExternalTemporalStillFrame("01_before", (path, error) -> {
            if (!isCurrentExternalTemporalCapture(token)) {
                deletePathQuietly(path);
                return;
            }
            if (path == null || path.isEmpty()) {
                if (attempt < 4) {
                    handler.postDelayed(() -> captureExternalTemporalBeforeFrame(token, execution, attempt + 1), 260L * attempt);
                    return;
                }
                clearExternalTemporalCapture(true);
                failExternalAgent("تعذر التقاط الصورة الأولى قبل تنفيذ الإجراء بعد أربع محاولات. لم ألغِ نظام الصور الثلاث ولم أنفذ الإجراء بلا توثيق قبلي. "
                        + (error == null ? "" : error));
                return;
            }
            externalTemporalBeforePath = path;
            execution.run();
            handler.postDelayed(() -> captureExternalTemporalDuringFrame(token), 160L);
        });
    }

    private boolean isCurrentExternalTemporalCapture(int token) {
        return smartTaskActive && externalTemporalCaptureInProgress && token == externalTemporalCaptureToken;
    }

    private void requestExternalTemporalStillFrame(String suffix, ExternalStillFrameCallback callback) {
        if (callback == null) return;
        if (!smartTaskActive || smartLiveBurstEnabled || !externalTemporalCaptureInProgress) {
            callback.onResult("", "طلب التقاط الصورة لم يعد تابعًا لمهمة نشطة.");
            return;
        }
        if (!externalTemporalStillRequestId.isEmpty()) {
            callback.onResult("", "لا يزال طلب التقاط صورة الشاشة السابق قيد التنفيذ.");
            return;
        }
        String requestId = safeFileToken(smartTaskId) + "_" + externalTemporalCaptureToken + "_" + suffix + "_" + System.currentTimeMillis();
        File directory = new File(new File(getFilesDir(), "bot_studio_files"), "ai_share");
        if (!directory.exists() && !directory.mkdirs()) {
            callback.onResult("", "تعذر إنشاء مجلد صور التحليل.");
            return;
        }
        File output = new File(directory, externalTemporalFramePrefix + "_" + suffix + ".jpg");
        externalTemporalStillRequestId = requestId;
        externalTemporalStillCallback = callback;
        externalTemporalStillTimeout = () -> {
            if (!requestId.equals(externalTemporalStillRequestId)) return;
            ExternalStillFrameCallback pending = externalTemporalStillCallback;
            clearExternalTemporalStillRequest();
            if (pending != null) pending.onResult("", "انتهت مهلة التقاط صورة الشاشة.");
        };
        handler.postDelayed(externalTemporalStillTimeout, 8000L);
        takeScreenshot((bitmap, screenshotError) -> {
            if (!requestId.equals(externalTemporalStillRequestId)) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                return;
            }
            if (bitmap == null) {
                ExternalStillFrameCallback pending = externalTemporalStillCallback;
                clearExternalTemporalStillRequest();
                if (pending != null) pending.onResult("", screenshotError == null ? "تعذر التقاط صورة الشاشة." : screenshotError);
                return;
            }
            try {
                screenshotShareExecutor.execute(() -> {
                    String failure = "";
                    try (FileOutputStream stream = new FileOutputStream(output, false)) {
                        if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 94, stream)) throw new IllegalStateException("تعذر ضغط الصورة.");
                        stream.flush();
                        stream.getFD().sync();
                    } catch (Exception e) {
                        failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    } finally {
                        if (!bitmap.isRecycled()) bitmap.recycle();
                    }
                    String resultFailure = failure;
                    handler.post(() -> {
                        if (!requestId.equals(externalTemporalStillRequestId)) {
                            deletePathQuietly(output.getAbsolutePath());
                            return;
                        }
                        ExternalStillFrameCallback pending = externalTemporalStillCallback;
                        clearExternalTemporalStillRequest();
                        if (!resultFailure.isEmpty() || !output.isFile() || output.length() <= 0L) {
                            deletePathQuietly(output.getAbsolutePath());
                            if (pending != null) pending.onResult("", resultFailure.isEmpty() ? "ملف الصورة الناتج فارغ." : resultFailure);
                        } else if (pending != null) pending.onResult(output.getAbsolutePath(), "");
                    });
                });
            } catch (Exception e) {
                if (!bitmap.isRecycled()) bitmap.recycle();
                ExternalStillFrameCallback pending = externalTemporalStillCallback;
                clearExternalTemporalStillRequest();
                if (pending != null) pending.onResult("", e.getClass().getSimpleName());
            }
        }, "إطار زمني " + suffix, false);
    }

    private void clearExternalTemporalStillRequest() {
        if (externalTemporalStillTimeout != null) handler.removeCallbacks(externalTemporalStillTimeout);
        externalTemporalStillTimeout = null;
        externalTemporalStillRequestId = "";
        externalTemporalStillCallback = null;
    }

    private void captureExternalTemporalDuringFrame(int token) {
        if (!isCurrentExternalTemporalCapture(token) || externalTemporalDuringCaptureFinished) return;
        externalTemporalDuringCaptureAttempts++;
        int attempt = externalTemporalDuringCaptureAttempts;
        String suffix = attempt == 1 ? "02_during" : "02_during_retry_" + attempt;
        requestExternalTemporalStillFrame(suffix, (path, error) -> {
            if (!isCurrentExternalTemporalCapture(token)) {
                deletePathQuietly(path);
                return;
            }
            if (path != null && !path.isEmpty()) {
                externalTemporalDuringPath = path;
                externalTemporalDuringCaptureFinished = true;
                return;
            }
            if (externalTemporalDuringCaptureAttempts < 5) {
                handler.postDelayed(() -> captureExternalTemporalDuringFrame(token), 180L * externalTemporalDuringCaptureAttempts);
            } else {
                externalTemporalDuringCaptureFinished = true;
                externalTemporalDuringPath = "";
            }
        });
    }

    private void captureExternalTemporalDuringRecovery(int token, int attempt) {
        if (!isCurrentExternalTemporalCapture(token)) return;
        requestExternalTemporalStillFrame("02_during_recovery_" + attempt, (path, error) -> {
            if (!isCurrentExternalTemporalCapture(token)) {
                deletePathQuietly(path);
                return;
            }
            if (path != null && !path.isEmpty()) {
                externalTemporalDuringPath = path;
                externalTemporalDuringCaptureFinished = true;
                captureExternalTemporalAfterFrame(token);
                return;
            }
            if (attempt < 3) {
                handler.postDelayed(() -> captureExternalTemporalDuringRecovery(token, attempt + 1), 300L * attempt);
                return;
            }
            clearExternalTemporalCapture(true);
            failExternalAgent("تعذر التقاط الصورة الثانية أثناء تنفيذ الإجراء بعد خمس محاولات أصلية وثلاث محاولات استعادة. لم أوقف الميزة تلقائيًا ولم أرسل صورة واحدة بديلة. "
                    + (error == null ? "" : error));
        });
    }

    private void captureExternalTemporalAfterFrame(int token) {
        if (!isCurrentExternalTemporalCapture(token)) return;
        if (!externalTemporalDuringCaptureFinished && externalTemporalAfterWaitAttempts < 120) {
            externalTemporalAfterWaitAttempts++;
            handler.postDelayed(() -> captureExternalTemporalAfterFrame(token), 100L);
            return;
        }
        if (externalTemporalBeforePath.isEmpty()) {
            clearExternalTemporalCapture(true);
            failExternalAgent("فقد Botna مسار الصورة الأولى قبل الإجراء؛ أوقف الخطوة دون إلغاء نمط الصور الثلاث.");
            return;
        }
        if (externalTemporalDuringPath.isEmpty()) {
            captureExternalTemporalDuringRecovery(token, 1);
            return;
        }
        captureExternalTemporalAfterFrameAttempt(token, 1);
    }

    private void captureExternalTemporalAfterFrameAttempt(int token, int attempt) {
        if (!isCurrentExternalTemporalCapture(token)) return;
        String suffix = attempt == 1 ? "03_after" : "03_after_retry_" + attempt;
        requestExternalTemporalStillFrame(suffix, (path, error) -> {
            if (!isCurrentExternalTemporalCapture(token)) {
                deletePathQuietly(path);
                return;
            }
            if (path == null || path.isEmpty()) {
                if (attempt < 4) {
                    handler.postDelayed(() -> captureExternalTemporalAfterFrameAttempt(token, attempt + 1), 300L * attempt);
                    return;
                }
                clearExternalTemporalCapture(true);
                failExternalAgent("تعذر التقاط الصورة الثالثة بعد الإجراء بعد أربع محاولات. حافظ Botna على نمط الصور الثلاث ولم يستبدله بلقطة واحدة. "
                        + (error == null ? "" : error));
                return;
            }
            externalTemporalAfterPath = path;
            synchronized (pendingExternalTemporalFramePaths) {
                pendingExternalTemporalFramePaths.clear();
                pendingExternalTemporalFramePaths.add(externalTemporalBeforePath);
                pendingExternalTemporalFramePaths.add(externalTemporalDuringPath);
                pendingExternalTemporalFramePaths.add(externalTemporalAfterPath);
            }
            externalTemporalCaptureInProgress = false;
            externalTemporalFramePrefix = "";
            externalTemporalBeforePath = "";
            externalTemporalDuringPath = "";
            externalTemporalAfterPath = "";
            externalTemporalDuringCaptureFinished = false;
            handler.postDelayed(this::requestSmartNextStep, 420L);
        });
    }

    private void clearExternalTemporalCapture(boolean deleteFiles) {
        clearExternalTemporalStillRequest();
        if (deleteFiles) {
            deletePathQuietly(externalTemporalBeforePath);
            deletePathQuietly(externalTemporalDuringPath);
            deletePathQuietly(externalTemporalAfterPath);
        }
        externalTemporalCaptureInProgress = false;
        externalTemporalFramePrefix = "";
        externalTemporalBeforePath = "";
        externalTemporalDuringPath = "";
        externalTemporalAfterPath = "";
        externalTemporalDuringCaptureFinished = false;
        externalTemporalDuringCaptureAttempts = 0;
        externalTemporalAfterWaitAttempts = 0;
    }

    private void deletePathQuietly(String path) {
        if (path == null || path.isEmpty()) return;
        try { new File(path).delete(); } catch (Exception ignored) { }
    }

    private ArrayList<String> takePendingExternalTemporalFramePaths() {
        ArrayList<String> result = new ArrayList<>();
        synchronized (pendingExternalTemporalFramePaths) {
            result.addAll(pendingExternalTemporalFramePaths);
            pendingExternalTemporalFramePaths.clear();
        }
        return result;
    }

    private void cleanupExternalTemporalPaths(List<String> paths) {
        if (paths == null) return;
        for (String path : paths) deletePathQuietly(path);
        paths.clear();
    }

    private void requestSensitiveStepApproval(JSONObject step, String decisionReason) {
        if (!smartTaskActive || step == null) return;
        String label = step.optString("label", step.optString("type", "إجراء حساس"));
        String body = "اقترح الوكيل الإجراء التالي، وقد يكون حساسًا أو غير قابل للتراجع:\n\n" + label
                + "\n\nالتفاصيل: " + step.toString()
                + "\n\nسبب الاختيار: " + (decisionReason == null ? "" : decisionReason)
                + "\n\nلن يُنفذ الإجراء إلا بعد موافقتك.";
        BotnaAgentStore.addMessage(this, smartTaskId, "system", "بانتظار موافقة يدوية", body, smartTaskActionCount + 1, false);
        report("توقف الوكيل بانتظار موافقتك على إجراء حساس.");
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("تأكيد إجراء حساس")
                .setMessage(body).setPositiveButton("موافقة وتنفيذ", null).setNegativeButton("إيقاف المهمة", null).create();
        if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                dialog.dismiss();
                BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, "running");
                if (shouldCaptureExternalActionFrames()) executeExternalActionWithTemporalFrames(() -> executeSmartPlannedAction(step, decisionReason));
                else executeSmartPlannedAction(step, decisionReason);
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                dialog.dismiss();
                endSmartTask("أوقف المستخدم المهمة قبل تنفيذ الإجراء الحساس.", false);
            });
        });
        try { dialog.show(); } catch (Exception e) {
            BotnaAgentStore.addMessage(this, smartTaskId, "system", "تعذر عرض طلب الموافقة", "لم يظهر مربع الموافقة؛ أوقف Botna المهمة لمنع تنفيذ الإجراء الحساس دون موافقة.", smartTaskActionCount, false);
            endSmartTask("تعذر عرض تأكيد الإجراء الحساس؛ لم يتم تنفيذه.", false);
        }
    }

    private String memoryActionType(String label) {
        String s = String.valueOf(label).toLowerCase(Locale.ROOT);
        if (s.contains("صورة") || s.contains("image")) return "IMAGE_MATCH";
        if (s.contains("نص") || s.contains("نقر") || s.contains("ضغط") || s.contains("text")) return "TEXT_CLICK";
        if (s.contains("انتظار") || s.contains("انتظر") || s.contains("wait") || s.contains("حقل")) return "WAIT";
        if (s.contains("إحداث") || s.contains("احداث") || s.contains("موضع") || s.contains("coordinate")) return "TAP_COORD";
        return "ACTION";
    }

    private void endSmartTask(String message, boolean reportedComplete) {
        boolean wasSmart = smartTaskActive;
        clearExternalTemporalCapture(true);
        cleanupExternalTemporalPaths(takePendingExternalTemporalFramePaths());
        if (wasSmart && !smartTaskId.isEmpty()) BotnaTaskEvidenceStore.recordEvent(this, smartTaskId, "task_finished", smartTaskActionCount,
                reportedComplete ? "اكتملت المهمة" : "توقفت المهمة", (message == null ? "" : message), reportedComplete, null);
        if (screenCaptureServiceStarted) {
            try {
                Intent stopCapture = new Intent(this, BotnaScreenCaptureService.class);
                stopCapture.setAction(BotnaScreenCaptureService.ACTION_STOP_CAPTURE);
                startService(stopCapture);
            } catch (Exception ignored) { }
        }
        screenCapturePermissionRequestInFlight = false;
        screenCaptureServiceReady = false;
        screenCaptureServiceStarted = false;
        externalLiveClipInFlight = false;
        externalLiveClipRequestId = "";
        externalLiveClipTaskId = "";
        externalLiveClipPrompt = "";
        externalLiveClipGoal = "";
        handler.removeCallbacks(directLiveVisionRunnable);
        smartPlanningInFlight = false; smartLiveVisionChangedDuringPlanning = false; liveVisionCaptureInFlight = false; smartLiveVisionReplans = 0; smartCoordinateRefusalReplans = 0; smartPlanningScreenFingerprint = 0L;
        clearPendingSmartTaskIfMatches(smartTaskId, smartTaskGoal);
        smartTaskActive = false; smartExternalSequenceActive = false; smartExternalSequenceExecutedCount = 0; running = false; paused = false; executionInFlight = false;
        getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit().putBoolean(PREF_AGENT_TASK_ACTIVE, false).putBoolean(PREF_AGENT_SCREEN_AVAILABLE, false).apply();
        removeView(smartPlanningControls); smartPlanningControls = null;
        executionGeneration++; currentBranches.clear(); currentLoops.clear(); executionSteps = new JSONArray();
        removeView(runControls); runControls = null; runControlsParams = null; runControlsExpanded = false;
        removeView(runStatusView); runStatusView = null; runStatusParams = null;
        if (wasSmart) {
            String finalStatus = reportedComplete ? "مكتملة" : "توقفت/فشلت";
            String lowerEndMessage = message == null ? "" : message.toLowerCase(Locale.ROOT);
            String checkpointState = reportedComplete ? "completed" : ((lowerEndMessage.contains("يدويًا") || lowerEndMessage.contains("المستخدم")) ? "paused" : "failed");
            try { BotStudioStore.get(this).saveCheckpoint(smartTaskId, smartTaskGoal, currentScriptId, smartTaskActionCount, smartTaskHistory, checkpointState); } catch (Exception ignored) { }
            BotnaLogStore.add(this, "smart_agent", smartTaskActionCount, "إنهاء المهمة",
                    reportedComplete ? "اعتُبرت المهمة مكتملة من الوكيل." : "توقفت المهمة قبل اكتمالها.", message + "\nالهدف: " + smartTaskGoal, null);
            BotnaAgentStore.setStatus(this, smartTaskId, finalStatus, message);
            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", reportedComplete ? "انتهت المهمة" : "توقفت المهمة",
                    message + "\nالهدف: " + smartTaskGoal + "\nعدد الإجراءات: " + smartTaskActionCount, smartTaskActionCount, reportedComplete);
            String studioBotId = BotStudioStore.botForTask(this, smartTaskId);
            if (!studioBotId.isEmpty()) {
                String conversationResult = (reportedComplete ? "✅ تمت المهمة بنجاح" : "⚠️ توقفت المهمة أو فشلت")
                        + "\nالهدف: " + smartTaskGoal + "\nالنتيجة والتفاصيل: " + (message == null ? "لا توجد تفاصيل إضافية." : message)
                        + "\nعدد الإجراءات المنفذة: " + smartTaskActionCount
                        + (reportedComplete ? "\nراجع الشاشة للتأكد من النتيجة النهائية." : "\nسبب التوقف مذكور أعلاه؛ لم يُعلن النجاح.");
                BotStudioStore.get(this).addMessage(studioBotId, "assistant", conversationResult, "", BotnaAICompiler.model(this));
            }
        }
        smartTaskGoal = ""; smartTaskId = ""; smartCurrentActionLabel = ""; smartCurrentActionDefinition = null;
        smartTaskHistory = new JSONArray(); smartTaskActionCount = 0; smartTaskWasResumed = false;
        smartLastScreenshotFingerprint = 0L; smartRepeatedActionReplans = 0; smartGoalRevision++; smartActionGoalRevision = smartGoalRevision;
        BotnaAgentKeepAliveService.stop(this);
        report((reportedComplete ? "انتهت مهمة البوت الذكي: " : "توقف البوت الذكي: ") + message);
        showBubble(); if (panelWasVisibleAtRunStart) showPanel();
    }

    private void logFailureWithScreenshot(String source, int stepIndex, String stepLabel,
                                          String message, String detail, boolean restoreUi) {
        if (!BotnaLogStore.screenshotsEnabled(this) || !BotnaVisionEngine.shouldCaptureFailureSnapshot(this)) {
            BotnaLogStore.add(this, source, stepIndex, stepLabel, message, detail, null); return;
        }
        boolean restorePanel = panelVisible;
        boolean restoreBubble = bubble != null;
        boolean restoreControls = running;
        hidePanel(); removeView(bubble); bubble = null;
        removeView(runControls); runControls = null;
        removeView(runStatusView); runStatusView = null; runStatusParams = null;
        handler.postDelayed(() -> takeScreenshot((bitmap, error) -> {
            String fullDetail = detail == null ? "" : detail;
            if (error != null) fullDetail += "\nتعذر حفظ لقطة التشخيص: " + error;
            BotnaLogStore.add(this, source, stepIndex, stepLabel, message, fullDetail, bitmap);
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            if (restoreUi) {
                if (running || restoreControls) showRunControls();
                else { if (restoreBubble || !smartTaskActive) showBubble(); if (restorePanel && !smartTaskActive) showPanel(); }
            }
        }), 160);
    }

    private void saveResult(String message) { String stamp=new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new java.util.Date()); getSharedPreferences(BotnaStore.PREFS,MODE_PRIVATE).edit().putString(BotnaStore.KEY_LAST_RESULT,stamp+" — "+message).apply(); }
    private String getLastResult() { return getSharedPreferences(BotnaStore.PREFS,MODE_PRIVATE).getString(BotnaStore.KEY_LAST_RESULT,"جاهز."); }
    private void report(String message) {
        saveResult(message);
        if (resultLabel != null) resultLabel.setText(getLastResult());
        updateRunStatus();
    }
    private void putString(JSONObject o,String k,String v){try{o.put(k,v);}catch(JSONException ignored){}}
    private void putInt(JSONObject o,String k,int v){try{o.put(k,v);}catch(JSONException ignored){}}
    private void putBoolean(JSONObject o,String k,boolean v){try{o.put(k,v);}catch(JSONException ignored){}}

    private Bitmap readTemplate(String name) {
        if(name==null||name.contains("/")||name.contains("..")) return null;
        File target = new File(new File(getFilesDir(), "botna_targets"), name);
        File library = new File(new File(getFilesDir(), "botna_library"), name);
        File file = target.isFile() ? target : library;
        if(!file.isFile()) return null;
        try { return BitmapFactoryCompat.decode(file); } catch(Exception e) { return null; }
    }
    private static final class BitmapFactoryCompat {
        static Bitmap decode(File f) { return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath()); }
    }
}
