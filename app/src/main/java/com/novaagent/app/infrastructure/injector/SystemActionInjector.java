package com.novaagent.app.infrastructure.injector;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.media.AudioManager;
import android.util.Log;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.services.accessibility.NovaAccessibilityService;

import java.util.List;

public class SystemActionInjector {
    private static final String TAG = "SystemActionInjector";

    public void executeAction(ActionCommandDto cmd) {
        NovaAccessibilityService service;
        try {
            service = ServiceLocator.getInstance().resolve(NovaAccessibilityService.class);
        } catch (Exception e) {
            return;
        }

        if (cmd.speech != null && !cmd.speech.trim().isEmpty()) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.VOICE_RECEIVED, cmd.speech));
        }

        boolean success = false;
        switch (cmd.action) {
            case "open_app":
                success = openAppByName(service, cmd.textToType);
                break;
            case "volume_up":
                success = adjustVolume(service, AudioManager.ADJUST_RAISE);
                break;
            case "volume_down":
                success = adjustVolume(service, AudioManager.ADJUST_LOWER);
                break;
            case "home": success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME); break;
            case "back": success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK); break;
            case "recents": success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS); break;
            case "quick_settings": success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS); break;
            case "tap": 
            case "click": 
                success = executeTap(service, cmd.x, cmd.y); 
                break;
            case "swipe": 
            case "scroll": 
                success = executeSwipe(service, cmd.direction); 
                break;
            case "type":
                executeTap(service, cmd.x, cmd.y);
                try { Thread.sleep(800); } catch (Exception ignored) {} 
                success = service.typeTextAtCoordinate(cmd.x, cmd.y, cmd.textToType);
                break;
            case "press_enter":
                // Mengetuk area keyboard virtual bagian kanan bawah (Tombol Cari / Enter)
                // Resolusi standar, titik estimasi x: 650, y: 1200
                success = executeTap(service, 650, 1200);
                break;
            case "done":
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "TUGAS SELESAI"));
                success = true;
                break;
            default:
                Log.w(TAG, "Aksi tidak dikenali: " + cmd.action);
        }

        if (!cmd.action.equals("done")) {
            // Beri tahu AI bahwa tangan sudah selesai bergerak, lanjut scan layar lagi
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_EXECUTED, cmd.action));
        }
    }

    private boolean adjustVolume(Context context, int direction) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            return true;
        }
        return false;
    }

    private boolean openAppByName(Context context, String appName) {
        if (appName == null || appName.isEmpty()) return false;
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        
        for (ApplicationInfo packageInfo : packages) {
            String label = pm.getApplicationLabel(packageInfo).toString().toLowerCase();
            if (label.contains(appName.toLowerCase())) {
                Intent intent = pm.getLaunchIntentForPackage(packageInfo.packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean executeTap(NovaAccessibilityService service, int x, int y) {
        if (x <= 0 || y <= 0) return false;
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 100));
        return service.dispatchGesture(builder.build(), null, null);
    }

    private boolean executeSwipe(NovaAccessibilityService service, String direction) {
        Path swipePath = new Path();
        int centerX = 360, centerY = 640;
        if ("up".equalsIgnoreCase(direction)) { swipePath.moveTo(centerX, 1000); swipePath.lineTo(centerX, 200); } 
        else if ("down".equalsIgnoreCase(direction)) { swipePath.moveTo(centerX, 200); swipePath.lineTo(centerX, 1000); } 
        else { return false; }
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 400));
        return service.dispatchGesture(builder.build(), null, null);
    }
}
