package com.botna.automation;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public final class BotnaExternalAppAgent {
    private static final String PREFS = "botna_external_app_agent";
    private static final String ENABLED = "enabled";
    private static final String SELECTED_PACKAGE = "selected_package";
    private static final String TASK_ATTACHMENT_PREFIX = "task_attachment_";
    public static final String DEEPSEEK = "com.deepseek.chat";
    public static final String CHATGPT = "com.openai.chatgpt";
    public static final String GEMINI = "com.google.android.apps.bard";
    public static final String COPILOT = "com.microsoft.copilot";

    private BotnaExternalAppAgent() { }

    public static boolean enabled(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ENABLED, true);
    }

    public static void setEnabled(Context context, boolean value) {
        if (context != null) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ENABLED, value).apply();
    }

    public static String selectedPackage(Context context) {
        return context == null ? DEEPSEEK : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SELECTED_PACKAGE, DEEPSEEK);
    }

    public static void setSelectedPackage(Context context, String packageName) {
        if (context == null || !isSupportedPackage(packageName)) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SELECTED_PACKAGE, packageName).apply();
    }

    public static String label(String packageName) {
        if (DEEPSEEK.equals(packageName)) return "DeepSeek";
        if (CHATGPT.equals(packageName)) return "ChatGPT";
        if (GEMINI.equals(packageName)) return "Gemini";
        if (COPILOT.equals(packageName)) return "Copilot";
        return "تطبيق ذكاء اصطناعي";
    }

    public static String[] labels() { return new String[]{"DeepSeek", "ChatGPT", "Gemini", "Copilot"}; }
    public static String[] packages() { return new String[]{DEEPSEEK, CHATGPT, GEMINI, COPILOT}; }

    public static boolean isSupportedPackage(String packageName) {
        for (String value : packages()) if (value.equals(packageName)) return true;
        return false;
    }

    public static boolean isInstalled(Context context, String packageName) {
        if (context == null || !isSupportedPackage(packageName)) return false;
        try { return context.getPackageManager().getLaunchIntentForPackage(packageName) != null; }
        catch (Exception ignored) { return false; }
    }

    public static String taskAttachmentKey(String taskId) { return TASK_ATTACHMENT_PREFIX + safe(taskId); }

    public static void saveTaskAttachment(Context context, String taskId, String path) {
        if (context == null || safe(taskId).isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(taskAttachmentKey(taskId), path == null ? "" : path).apply();
    }

    public static String taskAttachment(Context context, String taskId) {
        if (context == null || safe(taskId).isEmpty()) return "";
        String path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(taskAttachmentKey(taskId), "");
        return path != null && new File(path).isFile() ? path : "";
    }

    public static boolean canShareImages(Context context, String packageName, int imageCount) {
        if (context == null || !isSupportedPackage(packageName)) return false;
        Intent intent = new Intent(imageCount > 1 ? Intent.ACTION_SEND_MULTIPLE : Intent.ACTION_SEND);
        intent.setPackage(packageName);
        intent.setType("image/*");
        try { return !context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty(); }
        catch (Exception ignored) { return false; }
    }

    public static JSONObject parseAction(String raw, int imageWidth, int imageHeight) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) return null;
        ArrayList<JSONObject> objects = extractObjects(value);
        for (int i = objects.size() - 1; i >= 0; i--) {
            JSONObject parsed = convert(objects.get(i), imageWidth, imageHeight);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private static JSONObject convert(JSONObject source, int width, int height) {
        if (source == null || width <= 0 || height <= 0) return null;
        String action = source.optString("action", source.optString("type", "")).trim().toUpperCase(Locale.ROOT);
        try {
            JSONObject out = new JSONObject();
            if ("TAP".equals(action) || "CLICK".equals(action)) {
                int x = integer(source, "x", -1); int y = integer(source, "y", -1);
                if (x < 0 || y < 0 || x >= width || y >= height) return null;
                out.put("action", "TAP"); out.put("type", "TAP_COORD");
                out.put("xPercent", percent(x, width)); out.put("yPercent", percent(y, height));
                out.put("label", "نقر بالإحداثيات " + x + "," + y);
                out.put("reason", safe(source.optString("reason", "الإحداثيات ضمن أبعاد لقطة الشاشة الأصلية.")));
                return out;
            }
            if ("SWIPE".equals(action) || "SCROLL".equals(action)) {
                int x1 = integer(source, "x1", -1), y1 = integer(source, "y1", -1);
                int x2 = integer(source, "x2", -1), y2 = integer(source, "y2", -1);
                if (!inside(x1, y1, width, height) || !inside(x2, y2, width, height)) return null;
                out.put("action", "SWIPE"); out.put("type", "SWIPE");
                out.put("x1", percent(x1, width)); out.put("y1", percent(y1, height));
                out.put("x2", percent(x2, width)); out.put("y2", percent(y2, height));
                out.put("duration", Math.max(120, Math.min(2500, integer(source, "duration_ms", 450))));
                out.put("label", "سحب بالإحداثيات"); out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("TYPE".equals(action) || "WRITE".equals(action)) {
                String text = source.optString("text", source.optString("value", ""));
                if (text.isEmpty() || text.length() > 4000) return null;
                out.put("action", "TYPE"); out.put("type", "TYPE_TEXT"); out.put("value", text);
                out.put("label", "كتابة النص في الحقل المركّز"); out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("WAIT".equals(action)) {
                int ms = integer(source, "ms", integer(source, "wait_ms", 1000));
                out.put("action", "WAIT"); out.put("type", "WAIT"); out.put("delay", Math.max(250, Math.min(30000, ms)));
                out.put("label", "انتظار ذكي"); out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("BACK".equals(action) || "HOME".equals(action)) {
                out.put("action", action); out.put("type", action); out.put("label", "HOME".equals(action) ? "الرئيسية" : "رجوع");
                out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("DONE".equals(action)) {
                out.put("action", "DONE");
                out.put("reason", safe(source.optString("reason", "")));
                out.put("verification_evidence", safe(source.optString("verification_evidence", source.optString("evidence", ""))));
                return out;
            }
            if ("STOP".equals(action) || "ERROR".equals(action) || "NEED_USER".equals(action)) {
                out.put("action", "STOP");
                out.put("reason", safe(source.optString("reason", source.optString("message", "النموذج طلب تدخل المستخدم."))));
                return out;
            }
        } catch (JSONException ignored) { }
        return null;
    }

    private static int integer(JSONObject json, String key, int fallback) {
        Object value = json.opt(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return (int) Math.round(Double.parseDouble(((String) value).trim())); }
            catch (Exception ignored) { }
        }
        return fallback;
    }

    private static int percent(int value, int size) {
        return Math.max(0, Math.min(99, Math.round(value * 100f / Math.max(1, size - 1))));
    }

    private static boolean inside(int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private static ArrayList<JSONObject> extractObjects(String text) {
        ArrayList<JSONObject> result = new ArrayList<>();
        int start = -1, depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') { quoted = true; continue; }
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}' && depth > 0) {
                depth--;
                if (depth == 0 && start >= 0) {
                    try { result.add(new JSONObject(text.substring(start, i + 1))); }
                    catch (Exception ignored) { }
                    start = -1;
                }
            }
        }
        return result;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
