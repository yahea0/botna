package com.botna.automation;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

public final class BotnaScreenCapturePermissionActivity extends Activity {
    private static final int REQUEST_CAPTURE_PERMISSION = 23615;
    private String taskId = "";
    private String captureMode = BotnaScreenCaptureService.CAPTURE_MODE_VIDEO;
    private boolean requestStarted;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        taskId = getIntent() == null ? "" : getIntent().getStringExtra(BotnaScreenCaptureService.EXTRA_TASK_ID);
        if (taskId == null) taskId = "";
        captureMode = getIntent() == null ? BotnaScreenCaptureService.CAPTURE_MODE_VIDEO : getIntent().getStringExtra(BotnaScreenCaptureService.EXTRA_CAPTURE_MODE);
        if (!BotnaScreenCaptureService.CAPTURE_MODE_STILLS.equals(captureMode)) captureMode = BotnaScreenCaptureService.CAPTURE_MODE_VIDEO;
        if (state != null || requestStarted) {
            finish();
            return;
        }
        requestStarted = true;
        try {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE_PERMISSION);
        } catch (Exception e) {
            sendFailure("تعذر فتح نافذة إذن تسجيل الشاشة: " + e.getClass().getSimpleName());
            finish();
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE_PERMISSION) {
            finish();
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            sendFailure("لم يتم منح إذن التقاط الشاشة؛ وافق على نافذة نظام Android ليتمكن Botna من تحليل الشاشة.");
            finish();
            return;
        }
        if (!getSharedPreferences(BotnaStore.PREFS, MODE_PRIVATE).getBoolean(BotnaAccessibilityService.PREF_AGENT_TASK_ACTIVE, false)) {
            finish();
            return;
        }
        Intent service = new Intent(this, BotnaScreenCaptureService.class);
        service.setAction(BotnaScreenCaptureService.CAPTURE_MODE_STILLS.equals(captureMode)
                ? BotnaScreenCaptureService.ACTION_START_STILL_CAPTURE : BotnaScreenCaptureService.ACTION_START_CAPTURE);
        service.putExtra(BotnaScreenCaptureService.EXTRA_TASK_ID, taskId);
        service.putExtra(BotnaScreenCaptureService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(BotnaScreenCaptureService.EXTRA_RESULT_DATA, data);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } catch (Exception e) {
            sendFailure("تعذر تشغيل خدمة تسجيل الشاشة: " + e.getClass().getSimpleName());
        }
        finish();
    }

    private void sendFailure(String message) {
        Intent result = new Intent(BotnaScreenCaptureService.ACTION_CAPTURE_FAILED).setPackage(getPackageName());
        result.putExtra(BotnaScreenCaptureService.EXTRA_TASK_ID, taskId);
        result.putExtra(BotnaScreenCaptureService.EXTRA_REQUEST_ID, "");
        result.putExtra(BotnaScreenCaptureService.EXTRA_SUCCESS, false);
        result.putExtra(BotnaScreenCaptureService.EXTRA_ERROR, message == null ? "تعذر تسجيل الشاشة." : message);
        sendBroadcast(result);
    }
}
