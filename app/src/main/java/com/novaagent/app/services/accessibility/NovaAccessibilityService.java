package com.novaagent.app.services.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.data.cache.NodeCache;
import com.novaagent.app.infrastructure.system.NovaWatchdog;

import org.json.JSONArray;

public class NovaAccessibilityService extends AccessibilityService {
    private static final String TAG = "NovaAccessibilityService";

    private NodeScanner scanner;
    private NodeCache nodeCache;
    private NovaWatchdog watchdog;
    private boolean isServiceReady = false;

    @Override
    public void onCreate() {
        super.onCreate();
        scanner = new NodeScanner();
        nodeCache = new NodeCache();
        
        ServiceLocator locator = ServiceLocator.getInstance();
        locator.register(NovaAccessibilityService.class, this);
        locator.register(NodeCache.class, nodeCache);
        try { watchdog = locator.resolve(NovaWatchdog.class); } catch (Exception ignored) {}
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isServiceReady = true;
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "IDLE"));
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isServiceReady) return;
        if (watchdog != null) watchdog.updateHeartbeat();

        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            nodeCache.invalidate();
        }
    }

    public void requestScreenScan() {
        if (!isServiceReady) return;
        JSONArray cached = nodeCache.getValidCache();
        if (cached != null) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.SCREEN_UPDATED, cached));
            return;
        }

        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null) {
                JSONArray screenData = scanner.scanAndCompact(root);
                nodeCache.saveCache(screenData);
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.SCREEN_UPDATED, screenData));
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal scan layar", e);
        } finally {
            if (root != null) root.recycle();
        }
    }

    // FITUR BARU: JARI PENGETIK (Mengetik teks secara otomatis ke kolom yang sedang fokus)
    public boolean typeTextInFocusedNode(String textToType) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;

        AccessibilityNodeInfo focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        boolean success = false;
        
        if (focusedNode != null && focusedNode.isEditable()) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
            success = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            focusedNode.recycle();
        }
        root.recycle();
        return success;
    }

    @Override
    public void onInterrupt() { isServiceReady = false; }
    @Override
    public void onDestroy() { super.onDestroy(); isServiceReady = false; }
}
