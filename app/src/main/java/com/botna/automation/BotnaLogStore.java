package com.botna.automation;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Private, bounded diagnostic log storage. Screenshots never leave app-private storage. */
public final class BotnaLogStore {
    private static final String PREFS = "botna_settings";
    private static final String LOGS_KEY = "diagnostic_logs_v1";
    private static final String CAPTURE_KEY = "diagnostic_capture_screenshots_v1";
    private static final int MAX_LOGS = 100;
    private static final String DIR = "botna_logs";

    private BotnaLogStore() { }

    public static boolean screenshotsEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CAPTURE_KEY, true);
    }

    public static void setScreenshotsEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CAPTURE_KEY, enabled).apply();
    }

    public static synchronized JSONObject add(Context context, String source, int stepIndex,
                                               String stepLabel, String message, String detail,
                                               Bitmap screenshot) {
        JSONArray logs = read(context);
        String id = "log_" + System.currentTimeMillis() + "_" + Math.abs((int) (System.nanoTime() % 1000000));
        String imageName = "";
        if (screenshot != null && !screenshot.isRecycled() && screenshotsEnabled(context)) {
            imageName = saveScreenshot(context, id, screenshot);
        }
        JSONObject entry = new JSONObject();
        try {
            entry.put("id", id);
            entry.put("time", System.currentTimeMillis());
            entry.put("timeText", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            entry.put("source", safe(source));
            entry.put("stepIndex", stepIndex);
            entry.put("stepLabel", safe(stepLabel));
            entry.put("message", safe(message));
            entry.put("detail", safe(detail));
            entry.put("screenshot", imageName);
            JSONArray next = new JSONArray();
            next.put(entry);
            for (int i = 0; i < logs.length() && next.length() < MAX_LOGS; i++) {
                JSONObject old = logs.optJSONObject(i);
                if (old != null && !id.equals(old.optString("id"))) next.put(old);
            }
            java.util.HashSet<String> retainedIds = new java.util.HashSet<>();
            for (int i = 0; i < next.length(); i++) {
                JSONObject kept = next.optJSONObject(i);
                if (kept != null) retainedIds.add(kept.optString("id", ""));
            }
            for (int i = 0; i < logs.length(); i++) {
                JSONObject old = logs.optJSONObject(i);
                if (old != null && !retainedIds.contains(old.optString("id", ""))) deleteScreenshot(context, old);
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LOGS_KEY, next.toString()).apply();
        } catch (JSONException ignored) { }
        return entry;
    }

    public static synchronized JSONArray list(Context context) { return read(context); }

    public static synchronized void delete(Context context, String id) {
        JSONArray old = read(context), next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item == null) continue;
            if (id != null && id.equals(item.optString("id"))) deleteScreenshot(context, item);
            else next.put(item);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LOGS_KEY, next.toString()).apply();
    }

    public static synchronized void clear(Context context) {
        JSONArray logs = read(context);
        for (int i = 0; i < logs.length(); i++) deleteScreenshot(context, logs.optJSONObject(i));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LOGS_KEY, "[]").apply();
        File dir = new File(context.getFilesDir(), DIR);
        File[] files = dir.listFiles();
        if (files != null) for (File file : files) if (file.isFile()) file.delete();
    }

    public static Bitmap loadScreenshot(Context context, JSONObject entry) {
        if (entry == null) return null;
        String name = entry.optString("screenshot", "");
        if (name.isEmpty() || name.contains("/") || name.contains("..")) return null;
        File file = new File(new File(context.getFilesDir(), DIR), name);
        if (!file.isFile()) return null;
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    private static JSONArray read(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LOGS_KEY, "[]");
        try { return new JSONArray(raw); } catch (JSONException e) { return new JSONArray(); }
    }

    private static String saveScreenshot(Context context, String id, Bitmap source) {
        File dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists() && !dir.mkdirs()) return "";
        int maxSide = Math.max(source.getWidth(), source.getHeight());
        Bitmap scaled = source;
        boolean owned = false;
        try {
            if (maxSide > 1000) {
                float ratio = 1000f / maxSide;
                scaled = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * ratio)),
                        Math.max(1, Math.round(source.getHeight() * ratio)), true);
                owned = scaled != source;
            }
            File file = new File(dir, id + ".jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 68, out)) return "";
                out.flush();
            }
            return file.getName();
        } catch (Exception ignored) { return ""; }
        finally { if (owned && scaled != null && !scaled.isRecycled()) scaled.recycle(); }
    }

    private static void deleteScreenshot(Context context, JSONObject entry) {
        if (entry == null) return;
        String name = entry.optString("screenshot", "");
        if (name.isEmpty() || name.contains("/") || name.contains("..")) return;
        new File(new File(context.getFilesDir(), DIR), name).delete();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
