package com.botna.automation;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.hardware.HardwareBuffer;
import android.os.Build;
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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BotnaAccessibilityService extends AccessibilityService {
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

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService matcherExecutor = Executors.newSingleThreadExecutor();
    private WindowManager wm;
    private View bubble;
    private View panel;
    private View runControls;
    private ScrollView panelScrollView;
    private CropSelector cropSelector;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;
    private boolean panelVisible;
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
    private TextView resultLabel;
    private LinearLayout stepList;
    private int lastActionResult = 0;
    private int panelRestoreY;
    private String panelScriptId = "";

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ensureScript();
        showBubble();
        saveResult("Botna جاهز. اضغط B لفتح لوحة الأتمتة.");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { saveResult("تمت مقاطعة الخدمة."); }

    @Override public void onDestroy() {
        removeView(panel);
        removeView(runControls);
        removeView(cropSelector);
        removeView(bubble);
        panel = null; runControls = null; cropSelector = null; bubble = null;
        panelVisible = false;
        running = false; paused = false; executionGeneration++;
        currentBranches.clear(); currentLoops.clear();
        try { BotnaStore.saveScripts(this, BotnaStore.scripts(this)); } catch (Exception ignored) { }
        matcherExecutor.shutdownNow();
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
        bubbleParams = new WindowManager.LayoutParams(dp(58), dp(58),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getInt("bubble_x", dp(16));
        bubbleParams.y = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getInt("bubble_y", dp(250));
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
                        bubbleParams.x = Math.max(0, origin[0] + (int) dx);
                        bubbleParams.y = Math.max(dp(28), origin[1] + (int) dy);
                        try { wm.updateViewLayout(bubble, bubbleParams); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragged[0]) {
                        getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).edit()
                                .putInt("bubble_x", bubbleParams.x).putInt("bubble_y", bubbleParams.y).apply();
                    } else togglePanel();
                    return true;
            }
            return true;
        });
        try { wm.addView(b, bubbleParams); bubble = b; }
        catch (Exception e) { saveResult("تعذر إظهار الزر العائم: " + e.getClass().getSimpleName()); }
    }

    private void togglePanel() { if (panelVisible) hidePanel(); else showPanel(); }

    private void showPanel() {
        if (wm == null || panelVisible || running || singleStepTesting) return;

        // A fixed top header plus one full-content ScrollView keeps controls
        // near the top and makes every section reachable on short screens.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackground(glassShape(GLASS, Color.argb(235, 35, 226, 244), 2, 22));
        root.setElevation(dp(16));

        LinearLayout header = horizontal();
        TextView title = label("BOTNA  •  الأتمتة الذكية", 16, TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button saveClose = button("حفظ", false);
        saveClose.setContentDescription("حفظ السكربت وإغلاق لوحة الأتمتة");
        saveClose.setOnClickListener(v -> confirmSaveAndClose());
        header.addView(saveClose, new LinearLayout.LayoutParams(dp(66), dp(42)));
        Button close = button("×", false);
        close.setTextSize(20);
        close.setContentDescription("إغلاق لوحة الأتمتة بعد تأكيد الحفظ");
        close.setOnClickListener(v -> confirmSaveAndClose());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(38), dp(42));
        closeLp.setMargins(dp(4), 0, 0, 0);
        header.addView(close, closeLp);
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(42)));

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
        addButton(addRow4, "نقر موقع", () -> promptText("موقع النقر كنسبة مئوية x,y (مثال 50,50)", "50,50", value -> {
            String[] xy = value.split(",");
            if (xy.length != 2) { report("اكتب إحداثيين بهذا الشكل: 50,50"); return; }
            try {
                int x = Math.max(0, Math.min(100, Integer.parseInt(xy[0].trim())));
                int y = Math.max(0, Math.min(100, Integer.parseInt(xy[1].trim())));
                JSONObject step = addStep(TYPE_TAP, "نقر الموقع " + x + "%، " + y + "%");
                putInt(step, "xPercent", x); putInt(step, "yPercent", y);
                saveStepObject(step); rebuildPanel();
            } catch (NumberFormatException ex) { report("الإحداثيات يجب أن تكون أرقامًا من 0 إلى 100."); }
        }));
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
            JSONObject step = addStep(TYPE_IMAGE, "انتظار/مطابقة صورة");
            putBoolean(step, "tapOnMatch", false);
            putBoolean(step, "waitForImageOnly", true);
            putInt(step, "retries", 3);
            cropStepId = step.optString("id"); cropCreatedNewStep = true;
            saveStepObject(step); rebuildPanel(); beginCaptureForCrop();
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
        addButton(addRow7, "بدء تكرار", () -> promptNumber("عدد مرات تكرار المجموعة (1–1000)", 3, 1, 1000, count -> {
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

        LinearLayout runRow = horizontal();
        Button run = button("▶ تشغيل", true);
        Button pauseResume = button("Ⅱ إيقاف مؤقت / استكمال", false);
        Button restart = button("↻ من البداية", false);
        runRow.addView(run, new LinearLayout.LayoutParams(0, dp(42), 1));
        runRow.addView(pauseResume, new LinearLayout.LayoutParams(0, dp(42), 1));
        runRow.addView(restart, new LinearLayout.LayoutParams(0, dp(42), 1));
        LinearLayout.LayoutParams runLp = wrap(); runLp.topMargin = dp(7); content.addView(runRow, runLp);
        LinearLayout stopRow = horizontal();
        Button stop = button("■ إيقاف نهائي", false);
        stopRow.addView(stop, new LinearLayout.LayoutParams(-1, dp(38)));
        LinearLayout.LayoutParams stopLp = wrap(); stopLp.topMargin = dp(4); content.addView(stopRow, stopLp);
        run.setOnClickListener(v -> runActiveScript());
        pauseResume.setOnClickListener(v -> { if (!running) report("لا يوجد سكربت قيد التشغيل."); else if (paused) resumeScript(); else pauseScript(); });
        restart.setOnClickListener(v -> restartScript());
        stop.setOnClickListener(v -> stopScript());

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
                .setMessage("يُحفظ كل تعديل تلقائيًا أيضًا. هل تريد حفظ السكربت الحالي الآن وإغلاق اللوحة؟")
                .setPositiveButton("حفظ وإغلاق", (d, w) -> {
                    BotnaStore.saveScripts(this, BotnaStore.scripts(this));
                    report("تم حفظ السكربت محليًا.");
                    hidePanel();
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
            if (TYPE_IMAGE.equals(step.optString("type"))) desc += " • صور: " + (images == null ? 0 : images.length()) + " • تطابق " + step.optInt("threshold", 85) + "%";
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
        if (TYPE_IMAGE.equals(type)) {
            String[] options = new String[]{
                    "عرض الزوايا المحفوظة", "تغيير الهدف الحالي", "قص زاوية إضافية",
                    "تعديل نسبة التطابق", "إعدادات المطابقة المتقدمة", "اختبار الخطوة الآن",
                    step.optBoolean("enabled", true) ? "تعطيل الخطوة" : "تفعيل الخطوة", "حذف الخطوة"
            };
            showChoice("إعدادات خطوة الصورة", options, choice -> {
                if (choice == 0) showImageAngles(script, step, index);
                else if (choice == 1) changeImageTarget(script, step);
                else if (choice == 2) { cropStepId = step.optString("id"); cropCreatedNewStep = false; replaceImageTarget = false; beginCaptureForCrop(); }
                else if (choice == 3) promptNumber("نسبة التطابق المطلوبة (50–99)", step.optInt("threshold", 85), 50, 99, value -> {
                    putInt(step, "threshold", value); BotnaStore.saveSteps(this, script, steps); rebuildPanel();
                });
                else if (choice == 4) editImageAdvancedSettings(script, step, steps);
                else if (choice == 5) testStep(index);
                else if (choice == 6) { putBoolean(step, "enabled", !step.optBoolean("enabled", true)); BotnaStore.saveSteps(this, script, steps); rebuildPanel(); }
                else confirmDeleteStep(index);
            });
            return;
        }
        if (TYPE_TEXT.equals(type)) {
            showChoice("إعدادات خطوة النقر على النص", new String[]{"تغيير النص المستهدف", "عدد المحاولات", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0) promptText("تعديل النص المستهدف", step.optString("value"), value -> {
                    putString(step, "value", value); putString(step, "label", "نقر نص: " + value); BotnaStore.saveSteps(this, script, steps); rebuildPanel();
                });
                else if (choice == 1) promptNumber("عدد المحاولات (1–10)", step.optInt("retries", 1), 1, 10, value -> saveStepInt(script, step, "retries", value));
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
            showChoice("إعدادات النقر بالموقع", new String[]{"تغيير الإحداثيات", "انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if (choice == 0) promptText("x,y كنسب مئوية من الشاشة", step.optInt("xPercent", 50) + "," + step.optInt("yPercent", 50), value -> {
                    String[] xy = value.split(","); try { if (xy.length != 2) throw new NumberFormatException(); putInt(step, "xPercent", Math.max(0, Math.min(100, Integer.parseInt(xy[0].trim())))); putInt(step, "yPercent", Math.max(0, Math.min(100, Integer.parseInt(xy[1].trim())))); BotnaStore.saveSteps(this, script, steps); rebuildPanel(); } catch (NumberFormatException ex) { report("أدخل إحداثيين صحيحين مثل 50,50."); }
                });
                else if (choice == 1) promptNumber("انتظار قبل الخطوة (مللي ثانية)", step.optInt("beforeDelay", 0), 0, 60000, value -> saveStepInt(script, step, "beforeDelay", value));
                else if (choice == 2) promptNumber("انتظار بعد التنفيذ (مللي ثانية)", step.optInt("afterDelay", 0), 0, 60000, value -> saveStepInt(script, step, "afterDelay", value));
                else if (choice == 3) testStep(index);
                else if (choice == 4) toggleStep(script, step);
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
        } else if (TYPE_BACK.equals(type) || TYPE_HOME.equals(type)) {
            showChoice("إعدادات خطوة النظام", new String[]{"انتظار قبل التنفيذ", "انتظار بعد التنفيذ", "اختبار الخطوة", "تفعيل/تعطيل", "حذف"}, choice -> {
                if(choice==0) promptNumber("انتظار قبل التنفيذ (مللي ثانية)",step.optInt("beforeDelay",0),0,60000,value->saveStepInt(script,step,"beforeDelay",value));
                else if(choice==1) promptNumber("انتظار بعد التنفيذ (مللي ثانية)",step.optInt("afterDelay",0),0,60000,value->saveStepInt(script,step,"afterDelay",value));
                else if(choice==2)testStep(index); else if(choice==3)toggleStep(script,step); else confirmDeleteStep(index);
            });
        } else if (TYPE_REPEAT_START.equals(type)) {
            showChoice("إعدادات التكرار", new String[]{"عدد التكرارات", "اختبار إعداد التكرار", "تفعيل/تعطيل مجموعة التكرار", "حذف بداية التكرار"}, choice -> {
                if (choice == 0) promptNumber("عدد مرات تكرار المجموعة (1–1000)", step.optInt("repeatCount", 2), 1, 1000, value -> {
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

    private void toggleStep(JSONObject script, JSONObject step) {
        putBoolean(step, "enabled", !step.optBoolean("enabled", true));
        BotnaStore.saveSteps(this, script, BotnaStore.steps(script)); rebuildPanel();
    }

    private void changeImageTarget(JSONObject script, JSONObject step) {
        // Keep the old target intact until the user successfully crops a replacement.
        cropStepId = step.optString("id");
        cropCreatedNewStep = false;
        replaceImageTarget = true;
        beginCaptureForCrop();
    }

    private void editImageAdvancedSettings(JSONObject script, JSONObject step, JSONArray steps) {
        showChoice("إعدادات المطابقة المتقدمة", new String[]{
                step.optBoolean("tapOnMatch", true) ? "عدم الضغط بعد العثور على الصورة" : "الضغط بعد العثور على الصورة",
                "عدد محاولات المطابقة (1–10)", "فاصل إعادة المحاولة", "انتظار بعد المطابقة/الضغط",
                "انتظار قبل تنفيذ الخطوة", "تعطيل/تفعيل الخطوة"
        }, choice -> {
            if (choice == 0) { putBoolean(step, "tapOnMatch", !step.optBoolean("tapOnMatch", true)); BotnaStore.saveSteps(this, script, steps); rebuildPanel(); }
            else if (choice == 1) promptNumber("عدد محاولات المطابقة (1–10)", step.optInt("retries", 1), 1, 10, value -> saveStepInt(script, step, "retries", value));
            else if (choice == 2) promptNumber("فاصل إعادة المحاولة بالمللي ثانية", step.optInt("retryDelay", 500), 100, 10000, value -> saveStepInt(script, step, "retryDelay", value));
            else if (choice == 3) promptNumber("انتظار بعد المطابقة بالمللي ثانية", step.optInt("afterDelay", 0), 0, 60000, value -> saveStepInt(script, step, "afterDelay", value));
            else if (choice == 4) promptNumber("انتظار قبل تنفيذ الخطوة بالمللي ثانية", step.optInt("beforeDelay", 0), 0, 60000, value -> saveStepInt(script, step, "beforeDelay", value));
            else toggleStep(script, step);
        });
    }

    private AlertDialog imageAnglesDialog;

    private void showImageAngles(JSONObject script, JSONObject step, int stepIndex) {
        JSONArray images = step.optJSONArray("images");
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(10), dp(8), dp(10), dp(8));
        ScrollView scroller = new ScrollView(this); scroller.addView(list);
        List<Bitmap> loaded = new ArrayList<>();
        if (images == null || images.length() == 0) {
            list.addView(label("لا توجد زوايا محفوظة. استخدم «قص زاوية إضافية» لإضافتها.", 14, MUTED, false));
        } else {
            for (int i = 0; i < images.length(); i++) {
                final int angleIndex = i;
                final String filename = images.optString(i);
                LinearLayout row = horizontal(); row.setPadding(dp(5), dp(5), dp(5), dp(5));
                ImageView preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap bitmap = readTemplate(filename);
                if (bitmap != null) {
                    loaded.add(bitmap);
                    Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, dp(74), dp(56), true);
                    if (previewBitmap != bitmap) loaded.add(previewBitmap);
                    preview.setImageBitmap(previewBitmap);
                }
                row.addView(preview, new LinearLayout.LayoutParams(dp(74), dp(56)));
                TextView label = label("زاوية " + (i + 1) + "\n" + filename, 11, TEXT, false);
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(0, -2, 1); labelLp.setMargins(dp(7), 0, dp(7), 0); row.addView(label, labelLp);
                Button remove = button("حذف", false); row.addView(remove, new LinearLayout.LayoutParams(dp(60), dp(38)));
                remove.setOnClickListener(v -> {
                    AlertDialog confirm = new AlertDialog.Builder(this).setTitle("حذف الزاوية")
                            .setMessage("هل تريد حذف الزاوية " + (angleIndex + 1) + " من خطوة الصورة؟")
                            .setPositiveButton("حذف", (d, w) -> {
                                JSONArray current = step.optJSONArray("images"); JSONArray next = new JSONArray();
                                if (current != null) for (int j = 0; j < current.length(); j++) if (j != angleIndex) next.put(current.optString(j));
                                try { step.put("images", next); } catch (JSONException ignored) { }
                                BotnaStore.saveSteps(this, script, BotnaStore.steps(script));
                                report("تم حذف الزاوية من خطوة الصورة.");
                                if (imageAnglesDialog != null && imageAnglesDialog.isShowing()) imageAnglesDialog.dismiss();
                                showImageAngles(script, step, stepIndex);
                            }).setNegativeButton("إلغاء", (d, w) -> { }).create();
                    try { if (confirm.getWindow() != null) confirm.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); confirm.show(); }
                    catch (Exception e) { report("تعذر فتح تأكيد حذف الزاوية."); }
                });
                list.addView(row);
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("الزوايا المحفوظة")
                .setView(scroller).setPositiveButton("إغلاق", (d,w)->{}).create();
        imageAnglesDialog = dialog;
        dialog.setOnDismissListener(d -> { for (Bitmap b : loaded) if (b != null && !b.isRecycled()) b.recycle(); });
        try { if (dialog.getWindow() != null) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); dialog.show(); }
        catch (Exception e) { report("تعذر عرض زوايا الصورة."); }
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

    // ---------------- Screenshot crop with transparent resizable frame ----------------
    private void prepareCropStep() {
        JSONObject script = ensureScript(); JSONArray steps = BotnaStore.steps(script); JSONObject target = null;
        // All target crops belong to the first image step; never create a separate
        // step merely to add another angle to an existing target.
        for (int i = 0; i < steps.length(); i++) {
            JSONObject item = steps.optJSONObject(i);
            if (item != null && TYPE_IMAGE.equals(item.optString("type"))
                    && !item.optBoolean("waitForImageOnly", false)) {
                target = item;
                break;
            }
        }
        if (target == null) {
            target = addStep(TYPE_IMAGE, "مطابقة صورة الهدف");
            cropCreatedNewStep = true;
        } else {
            cropCreatedNewStep = false;
        }
        cropStepId = target.optString("id");
        replaceImageTarget = false;
        rebuildPanel();
        beginCaptureForCrop();
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
                showCropSelector(screenshot);
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

    private void showCropSelector(Bitmap screenshot) {
        cropSelector = new CropSelector(screenshot);
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        try { wm.addView(cropSelector, p); }
        catch (Exception e) { cropSelector = null; replaceImageTarget = false; screenshot.recycle(); showBubble(); showPanel(); report("تعذر فتح أداة القص."); }
    }

    private void finishCrop(Bitmap screenshot, RectF selection, int viewWidth, int viewHeight) {
        Bitmap cropped = null;
        try {
            if (screenshot == null || screenshot.isRecycled()) {
                throw new IllegalStateException("صورة الشاشة غير متاحة");
            }
            float sx = screenshot.getWidth() / (float) Math.max(1, viewWidth);
            float sy = screenshot.getHeight() / (float) Math.max(1, viewHeight);
            int left = Math.max(0, Math.min(screenshot.getWidth() - 1, (int) (selection.left * sx)));
            int top = Math.max(0, Math.min(screenshot.getHeight() - 1, (int) (selection.top * sy)));
            int right = Math.max(left + 1, Math.min(screenshot.getWidth(), (int) Math.ceil(selection.right * sx)));
            int bottom = Math.max(top + 1, Math.min(screenshot.getHeight(), (int) Math.ceil(selection.bottom * sy)));

            cropped = Bitmap.createBitmap(screenshot, left, top, right - left, bottom - top);
            File folder = new File(getFilesDir(), "botna_targets");
            if (!folder.exists() && !folder.mkdirs()) {
                throw new IllegalStateException("تعذر إنشاء مجلد الصور");
            }

            String fileName = "target_" + System.currentTimeMillis() + ".png";
            File file = new File(folder, fileName);
            try (FileOutputStream out = new FileOutputStream(file)) {
                if (!cropped.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw new IllegalStateException("فشل ضغط الصورة إلى PNG");
                }
                out.flush();
            }

            JSONObject script = ensureScript();
            JSONArray steps = BotnaStore.steps(script);
            boolean found = false;
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);
                if (step != null && cropStepId.equals(step.optString("id"))) {
                    JSONArray images = step.optJSONArray("images");
                    if (replaceImageTarget) images = new JSONArray();
                    if (images == null) images = new JSONArray();
                    images.put(fileName);
                    step.put("images", images);
                    step.put("label", "مطابقة صورة الهدف");
                    replaceImageTarget = false;
                    found = true;
                    break;
                }
            }
            if (!found) {
                JSONObject step = addStep(TYPE_IMAGE, "مطابقة صورة الهدف");
                JSONArray images = new JSONArray();
                images.put(fileName);
                step.put("images", images);
                saveStepObject(step);
                JSONObject refreshed = ensureScript();
                steps = BotnaStore.steps(refreshed);
            }
            BotnaStore.saveSteps(this, ensureScript(), steps);
            cropCreatedNewStep = false;
            report("تم قص الهدف وحفظ الصورة بنجاح.");

        } catch (Exception e) {
            replaceImageTarget = false;
            discardEmptyCreatedCropStep();
            report("تعذر حفظ الهدف: " + e.getClass().getSimpleName() + " — " + e.getMessage());
        } finally {
            // Critical crash fix: detach the drawing view BEFORE releasing its bitmap.
            View oldSelector = cropSelector;
            removeView(oldSelector);
            cropSelector = null;

            if (cropped != null && !cropped.isRecycled()) {
                cropped.recycle();
            }

            final Bitmap sourceToRelease = screenshot;
            if (sourceToRelease != null && !sourceToRelease.isRecycled()) {
                // Let Android finish any pending draw that still references the source bitmap.
                handler.postDelayed(() -> {
                    try {
                        if (!sourceToRelease.isRecycled()) sourceToRelease.recycle();
                    } catch (Exception ignored) { }
                }, 500);
            }

            showBubble();
            showPanel();
        }
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
        discardEmptyCreatedCropStep();
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
        showBubble();
        showPanel();
        report("تم إلغاء القص.");
    }

    private class CropSelector extends View {
        private final Bitmap screenshot;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final RectF rect = new RectF();
        private int mode = 0; // 0 new/inside, 1 top-left, 2 top-right, 3 bottom-left, 4 bottom-right, 5 move
        private float downX, downY;
        private final RectF initial = new RectF();
        private float density;
        CropSelector(Bitmap bitmap) { super(BotnaAccessibilityService.this); screenshot = bitmap; density = getResources().getDisplayMetrics().density; setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (rect.isEmpty()) { float cw = w * .38f, ch = h * .20f; rect.set((w-cw)/2f, (h-ch)/2f, (w+cw)/2f, (h+ch)/2f); }
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c); c.drawBitmap(screenshot, null, new RectF(0, 0, getWidth(), getHeight()), paint);
            paint.setColor(Color.argb(155, 0, 0, 0));
            c.drawRect(0,0,getWidth(),rect.top,paint); c.drawRect(0,rect.bottom,getWidth(),getHeight(),paint);
            c.drawRect(0,rect.top,rect.left,rect.bottom,paint); c.drawRect(rect.right,rect.top,getWidth(),rect.bottom,paint);
            paint.setColor(CYAN); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3*density); c.drawRect(rect,paint);
            paint.setStyle(Paint.Style.FILL); float r=8*density;
            c.drawCircle(rect.left,rect.top,r,paint); c.drawCircle(rect.right,rect.top,r,paint); c.drawCircle(rect.left,rect.bottom,r,paint); c.drawCircle(rect.right,rect.bottom,r,paint);
            c.drawCircle(rect.centerX(), rect.top, r, paint); c.drawCircle(rect.centerX(), rect.bottom, r, paint);
            c.drawCircle(rect.left, rect.centerY(), r, paint); c.drawCircle(rect.right, rect.centerY(), r, paint);
            paint.setColor(Color.argb(225,5,7,12)); paint.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(8*density, 8*density, getWidth()-8*density, 52*density), 12*density,12*density,paint);
            paint.setColor(TEXT); paint.setTextSize(14*density); paint.setTextAlign(Paint.Align.CENTER);
            c.drawText("حرّك الإطار أو اسحب إحدى زواياه لضبط القص", getWidth()/2f, 35*density, paint);
            float by=getHeight()-60*density;
            paint.setColor(Color.argb(240,8,14,22)); c.drawRoundRect(new RectF(getWidth()-150*density,by,getWidth()-8*density,getHeight()-8*density),12*density,12*density,paint);
            paint.setColor(CYAN); c.drawText("✓ حفظ الهدف", getWidth()-79*density, by+31*density, paint);
            paint.setColor(Color.rgb(240,110,130)); c.drawText("إلغاء", 65*density, by+31*density, paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if (e.getAction()==MotionEvent.ACTION_DOWN) {
                downX=x; downY=y; initial.set(rect);
                if (y > getHeight()-70*density) { mode=10; return true; }
                if (distance(x,y,rect.left,rect.top)<35*density) mode=1;
                else if(distance(x,y,rect.right,rect.top)<35*density) mode=2;
                else if(distance(x,y,rect.left,rect.bottom)<35*density) mode=3;
                else if(distance(x,y,rect.right,rect.bottom)<35*density) mode=4;
                else if(distance(x,y,rect.centerX(),rect.top)<35*density) mode=6;
                else if(distance(x,y,rect.centerX(),rect.bottom)<35*density) mode=7;
                else if(distance(x,y,rect.left,rect.centerY())<35*density) mode=8;
                else if(distance(x,y,rect.right,rect.centerY())<35*density) mode=9;
                else if(rect.contains(x,y)) mode=5;
                else { mode=0; rect.set(x,y,x,y); }
                invalidate(); return true;
            }
            if (e.getAction()==MotionEvent.ACTION_MOVE) {
                float dx=x-downX, dy=y-downY;
                if(mode==0) rect.set(Math.min(downX,x),Math.min(downY,y),Math.max(downX,x),Math.max(downY,y));
                else if(mode==1) { rect.left=clamp(downX+dx,0,rect.right-20*density); rect.top=clamp(downY+dy,0,rect.bottom-20*density); }
                else if(mode==2) { rect.right=clamp(initial.right+dx,rect.left+20*density,getWidth()); rect.top=clamp(initial.top+dy,0,rect.bottom-20*density); }
                else if(mode==3) { rect.left=clamp(initial.left+dx,0,rect.right-20*density); rect.bottom=clamp(initial.bottom+dy,rect.top+20*density,getHeight()-75*density); }
                else if(mode==4) { rect.right=clamp(initial.right+dx,rect.left+20*density,getWidth()); rect.bottom=clamp(initial.bottom+dy,rect.top+20*density,getHeight()-75*density); }
                else if(mode==5) { float w=initial.width(),h=initial.height(); float left=clamp(initial.left+dx,0,getWidth()-w); float top=clamp(initial.top+dy,55*density,getHeight()-75*density-h); rect.set(left,top,left+w,top+h); }
                else if(mode==6) rect.top=clamp(initial.top+dy,55*density,rect.bottom-20*density);
                else if(mode==7) rect.bottom=clamp(initial.bottom+dy,rect.top+20*density,getHeight()-75*density);
                else if(mode==8) rect.left=clamp(initial.left+dx,0,rect.right-20*density);
                else if(mode==9) rect.right=clamp(initial.right+dx,rect.left+20*density,getWidth());
                invalidate(); return true;
            }
            if (e.getAction()==MotionEvent.ACTION_UP) {
                if(mode==10) {
                    if(x > getWidth()/2f) finishCrop(screenshot,new RectF(rect),getWidth(),getHeight()); else cancelCrop(screenshot);
                }
                invalidate(); return true;
            }
            return true;
        }
        private float distance(float x,float y,float a,float b) { float dx=x-a,dy=y-b; return (float)Math.sqrt(dx*dx+dy*dy); }
        private float clamp(float v,float min,float max) { return Math.max(min,Math.min(max,v)); }
    }

    // ---------------- Image matching and script execution ----------------
    private interface MatchCallback {
        void done(ImageMatcher.Result result, String error);
    }

    private interface ActionResultCallback { void done(boolean success, String message); }

    private void runActiveScript() {
        if (running) { report(paused ? "السكربت متوقف مؤقتًا؛ استخدم الاستكمال." : "السكربت يعمل بالفعل."); return; }
        JSONObject script = ensureScript();
        JSONArray scriptSteps = BotnaStore.steps(script);
        if (scriptSteps.length() == 0) { report("أضف خطوات قبل التشغيل."); return; }
        String structureError = validateScriptStructure(scriptSteps);
        if (structureError != null) { report("لم يبدأ السكربت: " + structureError); return; }
        panelWasVisibleAtRunStart = panelVisible;
        currentScriptId = script.optString("id", "");
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
        if (!running && !singleStepTesting) { report("لا يوجد تنفيذ لإيقافه."); return; }
        executionGeneration++;
        running = false; paused = false; executionInFlight = false;
        currentBranches.clear(); currentLoops.clear();
        singleStepTesting = false;
        removeView(runControls); runControls = null;
        report("تم إيقاف التنفيذ نهائيًا.");
        showBubble();
        if (panelWasVisibleAtRunStart) showPanel();
    }

    private void showRunControls() {
        if (wm == null || !running) return;
        removeView(runControls); runControls = null;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(7), dp(6), dp(7), dp(6));
        bar.setBackground(glassShape(Color.argb(224, 8, 14, 24), Color.argb(235, 35, 226, 244), 1, 24));
        bar.setElevation(dp(15));
        Button pause = button(paused ? "▶ استكمال" : "Ⅱ مؤقت", true);
        Button restart = button("↻ من البداية", false);
        Button stop = button("■ إيقاف", false);
        bar.addView(pause, new LinearLayout.LayoutParams(0, dp(40), 1));
        bar.addView(restart, new LinearLayout.LayoutParams(0, dp(40), 1));
        bar.addView(stop, new LinearLayout.LayoutParams(0, dp(40), 1));
        pause.setOnClickListener(v -> { if (paused) resumeScript(); else pauseScript(); });
        restart.setOnClickListener(v -> restartScript());
        stop.setOnClickListener(v -> stopScript());
        int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(20), dp(350));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(width, dp(58),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        params.y = dp(86);
        try { wm.addView(bar, params); runControls = bar; }
        catch (Exception e) { saveResult("تعذر إظهار أزرار التحكم أثناء التشغيل."); }
    }

    private void runStep() {
        if (!running) return;
        if (paused) { executionInFlight = false; showRunControls(); return; }
        if (executionInFlight) return;
        JSONObject script = BotnaStore.getScript(this, currentScriptId);
        if (script == null) { finishScript("توقف السكربت: لم يتم العثور على تعريفه المحفوظ."); return; }
        JSONArray steps = BotnaStore.steps(script);
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
            int count = Math.max(1, Math.min(1000, step.optInt("repeatCount", 2)));
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

    private void executeActionStep(JSONObject step, String type, int generation) {
        if (TYPE_TEXT.equals(type)) {
            boolean ok = false;
            int retries = Math.max(1, Math.min(10, step.optInt("retries", 1)));
            for (int i = 0; i < retries && !ok; i++) ok = clickText(step.optString("value"));
            lastStepSuccess = ok;
            report(ok ? "نجحت خطوة النقر على النص: " + step.optString("value") : "فشلت خطوة النقر على النص: " + step.optString("value"));
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
            int width = getResources().getDisplayMetrics().widthPixels;
            int height = getResources().getDisplayMetrics().heightPixels;
            int x = Math.max(0, Math.min(width - 1, width * step.optInt("xPercent", 50) / 100));
            int y = Math.max(0, Math.min(height - 1, height * step.optInt("yPercent", 50) / 100));
            performTap(x, y, ok -> { if (!running || generation != executionGeneration) return; lastStepSuccess = ok; report(ok ? "نجح النقر بالموقع المحدد." : "فشل النقر بالموقع المحدد."); scheduleAfterAction(generation, step); });
        } else if (TYPE_LONG_PRESS.equals(type)) {
            int width=getResources().getDisplayMetrics().widthPixels, height=getResources().getDisplayMetrics().heightPixels;
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
            report(lastStepSuccess ? "تمت كتابة النص في الحقل المركّز." : "فشلت كتابة النص: لا يوجد حقل إدخال مركّز أو لا يدعم الكتابة."); scheduleAfterAction(generation, step);
        } else {
            lastStepSuccess = false; report("نوع خطوة غير معروف: " + type); scheduleNext(generation, 0);
        }
    }

    private void scheduleAfterAction(int generation, JSONObject step) {
        int configured = Math.max(0, Math.min(60000, step.optInt("afterDelay", 0)));
        // Give the compact pause/stop controls a real interaction window between
        // actions, while preserving any longer delay chosen for this step.
        scheduleNext(generation, Math.max(CONTROL_GAP_MS, configured));
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
        running = false; paused = false; executionInFlight = false;
        currentBranches.clear(); currentLoops.clear();
        executionGeneration++;
        removeView(runControls); runControls = null;
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
        Runnable testAction = () -> {
            if (generation != executionGeneration || !singleStepTesting) return;
            if (TYPE_IMAGE.equals(type)) {
                runImageMatch(step, 0, generation, (ok, message) -> finishSingleStepTest(generation, message));
            } else if (TYPE_TEXT.equals(type)) {
                boolean ok = false;
                int retries = Math.max(1, Math.min(10, step.optInt("retries", 1)));
                for (int i = 0; i < retries && !ok; i++) ok = clickText(step.optString("value", ""));
                finishSingleStepTest(generation, ok ? "نجح اختبار النقر على النص." : "فشل اختبار النقر على النص.");
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
                int width=getResources().getDisplayMetrics().widthPixels, height=getResources().getDisplayMetrics().heightPixels;
                int x=Math.max(0,Math.min(width-1,width*step.optInt("xPercent",50)/100));
                int y=Math.max(0,Math.min(height-1,height*step.optInt("yPercent",50)/100));
                performTap(x,y,ok->finishSingleStepTest(generation,ok?"نجح اختبار النقر بالموقع.":"فشل اختبار النقر بالموقع."));
            } else if (TYPE_LONG_PRESS.equals(type)) {
                int width=getResources().getDisplayMetrics().widthPixels, height=getResources().getDisplayMetrics().heightPixels;
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
            } else if (TYPE_IF_SUCCESS.equals(type) || TYPE_IF_FAIL.equals(type)) {
                boolean condition = TYPE_IF_SUCCESS.equals(type) ? lastStepSuccess : !lastStepSuccess;
                finishSingleStepTest(generation, "اختبار الشرط: " + (condition ? "سيتحقق الشرط بناءً على نتيجة الخطوة السابقة." : "لن يتحقق الشرط بناءً على نتيجة الخطوة السابقة."));
            } else if (TYPE_REPEAT_START.equals(type)) {
                finishSingleStepTest(generation, "تعريف التكرار صالح مبدئيًا: ستتكرر الكتلة " + Math.max(1, Math.min(1000, step.optInt("repeatCount", 2))) + " مرة عند تشغيل السكربت كاملًا.");
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
                boolean matched = match != null && match.score >= step.optInt("threshold", 85);
                if (!matched) {
                    retryImageOrFinish(step, attempt, generation, callback, false,
                            "لم يتحقق تطابق الصورة. أفضل نتيجة " + (match == null ? 0 : match.score) + "% (المطلوب " + step.optInt("threshold",85) + "%).");
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
        if (running && paused) { executionInFlight = false; showRunControls(); return; }
        int retries = Math.max(1, Math.min(10, step.optInt("retries", 1)));
        if (attempt + 1 < retries) {
            report(message + " إعادة المحاولة " + (attempt + 2) + "/" + retries + "…");
            int delay = Math.max(100, Math.min(10000, step.optInt("retryDelay", 500)));
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
        if (images == null || images.length() == 0) return null;
        List<ImageMatcher.NamedBitmap> templates = new ArrayList<>();
        for (int i = 0; i < images.length(); i++) {
            String name = images.optString(i); Bitmap bitmap = readTemplate(name);
            if (bitmap != null) templates.add(new ImageMatcher.NamedBitmap(name, bitmap));
        }
        try { return ImageMatcher.find(screen, templates, step.optInt("threshold", 85)); }
        finally { for (ImageMatcher.NamedBitmap item : templates) if (item.bitmap != null && !item.bitmap.isRecycled()) item.bitmap.recycle(); }
    }

    private void captureAppScreenshot(ScreenshotCallback callback) {
        if (Build.VERSION.SDK_INT < 30) { callback.done(null, "يتطلب Android 11 أو أحدث"); return; }
        boolean wasPanel = panelVisible;
        boolean shouldRestoreUi = !running && !singleStepTesting;
        hidePanel(); removeView(bubble); bubble = null;
        if (running) { removeView(runControls); runControls = null; }
        handler.postDelayed(() -> takeScreenshot((bitmap,error) -> {
            if (shouldRestoreUi) { showBubble(); if (wasPanel) showPanel(); }
            callback.done(bitmap,error);
        }), 180);
    }

    private interface GestureCallback { void done(boolean success); }

    private void tapMatchedTarget(ImageMatcher.Result match, GestureCallback callback) {
        if (Build.VERSION.SDK_INT < 24 || match == null) { callback.done(false); return; }
        removeView(runControls); runControls = null;
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
        removeView(runControls); runControls = null;
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
        removeView(runControls); runControls = null;
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
        int width=getResources().getDisplayMetrics().widthPixels, height=getResources().getDisplayMetrics().heightPixels;
        float x1=width*Math.max(0,Math.min(100,x1p))/100f, y1=height*Math.max(0,Math.min(100,y1p))/100f;
        float x2=width*Math.max(0,Math.min(100,x2p))/100f, y2=height*Math.max(0,Math.min(100,y2p))/100f;
        removeView(runControls); runControls = null;
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

    private boolean typeTextIntoFocusedField(String value) {
        AccessibilityNodeInfo root=findTargetRoot(); if(root==null)return false;
        AccessibilityNodeInfo focused=null;
        try {
            focused=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if(focused==null || !focused.isEditable())return false;
            android.os.Bundle args=new android.os.Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args);
        } catch(Exception e){return false;}
        finally { if(focused!=null)focused.recycle(); root.recycle(); }
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
        Button b=button(title,true); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(40),1); lp.setMargins(dp(2),0,dp(2),0); row.addView(b,lp); b.setOnClickListener(v->action.run());
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
    private void removeView(View view) { if(wm!=null&&view!=null)try{wm.removeView(view);}catch(Exception ignored){} }
    private void saveResult(String message) { String stamp=new java.text.SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new java.util.Date()); getSharedPreferences(BotnaStore.PREFS,MODE_PRIVATE).edit().putString(BotnaStore.KEY_LAST_RESULT,stamp+" — "+message).apply(); }
    private String getLastResult() { return getSharedPreferences(BotnaStore.PREFS,MODE_PRIVATE).getString(BotnaStore.KEY_LAST_RESULT,"جاهز."); }
    private void report(String message) { saveResult(message); if(resultLabel!=null) resultLabel.setText(getLastResult()); }
    private void putString(JSONObject o,String k,String v){try{o.put(k,v);}catch(JSONException ignored){}}
    private void putInt(JSONObject o,String k,int v){try{o.put(k,v);}catch(JSONException ignored){}}
    private void putBoolean(JSONObject o,String k,boolean v){try{o.put(k,v);}catch(JSONException ignored){}}

    private Bitmap readTemplate(String name) {
        if(name==null||name.contains("/")||name.contains("..")) return null;
        File f=new File(new File(getFilesDir(),"botna_targets"),name);
        if(!f.exists()) return null;
        try { return BitmapFactoryCompat.decode(f); } catch(Exception e) { return null; }
    }
    private static final class BitmapFactoryCompat {
        static Bitmap decode(File f) { return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath()); }
    }
}
