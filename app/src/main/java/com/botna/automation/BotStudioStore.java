package com.botna.automation;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
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
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BotStudioStore extends SQLiteOpenHelper {
    private static final String DB_NAME = "bot_studio_v1.db";
    private static final int DB_VERSION = 1;
    private static final String PREFS = "bot_studio_preferences";
    private static final String SELECTED_BOT = "selected_bot_id";
    private static final String VISION_STREAM = "vision_stream_enabled";
    private static final String VISION_SMART = "vision_smart_snapshots_enabled";
    private static final String VISION_OCR = "vision_ocr_enabled";
    private static final String VISION_YOLO = "vision_yolo_enabled";
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
        db.execSQL("CREATE TABLE messages(id TEXT PRIMARY KEY,bot_id TEXT NOT NULL,role TEXT NOT NULL,body TEXT NOT NULL,image_path TEXT NOT NULL,created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX messages_bot_time ON messages(bot_id,created_at)");
        db.execSQL("CREATE TABLE errors(id INTEGER PRIMARY KEY AUTOINCREMENT,signature TEXT NOT NULL,app_package TEXT NOT NULL,action_type TEXT NOT NULL,detail TEXT NOT NULL,strategy TEXT NOT NULL,failures INTEGER NOT NULL DEFAULT 1,successes INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL,UNIQUE(signature,app_package,action_type))");
        db.execSQL("CREATE TABLE checkpoints(task_id TEXT PRIMARY KEY,goal TEXT NOT NULL,script_id TEXT NOT NULL,action_count INTEGER NOT NULL,history_json TEXT NOT NULL,state TEXT NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX checkpoints_state_time ON checkpoints(state,updated_at)");
    }

    @Override public void onConfigure(SQLiteDatabase db) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true); }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 1) onCreate(db);
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

    public Skill[] allSkills() { return SKILLS.clone(); }

    public synchronized String skillGuidance(String botId) {
        StringBuilder out = new StringBuilder();
        for (Skill skill : SKILLS) if (isSkillEnabled(botId, skill.id)) out.append("\n- ").append(skill.title).append(": ").append(skill.guidance);
        return out.toString();
    }

    public synchronized void addMessage(String botId, String role, String body, String imagePath) {
        if (botId == null || botId.isEmpty()) return;
        ContentValues v = new ContentValues(); v.put("id", "chat_" + System.currentTimeMillis() + "_" + Math.abs((int) (System.nanoTime() % 1000000)));
        v.put("bot_id", botId); v.put("role", clean(role, 20, "user")); v.put("body", body == null ? "" : body);
        v.put("image_path", imagePath == null ? "" : imagePath); v.put("created_at", System.currentTimeMillis());
        getWritableDatabase().insert("messages", null, v);
        getWritableDatabase().execSQL("DELETE FROM messages WHERE bot_id=? AND id NOT IN (SELECT id FROM messages WHERE bot_id=? ORDER BY created_at DESC LIMIT 300)", new Object[]{botId, botId});
    }

    public synchronized JSONArray messages(String botId, int limit) {
        JSONArray out = new JSONArray();
        if (botId == null) return out;
        try (Cursor c = getReadableDatabase().rawQuery("SELECT role,body,image_path,created_at FROM messages WHERE bot_id=? ORDER BY created_at DESC LIMIT ?", new String[]{botId, String.valueOf(Math.max(1, Math.min(300, limit)))})) {
            while (c.moveToNext()) out.put(new JSONObject().put("role", c.getString(0)).put("body", c.getString(1)).put("image", c.getString(2)).put("time", c.getLong(3)));
        } catch (Exception ignored) { }
        JSONArray ordered = new JSONArray();
        for (int i = out.length() - 1; i >= 0; i--) ordered.put(out.optJSONObject(i));
        return ordered;
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
    public static boolean ocrEnabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean(VISION_OCR, false); }
    public static void setOcrEnabled(Context c, boolean b) { c.getSharedPreferences(PREFS, 0).edit().putBoolean(VISION_OCR, b).apply(); }
    public static boolean yoloEnabled(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean(VISION_YOLO, false); }
    public static void setYoloEnabled(Context c, boolean b) { c.getSharedPreferences(PREFS, 0).edit().putBoolean(VISION_YOLO, b).apply(); }
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

    public static String persistImage(Context context, android.net.Uri uri) {
        if (context == null || uri == null) return "";
        File dir = new File(context.getFilesDir(), "bot_studio_images"); if (!dir.exists() && !dir.mkdirs()) return "";
        String name = "image_" + System.currentTimeMillis() + ".jpg"; File target = new File(dir, name);
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
            try (java.io.InputStream probe = context.getContentResolver().openInputStream(uri)) {
                if (probe == null) return "";
                BitmapFactory.decodeStream(probe, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return "";
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = 1;
            while (Math.max(bounds.outWidth / options.inSampleSize, bounds.outHeight / options.inSampleSize) > 2880) options.inSampleSize *= 2;
            Bitmap source;
            try (java.io.InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) return "";
                source = BitmapFactory.decodeStream(input, null, options);
            }
            if (source == null) return "";
            Bitmap scaled = source; boolean owned = false; int side = Math.max(source.getWidth(), source.getHeight());
            if (side > 1440) { float f = 1440f / side; scaled = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth()*f)), Math.max(1, Math.round(source.getHeight()*f)), true); owned = scaled != source; }
            try (FileOutputStream out = new FileOutputStream(target)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)) throw new IllegalStateException("تعذر ضغط الصورة");
                out.flush();
            } finally { if (owned && !scaled.isRecycled()) scaled.recycle(); if (!source.isRecycled()) source.recycle(); }
            return target.getAbsolutePath();
        } catch (Exception e) { target.delete(); return ""; }
    }

    private static String normalize(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT).replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ة','ه').replace('ى','ي');
        return s.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
    private static String clean(String s, int max, String fallback) {
        String v = s == null ? "" : s.trim(); if (v.isEmpty()) v = fallback; return v.length() > max ? v.substring(0, max) : v;
    }
}
