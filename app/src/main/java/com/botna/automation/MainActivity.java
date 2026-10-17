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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String PREFS = "botna_settings";
    private static final String KEY_TARGET_TEXT = "target_text";
    private static final String KEY_LAST_RESULT = "last_result";

    private static final int BG = Color.rgb(5, 7, 12);
    private static final int CARD = Color.argb(238, 13, 17, 26);
    private static final int TEXT = Color.rgb(235, 246, 255);
    private static final int MUTED = Color.rgb(150, 174, 195);
    private static final int CYAN = Color.rgb(35, 226, 244);

    private TextView serviceStatus;
    private TextView lastResult;
    private EditText targetInput;

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

        TextView title = text(
                "بوتنا  Botna",
                29,
                TEXT,
                true
        );

        page.addView(title, matchWrap());

        TextView subtitle = text(
                "مساعد أتمتة مباشر لهاتفك",
                15,
                MUTED,
                false
        );

        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(4);
        page.addView(subtitle, subLp);

        // حالة الخدمة
        LinearLayout statusCard = card();

        LinearLayout.LayoutParams cardLp = matchWrap();
        cardLp.topMargin = dp(22);
        page.addView(statusCard, cardLp);

        TextView statusTitle = text(
                "حالة خدمة الأتمتة",
                18,
                TEXT,
                true
        );

        statusCard.addView(statusTitle);

        serviceStatus = text(
                "جارٍ فحص الحالة...",
                14,
                MUTED,
                false
        );

        LinearLayout.LayoutParams statusTextLp = matchWrap();
        statusTextLp.topMargin = dp(8);
        statusCard.addView(serviceStatus, statusTextLp);

        Button settingsButton = button(
                "تفعيل / إدارة خدمة إمكانية الوصول"
        );

        LinearLayout.LayoutParams settingsLp = matchWrap();
        settingsLp.topMargin = dp(14);
        statusCard.addView(settingsButton, settingsLp);

        settingsButton.setOnClickListener(v -> {
            try {
                startActivity(
                        new Intent(
                                Settings.ACTION_ACCESSIBILITY_SETTINGS
                        )
                );
            } catch (Exception ex) {
                Toast.makeText(
                        this,
                        "تعذر فتح إعدادات إمكانية الوصول",
                        Toast.LENGTH_LONG
                ).show();
            }
        });

        // النص المستهدف
        LinearLayout targetCard = card();

        LinearLayout.LayoutParams targetCardLp = matchWrap();
        targetCardLp.topMargin = dp(16);
        page.addView(targetCard, targetCardLp);

        targetCard.addView(
                text("النقر على نص محدد", 19, TEXT, true)
        );

        TextView targetHelp = text(
                "اكتب النص كما يظهر على الشاشة. بعد حفظه، " +
                "افتح التطبيق المطلوب واضغط الزر B العائم " +
                "ثم «اضغط النص المحفوظ».",
                14,
                MUTED,
                false
        );

        LinearLayout.LayoutParams helpLp = matchWrap();
        helpLp.topMargin = dp(8);
        targetCard.addView(targetHelp, helpLp);

        targetInput = new EditText(this);
        targetInput.setSingleLine(true);
        targetInput.setTextColor(TEXT);
        targetInput.setHintTextColor(MUTED);
        targetInput.setHint("مثال: تطوير");

        targetInput.setText(
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .getString(KEY_TARGET_TEXT, "")
        );

        targetInput.setTextSize(16);
        targetInput.setPadding(
                dp(12), dp(12), dp(12), dp(12)
        );

        targetInput.setBackground(
                roundDrawable(
                        Color.argb(230, 7, 13, 22),
                        Color.argb(230, 35, 226, 244),
                        1,
                        14
                )
        );

        LinearLayout.LayoutParams inputLp = matchWrap();
        inputLp.topMargin = dp(14);
        targetCard.addView(targetInput, inputLp);

        Button saveButton = button("حفظ النص المستهدف");

        LinearLayout.LayoutParams saveLp = matchWrap();
        saveLp.topMargin = dp(10);
        targetCard.addView(saveButton, saveLp);

        saveButton.setOnClickListener(v -> {
            String target = targetInput.getText()
                    .toString()
                    .trim();

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TARGET_TEXT, target)
                    .apply();

            hideKeyboard();

            Toast.makeText(
                    this,
                    target.isEmpty()
                            ? "تم مسح النص المستهدف"
                            : "تم حفظ النص: " + target,
                    Toast.LENGTH_SHORT
            ).show();
        });

        // الأدوات
        LinearLayout helpCard = card();

        LinearLayout.LayoutParams helpCardLp = matchWrap();
        helpCardLp.topMargin = dp(16);
        page.addView(helpCard, helpCardLp);

        helpCard.addView(
                text("أدوات النسخة الحالية", 19, TEXT, true)
        );

        addBullet(helpCard, "رجوع إلى الشاشة السابقة");
        addBullet(helpCard, "العودة إلى الرئيسية");
        addBullet(
                helpCard,
                "قراءة النصوص التي تعرضها التطبيقات عبر واجهة إمكانية الوصول"
        );
        addBullet(
                helpCard,
                "إنشاء السكربتات وإضافة الخطوات من الزر العائم"
        );
        addBullet(
                helpCard,
                "قص قوالب صور وحفظها محليًا لاستخدامها في المطابقة"
        );
        addBullet(
                helpCard,
                "عرض نتيجة كل محاولة والتنبيه عند عدم العثور على الهدف"
        );

        // آخر نتيجة
        LinearLayout resultCard = card();

        LinearLayout.LayoutParams resultLp = matchWrap();
        resultLp.topMargin = dp(16);
        page.addView(resultCard, resultLp);

        resultCard.addView(
                text("آخر نتيجة", 18, TEXT, true)
        );

        lastResult = text(
                "لم يتم تنفيذ أي إجراء بعد.",
                14,
                MUTED,
                false
        );

        LinearLayout.LayoutParams lastLp = matchWrap();
        lastLp.topMargin = dp(8);
        resultCard.addView(lastResult, lastLp);

        Button refresh = button("تحديث الحالة والنتيجة");

        LinearLayout.LayoutParams refreshLp = matchWrap();
        refreshLp.topMargin = dp(12);
        resultCard.addView(refresh, refreshLp);

        refresh.setOnClickListener(v -> refreshStatus());

        TextView disclaimer = text(
                "مهم: فعّل الخدمة بنفسك من إعدادات أندرويد. " +
                "تُخزَّن السكربتات وقوالب الصور محليًا. " +
                "تعتمد دقة مطابقة الصور على التشابه البصري " +
                "وحدّ التطابق، وقد لا تعمل على كل واجهة أو لعبة.",
                12,
                MUTED,
                false
        );

        LinearLayout.LayoutParams disclaimerLp = matchWrap();
        disclaimerLp.topMargin = dp(20);
        page.addView(disclaimer, disclaimerLp);

        setContentView(scroll);
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
                imm.hideSoftInputFromWindow(
                        targetInput.getWindowToken(),
                        0
                );
            }

            targetInput.clearFocus();

        } catch (Exception ignored) {
        }
    }
}
