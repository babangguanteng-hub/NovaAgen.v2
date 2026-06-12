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
        try {
            ServiceLocator.getInstance().register(NovaAccessibilityService.class, this);
            Log.d(TAG, "Layanan Aksesibilitas Terhubung dan Aman.");
        } catch (Exception e) {}
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Biarkan kosong agar RAM tidak cepat panas.
        // Kita hanya memindai layar saat Nova dipanggil saja.
    }

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
                    sb.append("[").append(type).append("] ")
                      .append("\"").append(nodeText).append("\" ")
                      .append("center(").append(bounds.centerX()).append(",").append(bounds.centerY()).append(")\n");
                }
            }
            
            // [ANTI MATI SENDIRI]: Mengekstrak anak-anak node dan LANGSUNG MENGHAPUSNYA DARI RAM!
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    parseNodeTree(child, sb);
                    child.recycle(); // <--- INI ADALAH OBAT ANTI-MATI NYA!
                }
            }
        } catch (Exception e) {}
    }

    public String getCurrentScreenContext() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            StringBuilder sb = new StringBuilder();
            parseNodeTree(rootNode, sb);
            lastScreenContext = sb.toString();
            rootNode.recycle(); // Hapus root node dari RAM
        }
        return lastScreenContext.isEmpty() ? "Tidak ada elemen yang bisa diklik." : lastScreenContext;
    }

    public void requestScreenScan() {
        String screenData = getCurrentScreenContext();
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.SCREEN_UPDATED, screenData));
    }

    public boolean typeTextInFocusedNode(String text) {
        if (text == null) text = "";
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;
        
        AccessibilityNodeInfo focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focusedNode != null && focusedNode.isEditable()) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean res = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            focusedNode.recycle();
            rootNode.recycle();
            return res;
        }
        rootNode.recycle();
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
