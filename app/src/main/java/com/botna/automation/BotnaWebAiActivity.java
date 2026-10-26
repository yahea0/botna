package com.botna.automation;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.util.Base64;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Locale;

public final class BotnaWebAiActivity extends Activity {
    public static final String ACTION_RESPONSE = "com.botna.automation.CUSTOM_WEB_AI_RESPONSE";
    public static final String ACTION_FAILED = "com.botna.automation.CUSTOM_WEB_AI_FAILED";
    public static final String EXTRA_TASK_ID = "custom_web_ai_task_id";
    public static final String EXTRA_MODEL_PACKAGE = "custom_web_ai_model_package";
    public static final String EXTRA_URL = "custom_web_ai_url";
    public static final String EXTRA_PROMPT = "custom_web_ai_prompt";
    public static final String EXTRA_ATTACHMENTS = "custom_web_ai_attachments";
    public static final String EXTRA_IMAGE_WIDTH = "custom_web_ai_image_width";
    public static final String EXTRA_IMAGE_HEIGHT = "custom_web_ai_image_height";
    public static final String EXTRA_TEXT = "custom_web_ai_text";
    private static final int MAX_ATTACHMENT_BYTES = 12 * 1024 * 1024;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private TextView statusView;
    private String taskId = "";
    private String modelPackage = BotnaExternalAppAgent.CUSTOM_WEB_AI;
    private String url = "";
    private String prompt = "";
    private String baselineText = "";
    private ArrayList<String> attachmentPaths = new ArrayList<>();
    private boolean pageLoaded;
    private boolean attachmentInjectionStarted;
    private boolean attachmentsInjected;
    private boolean promptInjected;
    private boolean sendStarted;
    private boolean resultSent;
    private boolean pageError;
    private int pagePollAttempts;
    private int attachAttempts;
    private int promptAttempts;
    private int sendReadyWaitAttempts;
    private int sendClickAttempts;
    private int responseStablePolls;
    private int lastProbeHash;
    private String lastResponseCandidate = "";
    private int imageWidth;
    private int imageHeight;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        taskId = safe(getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_TASK_ID));
        String requestedModel = safe(getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_MODEL_PACKAGE));
        modelPackage = BotnaExternalAppAgent.usesWebAiActivity(requestedModel) ? requestedModel : BotnaExternalAppAgent.CUSTOM_WEB_AI;
        url = safe(getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_URL));
        prompt = safe(getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_PROMPT));
        ArrayList<String> paths = getIntent() == null ? null : getIntent().getStringArrayListExtra(EXTRA_ATTACHMENTS);
        if (paths != null) attachmentPaths.addAll(paths);
        imageWidth = getIntent() == null ? 0 : getIntent().getIntExtra(EXTRA_IMAGE_WIDTH, 0);
        imageHeight = getIntent() == null ? 0 : getIntent().getIntExtra(EXTRA_IMAGE_HEIGHT, 0);
        if (!BotnaExternalAppAgent.isValidCustomWebAiUrl(url) || taskId.isEmpty() || prompt.isEmpty()) {
            sendFailure("رابط موقع الذكاء الاصطناعي أو تعليمات المهمة غير صالحة.");
            finish();
            return;
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String finishedUrl) {
                super.onPageFinished(view, finishedUrl);
                pageLoaded = true;
                if (statusView != null) statusView.setText("Botna: ينتظر ظهور محرر الرسالة في الموقع");
                handler.removeCallbacks(workflowRunnable);
                handler.postDelayed(workflowRunnable, 900L);
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame() && !resultSent) {
                    pageError = true;
                    handler.postDelayed(() -> {
                        if (pageError && !resultSent) sendFailure("تعذر تحميل موقع الذكاء الاصطناعي. تحقق من الرابط والاتصال بالإنترنت.");
                    }, 1800L);
                }
            }
        });
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        statusView = new TextView(this);
        statusView.setText("Botna: جارٍ فتح موقع الذكاء الاصطناعي");
        statusView.setTextColor(Color.WHITE);
        statusView.setTextSize(12);
        statusView.setPadding(dp(12), dp(8), dp(12), dp(8));
        statusView.setBackgroundColor(0xD91A1E2C);
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(statusView, statusLp);
        setContentView(root);
        webView.loadUrl(url);
        handler.postDelayed(() -> {
            if (!resultSent && !promptInjected && pagePollAttempts > 100) sendFailure("لم يظهر حقل إدخال الرسالة في الموقع خلال المهلة. سجّل الدخول أو افتح محادثة جديدة ثم أعد المهمة.");
        }, 180000L);
    }

    private final Runnable workflowRunnable = new Runnable() {
        @Override public void run() {
            if (resultSent || webView == null || isFinishing()) return;
            if (!pageLoaded) {
                handler.postDelayed(this, 800L);
                return;
            }
            pagePollAttempts++;
            probePage(probe -> {
                if (resultSent) return;
                if (probe == null) {
                    handler.postDelayed(this, 900L);
                    return;
                }
                String body = probe.optString("body", "");
                boolean composerReady = probe.optBoolean("composer", false);
                boolean uploading = probe.optBoolean("uploading", false);
                boolean generating = probe.optBoolean("generating", false);
                if (!promptInjected) {
                    if (!composerReady) {
                        updateStatus("Botna: ينتظر ظهور حقل الإدخال أو اكتمال تسجيل الدخول");
                        if (pagePollAttempts >= 180) sendFailure("لم يعثر Botna على حقل كتابة صالح في الموقع بعد ثلاث دقائق. قد يحتاج الموقع إلى تسجيل الدخول أو فتح محادثة.");
                        else handler.postDelayed(this, 1000L);
                        return;
                    }
                    if (!attachmentPaths.isEmpty() && !attachmentsInjected) {
                        if (!attachmentInjectionStarted || probe.optInt("fileInputs", 0) > 0) {
                            baselineText = body;
                            attachmentInjectionStarted = true;
                            injectAttachments(probe);
                            return;
                        }
                        if (attachAttempts >= 12) {
                            sendFailure("لم يتمكن Botna من العثور على حقل رفع الملفات في الموقع؛ لم يرسل التعليمات دون المرفقات.");
                            return;
                        }
                        attachAttempts++;
                        clickAttachmentControl();
                        handler.postDelayed(this, 900L);
                        return;
                    }
                    baselineText = body;
                    injectPrompt();
                    return;
                }
                if (!sendStarted) {
                    handler.postDelayed(this, 700L);
                    return;
                }
                String candidate = probe.optString("response", "").trim();
                if (candidate.isEmpty()) candidate = extractDelta(body, baselineText, prompt);
                String lower = body.toLowerCase(Locale.ROOT);
                if (lower.contains("uploading") || lower.contains("جارٍ الرفع") || lower.contains("جاري الرفع")) uploading = true;
                boolean actionLike = candidate.contains("\"action\"") || candidate.contains("\"type\"")
                        || candidate.contains("\"DONE\"") || candidate.contains("\"action\":")
                        || candidate.toUpperCase(Locale.ROOT).contains("\"ACTION\"");
                int hash = candidate.hashCode();
                if (!candidate.isEmpty() && !candidate.equals(lastResponseCandidate)) {
                    lastResponseCandidate = candidate;
                    responseStablePolls = 0;
                } else if (!candidate.isEmpty()) {
                    responseStablePolls++;
                }
                lastProbeHash = hash;
                if (actionLike && !uploading && !generating && responseStablePolls >= 2) {
                    sendResponse(candidate);
                    return;
                }
                updateStatus(generating ? "Botna: ينتظر اكتمال رد النموذج" : "Botna: ينتظر أمر JSON قابلًا للتنفيذ من الرد");
                if (pagePollAttempts >= 240) {
                    sendFailure("لم يُرجع الموقع أمر JSON قابلًا للتنفيذ خلال أربع دقائق. لم ينفذ Botna أي إجراء غير مؤكد.");
                    return;
                }
                handler.postDelayed(this, responseStablePolls > 0 ? 1200L : 2000L);
            });
        }
    };

    private interface ProbeCallback { void done(JSONObject result); }

    private void probePage(ProbeCallback callback) {
        String script = "(function(){try{" +
                "const vis=e=>{if(!e)return false;const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>90&&r.height>12&&r.bottom>0&&r.top<innerHeight&&s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity||1)>0.02};" +
                "const ed=[...document.querySelectorAll('textarea,input:not([type=file]):not([type=password]),[contenteditable=true],[role=textbox]')].filter(vis).filter(e=>{const t=((e.getAttribute('placeholder')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('name')||'')+' '+(e.getAttribute('type')||'')).toLowerCase();return !/(search|بحث|email|e-mail|password|كلمة المرور|username|اسم المستخدم)/.test(t)});" +
                "const score=e=>{const r=e.getBoundingClientRect(),t=((e.getAttribute('placeholder')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('name')||'')).toLowerCase();return r.bottom*2+r.width*0.05+(document.activeElement===e?600:0)+(/message|prompt|ask|اسأل|رسالة|اكتب/.test(t)?900:0)-(/search|بحث|email|password/.test(t)?4000:0)};ed.sort((a,b)=>score(a)-score(b));" +
                "const fs=[...document.querySelectorAll('input[type=file]')];const txt=(document.body&&document.body.innerText)||'';" +
                "const btns=[...document.querySelectorAll('button,[role=button]')].filter(vis);const labels=btns.map(e=>((e.innerText||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')).trim()).join('\\n');" +
                "const assistants=[...document.querySelectorAll('[data-message-author-role=assistant],[data-author=assistant],[data-testid*=assistant],article,[role=article],.prose,.markdown')].filter(vis).map(e=>(e.innerText||e.textContent||'').trim()).filter(t=>t.length>10&&!t.includes(" + JSONObject.quote(prompt.substring(0, Math.min(70, prompt.length))) + "));" +
                "const stop=/(stop generating|stop response|cancel response|إيقاف التوليد|إيقاف الرد|generating|جارٍ التفكير|جاري التفكير)/i.test(labels)||!!document.querySelector('[aria-busy=true],progress,[role=progressbar]');" +
                "return JSON.stringify({composer:ed.length>0,fileInputs:fs.length,body:txt.slice(-28000),response:assistants.length?assistants[assistants.length-1].slice(-16000):'',uploading:/(uploading|processing attachment|جارٍ الرفع|جاري الرفع|تحميل الصورة)/i.test(txt),generating:stop,buttons:labels.slice(-3000)});" +
                "}catch(e){return JSON.stringify({error:String(e)})}})()";
        webView.evaluateJavascript(script, value -> {
            JSONObject parsed = null;
            try {
                Object decoded = new JSONTokener(value == null ? "null" : value).nextValue();
                if (decoded instanceof String) parsed = new JSONObject((String) decoded);
                else if (decoded instanceof JSONObject) parsed = (JSONObject) decoded;
            } catch (Exception ignored) { }
            callback.done(parsed);
        });
    }

    private void injectAttachments(JSONObject probe) {
        if (resultSent || attachmentsInjected) return;
        JSONArray files = new JSONArray();
        int totalBytes = 0;
        try {
            for (String path : attachmentPaths) {
                File file = new File(path);
                if (!file.isFile() || file.length() <= 0) throw new IllegalStateException("مرفق غير موجود: " + file.getName());
                if (file.length() > MAX_ATTACHMENT_BYTES || totalBytes + file.length() > MAX_ATTACHMENT_BYTES) {
                    throw new IllegalStateException("حجم المرفقات أكبر من 12 ميغابايت؛ قلل الدقة أو استخدم موقعًا يقبل الملف بالحجم الحالي.");
                }
                byte[] data = readFile(file);
                totalBytes += data.length;
                String lower = file.getName().toLowerCase(Locale.ROOT);
                String mime = lower.endsWith(".mp4") ? "video/mp4" : lower.endsWith(".webm") ? "video/webm"
                        : lower.endsWith(".pdf") ? "application/pdf" : lower.endsWith(".png") ? "image/png"
                        : lower.endsWith(".webp") ? "image/webp" : lower.endsWith(".gif") ? "image/gif" : "image/jpeg";
                JSONObject item = new JSONObject();
                item.put("name", file.getName());
                item.put("mime", mime);
                item.put("data", Base64.encodeToString(data, Base64.NO_WRAP));
                files.put(item);
            }
        } catch (Exception e) {
            sendFailure(e.getMessage() == null ? "تعذر تجهيز مرفقات الموقع." : e.getMessage());
            return;
        }
        String script = "(function(files){try{" +
                "const vis=e=>{if(!e)return false;const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'};" +
                "let inputs=[...document.querySelectorAll('input[type=file]')];" +
                "if(!inputs.length){const bs=[...document.querySelectorAll('button,[role=button]')].filter(vis);const b=bs.find(e=>/(attach|upload|file|photo|image|add files|إرفاق|رفع|صورة|ملف|إضافة)/i.test((e.innerText||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')));if(b){b.click();return JSON.stringify({ok:false,needsInput:true})}return JSON.stringify({ok:false,needsInput:true})}" +
                "const dt=new DataTransfer();for(const x of files){const bin=atob(x.data),arr=new Uint8Array(bin.length);for(let i=0;i<bin.length;i++)arr[i]=bin.charCodeAt(i);dt.items.add(new File([arr],x.name,{type:x.mime,lastModified:Date.now()}))}" +
                "let input=inputs.find(e=>e.multiple)||inputs[0];try{input.files=dt.files}catch(e){return JSON.stringify({ok:false,needsInput:false,error:String(e)})}" +
                "input.dispatchEvent(new Event('input',{bubbles:true}));input.dispatchEvent(new Event('change',{bubbles:true}));return JSON.stringify({ok:input.files&&input.files.length>0,count:input.files?input.files.length:0,needsInput:false})" +
                "}catch(e){return JSON.stringify({ok:false,error:String(e)})}})(" + files.toString() + ")";
        attachmentInjectionStarted = true;
        attachAttempts++;
        webView.evaluateJavascript(script, value -> {
            JSONObject result = decodeObject(value);
            if (result != null && result.optBoolean("ok", false)) {
                attachmentsInjected = true;
                updateStatus("Botna: أُضيفت المرفقات؛ يستعد لإدخال التعليمات");
                handler.postDelayed(() -> {
                    if (!resultSent) {
                        injectPrompt();
                    }
                }, 1800L);
                return;
            }
            if (result != null && result.optBoolean("needsInput", false) && attachAttempts < 12) {
                updateStatus("Botna: يبحث عن زر إرفاق الملفات في الموقع");
                handler.postDelayed(workflowRunnable, 900L);
                return;
            }
            String detail = result == null ? "لم يستجب حقل رفع الملفات." : result.optString("error", "لم يقبل الموقع المرفقات.");
            sendFailure("تعذر إرفاق صور الشاشة أو الفيديو إلى الموقع: " + detail + " لم يرسل Botna التعليمات وحدها.");
        });
    }

    private void clickAttachmentControl() {
        String script = "(function(){const vis=e=>{if(!e)return false;const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'};const b=[...document.querySelectorAll('button,[role=button]')].filter(vis).find(e=>/(attach|upload|file|photo|image|add files|إرفاق|رفع|صورة|ملف|إضافة)/i.test((e.innerText||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('title')||'')));if(b){b.click();return 'clicked'}return 'missing'})()";
        webView.evaluateJavascript(script, value -> { });
    }

    private void injectPrompt() {
        if (resultSent || promptInjected || webView == null) return;
        String script = "(function(p){try{" +
                "const vis=e=>{if(!e)return false;const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>90&&r.height>12&&r.bottom>0&&r.top<innerHeight&&s.display!=='none'&&s.visibility!=='hidden'};" +
                "const ed=[...document.querySelectorAll('textarea,input:not([type=file]):not([type=password]),[contenteditable=true],[role=textbox]')].filter(vis).filter(e=>{const t=((e.getAttribute('placeholder')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('name')||'')+' '+(e.getAttribute('type')||'')).toLowerCase();return !/(search|بحث|email|password|username|كلمة المرور)/.test(t)});" +
                "const score=e=>{const r=e.getBoundingClientRect(),t=((e.getAttribute('placeholder')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('name')||'')).toLowerCase();return r.bottom*2+r.width*.05+(document.activeElement===e?500:0)+(/message|prompt|ask|اسأل|رسالة|اكتب/.test(t)?800:0)-(/search|بحث/.test(t)?4000:0)};ed.sort((a,b)=>score(a)-score(b));const e=ed[ed.length-1];if(!e)return JSON.stringify({ok:false});e.focus();" +
                "if(e.isContentEditable||e.getAttribute('contenteditable')==='true'){e.textContent=p}else{const proto=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;const desc=Object.getOwnPropertyDescriptor(proto,'value');if(desc&&desc.set)desc.set.call(e,p);else e.value=p;}" +
                "e.dispatchEvent(new InputEvent('input',{bubbles:true,inputType:'insertText',data:p}));e.dispatchEvent(new Event('change',{bubbles:true}));return JSON.stringify({ok:true,length:(e.value||e.innerText||e.textContent||'').length})" +
                "}catch(e){return JSON.stringify({ok:false,error:String(e)})}})(" + JSONObject.quote(prompt) + ")";
        promptAttempts++;
        webView.evaluateJavascript(script, value -> {
            JSONObject result = decodeObject(value);
            if (result != null && result.optBoolean("ok", false) && result.optInt("length", 0) >= prompt.length() * 0.9) {
                promptInjected = true;
                updateStatus("Botna: التعليمات جاهزة؛ سينتظر قبل الضغط على إرسال");
                int wait = BotnaExternalAppAgent.sendWaitMs(this, modelPackage);
                handler.postDelayed(this::sendPrompt, Math.max(0, wait));
                return;
            }
            if (promptAttempts < 8) handler.postDelayed(this::injectPrompt, 850L);
            else sendFailure("ظهر حقل الإدخال لكن تعذر التحقق من إدراج التعليمات كاملة؛ لم يضغط Botna إرسال.");
        });
    }

    private void sendPrompt() {
        if (resultSent || sendStarted || webView == null) return;
        probePage(probe -> {
            if (resultSent || sendStarted) return;
            if (probe == null) {
                waitToSendPrompt("لم يستطع فحص حالة الرفع قبل الإرسال.");
                return;
            }
            boolean uploading = probe.optBoolean("uploading", false);
            boolean generating = probe.optBoolean("generating", false);
            if (uploading || generating) {
                waitToSendPrompt("الموقع ما زال يرفع المرفقات أو يعالجها.");
                return;
            }
            dispatchSendPrompt();
        });
    }

    private void waitToSendPrompt(String reason) {
        sendReadyWaitAttempts++;
        updateStatus("Botna: ينتظر اكتمال المرفقات قبل الإرسال " + sendReadyWaitAttempts + "/45");
        if (sendReadyWaitAttempts >= 45) {
            sendFailure("لم تكتمل جاهزية الموقع أو رفع المرفقات خلال 45 محاولة. " + reason + " لم يرسل Botna التعليمات إلى مرفقات غير مستقرة.");
            return;
        }
        handler.postDelayed(this::sendPrompt, 1000L);
    }

    private void dispatchSendPrompt() {
        if (resultSent || sendStarted || webView == null) return;
        String script = "(function(p){try{" +
                "const vis=e=>{if(!e)return false;const r=e.getBoundingClientRect(),s=getComputedStyle(e);return r.width>80&&r.height>10&&r.bottom>0&&r.top<innerHeight&&s.display!=='none'&&s.visibility!=='hidden'};" +
                "const ed=[...document.querySelectorAll('textarea,input:not([type=file]):not([type=password]),[contenteditable=true],[role=textbox]')].filter(vis).filter(e=>{const t=((e.getAttribute('placeholder')||'')+' '+(e.getAttribute('aria-label')||'')+' '+(e.getAttribute('type')||'')).toLowerCase();return !/(search|بحث|email|password|username)/.test(t)});" +
                "const val=e=>e.isContentEditable?(e.innerText||e.textContent||''):(e.value||'');const e=ed.find(x=>val(x).includes(p.slice(0,Math.min(80,p.length)))&&val(x).includes(p.slice(Math.max(0,p.length-80))))||ed[ed.length-1];if(!e)return JSON.stringify({ok:false,reason:'no-composer'});if(!val(e).includes(p.slice(0,Math.min(80,p.length))))return JSON.stringify({ok:false,reason:'prompt-missing'});" +
                "let btns=[...document.querySelectorAll('button,[role=button],input[type=submit]')].filter(vis);let b=btns.filter(x=>/(send|submit|إرسال|ارسال|أرسل|بعث)/i.test((x.innerText||'')+' '+(x.getAttribute('aria-label')||'')+' '+(x.getAttribute('title')||''))).filter(x=>!/(stop|cancel|إيقاف|إلغاء)/i.test((x.innerText||'')+' '+(x.getAttribute('aria-label')||''))).pop();if(b){b.click();return JSON.stringify({ok:true,method:'button'})}const f=e.closest('form');if(f&&typeof f.requestSubmit==='function'){f.requestSubmit();return JSON.stringify({ok:true,method:'form'})}e.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',which:13,keyCode:13,bubbles:true}));e.dispatchEvent(new KeyboardEvent('keyup',{key:'Enter',code:'Enter',which:13,keyCode:13,bubbles:true}));return JSON.stringify({ok:true,method:'enter'})" +
                "}catch(e){return JSON.stringify({ok:false,error:String(e)})}})(" + JSONObject.quote(prompt) + ")";
        webView.evaluateJavascript(script, value -> {
            JSONObject result = decodeObject(value);
            if (result != null && result.optBoolean("ok", false)) {
                sendStarted = true;
                responseStablePolls = 0;
                lastResponseCandidate = "";
                updateStatus("Botna: أُرسلت التعليمات؛ ينتظر ردًا يحتوي أمر JSON");
                handler.postDelayed(workflowRunnable, 1800L);
                return;
            }
            sendClickAttempts++;
            if (sendClickAttempts < BotnaExternalAppAgent.sendMaxAttempts(this, modelPackage)) {
                updateStatus("Botna: يعيد محاولة الإرسال " + sendClickAttempts);
                handler.postDelayed(this::dispatchSendPrompt, 900L);
            } else {
                sendFailure("تعذر العثور على زر إرسال صالح أو تأكيد إرسال التعليمات بعد " + sendClickAttempts + " محاولات.");
            }
        });
    }

    private String extractDelta(String body, String baseline, String promptValue) {
        if (body == null) return "";
        String delta = body;
        if (baseline != null && !baseline.isEmpty()) {
            int at = body.lastIndexOf(baseline);
            if (at >= 0) delta = body.substring(at + baseline.length());
        }
        if (promptValue != null && !promptValue.isEmpty()) delta = delta.replace(promptValue, "");
        String marker = promptValue == null ? "" : promptValue.substring(0, Math.min(48, promptValue.length()));
        if (!marker.isEmpty()) {
            int at = delta.lastIndexOf(marker);
            if (at >= 0) delta = delta.substring(at + marker.length());
        }
        if (delta.length() > 16000) delta = delta.substring(delta.length() - 16000);
        return delta.trim();
    }

    private JSONObject decodeObject(String encoded) {
        try {
            Object decoded = new JSONTokener(encoded == null ? "null" : encoded).nextValue();
            if (decoded instanceof String) return new JSONObject((String) decoded);
            if (decoded instanceof JSONObject) return (JSONObject) decoded;
        } catch (Exception ignored) { }
        return null;
    }

    private byte[] readFile(File file) throws Exception {
        try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) > 0) {
                output.write(buffer, 0, read);
                if (output.size() > MAX_ATTACHMENT_BYTES) throw new IllegalStateException("حجم أحد المرفقات أكبر من 12 ميغابايت.");
            }
            return output.toByteArray();
        }
    }

    private void sendResponse(String response) {
        if (resultSent) return;
        resultSent = true;
        Intent result = new Intent(ACTION_RESPONSE).setPackage(getPackageName());
        result.putExtra(EXTRA_TASK_ID, taskId);
        result.putExtra(EXTRA_TEXT, response == null ? "" : response);
        sendBroadcast(result);
        updateStatus("Botna: استلم الرد؛ سيحلل الأمر ويتحقق منه");
        handler.postDelayed(this::finish, 12000L);
    }

    private void sendFailure(String reason) {
        if (resultSent) return;
        resultSent = true;
        Intent result = new Intent(ACTION_FAILED).setPackage(getPackageName());
        result.putExtra(EXTRA_TASK_ID, taskId);
        result.putExtra(EXTRA_TEXT, reason == null ? "تعذر إكمال الموقع." : reason);
        sendBroadcast(result);
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(this::finish, 450L);
    }

    private void updateStatus(String value) {
        if (statusView != null) statusView.setText(value == null ? "" : value);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String safe(String value) { return value == null ? "" : value.trim(); }

    @Override public void onBackPressed() {
        finish();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("BotnaBridge");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
