package com.botna.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String PREFS = "botna_settings";
    private static final String KEY_LAST_RESULT = "last_result";

    private static final int BG = Color.rgb(5, 7, 12);
    private static final int CARD = Color.argb(238, 13, 17, 26);
    private static final int TEXT = Color.rgb(235, 246, 255);
    private static final int MUTED = Color.rgb(150, 174, 195);
    private static final int CYAN = Color.rgb(35, 226, 244);

    private TextView serviceStatus;
    private TextView lastResult;
    private TextView dataSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        buildInterface();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(22), dp(20), dp(28));
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(page);

        TextView title = text("بوتنا  Botna", 30, TEXT, true);
        page.addView(title, matchWrap());
        TextView subtitle = text("منصة أتمتة ذكية ومباشرة لهاتفك", 15, MUTED, false);
        LinearLayout.LayoutParams subLp = matchWrap(); subLp.topMargin = dp(4); page.addView(subtitle, subLp);

        LinearLayout statusCard = card();
        LinearLayout.LayoutParams statusCardLp = matchWrap(); statusCardLp.topMargin = dp(22); page.addView(statusCard, statusCardLp);
        statusCard.addView(text("حالة خدمة الأتمتة", 18, TEXT, true));
        serviceStatus = text("جارٍ فحص الحالة...", 14, MUTED, false);
        LinearLayout.LayoutParams statusTextLp = matchWrap(); statusTextLp.topMargin = dp(8); statusCard.addView(serviceStatus, statusTextLp);
        Button settingsButton = button("تفعيل / إدارة خدمة إمكانية الوصول");
        LinearLayout.LayoutParams settingsLp = matchWrap(); settingsLp.topMargin = dp(14); statusCard.addView(settingsButton, settingsLp);
        settingsButton.setOnClickListener(v -> openAccessibilitySettings());

        LinearLayout dashboard = card();
        LinearLayout.LayoutParams dashLp = matchWrap(); dashLp.topMargin = dp(16); page.addView(dashboard, dashLp);
        dashboard.addView(text("مركز التحكم", 19, TEXT, true));
        TextView dashboardHelp = text("أنشئ السكربتات من الفقاعة B العائمة، واضبط مكتبة الصور، وشغّل الأوامر مع إمكانية الإيقاف والاستكمال.", 14, MUTED, false);
        LinearLayout.LayoutParams dashHelpLp = matchWrap(); dashHelpLp.topMargin = dp(7); dashboard.addView(dashboardHelp, dashHelpLp);

        LinearLayout primaryRow = new LinearLayout(this); primaryRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams primaryLp = matchWrap(); primaryLp.topMargin = dp(12); dashboard.addView(primaryRow, primaryLp);
        Button openService = button("إدارة الخدمة");
        Button aiSettings = button("إعدادات الذكاء الاصطناعي");
        primaryRow.addView(openService, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(0, dp(48), 1); aiLp.leftMargin = dp(7); primaryRow.addView(aiSettings, aiLp);
        openService.setOnClickListener(v -> openAccessibilitySettings());
        aiSettings.setOnClickListener(v -> BotnaAICompiler.showSettingsDialog(this, false, this::refreshStatus));

        LinearLayout secondaryRow = new LinearLayout(this); secondaryRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondaryLp = matchWrap(); secondaryLp.topMargin = dp(8); dashboard.addView(secondaryRow, secondaryLp);
        Button commandGuide = button("دليل الأوامر المخصصة");
        Button refresh = button("تحديث الحالة");
        secondaryRow.addView(commandGuide, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams refreshButtonLp = new LinearLayout.LayoutParams(0, dp(44), 1); refreshButtonLp.leftMargin = dp(7); secondaryRow.addView(refresh, refreshButtonLp);
        commandGuide.setOnClickListener(v -> showCommandGuide());
        refresh.setOnClickListener(v -> refreshStatus());

        LinearLayout agentRow = new LinearLayout(this); agentRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams agentRowLp = matchWrap(); agentRowLp.topMargin = dp(8); dashboard.addView(agentRow, agentRowLp);
        Button logsButton = button("قائمة السجلات");
        Button smartTaskButton = button("إنشاء بوت ذكي للمهام المعقدة");
        agentRow.addView(logsButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        LinearLayout.LayoutParams smartLp = new LinearLayout.LayoutParams(0, dp(48), 1); smartLp.leftMargin = dp(7); agentRow.addView(smartTaskButton, smartLp);
        logsButton.setOnClickListener(v -> showLogsDialog());
        smartTaskButton.setOnClickListener(v -> showSmartTaskDialog());

        LinearLayout agentListsRow = new LinearLayout(this); agentListsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams agentListsLp = matchWrap(); agentListsLp.topMargin = dp(8); dashboard.addView(agentListsRow, agentListsLp);
        Button savedAgentTasksButton = button("مهام الوكيل المحفوظة");
        Button agentMessagesButton = button("رسائل الوكيل");
        agentListsRow.addView(savedAgentTasksButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams agentMessagesLp = new LinearLayout.LayoutParams(0, dp(46), 1); agentMessagesLp.leftMargin = dp(7); agentListsRow.addView(agentMessagesButton, agentMessagesLp);
        savedAgentTasksButton.setOnClickListener(v -> showSavedAgentTasksDialog());
        agentMessagesButton.setOnClickListener(v -> showAgentMessagesDialog());

        LinearLayout dataCard = card();
        LinearLayout.LayoutParams dataLp = matchWrap(); dataLp.topMargin = dp(16); page.addView(dataCard, dataLp);
        dataCard.addView(text("بياناتك المحلية", 18, TEXT, true));
        int scriptsCount = BotnaStore.scripts(this).length();
        int imagesCount = BotnaStore.imageLibrary(this).length();
        dataSummary = text("السكربتات المحفوظة: " + scriptsCount + "\nقوالب مكتبة الصور: " + imagesCount
                + "\nمهام الوكيل المحفوظة: " + BotnaAgentStore.tasks(this).length()
                + "\nرسائل الوكيل: " + BotnaAgentStore.messages(this).length()
                + "\nالمزوّد المحدد: " + BotnaAICompiler.providerLabel(BotnaAICompiler.providerId(this))
                + "\nحالة الذكاء الاصطناعي: " + (BotnaAICompiler.isConfigured(this) ? "إعداد API مكتمل" : "يتطلب إعداد مفتاح/نقطة اتصال API"), 14, MUTED, false);
        dataCard.addView(dataSummary, matchWrap());
        TextView localInfo = text("السكربتات وقوالب الصور تبقى في مساحة التطبيق. يُشفّر مفتاح API محليًا باستخدام Android Keystore؛ لا تشارك المفتاح ولا تضعه في مستودع GitHub. استخدام مزوّد الذكاء الاصطناعي عبر API قد يكلّف رسومًا حسب حسابك.", 12, MUTED, false);
        LinearLayout.LayoutParams localInfoLp = matchWrap(); localInfoLp.topMargin = dp(10); dataCard.addView(localInfo, localInfoLp);

        LinearLayout resultCard = card();
        LinearLayout.LayoutParams resultLp = matchWrap(); resultLp.topMargin = dp(16); page.addView(resultCard, resultLp);
        resultCard.addView(text("آخر نتيجة", 18, TEXT, true));
        lastResult = text("لم يتم تنفيذ أي إجراء بعد.", 14, MUTED, false);
        LinearLayout.LayoutParams lastLp = matchWrap(); lastLp.topMargin = dp(8); resultCard.addView(lastResult, lastLp);
        Button refreshResult = button("تحديث السجل");
        LinearLayout.LayoutParams refreshResultLp = matchWrap(); refreshResultLp.topMargin = dp(12); resultCard.addView(refreshResult, refreshResultLp);
        refreshResult.setOnClickListener(v -> refreshStatus());

        TextView disclaimer = text("ملاحظة: تفعيل خدمة إمكانية الوصول يتم يدويًا من إعدادات أندرويد. قبل تشغيل أي أمر مولّد بالذكاء الاصطناعي راجع معاينة الخطوات؛ لن يُسمح للمترجم بتنفيذ كود عشوائي خارج أنواع الأوامر المدعومة.", 12, MUTED, false);
        LinearLayout.LayoutParams disclaimerLp = matchWrap(); disclaimerLp.topMargin = dp(20); page.addView(disclaimer, disclaimerLp);
        setContentView(scroll);
    }

    private void showSmartTaskDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), dp(4), dp(6), dp(4));
        TextView help = text("اكتب الهدف النهائي كما تريده. سيحلل الوكيل لقطة الشاشة بعد كل إجراء، ثم يقرر الخطوة التالية بناءً على النتيجة. يحتاج إلى خدمة إمكانية الوصول ومزوّد API يدعم الصور. الحد الوقائي 40 إجراءً لكل مهمة.", 13, MUTED, false);
        content.addView(help, matchWrap());
        EditText task = new EditText(this);
        task.setSingleLine(false); task.setMinLines(5); task.setMaxLines(10);
        task.setGravity(Gravity.TOP | Gravity.START); task.setTextSize(15); task.setTextColor(TEXT); task.setHintTextColor(MUTED);
        task.setHint("مثال: افتح التطبيق، ابحث عن العنصر المطلوب، افتحه ثم تحقق من النتيجة...");
        task.setPadding(dp(12), dp(10), dp(12), dp(10)); task.setBackground(roundDrawable(CARD, CYAN, 1, 14));
        LinearLayout.LayoutParams taskLp = matchWrap(); taskLp.topMargin = dp(10); content.addView(task, taskLp);
        CheckBox confirmation = new CheckBox(this);
        confirmation.setText("أفهم أن الوكيل سيرى لقطات الشاشة ويتحكم بالواجهة وفق المهمة، وسأراجع العمليات الحساسة بنفسي.");
        confirmation.setTextColor(MUTED); confirmation.setTextSize(12); confirmation.setChecked(false);
        LinearLayout.LayoutParams confirmLp = matchWrap(); confirmLp.topMargin = dp(8); content.addView(confirmation, confirmLp);
        ScrollView taskScroll = new ScrollView(this);
        taskScroll.setFillViewport(false);
        taskScroll.setSmoothScrollingEnabled(true);
        taskScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout dialogRoot = new LinearLayout(this); dialogRoot.setOrientation(LinearLayout.VERTICAL);
        dialogRoot.addView(taskScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout actionBar = new LinearLayout(this); actionBar.setOrientation(LinearLayout.HORIZONTAL);
        Button startTaskButton = button("بدء المهمة"); Button cancelTaskButton = button("إلغاء");
        actionBar.addView(startTaskButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
        LinearLayout.LayoutParams cancelTaskLp = new LinearLayout.LayoutParams(0, dp(48), 1f); cancelTaskLp.leftMargin = dp(8); actionBar.addView(cancelTaskButton, cancelTaskLp);
        LinearLayout.LayoutParams actionBarLp = matchWrap(); actionBarLp.topMargin = dp(6); dialogRoot.addView(actionBar, actionBarLp);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("إنشاء بوت ذكي للمهام الصعبة والطويلة")
                .setView(dialogRoot).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(560)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(48), Math.round(getResources().getDisplayMetrics().heightPixels * 0.78f)));
        }
        task.setOnFocusChangeListener((v, focused) -> {
            if (focused) taskScroll.postDelayed(() -> taskScroll.smoothScrollTo(0, Math.max(0, task.getBottom() - taskScroll.getHeight() + dp(32))), 300);
        });
        task.setOnClickListener(v -> taskScroll.postDelayed(() -> taskScroll.smoothScrollTo(0, Math.max(0, task.getBottom() - taskScroll.getHeight() + dp(32))), 300));
        cancelTaskButton.setOnClickListener(v -> dialog.dismiss());
        startTaskButton.setOnClickListener(v -> {
            String command = task.getText() == null ? "" : task.getText().toString().trim();
            if (command.isEmpty()) { task.setError("اكتب وصف المهمة أولًا."); return; }
            if (!confirmation.isChecked()) { Toast.makeText(this, "أكد فهمك لاستخدام لقطات الشاشة والتحكم بالواجهة أولًا.", Toast.LENGTH_LONG).show(); return; }
            if (!BotnaAICompiler.isConfigured(this)) {
                Toast.makeText(this, "اضبط مزود API ومفتاحًا واحدًا على الأقل قبل بدء الوكيل الذكي.", Toast.LENGTH_LONG).show(); return;
            }
            String taskId = BotnaAgentStore.createTask(this, command);
            dialog.dismiss();
            dispatchAgentTask(taskId, command);
        });
    }

    private void dispatchAgentTask(String taskId, String goal) {
        if (!BotnaAICompiler.isConfigured(this)) {
            Toast.makeText(this, "اضبط مزود API ومفتاحًا صالحًا قبل تشغيل الوكيل.", Toast.LENGTH_LONG).show();
            return;
        }
        if (taskId == null || taskId.trim().isEmpty()) taskId = BotnaAgentStore.createTask(this, goal);
        if (!isServiceEnabled()) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK, goal)
                    .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK_ID, taskId).apply();
            BotnaAgentStore.setStatus(this, taskId, "بانتظار تفعيل الخدمة", "فعّل خدمة إمكانية الوصول لبدء المهمة.");
            Toast.makeText(this, "حُفظت المهمة. فعّل خدمة Botna من إعدادات إمكانية الوصول، وستبدأ بعد تفعيلها.", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }
        Intent start = new Intent(BotnaAccessibilityService.ACTION_START_SMART_TASK).setPackage(getPackageName())
                .putExtra("task", goal).putExtra("agentTaskId", taskId);
        sendBroadcast(start);
        Toast.makeText(this, "أُرسلت المهمة إلى الوكيل. ستُحفظ خطواته ورسائله ويمكن مراجعتها لاحقًا.", Toast.LENGTH_LONG).show();
    }

    private void showSavedAgentTasksDialog() {
        org.json.JSONArray tasks = BotnaAgentStore.tasks(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(8), dp(5), dp(8), dp(5));
        TextView intro = text("كل مهمة محفوظة بشكل مستقل. إعادة تشغيل الوكيل تعيد التخطيط حسب الشاشة، أما تشغيل السكربت فيعيد الإجراءات المسجلة كما هي. افتح السكربت لتعديل الخطوات داخل محرر بوتنا.", 12, MUTED, false);
        root.addView(intro, matchWrap());
        Button create = button("＋ إنشاء مهمة ذكية جديدة");
        LinearLayout.LayoutParams createLp = matchWrap(); createLp.topMargin = dp(6); root.addView(create, createLp);
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, dp(430)); scrollLp.topMargin = dp(8); root.addView(scroll, scrollLp);
        if (tasks.length() == 0) list.addView(text("لا توجد مهام محفوظة بعد. أنشئ مهمة من الزر أعلاه.", 13, MUTED, false), matchWrap());
        final AlertDialog[] owner = new AlertDialog[1];
        create.setOnClickListener(v -> { if (owner[0] != null) owner[0].dismiss(); showSmartTaskDialog(); });
        for (int i = 0; i < tasks.length(); i++) {
            org.json.JSONObject item = tasks.optJSONObject(i); if (item == null) continue;
            String id = item.optString("id", ""), name = item.optString("name", "مهمة ذكية"), goal = item.optString("goal", "");
            String scriptId = item.optString("scriptId", "");
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(8), dp(7), dp(8), dp(7));
            card.setBackground(roundDrawable(CARD, CYAN, 1, 12)); LinearLayout.LayoutParams cardLp = matchWrap(); cardLp.topMargin = dp(6); list.addView(card, cardLp);
            card.addView(text(name, 15, TEXT, true), matchWrap());
            TextView status = text("الحالة: " + item.optString("status", "محفوظة") + " • مرات التشغيل: " + item.optInt("runs", 0)
                    + " • إجراءات محفوظة: " + BotnaStore.steps(BotnaStore.getScript(this, scriptId)).length(), 11, MUTED, false);
            LinearLayout.LayoutParams statusLp = matchWrap(); statusLp.topMargin = dp(3); card.addView(status, statusLp);
            TextView goalText = text(goal.length() > 180 ? goal.substring(0, 180) + "…" : goal, 12, MUTED, false);
            LinearLayout.LayoutParams goalLp = matchWrap(); goalLp.topMargin = dp(4); card.addView(goalText, goalLp);
            LinearLayout buttons1 = new LinearLayout(this); buttons1.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams buttons1Lp = matchWrap(); buttons1Lp.topMargin = dp(6); card.addView(buttons1, buttons1Lp);
            Button replayAgent = button("إعادة الوكيل"); Button edit = button("تعديل المهمة"); Button editScript = button("تعديل السكربت");
            buttons1.addView(replayAgent, new LinearLayout.LayoutParams(0, dp(40), 1));
            LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(0, dp(40), 1); editLp.leftMargin = dp(4); buttons1.addView(edit, editLp);
            LinearLayout.LayoutParams editScriptLp = new LinearLayout.LayoutParams(0, dp(40), 1); editScriptLp.leftMargin = dp(4); buttons1.addView(editScript, editScriptLp);
            LinearLayout buttons2 = new LinearLayout(this); buttons2.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams buttons2Lp = matchWrap(); buttons2Lp.topMargin = dp(4); card.addView(buttons2, buttons2Lp);
            Button playScript = button("تشغيل السكربت المحفوظ"); Button delete = button("حذف المهمة");
            buttons2.addView(playScript, new LinearLayout.LayoutParams(0, dp(38), 1));
            LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(0, dp(38), 1); deleteLp.leftMargin = dp(5); buttons2.addView(delete, deleteLp);
            replayAgent.setOnClickListener(v -> { if (owner[0] != null) owner[0].dismiss(); confirmRunSavedAgent(id); });
            edit.setOnClickListener(v -> { if (owner[0] != null) owner[0].dismiss(); showEditAgentTaskDialog(id); });
            editScript.setOnClickListener(v -> { if (owner[0] != null) owner[0].dismiss(); dispatchAgentScript(id, false); });
            playScript.setOnClickListener(v -> { if (owner[0] != null) owner[0].dismiss(); dispatchAgentScript(id, true); });
            delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("حذف المهمة الذكية")
                    .setMessage("سيُحذف تعريف المهمة وسكربتها المحفوظة. تبقى رسائل الوكيل وسجلات التشخيص كي تتمكن من مراجعة ما حدث.")
                    .setPositiveButton("حذف", (d, w) -> { BotnaAgentStore.deleteTask(this, id); if (owner[0] != null) owner[0].dismiss(); refreshStatus(); showSavedAgentTasksDialog(); })
                    .setNegativeButton("إلغاء", null).show());
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("مهام الوكيل الذكي المحفوظة")
                .setView(root).setPositiveButton("إغلاق", null).create();
        dialog.show(); owner[0] = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(560)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(48), Math.round(getResources().getDisplayMetrics().heightPixels * 0.78f)));
        }
    }

    private void confirmRunSavedAgent(String id) {
        org.json.JSONObject item = BotnaAgentStore.getTask(this, id); if (item == null) return;
        new AlertDialog.Builder(this).setTitle("إعادة تشغيل الوكيل")
                .setMessage("سيُعاد تحليل الشاشة الحالية والتحكم بواجهة الهاتف لتحقيق الهدف المحفوظ. قد يختلف المسار عن التشغيل السابق. راجع أي عملية حساسة يدويًا. متابعة؟")
                .setPositiveButton("بدء", (d, w) -> dispatchAgentTask(id, item.optString("goal", "")))
                .setNegativeButton("إلغاء", null).show();
    }

    private void showEditAgentTaskDialog(String id) {
        org.json.JSONObject item = BotnaAgentStore.getTask(this, id); if (item == null) return;
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(8), dp(5), dp(8), dp(5));
        EditText name = new EditText(this); name.setText(item.optString("name", "")); name.setSingleLine(true); name.setTextColor(TEXT); name.setHintTextColor(MUTED); name.setHint("اسم المهمة"); name.setBackground(roundDrawable(CARD, CYAN, 1, 10)); content.addView(name, matchWrap());
        EditText goal = new EditText(this); goal.setText(item.optString("goal", "")); goal.setSingleLine(false); goal.setMinLines(4); goal.setMaxLines(12); goal.setGravity(Gravity.TOP | Gravity.START); goal.setTextColor(TEXT); goal.setHintTextColor(MUTED); goal.setHint("وصف الهدف النهائي للوكيل"); goal.setBackground(roundDrawable(CARD, CYAN, 1, 10)); LinearLayout.LayoutParams goalLp = matchWrap(); goalLp.topMargin = dp(8); content.addView(goal, goalLp);
        ScrollView editScroll = new ScrollView(this); editScroll.setFillViewport(false); editScroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        LinearLayout editRoot = new LinearLayout(this); editRoot.setOrientation(LinearLayout.VERTICAL);
        editRoot.addView(editScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout editActions = new LinearLayout(this); editActions.setOrientation(LinearLayout.HORIZONTAL);
        Button saveEditButton = button("حفظ"); Button saveRunButton = button("حفظ وتشغيل"); Button cancelEditButton = button("إلغاء");
        editActions.addView(saveEditButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams saveRunLp = new LinearLayout.LayoutParams(0, dp(44), 1f); saveRunLp.leftMargin = dp(4); editActions.addView(saveRunButton, saveRunLp);
        LinearLayout.LayoutParams cancelEditLp = new LinearLayout.LayoutParams(0, dp(44), 1f); cancelEditLp.leftMargin = dp(4); editActions.addView(cancelEditButton, cancelEditLp);
        LinearLayout.LayoutParams editActionsLp = matchWrap(); editActionsLp.topMargin = dp(6); editRoot.addView(editActions, editActionsLp);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("تعديل المهمة المحفوظة").setView(editRoot).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(560)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(48), Math.round(getResources().getDisplayMetrics().heightPixels * 0.78f)));
        }
        View.OnFocusChangeListener focusEdit = (v, focused) -> { if (focused) editScroll.postDelayed(() -> editScroll.smoothScrollTo(0, Math.max(0, v.getBottom() - editScroll.getHeight() + dp(28))), 300); };
        name.setOnFocusChangeListener(focusEdit); goal.setOnFocusChangeListener(focusEdit);
        name.setOnClickListener(v -> focusEdit.onFocusChange(v, true)); goal.setOnClickListener(v -> focusEdit.onFocusChange(v, true));
        cancelEditButton.setOnClickListener(v -> dialog.dismiss());
        saveEditButton.setOnClickListener(v -> {
            String newGoal = goal.getText() == null ? "" : goal.getText().toString().trim();
            if (newGoal.isEmpty()) { goal.setError("اكتب الهدف المطلوب."); return; }
            BotnaAgentStore.updateGoal(this, id, name.getText() == null ? "" : name.getText().toString(), newGoal);
            dialog.dismiss(); refreshStatus(); showSavedAgentTasksDialog();
        });
        saveRunButton.setOnClickListener(v -> {
            String newGoal = goal.getText() == null ? "" : goal.getText().toString().trim();
            if (newGoal.isEmpty()) { goal.setError("اكتب الهدف المطلوب."); return; }
            BotnaAgentStore.updateGoal(this, id, name.getText() == null ? "" : name.getText().toString(), newGoal);
            dialog.dismiss(); confirmRunSavedAgent(id);
        });
    }

    private void dispatchAgentScript(String taskId, boolean runNow) {
        org.json.JSONObject item = BotnaAgentStore.getTask(this, taskId); if (item == null) return;
        String scriptId = item.optString("scriptId", "");
        org.json.JSONObject script = BotnaStore.getScript(this, scriptId);
        if (script == null || BotnaStore.steps(script).length() == 0) {
            Toast.makeText(this, "لا يحتوي السكربت على إجراءات محفوظة بعد. أعد تشغيل الوكيل أولًا.", Toast.LENGTH_LONG).show(); return;
        }
        if (!isServiceEnabled()) { Toast.makeText(this, "فعّل خدمة Botna قبل فتح أو تشغيل السكربت.", Toast.LENGTH_LONG).show(); openAccessibilitySettings(); return; }
        String action = runNow ? BotnaAccessibilityService.ACTION_RUN_AGENT_SCRIPT : BotnaAccessibilityService.ACTION_OPEN_AGENT_SCRIPT;
        Intent intent = new Intent(action).setPackage(getPackageName()).putExtra("scriptId", scriptId);
        sendBroadcast(intent);
        Toast.makeText(this, runNow ? "أُرسل السكربت المحفوظ للتشغيل." : "افتح محرر بوتنا لتعديل إجراءات الوكيل بالتفصيل.", Toast.LENGTH_SHORT).show();
    }

    private void showAgentMessagesDialog() {
        org.json.JSONArray all = BotnaAgentStore.messages(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(8), dp(5), dp(8), dp(5));
        content.addView(text("رسائل تفسيرية محفوظة من الوكيل: سبب اختيار الإجراء، نتيجة تنفيذه، وتحذيرات أو أسباب التوقف. يمكن حذف سجل الرسائل دون حذف المهام.", 12, MUTED, false), matchWrap());
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, dp(430)); scrollLp.topMargin = dp(8); content.addView(scroll, scrollLp);
        if (all.length() == 0) list.addView(text("لا توجد رسائل بعد.", 13, MUTED, false), matchWrap());
        int shown = 0;
        for (int i = all.length() - 1; i >= 0 && shown < 100; i--) {
            org.json.JSONObject msg = all.optJSONObject(i); if (msg == null) continue;
            String title = msg.optString("timeText", "") + "\n" + msg.optString("title", "رسالة")
                    + (msg.optBoolean("success", true) ? "" : "  ⚠");
            Button row = button(title); row.setTextSize(12); row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = matchWrap(); rowLp.topMargin = dp(4); list.addView(row, rowLp);
            row.setOnClickListener(v -> {
                new AlertDialog.Builder(this).setTitle(msg.optString("title", "تفاصيل رسالة الوكيل"))
                        .setMessage("الوقت: " + msg.optString("timeText", "-") + "\nالمهمة: " + agentTaskName(msg.optString("taskId", ""))
                                + "\nالإجراء: " + (msg.optInt("actionIndex", -1) >= 0 ? msg.optInt("actionIndex") : "غير محدد")
                                + "\nالحالة: " + (msg.optBoolean("success", true) ? "معلومة/نجاح" : "تحذير/فشل")
                                + "\n\nالتفاصيل:\n" + msg.optString("detail", "لا توجد تفاصيل إضافية."))
                        .setPositiveButton("إغلاق", null).show();
            });
            shown++;
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("رسائل الوكيل")
                .setView(content).setPositiveButton("إغلاق", null).setNegativeButton("حذف الرسائل", null).create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("حذف رسائل الوكيل")
                .setMessage("سيتم حذف سجل الرسائل التفسيري فقط. تبقى مهام الوكيل وسكربتاته وسجلات التشخيص.")
                .setPositiveButton("حذف", (d, w) -> { BotnaAgentStore.clearMessages(this); dialog.dismiss(); refreshStatus(); showAgentMessagesDialog(); })
                .setNegativeButton("إلغاء", null).show());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(560)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(48), Math.round(getResources().getDisplayMetrics().heightPixels * 0.78f)));
        }
    }

    private String agentTaskName(String taskId) {
        org.json.JSONObject task = BotnaAgentStore.getTask(this, taskId);
        return task == null ? "مهمة محذوفة/غير معروفة" : task.optString("name", "مهمة ذكية");
    }

    private void showLogsDialog() {
        org.json.JSONArray logs = BotnaLogStore.list(this);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(8), dp(4), dp(8), dp(4));
        TextView summary = text("عدد السجلات المحفوظة: " + logs.length() + " (الحد الأقصى 100). تُحفظ التفاصيل ولقطات التشخيص داخل مساحة التطبيق الخاصة.", 12, MUTED, false);
        content.addView(summary, matchWrap());
        Button captureToggle = button(BotnaLogStore.screenshotsEnabled(this) ? "لقطات الشاشة مع السجلات: مفعّلة" : "لقطات الشاشة مع السجلات: متوقفة");
        LinearLayout.LayoutParams toggleLp = matchWrap(); toggleLp.topMargin = dp(6); content.addView(captureToggle, toggleLp);
        final AlertDialog[] logsDialogRef = new AlertDialog[1];
        captureToggle.setOnClickListener(v -> {
            BotnaLogStore.setScreenshotsEnabled(this, !BotnaLogStore.screenshotsEnabled(this));
            if (logsDialogRef[0] != null) logsDialogRef[0].dismiss();
            showLogsDialog();
        });
        ScrollView listScroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); listScroll.addView(list);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, dp(390)); listLp.topMargin = dp(8); content.addView(listScroll, listLp);
        if (logs.length() == 0) {
            TextView empty = text("لا توجد سجلات حتى الآن. عند فشل ترجمة أو خطوة ستظهر هنا تفاصيل السبب ولقطة الشاشة إذا كانت مفعّلة.", 13, MUTED, false); list.addView(empty, matchWrap());
        }
        for (int i = 0; i < logs.length(); i++) {
            org.json.JSONObject item = logs.optJSONObject(i); if (item == null) continue;
            String title = item.optString("timeText", "وقت غير معروف") + "\n" + item.optString("message", "فشل غير محدد");
            Button row = button(title); row.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); row.setTextSize(12);
            LinearLayout.LayoutParams rowLp = matchWrap(); rowLp.topMargin = dp(5); list.addView(row, rowLp);
            row.setOnClickListener(v -> showLogDetails(item));
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("سجلات بوتنا التشخيصية")
                .setView(content).setPositiveButton("إغلاق", null).setNegativeButton("حذف جميع السجلات", null).create();
        dialog.show();
        logsDialogRef[0] = dialog;
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("حذف السجلات")
                .setMessage("سيُحذف سجل الأخطاء وكل لقطات التشخيص المرتبطة به من مساحة التطبيق. لا يؤثر ذلك على السكربتات أو مفاتيح API.")
                .setPositiveButton("حذف الكل", (d, w) -> { BotnaLogStore.clear(this); dialog.dismiss(); refreshStatus(); showLogsDialog(); })
                .setNegativeButton("إلغاء", null).show());
    }

    private void showLogDetails(org.json.JSONObject entry) {
        if (entry == null) return;
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(8), dp(6), dp(8), dp(6));
        String details = "الوقت: " + entry.optString("timeText", "-")
                + "\nالمصدر: " + entry.optString("source", "-")
                + "\nالخطوة: " + (entry.optInt("stepIndex", -1) >= 0 ? (entry.optInt("stepIndex") + 1) : "غير محددة")
                + "\nاسم الخطوة: " + entry.optString("stepLabel", "-")
                + "\nالنتيجة: " + entry.optString("message", "-")
                + "\n\nالسبب والتفاصيل:\n" + entry.optString("detail", "لا توجد تفاصيل إضافية.");
        TextView text = text(details, 13, TEXT, false); content.addView(text, matchWrap());
        Bitmap screenshot = BotnaLogStore.loadScreenshot(this, entry);
        if (screenshot != null) {
            ImageView image = new ImageView(this); image.setImageBitmap(screenshot); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams imageLp = matchWrap(); imageLp.topMargin = dp(10); imageLp.height = dp(260); content.addView(image, imageLp);
        } else {
            TextView noImage = text("لا توجد لقطة شاشة محفوظة لهذا السجل. قد تكون اللقطات متوقفة أو لم يسمح أندرويد بالتقاطها.", 11, MUTED, false);
            LinearLayout.LayoutParams noImageLp = matchWrap(); noImageLp.topMargin = dp(8); content.addView(noImage, noImageLp);
        }
        AlertDialog detailDialog = new AlertDialog.Builder(this).setTitle("تفاصيل سجل الفشل").setView(content)
                .setPositiveButton("إغلاق", null).setNegativeButton("حذف هذا السجل", null).create();
        detailDialog.setOnDismissListener(d -> { if (screenshot != null && !screenshot.isRecycled()) screenshot.recycle(); });
        detailDialog.show();
        detailDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("حذف هذا السجل؟").setMessage("سيتم حذف تفاصيله ولقطة الشاشة المرتبطة به.")
                .setPositiveButton("حذف", (d, w) -> { BotnaLogStore.delete(this, entry.optString("id", "")); detailDialog.dismiss(); refreshStatus(); showLogsDialog(); })
                .setNegativeButton("إلغاء", null).show());
    }

    private void openAccessibilitySettings() {
        try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        catch (Exception ex) { Toast.makeText(this, "تعذر فتح إعدادات إمكانية الوصول", Toast.LENGTH_LONG).show(); }
    }

    private void showCommandGuide() {
        String guide = "أمثلة للأوامر المدعومة:\n\n" +
                "• تابع صورة رقم 2 بالمكتبة دون ضغط\n" +
                "• اضغط على صورة رقم 1 ثم انتظر ثانيتين\n" +
                "• إذا وجدت الصورة رقم 3 انتظر 1000 مللي ثانية ثم ارجع\n" +
                "• انقر نص: تطوير\n" +
                "• اكتب: مرحبًا\n" +
                "• انقر موقع 50,70\n" +
                "• مرر للأسفل\n" +
                "• كرر 3 مرات: تابع صورة رقم 1 بالمكتبة دون ضغط\n" +
                "• افتح تطبيق واتساب ثم انتظر ثانيتين\n" +
                "• إذا فشل فتح التطبيق، ارجع وإلا تابع صورة رقم 2\n" +
                "\nأفضل نتيجة للأوامر المركبة تأتي مع مزوّد API مضبوط. راجع دائمًا معاينة الترجمة قبل التنفيذ.";
        new AlertDialog.Builder(this).setTitle("دليل الأوامر المخصصة").setMessage(guide).setPositiveButton("حسنًا", null).show();
    }

    @Override
    public void onBackPressed() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("حفظ السكربتات والخروج")
                .setMessage("تُحفظ خطوات السكربتات تلقائيًا أثناء التعديل. هل تريد حفظها الآن والخروج من Botna؟")
                .setPositiveButton("حفظ وخروج", (d, w) -> {
                    BotnaStore.saveScripts(this, BotnaStore.scripts(this));
                    finish();
                })
                .setNegativeButton("إلغاء", (d, w) -> { })
                .create();
        dialog.show();
    }

    private void refreshStatus() {
        if (serviceStatus == null) {
            return;
        }

        boolean enabled = isServiceEnabled();

        serviceStatus.setText(
                enabled
                        ? "● الخدمة مفعّلة — يفترض ظهور الزر B فوق التطبيقات."
                        : "○ الخدمة غير مفعّلة — افتح الإعدادات وفعّل Botna يدويًا."
        );

        serviceStatus.setTextColor(
                enabled
                        ? CYAN
                        : Color.rgb(255, 190, 100)
        );

        if (dataSummary != null) {
            dataSummary.setText("السكربتات المحفوظة: " + BotnaStore.scripts(this).length()
                    + "\nقوالب مكتبة الصور: " + BotnaStore.imageLibrary(this).length()
                    + "\nمهام الوكيل المحفوظة: " + BotnaAgentStore.tasks(this).length()
                    + "\nرسائل الوكيل: " + BotnaAgentStore.messages(this).length()
                    + "\nالمزوّد المحدد: " + BotnaAICompiler.providerLabel(BotnaAICompiler.providerId(this))
                    + "\nالمفاتيح المحفوظة لهذا المزوّد: " + BotnaKeyVault.count(this, BotnaAICompiler.providerId(this))
                    + "\nالسجلات التشخيصية: " + BotnaLogStore.list(this).length()
                    + "\nحالة الذكاء الاصطناعي: " + (BotnaAICompiler.isConfigured(this) ? "إعداد API مكتمل" : "يتطلب إعداد مفتاح/نقطة اتصال API"));
        }

        if (lastResult != null) {
            lastResult.setText(
                    getSharedPreferences(PREFS, MODE_PRIVATE)
                            .getString(
                                    KEY_LAST_RESULT,
                                    "لم يتم تنفيذ أي إجراء بعد."
                            )
            );
        }
    }

    private boolean isServiceEnabled() {
        String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );

        if (TextUtils.isEmpty(enabledServices)) {
            return false;
        }

        ComponentName expected = new ComponentName(
                this,
                BotnaAccessibilityService.class
        );

        for (String item : enabledServices.split(":")) {
            ComponentName parsed =
                    ComponentName.unflattenFromString(item);

            if (expected.equals(parsed)) {
                return true;
            }
        }

        return false;
    }

    private void addBullet(
            LinearLayout parent,
            String value
    ) {
        TextView bullet = text(
                "•  " + value,
                14,
                MUTED,
                false
        );

        LinearLayout.LayoutParams lp = matchWrap();
        lp.topMargin = dp(8);
        parent.addView(bullet, lp);
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                dp(16), dp(16), dp(16), dp(16)
        );

        layout.setBackground(
                roundDrawable(
                        CARD,
                        Color.argb(150, 35, 226, 244),
                        1,
                        20
                )
        );

        layout.setElevation(dp(5));
        return layout;
    }

    private TextView text(
            String value,
            int sp,
            int color,
            boolean bold
    ) {
        TextView view = new TextView(this);

        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        view.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);

        view.setText(value);
        view.setTextSize(14);
        view.setAllCaps(false);
        view.setTextColor(Color.rgb(239, 248, 255));

        view.setBackground(
                roundDrawable(
                        Color.argb(105, 35, 226, 244),
                        Color.argb(220, 35, 226, 244),
                        1,
                        14
                )
        );

        view.setElevation(dp(3));
        view.setPadding(
                dp(10), dp(8), dp(10), dp(8)
        );

        return view;
    }

    private GradientDrawable roundDrawable(
            int fill,
            int stroke,
            int strokeWidth,
            int radius
    ) {
        GradientDrawable shape = new GradientDrawable();

        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));

        if (strokeWidth > 0) {
            shape.setStroke(dp(strokeWidth), stroke);
        }

        return shape;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(float value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density
        );
    }

    private void hideKeyboard() {
        try {
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(
                            Context.INPUT_METHOD_SERVICE
                    );

            if (imm != null) {
                imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
            }


        } catch (Exception ignored) {
        }
    }
}
