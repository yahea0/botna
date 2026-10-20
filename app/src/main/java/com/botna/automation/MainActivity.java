package com.botna.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
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
    private static final String KEY_ONBOARDING_DONE = "onboarding_v1_done";
    private static final String DEVELOPER_NAME = "يحيى النباهين";
    private static final String DEVELOPER_TELEGRAM = "@yahea21";

    private static final int BG = Color.rgb(5, 7, 12);
    private static final int CARD = Color.argb(238, 13, 17, 26);
    private static final int TEXT = Color.rgb(235, 246, 255);
    private static final int MUTED = Color.rgb(150, 174, 195);
    private static final int CYAN = Color.rgb(35, 226, 244);

    private TextView serviceStatus;
    private TextView lastResult;
    private TextView dataSummary;
    private TextView savedScriptsSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        buildInterface();
        new Handler(Looper.getMainLooper()).postDelayed(this::showWelcomeIfNeeded, 320);
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
        BotnaI18n.applyDirection(this, page);
        scroll.addView(page);

        LinearLayout brandBar = new LinearLayout(this);
        brandBar.setOrientation(LinearLayout.HORIZONTAL);
        brandBar.setGravity(Gravity.CENTER_VERTICAL);
        BotnaI18n.applyDirection(this, brandBar);
        ImageView appMark = new ImageView(this);
        appMark.setImageResource(R.mipmap.ic_launcher);
        appMark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brandBar.addView(appMark, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout brandWords = new LinearLayout(this); brandWords.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams brandWordsLp = new LinearLayout.LayoutParams(0, -2, 1f);
        brandWordsLp.leftMargin = dp(9); brandWordsLp.rightMargin = dp(9); brandBar.addView(brandWords, brandWordsLp);
        TextView title = text(BotnaI18n.isEnglish(this) ? "Botna" : "بوتنا  Botna", 28, TEXT, true);
        brandWords.addView(title, matchWrap());
        TextView subtitle = text("منصة أتمتة ذكية ومباشرة لهاتفك", 14, MUTED, false);
        LinearLayout.LayoutParams subLp = matchWrap(); subLp.topMargin = dp(3); brandWords.addView(subtitle, subLp);
        Button languageButton = button(BotnaI18n.isEnglish(this) ? "🌐 English" : "🌐 اللغة");
        languageButton.setTextSize(12); languageButton.setMinWidth(dp(82));
        brandBar.addView(languageButton, new LinearLayout.LayoutParams(-2, dp(42)));
        languageButton.setOnClickListener(v -> showLanguageChooser());
        LinearLayout.LayoutParams brandBarLp = matchWrap(); brandBarLp.bottomMargin = dp(3); page.addView(brandBar, brandBarLp);
        TextView developerInline = text(BotnaI18n.isEnglish(this)
                ? "Developer: يحيى النباهين • Telegram @yahea21"
                : "المطوّر: يحيى النباهين • Telegram: @yahea21", 11, MUTED, true);
        developerInline.setGravity(BotnaI18n.isEnglish(this) ? Gravity.LEFT : Gravity.RIGHT);
        developerInline.setOnClickListener(v -> { try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/yahea21"))); } catch (Exception ignored) { } });
        LinearLayout.LayoutParams developerInlineLp = matchWrap(); developerInlineLp.bottomMargin = dp(4); page.addView(developerInline, developerInlineLp);

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

        // Dedicated script-library card separated from agent tasks and diagnostics.
        LinearLayout scriptsCard = card();
        LinearLayout.LayoutParams scriptsCardLp = matchWrap(); scriptsCardLp.topMargin = dp(16); page.addView(scriptsCard, scriptsCardLp);
        scriptsCard.addView(text("السكربتات المحفوظة", 19, TEXT, true), matchWrap());
        savedScriptsSummary = text(buildSavedScriptsSummary(), 13, MUTED, false);
        LinearLayout.LayoutParams scriptsSummaryLp = matchWrap(); scriptsSummaryLp.topMargin = dp(5); scriptsCard.addView(savedScriptsSummary, scriptsSummaryLp);
        Button savedScriptsButton = button("فتح قائمة السكربتات المحفوظة");
        LinearLayout.LayoutParams savedScriptsLp = matchWrap(); savedScriptsLp.topMargin = dp(9); scriptsCard.addView(savedScriptsButton, savedScriptsLp);
        savedScriptsButton.setOnClickListener(v -> showSavedScriptsDialog());

        LinearLayout helpCard = card();
        LinearLayout.LayoutParams helpCardLp = matchWrap(); helpCardLp.topMargin = dp(16); page.addView(helpCard, helpCardLp);
        helpCard.addView(text("مساعد استخدام بوتنا", 19, TEXT, true), matchWrap());
        TextView helpIntro = text("اسأل عن التطبيق أو إحدى ميزاته.", 13, MUTED, false);
        LinearLayout.LayoutParams helpIntroLp = matchWrap(); helpIntroLp.topMargin = dp(4); helpCard.addView(helpIntro, helpIntroLp);
        EditText helpQuestion = new EditText(this);
        helpQuestion.setSingleLine(false); helpQuestion.setMinLines(2); helpQuestion.setMaxLines(4);
        helpQuestion.setGravity(Gravity.TOP | Gravity.START); helpQuestion.setTextSize(14); helpQuestion.setTextColor(TEXT); helpQuestion.setHintTextColor(MUTED);
        helpQuestion.setHint(BotnaI18n.translate(this, "اسأل عن التطبيق أو إحدى ميزاته…"));
        helpQuestion.setPadding(dp(12), dp(10), dp(12), dp(10)); helpQuestion.setBackground(roundDrawable(Color.argb(235, 7, 15, 29), CYAN, 1, 14));
        BotnaI18n.applyDirection(this, helpQuestion);
        LinearLayout.LayoutParams helpQuestionLp = matchWrap(); helpQuestionLp.topMargin = dp(8); helpCard.addView(helpQuestion, helpQuestionLp);
        Button askHelp = button("اسأل بوتنا");
        LinearLayout.LayoutParams askHelpLp = matchWrap(); askHelpLp.topMargin = dp(8); helpCard.addView(askHelp, askHelpLp);
        TextView helpAnswer = text(BotnaI18n.isEnglish(this)
                ? "Answers to common in-app questions appear here without using your AI API quota."
                : "ستظهر الإجابة هنا دون استهلاك حصة API للأسئلة الشائعة داخل التطبيق.", 13, TEXT, false);
        LinearLayout.LayoutParams helpAnswerLp = matchWrap(); helpAnswerLp.topMargin = dp(9);
        helpAnswer.setPadding(dp(10), dp(9), dp(10), dp(9)); helpAnswer.setBackground(roundDrawable(Color.argb(110, 12, 70, 112), Color.argb(160, 35, 226, 244), 1, 12));
        helpCard.addView(helpAnswer, helpAnswerLp);
        askHelp.setOnClickListener(v -> {
            String question = helpQuestion.getText() == null ? "" : helpQuestion.getText().toString().trim();
            if (question.isEmpty()) { helpQuestion.setError(BotnaI18n.isEnglish(this) ? "Enter a question first." : "اكتب سؤالك أولًا."); return; }
            helpAnswer.setText(answerProductQuestion(question));
        });

        LinearLayout dataCard = card();
        LinearLayout.LayoutParams dataLp = matchWrap(); dataLp.topMargin = dp(16); page.addView(dataCard, dataLp);
        dataCard.addView(text("بياناتك المحلية", 18, TEXT, true));
        dataSummary = text(buildDataSummary(), 14, MUTED, false);
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
        LinearLayout developerCard = card();
        LinearLayout.LayoutParams developerLp = matchWrap(); developerLp.topMargin = dp(16); page.addView(developerCard, developerLp);
        developerCard.addView(text("اسم المطوّر", 18, TEXT, true), matchWrap());
        developerCard.addView(text(DEVELOPER_NAME + "  •  " + DEVELOPER_TELEGRAM, 15, MUTED, true), matchWrap());
        developerCard.addView(text("للتواصل والاستفسار", 12, MUTED, false), matchWrap());
        Button telegram = button("تواصل عبر Telegram");
        LinearLayout.LayoutParams telegramLp = matchWrap(); telegramLp.topMargin = dp(8); developerCard.addView(telegram, telegramLp);
        telegram.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/yahea21"))); }
            catch (Exception ex) { Toast.makeText(this, BotnaI18n.isEnglish(this) ? "Could not open Telegram." : "تعذر فتح رابط Telegram.", Toast.LENGTH_LONG).show(); }
        });
        Button privacyButton = button("سياسة الخصوصية");
        LinearLayout.LayoutParams privacyLp = matchWrap(); privacyLp.topMargin = dp(7); developerCard.addView(privacyButton, privacyLp);
        privacyButton.setOnClickListener(v -> showPrivacyNotice());

        setContentView(scroll);
        scroll.setAlpha(0f); scroll.setTranslationY(dp(6));
        scroll.animate().alpha(1f).translationY(0f).setDuration(220).start();
    }

    private void showPrivacyNotice() {
        boolean en = BotnaI18n.isEnglish(this);
        String policy = en
                ? "LOCAL STORAGE\nScripts, agent tasks, image templates, agent messages and diagnostic logs are stored in the app's private storage. API keys are encrypted using Android Keystore.\n\nAI PROVIDERS\nWhen you request AI analysis, your prompt and necessary request data are sent to the provider configured in AI Settings. If screenshot context is enabled, a screenshot may be sent and may contain private information. Provider retention, quotas and fees apply.\n\nACCESSIBILITY\nAfter you enable the Accessibility service in Android Settings, Botna can inspect visible UI elements and perform supported automation steps that you requested. Supervise sensitive actions such as payments, messages, account changes and deletion.\n\nDIAGNOSTICS\nLogs are stored locally and can be deleted. Screenshot logging can be turned off in log settings.\n\nCONTACT\nDeveloper: يحيى النباهين • Telegram: @yahea21\n\nBefore publishing, review the complete PRIVACY.md policy and host an approved public URL."
                : "التخزين المحلي\nتُحفظ السكربتات ومهام الوكيل وقوالب الصور ورسائله والسجلات التشخيصية في مساحة التطبيق الخاصة. تُشفّر مفاتيح API باستخدام Android Keystore.\n\nمزوّدو الذكاء الاصطناعي\nعند طلب التحليل، تُرسل تعليماتك والبيانات اللازمة إلى المزوّد الذي ضبطته في إعدادات الذكاء الاصطناعي. إذا فعّلت إرفاق لقطة الشاشة فقد تُرسل صورة تحتوي معلومات خاصة. تنطبق سياسات المزوّد وحصته ورسومه.\n\nإمكانية الوصول\nبعد تفعيل خدمة إمكانية الوصول من إعدادات أندرويد، يستطيع Botna قراءة عناصر الواجهة الظاهرة وتنفيذ خطوات الأتمتة المدعومة التي طلبتها. راجع بنفسك العمليات الحساسة مثل الدفع والرسائل وتغيير الحسابات والحذف.\n\nالسجلات\nتُحفظ السجلات محليًا ويمكن حذفها. ويمكن إيقاف حفظ لقطات الشاشة من إعدادات السجلات.\n\nالتواصل\nالمطوّر: يحيى النباهين • Telegram: @yahea21\n\nقبل النشر، راجع ملف PRIVACY.md كاملًا واستضف نسخة معتمدة على رابط عام.";
        TextView body = text(policy, 13, TEXT, false); body.setPadding(dp(12),dp(10),dp(12),dp(10));
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setClipToPadding(false); scroll.addView(body);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "سياسة الخصوصية"))
                .setView(scroll).setPositiveButton(BotnaI18n.translate(this, "إغلاق"), null).create();
        dialog.show(); sizeDialogToViewport(dialog, 0.82f);
    }

    private void showLanguageChooser() {
        boolean english = BotnaI18n.isEnglish(this);
        String[] choices = {"العربية", "English"};
        AlertDialog chooser = new AlertDialog.Builder(this)
                .setTitle(BotnaI18n.isEnglish(this) ? "App language" : "لغة التطبيق")
                .setSingleChoiceItems(choices, english ? 1 : 0, (dialog, which) -> {
                    BotnaI18n.setLanguage(this, which == 1 ? "en" : "ar");
                    try { sendBroadcast(new Intent(BotnaAccessibilityService.ACTION_LANGUAGE_CHANGED).setPackage(getPackageName())); } catch (Exception ignored) { }
                    dialog.dismiss();
                    buildInterface();
                    refreshStatus();
                })
                .setNegativeButton(BotnaI18n.isEnglish(this) ? "Cancel" : "إلغاء", null)
                .create();
        chooser.show(); sizeDialogToViewport(chooser, 0.48f);
    }

    private void showWelcomeIfNeeded() {
        if (isFinishing() || getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ONBOARDING_DONE, false)) return;
        boolean en = BotnaI18n.isEnglish(this);
        final int[] pageIndex = {0};
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(12));
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(247, 16, 47, 78), Color.argb(250, 7, 13, 25)});
        glass.setCornerRadius(dp(24)); glass.setStroke(dp(1), Color.argb(235, 35, 226, 244)); root.setBackground(glass);
        ImageView logo = new ImageView(this); logo.setImageResource(R.mipmap.ic_launcher); logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(dp(92), dp(92)); logoLp.gravity = Gravity.CENTER_HORIZONTAL; root.addView(logo, logoLp);
        TextView heading = text("مرحبًا بك في بوتنا", 23, TEXT, true); heading.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams headingLp = matchWrap(); headingLp.topMargin = dp(8); root.addView(heading, headingLp);
        TextView body = text("ابنِ أتمتتك بخطوات واضحة، اختبرها، ثم شغّلها مع إمكانية الإيقاف والاستكمال.", 15, MUTED, false);
        body.setGravity(Gravity.CENTER); body.setPadding(dp(4), dp(6), dp(4), dp(8));
        LinearLayout.LayoutParams bodyLp = matchWrap(); bodyLp.topMargin = dp(5); root.addView(body, bodyLp);
        TextView pageIndicator = text("● ○ ○", 13, CYAN, true); pageIndicator.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams dotLp = matchWrap(); dotLp.topMargin = dp(3); root.addView(pageIndicator, dotLp);
        TextView author = text("المطوّر: يحيى النباهين  •  Telegram: @yahea21", 12, TEXT, true);
        author.setGravity(Gravity.CENTER); LinearLayout.LayoutParams authorLp = matchWrap(); authorLp.topMargin = dp(8); root.addView(author, authorLp);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL); actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsLp = matchWrap(); actionsLp.topMargin = dp(12); root.addView(actions, actionsLp);
        Button prev = button("السابق"); Button next = button("التالي"); Button skip = button("تخطي المقدمة");
        actions.addView(prev, new LinearLayout.LayoutParams(0, dp(44), 1f));
        LinearLayout.LayoutParams nextLp = new LinearLayout.LayoutParams(0, dp(44), 1f); nextLp.leftMargin = dp(5); actions.addView(next, nextLp);
        LinearLayout.LayoutParams skipLp = new LinearLayout.LayoutParams(0, dp(44), 1f); skipLp.leftMargin = dp(5); actions.addView(skip, skipLp);
        AlertDialog intro = new AlertDialog.Builder(this).setView(root).create();
        Runnable render = () -> {
            final int i = pageIndex[0];
            String[] arTitles = {"مرحبًا بك في بوتنا", "أنشئ، راجع، ثم شغّل", "خصوصيتك والتحكم بيدك"};
            String[] enTitles = {"Welcome to Botna", "Create, review, then run", "You stay in control"};
            String[] arBodies = {
                    "ابنِ أتمتتك بخطوات واضحة، اختبرها، ثم شغّلها مع إمكانية الإيقاف والاستكمال.",
                    "فعّل خدمة إمكانية الوصول، واضبط مزوّد API عند الحاجة، ثم افتح الفقاعة B لإضافة الأوامر والبلوكات. راجع الخطوات قبل تشغيلها.",
                    "الوكيل قد يقرأ لقطة الشاشة ويرسلها إلى مزوّد API الذي تختاره. راجع العمليات الحساسة بنفسك. المطوّر: يحيى النباهين • @yahea21"
            };
            String[] enBodies = {
                    "Build automation with clear steps, review it, then run, pause, or resume it.",
                    "Enable Accessibility, configure an API provider if needed, then open the B bubble to add commands and blocks. Review steps before running.",
                    "The agent may read screenshots and send them to the AI provider you choose. Review sensitive actions yourself. Developer: يحيى النباهين • @yahea21"
            };
            heading.setText(en ? enTitles[i] : arTitles[i]); body.setText(en ? enBodies[i] : arBodies[i]);
            pageIndicator.setText(i == 0 ? "● ○ ○" : (i == 1 ? "○ ● ○" : "○ ○ ●"));
            prev.setEnabled(i > 0); prev.setAlpha(i > 0 ? 1f : 0.45f);
            next.setText(BotnaI18n.buttonText(this, i == 2 ? "ابدأ الاستخدام" : "التالي"));
        };
        render.run();
        next.setOnClickListener(v -> {
            if (pageIndex[0] >= 2) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply(); intro.dismiss(); return; }
            body.animate().alpha(0f).translationY(dp(7)).setDuration(130).withEndAction(() -> {
                pageIndex[0]++; render.run(); body.setTranslationY(dp(-7)); body.animate().alpha(1f).translationY(0).setDuration(180).start();
            }).start();
        });
        prev.setOnClickListener(v -> {
            if (pageIndex[0] <= 0) return;
            body.animate().alpha(0f).translationY(dp(7)).setDuration(110).withEndAction(() -> {
                pageIndex[0]--; render.run(); body.setTranslationY(dp(-7)); body.animate().alpha(1f).translationY(0).setDuration(180).start();
            }).start();
        });
        skip.setOnClickListener(v -> { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply(); intro.dismiss(); });
        intro.setOnDismissListener(d -> getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply());
        intro.show(); sizeDialogToViewport(intro, 0.74f);
        root.setAlpha(0f); root.setScaleX(0.97f); root.setScaleY(0.97f);
        root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260).start();
    }

    private void sizeDialogToViewport(AlertDialog dialog, float heightFraction) {
        if (dialog == null || dialog.getWindow() == null) return;
        int width = Math.max(dp(1), Math.min(getResources().getDisplayMetrics().widthPixels - dp(28), dp(600)));
        int height = Math.max(dp(1), Math.min(getResources().getDisplayMetrics().heightPixels - dp(36),
                Math.round(getResources().getDisplayMetrics().heightPixels * Math.max(0.35f, Math.min(0.88f, heightFraction)))));
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        dialog.getWindow().setLayout(width, height);
    }

    private String buildSavedScriptsSummary() {
        int count = BotnaStore.scripts(this).length();
        return BotnaI18n.isEnglish(this)
                ? "Scripts: " + count + "\nCreate a standalone script, edit blocks, rename, run, or delete it."
                : "عدد السكربتات: " + count + "\nيمكنك إنشاء سكربت مستقل، تعديل بلوكاته، إعادة تسميته، تشغيله أو حذفه.";
    }

    private String buildDataSummary() {
        int scripts = BotnaStore.scripts(this).length(), images = BotnaStore.imageLibrary(this).length();
        int tasks = BotnaAgentStore.tasks(this).length(), messages = BotnaAgentStore.messages(this).length();
        int keys = BotnaKeyVault.count(this, BotnaAICompiler.providerId(this)), logs = BotnaLogStore.list(this).length();
        String provider = BotnaAICompiler.providerLabel(BotnaAICompiler.providerId(this));
        boolean configured = BotnaAICompiler.isConfigured(this), en = BotnaI18n.isEnglish(this);
        if (en) return "Saved scripts: " + scripts + "\nImage templates: " + images + "\nSaved agent tasks: " + tasks
                + "\nAgent messages: " + messages + "\nSelected provider: " + provider + "\nSaved keys for this provider: " + keys
                + "\nDiagnostic logs: " + logs + "\nAI status: " + (configured ? "API configured" : "API key/endpoint setup required");
        return "السكربتات المحفوظة: " + scripts + "\nقوالب مكتبة الصور: " + images + "\nمهام الوكيل المحفوظة: " + tasks
                + "\nرسائل الوكيل: " + messages + "\nالمزوّد المحدد: " + provider + "\nالمفاتيح المحفوظة لهذا المزوّد: " + keys
                + "\nالسجلات التشخيصية: " + logs + "\nحالة الذكاء الاصطناعي: " + (configured ? "إعداد API مكتمل" : "يتطلب إعداد مفتاح/نقطة اتصال API");
    }

    private String answerProductQuestion(String rawQuestion) {
        String q = rawQuestion == null ? "" : rawQuestion.trim().toLowerCase(java.util.Locale.ROOT)
                .replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ة','ه').replace('ى','ي');
        boolean en = BotnaI18n.isEnglish(this);
        if (q.contains("script") || q.contains("سكربت") || q.contains("خطوه") || q.contains("خطوة"))
            return en ? "Saved scripts is a separate card on the home screen. Open it to create a script, add or edit blocks, rename it, run it, or delete it. Use the floating B bubble to edit the script step-by-step." : "افتح بطاقة «السكربتات المحفوظة» في الواجهة الرئيسية. يمكنك إنشاء سكربت، إضافة أو تعديل البلوكات، تغيير الاسم، التشغيل أو الحذف. استخدم الفقاعة B لتحرير الخطوات واحدة تلو الأخرى.";
        if (q.contains("api") || q.contains("مفتاح") || q.contains("gemini") || q.contains("مزود") || q.contains("provider"))
            return en ? "Open AI Settings, select one provider, enter its exact API URL, model ID and key, then save and test the connection. API usage may have quotas or fees; adding keys from the same project does not necessarily increase its quota." : "افتح «إعدادات الذكاء الاصطناعي»، اختر مزوّدًا واحدًا، أدخل رابط API ومعرّف النموذج والمفتاح الصحيح، ثم احفظ واختبر الاتصال. قد تكون هناك حصة أو رسوم؛ تعدد المفاتيح من المشروع نفسه لا يضمن زيادة الحصة.";
        if (q.contains("سجل") || q.contains("log") || q.contains("فشل") || q.contains("خطا") || q.contains("error"))
            return en ? "Open Logs from the home screen. Select a record to read the step, reason and details; a screenshot is shown when screenshot logging was enabled and Android allowed capture. You can delete one or all records there." : "افتح «قائمة السجلات» من الواجهة الرئيسية. اضغط على السجل لعرض الخطوة والسبب والتفاصيل؛ تظهر الصورة إذا كان حفظ اللقطات مفعّلًا وسمح أندرويد بالتقاطها. يمكنك حذف سجل واحد أو جميع السجلات.";
        if (q.contains("وكيل") || q.contains("agent") || q.contains("مهمه") || q.contains("task"))
            return en ? "Use Create Smart Agent Task for a goal that may require several steps. The agent reviews fresh screenshots between actions and saves explanations in Agent Messages. Review sensitive actions and enable Accessibility first." : "استخدم «إنشاء بوت ذكي للمهام المعقدة» لوصف هدف يتطلب عدة خطوات. يعيد الوكيل مراجعة لقطات حديثة بين الإجراءات ويحفظ الشروحات في «رسائل الوكيل». راجع العمليات الحساسة وفعّل خدمة إمكانية الوصول أولًا.";
        if (q.contains("صوره") || q.contains("صورة") || q.contains("image") || q.contains("قالب"))
            return en ? "Open the floating B bubble, then Image Library to add a template from a screenshot. Use an image-match step and tune the matching threshold from the block settings if needed." : "افتح الفقاعة B ثم «مكتبة الصور» لإضافة قالب من لقطة شاشة. أضف خطوة مطابقة صورة، واضبط نسبة المطابقة من إعدادات البلوك عند الحاجة.";
        if (q.contains("امكانيه الوصول") || q.contains("إمكانية الوصول") || q.contains("accessibility") || q.contains("خدمه") || q.contains("service"))
            return en ? "In the Automation Service Status card, tap Enable / Manage Accessibility Service. Android Settings opens; select Botna and enable it manually. Android requires this user action." : "في بطاقة «حالة خدمة الأتمتة»، اضغط «تفعيل / إدارة خدمة إمكانية الوصول». ستفتح إعدادات أندرويد؛ اختر Botna وفعّل الخدمة يدويًا، لأن أندرويد يتطلب موافقة المستخدم.";
        if (q.contains("بحث") || q.contains("امر") || q.contains("أمر") || q.contains("command") || q.contains("search"))
            return en ? "Open the floating B panel and tap the magnifying-glass command search. Type a keyword, select the matching command, then fill in any parameters it asks for." : "افتح لوحة الفقاعة B واضغط زر العدسة للبحث عن الأوامر. اكتب كلمة، واختر الأمر المطابق، ثم أدخل المعلمات التي يطلبها إن وجدت.";
        if (q.contains("خصوصيه") || q.contains("privacy") || q.contains("بيانات"))
            return en ? "Scripts and logs are stored in the app's private local storage. When you use an AI provider, only the data required for that request is sent to that provider; do not include passwords or private information unless necessary." : "تُحفظ السكربتات والسجلات في مساحة التطبيق المحلية الخاصة. عند استخدام مزوّد AI تُرسل البيانات اللازمة للطلب إلى ذلك المزوّد؛ تجنب تضمين كلمات المرور أو معلومات خاصة غير ضرورية.";
        return en ? "I can explain scripts, command search, the floating B panel, Accessibility permission, AI provider/API settings, image templates, the smart agent, saved agent tasks, and logs. Try asking about one of these features." : "أستطيع شرح السكربتات، البحث عن الأوامر، الفقاعة B، إذن إمكانية الوصول، إعدادات مزوّد الذكاء الاصطناعي، قوالب الصور، الوكيل الذكي، مهامه المحفوظة والسجلات. اذكر إحدى هذه الميزات لأشرح لك أين تجدها وكيف تستخدمها.";
    }

    private void showSmartTaskDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), dp(4), dp(6), dp(4));
        TextView help = text("اكتب الهدف النهائي أو السؤال كما تريده، وليس شرطًا أن يكون أمرًا مباشرًا: مثل «اختبر سرعة الإنترنت وأخبرني بالنتيجة» أو «افتح الرابط ولخّص محتواه». سيحلل الوكيل لقطة الشاشة بعد كل إجراء، ثم يحفظ الخلاصة والنصوص التي ظهرت في رسائل الوكيل والسجلات. يحتاج إلى خدمة إمكانية الوصول ومزوّد API يدعم الصور. الحد الوقائي 40 إجراءً لكل مهمة.", 13, MUTED, false);
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
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "إنشاء بوت ذكي للمهام الصعبة والطويلة"))
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
            if (!BotnaAICompiler.isConfigured(this) && !BotnaAccessibilityService.isLocalSpeedTestRequest(command)) {
                Toast.makeText(this, "اضبط مزود API ومفتاحًا واحدًا على الأقل قبل بدء الوكيل الذكي. اختبار سرعة التنزيل المحلي لا يحتاج مفتاحًا.", Toast.LENGTH_LONG).show(); return;
            }
            String taskId = BotnaAgentStore.createTask(this, command);
            dialog.dismiss();
            dispatchAgentTask(taskId, command);
        });
    }

    private void dispatchAgentTask(String taskId, String goal) {
        if (!BotnaAICompiler.isConfigured(this) && !BotnaAccessibilityService.isLocalSpeedTestRequest(goal)) {
            Toast.makeText(this, "اضبط مزود API ومفتاحًا صالحًا قبل تشغيل الوكيل. اختبار سرعة التنزيل المحلي لا يحتاج مفتاحًا.", Toast.LENGTH_LONG).show();
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

    private void showSavedScriptsDialog() {
        org.json.JSONArray scripts = BotnaStore.scripts(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8), dp(5), dp(8), dp(5));
        root.addView(text("كل سكربت محفوظ مستقل عن مهام الوكيل. يمكنك إعادة تسميته، تعديل خطواته أو إضافة خطوات من محرر Botna، تشغيله أو حذفه.", 12, MUTED, false), matchWrap());
        Button create = button("＋ إنشاء سكربت جديد");
        LinearLayout.LayoutParams createLp = matchWrap(); createLp.topMargin = dp(7); root.addView(create, createLp);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, listHeightDp(430, 0.46f)); scrollLp.topMargin = dp(8); root.addView(scroll, scrollLp);
        final AlertDialog[] owner = new AlertDialog[1];
        create.setOnClickListener(v -> {
            EditText name = new EditText(this); name.setSingleLine(true); name.setTextColor(TEXT); name.setHintTextColor(MUTED);
            name.setHint("اسم السكربت"); name.setText("سكربت جديد"); name.setSelectAllOnFocus(true);
            AlertDialog createDialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "إنشاء سكربت محفوظ"))
                    .setView(name).setPositiveButton(BotnaI18n.translate(this, "إنشاء"), (d, w) -> {
                        org.json.JSONObject created = BotnaStore.createScript(this, name.getText() == null ? "سكربت جديد" : name.getText().toString());
                        if (owner[0] != null) owner[0].dismiss(); refreshStatus();
                        if (created != null) dispatchSavedScript(created.optString("id", ""), false);
                        else showSavedScriptsDialog();
                    }).setNegativeButton(BotnaI18n.translate(this, "إلغاء"), null).create();
            createDialog.show();
            if (createDialog.getWindow() != null) createDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        });
        if (scripts.length() == 0) list.addView(text("لا توجد سكربتات محفوظة. أنشئ سكربتًا جديدًا ثم أضف خطواته من محرر Botna.", 13, MUTED, false), matchWrap());
        for (int i = 0; i < scripts.length(); i++) {
            org.json.JSONObject script = scripts.optJSONObject(i); if (script == null) continue;
            final String id = script.optString("id", "");
            String name = script.optString("name", "سكربت بدون اسم");
            int count = BotnaStore.steps(script).length();
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(8), dp(7), dp(8), dp(7));
            card.setBackground(roundDrawable(CARD, CYAN, 1, 12));
            LinearLayout.LayoutParams cardLp = matchWrap(); cardLp.topMargin = dp(7); list.addView(card, cardLp);
            card.addView(text(name, 15, TEXT, true), matchWrap());
            LinearLayout.LayoutParams detailLp = matchWrap(); detailLp.topMargin = dp(3);
            card.addView(text("الخطوات: " + count + (id.equals(BotnaStore.activeId(this)) ? " • السكربت النشط" : ""), 11, MUTED, false), detailLp);
            LinearLayout row1 = new LinearLayout(this); row1.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams row1Lp = matchWrap(); row1Lp.topMargin = dp(6); card.addView(row1, row1Lp);
            Button edit = button("إضافة/تعديل الخطوات"); Button run = button("▶ تشغيل");
            row1.addView(edit, new LinearLayout.LayoutParams(0, dp(40), 1));
            LinearLayout.LayoutParams runLp = new LinearLayout.LayoutParams(0, dp(40), 1); runLp.leftMargin = dp(4); row1.addView(run, runLp);
            LinearLayout row2 = new LinearLayout(this); row2.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams row2Lp = matchWrap(); row2Lp.topMargin = dp(4); card.addView(row2, row2Lp);
            Button rename = button("إعادة تسمية"); Button delete = button("حذف السكربت");
            row2.addView(rename, new LinearLayout.LayoutParams(0, dp(38), 1));
            LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(0, dp(38), 1); delLp.leftMargin = dp(4); row2.addView(delete, delLp);
            edit.setOnClickListener(v -> { if (owner[0] != null) owner[0].dismiss(); dispatchSavedScript(id, false); });
            run.setOnClickListener(v -> { if (owner[0] != null) owner[0].dismiss(); dispatchSavedScript(id, true); });
            rename.setOnClickListener(v -> {
                EditText input = new EditText(this); input.setSingleLine(true); input.setTextColor(TEXT); input.setHintTextColor(MUTED);
                input.setText(name); input.setSelectAllOnFocus(true);
                AlertDialog renameDialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "إعادة تسمية السكربت"))
                        .setView(input).setPositiveButton(BotnaI18n.translate(this, "حفظ الاسم"), (d, w) -> {
                            String newName = input.getText() == null ? "" : input.getText().toString().trim();
                            if (!BotnaStore.renameScript(this, id, newName)) Toast.makeText(this, "اكتب اسمًا غير فارغ ثم أعد المحاولة.", Toast.LENGTH_LONG).show();
                            if (owner[0] != null) owner[0].dismiss(); refreshStatus(); showSavedScriptsDialog();
                        }).setNegativeButton(BotnaI18n.translate(this, "إلغاء"), null).create();
                renameDialog.show(); if (renameDialog.getWindow() != null) renameDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            });
            delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "حذف السكربت بالكامل"))
                    .setMessage("سيُحذف «" + name + "» وجميع خطواته المحفوظة. لا يمكن التراجع عن ذلك.")
                    .setPositiveButton(BotnaI18n.translate(this, "حذف"), (d, w) -> { BotnaStore.deleteScript(this, id); if (owner[0] != null) owner[0].dismiss(); refreshStatus(); showSavedScriptsDialog(); })
                    .setNegativeButton(BotnaI18n.translate(this, "إلغاء"), null).show());
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "السكربتات المحفوظة"))
                .setView(root).setPositiveButton(BotnaI18n.translate(this, "إغلاق"), null).create();
        dialog.show(); owner[0] = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(560)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(48), Math.round(getResources().getDisplayMetrics().heightPixels * 0.78f)));
        }
    }

    private void dispatchSavedScript(String scriptId, boolean runNow) {
        org.json.JSONObject script = BotnaStore.getScript(this, scriptId);
        if (script == null) { Toast.makeText(this, "لم يتم العثور على السكربت المحفوظ.", Toast.LENGTH_LONG).show(); return; }
        if (!isServiceEnabled()) {
            Toast.makeText(this, "فعّل خدمة Botna قبل تحرير أو تشغيل السكربت.", Toast.LENGTH_LONG).show(); openAccessibilitySettings(); return;
        }
        String action = runNow ? BotnaAccessibilityService.ACTION_RUN_AGENT_SCRIPT : BotnaAccessibilityService.ACTION_OPEN_AGENT_SCRIPT;
        Intent command = new Intent(action).setPackage(getPackageName()).putExtra("scriptId", scriptId);
        sendBroadcast(command);
        Toast.makeText(this, runNow ? "تم إرسال السكربت للتشغيل." : "افتح محرر Botna لإضافة الخطوات أو تعديلها.", Toast.LENGTH_SHORT).show();
    }

    private void showSavedAgentTasksDialog() {
        org.json.JSONArray tasks = BotnaAgentStore.tasks(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(8), dp(5), dp(8), dp(5));
        TextView intro = text("كل مهمة محفوظة بشكل مستقل. إعادة تشغيل الوكيل تعيد التخطيط حسب الشاشة، أما تشغيل السكربت فيعيد الإجراءات المسجلة كما هي. افتح السكربت لتعديل الخطوات داخل محرر بوتنا.", 12, MUTED, false);
        root.addView(intro, matchWrap());
        Button create = button("＋ إنشاء مهمة ذكية جديدة");
        LinearLayout.LayoutParams createLp = matchWrap(); createLp.topMargin = dp(6); root.addView(create, createLp);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(true); scroll.setPadding(0, 0, 0, dp(12)); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(0, 0, 0, dp(12)); scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, listHeightDp(430, 0.46f)); scrollLp.topMargin = dp(8); root.addView(scroll, scrollLp);
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
            delete.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "حذف المهمة الذكية"))
                    .setMessage(BotnaI18n.translate(this, "سيُحذف تعريف المهمة وسكربتها المحفوظة. تبقى رسائل الوكيل وسجلات التشخيص كي تتمكن من مراجعة ما حدث."))
                    .setPositiveButton(BotnaI18n.translate(this, "حذف"), (d, w) -> { BotnaAgentStore.deleteTask(this, id); if (owner[0] != null) owner[0].dismiss(); refreshStatus(); showSavedAgentTasksDialog(); })
                    .setNegativeButton(BotnaI18n.translate(this, "إلغاء"), null).show());
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "مهام الوكيل الذكي المحفوظة"))
                .setView(root).setPositiveButton(BotnaI18n.translate(this, "إغلاق"), null).create();
        dialog.show(); owner[0] = dialog;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(560)),
                    Math.min(getResources().getDisplayMetrics().heightPixels - dp(48), Math.round(getResources().getDisplayMetrics().heightPixels * 0.78f)));
        }
    }

    private void confirmRunSavedAgent(String id) {
        org.json.JSONObject item = BotnaAgentStore.getTask(this, id); if (item == null) return;
        new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "إعادة تشغيل الوكيل"))
                .setMessage(BotnaI18n.translate(this, "سيُعاد تحليل الشاشة الحالية والتحكم بواجهة الهاتف لتحقيق الهدف المحفوظ. قد يختلف المسار عن التشغيل السابق. راجع أي عملية حساسة يدويًا. متابعة؟"))
                .setPositiveButton(BotnaI18n.translate(this, "بدء"), (d, w) -> dispatchAgentTask(id, item.optString("goal", "")))
                .setNegativeButton(BotnaI18n.translate(this, "إلغاء"), null).show();
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
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "تعديل المهمة المحفوظة")).setView(editRoot).create();
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
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(true); scroll.setPadding(0, 0, 0, dp(12)); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(0, 0, 0, dp(12)); scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, listHeightDp(430, 0.44f)); scrollLp.topMargin = dp(8); content.addView(scroll, scrollLp);
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
                        .setPositiveButton(BotnaI18n.translate(this, "إغلاق"), null).show();
            });
            shown++;
        }
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "رسائل الوكيل"))
                .setView(content).setPositiveButton(BotnaI18n.translate(this, "إغلاق"), null).setNegativeButton(BotnaI18n.translate(this, "حذف الرسائل"), null).create();
        dialog.show(); sizeDialogToViewport(dialog, 0.84f);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "حذف رسائل الوكيل"))
                .setMessage(BotnaI18n.translate(this, "سيتم حذف سجل الرسائل التفسيري فقط. تبقى مهام الوكيل وسكربتاته وسجلات التشخيص."))
                .setPositiveButton(BotnaI18n.translate(this, "حذف"), (d, w) -> { BotnaAgentStore.clearMessages(this); dialog.dismiss(); refreshStatus(); showAgentMessagesDialog(); })
                .setNegativeButton(BotnaI18n.translate(this, "إلغاء"), null).show());
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
        ScrollView listScroll = new ScrollView(this); listScroll.setFillViewport(true); listScroll.setClipToPadding(false); listScroll.setVerticalScrollBarEnabled(true); listScroll.setPadding(0, 0, 0, dp(12)); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(0, 0, 0, dp(12)); listScroll.addView(list);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(-1, listHeightDp(390, 0.42f)); listLp.topMargin = dp(8); content.addView(listScroll, listLp);
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
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "سجلات بوتنا التشخيصية"))
                .setView(content).setPositiveButton(BotnaI18n.translate(this, "إغلاق"), null).setNegativeButton(BotnaI18n.translate(this, "حذف جميع السجلات"), null).create();
        dialog.show(); sizeDialogToViewport(dialog, 0.84f);
        logsDialogRef[0] = dialog;
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(BotnaI18n.translate(this, "حذف السجلات"))
                .setMessage(BotnaI18n.translate(this, "سيُحذف سجل الأخطاء وكل لقطات التشخيص المرتبطة به من مساحة التطبيق. لا يؤثر ذلك على السكربتات أو مفاتيح API."))
                .setPositiveButton(BotnaI18n.translate(this, "حذف الكل"), (d, w) -> { BotnaLogStore.clear(this); dialog.dismiss(); refreshStatus(); showLogsDialog(); })
                .setNegativeButton(BotnaI18n.translate(this, "إلغاء"), null).show());
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
        ScrollView detailScroll = new ScrollView(this); detailScroll.setFillViewport(false); detailScroll.setClipToPadding(false); detailScroll.addView(content);
        AlertDialog detailDialog = new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "تفاصيل سجل الفشل")).setView(detailScroll)
                .setPositiveButton(BotnaI18n.translate(this, "إغلاق"), null).setNegativeButton(BotnaI18n.translate(this, "حذف هذا السجل"), null).create();
        detailDialog.setOnDismissListener(d -> { if (screenshot != null && !screenshot.isRecycled()) screenshot.recycle(); });
        detailDialog.show(); sizeDialogToViewport(detailDialog, 0.82f);
        detailDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(BotnaI18n.translate(this, "حذف هذا السجل؟")).setMessage(BotnaI18n.translate(this, "سيتم حذف تفاصيله ولقطة الشاشة المرتبطة به."))
                .setPositiveButton(BotnaI18n.translate(this, "حذف"), (d, w) -> { BotnaLogStore.delete(this, entry.optString("id", "")); detailDialog.dismiss(); refreshStatus(); showLogsDialog(); })
                .setNegativeButton(BotnaI18n.translate(this, "إلغاء"), null).show());
    }

    private int listHeightDp(int maxDp, float screenFraction) {
        float density = getResources().getDisplayMetrics().density;
        int screenDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        int preferred = Math.round(screenDp * Math.max(0.28f, Math.min(0.58f, screenFraction)));
        return dp(Math.max(170, Math.min(maxDp, preferred)));
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
        new AlertDialog.Builder(this).setTitle(BotnaI18n.translate(this, "دليل الأوامر المخصصة")).setMessage(guide).setPositiveButton(BotnaI18n.translate(this, "حسنًا"), null).show();
    }

    @Override
    public void onBackPressed() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(BotnaI18n.translate(this, "حفظ السكربتات والخروج"))
                .setMessage(BotnaI18n.translate(this, "تُحفظ خطوات السكربتات تلقائيًا أثناء التعديل. هل تريد حفظها الآن والخروج من Botna؟"))
                .setPositiveButton(BotnaI18n.translate(this, "حفظ وخروج"), (d, w) -> {
                    BotnaStore.saveScripts(this, BotnaStore.scripts(this));
                    finish();
                })
                .setNegativeButton(BotnaI18n.translate(this, "إلغاء"), (d, w) -> { })
                .create();
        dialog.show();
    }

    private void refreshStatus() {
        if (serviceStatus == null) {
            return;
        }

        boolean enabled = isServiceEnabled();

        serviceStatus.setText(enabled
                ? (BotnaI18n.isEnglish(this) ? "● Service enabled — the B bubble should appear over apps." : "● الخدمة مفعّلة — يفترض ظهور الزر B فوق التطبيقات.")
                : (BotnaI18n.isEnglish(this) ? "○ Service disabled — enable Botna in Android Settings." : "○ الخدمة غير مفعّلة — افتح الإعدادات وفعّل Botna يدويًا."));

        serviceStatus.setTextColor(
                enabled
                        ? CYAN
                        : Color.rgb(255, 190, 100)
        );

        if (savedScriptsSummary != null) savedScriptsSummary.setText(buildSavedScriptsSummary());
        if (dataSummary != null) dataSummary.setText(buildDataSummary());

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

        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(236, 16, 27, 43), Color.argb(242, 7, 13, 24)});
        glass.setCornerRadius(dp(20));
        glass.setStroke(dp(1), Color.argb(155, 35, 226, 244));
        layout.setBackground(glass);

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

        view.setText(BotnaI18n.headingText(this, value, bold));
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.START);
        BotnaI18n.applyDirection(this, view);

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

        view.setText(BotnaI18n.buttonText(this, value));
        view.setTextSize(14);
        view.setAllCaps(false);
        view.setTextColor(Color.rgb(239, 248, 255));

        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(225, 20, 112, 145), Color.argb(235, 13, 47, 82)});
        glass.setCornerRadius(dp(15));
        glass.setStroke(dp(1), Color.argb(235, 35, 226, 244));
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(100, 110, 235, 255)), glass, null));

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
