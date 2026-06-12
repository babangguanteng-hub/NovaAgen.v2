package com.novaagent.app.services.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.novaagent.app.core.di.ServiceLocator;

public class NovaAccessibilityService extends AccessibilityService {
    private static final String TAG = "NovaA11yService";
    
    // Menyimpan denah layar terakhir
    private String lastScreenContext = "Layar Kosong";

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            ServiceLocator.getInstance().register(NovaAccessibilityService.class, this);
            Log.d(TAG, "Layanan Aksesibilitas Terhubung dan Aman.");
        } catch (Exception e) {
            Log.e(TAG, "Gagal mendaftar ke ServiceLocator", e);
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        try {
            // [ANTI-BUG 4]: Hanya baca layar saat ada perubahan besar, hemat RAM Android Go
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                AccessibilityNodeInfo rootNode = getRootInActiveWindow();
                if (rootNode != null) {
                    StringBuilder sb = new StringBuilder();
                    parseNodeTree(rootNode, sb);
                    lastScreenContext = sb.toString();
                    rootNode.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Abaikan error A11y", e);
        }
    }

    // [ANTI-BUG 1]: Fungsi Rekursif Pemindai Layar (Mata Nova)
    private void parseNodeTree(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        
        if (node.isVisibleToUser()) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            String nodeText = text != null ? text.toString() : (desc != null ? desc.toString() : "");
            
            if (!nodeText.isEmpty() && (node.isClickable() || node.isScrollable() || node.isEditable())) {
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                
                // Format: [Tipe] "Teks" (X, Y)
                String type = node.isEditable() ? "Input" : (node.isClickable() ? "Button" : "Text");
                sb.append("[").append(type).append("] ")
                  .append("\"").append(nodeText).append("\" ")
                  .append("center(").append(bounds.centerX()).append(",").append(bounds.centerY()).append(")\n");
            }
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            parseNodeTree(node.getChild(i), sb);
        }
    }

    // Dipanggil oleh Otak AI saat butuh melihat layar
    public String getCurrentScreenContext() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode != null) {
            StringBuilder sb = new StringBuilder();
            parseNodeTree(rootNode, sb);
            lastScreenContext = sb.toString();
            rootNode.recycle();
        }
        return lastScreenContext.isEmpty() ? "Tidak ada elemen yang bisa diklik." : lastScreenContext;
    }

    // Fitur Mengetik Otomatis
    public boolean typeTextInFocusedNode(String text) {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return false;
        AccessibilityNodeInfo focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focusedNode != null && focusedNode.isEditable()) {
            Bundle arguments = new Bundle();
            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            boolean res = focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
            focusedNode.recycle();
            return res;
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
