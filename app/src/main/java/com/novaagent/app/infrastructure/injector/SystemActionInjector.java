package com.novaagent.app.infrastructure.injector;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.services.accessibility.NovaAccessibilityService;

public class SystemActionInjector {
    private static final String TAG = "SystemActionInjector";

    public SystemActionInjector() {}

    public void executeAction(ActionCommandDto cmd) {
        NovaAccessibilityService service;
        try {
            service = ServiceLocator.getInstance().resolve(NovaAccessibilityService.class);
        } catch (Exception e) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_FAILED, "NO_ACCESSIBILITY_SERVICE"));
            return;
        }

        // --- INI KUNCI UTAMANYA: Mengirimkan teks ke mulut Nova (TTS) ---
        if (cmd.speech != null && !cmd.speech.trim().isEmpty()) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.VOICE_RECEIVED, cmd.speech));
        }

        boolean success = false;
        switch (cmd.action) {
            case "home":
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                break;
            case "back":
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                break;
            case "recents":
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                break;
            case "quick_settings":
                success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
                break;
            case "done":
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "TUGAS SELESAI"));
                success = true;
                break;
            case "tap":
                success = executeTap(service, cmd.x, cmd.y);
                break;
            case "swipe":
                // Bypass sementara agar tidak error kompilasi
                success = true; 
                break;
            default:
                Log.w(TAG, "Aksi tidak dikenali: " + cmd.action);
        }

        if (!cmd.action.equals("tap") && !cmd.action.equals("swipe")) {
            if (success) {
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_EXECUTED, cmd.action));
            } else {
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_FAILED, cmd.action));
            }
        }
    }

    private boolean executeTap(NovaAccessibilityService service, int x, int y) {
        if (x <= 0 || y <= 0) return false;
        Path clickPath = new Path();
        clickPath.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 100));
        return service.dispatchGesture(builder.build(), new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_EXECUTED, "tap"));
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_FAILED, "tap"));
            }
        }, null);
    }
}
