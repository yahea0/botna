package com.botna.automation;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Persists reusable smart-agent goals, their generated scripts, and an explanatory message timeline. */
public final class BotnaAgentStore {
    private static final String PREFS = BotnaStore.PREFS;
    private static final String TASKS_KEY = "smart_agent_tasks_v1";
    private static final String MESSAGES_KEY = "smart_agent_messages_v1";
    private static final int MAX_TASKS = 60;
    private static final int MAX_MESSAGES = 400;

    private BotnaAgentStore() { }

    public static synchronized JSONArray tasks(Context context) {
        try { return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TASKS_KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static synchronized JSONArray messages(Context context) {
        try { return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MESSAGES_KEY, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    public static synchronized JSONObject getTask(Context context, String id) {
        if (id == null || id.trim().isEmpty()) return null;
        JSONArray all = tasks(context);
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) return item;
        }
        return null;
    }

    public static synchronized String createTask(Context context, String goal) {
        String cleanGoal = goal == null ? "" : goal.trim();
        String id = "agent_" + System.currentTimeMillis() + "_" + Math.abs((int) (System.nanoTime() % 1000000));
        String name = defaultName(cleanGoal);
        JSONObject script = BotnaStore.createScript(context, "وكيل - " + name);
        JSONArray all = tasks(context);
        JSONObject item = new JSONObject();
        try {
            item.put("id", id); item.put("name", name); item.put("goal", cleanGoal);
            item.put("scriptId", script == null ? "" : script.optString("id", ""));
            item.put("created", System.currentTimeMillis()); item.put("updated", System.currentTimeMillis());
            item.put("runs", 0); item.put("status", "محفوظة"); item.put("lastMessage", "تم حفظ المهمة كوكيل قابل لإعادة التشغيل.");
            all.put(0, item);
        } catch (JSONException ignored) { }
        while (all.length() > MAX_TASKS) {
            JSONObject old = all.optJSONObject(all.length() - 1);
            if (old != null) {
                String oldScript = old.optString("scriptId", "");
                if (!oldScript.isEmpty()) BotnaStore.deleteScript(context, oldScript);
            }
            all.remove(all.length() - 1);
        }
        saveTasks(context, all);
        addMessage(context, id, "system", "حفظ المهمة", "حُفظ هدف الوكيل وقُرن بسكربت مستقل يمكن تعديله من محرر بوتنا.", -1, true);
        return id;
    }

    public static synchronized void updateGoal(Context context, String id, String name, String goal) {
        JSONObject item = getTask(context, id);
        if (item == null) return;
        String cleanGoal = goal == null ? "" : goal.trim();
        String cleanName = name == null || name.trim().isEmpty() ? defaultName(cleanGoal) : name.trim();
        try {
            item.put("goal", cleanGoal); item.put("name", cleanName); item.put("updated", System.currentTimeMillis());
            String scriptId = item.optString("scriptId", "");
            JSONObject script = BotnaStore.getScript(context, scriptId);
            if (script != null) {
                script.put("name", "وكيل - " + cleanName);
                BotnaStore.saveScripts(context, replaceScript(BotnaStore.scripts(context), script));
            }
        } catch (JSONException ignored) { }
        persistTask(context, item);
        addMessage(context, id, "user", "تعديل المهمة", "تم تحديث وصف المهمة المحفوظة.", -1, true);
    }

    /** Clears the prior run's generated actions before replaying the saved goal. */
    public static synchronized String prepareRun(Context context, String id) {
        JSONObject item = getTask(context, id);
        if (item == null) return "";
        String scriptId = item.optString("scriptId", "");
        JSONObject script = BotnaStore.getScript(context, scriptId);
        if (script == null) {
            script = BotnaStore.createScript(context, "وكيل - " + item.optString("name", "مهمة"));
            scriptId = script == null ? "" : script.optString("id", "");
            try { item.put("scriptId", scriptId); } catch (JSONException ignored) { }
        }
        if (script != null) BotnaStore.saveSteps(context, script, new JSONArray());
        if (!scriptId.isEmpty()) BotnaStore.setActiveId(context, scriptId);
        try {
            item.put("status", "قيد التنفيذ"); item.put("updated", System.currentTimeMillis());
            item.put("runs", item.optInt("runs", 0) + 1); item.put("lastMessage", "بدأ تشغيل المهمة.");
        } catch (JSONException ignored) { }
        persistTask(context, item);
        addMessage(context, id, "system", "بدء التشغيل", "بدأ الوكيل من جديد؛ سيسجل الإجراءات التي نفذها في سكربت مستقل.", -1, true);
        return scriptId;
    }

    public static synchronized String scriptId(Context context, String taskId) {
        JSONObject item = getTask(context, taskId);
        return item == null ? "" : item.optString("scriptId", "");
    }

    public static synchronized void appendStep(Context context, String taskId, JSONObject step) {
        if (step == null) return;
        JSONObject item = getTask(context, taskId);
        if (item == null) return;
        JSONObject script = BotnaStore.getScript(context, item.optString("scriptId", ""));
        if (script == null) return;
        JSONArray steps = BotnaStore.steps(script);
        try { steps.put(new JSONObject(step.toString())); }
        catch (Exception e) { return; }
        BotnaStore.saveSteps(context, script, steps);
        try { item.put("stepCount", steps.length()); item.put("updated", System.currentTimeMillis()); }
        catch (JSONException ignored) { }
        persistTask(context, item);
    }

    public static synchronized void setStatus(Context context, String id, String status, String message) {
        JSONObject item = getTask(context, id);
        if (item == null) return;
        try { item.put("status", safe(status)); item.put("lastMessage", safe(message)); item.put("updated", System.currentTimeMillis()); }
        catch (JSONException ignored) { }
        persistTask(context, item);
    }

    public static synchronized JSONObject addMessage(Context context, String taskId, String role, String title,
                                                     String detail, int actionIndex, boolean success) {
        JSONArray all = messages(context);
        JSONObject item = new JSONObject();
        String timeText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        try {
            item.put("id", "msg_" + System.currentTimeMillis() + "_" + Math.abs((int) (System.nanoTime() % 1000000)));
            item.put("taskId", safe(taskId)); item.put("role", safe(role)); item.put("title", safe(title));
            item.put("detail", safe(detail)); item.put("actionIndex", actionIndex); item.put("success", success);
            item.put("time", System.currentTimeMillis()); item.put("timeText", timeText);
            all.put(item);
        } catch (JSONException ignored) { }
        while (all.length() > MAX_MESSAGES) all.remove(0);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MESSAGES_KEY, all.toString()).apply();
        JSONObject task = getTask(context, taskId);
        if (task != null) {
            try {
                task.put("lastMessage", safe(title));
                task.put("updated", System.currentTimeMillis());
            } catch (JSONException ignored) { }
            persistTask(context, task);
        }
        return item;
    }

    public static synchronized void deleteTask(Context context, String id) {
        JSONArray old = tasks(context), next = new JSONArray();
        String scriptId = "";
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item == null) continue;
            if (id != null && id.equals(item.optString("id"))) { scriptId = item.optString("scriptId", ""); continue; }
            next.put(item);
        }
        saveTasks(context, next);
        if (!scriptId.isEmpty()) BotnaStore.deleteScript(context, scriptId);
    }

    public static synchronized void clearMessages(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(MESSAGES_KEY, "[]").apply();
    }

    private static void saveTasks(Context context, JSONArray items) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(TASKS_KEY, items == null ? "[]" : items.toString()).apply();
    }

    private static void persistTask(Context context, JSONObject task) {
        if (task == null) return;
        JSONArray all = tasks(context);
        String id = task.optString("id", "");
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item != null && id.equals(item.optString("id"))) {
                try { all.put(i, task); } catch (JSONException ignored) { }
                saveTasks(context, all);
                return;
            }
        }
    }

    private static JSONArray replaceScript(JSONArray scripts, JSONObject replacement) {
        JSONArray out = new JSONArray(); boolean replaced = false;
        for (int i = 0; i < scripts.length(); i++) {
            JSONObject item = scripts.optJSONObject(i);
            if (item != null && item.optString("id").equals(replacement.optString("id"))) { out.put(replacement); replaced = true; }
            else if (item != null) out.put(item);
        }
        if (!replaced) out.put(replacement);
        return out;
    }

    private static String defaultName(String goal) {
        String value = goal == null ? "" : goal.replace('\n', ' ').trim();
        if (value.isEmpty()) return "مهمة ذكية";
        return value.length() > 38 ? value.substring(0, 38).trim() + "…" : value;
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
