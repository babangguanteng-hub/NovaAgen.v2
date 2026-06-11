package com.novaagent.app.infrastructure.injector;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.services.accessibility.NovaAccessibilityService;

public class SystemActionInjector {
    private static final String TAG = "SystemActionInjector";
    private int screenWidth = 720; 
    private int screenHeight = 1600;

    public void executeAction(ActionCommandDto cmd) {
        // Kirim ucapan AI (Speech) ke UI untuk diucapkan (Text-To-Speech)
        if (cmd.speech != null && !cmd.speech.isEmpty()) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.VOICE_RECEIVED, cmd.speech));
        }

        NovaAccessibilityService service;
        try {
            service = ServiceLocator.getInstance().resolve(NovaAccessibilityService.class);
        } catch (Exception e) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_FAILED, "NO_ACCESSIBILITY"));
            return;
        }

        Log.i(TAG, "Mengeksekusi Aksi: " + cmd.action);

        boolean success = false;
        switch (cmd.action) {
            case "tap":
                success = executeTap(service, cmd.x, cmd.y);
                break;
            case "swipe":
                success = executeSwipe(service, cmd.direction);
                break;
            case "type":
                success = service.typeTextInFocusedNode(cmd.textToType);
                if (success) EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_EXECUTED, "type"));
                else EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_FAILED, "type_failed"));
                break;
            case "global":
                if ("quick_settings".equals(cmd.globalAction)) success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
                else if ("home".equals(cmd.globalAction)) success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
                else if ("back".equals(cmd.globalAction)) success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                else if ("recents".equals(cmd.globalAction)) success = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
                
                if (success) EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_EXECUTED, "global"));
                else EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_FAILED, "global_failed"));
                break;
            case "volume_up":
            case "volume_down":
                success = executeVolumeControl(service, cmd.action);
                if (success) EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_EXECUTED, "volume"));
                break;
            case "done":
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "TUGAS SELESAI"));
                break;
        }
    }

    private boolean executeVolumeControl(Context context, String action) {
        try {
            AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                int direction = action.equals("volume_up") ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal mengubah volume", e);
        }
        return false;
    }

    private boolean executeTap(NovaAccessibilityService service, int x, int y) {
        Path path = new Path(); path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        return dispatch(service, builder.build(), "Tap");
    }

    private boolean executeSwipe(NovaAccessibilityService service, String direction) {
        float startX = screenWidth/2f, startY = screenHeight/2f, endX = startX, endY = startY;
        switch (direction) {
            case "down": startY = screenHeight * 0.2f; endY = screenHeight * 0.8f; break;
            case "up": startY = screenHeight * 0.8f; endY = screenHeight * 0.2f; break;
            case "left": startX = screenWidth * 0.8f; endX = screenWidth * 0.2f; break;
            case "right": startX = screenWidth * 0.2f; endX = screenWidth * 0.8f; break;
        }
        Path path = new Path(); path.moveTo(startX, startY); path.lineTo(endX, endY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 400));
        return dispatch(service, builder.build(), "Swipe");
    }

    private boolean dispatch(NovaAccessibilityService service, GestureDescription gesture, String tag) {
        return service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) { EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_EXECUTED, tag)); }
        }, new Handler(Looper.getMainLooper()));
    }
}
