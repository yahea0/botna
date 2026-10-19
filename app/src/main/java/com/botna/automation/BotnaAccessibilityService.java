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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
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
    public static final String ACTION_OPEN_AGENT_SCRIPT = "com.botna.automation.OPEN_AGENT_SCRIPT";
    public static final String ACTION_RUN_AGENT_SCRIPT = "com.botna.automation.RUN_AGENT_SCRIPT";
    public static final String PREF_PENDING_SMART_TASK = "pending_smart_task_v1";
    public static final String PREF_PENDING_SMART_TASK_ID = "pending_smart_task_id_v1";

    /** Detect an explicit Internet download-speed test request that can run locally without AI quota. */
    public static boolean isLocalSpeedTestRequest(String source) {
        if (source == null) return false;
        String value = source.toLowerCase(Locale.ROOT)
                .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
                .replace('ى', 'ي').replace('ة', 'ه');
        return value.contains("سرع") && (value.contains("نت") || value.contains("انترنت")
                || value.contains("internet") || value.contains("wifi") || value.contains("واي فاي"));
    }
    private static final int SMART_TASK_MAX_ACTIONS = 40;
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

    private static final int CROP_TARGET = 0;
    private static final int CROP_SEARCH_REGION = 1;
    private static final int CROP_LIBRARY_IMAGE = 2;
    private static final int CROP_LIBRARY_SEARCH_REGION = 3;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService matcherExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService aiExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
    private WindowManager.LayoutParams runControlsParams;
    private WindowManager wm;
    private View bubble;
    private View panel;
    private View runControls;
    private TextView runStatusView;
    private WindowManager.LayoutParams runStatusParams;
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
    // Search catalogue is rebuilt with the panel so each result always invokes the current command action.
    private final List<CommandEntry> commandCatalog = new ArrayList<>();
    private int lastActionResult = 0;
    private int panelRestoreY;
    private String panelScriptId = "";
    private AlertDialog compiledBlockDialog;
    private View smartPlanningControls;
    private boolean smartTaskActive;
    private boolean smartTaskReceiverRegistered;
    private String smartTaskGoal = "";
    private String smartTaskId = "";
    private String smartCurrentActionLabel = "";
    private JSONArray smartTaskHistory = new JSONArray();
    private int smartTaskActionCount;
    private BroadcastReceiver smartTaskReceiver;

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ensureScript();
        registerSmartTaskReceiver();
        showBubble();
        saveResult("Botna جاهز. اضغط B لفتح لوحة الأتمتة.");
        String pending = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getString(PREF_PENDING_SMART_TASK, "");
        String pendingId = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getString(PREF_PENDING_SMART_TASK_ID, "");
        if (pending != null && !pending.trim().isEmpty()) {
            getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit().remove(PREF_PENDING_SMART_TASK).remove(PREF_PENDING_SMART_TASK_ID).apply();
            final String taskId = pendingId == null ? "" : pendingId;
            handler.postDelayed(() -> startSmartTask(pending, taskId), 500);
        }
    }

    private void registerSmartTaskReceiver() {
        if (smartTaskReceiverRegistered) return;
        smartTaskReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();
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
                String task = intent.getStringExtra("task");
                String taskId = intent.getStringExtra("agentTaskId");
                if (task == null || task.trim().isEmpty()) task = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getString(PREF_PENDING_SMART_TASK, "");
                if (taskId == null || taskId.trim().isEmpty()) taskId = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getString(PREF_PENDING_SMART_TASK_ID, "");
                getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit().remove(PREF_PENDING_SMART_TASK).remove(PREF_PENDING_SMART_TASK_ID).apply();
                final String requested = task == null ? "" : task.trim();
                final String requestedId = taskId == null ? "" : taskId.trim();
                if (!requested.isEmpty()) handler.post(() -> startSmartTask(requested, requestedId));
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_START_SMART_TASK);
        filter.addAction(ACTION_OPEN_AGENT_SCRIPT);
        filter.addAction(ACTION_RUN_AGENT_SCRIPT);
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(smartTaskReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(smartTaskReceiver, filter);
            smartTaskReceiverRegistered = true;
        } catch (Exception ignored) { smartTaskReceiverRegistered = false; }
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

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { saveResult("تمت مقاطعة الخدمة."); }

    @Override public void onDestroy() {
        if (smartTaskActive && smartTaskId != null && !smartTaskId.isEmpty()) {
            String reason = "انقطعت خدمة إمكانية الوصول أثناء تنفيذ المهمة؛ أُبقيت الخطوات والرسائل المحفوظة للمراجعة.";
            BotnaAgentStore.setStatus(this, smartTaskId, "انقطعت الخدمة", reason);
            BotnaAgentStore.addMessage(this, smartTaskId, "system", "انقطعت خدمة بوتنا", reason, smartTaskActionCount, false);
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
        matcherExecutor.shutdownNow();
        aiExecutor.shutdownNow();
        networkExecutor.shutdownNow();
        super.onDestroy();
    }

    // ---------------- Floating control and glass interface ----------------
    private void showBubble() {
        if (wm == null || bubble != null || running || singleStepTesting) return;
        TextView b = new TextView(this);
        b.setText("B"); b.setTextSize(20); b.setTextColor(CYAN);
        b.setGravity(Gravity.CENTER); b.setElevation(dp(12));
        b.setBackground(glassShape(Color.argb(228, 8, 13, 22), Color.argb(240, 35, 226, 244), 2, 36));
        b.setContentDescription("بوتنا: افتح لوحة الأتمتة");
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
        magnet.setDuration(190L);
        magnet.addUpdateListener(animation -> {
            if (bubble != animatedBubble || bubbleParams != animatedParams) return;
            float t = (float) animation.getAnimatedValue();
            animatedParams.x = startX + Math.round((targetX - startX) * t);
            animatedParams.y = startY + Math.round((targetY - startY) * t);
            try { wm.updateViewLayout(animatedBubble, animatedParams); } catch (Exception ignored) { }
        });
        magnet.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                if (bubble != animatedBubble || bubbleParams != animatedParams) return;
                animatedParams.x = targetX; animatedParams.y = targetY;
                try { wm.updateViewLayout(animatedBubble, animatedParams); } catch (Exception ignored) { }
                getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit()
                        .putInt("bubble_side", side).putInt("bubble_y", targetY).apply();
            }
        });
        magnet.start();
    }

    private void togglePanel() { if (panelVisible) hidePanel(); else showPanel(); }

    private void showPanel() {
        if (wm == null || panelVisible || running || singleStepTesting) return;
        commandCatalog.clear();

        // A fixed top header plus one full-content ScrollView keeps controls
        // near the top and makes every section reachable on short screens.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackground(glassShape(GLASS, Color.argb(235, 35, 226, 244), 2, 22));
        root.setElevation(dp(16));

        LinearLayout header = horizontal();
        TextView title = label("BOTNA  •  الأتمتة الذكية", 16, TEXT, true);
        title.setTextSize(13);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button hideList = button("إخفاء القائمة", false);
        hideList.setTextSize(11);
        hideList.setContentDescription("إخفاء القائمة والعودة إلى الزر العائم");
        hideList.setOnClickListener(v -> { hidePanel(); showBubble(); });
        header.addView(hideList, new LinearLayout.LayoutParams(dp(82), dp(42)));
        Button saveClose = button("حفظ", false);
        saveClose.setContentDescription("حفظ السكربت وإغلاق لوحة الأتمتة");
        saveClose.setOnClickListener(v -> confirmSaveAndClose());
        header.addView(saveClose, new LinearLayout.LayoutParams(dp(50), dp(42)));
        Button close = button("×", false);
        close.setTextSize(20);
        close.setContentDescription("إغلاق لوحة الأتمتة بعد تأكيد الحفظ");
        close.setOnClickListener(v -> confirmSaveAndClose());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(34), dp(42));
        closeLp.setMargins(dp(4), 0, 0, 0);
        header.addView(close, closeLp);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(42)));

        // Keep the essential execution controls pinned above the scrollable command list.
        LinearLayout fixedRunRow = horizontal();
        Button fixedRun = button("▶ بدء", true);
        Button fixedPause = button("Ⅱ إيقاف/استكمال", false);
        Button fixedStop = button("■ إيقاف", false);
        fixedRunRow.addView(fixedRun, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams fixedPauseLp = new LinearLayout.LayoutParams(0, dp(38), 1); fixedPauseLp.setMargins(dp(3), 0, dp(3), 0); fixedRunRow.addView(fixedPause, fixedPauseLp);
        fixedRunRow.addView(fixedStop, new LinearLayout.LayoutParams(0, dp(38), 1));
        Button searchCommands = button("⌕", false);
        searchCommands.setContentDescription("البحث عن أمر أو خطوة لإضافتها إلى السكربت");
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

        // Network diagnostics and recovery primitives are explicit script steps;
        // they never change Wi-Fi/router settings by themselves.
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

        // The steps list is inside the same ScrollView; avoid nested scroll areas.
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
                .setTitle("حفظ السكربت والخروج")
                .setMessage("سيتم حفظ التعديلات ثم إغلاق لوحة الأتمتة والزر العائم B معًا. هل تريد المتابعة؟")
                .setPositiveButton("حفظ وخروج", (d, w) -> {
                    BotnaStore.saveScripts(this, BotnaStore.scripts(this));
                    hidePanel();
                    removeView(bubble); bubble = null;
                    report("تم حفظ السكربت وإغلاق اللوحة والزر العائم.");
                })
                .setNegativeButton("إلغاء", (d, w) -> { })
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

    // ---------------- Step storage and editing ----------------
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
                .setTitle("حذف الخطوة")
                .setMessage("هل تريد حذف هذه الخطوة نهائيًا؟")
                .setPositiveButton("حذف", (d, w) -> deleteStep(index))
                .setNegativeButton("إلغاء", (d, w) -> { })
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
                    "قص هدف جديد كخطوة مستقلة", "تعديل نسبة التطابق", "منطقة البحث",
                    "إعدادات المطابقة المتقدمة", "اختبار الخطوة الآن",
                    step.optBoolean("enabled", true) ? "تعطيل الخطوة" : "تفعيل الخطوة", "حذف الخطوة"
            };
            showChoice("إعدادات خطوة الصورة", options, choice -> {
                if (choice == 0) showImageAngles(script, step, index);
                else if (choice == 1) changeImageTarget(script, step);
                else if (choice == 2) prepareCropAngle(step);
                else if (choice == 3) prepareCropStep();
                else if (choice == 4) promptNumber("نسبة التطابق (0–100)", step.optInt("threshold", 85), 0, 100, value -> {
                    putInt(step, "threshold", value); BotnaStore.saveSteps(this, script, steps); rebuildPanel();
                });
                else if (choice == 5) editSearchRegion(script, step);
                else if (choice == 6) editImageAdvancedSettings(script, step, steps);
                else if (choice == 7) testStep(index);
                else if (choice == 8) { putBoolean(step, "enabled", !step.optBoolean("enabled", true)); BotnaStore.saveSteps(this, script, steps); rebuildPanel(); }
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
        // Wait for the launcher/Recents surface to become accessible, then only tap a visible label.
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
            // Never clear an arbitrary editable field; only modify the field that currently
            // owns input focus so a saved login/form value elsewhere is not wiped by accident.
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
        // Keep the old target intact until the user successfully crops a replacement.
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
                    AlertDialog confirm = new AlertDialog.Builder(this).setTitle("حذف الزاوية")
                            .setMessage("هل تريد حذف الزاوية " + (angleIndex + 1) + " من خطوة الصورة؟")
                            .setPositiveButton("حذف", (d, w) -> {
                                JSONArray current = step.optJSONArray("images"); JSONArray next = new JSONArray();
                                if (current != null) for (int j = 0; j < current.length(); j++) if (j != angleIndex) next.put(current.optString(j));
                                try { step.put("images", next); } catch (JSONException ignored) { }
                                removeImageAngleSettings(step, filename);
                                BotnaStore.saveSteps(this, script, BotnaStore.steps(script));
                                report("تم حذف الزاوية وإعداداتها.");
                                if (imageAnglesDialog != null && imageAnglesDialog.isShowing()) imageAnglesDialog.dismiss();
                                showImageAngles(script, step, stepIndex);
                            }).setNegativeButton("إلغاء", (d, w) -> { }).create();
                    try { if (confirm.getWindow() != null) confirm.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); confirm.show(); }
                    catch (Exception e) { report("تعذر فتح تأكيد حذف الزاوية."); }
                });
                list.addView(row);
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("زوايا الصورة وإعدادات كل زاوية")
                .setView(scroller).setPositiveButton("إغلاق", (d,w)->{}).create();
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
        EditText input = new EditText(this); input.setSingleLine(true); input.setInputType(2); input.setText(String.valueOf(initial));
        showCustomDialog(title, input, () -> {
            int value = initial; try { value = Integer.parseInt(input.getText().toString().trim()); } catch (Exception ignored) { }
            callback.done(Math.max(min, Math.min(max, value)));
        });
    }

    private void showCustomDialog(String title, View content, Runnable positive) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(content)
                .setPositiveButton("حفظ", (d, w) -> positive.run())
                .setNegativeButton("إلغاء", (d, w) -> { }).create();
        try { if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); dialog.show(); }
        catch (Exception e) { Toast.makeText(this, "تعذر فتح نافذة التعديل", Toast.LENGTH_LONG).show(); }
    }

    private void showChoice(String title, String[] options, ChoiceCallback callback) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setItems(options, (d, which) -> callback.done(which)).create();
        try { if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); dialog.show(); }
        catch (Exception e) { Toast.makeText(this, "تعذر فتح قائمة الخيارات", Toast.LENGTH_LONG).show(); }
    }

    // ---------------- Custom-step editor, Arabic command compiler and image library ----------------
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
        input.setSingleLine(false); input.setMinLines(4); input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setTextSize(15); input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setTextColor(TEXT); input.setHintTextColor(MUTED);
        input.setHint("مثال: تابع صورة رقم 4 بالمكتبة، إذا وجدتها انتظر ثانيتين ثم ارجع");
        input.setBackground(glassShape(Color.rgb(8, 14, 22), CYAN, 1, 12));
        content.addView(input, new LinearLayout.LayoutParams(-1, -2));
        CheckBox includeScreenContext = new CheckBox(this);
        includeScreenContext.setText("إرفاق لقطة الشاشة الحالية للمترجم البصري");
        includeScreenContext.setTextColor(MUTED);
        includeScreenContext.setTextSize(12);
        includeScreenContext.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
        includeScreenContext.setChecked(true);
        LinearLayout.LayoutParams visionLp = wrap(); visionLp.topMargin = dp(3);
        content.addView(includeScreenContext, visionLp);
        TextView visionPrivacy = label("عند التفعيل، تُرسل لقطة الشاشة إلى مزوّد الذكاء الاصطناعي عبر الإنترنت. أوقفه في الشاشات الحساسة.", 10, MUTED, false);
        LinearLayout.LayoutParams visionPrivacyLp = wrap(); visionPrivacyLp.topMargin = dp(1);
        content.addView(visionPrivacy, visionPrivacyLp);
        // The app temporarily hides Botna overlays before taking this screenshot.
        // The provider may reject images if the selected model is text-only.
        // Save the draft while the user types. The previous translation is cleared immediately
        // after the debounce so stale executable instructions can never run against new text.
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
                ? "المزوّد المحدد: " + BotnaAICompiler.providerLabel(BotnaAICompiler.providerId(this)) + " — متاح للتحليل عبر الإنترنت."
                : "المحلّل المحلي متاح. لفهم الأوامر المركبة استخدم مزوّد API مضبوطًا من الإعدادات.", 12, MUTED, false);
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
                .setTitle("اكتب الأمر بالعربية")
                .setView(editorRoot)
                .create();
        try {
            if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                android.util.DisplayMetrics editorMetrics = realScreenMetrics();
                dialog.getWindow().setLayout(Math.min(editorMetrics.widthPixels - dp(24), dp(520)),
                        Math.min((int) (editorMetrics.heightPixels * 0.78f), dp(720)));
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

                // Persist the exact source text in its owning script before either compiler runs.
                JSONObject owningScript = BotnaStore.getScript(this, scriptId);
                JSONObject latest = findStepById(owningScript, stepId);
                if (owningScript == null || latest == null) { report("لم يتم العثور على السكربت أو الخطوة المطلوب حفظها."); return; }
                saveCustomDraft(scriptId, stepId, command);

                JSONArray librarySnapshot = BotnaStore.imageLibrary(this);
                boolean useAI = BotnaAICompiler.isConfigured(this);
                boolean useScreen = useAI && includeScreenContext.isChecked();
                // Close every Botna surface before collecting the screenshot. The editor must not
                // be shown again until compilation is finished, otherwise it contaminates vision.
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
                            JSONObject plan = BotnaAICompiler.compile(this, command, librarySnapshot, screenContext);
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
            // Drafts never retain compiled output from an older source string.
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
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(8),dp(12),dp(8));
        TextView source = label("النص الأصلي: " + latestParent.optString("instructions", ""), 12, MUTED, false);
        root.addView(source, wrap());
        ScrollView scroll = new ScrollView(this); LinearLayout blocks = new LinearLayout(this); blocks.setOrientation(LinearLayout.VERTICAL);
        if (compiled.length() == 0) {
            TextView empty = label("لا توجد أوامر مترجمة قابلة للتنفيذ. النص الأصلي محفوظ ويمكن إعادة تحليله.", 13, TEXT, false); blocks.addView(empty, wrap());
        }
        for (int i=0; i<compiled.length(); i++) {
            JSONObject child = compiled.optJSONObject(i); if (child == null) continue;
            final int index = i;
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(10),dp(8),dp(10),dp(8));
            card.setBackground(glassShape(Color.argb(220,14,27,40),Color.argb(190,35,226,244),1,12));
            TextView title = label((i+1)+". "+child.optString("label",child.optString("type","خطوة")),13,TEXT,true);
            card.addView(title,wrap());
            TextView meta = label("النوع: "+child.optString("type","-")+" • قبل: "+child.optInt("beforeDelay",0)+" ms • بعد: "+child.optInt("afterDelay",0)+" ms • "+(child.optBoolean("enabled",true)?"مفعّل":"معطّل"),10,MUTED,false);
            LinearLayout.LayoutParams metaLp=wrap();metaLp.topMargin=dp(2);card.addView(meta,metaLp);
            Button edit = button("تغيير الإعدادات",false); edit.setTextSize(11);
            LinearLayout.LayoutParams editLp=wrap();editLp.topMargin=dp(5);card.addView(edit,editLp);
            View.OnClickListener opener = v -> editCompiledBlockSettings(parentId,index);
            title.setOnClickListener(opener); card.setOnClickListener(opener); edit.setOnClickListener(opener);
            LinearLayout.LayoutParams cardLp=wrap();cardLp.topMargin=dp(6);blocks.addView(card,cardLp);
        }
        if (warnings != null && warnings.length()>0) {
            TextView warningTitle = label("ملاحظات الترجمة",12,Color.rgb(255,190,110),true); LinearLayout.LayoutParams wt=wrap();wt.topMargin=dp(8);blocks.addView(warningTitle,wt);
            for(int i=0;i<warnings.length();i++){TextView w=label("• "+warnings.optString(i),11,MUTED,false);blocks.addView(w,wrap());}
        }
        scroll.addView(blocks); LinearLayout.LayoutParams scrollLp=new LinearLayout.LayoutParams(-1,dp(390));scrollLp.topMargin=dp(8);root.addView(scroll,scrollLp);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("بلوكات الخطوة المخصصة • "+compiled.length()).setView(root).setPositiveButton("إغلاق",null)
                .setNeutralButton("إعادة تحليل النص",null).create();
        compiledBlockDialog = dialog;
        dialog.setOnDismissListener(d -> { if (compiledBlockDialog == dialog) compiledBlockDialog = null; });
        try {
            if(dialog.getWindow()!=null)dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{dialog.dismiss();JSONObject now=findStepById(ensureScript(),parentId);if(now!=null)editCustomInstructions(now);});
        } catch(Exception e){report("تعذر عرض بلوكات الخطوة المخصصة.");}
    }

    private void editCompiledBlockSettings(String parentId, int blockIndex) {
        // Remove the block list first so the settings dialog is the active accessibility overlay.
        // The step data is persisted separately, so closing the list does not lose edits.
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
        options.add(block.optBoolean("enabled",true)?"تعطيل هذا البلوك":"تفعيل هذا البلوك");
        options.add("حذف هذا البلوك");
        final String[] items=options.toArray(new String[0]);
        AlertDialog settingsDialog = new AlertDialog.Builder(this).setTitle("تغيير إعدادات البلوك "+(blockIndex+1)).setItems(items,(d,choice)->{
            d.dismiss();
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
            JSONObject current=findCompiledBlock(parentId,blockIndex);if(current==null)return;
            if(selected.startsWith("تعطيل")||selected.startsWith("تفعيل")){putBoolean(current,"enabled",!current.optBoolean("enabled",true));saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));}
            else if(selected.equals("حذف هذا البلوك")){blocks.remove(blockIndex);try{parent.put("compiledSteps",blocks);}catch(JSONException ignored){}saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));}
        }).create();
        try {
            if (settingsDialog.getWindow() != null) settingsDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            settingsDialog.show();
        } catch (Exception e) { report("تعذر فتح إعدادات البلوك؛ أُغلق المحرر القديم وأعيدت محاولة الفتح."); }
    }

    private JSONObject findCompiledBlock(String parentId,int index){JSONObject parent=findStepById(ensureScript(),parentId);JSONArray a=parent==null?null:parent.optJSONArray("compiledSteps");return a==null?null:a.optJSONObject(index);}
    private void saveCompiledBlockParent(String parentId){JSONObject script=ensureScript();JSONObject parent=findStepById(script,parentId);if(parent!=null)BotnaStore.saveSteps(this,script,BotnaStore.steps(script));}
    private void updateCompiledBlock(String parentId,int index,String key,int value){JSONObject block=findCompiledBlock(parentId,index);if(block==null)return;putInt(block,key,value);saveCompiledBlockParent(parentId);showCompiledBlockEditor(findStepById(ensureScript(),parentId));}

    private String normalizeArabicCommand(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        value = value.replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي');
        String arabicDigits="٠١٢٣٤٥٦٧٨٩";
        for(int i=0;i<arabicDigits.length();i++) value=value.replace(arabicDigits.charAt(i),(char)('0'+i));
        value=value.replaceAll("[\\u064B-\\u065F\\u0670]", "");
        return value;
    }

    private JSONArray compileCustomInstructions(String original, List<String> warnings) {
        JSONArray result = new JSONArray();
        String clean = original.replace("\r", "\n");
        // Preserve "ثم" within an inline image condition so every following action
        // remains inside the IF block instead of being emitted after END_IF.
        final String conditionalThenToken = "__botna_conditional_then__";
        clean = clean.replaceAll("(?iu)((?:اذا|إذا)\\s+(?:وجدتها|وجدته|ظهرت|لقيتها|لم\\s+تجدها|لم\\s+تجده|ما\\s+لقيتها|لم\\s+تظهر).*?)\\s+ثم\\s+", "$1 " + conditionalThenToken + " ");
        clean = clean.replaceAll("(?iu)\\s+و(?=(?:ابحث|دور|دوّر|فتش|اكتب|اغلق|اقفل|اسحب|مرر|انقر|اضغط|افتح|شغل))", ";");
        // Other sequence connectors split the natural-language request into actions.
        String[] clauses = clean.split("(?iu)\\n+|[؛;]+|\\s+(?:ثم|وبعدين|بعدين|بعد ذلك|بعدها|ومن ثم)\\s+");
        Deque<String> structure = new ArrayDeque<>();
        for (String raw : clauses) {
            String clause = raw.trim(); if (clause.isEmpty()) continue;
            String norm = normalizeArabicCommand(clause);
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
            // Inline image-success instruction, e.g. "تابع صورة رقم 4 بالمكتبة إذا وجدتها انتظر ثانيتين".
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
            if (norm.startsWith("اذا نجح") || norm.startsWith("اذا وجدت") || norm.startsWith("اذا تحقق")) {
                addCompiled(result,TYPE_IF_SUCCESS,"إذا نجح الإجراء السابق"); structure.push("IF");
                String rest=norm.replaceFirst("(?iu)^اذا\\s+(?:نجح|وجدت(?:ها|ه)?|تحقق)(?:\\s+الاجراء السابق)?\\s*[:：]?\\s*","").trim();
                if(!rest.isEmpty()) parseCustomAction(rest,result,warnings,BotnaStore.imageLibrary(this));
                continue;
            }
            if (norm.startsWith("اذا فشل") || norm.startsWith("اذا لم تجد") || norm.startsWith("اذا لم تظهر")) {
                addCompiled(result,TYPE_IF_FAIL,"إذا فشل الإجراء السابق"); structure.push("IF");
                String rest=norm.replaceFirst("(?iu)^اذا\\s+(?:فشل|لم\\s+(?:تجد|تظهر))(?:\\s+الاجراء السابق)?\\s*[:：]?\\s*","").trim();
                if(!rest.isEmpty()) parseCustomAction(rest,result,warnings,BotnaStore.imageLibrary(this));
                continue;
            }
            if (norm.equals("اذا نجح") || norm.equals("اذا فشل")) {
                addCompiled(result,norm.contains("فشل")?TYPE_IF_FAIL:TYPE_IF_SUCCESS,norm); structure.push("IF"); continue;
            }
            parseCustomAction(clause,result,warnings,BotnaStore.imageLibrary(this));
        }
        while(!structure.isEmpty()) {
            String kind=structure.pop();
            if("IF".equals(kind)) { addCompiled(result,TYPE_END_IF,"نهاية شرط أُكملت تلقائيًا"); warnings.add("أُغلقت كتلة شرط مفتوحة تلقائيًا في نهاية الخطوة المخصصة."); }
            else { addCompiled(result,TYPE_REPEAT_END,"نهاية تكرار أُكملت تلقائيًا"); warnings.add("أُغلقت كتلة تكرار مفتوحة تلقائيًا في نهاية الخطوة المخصصة."); }
        }
        return result;
    }

    private void parseCustomAction(String command, JSONArray result, List<String> warnings, JSONArray library) {
        if (command == null || command.trim().isEmpty()) return;
        String rawCommand = command.trim();
        String norm = normalizeArabicCommand(rawCommand);
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
        if (norm.contains("فحص الانترنت") || norm.contains("افحص الانترنت") || norm.contains("اختبر الاتصال") || norm.contains("اختبر الانترنت")) {
            JSONObject st = compiledStep(TYPE_CHECK_INTERNET, "فحص اتصال الإنترنت"); putInt(st,"attempts",3); putInt(st,"intervalMs",5000); putInt(st,"timeoutMs",4000); result.put(st); return;
        }
        if ((norm.contains("انتظر") || norm.contains("استنى") || norm.contains("ضل افحص") || norm.contains("ظل افحص")) && (norm.contains("يعود الانترنت") || norm.contains("يرجع الانترنت") || norm.contains("يتصل بالانترنت") || norm.contains("الانترنت يرجع") || norm.contains("الاتصال يرجع"))) {
            JSONObject st = compiledStep(TYPE_WAIT_INTERNET, "انتظار عودة الإنترنت"); putInt(st,"attempts",120); putInt(st,"intervalMs",5000); putInt(st,"timeoutMs",4000); result.put(st); return;
        }
        if (norm.contains("نبهني") || norm.contains("اظهر رسالة") || norm.contains("أظهر رسالة") || norm.contains("شغل نغمة") || norm.contains("شغل تنبيه") || norm.contains("اعرض رسالة")) {
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
        if (norm.contains("اضغط انتر") || norm.contains("اضغط enter") || norm.contains("اضغط بحث") || norm.contains("نفذ البحث") || norm.contains("زر البحث")) { addCompiled(result,TYPE_PRESS_ENTER,"تنفيذ Enter/بحث"); return; }
        if (norm.startsWith("ابحث عن ") || norm.startsWith("ابحثلي عن ") || norm.startsWith("دور على ") || norm.startsWith("دورلي على ") || norm.startsWith("فتش عن ") || norm.startsWith("دوّر على ")) {
            String value = rawCommand.replaceFirst("(?iu)^\\s*(?:ابحثلي\\s+عن|ابحث\\s+عن|دورلي\\s+على|دور\\s+على|فتش\\s+عن|دوّر\\s+على)\\s*[:：]?\\s*", "").trim().replaceAll("[«»\"']", "");
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
        java.util.regex.Matcher openApp = java.util.regex.Pattern.compile("(?iu)^(?:افتح|شغّل|شغل|ادخل على|افتحلي|افتح لي|شغللي|شغل لي)\\s+(?:تطبيق\\s+)?(.+?)\\s*$").matcher(rawCommand);
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
        warnings.add("تعذّر تحويل العبارة إلى أمر مدعوم بصورة موثوقة: «"+norm+"». لم تُنفّذ هذه العبارة؛ جرّب تحديد التطبيق أو النص أو العنصر المقصود بشكل أوضح.");
    }

    private int parseDelayMs(String norm) {
        String source = norm == null ? "" : norm;
        if (source.contains("ثانيتين")) return 2000;
        if (source.contains("ثلاث ثوان") || source.contains("ثلاثة ثوان")) return 3000;
        int waitAt = source.indexOf("انتظر");
        if (waitAt >= 0) source = source.substring(waitAt + "انتظر".length());
        else {
            waitAt = source.indexOf("انتظار");
            if (waitAt >= 0) source = source.substring(waitAt + "انتظار".length());
        }
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("(\\d+)\\s*(مللي\\s*ثانية|ms|ثانية|ثواني|دقيقة|دقائق)?").matcher(source);
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
        JSONObject step=compiledStep(TYPE_IMAGE,"مطابقة صورة المكتبة رقم "+number+": "+image.optString("name","صورة"));
        JSONArray names=new JSONArray(); names.put(image.optString("fileName"));
        try { step.put("images",names); } catch(JSONException ignored) { }
        putInt(step,"threshold",clampInt(image.optInt("threshold",85),0,100));
        putBoolean(step,"tapOnMatch",autoTap && image.optBoolean("tapOnMatch",true));
        putBoolean(step,"waitForImageOnly",false);
        putInt(step,"retries",Math.max(1,image.optInt("retries",2)));
        putInt(step,"retryDelay",clampInt(image.optInt("retryDelay",500),100,10000));
        putInt(step,"beforeDelay",clampInt(image.optInt("beforeDelay",0),0,60000));
        putInt(step,"afterDelay",clampInt(image.optInt("afterDelay",0),0,60000));
        putString(step,"searchRegionMode",image.optString("searchRegionMode","full"));
        for(String k:new String[]{"searchX","searchY","searchW","searchH","captureX","captureY","captureW","captureH"}) if(image.has(k)) putInt(step,k,image.optInt(k));
        compiled.put(step);
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

    /** Find the object in the very same JSONArray that will be persisted. */
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
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("حذف صورة المكتبة").setMessage("سيتم حذف قالب الصورة من المكتبة. الخطوات المخصصة التي تستخدمها ستعرض تحذيرًا إذا أصبحت الصورة غير متاحة. متابعة؟")
                .setPositiveButton("حذف",(d,w)->{
                    JSONArray old=getLibrary(),next=new JSONArray(); String filename="";
                    for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i); if(item!=null&&id.equals(item.optString("id"))){filename=item.optString("fileName");continue;} next.put(item);}
                    saveLibrary(next); if(!filename.isEmpty()) {File f=new File(new File(getFilesDir(),"botna_library"),filename); if(f.exists()) f.delete();}
                    report("تم حذف صورة المكتبة."); showImageLibrary();
                }).setNegativeButton("إلغاء",null).create();
        try { if(dialog.getWindow()!=null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); dialog.show(); }
        catch(Exception e) { report("تعذر عرض تأكيد حذف صورة المكتبة."); }
    }

    // ---------------- Point picker for absolute screen taps ----------------
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
        private int actionMode; // 0 point, 1 save, 2 copy, 3 cancel
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

    // ---------------- Screenshot crop with transparent resizable frame ----------------
    private void prepareCropAngle(JSONObject step) {
        if (step == null || !TYPE_IMAGE.equals(step.optString("type"))) return;
        // This is the explicit, opt-in action for adding a variant to an existing step.
        // The main “قص هدف” button below always creates an independent step.
        cropStepId = step.optString("id");
        cropCreatedNewStep = false;
        replaceImageTarget = false;
        cropPurpose = CROP_TARGET;
        beginCaptureForCrop();
    }

    private void prepareCropStep() {
        // Every “قص هدف” command creates a separate image step in sequence.
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

    private interface ScreenshotCallback { void done(Bitmap bitmap, String error); }

    private void takeScreenshot(ScreenshotCallback callback) {
        if (Build.VERSION.SDK_INT < 30) { callback.done(null, "نسخة أندرويد لا تدعم واجهة اللقطة المطلوبة"); return; }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    HardwareBuffer buffer = null;
                    Bitmap wrapped = null;
                    try {
                        buffer = result.getHardwareBuffer();
                        wrapped = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (wrapped == null) { callback.done(null, "تعذر تحويل بيانات الصورة"); return; }
                        Bitmap copy = wrapped.copy(Bitmap.Config.ARGB_8888, false);
                        callback.done(copy, null);
                    } catch (Exception e) { callback.done(null, e.getClass().getSimpleName()); }
                    finally {
                        if (wrapped != null && !wrapped.isRecycled()) wrapped.recycle();
                        if (buffer != null) buffer.close();
                    }
                }
                @Override public void onFailure(int errorCode) { callback.done(null, "رمز الخطأ " + errorCode); }
            });
        } catch (Exception e) { callback.done(null, e.getClass().getSimpleName()); }
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

                // Store a small margin around the original crop. Percentages allow the
                // area to scale with the screenshot, and the margin tolerates slight movement.
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
        // Detach the view first; never recycle a bitmap while CropSelector can still draw it.
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
        private int mode = 0; // 0 create, 1..4 corners, 5 move, 6..9 edges, 10 save, 11 cancel
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
            // Only four small visible corner handles. Edge dragging remains available
            // through invisible hit bands, avoiding decorative dots over tiny targets.
            paint.setStyle(Paint.Style.FILL); float r = 2.8f*density;
            paint.setColor(Color.argb(245, 28, 229, 244));
            c.drawCircle(rect.left,rect.top,r,paint); c.drawCircle(rect.right,rect.top,r,paint);
            c.drawCircle(rect.left,rect.bottom,r,paint); c.drawCircle(rect.right,rect.bottom,r,paint);
            // Keep the action bar at the TOP, not the bottom. A bottom toolbar
            // steals touch events from the lower crop edge and makes full-height
            // selections unreliable on devices with a gesture/navigation area.
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
            // Choose the nearest corner instead of checking corners in a fixed order.
            // This keeps tiny selections resizable when their touch radii overlap.
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
                if(handle!=0) mode=handle; // Corner/edge hit zones take priority over controls.
                else if(y>=9*density && y<=48*density && x>=getWidth()-90*density) mode=10;
                else if(y>=9*density && y<=48*density && x<=92*density) mode=11;
                else if(y>=9*density && y<=48*density && !rect.contains(x,y)) mode=12; // ignore header gestures
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

    // ---------------- Image matching and script execution ----------------
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
        report("تم الإيقاف المؤقت عند الخطوة " + (currentStepIndex + 1) + ". اضغط استكمال للمتابعة من الموضع نفسه.");
        showRunControls();
    }

    private void resumeScript() {
        if (!running || !paused) return;
        paused = false;
        report("استُكمل السكربت من الخطوة " + (currentStepIndex + 1) + ".");
        showRunControls();
        if (!executionInFlight) runStep();
    }

    private void restartScript() {
        if (!running) { runActiveScript(); return; }
        executionGeneration++; // invalidate any screenshot/matcher callback from the old run
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
        smartTaskActive = false; smartTaskGoal = ""; smartTaskId = ""; smartCurrentActionLabel = ""; smartTaskHistory = new JSONArray(); smartTaskActionCount = 0;
        removeView(smartPlanningControls); smartPlanningControls = null;
        executionGeneration++;
        running = false; paused = false; executionInFlight = false;
        currentBranches.clear(); currentLoops.clear();
        singleStepTesting = false; customTestMode = false;
        executionSteps = new JSONArray();
        removeView(runControls); runControls = null; runControlsParams = null; runControlsExpanded = false;
        removeView(runStatusView); runStatusView = null; runStatusParams = null;
        report("تم إيقاف التنفيذ نهائيًا.");
        showBubble();
        if (panelWasVisibleAtRunStart) showPanel();
    }

    private void showRunStatus() {
        if (wm == null || !running || runControlsParams == null) return;
        removeView(runStatusView); runStatusView = null;
        String stored = getLastResult();
        int divider = stored.indexOf(" — ");
        String message = divider >= 0 ? stored.substring(divider + 3) : stored;
        if (message.length() > 64) message = message.substring(0, 61) + "…";
        TextView pill = label(message, 10, TEXT, false);
        pill.setGravity(Gravity.CENTER);
        pill.setSingleLine(true);
        pill.setPadding(dp(7), 0, dp(7), 0);
        pill.setBackground(glassShape(Color.argb(220, 8, 14, 24), Color.argb(200, 35, 226, 244), 1, 13));
        int width = Math.max(dp(150), Math.min(dp(280), realScreenMetrics().widthPixels - dp(12)));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, dp(24),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = clampInt(runControlsParams.x, 0, Math.max(0, realScreenMetrics().widthPixels - width));
        params.y = Math.max(0, runControlsParams.y - dp(27));
        try { wm.addView(pill, params); runStatusView = pill; runStatusParams = params; }
        catch (Exception ignored) { runStatusView = null; runStatusParams = null; }
    }

    private void updateRunStatus() {
        if (!running || runStatusView == null) return;
        String stored = getLastResult(); int divider = stored.indexOf(" — ");
        String message = divider >= 0 ? stored.substring(divider + 3) : stored;
        if (message.length() > 64) message = message.substring(0, 61) + "…";
        runStatusView.setText(message);
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
                    if (Math.abs(dx) + Math.abs(dy) > dp(5)) dragged[0] = true;
                    if (dragged[0]) {
                        params.x = clampInt(origin[0] + Math.round(dx), 0, Math.max(0, metrics.widthPixels - boxWidth));
                        params.y = clampInt(origin[1] + Math.round(dy), 0, Math.max(0, metrics.heightPixels - boxHeight));
                        try { wm.updateViewLayout(dock, params); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
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
        try { wm.addView(dock, params); runControls = dock; runControlsParams = params; showRunStatus(); }
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
        if (paused) { executionInFlight = false; showRunControls(); return; }
        if (executionInFlight) return;
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

        // Conditional markers always keep the structure balanced. Disabling an IF
        // skips the complete conditional block; ELSE/END_IF remain structural.
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

        // Repeat blocks can be nested. Inactive or disabled loops still push a
        // frame so their END marker cannot accidentally close an outer loop.
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
        // Keep controls available during the pre-action delay. Screenshot and
        // gesture helpers hide them only at the moment they would obstruct the action.
        showRunControls();
        int beforeDelay = Math.max(0, Math.min(60000, step.optInt("beforeDelay", 0)));
        Runnable action = () -> {
            if (generation != executionGeneration || !running) return;
            if (paused) { executionInFlight = false; showRunControls(); return; }
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
            StructureFrame frame = stack.peek();
            return "هناك " + ("IF".equals(frame.type) ? "شرط" : "تكرار") + " لم تُضف له علامة نهاية. أكمل البنية قبل التشغيل.";
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
        boolean success = clickText(step.optString("value", ""));
        if (success || attempt + 1 >= retries) {
            lastStepSuccess = success;
            report(success ? "نجحت خطوة النقر على النص: " + step.optString("value")
                    : "فشلت خطوة النقر على النص بعد " + (attempt + 1) + " محاولة: " + step.optString("value"));
            scheduleAfterAction(generation, step);
            return;
        }
        int delay = Math.max(0, step.optInt("retryDelay", 500));
        handler.postDelayed(() -> executeTextClickWithRetries(step, generation, attempt + 1), delay);
    }

    private interface SpeedTestCallback { void done(boolean success, String message); }

    /** Approximate download throughput only; this is not a full ping/upload/ISP certification test. */
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
            int x = Math.max(0, Math.min(width - 1, width * step.optInt("xPercent", 50) / 100));
            int y = Math.max(0, Math.min(height - 1, height * step.optInt("yPercent", 50) / 100));
            performTap(x, y, ok -> { if (!running || generation != executionGeneration) return; lastStepSuccess = ok; report(ok ? "نجح النقر بالموقع المحدد." : "فشل النقر بالموقع المحدد."); scheduleAfterAction(generation, step); });
        } else if (TYPE_LONG_PRESS.equals(type)) {
            android.util.DisplayMetrics metrics=realScreenMetrics(); int width=metrics.widthPixels, height=metrics.heightPixels;
            int x=Math.max(0,Math.min(width-1,width*step.optInt("xPercent",50)/100));
            int y=Math.max(0,Math.min(height-1,height*step.optInt("yPercent",50)/100));
            performLongPress(x,y,step.optInt("duration",800),ok->{if(!running||generation!=executionGeneration)return;lastStepSuccess=ok;report(ok?"نجح الضغط المطوّل.":"فشل الضغط المطوّل.");scheduleAfterAction(generation,step);});
        } else if (TYPE_SWIPE.equals(type)) {
            performSwipe(step.optInt("x1", 50), step.optInt("y1", 75), step.optInt("x2", 50), step.optInt("y2", 25), step.optInt("duration", 450), ok -> {
                if (!running || generation != executionGeneration) return; lastStepSuccess = ok; report(ok ? "اكتملت حركة السحب." : "فشلت حركة السحب."); scheduleAfterAction(generation, step);
            });
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
        if (!lastStepSuccess) {
            String label = step == null ? "خطوة غير معروفة" : step.optString("label", step.optString("type", "خطوة"));
            String details = getLastResult() + "\nالنوع: " + (step == null ? "-" : step.optString("type", "-"))
                    + "\nالإعدادات: " + (step == null ? "{}" : step.toString());
            logFailureWithScreenshot("execution", currentStepIndex, label, "فشلت الخطوة أثناء التنفيذ.", details, true);
        }
        // A failed action gets a little extra time so the diagnostic screenshot can be captured before the next action changes the screen.
        int minimum = lastStepSuccess ? CONTROL_GAP_MS : 750;
        scheduleNext(generation, Math.max(minimum, configured));
    }

    private void scheduleNext(int generation, int delayMs) {
        if (generation != executionGeneration || !running) return;
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
        boolean actionSucceeded = lastStepSuccess;
        String actionLabel = smartCurrentActionLabel;
        running = false; paused = false; executionInFlight = false;
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
                event.put("result", message);
                event.put("time", System.currentTimeMillis());
                smartTaskHistory.put(event);
                while (smartTaskHistory.length() > 20) smartTaskHistory.remove(0);
            } catch (JSONException ignored) { }
            smartTaskActionCount++;
            smartCurrentActionLabel = "";
            report("الوكيل: " + (actionSucceeded ? "اكتمل الإجراء " : "فشل الإجراء ") + smartTaskActionCount + "/" + SMART_TASK_MAX_ACTIONS + " — سيعيد قراءة الشاشة ويقرر الخطوة التالية.");
            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", (actionSucceeded ? "نجح الإجراء " : "فشل الإجراء ") + smartTaskActionCount + ": " + actionLabel,
                    message + "\nسيعيد الوكيل قراءة الشاشة قبل تحديد الخطوة التالية.", smartTaskActionCount, actionSucceeded);
            if (!actionSucceeded) BotnaLogStore.add(this, "smart_agent", currentStepIndex, actionLabel,
                    "فشل إجراء داخل البوت الذكي.", message + "\nالمهمة: " + smartTaskGoal, null);
            if (smartTaskActionCount >= SMART_TASK_MAX_ACTIONS) {
                endSmartTask("توقف الوكيل بعد بلوغ الحد الوقائي " + SMART_TASK_MAX_ACTIONS + " إجراءً. راجع سجل التنفيذ أو ابدأ المهمة مجددًا.", false);
            } else {
                handler.postDelayed(this::requestSmartNextStep, 450);
            }
            return;
        }
        report(message);
        showBubble();
        if (panelWasVisibleAtRunStart) showPanel();
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
            // The screen pixels have already been captured, so controls can safely
            // reappear while OpenCV compares the bitmap in the background.
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
        removeView(runControls); runControls = null; removeView(runStatusView); runStatusView = null; runStatusParams = null;
        hidePanel(); removeView(bubble); bubble = null;
        handler.postDelayed(() -> {
            try {
                Path path = new Path(); path.moveTo(x,y); path.lineTo(x+1,y+1);
                GestureDescription gesture = new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,80)).build();
                boolean sent=dispatchGesture(gesture,new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription d){callback.done(true);}
                    @Override public void onCancelled(GestureDescription d){callback.done(false);}
                },handler);
                if(!sent)callback.done(false);
            } catch(Exception e){callback.done(false);}
        },80);
    }

    private void performLongPress(int x, int y, int duration, GestureCallback callback) {
        if (Build.VERSION.SDK_INT < 24) { callback.done(false); return; }
        removeView(runControls); runControls = null; removeView(runStatusView); runStatusView = null; runStatusParams = null;
        hidePanel(); removeView(bubble); bubble = null;
        handler.postDelayed(() -> {
            try {
                Path path = new Path(); path.moveTo(x, y); path.lineTo(x + 1, y + 1);
                GestureDescription gesture = new GestureDescription.Builder()
                        .addStroke(new GestureDescription.StrokeDescription(path, 0, Math.max(300, Math.min(5000, duration))))
                        .build();
                boolean sent = dispatchGesture(gesture, new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription d) { callback.done(true); }
                    @Override public void onCancelled(GestureDescription d) { callback.done(false); }
                }, handler);
                if (!sent) callback.done(false);
            } catch (Exception e) { callback.done(false); }
        }, 80);
    }

    private void performSwipe(int x1p, int y1p, int x2p, int y2p, int duration, GestureCallback callback) {
        if (Build.VERSION.SDK_INT < 24) { callback.done(false); return; }
        android.util.DisplayMetrics metrics=realScreenMetrics(); int width=metrics.widthPixels, height=metrics.heightPixels;
        float x1=width*Math.max(0,Math.min(100,x1p))/100f, y1=height*Math.max(0,Math.min(100,y1p))/100f;
        float x2=width*Math.max(0,Math.min(100,x2p))/100f, y2=height*Math.max(0,Math.min(100,y2p))/100f;
        removeView(runControls); runControls = null; removeView(runStatusView); runStatusView = null; runStatusParams = null;
        hidePanel(); removeView(bubble); bubble = null;
        handler.postDelayed(() -> {
            try {
                Path path=new Path(); path.moveTo(x1,y1); path.lineTo(x2,y2);
                GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,Math.max(100,Math.min(5000,duration)))).build();
                boolean sent=dispatchGesture(gesture,new GestureResultCallback(){
                    @Override public void onCompleted(GestureDescription d){callback.done(true);}
                    @Override public void onCancelled(GestureDescription d){callback.done(false);}
                },handler);
                if(!sent)callback.done(false);
            } catch(Exception e){callback.done(false);}
        },80);
    }

    private interface NetworkProbeCallback { void done(boolean reachable); }

    /** Make a real HTTP request and consume a small part of the response. The default endpoint is an expected HTTP 204 connectivity check. */
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
        // Do not require an application root: many keyboards expose the Search/Enter key
        // in their own TYPE_INPUT_METHOD window, which can still be inspected independently.
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
                    // A real click on the input usually opens the phone's keyboard before
                    // setting/pasting text, while the automation remains independent of layout.
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
            if (target == null || !target.isEditable() || !target.isEnabled()) return false;
            android.os.Bundle args = new android.os.Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value);
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true;

            // Many custom fields reject ACTION_SET_TEXT but accept paste. This fallback
            // writes full Unicode text (including Arabic) into the currently editable field.
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
        if (hasEditableInputOnScreen()) { lastStepSuccess=true; report("ظهر حقل إدخال قابل للكتابة."); scheduleAfterAction(generation,step); return; }
        if (attempt + 1 >= maxAttempts) { lastStepSuccess=false; report("لم يظهر حقل الإدخال بعد "+maxAttempts+" محاولة."); scheduleAfterAction(generation,step); return; }
        report("انتظار ظهور حقل الإدخال — محاولة "+(attempt+1)+"/"+maxAttempts);
        handler.postDelayed(()->waitForInputStep(step,generation,attempt+1,maxAttempts,intervalMs),intervalMs);
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
        if (containsTextOnScreen(target)) {
            lastStepSuccess = true; report("ظهر النص المنتظر: " + target); scheduleAfterAction(generation, step); return;
        }
        if (attempt + 1 >= maxAttempts) {
            lastStepSuccess = false; report("لم يظهر النص «" + target + "» بعد " + maxAttempts + " محاولة."); scheduleAfterAction(generation, step); return;
        }
        report("انتظار ظهور النص «" + target + "» — محاولة " + (attempt + 1) + "/" + maxAttempts);
        handler.postDelayed(() -> waitForTextStep(step, generation, attempt + 1, maxAttempts, intervalMs), intervalMs);
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

    private void readScreen() {
        AccessibilityNodeInfo root = findTargetRoot();
        if (root == null) { report("لم أستطع قراءة التطبيق خلف اللوحة. أغلق لوحة بوتنا ثم أعد المحاولة."); return; }
        List<String> values = new ArrayList<>();
        collectText(root, values, 0);
        root.recycle();
        if (values.isEmpty()) report("لم تظهر نصوص قابلة للقراءة. بعض الألعاب ترسم الأزرار داخل صورة أو مشهد Unity.");
        else report("تم العثور على " + values.size() + " نصًا: " + joinFirst(values, 14));
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

    // ---------------- Helpers and glass styling ----------------
    private void addButton(LinearLayout row, String title, Runnable action) {
        Button b = button(title, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1);
        lp.setMargins(dp(2), 0, dp(2), 0);
        row.addView(b, lp);
        if (action != null) commandCatalog.add(new CommandEntry(title, action));
        b.setOnClickListener(v -> { if (action != null) action.run(); });
    }

    /** Shows a live-filtered catalogue of the same command actions used by the visible buttons. */
    private void showCommandSearchDialog() {
        if (commandCatalog.isEmpty()) {
            report("قائمة الأوامر غير جاهزة. أغلق لوحة Botna وافتحها مجددًا ثم حاول مرة أخرى.");
            return;
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(4), dp(8), dp(4));

        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setTextSize(15);
        query.setTextColor(TEXT);
        query.setHintTextColor(MUTED);
        query.setHint("اكتب اسم الأمر، مثل: بحث، كتابة، سحب، إشعارات…");
        query.setSelectAllOnFocus(false);
        query.setBackground(glassShape(BUTTON, CYAN, 1, 12));
        query.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(query, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView count = label("كل الأوامر المتاحة", 11, MUTED, false);
        LinearLayout.LayoutParams countLp = wrap(); countLp.topMargin = dp(5); root.addView(count, countLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(results, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, dp(300));
        scrollLp.topMargin = dp(4); root.addView(scroll, scrollLp);

        final AlertDialog[] owner = new AlertDialog[1];
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("بحث عن أمر لإضافته")
                .setView(root)
                .setNegativeButton("إغلاق", null)
                .create();
        owner[0] = dialog;
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int countChanged) {
                renderCommandSearchResults(s == null ? "" : s.toString(), results, count, owner[0]);
            }
            @Override public void afterTextChanged(Editable editable) { }
        };
        query.addTextChangedListener(watcher);
        renderCommandSearchResults("", results, count, dialog);
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(520)),
                        Math.min(getResources().getDisplayMetrics().heightPixels - dp(64), dp(500)));
            }
            query.requestFocus();
            query.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(query, InputMethodManager.SHOW_IMPLICIT);
            }, 180);
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(520)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(64), dp(500)));
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
            result.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(42));
            lp.topMargin = dp(3);
            results.addView(result, lp);
            result.setOnClickListener(v -> {
                if (owner != null) owner.dismiss();
                // Let the search dialog finish closing before a command opens its own prompt/dialog.
                handler.post(() -> {
                    try { entry.action.run(); }
                    catch (RuntimeException ex) { report("تعذّر تنفيذ الأمر المحدد: " + ex.getMessage()); }
                });
            });
        }
        count.setText(matches + " أمر مطابق. اضغط على أمر لإضافته أو فتح إعداداته.");
        if (matches == 0) {
            TextView empty = label("لا توجد أوامر مطابقة. جرّب كلمة أقصر أو مرادفًا مثل: نقر، كتابة، انتظار، بحث.", 12, MUTED, false);
            empty.setPadding(dp(8), dp(12), dp(8), dp(12));
            results.addView(empty, wrap());
        }
    }

    private String commandSearchIndex(String title) {
        String normalized = normalizeCommandSearch(title);
        StringBuilder index = new StringBuilder(normalized);
        // Common Arabic variants/dialect words and English labels make the catalog easier to find.
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
    private LinearLayout horizontal() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL); return l; }
    private Button button(String title, boolean primary) {
        Button b=new Button(this); b.setText(title); b.setTextSize(12); b.setAllCaps(false); b.setPadding(dp(4),dp(3),dp(4),dp(3));
        b.setTextColor(TEXT); b.setBackground(glassShape(primary?Color.argb(95,35,226,244):BUTTON, primary?Color.argb(235,35,226,244):Color.argb(150,35,226,244),1,12)); b.setElevation(dp(5)); return b;
    }
    private TextView label(String value,int size,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL); if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD); return t;
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
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(7), dp(3), dp(7), dp(3));
        bar.setBackground(glassShape(Color.argb(238, 8, 14, 24), Color.argb(235, 35, 226, 244), 1, 16));
        TextView state = label("البوت الذكي يحلل الشاشة…", 11, TEXT, false);
        Button stop = button("إيقاف الوكيل", false);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        bar.addView(state, textLp);
        bar.addView(stop, new LinearLayout.LayoutParams(dp(100), dp(34)));
        stop.setOnClickListener(v -> stopScript());
        android.util.DisplayMetrics metrics = realScreenMetrics();
        int width = Math.min(metrics.widthPixels - dp(12), dp(310));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, dp(44),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(72);
        try { wm.addView(bar, params); smartPlanningControls = bar; }
        catch (Exception e) { smartPlanningControls = null; report("تعذر إظهار زر إيقاف الوكيل أثناء التحليل."); }
    }

    private void startSmartTask(String task) { startSmartTask(task, ""); }

    private void startSmartTask(String task, String requestedTaskId) {
        String requested = task == null ? "" : task.trim();
        if (requested.isEmpty()) return;
        if (running || singleStepTesting || smartTaskActive) {
            String reason = "هناك تنفيذ جارٍ بالفعل. أوقفه قبل بدء بوت ذكي جديد.";
            report(reason);
            BotnaLogStore.add(this, "smart_agent", -1, "بدء المهمة", "لم تبدأ المهمة الجديدة لأن تنفيذًا آخر ما زال جاريًا.", requested, null);
            if (requestedTaskId != null && !requestedTaskId.isEmpty()) BotnaAgentStore.addMessage(this, requestedTaskId, "assistant", "لم تبدأ المهمة", reason, -1, false);
            return;
        }
        if (isLocalSpeedTestRequest(requested)) {
            startLocalSpeedTestTask(requested, requestedTaskId);
            return;
        }
        if (!BotnaAICompiler.isConfigured(this)) {
            String reason = "لا يمكن بدء البوت الذكي دون مزوّد API ومفتاح محفوظ.";
            report(reason);
            BotnaLogStore.add(this, "smart_agent", -1, "بدء المهمة", "لا يوجد مزود AI مضبوط.", requested, null);
            if (requestedTaskId != null && !requestedTaskId.isEmpty()) {
                BotnaAgentStore.setStatus(this, requestedTaskId, "لم تبدأ", reason);
                BotnaAgentStore.addMessage(this, requestedTaskId, "assistant", "لم تبدأ المهمة", reason, -1, false);
            }
            return;
        }
        smartTaskId = requestedTaskId == null ? "" : requestedTaskId.trim();
        JSONObject savedAgentTask = BotnaAgentStore.getTask(this, smartTaskId);
        if (savedAgentTask == null) smartTaskId = BotnaAgentStore.createTask(this, requested);
        else if (!requested.equals(savedAgentTask.optString("goal", "")))
            BotnaAgentStore.updateGoal(this, smartTaskId, savedAgentTask.optString("name", ""), requested);
        currentScriptId = BotnaAgentStore.prepareRun(this, smartTaskId);
        if (!currentScriptId.isEmpty()) BotnaStore.setActiveId(this, currentScriptId);
        JSONObject script = ensureScript(); currentScriptId = script == null ? currentScriptId : script.optString("id", currentScriptId);
        smartTaskGoal = requested; smartTaskHistory = new JSONArray(); smartTaskActionCount = 0;
        smartCurrentActionLabel = ""; smartTaskActive = true;
        panelWasVisibleAtRunStart = panelVisible;
        report("بدأ البوت الذكي. سيحلل لقطة شاشة بعد كل إجراء (حد أقصى " + SMART_TASK_MAX_ACTIONS + " إجراءً).");
        BotnaAgentStore.setStatus(this, smartTaskId, "قيد التنفيذ", "بدأ الوكيل المهمة.");
        BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "بدأ الوكيل المهمة", "الهدف: " + requested + "\nسيحلل لقطة شاشة بعد كل إجراء، ويسجل الأفعال في سكربت مستقل.", 0, true);
        BotnaLogStore.add(this, "smart_agent", -1, "بدء المهمة", "بدأ تنفيذ مهمة تفاعلية متتابعة.", requested, null);
        requestSmartNextStep();
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
        smartCurrentActionLabel = "قياس سرعة التنزيل";
        smartTaskActive = true;
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

    private void requestSmartNextStep() {
        if (!smartTaskActive) return;
        if (smartTaskActionCount >= SMART_TASK_MAX_ACTIONS) {
            endSmartTask("انتهت المهمة عند الحد الوقائي لعدد الإجراءات.", false); return;
        }
        hideBotnaOverlaysForCleanScreenshot();
        handler.postDelayed(() -> takeScreenshot((screen, screenshotError) -> {
            if (!smartTaskActive) { if (screen != null && !screen.isRecycled()) screen.recycle(); return; }
            if (screen == null) {
                String detail = "تعذر أخذ لقطة شاشة جديدة بعد الإجراء رقم " + smartTaskActionCount + ": " + screenshotError;
                BotnaLogStore.add(this, "smart_agent", smartTaskActionCount, smartCurrentActionLabel,
                        "توقف الوكيل لأن لقطة الشاشة غير متاحة.", detail, null);
                BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "تعذر التقاط الشاشة", detail, smartTaskActionCount, false);
                endSmartTask(detail, false); return;
            }
            final Bitmap currentScreen = screen;
            showSmartPlanningControls();
            final String goal = smartTaskGoal;
            final JSONArray history;
            try { history = new JSONArray(smartTaskHistory.toString()); }
            catch (JSONException e) { if (!currentScreen.isRecycled()) currentScreen.recycle(); endSmartTask("تعذر تجهيز سجل الخطوات السابق.", false); return; }
            try {
                aiExecutor.execute(() -> {
                    JSONObject plan = null; String error = null; JSONArray executable = new JSONArray();
                    try {
                        plan = BotnaAICompiler.compileSmartNext(this, goal, history, currentScreen);
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
                    // Keep the clean current screenshot alive until the UI callback has either logged it
                    // or passed it to the next action. This associates a planning failure with the exact screen.
                    JSONObject finalPlan = plan; JSONArray finalAction = executable; String finalError = error;
                    handler.post(() -> {
                        removeView(smartPlanningControls); smartPlanningControls = null;
                        if (!smartTaskActive) { if (!currentScreen.isRecycled()) currentScreen.recycle(); return; }
                        JSONArray proposals = finalPlan == null ? null : finalPlan.optJSONArray("steps");
                        boolean done = finalPlan != null && finalPlan.optBoolean("task_done", false)
                                && (proposals == null || proposals.length() == 0);
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
                        if (finalError != null || finalAction == null || finalAction.length() == 0) {
                            String reason = finalError == null ? "لم يقدّم النموذج خطوة واحدة واضحة. تحذيرات: " + (finalPlan == null ? "لا يوجد رد" : finalPlan.optJSONArray("warnings")) : finalError;
                            BotnaLogStore.add(this, "smart_agent", smartTaskActionCount, "تخطيط الخطوة التالية",
                                    "لم يستطع الوكيل اختيار إجراء قابل للتنفيذ بأمان.", reason + "\nالمهمة: " + smartTaskGoal,
                                    BotnaLogStore.screenshotsEnabled(this) ? currentScreen : null);
                            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "توقّف التخطيط بسبب خطأ", reason + "\nالمهمة: " + smartTaskGoal, smartTaskActionCount, false);
                            if (!currentScreen.isRecycled()) currentScreen.recycle();
                            endSmartTask("توقف الوكيل بدل التخمين: " + reason, false); return;
                        }
                        JSONObject next = finalAction.optJSONObject(0);
                        if (next == null) { if (!currentScreen.isRecycled()) currentScreen.recycle(); endSmartTask("أعاد المترجم إجراءً فارغًا.", false); return; }
                        if (!currentScreen.isRecycled()) currentScreen.recycle();
                        smartCurrentActionLabel = next.optString("label", next.optString("type", "إجراء"));
                        BotnaAgentStore.appendStep(this, smartTaskId, next);
                        String decisionReason = finalPlan == null ? "" : finalPlan.optString("reason", "");
                        BotnaAgentStore.addMessage(this, smartTaskId, "assistant", "الإجراء التالي: " + smartCurrentActionLabel,
                                (decisionReason.isEmpty() ? "اختير هذا الإجراء بناءً على لقطة الشاشة الحالية." : decisionReason)
                                        + "\nتعريف الإجراء المحفوظ للتعديل: " + next.toString(), smartTaskActionCount + 1, true);
                        currentScriptId = BotnaAgentStore.scriptId(this, smartTaskId);
                        if (!currentScriptId.isEmpty()) BotnaStore.setActiveId(this, currentScriptId);
                        executionSteps = new JSONArray().put(next);
                        currentStepIndex = 0; currentBranches.clear(); currentLoops.clear();
                        lastStepSuccess = true; executionGeneration++; running = true; paused = false; executionInFlight = false;
                        panelWasVisibleAtRunStart = false;
                        report("البوت الذكي ينفّذ " + (smartTaskActionCount + 1) + "/" + SMART_TASK_MAX_ACTIONS + ": " + smartCurrentActionLabel);
                        showRunControls(); runStep();
                    });
                });
            } catch (Exception e) {
                if (!currentScreen.isRecycled()) currentScreen.recycle();
                endSmartTask("تعذر بدء مخطط الذكاء الاصطناعي: " + e.getMessage(), false);
            }
        }), 180);
    }

    private void endSmartTask(String message, boolean reportedComplete) {
        boolean wasSmart = smartTaskActive;
        smartTaskActive = false; running = false; paused = false; executionInFlight = false;
        removeView(smartPlanningControls); smartPlanningControls = null;
        executionGeneration++; currentBranches.clear(); currentLoops.clear(); executionSteps = new JSONArray();
        removeView(runControls); runControls = null; runControlsParams = null; runControlsExpanded = false;
        removeView(runStatusView); runStatusView = null; runStatusParams = null;
        if (wasSmart) {
            String finalStatus = reportedComplete ? "مكتملة" : "توقفت/فشلت";
            BotnaLogStore.add(this, "smart_agent", smartTaskActionCount, "إنهاء المهمة",
                    reportedComplete ? "اعتُبرت المهمة مكتملة من الوكيل." : "توقفت المهمة قبل اكتمالها.", message + "\nالهدف: " + smartTaskGoal, null);
            BotnaAgentStore.setStatus(this, smartTaskId, finalStatus, message);
            BotnaAgentStore.addMessage(this, smartTaskId, "assistant", reportedComplete ? "انتهت المهمة" : "توقفت المهمة",
                    message + "\nالهدف: " + smartTaskGoal + "\nعدد الإجراءات: " + smartTaskActionCount, smartTaskActionCount, reportedComplete);
        }
        smartTaskGoal = ""; smartTaskId = ""; smartCurrentActionLabel = ""; smartTaskHistory = new JSONArray(); smartTaskActionCount = 0;
        report((reportedComplete ? "انتهت مهمة البوت الذكي: " : "توقف البوت الذكي: ") + message);
        showBubble(); if (panelWasVisibleAtRunStart) showPanel();
    }

    private void logFailureWithScreenshot(String source, int stepIndex, String stepLabel,
                                          String message, String detail, boolean restoreUi) {
        if (!BotnaLogStore.screenshotsEnabled(this)) {
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
