package com.botna.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import androidx.core.content.FileProvider;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BotStudioActivity extends Activity {
    private static final int BG = Color.rgb(5, 7, 12);
    private static final int CARD = Color.rgb(12, 20, 32);
    private static final int FIELD = Color.rgb(8, 14, 23);
    private static final int TEXT = Color.rgb(235, 246, 255);
    private static final int MUTED = Color.rgb(150, 174, 195);
    private static final int CYAN = Color.rgb(35, 226, 244);
    private final ExecutorService chatExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService skillImportExecutor = Executors.newSingleThreadExecutor();
    private BotStudioStore store;
    private LinearLayout page;
    private LinearLayout content;
    private String section = "bots";
    private String selectedBotId = "";
    private String attachedFilePath = "";
    private String attachedFileName = "";
    private String attachedFileMime = "";
    private boolean chatBusy;
    private EditText chatInput;
    private TextView chatStatus;
    private Button sendChatButton;
    private int chatHistoryLimit = 120;
    private boolean showOlderAfterReload;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        store = BotStudioStore.get(this);
        ensureSelectedBot();
        render();
    }

    @Override protected void onDestroy() {
        chatExecutor.shutdownNow();
        skillImportExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == 4112) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty() && chatInput != null) {
                String current = chatInput.getText() == null ? "" : chatInput.getText().toString().trim();
                chatInput.setText((current.isEmpty() ? "" : current + " ") + results.get(0));
                chatInput.setSelection(chatInput.getText().length());
                chatInput.requestFocus();
            } else Toast.makeText(this, "لم أتمكن من تحويل الصوت إلى نص.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (requestCode == 4108 && data.getData() != null) {
            String saved = BotStudioStore.persistAttachment(this, data.getData());
            if (saved.isEmpty()) Toast.makeText(this, "تعذر حفظ الملف أو تجاوز الحد المسموح 80 ميجابايت.", Toast.LENGTH_LONG).show();
            else {
                attachedFilePath = saved;
                attachedFileName = BotStudioStore.attachmentName(saved);
                attachedFileMime = BotStudioStore.attachmentMime(saved);
                if (chatStatus != null) chatStatus.setText("المرفق جاهز: " + attachedFileName + " • " + attachedFileMime);
                if (chatInput != null) chatInput.requestFocus();
            }
            return;
        }
        if (requestCode == 4111 && data.getData() != null) {
            String saved = BotStudioStore.persistAttachment(this, data.getData());
            if (saved.isEmpty()) { Toast.makeText(this, "تعذر استيراد الملف.", Toast.LENGTH_LONG).show(); return; }
            importSkillFile(saved);
        }
    }

    private void ensureSelectedBot() {
        JSONArray bots = store.bots();
        selectedBotId = BotStudioStore.selectedBotId(this);
        boolean found = false;
        for (int i = 0; i < bots.length(); i++) {
            JSONObject item = bots.optJSONObject(i);
            if (item != null && selectedBotId.equals(item.optString("id"))) { found = true; break; }
        }
        if (!found && bots.length() > 0) {
            JSONObject first = bots.optJSONObject(0);
            selectedBotId = first == null ? "" : first.optString("id", "");
            BotStudioStore.selectBot(this, selectedBotId);
        }
    }

    private void render() {
        ScrollView outer = new ScrollView(this); outer.setFillViewport(true); outer.setBackgroundColor(BG);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(16), dp(14), dp(16), dp(24));
        BotnaI18n.applyDirection(this, page);
        outer.addView(page, new ScrollView.LayoutParams(-1, -2));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); BotnaI18n.applyDirection(this, header);
        Button back = button("‹"); header.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        back.setOnClickListener(v -> finish());
        LinearLayout heading = new LinearLayout(this); heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(0, -2, 1f); headingLp.leftMargin = dp(8); headingLp.rightMargin = dp(8); header.addView(heading, headingLp);
        heading.addView(text("استوديو البوتات الذكية", 22, TEXT, true), matchWrap());
        heading.addView(text("مصنع الوكلاء والشخصيات والمهارات", 12, MUTED, false), matchWrap());
        page.addView(header, matchWrap());

        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL); tabs.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tabsLp = matchWrap(); tabsLp.topMargin = dp(12); page.addView(tabs, tabsLp);
        addTab(tabs, "البوتات", "bots"); addTab(tabs, "الرؤية", "vision"); addTab(tabs, "المهارات", "skills"); addTab(tabs, "الذاكرة", "memory");
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentLp = matchWrap(); contentLp.topMargin = dp(10); page.addView(content, contentLp);
        switch (section) {
            case "vision": renderVision(); break;
            case "skills": renderSkills(); break;
            case "memory": renderMemory(); break;
            case "chat": renderChat(); break;
            default: renderBots(); break;
        }
        setContentView(outer);
    }

    private void addTab(LinearLayout row, String label, String key) {
        Button b = button(label); b.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f); lp.leftMargin = dp(2); lp.rightMargin = dp(2); row.addView(b, lp);
        if (section.equals(key) || ("chat".equals(section) && "bots".equals(key))) b.setBackground(glassButtonShape(true));
        b.setOnClickListener(v -> { section = key; render(); });
    }

    private void renderBots() {
        LinearLayout hero = card(); content.addView(hero, matchWrap());
        hero.addView(text("شخصيات الوكلاء", 18, TEXT, true), matchWrap());
        hero.addView(text("لكل بوت اسم ودور وتعليمات شخصية ومهارات مستقلة. اختر بوتًا لتعديل هويته أو بدء محادثة معه.", 12, MUTED, false), top(matchWrap(), 6));
        Button create = button("＋ إنشاء بوت جديد"); hero.addView(create, top(matchWrap(), 10)); create.setOnClickListener(v -> showBotEditor(null));
        JSONArray bots = store.bots();
        for (int i = 0; i < bots.length(); i++) {
            JSONObject bot = bots.optJSONObject(i); if (bot == null) continue;
            String id = bot.optString("id");
            LinearLayout row = card(); content.addView(row, top(matchWrap(), 8));
            Button select = button(bot.optString("icon", "🤖") + "  " + bot.optString("name", "بوت") + (id.equals(selectedBotId) ? "  ✓" : ""));
            row.addView(select, matchWrap());
            select.setOnClickListener(v -> { selectedBotId = id; BotStudioStore.selectBot(this, id); render(); });
            TextView detail = text(bot.optString("role", "مساعد عام") + "\n" + bot.optString("persona", ""), 12, MUTED, false);
            row.addView(detail, top(matchWrap(), 5));
            LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); row.addView(actions, top(matchWrap(), 6));
            Button edit = button("تعديل الشخصية"); Button talk = button("محادثة البوت");
            actions.addView(edit, new LinearLayout.LayoutParams(0, dp(42), 1)); LinearLayout.LayoutParams talkLp = new LinearLayout.LayoutParams(0, dp(42), 1); talkLp.leftMargin = dp(6); actions.addView(talk, talkLp);
            edit.setOnClickListener(v -> showBotEditor(bot)); talk.setOnClickListener(v -> { selectedBotId = id; BotStudioStore.selectBot(this, id); section = "chat"; render(); });
        }
        LinearLayout run = card(); content.addView(run, top(matchWrap(), 10));
        run.addView(text("تشغيل مهمة باستخدام الشخصية المختارة", 16, TEXT, true), matchWrap());
        run.addView(text("يستخدم الوكيل الحالي ولقطات الشاشة المتاحة، وتضاف تعليمات الشخصية والمهارات المفعّلة إلى سياقه.", 12, MUTED, false), top(matchWrap(), 5));
        Button task = button("بدء مهمة ذكية"); run.addView(task, top(matchWrap(), 8)); task.setOnClickListener(v -> showTaskDialog());
        Button chat = button("فتح المحادثة مع البوت المختار"); run.addView(chat, top(matchWrap(), 7)); chat.setOnClickListener(v -> { section = "chat"; render(); });
    }

    private void renderVision() {
        LinearLayout panel = card(); content.addView(panel, matchWrap());
        panel.addView(text("محرك الرؤية واللقطات المتتابعة", 18, TEXT, true), matchWrap());
        panel.addView(text("تستخدم المهام لقطات الشاشة اللازمة للتحقق من موضع التنفيذ. أما المراقبة المتتابعة في الخلفية فتظل اختيارية مع فواصل زمنية لتقليل استهلاك البطارية.", 12, MUTED, false), top(matchWrap(), 6));
        addSwitch(panel, "مراقبة تغيرات الشاشة بالخلفية", "تلتقط الخدمة عينة بعد أحداث الواجهة المهمة عندما لا تكون المهمة الذكية قيد التنفيذ.", BotStudioStore.visionStreamEnabled(this), BotStudioStore::setVisionStreamEnabled);
        addSwitch(panel, "الرؤية المباشرة", "تلتقط الشاشة كل نحو ثانيتين أثناء عمل الوكيل، وتحفظ كل لقطة تنجح في الالتقاط للمهمة. إذا تغيرت الشاشة أثناء التخطيط، يُلغى الإجراء القديم وتُعاد القراءة قبل النقر.", BotStudioStore.directLiveVisionEnabled(this), BotStudioStore::setDirectLiveVisionEnabled);
        addSwitch(panel, "اللقطات الذكية عند تغير المحتوى", "تسجل إشارة فقط عندما يختلف المحتوى المرئي بوضوح عن العينة السابقة.", BotStudioStore.smartSnapshotsEnabled(this), BotStudioStore::setSmartSnapshotsEnabled);
        addSwitch(panel, "حفظ لقطة تحقق بعد كل إجراء", "يحتفظ بسجل لقطة بعد الإجراء عندما تكون لقطات التشخيص مفعّلة؛ ولا يعطّل اللقطات الضرورية لفهم الشاشة أثناء المهمة.", BotStudioStore.captureAfterActionEnabled(this), BotStudioStore::setCaptureAfterActionEnabled);
        addSwitch(panel, "حفظ لقطة عند فشل الخطوة", "يحتفظ بلقطة تشخيص عند الإخفاق لتوضيح الموضع الذي توقفت عنده المهمة.", BotStudioStore.captureOnFailureEnabled(this), BotStudioStore::setCaptureOnFailureEnabled);
        TextView status = text(BotnaVisionEngine.status(this), 13, CYAN, false); content.addView(status, top(matchWrap(), 8));
        Button test = button("اختبار جاهزية الرؤية"); content.addView(test, top(matchWrap(), 8));
        test.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("حالة محرك الرؤية").setMessage(BotnaVisionEngine.status(this)
                + "\n\nالمسارات المتاحة: شجرة عناصر إمكانية الوصول، مطابقة القوالب عبر OpenCV، والتحليل البصري عبر المزوّد إذا كان النموذج يدعم الصور. تستخدم هذه النسخة مسار الرؤية الخفيف دون تنزيل نماذج محلية إضافية.")
                .setPositiveButton("حسنًا", null).show());
        Button archive = button("أرشيف لقطات المهام المحفوظة"); content.addView(archive, top(matchWrap(), 8));
        archive.setOnClickListener(v -> showTaskEvidenceArchive());
        LinearLayout skillsActions = new LinearLayout(this); skillsActions.setOrientation(LinearLayout.HORIZONTAL); skillsActions.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams skillActionsLp = top(matchWrap(), 9); panel.addView(skillsActions, skillActionsLp);
        Button importSkill = button("⇧"); importSkill.setTextSize(23); importSkill.setContentDescription("استيراد مهارة من ملف"); importSkill.setBackground(glassButtonShape(false));
        Button createSkill = button("＋"); createSkill.setTextSize(23); createSkill.setContentDescription("إنشاء مهارة جديدة"); createSkill.setBackground(glassButtonShape(false));
        skillsActions.addView(importSkill, new LinearLayout.LayoutParams(0, dp(48), 1)); LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(0, dp(48), 1); createLp.leftMargin = dp(8); skillsActions.addView(createSkill, createLp);
        importSkill.setOnClickListener(v -> selectSkillFile()); createSkill.setOnClickListener(v -> showSkillEditor());
        LinearLayout safety = card(); content.addView(safety, top(matchWrap(), 10));
        safety.addView(text("الأمان والتحقق الحرج", 17, TEXT, true), matchWrap());
        addSwitch(safety, "طلب موافقة على العمليات الحساسة", "يوقف الوكيل قبل إجراءات الدفع والحذف والإرسال والنشر التي يمكن التعرف عليها من وصف الإجراء.", BotStudioStore.safetySandboxEnabled(this), BotStudioStore::setSafetySandboxEnabled);
        safety.addView(text("نقاط الاستئناف تحفظ هدف المهمة وسجل الأفعال بعد كل خطوة مكتملة. يمكن استئناف التخطيط من الشاشة الحالية؛ لا يمكن استعادة حالة التطبيق الخارجي إذا تغيّرت خارج Botna.", 12, MUTED, false), top(matchWrap(), 8));
    }

    private void showTaskEvidenceArchive() {
        JSONArray tasks = BotnaTaskEvidenceStore.listTasks(this);
        if (tasks.length() == 0) { Toast.makeText(this, "لا توجد لقطات محفوظة للمهام حتى الآن.", Toast.LENGTH_LONG).show(); return; }
        String[] labels = new String[tasks.length()];
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            String goal = task == null ? "مهمة" : task.optString("goal", "مهمة");
            if (goal.length() > 62) goal = goal.substring(0, 62) + "…";
            labels[i] = goal + " • " + (task == null ? 0 : task.optInt("frameCount", 0)) + " لقطة";
        }
        new AlertDialog.Builder(this).setTitle("أرشيف لقطات المهام")
                .setItems(labels, (dialog, which) -> { JSONObject task = tasks.optJSONObject(which); if (task != null) showTaskEvidenceFrames(task.optString("taskId", ""), task.optString("goal", "")); })
                .setNegativeButton("إغلاق", null).show();
    }

    private void showTaskEvidenceFrames(String taskId, String goal) {
        JSONArray frames = BotnaTaskEvidenceStore.listFrames(this, taskId, 150);
        if (frames.length() == 0) { Toast.makeText(this, "لم أعثر على صور لهذا السجل.", Toast.LENGTH_LONG).show(); return; }
        String[] labels = new String[frames.length()];
        for (int i = 0; i < frames.length(); i++) {
            JSONObject frame = frames.optJSONObject(i);
            long time = frame == null ? 0L : frame.optLong("time", 0L);
            String stamp = new SimpleDateFormat("MM/dd hh:mm:ss a", Locale.getDefault()).format(new Date(time));
            labels[i] = stamp + " • خطوة " + (frame == null ? -1 : frame.optInt("actionIndex", -1)) + " • " + (frame == null ? "لقطة" : frame.optString("label", "لقطة"));
        }
        new AlertDialog.Builder(this).setTitle("لقطات المهمة: " + (goal.length() > 24 ? goal.substring(0, 24) + "…" : goal))
                .setItems(labels, (dialog, which) -> {
                    JSONObject frame = frames.optJSONObject(which); if (frame == null) return;
                    Bitmap bitmap = BotnaTaskEvidenceStore.loadFrame(this, taskId, frame.optString("file", ""));
                    if (bitmap == null) { Toast.makeText(this, "تعذر فتح صورة اللقطة.", Toast.LENGTH_LONG).show(); return; }
                    ScrollView scroll = new ScrollView(this);
                    ImageView image = new ImageView(this); image.setImageBitmap(bitmap); image.setAdjustViewBounds(true); image.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    scroll.addView(image, new ScrollView.LayoutParams(-1, -2));
                    new AlertDialog.Builder(this).setTitle(labels[which]).setView(scroll)
                            .setPositiveButton("إغلاق", (d, w) -> { if (!bitmap.isRecycled()) bitmap.recycle(); })
                            .setOnCancelListener(d -> { if (!bitmap.isRecycled()) bitmap.recycle(); }).show();
                }).setNegativeButton("إغلاق", null).show();
    }

    private interface BoolSetter { void set(Context context, boolean value); }
    private void addSwitch(LinearLayout parent, String title, String description, boolean checked, BoolSetter setter) {
        LinearLayout wrapper = new LinearLayout(this); wrapper.setOrientation(LinearLayout.VERTICAL); wrapper.setPadding(dp(4), dp(9), dp(4), dp(9));
        LinearLayout.LayoutParams wp = matchWrap(); wp.topMargin = dp(3); parent.addView(wrapper, wp);
        Switch sw = new Switch(this); sw.setText(title); sw.setTextColor(TEXT); sw.setTextSize(14); sw.setChecked(checked); wrapper.addView(sw, matchWrap());
        wrapper.addView(text(description, 11, MUTED, false), top(matchWrap(), 3));
        sw.setOnCheckedChangeListener((button, value) -> setter.set(this, value));
    }

    private void renderSkills() {
        JSONObject bot = store.bot(selectedBotId);
        if (bot == null) { ensureSelectedBot(); bot = store.bot(selectedBotId); }
        LinearLayout panel = card(); content.addView(panel, matchWrap());
        panel.addView(text("المهارات المركبة", 18, TEXT, true), matchWrap());
        panel.addView(text("المهارات المفعّلة تحقن إرشاداتها في سياق التخطيط للشخصية المختارة: " + (bot == null ? "بوت" : bot.optString("name")), 12, MUTED, false), top(matchWrap(), 5));
        for (BotStudioStore.Skill skill : store.allSkills()) {
            addSwitch(panel, skill.title, skill.description, store.isSkillEnabled(selectedBotId, skill.id), (c, enabled) -> store.setSkillEnabled(selectedBotId, skill.id, enabled));
        }
        LinearLayout skillTools = new LinearLayout(this); skillTools.setOrientation(LinearLayout.HORIZONTAL); panel.addView(skillTools, top(matchWrap(), 8));
        Button importSkill = button("استيراد مهارة"); Button createSkill = button("إنشاء مهارة");
        skillTools.addView(importSkill, new LinearLayout.LayoutParams(0, dp(44), 1)); LinearLayout.LayoutParams createSkillLp = new LinearLayout.LayoutParams(0, dp(44), 1); createSkillLp.leftMargin = dp(6); skillTools.addView(createSkill, createSkillLp);
        importSkill.setOnClickListener(v -> selectSkillFile()); createSkill.setOnClickListener(v -> showSkillEditor());
        Button export = button("تصدير محادثات البوت المحدد بصيغة JSON"); panel.addView(export, top(matchWrap(), 8));
        export.setOnClickListener(v -> exportChat());
    }

    private void renderMemory() {
        LinearLayout topPanel = card(); content.addView(topPanel, matchWrap());
        topPanel.addView(text("ذاكرة الأخطاء ونقاط الاستئناف", 18, TEXT, true), matchWrap());
        topPanel.addView(text("تحفظ قاعدة محلية اسم الحزمة ونوع الفعل والاستراتيجية السابقة ومؤشرات النجاح والفشل. هذه ذاكرة تجارب وليست إعادة تدريب لأوزان النموذج.", 12, MUTED, false), top(matchWrap(), 5));
        JSONArray checkpoints = store.checkpoints(true);
        if (checkpoints.length() == 0) topPanel.addView(text("لا توجد مهام قابلة للاستئناف حاليًا.", 13, MUTED, false), top(matchWrap(), 8));
        for (int i = 0; i < checkpoints.length(); i++) {
            JSONObject cp = checkpoints.optJSONObject(i); if (cp == null) continue;
            LinearLayout row = card(); content.addView(row, top(matchWrap(), 8));
            row.addView(text(cp.optString("goal", "مهمة") + "\nالحالة: " + cp.optString("state") + " • الإجراءات المكتملة: " + cp.optInt("actionCount"), 13, TEXT, true), matchWrap());
            Button resume = button("استئناف من الحالة الحالية"); row.addView(resume, top(matchWrap(), 6));
            resume.setOnClickListener(v -> resumeCheckpoint(cp));
        }
        JSONArray errors = store.errorRecords();
        LinearLayout errorPanel = card(); content.addView(errorPanel, top(matchWrap(), 10));
        errorPanel.addView(text("ذاكرة الأخطاء (" + errors.length() + ")", 17, TEXT, true), matchWrap());
        for (int i = 0; i < errors.length(); i++) {
            JSONObject e = errors.optJSONObject(i); if (e == null) continue;
            errorPanel.addView(text(e.optString("package") + " • " + e.optString("type") + "\n" + e.optString("detail") + "\nالمعالجة المقترحة: " + e.optString("strategy")
                    + "\nفشل: " + e.optInt("failures") + " • نجاح موثق: " + e.optInt("successes"), 12, MUTED, false), top(matchWrap(), 9));
            errorPanel.addView(divider(), top(matchWrap(), 5));
        }
        Button clear = button("مسح ذاكرة الأخطاء"); errorPanel.addView(clear, top(matchWrap(), 8));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("مسح ذاكرة الأخطاء؟").setMessage("سيتم حذف سجلات التعلم التراكمي المحلية فقط.")
                .setNegativeButton("إلغاء", null).setPositiveButton("مسح", (d, w) -> { store.clearErrorMemory(); render(); }).show());
    }

    private void renderChat() {
        JSONObject bot = store.bot(selectedBotId);
        if (bot == null) { section = "bots"; render(); return; }
        LinearLayout panel = card(); content.addView(panel, matchWrap());
        panel.addView(text(bot.optString("icon", "🤖") + "  محادثة " + bot.optString("name", "البوت"), 18, TEXT, true), matchWrap());
        panel.addView(text("الدور: " + bot.optString("role") + "\n" + bot.optString("persona"), 12, MUTED, false), top(matchWrap(), 5));
        panel.addView(text("يمكنك محادثة البوت أو تكليفه بتنفيذ مهمة على الهاتف بصيغة أمر؛ عند بدء المهمة يغلق Botna واجهته ويواصل الوكيل من خدمة إمكانية الوصول.", 11, MUTED, false), top(matchWrap(), 5));
        LinearLayout settingsRow = new LinearLayout(this); settingsRow.setOrientation(LinearLayout.HORIZONTAL); settingsRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); panel.addView(settingsRow, top(matchWrap(), 7));
        Button profile = button("تعديل الشخصية"); Button aiSettings = button("إعدادات المزوّد");
        settingsRow.addView(profile, new LinearLayout.LayoutParams(0, dp(40), 1)); LinearLayout.LayoutParams aiLp = new LinearLayout.LayoutParams(0, dp(40), 1); aiLp.leftMargin = dp(6); settingsRow.addView(aiSettings, aiLp);
        profile.setOnClickListener(v -> showBotEditor(bot));
        aiSettings.setOnClickListener(v -> BotnaAICompiler.showSettingsDialog(this, false, this::render));
        Button thinking = button("🧠 مستوى التفكير: " + thinkingLevelLabel(BotStudioStore.thinkingLevel(this)));
        panel.addView(thinking, top(matchWrap(), 6)); thinking.setOnClickListener(v -> showThinkingDialog());
        panel.addView(text(BotnaAICompiler.thinkingSupportDescription(this), 10, MUTED, false), top(matchWrap(), 3));
        int screenDpHeight = Math.max(480, (int) (getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density));
        int chatViewportDp = Math.max(280, Math.min(430, (int) (screenDpHeight * 0.47f)));
        ScrollView chatScroll = new CappedScrollView(this, dp(chatViewportDp)); chatScroll.setFillViewport(false); chatScroll.setSmoothScrollingEnabled(true); chatScroll.setNestedScrollingEnabled(false); chatScroll.setVerticalScrollBarEnabled(true); chatScroll.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(0, dp(3), 0, dp(5)); chatScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams chatScrollLp = matchWrap(); chatScrollLp.topMargin = dp(8); panel.addView(chatScroll, chatScrollLp);
        chatScroll.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_MOVE && chatScroll.getParent() != null) chatScroll.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) && chatScroll.getParent() != null) chatScroll.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
        int totalMessages = store.messageCount(selectedBotId);
        int loadedLimit = Math.min(Math.max(120, chatHistoryLimit), 10000);
        JSONArray history = store.messages(selectedBotId, loadedLimit);
        if (totalMessages > history.length()) {
            Button older = button("↑ تحميل الرسائل الأقدم (" + (totalMessages - history.length()) + ")");
            messages.addView(older, top(matchWrap(), 5));
            older.setOnClickListener(v -> { chatHistoryLimit = Math.min(10000, chatHistoryLimit + 120); showOlderAfterReload = true; render(); });
        }
        int renderedPreviews = 0;
        long priorDisplayedTimestamp = -1L;
        for (int i = 0; i < history.length(); i++) {
            JSONObject msg = history.optJSONObject(i); if (msg == null) continue;
            boolean user = "user".equals(msg.optString("role"));
            String body = normalizeMessageBody(msg.optString("body", ""));
            String attachment = msg.optString("image", "");
            boolean hasAttachment = !attachment.isEmpty() && new File(attachment).isFile();
            if (body.isEmpty() && !hasAttachment) continue;
            long timestamp = msg.optLong("time", 0L);
            LinearLayout messageCard = new LinearLayout(this); messageCard.setOrientation(LinearLayout.VERTICAL); messageCard.setGravity(Gravity.TOP); messageCard.setMinimumHeight(0); messageCard.setPadding(dp(10), dp(8), dp(10), dp(8));
            messageCard.setBackground(shape(user ? Color.rgb(14, 54, 79) : FIELD, Color.rgb(38, 80, 100), 1, 12));
            LinearLayout.LayoutParams cardLp = matchWrap(); cardLp.topMargin = dp(5); messages.addView(messageCard, cardLp);
            LinearLayout meta = new LinearLayout(this); meta.setOrientation(LinearLayout.HORIZONTAL); meta.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); meta.setGravity(Gravity.CENTER_VERTICAL);
            messageCard.addView(meta, matchWrap());
            Button copyWhole = button("⧉"); copyWhole.setTextSize(13); copyWhole.setContentDescription("نسخ الرسالة كاملة"); copyWhole.setBackground(glassButtonShape(false));
            meta.addView(copyWhole, new LinearLayout.LayoutParams(dp(38), dp(32)));
            copyWhole.setOnClickListener(v -> copyToClipboard("رسالة Botna", body));
            TextView author = text(user ? "أنت" : bot.optString("name", "البوت"), 11, user ? CYAN : TEXT, true);
            LinearLayout.LayoutParams authorLp = new LinearLayout.LayoutParams(0, -2, 1f);
            authorLp.leftMargin = dp(8);
            meta.addView(author, authorLp);
            addFormattedMessageBody(messageCard, body, user ? TEXT : MUTED);
            if (hasAttachment) {
                if ((BotStudioStore.isImageAttachment(attachment) || BotStudioStore.isPdfAttachment(attachment)) && renderedPreviews < 12) {
                    renderedPreviews++;
                    Bitmap preview = BotStudioStore.loadAttachmentPreview(this, attachment, 460);
                    if (preview != null) {
                        ImageView thumbnail = new ImageView(this); thumbnail.setImageBitmap(preview); thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        thumbnail.setAdjustViewBounds(true); thumbnail.setMaxHeight(dp(230)); thumbnail.setBackground(shape(FIELD, CYAN, 1, 10));
                        messageCard.addView(thumbnail, top(matchWrap(), 6));
                        thumbnail.setOnClickListener(v -> showAttachmentPreview(attachment));
                        if (BotStudioStore.isPdfAttachment(attachment)) {
                            Button openOriginal = button("فتح ملف PDF الأصلي"); messageCard.addView(openOriginal, top(matchWrap(), 5));
                            openOriginal.setOnClickListener(v -> shareAttachment(attachment));
                        }
                    } else {
                        Button openOriginal = button("📄  فتح الملف الأصلي: " + BotStudioStore.attachmentName(attachment));
                        messageCard.addView(openOriginal, top(matchWrap(), 6)); openOriginal.setOnClickListener(v -> shareAttachment(attachment));
                    }
                } else {
                    Button file = button("📄  " + BotStudioStore.attachmentName(attachment) + "\n" + BotStudioStore.attachmentMime(attachment));
                    file.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); messageCard.addView(file, top(matchWrap(), 6));
                    file.setOnClickListener(v -> shareAttachment(attachment));
                }
            }
            if (!user) {
                String modelName = msg.optString("model", "");
                if (!modelName.isEmpty()) {
                    TextView modelTag = text("◦ " + modelName, 10, CYAN, false);
                    modelTag.setGravity(Gravity.LEFT); LinearLayout.LayoutParams modelLp = top(matchWrap(), 5); messageCard.addView(modelTag, modelLp);
                }
            }
            TextView stamp = text(formatTimestamp(timestamp, priorDisplayedTimestamp), 10, MUTED, false);
            stamp.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams stampLp = top(matchWrap(), 5);
            messageCard.addView(stamp, stampLp);
            priorDisplayedTimestamp = timestamp;
        }
        if (showOlderAfterReload) { showOlderAfterReload = false; chatScroll.post(() -> chatScroll.scrollTo(0, 0)); }
        else chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        chatStatus = text(chatBusy ? "جارٍ الحصول على رد البوت..." : (attachedFilePath.isEmpty() ? "أرفق صورة أو ملفًا أو استخدم الميكروفون." : "المرفق: " + attachedFileName), 11, CYAN, false);
        panel.addView(chatStatus, top(matchWrap(), 5));
        LinearLayout composer = new LinearLayout(this); composer.setOrientation(LinearLayout.VERTICAL); composer.setPadding(dp(8), dp(6), dp(8), dp(7));
        composer.setBackground(shape(FIELD, CYAN, 1.2f, 22)); panel.addView(composer, top(matchWrap(), 6));
        chatInput = new EditText(this); chatInput.setTextColor(TEXT); chatInput.setHintTextColor(MUTED); chatInput.setTextSize(14); chatInput.setGravity(Gravity.TOP | Gravity.START);
        chatInput.setMinLines(2); chatInput.setMaxLines(6); chatInput.setHint("احكي مع البوت باللهجة اللي بتحبها..."); chatInput.setPadding(dp(8), dp(7), dp(8), dp(7)); chatInput.setBackgroundColor(Color.TRANSPARENT);
        composer.addView(chatInput, matchWrap());
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams controlsLp = top(matchWrap(), 4); composer.addView(controls, controlsLp);
        sendChatButton = button("↑"); sendChatButton.setTextSize(25); sendChatButton.setContentDescription("إرسال إلى البوت"); sendChatButton.setBackground(glassButtonShape(true));
        Button voice = button("🎙"); voice.setTextSize(20); voice.setContentDescription("إملاء صوتي"); voice.setBackground(glassButtonShape(false));
        Button attach = button("＋"); attach.setTextSize(24); attach.setContentDescription("إرفاق ملف أو صورة"); attach.setBackground(glassButtonShape(false));
        sendChatButton.setEnabled(!chatBusy); attach.setEnabled(!chatBusy); voice.setEnabled(!chatBusy);
        controls.addView(sendChatButton, new LinearLayout.LayoutParams(dp(50), dp(48)));
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, dp(1), 1f); controls.addView(new View(this), spacerLp);
        LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(dp(50), dp(48)); voiceLp.leftMargin = dp(7); controls.addView(voice, voiceLp);
        LinearLayout.LayoutParams attachLp = new LinearLayout.LayoutParams(dp(50), dp(48)); attachLp.leftMargin = dp(7); controls.addView(attach, attachLp);
        attach.setOnClickListener(v -> selectFile()); voice.setOnClickListener(v -> startVoiceInput()); sendChatButton.setOnClickListener(v -> sendChat());
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendChat() {
        if (chatBusy) return;
        String message = chatInput == null || chatInput.getText() == null ? "" : chatInput.getText().toString().trim();
        if (message.isEmpty() && attachedFilePath.isEmpty()) { Toast.makeText(this, "اكتب رسالة أو أرفق ملفًا أولًا.", Toast.LENGTH_SHORT).show(); return; }
        if (!BotnaAICompiler.isConfigured(this)) { Toast.makeText(this, "اضبط مزوّد الذكاء الاصطناعي في إعدادات Botna أولًا.", Toast.LENGTH_LONG).show(); return; }
        JSONObject bot = store.bot(selectedBotId); if (bot == null) return;
        final String requestBotId = selectedBotId;
        final String persona = bot.optString("name") + "، الدور: " + bot.optString("role") + "، الشخصية: " + bot.optString("persona") + "\nالمهارات المفعلة:" + store.skillGuidance(selectedBotId);
        final String filePath = attachedFilePath;
        final String fileName = attachedFileName;
        final String fileMime = attachedFileMime;
        attachedFilePath = ""; attachedFileName = ""; attachedFileMime = "";
        store.addMessage(requestBotId, "user", message, filePath, "");
        if (chatInput != null) chatInput.setText("");
        if (isActiveTaskCorrection(message)) {
            boolean activeFlag = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE)
                    .getBoolean(BotnaAccessibilityService.PREF_AGENT_TASK_ACTIVE, false);
            if (activeFlag && BotnaAccessibilityService.isServiceActuallyConnected(this)) {
                sendBroadcast(new Intent(BotnaAccessibilityService.ACTION_CORRECT_SMART_TASK).setPackage(getPackageName()).putExtra("correction", message));
                store.addMessage(requestBotId, "assistant", "وصل التصحيح إلى خدمة الوكيل المتصلة. سيوقف الوكيل بقية الخطوات المركبة القديمة عند أقرب نقطة آمنة، ثم يلتقط الشاشة مجددًا ويعيد التخطيط. لم أعتبر ذلك نجاحًا للتنفيذ بعد.", "", BotnaAICompiler.model(this));
                chatBusy = false;
                render();
                return;
            }
            if (activeFlag) {
                chatBusy = true;
                render();
                chatExecutor.execute(() -> {
                    JSONObject checkpoint = findLatestOpenCheckpointForBot(requestBotId);
                    runOnUiThread(() -> {
                        chatBusy = false;
                        if (checkpoint != null) {
                            resumeCheckpointWithCorrection(requestBotId, message, checkpoint);
                        } else {
                            store.addMessage(requestBotId, "assistant", "تعذر تسليم التصحيح لأن خدمة الوكيل غير متصلة ولم أجد نقطة استئناف لهذا البوت. لا أرى الشاشة حاليًا ولم ينفذ الهاتف شيئًا.", "", BotnaAICompiler.model(this));
                            if (requestBotId.equals(selectedBotId)) render();
                        }
                    });
                });
                return;
            }
        }
        if (isTaskCorrectionRequest(message) && hasOpenCheckpointForBot(requestBotId)) {
            chatBusy = true;
            render();
            chatExecutor.execute(() -> {
                JSONObject checkpoint = findLatestOpenCheckpointForBot(requestBotId);
                runOnUiThread(() -> {
                    chatBusy = false;
                    if (checkpoint == null) {
                        store.addMessage(requestBotId, "assistant", "لم أجد مهمة غير مكتملة مرتبطة بهذا البوت لتطبيق التصحيح عليها. لم يبدأ أي تنفيذ.", "", BotnaAICompiler.model(this));
                        if (requestBotId.equals(selectedBotId)) render();
                        return;
                    }
                    resumeCheckpointWithCorrection(requestBotId, message, checkpoint);
                });
            });
            return;
        }
        if (isResumeAutomationRequest(message)) {
            boolean agentActive = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE)
                    .getBoolean(BotnaAccessibilityService.PREF_AGENT_TASK_ACTIVE, false);
            if (agentActive && BotnaAccessibilityService.isServiceActuallyConnected(this)) {
                String restartInstruction = "طلب المستخدم إعادة التقييم والاستكمال من حالة الشاشة الحالية. التقط لقطة شاشة جديدة الآن، أعد تقييم الهدف المصحح وسجل الأفعال، ولا تكرر الإجراء نفسه إذا فشل أو لم يغير الشاشة. إذا تعذر التصوير أو التحكم فتوقف واذكر السبب بصدق.";
                sendBroadcast(new Intent(BotnaAccessibilityService.ACTION_CORRECT_SMART_TASK)
                        .setPackage(getPackageName()).putExtra("correction", restartInstruction));
                store.addMessage(requestBotId, "assistant",
                        "وصل طلب الاستكمال إلى خدمة الوكيل المتصلة. طلبت منها إعادة التقاط الشاشة والتخطيط من الحالة الحالية بدل بدء نسخة مكررة من المهمة. سأعتبرها مستأنفة فقط بعد نجاح فحص الشاشة فعليًا.",
                        "", BotnaAICompiler.model(this));
                if (requestBotId.equals(selectedBotId)) render();
                return;
            }
            chatBusy = true;
            render();
            chatExecutor.execute(() -> {
                JSONObject checkpoint = findCheckpointForResume(requestBotId, message);
                runOnUiThread(() -> {
                    chatBusy = false;
                    if (checkpoint == null) {
                        store.addMessage(requestBotId, "assistant", "لا أستطيع استئناف المهمة: لم أجد نقطة استئناف محفوظة لهذه الشخصية. لم أدّعِ أن التحكم بدأ أو أنني أرى الشاشة.", "", BotnaAICompiler.model(this));
                        if (requestBotId.equals(selectedBotId)) render();
                    } else {
                        if (requestBotId.equals(selectedBotId)) render();
                        resumeCheckpoint(checkpoint);
                    }
                });
            });
            return;
        }
        if (isAutomationTaskRequest(message)) {
            chatBusy = true;
            render();
            chatExecutor.execute(() -> {
                String preparedGoal = prepareTaskGoal(message, filePath);
                runOnUiThread(() -> {
                    chatBusy = false;
                    startTaskFromChat(requestBotId, message, filePath, preparedGoal);
                });
            });
            return;
        }
        chatBusy = true; render();
        final String modelUsed = BotnaAICompiler.model(this);
        final String thinkingLevel = BotStudioStore.thinkingLevel(this);
        chatExecutor.execute(() -> {
            Bitmap image = null;
            String response;
            try {
                String requestText = store.contextForChat(requestBotId, message, 26000)
                        + "\n" + BotnaAccessibilityService.agentRuntimeStatus(this)
                        + "\nالوقت الحالي على الجهاز: " + BotnaWebTools.currentTimeText()
                        + ". استخدم الوقت لحساب الفواصل. هذه المحادثة لا تحتوي على لقطة شاشة حية إلا إذا أُرفقت صورة واضحة في الطلب نفسه."
                        + "\n\n" + BotnaWebTools.capabilitiesDescription()
                        + "\n\nالرسالة الحالية التي يجب الإجابة عنها: " + message;
                String localToolResult = BotnaWebTools.localToolContext(message);
                if (!localToolResult.isEmpty()) requestText += "\n\n--- نتيجة أداة محلية ---\n" + localToolResult + "\n--- نهاية نتيجة الأداة ---\nاستخدم نتيجة الأداة كما هي عند الإجابة، ولا تدّع أن الأداة نفذت كودًا عامًا.";
                if (BotnaWebTools.shouldSearch(message)) {
                    try {
                        String liveResults = BotnaWebTools.searchContext(message);
                        requestText += "\n\n--- نتائج أداة البحث المباشر ---\n" + liveResults
                                + "\n--- نهاية نتائج البحث ---\nاعتمد على هذه البيانات عند الإجابة عن الجزء الحالي، واذكر وقت الجلب والمصدر. إذا كانت النتائج غير كافية أو متعارضة فصرّح بذلك، ولا تستبدلها بسعر أو معلومة حالية من الذاكرة.";
                    } catch (Exception searchFailure) {
                        requestText += "\n\n[حاول Botna البحث عبر الإنترنت لكنه فشل: "
                                + (searchFailure.getMessage() == null ? searchFailure.getClass().getSimpleName() : searchFailure.getMessage())
                                + "]. أبلغ المستخدم أن البحث المباشر لم ينجح، ولا تقدّم الأرقام الحالية على أنها مؤكدة.";
                    }
                }
                if (!filePath.isEmpty()) {
                    String fileText = BotStudioStore.readAttachmentText(filePath);
                    StringBuilder attachmentContext = new StringBuilder("\n\n[مرفق المستخدم: ").append(fileName).append("؛ نوع الملف: ").append(fileMime).append("]");
                    if (!fileText.isEmpty()) {
                        if (fileText.length() > 24000) fileText = fileText.substring(0, 24000) + "\n[اقتُطع النص للحفاظ على استقرار الطلب]";
                        attachmentContext.append("\nمحتوى الملف المستخرج محليًا:\n").append(fileText);
                    } else if (!BotStudioStore.isImageAttachment(filePath) && !BotStudioStore.isPdfAttachment(filePath)) {
                        attachmentContext.append("\nلم يستطع Botna استخراج محتوى هذا النوع من الملف؛ لا تخمّن محتواه، واطلب صيغة قابلة للقراءة عند الحاجة.");
                    }
                    requestText += attachmentContext.toString();
                }
                if (!filePath.isEmpty() && (BotStudioStore.isImageAttachment(filePath) || BotStudioStore.isPdfAttachment(filePath))) image = BotStudioStore.loadAttachmentPreview(this, filePath, 1440);
                response = BotnaAICompiler.chat(this, persona, requestText, image, thinkingLevel);
            } catch (Exception e) { response = "تعذر الحصول على رد: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            finally { if (image != null && !image.isRecycled()) image.recycle(); }
            final String answer = response == null ? "لم يصل رد نصي من المزوّد." : response;
            runOnUiThread(() -> { chatBusy = false; store.addMessage(requestBotId, "assistant", answer, "", modelUsed); if (requestBotId.equals(selectedBotId)) render(); });
        });
    }

    private boolean isActiveTaskCorrection(String raw) {
        if (raw == null || raw.trim().isEmpty()) return false;
        boolean active = getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE)
                .getBoolean(BotnaAccessibilityService.PREF_AGENT_TASK_ACTIVE, false);
        if (!active) return false;
        String value = BotnaArabicDialect.normalize(raw);
        String[] markers = {"لا هيك", "مش هيك", "غلط", "خطا", "خطوات صحيحه", "الخطوات الصحيحه", "نفذ اللي طلبته", "نفذ المطلوب بالضبط", "لا تكرر", "لا تعيد", "بدل الاتجاه", "اعكس الاتجاه", "عكس التمرير", "تعكس التمرير", "التمرير بالعكس", "ارجع خطوه", "وقف هالحركه", "وقف تكرار", "نفذ بالخطوات", "التصحيح", "بدل ما", "مش راضي", "بالعكس", "correct the action", "do not repeat", "wrong direction", "try a different"};
        for (String marker : markers) if (value.contains(BotnaArabicDialect.normalize(marker))) return true;
        return false;
    }

    private boolean isResumeAutomationRequest(String raw) {
        String value = BotnaArabicDialect.normalize(raw == null ? "" : raw).trim();
        String[] phrases = {"كمل المهمه", "اكمل المهمه", "استأنف المهمه", "تابع المهمه", "واصل المهمه",
                 "كمل من وين وقفت", "كمل من حيث توقفت", "كمل الوكيل", "استأنف الوكيل", "خلينا نكمل",
                "ابدأ من جديد", "ابدأ المهمة من جديد", "ارجع اشتغل", "جرب من جديد", "اعد المحاولة",
                "شغل الوكيل", "resume task", "continue the task", "resume the task", "start again"};
        for (String phrase : phrases) if (value.contains(BotnaArabicDialect.normalize(phrase))) return true;
        String[] shortCues = {"كمل", "اكمل", "تابع", "واصل", "استأنف", "ابدا", "ابدأ", "اشتغل", "شغل", "جرب", "عيد المحاوله", "عيد المحاولة", "نفذ", "نفذها", "ارجع كمل", "كملها"};
        for (String cue : shortCues) {
            String normalizedCue = BotnaArabicDialect.normalize(cue);
            if (value.equals(normalizedCue) || value.startsWith(normalizedCue + " ") || value.startsWith(normalizedCue + "،")) {
                return hasOpenCheckpointForBot(selectedBotId);
            }
        }
        return false;
    }

    private boolean isTaskCorrectionRequest(String raw) {
        String value = BotnaArabicDialect.normalize(raw == null ? "" : raw);
        String[] markers = {"لا هيك", "مش هيك", "غلط", "خطا", "ما اعطى النتيجه", "ما اعطت النتيجه",
                "ما عمل المطلوب", "ما نفذ المطلوب", "ما اشتغل", "خطوات صحيحه", "نفذ اللي طلبته", "نفذ المطلوب بالضبط",
                "لا تكرر", "لا تعيد", "بدل الاتجاه", "اعكس الاتجاه", "عكس التمرير", "تعكس التمرير", "التمرير بالعكس",
                "الخطوات الصحيحه", "بالطريقه الصحيحه", "جرب طريقه ثانيه", "صحح المسار", "ارجع خطوه",
                "وقف هالحركه", "وقف تكرار", "نفذ بالخطوات", "بالعكس", "wrong action", "wrong direction", "do not repeat"};
        for (String marker : markers) if (value.contains(BotnaArabicDialect.normalize(marker))) return true;
        return false;
    }

    private boolean hasOpenCheckpointForBot(String botId) {
        JSONArray checkpoints = store.checkpoints(true);
        for (int i = 0; i < checkpoints.length(); i++) {
            JSONObject checkpoint = checkpoints.optJSONObject(i);
            if (checkpoint == null) continue;
            String taskId = checkpoint.optString("taskId", "");
            String linkedBot = BotStudioStore.botForTask(this, taskId);
            if (linkedBot.isEmpty() || linkedBot.equals(botId)) return true;
        }
        return false;
    }

    private JSONObject findLatestOpenCheckpointForBot(String botId) {
        JSONArray checkpoints = store.checkpoints(true);
        for (int i = 0; i < checkpoints.length(); i++) {
            JSONObject checkpoint = checkpoints.optJSONObject(i);
            if (checkpoint == null) continue;
            String taskId = checkpoint.optString("taskId", "");
            String linkedBot = BotStudioStore.botForTask(this, taskId);
            if (!linkedBot.isEmpty() && !linkedBot.equals(botId)) continue;
            return checkpoint;
        }
        return null;
    }

    private void resumeCheckpointWithCorrection(String botId, String correction, JSONObject checkpoint) {
        if (checkpoint == null) {
            store.addMessage(botId, "assistant", "لم أجد نقطة استئناف؛ لم يبدأ التنفيذ.", "", BotnaAICompiler.model(this));
            if (botId.equals(selectedBotId)) render();
            return;
        }
        String id = checkpoint.optString("taskId", "");
        String oldGoal = checkpoint.optString("goal", "");
        if (id.isEmpty() || oldGoal.trim().isEmpty()) {
            store.addMessage(botId, "assistant", "نقطة الاستئناف لا تحتوي على هدف محفوظ صالح؛ لم أبدأ التنفيذ ولم أدّع رؤية الشاشة.", "", BotnaAICompiler.model(this));
            if (botId.equals(selectedBotId)) render();
            return;
        }
        String correctedGoal = oldGoal + "\n\nتصحيح صريح من المستخدم قبل الاستئناف؛ له الأولوية على الخطة القديمة: " + correction.trim()
                + "\nيجب إعادة قراءة الشاشة الحالية، وتغيير الاستراتيجية إذا فشل الإجراء السابق، وعدم تكرار الإجراء نفسه بمعلماته نفسها إذا لم ينتج تغييرًا مرئيًا.";
        try {
            store.saveCheckpoint(id, correctedGoal, checkpoint.optString("scriptId", ""), checkpoint.optInt("actionCount", 0),
                    checkpoint.optJSONArray("history"), "paused");
            BotnaAgentStore.updateGoal(this, id, "استئناف بعد تصحيح المستخدم", correctedGoal);
        } catch (Exception ignored) { }
        JSONObject correctedCheckpoint;
        try {
            correctedCheckpoint = new JSONObject(checkpoint.toString());
            correctedCheckpoint.put("goal", correctedGoal);
            correctedCheckpoint.put("state", "paused");
        } catch (Exception e) {
            store.addMessage(botId, "assistant", "تعذر تجهيز نسخة المهمة المصححة؛ لم يبدأ التنفيذ.", "", BotnaAICompiler.model(this));
            if (botId.equals(selectedBotId)) render();
            return;
        }
        store.addMessage(botId, "assistant", "حفظت تصحيحك داخل هدف المهمة السابقة. سأحاول إعادة الاتصال بخدمة الوكيل، ثم سأتحقق من لقطة شاشة جديدة. لن أدّعي أن الاستئناف بدأ ما لم تؤكد الخدمة ذلك.", "", BotnaAICompiler.model(this));
        if (botId.equals(selectedBotId)) render();
        resumeCheckpoint(correctedCheckpoint);
    }

    private JSONObject findCheckpointForResume(String botId, String request) {
        JSONArray checkpoints = store.checkpoints(true);
        String normalizedRequest = BotnaArabicDialect.normalize(request == null ? "" : request);
        java.util.HashSet<String> genericTerms = new java.util.HashSet<>(java.util.Arrays.asList(
                "كمل", "اكمل", "استأنف", "تابع", "واصل", "ابدا", "اشتغل", "شغل", "جرب", "عيد", "نفذ", "نفذها", "كملها", "خلينا", "جديد", "مره", "ثانيه", "اول", "المهمه", "مهمه", "تنفيذ", "الوكيل", "بوتنا", "من", "وين", "حيث", "وقفت", "توقفت", "ارجع", "كنت", "شغال", "شغاله", "عليها", "عليه", "معها", "معه", "التي", "الذي", "كان", "كانت", "عملتها", "عملت", "لما", "قبل", "بعد", "انت", "انتا", "هاي", "هذي", "اخر", "اخير", "resume", "task", "continue", "the", "from", "where", "stopped", "please"));
        ArrayList<String> specificTerms = new ArrayList<>();
        for (String token : normalizedRequest.split("\\s+")) {
            if (token.length() >= 4 && !genericTerms.contains(token) && !specificTerms.contains(token)) specificTerms.add(token);
        }
        JSONObject best = null; int bestScore = -1;
        for (int i = 0; i < checkpoints.length(); i++) {
            JSONObject cp = checkpoints.optJSONObject(i); if (cp == null) continue;
            String taskId = cp.optString("taskId", "");
            String linkedBot = BotStudioStore.botForTask(this, taskId);
            if (!linkedBot.isEmpty() && !linkedBot.equals(botId)) continue;
            String state = cp.optString("state", "");
            if ("completed".equalsIgnoreCase(state)) continue;
            String goal = BotnaArabicDialect.normalize(cp.optString("goal", ""));
            int score = 0;
            if (!normalizedRequest.isEmpty() && normalizedRequest.length() >= 8 && goal.contains(normalizedRequest)) score += 10;
            for (String token : specificTerms) if (goal.contains(token)) score += 3;
            if (specificTerms.isEmpty()) score = 1;
            if (score > bestScore) { bestScore = score; best = cp; }
        }
        if (best != null && !specificTerms.isEmpty() && bestScore <= 0) return null;
        return best;
    }

    private void copyToClipboard(String label, String value) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard == null) throw new IllegalStateException("خدمة النسخ غير متاحة");
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value == null ? "" : value));
            Toast.makeText(this, "تم نسخ النص", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "تعذر نسخ الرسالة", Toast.LENGTH_SHORT).show(); }
    }

    private String formatTimestamp(long timestamp, long previousTimestamp) {
        if (timestamp <= 0) return "وقت غير معروف";
        Date date = new Date(timestamp);
        java.util.Calendar clock = java.util.Calendar.getInstance();
        clock.setTime(date);
        String period = clock.get(java.util.Calendar.HOUR_OF_DAY) < 12 ? "صباحًا" : "مساءً";
        String base = new SimpleDateFormat("dd/MM/yyyy • hh:mm", Locale.getDefault()).format(date) + " " + period;
        if (previousTimestamp <= 0 || timestamp < previousTimestamp) return base;
        long gap = timestamp - previousTimestamp;
        String delta;
        if (gap < 1000L) delta = "0 ث";
        else if (gap < 60000L) delta = (gap / 1000L) + " ث عن السابقة";
        else if (gap < 3600000L) delta = (gap / 60000L) + " د عن السابقة";
        else if (gap < 86400000L) delta = (gap / 3600000L) + " س عن السابقة";
        else delta = (gap / 86400000L) + " يوم عن السابقة";
        return base + " • " + delta;
    }

    private String normalizeMessageBody(String input) {
        if (input == null || input.isEmpty()) return "";
        String value = input.replace("\r\n", "\n").replace('\r', '\n')
                .replace('\u00a0', ' ').replace("\u200b", "").replace("\ufeff", "")
                .replace('\u2028', '\n').replace('\u2029', '\n').replace('\u000b', '\n').replace('\u000c', '\n');
        value = value.replaceAll("(?m)^[\\p{Z}\\p{C}]*$", "");
        value = value.replaceAll("(?m)^[\\t \\p{Zs}]+$", "");
        value = value.replaceAll("\\n{3,}", "\n\n").trim();
        return value;
    }

    private String normalizeCodeBlockDisplay(String source) {
        if (source == null || source.isEmpty()) return "";
        String value = source.replace("\r\n", "\n").replace('\r', '\n');
        value = value.replaceAll("(?m)^[\\t ]+$", "");
        return value.replaceAll("\\n{4,}", "\n\n\n");
    }

    private void addFormattedMessageBody(LinearLayout parent, String body, int color) {
        String source = body == null ? "" : body;
        Pattern codePattern = Pattern.compile("(?s)```([^\\r\\n`]*)\\s*\\r?\\n(.*?)```");
        Matcher matcher = codePattern.matcher(source);
        int cursor = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            if (matcher.start() > cursor) addMessageText(parent, source.substring(cursor, matcher.start()), color);
            addCodeBlock(parent, matcher.group(1).trim(), matcher.group(2));
            cursor = matcher.end();
        }
        if (!found) { addMessageText(parent, source, color); return; }
        if (cursor < source.length()) addMessageText(parent, source.substring(cursor), color);
    }

    private void addMessageText(LinearLayout parent, String textValue, int color) {
        if (textValue == null || textValue.trim().isEmpty()) return;
        TextView body = text(textValue.trim(), 13, color, false);
        body.setGravity(Gravity.TOP | Gravity.RIGHT);
        body.setIncludeFontPadding(false);
        body.setLineSpacing(dp(1), 1.0f);
        body.setAutoLinkMask(Linkify.WEB_URLS); body.setLinkTextColor(CYAN); body.setMovementMethod(LinkMovementMethod.getInstance());
        body.setTextIsSelectable(true);
        parent.addView(body, top(matchWrap(), 4));
    }

    private void addCodeBlock(LinearLayout parent, String language, String code) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(8), dp(6), dp(8), dp(6));
        box.setBackground(shape(Color.rgb(4, 10, 17), Color.rgb(48, 112, 136), 1, 10));
        parent.addView(box, top(matchWrap(), 6));
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); header.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(header, matchWrap());
        TextView lang = text(language.isEmpty() ? "CODE" : language, 10, CYAN, true); lang.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        header.addView(lang, new LinearLayout.LayoutParams(0, dp(28), 1f));
        Button copy = button("نسخ الكود ⧉"); copy.setTextSize(10); header.addView(copy, new LinearLayout.LayoutParams(-2, dp(30)));
        copy.setOnClickListener(v -> copyToClipboard(language.isEmpty() ? "كود Botna" : language, code));
        HorizontalScrollView horizontal = new HorizontalScrollView(this); horizontal.setFillViewport(false); horizontal.setHorizontalScrollBarEnabled(true);
        box.addView(horizontal, top(matchWrap(), 4));
        TextView codeView = text(normalizeCodeBlockDisplay(code), 12, TEXT, false);
        codeView.setTypeface(Typeface.MONOSPACE); codeView.setGravity(Gravity.LEFT | Gravity.TOP); codeView.setTextIsSelectable(true);
        codeView.setHorizontallyScrolling(true); codeView.setPadding(dp(5), dp(5), dp(5), dp(5));
        horizontal.addView(codeView, new HorizontalScrollView.LayoutParams(-2, -2));
    }

    private String conversationContext(JSONArray messages) {
        StringBuilder out = new StringBuilder("سياق المحادثة السابقة مع الشخصية المختارة. استخدمه لفهم المراجع والطلبات المتتابعة ولا تكرر الإجابات السابقة دون حاجة:");
        int start = Math.max(0, messages.length() - 16);
        for (int i = start; i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i); if (m == null) continue;
            String role = m.optString("role", "user");
            out.append("\n").append("assistant".equals(role) ? "البوت" : "المستخدم").append(": ").append(m.optString("body", ""));
            String attached = m.optString("image", "");
            if (!attached.isEmpty()) out.append(" [مرفق سابق: ").append(BotStudioStore.attachmentName(attached)).append("]");
        }
        return out.toString();
    }

    private boolean isAutomationTaskRequest(String raw) {
        String value = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ة','ه').trim();
        if (value.isEmpty()) return false;
        String[] questionStarts = {"كيف ", "شو يعني", "شو معنى", "ما معنى", "اشرح ", "ما هو", "ما هي", "هل يمكن", "هل تستطيع", "شو رأيك", "ليش ", "لماذا ", "what is ", "how do i", "explain "};
        for (String q : questionStarts) if (value.startsWith(q)) return false;
        String[] explicit = {"نفذ المهمة", "نفذ هذا", "ابدأ المهمة", "ابدأ بتنفيذ", "كلفك", "نفذ الامر", "نفّذ المهمة", "شغل المهمة"};
        for (String q : explicit) if (value.contains(q)) return true;
        String[] verbs = {"افتح", "افتحلي", "فوت", "ادخل", "روح", "روحلي", "انتقل", "اضغط", "اكبس", "دوس", "انقر", "شغل", "شغلي", "اكتب", "اكتبلي", "ابحث", "دور", "فتش", "اقرأ", "مرر", "اسحب", "استخدم", "نفذ", "افحص", "تأكد", "اتصل", "حط", "املأ", "عبّي", "ابعث", "ارسل", "احذف", "امسح", "اختار", "اختر", "صوّر", "صور", "اسكر", "سكر", "اغلق", "فعل", "فعّل", "انزل", "طلع", "اضف", "ضيف", "حمّل", "نزل", "open ", "click ", "tap ", "search ", "go to ", "navigate ", "type ", "scroll ", "send ", "fill ", "launch "};
        for (String verb : verbs) {
            if (value.startsWith(verb) || value.matches(".*\\b" + java.util.regex.Pattern.quote(verb.trim()) + "\\b.*")) return true;
        }
        return value.startsWith("بدي منك ") || value.startsWith("بدي اياك ") || value.startsWith("لو سمحت افتح") || value.startsWith("ممكن تفتح");
    }

    private String prepareTaskGoal(String goal, String attachmentPath) {
        String effectiveGoal = goal == null ? "" : goal.trim();
        if (attachmentPath == null || attachmentPath.isEmpty()) return effectiveGoal;
        String attachmentName = BotStudioStore.attachmentName(attachmentPath);
        String attachmentMime = BotStudioStore.attachmentMime(attachmentPath);
        effectiveGoal += "\n[مرفق من محادثة البوت: " + attachmentName + "؛ النوع: " + attachmentMime + "]";
        if (BotStudioStore.isImageAttachment(attachmentPath) || BotStudioStore.isPdfAttachment(attachmentPath)) {
            Bitmap reference = null;
            try {
                reference = BotStudioStore.loadAttachmentPreview(this, attachmentPath, 1440);
                if (reference == null) {
                    effectiveGoal += "\nلم يتمكن Botna من إنشاء معاينة للمرفق. لا تفترض محتوى الصورة أو المستند.";
                } else {
                    String prompt = "مهمة المستخدم التي تحتاج هذا المرجع البصري: " + effectiveGoal
                            + "\nحلل الصورة المرفقة كمرجع فقط. استخرج النصوص والأشكال والعناصر المرئية المرتبطة بالمهمة، وميّز بين ما تراه فعلًا وما لا تستطيع تأكيده. لا تنفذ المهمة ولا تزعم أنك نقرت شيئًا. أعد وصفًا موجزًا ودقيقًا يمكن أن يستخدمه منفذ أتمتة أندرويد للعثور على الهدف على الشاشة الحالية.";
                    String description = BotnaAICompiler.chatWithImageStrict(this,
                            "أنت محلل مرجع بصري لتجهيز هدف أتمتة في Botna. لا تخمّن أي جزء غير ظاهر، وإذا لم تتمكن من رؤية الصورة فأبلغ بذلك ولا تصف محتوى افتراضيًا.",
                            prompt, reference, BotStudioStore.thinkingLevel(this));
                    if (description == null || description.trim().isEmpty()) {
                        effectiveGoal += "\nلم يتمكن النموذج الحالي من تحليل المرفق بصريًا. لا تفترض محتواه؛ اعتمد على لقطة الشاشة الحية والنص الأصلي فقط.";
                    } else {
                        description = description.trim();
                        if (description.length() > 6000) description = description.substring(0, 6000) + "\n[اقتُطع الوصف البصري للحفاظ على استقرار المهمة]";
                        effectiveGoal += "\nوصف المرجع البصري الناتج عن تحليل النموذج (استخدمه كقرينة، وتحقق من الشاشة الحية قبل الضغط):\n" + description;
                    }
                }
            } catch (Exception e) {
                effectiveGoal += "\nتعذر تحليل المرفق بصريًا قبل المهمة؛ استخدم الشاشة الحية ولا تخمّن محتوى الملف. السبب: "
                        + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                if (reference != null && !reference.isRecycled()) reference.recycle();
            }
        } else {
            String extracted = BotStudioStore.readAttachmentText(attachmentPath);
            if (!extracted.isEmpty()) {
                if (extracted.length() > 16000) extracted = extracted.substring(0, 16000) + "\n[اقتُطع النص للحفاظ على استقرار المهمة]";
                effectiveGoal += "\nمحتوى نصي مستخرج من المرفق:\n" + extracted;
            } else {
                effectiveGoal += "\nلم يتم استخراج نص قابل للقراءة من هذا النوع من الملف؛ لا تفترض محتواه.";
            }
        }
        return effectiveGoal;
    }

    private void startTaskFromChat(String botId, String goal, String attachmentPath, String effectiveGoal) {
        if (!accessibilityEnabled() || !BotnaAccessibilityService.isServiceActuallyConnected(this)) {
            String id = BotnaAgentStore.createTask(this, effectiveGoal);
            BotStudioStore.associateTaskWithBot(this, id, botId);
            getSharedPreferences(BotnaAICompiler.PREFS, MODE_PRIVATE).edit().putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK, effectiveGoal)
                    .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK_ID, id)
                    .putBoolean(BotnaAccessibilityService.PREF_PENDING_SMART_RESUME, false).apply();
            String reason = !accessibilityEnabled()
                    ? "حُفظت المهمة بانتظار تفعيل خدمة إمكانية الوصول. لم يبدأ التحكم بعد ولا توجد لقطة شاشة."
                    : "حُفظت المهمة، لكن خدمة إمكانية الوصول ليست متصلة فعليًا الآن. لم يبدأ التحكم بعد ولا توجد لقطة شاشة مؤكدة؛ ستبدأ محاولة التنفيذ فقط عند إعادة اتصال الخدمة.";
            store.addMessage(botId, "assistant", reason, "", BotnaAICompiler.model(this));
            render();
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignored) { }
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
            return;
        }
        String id = BotnaAgentStore.createTask(this, effectiveGoal);
        BotStudioStore.associateTaskWithBot(this, id, botId);
        getSharedPreferences(BotnaAICompiler.PREFS, MODE_PRIVATE).edit()
                .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK, effectiveGoal)
                .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK_ID, id)
                .putBoolean(BotnaAccessibilityService.PREF_PENDING_SMART_RESUME, false).apply();
        String taskNote = "أُرسلت المهمة إلى خدمة الوكيل؛ لم أعتبر التنفيذ بدأ فعليًا بعد. سيبدأ الوكيل فقط عندما يستقبل المهمة ويلتقط لقطة شاشة جديدة بنجاح، ثم سيرسل النتيجة أو سبب الفشل إلى هذه المحادثة.\nالهدف: " + goal;
        if (attachmentPath != null && !attachmentPath.isEmpty()) taskNote += "\nالمرفق المرتبط بالطلب: " + BotStudioStore.attachmentName(attachmentPath);
        store.addMessage(botId, "assistant", taskNote, "", BotnaAICompiler.model(this));
        sendBroadcast(new Intent(BotnaAccessibilityService.ACTION_START_SMART_TASK).setPackage(getPackageName()).putExtra("task", effectiveGoal).putExtra("agentTaskId", id));
        Toast.makeText(this, "أُرسلت المهمة؛ ينتظر Botna تأكيد استلامها والتقاط الشاشة.", Toast.LENGTH_SHORT).show();
        moveTaskToBack(true);
        finish();
    }

    private void startVoiceInput() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-PS");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-PS");
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "احكي أمرك لبوتنا");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            startActivityForResult(intent, 4112);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "لا يوجد تطبيق تعرّف صوتي متاح على الجهاز.", Toast.LENGTH_LONG).show();
        }
    }

    private void selectFile() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT); pick.addCategory(Intent.CATEGORY_OPENABLE); pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        startActivityForResult(pick, 4108);
    }

    private void selectSkillFile() {
        String[] choices = {"استيراد من GitHub أو رابط HTTPS", "اقتراحات مهارات جاهزة", "استيراد ملف من الجهاز"};
        new AlertDialog.Builder(this).setTitle("مصدر المهارة")
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) showSkillUrlImportDialog();
                    else if (which == 1) showSkillSuggestions();
                    else {
                        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        pick.addCategory(Intent.CATEGORY_OPENABLE);
                        pick.setType("*/*");
                        startActivityForResult(pick, 4111);
                    }
                }).show();
    }

    private void showSkillUrlImportDialog() {
        EditText address = field("رابط GitHub أو SKILL.md أو Markdown على HTTPS", "");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setSingleLine(false);
        address.setMinLines(2);
        new AlertDialog.Builder(this).setTitle("استيراد مهارة من الإنترنت")
                .setMessage("يدعم مستودع GitHub عام، أو رابط ملف RAW/Markdown/JSON عبر HTTPS. ستُستورد التعليمات كنص فقط، ولن ينفّذ Botna أي كود من المستودع.")
                .setView(address).setNegativeButton("إلغاء", null).setPositiveButton("جلب المهارة", (dialog, which) -> {
                    String url = address.getText() == null ? "" : address.getText().toString().trim();
                    if (url.isEmpty() || !url.startsWith("https://")) {
                        Toast.makeText(this, "أدخل رابط HTTPS صالحًا.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    fetchAndImportSkill(url);
                }).show();
    }

    private void showSkillSuggestions() {
        String[] titles = {"التنقل والتعامل مع التطبيقات", "التصحيح بعد فشل الإجراء", "البحث والتحقق من المصادر", "استخراج وتحليل الملفات", "تأكيد الشاشة قبل النقر", "التوقف الآمن للإجراءات الحساسة"};
        String[] descriptions = {"خطوات عملية للتحكم بالتطبيقات", "منع تكرار الفعل الفاشل", "استخدام الإنترنت مع ذكر المصدر والوقت", "قراءة البيانات المنظمة دون اختلاق قيم", "التأكد من النص والعنصر قبل الضغط", "طلب موافقة المستخدم قبل الأثر الخارجي"};
        String[] guidance = {
                "قسّم الهدف إلى فعل واحد في كل مرة. اقرأ الشاشة بعد كل فعل، وتأكد من التطبيق والزر قبل المتابعة. لا تكرر خطوة إذا لم يتغير السياق.",
                "عند فشل فعل أو عدم تغير الشاشة، سجّل النتيجة، ولا تكرر نفس النوع والمعلمات مباشرة. جرّب بديلًا تدعمه الشاشة، أو أوقف المهمة مع سبب واضح.",
                "للمعلومات الحديثة استخدم أداة البحث المتاحة، وقارن المصادر، واذكر وقت الجلب. إذا فشل البحث فلا تقدم معلومات من الذاكرة على أنها آنية.",
                "حلّل النصوص وJSON وCSV والملفات المكتبية التي يستطيع Botna استخراج محتواها. اذكر صراحةً الأنواع التي لا يمكن قراءتها بدل تخمين المحتوى.",
                "قبل النقر، تحقق من لقطة شاشة حديثة أو عنصر إمكانية وصول مطابق. بعد النقر تحقق من العلامة المتوقعة، ولا تعلن النجاح اعتمادًا على إرسال الإيماءة وحده.",
                "توقف واطلب موافقة واضحة قبل الدفع أو حذف البيانات أو النشر أو إرسال محتوى خارجي. لا تحاول تجاوز القيود أو تأكيدات النظام."
        };
        boolean[] checked = new boolean[titles.length];
        new AlertDialog.Builder(this).setTitle("اقتراحات مهارات جاهزة")
                .setMultiChoiceItems(titles, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton("إلغاء", null).setPositiveButton("إضافة المحددة", (dialog, which) -> {
                    int added = 0;
                    for (int i = 0; i < checked.length; i++) {
                        if (!checked[i]) continue;
                        BotStudioStore.Skill skill = store.saveCustomSkill(titles[i], descriptions[i], guidance[i]);
                        store.setSkillEnabled(selectedBotId, skill.id, true);
                        added++;
                    }
                    Toast.makeText(this, added == 0 ? "لم تحدد مهارة." : "تمت إضافة " + added + " مهارة وتفعيلها.", Toast.LENGTH_LONG).show();
                    render();
                }).show();
    }

    private void fetchAndImportSkill(String sourceUrl) {
        Toast.makeText(this, "جارٍ جلب المهارة والتحقق من محتواها…", Toast.LENGTH_SHORT).show();
        final String targetBotId = selectedBotId;
        skillImportExecutor.execute(() -> {
            ImportedSkillData result = null;
            String failure = null;
            try { result = fetchSkillFromUrl(sourceUrl); }
            catch (Exception e) { failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
            ImportedSkillData finalResult = result;
            String finalFailure = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (finalResult == null) {
                    new AlertDialog.Builder(this).setTitle("تعذر استيراد المهارة")
                            .setMessage("لم يتم حفظ أي تعليمات.\\n" + finalFailure)
                            .setPositiveButton("حسنًا", null).show();
                    return;
                }
                String raw = finalResult.content;
                String title = finalResult.title;
                String description = finalResult.description;
                String guidance = raw;
                try {
                    JSONObject obj = new JSONObject(raw);
                    title = obj.optString("title", obj.optString("name", title));
                    description = obj.optString("description", description);
                    guidance = obj.optString("guidance", obj.optString("instructions", obj.optString("prompt", raw)));
                } catch (Exception ignored) { }
                if (guidance.trim().isEmpty()) {
                    Toast.makeText(this, "الملف لا يحتوي على تعليمات مهارة قابلة للاستيراد.", Toast.LENGTH_LONG).show();
                    return;
                }
                BotStudioStore.Skill skill = store.saveCustomSkill(title, description, guidance);
                store.setSkillEnabled(targetBotId, skill.id, true);
                Toast.makeText(this, "تم استيراد المهارة وتفعيلها للشخصية المختارة.", Toast.LENGTH_LONG).show();
                render();
            });
        });
    }

    private ImportedSkillData fetchSkillFromUrl(String source) throws Exception {
        URL input = new URL(source);
        if (!"https".equalsIgnoreCase(input.getProtocol())) throw new IllegalArgumentException("يُسمح بروابط HTTPS فقط.");
        String host = input.getHost().toLowerCase(Locale.ROOT);
        String path = input.getPath() == null ? "" : input.getPath();
        if (host.equals("github.com") || host.equals("www.github.com")) {
            String[] parts = path.split("/");
            if (parts.length >= 5 && ("blob".equals(parts[3]) || "raw".equals(parts[3]))) {
                String rawPath = "";
                if ("blob".equals(parts[3])) rawPath = "https://raw.githubusercontent.com/" + parts[1] + "/" + parts[2] + "/" + joinPath(parts, 4);
                else rawPath = "https://raw.githubusercontent.com/" + parts[1] + "/" + parts[2] + "/" + joinPath(parts, 4);
                String content = downloadSkillText(rawPath, 500000);
                return new ImportedSkillData(skillTitleFromUrl(rawPath), "مهارة مستوردة من GitHub", normalizeRemoteContent(content));
            }
            if (parts.length >= 3 && !parts[1].isEmpty() && !parts[2].isEmpty()) {
                String owner = parts[1], repo = parts[2];
                String repoJson = downloadSkillText("https://api.github.com/repos/" + owner + "/" + repo, 500000);
                String branch = new JSONObject(repoJson).optString("default_branch", "main");
                String treeJson = downloadSkillText("https://api.github.com/repos/" + owner + "/" + repo + "/git/trees/" + branch + "?recursive=1", 1500000);
                JSONArray tree = new JSONObject(treeJson).optJSONArray("tree");
                if (tree == null) throw new IllegalStateException("لم أجد قائمة ملفات المستودع.");
                ArrayList<String> candidates = new ArrayList<>();
                for (int i = 0; i < tree.length(); i++) {
                    JSONObject entry = tree.optJSONObject(i);
                    if (entry == null || !"blob".equals(entry.optString("type"))) continue;
                    String filePath = entry.optString("path", "");
                    String lower = filePath.toLowerCase(Locale.ROOT);
                    String basename = lower.substring(lower.lastIndexOf('/') + 1);
                    if (basename.equals("skill.md") || basename.equals("agents.md") || basename.equals("instructions.md")
                            || basename.equals("readme.md") || basename.endsWith(".skill.json")) candidates.add(filePath);
                }
                candidates.sort((a, b) -> Integer.compare(skillFileScore(b), skillFileScore(a)));
                if (candidates.isEmpty()) throw new IllegalStateException("لم أجد SKILL.md أو README.md أو ملف تعليمات قابلًا للاستيراد.");
                StringBuilder combined = new StringBuilder();
                int fetched = 0;
                for (String candidate : candidates) {
                    if (fetched >= 3 || combined.length() >= 30000) break;
                    String rawUrl = "https://raw.githubusercontent.com/" + owner + "/" + repo + "/" + branch + "/" + candidate.replace(" ", "%20");
                    try {
                        String content = downloadSkillText(rawUrl, 500000);
                        if (content.trim().isEmpty()) continue;
                        combined.append("\\n\\n--- ملف المهارة: ").append(candidate).append(" ---\\n")
                                .append(normalizeRemoteContent(content));
                        fetched++;
                    } catch (Exception ignored) { }
                }
                if (combined.length() == 0) throw new IllegalStateException("وجدت ملفات مرشحة لكن تعذر تنزيل محتواها.");
                String title = repo.replace('-', ' ').replace('_', ' ');
                return new ImportedSkillData(title, "تعليمات مستوردة من المستودع العام " + owner + "/" + repo, combined.substring(0, Math.min(combined.length(), 30000)));
            }
        }
        String content = downloadSkillText(source, 500000);
        if (content.trim().isEmpty()) throw new IllegalStateException("المحتوى المسترجع فارغ.");
        return new ImportedSkillData(skillTitleFromUrl(source), "مهارة مستوردة من رابط HTTPS", normalizeRemoteContent(content));
    }

    private static final class ImportedSkillData {
        final String title;
        final String description;
        final String content;
        ImportedSkillData(String title, String description, String content) { this.title = title; this.description = description; this.content = content; }
    }

    private static int skillFileScore(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        int score = name.equals("skill.md") ? 100 : name.endsWith(".skill.json") ? 95 : name.equals("instructions.md") ? 88
                : name.equals("agents.md") ? 84 : name.equals("readme.md") ? 40 : 0;
        if (lower.contains("/skills/") || lower.startsWith("skills/")) score += 30;
        if (lower.contains("/examples/") || lower.contains("/test/")) score -= 20;
        return score;
    }

    private static String joinPath(String[] parts, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < parts.length; i++) { if (out.length() > 0) out.append('/'); out.append(parts[i]); }
        return out.toString();
    }

    private static String skillTitleFromUrl(String source) {
        try {
            URL url = new URL(source);
            String path = url.getPath();
            if (path == null || path.trim().isEmpty()) return "مهارة مستوردة";
            String name = path.substring(path.lastIndexOf('/') + 1).replace('-', ' ').replace('_', ' ');
            return name.replaceFirst("\\.[^.]+$", "").trim().isEmpty() ? "مهارة مستوردة" : name.replaceFirst("\\.[^.]+$", "").trim();
        } catch (Exception ignored) { return "مهارة مستوردة"; }
    }

    private static String normalizeRemoteContent(String raw) {
        if (raw == null) return "";
        String value = raw.replace("\r\n", "\n").replace('\r', '\n');
        if (value.trim().startsWith("<!DOCTYPE html") || value.trim().startsWith("<html") || value.contains("<html")) {
            value = value.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                    .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                    .replaceAll("(?s)<[^>]+>", " ");
            value = android.text.Html.fromHtml(value, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        }
        value = value.replaceAll("\\n{4,}", "\n\n\n").trim();
        return value.length() > 30000 ? value.substring(0, 30000) : value;
    }

    private static String downloadSkillText(String urlText, int maxBytes) throws Exception {
        URL url = new URL(urlText);
        if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IllegalArgumentException("الرابط النهائي يجب أن يستخدم HTTPS.");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "Botna-Android/0.23.6");
            connection.setRequestProperty("Accept", "application/vnd.github+json,text/markdown,text/plain,application/json,text/html,*/*");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + " أثناء تنزيل المهارة.");
            URL finalUrl = connection.getURL();
            if (!"https".equalsIgnoreCase(finalUrl.getProtocol())) throw new IllegalStateException("تم رفض إعادة توجيه غير مشفرة.");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count; int total = 0;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > maxBytes) throw new IllegalStateException("حجم محتوى المهارة أكبر من الحد الآمن.");
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }

    private void importSkillFile(String path) {
        String raw = BotStudioStore.readAttachmentText(path);
        if (raw.isEmpty()) { Toast.makeText(this, "لم أستطع استخراج نص من هذا الملف. استخدم JSON أو TXT أو Markdown أو DOCX أو XLSX أو PPTX.", Toast.LENGTH_LONG).show(); return; }
        String name = BotStudioStore.attachmentName(path).replaceFirst("\\.[^.]+$", "");
        String title = name; String description = "مهارة مستوردة من ملف"; String guidance = raw;
        try {
            JSONObject obj = new JSONObject(raw);
            title = obj.optString("title", obj.optString("name", title));
            description = obj.optString("description", description);
            guidance = obj.optString("guidance", obj.optString("instructions", obj.optString("prompt", raw)));
        } catch (Exception ignored) { }
        BotStudioStore.Skill skill = store.saveCustomSkill(title, description, guidance);
        store.setSkillEnabled(selectedBotId, skill.id, true);
        Toast.makeText(this, "تم استيراد المهارة وتفعيلها للشخصية المختارة.", Toast.LENGTH_LONG).show();
        render();
    }

    private void showSkillEditor() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(4), dp(4), dp(4), dp(4));
        EditText title = field("اسم المهارة", "مهارة جديدة"); EditText description = field("وصف قصير", "مهارة مخصصة للمستخدم");
        EditText guidance = field("تعليمات المهارة", "صف ما ينبغي للبوت فعله، والقيود التي يجب اتباعها."); guidance.setMinLines(4); guidance.setMaxLines(10);
        form.addView(title, matchWrap()); form.addView(description, top(matchWrap(), 6)); form.addView(guidance, top(matchWrap(), 6));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("إنشاء مهارة") .setView(form).setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (title.getText().toString().trim().isEmpty() || guidance.getText().toString().trim().isEmpty()) { Toast.makeText(this, "اسم المهارة وتعليماتها مطلوبان.", Toast.LENGTH_SHORT).show(); return; }
            BotStudioStore.Skill skill = store.saveCustomSkill(title.getText().toString(), description.getText().toString(), guidance.getText().toString());
            store.setSkillEnabled(selectedBotId, skill.id, true); dialog.dismiss(); render();
        })); dialog.show();
    }

    private void showThinkingDialog() {
        String current = BotStudioStore.thinkingLevel(this);
        String[] levels = {"max", "high", "medium", "low", "auto"};
        String[] labels = {"الأقصى", "مرتفع", "متوسط", "منخفض", "تلقائي حسب المزوّد"};
        int selected = 0; for (int i = 0; i < levels.length; i++) if (levels[i].equals(current)) selected = i;
        final int[] selection = {selected};
        new AlertDialog.Builder(this).setTitle("مستوى التفكير الحقيقي")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> selection[0] = which)
                .setNeutralButton("فحص دعم النموذج", (dialog, which) -> new AlertDialog.Builder(this).setTitle("دعم النموذج الحالي").setMessage(BotnaAICompiler.thinkingSupportDescription(this)).setPositiveButton("حسنًا", null).show())
                .setNegativeButton("إلغاء", null).setPositiveButton("تطبيق", (dialog, which) -> {
                    BotStudioStore.setThinkingLevel(this, levels[selection[0]]);
                    Toast.makeText(this, BotnaAICompiler.thinkingSupportDescription(this), Toast.LENGTH_LONG).show(); render();
                }).show();
    }

    private String thinkingLevelLabel(String level) {
        switch (level) { case "high": return "مرتفع"; case "medium": return "متوسط"; case "low": return "منخفض"; case "auto": return "تلقائي"; default: return "الأقصى"; }
    }

    private void showAttachmentPreview(String path) {
        Bitmap bitmap = BotStudioStore.loadAttachmentPreview(this, path, 3200);
        if (bitmap == null) { shareAttachment(path); return; }
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView image = new ImageView(this); image.setImageBitmap(bitmap); image.setScaleType(ImageView.ScaleType.FIT_CENTER); image.setBackgroundColor(Color.BLACK);
        image.setContentDescription(BotStudioStore.attachmentName(path));
        image.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(image); dialog.setOnDismissListener(d -> { if (!bitmap.isRecycled()) bitmap.recycle(); });
        Window window = dialog.getWindow(); if (window != null) { window.setLayout(-1, -1); window.setBackgroundDrawableResource(android.R.color.black); }
        dialog.show(); if (dialog.getWindow() != null) dialog.getWindow().setLayout(-1, -1);
    }

    private void shareAttachment(String path) {
        try {
            File file = new File(path);
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            String mime = BotStudioStore.attachmentMime(path);
            Intent open = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(open, "فتح المرفق"));
        } catch (Exception e) {
            try {
                File file = new File(path);
                Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
                Intent share = new Intent(Intent.ACTION_SEND).setType(BotStudioStore.attachmentMime(path)).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(share, "مشاركة المرفق"));
            } catch (Exception ignored) { Toast.makeText(this, "تعذر فتح الملف؛ قد لا يوجد تطبيق يدعم هذا النوع.", Toast.LENGTH_LONG).show(); }
        }
    }

    private void showBotEditor(JSONObject existing) {
        boolean edit = existing != null;
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(4), dp(4), dp(4), dp(4));
        EditText name = field("اسم البوت", edit ? existing.optString("name") : "");
        EditText icon = field("أيقونة/رمز", edit ? existing.optString("icon", "🤖") : "🤖");
        EditText role = field("الدور الوظيفي", edit ? existing.optString("role") : "مساعد عام");
        EditText persona = field("تعليمات الشخصية والسلوك", edit ? existing.optString("persona") : "افهم اللهجة الفلسطينية، لا تخمن، واذكر ما لم تتأكد منه."); persona.setMinLines(3); persona.setMaxLines(7);
        form.addView(name, matchWrap()); form.addView(icon, top(matchWrap(), 6)); form.addView(role, top(matchWrap(), 6)); form.addView(persona, top(matchWrap(), 6));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(edit ? "تعديل الشخصية" : "إنشاء بوت جديد").setView(form)
                .setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("اكتب اسم البوت"); return; }
            if (edit) store.updateBot(existing.optString("id"), name.getText().toString(), icon.getText().toString(), role.getText().toString(), persona.getText().toString());
            else store.createBot(name.getText().toString(), icon.getText().toString(), role.getText().toString(), persona.getText().toString());
            ensureSelectedBot(); dialog.dismiss(); render();
        }));
        dialog.show();
    }

    private void showTaskDialog() {
        EditText goal = field("اكتب الهدف النهائي", ""); goal.setMinLines(4); goal.setMaxLines(10);
        CheckBox confirm = new CheckBox(this); confirm.setText("أوافق على قراءة لقطات الشاشة والتحكم بالواجهة ضمن الهدف، وسأراجع الإجراءات الحساسة."); confirm.setTextColor(MUTED);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(6), dp(4), dp(6), dp(4)); root.addView(goal, matchWrap()); root.addView(confirm, top(matchWrap(), 6));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("إنشاء مهمة للوكيل").setView(root).setNegativeButton("إلغاء", null).setPositiveButton("بدء", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String text = goal.getText().toString().trim();
            if (text.isEmpty()) { goal.setError("اكتب الهدف أولًا"); return; }
            if (!confirm.isChecked()) { Toast.makeText(this, "أكد موافقتك على قراءة الشاشة والتحكم بالواجهة.", Toast.LENGTH_LONG).show(); return; }
            if (!BotnaAICompiler.isConfigured(this) && !BotnaAccessibilityService.isLocalSpeedTestRequest(text)) { Toast.makeText(this, "اضبط المزوّد الحالي من إعدادات الذكاء الاصطناعي أولًا.", Toast.LENGTH_LONG).show(); return; }
            String id = BotnaAgentStore.createTask(this, text); dialog.dismiss(); dispatchTask(text, id);
        })); dialog.show();
    }

    private void dispatchTask(String goal, String id) {
        if (!accessibilityEnabled() || !BotnaAccessibilityService.isServiceActuallyConnected(this)) {
            getSharedPreferences(BotnaAICompiler.PREFS, MODE_PRIVATE).edit().putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK, goal)
                    .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK_ID, id).putBoolean(BotnaAccessibilityService.PREF_PENDING_SMART_RESUME, false).apply();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, !accessibilityEnabled() ? "تم حفظ المهمة. فعّل خدمة Botna ثم ستبدأ." : "خدمة Botna غير متصلة فعليًا الآن. حفظت المهمة بانتظار إعادة اتصال الخدمة؛ لم يبدأ التحكم بعد.", Toast.LENGTH_LONG).show(); return;
        }
        sendBroadcast(new Intent(BotnaAccessibilityService.ACTION_START_SMART_TASK).setPackage(getPackageName()).putExtra("task", goal).putExtra("agentTaskId", id));
        Toast.makeText(this, "أُرسلت المهمة إلى الوكيل.", Toast.LENGTH_SHORT).show();
    }

    private void resumeCheckpoint(JSONObject cp) {
        String id = cp.optString("taskId", ""), goal = cp.optString("goal", "");
        if (id.isEmpty() || goal.isEmpty()) { Toast.makeText(this, "نقطة الاستئناف غير مكتملة؛ لم يبدأ التنفيذ.", Toast.LENGTH_LONG).show(); return; }
        String botId = BotStudioStore.botForTask(this, id);
        if (botId.isEmpty()) botId = selectedBotId;
        if (!accessibilityEnabled() || !BotnaAccessibilityService.isServiceActuallyConnected(this)) {
            getSharedPreferences(BotnaAICompiler.PREFS, MODE_PRIVATE).edit()
                    .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK, goal)
                    .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK_ID, id)
                    .putBoolean(BotnaAccessibilityService.PREF_PENDING_SMART_RESUME, true).apply();
            String reason = !accessibilityEnabled()
                    ? "فشل الاستئناف الآن: خدمة إمكانية الوصول غير مفعلة. حفظت الطلب بانتظار تفعيلها؛ لم يتم التحكم بالهاتف ولم أحصل على لقطة شاشة."
                    : "خدمة إمكانية الوصول مفعلة في إعدادات أندرويد لكنها غير متصلة فعليًا الآن. حفظت طلب الاستئناف لمحاولة واحدة عند إعادة اتصال الخدمة؛ لا توجد لقطة شاشة حية مؤكدة حاليًا.";
            if (!botId.isEmpty()) store.addMessage(botId, "assistant", "⚠️ لم يبدأ الاستئناف بعد\n" + reason, "", BotnaAICompiler.model(this));
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show();
            try { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); } catch (Exception ignored) { }
            if (botId.equals(selectedBotId) && "chat".equals(section)) render();
            return;
        }
        if (!botId.isEmpty()) store.addMessage(botId, "assistant", "طلبت استئناف المهمة: " + goal
                + "\nحالة الخدمة: متصلة. لم أؤكد رؤية الشاشة بعد؛ سيبدأ الاستئناف فقط إذا نجح التقاط لقطة جديدة.", "", BotnaAICompiler.model(this));
        getSharedPreferences(BotnaAICompiler.PREFS, MODE_PRIVATE).edit()
                .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK, goal)
                .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK_ID, id)
                .putBoolean(BotnaAccessibilityService.PREF_PENDING_SMART_RESUME, true).apply();
        sendBroadcast(new Intent(BotnaAccessibilityService.ACTION_RESUME_SMART_TASK).setPackage(getPackageName()).putExtra("task", goal).putExtra("agentTaskId", id));
        Toast.makeText(this, "أُرسل طلب الاستئناف. سيُبلغك الوكيل بنجاح أو فشل لقطة الشاشة الفعلية.", Toast.LENGTH_LONG).show();
        if (botId.equals(selectedBotId) && "chat".equals(section)) render();
    }

    private boolean accessibilityEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabled != null && enabled.contains(new ComponentName(this, BotnaAccessibilityService.class).flattenToString());
    }

    private void exportChat() {
        JSONArray messages = store.messages(selectedBotId, 300);
        String payload;
        try {
            payload = messages.toString(2);
        } catch (org.json.JSONException e) {
            Toast.makeText(this, "تعذر تجهيز ملف المحادثة للتصدير.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("application/json");
        send.putExtra(Intent.EXTRA_TEXT, payload);
        try {
            startActivity(Intent.createChooser(send, "تصدير محادثة البوت"));
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح خيارات التصدير.", Toast.LENGTH_SHORT).show();
        }
    }

    private EditText field(String hint, String value) {
        EditText e = new EditText(this); e.setTextColor(TEXT); e.setHintTextColor(MUTED); e.setHint(hint); e.setText(value); e.setTextSize(14);
        e.setPadding(dp(10), dp(8), dp(10), dp(8)); e.setBackground(shape(FIELD, CYAN, 1, 10)); e.setSingleLine(false); return e;
    }
    private static final class CappedScrollView extends ScrollView {
        private final int maxHeightPx;
        CappedScrollView(Context context, int maxHeightPx) {
            super(context);
            this.maxHeightPx = Math.max(1, maxHeightPx);
        }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int cappedHeightSpec = View.MeasureSpec.makeMeasureSpec(maxHeightPx, View.MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, cappedHeightSpec);
        }
    }

    private LinearLayout card() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(12), dp(12), dp(12), dp(12)); l.setBackground(shape(CARD, Color.rgb(34, 128, 148), 1, 18)); return l; }
    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setPadding(dp(6), dp(4), dp(6), dp(4));
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, glassButtonShape(true));
        states.addState(new int[]{android.R.attr.state_enabled}, glassButtonShape(false));
        states.addState(new int[]{}, glassButtonShape(false));
        b.setBackground(states);
        b.setElevation(dp(2));
        return b;
    }

    private GradientDrawable glassButtonShape(boolean pressed) {
        int top = pressed ? Color.argb(174, 40, 194, 224) : Color.argb(112, 24, 130, 170);
        int bottom = pressed ? Color.argb(150, 12, 66, 101) : Color.argb(72, 7, 31, 54);
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        glass.setCornerRadius(dp(18));
        glass.setStroke(dp(1), Color.argb(220, 43, 211, 236));
        return glass;
    }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.RIGHT); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private View divider() { View v = new View(this); v.setBackgroundColor(Color.rgb(31, 62, 76)); v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1))); return v; }
    private GradientDrawable shape(int fill, int stroke, float width, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(Math.max(1, Math.round(width))), stroke); return g; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams top(LinearLayout.LayoutParams lp, int margin) { lp.topMargin = dp(margin); return lp; }
}
