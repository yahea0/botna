package com.botna.automation;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
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
    private ImageView composerPreview;
    private ImageView sendPreview;
    private Button composerButton;
    private Button sendButton;
    private EditText thresholdInput;
    private EditText responseWaitInput;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String requested = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_PACKAGE);
        if (BotnaExternalAppAgent.isSupportedPackage(requested)) packageName = requested;
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(20));
        root.setBackgroundColor(Color.rgb(13, 22, 35));
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        TextView title = text("صور الحقل وزر الإرسال — " + BotnaExternalAppAgent.label(packageName), 20, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        root.addView(title, params(-1, -2, 0, 0, 0, dp(8)));
        TextView guidance = text("اختر قصاصة صغيرة وواضحة من لقطة الشاشة لكل عنصر. صورة حقل الإدخال تحدد مكان النقر قبل لصق التعليمات، وصورة زر الإرسال تحدد مكان النقر عندما يصبح جاهزًا. يمكنك معاينة الصورة الحالية واستبدالها في أي وقت.", 14, 0xFFB8C7D8, false);
        root.addView(guidance, params(-1, -2, 0, 0, 0, dp(14)));

        addTemplateSection(root, true);
        addTemplateSection(root, false);

        LinearLayout settingsCard = new LinearLayout(this);
        settingsCard.setOrientation(LinearLayout.VERTICAL);
        settingsCard.setPadding(dp(12), dp(12), dp(12), dp(12));
        settingsCard.setBackground(panelBackground());
        root.addView(settingsCard, params(-1, -2, 0, dp(2), 0, dp(12)));

        TextView thresholdLabel = text("دقة التشابه المطلوبة (50–100%)", 15, Color.WHITE, true);
        thresholdLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        settingsCard.addView(thresholdLabel, params(-1, -2, 0, 0, 0, dp(5)));
        thresholdInput = numberField(String.valueOf(BotnaExternalAppAgent.visualThreshold(this, packageName)), "85");
        settingsCard.addView(thresholdInput, params(-1, dp(50), 0, 0, 0, dp(12)));

        TextView responseLabel = text("الانتظار قبل قراءة رد النموذج (ثانية)", 15, Color.WHITE, true);
        responseLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        settingsCard.addView(responseLabel, params(-1, -2, 0, 0, 0, dp(5)));
        responseWaitInput = numberField(String.valueOf(BotnaExternalAppAgent.responseWaitMs(this, packageName) / 1000), "15");
        settingsCard.addView(responseWaitInput, params(-1, dp(50), 0, 0, 0, 0));

        TextView footer = text("تظهر معاينة كل صورة بعد حفظها. اضغط زر الاختيار مرة أخرى لاستبدالها. تبقى الصور والإعدادات خاصة بالنموذج المختار.", 12, 0xFF9FB0C4, false);
        root.addView(footer, params(-1, -2, 0, 0, 0, dp(12)));
        Button save = button("حفظ الإعدادات والعودة");
        save.setOnClickListener(v -> saveAndFinish());
        root.addView(save, params(-1, dp(48), 0, 0, 0, dp(2)));

        setContentView(scroll);
        refreshStatuses();
        root.requestFocus();
    }

    private void addTemplateSection(LinearLayout root, boolean composer) {
        String role = composer ? BotnaExternalAppAgent.TEMPLATE_COMPOSER : BotnaExternalAppAgent.TEMPLATE_SEND;
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(12), dp(10), dp(12), dp(12));
        section.setBackground(panelBackground());
        root.addView(section, params(-1, -2, 0, 0, 0, dp(12)));

        TextView title = text(composer ? "صورة حقل الإدخال" : "صورة زر الإرسال", 15, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        section.addView(title, params(-1, -2, 0, 0, 0, dp(4)));

        TextView status = text("", 12, 0xFFB8C7D8, false);
        status.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        section.addView(status, params(-1, -2, 0, 0, 0, dp(6)));

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setAdjustViewBounds(false);
        preview.setPadding(dp(5), dp(5), dp(5), dp(5));
        preview.setBackground(roundShape(0xFF0B1522, 0xFF29485C, 1, 10));
        preview.setVisibility(View.GONE);
        section.addView(preview, params(-1, dp(92), 0, 0, 0, dp(8)));

        Button choose = button("اختيار صورة " + (composer ? "حقل الإدخال" : "زر الإرسال"));
        choose.setOnClickListener(v -> openImagePicker(role));
        section.addView(choose, params(-1, dp(44), 0, 0, 0, 0));

        if (composer) {
            composerStatus = status;
            composerPreview = preview;
            composerButton = choose;
        } else {
            sendStatus = status;
            sendPreview = preview;
            sendButton = choose;
        }
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
        File composerFile = BotnaExternalAppAgent.visualTemplateFile(this, packageName, BotnaExternalAppAgent.TEMPLATE_COMPOSER);
        File sendFile = BotnaExternalAppAgent.visualTemplateFile(this, packageName, BotnaExternalAppAgent.TEMPLATE_SEND);
        boolean hasComposer = composerFile != null && composerFile.isFile() && composerFile.length() > 0;
        boolean hasSend = sendFile != null && sendFile.isFile() && sendFile.length() > 0;
        if (composerStatus != null) composerStatus.setText(hasComposer ? "الصورة محفوظة — اضغط لتغييرها" : "لم تُختر صورة بعد");
        if (sendStatus != null) sendStatus.setText(hasSend ? "الصورة محفوظة — اضغط لتغييرها" : "لم تُختر صورة بعد");
        if (composerButton != null) composerButton.setText(hasComposer ? "تغيير صورة حقل الإدخال" : "اختيار صورة حقل الإدخال");
        if (sendButton != null) sendButton.setText(hasSend ? "تغيير صورة زر الإرسال" : "اختيار صورة زر الإرسال");
        loadPreview(composerPreview, composerFile);
        loadPreview(sendPreview, sendFile);
    }

    private void loadPreview(ImageView target, File file) {
        if (target == null) return;
        if (file == null || !file.isFile() || file.length() <= 0) {
            target.setImageDrawable(null);
            target.setVisibility(View.GONE);
            return;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            target.setImageDrawable(null);
            target.setVisibility(View.GONE);
            return;
        }
        int sample = 1;
        while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 240) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (bitmap == null) {
            target.setImageDrawable(null);
            target.setVisibility(View.GONE);
            return;
        }
        target.setImageBitmap(bitmap);
        target.setContentDescription("معاينة الصورة المرجعية المحفوظة");
        target.setVisibility(View.VISIBLE);
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
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        button.setStateListAnimator(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x5535E2F4), roundShape(0xFF126E8A, 0xFF35CFE4, 1, 10), null));
        return button;
    }

    private GradientDrawable roundShape(int fill, int stroke, int strokeWidth, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), stroke);
        return drawable;
    }

    private GradientDrawable panelBackground() {
        return roundShape(0xFF101D2B, 0xFF29485C, 1, 12);
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
