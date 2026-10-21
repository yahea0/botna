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
import android.view.inputmethod.InputMethodManager;
import android.graphics.Rect;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ScrollView;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;

public final class BotnaAICompiler {
    public static final String PREFS = "botna_settings";
    private static final String PROVIDER_KEY = "ai_provider_id_v2";
    private static final String MODEL_PREFIX = "ai_model_v2_";
    private static final String ENDPOINT_PREFIX = "ai_endpoint_v2_";
    private static final String KEY_COOLDOWN_PREFIX = "ai_key_cooldown_until_v1_";

    public static final String PROVIDER_GITHUB_MODELS = "github_models";
    public static final String PROVIDER_OPENAI_COMPAT = "openai_compatible";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_OPENROUTER = "openrouter";
    public static final String PROVIDER_GROQ = "groq";
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
            "افهم صياغة المستخدم باللهجة الفلسطينية وحوّلها إلى خطوات مرتبة لا إلى إعادة لنصه. ميّز سؤال الشرح عن أمر التنفيذ، وافهم التسلسل المركب مثل فتح يوتيوب ثم البحث باسم أغنية وتشغيل النتيجة. عند قول «إذا نجحت انتظر ثانيتين ثم افتح» استخدم if_success ثم wait ثم الإجراء المرتبط ثم end_if. وعند قول «إذا فشلت حاول N مرات» ضع retry_on_failure_count وretry_on_failure_delay_ms على الإجراء المقصود. لا تخمّن موقع أيقونة موصوفة بالشكل دون لقطة شاشة موثوقة أو وصف وصول. " +
            "أنت محرك ترجمة أوامر احترافي لتطبيق أتمتة أندرويد اسمه Botna. المستخدم يتحدث العربية بلهجة فلسطينية، وقد يستخدم العامية الفلسطينية أو أخطاء إملائية أو تعبيرات غير مرتبة مثل افتحلي، روح، بتلاقي، اعمل، سوي، جيبلي، افحص، ابحثلي عبر الإنترنت. استنبط قصده العملي دون تغيير الهدف، وطبّع اللهجة إلى أوامر دقيقة. ميّز بين طلب تنفيذ (انقر/اكتب/افتح) وطلب معلومات أو نتيجة (اقرأ الشاشة/نتيجة البحث/محتوى الرابط/اختبار السرعة): الطلب المعلوماتي يجب أن ينتهي بإجابة فعلية مستندة إلى النص المقروء أو القياس، لا بمجرد رسالة «تم». " +
            "أخرج JSON صالحًا فقط بالشكل {\"steps\":[{\"action\":\"...\",\"image_index\":1,\"app_name\":\"\",\"text\":\"\",\"delay_ms\":0,\"duration_ms\":800,\"x_percent\":50,\"y_percent\":50,\"x1_percent\":50,\"y1_percent\":75,\"x2_percent\":50,\"y2_percent\":25,\"direction\":\"up\",\"count\":1,\"url\":\"https://...\",\"message\":\"...\",\"attempts\":3,\"max_attempts\":120,\"interval_ms\":5000,\"timeout_ms\":4000,\"retry_on_failure_count\":1,\"retry_on_failure_delay_ms\":700,\"sound\":true}],\"warnings\":[\"...\"]}. لا تخرج Java أو shell أو JavaScript أو SQL أو كودًا عامًا. كل عنصر خطوة واحدة فقط وبترتيب التنفيذ. أعِد أيضًا before_delay_ms وafter_delay_ms عندما يحتاج تسلسل الواجهة إلى مهلة، ولا تجعلها صفرًا افتراضيًا إذا كان فتح التطبيق أو انتقال الشاشة يتطلب انتظارًا. " +
            "الأفعال الوحيدة المسموحة: launch_app, open_url, find_image, tap_image, click_text, type_text, clear_text, press_enter, wait, wait_for_text, check_text, wait_for_input, tap_at, long_press_at, swipe, scroll, read_screen_text, back, home, open_notifications, open_quick_settings, open_recent_apps, dismiss_notifications, close_recent_apps, check_internet, wait_for_internet, notify, if_success, if_fail, else, end_if, repeat_start, repeat_end. " +
            "استخدم launch_app لفتح تطبيق مثبت؛ اسم التطبيق يوضع في app_name. استخدم open_url لرابط HTTP/HTTPS فقط؛ لصفحة الراوتر استخدم الرابط الذي ذكره المستخدم ولا تخمّن بيانات الدخول أو زر إعادة التشغيل. لا تدّعِ أن تطبيقًا أو زرًا موجود إذا لم يتأكد ذلك. استخدم find_image للعثور على قالب دون النقر، وtap_image للعثور والنقر. طابق الاسم الذي يذكره المستخدم مع الاسم المحفوظ في مكتبة الصور حرفيًا قدر الإمكان، حتى لو كان الاسم لهجة عامية أو اسمًا اختاره المستخدم، ثم استخدم رقم image_index المقابل لذلك الاسم فقط. إذا طلب الضغط أو الفتح فاستخدم tap_image، وإذا طلب الانتظار أو التأكد من الظهور دون الضغط فاستخدم find_image. image_index هو رقم القالب من مكتبة الصور المعطاة ولا يجوز التخمين عند تشابه الأسماء. استخدم click_text فقط للنصوص التي يمكن الوصول إليها عبر Accessibility؛ بعض الألعاب مثل Unity ترسم النصوص داخل صور، ففيها فضّل قالب الصورة أو إحداثياتها. إذا وصف المستخدم العنصر بالشكل أو اللون أو موقعه النسبي، قارن الوصف بلقطة الشاشة الحالية واختر tap_at بإحداثيات نسبية فقط عندما يكون الهدف واضحًا؛ لا تخمّن العنصر إذا وُجدت عدة عناصر مشابهة. استخدم read_screen_text لقراءة النص الفعلي عند طلب نتيجة أو شرح. " +
            "استخدم wait_for_text لانتظار ظهور نص محدد في شجرة إمكانية الوصول وwait_for_input لانتظار ظهور حقل قابل للتحرير؛ لا تستخدم انتظارًا ثابتًا إذا طلب المستخدم صراحةً الانتظار حتى يظهر العنصر. استخدم type_text لكتابة النص الحرفي المحدد في حقل إدخال مركّز؛ لا تضف أو تغيّر النص بين علامتي الاقتباس. يستخدم التطبيق ACTION_SET_TEXT ثم اللصق الاحتياطي إذا احتاج الأمر، وليس محاكاة كل ضغطة مفتاح فعلية. استخدم press_enter لإرسال فعل Enter/بحث في الحقل إن كان التطبيق يدعمه، وإلا انقر زر البحث الظاهر. استخدم tap_at وlong_press_at بإحداثيات نسبية من 0 إلى 100. استخدم swipe عندما يحدد المستخدم نقطة بداية ونهاية، وأرسل x1_percent وy1_percent وx2_percent وy2_percent وduration_ms؛ أما scroll فهو تمرير قياسي حسب الاتجاه. استخدم check_text للتحقق الفوري من ظهور النص (محاولة واحدة)، وwait_for_text للانتظار حتى يظهر النص. استخدم clear_text لمسح حقل البحث/الإدخال المركّز. استخدم open_notifications لسحب لوحة الإشعارات، وopen_quick_settings لفتح الإعدادات السريعة، وopen_recent_apps لعرض التطبيقات الأخيرة، وdismiss_notifications لإغلاق لوحة الإشعارات إذا دعم إصدار أندرويد ذلك. wait.delay_ms بين 0 و60000، وlong_press_at.duration_ms بين 300 و5000، وrepeat_start.count عدد صحيح موجب دون سقف صغير مصطنع؛ إذا كان العدد كبيرًا جدًا نبّه المستخدم إلى طول المهمة بدل تقليص العدد بصمت. " +
            "check_internet يرسل طلب HTTP(S) صغيرًا إلى اختبار اتصال ويراجع الاستجابة؛ اكتب attempts كعدد صحيح موجب دون حد صغير مصطنع، مع interval_ms وtimeout_ms المناسبين. wait_for_internet يعيد الفحص حتى يعود الاتصال أو تنفد max_attempts؛ استخدم فاصلًا واقعيًا مثل 5000 مللي ثانية. بعد النجاح، استخدم if_success ثم notify لإظهار رسالة وتشغيل تنبيه صوتي. بعد الفشل النهائي استخدم if_fail. لا تدّعِ أن ظهور إشارة Wi-Fi يعني وجود إنترنت. " +
            "مثال: 'إذا لقيت الصورة 4 استنى ثانيتين وبعدين ارجع' => find_image للصورة 4، ثم if_success، ثم wait 2000، ثم back، ثم end_if. 'إذا ما لقيتها اعمل كذا وإلا...' يجب أن تعكس الشرط بدقة. مثال الشبكة: check_internet attempts=3 interval_ms=5000 ثم if_fail ثم open_url بعنوان صفحة الراوتر الذي أعطاه المستخدم، ولا تختلق خطوات إدارة الراوتر أو كلمة المرور؛ اتبع فقط الأزرار المتحقق منها في الشاشة. مثال انتظار العودة: wait_for_internet max_attempts=120 interval_ms=5000 ثم if_success ثم notify برسالة النجاح. اجعل كل كتلة شرط وتكرار متداخلة متوازنة، واستخدم else/end_if/repeat_end صراحة. لا تبتكر صورًا أو أسماء تطبيقات أو أفعالًا غير مدعومة. " +
            "إذا كان وصف المستخدم ملتبسًا، اختر فقط ما تدعمه العبارة بثقة وأضف تحذيرًا بالعربية يذكر الجزء غير المؤكد؛ لا تخمّن عملية خطرة. لا تنفّذ عمليات شراء أو مدفوعات أو حذف بيانات أو إرسال رسائل خارجية تلقائيًا؛ اطلب تأكيدًا عبر تحذير بدل إنشاء تنفيذ غير قابل للتراجع. " +
            "للتسلسلات الطويلة احتفظ بكل خطوة وبترتيبها؛ لا تختصر أو تُسقط الفعل بين خطوات، ولا تضع خطوة بعد if_success/if_fail خارج كتلتها إلا عندما يصرّح المستخدم بذلك. مثال بحث فيديو: launch_app للتطبيق المذكور، wait_for_input، click_text على حقل البحث إن لم يكن مركّزًا، type_text للنص الحرفي، press_enter أو click_text لزر البحث، ثم انتظر النتائج، واختر النتيجة/القناة من عناصر الشاشة أو الصور المتاحة، وافتح قائمة المشاركة، واختر تطبيق المشاركة المذكور إذا ظهر فيها. لا تدّعِ أن تنزيلًا نجح أو أن تطبيقًا مثبت ما لم يتحقق ذلك. " +
            "في سيناريو الراوتر والشبكة، check_internet يعني طلب HTTP(S) فعليًا مع قراءة الاستجابة؛ استخدم if_fail بعد فشل كل المحاولات. لا تختلق عنوان الراوتر أو بيانات الدخول. إذا أعطى المستخدم عنوان صفحة الراوتر وطلب إعادة التشغيل صراحة، افتح العنوان ثم نفّذ فقط أسماء الأزرار/النصوص التي ظهرت في لقطة مؤكدة أو حدّدها المستخدم (مثل إعادة التشغيل والتأكيد)، وانتظر نتيجة الواجهة؛ إذا تعذر التعرف عليها، أضف تحذيرًا بدل النقر العشوائي. بعد الإجراء استخدم wait_for_internet ثم if_success وnotify برسالة واضحة، أو if_fail لإظهار فشل المهلة. أندرويد لا يتيح عادةً لتطبيق عادي تشغيل مفتاح Wi-Fi/راوتر مباشرةً بلا صلاحيات خاصة؛ لا تعد بذلك. " +
            "أدخل النص عن طريق type_text بحروفه كما ورد، بما في ذلك العربية وعلامات الاقتباس الداخلية؛ فتح حقل الكتابة يتم عبر wait_for_input ثم click_text/ tap_at إذا احتاج التطبيق ذلك. لا تعتبر ACTION_SET_TEXT نفسه محاكاة لكل ضغطة مفتاح؛ التطبيق يستخدم إجراء إمكانية الوصول واللصق الاحتياطي عندما يسمح التطبيق الهدف. " +
            "إذا أُرفقت لقطة شاشة، استخدمها لتحديد السياق الظاهر فقط، ولا تدّعِ معرفة ما هو خارج الصورة. تعامل مع الجمل الطويلة كأهداف فرعية متعددة؛ استخرج الأفعال والشروط والتوقيت والنتيجة المطلوبة، واحتفظ بترتيبها ولا تختصر الخطوات التابعة لأفعال مثل ثم، بعدين، وبعدين، وكمان، وبس، وخليك. ميّز بين استمرار المراقبة وبين تنفيذ فحص واحد، ولا تحول طلب الاستمرار إلى تكرار غير محدود ما لم توجد آلية مدعومة وحدّ توقف واضح. خطتك مقترح أولي، وسيتم فحص بنية الشروط والتكرار والأنواع المدعومة داخل التطبيق قبل التشغيل. " + BotnaArabicDialect.promptGuidance();

    private BotnaAICompiler() { }

    public static String providerId(Context context) {
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PROVIDER_KEY, "");
        if (saved != null && !saved.trim().isEmpty()) {
            if (PROVIDER_GITHUB_MODELS.equals(saved.trim())) return PROVIDER_GEMINI;
            return saved.trim();
        }

        if (!BotnaKeyVault.load(context).isEmpty()) return PROVIDER_OPENAI_RESPONSES;

        return PROVIDER_GEMINI;
    }

    public static String providerLabel(String id) {
        if (PROVIDER_GITHUB_MODELS.equals(id)) return "GitHub Models — حصة مجانية حسب الحدود";
        if (PROVIDER_OPENAI_COMPAT.equals(id)) return "مزود متوافق قديم/مخصص";
        if (PROVIDER_DEEPSEEK.equals(id)) return "DeepSeek API";
        if (PROVIDER_OPENROUTER.equals(id)) return "OpenRouter API";
        if (PROVIDER_GROQ.equals(id)) return "Groq API";
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
        return BotnaKeyVault.count(context, id) > 0 && !endpoint(context).trim().isEmpty() && !model(context).trim().isEmpty();
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
        else {
            List<String> additions = splitKeyLines(key);
            if (!additions.isEmpty()) {
                ArrayList<String> merged = BotnaKeyVault.loadAll(context, provider);
                for (String candidate : additions) if (!merged.contains(candidate) && merged.size() < 20) merged.add(candidate);
                BotnaKeyVault.saveAll(context, provider, merged);
            }
        }
        boolean ok = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PROVIDER_KEY, provider)
                .putString(MODEL_PREFIX + provider, chosenModel)
                .putString(ENDPOINT_PREFIX + provider, chosenEndpoint)
                .commit();
        if (!ok) throw new IllegalStateException("تعذر حفظ إعدادات مزود الذكاء الاصطناعي");
    }

    public static JSONObject compile(Context context, String instructions, JSONArray library) throws Exception {
        return compile(context, instructions, library, null);
    }

    public static JSONObject compile(Context context, String instructions, JSONArray library, Bitmap screenContext) throws Exception {
        return compile(context, instructions, library, screenContext, "");
    }

    public static JSONObject compile(Context context, String instructions, JSONArray library, Bitmap screenContext, String appContext) throws Exception {
        String provider = providerId(context);
        ArrayList<String> keys = BotnaKeyVault.loadAll(context, provider);
        if (keys.isEmpty()) throw new IllegalStateException("لا يوجد مفتاح محفوظ لمزوّد " + providerLabel(provider) + ". افتح إعدادات الذكاء الاصطناعي وأضف مفتاحًا واحدًا على الأقل.");
        String model = model(context).trim();
        String endpoint = endpoint(context).trim();
        if (model.isEmpty() || endpoint.isEmpty()) throw new IllegalStateException("أدخل رابط API واسم النموذج لهذا المزوّد أولًا.");

        String userText = "مكتبة الصور المتاحة، أرقامها وأسماؤها (لا تختَر رقمًا خارجها): " + libraryForPrompt(library)
                + "\n\nحلّل الأمر الفلسطيني التالي كاملًا قبل إخراج JSON. استخرج كل فعل وشرط ومدة وانتظار ونتيجة مطلوبة، وحافظ على ترتيب العطف والتتابع. لا تسقط التعليمات المتأخرة ولا تعتبر طلب النتيجة مجرد تنفيذ. إذا تعذر تنفيذ جزء لغياب عنوان أو معلومة شاشة أو صلاحية، احتفظ بالأجزاء الآمنة وأضف تحذيرًا محددًا ولا تخترع قيمة.\nالأمر الأصلي بالحرف:\n" + (instructions == null ? "" : instructions.trim());
        if (appContext != null && !appContext.trim().isEmpty()) userText += "\n\nسياق التطبيق وحالته الفعلية للاستخدام في التخطيط فقط:\n" + appContext.trim();
        if (screenContext != null) {
            userText += "\n\nأبعاد لقطة الشاشة الأصلية: " + screenContext.getWidth() + "×" + screenContext.getHeight()
                    + " بكسل. قد تُرسل الصورة مصغرة مع الحفاظ على نسبة الأبعاد لتسريع النقل وتقليل حجم البيانات؛ لا تستنتج إحداثيات بكسلية من حجم الصورة المرفقة. أعد مواقع العناصر كنسب مئوية من الشاشة الأصلية الكاملة (0-100)، مع مراعاة شريط الحالة وشريط التنقل.";
        }
        String encodedImage = screenContext == null ? null : bitmapToJpegBase64(screenContext);
        int startIndex = BotnaKeyVault.activeIndex(context, provider, keys.size());
        Exception lastFailure = null;
        int attemptedKeys = 0;
        long earliestCooldown = Long.MAX_VALUE;
        for (int offset = 0; offset < keys.size(); offset++) {
            int keyIndex = (startIndex + offset) % keys.size();
            String apiKey = keys.get(keyIndex);
            long cooldownUntil = keyCooldownUntil(context, provider, apiKey);
            if (cooldownUntil > System.currentTimeMillis()) {
                earliestCooldown = Math.min(earliestCooldown, cooldownUntil);
                continue;
            }
            attemptedKeys++;
            try {
                JSONObject plan;
                try {
                    plan = requestPlan(provider, endpoint, apiKey, model, userText, encodedImage);
                } catch (Exception first) {
                    if (encodedImage == null || !looksLikeVisionCompatibilityError(first)) throw first;
                    plan = requestPlan(provider, endpoint, apiKey, model, userText, null);
                    JSONArray warnings = plan.optJSONArray("warnings");
                    if (warnings == null) { warnings = new JSONArray(); plan.put("warnings", warnings); }
                    warnings.put("المزوّد أو النموذج لم يقبل صورة الشاشة؛ تُرجمت التعليمات دون سياق بصري مباشر. اختر نموذجًا يدعم الصور لتحليل موثوق.");
                }
                BotnaKeyVault.noteSuccessfulKey(context, provider, apiKey);
                return plan;
            } catch (Exception ex) {
                lastFailure = ex;
                if (!shouldRotateKey(ex)) throw ex;
                long cooldownMs = keyCooldownDurationMillis(ex);
                if (cooldownMs > 0) {
                    long until = System.currentTimeMillis() + cooldownMs;
                    setKeyCooldown(context, provider, apiKey, until);
                    earliestCooldown = Math.min(earliestCooldown, until);
                }
                BotnaKeyVault.setActiveIndex(context, provider, (keyIndex + 1) % keys.size());
            }
        }
        boolean allKeysCooling = !keys.isEmpty();
        for (String candidateKey : keys) {
            if (keyCooldownUntil(context, provider, candidateKey) <= System.currentTimeMillis()) { allKeysCooling = false; break; }
        }
        if ((attemptedKeys == 0 || allKeysCooling) && earliestCooldown != Long.MAX_VALUE) {
            String retryAt = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new java.util.Date(earliestCooldown));
            String lastDetail = lastFailure == null || lastFailure.getMessage() == null ? "" : " آخر رد من المزوّد: " + lastFailure.getMessage();
            throw new IllegalStateException("لا يوجد مفتاح لهذا المزوّد متاح الآن. جُرّبت المفاتيح المحفوظة وأُوقفت إعادة المحاولة مؤقتًا لتجنّب تكرار أخطاء الحصة. أقرب موعد محفوظ لإعادة المحاولة: " + retryAt + ". إذا كانت مفاتيح Gemini كلها من المشروع نفسه فستشارك الحصة ذاتها؛ جرّب مشروعًا/مزوّدًا لديه حصة متاحة أو انتظر إعادة الضبط." + lastDetail);
        }
        if (lastFailure != null) throw lastFailure;
        throw new IllegalStateException("لم ينجح أي مفتاح محفوظ لهذا المزوّد.");
    }

    public static JSONObject compileSmartNext(Context context, String goal, JSONArray history, Bitmap screenContext) throws Exception {
        return compileSmartNext(context, goal, history, screenContext, "");
    }

    public static JSONObject compileSmartNext(Context context, String goal, JSONArray history, Bitmap screenContext, String appContext) throws Exception {
        StringBuilder prompt = new StringBuilder();
        prompt.append("وضع وكيل ذكي تفاعلي للمهمة التالية. اقرأ لقطة الشاشة الحالية، وقارنها بسجل الأفعال، ثم قرر خطوة واحدة فقط الآن.\n")
                .append("المهمة الأصلية بالحرف: ").append(goal == null ? "" : goal.trim()).append("\n")
                .append("سجل الأفعال السابقة بصيغة JSON: ").append(history == null ? "[]" : history.toString()).append("\n\n")
                .append("أعد JSON بالشكل {\"task_done\":false,\"steps\":[{...فعل مدعوم واحد فقط...}],\"warnings\":[],\"reason\":\"سبب القرار المختصر بالعربية\"}. ")
                .append("إذا اكتملت المهمة تمامًا أعد task_done=true وsteps=[] . إذا لم تكتمل أعد خطوة واحدة فقط من قائمة الأفعال المدعومة. لا تُعد خطة مستقبلية ولا أكثر من فعل في الرد. ")
                .append("اعتمد على لقطة الشاشة الحالية لتحديد حالة التطبيق وعناصره، ولا تخترع عناصر أو نصوصًا غير ظاهرة. استنتج موضع المستخدم الحالي من واجهة الشاشة فقط. بعد كل فعل ستصل لقطة جديدة؛ قارنها بلقطة/نتيجة الخطوة السابقة. ")
                .append("اعتبر سجل الأفعال ذاكرة عمل للمهمة: إذا فشل فعل أو بقيت الشاشة كما هي، لا تكرر الفعل نفسه بالطريقة نفسها؛ حدّد سببًا محتملًا من النتيجة وجرّب إجراءً بديلًا تدعمه الشاشة، مثل فتح الحقل أولًا أو انتظار التحميل أو استخدام نصّ ظاهر، ولا تدّعِ النجاح دون علامة مرئية. إذا تغيّرت الشاشة، حدّث خطتك وفق الحالة الجديدة. لا تعتبر ذلك تدريبًا دائمًا للنموذج؛ إنه تصحيح اعتمادًا على سجل المهمة الحالية. ")
                .append("لا تنفذ شراءً أو دفعًا أو حذف بيانات أو إرسال رسالة خارجية دون تأكيد صريح. إذا تعذر الاستنتاج بأمان فأعد steps=[] وtask_done=false مع تحذير يشرح ما يلزم توضيحه. ")
                .append("إذا كان الهدف سؤالًا معلوماتيًا أو طلب نتيجة مثل اختبار سرعة التنزيل أو معرفة محتوى صفحة، فلا تكتفِ بعبارة عامة: اقرأ النصوص الظاهرة أو استخدم الإجراء المدعوم، وعند الاكتمال اكتب في reason خلاصة النتيجة الفعلية والنصوص المهمة بالعربية. لا تختلق قياسات أو نتائج غير ظاهرة. ")
                .append("إذا طلب المستخدم نتيجة أو شرحًا لا مجرد تنفيذ (مثل: اختبر سرعة الإنترنت وأخبرني بالنتيجة، ما محتوى الرابط، ما نتيجة البحث، ماذا ظهر؟)، تعامل معها كمهمة لها جواب: افتح الرابط/التطبيق المذكور، انتظر التحميل، اقرأ النصوص الظاهرة فعليًا، كرر التقاط الشاشة والإجراءات المدعومة عند الحاجة، ولا تعلن task_done=true إلا عندما تملك خلاصة فعلية. ضع الجواب المقروء والأرقام التي ظهرت في reason النهائي؛ إذا تعذر الوصول للمحتوى، سجّل ما منع الوصول بدل اختلاق الإجابة. ")
                .append("المطلوب تحقيقه: ").append(goal == null ? "" : goal.trim());
        JSONObject plan = compile(context, prompt.toString(), new JSONArray(), screenContext, appContext);
        JSONArray steps = plan.optJSONArray("steps");
        if (steps != null && steps.length() > 1) {
            JSONObject first = steps.optJSONObject(0);
            JSONArray one = new JSONArray();
            if (first != null) one.put(first);
            plan.put("steps", one);
            JSONArray warnings = plan.optJSONArray("warnings");
            if (warnings == null) { warnings = new JSONArray(); plan.put("warnings", warnings); }
            warnings.put("تم الاقتصار على الإجراء الأول فقط لأن الوكيل يراجع لقطة شاشة جديدة بعد كل إجراء.");
        }
        return plan;
    }

    private static List<String> splitKeyLines(String text) {
        ArrayList<String> result = new ArrayList<>();
        if (text == null) return result;
        for (String line : text.split("\\r?\\n")) {
            String value = line == null ? "" : line.trim();
            if (value.isEmpty() || value.matches("[-_—─]{3,}") || result.contains(value)) continue;
            if (result.size() >= 20) break;
            result.add(value);
        }
        return result;
    }

    private static boolean shouldRotateKey(Exception error) {
        String message = String.valueOf(error == null ? "" : error.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("http 429") || message.contains("http 401") || message.contains("http 403")
                || message.contains("quota") || message.contains("rate limit") || message.contains("resource_exhausted")
                || message.contains("insufficient_quota") || message.contains("billing") || message.contains("exceeded your")
                || message.contains("invalid api key") || message.contains("unauthorized") || message.contains("too many requests");
    }

    public static boolean isTransientRateLimitFailure(String message) {
        String lower = String.valueOf(message == null ? "" : message).toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) return false;
        if (lower.contains("quota exceeded for metric") || lower.contains("free_tier_requests")
                || lower.contains("current quota") || lower.contains("daily quota") || lower.contains("daily limit")
                || lower.contains("insufficient_quota") || lower.contains("billing") || lower.contains("payment required")
                || lower.contains("exceeded your current quota") || lower.contains("invalid api key")
                || lower.contains("unauthorized") || lower.contains("http 401") || lower.contains("http 403")) return false;
        Matcher retry = Pattern.compile("retry\\s+in\\s+(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+(?:\\.\\d+)?)s)?", Pattern.CASE_INSENSITIVE).matcher(lower);
        if (retry.find()) {
            long seconds = parseLong(retry.group(1)) * 3600L + parseLong(retry.group(2)) * 60L + (long) parseDouble(retry.group(3));
            return seconds > 0 && seconds <= 90;
        }
        return lower.contains("rate limit") || lower.contains("rate_limit_exceeded") || lower.contains("too many requests")
                || lower.contains("requests per minute") || lower.contains("per minute") || lower.contains("http 429")
                || lower.contains("http 503") || lower.contains("http 408") || lower.contains("temporarily unavailable")
                || lower.contains("timed out") || lower.contains("timeout");
    }

    public static long retryDelayMillis(String message, int attempt) {
        String lower = String.valueOf(message == null ? "" : message).toLowerCase(Locale.ROOT);
        Matcher retry = Pattern.compile("retry\\s+in\\s+(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+(?:\\.\\d+)?)s)?", Pattern.CASE_INSENSITIVE).matcher(lower);
        if (retry.find()) {
            long hours = parseLong(retry.group(1));
            long minutes = parseLong(retry.group(2));
            double seconds = parseDouble(retry.group(3));
            double exact = (hours * 3600d + minutes * 60d + seconds) * 1000d;
            if (exact <= 0d || exact > 90_000d) return -1L;
            return (long) exact + 500L + (System.currentTimeMillis() % 400L);
        }
        int exponent = Math.max(0, Math.min(5, attempt - 1));
        long base = Math.min(30_000L, 1_200L * (1L << exponent));
        return base + 250L + (System.currentTimeMillis() % 500L);
    }

    private static long keyCooldownDurationMillis(Exception error) {
        String message = String.valueOf(error == null ? "" : error.getMessage());
        String lower = message.toLowerCase(Locale.ROOT);
        boolean rateLimited = lower.contains("http 429") || lower.contains("quota") || lower.contains("rate limit")
                || lower.contains("resource_exhausted") || lower.contains("insufficient_quota")
                || lower.contains("exceeded your") || lower.contains("too many requests");
        if (!rateLimited) return 0L;
        Matcher matcher = Pattern.compile("retry\\s+in\\s+(?:(\\d+)h)?\\s*(?:(\\d+)m)?\\s*(?:(\\d+(?:\\.\\d+)?)s)?", Pattern.CASE_INSENSITIVE).matcher(message);
        if (matcher.find()) {
            long hours = parseLong(matcher.group(1));
            long minutes = parseLong(matcher.group(2));
            double seconds = parseDouble(matcher.group(3));
            long ms = (hours * 3600L + minutes * 60L) * 1000L + (long) (seconds * 1000.0d);
            return Math.max(1000L, Math.min(24L * 60L * 60L * 1000L, ms));
        }
        return 60_000L;
    }

    private static long parseLong(String value) { try { return value == null || value.isEmpty() ? 0L : Long.parseLong(value); } catch (Exception ignored) { return 0L; } }
    private static double parseDouble(String value) { try { return value == null || value.isEmpty() ? 0d : Double.parseDouble(value); } catch (Exception ignored) { return 0d; } }

    private static String keyCooldownPref(Context context, String provider, String key) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((key == null ? "" : key).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 8; i++) hex.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
        return KEY_COOLDOWN_PREFIX + provider + "_" + hex;
    }

    private static long keyCooldownUntil(Context context, String provider, String key) {
        try { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(keyCooldownPref(context, provider, key), 0L); }
        catch (Exception ignored) { return 0L; }
    }

    private static void setKeyCooldown(Context context, String provider, String key, long until) {
        try { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(keyCooldownPref(context, provider, key), until).apply(); }
        catch (Exception ignored) { }
    }

    private static JSONObject requestPlan(String provider, String endpoint, String apiKey, String model, String userText, String imageBase64) throws Exception {
        if (PROVIDER_ANTHROPIC.equals(provider)) return requestAnthropic(endpoint, apiKey, model, userText, imageBase64);
        if (PROVIDER_GEMINI.equals(provider)) return requestGemini(endpoint, apiKey, model, userText, imageBase64);
        if (PROVIDER_OPENAI_RESPONSES.equals(provider)) return requestResponses(endpoint, apiKey, model, userText, imageBase64);
        if (PROVIDER_GITHUB_MODELS.equals(provider) || PROVIDER_OPENAI_COMPAT.equals(provider)
                || PROVIDER_DEEPSEEK.equals(provider) || PROVIDER_OPENROUTER.equals(provider) || PROVIDER_GROQ.equals(provider)
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
        } else if (PROVIDER_OPENROUTER.equals(provider)) {
            headers.put("HTTP-Referer", "https://github.com/");
            headers.put("X-OpenRouter-Title", "Botna Android Automation");
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

        if (maxSide > 1440) {
            float ratio = 1440f / maxSide;
            scaled = Bitmap.createScaledBitmap(source, Math.max(1, Math.round(source.getWidth() * ratio)), Math.max(1, Math.round(source.getHeight() * ratio)), true);
            copied = scaled != source;
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!scaled.compress(Bitmap.CompressFormat.JPEG, 64, out)) throw new IllegalStateException("تعذر تجهيز لقطة الشاشة للإرسال");
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
            case PROVIDER_DEEPSEEK: return "https://api.deepseek.com/chat/completions";
            case PROVIDER_OPENROUTER: return "https://openrouter.ai/api/v1/chat/completions";
            case PROVIDER_GROQ: return "https://api.groq.com/openai/v1/chat/completions";
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
            case PROVIDER_DEEPSEEK: return "deepseek-flash";
            case PROVIDER_OPENROUTER: return "openai/gpt-4.1-mini";
            case PROVIDER_GROQ: return "openai/gpt-oss-120b";
            case PROVIDER_OPENAI_RESPONSES: return "gpt-5.4-mini";
            case PROVIDER_ANTHROPIC: return "claude-sonnet-4-5";
            case PROVIDER_GEMINI: return "gemini-3.8-flash";
            case PROVIDER_CUSTOM: return "model-name";
            case PROVIDER_COPILOT_BRIDGE: return "gpt-5.4";
            default: return "model-name";
        }
    }

    private static String normalizeProviderId(String value) {
        if (value == null) return PROVIDER_GEMINI;
        switch (value) {
            case PROVIDER_GITHUB_MODELS:
            case PROVIDER_OPENAI_COMPAT:
            case PROVIDER_DEEPSEEK:
            case PROVIDER_OPENROUTER:
            case PROVIDER_GROQ:
            case PROVIDER_OPENAI_RESPONSES:
            case PROVIDER_ANTHROPIC:
            case PROVIDER_GEMINI:
            case PROVIDER_CUSTOM:
            case PROVIDER_COPILOT_BRIDGE: return value;
            default: return PROVIDER_CUSTOM;
        }
    }

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

    public static void showSettingsDialog(Context context, boolean accessibilityOverlay, Runnable onSaved) {
        final String[] ids = { PROVIDER_DEEPSEEK, PROVIDER_OPENROUTER, PROVIDER_GROQ,
                PROVIDER_OPENAI_RESPONSES, PROVIDER_ANTHROPIC, PROVIDER_GEMINI,
                PROVIDER_CUSTOM, PROVIDER_OPENAI_COMPAT, PROVIDER_COPILOT_BRIDGE };
        final String[] labels = {
                BotnaI18n.translate(context, "DeepSeek API — مستقل"),
                BotnaI18n.translate(context, "OpenRouter API — مستقل"),
                BotnaI18n.translate(context, "Groq API — مستقل"),
                "OpenAI Responses API",
                "Anthropic Messages API",
                "Google Gemini API",
                BotnaI18n.translate(context, "رابط مخصص متوافق مع Chat Completions"),
                BotnaI18n.translate(context, "مزود متوافق قديم/مخصص"),
                BotnaI18n.translate(context, "GitHub Copilot عبر جسر خارجي (ليس مفتاح API عاديًا)")
        };
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(14 * context.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        BotnaI18n.applyDirection(context, container);
        TextView intro = new TextView(context);
        intro.setText(BotnaI18n.translate(context, "اختر مزودًا واحدًا مستقلًا؛ لكل مزود رابط ونموذج ومفتاح منفصل. زر «روابط» يعرض العناوين المعروفة، وزر «النماذج» يجلب المعرفات من API عند توفر اتصال ومفتاح صالح. أسماء النماذج وتوفرها يتغيران حسب حساب المزود."));
        intro.setTextColor(MUTED); intro.setTextSize(12); intro.setGravity(BotnaI18n.isEnglish(context) ? Gravity.LEFT : Gravity.RIGHT);
        container.addView(intro, new LinearLayout.LayoutParams(-1, -2));

        Spinner providerSpinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        providerSpinner.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(-1, -2); spinnerLp.topMargin = pad / 2;
        container.addView(providerSpinner, spinnerLp);

        TextView status = new TextView(context); status.setTextColor(MUTED); status.setTextSize(12); status.setGravity(BotnaI18n.isEnglish(context) ? Gravity.LEFT : Gravity.RIGHT); BotnaI18n.applyDirection(context, status);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2); statusLp.topMargin = pad / 3; container.addView(status, statusLp);

        EditText endpoint = new EditText(context); endpoint.setSingleLine(true); endpoint.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpoint.setHint(BotnaI18n.translate(context, "رابط API الكامل أو عنوان الجذر لـ Gemini")); styleInput(context, endpoint, pad);
        Button endpointPicker = smallGlassButton(context, "🔗 روابط");
        LinearLayout endpointRow = new LinearLayout(context); endpointRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams endpointFieldLp = new LinearLayout.LayoutParams(0, -2, 1f); endpointRow.addView(endpoint, endpointFieldLp);
        LinearLayout.LayoutParams endpointButtonLp = new LinearLayout.LayoutParams(Math.round(76 * context.getResources().getDisplayMetrics().density), -2);
        endpointButtonLp.leftMargin = Math.max(4, pad / 3); endpointRow.addView(endpointPicker, endpointButtonLp);
        LinearLayout.LayoutParams endpointLp = new LinearLayout.LayoutParams(-1, -2); endpointLp.topMargin = pad / 2; container.addView(endpointRow, endpointLp);

        EditText model = new EditText(context); model.setSingleLine(true); model.setInputType(InputType.TYPE_CLASS_TEXT);
        model.setHint(BotnaI18n.translate(context, "اسم النموذج كما يقدمه المزوّد")); styleInput(context, model, pad);
        Button modelPicker = smallGlassButton(context, "🧠 النماذج");
        LinearLayout modelRow = new LinearLayout(context); modelRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modelFieldLp = new LinearLayout.LayoutParams(0, -2, 1f); modelRow.addView(model, modelFieldLp);
        LinearLayout.LayoutParams modelButtonLp = new LinearLayout.LayoutParams(Math.round(86 * context.getResources().getDisplayMetrics().density), -2);
        modelButtonLp.leftMargin = Math.max(4, pad / 3); modelRow.addView(modelPicker, modelButtonLp);
        LinearLayout.LayoutParams modelLp = new LinearLayout.LayoutParams(-1, -2); modelLp.topMargin = pad / 3; container.addView(modelRow, modelLp);

        EditText key = new EditText(context); key.setSingleLine(false); key.setMinLines(2); key.setMaxLines(5);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setGravity(Gravity.TOP | Gravity.START);
        key.setHint(BotnaI18n.translate(context, "أضف مفاتيح API جديدة — كل مفتاح في سطر مستقل. اتركه فارغًا للاحتفاظ بالمحفوظ")); styleInput(context, key, pad);
        LinearLayout.LayoutParams keyLp = new LinearLayout.LayoutParams(-1, -2); keyLp.topMargin = pad / 3; container.addView(key, keyLp);
        TextView keyCount = new TextView(context); keyCount.setTextColor(MUTED); keyCount.setTextSize(11); keyCount.setGravity(BotnaI18n.isEnglish(context) ? Gravity.LEFT : Gravity.RIGHT); BotnaI18n.applyDirection(context, keyCount);
        LinearLayout.LayoutParams keyCountLp = new LinearLayout.LayoutParams(-1, -2); keyCountLp.topMargin = pad / 5; container.addView(keyCount, keyCountLp);
        LinearLayout keyPreview = new LinearLayout(context); keyPreview.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams keyPreviewLp = new LinearLayout.LayoutParams(-1, -2); keyPreviewLp.topMargin = pad / 5; container.addView(keyPreview, keyPreviewLp);
        Runnable refreshKeyPreview = () -> {
            keyPreview.removeAllViews();
            List<String> lines = splitKeyLines(key.getText().toString());
            for (int i = 0; i < lines.size(); i++) {
                TextView item = new TextView(context); item.setText(BotnaI18n.isEnglish(context) ? "New key " + (i + 1) + " • will be encrypted" : "مفتاح جديد " + (i + 1) + " • سيُحفظ مشفّرًا");
                item.setTextColor(TEXT); item.setTextSize(11); item.setPadding(pad / 2, pad / 5, pad / 2, pad / 5);
                item.setBackground(shape(context, Color.argb(95, 35, 226, 244), CYAN)); keyPreview.addView(item, new LinearLayout.LayoutParams(-1, -2));
                if (i + 1 < lines.size()) { View divider = new View(context); divider.setBackgroundColor(Color.argb(150, 35, 226, 244));
                    LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, Math.max(1, pad / 12)); dividerLp.topMargin = pad / 5; dividerLp.bottomMargin = pad / 5; keyPreview.addView(divider, dividerLp); }
            }
            int saved = BotnaKeyVault.count(context, providerSpinner.getSelectedItemPosition() >= 0 && providerSpinner.getSelectedItemPosition() < ids.length ? ids[providerSpinner.getSelectedItemPosition()] : PROVIDER_GEMINI);
            keyCount.setText(BotnaI18n.isEnglish(context)
                    ? "Saved keys for this provider: " + saved + " • New keys in field: " + lines.size() + " • maximum 20"
                    : "المفاتيح المحفوظة لهذا المزوّد: " + saved + " • مفاتيح جديدة في الحقل: " + lines.size() + " • الحد الأقصى 20");
        };
        key.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshKeyPreview.run(); }
            @Override public void afterTextChanged(android.text.Editable e) { }
        });

        TextView help = new TextView(context); help.setTextColor(MUTED); help.setTextSize(12); help.setGravity(BotnaI18n.isEnglish(context) ? Gravity.LEFT : Gravity.RIGHT); BotnaI18n.applyDirection(context, help);
        LinearLayout.LayoutParams helpLp = new LinearLayout.LayoutParams(-1, -2); helpLp.topMargin = pad / 3; container.addView(help, helpLp);

        Button testConnectionButton = new Button(context);
        testConnectionButton.setText("🔌 " + BotnaI18n.buttonText(context, "اختبار الاتصال دون تنفيذ أي خطوة"));
        testConnectionButton.setTextSize(13);
        testConnectionButton.setAllCaps(false);
        testConnectionButton.setTextColor(TEXT);
        android.graphics.drawable.GradientDrawable testGlass = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(232, 18, 101, 151), Color.argb(240, 8, 38, 72)});
        testGlass.setCornerRadius(Math.round(14 * context.getResources().getDisplayMetrics().density));
        testGlass.setStroke(Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)), CYAN);
        testConnectionButton.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(100, 120, 240, 255)), testGlass, null));
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
            int savedKeyCount = BotnaKeyVault.count(context, id);
            status.setText(savedKeyCount > 0
                    ? (BotnaI18n.isEnglish(context) ? "✓ " + savedKeyCount + " encrypted key(s) saved. Available keys are tried, but Gemini keys from the same project share quota." : "✓ يوجد " + savedKeyCount + " مفتاح/مفاتيح محفوظة ومشفّرة؛ سيجرّب التطبيق المفاتيح المتاحة، لكن مفاتيح Gemini من المشروع نفسه تشترك في الحصة.")
                    : (BotnaI18n.isEnglish(context) ? "No key is saved for this provider yet." : "لا يوجد مفتاح محفوظ لهذا المزوّد حتى الآن."));
            refreshKeyPreview.run();
            if (PROVIDER_GITHUB_MODELS.equals(id)) {
                help.setText(BotnaI18n.translate(context, "GitHub Models: أدخل GitHub token صالحًا واسم نموذج متاحًا لحسابك. الحصة المجانية وحدود النماذج تتغير حسب شروط GitHub."));
            } else if (PROVIDER_DEEPSEEK.equals(id)) {
                help.setText(BotnaI18n.translate(context, "DeepSeek: رابط Chat Completions مستقل. النموذج الافتراضي deepseek-flash؛ استخدم زر النماذج لمعرفة المعرفات المتاحة لحسابك."));
            } else if (PROVIDER_OPENROUTER.equals(id)) {
                help.setText(BotnaI18n.translate(context, "OpenRouter: كل النماذج تمر عبر هذا المزود، وقد تختلف الرؤية والسعر حسب النموذج. اختر نموذجًا يدعم الصور إذا أرفقت لقطة شاشة."));
            } else if (PROVIDER_GROQ.equals(id)) {
                help.setText(BotnaI18n.translate(context, "Groq API: استخدم مفتاح Groq ومعرف نموذج فعّال من القائمة المباشرة. ليست كل النماذج تدعم الصور."));
            } else if (PROVIDER_OPENAI_COMPAT.equals(id)) {
                help.setText(BotnaI18n.translate(context, "ملف متوافق قديم للحفاظ على الإعدادات السابقة. يفضّل اختيار DeepSeek أو OpenRouter أو Groq من الملفات المفصولة."));
            } else if (PROVIDER_CUSTOM.equals(id)) {
                help.setText(BotnaI18n.translate(context, "أدخل رابط /chat/completions الكامل مع المفتاح ومعرّف النموذج. يدعم هذا غالبية واجهات OpenAI-compatible، وليس كل واجهات النماذج الخاصة."));
            } else if (PROVIDER_COPILOT_BRIDGE.equals(id)) {
                help.setText(BotnaI18n.translate(context, "لا يمكن استخدام Copilot Free بإدخال مفتاح API عادي. هذا الخيار يتطلب خدمة جسر خارجية تستخدم تكامل GitHub Copilot الرسمي وOAuth/SDK؛ لا تستخدم نقاط API غير موثقة."));
            } else if (PROVIDER_ANTHROPIC.equals(id)) {
                help.setText(BotnaI18n.translate(context, "يتطلب مفتاح Anthropic API صالحًا ورصيد/حصة في حساب Anthropic. رابط Messages API قابل للتعديل."));
            } else if (PROVIDER_GEMINI.equals(id)) {
                help.setText(BotnaI18n.translate(context, "أدخل مفتاح Gemini API. في هذا الوضع اكتب جذر API مثل https://generativelanguage.googleapis.com/v1beta واسم النموذج دون بادئة models/."));
            } else {
                help.setText(BotnaI18n.translate(context, "يستخدم هذا الوضع OpenAI Responses API. مفتاح ChatGPT/Copilot لا يعادل تلقائيًا مفتاح API؛ استخدم اعتماد API رسميًا من المزوّد."));
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

        endpointPicker.setOnClickListener(v -> {
            stashCurrent[0].run();
            int index = providerSpinner.getSelectedItemPosition(); if (index < 0 || index >= ids.length) return;
            String selectedProvider = ids[index];
            String[] values = endpointSuggestions(selectedProvider);
            String[] labelsForPicker = endpointSuggestionLabels(selectedProvider);
            if (BotnaI18n.isEnglish(context)) {
                for (int i = 0; i < labelsForPicker.length; i++) labelsForPicker[i] = (i == 0 ? "★ Recommended — " : "Compatible alternative — ") + (values[i].isEmpty() ? "Enter bridge URL manually" : values[i]);
            }
            AlertDialog picker = new AlertDialog.Builder(context).setTitle(BotnaI18n.isEnglish(context) ? "Suggested API endpoints — " + providerLabel(selectedProvider) : "روابط API المقترحة — " + providerLabel(selectedProvider))
                    .setItems(labelsForPicker, (d, which) -> {
                        if (which < 0 || which >= values.length) return;
                        endpoint.setText(values[which]);
                        drafts.put(selectedProvider, new String[]{values[which], model.getText().toString(), key.getText().toString()});
                        status.setText(BotnaI18n.translate(context, "تم اختيار الرابط. اضغط حفظ الإعدادات لاعتماده."));
                    }).setNegativeButton(BotnaI18n.translate(context, "إغلاق"), null).create();
            try { if (picker.getWindow() != null && accessibilityOverlay) picker.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); picker.show(); }
            catch (Exception ex) { status.setText(BotnaI18n.translate(context, "تعذر عرض روابط API.")); }
        });

        modelPicker.setOnClickListener(v -> {
            stashCurrent[0].run();
            int index = providerSpinner.getSelectedItemPosition(); if (index < 0 || index >= ids.length) return;
            String selectedProvider = ids[index];
            String[] data = drafts.get(selectedProvider);
            if (data == null) data = new String[]{endpoint.getText().toString(), model.getText().toString(), key.getText().toString()};
            final String selectedEndpoint = data[0] == null ? "" : data[0].trim();
            List<String> typedKeys = splitKeyLines(data[2]);
            final String selectedKey = typedKeys.isEmpty() ? BotnaKeyVault.load(context, selectedProvider) : typedKeys.get(0);
            if (selectedEndpoint.isEmpty() || selectedKey.isEmpty()) {
                status.setText(BotnaI18n.translate(context, "أدخل رابط API ومفتاح هذا المزود قبل جلب النماذج.")); return;
            }
            modelPicker.setEnabled(false); status.setText(BotnaI18n.isEnglish(context) ? "Fetching models for " + providerLabel(selectedProvider) + " from API…" : "جارٍ جلب نماذج " + providerLabel(selectedProvider) + " من API…");
            final String requestedProvider = selectedProvider;
            new Thread(() -> {
                List<ModelOption> found = new ArrayList<>(); String error = null;
                try { found = fetchModels(requestedProvider, selectedEndpoint, selectedKey); }
                catch (Exception ex) { error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }
                final List<ModelOption> modelsFound = found; final String fetchError = error;
                new Handler(Looper.getMainLooper()).post(() -> {
                    modelPicker.setEnabled(true);
                    if (providerSpinner.getSelectedItemPosition() >= 0 && !requestedProvider.equals(ids[providerSpinner.getSelectedItemPosition()])) {
                        status.setText(BotnaI18n.translate(context, "تم إنهاء طلب النماذج للمزود السابق؛ اختره مرة أخرى لعرض النتيجة.")); return;
                    }
                    if (fetchError != null || modelsFound.isEmpty()) {
                        status.setText(BotnaI18n.isEnglish(context)
                                ? "Could not fetch models: " + (fetchError == null ? "The API returned no model list." : fetchError)
                                : "تعذر جلب النماذج: " + (fetchError == null ? "لم يرجع API قائمة نماذج." : fetchError)); return;
                    }
                    int count = Math.min(modelsFound.size(), 80);
                    String[] display = new String[count];
                    for (int i = 0; i < count; i++) {
                        String label = modelsFound.get(i).label;
                        if (BotnaI18n.isEnglish(context)) {
                            label = label.replace("★ الافتراضي", "★ Default")
                                    .replace("★ الافتراضي —", "★ Default —")
                                    .replace("✓ يدعم الصور", "✓ Vision")
                                    .replace("★ موصى به", "★ Recommended")
                                    .replace("بديل متوافق", "Compatible alternative");
                        }
                        display[i] = label;
                    }
                    AlertDialog modelsDialog = new AlertDialog.Builder(context).setTitle(BotnaI18n.isEnglish(context) ? "Available models — " + providerLabel(requestedProvider) : "النماذج المتاحة — " + providerLabel(requestedProvider))
                            .setItems(display, (d, which) -> {
                                if (which < 0 || which >= count) return;
                                String chosen = modelsFound.get(which).id;
                                model.setText(chosen);
                                drafts.put(requestedProvider, new String[]{endpoint.getText().toString(), chosen, key.getText().toString()});
                                status.setText(BotnaI18n.isEnglish(context) ? "Selected model " + chosen + ". Save settings to apply it." : "اخترت النموذج " + chosen + ". اضغط حفظ الإعدادات للاعتماد.");
                            }).setNegativeButton(BotnaI18n.translate(context, "إغلاق"), null).create();
                    try { if (modelsDialog.getWindow() != null && accessibilityOverlay) modelsDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); modelsDialog.show(); }
                    catch (Exception ex) { status.setText(BotnaI18n.translate(context, "تعذر عرض قائمة النماذج.")); }
                });
            }, "Botna-Model-Fetch").start();
        });

        testConnectionButton.setOnClickListener(v -> {
            stashCurrent[0].run();
            int index = providerSpinner.getSelectedItemPosition();
            if (index < 0 || index >= ids.length) return;
            String selected = ids[index];
            String[] data = drafts.get(selected);
            if (data == null) data = new String[]{endpoint.getText().toString(), model.getText().toString(), key.getText().toString()};
            final String testEndpoint = data[0] == null ? "" : data[0].trim();
            final String testModel = data[1] == null ? "" : data[1].trim();
            List<String> providedKeys = splitKeyLines(data[2]);
            final String testKey = providedKeys.isEmpty() ? BotnaKeyVault.load(context, selected) : providedKeys.get(0);
            if (testEndpoint.isEmpty() || testModel.isEmpty() || testKey.isEmpty()) {
                status.setText(BotnaI18n.translate(context, "أدخل رابط API والنموذج والمفتاح أو احفظ مفتاحًا لهذا المزوّد أولًا."));
                return;
            }
            testConnectionButton.setEnabled(false);
            status.setText(BotnaI18n.translate(context, "جارٍ اختبار الاتصال… لن تُنفّذ أي خطوة على الهاتف."));
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

        ScrollView settingsScroll = new ScrollView(context);
        settingsScroll.setFillViewport(false);
        settingsScroll.setVerticalScrollBarEnabled(true);
        settingsScroll.setSmoothScrollingEnabled(true);
        settingsScroll.setClipToPadding(false);
        settingsScroll.addView(container, new ScrollView.LayoutParams(-1, -2));

        LinearLayout dialogRoot = new LinearLayout(context);
        dialogRoot.setOrientation(LinearLayout.VERTICAL);
        int headPad = Math.round(6 * context.getResources().getDisplayMetrics().density);
        dialogRoot.setPadding(headPad, headPad, headPad, headPad);
        LinearLayout settingsHeader = new LinearLayout(context);
        settingsHeader.setOrientation(LinearLayout.HORIZONTAL);
        settingsHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView settingsTitle = new TextView(context);
        settingsTitle.setText(BotnaI18n.translate(context, "مركز إعدادات مزوّدي الذكاء الاصطناعي"));
        settingsTitle.setTextColor(TEXT);
        settingsTitle.setTextSize(15);
        settingsTitle.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        settingsHeader.addView(settingsTitle, new LinearLayout.LayoutParams(0, Math.round(44 * context.getResources().getDisplayMetrics().density), 1f));
        Button backSettingsButton = smallGlassButton(context, "↩");
        Button closeSettingsButton = smallGlassButton(context, "×");
        LinearLayout.LayoutParams headerBtnLp = new LinearLayout.LayoutParams(Math.round(40 * context.getResources().getDisplayMetrics().density), Math.round(40 * context.getResources().getDisplayMetrics().density));
        settingsHeader.addView(backSettingsButton, headerBtnLp);
        LinearLayout.LayoutParams closeHeaderLp = new LinearLayout.LayoutParams(headerBtnLp);
        closeHeaderLp.leftMargin = headPad / 2;
        settingsHeader.addView(closeSettingsButton, closeHeaderLp);
        dialogRoot.addView(settingsHeader, new LinearLayout.LayoutParams(-1, -2));
        dialogRoot.addView(settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button saveSettingsButton = smallGlassButton(context, "💾 حفظ الإعدادات");
        saveSettingsButton.setTextSize(14);
        LinearLayout.LayoutParams saveActionLp = new LinearLayout.LayoutParams(-1, Math.round(48 * context.getResources().getDisplayMetrics().density));
        saveActionLp.leftMargin = pad / 2; saveActionLp.rightMargin = pad / 2; saveActionLp.topMargin = pad / 4;
        dialogRoot.addView(saveSettingsButton, saveActionLp);

        LinearLayout secondaryActions = new LinearLayout(context);
        secondaryActions.setOrientation(LinearLayout.HORIZONTAL);
        Button clearKeysButton = smallGlassButton(context, "🔑 مسح مفاتيح المزوّد");
        clearKeysButton.setTextSize(11);
        secondaryActions.addView(clearKeysButton, new LinearLayout.LayoutParams(-1, Math.round(44 * context.getResources().getDisplayMetrics().density)));
        LinearLayout.LayoutParams secondaryActionsLp = new LinearLayout.LayoutParams(-1, -2);
        secondaryActionsLp.leftMargin = pad / 2; secondaryActionsLp.rightMargin = pad / 2; secondaryActionsLp.topMargin = pad / 4; secondaryActionsLp.bottomMargin = pad / 3;
        dialogRoot.addView(secondaryActions, secondaryActionsLp);

        AlertDialog dialog = new AlertDialog.Builder(context).setView(dialogRoot).create();
        try {
            if (dialog.getWindow() != null && accessibilityOverlay) dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            dialog.show();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
                android.util.DisplayMetrics screen = context.getResources().getDisplayMetrics();
                int width = Math.min(screen.widthPixels - Math.round(24 * screen.density), Math.round(560 * screen.density));
                int height = Math.min(screen.heightPixels - Math.round(48 * screen.density), Math.round(screen.heightPixels * 0.78f));
                dialog.getWindow().setLayout(Math.max(1, width), Math.max(1, height));
            }
            closeSettingsButton.setOnClickListener(v -> dialog.dismiss());
            backSettingsButton.setOnClickListener(v -> dialog.dismiss());
            View.OnFocusChangeListener scrollToFocusedInput = (v, focused) -> {
                if (!focused) return;
                settingsScroll.postDelayed(() -> {
                    try {
                        Rect rect = new Rect(0, 0, v.getWidth(), v.getHeight());
                        settingsScroll.offsetDescendantRectToMyCoords(v, rect);
                        int targetY = Math.max(0, rect.bottom - settingsScroll.getHeight() + Math.round(36 * context.getResources().getDisplayMetrics().density));
                        settingsScroll.smoothScrollTo(0, targetY);
                    } catch (Exception ignored) { }
                }, 350);
            };
            endpoint.setOnFocusChangeListener(scrollToFocusedInput);
            model.setOnFocusChangeListener(scrollToFocusedInput);
            key.setOnFocusChangeListener(scrollToFocusedInput);
            endpoint.setOnClickListener(v -> scrollToFocusedInput.onFocusChange(v, true));
            model.setOnClickListener(v -> scrollToFocusedInput.onFocusChange(v, true));
            key.setOnClickListener(v -> scrollToFocusedInput.onFocusChange(v, true));
            saveSettingsButton.setOnClickListener(v -> {
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
            clearKeysButton.setOnClickListener(v -> {
                try {
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && key.getWindowToken() != null) imm.hideSoftInputFromWindow(key.getWindowToken(), 0);
                    key.clearFocus();
                } catch (Exception ignored) { }
                int index = providerSpinner.getSelectedItemPosition(); if (index < 0 || index >= ids.length) return;
                String selected = ids[index];
                AlertDialog confirm = new AlertDialog.Builder(context).setTitle(BotnaI18n.translate(context, "مسح مفاتيح المزوّد"))
                        .setMessage(BotnaI18n.translate(context, "سيُحذف جميع مفاتيح هذا المزوّد المشفّرة فقط، ولن تتأثر مفاتيح المزوّدين الآخرين. لا يمكن التراجع عن الحذف. متابعة؟"))
                        .setPositiveButton(BotnaI18n.translate(context, "مسح جميع المفاتيح"), (d, w) -> {
                            try {
                                BotnaKeyVault.save(context, selected, "");
                                status.setText(BotnaI18n.translate(context, "تم حذف جميع مفاتيح هذا المزوّد.")); key.setText(BotnaI18n.translate(context, "")); drafts.put(selected, new String[]{endpoint.getText().toString(), model.getText().toString(), ""}); refreshKeyPreview.run();
                                Toast.makeText(context, "تم مسح جميع مفاتيح هذا المزوّد.", Toast.LENGTH_SHORT).show();
                                if (onSaved != null) onSaved.run();
                            } catch (Exception e) { Toast.makeText(context, "تعذر مسح المفتاح.", Toast.LENGTH_LONG).show(); }
                        }).setNegativeButton(BotnaI18n.translate(context, "إلغاء"), null).create();
                try { if (confirm.getWindow() != null && accessibilityOverlay) confirm.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY); confirm.show(); }
                catch (Exception e) { Toast.makeText(context, "تعذر فتح تأكيد المسح.", Toast.LENGTH_LONG).show(); }
            });
        } catch (Exception e) { Toast.makeText(context, "تعذر فتح إعدادات الذكاء الاصطناعي.", Toast.LENGTH_LONG).show(); }
    }

    private static final class ModelOption {
        final String id; final String label; final boolean vision; final boolean recommended;
        ModelOption(String id, String label, boolean vision, boolean recommended) { this.id = id; this.label = label; this.vision = vision; this.recommended = recommended; }
    }

    private static Button smallGlassButton(Context context, String label) {
        Button button = new Button(context);
        button.setText(label); button.setAllCaps(false); button.setTextSize(10); button.setSingleLine(false);
        button.setTextColor(TEXT);
        button.setPadding(2, 0, 2, 0);
        android.graphics.drawable.GradientDrawable glass = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                new int[]{Color.argb(245, 18, 103, 151), Color.argb(245, 10, 44, 82)});
        glass.setCornerRadius(Math.round(12 * context.getResources().getDisplayMetrics().density));
        glass.setStroke(Math.max(1, Math.round(context.getResources().getDisplayMetrics().density)), CYAN);
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(100, 120, 240, 255)), glass, null));
        return button;
    }

    private static String[] endpointSuggestions(String provider) {
        if (PROVIDER_DEEPSEEK.equals(provider)) return new String[]{"https://api.deepseek.com/chat/completions", "https://api.deepseek.com/v1/chat/completions"};
        if (PROVIDER_OPENROUTER.equals(provider)) return new String[]{"https://openrouter.ai/api/v1/chat/completions"};
        if (PROVIDER_GROQ.equals(provider)) return new String[]{"https://api.groq.com/openai/v1/chat/completions"};
        if (PROVIDER_OPENAI_RESPONSES.equals(provider)) return new String[]{"https://api.openai.com/v1/responses"};
        if (PROVIDER_ANTHROPIC.equals(provider)) return new String[]{"https://api.anthropic.com/v1/messages"};
        if (PROVIDER_GEMINI.equals(provider)) return new String[]{"https://generativelanguage.googleapis.com/v1beta"};
        if (PROVIDER_GITHUB_MODELS.equals(provider)) return new String[]{"https://models.github.ai/inference/chat/completions"};
        if (PROVIDER_COPILOT_BRIDGE.equals(provider)) return new String[]{""};
        if (PROVIDER_OPENAI_COMPAT.equals(provider)) return new String[]{"https://api.deepseek.com/chat/completions"};
        return new String[]{"https://provider.example/v1/chat/completions"};
    }

    private static String[] endpointSuggestionLabels(String provider) {
        String[] values = endpointSuggestions(provider); String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = (i == 0 ? "★ موصى به — " : "بديل متوافق — ") + (values[i].isEmpty() ? "أدخل رابط الجسر يدويًا" : values[i]);
        return labels;
    }

    private static List<ModelOption> fetchModels(String provider, String endpoint, String key) throws Exception {
        String id = normalizeProviderId(provider);
        String url = modelsEndpoint(id, endpoint);
        if (PROVIDER_GEMINI.equals(id)) url += (url.contains("?") ? "&" : "?") + "key=" + URLEncoder.encode(key, "UTF-8");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET"); connection.setConnectTimeout(15000); connection.setReadTimeout(25000);
            connection.setRequestProperty("Accept", "application/json");
            if (!PROVIDER_GEMINI.equals(id) && !key.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + key);
            if (PROVIDER_ANTHROPIC.equals(id)) { connection.setRequestProperty("x-api-key", key); connection.setRequestProperty("anthropic-version", "2023-06-01"); }
            if (PROVIDER_GITHUB_MODELS.equals(id)) { connection.setRequestProperty("Accept", "application/vnd.github+json"); connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28"); }
            if (PROVIDER_OPENROUTER.equals(id)) { connection.setRequestProperty("HTTP-Referer", "https://github.com/"); connection.setRequestProperty("X-OpenRouter-Title", "Botna Android Automation"); }
            int code = connection.getResponseCode();
            InputStream body = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
            String raw = readAll(body);
            if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code + ": " + trimForMessage(raw));
            JSONObject json = new JSONObject(raw);
            JSONArray data = json.optJSONArray("data");
            if (data == null) data = json.optJSONArray("models");
            if (data == null) throw new IllegalStateException("استجابة API لا تحتوي على data أو models. تحقق من رابط جلب النماذج.");
            String defaultId = defaultModel(id);
            List<ModelOption> found = new ArrayList<>();
            for (int i = 0; i < data.length(); i++) {
                JSONObject item = data.optJSONObject(i); if (item == null) continue;
                String modelId = item.optString("id", item.optString("name", "")).trim();
                if (modelId.startsWith("models/")) modelId = modelId.substring("models/".length());
                if (modelId.isEmpty()) continue;
                if (item.has("active") && !item.optBoolean("active", true)) continue;
                JSONArray methods = item.optJSONArray("supported_generation_methods");
                if (methods != null) {
                    boolean usable = false; for (int j = 0; j < methods.length(); j++) if ("generateContent".equalsIgnoreCase(methods.optString(j))) usable = true;
                    if (!usable) continue;
                }
                boolean vision = supportsImage(item);
                boolean recommended = modelId.equals(defaultId) || vision;
                String label = modelId + (modelId.equals(defaultId) ? "  ★ الافتراضي" : vision ? "  ✓ يدعم الصور" : "");
                found.add(new ModelOption(modelId, label, vision, recommended));
            }
            Collections.sort(found, new Comparator<ModelOption>() {
                @Override public int compare(ModelOption a, ModelOption b) {
                    if (a.id.equals(defaultId) && !b.id.equals(defaultId)) return -1;
                    if (!a.id.equals(defaultId) && b.id.equals(defaultId)) return 1;
                    if (a.vision != b.vision) return a.vision ? -1 : 1;
                    return a.id.compareToIgnoreCase(b.id);
                }
            });
            if (found.size() > 200) return new ArrayList<>(found.subList(0, 200));
            return found;
        } finally { if (connection != null) connection.disconnect(); }
    }

    private static boolean supportsImage(JSONObject item) {
        JSONObject architecture = item.optJSONObject("architecture");
        JSONArray modalities = architecture == null ? item.optJSONArray("input_modalities") : architecture.optJSONArray("input_modalities");
        if (modalities == null) return false;
        for (int i = 0; i < modalities.length(); i++) if ("image".equalsIgnoreCase(modalities.optString(i))) return true;
        return false;
    }

    private static String modelsEndpoint(String provider, String endpoint) {
        String value = trimTrailingSlash(endpoint);
        if (PROVIDER_GEMINI.equals(provider)) return value + "/models";
        String[] suffixes = {"/chat/completions", "/inference/chat/completions", "/responses", "/messages", "/completions"};
        for (String suffix : suffixes) if (value.endsWith(suffix)) return value.substring(0, value.length() - suffix.length()) + "/models";
        if (value.endsWith("/v1")) return value + "/models";
        return value + "/models";
    }

    private static String trimForMessage(String raw) {
        String value = raw == null ? "" : raw.trim();
        return value.length() > 240 ? value.substring(0, 240) : value;
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
