package com.botna.automation;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.os.ParcelFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BotStudioStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "bot_studio_v1.db";
    private static final int DB_VERSION = 3;
    private static final String PREFS = "bot_studio_preferences";
    private static final String SELECTED_BOT = "selected_bot_id";
    private static final String VISION_STREAM = "vision_stream_enabled";
    private static final String VISION_SMART = "vision_smart_snapshots_enabled";
    private static final String VISION_CAPTURE_AFTER_ACTION = "vision_capture_after_action";
    private static final String VISION_CAPTURE_ON_FAILURE = "vision_capture_on_failure";
    private static final String THINKING_LEVEL = "chat_thinking_level";
    private static final String SAFETY_SANDBOX = "safety_sandbox_enabled";
    private static volatile BotStudioStore instance;
    private final Context appContext;

    public static final class Skill {
        public final String id;
        public final String title;
        public final String description;
        public final String guidance;
        public Skill(String id, String title, String description, String guidance) {
            this.id = id; this.title = title; this.description = description; this.guidance = guidance;
        }
    }

    private static final Skill[] SKILLS = new Skill[]{
            new Skill("browser", "التصفح والبحث الذكي", "فتح الصفحات وقراءة المحتوى وتلخيصه", "عند تفعيل مهارة التصفح، اقرأ النصوص الظاهرة فعليًا، تحقق من الصفحة قبل المتابعة، ولا تختلق محتوى غير ظاهر."),
            new Skill("extract", "استخراج البيانات", "استخراج النصوص والجداول إلى JSON أو CSV", "عند استخراج البيانات، احتفظ بأسماء الأعمدة والقيم كما تظهر، واذكر الخانات غير المقروءة بدل تخمينها."),
            new Skill("social", "التفاعل الاجتماعي", "مساعدة في إدارة الردود والتفاعلات", "لا ترسل رسالة ولا تنشر محتوى عامًا تلقائيًا إذا كان وضع التحقق الحرج مفعّلًا؛ اعرض المحتوى واطلب موافقة المستخدم."),
            new Skill("business", "الأتمتة المالية والتجارية", "متابعة الأسعار وتعبئة النماذج", "يمكن قراءة الأسعار وتجهيز النماذج، لكن لا تنفّذ شراءً أو دفعًا أو صفقة أو تحويلًا ماليًا من دون موافقة صريحة في اللحظة نفسها.")
    };

    private BotStudioStore(Context context) { super(context.getApplicationContext(), DB_NAME, null, DB_VERSION); appContext = context.getApplicationContext(); }

    public static BotStudioStore get(Context context) {
        if (instance == null) synchronized (BotStudioStore.class) {
            if (instance == null) instance = new BotStudioStore(context);
        }
        return instance;
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE bots(id TEXT PRIMARY KEY,name TEXT NOT NULL,icon TEXT NOT NULL,role TEXT NOT NULL,persona TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE skills(bot_id TEXT NOT NULL,skill_id TEXT NOT NULL,enabled INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(bot_id,skill_id),FOREIGN KEY(bot_id) REFERENCES bots(id) ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,bot_id TEXT NOT NULL,role TEXT NOT NULL,body TEXT NOT NULL,image_path TEXT NOT NULL,created_at INTEGER NOT NULL,model_name TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX messages_bot_time ON messages(bot_id,created_at)");
        db.execSQL("CREATE TABLE errors(id INTEGER PRIMARY KEY AUTOINCREMENT,signature TEXT NOT NULL,app_package TEXT NOT NULL,action_type TEXT NOT NULL,detail TEXT NOT NULL,strategy TEXT NOT NULL,failures INTEGER NOT NULL DEFAULT 1,successes INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL,UNIQUE(signature,app_package,action_type))");
        db.execSQL("CREATE TABLE checkpoints(task_id TEXT PRIMARY KEY,goal TEXT NOT NULL,script_id TEXT NOT NULL,action_count INTEGER NOT NULL,history_json TEXT NOT NULL,state TEXT NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX checkpoints_state_time ON checkpoints(state,updated_at)");
        db.execSQL("CREATE TABLE custom_skills(id TEXT PRIMARY KEY,title TEXT NOT NULL,description TEXT NOT NULL,guidance TEXT NOT NULL,created_at INTEGER NOT NULL)");
    }

    @Override public void onConfigure(SQLiteDatabase db) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true); }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE messages ADD COLUMN model_name TEXT NOT NULL DEFAULT ''");
        if (oldVersion < 3) db.execSQL("CREATE TABLE IF NOT EXISTS custom_skills(id TEXT PRIMARY KEY,title TEXT NOT NULL,description TEXT NOT NULL,guidance TEXT NOT NULL,created_at INTEGER NOT NULL)");
    }

    public synchronized JSONArray bots() {
        JSONArray out = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,icon,role,persona,created_at,updated_at FROM bots ORDER BY updated_at DESC", null)) {
            while (c.moveToNext()) out.put(new JSONObject().put("id", c.getString(0)).put("name", c.getString(1)).put("icon", c.getString(2))
                    .put("role", c.getString(3)).put("persona", c.getString(4)).put("created", c.getLong(5)).put("updated", c.getLong(6)));
        } catch (Exception ignored) { }
        if (out.length() == 0) {
            String id = createBot("بوتنا", "🤖", "مساعد عام", "مساعد فلسطيني عملي يفهم اللهجة، يوضح عدم اليقين، ولا يدّعي نجاحًا غير متحقق.");
            return bots();
        }
        return out;
    }

    public synchronized String createBot(String name, String icon, String role, String persona) {
        String cleanName = clean(name, 64, "بوت جديد");
        String cleanIcon = clean(icon, 12, "🤖");
        String cleanRole = clean(role, 80, "مساعد عام");
        String cleanPersona = clean(persona, 3000, "مساعد دقيق، واضح، ويحترم الأمان والخصوصية.");
        String id = "bot_" + System.currentTimeMillis() + "_" + Math.abs((int) (System.nanoTime() % 1000000));
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues(); values.put("id", id); values.put("name", cleanName); values.put("icon", cleanIcon);
        values.put("role", cleanRole); values.put("persona", cleanPersona); values.put("created_at", now); values.put("updated_at", now);
        getWritableDatabase().insertWithOnConflict("bots", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        for (Skill skill : SKILLS) setSkillEnabled(id, skill.id, "browser".equals(skill.id));
        selectBot(id);
        return id;
    }

    public synchronized void updateBot(String id, String name, String icon, String role, String persona) {
        if (id == null || id.trim().isEmpty()) return;
        ContentValues values = new ContentValues(); values.put("name", clean(name, 64, "بوت")); values.put("icon", clean(icon, 12, "🤖"));
        values.put("role", clean(role, 80, "مساعد عام")); values.put("persona", clean(persona, 3000, "مساعد دقيق")); values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("bots", values, "id=?", new String[]{id});
    }

    public synchronized JSONObject bot(String id) {
        if (id == null) return null;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,name,icon,role,persona,created_at,updated_at FROM bots WHERE id=?", new String[]{id})) {
            if (!c.moveToFirst()) return null;
            return new JSONObject().put("id", c.getString(0)).put("name", c.getString(1)).put("icon", c.getString(2)).put("role", c.getString(3))
                    .put("persona", c.getString(4)).put("created", c.getLong(5)).put("updated", c.getLong(6));
        } catch (Exception ignored) { return null; }
    }

    public synchronized void deleteBot(String id) {
        if (id == null) return;
        getWritableDatabase().delete("bots", "id=?", new String[]{id});
        getWritableDatabase().delete("skills", "bot_id=?", new String[]{id});
        getWritableDatabase().delete("messages", "bot_id=?", new String[]{id});
        if (id.equals(selectedBotId(appContext))) selectBot(appContext, "");
    }

    public static String selectedBotId(Context context) {
        if (context == null) return "";
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SELECTED_BOT, "");
    }

    public static void selectBot(Context context, String id) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(SELECTED_BOT, id == null ? "" : id).apply();
    }

    private void selectBot(String id) { selectBot(appContext, id); }

    public synchronized void setSkillEnabled(String botId, String skillId, boolean enabled) {
        if (botId == null || botId.isEmpty() || skillId == null || skillId.isEmpty()) return;
        ContentValues v = new ContentValues(); v.put("bot_id", botId); v.put("skill_id", skillId); v.put("enabled", enabled ? 1 : 0);
        getWritableDatabase().insertWithOnConflict("skills", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized boolean isSkillEnabled(String botId, String skillId) {
        if (botId == null || botId.isEmpty()) return false;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT enabled FROM skills WHERE bot_id=? AND skill_id=?", new String[]{botId, skillId})) {
            if (c.moveToFirst()) return c.getInt(0) != 0;
        } catch (Exception ignored) { }
        return "browser".equals(skillId);
    }

    public synchronized Skill[] allSkills() {
        ArrayList<Skill> result = new ArrayList<>();
        java.util.Collections.addAll(result, SKILLS);
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,title,description,guidance FROM custom_skills ORDER BY created_at DESC", null)) {
            while (c.moveToNext()) result.add(new Skill(c.getString(0), c.getString(1), c.getString(2), c.getString(3)));
        } catch (Exception ignored) { }
        return result.toArray(new Skill[0]);
    }

    public synchronized Skill saveCustomSkill(String title, String description, String guidance) {
        String cleanTitle = clean(title, 80, "مهارة جديدة");
        String cleanDescription = clean(description, 300, "مهارة مستوردة");
        String cleanGuidance = clean(guidance, 12000, "اتبع تعليمات المهارة بعد التأكد من أن الإجراء مناسب وآمن.");
        String id = "custom_" + System.currentTimeMillis() + "_" + Math.abs((int) (System.nanoTime() % 1000000));
        ContentValues v = new ContentValues(); v.put("id", id); v.put("title", cleanTitle); v.put("description", cleanDescription);
        v.put("guidance", cleanGuidance); v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("custom_skills", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        return new Skill(id, cleanTitle, cleanDescription, cleanGuidance);
    }

    public synchronized String skillGuidance(String botId) {
        StringBuilder out = new StringBuilder();
        for (Skill skill : allSkills()) if (isSkillEnabled(botId, skill.id)) out.append("\n- ").append(skill.title).append(": ").append(skill.guidance);
        return out.toString();
    }

    public synchronized void addMessage(String botId, String role, String body, String attachmentPath) {
        String modelName = "assistant".equals(role) ? BotnaAICompiler.model(appContext) : "";
        addMessage(botId, role, body, attachmentPath, modelName);
    }

    public synchronized void addMessage(String botId, String role, String body, String attachmentPath, String modelName) {
        if (botId == null || botId.isEmpty()) return;
        ContentValues v = new ContentValues(); v.put("id", "chat_" + System.currentTimeMillis() + "_" + Math.abs((int) (System.nanoTime() % 1000000)));
        v.put("bot_id", botId); v.put("role", clean(role, 20, "user")); v.put("body", body == null ? "" : body);
        v.put("image_path", attachmentPath == null ? "" : attachmentPath); v.put("created_at", System.currentTimeMillis());
        v.put("model_name", modelName == null ? "" : modelName);
        getWritableDatabase().insert("messages", null, v);
        getWritableDatabase().execSQL("DELETE FROM messages WHERE bot_id=? AND id NOT IN (SELECT id FROM messages WHERE bot_id=? ORDER BY created_at DESC LIMIT 300)", new Object[]{botId, botId});
    }

    public synchronized JSONArray messages(String botId, int limit) {
        JSONArray out = new JSONArray();
        if (botId == null) return out;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT role,body,image_path,created_at,model_name FROM messages WHERE bot_id=? ORDER BY created_at DESC LIMIT ?", new String[]{botId, String.valueOf(Math.max(1, Math.min(300, limit)))})) {
            while (c.moveToNext()) out.put(new JSONObject().put("role", c.getString(0)).put("body", c.getString(1)).put("image", c.getString(2)).put("time", c.getLong(3)).put("model", c.getString(4)));
        } catch (Exception ignored) { }
        JSONArray ordered = new JSONArray();
        for (int i = out.length() - 1; i >= 0; i--) ordered.put(out.optJSONObject(i));
        return ordered;
    }

    public static void associateTaskWithBot(Context context, String taskId, String botId) {
        if (context == null || taskId == null || taskId.isEmpty() || botId == null || botId.isEmpty()) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("task_bot_" + taskId, botId).apply();
    }

    public static String botForTask(Context context, String taskId) {
        if (context == null || taskId == null || taskId.isEmpty()) return "";
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("task_bot_" + taskId, "");
    }

    public static String thinkingLevel(Context context) {
        if (context == null) return "max";
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(THINKING_LEVEL, "max");
        return normalizeThinkingLevel(value);
    }

    public static void setThinkingLevel(Context context, String value) {
        if (context == null) return;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(THINKING_LEVEL, normalizeThinkingLevel(value)).apply();
    }

    private static String normalizeThinkingLevel(String value) {
        if (value == null) return "max";
        switch (value.toLowerCase(Locale.ROOT).trim()) {
            case "low": case "medium": case "high": case "max": case "auto": return value.toLowerCase(Locale.ROOT).trim();
            default: return "max";
        }
    }

    public synchronized void recordFailure(String appPackage, String actionType, String detail, String strategy) {
        String pkg = clean(appPackage, 200, "unknown"); String type = clean(actionType, 100, "action");
        String signature = normalize(type);
        if (signature.length() > 180) signature = signature.substring(0, 180);
        ContentValues v = new ContentValues(); v.put("signature", signature); v.put("app_package", pkg); v.put("action_type", type);
        v.put("detail", clean(detail, 1200, "")); v.put("strategy", clean(strategy, 1200, "")); v.put("failures", 1); v.put("successes", 0); v.put("updated_at", System.currentTimeMillis());
        SQLiteDatabase db = getWritableDatabase();
        String[] where = new String[]{signature, pkg, type};
        int rowId = -1;
        try (Cursor c = db.rawQuery("SELECT id,failures FROM errors WHERE signature=? AND app_package=? AND action_type=?", where)) {
            if (c.moveToFirst()) { rowId = c.getInt(0); int count = c.getInt(1); ContentValues update = new ContentValues();
                update.put("detail", clean(detail, 1200, "")); update.put("strategy", clean(strategy, 1200, "")); update.put("failures", count + 1); update.put("updated_at", System.currentTimeMillis());
                db.update("errors", update, "id=?", new String[]{String.valueOf(rowId)}); }
        } catch (Exception ignored) { }
        if (rowId < 0) db.insert("errors", null, v);
    }

    public synchronized void recordSuccess(String appPackage, String actionType, String detail) {
        String pkg = clean(appPackage, 200, "unknown"); String type = clean(actionType, 100, "action");
        String signature = normalize(type);
        if (signature.length() > 180) signature = signature.substring(0, 180);
        getWritableDatabase().execSQL("UPDATE errors SET successes=successes+1,updated_at=? WHERE signature=? AND app_package=? AND action_type=?",
                new Object[]{System.currentTimeMillis(), signature, pkg, type});
    }

    public synchronized String knownStrategy(String appPackage, String actionType) {
        String pkg = clean(appPackage, 200, "unknown"); String type = clean(actionType, 100, "action");
        try (Cursor c = getReadableDatabase().rawQuery("SELECT strategy,failures,successes FROM errors WHERE app_package=? AND action_type=? ORDER BY (successes-failures) DESC,updated_at DESC LIMIT 3", new String[]{pkg, type})) {
            StringBuilder out = new StringBuilder();
            while (c.moveToNext()) { String strategy = c.getString(0); if (strategy != null && !strategy.isEmpty()) out.append("\n- معالجة سابقة: ").append(strategy).append(" (فشل ").append(c.getInt(1)).append("، نجاح ").append(c.getInt(2)).append(")"); }
            return out.toString();
        } catch (Exception ignored) { return ""; }
    }

    public synchronized JSONArray errorRecords() {
        JSONArray out = new JSONArray();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT app_package,action_type,detail,strategy,failures,successes,updated_at FROM errors ORDER BY updated_at DESC LIMIT 80", null)) {
            while (c.moveToNext()) out.put(new JSONObject().put("package", c.getString(0)).put("type", c.getString(1)).put("detail", c.getString(2))
                    .put("strategy", c.getString(3)).put("failures", c.getInt(4)).put("successes", c.getInt(5)).put("updated", c.getLong(6)));
        } catch (Exception ignored) { }
        return out;
    }

    public synchronized void clearErrorMemory() { getWritableDatabase().delete("errors", null, null); }

    public synchronized void saveCheckpoint(String taskId, String goal, String scriptId, int actionCount, JSONArray history, String state) {
        if (taskId == null || taskId.isEmpty()) return;
        ContentValues v = new ContentValues(); v.put("task_id", taskId); v.put("goal", goal == null ? "" : goal);
        v.put("script_id", scriptId == null ? "" : scriptId); v.put("action_count", actionCount); v.put("history_json", history == null ? "[]" : history.toString());
        v.put("state", state == null ? "paused" : state); v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("checkpoints", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized JSONArray checkpoints(boolean activeOnly) {
        JSONArray out = new JSONArray();
        String sql = "SELECT task_id,goal,script_id,action_count,history_json,state,updated_at FROM checkpoints" + (activeOnly ? " WHERE state IN ('running','paused','interrupted','approval','failed')" : "") + " ORDER BY updated_at DESC";
        try (Cursor c = getReadableDatabase().rawQuery(sql, null)) {
            while (c.moveToNext()) out.put(new JSONObject().put("taskId", c.getString(0)).put("goal", c.getString(1)).put("scriptId", c.getString(2))
                    .put("actionCount", c.getInt(3)).put("history", new JSONArray(c.getString(4))).put("state", c.getString(5)).put("updated", c.getLong(6)));
        } catch (Exception ignored) { }
        return out;
    }

    public synchronized JSONObject checkpoint(String taskId) {
        JSONArray a = checkpoints(false);
        for (int i = 0; i < a.length(); i++) { JSONObject item = a.optJSONObject(i); if (item != null && taskId != null && taskId.equals(item.optString("taskId"))) return item; }
        return null;
    }

    public static boolean visionStreamEnabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean(VISION_STREAM, false); }
    public static void setVisionStreamEnabled(Context c, boolean b) { c.getSharedPreferences(PREFS, 0).edit().putBoolean(VISION_STREAM, b).apply(); }
    public static boolean smartSnapshotsEnabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean(VISION_SMART, true); }
    public static void setSmartSnapshotsEnabled(Context c, boolean b) { c.getSharedPreferences(PREFS, 0).edit().putBoolean(VISION_SMART, b).apply(); }
    public static boolean captureAfterActionEnabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean(VISION_CAPTURE_AFTER_ACTION, true); }
    public static void setCaptureAfterActionEnabled(Context c, boolean b) { c.getSharedPreferences(PREFS, 0).edit().putBoolean(VISION_CAPTURE_AFTER_ACTION, b).apply(); }
    public static boolean captureOnFailureEnabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean(VISION_CAPTURE_ON_FAILURE, true); }
    public static void setCaptureOnFailureEnabled(Context c, boolean b) { c.getSharedPreferences(PREFS, 0).edit().putBoolean(VISION_CAPTURE_ON_FAILURE, b).apply(); }
    public static boolean safetySandboxEnabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean(SAFETY_SANDBOX, true); }
    public static void setSafetySandboxEnabled(Context c, boolean b) { c.getSharedPreferences(PREFS, 0).edit().putBoolean(SAFETY_SANDBOX, b).apply(); }

    public static String recommendedStrategy(String actionType, String label) {
        String lower = (String.valueOf(actionType) + " " + String.valueOf(label)).toLowerCase(Locale.ROOT);
        if (lower.contains("text_click")) return "افحص نص الزر الحالي بعد تحديث الشاشة؛ جرّب تسمية بديلة مرئية أو انتظر التحميل قبل المحاولة التالية.";
        if (lower.contains("image_match")) return "أعد التقاط الشاشة بعد الانتظار، ثم استخدم قالب الصورة الصحيح وتأكد أن نسبة المطابقة كافية.";
        if (lower.contains("wait") || lower.contains("input")) return "تحقق من تغيّر الشاشة، ثم انتظر مدة قصيرة إضافية بدل تكرار الفعل مباشرة.";
        if (lower.contains("tap") || lower.contains("coord")) return "لا تكرر إحداثيات قديمة؛ أعد قراءة الشاشة وحدد الهدف مجددًا قبل أي نقرة.";
        return "أعد فحص الشاشة والحالة الحالية، واستخدم بديلًا مدعومًا بدل تكرار الإجراء نفسه دون تغيير.";
    }

    public static boolean requiresManualApproval(JSONObject step) {
        if (step == null) return false;
        String value = normalize(step.optString("label", "") + " " + step.optString("value", "") + " " + step.optString("message", ""));
        String[] markers = {"شراء", "اشتري", "اشتر", "ادفع", "دفع", "تحويل مالي", "تحويل", "حواله", "صفقه", "تنفيذ الطلب", "تاكيد الطلب", "احذف", "حذف", "مسح الملفات", "مسح البيانات", "اعاده ضبط المصنع", "ارسال رساله", "ارسل الرساله", "ارسال بريد", "ارسال", "نشر", "منشور عام", "publish", "delete", "clear data", "factory reset", "payment", "pay", "transfer", "place order", "purchase", "buy", "send message", "send email", "public post"};
        for (String marker : markers) if (value.contains(normalize(marker))) return true;
        return false;
    }

    public static Bitmap loadImageFile(String path, int maxSide) {
        if (path == null || path.isEmpty()) return null;
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeFile(path, bounds);
        int width = bounds.outWidth, height = bounds.outHeight;
        if (width <= 0 || height <= 0) return null;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
        while (Math.max(width / options.inSampleSize, height / options.inSampleSize) > Math.max(1, maxSide) * 2) options.inSampleSize *= 2;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        if (bitmap == null) return null;
        int side = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (side <= maxSide) return bitmap;
        float factor = maxSide / (float) side;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * factor)), Math.max(1, Math.round(bitmap.getHeight() * factor)), true);
        if (scaled != bitmap) bitmap.recycle();
        return scaled;
    }

    public static String persistImage(Context context, Uri uri) {
        return persistAttachment(context, uri);
    }

    public static String persistAttachment(Context context, Uri uri) {
        if (context == null || uri == null) return "";
        String originalName = "attachment_" + System.currentTimeMillis();
        long declaredSize = -1;
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) originalName = cursor.getString(nameIndex);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) declaredSize = cursor.getLong(sizeIndex);
            }
        } catch (Exception ignored) { }
        if (declaredSize > 80L * 1024L * 1024L) return "";
        File dir = new File(context.getFilesDir(), "bot_studio_files");
        if (!dir.exists() && !dir.mkdirs()) return "";
        String safeName = new File(originalName == null ? "attachment" : originalName).getName().replaceAll("[^\\p{L}\\p{N}._ -]", "_").trim();
        if (safeName.isEmpty()) safeName = "attachment.bin";
        File target = new File(dir, System.currentTimeMillis() + "_" + safeName);
        long copied = 0;
        try (InputStream input = context.getContentResolver().openInputStream(uri); OutputStream output = new FileOutputStream(target)) {
            if (input == null) return "";
            byte[] buffer = new byte[65536]; int count;
            while ((count = input.read(buffer)) != -1) {
                copied += count;
                if (copied > 80L * 1024L * 1024L) { target.delete(); return ""; }
                output.write(buffer, 0, count);
            }
            output.flush();
            return target.getAbsolutePath();
        } catch (Exception e) {
            target.delete(); return "";
        }
    }

    public static String attachmentName(String path) {
        if (path == null || path.isEmpty()) return "";
        return new File(path).getName().replaceFirst("^\\d+_", "");
    }

    public static String attachmentMime(String path) {
        String name = attachmentName(path).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(dot + 1));
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }

    public static boolean isImageAttachment(String path) {
        return attachmentMime(path).startsWith("image/");
    }

    public static boolean isPdfAttachment(String path) {
        return path != null && path.toLowerCase(Locale.ROOT).endsWith(".pdf");
    }

    public static String readAttachmentText(String path) {
        if (path == null || path.isEmpty()) return "";
        File file = new File(path);
        if (!file.isFile() || file.length() > 2L * 1024L * 1024L) return "";
        String lower = file.getName().toLowerCase(Locale.ROOT);
        try {
            if (lower.endsWith(".docx") || lower.endsWith(".pptx") || lower.endsWith(".xlsx") || lower.endsWith(".odt") || lower.endsWith(".epub")) {
                StringBuilder all = new StringBuilder();
                try (ZipInputStream zip = new ZipInputStream(new FileInputStream(file))) {
                    ZipEntry entry; byte[] buffer = new byte[8192];
                    while ((entry = zip.getNextEntry()) != null && all.length() < 120000) {
                        String name = entry.getName().toLowerCase(Locale.ROOT);
                        boolean useful = (lower.endsWith(".docx") && name.equals("word/document.xml"))
                                || (lower.endsWith(".pptx") && name.startsWith("ppt/slides/slide") && name.endsWith(".xml"))
                                || (lower.endsWith(".xlsx") && (name.equals("xl/sharedstrings.xml") || name.matches("xl/worksheets/sheet[0-9]+\\.xml")))
                                || ((lower.endsWith(".odt") || lower.endsWith(".epub")) && (name.endsWith(".xml") || name.endsWith(".html") || name.endsWith(".xhtml")));
                        if (useful) {
                            ByteArrayOutputStream out = new ByteArrayOutputStream(); int n;
                            while ((n = zip.read(buffer)) != -1 && out.size() < 800000) out.write(buffer, 0, n);
                            all.append(xmlToText(out.toString(StandardCharsets.UTF_8.name()))).append("\n");
                        }
                    }
                }
                return clip(all.toString().trim(), 120000);
            }
            String mime = attachmentMime(path);
            if (mime.startsWith("text/") || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".csv")
                    || lower.endsWith(".md") || lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".js")
                    || lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".kt") || lower.endsWith(".yaml") || lower.endsWith(".yml")
                    || lower.endsWith(".log") || lower.endsWith(".ini") || lower.endsWith(".rtf")) {
                byte[] data = new byte[(int) Math.min(file.length(), 2L * 1024L * 1024L)];
                try (InputStream input = new FileInputStream(file)) { int offset = 0; while (offset < data.length) { int n = input.read(data, offset, data.length - offset); if (n < 0) break; offset += n; } }
                String content = new String(data, StandardCharsets.UTF_8);
                if (lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".rtf")) content = xmlToText(content);
                return clip(content, 120000);
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static String xmlToText(String value) {
        if (value == null) return "";
        return value.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&#10;", " ").replaceAll("\\s+", " ").trim();
    }

    private static String clip(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max) + "\n[تم اقتطاع المحتوى الطويل]";
    }

    public static Bitmap loadAttachmentPreview(Context context, String path, int maxSide) {
        if (isImageAttachment(path)) return loadImageFile(path, maxSide);
        if (!isPdfAttachment(path) || context == null) return null;
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(new File(path), ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            int pages = Math.min(3, renderer.getPageCount());
            if (pages == 0) return null;
            ArrayList<Bitmap> pageBitmaps = new ArrayList<>(); int totalHeight = 0; int width = Math.min(1000, Math.max(400, maxSide));
            for (int i = 0; i < pages; i++) {
                PdfRenderer.Page page = renderer.openPage(i);
                float scale = width / (float) page.getWidth(); int h = Math.max(1, Math.round(page.getHeight() * scale));
                Bitmap bitmap = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                page.close(); pageBitmaps.add(bitmap); totalHeight += h + 12;
            }
            Bitmap combined = Bitmap.createBitmap(width, Math.max(1, totalHeight), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(combined); canvas.drawColor(android.graphics.Color.WHITE); Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG); int y = 0;
            for (Bitmap bitmap : pageBitmaps) { canvas.drawBitmap(bitmap, 0, y, paint); y += bitmap.getHeight() + 12; bitmap.recycle(); }
            int side = Math.max(combined.getWidth(), combined.getHeight());
            if (side <= maxSide) return combined;
            float factor = maxSide / (float) side;
            Bitmap scaled = Bitmap.createScaledBitmap(combined, Math.max(1, Math.round(combined.getWidth() * factor)), Math.max(1, Math.round(combined.getHeight() * factor)), true);
            if (scaled != combined) combined.recycle();
            return scaled;
        } catch (Exception ignored) { return null; }
    }
    private static String normalize(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ة','ه').replace('ى','ي');
        return s.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
    private static String clean(String s, int max, String fallback) {
        String v = s == null ? "" : s.trim(); if (v.isEmpty()) v = fallback; return v.length() > max ? v.substring(0, max) : v;
    }
}
