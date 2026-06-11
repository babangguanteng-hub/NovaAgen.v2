package com.nova.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * ActionAssistantService
 * Layanan Aksesibilitas Utama untuk Otomatisasi Agen AI Nova.
 * Mendukung kontrol global, gestur dinamis (tap, swipe, long press), 
 * manipulasi node (klik teks, set teks), dan screen reading.
 */
public class ActionAssistantService extends AccessibilityService {

    private static final String TAG = "NovaActionService";
    public static boolean isServiceRunning = false;
    private CommandReceiver commandReceiver;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isServiceRunning = true;
        
        // Registrasi Penerima Broadcast Perintah dari Modul AI
        commandReceiver = new CommandReceiver();
        IntentFilter filter = new IntentFilter("com.nova.agent.EXECUTE_ACTION");
        
        // Kompatibilitas Android 14 (Tiramisu+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commandReceiver, filter);
        }
        Log.d(TAG, "Nova Action Assistant Service telah aktif.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Logika event real-time jika diperlukan pemantauan state
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Layanan terinterupsi.");
    }

    @Override
    public boolean onUnbind(Intent intent) {
        isServiceRunning = false;
        try {
            if (commandReceiver != null) {
                unregisterReceiver(commandReceiver);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal unregister receiver: " + e.getMessage());
        }
        return super.onUnbind(intent);
    }

    /**
     * Penerima Broadcast internal untuk memproses instruksi makro AI
     */
    private class CommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String aiCommand = intent.getStringExtra("command_code");
            if (aiCommand == null) return;

            // Eksekusi makro di thread terpisah agar UI (Layanan) tidak freeze di Android Go
            new Thread(new Runnable() {
                @Override
                public void run() {
                    executeMacro(aiCommand);
                }
            }).start();
        }
    }

    /**
     * Mesin Parser & Eksekutor Makro
     */
    private void executeMacro(String cmdBlock) {
        try {
            // ==========================================
            // 1. GLOBAL ACTIONS
            // ==========================================
            if (cmdBlock.contains("[CMD:HOME]")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
                Thread.sleep(500);
            }
            if (cmdBlock.contains("[CMD:BACK]")) {
                performGlobalAction(GLOBAL_ACTION_BACK);
                Thread.sleep(500);
            }
            if (cmdBlock.contains("[CMD:RECENTS]")) {
                performGlobalAction(GLOBAL_ACTION_RECENTS);
                Thread.sleep(500);
            }
            if (cmdBlock.contains("[CMD:NOTIFICATIONS]")) {
                performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
                Thread.sleep(500);
            }
            if (cmdBlock.contains("[CMD:QUICK_SETTINGS]")) {
                performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS);
                Thread.sleep(500);
            }

            // ==========================================
            // 2. GESTURE ACTIONS
            // ==========================================
            if (cmdBlock.contains("[CMD:TAP:")) {
                String coords = extractValue(cmdBlock, "[CMD:TAP:", "]");
                String[] parts = coords.split(",");
                if (parts.length == 2) {
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    dispatchGestureAction(x, y, x, y, 50); // Tap durasi 50ms
                    Thread.sleep(500);
                }
            }
            
            if (cmdBlock.contains("[CMD:LONG_PRESS:")) {
                String coords = extractValue(cmdBlock, "[CMD:LONG_PRESS:", "]");
                String[] parts = coords.split(",");
                if (parts.length == 2) {
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    dispatchGestureAction(x, y, x, y, 1000); // Long press durasi 1 detik
                    Thread.sleep(500);
                }
            }

            if (cmdBlock.contains("[CMD:SWIPE_UP]")) {
                performDirectionalSwipe("UP");
                Thread.sleep(500);
            }
            if (cmdBlock.contains("[CMD:SWIPE_DOWN]")) {
                performDirectionalSwipe("DOWN");
                Thread.sleep(500);
            }
            if (cmdBlock.contains("[CMD:SWIPE_LEFT]")) {
                performDirectionalSwipe("LEFT");
                Thread.sleep(500);
            }
            if (cmdBlock.contains("[CMD:SWIPE_RIGHT]")) {
                performDirectionalSwipe("RIGHT");
                Thread.sleep(500);
            }

            // ==========================================
            // 3. NODE INTERACTIONS (UI MANIPULATION)
            // ==========================================
            if (cmdBlock.contains("[CMD:CLICK_TEXT:")) {
                String textToClick = extractValue(cmdBlock, "[CMD:CLICK_TEXT:", "]");
                waitForNodeAndClickByText(textToClick, 4000);
            }

            if (cmdBlock.contains("[CMD:CLICK_DESC:")) {
                String descToClick = extractValue(cmdBlock, "[CMD:CLICK_DESC:", "]");
                waitForNodeAndClickByDescription(descToClick, 4000);
            }

            if (cmdBlock.contains("[CMD:TYPE:")) {
                String textToType = extractValue(cmdBlock, "[CMD:TYPE:", "]");
                AccessibilityNodeInfo inputBox = waitForNodeByClassName("android.widget.EditText", 5000);
                if (inputBox != null) {
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
                    inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    Thread.sleep(800);
                }
            }

            // ==========================================
            // 4. SCREEN UNDERSTANDING (READ SCREEN)
            // ==========================================
            if (cmdBlock.contains("[CMD:READ_SCREEN]")) {
                readActiveScreenTextAndBroadcast();
            }

        } catch (Exception e) {
            Log.e(TAG, "Error mengeksekusi makro: " + e.getMessage());
        }
    }

    /**
     * Membaca seluruh teks aktif yang terlihat di layar dan mengirimkannya kembali ke AI.
     */
    private void readActiveScreenTextAndBroadcast() {
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        if (rootNode == null) return;

        List<String> textList = new ArrayList<>();
        traverseAndExtractText(rootNode, textList);

        StringBuilder screenData = new StringBuilder();
        for (String text : textList) {
            screenData.append(text).append(" | ");
        }

        // Broadcast teks layar ke modul utama (Groq AI)
        Intent responseIntent = new Intent("com.nova.agent.SCREEN_TEXT_RESULT");
        responseIntent.putExtra("extracted_text", screenData.toString());
        responseIntent.setPackage(getPackageName());
        sendBroadcast(responseIntent);
    }

    /**
     * Rekursif mendalam untuk menyalin teks dan deskripsi dari UI tree
     */
    private void traverseAndExtractText(AccessibilityNodeInfo node, List<String> list) {
        if (node == null) return;

        CharSequence text = node.getText();
        CharSequence desc = node.getContentDescription();

        if (text != null && text.length() > 0) {
            list.add(text.toString().trim());
        } else if (desc != null && desc.length() > 0) {
            list.add(desc.toString().trim());
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            traverseAndExtractText(node.getChild(i), list);
        }
    }

    /**
     * Mesin pembuat gestur (Swipe & Tap) menggunakan Accessibility API
     */
    private void dispatchGestureAction(int startX, int startY, int endX, int endY, int durationMs) {
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
    }

    /**
     * Helper untuk Swipe terarah menggunakan persentase layar perangkat
     */
    private void performDirectionalSwipe(String direction) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;

        int centerX = width / 2;
        int centerY = height / 2;
        int duration = 400;

        switch (direction) {
            case "UP": // Geser jari ke atas (Layar turun ke bawah)
                dispatchGestureAction(centerX, (int)(height * 0.8), centerX, (int)(height * 0.2), duration);
                break;
            case "DOWN": // Geser jari ke bawah (Layar naik ke atas)
                dispatchGestureAction(centerX, (int)(height * 0.2), centerX, (int)(height * 0.8), duration);
                break;
            case "LEFT": // Geser jari ke kiri
                dispatchGestureAction((int)(width * 0.8), centerY, (int)(width * 0.2), centerY, duration);
                break;
            case "RIGHT": // Geser jari ke kanan
                dispatchGestureAction((int)(width * 0.2), centerY, (int)(width * 0.8), centerY, duration);
                break;
        }
    }

    // ==========================================
    // NODE SEARCH ENGINE (Aksesibilitas Mendalam)
    // ==========================================

    private AccessibilityNodeInfo waitForNodeByClassName(String className, int timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo node = findNodeByClassName(root, className);
                if (node != null) return node;
            }
            Thread.sleep(300);
        }
        return null;
    }

    private boolean waitForNodeAndClickByText(String text, int timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && clickNodeByCondition(root, text, true)) {
                return true;
            }
            Thread.sleep(300);
        }
        return false;
    }

    private boolean waitForNodeAndClickByDescription(String desc, int timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && clickNodeByCondition(root, desc, false)) {
                return true;
            }
            Thread.sleep(300);
        }
        return false;
    }

    private AccessibilityNodeInfo findNodeByClassName(AccessibilityNodeInfo node, String className) {
        if (node == null) return null;
        if (node.getClassName() != null && className.equals(node.getClassName().toString()) && node.isEditable()) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findNodeByClassName(node.getChild(i), className);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Mencari node berdasarkan teks atau deskripsi, lalu memicu klik ke elemen atau induknya.
     */
    private boolean clickNodeByCondition(AccessibilityNodeInfo node, String target, boolean isTextBased) {
        if (node == null) return false;
        
        CharSequence nodeText = node.getText();
        CharSequence nodeDesc = node.getContentDescription();
        boolean match = false;

        if (isTextBased && nodeText != null) {
            match = nodeText.toString().toLowerCase().contains(target.toLowerCase());
        } else if (!isTextBased && nodeDesc != null) {
            match = nodeDesc.toString().toLowerCase().contains(target.toLowerCase());
        }

        if (match) {
            AccessibilityNodeInfo clickableNode = node;
            while (clickableNode != null && !clickableNode.isClickable()) {
                clickableNode = clickableNode.getParent();
            }
            if (clickableNode != null && clickableNode.isClickable()) {
                clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                return true;
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            if (clickNodeByCondition(node.getChild(i), target, isTextBased)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Utilitas ekstraksi string dari format makro [CMD:X:Y]
     */
    private String extractValue(String fullCmd, String prefix, String suffix) {
        try {
            int start = fullCmd.indexOf(prefix) + prefix.length();
            int end = fullCmd.indexOf(suffix, start);
            return fullCmd.substring(start, end).trim();
        } catch (Exception e) { 
            return ""; 
        }
    }
}
