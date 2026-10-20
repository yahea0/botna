package com.botna.automation;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class BotnaI18n {
    private static final String PREFS = "botna_settings";
    private static final String KEY_LANGUAGE = "ui_language";
    private static final Map<String, String> ENGLISH;
    private static final Map<String, String> ICONS;
    private static final Map<String, String> HEADING_ICONS;

    static {
        Map<String, String> e = new HashMap<>();
        put(e, "بوتنا  Botna", "Botna Automation");
        put(e, "منصة أتمتة ذكية ومباشرة لهاتفك", "Smart automation, directly on your phone");
        put(e, "حالة خدمة الأتمتة", "Automation service status");
        put(e, "جارٍ فحص الحالة...", "Checking status…");
        put(e, "تفعيل / إدارة خدمة إمكانية الوصول", "Enable / manage Accessibility service");
        put(e, "مركز التحكم", "Control center");
        put(e, "أنشئ السكربتات من الفقاعة B العائمة، واضبط مكتبة الصور، وشغّل الأوامر مع إمكانية الإيقاف والاستكمال.", "Create scripts from the floating B bubble, manage the image library, and run, pause, or resume commands.");
        put(e, "إدارة الخدمة", "Manage service");
        put(e, "إعدادات الذكاء الاصطناعي", "AI settings");
        put(e, "دليل الأوامر المخصصة", "Command guide");
        put(e, "تحديث الحالة", "Refresh status");
        put(e, "قائمة السجلات", "Logs");
        put(e, "إنشاء بوت ذكي للمهام المعقدة", "Create smart agent task");
        put(e, "إنشاء بوت ذكي للمهام الصعبة والطويلة", "Create a smart agent for complex tasks");
        put(e, "مهام الوكيل المحفوظة", "Saved agent tasks");
        put(e, "رسائل الوكيل", "Agent messages");
        put(e, "السكربتات المحفوظة", "Saved scripts");
        put(e, "عدد السكربتات: ", "Scripts: ");
        put(e, "\nيمكنك إنشاء سكربت مستقل، تعديل بلوكاته، إعادة تسميته، تشغيله أو حذفه.", "\nCreate a standalone script, edit its blocks, rename, run, or delete it.");
        put(e, "فتح قائمة السكربتات المحفوظة", "Open saved scripts");
        put(e, "بياناتك المحلية", "Your local data");
        put(e, "السكربتات المحفوظة: ", "Saved scripts: ");
        put(e, "\nقوالب مكتبة الصور: ", "\nImage templates: ");
        put(e, "\nمهام الوكيل المحفوظة: ", "\nSaved agent tasks: ");
        put(e, "\nرسائل الوكيل: ", "\nAgent messages: ");
        put(e, "\nالمزوّد المحدد: ", "\nSelected provider: ");
        put(e, "\nالمفاتيح المحفوظة لهذا المزوّد: ", "\nKeys saved for this provider: ");
        put(e, "\nالسجلات التشخيصية: ", "\nDiagnostic logs: ");
        put(e, "\nحالة الذكاء الاصطناعي: ", "\nAI status: ");
        put(e, "إعداد API مكتمل", "API configured");
        put(e, "يتطلب إعداد مفتاح/نقطة اتصال API", "API key/endpoint setup required");
        put(e, "السكربتات وقوالب الصور تبقى في مساحة التطبيق. يُشفّر مفتاح API محليًا باستخدام Android Keystore؛ لا تشارك المفتاح ولا تضعه في مستودع GitHub. استخدام مزوّد الذكاء الاصطناعي عبر API قد يكلّف رسومًا حسب حسابك.", "Scripts and image templates stay in the app's private storage. API keys are encrypted with Android Keystore. Never share secrets or commit them to GitHub. API usage may incur provider charges.");
        put(e, "آخر نتيجة", "Latest result");
        put(e, "لم يتم تنفيذ أي إجراء بعد.", "No action has been executed yet.");
        put(e, "تحديث السجل", "Refresh log");
        put(e, "ملاحظة: تفعيل خدمة إمكانية الوصول يتم يدويًا من إعدادات أندرويد. قبل تشغيل أي أمر مولّد بالذكاء الاصطناعي راجع معاينة الخطوات؛ لن يُسمح للمترجم بتنفيذ كود عشوائي خارج أنواع الأوامر المدعومة.", "Note: Enable Accessibility manually in Android Settings. Review AI-generated steps before running; the compiler only supports defined action types, not arbitrary code.");
        put(e, "اللغة", "Language"); put(e, "العربية", "Arabic"); put(e, "الإنجليزية", "English");
        put(e, "اسم المطوّر", "Developer"); put(e, "تطوير يحيى النباهين", "Developed by Yahya Al-Nabahin");
        put(e, "للتواصل والاستفسار", "Contact and support"); put(e, "تواصل عبر Telegram", "Contact via Telegram");
        put(e, "اسأل بوتنا", "Ask Botna"); put(e, "اسأل عن التطبيق أو إحدى ميزاته…", "Ask about the app or a feature…");
        put(e, "مساعد استخدام بوتنا", "Botna Help Assistant");
        put(e, "اسأل عن السكربتات أو الوكيل أو المفاتيح أو السجلات أو طريقة تشغيل التطبيق.", "Ask about scripts, the agent, API keys, logs, or how to use the app.");
        put(e, "مرحبًا بك في بوتنا", "Welcome to Botna"); put(e, "التالي", "Next"); put(e, "السابق", "Back"); put(e, "ابدأ الاستخدام", "Get started"); put(e, "تخطي المقدمة", "Skip intro");
        put(e, "ابنِ أتمتتك بخطوات واضحة، اختبرها، ثم شغّلها مع إمكانية الإيقاف والاستكمال.", "Build automation with clear steps, review it, then run, pause, or resume it.");
        put(e, "١. فعّل خدمة إمكانية الوصول من إعدادات أندرويد. ٢. اختر مزوّد AI واضبط API عند الحاجة. ٣. أنشئ سكربتًا من الفقاعة B وراجع الخطوات قبل تشغيلها.", "1. Enable Accessibility in Android Settings. 2. Configure an AI provider/API if needed. 3. Create a script from the B bubble and review steps before running.");
        put(e, "التطبيق لا يرسل بياناتك إلى المطوّر. الطلبات والصور تذهب إلى مزوّد API الذي تختاره عند استخدام الذكاء الاصطناعي.", "The app does not send your data to the developer. Requests and screenshots are sent to the AI API provider you select.");
        put(e, "فهم طريقة الاستخدام", "How to use Botna");
        put(e, "شرح الميزة", "Feature explanation");
        put(e, "إنشاء سكربت جديد", "Create script"); put(e, "＋ إنشاء سكربت جديد", "＋ Create script");
        put(e, "إغلاق", "Close"); put(e, "إلغاء", "Cancel"); put(e, "حسنًا", "OK"); put(e, "حفظ", "Save"); put(e, "حفظ وخروج", "Save and exit"); put(e, "حفظ الإعدادات", "Save settings");
        put(e, "حذف", "Delete"); put(e, "حذف الكل", "Delete all"); put(e, "حذف جميع السجلات", "Delete all logs"); put(e, "حذف الرسائل", "Delete messages"); put(e, "حذف رسائل الوكيل", "Delete agent messages");
        put(e, "بدء المهمة", "Start task"); put(e, "إعادة تشغيل الوكيل", "Restart agent"); put(e, "تعديل المهمة", "Edit task"); put(e, "تعديل السكربت", "Edit script"); put(e, "تشغيل السكربت المحفوظ", "Run saved script");
        put(e, "قائمة السكربتات المحفوظة", "Saved scripts"); put(e, "رسائل تفسيرية محفوظة من الوكيل: سبب اختيار الإجراء، نتيجة تنفيذه، وتحذيرات أو أسباب التوقف. يمكن حذف سجل الرسائل دون حذف المهام.", "Saved explanations from the agent: why it chose an action, results, warnings, and stop reasons. Messages can be deleted without deleting tasks.");
        put(e, "سجلات بوتنا التشخيصية", "Botna diagnostic logs"); put(e, "تفاصيل سجل الفشل", "Failure log details"); put(e, "تفاصيل رسالة الوكيل", "Agent message details");
        put(e, "بحث عن أمر لإضافته", "Search for a command to add"); put(e, "ابحث عن أمر: بحث، كتابة، سحب، إشعارات…", "Search commands: search, type, swipe, notifications…"); put(e, "إغلاق البحث", "Close search");
        put(e, "قص هدف", "Crop target"); put(e, "نقر نص", "Tap text"); put(e, "انتظار", "Wait"); put(e, "رجوع", "Back"); put(e, "الرئيسية", "Home"); put(e, "إذا نجح", "If success"); put(e, "إذا فشل", "If failure"); put(e, "وإلا", "Else"); put(e, "نهاية الشرط", "End condition"); put(e, "كتابة نص", "Type text"); put(e, "فتح تطبيق", "Open app"); put(e, "فتح رابط", "Open URL"); put(e, "فحص الإنترنت", "Check internet"); put(e, "انتظار عودة الإنترنت", "Wait for internet"); put(e, "ضغط Enter/بحث", "Press Enter/Search"); put(e, "انتظار ظهور نص", "Wait for text"); put(e, "انتظار حقل إدخال", "Wait for input field"); put(e, "فتح الإشعارات", "Open notifications"); put(e, "الإعدادات السريعة", "Quick settings"); put(e, "التطبيقات الأخيرة", "Recent apps"); put(e, "مسح حقل الكتابة", "Clear input field"); put(e, "مكتبة الصور", "Image library"); put(e, "تغيير الإعدادات", "Change settings"); put(e, "تحليل وحفظ", "Analyze and save"); put(e, "حذف الخطوة", "Delete step"); put(e, "بدء", "Start"); put(e, "إيقاف مؤقت", "Pause"); put(e, "استكمال", "Resume"); put(e, "إيقاف نهائي", "Stop"); put(e, "إيقاف الوكيل", "Stop agent");
        put(e, "السكربتات المحفوظة", "Saved scripts");
        put(e, "عدد السجلات المحفوظة: ", "Saved logs: ");
        put(e, "لقطات الشاشة مع السجلات: مفعّلة", "Screenshots with logs: On");
        put(e, "لقطات الشاشة مع السجلات: متوقفة", "Screenshots with logs: Off");
        put(e, "لا توجد سجلات حتى الآن. عند فشل ترجمة أو خطوة ستظهر هنا تفاصيل السبب ولقطة الشاشة إذا كانت مفعّلة.", "No logs yet. Translation or step failures will show their reason and a screenshot here when screenshot logging is enabled.");
        put(e, "عدد السجلات المحفوظة: ", "Saved logs: ");
        put(e, "إنشاء بوت ذكي للمهام الصعبة والطويلة", "Create a smart agent for complex tasks");
        put(e, "اكتب الهدف المطلوب.", "Enter the goal.");
        put(e, "مثال: افتح التطبيق، ابحث عن العنصر المطلوب، افتحه ثم تحقق من النتيجة...", "Example: open an app, find an item, open it, then verify the result…");
        put(e, "أفهم أن الوكيل سيرى لقطات الشاشة ويتحكم بالواجهة وفق المهمة، وسأراجع العمليات الحساسة بنفسي.", "I understand the agent will view screenshots and control the interface; I will review sensitive actions myself.");
        put(e, "BOTNA  •  الأتمتة الذكية", "BOTNA • Smart Automation");
        put(e, "إخفاء القائمة", "Hide panel"); put(e, "حفظ", "Save"); put(e, "▶ بدء", "▶ Start");
        put(e, "Ⅱ إيقاف/استكمال", "Ⅱ Pause/Resume"); put(e, "■ إيقاف", "■ Stop"); put(e, "البحث عن أمر أو خطوة لإضافتها إلى السكربت", "Search for a command or step to add");
        put(e, "بحث عن أمر", "Search commands"); put(e, "أمر", "Command"); put(e, "تأخير عشوائي", "Random delay"); put(e, "انتظار صورة", "Wait for image");
        put(e, "اختبار آخر خطوة", "Test last step"); put(e, "اختيار خطوة محددة", "Select a specific step"); put(e, "بدء تكرار", "Start loop");
        put(e, "نهاية التكرار", "End loop"); put(e, "ضغط مطوّل", "Long press"); put(e, "إعادة قراءة الشاشة", "Read screen again"); put(e, "اختبار خطوة محددة", "Test selected step");
        put(e, "انتظار ظهور نص", "Wait for text"); put(e, "انتظار حقل إدخال", "Wait for input field"); put(e, "انتظار عودة الإنترنت", "Wait for internet");
        put(e, "قياس سرعة التنزيل", "Measure download speed"); put(e, "تنبيه مع نغمة", "Alert with sound"); put(e, "قص هدف", "Crop target"); put(e, "قص صورة", "Crop image");
        put(e, "فتح الإشعارات", "Open notifications"); put(e, "فتح شريط الإشعارات", "Open notification shade"); put(e, "الإعدادات السريعة", "Quick settings");
        put(e, "التطبيقات الأخيرة", "Recent apps"); put(e, "إغلاق الإشعارات", "Dismiss notifications"); put(e, "إغلاق التطبيقات الأخيرة", "Close recent apps");
        put(e, "مسح حقل الكتابة", "Clear input field"); put(e, "سحب شريط الإشعارات", "Swipe notification shade"); put(e, "فتح الرئيسية", "Go Home");
        put(e, "تشغيل", "Run"); put(e, "إيقاف مؤقت", "Pause"); put(e, "استكمال السكربت", "Resume script"); put(e, "إيقاف السكربت نهائيًا", "Stop script");
        put(e, "حفظ السكربت والخروج", "Save script and exit"); put(e, "تُحفظ خطوات السكربتات تلقائيًا أثناء التعديل. هل تريد حفظها الآن والخروج من Botna؟", "Script steps are saved automatically while editing. Save now and exit Botna?");
        put(e, "اكتب الأمر بالعربية", "Enter the command in Arabic"); put(e, "بلوكات الخطوة المخصصة", "Custom step blocks"); put(e, "تغيير إعدادات البلوك", "Change block settings");
        put(e, "إعدادات خطوة الصورة", "Image step settings"); put(e, "إعدادات خطوة النظام", "System action settings"); put(e, "إعدادات خطوة النقر على النص", "Text tap settings");
        put(e, "إعدادات صورة المكتبة", "Image library settings"); put(e, "إعدادات فتح التطبيق", "Open app settings"); put(e, "إعدادات فتح الرابط", "Open URL settings");
        put(e, "مركز إعدادات مزوّدي الذكاء الاصطناعي", "AI provider settings");
        put(e, "اختر مزودًا واحدًا مستقلًا؛ لكل مزود رابط ونموذج ومفتاح منفصل. زر «روابط» يعرض العناوين المعروفة، وزر «النماذج» يجلب المعرفات من API عند توفر اتصال ومفتاح صالح. أسماء النماذج وتوفرها يتغيران حسب حساب المزود.", "Choose one independent provider; each has its own URL, model, and key. Links shows known endpoints and Models fetches model IDs when the API and key are valid. Availability depends on your provider account.");
        put(e, "رابط API الكامل أو عنوان الجذر لـ Gemini", "Full API URL or Gemini API root"); put(e, "اسم النموذج كما يقدمه المزوّد", "Model ID as provided by the provider");
        put(e, "أضف مفاتيح API جديدة — كل مفتاح في سطر مستقل. اتركه فارغًا للاحتفاظ بالمحفوظ", "Add API keys, one per line. Leave empty to keep saved keys");
        put(e, "روابط", "Links"); put(e, "النماذج", "Models"); put(e, "مسح مفاتيح المزوّد", "Clear provider keys");
        put(e, "اختبار الاتصال دون تنفيذ أي خطوة", "Test connection without executing steps"); put(e, "حفظ الإعدادات", "Save settings");
        put(e, "DeepSeek API — مستقل", "DeepSeek API — independent"); put(e, "OpenRouter API — مستقل", "OpenRouter API — independent"); put(e, "Groq API — مستقل", "Groq API — independent");
        put(e, "رابط مخصص متوافق مع Chat Completions", "Custom Chat Completions endpoint"); put(e, "مزود متوافق قديم/مخصص", "Legacy/custom compatible provider");
        put(e, "GitHub Copilot عبر جسر خارجي (ليس مفتاح API عاديًا)", "GitHub Copilot via external bridge (not a normal API key)");
        put(e, "مفتاح جديد ", "New key "); put(e, " • سيُحفظ مشفّرًا", " • will be encrypted"); put(e, "المفاتيح المحفوظة لهذا المزوّد: ", "Saved keys for this provider: ");
        put(e, " • مفاتيح جديدة في الحقل: ", " • New keys in field: "); put(e, " • الحد الأقصى 20", " • maximum 20");
        put(e, "✓ يوجد ", "✓ "); put(e, " مفتاح/مفاتيح محفوظة ومشفّرة؛ سيجرّب التطبيق المفاتيح المتاحة، لكن مفاتيح Gemini من المشروع نفسه تشترك في الحصة.", " encrypted key(s) saved. Available keys will be tried; Gemini keys from one project share its quota.");
        put(e, "لا يوجد مفتاح محفوظ لهذا المزوّد حتى الآن.", "No key is saved for this provider yet.");
        put(e, "تفعيل / إدارة خدمة إمكانية الوصول", "Enable / manage Accessibility service");
        put(e, "الخطوات:", "Steps:"); put(e, "اسم المطوّر", "Developer"); put(e, "للتواصل والاستفسار", "Contact and support");
        put(e, "خطوة مخصصة", "Custom step"); put(e, "المهمة", "Task"); put(e, "النص", "Text"); put(e, "التفاصيل", "Details");
        put(e, "＋ قص هدف", "＋ Crop target"); put(e, "＋ نقر نص", "＋ Tap text"); put(e, "＋ انتظار", "＋ Wait"); put(e, "تمرير", "Swipe/scroll"); put(e, "تأخير عشوائي", "Random delay");
        put(e, "＋ خطوة مخصصة", "＋ Custom step"); put(e, "إضافة/تعديل الخطوات", "Add/edit steps"); put(e, "إعادة الوكيل", "Restart agent"); put(e, "إعادة تسمية السكربت", "Rename script");
        put(e, "افتح محرر Botna لإضافة الخطوات أو تعديلها.", "Open the Botna editor to add or edit steps.");
        put(e, "كل سكربت محفوظ مستقل عن مهام الوكيل. يمكنك إعادة تسميته، تعديل خطواته أو إضافة خطوات من محرر Botna، تشغيله أو حذفه.", "Each saved script is independent of agent tasks. Rename it, edit or add steps in the Botna editor, run it, or delete it.");
        put(e, "كل مهمة محفوظة بشكل مستقل. إعادة تشغيل الوكيل تعيد التخطيط حسب الشاشة، أما تشغيل السكربت فيعيد الإجراءات المسجلة كما هي. افتح السكربت لتعديل الخطوات داخل محرر بوتنا.", "Each task is saved independently. Restarting the agent replans from the current screen; running the script replays saved actions. Open the script to edit its steps in the Botna editor.");
        put(e, "لا توجد سكربتات محفوظة. أنشئ سكربتًا جديدًا ثم أضف خطواته من محرر Botna.", "No saved scripts. Create one, then add steps in the Botna editor.");
        put(e, "لا توجد مهام محفوظة بعد. أنشئ مهمة من الزر أعلاه.", "No saved tasks yet. Create a task using the button above.");
        put(e, "لا توجد رسائل بعد.", "No messages yet."); put(e, "لا توجد سجلات حتى الآن. عند فشل ترجمة أو خطوة ستظهر هنا تفاصيل السبب ولقطة الشاشة إذا كانت مفعّلة.", "No logs yet. Translation or step failures will appear here with details and a screenshot if enabled.");
        put(e, "كل الأوامر المتاحة", "All available commands"); put(e, "ابحث عن أمر لإضافته", "Search for a command to add");
        put(e, "افتح التطبيق، ابحث عن العنصر المطلوب، افتحه ثم تحقق من النتيجة...", "Open an app, find the required item, open it, then verify the result…");
        put(e, "حالة الوكيل", "Agent status"); put(e, "الحالة:", "Status:"); put(e, "الخطوات:", "Steps:");
        put(e, "مثال: افتح التطبيق، ابحث عن العنصر المطلوب، افتحه ثم تحقق من النتيجة...", "Example: open an app, find an item, open it, then verify the result…");
        put(e, "أمثلة للأوامر المدعومة:\n\n", "Supported command examples:\n\n");
        put(e, "أفضل نتيجة للأوامر المركبة تأتي مع مزوّد API مضبوط. راجع دائمًا معاينة الترجمة قبل التنفيذ.", "Complex commands work best with a configured API provider. Always review the generated plan before running it.");
        put(e, "فعّل خدمة Botna قبل فتح أو تشغيل السكربت.", "Enable the Botna service before opening or running a script.");
        put(e, "فعّل خدمة Botna قبل تحرير أو تشغيل السكربت.", "Enable the Botna service before editing or running a script.");
        put(e, "لم يتم العثور على السكربت المحفوظ.", "The saved script could not be found."); put(e, "لم يتم تنفيذ أي إجراء بعد.", "No action has been executed yet.");
        put(e, "عدد السجلات المحفوظة: ", "Saved logs: "); put(e, "السجلات التشخيصية:", "Diagnostic logs:");
        put(e, "الوقت:", "Time:"); put(e, "المصدر:", "Source:"); put(e, "اسم الخطوة:", "Step name:"); put(e, "النتيجة:", "Result:"); put(e, "السبب والتفاصيل:", "Reason and details:");
        put(e, "وقت غير معروف", "Unknown time"); put(e, "غير محددة", "Not specified"); put(e, "غير محدد", "Not specified"); put(e, "فشل غير محدد", "Unspecified failure");
        put(e, "معلومة/نجاح", "Information/success"); put(e, "تحذير/فشل", "Warning/failure"); put(e, "إعادة تسمية", "Rename"); put(e, "حفظ الاسم", "Save name");
        put(e, "تعديل المهمة المحفوظة", "Edit saved task"); put(e, "حذف المهمة الذكية", "Delete smart task"); put(e, "حذف السكربت بالكامل", "Delete entire script");
        put(e, "سيتم حذف سجل الرسائل التفسيري فقط. تبقى مهام الوكيل وسكربتاته وسجلات التشخيص.", "Only explanatory messages will be deleted. Agent tasks, scripts, and diagnostic logs will remain.");
        put(e, "سيُحذف سجل الأخطاء وكل لقطات التشخيص المرتبطة به من مساحة التطبيق. لا يؤثر ذلك على السكربتات أو مفاتيح API.", "The error log and its diagnostic screenshots will be deleted from app storage. Scripts and API keys are unaffected.");
        put(e, "لا توجد لقطة شاشة محفوظة لهذا السجل. قد تكون اللقطات متوقفة أو لم يسمح أندرويد بالتقاطها.", "No screenshot was saved for this log. Screenshot logging may be off or Android may have blocked capture.");
        put(e, "لقطات الشاشة مع السجلات: مفعّلة", "Screenshots with logs: On"); put(e, "لقطات الشاشة مع السجلات: متوقفة", "Screenshots with logs: Off");
        put(e, "الزر B", "the B bubble"); put(e, "الخدمة مفعّلة", "Service enabled"); put(e, "الخدمة غير مفعّلة", "Service disabled");
        put(e, "اختبار الاتصال… لن تُنفّذ أي خطوة على الهاتف.", "Testing connection… no phone actions will be executed.");
        put(e, "GitHub Models: أدخل GitHub token صالحًا واسم نموذج متاحًا لحسابك. الحصة المجانية وحدود النماذج تتغير حسب شروط GitHub.", "GitHub Models: Enter a valid GitHub token and a model available to your account. Free quota and model limits depend on GitHub's terms.");
        put(e, "DeepSeek: رابط Chat Completions مستقل. النموذج الافتراضي deepseek-flash؛ استخدم زر النماذج لمعرفة المعرفات المتاحة لحسابك.", "DeepSeek uses its own Chat Completions URL. Use Models to fetch IDs available to your account.");
        put(e, "OpenRouter: كل النماذج تمر عبر هذا المزود، وقد تختلف الرؤية والسعر حسب النموذج. اختر نموذجًا يدعم الصور إذا أرفقت لقطة شاشة.", "OpenRouter routes requests through its provider; vision and pricing vary by model. Choose a vision-capable model when attaching screenshots.");
        put(e, "Groq API: استخدم مفتاح Groq ومعرف نموذج فعّال من القائمة المباشرة. ليست كل النماذج تدعم الصور.", "Groq API requires a valid Groq key and model ID. Not every model supports images.");
        put(e, "ملف متوافق قديم للحفاظ على الإعدادات السابقة. يفضّل اختيار DeepSeek أو OpenRouter أو Groq من الملفات المفصولة.", "Legacy-compatible profile for older settings. Prefer the separate DeepSeek, OpenRouter, or Groq profiles.");
        put(e, "أدخل رابط /chat/completions الكامل مع المفتاح ومعرّف النموذج. يدعم هذا غالبية واجهات OpenAI-compatible، وليس كل واجهات النماذج الخاصة.", "Enter the full /chat/completions URL, API key, and model ID. This supports many OpenAI-compatible APIs, but not every proprietary endpoint.");
        put(e, "لا يمكن استخدام Copilot Free بإدخال مفتاح API عادي. هذا الخيار يتطلب خدمة جسر خارجية تستخدم تكامل GitHub Copilot الرسمي وOAuth/SDK؛ لا تستخدم نقاط API غير موثقة.", "Copilot Free cannot be used with a normal API key. This option requires an external bridge using the official GitHub Copilot integration and OAuth/SDK; do not use undocumented endpoints.");
        put(e, "يتطلب مفتاح Anthropic API صالحًا ورصيد/حصة في حساب Anthropic. رابط Messages API قابل للتعديل.", "Requires a valid Anthropic API key and available account quota. The Messages API URL can be edited.");
        put(e, "أدخل مفتاح Gemini API. في هذا الوضع اكتب جذر API مثل https://generativelanguage.googleapis.com/v1beta واسم النموذج دون بادئة models/.", "Enter a Gemini API key. Use an API root such as https://generativelanguage.googleapis.com/v1beta and a model ID without the models/ prefix.");
        put(e, "يستخدم هذا الوضع OpenAI Responses API. مفتاح ChatGPT/Copilot لا يعادل تلقائيًا مفتاح API؛ استخدم اعتماد API رسميًا من المزوّد.", "This mode uses the OpenAI Responses API. ChatGPT/Copilot access is not automatically an API key; use official API credentials.");
        put(e, "تم اختيار الرابط. اضغط حفظ الإعدادات لاعتماده.", "Endpoint selected. Save settings to use it."); put(e, "تعذر عرض روابط API.", "Could not show API URLs.");
        put(e, "أدخل رابط API ومفتاح هذا المزود قبل جلب النماذج.", "Enter this provider's API URL and key before fetching models.");
        put(e, "جارٍ اختبار الاتصال… لن تُنفّذ أي خطوة على الهاتف.", "Testing connection… no phone actions will run.");
        put(e, "إعدادات الذكاء الاصطناعي", "AI settings"); put(e, "اختبار اتصال واحد الآن", "Test connection now");
        put(e, "إنشاء", "Create"); put(e, "اسم السكربت", "Script name"); put(e, "سكربت جديد", "New script"); put(e, "سكربت بدون اسم", "Untitled script");
        put(e, "اسم المهمة", "Task name"); put(e, "مهمة ذكية", "Smart task"); put(e, "وصف الهدف النهائي للوكيل", "Describe the agent's final goal");
        put(e, "اسم المطوّر", "Developer"); put(e, "يحيى النباهين", "Yahya Al-Nabahin");
        put(e, "اسأل عن التطبيق أو إحدى ميزاته.", "Ask about the app or one of its features.");
        put(e, "ستظهر الإجابة هنا دون استهلاك حصة API للأسئلة الشائعة داخل التطبيق.", "Answers to common in-app questions appear here without using your AI API quota.");
        put(e, "أكد فهمك لاستخدام لقطات الشاشة والتحكم بالواجهة أولًا.", "Confirm that you understand screenshot use and interface control first.");
        put(e, "اضبط مزود API ومفتاحًا صالحًا قبل تشغيل الوكيل. اختبار سرعة التنزيل المحلي لا يحتاج مفتاحًا.", "Configure an API provider and a valid key before running the agent. Local download speed tests do not need a key.");
        put(e, "اضبط مزود API ومفتاحًا واحدًا على الأقل قبل بدء الوكيل الذكي. اختبار سرعة التنزيل المحلي لا يحتاج مفتاحًا.", "Configure an API provider and at least one key before starting the smart agent. Local download speed tests do not need a key.");
        put(e, "حُفظت المهمة. فعّل خدمة Botna من إعدادات إمكانية الوصول، وستبدأ بعد تفعيلها.", "Task saved. Enable Botna in Accessibility Settings and it will start after the service is enabled.");
        put(e, "أُرسلت المهمة إلى الوكيل. ستُحفظ خطواته ورسائله ويمكن مراجعتها لاحقًا.", "Task sent to the agent. Its steps and messages will be saved for later review.");
        put(e, "تم إرسال السكربت للتشغيل.", "Script sent to run."); put(e, "أُرسل السكربت المحفوظ للتشغيل.", "Saved script sent to run.");
        put(e, "افتح محرر بوتنا لتعديل إجراءات الوكيل بالتفصيل.", "Open the Botna editor to modify the agent's actions in detail.");
        put(e, "اكتب اسمًا غير فارغ ثم أعد المحاولة.", "Enter a non-empty name and try again."); put(e, "اكتب سؤالك أولًا.", "Enter your question first."); put(e, "اكتب وصف المهمة أولًا.", "Enter a task description first.");
        put(e, "تعذر فتح رابط Telegram.", "Could not open the Telegram link."); put(e, "تعذر فتح إعدادات إمكانية الوصول", "Could not open Accessibility Settings.");
        put(e, "بانتظار تفعيل الخدمة", "Waiting for service activation"); put(e, "فعّل خدمة إمكانية الوصول لبدء المهمة.", "Enable Accessibility to start the task.");
        put(e, "حذف السجلات", "Delete logs"); put(e, "حذف السكربت", "Delete script"); put(e, "حذف المهمة", "Delete task"); put(e, "حذف هذا السجل", "Delete this log");
        put(e, "حذف هذا السجل؟", "Delete this log?"); put(e, "حفظ السكربتات والخروج", "Save scripts and exit"); put(e, "حفظ وتشغيل", "Save and run");
        put(e, "خصوصيتك والتحكم بيدك", "Your privacy and control"); put(e, "لغة التطبيق", "App language"); put(e, "محفوظة", "Saved"); put(e, "مزود", "Provider"); put(e, "مفتاح", "Key");
        put(e, "مهمة محذوفة/غير معروفة", "Deleted or unknown task"); put(e, "وكيل", "Agent"); put(e, "رسالة", "Message");
        put(e, "لا يحتوي السكربت على إجراءات محفوظة بعد. أعد تشغيل الوكيل أولًا.", "This script has no saved actions yet. Run the agent first.");
        put(e, "لا توجد تفاصيل إضافية.", "No additional details."); put(e, "لا يوجد رد مفهوم.", "No understandable response was returned.");
        put(e, "المطوّر: يحيى النباهين • Telegram: @yahea21", "Developer: Yahya Al-Nabahin • Telegram: @yahea21");
        put(e, "المطوّر: يحيى النباهين  •  Telegram: @yahea21", "Developer: Yahya Al-Nabahin • Telegram: @yahea21");
        put(e, "تأخير عشوائي", "Random delay"); put(e, "انتظار صورة", "Wait for image"); put(e, "تنبيه مع نغمة", "Alert with sound");
        put(e, "سحب الشاشة", "Swipe screen"); put(e, "اختبار خطوة محددة", "Test selected step"); put(e, "تنبيه مع نغمة", "Alert with sound");
        put(e, "تحديث السجل", "Refresh log"); put(e, "إظهار سجل الوكيل", "Show agent log"); put(e, "أدخل وصف الهدف النهائي للوكيل", "Describe the agent's final goal");
        put(e, "إخفاء القائمة والعودة إلى الزر العائم", "Hide the panel and return to the floating bubble");
        put(e, "حفظ السكربت وإغلاق لوحة الأتمتة", "Save script and close the automation panel");
        put(e, "إغلاق لوحة الأتمتة بعد تأكيد الحفظ", "Close the automation panel after confirming save");
        put(e, "البحث عن أمر أو خطوة لإضافتها إلى السكربت", "Search for a command or step to add to the script");
        put(e, "مثال: تابع صورة رقم 4 بالمكتبة، إذا وجدتها انتظر ثانيتين ثم ارجع", "Example: watch for image 4 in the library, wait two seconds if found, then go back");
        put(e, "إيقاف/استكمال", "Pause/Resume"); put(e, "إيقاف", "Stop"); put(e, "بدء", "Start");
        put(e, "قراءة عناصر الشاشة", "Read screen elements"); put(e, "إعدادات AI", "AI settings"); put(e, "إعدادات مترجم الذكاء الاصطناعي", "AI compiler settings");
        put(e, "سكربت جديد", "New script"); put(e, "＋ سكربت جديد", "＋ New script"); put(e, "تأكيد", "Confirm"); put(e, "فتح", "Open"); put(e, "إضافة", "Add");
        put(e, "أنشئ السكربتات من الفقاعة B العائمة، واضبط مكتبة الصور، وشغّل الأوامر مع إمكانية الإيقاف والاستكمال.", "Create scripts from the floating B bubble, manage image templates, and run commands with pause/resume controls.");
        put(e, "إضافة/تعديل الخطوات", "Add/edit steps"); put(e, "إعادة الوكيل", "Restart agent"); put(e, "إعادة تسمية", "Rename"); put(e, "تعديل المهمة", "Edit task"); put(e, "إمكانية الوصول", "Accessibility");
        put(e, "المطوّر: يحيى النباهين  •  Telegram: @yahea21", "Developer: يحيى النباهين • Telegram: @yahea21");
        put(e, "جارٍ فحص الحالة...", "Checking service status…"); put(e, "قائمة السكربتات المحفوظة", "Saved scripts");
        put(e, "إنشاء بوت ذكي للمهام المعقدة", "Create smart agent task"); put(e, "تعديل المهمة المحفوظة", "Edit saved task"); put(e, "تشغيل السكربت المحفوظ", "Run saved script");
        put(e, "حذف رسائل الوكيل", "Delete agent messages"); put(e, "حذف هذا السجل", "Delete this log"); put(e, "حفظ وتشغيل", "Save and run");
        put(e, "سيتم حذف تفاصيله ولقطة الشاشة المرتبطة به.", "Its details and attached screenshot will be deleted.");
        put(e, "سيُحذف «", "Delete “"); put(e, "لا توجد سجلات حتى الآن. عند فشل ترجمة أو خطوة ستظهر هنا تفاصيل السبب ولقطة الشاشة إذا كانت مفعّلة.", "No logs yet. Translation or step failures will appear here with the reason and a screenshot if enabled.");
        put(e, "لا توجد سكربتات محفوظة. أنشئ سكربتًا جديدًا ثم أضف خطواته من محرر Botna.", "No saved scripts. Create a new script, then add steps in the Botna editor.");
        put(e, "لا توجد لقطة شاشة محفوظة لهذا السجل. قد تكون اللقطات متوقفة أو لم يسمح أندرويد بالتقاطها.", "No screenshot was saved for this log. Screenshot logging may be off or Android may have blocked capture.");
        put(e, "لا توجد مهام محفوظة بعد. أنشئ مهمة من الزر أعلاه.", "No saved tasks yet. Create one using the button above.");
        put(e, "لا توجد رسائل بعد.", "No messages yet."); put(e, "لم يتم تنفيذ أي إجراء بعد.", "No action has been executed yet.");
        put(e, "لقطات الشاشة مع السجلات: مفعّلة", "Screenshots with logs: On"); put(e, "لقطات الشاشة مع السجلات: متوقفة", "Screenshots with logs: Off");
        put(e, "أنشئ، راجع، ثم شغّل", "Create, review, then run"); put(e, "مساعد استخدام بوتنا", "Botna help assistant");
        put(e, "BOTNA  •  الأتمتة الذكية", "BOTNA • Smart automation"); put(e, "اسم السكربت الجديد", "New script name"); put(e, "اسم الصورة الجديدة", "New image name");
        put(e, "الفاصل بالمللي ثانية", "Interval in milliseconds"); put(e, "الفاصل بين المحاولات (ms)", "Retry interval (ms)"); put(e, "فاصل إعادة المحاولة بالمللي ثانية", "Retry delay in milliseconds");
        put(e, "أدنى وأقصى انتظار بالمللي ثانية (min,max)", "Minimum and maximum wait in milliseconds (min,max)"); put(e, "أدنى وأقصى مدة (min,max)", "Minimum and maximum duration (min,max)");
        put(e, "اسم التطبيق أو اسم الحزمة", "App name or package name"); put(e, "اسم التطبيق المثبت الذي تريد فتحه", "Installed app to open"); put(e, "اسم الصورة", "Image name");
        put(e, "رابط HTTP/HTTPS", "HTTP/HTTPS URL"); put(e, "رابط HTTP/HTTPS (مثل صفحة الراوتر)", "HTTP/HTTPS URL (e.g. router page)");
        put(e, "رابط اختبار HTTP/HTTPS؛ اتركه فارغًا لاستخدام فحص اتصال الإنترنت الافتراضي", "HTTP/HTTPS test URL; leave empty to use the default connectivity test");
        put(e, "دقة المطابقة (0–100%)", "Match threshold (0–100%)"); put(e, "نسبة التطابق (0–100)", "Match threshold (0–100)");
        put(e, "عدد التكرارات (أي عدد صحيح موجب)", "Repeat count (any positive integer)"); put(e, "عدد المحاولات (أي عدد صحيح موجب)", "Attempts (any positive integer)");
        put(e, "عدد المحاولات لهذه الزاوية (أي عدد صحيح موجب)", "Attempts for this angle (any positive integer)"); put(e, "عدد مرات تكرار المجموعة (أي عدد صحيح موجب)", "Group repetitions (any positive integer)");
        put(e, "مدة الانتظار (ms)", "Wait duration (ms)"); put(e, "مدة الانتظار بالمللي ثانية", "Wait duration in milliseconds"); put(e, "مدة التمرير (ms)", "Scroll duration (ms)"); put(e, "مدة السحب (ms)", "Swipe duration (ms)");
        put(e, "مدة الضغط المطوّل (ms)", "Long-press duration (ms)"); put(e, "مدة الحركة بالمللي ثانية", "Gesture duration in milliseconds");
        put(e, "النص الذي سيُكتب", "Text to type"); put(e, "النص الذي سيُكتب في الحقل المركّز", "Text to type into the focused field"); put(e, "النص الذي يجب أن يظهر على الشاشة", "Text expected on screen");
        put(e, "النص المطلوب النقر عليه", "Text to tap"); put(e, "النص المنتظر", "Expected text"); put(e, "نص الرسالة التي ستظهر عند الوصول لهذه الخطوة", "Message to show when this step is reached");
        put(e, "إرفاق لقطة الشاشة الحالية للمترجم البصري", "Attach current screenshot for visual analysis"); put(e, "الحد الأقصى للمحاولات والفاصل بالمللي ثانية", "Maximum attempts and interval in milliseconds");
        put(e, "ملاحظات الترجمة", "Translation notes"); put(e, "كل الأوامر المتاحة", "All available commands"); put(e, "لا توجد أوامر مترجمة قابلة للتنفيذ. النص الأصلي محفوظ ويمكن إعادة تحليله.", "No executable translated commands. The original text is saved and can be analyzed again.");
        put(e, "لا توجد خطوات بعد. أضف خطوة من الأزرار أعلاه.", "No steps yet. Add a step using the buttons above.");
        put(e, "لا توجد زوايا محفوظة في هذه الخطوة. أضف زاوية أو قص هدفًا مستقلًا.", "No saved angles for this step. Add an angle or crop a separate target.");
        put(e, "هل تريد حذف هذه الخطوة نهائيًا؟", "Delete this step permanently?"); put(e, "هل تريد حذف الزاوية ", "Delete angle ");
        put(e, "سيتم حفظ التعديلات ثم إغلاق لوحة الأتمتة والزر العائم B معًا. هل تريد المتابعة؟", "Changes will be saved, then the automation panel and floating B bubble will close. Continue?");
        put(e, "سيُحفظ النص الأصلي أولًا حتى لو فشل التحليل. راجع معاينة الخطوات قبل التشغيل.", "The original text is saved even if analysis fails. Review the step preview before running.");
        put(e, "نوع خطوة غير معروف: ", "Unknown step type: "); put(e, "فشل الإجراء ", "Action failed "); put(e, "نجح الإجراء ", "Action succeeded ");
        put(e, "فشل إجراء داخل البوت الذكي.", "A smart-agent action failed."); put(e, "فشل طلب الذكاء الاصطناعي؛ ستجري محاولة بالمحلل المحلي.", "AI request failed; the local parser will be tried.");
        put(e, "تعذر فتح نافذة التعديل", "Could not open the edit window"); put(e, "تعذر فتح قائمة الخيارات", "Could not open the options list");
        put(e, "تم إنهاء طلب النماذج للمزود السابق؛ اختره مرة أخرى لعرض النتيجة.", "The previous provider's model request was cancelled; select it again to see the result.");
        put(e, "إضافة خطوة", "Add step"); put(e, "اسم التطبيق", "App name"); put(e, "البوت الذكي يحلل الشاشة…", "Smart agent is analyzing the screen…");
        put(e, "الخطوات — اضغط للتعديل، واضغط مطولًا للنسخ/القص/التحريك/اللصق", "Steps — tap to edit; long-press to copy, cut, move, or paste");
        put(e, "النص الأصلي: ", "Original text: "); put(e, "النوع: ", "Type: ");
        put(e, "انتظار بعد التنفيذ (0–60000 ms)", "Wait after action (0–60000 ms)"); put(e, "انتظار بعد التنفيذ (ms)", "Wait after action (ms)");
        put(e, "انتظار بعد التنفيذ (مللي ثانية)", "Wait after action (milliseconds)"); put(e, "انتظار بعد المطابقة بالمللي ثانية", "Wait after match in milliseconds");
        put(e, "انتظار قبل التنفيذ (0–60000 ms)", "Wait before action (0–60000 ms)"); put(e, "انتظار قبل التنفيذ (ms)", "Wait before action (ms)");
        put(e, "انتظار قبل التنفيذ (مللي ثانية)", "Wait before action (milliseconds)"); put(e, "انتظار قبل الخطوة (مللي ثانية)", "Wait before step (milliseconds)");
        put(e, "انتظار قبل المطابقة بالمللي ثانية", "Wait before match in milliseconds"); put(e, "انتظار قبل تنفيذ الخطوة بالمللي ثانية", "Wait before executing step in milliseconds");
        put(e, "تبديل السكربت", "Switch script"); put(e, "تعديل النص المستهدف", "Edit target text"); put(e, "حذف الزاوية", "Delete angle"); put(e, "حذف صورة المكتبة", "Delete library image");
        put(e, "رسالة التنبيه", "Alert message"); put(e, "رقم الخطوة للاختبار", "Step number to test"); put(e, "زوايا الصورة وإعدادات كل زاوية", "Image angles and per-angle settings");
        put(e, "عدد المحاولات والفاصل بالمللي ثانية (مثال 3,5000)", "Attempts and interval in milliseconds (e.g. 3,5000)");
        put(e, "عدد محاولات المطابقة (أي عدد صحيح موجب)", "Match attempts (any positive integer)");
        put(e, "عند التفعيل، تُرسل لقطة الشاشة إلى مزوّد الذكاء الاصطناعي عبر الإنترنت. أوقفه في الشاشات الحساسة.", "When enabled, the screenshot is sent to your AI provider online. Disable this for sensitive screens.");
        put(e, "فاصل إعادة المحاولة لهذه الزاوية (ms)", "Retry interval for this angle (ms)"); put(e, "فاصل المحاولة بالمللي ثانية", "Retry interval in milliseconds");
        put(e, "مدة الضغط بالمللي ثانية", "Press duration in milliseconds"); put(e, "موقع الضغط المطوّل x,y كنسب مئوية", "Long-press position x,y as screen percentages");
        put(e, "نسبة التطابق لهذه الزاوية (0–100)", "Match threshold for this angle (0–100)"); put(e, "نقر موقع", "Tap coordinate");
        put(e, "إنشاء سكربت محفوظ", "Create saved script"); put(e, "سيُحذف تعريف المهمة وسكربتها المحفوظة. تبقى رسائل الوكيل وسجلات التشخيص كي تتمكن من مراجعة ما حدث.", "The task definition and its saved script will be deleted. Agent messages and diagnostic logs will remain for review.");
        put(e, "مهام الوكيل الذكي المحفوظة", "Saved smart-agent tasks"); put(e, "▶ تشغيل", "▶ Run"); put(e, "＋ إنشاء مهمة ذكية جديدة", "＋ Create new smart task");
        put(e, "إنشاء مهمة ذكية جديدة", "Create new smart task");
        put(e, "x,y كنسب مئوية من الشاشة", "x,y as percentages of the screen");
        put(e, "x1,y1,x2,y2 كنسب مئوية", "x1,y1,x2,y2 as screen percentages");
        put(e, "x1,y1,x2,y2 — نسب من 0 إلى 100", "x1,y1,x2,y2 — values from 0 to 100");
        put(e, "الفاصل بين المحاولات بالمللي ثانية", "Retry interval in milliseconds");
        put(e, "الإحداثيات النسبية x,y من 0 إلى 100", "Relative coordinates x,y from 0 to 100");
        put(e, "السحب: x1,y1,x2,y2 كنسب مئوية", "Swipe: x1,y1,x2,y2 as percentages");
        put(e, "سيتم حذف قالب الصورة من المكتبة. الخطوات المخصصة التي تستخدمها ستعرض تحذيرًا إذا أصبحت الصورة غير متاحة. متابعة؟", "This image template will be removed from the library. Custom steps that use it will warn if the image becomes unavailable. Continue?");
        put(e, "اكتب الهدف النهائي أو السؤال كما تريده، وليس شرطًا أن يكون أمرًا مباشرًا: مثل «اختبر سرعة الإنترنت وأخبرني بالنتيجة» أو «افتح الرابط ولخّص ما بداخله».", "Describe your goal or question naturally, not necessarily as a direct command. Example: ‘test internet speed and tell me the result’ or ‘open this URL and summarize its contents’.");
        put(e, "سيُعاد تحليل الشاشة الحالية والتحكم بواجهة الهاتف لتحقيق الهدف المحفوظ. قد يختلف المسار عن التشغيل السابق. راجع أي عملية حساسة يدويًا. متابعة؟", "The current screen will be re-analyzed to achieve the saved goal. The path may differ from the previous run. Review sensitive actions manually. Continue?");
        put(e, "سيُحذف جميع مفاتيح هذا المزوّد المشفّرة فقط، ولن تتأثر مفاتيح المزوّدين الآخرين. لا يمكن التراجع عن الحذف. متابعة؟", "Only this provider's encrypted keys will be deleted; keys for other providers are unaffected. This cannot be undone. Continue?");
        ENGLISH = Collections.unmodifiableMap(e);

        Map<String, String> icons = new HashMap<>();
        icons.put("السكربتات المحفوظة", "📚"); icons.put("قائمة السكربتات المحفوظة", "📚"); icons.put("فتح قائمة السكربتات المحفوظة", "📚");
        icons.put("قائمة السجلات", "🧾"); icons.put("سجلات بوتنا التشخيصية", "🧾"); icons.put("رسائل الوكيل", "💬"); icons.put("مهام الوكيل المحفوظة", "🤖");
        icons.put("إعدادات الذكاء الاصطناعي", "🧠"); icons.put("دليل الأوامر المخصصة", "📖"); icons.put("اللغة", "🌐"); icons.put("تواصل عبر Telegram", "✉️");
        icons.put("انتظار", "⏳"); icons.put("انتظار ظهور نص", "👁️"); icons.put("فحص الإنترنت", "🔬"); icons.put("اختبار آخر خطوة", "🧪");
        icons.put("كتابة نص", "⌨️"); icons.put("نقر نص", "👆"); icons.put("نقر موقع", "🎯"); icons.put("سحب الشاشة", "↔️"); icons.put("فتح تطبيق", "📱"); icons.put("فتح رابط", "🔗");
        icons.put("حفظ", "💾"); icons.put("حفظ وخروج", "💾"); icons.put("إغلاق", "✕"); icons.put("إلغاء", "↩️"); icons.put("حذف", "🗑️"); icons.put("بدء المهمة", "▶️");
        icons.put("مكتبة الصور", "🖼️"); icons.put("تغيير الإعدادات", "⚙️"); icons.put("بحث عن أمر لإضافته", "🔎"); icons.put("تحديث الحالة", "🔄");
        ICONS = Collections.unmodifiableMap(icons);
        Map<String, String> headingIcons = new HashMap<>();
        headingIcons.put("حالة خدمة الأتمتة", "🔌"); headingIcons.put("مركز التحكم", "🎛️");
        headingIcons.put("السكربتات المحفوظة", "📚"); headingIcons.put("بياناتك المحلية", "🗄️");
        headingIcons.put("آخر نتيجة", "✅"); headingIcons.put("اسم المطوّر", "👨‍💻");
        headingIcons.put("مساعد استخدام بوتنا", "💡"); headingIcons.put("مرحبًا بك في بوتنا", "🤖");
        headingIcons.put("مهام الوكيل الذكي المحفوظة", "🤖"); headingIcons.put("رسائل الوكيل", "💬");
        headingIcons.put("سجلات بوتنا التشخيصية", "🧾"); headingIcons.put("BOTNA  •  الأتمتة الذكية", "🤖");
        HEADING_ICONS = Collections.unmodifiableMap(headingIcons);
    }

    private BotnaI18n() { }
    private static void put(Map<String, String> map, String ar, String en) { map.put(ar, en); }

    public static boolean isEnglish(Context context) {

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                android.app.LocaleManager manager = context.getSystemService(android.app.LocaleManager.class);
                if (manager != null) {
                    String tags = manager.getApplicationLocales().toLanguageTags();
                    if (tags != null && !tags.trim().isEmpty()) return tags.toLowerCase(java.util.Locale.ROOT).startsWith("en");
                }
            } catch (Exception ignored) { }
        }
        String saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, "");
        if (saved != null && !saved.trim().isEmpty()) return "en".equalsIgnoreCase(saved);
        try {
            String language = context.getResources().getConfiguration().getLocales().get(0).getLanguage();
            if (language != null && !language.isEmpty()) return "en".equalsIgnoreCase(language);
        } catch (Exception ignored) { }
        return "en".equalsIgnoreCase(java.util.Locale.getDefault().getLanguage());
    }

    public static String language(Context context) { return isEnglish(context) ? "en" : "ar"; }

    public static void setLanguage(Context context, String language) {
        String selected = "en".equalsIgnoreCase(language) ? "en" : "ar";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, selected).apply();

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            try {
                android.app.LocaleManager manager = context.getSystemService(android.app.LocaleManager.class);
                if (manager != null) manager.setApplicationLocales(android.os.LocaleList.forLanguageTags(selected));
            } catch (Exception ignored) { }
        }
    }

    public static String translate(Context context, String value) {
        if (value == null || value.isEmpty() || !isEnglish(context)) return value;
        String mapped = ENGLISH.get(value);
        if (mapped != null) return mapped;
        if (value.startsWith("الحالة: ")) return "Status: " + value.substring("الحالة: ".length());
        if (value.startsWith("الخطوات: ")) return "Steps: " + value.substring("الخطوات: ".length());
        if (value.startsWith("الوقت: ")) return "Time: " + value.substring("الوقت: ".length());
        if (value.startsWith("النوع: ")) return "Type: " + value.substring("النوع: ".length());
        if (value.startsWith("الهدف: ")) return "Goal: " + value.substring("الهدف: ".length());
        if (value.startsWith("المزوّد المحدد: ")) return "Selected provider: " + value.substring("المزوّد المحدد: ".length());
        if (value.startsWith("عدد السكربتات: ")) return "Scripts: " + value.substring("عدد السكربتات: ".length());
        if (value.startsWith("السكربت الحالي: ")) return "Current script: " + value.substring("السكربت الحالي: ".length());
        if (value.startsWith("السكربت الجاري: ")) return "Running script: " + value.substring("السكربت الجاري: ".length());
        if (value.startsWith("النص الأصلي: ")) return "Original text: " + value.substring("النص الأصلي: ".length());
        if (value.startsWith("إجراء النظام: ")) return "System action: " + value.substring("إجراء النظام: ".length());
        if (value.startsWith("فتح تطبيق: ")) return "Open app: " + value.substring("فتح تطبيق: ".length());
        if (value.startsWith("فتح رابط: ")) return "Open URL: " + value.substring("فتح رابط: ".length());
        if (value.startsWith("كتابة نص: ")) return "Type text: " + value.substring("كتابة نص: ".length());
        return value;
    }

    public static String buttonText(Context context, String value) {
        if (value == null || value.isEmpty()) return value;
        String icon = ICONS.get(value);
        String translated = translate(context, value);
        if (value.startsWith("＋") || value.startsWith("▶") || value.startsWith("✓")) {
            if (!translated.equals(value)) return translated;
            return value.substring(0, 1) + " " + translate(context, value.substring(1).trim());
        }
        if (value.startsWith("Ⅱ") || value.startsWith("■") || value.startsWith("⌕") || value.startsWith("⚙")) {
            String symbol = value.substring(0, 1);
            String rest = value.substring(1).trim();
            return symbol + " " + translate(context, rest);
        }
        if (value.startsWith("•")) return translate(context, value);
        if (value.startsWith("🌐") || value.equals("×") || value.equals("⌕") || value.equals("Ⅱ") || value.equals("■")
                || value.matches("(?s).*\\d{4}-\\d{2}-\\d{2}.*") || value.length() > 36) return translated;
        if (icon == null && value.length() <= 24 && !value.contains("\\n")) icon = "✧";
        return icon == null ? translated : icon + "  " + translated;
    }

    public static String headingText(Context context, String value, boolean bold) {
        String translated = translate(context, value);
        if (!bold || value == null || value.isEmpty() || value.startsWith("🤖") || value.startsWith("📚")) return translated;
        String icon = HEADING_ICONS.get(value);
        return icon == null ? translated : icon + "  " + translated;
    }

    public static void applyDirection(Context context, View view) {
        if (view != null) view.setLayoutDirection(isEnglish(context) ? View.LAYOUT_DIRECTION_LTR : View.LAYOUT_DIRECTION_RTL);
    }
}
