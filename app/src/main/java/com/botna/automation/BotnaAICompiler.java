package com.botna.automation;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Optional online natural-language command planner. Output is data, never executable code. */
public final class BotnaAICompiler {
    public static final String PREFS = "botna_settings";
    private static final String MODEL_KEY = "ai_model_v1";
    private static final String DEFAULT_MODEL = "gpt-5.4-mini";
    private static final int BG = Color.rgb(5, 7, 12);
    private static final int CARD = Color.rgb(8, 14, 22);
    private static final int CYAN = Color.rgb(35, 226, 244);
    private static final int TEXT = Color.rgb(239, 248, 255);
    private static final int MUTED = Color.rgb(151, 174, 194);

    private BotnaAICompiler() { }

    public static String model(Context context) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MODEL_KEY, DEFAULT_MODEL);
        if (value == null || value.trim().isEmpty() || "gpt-4.1-mini".equalsIgnoreCase(value.trim())) {
            return DEFAULT_MODEL;
        }
        return value.trim();
    }

    public static boolean isConfigured(Context context) { return !BotnaKeyVault.load(context).isEmpty(); }

    public static void saveSettings(Context context, String key, String model, boolean clearKey) throws Exception {
        if (clearKey) BotnaKeyVault.save(context, "");
        else if (key != null && !key.trim().isEmpty()) BotnaKeyVault.save(context, key.trim());
        String chosen = model == null || model.trim().isEmpty() ? DEFAULT_MODEL : model.trim();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MODEL_KEY, chosen).commit();
    }

    /** Calls the official Responses API. Caller must invoke this from a background thread. */
    public static JSONObject compile(Context context, String instructions, JSONArray library) throws Exception {
        String apiKey = BotnaKeyVault.load(context);
        if (apiKey.isEmpty()) throw new IllegalStateException("لم تضف مفتاح API بعد. افتح إعدادات الذكاء الاصطناعي أولًا.");
        String model = model(context);
        String system = "أنت مترجم أوامر تطبيق أتمتة أندرويد اسمه Botna. حوّل وصف المستخدم العربي إلى JSON فقط بالشكل {\"steps\":[{\"action\":\"...\",\"image_index\":1,\"text\":\"\",\"delay_ms\":0,\"duration_ms\":800,\"x_percent\":50,\"y_percent\":50,\"direction\":\"up\",\"count\":1}],\"warnings\":[\"...\"]}. لا تُخرج كود Java أو shell أو JS. كل خطوة فعل واحد مرتب بالتنفيذ. الأفعال المسموحة حصراً: find_image, tap_image, click_text, type_text, wait, tap_at, long_press_at, scroll, back, home, if_success, if_fail, else, end_if, repeat_start, repeat_end. استخدم find_image للعثور دون نقر، tap_image للنقر على قالب صورة، وimage_index هو رقم الصورة من المكتبة التي أعطيتك. إحداثيات tap_at وlong_press_at نسب مئوية 0-100. wait.delay_ms بالمللي ثانية 0-60000، long_press_at.duration_ms 300-5000، repeat_start.count 1-1000. إذا كانت هناك عبارة شرطية مثل 'إذا وجدتها انتظر ثم ارجع' فأخرج find_image ثم if_success ثم الانتظار/الرجوع ثم end_if. للشرط عند عدم العثور أخرج find_image ثم if_fail ثم الأفعال ثم end_if. للتكرار استخدم repeat_start وrepeat_end صريحين. حافظ على ترتيب المستخدم، ولا تخترع أسماء صور غير موجودة؛ أضف تحذيراً واضحاً إذا كان الأمر غامضاً أو رقم الصورة غير صالح. لا تنفذ أي فعل غير موجود في القائمة.";
        String user = "المكتبة المتاحة بصيغة JSON مع أرقامها: " + libraryForPrompt(library) + "\n\nالأمر المطلوب ترجمته: " + instructions;
        JSONArray input = new JSONArray();
        input.put(new JSONObject().put("role", "system").put("content", system));
        input.put(new JSONObject().put("role", "user").put("content", user));
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("input", input);
        body.put("max_output_tokens", 6000);
        body.put("text", new JSONObject().put("format", new JSONObject().put("type", "json_object")));

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(payload); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String response = readAll(stream);
            JSONObject json = new JSONObject(response);
            if (status < 200 || status >= 300) {
                String apiMessage = json.optJSONObject("error") == null ? "HTTP " + status : json.optJSONObject("error").optString("message", "HTTP " + status);
                throw new IllegalStateException("فشل طلب الذكاء الاصطناعي: " + apiMessage);
            }
            String outputText = extractOutputText(json);
            if (outputText.trim().isEmpty()) throw new IllegalStateException("لم يُرجع النموذج خطة أوامر نصية.");
            JSONObject plan = new JSONObject(stripJsonFences(outputText));
            if (plan.optJSONArray("steps") == null) throw new IllegalStateException("صيغة الخطة لا تحتوي على قائمة steps.");
            return plan;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String libraryForPrompt(JSONArray library) throws JSONException {
        JSONArray out = new JSONArray();
        if (library != null) for (int i = 0; i < library.length(); i++) {
            JSONObject source = library.optJSONObject(i);
            if (source == null) continue;
            JSONObject item = new JSONObject();
            item.put("image_index", i + 1);
            item.put("name", source.optString("name", "صورة " + (i + 1)));
            item.put("default_threshold", source.optInt("threshold", 85));
            out.put(item);
        }
        return out.toString();
    }

    private static String extractOutputText(JSONObject response) throws JSONException {
        String direct = response.optString("output_text", "");
        if (!direct.isEmpty()) return direct;
        JSONArray output = response.optJSONArray("output");
        if (output != null) for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null || !"message".equals(item.optString("type"))) continue;
            JSONArray content = item.optJSONArray("content");
            if (content == null) continue;
            for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && "output_text".equals(part.optString("type"))) return part.optString("text", "");
                if (part != null && part.has("text")) return part.optString("text", "");
            }
        }
        return "";
    }

    private static String stripJsonFences(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            int first = text.indexOf('\n');
            int last = text.lastIndexOf("```");
            if (first >= 0 && last > first) text = text.substring(first + 1, last).trim();
        }
        return text;
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Provider settings UI. API keys are encrypted using Android Keystore on this device. */
    public static void showSettingsDialog(Context context, boolean accessibilityOverlay, Runnable onSaved) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * context.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        TextView status = new TextView(context);
        status.setText(BotnaKeyVault.mask(context) + "\nالمفتاح لا يُضمّن في ملفات المشروع. استخدام API قد يستهلك رصيدًا مدفوعًا؛ يلزم الإنترنت.");
        status.setTextColor(MUTED); status.setTextSize(13); status.setGravity(Gravity.RIGHT);
        container.addView(status, new LinearLayout.LayoutParams(-1, -2));
        EditText key = new EditText(context);
        key.setSingleLine(true); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setHint("ألصق مفتاح API (اتركه فارغًا للاحتفاظ بالمحفوظ)"); key.setHintTextColor(MUTED); key.setTextColor(TEXT); key.setTextSize(14);
        key.setBackground(shape(context, CARD, CYAN)); key.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(-1, -2); keyLp.topMargin = pad; container.addView(key, keyLp);
        EditText model = new EditText(context);
        model.setSingleLine(true); model.setText(model(context)); model.setHint("اسم النموذج من منصة API"); model.setTextColor(TEXT); model.setHintTextColor(MUTED); model.setTextSize(14);
        model.setBackground(shape(context, CARD, CYAN)); model.setPadding(pad, pad / 2, pad, pad / 2);
        LinearLayout.LayoutParams modelLp = new LinearLayout.LayoutParams(-1, -2); modelLp.topMargin = pad / 2; container.addView(model, modelLp);
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("إعدادات الذكاء الاصطناعي")
                .setView(container)
                .setPositiveButton("حفظ الإعدادات", null)
                .setNeutralButton("مسح المفتاح", null)
                .setNegativeButton("إغلاق", null).create();
        try {
            if (dialog.getWindow() != null && accessibilityOverlay) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    saveSettings(context, key.getText().toString(), model.getText().toString(), false);
                    Toast.makeText(context, "تم حفظ إعدادات الذكاء الاصطناعي على الجهاز.", Toast.LENGTH_LONG).show();
                    dialog.dismiss(); if (onSaved != null) onSaved.run();
                } catch (Exception e) { Toast.makeText(context, "تعذر حفظ المفتاح: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                AlertDialog confirm = new AlertDialog.Builder(context)
                        .setTitle("مسح مفتاح API")
                        .setMessage("هل تريد حذف المفتاح المشفّر من هذا الجهاز؟")
                        .setPositiveButton("مسح", (d, w) -> {
                            try { saveSettings(context, "", model.getText().toString(), true); Toast.makeText(context, "تم مسح المفتاح.", Toast.LENGTH_SHORT).show(); dialog.dismiss(); if (onSaved != null) onSaved.run(); }
                            catch (Exception e) { Toast.makeText(context, "تعذر مسح المفتاح.", Toast.LENGTH_LONG).show(); }
                        })
                        .setNegativeButton("إلغاء", null).create();
                try { if (confirm.getWindow() != null && accessibilityOverlay) confirm.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); confirm.show(); }
                catch (Exception e) { Toast.makeText(context, "تعذر فتح تأكيد المسح.", Toast.LENGTH_LONG).show(); }
            });
        } catch (Exception e) {
            Toast.makeText(context, "تعذر فتح إعدادات الذكاء الاصطناعي.", Toast.LENGTH_LONG).show();
        }
    }

    private static GradientDrawable shape(Context context, int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill); shape.setCornerRadius(18 * context.getResources().getDisplayMetrics().density);
        shape.setStroke(Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)), stroke);
        return shape;
    }
}
