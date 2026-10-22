package com.botna.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
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
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
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
    private String attachedImagePath = "";
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
        if (requestCode != 4108 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        String saved = BotStudioStore.persistImage(this, data.getData());
        if (saved.isEmpty()) Toast.makeText(this, "تعذر حفظ الصورة المختارة.", Toast.LENGTH_LONG).show();
        else {
            attachedImagePath = saved;
            if (chatStatus != null) chatStatus.setText("تم إرفاق صورة للمحادثة: " + new File(saved).getName());
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
        panel.addView(text("محرك الرؤية والبث الذكي", 18, TEXT, true), matchWrap());
        panel.addView(text("الالتقاط المستمر اختياري ويُفعّل فقط بعد اختيارك. يحلل Botna تغيّرًا بصريًا خفيفًا محليًا، بينما تبقى شجرة إمكانية الوصول ومطابقة OpenCV مسار الرجوع.", 12, MUTED, false), top(matchWrap(), 6));
        addSwitch(panel, "مراقبة تغيرات الشاشة", "تلتقط الخدمة صورة بعد تغيّرات واجهة مهمة مع تهدئة زمنية للحفاظ على البطارية.", BotStudioStore.visionStreamEnabled(this), BotStudioStore::setVisionStreamEnabled);
        addSwitch(panel, "اللقطات الذكية", "تجنب الالتقاط المتكرر؛ تسجل إشارة عندما يختلف المحتوى المرئي عن اللقطة السابقة.", BotStudioStore.smartSnapshotsEnabled(this), BotStudioStore::setSmartSnapshotsEnabled);
        addSwitch(panel, "السماح بمحرك OCR محلي", "تفضيل OCR إذا توفر نموذج عربي محلي متوافق. لا يوجد نموذج OCR عربي مضمّن في هذه الحزمة، لذا يظل مسار الرجوع فعالًا.", BotStudioStore.ocrEnabled(this), BotStudioStore::setOcrEnabled);
        addSwitch(panel, "السماح بمحرك YOLO المحلي", "تفعيل خيار التوافق مع كاشف محلي عند تثبيته لاحقًا. وزن YOLO غير مضمن حاليًا؛ فشل التحميل لا يوقف الوكيل.", BotStudioStore.yoloEnabled(this), BotStudioStore::setYoloEnabled);
        TextView status = text(BotnaVisionEngine.status(this), 13, CYAN, false); content.addView(status, top(matchWrap(), 8));
        Button test = button("اختبار جاهزية الرؤية"); content.addView(test, top(matchWrap(), 8));
        test.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("حالة محرك الرؤية").setMessage(BotnaVisionEngine.status(this)
                + "\n\nمصدر تحديد العناصر الفعلي المتاح الآن: شجرة عناصر إمكانية الوصول، مطابقة القوالب عبر OpenCV، وتحليل الصورة بواسطة المزوّد إذا كانت إعداداته تدعم الصور. لا يوجد وزن YOLO/OCR عربي مستقل داخل هذه الحزمة.")
                .setPositiveButton("حسنًا", null).show());
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
        sw.setOnCheckedChangeListener((button, value) -> { setter.set(this, value); if ((title.contains("OCR") || title.contains("YOLO")) && value) Toast.makeText(this, "لم يتم تضمين وزن محلي لهذا المحرك؛ يستخدم Botna مسار الرجوع تلقائيًا.", Toast.LENGTH_LONG).show(); });
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
        panel.addView(text("ترسل رسائلك والصور التي تختارها إلى المزوّد المحدد، وتُحفظ المحادثة محليًا داخل التطبيق.", 11, MUTED, false), top(matchWrap(), 5));
        Button profile = button("تعديل الشخصية"); panel.addView(profile, top(matchWrap(), 7)); profile.setOnClickListener(v -> showBotEditor(bot));
        Button aiSettings = button("إعدادات مزوّد الذكاء الاصطناعي"); panel.addView(aiSettings, top(matchWrap(), 6));
        aiSettings.setOnClickListener(v -> BotnaAICompiler.showSettingsDialog(this, false, this::render));
        JSONArray history = store.messages(selectedBotId, 80);
        ScrollView chatScroll = new ScrollView(this); chatScroll.setFillViewport(false);
        LinearLayout messages = new LinearLayout(this); messages.setOrientation(LinearLayout.VERTICAL); chatScroll.addView(messages, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams chatScrollLp = new LinearLayout.LayoutParams(-1, dp(360)); chatScrollLp.topMargin = dp(8); panel.addView(chatScroll, chatScrollLp);
        for (int i = 0; i < history.length(); i++) {
            JSONObject msg = history.optJSONObject(i); if (msg == null) continue;
            boolean user = "user".equals(msg.optString("role"));
            TextView bubble = text((user ? "أنت: " : "" + bot.optString("name") + ": ") + msg.optString("body")
                    + (msg.optString("image").isEmpty() ? "" : "\n📷 صورة مرفقة: " + new File(msg.optString("image")).getName()), 13, user ? TEXT : MUTED, false);
            bubble.setPadding(dp(10), dp(8), dp(10), dp(8)); bubble.setBackground(shape(user ? Color.rgb(14, 54, 79) : FIELD, Color.rgb(38, 80, 100), 1, 12));
            LinearLayout.LayoutParams bp = matchWrap(); bp.topMargin = dp(5); messages.addView(bubble, bp);
        }
        chatStatus = text(chatBusy ? "جارٍ الحصول على رد البوت..." : (attachedImagePath.isEmpty() ? "اختر رسالة أو أرفق صورة." : "الصورة المرفقة: " + new File(attachedImagePath).getName()), 11, CYAN, false);
        panel.addView(chatStatus, top(matchWrap(), 5));
        chatInput = new EditText(this); chatInput.setTextColor(TEXT); chatInput.setHintTextColor(MUTED); chatInput.setTextSize(14); chatInput.setGravity(Gravity.TOP | Gravity.START);
        chatInput.setMinLines(2); chatInput.setMaxLines(6); chatInput.setHint("احكي مع البوت باللهجة اللي بتحبها..."); chatInput.setPadding(dp(10), dp(10), dp(10), dp(10)); chatInput.setBackground(shape(FIELD, CYAN, 1, 12));
        panel.addView(chatInput, top(matchWrap(), 6));
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); panel.addView(controls, top(matchWrap(), 6));
        Button attach = button("📎 إرفاق صورة"); sendChatButton = button("إرسال إلى الذكاء");
        sendChatButton.setEnabled(!chatBusy); attach.setEnabled(!chatBusy);
        controls.addView(attach, new LinearLayout.LayoutParams(0, dp(44), 1)); LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(0, dp(44), 1); sendLp.leftMargin = dp(6); controls.addView(sendChatButton, sendLp);
        attach.setOnClickListener(v -> selectImage()); sendChatButton.setOnClickListener(v -> sendChat());
        Button runTask = button("تحويل هدف إلى مهمة أتمتة"); panel.addView(runTask, top(matchWrap(), 8)); runTask.setOnClickListener(v -> showTaskDialog());
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendChat() {
        if (chatBusy) return;
        String message = chatInput == null || chatInput.getText() == null ? "" : chatInput.getText().toString().trim();
        if (message.isEmpty() && attachedImagePath.isEmpty()) { Toast.makeText(this, "اكتب رسالة أو أرفق صورة أولًا.", Toast.LENGTH_SHORT).show(); return; }
        if (!BotnaAICompiler.isConfigured(this)) { Toast.makeText(this, "اضبط مزوّد الذكاء الاصطناعي في إعدادات Botna أولًا.", Toast.LENGTH_LONG).show(); return; }
        JSONObject bot = store.bot(selectedBotId); if (bot == null) return;
        String persona = bot.optString("name") + "، الدور: " + bot.optString("role") + "، الشخصية: " + bot.optString("persona") + "\nالمهارات المفعلة:" + store.skillGuidance(selectedBotId);
        final String requestBotId = selectedBotId;
        String imagePath = attachedImagePath; attachedImagePath = "";
        String context = conversationContext(store.messages(requestBotId, 18));
        store.addMessage(requestBotId, "user", message, imagePath);
        chatInput.setText(""); chatBusy = true;
        render();
        chatExecutor.execute(() -> {
            Bitmap image = BotStudioStore.loadImageFile(imagePath, 1440);
            String response;
            try { response = BotnaAICompiler.chat(this, persona, context + "\nالرسالة الحالية: " + message, image); }
            catch (Exception e) { response = "تعذر الحصول على رد: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
            finally { if (image != null && !image.isRecycled()) image.recycle(); }
            final String answer = response == null ? "لم يصل رد نصي من المزوّد." : response;
            runOnUiThread(() -> { chatBusy = false; store.addMessage(requestBotId, "assistant", answer, ""); if (requestBotId.equals(selectedBotId)) render(); });
        });
    }

    private String conversationContext(JSONArray messages) {
        StringBuilder out = new StringBuilder("سياق المحادثة السابقة، للاستخدام في الرد الحالي دون تكرارها:");
        int start = Math.max(0, messages.length() - 12);
        for (int i = start; i < messages.length(); i++) {
            JSONObject m = messages.optJSONObject(i); if (m == null) continue;
            out.append("\n").append("assistant".equals(m.optString("role")) ? "البوت" : "المستخدم").append(": ").append(m.optString("body"));
        }
        return out.toString();
    }

    private void selectImage() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT); pick.addCategory(Intent.CATEGORY_OPENABLE); pick.setType("image/*");
        startActivityForResult(pick, 4108);
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
