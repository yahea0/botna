package com.botna.automation;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class BotnaTaskEvidenceStore {
    private static final String ROOT = "botna_task_evidence";
    private static final int MAX_IMAGE_SIDE = 1200;
    private static final int JPEG_QUALITY = 72;

    private BotnaTaskEvidenceStore() { }

    public static synchronized void ensureTask(Context context, String taskId, String goal) {
        if (context == null || safe(taskId).isEmpty()) return;
        try {
            File dir = taskDir(context, taskId);
            if (!dir.exists() && !dir.mkdirs()) return;
            File manifest = new File(dir, "manifest.json");
            JSONObject state = readObject(manifest);
            if (state == null) {
                state = new JSONObject();
                state.put("taskId", taskId);
                state.put("goal", safe(goal));
                state.put("startedAt", System.currentTimeMillis());
                state.put("updatedAt", System.currentTimeMillis());
                state.put("frameCount", 0);
                state.put("phase", 0);
                writeObject(manifest, state);
            } else {
                state.put("updatedAt", System.currentTimeMillis());
                if (safe(goal).length() > 0 && safe(state.optString("goal", "")).isEmpty()) state.put("goal", safe(goal));
                writeObject(manifest, state);
            }
        } catch (Exception ignored) { }
    }

    public static synchronized String saveFrame(Context context, String taskId, String goal,
                                                 int actionIndex, String label, Bitmap source) {
        if (context == null || safe(taskId).isEmpty() || source == null || source.isRecycled()) return "";
        ensureTask(context, taskId, goal);
        Bitmap scaled = null;
        try {
            File dir = taskDir(context, taskId);
            JSONObject manifest = readObject(new File(dir, "manifest.json"));
            if (manifest == null) manifest = new JSONObject();
            int frameIndex = Math.max(1, manifest.optInt("frameCount", 0) + 1);
            String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
            String filename = String.format(Locale.US, "frame_%06d_%s.jpg", frameIndex, stamp);
            int maxSide = Math.max(source.getWidth(), source.getHeight());
            if (maxSide > MAX_IMAGE_SIDE) {
                float ratio = MAX_IMAGE_SIDE / (float) maxSide;
                scaled = Bitmap.createScaledBitmap(source,
                        Math.max(1, Math.round(source.getWidth() * ratio)),
                        Math.max(1, Math.round(source.getHeight() * ratio)), true);
            } else scaled = source;
            File destination = new File(dir, filename);
            try (FileOutputStream out = new FileOutputStream(destination)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) return "";
                out.flush();
                out.getFD().sync();
            }
            JSONObject frame = new JSONObject();
            frame.put("file", filename);
            frame.put("time", System.currentTimeMillis());
            frame.put("actionIndex", actionIndex);
            frame.put("label", safe(label));
            frame.put("width", source.getWidth());
            frame.put("height", source.getHeight());
            frame.put("fingerprint", fingerprint(source));
            appendLine(new File(dir, "frames.jsonl"), frame);
            manifest.put("frameCount", frameIndex);
            manifest.put("updatedAt", System.currentTimeMillis());
            writeObject(new File(dir, "manifest.json"), manifest);
            JSONObject event = new JSONObject();
            event.put("type", "screenshot"); event.put("time", frame.optLong("time"));
            event.put("actionIndex", actionIndex); event.put("label", safe(label));
            event.put("detail", "حُفظت لقطة الشاشة داخل أرشيف المهمة: " + filename);
            event.put("frame", filename); event.put("fingerprint", frame.optString("fingerprint"));
            appendLine(new File(dir, "events.jsonl"), event);
            return filename;
        } catch (Exception ignored) {
            return "";
        } finally {
            if (scaled != null && scaled != source && !scaled.isRecycled()) scaled.recycle();
        }
    }

    public static synchronized void recordEvent(Context context, String taskId, JSONObject event) {
        if (context == null || safe(taskId).isEmpty() || event == null) return;
        try {
            File dir = taskDir(context, taskId);
            if (!dir.exists() && !dir.mkdirs()) return;
            JSONObject item = new JSONObject(event.toString());
            if (!item.has("time")) item.put("time", System.currentTimeMillis());
            if (!item.has("taskId")) item.put("taskId", taskId);
            appendLine(new File(dir, "events.jsonl"), item);
            JSONObject manifest = readObject(new File(dir, "manifest.json"));
            if (manifest != null) { manifest.put("updatedAt", System.currentTimeMillis()); writeObject(new File(dir, "manifest.json"), manifest); }
        } catch (Exception ignored) { }
    }

    public static synchronized void recordEvent(Context context, String taskId, String type,
                                                int actionIndex, String label, String detail,
                                                boolean success, JSONObject definition) {
        try {
            JSONObject event = new JSONObject();
            event.put("type", safe(type)); event.put("actionIndex", actionIndex);
            event.put("label", safe(label)); event.put("detail", safe(detail));
            event.put("success", success); event.put("time", System.currentTimeMillis());
            if (definition != null) event.put("definition", new JSONObject(definition.toString()));
            recordEvent(context, taskId, event);
        } catch (Exception ignored) { }
    }

    public static synchronized void savePlanIfAbsent(Context context, String taskId, String goal, JSONArray plan) {
        if (context == null || safe(taskId).isEmpty() || plan == null || plan.length() == 0) return;
        try {
            File dir = taskDir(context, taskId);
            if (!dir.exists() && !dir.mkdirs()) return;
            File file = new File(dir, "task_plan.json");
            JSONObject existing = readObject(file);
            if (existing != null && !existing.optBoolean("fallback", false)
                    && existing.optJSONArray("steps") != null && existing.optJSONArray("steps").length() > 0) return;
            JSONObject saved = new JSONObject();
            saved.put("taskId", taskId); saved.put("goal", safe(goal));
            saved.put("steps", new JSONArray(plan.toString()));
            saved.put("savedAt", System.currentTimeMillis());
            int previousPhase = existing == null ? 0 : Math.max(0, existing.optInt("currentPhase", 0));
            saved.put("currentPhase", Math.min(previousPhase, Math.max(0, plan.length() - 1)));
            saved.put("fallback", false);
            writeObject(file, saved);
            recordEvent(context, taskId, "task_plan", 0, "خطة المهمة المجزأة", plan.toString(), true, null);
        } catch (Exception ignored) { }
    }

    public static synchronized void ensureFallbackPlan(Context context, String taskId, String goal) {
        if (context == null || safe(taskId).isEmpty()) return;
        try {
            File dir = taskDir(context, taskId);
            File planFile = new File(dir, "task_plan.json");
            JSONObject existing = readObject(planFile);
            if (existing != null && existing.optJSONArray("steps") != null && existing.optJSONArray("steps").length() > 0) return;
            String raw = safe(goal).replaceAll("[؛;]+", "\n");
            raw = raw.replaceAll("(?iu)\\s+(?:ثم|وبعدين|بعدها|بعد ذلك|ومن ثم|كمان)\\s+", "\n");
            String[] parts = raw.split("[\\n.!؟?]+\\s*");
            JSONArray steps = new JSONArray();
            for (String part : parts) {
                String item = part == null ? "" : part.trim();
                if (item.length() < 4) continue;
                steps.put(item.length() > 500 ? item.substring(0, 500) : item);
                if (steps.length() >= 15) break;
            }
            if (steps.length() == 0 && !raw.trim().isEmpty()) steps.put(raw.trim().length() > 500 ? raw.trim().substring(0, 500) : raw.trim());
            if (steps.length() > 0) {
                JSONObject plan = new JSONObject(); plan.put("taskId", taskId); plan.put("goal", safe(goal));
                plan.put("steps", steps); plan.put("currentPhase", 0); plan.put("savedAt", System.currentTimeMillis()); plan.put("fallback", true);
                writeObject(planFile, plan);
            }
        } catch (Exception ignored) { }
    }

    public static synchronized void updatePlanProgress(Context context, String taskId, int phase, String detail) {
        if (context == null || safe(taskId).isEmpty()) return;
        try {
            File dir = taskDir(context, taskId);
            JSONObject plan = readObject(new File(dir, "task_plan.json"));
            if (plan != null) { plan.put("currentPhase", Math.max(0, phase)); plan.put("updatedAt", System.currentTimeMillis()); writeObject(new File(dir, "task_plan.json"), plan); }
            JSONObject manifest = readObject(new File(dir, "manifest.json"));
            if (manifest != null) { manifest.put("phase", Math.max(0, phase)); writeObject(new File(dir, "manifest.json"), manifest); }
            recordEvent(context, taskId, "phase", phase, "تقدم خطة المهمة", safe(detail), true, null);
        } catch (Exception ignored) { }
    }

    public static synchronized String contextSummary(Context context, String taskId, int maxEvents) {
        if (context == null || safe(taskId).isEmpty()) return "";
        StringBuilder out = new StringBuilder("\n--- ذاكرة المهمة الدائمة وأرشيف لقطات الشاشة ---\n");
        try {
            File dir = taskDir(context, taskId);
            JSONObject plan = readObject(new File(dir, "task_plan.json"));
            if (plan != null) {
                out.append("خطة المهمة المحفوظة (لا تُسقط أي مرحلة): ");
                JSONArray steps = plan.optJSONArray("steps");
                int phase = plan.optInt("currentPhase", 0);
                if (steps != null) for (int i = 0; i < steps.length(); i++) {
                    out.append(i == phase ? " [الحالية] " : " ").append(i + 1).append(". ").append(steps.optString(i, "")).append(";");
                }
                out.append("\nالمرحلة الحالية المحفوظة: ").append(phase + 1).append(" من ").append(steps == null ? 0 : steps.length()).append(".\n");
            } else out.append("لم يثبت النموذج خطة مجزأة بعد؛ احفظ خطة فرعية مرتبة الآن ثم نفّذ فعلًا واحدًا فقط.\n");
            List<JSONObject> frames = readTail(new File(dir, "frames.jsonl"), 8);
            out.append("آخر لقطات الشاشة المحفوظة (كل لقطة محفوظة كصورة داخل أرشيف المهمة):\n");
            for (JSONObject frame : frames) out.append("- الإجراء ").append(frame.optInt("actionIndex", -1))
                    .append("، ").append(formatTime(frame.optLong("time"))).append("، ").append(frame.optString("label", "لقطة"))
                    .append("، الملف=").append(frame.optString("file", ""))
                    .append("، بصمة الشاشة=").append(frame.optString("fingerprint", "")).append("\n");
            List<JSONObject> events = readTail(new File(dir, "events.jsonl"), Math.max(1, Math.min(40, maxEvents)));
            out.append("سجل الأحداث والأفعال الدائم (الأحدث أخيرًا):\n");
            for (JSONObject event : events) {
                out.append("- ").append(formatTime(event.optLong("time"))).append(" | ")
                        .append(event.optString("type", "event")).append(" | خطوة ").append(event.optInt("actionIndex", -1))
                        .append(" | ").append(event.optString("label", ""))
                        .append(" | نجاح=").append(event.optBoolean("success", false))
                        .append(" | ").append(shorten(event.optString("detail", ""), 500)).append("\n");
            }
            out.append("استخدم الخطة والسجل كذاكرة طويلة، ولا تعِد حركة فاشلة كما هي. إذا كانت الشاشة لا تطابق المرحلة الحالية، أوقف الخطة المنحرفة واستخدم rollback_previous_step عندما يكون الرجوع آمنًا.");
        } catch (Exception e) { out.append("تعذر قراءة جزء من أرشيف المهمة: ").append(e.getClass().getSimpleName()); }
        if (out.length() > 10500) out.setLength(10500);
        return out.toString();
    }

    public static synchronized JSONArray listTasks(Context context) {
        JSONArray out = new JSONArray();
        if (context == null) return out;
        File root = new File(context.getFilesDir(), ROOT);
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) return out;
        java.util.Arrays.sort(dirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File dir : dirs) {
            JSONObject manifest = readObject(new File(dir, "manifest.json"));
            if (manifest == null) continue;
            try {
                manifest.put("taskId", dir.getName());
                manifest.put("frameCount", countLines(new File(dir, "frames.jsonl")));
                out.put(manifest);
            } catch (JSONException ignored) { }
        }
        return out;
    }

    public static synchronized JSONArray listFrames(Context context, String taskId, int limit) {
        JSONArray out = new JSONArray();
        if (context == null || safe(taskId).isEmpty()) return out;
        List<JSONObject> items = readTail(new File(taskDir(context, taskId), "frames.jsonl"), Math.max(1, Math.min(500, limit)));
        for (JSONObject item : items) out.put(item);
        return out;
    }

    public static Bitmap loadFrame(Context context, String taskId, String filename) {
        if (context == null || safe(taskId).isEmpty() || safe(filename).isEmpty()
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) return null;
        File file = new File(taskDir(context, taskId), filename);
        return file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
    }

    public static Bitmap createVisionContextSheet(Context context, String taskId, Bitmap current, int historyLimit) {
        if (context == null || current == null || current.isRecycled()) return null;
        JSONArray frames = listFrames(context, taskId, Math.max(2, historyLimit + 1));
        List<JSONObject> previous = new ArrayList<>();
        for (int i = Math.max(0, frames.length() - 2); i >= 0 && previous.size() < Math.max(1, historyLimit); i--) {
            JSONObject item = frames.optJSONObject(i);
            if (item != null) previous.add(item);
        }
        if (previous.isEmpty()) return null;
        int sideWidth = Math.max(220, current.getWidth() / 3);
        Bitmap sheet = Bitmap.createBitmap(current.getWidth() + sideWidth, current.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sheet);
        canvas.drawColor(Color.rgb(7, 12, 20));
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG); border.setColor(Color.rgb(35, 226, 244)); border.setStrokeWidth(3); border.setStyle(Paint.Style.STROKE);
        canvas.drawBitmap(current, 0, 0, null);
        canvas.drawRect(0, 0, current.getWidth(), current.getHeight(), border);
        Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG); labelPaint.setColor(Color.WHITE); labelPaint.setTextSize(Math.max(15, current.getWidth() * 0.018f)); labelPaint.setStyle(Paint.Style.FILL);
        int count = previous.size(); float slotHeight = current.getHeight() / (float) count;
        for (int i = 0; i < count; i++) {
            JSONObject frame = previous.get(i);
            Bitmap old = loadFrame(context, taskId, frame.optString("file", ""));
            float top = i * slotHeight;
            canvas.drawRect(current.getWidth(), top, current.getWidth() + sideWidth, top + slotHeight, new Paint() {{ setColor(Color.rgb(13, 26, 39)); setStyle(Paint.Style.FILL); }});
            if (old != null) {
                float scale = Math.min(sideWidth / (float) old.getWidth(), slotHeight / (float) old.getHeight());
                float w = old.getWidth() * scale, h = old.getHeight() * scale;
                RectF dst = new RectF(current.getWidth() + (sideWidth - w) / 2f, top + (slotHeight - h) / 2f, current.getWidth() + (sideWidth + w) / 2f, top + (slotHeight + h) / 2f);
                canvas.drawBitmap(old, null, dst, null);
                old.recycle();
            }
            canvas.drawRect(current.getWidth(), top, current.getWidth() + sideWidth, top + slotHeight, border);
            String label = "قبل • خطوة " + frame.optInt("actionIndex", -1);
            canvas.drawText(label, current.getWidth() + 8, Math.min(top + 24, top + slotHeight - 4), labelPaint);
        }
        return sheet;
    }

    private static File root(Context context) { return new File(context.getFilesDir(), ROOT); }
    private static File taskDir(Context context, String taskId) { return new File(root(context), safe(taskId).replaceAll("[^A-Za-z0-9_-]", "_")); }
    private static JSONObject readObject(File file) {
        if (file == null || !file.isFile()) return null;
        try (FileInputStream in = new FileInputStream(file); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int read; while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
            return new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) { return null; }
    }
    private static void writeObject(File file, JSONObject value) throws Exception {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) { out.write(value.toString().getBytes(StandardCharsets.UTF_8)); out.flush(); out.getFD().sync(); }
        if (!temp.renameTo(file)) { try (FileOutputStream out = new FileOutputStream(file)) { out.write(value.toString().getBytes(StandardCharsets.UTF_8)); out.flush(); } temp.delete(); }
    }
    private static void appendLine(File file, JSONObject value) throws Exception {
        try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8))) { out.write(value.toString()); out.newLine(); }
    }
    private static List<JSONObject> readTail(File file, int limit) {
        List<JSONObject> out = new ArrayList<>();
        if (file == null || !file.isFile() || limit <= 0) return out;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            ArrayList<String> lines = new ArrayList<>(); String line;
            while ((line = reader.readLine()) != null) { lines.add(line); if (lines.size() > limit * 2) lines.remove(0); }
            int start = Math.max(0, lines.size() - limit);
            for (int i = start; i < lines.size(); i++) try { out.add(new JSONObject(lines.get(i))); } catch (Exception ignored) { }
        } catch (Exception ignored) { }
        return out;
    }
    private static int countLines(File file) {
        if (file == null || !file.isFile()) return 0;
        int count = 0;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) { while (in.readLine() != null) count++; }
        catch (Exception ignored) { }
        return count;
    }
    private static String fingerprint(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) return "";
        long bits = 0L; int bit = 0; int rows = 8, cols = 8;
        for (int y = 0; y < rows; y++) {
            int py = Math.min(bitmap.getHeight() - 1, (int) ((y + 0.5f) * bitmap.getHeight() / rows));
            for (int x = 0; x < cols; x++) {
                int px = Math.min(bitmap.getWidth() - 1, (int) ((x + 0.5f) * bitmap.getWidth() / cols));
                int c = bitmap.getPixel(px, py); int gray = (Color.red(c) * 30 + Color.green(c) * 59 + Color.blue(c) * 11) / 100;
                if (gray > 127) bits |= (1L << bit); bit++;
            }
        }
        return String.format(Locale.US, "%016x", bits);
    }
    private static String formatTime(long time) { return new SimpleDateFormat("yyyy/MM/dd hh:mm:ss a", Locale.getDefault()).format(new Date(time)); }
    private static String shorten(String value, int max) { String s = safe(value).replaceAll("\\s+", " ").trim(); return s.length() > max ? s.substring(0, max) + "…" : s; }
    private static String safe(String value) { return value == null ? "" : value; }
}
