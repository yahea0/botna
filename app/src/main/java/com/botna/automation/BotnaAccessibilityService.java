package com.botna.automation;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BotnaAccessibilityService
        extends AccessibilityService {

    private static final String PREFS = "botna_settings";
    private static final String KEY_TARGET_TEXT = "target_text";
    private static final String KEY_LAST_RESULT = "last_result";

    private static final int BG = Color.rgb(7, 17, 31);
    private static final int CARD = Color.rgb(14, 31, 51);
    private static final int TEXT = Color.rgb(235, 246, 255);
    private static final int MUTED = Color.rgb(164, 190, 210);
    private static final int CYAN = Color.rgb(24, 215, 232);

    private WindowManager windowManager;
    private View bubbleView;
    private View panelView;
    private TextView resultView;

    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;

    private boolean panelVisible = false;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        windowManager = (WindowManager)
                getSystemService(WINDOW_SERVICE);

        showBubble();

        saveResult(
            "الخدمة متصلة. اضغط الزر B العائم لعرض الأدوات."
        );
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // لا ننفذ أوامر تلقائية عند تغير الشاشة.
        // الأوامر تبدأ فقط عندما يضغط المستخدم أحد الأزرار.
    }

    @Override
    public void onInterrupt() {
        saveResult(
            "تمت مقاطعة خدمة إمكانية الوصول بواسطة النظام."
        );
    }

    @Override
    public void onDestroy() {
        removeView(panelView);
        removeView(bubbleView);

        panelView = null;
        bubbleView = null;
        panelVisible = false;

        super.onDestroy();
    }

    private void showBubble() {
        if (windowManager == null || bubbleView != null) {
            return;
        }

        Button bubble = new Button(this);

        bubble.setText("B");
        bubble.setTextSize(18);
        bubble.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bubble.setTextColor(Color.rgb(3, 20, 29));
        bubble.setPadding(0, 0, 0, 0);

        bubble.setBackground(
            round(CYAN, CYAN, 0, 28)
        );

        bubble.setContentDescription(
            "فتح أدوات بوتنا العائمة"
        );

        bubble.setOnClickListener(v -> togglePanel());

        bubbleParams = new WindowManager.LayoutParams(
            dp(56),
            dp(56),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.END;
        bubbleParams.x = dp(12);
        bubbleParams.y = dp(220);

        try {
            windowManager.addView(bubble, bubbleParams);
            bubbleView = bubble;

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

    private void showPanel() {
        if (windowManager == null || panelVisible) {
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        root.setPadding(
            dp(14), dp(14), dp(14), dp(14)
        );

        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        root.setBackground(
            round(BG, CYAN, 1, 18)
        );

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView title = label(
            "بوتنا | الأدوات", 17, TEXT, true
        );

        header.addView(
            title,
            new LinearLayout.LayoutParams(0, -2, 1)
        );

        Button close = smallButton("إخفاء", false);

        close.setOnClickListener(v -> hidePanel());

        header.addView(
            close,
            new LinearLayout.LayoutParams(-2, -2)
        );

        root.addView(header);

        // زر الرجوع
        addAction(root, "رجوع", () -> {
            boolean ok = performGlobalAction(
                GLOBAL_ACTION_BACK
            );

            report(
                ok
                    ? "تم تنفيذ أمر الرجوع."
                    : "لم يتمكن النظام من تنفيذ الرجوع."
            );
        });

        // زر الرئيسية
        addAction(root, "الرئيسية", () -> {
            boolean ok = performGlobalAction(
                GLOBAL_ACTION_HOME
            );

            report(
                ok
                    ? "تم طلب العودة إلى الرئيسية."
                    : "لم يتمكن النظام من تنفيذ الرئيسية."
            );
        });

        // قراءة عناصر الواجهة
        addAction(
            root,
            "قراءة عناصر الشاشة",
            this::readScreen
        );

        // الضغط على النص الذي حفظه المستخدم
        addAction(
            root,
            "اضغط النص المحفوظ",
            this::clickSavedText
        );

        ScrollView resultsScroll = new ScrollView(this);
        resultsScroll.setFillViewport(false);

        resultView = label(
            "اضغط أحد الأزرار لتنفيذ إجراء يدوي.",
            12,
            MUTED,
            false
        );

        resultView.setPadding(
            dp(8), dp(10), dp(8), dp(10)
        );

        resultsScroll.addView(resultView);

        LinearLayout.LayoutParams resultLp =
            new LinearLayout.LayoutParams(-1, dp(150));

        resultLp.topMargin = dp(8);
        root.addView(resultsScroll, resultLp);

        TextView safety = label(
            "تنبيه: الضغط على النص يعمل فقط إذا استطاعت "
                + "أندرويد قراءة العنصر.",
            11,
            MUTED,
            false
        );

        LinearLayout.LayoutParams safetyLp =
            new LinearLayout.LayoutParams(-1, -2);

        safetyLp.topMargin = dp(8);
        root.addView(safety, safetyLp);

        panelParams = new WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        );

        panelParams.gravity = Gravity.TOP | Gravity.END;
        panelParams.x = dp(8);
        panelParams.y = dp(285);

        try {
            windowManager.addView(root, panelParams);

            panelView = root;
            panelVisible = true;

        } catch (Exception e) {
            report(
                "تعذر فتح لوحة الأدوات: "
                    + e.getClass().getSimpleName()
            );
        }
    }

    private void hidePanel() {
        removeView(panelView);

        panelView = null;
        resultView = null;
        panelVisible = false;
    }

    private void addAction(
        LinearLayout parent,
        String title,
        Runnable action
    ) {
        Button button = smallButton(title, true);

        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(-1, -2);

        lp.topMargin = dp(7);

        parent.addView(button, lp);

        button.setOnClickListener(v -> action.run());
    }

    private Button smallButton(
        String title,
        boolean primary
    ) {
        Button button = new Button(this);

        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(13);

        button.setPadding(
            dp(6), dp(4), dp(6), dp(4)
        );

        button.setTextColor(
            primary ? Color.rgb(3, 20, 29) : TEXT
        );

        button.setBackground(
            round(
                primary ? CYAN : CARD,
                primary ? CYAN : Color.rgb(42, 82, 108),
                1,
                12
            )
        );

        return button;
    }

    private void readScreen() {
        AccessibilityNodeInfo root =
            getRootInActiveWindow();

        if (root == null) {
            updatePanel(
                "لم أستطع الوصول إلى عناصر الشاشة الحالية. "
                    + "جرّب إغلاق اللوحة وإعادة فتحها داخل "
                    + "التطبيق المطلوب."
            );

            report(
                "فشلت قراءة الشاشة: لا توجد نافذة نشطة متاحة."
            );

            return;
        }

        List<String> lines = new ArrayList<>();

        try {
            collectLabels(root, lines, 0);

            String message;

            if (lines.isEmpty()) {
                message =
                    "لم أجد نصوصًا يمكن قراءتها. قد يكون "
                    + "التطبيق يرسم واجهته بطريقة لا تعرض "
                    + "عناصرها لخدمة إمكانية الوصول.";

            } else {
                StringBuilder builder = new StringBuilder(
                    "العناصر المقروءة (حتى 35):\n"
                );

                for (String line : lines) {
                    builder.append("• ")
                        .append(line)
                        .append("\n");
                }

                message = builder.toString();
            }

            updatePanel(message);

            report(
                lines.isEmpty()
                    ? "قراءة الشاشة: لم يتم العثور على نصوص."
                    : "قراءة الشاشة: تم العثور على "
                        + lines.size()
                        + " عنصر نصي."
            );

        } finally {
            root.recycle();
        }
    }

    private void collectLabels(
        AccessibilityNodeInfo node,
        List<String> lines,
        int depth
    ) {
        if (node == null
            || lines.size() >= 35
            || depth > 30) {
            return;
        }

        CharSequence textValue = node.getText();
        CharSequence desc = node.getContentDescription();

        String value = textValue != null
            ? textValue.toString().trim()
            : "";

        if (value.isEmpty() && desc != null) {
            value = desc.toString().trim();
        }

        if (!value.isEmpty() && !lines.contains(value)) {
            lines.add(value);
        }

        int count = node.getChildCount();

        for (int i = 0;
             i < count && lines.size() < 35;
             i++) {

            AccessibilityNodeInfo child = node.getChild(i);

            if (child != null) {
                try {
                    collectLabels(
                        child, lines, depth + 1
                    );
                } finally {
                    child.recycle();
                }
            }
        }
    }

    private void clickSavedText() {
        String target = getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        ).getString(KEY_TARGET_TEXT, "").trim();

        if (target.isEmpty()) {
            updatePanel(
                "لم تحفظ نصًا بعد. ارجع إلى تطبيق Botna، "
                    + "اكتب النص ثم احفظه."
            );

            report(
                "لم ينفذ النقر: النص المستهدف غير محفوظ."
            );

            return;
        }

        AccessibilityNodeInfo root =
            getRootInActiveWindow();

        if (root == null) {
            updatePanel(
                "لا توجد نافذة نشطة يمكن فحصها. افتح "
                    + "التطبيق المطلوب ثم أعد المحاولة."
            );

            report(
                "لم ينفذ النقر على «"
                    + target
                    + "»: لا توجد نافذة نشطة."
            );

            return;
        }

        boolean clicked;

        try {
            clicked = findAndClick(root, target, true);

            if (!clicked) {
                clicked = findAndClick(
                    root, target, false
                );
            }

        } finally {
            root.recycle();
        }

        String message = clicked
            ? "أرسلت أمر النقر إلى العنصر المطابق للنص: «"
                + target
                + "». تحقق من أن التطبيق استجاب."
            : "لم أجد عنصرًا مطابقًا للنص «"
                + target
                + "». تأكد من النص أو أن العنصر ظاهر "
                + "وقابل للقراءة.";

        updatePanel(message);

        report(
            clicked
                ? "تم إرسال نقر للنص «" + target + "»."
                : "لم يتم العثور على النص «"
                    + target
                    + "». لا يوجد نقر احتياطي بالإحداثيات."
        );
    }

    private boolean findAndClick(
        AccessibilityNodeInfo node,
        String target,
        boolean exactOnly
    ) {
        if (node == null) {
            return false;
        }

        CharSequence textValue = node.getText();
        CharSequence desc = node.getContentDescription();

        String label = textValue != null
            ? textValue.toString().trim()
            : "";

        if (label.isEmpty() && desc != null) {
            label = desc.toString().trim();
        }

        boolean matches = exactOnly
            ? label.equalsIgnoreCase(target)
            : label.toLowerCase(Locale.ROOT)
                .contains(target.toLowerCase(Locale.ROOT));

        if (matches && clickNodeOrClickableParent(node)) {
            return true;
        }

        int count = node.getChildCount();

        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);

            if (child == null) {
                continue;
            }

            boolean found;

            try {
                found = findAndClick(
                    child, target, exactOnly
                );
            } finally {
                child.recycle();
            }

            if (found) {
                return true;
            }
        }

        return false;
    }

    private boolean clickNodeOrClickableParent(
        AccessibilityNodeInfo node
    ) {
        if (node.isClickable()
            && node.performAction(
                AccessibilityNodeInfo.ACTION_CLICK
            )) {
            return true;
        }

        AccessibilityNodeInfo parent = node.getParent();

        while (parent != null) {
            AccessibilityNodeInfo next = null;
            boolean clickable = parent.isClickable();

            if (clickable) {
                boolean success = parent.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                );

                parent.recycle();

                // لا نجرب عنصرًا آخر إذا فشل النقر.
                return success;
            }

            next = parent.getParent();
            parent.recycle();
            parent = next;
        }

        return false;
    }

    private void updatePanel(String value) {
        if (resultView != null) {
            resultView.setText(value);
        } else {
            Toast.makeText(
                this,
                value,
                Toast.LENGTH_LONG
            ).show();
        }
    }

    private void report(String result) {
        saveResult(result);

        if (resultView != null) {
            resultView.setText(result);
        }
    }

    private void saveResult(String result) {
        String stamp = new SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(new Date());

        getSharedPreferences(
            PREFS,
            MODE_PRIVATE
        ).edit()
            .putString(
                KEY_LAST_RESULT,
                stamp + " — " + result
            )
            .apply();
    }

    private TextView label(
        String text,
        int sp,
        int color,
        boolean bold
    ) {
        TextView view = new TextView(this);

        view.setText(text);
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

    private GradientDrawable round(
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

    private int dp(float value) {
        return Math.round(
            value * getResources()
                .getDisplayMetrics().density
        );
    }

    private void removeView(View view) {
        if (windowManager != null && view != null) {
            try {
                windowManager.removeView(view);
            } catch (Exception ignored) {
            }
        }
    }
}
