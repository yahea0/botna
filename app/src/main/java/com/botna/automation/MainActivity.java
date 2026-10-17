package com.botna.automation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
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

        LinearLayout dataCard = card();
        LinearLayout.LayoutParams dataLp = matchWrap(); dataLp.topMargin = dp(16); page.addView(dataCard, dataLp);
        dataCard.addView(text("بياناتك المحلية", 18, TEXT, true));
        int scriptsCount = BotnaStore.scripts(this).length();
        int imagesCount = BotnaStore.imageLibrary(this).length();
        dataSummary = text("السكربتات المحفوظة: " + scriptsCount + "\nقوالب مكتبة الصور: " + imagesCount + "\nحالة مترجم الذكاء الاصطناعي: " + (BotnaAICompiler.isConfigured(this) ? "مفتاح محفوظ" : "يتطلب إعداد مفتاح API"), 14, MUTED, false);
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
                "\nأفضل نتيجة للأوامر المركبة تأتي مع إعداد مفتاح API في إعدادات الذكاء الاصطناعي. راجع دائمًا معاينة الترجمة قبل التنفيذ.";
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
                    + "\nحالة مترجم الذكاء الاصطناعي: " + (BotnaAICompiler.isConfigured(this) ? "مفتاح محفوظ" : "يتطلب إعداد مفتاح API"));
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
