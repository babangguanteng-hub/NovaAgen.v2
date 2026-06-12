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

    public void executeAction(ActionCommandDto cmd) {
        NovaAccessibilityService service;
        try {
            service = ServiceLocator.getInstance().resolve(NovaAccessibilityService.class);
        } catch (Exception e) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_FAILED, "NO_ACCESSIBILITY_SERVICE"));
            return;
        }

        if (cmd.speech != null && !cmd.speech.trim().isEmpty()) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.VOICE_RECEIVED, cmd.speech));
        }

        boolean success = false;
        // [ANTI-BUG 2]: Implementasi Seluruh Fisik (Tangan Nova)
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
            case "type":
            case "search":
                success = service.typeTextInFocusedNode(cmd.textToType);
                break;
            case "tap":
            case "click":
                success = executeTap(service, cmd.x, cmd.y);
                break;
            case "swipe":
            case "scroll":
                success = executeSwipe(service, cmd.direction);
                break;
            case "done":
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "TUGAS SELESAI"));
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
        builder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 100)); // 100ms ketukan
        return service.dispatchGesture(builder.build(), null, null);
    }

    private boolean executeSwipe(NovaAccessibilityService service, String direction) {
        Path swipePath = new Path();
        // Asumsi resolusi layar rata-rata Android Go (X: 720, Y: 1280)
        int centerX = 360, centerY = 640;
        
        if ("up".equalsIgnoreCase(direction)) {
            swipePath.moveTo(centerX, 1000); swipePath.lineTo(centerX, 200); // Scroll kebawah (Jari keatas)
        } else if ("down".equalsIgnoreCase(direction)) {
            swipePath.moveTo(centerX, 200); swipePath.lineTo(centerX, 1000);
        } else if ("left".equalsIgnoreCase(direction)) {
            swipePath.moveTo(600, centerY); swipePath.lineTo(100, centerY);
        } else if ("right".equalsIgnoreCase(direction)) {
            swipePath.moveTo(100, centerY); swipePath.lineTo(600, centerY);
        } else {
            return false;
        }

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 400)); // 400ms geseran halus
        return service.dispatchGesture(builder.build(), null, null);
    }
}
