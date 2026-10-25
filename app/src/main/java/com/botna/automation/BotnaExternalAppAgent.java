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

    public static boolean hasExplicitlySelectedPackage(Context context) {
        return context != null && context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(SELECTED_PACKAGE);
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

    public static boolean canShareVideo(Context context, String packageName) {
        if (context == null || !isSupportedPackage(packageName)) return false;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setPackage(packageName);
        intent.setType("video/mp4");
        try { return !context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty(); }
        catch (Exception ignored) { return false; }
    }

    public static boolean canShareMixedAttachments(Context context, String packageName) {
        if (context == null || !isSupportedPackage(packageName)) return false;
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setPackage(packageName);
        intent.setType("*/*");
        try { return !context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty(); }
        catch (Exception ignored) { return false; }
    }

    public static JSONObject parseAction(String raw, int imageWidth, int imageHeight) {
        String value = stripCodeFence(raw);
        if (value.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null;
        ArrayList<String> roots = extractJsonRoots(value);
        if (roots.size() == 1) {
            String root = roots.get(0);
            try {
                if (root.charAt(0) == '[') {
                    return convertSequence(new org.json.JSONArray(root), imageWidth, imageHeight, "تنفيذ الإجراءات المرتبة التي أعادها النموذج.");
                }
                if (root.charAt(0) == '{') {
                    JSONObject source = new JSONObject(root);
                    JSONObject sequence = convertSequence(source, imageWidth, imageHeight);
                    if (sequence != null) return sequence;
                    return convert(source, imageWidth, imageHeight);
                }
            } catch (Exception ignored) {
                return null;
            }
        } else if (roots.size() > 1) {
            ArrayList<JSONObject> rootActions = new ArrayList<>();
            for (String root : roots) {
                try {
                    if (root.startsWith("[")) return null;
                    JSONObject source = new JSONObject(root);
                    JSONObject sequence = convertSequence(source, imageWidth, imageHeight);
                    if (sequence != null) return null;
                    boolean actionObject = source.has("action") || source.has("type");
                    JSONObject parsed = convert(source, imageWidth, imageHeight);
                    if (parsed != null) rootActions.add(parsed);
                    else if (actionObject) return null;
                } catch (Exception ignored) {
                    return null;
                }
            }
            if (rootActions.size() == 1) return rootActions.get(0);
            if (rootActions.size() >= 2 && rootActions.size() <= 8) return wrapSequence(rootActions, "تنفيذ الإجراءات المرتبة المستخرجة من رد النموذج.");
            return null;
        }
        ArrayList<JSONObject> objects = extractObjects(value);
        ArrayList<JSONObject> recoveredActions = new ArrayList<>();
        boolean invalidActionObject = false;
        for (JSONObject source : objects) {
            JSONObject sequence = convertSequence(source, imageWidth, imageHeight);
            if (sequence != null) return sequence;
            boolean actionObject = source.has("action") || source.has("type");
            JSONObject parsed = convert(source, imageWidth, imageHeight);
            if (parsed != null) recoveredActions.add(parsed);
            else if (actionObject) invalidActionObject = true;
        }
        if (invalidActionObject || recoveredActions.isEmpty() || recoveredActions.size() > 8) return null;
        if (recoveredActions.size() == 1) return recoveredActions.get(0);
        return wrapSequence(recoveredActions, "تنفيذ الإجراءات المرتبة المستخرجة من رد النموذج.");
    }

    public static JSONObject parseLatestAction(String raw, int imageWidth, int imageHeight) {
        String value = stripCodeFence(raw);
        if (value.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null;
        ArrayList<String> roots = extractJsonRoots(value);
        if (roots.isEmpty()) return null;
        String root = roots.get(roots.size() - 1);
        int rootStart = value.lastIndexOf(root);
        int rootEnd = rootStart < 0 ? -1 : rootStart + root.length();
        int lastActionMarker = Math.max(value.lastIndexOf("\"action\""), value.lastIndexOf("\"type\""));
        if (rootEnd < 0 || lastActionMarker > rootEnd) return null;
        try {
            if (root.startsWith("[")) return convertSequence(new org.json.JSONArray(root), imageWidth, imageHeight, "تنفيذ الإجراءات المرتبة التي أعادها النموذج.");
            JSONObject source = new JSONObject(root);
            JSONObject sequence = convertSequence(source, imageWidth, imageHeight);
            if (sequence != null) return sequence;
            return convert(source, imageWidth, imageHeight);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static JSONObject convertSequence(JSONObject source, int width, int height) {
        if (source == null || width <= 0 || height <= 0) return null;
        org.json.JSONArray input = source.optJSONArray("actions");
        if (input == null) input = source.optJSONArray("steps");
        if (input == null) input = source.optJSONArray("commands");
        if (input == null) return null;
        return convertSequence(input, width, height, source.optString("reason", "تنفيذ الإجراءات المرتبة التي أعادها النموذج."));
    }

    private static JSONObject convertSequence(org.json.JSONArray input, int width, int height, String reason) {
        if (input == null || width <= 0 || height <= 0 || input.length() < 1 || input.length() > 8) return null;
        org.json.JSONArray normalized = new org.json.JSONArray();
        try {
            for (int i = 0; i < input.length(); i++) {
                JSONObject item = input.optJSONObject(i);
                if (item == null) return null;
                JSONObject converted = convert(item, width, height);
                if (converted == null) return null;
                String kind = converted.optString("action", "").toUpperCase(Locale.ROOT);
                if (input.length() > 1 && ("DONE".equals(kind) || "STOP".equals(kind) || "SEQUENCE".equals(kind))) return null;
                normalized.put(converted);
            }
            if (normalized.length() == 1) return normalized.optJSONObject(0);
            JSONObject out = new JSONObject();
            out.put("action", "SEQUENCE");
            out.put("type", "SEQUENCE");
            out.put("steps", normalized);
            out.put("reason", safe(reason));
            out.put("label", "سلسلة من " + normalized.length() + " إجراءات");
            return out;
        } catch (JSONException e) {
            return null;
        }
    }

    private static JSONObject wrapSequence(ArrayList<JSONObject> actions, String reason) {
        if (actions == null || actions.size() < 2 || actions.size() > 8) return null;
        try {
            org.json.JSONArray normalized = new org.json.JSONArray();
            for (JSONObject action : actions) {
                if (action == null) return null;
                String kind = action.optString("action", "").toUpperCase(Locale.ROOT);
                if ("DONE".equals(kind) || "STOP".equals(kind) || "SEQUENCE".equals(kind)) return null;
                normalized.put(new JSONObject(action.toString()));
            }
            JSONObject out = new JSONObject();
            out.put("action", "SEQUENCE");
            out.put("type", "SEQUENCE");
            out.put("steps", normalized);
            out.put("reason", safe(reason));
            out.put("label", "سلسلة من " + normalized.length() + " إجراءات");
            return out;
        } catch (JSONException e) {
            return null;
        }
    }

    private static String stripCodeFence(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        if (value.startsWith("```") ) {
            int newline = value.indexOf('\n');
            if (newline >= 0) value = value.substring(newline + 1);
            int closing = value.lastIndexOf("```");
            if (closing >= 0) value = value.substring(0, closing);
        }
        return value.trim();
    }

    private static ArrayList<String> extractJsonRoots(String value) {
        ArrayList<String> roots = new ArrayList<>();
        if (value == null || value.isEmpty()) return roots;
        java.util.ArrayDeque<Character> stack = new java.util.ArrayDeque<>();
        int start = -1;
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (start < 0) {
                if (c == '{' || c == '[') {
                    start = i;
                    stack.push(c);
                    quoted = false;
                    escaped = false;
                }
                continue;
            }
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (c == '"') {
                quoted = true;
            } else if (c == '{' || c == '[') {
                stack.push(c);
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty()) {
                    start = -1;
                    continue;
                }
                char open = stack.pop();
                if ((open == '{' && c != '}') || (open == '[' && c != ']')) {
                    stack.clear();
                    start = -1;
                    quoted = false;
                    escaped = false;
                    continue;
                }
                if (stack.isEmpty()) {
                    roots.add(value.substring(start, i + 1).trim());
                    start = -1;
                    quoted = false;
                    escaped = false;
                }
            }
        }
        return roots;
    }

    private static JSONObject convert(JSONObject source, int width, int height) {
        if (source == null || width <= 0 || height <= 0) return null;
        String action = source.optString("action", source.optString("type", "")).trim().toUpperCase(Locale.ROOT);
        try {
            JSONObject out = new JSONObject();
            if ("TAP".equals(action) || "CLICK".equals(action) || "TAP_AT".equals(action)) {
                int x = integerAny(source, -1, "x", "tap_x", "tapX", "screen_x");
                int y = integerAny(source, -1, "y", "tap_y", "tapY", "screen_y");
                if (!inside(x, y, width, height)) return null;
                out.put("action", "TAP");
                out.put("type", "TAP_COORD");
                out.put("xPercent", percent(x, width));
                out.put("yPercent", percent(y, height));
                out.put("screenX", x);
                out.put("screenY", y);
                out.put("screenWidth", width);
                out.put("screenHeight", height);
                out.put("label", "نقر بالإحداثيات " + x + "," + y);
                out.put("reason", safe(source.optString("reason", "الإحداثيات ضمن أبعاد لقطة الشاشة الأصلية.")));
                addAfterDelay(out, source, 350);
                return out;
            }
            if ("LONG_PRESS".equals(action) || "LONGPRESS".equals(action) || "PRESS_HOLD".equals(action)) {
                int x = integerAny(source, -1, "x", "tap_x", "tapX", "screen_x");
                int y = integerAny(source, -1, "y", "tap_y", "tapY", "screen_y");
                if (!inside(x, y, width, height)) return null;
                out.put("action", "LONG_PRESS");
                out.put("type", "LONG_PRESS_COORD");
                out.put("xPercent", percent(x, width));
                out.put("yPercent", percent(y, height));
                out.put("screenX", x);
                out.put("screenY", y);
                out.put("screenWidth", width);
                out.put("screenHeight", height);
                out.put("duration", Math.max(300, Math.min(5000, integer(source, "duration_ms", 850))));
                out.put("label", "ضغط مطوّل عند " + x + "," + y);
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 350);
                return out;
            }
            if ("SWIPE".equals(action) || "DRAG".equals(action)) {
                int x1 = integerAny(source, -1, "x1", "start_x", "startX", "from_x", "fromX", "x_start");
                int y1 = integerAny(source, -1, "y1", "start_y", "startY", "from_y", "fromY", "y_start");
                int x2 = integerAny(source, -1, "x2", "end_x", "endX", "to_x", "toX", "x_end");
                int y2 = integerAny(source, -1, "y2", "end_y", "endY", "to_y", "toY", "y_end");
                if (!inside(x1, y1, width, height) || !inside(x2, y2, width, height)) return null;
                out.put("action", "SWIPE");
                out.put("type", "SWIPE");
                out.put("x1", percent(x1, width));
                out.put("y1", percent(y1, height));
                out.put("x2", percent(x2, width));
                out.put("y2", percent(y2, height));
                out.put("screenX1", x1);
                out.put("screenY1", y1);
                out.put("screenX2", x2);
                out.put("screenY2", y2);
                out.put("screenWidth", width);
                out.put("screenHeight", height);
                out.put("duration", Math.max(100, Math.min(5000, integerAny(source, 450, "duration_ms", "duration", "durationMs", "duration_millis"))));
                out.put("label", "سحب بالإحداثيات");
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 450);
                return out;
            }
            if ("SCROLL".equals(action) || "SCROLL_SCREEN".equals(action)) {
                String direction = source.optString("direction", "down").trim().toLowerCase(Locale.ROOT);
                if (!("up".equals(direction) || "down".equals(direction) || "left".equals(direction) || "right".equals(direction))) return null;
                out.put("action", "SCROLL");
                out.put("type", "SCROLL");
                out.put("direction", direction);
                out.put("duration", Math.max(100, Math.min(5000, integer(source, "duration_ms", 450))));
                out.put("label", "تمرير الشاشة " + direction);
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 450);
                return out;
            }
            if ("TYPE".equals(action) || "WRITE".equals(action) || "TYPE_TEXT".equals(action) || "ENTER_TEXT".equals(action)) {
                String text = source.optString("text", source.optString("value", ""));
                if (text.isEmpty() || text.length() > 4000) return null;
                out.put("action", "TYPE");
                out.put("type", "TYPE_TEXT");
                out.put("value", text);
                out.put("label", "كتابة النص في الحقل المركّز");
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 250);
                return out;
            }
            if ("CLICK_TEXT".equals(action) || "TAP_TEXT".equals(action) || "TEXT_CLICK".equals(action)) {
                String text = source.optString("text", source.optString("value", source.optString("label_text", ""))).trim();
                if (text.isEmpty() || text.length() > 180) return null;
                out.put("action", "CLICK_TEXT");
                out.put("type", "TEXT_CLICK");
                out.put("value", text);
                out.put("label", "نقر على النص الظاهر: " + text);
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 350);
                return out;
            }
            if ("WAIT".equals(action) || "SLEEP".equals(action) || "DELAY".equals(action)) {
                int ms = integer(source, "ms", integer(source, "wait_ms", 1000));
                out.put("action", "WAIT");
                out.put("type", "WAIT");
                out.put("delay", Math.max(250, Math.min(60000, ms)));
                out.put("label", "انتظار " + Math.max(250, Math.min(60000, ms)) + " ms");
                out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("BACK".equals(action) || "HOME".equals(action)) {
                out.put("action", action);
                out.put("type", action);
                out.put("label", "HOME".equals(action) ? "الرئيسية" : "رجوع");
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 650);
                return out;
            }
            if ("OPEN_APP".equals(action) || "LAUNCH_APP".equals(action) || "START_APP".equals(action) || "APP".equals(action)) {
                String appName = source.optString("app_name", source.optString("appName", source.optString("package", source.optString("name", source.optString("value", ""))))).trim();
                if (appName.isEmpty() || appName.length() > 180) return null;
                out.put("action", "OPEN_APP");
                out.put("type", "LAUNCH_APP");
                out.put("appName", appName);
                out.put("label", "فتح التطبيق: " + appName);
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 3500);
                return out;
            }
            if ("OPEN_URL".equals(action) || "OPEN_LINK".equals(action) || "VISIT_URL".equals(action)) {
                String url = source.optString("url", source.optString("value", "")).trim();
                if (!(url.startsWith("https://") || url.startsWith("http://")) || url.length() > 2048) return null;
                out.put("action", "OPEN_URL");
                out.put("type", "OPEN_URL");
                out.put("url", url);
                out.put("label", "فتح الرابط: " + url);
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 2200);
                return out;
            }
            if ("ENTER".equals(action) || "PRESS_ENTER".equals(action) || "SUBMIT".equals(action) || "SEARCH".equals(action)) {
                out.put("action", "PRESS_ENTER");
                out.put("type", "PRESS_ENTER");
                out.put("label", "إرسال Enter أو بحث");
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 750);
                return out;
            }
            if ("CLEAR_TEXT".equals(action) || "CLEAR_INPUT".equals(action) || "ERASE_FIELD".equals(action)) {
                out.put("action", "CLEAR_TEXT");
                out.put("type", "CLEAR_TEXT");
                out.put("label", "مسح حقل الإدخال المركّز");
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 300);
                return out;
            }
            if ("GLOBAL_ACTION".equals(action) || "SYSTEM_ACTION".equals(action)
                    || "RECENTS".equals(action) || "RECENT_APPS".equals(action) || "APP_SWITCHER".equals(action)
                    || "NOTIFICATIONS".equals(action) || "OPEN_NOTIFICATIONS".equals(action)
                    || "OPEN_NOTIFICATION_SHADE".equals(action) || "NOTIFICATION_SHADE".equals(action)
                    || "QUICK_SETTINGS".equals(action) || "OPEN_QUICK_SETTINGS".equals(action) || "QUICK_SETTINGS_PANEL".equals(action)
                    || "DISMISS_NOTIFICATIONS".equals(action) || "DISMISS_NOTIFICATION_SHADE".equals(action)
                    || "CLOSE_RECENTS".equals(action) || "CLOSE_RECENT_APPS".equals(action) || "OPEN_RECENTS".equals(action)) {
                String systemAction = source.optString("system_action", source.optString("systemAction", source.optString("value", ""))).trim().toLowerCase(Locale.ROOT);
                if ("RECENTS".equals(action) || "RECENT_APPS".equals(action) || "APP_SWITCHER".equals(action) || "OPEN_RECENTS".equals(action)) systemAction = "recent_apps";
                else if ("NOTIFICATIONS".equals(action) || "OPEN_NOTIFICATIONS".equals(action) || "OPEN_NOTIFICATION_SHADE".equals(action) || "NOTIFICATION_SHADE".equals(action)) systemAction = "notifications";
                else if ("QUICK_SETTINGS".equals(action) || "OPEN_QUICK_SETTINGS".equals(action) || "QUICK_SETTINGS_PANEL".equals(action)) systemAction = "quick_settings";
                else if ("DISMISS_NOTIFICATIONS".equals(action) || "DISMISS_NOTIFICATION_SHADE".equals(action)) systemAction = "dismiss_notifications";
                else if ("CLOSE_RECENTS".equals(action) || "CLOSE_RECENT_APPS".equals(action)) {
                    out.put("action", "CLOSE_RECENTS");
                    out.put("type", "CLOSE_RECENTS");
                    out.put("label", "إغلاق التطبيقات الأخيرة");
                    out.put("reason", safe(source.optString("reason", "")));
                    return out;
                }
                if ("recent_apps".equals(systemAction) || "recents".equals(systemAction) || "recent".equals(systemAction)) systemAction = "recent_apps";
                else if ("notifications".equals(systemAction) || "open_notifications".equals(systemAction) || "notification_shade".equals(systemAction) || "الإشعارات".equals(systemAction)) systemAction = "notifications";
                else if ("quick_settings".equals(systemAction) || "open_quick_settings".equals(systemAction) || "quicksettings".equals(systemAction) || "settings".equals(systemAction) || "الإعدادات السريعة".equals(systemAction)) systemAction = "quick_settings";
                else if ("dismiss_notifications".equals(systemAction) || "dismiss_notification_shade".equals(systemAction) || "dismiss".equals(systemAction) || "إغلاق الإشعارات".equals(systemAction)) systemAction = "dismiss_notifications";
                else return null;
                out.put("action", "GLOBAL_ACTION");
                out.put("type", "GLOBAL_ACTION");
                out.put("systemAction", systemAction);
                out.put("label", "إجراء النظام: " + systemAction);
                out.put("reason", safe(source.optString("reason", "")));
                addAfterDelay(out, source, 900);
                return out;
            }
            if ("WAIT_TEXT".equals(action) || "WAIT_FOR_TEXT".equals(action) || "WAIT_UNTIL_TEXT".equals(action)) {
                String text = source.optString("text", source.optString("value", "")).trim();
                if (text.isEmpty() || text.length() > 180) return null;
                out.put("action", "WAIT_TEXT");
                out.put("type", "WAIT_TEXT");
                out.put("value", text);
                out.put("attempts", Math.max(1, Math.min(240, integer(source, "attempts", 30))));
                out.put("intervalMs", Math.max(250, Math.min(60000, integer(source, "interval_ms", 700))));
                out.put("label", "انتظار ظهور النص: " + text);
                out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("WAIT_INPUT".equals(action) || "WAIT_FOR_INPUT".equals(action) || "WAIT_FOR_FIELD".equals(action)) {
                out.put("action", "WAIT_INPUT");
                out.put("type", "WAIT_INPUT");
                out.put("attempts", Math.max(1, Math.min(240, integer(source, "attempts", 30))));
                out.put("intervalMs", Math.max(250, Math.min(60000, integer(source, "interval_ms", 700))));
                out.put("label", "انتظار ظهور حقل إدخال");
                out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("IMAGE_MATCH".equals(action) || "WAIT_IMAGE".equals(action) || "FIND_IMAGE".equals(action)) {
                String image = source.optString("image_name", source.optString("image", source.optString("filename", source.optString("value", "")))).trim();
                if (image.isEmpty() || image.contains("/") || image.contains("..") || image.length() > 180) return null;
                out.put("action", "IMAGE_MATCH");
                out.put("type", "IMAGE_MATCH");
                out.put("images", new org.json.JSONArray().put(image));
                out.put("threshold", Math.max(0, Math.min(100, integer(source, "threshold", 85))));
                out.put("tapOnMatch", !("WAIT_IMAGE".equals(action) || source.optBoolean("wait_only", false)));
                out.put("retries", Math.max(1, Math.min(100, integer(source, "retries", 2))));
                out.put("retryDelay", Math.max(100, Math.min(10000, integer(source, "retry_delay_ms", 500))));
                out.put("searchRegionMode", "full");
                out.put("label", "مطابقة صورة محفوظة: " + image);
                out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("CHECK_INTERNET".equals(action) || "WAIT_INTERNET".equals(action)) {
                out.put("action", action);
                out.put("type", action);
                out.put("attempts", Math.max(1, Math.min(240, integer(source, "attempts", "WAIT_INTERNET".equals(action) ? 120 : 3))));
                out.put("intervalMs", Math.max(250, Math.min(60000, integer(source, "interval_ms", 5000))));
                out.put("timeoutMs", Math.max(1000, Math.min(12000, integer(source, "timeout_ms", 4000))));
                String url = source.optString("url", "").trim();
                if (!url.isEmpty()) out.put("url", url);
                out.put("label", "WAIT_INTERNET".equals(action) ? "انتظار عودة الإنترنت" : "فحص اتصال الإنترنت");
                out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("SPEED_TEST".equals(action) || "READ_SCREEN_TEXT".equals(action)) {
                out.put("action", action);
                out.put("type", action);
                out.put("label", "SPEED_TEST".equals(action) ? "قياس سرعة الإنترنت" : "قراءة نصوص الشاشة");
                out.put("reason", safe(source.optString("reason", "")));
                return out;
            }
            if ("NOTIFY".equals(action) || "SHOW_NOTIFICATION".equals(action)) {
                String message = source.optString("message", source.optString("text", "تم تنفيذ الأمر"));
                if (message.trim().isEmpty() || message.length() > 500) return null;
                out.put("action", "NOTIFY");
                out.put("type", "NOTIFY");
                out.put("message", message);
                out.put("sound", source.optBoolean("sound", true));
                out.put("label", "إظهار تنبيه للمستخدم");
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

    private static void addAfterDelay(JSONObject output, JSONObject source, int fallback) throws JSONException {
        int value = integer(source, "after_delay_ms", integer(source, "afterDelay", fallback));
        output.put("afterDelay", Math.max(0, Math.min(60000, value)));
    }

    private static int integerAny(JSONObject json, int fallback, String... keys) {
        if (json == null || keys == null) return fallback;
        for (String key : keys) {
            if (key == null || !json.has(key)) continue;
            Object value = json.opt(key);
            if (value instanceof Number) return ((Number) value).intValue();
            if (value instanceof String) {
                try { return (int) Math.round(Double.parseDouble(((String) value).trim())); }
                catch (Exception ignored) { }
            }
        }
        return fallback;
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
