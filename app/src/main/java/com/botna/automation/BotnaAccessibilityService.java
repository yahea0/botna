package com.botna.automation;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.AlertDialog;
import android.content.ClipData;
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
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

public class BotnaAccessibilityService extends AccessibilityService {

    private static final int BG = Color.rgb(5, 7, 12);
    private static final int GLASS = Color.argb(242, 13, 17, 26);
    private static final int BUTTON = Color.argb(225, 19, 31, 47);
    private static final int TEXT = Color.rgb(239, 248, 255);
    private static final int MUTED = Color.rgb(151, 174, 194);
    private static final int CYAN = Color.rgb(35, 226, 244);

    private static final String TYPE_TEXT = "TEXT_CLICK";
    private static final String TYPE_BACK = "BACK";
    private static final String TYPE_HOME = "HOME";
    private static final String TYPE_WAIT = "WAIT";
    private static final String TYPE_IMAGE = "IMAGE_MATCH";
    private static final String TYPE_IF_SUCCESS = "IF_SUCCESS";
    private static final String TYPE_IF_FAIL = "IF_FAIL";
    private static final String TYPE_ELSE = "ELSE";
    private static final String TYPE_END_IF = "END_IF";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private View bubble;
    private View panel;
    private CropSelector cropSelector;

    private WindowManager.LayoutParams bubbleParams;

    // هذا هو التعريف الذي كان ناقصًا في ملفك السابق.
    private WindowManager.LayoutParams panelParams;

    private boolean panelVisible;
    private boolean running;
    private boolean lastStepSuccess = true;

    private String cropStepId = "";
    private TextView resultLabel;
    private LinearLayout stepList;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        ensureScript();
        showBubble();

        saveResult("Botna جاهز. اضغط B لفتح لوحة الأتمتة.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // لا تبدأ أي أتمتة تلقائيًا بسبب تغير الشاشة.
        // تنفيذ الخطوات يبدأ عندما يطلب المستخدم ذلك.
    }

    @Override
    public void onInterrupt() {
        saveResult("تمت مقاطعة الخدمة.");
    }

    @Override
    public void onDestroy() {
        removeView(panel);
        removeView(cropSelector);
        removeView(bubble);

        panel = null;
        cropSelector = null;
        bubble = null;
        panelVisible = false;
        running = false;

        super.onDestroy();
    }

    // =========================================================
    // الزر العائم القابل للسحب
    // =========================================================

    private void showBubble() {
        if (wm == null || bubble != null) {
            return;
        }

        TextView b = new TextView(this);

        b.setText("B");
        b.setTextSize(20);
        b.setTextColor(CYAN);
        b.setGravity(Gravity.CENTER);
        b.setElevation(dp(12));

        b.setBackground(
                glassShape(
                        Color.argb(228, 8, 13, 22),
                        Color.argb(240, 35, 226, 244),
                        2,
                        36
                )
        );

        b.setContentDescription("بوتنا: افتح لوحة الأتمتة");

        bubbleParams = new WindowManager.LayoutParams(
                dp(58),
                dp(58),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;

        bubbleParams.x = getSharedPreferences(
                BotnaStore.PREFS,
                MODE_PRIVATE
        ).getInt("bubble_x", dp(16));

        bubbleParams.y = getSharedPreferences(
                BotnaStore.PREFS,
                MODE_PRIVATE
        ).getInt("bubble_y", dp(250));

        final float[] down = new float[2];
        final int[] origin = new int[2];
        final boolean[] dragged = new boolean[1];

        b.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();

                    origin[0] = bubbleParams.x;
                    origin[1] = bubbleParams.y;

                    dragged[0] = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - down[0];
                    float dy = e.getRawY() - down[1];

                    if (Math.abs(dx) + Math.abs(dy) > dp(6)) {
                        dragged[0] = true;
                    }

                    if (dragged[0]) {
                        bubbleParams.x = Math.max(
                                0,
                                origin[0] + (int) dx
                        );

                        bubbleParams.y = Math.max(
                                dp(28),
                                origin[1] + (int) dy
                        );

                        try {
                            wm.updateViewLayout(bubble, bubbleParams);
                        } catch (Exception ignored) {
                        }
                    }

                    return true;

                case MotionEvent.ACTION_UP:
                    if (dragged[0]) {
                        getSharedPreferences(
                                BotnaStore.PREFS,
                                MODE_PRIVATE
                        ).edit()
                                .putInt("bubble_x", bubbleParams.x)
                                .putInt("bubble_y", bubbleParams.y)
                                .apply();
                    } else {
                        togglePanel();
                    }

                    return true;
            }

            return true;
        });

        try {
            wm.addView(b, bubbleParams);
            bubble = b;

        } catch (Exception e) {
            saveResult(
                    "تعذر إظهار الزر العائم: "
                            + e.getClass().getSimpleName()
            );
        }
    }

    private void togglePanel() {
        if (panelVisible) {
            hidePanel();
        } else {
            showPanel();
        }
    }

    // =========================================================
    // لوحة التحكم الزجاجية
    // =========================================================

    private void showPanel() {
        if (wm == null || panelVisible) {
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        root.setPadding(
                dp(12), dp(12), dp(12), dp(12)
        );

        root.setBackground(
                glassShape(
                        GLASS,
                        Color.argb(230, 35, 226, 244),
                        2,
                        23
                )
        );

        root.setElevation(dp(14));

        LinearLayout header = horizontal();

        TextView title = label(
                "BOTNA  •  الأتمتة الذكية",
                17,
                TEXT,
                true
        );

        header.addView(
                title,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        Button hide = button("إخفاء", false);
        hide.setOnClickListener(v -> hidePanel());

        header.addView(hide, wrap());
        root.addView(header);

        JSONObject script = BotnaStore.activeScript(this);

        TextView activeName = label(
                "السكربت الحالي: "
                        + script.optString("name", "بدون اسم"),
                13,
                CYAN,
                true
        );

        LinearLayout.LayoutParams nameLp = wrap();
        nameLp.topMargin = dp(7);

        root.addView(activeName, nameLp);

        LinearLayout scriptButtons = horizontal();

        Button newScript = button("＋ سكربت جديد", true);
        Button chooseScript = button("تبديل السكربت", false);

        scriptButtons.addView(
                newScript,
                new LinearLayout.LayoutParams(0, dp(42), 1)
        );

        scriptButtons.addView(
                chooseScript,
                new LinearLayout.LayoutParams(0, dp(42), 1)
        );

        LinearLayout.LayoutParams scriptLp = wrap();
        scriptLp.topMargin = dp(7);

        root.addView(scriptButtons, scriptLp);

        newScript.setOnClickListener(v ->
                promptText(
                        "اسم السكربت الجديد",
                        "سكربت جديد",
                        name -> {
                            BotnaStore.createScript(this, name);
                            rebuildPanel();
                        }
                )
        );

        chooseScript.setOnClickListener(
                v -> chooseScriptDialog()
        );

        Button readScreenButton = button(
                "⌕ قراءة عناصر الشاشة",
                false
        );

        LinearLayout.LayoutParams readLp =
                new LinearLayout.LayoutParams(-1, dp(40));

        readLp.topMargin = dp(7);

        root.addView(readScreenButton, readLp);

        readScreenButton.setOnClickListener(v -> readScreen());

        LinearLayout addRow1 = horizontal();

        addButton(addRow1, "＋ نقر نص", () ->
                promptText(
                        "النص المطلوب النقر عليه",
                        "تطوير",
                        value -> {
                            JSONObject s = addStep(
                                    TYPE_TEXT,
                                    "نقر نص: " + value
                            );

                            putString(s, "value", value);
                            rebuildPanel();
                        }
                )
        );

        addButton(addRow1, "＋ انتظار", () ->
                promptNumber(
                        "مدة الانتظار بالمللي ثانية",
                        500,
                        0,
                        60000,
                        value -> {
                            JSONObject s = addStep(
                                    TYPE_WAIT,
                                    "انتظار " + value + " ms"
                            );

                            putInt(s, "delay", value);
                            rebuildPanel();
                        }
                )
        );

        addButton(
                addRow1,
                "＋ قص هدف",
                this::prepareCropStep
        );

        LinearLayout.LayoutParams addLp = wrap();
        addLp.topMargin = dp(7);

        root.addView(addRow1, addLp);

        LinearLayout addRow2 = horizontal();

        addButton(addRow2, "رجوع", () -> {
            addStep(TYPE_BACK, "رجوع");
            rebuildPanel();
        });

        addButton(addRow2, "الرئيسية", () -> {
            addStep(TYPE_HOME, "الرئيسية");
            rebuildPanel();
        });

        addButton(addRow2, "إذا نجح", () -> {
            addStep(
                    TYPE_IF_SUCCESS,
                    "إذا نجح الإجراء السابق"
            );

            rebuildPanel();
        });

        LinearLayout.LayoutParams add2Lp = wrap();
        add2Lp.topMargin = dp(6);

        root.addView(addRow2, add2Lp);

        LinearLayout addRow3 = horizontal();

        addButton(addRow3, "إذا فشل", () -> {
            addStep(
                    TYPE_IF_FAIL,
                    "إذا فشل الإجراء السابق"
            );

            rebuildPanel();
        });

        addButton(addRow3, "وإلا", () -> {
            addStep(TYPE_ELSE, "وإلا");
            rebuildPanel();
        });

        addButton(addRow3, "نهاية الشرط", () -> {
            addStep(TYPE_END_IF, "نهاية الشرط");
            rebuildPanel();
        });

        LinearLayout.LayoutParams add3Lp = wrap();
        add3Lp.topMargin = dp(6);

        root.addView(addRow3, add3Lp);

        LinearLayout runRow = horizontal();

        Button run = button("▶ تشغيل السكربت", true);
        Button stop = button("■ إيقاف", false);

        runRow.addView(
                run,
                new LinearLayout.LayoutParams(0, dp(44), 1)
        );

        runRow.addView(
                stop,
                new LinearLayout.LayoutParams(0, dp(44), 1)
        );

        LinearLayout.LayoutParams runLp = wrap();
        runLp.topMargin = dp(8);

        root.addView(runRow, runLp);

        run.setOnClickListener(v -> runActiveScript());

        stop.setOnClickListener(v -> {
            running = false;
            report("تم إيقاف السكربت يدويًا.");
        });

        ScrollView stepScroll = new ScrollView(this);
        stepScroll.setFillViewport(false);

        stepList = new LinearLayout(this);
        stepList.setOrientation(LinearLayout.VERTICAL);

        TextView stepsTitle = label(
                "الخطوات — اضغط للتعديل، واضغط مطولًا للسحب والترتيب",
                12,
                MUTED,
                true
        );

        LinearLayout.LayoutParams stLp = wrap();
        stLp.topMargin = dp(10);

        root.addView(stepsTitle, stLp);
        stepScroll.addView(stepList);

        LinearLayout.LayoutParams scrollLp =
                new LinearLayout.LayoutParams(-1, dp(210));

        scrollLp.topMargin = dp(5);
        root.addView(stepScroll, scrollLp);

        resultLabel = label(
                getLastResult(),
                12,
                MUTED,
                false
        );

        LinearLayout.LayoutParams resultLp = wrap();
        resultLp.topMargin = dp(7);

        root.addView(resultLabel, resultLp);

        populateSteps(script);

        int screenWidth =
                getResources().getDisplayMetrics().widthPixels;

        int screenHeight =
                getResources().getDisplayMetrics().heightPixels;

        // تعريف النافذة موجود هنا ويعتمد على الحقل المعرّف بالأعلى.
        panelParams = new WindowManager.LayoutParams(
                Math.min(dp(348), screenWidth - dp(20)),
                Math.min(dp(640), screenHeight - dp(80)),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );

        panelParams.gravity =
                Gravity.TOP | Gravity.CENTER_HORIZONTAL;

        panelParams.y = dp(70);

        try {
            wm.addView(root, panelParams);
            panel = root;
            panelVisible = true;

        } catch (Exception e) {
            saveResult(
                    "تعذر فتح اللوحة: "
                            + e.getClass().getSimpleName()
            );
        }
    }

    private void rebuildPanel() {
        hidePanel();
        showPanel();
    }

    private void hidePanel() {
        removeView(panel);

        panel = null;
        resultLabel = null;
        stepList = null;
        panelVisible = false;
    }

    // =========================================================
    // عرض الخطوات وترتيبها
    // =========================================================

    private void populateSteps(JSONObject script) {
        if (stepList == null) {
            return;
        }

        JSONArray steps = BotnaStore.steps(script);

        if (steps.length() == 0) {
            TextView empty = label(
                    "لا توجد خطوات بعد. أضف خطوة من الأزرار أعلاه.",
                    12,
                    MUTED,
                    false
            );

            empty.setPadding(
                    dp(7), dp(10), dp(7), dp(10)
            );

            stepList.addView(empty);
            return;
        }

        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);

            if (step == null) {
                continue;
            }

            final int index = i;

            LinearLayout card = horizontal();

            card.setPadding(
                    dp(8), dp(7), dp(8), dp(7)
            );

            card.setBackground(
                    glassShape(
                            Color.argb(180, 12, 24, 38),
                            Color.argb(105, 35, 226, 244),
                            1,
                            13
                    )
            );

            LinearLayout.LayoutParams cardLp = wrap();
            cardLp.topMargin = dp(5);

            stepList.addView(card, cardLp);

            TextView number = label(
                    String.valueOf(i + 1),
                    13,
                    CYAN,
                    true
            );

            number.setGravity(Gravity.CENTER);

            card.addView(
                    number,
                    new LinearLayout.LayoutParams(dp(25), -1)
            );

            JSONArray images = step.optJSONArray("images");

            if (images != null && images.length() > 0) {
                ImageView thumb = new ImageView(this);

                Bitmap bmp = readTemplate(
                        images.optString(0)
                );

                if (bmp != null) {
                    Bitmap small = Bitmap.createScaledBitmap(
                            bmp,
                            dp(42),
                            dp(42),
                            true
                    );

                    thumb.setImageBitmap(small);
                    bmp.recycle();
                }

                card.addView(
                        thumb,
                        new LinearLayout.LayoutParams(
                                dp(42), dp(42)
                        )
                );
            }

            String desc = step.optString(
                    "label",
                    step.optString("type", "خطوة")
            );

            if (TYPE_IMAGE.equals(step.optString("type"))) {
                desc += " • صور: "
                        + (images == null ? 0 : images.length())
                        + " • تطابق "
                        + step.optInt("threshold", 85)
                        + "%";
            }

            TextView item = label(desc, 12, TEXT, false);

            LinearLayout.LayoutParams itemLp =
                    new LinearLayout.LayoutParams(0, -2, 1);

            itemLp.gravity = Gravity.CENTER_VERTICAL;

            card.addView(item, itemLp);

            TextView del = label(
                    "×",
                    22,
                    Color.rgb(255, 115, 133),
                    true
            );

            del.setGravity(Gravity.CENTER);

            card.addView(
                    del,
                    new LinearLayout.LayoutParams(dp(30), dp(42))
            );

            card.setOnClickListener(v -> editStep(index));

            del.setOnClickListener(v -> deleteStep(index));

            card.setOnLongClickListener(v -> {
                ClipData data = ClipData.newPlainText(
                        "botna-step-index",
                        String.valueOf(index)
                );

                if (Build.VERSION.SDK_INT >= 24) {
                    v.startDragAndDrop(
                            data,
                            new View.DragShadowBuilder(v),
                            null,
                            0
                    );
                } else {
                    v.startDrag(
                            data,
                            new View.DragShadowBuilder(v),
                            null,
                            0
                    );
                }

                return true;
            });

            card.setOnDragListener((v, event) -> {
                if (event.getAction()
                        == android.view.DragEvent.ACTION_DROP) {

                    try {
                        reorderStep(
                                Integer.parseInt(
                                        event.getClipData()
                                                .getItemAt(0)
                                                .getText()
                                                .toString()
                                ),
                                index
                        );
                    } catch (Exception ignored) {
                    }

                    return true;
                }

                return true;
            });
        }
    }

    // =========================================================
    // تخزين الخطوات وتعديلها
    // =========================================================

    private JSONObject ensureScript() {
        return BotnaStore.activeScript(this);
    }

    private JSONObject addStep(
            String type,
            String label
    ) {
        JSONObject script = ensureScript();
        JSONArray steps = BotnaStore.steps(script);

        JSONObject step = BotnaStore.newStep(type, label);

        steps.put(step);

        BotnaStore.saveSteps(this, script, steps);

        return step;
    }

    private void deleteStep(int index) {
        JSONObject script = ensureScript();

        JSONArray old = BotnaStore.steps(script);
        JSONArray next = new JSONArray();

        for (int i = 0; i < old.length(); i++) {
            if (i != index) {
                next.put(old.optJSONObject(i));
            }
        }

        BotnaStore.saveSteps(this, script, next);

        rebuildPanel();
    }

    private void reorderStep(int from, int to) {
        if (from == to) {
            return;
        }

        JSONObject script = ensureScript();
        JSONArray old = BotnaStore.steps(script);

        if (from < 0 || to < 0
                || from >= old.length()
                || to >= old.length()) {
            return;
        }

        List<JSONObject> list = new ArrayList<>();

        for (int i = 0; i < old.length(); i++) {
            list.add(old.optJSONObject(i));
        }

        JSONObject moved = list.remove(from);
        list.add(to, moved);

        JSONArray next = new JSONArray();

        for (JSONObject item : list) {
            next.put(item);
        }

        BotnaStore.saveSteps(this, script, next);

        rebuildPanel();
        report("تم تغيير ترتيب الخطوة.");
    }

    private void editStep(int index) {
        JSONObject script = ensureScript();
        JSONArray steps = BotnaStore.steps(script);

        JSONObject step = steps.optJSONObject(index);

        if (step == null) {
            return;
        }

        String type = step.optString("type");

        if (TYPE_IMAGE.equals(type)) {
            showChoice(
                    "إعدادات هدف الصورة",
                    new String[]{
                            "قص نسخة/زاوية إضافية",
                            "تعديل نسبة التطابق",
                            "مطابقة الهدف الآن",
                            "حذف هذه الخطوة"
                    },
                    choice -> {
                        if (choice == 0) {
                            cropStepId = step.optString("id");
                            beginCaptureForCrop();

                        } else if (choice == 1) {
                            promptNumber(
                                    "نسبة التطابق المطلوبة (50–99)",
                                    step.optInt("threshold", 85),
                                    50,
                                    99,
                                    value -> {
                                        putInt(step, "threshold", value);

                                        BotnaStore.saveSteps(
                                                this,
                                                script,
                                                steps
                                        );

                                        rebuildPanel();
                                    }
                            );

                        } else if (choice == 2) {
                            testImageStep(step);

                        } else {
                            deleteStep(index);
                        }
                    }
            );

            return;
        }

        if (TYPE_TEXT.equals(type)) {
            promptText(
                    "تعديل النص المستهدف",
                    step.optString("value"),
                    value -> {
                        putString(step, "value", value);

                        putString(
                                step,
                                "label",
                                "نقر نص: " + value
                        );

                        BotnaStore.saveSteps(
                                this,
                                script,
                                steps
                        );

                        rebuildPanel();
                    }
            );

        } else if (TYPE_WAIT.equals(type)) {
            promptNumber(
                    "مدة الانتظار بالمللي ثانية",
                    step.optInt("delay", 500),
                    0,
                    60000,
                    value -> {
                        putInt(step, "delay", value);

                        putString(
                                step,
                                "label",
                                "انتظار " + value + " ms"
                        );

                        BotnaStore.saveSteps(
                                this,
                                script,
                                steps
                        );

                        rebuildPanel();
                    }
            );

        } else {
            showChoice(
                    "تعديل الخطوة",
                    new String[]{
                            "تعطيل/تفعيل الخطوة",
                            "حذف هذه الخطوة"
                    },
                    choice -> {
                        if (choice == 0) {
                            putBoolean(
                                    step,
                                    "enabled",
                                    !step.optBoolean("enabled", true)
                            );

                            BotnaStore.saveSteps(
                                    this,
                                    script,
                                    steps
                            );

                            rebuildPanel();

                        } else {
                            deleteStep(index);
                        }
                    }
            );
        }
    }

    private void chooseScriptDialog() {
        JSONArray all = BotnaStore.scripts(this);
        String[] names = new String[all.length()];

        for (int i = 0; i < all.length(); i++) {
            JSONObject s = all.optJSONObject(i);

            names[i] = s == null
                    ? "سكربت"
                    : s.optString("name", "سكربت");
        }

        if (names.length == 0) {
            BotnaStore.createScript(this, "السكربت الأول");
            rebuildPanel();
            return;
        }

        showChoice("اختر سكربت", names, which -> {
            JSONObject chosen = all.optJSONObject(which);

            if (chosen != null) {
                BotnaStore.setActiveId(
                        this,
                        chosen.optString("id")
                );

                rebuildPanel();
            }
        });
    }

    private void promptText(
            String title,
            String initial,
            TextCallback callback
    ) {
        EditText input = new EditText(this);

        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setText(initial);
        input.setSelectAllOnFocus(true);

        input.setBackground(
                glassShape(BUTTON, CYAN, 1, 12)
        );

        input.setPadding(
                dp(12), dp(10), dp(12), dp(10)
        );

        showCustomDialog(
                title,
                input,
                () -> callback.done(
                        input.getText().toString().trim()
                )
        );
    }

    private interface TextCallback {
        void done(String value);
    }

    private interface IntCallback {
        void done(int value);
    }

    private interface ChoiceCallback {
        void done(int value);
    }

    private void promptNumber(
            String title,
            int initial,
            int min,
            int max,
            IntCallback callback
    ) {
        EditText input = new EditText(this);

        input.setSingleLine(true);
        input.setInputType(2);
        input.setText(String.valueOf(initial));

        showCustomDialog(title, input, () -> {
            int value = initial;

            try {
                value = Integer.parseInt(
                        input.getText().toString().trim()
                );
            } catch (Exception ignored) {
            }

            callback.done(
                    Math.max(min, Math.min(max, value))
            );
        });
    }

    private void showCustomDialog(
            String title,
            View content,
            Runnable positive
    ) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(content)
                .setPositiveButton(
                        "حفظ",
                        (d, w) -> positive.run()
                )
                .setNegativeButton(
                        "إلغاء",
                        (d, w) -> {
                        }
                )
                .create();

        try {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                );
            }

            dialog.show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "تعذر فتح نافذة التعديل",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showChoice(
            String title,
            String[] options,
            ChoiceCallback callback
    ) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(
                        options,
                        (d, which) -> callback.done(which)
                )
                .create();

        try {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setType(
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                );
            }

            dialog.show();

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "تعذر فتح قائمة الخيارات",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // =========================================================
    // التقاط الشاشة وأداة قص الأهداف
    // =========================================================

    private void prepareCropStep() {
        JSONObject script = ensureScript();
        JSONArray steps = BotnaStore.steps(script);
        JSONObject target = null;

        for (int i = steps.length() - 1; i >= 0; i--) {
            JSONObject item = steps.optJSONObject(i);

            if (item != null &&
                    TYPE_IMAGE.equals(item.optString("type"))) {
                target = item;
                break;
            }
        }

        if (target == null) {
            target = addStep(
                    TYPE_IMAGE,
                    "مطابقة صورة الهدف"
            );
        }

        cropStepId = target.optString("id");

        rebuildPanel();
        beginCaptureForCrop();
    }

    private void beginCaptureForCrop() {
        if (Build.VERSION.SDK_INT < 30) {
            report(
                    "قص الصور يحتاج Android 11 أو أحدث."
            );

            return;
        }

        hidePanel();
        removeView(bubble);
        bubble = null;

        handler.postDelayed(() ->
                takeScreenshot((screenshot, error) -> {
                    if (screenshot == null) {
                        showBubble();
                        showPanel();

                        report("تعذر التقاط الشاشة: " + error);
                        return;
                    }

                    showCropSelector(screenshot);
                }),
                220
        );
    }

    private interface ScreenshotCallback {
        void done(Bitmap bitmap, String error);
    }

    private void takeScreenshot(ScreenshotCallback callback) {
        if (Build.VERSION.SDK_INT < 30) {
            callback.done(
                    null,
                    "نسخة أندرويد لا تدعم واجهة اللقطة المطلوبة"
            );

            return;
        }

        try {
            takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    getMainExecutor(),
                    new TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(ScreenshotResult result) {
                            HardwareBuffer buffer = null;
                            Bitmap wrapped = null;

                            try {
                                buffer = result.getHardwareBuffer();

                                wrapped = Bitmap.wrapHardwareBuffer(
                                        buffer,
                                        result.getColorSpace()
                                );

                                if (wrapped == null) {
                                    callback.done(
                                            null,
                                            "تعذر تحويل بيانات الصورة"
                                    );

                                    return;
                                }

                                Bitmap copy = wrapped.copy(
                                        Bitmap.Config.ARGB_8888,
                                        false
                                );

                                callback.done(copy, null);

                            } catch (Exception e) {
                                callback.done(
                                        null,
                                        e.getClass().getSimpleName()
                                );

                            } finally {
                                if (wrapped != null &&
                                        !wrapped.isRecycled()) {
                                    wrapped.recycle();
                                }

                                if (buffer != null) {
                                    buffer.close();
                                }
                            }
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            callback.done(
                                    null,
                                    "رمز الخطأ " + errorCode
                            );
                        }
                    }
            );

        } catch (Exception e) {
            callback.done(
                    null,
                    e.getClass().getSimpleName()
            );
        }
    }

    private void showCropSelector(Bitmap screenshot) {
        cropSelector = new CropSelector(screenshot);

        WindowManager.LayoutParams p =
                new WindowManager.LayoutParams(
                        -1,
                        -1,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT
                );

        p.gravity = Gravity.TOP | Gravity.START;

        try {
            wm.addView(cropSelector, p);

        } catch (Exception e) {
            cropSelector = null;
            screenshot.recycle();

            showBubble();
            showPanel();

            report("تعذر فتح أداة القص.");
        }
    }

    private void finishCrop(
            Bitmap screenshot,
            RectF selection,
            int viewWidth,
            int viewHeight
    ) {
        try {
            float sx = screenshot.getWidth()
                    / (float) Math.max(1, viewWidth);

            float sy = screenshot.getHeight()
                    / (float) Math.max(1, viewHeight);

            int left = Math.max(
                    0,
                    Math.min(
                            screenshot.getWidth() - 1,
                            (int) (selection.left * sx)
                    )
            );

            int top = Math.max(
                    0,
                    Math.min(
                            screenshot.getHeight() - 1,
                            (int) (selection.top * sy)
                    )
            );

            int right = Math.max(
                    left + 1,
                    Math.min(
                            screenshot.getWidth(),
                            (int) (selection.right * sx)
                    )
            );

            int bottom = Math.max(
                    top + 1,
                    Math.min(
                            screenshot.getHeight(),
                            (int) (selection.bottom * sy)
                    )
            );

            Bitmap cropped = Bitmap.createBitmap(
                    screenshot,
                    left,
                    top,
                    right - left,
                    bottom - top
            );

            File folder = new File(
                    getFilesDir(),
                    "botna_targets"
            );

            if (!folder.exists() && !folder.mkdirs()) {
                throw new Exception("تعذر إنشاء مجلد الصور");
            }

            String fileName = "target_"
                    + System.currentTimeMillis()
                    + ".png";

            File file = new File(folder, fileName);

            try (FileOutputStream out =
                         new FileOutputStream(file)) {

                cropped.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        out
                );
            }

            cropped.recycle();

            JSONObject script = ensureScript();
            JSONArray steps = BotnaStore.steps(script);
            boolean found = false;

            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);

                if (step != null &&
                        cropStepId.equals(step.optString("id"))) {

                    JSONArray images =
                            step.optJSONArray("images");

                    if (images == null) {
                        images = new JSONArray();
                    }

                    images.put(fileName);
                    step.put("images", images);

                    found = true;
                    break;
                }
            }

            if (!found) {
                JSONObject step = addStep(
                        TYPE_IMAGE,
                        "مطابقة صورة الهدف"
                );

                JSONArray images = new JSONArray();
                images.put(fileName);

                step.put("images", images);

                steps = BotnaStore.steps(ensureScript());
            }

            BotnaStore.saveSteps(
                    this,
                    ensureScript(),
                    steps
            );

            report(
                    "تم قص الهدف وحفظ الصورة. "
                            + "يمكنك قص زوايا إضافية لنفس الخطوة."
            );

            screenshot.recycle();

        } catch (Exception e) {
            report("فشل حفظ الصورة: " + e.getMessage());

        } finally {
            removeView(cropSelector);
            cropSelector = null;

            showBubble();
            showPanel();
        }
    }

    private void cancelCrop(Bitmap screenshot) {
        removeView(cropSelector);
        cropSelector = null;

        if (screenshot != null && !screenshot.isRecycled()) {
            screenshot.recycle();
        }

        showBubble();
        showPanel();

        report("تم إلغاء القص.");
    }

    private class CropSelector extends View {

        private final Bitmap screenshot;

        private final Paint paint = new Paint(
                Paint.ANTI_ALIAS_FLAG
                        | Paint.FILTER_BITMAP_FLAG
        );

        private final RectF rect = new RectF();
        private final RectF initial = new RectF();

        private int mode;
        private float downX;
        private float downY;
        private final float density;

        CropSelector(Bitmap bitmap) {
            super(BotnaAccessibilityService.this);

            screenshot = bitmap;

            density = getResources()
                    .getDisplayMetrics()
                    .density;

            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onSizeChanged(
                int w,
                int h,
                int oldW,
                int oldH
        ) {
            if (rect.isEmpty()) {
                float cw = w * 0.38f;
                float ch = h * 0.20f;

                rect.set(
                        (w - cw) / 2f,
                        (h - ch) / 2f,
                        (w + cw) / 2f,
                        (h + ch) / 2f
                );
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            c.drawBitmap(
                    screenshot,
                    null,
                    new RectF(0, 0, getWidth(), getHeight()),
                    paint
            );

            paint.setColor(Color.argb(155, 0, 0, 0));

            c.drawRect(0, 0, getWidth(), rect.top, paint);
            c.drawRect(0, rect.bottom, getWidth(), getHeight(), paint);
            c.drawRect(0, rect.top, rect.left, rect.bottom, paint);
            c.drawRect(rect.right, rect.top, getWidth(), rect.bottom, paint);

            paint.setColor(CYAN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3 * density);

            c.drawRect(rect, paint);

            paint.setStyle(Paint.Style.FILL);

            float r = 8 * density;

            c.drawCircle(rect.left, rect.top, r, paint);
            c.drawCircle(rect.right, rect.top, r, paint);
            c.drawCircle(rect.left, rect.bottom, r, paint);
            c.drawCircle(rect.right, rect.bottom, r, paint);

            c.drawCircle(rect.centerX(), rect.top, r, paint);
            c.drawCircle(rect.centerX(), rect.bottom, r, paint);
            c.drawCircle(rect.left, rect.centerY(), r, paint);
            c.drawCircle(rect.right, rect.centerY(), r, paint);

            paint.setColor(Color.argb(225, 5, 7, 12));

            c.drawRoundRect(
                    new RectF(
                            8 * density,
                            8 * density,
                            getWidth() - 8 * density,
                            52 * density
                    ),
                    12 * density,
                    12 * density,
                    paint
            );

            paint.setColor(TEXT);
            paint.setTextSize(14 * density);
            paint.setTextAlign(Paint.Align.CENTER);

            c.drawText(
                    "حرّك الإطار أو اسحب إحدى زواياه لضبط القص",
                    getWidth() / 2f,
                    35 * density,
                    paint
            );

            float by = getHeight() - 60 * density;

            paint.setColor(Color.argb(240, 8, 14, 22));

            c.drawRoundRect(
                    new RectF(
                            getWidth() - 150 * density,
                            by,
                            getWidth() - 8 * density,
                            getHeight() - 8 * density
                    ),
                    12 * density,
                    12 * density,
                    paint
            );

            paint.setColor(CYAN);

            c.drawText(
                    "✓ حفظ الهدف",
                    getWidth() - 79 * density,
                    by + 31 * density,
                    paint
            );

            paint.setColor(Color.rgb(240, 110, 130));

            c.drawText(
                    "إلغاء",
                    65 * density,
                    by + 31 * density,
                    paint
            );
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX();
            float y = e.getY();

            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                downX = x;
                downY = y;
                initial.set(rect);

                if (y > getHeight() - 70 * density) {
                    mode = 10;
                    return true;
                }

                if (distance(x, y, rect.left, rect.top) < 35 * density) {
                    mode = 1;

                } else if (distance(x, y, rect.right, rect.top) < 35 * density) {
                    mode = 2;

                } else if (distance(x, y, rect.left, rect.bottom) < 35 * density) {
                    mode = 3;

                } else if (distance(x, y, rect.right, rect.bottom) < 35 * density) {
                    mode = 4;

                } else if (distance(x, y, rect.centerX(), rect.top) < 35 * density) {
                    mode = 6;

                } else if (distance(x, y, rect.centerX(), rect.bottom) < 35 * density) {
                    mode = 7;

                } else if (distance(x, y, rect.left, rect.centerY()) < 35 * density) {
                    mode = 8;

                } else if (distance(x, y, rect.right, rect.centerY()) < 35 * density) {
                    mode = 9;

                } else if (rect.contains(x, y)) {
                    mode = 5;

                } else {
                    mode = 0;
                    rect.set(x, y, x, y);
                }

                invalidate();
                return true;
            }

            if (e.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = x - downX;
                float dy = y - downY;

                if (mode == 0) {
                    rect.set(
                            Math.min(downX, x),
                            Math.min(downY, y),
                            Math.max(downX, x),
                            Math.max(downY, y)
                    );

                } else if (mode == 1) {
                    rect.left = clamp(
                            initial.left + dx,
                            0,
                            rect.right - 20 * density
                    );

                    rect.top = clamp(
                            initial.top + dy,
                            0,
                            rect.bottom - 20 * density
                    );

                } else if (mode == 2) {
                    rect.right = clamp(
                            initial.right + dx,
                            rect.left + 20 * density,
                            getWidth()
                    );

                    rect.top = clamp(
                            initial.top + dy,
                            0,
                            rect.bottom - 20 * density
                    );

                } else if (mode == 3) {
                    rect.left = clamp(
                            initial.left + dx,
                            0,
                            rect.right - 20 * density
                    );

                    rect.bottom = clamp(
                            initial.bottom + dy,
                            rect.top + 20 * density,
                            getHeight() - 75 * density
                    );

                } else if (mode == 4) {
                    rect.right = clamp(
                            initial.right + dx,
                            rect.left + 20 * density,
                            getWidth()
                    );

                    rect.bottom = clamp(
                            initial.bottom + dy,
                            rect.top + 20 * density,
                            getHeight() - 75 * density
                    );

                } else if (mode == 5) {
                    float w = initial.width();
                    float h = initial.height();

                    float left = clamp(
                            initial.left + dx,
                            0,
                            getWidth() - w
                    );

                    float top = clamp(
                            initial.top + dy,
                            55 * density,
                            getHeight() - 75 * density - h
                    );

                    rect.set(left, top, left + w, top + h);

                } else if (mode == 6) {
                    rect.top = clamp(
                            initial.top + dy,
                            55 * density,
                            rect.bottom - 20 * density
                    );

                } else if (mode == 7) {
                    rect.bottom = clamp(
                            initial.bottom + dy,
                            rect.top + 20 * density,
                            getHeight() - 75 * density
                    );

                } else if (mode == 8) {
                    rect.left = clamp(
                            initial.left + dx,
                            0,
                            rect.right - 20 * density
                    );

                } else if (mode == 9) {
                    rect.right = clamp(
                            initial.right + dx,
                            rect.left + 20 * density,
                            getWidth()
                    );
                }

                invalidate();
                return true;
            }

            if (e.getAction() == MotionEvent.ACTION_UP) {
                if (mode == 10) {
                    if (x > getWidth() / 2f) {
                        finishCrop(
                                screenshot,
                                new RectF(rect),
                                getWidth(),
                                getHeight()
                        );
                    } else {
                        cancelCrop(screenshot);
                    }
                }

                invalidate();
                return true;
            }

            return true;
        }

        private float distance(
                float x,
                float y,
                float a,
                float b
        ) {
            float dx = x - a;
            float dy = y - b;

            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private float clamp(
                float value,
                float min,
                float max
        ) {
            return Math.max(min, Math.min(max, value));
        }
    }

    // =========================================================
    // مطابقة الصور وتشغيل السكربتات
    // =========================================================

    private void testImageStep(JSONObject step) {
        captureAppScreenshot((screen, error) -> {
            if (screen == null) {
                report("فشل فحص الصورة: " + error);
                return;
            }

            ImageMatcher.Result result = matchStep(screen, step);
            screen.recycle();

            if (result != null &&
                    result.score >= step.optInt("threshold", 85)) {

                report(
                        "تم العثور على الهدف بنسبة تقريبية "
                                + result.score
                                + "% عند X="
                                + result.x
                                + ", Y="
                                + result.y
                                + "."
                );

            } else {
                report(
                        "لم يصل التطابق إلى الحد المطلوب. أفضل تطابق تقريبي: "
                                + (result == null ? 0 : result.score)
                                + "%."
                );
            }
        });
    }

    private void runActiveScript() {
        if (running) {
            report("السكربت يعمل بالفعل.");
            return;
        }

        JSONObject script = ensureScript();
        JSONArray steps = BotnaStore.steps(script);

        if (steps.length() == 0) {
            report("أضف خطوات قبل التشغيل.");
            return;
        }

        running = true;
        lastStepSuccess = true;

        runStep(
                script.optString("id"),
                0,
                new ArrayDeque<>()
        );
    }

    private static final class Branch {
        final boolean parentAllowed;
        final boolean matched;
        boolean inElse;

        Branch(boolean parentAllowed, boolean matched) {
            this.parentAllowed = parentAllowed;
            this.matched = matched;
        }
    }

    private boolean branchAllowed(Deque<Branch> branches) {
        for (Branch b : branches) {
            if (!b.parentAllowed) {
                return false;
            }

            if (b.inElse ? b.matched : !b.matched) {
                return false;
            }
        }

        return true;
    }

    private void runStep(
            String scriptId,
            int index,
            Deque<Branch> branches
    ) {
        if (!running) {
            return;
        }

        JSONObject script = BotnaStore.getScript(this, scriptId);

        if (script == null) {
            running = false;
            report("لم يتم العثور على السكربت.");
            return;
        }

        JSONArray steps = BotnaStore.steps(script);

        if (index >= steps.length()) {
            running = false;

            report(
                    "انتهى السكربت. آخر خطوة: "
                            + (lastStepSuccess ? "نجاح" : "فشل")
                            + "."
            );

            return;
        }

        JSONObject step = steps.optJSONObject(index);

        if (step == null) {
            runStep(scriptId, index + 1, branches);
            return;
        }

        if (!step.optBoolean("enabled", true)) {
            handler.post(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    )
            );

            return;
        }

        String type = step.optString("type");
        boolean activeBefore = branchAllowed(branches);

        if (TYPE_IF_SUCCESS.equals(type)
                || TYPE_IF_FAIL.equals(type)) {

            boolean condition = TYPE_IF_SUCCESS.equals(type)
                    ? lastStepSuccess
                    : !lastStepSuccess;

            branches.push(
                    new Branch(activeBefore, condition)
            );

            handler.post(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    )
            );

            return;
        }

        if (TYPE_ELSE.equals(type)) {
            if (!branches.isEmpty()) {
                branches.peek().inElse = true;
            }

            handler.post(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    )
            );

            return;
        }

        if (TYPE_END_IF.equals(type)) {
            if (!branches.isEmpty()) {
                branches.pop();
            }

            handler.post(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    )
            );

            return;
        }

        if (!activeBefore) {
            handler.post(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    )
            );

            return;
        }

        if (TYPE_TEXT.equals(type)) {
            boolean ok = clickText(step.optString("value"));
            lastStepSuccess = ok;

            report(
                    ok
                            ? "نجحت خطوة النقر على النص: "
                                    + step.optString("value")
                            : "فشلت خطوة النقر على النص: "
                                    + step.optString("value")
            );

            handler.post(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    )
            );

        } else if (TYPE_BACK.equals(type)) {
            lastStepSuccess = performGlobalAction(
                    GLOBAL_ACTION_BACK
            );

            report(
                    lastStepSuccess
                            ? "نجحت خطوة الرجوع."
                            : "فشلت خطوة الرجوع."
            );

            handler.post(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    )
            );

        } else if (TYPE_HOME.equals(type)) {
            lastStepSuccess = performGlobalAction(
                    GLOBAL_ACTION_HOME
            );

            report(
                    lastStepSuccess
                            ? "تم أمر الرئيسية."
                            : "فشل أمر الرئيسية."
            );

            handler.post(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    )
            );

        } else if (TYPE_WAIT.equals(type)) {
            int delay = Math.max(
                    0,
                    Math.min(
                            60000,
                            step.optInt("delay", 500)
                    )
            );

            lastStepSuccess = true;

            handler.postDelayed(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    ),
                    delay
            );

        } else if (TYPE_IMAGE.equals(type)) {
            captureAppScreenshot((screen, error) -> {
                if (screen == null) {
                    lastStepSuccess = false;

                    report(
                            "فشل التقاط الشاشة للمطابقة: " + error
                    );

                    handler.post(
                            () -> runStep(
                                    scriptId,
                                    index + 1,
                                    branches
                            )
                    );

                    return;
                }

                ImageMatcher.Result match = matchStep(screen, step);
                screen.recycle();

                boolean matched = match != null
                        && match.score >= step.optInt("threshold", 85);

                if (!matched) {
                    lastStepSuccess = false;

                    report(
                            "فشلت مطابقة الصورة؛ أفضل نتيجة "
                                    + (match == null ? 0 : match.score)
                                    + "%"
                    );

                    handler.post(
                            () -> runStep(
                                    scriptId,
                                    index + 1,
                                    branches
                            )
                    );

                } else {
                    report(
                            "تم العثور على الهدف بنسبة تقريبية "
                                    + match.score
                                    + "%؛ جارٍ الضغط على مركزه."
                    );

                    tapMatchedTarget(match, ok -> {
                        lastStepSuccess = ok;

                        report(
                                ok
                                        ? "نجحت مطابقة الصورة والضغط على الهدف."
                                        : "تم التعرف على الهدف لكن فشل الضغط عليه."
                        );

                        handler.post(
                                () -> runStep(
                                        scriptId,
                                        index + 1,
                                        branches
                                )
                        );
                    });
                }
            });

        } else {
            lastStepSuccess = false;

            handler.post(
                    () -> runStep(
                            scriptId,
                            index + 1,
                            branches
                    )
            );
        }
    }

    private ImageMatcher.Result matchStep(
            Bitmap screen,
            JSONObject step
    ) {
        JSONArray images = step.optJSONArray("images");

        if (images == null || images.length() == 0) {
            return null;
        }

        List<ImageMatcher.NamedBitmap> templates =
                new ArrayList<>();

        for (int i = 0; i < images.length(); i++) {
            String name = images.optString(i);
            Bitmap bitmap = readTemplate(name);

            if (bitmap != null) {
                templates.add(
                        new ImageMatcher.NamedBitmap(name, bitmap)
                );
            }
        }

        ImageMatcher.Result result = ImageMatcher.find(
                screen,
                templates,
                step.optInt("threshold", 85)
        );

        for (ImageMatcher.NamedBitmap b : templates) {
            if (!b.bitmap.isRecycled()) {
                b.bitmap.recycle();
            }
        }

        return result;
    }

    private void captureAppScreenshot(
            ScreenshotCallback callback
    ) {
        if (Build.VERSION.SDK_INT < 30) {
            callback.done(null, "يتطلب Android 11 أو أحدث");
            return;
        }

        boolean wasPanel = panelVisible;

        hidePanel();
        removeView(bubble);
        bubble = null;

        handler.postDelayed(() ->
                takeScreenshot((bitmap, error) -> {
                    showBubble();

                    if (wasPanel) {
                        showPanel();
                    }

                    callback.done(bitmap, error);
                }),
                180
        );
    }

    private interface GestureCallback {
        void done(boolean success);
    }

    private void tapMatchedTarget(
            ImageMatcher.Result match,
            GestureCallback callback
    ) {
        if (Build.VERSION.SDK_INT < 24 || match == null) {
            callback.done(false);
            return;
        }

        boolean wasPanel = panelVisible;

        hidePanel();
        removeView(bubble);
        bubble = null;

        handler.postDelayed(() -> {
            try {
                Path path = new Path();

                float x = match.x + match.width / 2f;
                float y = match.y + match.height / 2f;

                path.moveTo(x, y);
                path.lineTo(x + 1f, y + 1f);

                GestureDescription.StrokeDescription stroke =
                        new GestureDescription.StrokeDescription(
                                path,
                                0,
                                90
                        );

                GestureDescription gesture =
                        new GestureDescription.Builder()
                                .addStroke(stroke)
                                .build();

                boolean dispatched = dispatchGesture(
                        gesture,
                        new GestureResultCallback() {
                            @Override
                            public void onCompleted(
                                    GestureDescription description
                            ) {
                                showBubble();

                                if (wasPanel) {
                                    showPanel();
                                }

                                callback.done(true);
                            }

                            @Override
                            public void onCancelled(
                                    GestureDescription description
                            ) {
                                showBubble();

                                if (wasPanel) {
                                    showPanel();
                                }

                                callback.done(false);
                            }
                        },
                        handler
                );

                if (!dispatched) {
                    showBubble();

                    if (wasPanel) {
                        showPanel();
                    }

                    callback.done(false);
                }

            } catch (Exception e) {
                showBubble();

                if (wasPanel) {
                    showPanel();
                }

                callback.done(false);
            }
        }, 140);
    }

    // =========================================================
    // قراءة الشاشة والنقر على النص
    // =========================================================

    private boolean clickText(String target) {
        if (target == null || target.trim().isEmpty()) {
            return false;
        }

        AccessibilityNodeInfo root = findTargetRoot();

        if (root == null) {
            return false;
        }

        try {
            if (findAndClick(root, target.trim(), true)) {
                return true;
            }

            return findAndClick(root, target.trim(), false);

        } finally {
            root.recycle();
        }
    }

    private AccessibilityNodeInfo findTargetRoot() {
        try {
            for (AccessibilityWindowInfo win : getWindows()) {
                if (win == null ||
                        win.getType()
                                != AccessibilityWindowInfo.TYPE_APPLICATION) {
                    continue;
                }

                AccessibilityNodeInfo root = win.getRoot();

                if (root == null) {
                    continue;
                }

                CharSequence pkg = root.getPackageName();

                if (pkg != null &&
                        getPackageName().contentEquals(pkg)) {
                    root.recycle();
                    continue;
                }

                return root;
            }

        } catch (Exception ignored) {
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();

        if (root != null
                && root.getPackageName() != null
                && getPackageName().contentEquals(
                        root.getPackageName()
                )) {

            root.recycle();
            return null;
        }

        return root;
    }

    private boolean findAndClick(
            AccessibilityNodeInfo node,
            String target,
            boolean exact
    ) {
        if (node == null) {
            return false;
        }

        String value = node.getText() == null
                ? ""
                : node.getText().toString().trim();

        if (value.isEmpty()
                && node.getContentDescription() != null) {
            value = node.getContentDescription()
                    .toString()
                    .trim();
        }

        boolean hit = exact
                ? value.equalsIgnoreCase(target)
                : value.toLowerCase(Locale.ROOT)
                        .contains(
                                target.toLowerCase(Locale.ROOT)
                        );

        if (hit && clickNodeOrParent(node)) {
            return true;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child == null) {
                continue;
            }

            boolean found;

            try {
                found = findAndClick(child, target, exact);
            } finally {
                child.recycle();
            }

            if (found) {
                return true;
            }
        }

        return false;
    }

    private boolean clickNodeOrParent(
            AccessibilityNodeInfo node
    ) {
        if (node.isClickable()) {
            return node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
            );
        }

        AccessibilityNodeInfo parent = node.getParent();

        while (parent != null) {
            if (parent.isClickable()) {
                boolean ok = parent.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                );

                parent.recycle();
                return ok;
            }

            AccessibilityNodeInfo next = parent.getParent();

            parent.recycle();
            parent = next;
        }

        return false;
    }

    private void readScreen() {
        AccessibilityNodeInfo root = findTargetRoot();

        if (root == null) {
            report(
                    "لم أستطع قراءة التطبيق خلف اللوحة. "
                            + "أغلق لوحة بوتنا ثم أعد المحاولة."
            );

            return;
        }

        List<String> values = new ArrayList<>();

        try {
            collectText(root, values, 0);
        } finally {
            root.recycle();
        }

        if (values.isEmpty()) {
            report(
                    "لم تظهر نصوص قابلة للقراءة. "
                            + "بعض الألعاب ترسم الأزرار داخل صورة أو مشهد Unity."
            );

        } else {
            report(
                    "تم العثور على "
                            + values.size()
                            + " نصًا: "
                            + joinFirst(values, 14)
            );
        }
    }

    private void collectText(
            AccessibilityNodeInfo node,
            List<String> values,
            int depth
    ) {
        if (node == null
                || values.size() >= 35
                || depth > 24) {
            return;
        }

        CharSequence t = node.getText();
        CharSequence d = node.getContentDescription();

        String value = t == null
                ? ""
                : t.toString().trim();

        if (value.isEmpty() && d != null) {
            value = d.toString().trim();
        }

        if (!value.isEmpty() && !values.contains(value)) {
            values.add(value);
        }

        for (int i = 0;
                i < node.getChildCount() && values.size() < 35;
                i++) {

            AccessibilityNodeInfo child = node.getChild(i);

            if (child == null) {
                continue;
            }

            try {
                collectText(child, values, depth + 1);
            } finally {
                child.recycle();
            }
        }
    }

    private String joinFirst(
            List<String> values,
            int max
    ) {
        StringBuilder out = new StringBuilder();

        int n = Math.min(max, values.size());

        for (int i = 0; i < n; i++) {
            if (i > 0) {
                out.append(" • ");
            }

            out.append(values.get(i));
        }

        if (values.size() > n) {
            out.append(" • …");
        }

        return out.toString();
    }

    // =========================================================
    // عناصر الواجهة والمساعدات
    // =========================================================

    private void addButton(
            LinearLayout row,
            String title,
            Runnable action
    ) {
        Button b = button(title, true);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                        0,
                        dp(40),
                        1
                );

        lp.setMargins(dp(2), 0, dp(2), 0);

        row.addView(b, lp);

        b.setOnClickListener(v -> action.run());
    }

    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this);

        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        return l;
    }

    private Button button(
            String title,
            boolean primary
    ) {
        Button b = new Button(this);

        b.setText(title);
        b.setTextSize(12);
        b.setAllCaps(false);

        b.setPadding(
                dp(4), dp(3), dp(4), dp(3)
        );

        b.setTextColor(TEXT);

        b.setBackground(
                glassShape(
                        primary
                                ? Color.argb(95, 35, 226, 244)
                                : BUTTON,
                        primary
                                ? Color.argb(235, 35, 226, 244)
                                : Color.argb(150, 35, 226, 244),
                        1,
                        12
                )
        );

        b.setElevation(dp(5));

        return b;
    }

    private TextView label(
            String value,
            int size,
            int color,
            boolean bold
    ) {
        TextView t = new TextView(this);

        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);

        if (bold) {
            t.setTypeface(
                    android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD
            );
        }

        return t;
    }

    private android.graphics.drawable.GradientDrawable glassShape(
            int fill,
            int stroke,
            int width,
            int radius
    ) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();

        d.setColor(fill);
        d.setCornerRadius(dp(radius));

        if (width > 0) {
            d.setStroke(dp(width), stroke);
        }

        return d;
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private int dp(float value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private void removeView(View view) {
        if (wm != null && view != null) {
            try {
                wm.removeView(view);
            } catch (Exception ignored) {
            }
        }
    }

    private void saveResult(String message) {
        String stamp = new java.text.SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
        ).format(new java.util.Date());

        getSharedPreferences(
                BotnaStore.PREFS,
                MODE_PRIVATE
        ).edit()
                .putString(
                        BotnaStore.KEY_LAST_RESULT,
                        stamp + " — " + message
                )
                .apply();
    }

    private String getLastResult() {
        return getSharedPreferences(
                BotnaStore.PREFS,
                MODE_PRIVATE
        ).getString(
                BotnaStore.KEY_LAST_RESULT,
                "جاهز."
        );
    }

    private void report(String message) {
        saveResult(message);

        if (resultLabel != null) {
            resultLabel.setText(getLastResult());
        }
    }

    private void putString(
            JSONObject o,
            String k,
            String v
    ) {
        try {
            o.put(k, v);
        } catch (JSONException ignored) {
        }
    }

    private void putInt(
            JSONObject o,
            String k,
            int v
    ) {
        try {
            o.put(k, v);
        } catch (JSONException ignored) {
        }
    }

    private void putBoolean(
            JSONObject o,
            String k,
            boolean v
    ) {
        try {
            o.put(k, v);
        } catch (JSONException ignored) {
        }
    }

    private Bitmap readTemplate(String name) {
        if (name == null
                || name.contains("/")
                || name.contains("..")) {
            return null;
        }

        File f = new File(
                new File(getFilesDir(), "botna_targets"),
                name
        );

        if (!f.exists()) {
            return null;
        }

        try {
            return android.graphics.BitmapFactory.decodeFile(
                    f.getAbsolutePath()
            );
        } catch (Exception e) {
            return null;
        }
    }
}
