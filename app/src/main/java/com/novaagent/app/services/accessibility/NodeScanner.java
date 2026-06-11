package com.novaagent.app.services.accessibility;

import android.graphics.Rect;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * NodeScanner melakukan scanning dan kompresi Accessibility Tree
 * dengan pendekatan yang sangat berhati-hati terhadap memory leak (C++ Binder).
 */
public class NodeScanner {
    private static final String TAG = "NodeScanner";
    private static final int MAX_DEPTH = 30; // Mencegah StackOverflowError

    public JSONArray scanAndCompact(AccessibilityNodeInfo root) {
        JSONArray outputArray = new JSONArray();
        if (root == null) {
            Log.w(TAG, "Root node null. Batal scanning.");
            return outputArray;
        }

        Rect tempRect = new Rect();
        traverse(root, outputArray, 0, tempRect);
        return outputArray;
    }

    private void traverse(AccessibilityNodeInfo node, JSONArray array, int depth, Rect rect) {
        if (node == null || depth > MAX_DEPTH) return;

        // Jangan kumpulkan node yang tidak terlihat oleh pengguna
        if (isVisible(node)) {
            CharSequence text = node.getText();
            CharSequence desc = node.getContentDescription();
            boolean isClickable = node.isClickable();
            boolean isEditable = node.isEditable();

            boolean hasText = text != null && text.length() > 0;
            boolean hasDesc = desc != null && desc.length() > 0;

            // Kita hanya peduli pada elemen interaktif atau yang memiliki teks
            if (hasText || hasDesc || isClickable || isEditable) {
                node.getBoundsInScreen(rect);
                // Buang elemen yang dimensinya tidak masuk akal (0x0)
                if (!rect.isEmpty() && rect.width() > 0 && rect.height() > 0) {
                    try {
                        JSONObject obj = new JSONObject();
                        if (hasText) obj.put("text", text.toString());
                        if (hasDesc) obj.put("contentDescription", desc.toString());
                        
                        CharSequence className = node.getClassName();
                        if (className != null) {
                            String cName = className.toString();
                            int dotIdx = cName.lastIndexOf('.');
                            // Hanya simpan nama simpelnya saja (misal: "Button" bukan "android.widget.Button")
                            obj.put("className", dotIdx >= 0 ? cName.substring(dotIdx + 1) : cName);
                        }
                        
                        if (isClickable) obj.put("clickable", true);
                        if (isEditable) obj.put("editable", true);
                        obj.put("cx", rect.centerX());
                        obj.put("cy", rect.centerY());
                        obj.put("bounds", rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom);
                        
                        array.put(obj);
                    } catch (Exception e) {
                        Log.e(TAG, "Gagal mem-parsing node ke JSON", e);
                    }
                }
            }
        }

        // --- TRAVERSAL ANAK (CHILDREN) ---
        int childCount = node.getChildCount();
        List<AccessibilityNodeInfo> childrenToRecycle = new ArrayList<>(childCount);
        
        try {
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) {
                    childrenToRecycle.add(child);
                    traverse(child, array, depth + 1, rect);
                }
            }
        } finally {
            // SANGAT KRITIS: Semua anak yang diambil WAJIB dibuang
            for (AccessibilityNodeInfo child : childrenToRecycle) {
                safeRecycle(child);
            }
        }
    }

    private boolean isVisible(AccessibilityNodeInfo node) {
        try {
            return node.isVisibleToUser();
        } catch (Exception e) {
            return false;
        }
    }

    private void safeRecycle(AccessibilityNodeInfo node) {
        if (node != null) {
            try {
                node.recycle();
            } catch (Exception ignored) {
                // Abaikan jika sudah di-recycle
            }
        }
    }
}
