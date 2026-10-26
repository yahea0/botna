package com.botna.automation;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;

public final class BotnaAiTemplateSettingsActivity extends Activity {
    public static final String EXTRA_PACKAGE = "botna_template_package";
    public static final String ACTION_SETTINGS_CLOSED = "com.botna.automation.TEMPLATE_SETTINGS_CLOSED";
    private static final int REQUEST_COMPOSER_IMAGE = 4101;
    private static final int REQUEST_SEND_IMAGE = 4102;
    private String packageName = BotnaExternalAppAgent.DEEPSEEK;
    private String pendingRole = "";
    private TextView composerStatus;
    private TextView sendStatus;
    private EditText thresholdInput;
    private EditText responseWaitInput;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String requested = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_PACKAGE);
        if (BotnaExternalAppAgent.isSupportedPackage(requested)) packageName = requested;
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(13, 22, 35));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("صور الحقل وزر الإرسال — " + BotnaExternalAppAgent.label(packageName), 20, Color.WHITE, true);
        root.addView(title, params(-1, -2, 0, 0, 0, dp(10)));
        TextView guidance = text("اختر قصاصة صغيرة وواضحة من لقطة الشاشة لكل عنصر. صورة حقل الإدخال تحدد مكان النقر قبل لصق التعليمات، وصورة زر الإرسال يجب أن تمثل الزر عندما يصبح جاهزًا للإرسال. البحث يجري على كامل الشاشة مع تكرار المحاولة.", 14, 0xFFB8C7D8, false);
        root.addView(guidance, params(-1, -2, 0, 0, 0, dp(16)));

        composerStatus = text("صورة حقل الإدخال: " + statusFor(BotnaExternalAppAgent.TEMPLATE_COMPOSER), 14, Color.WHITE, false);
        root.addView(composerStatus, params(-1, -2, 0, 0, 0, dp(6)));
        Button composerButton = button("اختيار صورة حقل الإدخال");
        composerButton.setOnClickListener(v -> openImagePicker(BotnaExternalAppAgent.TEMPLATE_COMPOSER));
        root.addView(composerButton, params(-1, dp(48), 0, 0, 0, dp(12)));

        sendStatus = text("صورة زر الإرسال: " + statusFor(BotnaExternalAppAgent.TEMPLATE_SEND), 14, Color.WHITE, false);
        root.addView(sendStatus, params(-1, -2, 0, 0, 0, dp(6)));
        Button sendButton = button("اختيار صورة زر الإرسال");
        sendButton.setOnClickListener(v -> openImagePicker(BotnaExternalAppAgent.TEMPLATE_SEND));
        root.addView(sendButton, params(-1, dp(48), 0, 0, 0, dp(18)));

        thresholdInput = numberField(String.valueOf(BotnaExternalAppAgent.visualThreshold(this, packageName)), "85");
        root.addView(text("دقة التشابه المطلوبة (50–100%)", 14, Color.WHITE, true), params(-1, -2, 0, 0, 0, dp(5)));
        root.addView(thresholdInput, params(-1, dp(52), 0, 0, 0, dp(14)));

        responseWaitInput = numberField(String.valueOf(BotnaExternalAppAgent.responseWaitMs(this, packageName) / 1000), "15");
        root.addView(text("الانتظار قبل قراءة رد النموذج (ثانية)", 14, Color.WHITE, true), params(-1, -2, 0, 0, 0, dp(5)));
        root.addView(responseWaitInput, params(-1, dp(52), 0, 0, 0, dp(18)));

        TextView footer = text("لا تستخدم صورة شاشة كاملة كقالب إذا أمكن قص العنصر نفسه؛ القالب الصغير يقلل اختلافات الواجهة. ستبقى هذه الإعدادات خاصة بالنموذج المختار.", 12, 0xFF9FB0C4, false);
        root.addView(footer, params(-1, -2, 0, 0, 0, dp(18)));
        Button save = button("حفظ الإعدادات والعودة");
        save.setOnClickListener(v -> saveAndFinish());
        root.addView(save, params(-1, dp(52), 0, 0, 0, 0));
        setContentView(scroll);
    }

    private void openImagePicker(String role) {
        pendingRole = role;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, BotnaExternalAppAgent.TEMPLATE_COMPOSER.equals(role) ? REQUEST_COMPOSER_IMAGE : REQUEST_SEND_IMAGE);
        } catch (Exception e) {
            Toast.makeText(this, "تعذر فتح معرض الصور.", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        String role = requestCode == REQUEST_COMPOSER_IMAGE ? BotnaExternalAppAgent.TEMPLATE_COMPOSER
                : requestCode == REQUEST_SEND_IMAGE ? BotnaExternalAppAgent.TEMPLATE_SEND : pendingRole;
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            saveTemplate(uri, role);
            refreshStatuses();
            Toast.makeText(this, BotnaExternalAppAgent.TEMPLATE_COMPOSER.equals(role) ? "تم حفظ صورة حقل الإدخال." : "تم حفظ صورة زر الإرسال.", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "تعذر حفظ الصورة المحددة. اختر صورة أصغر أو قصاصة أوضح.", Toast.LENGTH_LONG).show();
        }
    }

    private void saveTemplate(Uri uri, String role) throws Exception {
        File destination = BotnaExternalAppAgent.visualTemplateFile(this, packageName, role);
        if (destination == null) throw new IllegalStateException("template-path");
        Bitmap source;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("template-stream");
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream boundsInput = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(boundsInput, null, bounds);
            }
            if (bounds.outWidth < 8 || bounds.outHeight < 8) throw new IllegalArgumentException("template-dimensions");
            int sample = 1;
            while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            source = BitmapFactory.decodeStream(input, null, options);
        }
        if (source == null || source.isRecycled()) throw new IllegalStateException("template-decode");
        try (FileOutputStream output = new FileOutputStream(destination, false)) {
            if (!source.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IllegalStateException("template-compress");
            output.flush();
            output.getFD().sync();
        } finally {
            if (!source.isRecycled()) source.recycle();
        }
    }

    private void saveAndFinish() {
        long threshold = parseNumber(thresholdInput, 85);
        long responseSeconds = parseNumber(responseWaitInput, 15);
        BotnaExternalAppAgent.setVisualThreshold(this, packageName, (int) Math.max(50, Math.min(100, threshold)));
        long responseMs = responseSeconds <= 0L ? 0L
                : responseSeconds >= Integer.MAX_VALUE / 1000L ? Integer.MAX_VALUE
                : responseSeconds * 1000L;
        BotnaExternalAppAgent.setResponseWaitMs(this, packageName, (int) responseMs);
        Toast.makeText(this, "تم حفظ إعدادات " + BotnaExternalAppAgent.label(packageName), Toast.LENGTH_SHORT).show();
        finish();
    }

    private long parseNumber(EditText input, long fallback) {
        try { return Long.parseLong(input.getText().toString().trim()); } catch (Exception ignored) { return fallback; }
    }

    private String statusFor(String role) {
        File file = BotnaExternalAppAgent.visualTemplateFile(this, packageName, role);
        return file != null && file.isFile() && file.length() > 0 ? "محفوظة" : "لم تُختر بعد";
    }

    private void refreshStatuses() {
        composerStatus.setText("صورة حقل الإدخال: " + statusFor(BotnaExternalAppAgent.TEMPLATE_COMPOSER));
        sendStatus.setText("صورة زر الإرسال: " + statusFor(BotnaExternalAppAgent.TEMPLATE_SEND));
    }

    private EditText numberField(String value, String hint) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        edit.setText(value);
        edit.setHint(hint);
        edit.setTextColor(Color.WHITE);
        edit.setHintTextColor(0xFF94A3B8);
        edit.setTextSize(17);
        edit.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        edit.setPadding(dp(14), dp(8), dp(14), dp(8));
        edit.setBackgroundColor(0xFF152638);
        return edit;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF126E8A));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(null, android.graphics.Typeface.BOLD);
        return text;
    }

    private LinearLayout.LayoutParams params(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(left, top, right, bottom);
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void finish() {
        try { sendBroadcast(new Intent(ACTION_SETTINGS_CLOSED).setPackage(getPackageName())); } catch (Exception ignored) { }
        super.finish();
    }
}
