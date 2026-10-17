package com.botna.automation;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Provider-agnostic Arabic/Pale​stinian natural-language plan compiler.
 * AI output is validated JSON data (never Java, shell, JS, or arbitrary executable code).
 */
public final class BotnaAICompiler {
    public static final String PREFS = "botna_settings";
    private static final String PROVIDER_KEY = "ai_provider_id_v2";
    private static final String MODEL_PREFIX = "ai_model_v2_";
    private static final String ENDPOINT_PREFIX = "ai_endpoint_v2_";

    public static final String PROVIDER_GITHUB_MODELS = "github_models";
    public static final String PROVIDER_OPENAI_COMPAT = "openai_compatible";
    public static final String PROVIDER_OPENAI_RESPONSES = "openai_responses";
    public static final String PROVIDER_ANTHROPIC = "anthropic_messages";
    public static final String PROVIDER_GEMINI = "gemini_generate";
    public static final String PROVIDER_CUSTOM = "custom_compatible";
    public static final String PROVIDER_COPILOT_BRIDGE = "copilot_bridge";

    private static final int CARD = Color.rgb(8, 14, 22);
    private static final int CYAN = Color.rgb(35, 226, 244);
    private static final int TEXT = Color.rgb(239, 248, 255);
    private static final int MUTED = Color.rgb(151, 174, 194);

    private static final String SYSTEM_INSTRUCTIONS =
            "أنت محرك ترجمة أوامر احترافي لتطبيق أتمتة أندرويد اسمه Botna. المستخدم يتحدث العربية بلهجة فلسطينية، وقد يستخدم العامية الفلسطينية أو أخطاء إملائية أو تعبيرات غير مرتبة. استنبط قصده العملي دون تغيير الهدف، وطبّع اللهجة إلى أوامر دقيقة. " +
            "أخرج JSON صالحًا فقط بالشكل {\"steps\":[{\"action\":\"...\",\"image_index\":1,\"app_name\":\"\",\"text\":\"\",\"delay_ms\":0,\"duration_ms\":800,\"x_percent\":50,\"y_percent\":50,\"direction\":\"up\",\"count\":1,\"url\":\"https://...\",\"message\":\"...\",\"attempts\":3,\"max_attempts\":120,\"interval_ms\":5000,\"timeout_ms\":4000,\"sound\":true}],\"warnings\":[\"...\"]}. لا تخرج Java أو shell أو JavaScript أو SQL أو كودًا عامًا. كل عنصر خطوة واحدة فقط وبترتيب التنفيذ. " +
            "الأفعال الوحيدة المسموحة: launch_app, open_url, find_image, tap_image, click_text, type_text, press_enter, wait, wait_for_text, wait_for_input, tap_at, long_press_at, scroll, back, home, check_internet, wait_for_internet, notify, if_success, if_fail, else, end_if, repeat_start, repeat_end. " +
            "استخدم launch_app لفتح تطبيق مثبت؛ اسم التطبيق يوضع في app_name. استخدم open_url لرابط HTTP/HTTPS فقط؛ لصفحة الراوتر استخدم الرابط الذي ذكره المستخدم ولا تخمّن بيانات الدخول أو زر إعادة التشغيل. لا تدّعِ أن تطبيقًا أو زرًا موجود إذا لم يتأكد ذلك. استخدم find_image للعثور على قالب دون النقر، وtap_image للعثور والنقر. image_index هو رقم القالب من مكتبة الصور المعطاة. استخدم click_text فقط للنصوص التي يمكن الوصول إليها عبر Accessibility؛ بعض الألعاب مثل Unity ترسم النصوص داخل صور، ففيها فضّل قالب الصورة أو إحداثياتها. " +
            "استخدم wait_for_text لانتظار ظهور نص محدد في شجرة إمكانية الوصول وwait_for_input لانتظار ظهور حقل قابل للتحرير؛ لا تستخدم انتظارًا ثابتًا إذا طلب المستخدم صراحةً الانتظار حتى يظهر العنصر. استخدم type_text لكتابة النص الحرفي المحدد في حقل إدخال مركّز؛ لا تضف أو تغيّر النص بين علامتي الاقتباس. يستخدم التطبيق ACTION_SET_TEXT ثم اللصق الاحتياطي إذا احتاج الأمر، وليس محاكاة كل ضغطة مفتاح فعلية. استخدم press_enter لإرسال فعل Enter/بحث في الحقل إن كان التطبيق يدعمه، وإلا انقر زر البحث الظاهر. استخدم tap_at وlong_press_at بإحداثيات نسبية من 0 إلى 100. wait.delay_ms بين 0 و60000، وlong_press_at.duration_ms بين 300 و5000، وrepeat_start.count بين 1 و1000. " +
            "check_internet يرسل طلب HTTP(S) صغيرًا إلى اختبار اتصال ويراجع الاستجابة؛ اكتب attempts (1–20) وinterval_ms وtimeout_ms. wait_for_internet يعيد الفحص حتى يعود الاتصال أو تنفد max_attempts؛ استخدم فاصلًا واقعيًا مثل 5000 مللي ثانية. بعد النجاح، استخدم if_success ثم notify لإظهار رسالة وتشغيل تنبيه صوتي. بعد الفشل النهائي استخدم if_fail. لا تدّعِ أن ظهور إشارة Wi-Fi يعني وجود إنترنت. " +
            "مثال: 'إذا لقيت الصورة 4 استنى ثانيتين وبعدين ارجع' => find_image للصورة 4، ثم if_success، ثم wait 2000، ثم back، ثم end_if. 'إذا ما لقيتها اعمل كذا وإلا...' يجب أن تعكس الشرط بدقة. مثال الشبكة: check_internet attempts=3 interval_ms=5000 ثم if_fail ثم open_url بعنوان صفحة الراوتر الذي أعطاه المستخدم، ولا تختلق خطوات إدارة الراوتر أو كلمة المرور؛ اتبع فقط الأزرار المتحقق منها في الشاشة. مثال انتظار العودة: wait_for_internet max_attempts=120 interval_ms=5000 ثم if_success ثم notify برسالة النجاح. اجعل كل كتلة شرط وتكرار متداخلة متوازنة، واستخدم else/end_if/repeat_end صراحة. لا تبتكر صورًا أو أسماء تطبيقات أو أفعالًا غير مدعومة. " +
            "إذا كان وصف المستخدم ملتبسًا، اختر فقط ما تدعمه العبارة بثقة وأضف تحذيرًا بالعربية يذكر الجزء غير المؤكد؛ لا تخمّن عملية خطرة. لا تنفّذ عمليات شراء أو مدفوعات أو حذف بيانات أو إرسال رسائل خارجية تلقائيًا؛ اطلب تأكيدًا عبر تحذير بدل إنشاء تنفيذ غير قابل للتراجع. " +
            "للتسلسلات الطويلة احتفظ بكل خطوة وبترتيبها؛ لا تختصر أو تُسقط الفعل بين خطوات، ولا تضع خطوة بعد if_success/if_fail خارج كتلتها إلا عندما يصرّح المستخدم بذلك. مثال بحث فيديو: launch_app للتطبيق المذكور، wait_for_input، click_text على حقل البحث إن لم يكن مركّزًا، type_text للنص الحرفي، press_enter أو click_text لزر البحث، ثم انتظر النتائج، واختر النتيجة/القناة من عناصر الشاشة أو الصور المتاحة، وافتح قائمة المشاركة، واختر تطبيق المشاركة المذكور إذا ظهر فيها. لا تدّعِ أن تنزيلًا نجح أو أن تطبيقًا مثبت ما لم يتحقق ذلك. " +
            "في سيناريو الراوتر والشبكة، check_internet يعني طلب HTTP(S) فعليًا مع قراءة الاستجابة؛ استخدم if_fail بعد فشل كل المحاولات. لا تختلق عنوان الراوتر أو بيانات الدخول. إذا أعطى المستخدم عنوان صفحة الراوتر وطلب إعادة التشغيل صراحة، افتح العنوان ثم نفّذ فقط أسماء الأزرار/النصوص التي ظهرت في لقطة مؤكدة أو حدّدها المستخدم (مثل إعادة التشغيل والتأكيد)، وانتظر نتيجة الواجهة؛ إذا تعذر التعرف عليها، أضف تحذيرًا بدل النقر العشوائي. بعد الإجراء استخدم wait_for_internet ثم if_success وnotify برسالة واضحة، أو if_fail لإظهار فشل المهلة. أندرويد لا يتيح عادةً لتطبيق عادي تشغيل مفتاح Wi-Fi/راوتر مباشرةً بلا صلاحيات خاصة؛ لا تعد بذلك. " +
            "أدخل النص عن طريق type_text بحروفه كما ورد، بما في ذلك العربية وعلامات الاقتباس الداخلية؛ فتح حقل الكتابة يتم عبر wait_for_input ثم click_text/ tap_at إذا احتاج التطبيق ذلك. لا تعتبر ACTION_SET_TEXT نفسه محاكاة لكل ضغطة مفتاح؛ التطبيق يستخدم إجراء إمكانية الوصول واللصق الاحتياطي عندما يسمح التطبيق الهدف. " +
            "إذا أُرفقت لقطة شاشة، استخدمها لتحديد السياق الظاهر فقط، ولا تدّعِ معرفة ما هو خارج الصورة. خطتك مقترح أولي، وسيتم فحص بنية الشروط والتكرار والأنواع المدعومة داخل التطبيق قبل التشغيل.";

    private BotnaAICompiler() { }

    public static String providerId(Context context) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PROVIDER_KEY, "");
        if (saved != null && !saved.trim().isEmpty()) return saved.trim();
        // Keep older installations using an existing OpenAI key on the old endpoint.
        if (!BotnaKeyVault.load(context).isEmpty()) return PROVIDER_OPENAI_RESPONSES;
        return PROVIDER_GITHUB_MODELS;
    }

    public static String providerLabel(String id) {
        if (PROVIDER_GITHUB_MODELS.equals(id)) return "GitHub Models — حصة مجانية حسب الحدود";
        if (PROVIDER_OPENAI_COMPAT.equals(id)) return "OpenAI-compatible — DeepSeek / OpenRouter / Groq";
        if (PROVIDER_OPENAI_RESPONSES.equals(id)) return "OpenAI Responses API";
        if (PROVIDER_ANTHROPIC.equals(id)) return "Anthropic Messages API";
        if (PROVIDER_GEMINI.equals(id)) return "Google Gemini API";
        if (PROVIDER_CUSTOM.equals(id)) return "مزود مخصص متوافق مع Chat Completions";
        if (PROVIDER_COPILOT_BRIDGE.equals(id)) return "GitHub Copilot عبر جسر خارجي (متقدم)";
        return id == null ? "غير محدد" : id;
    }

    public static String model(Context context) {
        String id = providerId(context);
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(MODEL_PREFIX + id, defaultModel(id));
    }

    public static String endpoint(Context context) {
        String id = providerId(context);
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ENDPOINT_PREFIX + id, defaultEndpoint(id));
    }

    public static boolean isConfigured(Context context) {
        String id = providerId(context);
        return !BotnaKeyVault.load(context, id).isEmpty() && !endpoint(context).trim().isEmpty() && !model(context).trim().isEmpty();
    }

    public static void saveSettings(Context context, String key, String model, boolean clearKey) throws Exception {
        String id = providerId(context);
        saveProfileSettings(context, id, key, model, endpoint(context), clearKey);
    }

    public static void saveProfileSettings(Context context, String id, String key, String model, String endpoint, boolean clearKey) throws Exception {
        String provider = normalizeProviderId(id);
        String chosenModel = model == null || model.trim().isEmpty() ? defaultModel(provider) : model.trim();
        String chosenEndpoint = endpoint == null ? "" : endpoint.trim();
        if (!PROVIDER_COPILOT_BRIDGE.equals(provider) && chosenEndpoint.isEmpty()) {
            chosenEndpoint = defaultEndpoint(provider);
        }
        if (clearKey) BotnaKeyVault.save(context, provider, "");
        else if (key != null && !key.trim().isEmpty()) BotnaKeyVault.save(context, provider, key.trim());
        boolean ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PROVIDER_KEY, provider)
                .putString(MODEL_PREFIX + provider, chosenModel)
                .putString(ENDPOINT_PREFIX + provider, chosenEndpoint)
                .commit();
        if (!ok) throw new IllegalStateException("تعذر حفظ إعدادات مزود الذكاء الاصطناعي");
    }

    /** Legacy caller interface; the optional screenshot supplies current screen context to vision-capable models. */
    public static JSONObject compile(Context context, String instructions, JSONArray library) throws Exception {
        return compile(context, instructions, library, null);
    }

    public static JSONObject compile(Context context, String instructions, JSONArray library, Bitmap screenContext) throws Exception {
        String provider = providerId(context);
        String apiKey = BotnaKeyVault.load(context, provider);
        if (apiKey.isEmpty()) throw new IllegalStateException("لا يوجد مفتاح محفوظ لمزوّد " + providerLabel(provider) + ". افتح إعدادات الذكاء الاصطناعي.");
        String model = model(context).trim();
        String endpoint = endpoint(context).trim();
        if (model.isEmpty() || endpoint.isEmpty()) throw new IllegalStateException("أدخل رابط API واسم النموذج لهذا المزوّد أولًا.");

        String userText = "مكتبة الصور المتاحة، أرقامها وأسماؤها (لا تختَر رقمًا خارجها): " + libraryForPrompt(library)
                + "\n\nالأمر المكتوب باللهجة الفلسطينية المطلوب ترجمته إلى خطة تنفيذ دقيقة:\n" + (instructions == null ? "" : instructions.trim());
        String encodedImage = screenContext == null ? null : bitmapToJpegBase64(screenContext);
        try {
            return requestPlan(provider, endpoint, apiKey, model, userText, encodedImage);
        } catch (Exception first) {
            if (encodedImage == null || !looksLikeVisionCompatibilityError(first)) throw first;
            JSONObject fallback = requestPlan(provider, endpoint, apiKey, model, userText, null);
            JSONArray warnings = fallback.optJSONArray("warnings");
            if (warnings == null) { warnings = new JSONArray(); fallback.put("warnings", warnings); }
            warnings.put("المزوّد أو النموذج لم يقبل صورة الشاشة؛ تُرجمت التعليمات دون سياق بصري مباشر. جرّب نموذجًا يدعم الرؤية.");
            return fallback;
        }
    }

    private static JSONObject requestPlan(String provider, String endpoint, String apiKey, String model, String userText, String imageBase64) throws Exception {
        if (PROVIDER_ANTHROPIC.equals(provider)) return requestAnthropic(endpoint, apiKey, model, userText, imageBase64);
        if (PROVIDER_GEMINI.equals(provider)) return requestGemini(endpoint, apiKey, model, userText, imageBase64);
        if (PROVIDER_OPENAI_RESPONSES.equals(provider)) return requestResponses(endpoint, apiKey, model, userText, imageBase64);
        if (PROVIDER_GITHUB_MODELS.equals(provider) || PROVIDER_OPENAI_COMPAT.equals(provider)
                || PROVIDER_CUSTOM.equals(provider) || PROVIDER_COPILOT_BRIDGE.equals(provider)) {
            return requestChatCompletions(provider, endpoint, apiKey, model, userText, imageBase64);
        }
        throw new IllegalStateException("المزوّد غير معروف: " + provider);
    }

    private static JSONObject requestChatCompletions(String provider, String endpoint, String key, String model, String userText, String image) throws Exception {
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", SYSTEM_INSTRUCTIONS));
        JSONObject user = new JSONObject().put("role", "user");
        if (image == null) user.put("content", userText);
        else {
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("type", "text").put("text", userText + "\n\nحلّل هذه اللقطة الحالية لاستخراج السياق الظاهر قبل بناء خطة الخطوات."));
            parts.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:image/jpeg;base64," + image)));
            user.put("content", parts);
        }
        messages.put(user);
        JSONObject body = new JSONObject().put("model", model).put("messages", messages).put("temperature", 0.1).put("max_tokens", 6000);
        JSONObject response = postJson(endpoint, key, body, headersFor(provider));
        String text = extractChatText(response);
        return validatePlanText(text);
    }

    private static JSONObject requestResponses(String endpoint, String key, String model, String userText, String image) throws Exception {
        JSONObject body = new JSONObject().put("model", model).put("instructions", SYSTEM_INSTRUCTIONS)
                .put("max_output_tokens", 6000).put("store", false)
                .put("text", new JSONObject().put("format", new JSONObject().put("type", "json_object")));
        if (image == null) body.put("input", userText);
        else {
            JSONArray content = new JSONArray();
            content.put(new JSONObject().put("type", "input_text").put("text", userText + "\n\nحلّل لقطة الشاشة الحالية المرفقة."));
            content.put(new JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64," + image));
            JSONArray input = new JSONArray();
            input.put(new JSONObject().put("role", "user").put("content", content));
            body.put("input", input);
        }
        JSONObject response = postJson(resolveResponsesEndpoint(endpoint), key, body, null);
        return validatePlanText(extractResponsesText(response));
    }

    private static JSONObject requestAnthropic(String endpoint, String key, String model, String userText, String image) throws Exception {
        JSONObject body = new JSONObject().put("model", model).put("max_tokens", 6000).put("system", SYSTEM_INSTRUCTIONS);
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", userText + (image == null ? "" : "\n\nحلّل لقطة الشاشة الحالية المرفقة.")));
        if (image != null) content.put(new JSONObject().put("type", "image").put("source", new JSONObject()
                .put("type", "base64").put("media_type", "image/jpeg").put("data", image)));
        JSONArray messages = new JSONArray().put(new JSONObject().put("role", "user").put("content", content));
        body.put("messages", messages);
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", key);
        headers.put("anthropic-version", "2023-06-01");
        return validatePlanText(extractAnthropicText(postJson(resolveAnthropicEndpoint(endpoint), null, body, headers)));
    }

    private static JSONObject requestGemini(String endpoint, String key, String model, String userText, String image) throws Exception {
        String base = endpoint.trim();
        String url;
        if (base.contains(":generateContent")) url = base + (base.contains("?") ? "&" : "?") + "key=" + URLEncoder.encode(key, "UTF-8");
        else url = trimTrailingSlash(base) + "/models/" + URLEncoder.encode(model, "UTF-8") + ":generateContent?key=" + URLEncoder.encode(key, "UTF-8");
        JSONObject partText = new JSONObject().put("text", userText + (image == null ? "" : "\n\nحلّل لقطة الشاشة الحالية المرفقة."));
        JSONArray parts = new JSONArray().put(partText);
        if (image != null) parts.put(new JSONObject().put("inlineData", new JSONObject().put("mimeType", "image/jpeg").put("data", image)));
        JSONObject body = new JSONObject()
                .put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", SYSTEM_INSTRUCTIONS))))
                .put("contents", new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)))
                .put("generationConfig", new JSONObject().put("temperature", 0.1).put("maxOutputTokens", 6000).put("responseMimeType", "application/json"));
        JSONObject response = postJson(url, null, body, null);
        return validatePlanText(extractGeminiText(response));
    }

    private static Map<String, String> headersFor(String provider) {
        Map<String, String> headers = new HashMap<>();
        if (PROVIDER_GITHUB_MODELS.equals(provider)) {
            headers.put("Accept", "application/vnd.github+json");
            headers.put("X-GitHub-Api-Version", "2022-11-28");
        }
        return headers;
    }

    private static JSONObject postJson(String endpoint, String key, JSONObject body, Map<String, String> extraHeaders) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            if (key != null && !key.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + key);
            if (extraHeaders != null) for (Map.Entry<String, String> header : extraHeaders.entrySet()) connection.setRequestProperty(header.getKey(), header.getValue());
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = connection.getOutputStream()) { out.write(payload); }
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readAll(stream);
            JSONObject json = new JSONObject(responseText);
            if (status < 200 || status >= 300) {
                JSONObject error = json.optJSONObject("error");
                String message = error == null ? responseText : error.optString("message", error.optString("type", "HTTP " + status));
                if (message.length() > 500) message = message.substring(0, 500);
                throw new IllegalStateException("فشل API (HTTP " + status + "): " + message);
            }
            return json;
        } finally { if (connection != null) connection.disconnect(); }
    }

    private static JSONObject validatePlanText(String outputText) throws JSONException {
        String text = stripJsonFences(outputText);
        if (text.trim().isEmpty()) throw new JSONException("لم يُرجع النموذج خطة أوامر.");
        JSONObject plan = new JSONObject(text);
        if (plan.optJSONArray("steps") == null) throw new JSONException("الاستجابة لا تحتوي على قائمة steps.");
        if (plan.optJSONArray("warnings") == null) plan.put("warnings", new JSONArray());
        return plan;
    }

    private static String extractChatText(JSONObject response) throws JSONException {
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return response.optString("output_text", "");
        JSONObject message = choices.optJSONObject(0) == null ? null : choices.optJSONObject(0).optJSONObject("message");
        if (message == null) return "";
        Object content = message.opt("content");
        if (content instanceof String) return (String) content;
        if (content instanceof JSONArray) {
            JSONArray parts = (JSONArray) content; StringBuilder out = new StringBuilder();
            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i); if (part != null) out.append(part.optString("text", ""));
            }
            return out.toString();
        }
        return "";
    }

    private static String extractResponsesText(JSONObject response) throws JSONException {
        String direct = response.optString("output_text", "");
        if (!direct.isEmpty()) return direct;
        JSONArray output = response.optJSONArray("output");
        if (output != null) for (int i = 0; i < output.length(); i++) {
            JSONObject item = output.optJSONObject(i);
            if (item == null || !"message".equals(item.optString("type"))) continue;
            JSONArray content = item.optJSONArray("content");
            if (content != null) for (int j = 0; j < content.length(); j++) {
                JSONObject part = content.optJSONObject(j);
                if (part != null && ("output_text".equals(part.optString("type")) || part.has("text"))) return part.optString("text", "");
            }
        }
        return "";
    }

    private static String extractAnthropicText(JSONObject response) {
        JSONArray content = response.optJSONArray("content");
        if (content != null) for (int i = 0; i < content.length(); i++) {
            JSONObject part = content.optJSONObject(i);
            if (part != null && "text".equals(part.optString("type"))) return part.optString("text", "");
        }
        return "";
    }

    private static String extractGeminiText(JSONObject response) {
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) return "";
        JSONObject content = candidates.optJSONObject(0) == null ? null : candidates.optJSONObject(0).optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) { JSONObject p = parts.optJSONObject(i); if (p != null) out.append(p.optString("text", "")); }
        return out.toString();
    }

    private static String libraryForPrompt(JSONArray library) throws JSONException {
        JSONArray out = new JSONArray();
        if (library != null) for (int i = 0; i < library.length(); i++) {
            JSONObject source = library.optJSONObject(i); if (source == null) continue;
            JSONObject item = new JSONObject();
            item.put("image_index", i + 1);
            item.put("name", source.optString("name", "صورة " + (i + 1)));
            item.put("default_threshold", source.optInt("threshold", 85));
            out.put(item);
        }
        return out.toString();
    }

    private static String bitmapToJpegBase64(Bitmap source) throws Exception {
        Bitmap scaled = source;
        boolean copied = false;
        int maxSide = Math.max(source.getWidth(), source.getHeight());
        if (maxSide > 1200) {
            float ratio = 1200f / maxSide;
            scaled = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * ratio)), Math.max(1, Math.round(source.getHeight() * ratio)), true);
            copied = scaled != source;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)) throw new IllegalStateException("تعذر تجهيز لقطة الشاشة للإرسال");
            return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
        } finally { if (copied && !scaled.isRecycled()) scaled.recycle(); }
    }

    private static boolean looksLikeVisionCompatibilityError(Exception error) {
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("http 400") || message.contains("http 422") || message.contains("image")
                || message.contains("vision") || message.contains("unsupported") || message.contains("inline_data")
                || message.contains("inline data") || message.contains("multimodal") || message.contains("image_url");
    }

    private static String resolveResponsesEndpoint(String endpoint) {
        String value = trimTrailingSlash(endpoint);
        if (value.endsWith("/responses")) return value;
        if (value.endsWith("/v1")) return value + "/responses";
        return value;
    }

    private static String resolveAnthropicEndpoint(String endpoint) {
        String value = trimTrailingSlash(endpoint);
        if (value.endsWith("/messages")) return value;
        if (value.endsWith("/v1")) return value + "/messages";
        return value;
    }

    private static String trimTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String defaultEndpoint(String provider) {
        switch (provider) {
            case PROVIDER_GITHUB_MODELS: return "https://models.github.ai/inference/chat/completions";
            case PROVIDER_OPENAI_COMPAT: return "https://api.deepseek.com/chat/completions";
            case PROVIDER_OPENAI_RESPONSES: return "https://api.openai.com/v1/responses";
            case PROVIDER_ANTHROPIC: return "https://api.anthropic.com/v1/messages";
            case PROVIDER_GEMINI: return "https://generativelanguage.googleapis.com/v1beta";
            case PROVIDER_CUSTOM: return "https://provider.example/v1/chat/completions";
            case PROVIDER_COPILOT_BRIDGE: return "";
            default: return "";
        }
    }

    private static String defaultModel(String provider) {
        switch (provider) {
            case PROVIDER_GITHUB_MODELS: return "openai/gpt-4.1-mini";
            case PROVIDER_OPENAI_COMPAT: return "deepseek-flash";
            case PROVIDER_OPENAI_RESPONSES: return "gpt-5.4-mini";
            case PROVIDER_ANTHROPIC: return "claude-sonnet-4-5";
            case PROVIDER_GEMINI: return "gemini-2.5-flash";
            case PROVIDER_CUSTOM: return "model-name";
            case PROVIDER_COPILOT_BRIDGE: return "gpt-5.4";
            default: return "model-name";
        }
    }

    private static String normalizeProviderId(String value) {
        if (value == null) return PROVIDER_GITHUB_MODELS;
        switch (value) {
            case PROVIDER_GITHUB_MODELS:
            case PROVIDER_OPENAI_COMPAT:
            case PROVIDER_OPENAI_RESPONSES:
            case PROVIDER_ANTHROPIC:
            case PROVIDER_GEMINI:
            case PROVIDER_CUSTOM:
            case PROVIDER_COPILOT_BRIDGE: return value;
            default: return PROVIDER_CUSTOM;
        }
    }

    /** Makes a real non-executing round-trip to validate the currently entered provider profile. */
    public static String testConnection(String provider, String endpoint, String apiKey, String model) throws Exception {
        String id = normalizeProviderId(provider);
        if (endpoint == null || endpoint.trim().isEmpty()) throw new IllegalArgumentException("رابط API فارغ.");
        if (apiKey == null || apiKey.trim().isEmpty()) throw new IllegalArgumentException("لم يُدخل مفتاح لهذا المزوّد ولم يوجد مفتاح محفوظ.");
        if (model == null || model.trim().isEmpty()) throw new IllegalArgumentException("اسم النموذج فارغ.");
        String prompt = "مكتبة الصور المتاحة: []. هذا اختبار اتصال فقط ولا تُنفّذ الخطوات. " +
                "حوّل الأمر التالي إلى JSON لخطة خطوة واحدة: انتظر 250 مللي ثانية. " +
                "أعد action=wait و delay_ms=250 ضمن steps، وأعد warnings كقائمة.";
        JSONObject plan = requestPlan(id, endpoint.trim(), apiKey.trim(), model.trim(), prompt, null);
        JSONArray steps = plan.optJSONArray("steps");
        if (steps == null || steps.length() == 0) throw new IllegalStateException("وصل رد من المزوّد لكن لم يرجع خطة JSON صالحة.");
        return "نجح الاتصال بـ" + providerLabel(id) + " — النموذج " + model.trim() + " أعاد " + steps.length() + " خطوة اختبار (لم تُنفّذ).";
    }

    private static String stripJsonFences(String value) {
        String text = value == null ? "" : value.trim();
        if (text.startsWith("```")) {
            int first = text.indexOf('\n'); int last = text.lastIndexOf("```");
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

    /** Settings UI; API credentials are stored per profile and encrypted using Android Keystore. */
    public static void showSettingsDialog(Context context, boolean accessibilityOverlay, Runnable onSaved) {
        final String[] ids = { PROVIDER_GITHUB_MODELS, PROVIDER_OPENAI_COMPAT, PROVIDER_OPENAI_RESPONSES,
                PROVIDER_ANTHROPIC, PROVIDER_GEMINI, PROVIDER_CUSTOM, PROVIDER_COPILOT_BRIDGE };
        final String[] labels = {
                "GitHub Models — حصة مجانية حسب الحدود",
                "DeepSeek / OpenRouter / Groq / مزوّد متوافق",
                "OpenAI Responses API",
                "Anthropic Messages API",
                "Google Gemini API",
                "رابط مخصص متوافق مع Chat Completions",
                "GitHub Copilot عبر جسر خارجي (ليس مفتاح API عاديًا)"
        };
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(14 * context.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        TextView intro = new TextView(context);
        intro.setText("اختر المزوّد والنموذج. تُحفظ إعدادات كل مزوّد ومفتاحه بشكل منفصل ومشفّر على هذا الجهاز. GitHub Models يوفر حصة استخدام محدودة لبعض النماذج، لكنه ليس خدمة Copilot نفسها.");
        intro.setTextColor(MUTED); intro.setTextSize(12); intro.setGravity(Gravity.RIGHT);
        container.addView(intro, new LinearLayout.LayoutParams(-1, -2));

        Spinner providerSpinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(-1, -2); spinnerLp.topMargin = pad / 2;
        container.addView(providerSpinner, spinnerLp);

        TextView status = new TextView(context); status.setTextColor(MUTED); status.setTextSize(12); status.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2); statusLp.topMargin = pad / 3; container.addView(status, statusLp);

        EditText endpoint = new EditText(context); endpoint.setSingleLine(true); endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setHint("رابط API الكامل أو عنوان الجذر لـ Gemini"); styleInput(context, endpoint, pad);
        LinearLayout.LayoutParams endpointLp = new LinearLayout.LayoutParams(-1, -2); endpointLp.topMargin = pad / 2; container.addView(endpoint, endpointLp);

        EditText model = new EditText(context); model.setSingleLine(true); model.setInputType(InputType.TYPE_CLASS_TEXT);
        model.setHint("اسم النموذج كما يقدمه المزوّد"); styleInput(context, model, pad);
        LinearLayout.LayoutParams modelLp = new LinearLayout.LayoutParams(-1, -2); modelLp.topMargin = pad / 3; container.addView(model, modelLp);

        EditText key = new EditText(context); key.setSingleLine(true); key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setHint("مفتاح API أو GitHub token — اتركه فارغًا للاحتفاظ بالمحفوظ"); styleInput(context, key, pad);
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(-1, -2); keyLp.topMargin = pad / 3; container.addView(key, keyLp);

        TextView help = new TextView(context); help.setTextColor(MUTED); help.setTextSize(12); help.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(-1, -2); helpLp.topMargin = pad / 3; container.addView(help, helpLp);

        Button testConnectionButton = new Button(context);
        testConnectionButton.setText("اختبار الاتصال دون تنفيذ أي خطوة");
        testConnectionButton.setTextSize(13);
        testConnectionButton.setAllCaps(false);
        testConnectionButton.setTextColor(TEXT);
        testConnectionButton.setBackground(shape(context, Color.argb(95, 35, 226, 244), CYAN));
        LinearLayout.LayoutParams testButtonLp = new LinearLayout.LayoutParams(-1, -2);
        testButtonLp.topMargin = pad / 2;
        container.addView(testConnectionButton, testButtonLp);

        Map<String, String[]> drafts = new HashMap<>();
        final int[] currentIndex = {-1};
        final boolean[] binding = {false};
        Runnable[] stashCurrent = new Runnable[1];
        stashCurrent[0] = () -> {
            if (currentIndex[0] < 0 || currentIndex[0] >= ids.length) return;
            drafts.put(ids[currentIndex[0]], new String[]{endpoint.getText().toString(), model.getText().toString(), key.getText().toString()});
        };
        Runnable[] loadCurrent = new Runnable[1];
        loadCurrent[0] = () -> {
            int i = providerSpinner.getSelectedItemPosition(); if (i < 0 || i >= ids.length) return;
            String id = ids[i]; currentIndex[0] = i;
            String[] draft = drafts.get(id);
            endpoint.setText(draft == null ? savedOrDefault(context, ENDPOINT_PREFIX + id, defaultEndpoint(id)) : draft[0]);
            model.setText(draft == null ? savedOrDefault(context, MODEL_PREFIX + id, defaultModel(id)) : draft[1]);
            key.setText(draft == null ? "" : draft[2]);
            status.setText(BotnaKeyVault.hasKey(context, id) ? "✓ يوجد مفتاح محفوظ ومشفّر لهذا المزوّد." : "لا يوجد مفتاح محفوظ لهذا المزوّد حتى الآن.");
            if (PROVIDER_GITHUB_MODELS.equals(id)) {
                help.setText("GitHub Models: أدخل GitHub token صالحًا واسم نموذج متاحًا لحسابك. الحصة المجانية وحدود النماذج تتغير حسب شروط GitHub.");
            } else if (PROVIDER_OPENAI_COMPAT.equals(id)) {
                help.setText("مثال DeepSeek: https://api.deepseek.com/chat/completions — مثال OpenRouter: https://openrouter.ai/api/v1/chat/completions. راجع رابط المزوّد الحالي.");
            } else if (PROVIDER_CUSTOM.equals(id)) {
                help.setText("أدخل رابط /chat/completions الكامل مع المفتاح ومعرّف النموذج. يدعم هذا غالبية واجهات OpenAI-compatible، وليس كل واجهات النماذج الخاصة.");
            } else if (PROVIDER_COPILOT_BRIDGE.equals(id)) {
                help.setText("لا يمكن استخدام Copilot Free بإدخال مفتاح API عادي. هذا الخيار يتطلب خدمة جسر خارجية تستخدم تكامل GitHub Copilot الرسمي وOAuth/SDK؛ لا تستخدم نقاط API غير موثقة.");
            } else if (PROVIDER_ANTHROPIC.equals(id)) {
                help.setText("يتطلب مفتاح Anthropic API صالحًا ورصيد/حصة في حساب Anthropic. رابط Messages API قابل للتعديل.");
            } else if (PROVIDER_GEMINI.equals(id)) {
                help.setText("أدخل مفتاح Gemini API. في هذا الوضع اكتب جذر API مثل https://generativelanguage.googleapis.com/v1beta واسم النموذج دون بادئة models/.");
            } else {
                help.setText("يستخدم هذا الوضع OpenAI Responses API. مفتاح ChatGPT/Copilot لا يعادل تلقائيًا مفتاح API؛ استخدم اعتماد API رسميًا من المزوّد.");
            }
        };
        providerSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!binding[0]) { stashCurrent[0].run(); loadCurrent[0].run(); }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        String active = providerId(context); int activePosition = 0;
        for (int i = 0; i < ids.length; i++) if (ids[i].equals(active)) activePosition = i;
        binding[0] = true; providerSpinner.setSelection(activePosition); currentIndex[0] = activePosition;
        loadCurrent[0].run(); binding[0] = false;

        testConnectionButton.setOnClickListener(v -> {
            stashCurrent[0].run();
            int index = providerSpinner.getSelectedItemPosition();
            if (index < 0 || index >= ids.length) return;
            String selected = ids[index];
            String[] data = drafts.get(selected);
            if (data == null) data = new String[]{endpoint.getText().toString(), model.getText().toString(), key.getText().toString()};
            final String testEndpoint = data[0] == null ? "" : data[0].trim();
            final String testModel = data[1] == null ? "" : data[1].trim();
            String providedKey = data[2] == null ? "" : data[2].trim();
            final String testKey = providedKey.isEmpty() ? BotnaKeyVault.load(context, selected) : providedKey;
            if (testEndpoint.isEmpty() || testModel.isEmpty() || testKey.isEmpty()) {
                status.setText("أدخل رابط API والنموذج والمفتاح أو احفظ مفتاحًا لهذا المزوّد أولًا.");
                return;
            }
            testConnectionButton.setEnabled(false);
            status.setText("جارٍ اختبار الاتصال… لن تُنفّذ أي خطوة على الهاتف.");
            final String testProvider = selected;
            new Thread(() -> {
                String outcome;
                try { outcome = testConnection(testProvider, testEndpoint, testKey, testModel); }
                catch (Exception ex) {
                    String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    outcome = "فشل الاختبار: " + detail;
                }
                final String finalOutcome = outcome;
                new Handler(Looper.getMainLooper()).post(() -> {
                    status.setText(finalOutcome);
                    testConnectionButton.setEnabled(true);
                });
            }, "Botna-Provider-Test").start();
        });

        AlertDialog dialog = new AlertDialog.Builder(context).setTitle("مركز إعدادات مزوّدي الذكاء الاصطناعي")
                .setView(container).setPositiveButton("حفظ الإعدادات", null)
                .setNeutralButton("مسح مفتاح هذا المزوّد", null).setNegativeButton("إغلاق", null).create();
        try {
            if (dialog.getWindow() != null && accessibilityOverlay) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                stashCurrent[0].run();
                int index = providerSpinner.getSelectedItemPosition(); if (index < 0 || index >= ids.length) return;
                String selected = ids[index];
                String[] data = drafts.get(selected);
                if (data == null) data = new String[]{endpoint.getText().toString(), model.getText().toString(), key.getText().toString()};
                if (PROVIDER_COPILOT_BRIDGE.equals(selected) && data[0].trim().isEmpty()) {
                    Toast.makeText(context, "أدخل رابط الجسر المتوافق مع Chat Completions؛ الاتصال المباشر بـCopilot غير متاح بمفتاح عادي.", Toast.LENGTH_LONG).show(); return;
                }
                if (data[1].trim().isEmpty() || data[0].trim().isEmpty()) {
                    Toast.makeText(context, "أدخل رابط API واسم النموذج قبل الحفظ.", Toast.LENGTH_LONG).show(); return;
                }
                try {
                    saveProfileSettings(context, selected, data[2], data[1], data[0], false);
                    Toast.makeText(context, "حُفظ إعداد " + providerLabel(selected) + " ومفتاحه إن أُدخل.", Toast.LENGTH_LONG).show();
                    dialog.dismiss(); if (onSaved != null) onSaved.run();
                } catch (Exception e) { Toast.makeText(context, "تعذر حفظ الإعدادات: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                int index = providerSpinner.getSelectedItemPosition(); if (index < 0 || index >= ids.length) return;
                String selected = ids[index];
                AlertDialog confirm = new AlertDialog.Builder(context).setTitle("مسح مفتاح المزوّد")
                        .setMessage("سيُحذف المفتاح المشفّر المحفوظ لهذا المزوّد فقط، ولن تتأثر مفاتيح المزوّدين الآخرين. متابعة؟")
                        .setPositiveButton("مسح المفتاح", (d, w) -> {
                            try {
                                BotnaKeyVault.save(context, selected, "");
                                status.setText("تم حذف مفتاح هذا المزوّد."); key.setText("");
                                Toast.makeText(context, "تم مسح المفتاح المحفوظ لهذا المزوّد.", Toast.LENGTH_SHORT).show();
                                if (onSaved != null) onSaved.run();
                            } catch (Exception e) { Toast.makeText(context, "تعذر مسح المفتاح.", Toast.LENGTH_LONG).show(); }
                        }).setNegativeButton("إلغاء", null).create();
                try { if (confirm.getWindow() != null && accessibilityOverlay) confirm.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); confirm.show(); }
                catch (Exception e) { Toast.makeText(context, "تعذر فتح تأكيد المسح.", Toast.LENGTH_LONG).show(); }
            });
        } catch (Exception e) { Toast.makeText(context, "تعذر فتح إعدادات الذكاء الاصطناعي.", Toast.LENGTH_LONG).show(); }
    }

    private static String savedOrDefault(Context context, String key, String fallback) {
        String value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static void styleInput(Context context, EditText input, int pad) {
        input.setPadding(pad, pad / 2, pad, pad / 2); input.setTextColor(TEXT); input.setHintTextColor(MUTED); input.setTextSize(13);
        input.setBackground(shape(context, CARD, CYAN));
    }

    private static GradientDrawable shape(Context context, int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill); shape.setCornerRadius(16 * context.getResources().getDisplayMetrics().density);
        shape.setStroke(Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)), stroke);
        return shape;
    }
}
