package com.botna.automation;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Local-only storage for Botna scripts and step definitions. */
public final class BotnaStore {

    public static final String PREFS = "botna_settings";
    public static final String KEY_TARGET_TEXT = "target_text";
    public static final String KEY_LAST_RESULT = "last_result";

    private static final String KEY_SCRIPTS = "scripts_v1";
    private static final String KEY_ACTIVE_SCRIPT = "active_script_v1";
    private static final String KEY_IMAGE_LIBRARY = "image_library_v1";

    private BotnaStore() {
    }

    public static JSONArray scripts(Context context) {
        String raw = prefs(context).getString(KEY_SCRIPTS, "[]");

        try {
            return new JSONArray(raw);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static void saveScripts(
            Context context,
            JSONArray scripts
    ) {
        prefs(context)
                .edit()
                .putString(KEY_SCRIPTS, scripts.toString())
                .commit();
    }

    /** Images owned by the custom-step library, separate from per-step targets. */
    public static JSONArray imageLibrary(Context context) {
        String raw = prefs(context).getString(KEY_IMAGE_LIBRARY, "[]");
        try { return new JSONArray(raw); }
        catch (JSONException e) { return new JSONArray(); }
    }

    public static void saveImageLibrary(Context context, JSONArray library) {
        prefs(context).edit().putString(KEY_IMAGE_LIBRARY,
                library == null ? "[]" : library.toString()).commit();
    }

    public static String activeId(Context context) {
        return prefs(context).getString(KEY_ACTIVE_SCRIPT, "");
    }

    public static void setActiveId(
            Context context,
            String id
    ) {
        prefs(context)
                .edit()
                .putString(KEY_ACTIVE_SCRIPT, id)
                .commit();
    }

    public static synchronized void deleteScript(Context context, String id) {
        if (id == null || id.trim().isEmpty()) return;
        JSONArray old = scripts(context);
        JSONArray next = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            JSONObject item = old.optJSONObject(i);
            if (item == null || id.equals(item.optString("id"))) continue;
            next.put(item);
        }
        saveScripts(context, next);
        if (id.equals(activeId(context))) {
            setActiveId(context, next.length() > 0 ? next.optJSONObject(0).optString("id", "") : "");
        }
    }

    public static JSONObject getScript(
            Context context,
            String id
    ) {
        JSONArray all = scripts(context);

        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);

            if (item != null &&
                    id.equals(item.optString("id"))) {
                return item;
            }
        }

        return null;
    }

    public static JSONObject activeScript(Context context) {
        JSONArray all = scripts(context);
        String id = activeId(context);

        if (!id.isEmpty()) {
            JSONObject found = getScript(context, id);

            if (found != null) {
                return found;
            }
        }

        if (all.length() > 0) {
            JSONObject first = all.optJSONObject(0);

            if (first != null) {
                setActiveId(context, first.optString("id"));
                return first;
            }
        }

        return createScript(context, "السكربت الأول");
    }

    public static JSONObject createScript(
            Context context,
            String requestedName
    ) {
        JSONArray all = scripts(context);

        String name = requestedName == null
                ? ""
                : requestedName.trim();

        if (name.isEmpty()) {
            name = "سكربت " +
                    new SimpleDateFormat(
                            "MM-dd HH:mm",
                            Locale.getDefault()
                    ).format(new Date());
        }

        JSONObject item = new JSONObject();

        try {
            item.put("id", "s" + System.nanoTime());
            item.put("name", name);
            item.put("steps", new JSONArray());
            item.put("created", System.currentTimeMillis());

            all.put(item);

            saveScripts(context, all);
            setActiveId(context, item.optString("id"));

        } catch (JSONException ignored) {
        }

        return item;
    }

    public static JSONArray steps(JSONObject script) {
        if (script == null) {
            return new JSONArray();
        }

        JSONArray steps = script.optJSONArray("steps");

        return steps == null
                ? new JSONArray()
                : steps;
    }

    public static void saveSteps(
            Context context,
            JSONObject script,
            JSONArray steps
    ) {
        if (script == null) {
            return;
        }

        try {
            script.put("steps", steps);
        } catch (JSONException e) {
            return;
        }

        JSONArray all = scripts(context);

        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);

            if (item != null &&
                    script.optString("id")
                            .equals(item.optString("id"))) {

                // إصلاح الخطأ: JSONArray.put(index, value)
                // قد يرمي JSONException، لذلك نعالجه هنا.
                try {
                    all.put(i, script);
                } catch (JSONException e) {
                    return;
                }

                saveScripts(context, all);
                return;
            }
        }
    }

    public static JSONObject newStep(
            String type,
            String label
    ) {
        JSONObject step = new JSONObject();

        try {
            step.put("id", "p" + System.nanoTime());
            step.put("type", type);
            step.put("label", label);
            step.put("value", "");
            step.put("delay", 500);
            step.put("threshold", 85);
            step.put("enabled", true);
            step.put("images", new JSONArray());

        } catch (JSONException ignored) {
        }

        return step;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
        );
    }
}
