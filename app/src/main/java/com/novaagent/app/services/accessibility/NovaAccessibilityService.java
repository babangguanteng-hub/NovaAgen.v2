package com.novaagent.app.services.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.core.bus.AgentEvent;

public class NovaAccessibilityService extends AccessibilityService {
    private static final String TAG = "NovaA11yService";
    private String lastScreenContext = "Layar Kosong";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try { ServiceLocator.getInstance().register(NovaAccessibilityService.class, this); } catch (Exception e) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    private void parseNodeTree(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        try {
            if (node.isVisibleToUser()) {
                CharSequence text = node.getText();
                CharSequence desc = node.getContentDescription();
                String nodeText = text != null ? text.toString() : (desc != null ? desc.toString() : "");
                
                if (!nodeText.isEmpty() && (node.isClickable() || node.isScrollable() || node.isEditable())) {
                    Rect bounds = new Rect();
                    node.getBoundsInScreen(bounds);
                    String type = node.isEditable() ? "Input" : (node.isClickable() ? "Button" : "Text");
                    sb.append("[").append(type).append("] \"").append(nodeText).append("\" center(")
                      .append(bounds.centerX()).append(",").append(bounds.centerY()).append(")\n");
                }
            }
            
            for (int i = 0; i < node.getChildCount(); i++) {
                parseNodeTree(node.getChild(i), sb);
            }
        } catch (Exception e) {}
    }

    public String getCurrentScreenContext() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode != null) {
                StringBuilder sb = new StringBuilder();
                parseNodeTree(rootNode, sb);
                lastScreenContext = sb.toString();
                rootNode.recycle(); 
            }
        } catch (Exception e) {}
        return lastScreenContext.isEmpty() ? "Tidak ada elemen di layar." : lastScreenContext;
    }

    public void requestScreenScan() {
        String screenData = getCurrentScreenContext();
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.SCREEN_UPDATED, screenData));
    }

    // =========================================================
    // INI DIA FUNGSI YANG HILANG DAN BIKIN ERROR
    // =========================================================
    public boolean typeTextAtCoordinate(int x, int y, String text) {
        if (text == null) text = "";
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) return false;
            
            boolean result = findAndSetTextByCoordinate(rootNode, x, y, text);
            rootNode.recycle();
            return result;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean findAndSetTextByCoordinate(AccessibilityNodeInfo node, int x, int y, String text) {
        if (node == null) return false;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        
        if (bounds.contains(x, y) && (node.isEditable() || node.getClassName().toString().contains("EditText"))) {
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findAndSetTextByCoordinate(node.getChild(i), x, y, text)) return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        try { ServiceLocator.getInstance().register(NovaAccessibilityService.class, null); } catch (Exception e) {}
        return super.onUnbind(intent);
    }
}
