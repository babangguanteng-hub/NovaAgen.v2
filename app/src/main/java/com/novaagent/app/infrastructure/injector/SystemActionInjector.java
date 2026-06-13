package com.novaagent.app.infrastructure.injector;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.services.accessibility.NovaAccessibilityService;
import java.util.List;

public class SystemActionInjector {
    private static final String TAG = "SystemActionInjector";
    private final int screenWidth;
    private final int screenHeight;
    private final Handler delayHandler;

    public SystemActionInjector(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        this.screenWidth = metrics.widthPixels;
        this.screenHeight = metrics.heightPixels;
        this.delayHandler = new Handler(Looper.getMainLooper());
    }

    public void executeAction(ActionCommandDto cmd) {
        NovaAccessibilityService service;
        try {
            service = ServiceLocator.getInstance().resolve(NovaAccessibilityService.class);
        } catch (Exception e) { return; }

        if (cmd.speech != null && !cmd.speech.trim().isEmpty()) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.VOICE_RECEIVED, cmd.speech));
        }

        boolean success = false;
        switch (cmd.action) {
            case "open_app": success = openAppByName(service, cmd.textToType); break;
            case "volume_up": success = adjustVolume(service, AudioManager.ADJUST_RAISE); break;
            case "volume_down": success = adjustVolume(service, AudioManager.ADJUST_LOWER); break;
            case "home": success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); break;
            case "back": success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); break;
            case "tap": case "click": success = executeTap(service, cmd.x, cmd.y); break;
            case "swipe": case "scroll": success = executeSwipe(service, cmd.direction); break;
            case "press_enter":
                // Resolusi Dinamis: Mengetuk area estimasi tombol "Enter" di pojok kanan bawah keyboard
                int enterX = (int) (screenWidth * 0.90);
                int enterY = (int) (screenHeight * 0.90);
                success = executeTap(service, enterX, enterY);
                break;
            case "type":
                success = executeTap(service, cmd.x, cmd.y);
                // Non-Blocking Delay: Tidak pakai Thread.sleep() lagi
                delayHandler.postDelayed(() -> {
                    service.typeTextAtCoordinate(cmd.x, cmd.y, cmd.textToType);
                    EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_EXECUTED, cmd.action));
                }, 800);
                return; // Return awal agar tidak memicu ACTION_EXECUTED ganda di bawah
            case "done":
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "TUGAS SELESAI"));
                return;
        }

        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_EXECUTED, cmd.action));
    }

    private boolean adjustVolume(Context context, int direction) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) { audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI); return true; }
        return false;
    }

    private boolean openAppByName(Context context, String appName) {
        if (appName == null || appName.isEmpty()) return false;
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo packageInfo : packages) {
            if (pm.getApplicationLabel(packageInfo).toString().toLowerCase().contains(appName.toLowerCase())) {
                Intent intent = pm.getLaunchIntentForPackage(packageInfo.packageName);
                if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent); return true; }
            }
        }
        return false;
    }

    private boolean executeTap(NovaAccessibilityService service, int x, int y) {
        if (x <= 0 || y <= 0) return false;
        Path clickPath = new Path(); clickPath.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 100));
        return service.dispatchGesture(builder.build(), null, null);
    }

    private boolean executeSwipe(NovaAccessibilityService service, String direction) {
        Path swipePath = new Path();
        int centerX = screenWidth / 2;
        int topY = (int) (screenHeight * 0.2);
        int bottomY = (int) (screenHeight * 0.8);
        
        if ("up".equalsIgnoreCase(direction)) { swipePath.moveTo(centerX, bottomY); swipePath.lineTo(centerX, topY); } 
        else if ("down".equalsIgnoreCase(direction)) { swipePath.moveTo(centerX, topY); swipePath.lineTo(centerX, bottomY); } 
        else { return false; }
        
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 400));
        return service.dispatchGesture(builder.build(), null, null);
    }
}
