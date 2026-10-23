package com.botna.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
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
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
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
        if (section.equals(key) || ("chat".equals(section) && "bots".equals(key))) b.setBackground(shape(Color.rgb(16, 78, 102), CYAN, 1.4f, 14));
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
        addSwitch(panel, "اللقطات الذكية عند تغير المحتوى", "تسجل إشارة فقط عندما يختلف المحتوى المرئي بوضوح عن العينة السابقة.", BotStudioStore.smartSnapshotsEnabled(this), BotStudioStore::setSmartSnapshotsEnabled);
        addSwitch(panel, "حفظ لقطة تحقق بعد كل إجراء", "يحتفظ بسجل لقطة بعد الإجراء عندما تكون لقطات التشخيص مفعّلة؛ ولا يعطّل اللقطات الضرورية لفهم الشاشة أثناء المهمة.", BotStudioStore.captureAfterActionEnabled(this), BotStudioStore::setCaptureAfterActionEnabled);
        addSwitch(panel, "حفظ لقطة عند فشل الخطوة", "يحتفظ بلقطة تشخيص عند الإخفاق لتوضيح الموضع الذي توقفت عنده المهمة.", BotStudioStore.captureOnFailureEnabled(this), BotStudioStore::setCaptureOnFailureEnabled);
        TextView status = text(BotnaVisionEngine.status(this), 13, CYAN, false); content.addView(status, top(matchWrap(), 8));
        Button test = button("اختبار جاهزية الرؤية"); content.addView(test, top(matchWrap(), 8));
        test.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("حالة محرك الرؤية").setMessage(BotnaVisionEngine.status(this)
                + "\n\nالمسارات المتاحة: شجرة عناصر إمكانية الوصول، مطابقة القوالب عبر OpenCV، والتحليل البصري عبر المزوّد إذا كان النموذج يدعم الصور. تستخدم هذه النسخة مسار الرؤية الخفيف دون تنزيل نماذج محلية إضافية.")
                .setPositiveButton("حسنًا", null).show());
        LinearLayout skillsActions = new LinearLayout(this); skillsActions.setOrientation(LinearLayout.HORIZONTAL); skillsActions.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams skillActionsLp = top(matchWrap(), 9); panel.addView(skillsActions, skillActionsLp);
        Button importSkill = button("⇧"); importSkill.setTextSize(23); importSkill.setContentDescription("استيراد مهارة من ملف"); importSkill.setBackground(shape(Color.rgb(8, 27, 40), Color.rgb(58, 153, 178), 1.2f, 28));
        Button createSkill = button("＋"); createSkill.setTextSize(23); createSkill.setContentDescription("إنشاء مهارة جديدة"); createSkill.setBackground(shape(Color.rgb(8, 27, 40), Color.rgb(58, 153, 178), 1.2f, 28));
        skillsActions.addView(importSkill, new LinearLayout.LayoutParams(0, dp(48), 1)); LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(0, dp(48), 1); createLp.leftMargin = dp(8); skillsActions.addView(createSkill, createLp);
        importSkill.setOnClickListener(v -> selectSkillFile()); createSkill.setOnClickListener(v -> showSkillEditor());
        LinearLayout safety = card(); content.addView(safety, top(matchWrap(), 10));
        safety.addView(text("الأمان والتحقق الحرج", 17, TEXT, true), matchWrap());
        addSwitch(safety, "طلب موافقة على العمليات الحساسة", "يوقف الوكيل قبل إجراءات الدفع والحذف والإرسال والنشر التي يمكن التعرف عليها من وصف الإجراء.", BotStudioStore.safetySandboxEnabled(this), BotStudioStore::setSafetySandboxEnabled);
        safety.addView(text("نقاط الاستئناف تحفظ هدف المهمة وسجل الأفعال بعد كل خطوة مكتملة. يمكن استئناف التخطيط من الشاشة الحالية؛ لا يمكن استعادة حالة التطبيق الخارجي إذا تغيّرت خارج Botna.", 12, MUTED, false), top(matchWrap(), 8));
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
        ScrollView chatScroll = new ScrollView(this); chatScroll.setFillViewport(false); chatScroll.setSmoothScrollingEnabled(true); chatScroll.setNestedScrollingEnabled(true); chatScroll.setVerticalScrollBarEnabled(true);
        LinearLayout messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); messages.setPadding(0, dp(3), 0, dp(5)); chatScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams chatScrollLp = new LinearLayout.LayoutParams(-1, dp(420)); chatScrollLp.topMargin = dp(8); panel.addView(chatScroll, chatScrollLp);
        chatScroll.setOnTouchListener((v, event) -> {
            if (event.getAction() == android.view.MotionEvent.ACTION_MOVE) chatScroll.getParent().requestDisallowInterceptTouchEvent(true);
            if (event.getAction() == android.view.MotionEvent.ACTION_UP || event.getAction() == android.view.MotionEvent.ACTION_CANCEL) chatScroll.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
        JSONArray history = store.messages(selectedBotId, 120);
        int renderedPreviews = 0;
        for (int i = 0; i < history.length(); i++) {
            JSONObject msg = history.optJSONObject(i); if (msg == null) continue;
            boolean user = "user".equals(msg.optString("role"));
            LinearLayout messageCard = new LinearLayout(this); messageCard.setOrientation(LinearLayout.VERTICAL); messageCard.setPadding(dp(10), dp(8), dp(10), dp(8));
            messageCard.setBackground(shape(user ? Color.rgb(14, 54, 79) : FIELD, Color.rgb(38, 80, 100), 1, 12));
            LinearLayout.LayoutParams cardLp = matchWrap(); cardLp.topMargin = dp(5); messages.addView(messageCard, cardLp);
            TextView bubble = text((user ? "أنت: " : bot.optString("name", "البوت") + ": ") + msg.optString("body"), 13, user ? TEXT : MUTED, false);
            bubble.setAutoLinkMask(Linkify.WEB_URLS); bubble.setLinkTextColor(CYAN); bubble.setMovementMethod(LinkMovementMethod.getInstance());
            messageCard.addView(bubble, matchWrap());
            String attachment = msg.optString("image", "");
            if (!attachment.isEmpty() && new File(attachment).isFile()) {
                if ((BotStudioStore.isImageAttachment(attachment) || BotStudioStore.isPdfAttachment(attachment)) && renderedPreviews < 12) {
                    renderedPreviews++;
                    Bitmap preview = BotStudioStore.loadAttachmentPreview(this, attachment, 460);
                    if (preview != null) {
                        ImageView thumbnail = new ImageView(this); thumbnail.setImageBitmap(preview); thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
                        thumbnail.setAdjustViewBounds(true); thumbnail.setMaxHeight(dp(230)); thumbnail.setBackground(shape(FIELD, CYAN, 1, 10));
                        messageCard.addView(thumbnail, top(matchWrap(), 6));
                        thumbnail.setOnClickListener(v -> showAttachmentPreview(attachment));
                        if (BotStudioStore.isPdfAttachment(attachment)) {
                            Button openOriginal = button("فتح ملف PDF الأصلي");
                            messageCard.addView(openOriginal, top(matchWrap(), 5));
                            openOriginal.setOnClickListener(v -> shareAttachment(attachment));
                        }
                    } else {
                        Button openOriginal = button("📄  فتح الملف الأصلي: " + BotStudioStore.attachmentName(attachment));
                        messageCard.addView(openOriginal, top(matchWrap(), 6));
                        openOriginal.setOnClickListener(v -> shareAttachment(attachment));
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
        }
        chatStatus = text(chatBusy ? "جارٍ الحصول على رد البوت..." : (attachedFilePath.isEmpty() ? "أرفق صورة أو ملفًا أو استخدم الميكروفون." : "المرفق: " + attachedFileName), 11, CYAN, false);
        panel.addView(chatStatus, top(matchWrap(), 5));
        chatInput = new EditText(this); chatInput.setTextColor(TEXT); chatInput.setHintTextColor(MUTED); chatInput.setTextSize(14); chatInput.setGravity(Gravity.TOP | Gravity.START);
        chatInput.setMinLines(2); chatInput.setMaxLines(6); chatInput.setHint("احكي مع البوت باللهجة اللي بتحبها..."); chatInput.setPadding(dp(10), dp(10), dp(10), dp(10)); chatInput.setBackground(shape(FIELD, CYAN, 1, 20));
        panel.addView(chatInput, top(matchWrap(), 6));
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); controls.setGravity(Gravity.CENTER_VERTICAL); panel.addView(controls, top(matchWrap(), 6));
        sendChatButton = button("↑"); sendChatButton.setTextSize(25); sendChatButton.setContentDescription("إرسال إلى البوت"); sendChatButton.setBackground(shape(Color.rgb(15, 70, 100), CYAN, 1.2f, 28));
        Button voice = button("🎙"); voice.setTextSize(20); voice.setContentDescription("إملاء صوتي"); voice.setBackground(shape(Color.rgb(8, 25, 36), Color.rgb(69, 137, 163), 1, 28));
        Button attach = button("＋"); attach.setTextSize(24); attach.setContentDescription("إرفاق ملف أو صورة"); attach.setBackground(shape(Color.rgb(8, 25, 36), Color.rgb(69, 137, 163), 1, 28));
        sendChatButton.setEnabled(!chatBusy); attach.setEnabled(!chatBusy); voice.setEnabled(!chatBusy);
        controls.addView(sendChatButton, new LinearLayout.LayoutParams(dp(50), dp(48)));
        LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, dp(1), 1f); controls.addView(new View(this), spacerLp);
        LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(dp(50), dp(48)); voiceLp.leftMargin = dp(7); controls.addView(voice, voiceLp);
        LinearLayout.LayoutParams attachLp = new LinearLayout.LayoutParams(dp(50), dp(48)); attachLp.leftMargin = dp(7); controls.addView(attach, attachLp);
        attach.setOnClickListener(v -> selectFile()); voice.setOnClickListener(v -> startVoiceInput()); sendChatButton.setOnClickListener(v -> sendChat());
        chatScroll.post(() -> chatScroll.smoothScrollTo(0, messages.getHeight()));
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
        final String priorContext = conversationContext(store.messages(requestBotId, 24));
        attachedFilePath = ""; attachedFileName = ""; attachedFileMime = "";
        store.addMessage(requestBotId, "user", message, filePath, "");
        if (chatInput != null) chatInput.setText("");
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
        final String requestTextBase = priorContext + "\n\nالرسالة الحالية للمستخدم: " + message;
        chatBusy = true; render();
        final String modelUsed = BotnaAICompiler.model(this);
        final String thinkingLevel = BotStudioStore.thinkingLevel(this);
        chatExecutor.execute(() -> {
            Bitmap image = null;
            String response;
            try {
                String requestText = requestTextBase;
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
        if (!accessibilityEnabled()) {
            String id = BotnaAgentStore.createTask(this, effectiveGoal);
            BotStudioStore.associateTaskWithBot(this, id, botId);
            getSharedPreferences(BotnaAICompiler.PREFS, MODE_PRIVATE).edit().putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK, effectiveGoal)
                    .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK_ID, id).apply();
            store.addMessage(botId, "assistant", "حُفظت المهمة بانتظار تفعيل خدمة إمكانية الوصول. فعّل الخدمة ثم ستبدأ المهمة.", "", BotnaAICompiler.model(this));
            render(); startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "فعّل خدمة Botna لبدء المهمة. لم يبدأ التحكم بعد.", Toast.LENGTH_LONG).show();
            return;
        }
        String id = BotnaAgentStore.createTask(this, effectiveGoal);
        BotStudioStore.associateTaskWithBot(this, id, botId);
        String taskNote = "بدأ الوكيل تنفيذ المهمة على الهاتف. سيعود بنتيجة مفصلة أو سبب التوقف في هذه المحادثة.\nالهدف: " + goal;
        if (attachmentPath != null && !attachmentPath.isEmpty()) taskNote += "\nالمرفق المرتبط بالطلب: " + BotStudioStore.attachmentName(attachmentPath);
        store.addMessage(botId, "assistant", taskNote, "", BotnaAICompiler.model(this));
        sendBroadcast(new Intent(BotnaAccessibilityService.ACTION_START_SMART_TASK).setPackage(getPackageName()).putExtra("task", effectiveGoal).putExtra("agentTaskId", id));
        Toast.makeText(this, "بدأت المهمة. سيواصل الوكيل العمل في الخلفية.", Toast.LENGTH_SHORT).show();
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
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT); pick.addCategory(Intent.CATEGORY_OPENABLE); pick.setType("*/*");
        startActivityForResult(pick, 4111);
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
        if (!accessibilityEnabled()) {
            getSharedPreferences(BotnaAICompiler.PREFS, MODE_PRIVATE).edit().putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK, goal)
                    .putString(BotnaAccessibilityService.PREF_PENDING_SMART_TASK_ID, id).apply();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "تم حفظ المهمة. فعّل خدمة Botna ثم ستبدأ المهمة.", Toast.LENGTH_LONG).show(); return;
        }
        sendBroadcast(new Intent(BotnaAccessibilityService.ACTION_START_SMART_TASK).setPackage(getPackageName()).putExtra("task", goal).putExtra("agentTaskId", id));
        Toast.makeText(this, "أُرسلت المهمة إلى الوكيل.", Toast.LENGTH_SHORT).show();
    }

    private void resumeCheckpoint(JSONObject cp) {
        String id = cp.optString("taskId", ""), goal = cp.optString("goal", "");
        if (id.isEmpty() || goal.isEmpty()) { Toast.makeText(this, "نقطة الاستئناف غير مكتملة.", Toast.LENGTH_LONG).show(); return; }
        if (!accessibilityEnabled()) { Toast.makeText(this, "فعّل خدمة إمكانية الوصول قبل الاستئناف.", Toast.LENGTH_LONG).show(); startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return; }
        sendBroadcast(new Intent(BotnaAccessibilityService.ACTION_RESUME_SMART_TASK).setPackage(getPackageName()).putExtra("task", goal).putExtra("agentTaskId", id));
        Toast.makeText(this, "تم إرسال طلب الاستئناف. سيعيد الوكيل قراءة الشاشة الحالية.", Toast.LENGTH_LONG).show();
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
    private LinearLayout card() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); l.setPadding(dp(12), dp(12), dp(12), dp(12)); l.setBackground(shape(CARD, Color.rgb(34, 128, 148), 1, 18)); return l; }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setTextSize(13); b.setTextColor(TEXT); b.setAllCaps(false); b.setTypeface(Typeface.DEFAULT, Typeface.BOLD); b.setPadding(dp(6), dp(4), dp(6), dp(4)); b.setBackground(shape(Color.rgb(14, 67, 94), CYAN, 1, 13)); return b; }
    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.RIGHT); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private View divider() { View v = new View(this); v.setBackgroundColor(Color.rgb(31, 62, 76)); v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1))); return v; }
    private GradientDrawable shape(int fill, int stroke, float width, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(fill); g.setCornerRadius(dp(radius)); g.setStroke(dp(Math.max(1, Math.round(width))), stroke); return g; }
    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }
    private LinearLayout.LayoutParams top(LinearLayout.LayoutParams lp, int margin) { lp.topMargin = dp(margin); return lp; }
}
