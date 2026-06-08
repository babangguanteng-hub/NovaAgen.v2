package com.nova.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class ActionAssistantService extends AccessibilityService {
    public static boolean isServiceRunning = false;
    private CommandReceiver commandReceiver;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isServiceRunning = true;
        commandReceiver = new CommandReceiver();
        IntentFilter filter = new IntentFilter("com.nova.agent.EXECUTE_ACTION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commandReceiver, filter);
        }
        Log.d("NovaAction", "Real-Time Action Engine AKTIF.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        isServiceRunning = false;
        try { unregisterReceiver(commandReceiver); } catch (Exception ignored) {}
        return super.onUnbind(intent);
    }

    private class CommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String aiCommand = intent.getStringExtra("command_code");
            if (aiCommand == null) return;
            Log.d("NovaAction", "Menerima Kode: " + aiCommand);
            
            // Eksekusi kode secara bertahap agar terlihat nyata
            new Thread(new Runnable() {
                @Override
                public void run() {
                    executeMacro(aiCommand);
                }
            }).start();
        }
    }

    private void executeMacro(String cmdBlock) {
        try {
            // [CMD:OPEN:whatsapp]
            if (cmdBlock.contains("[CMD:OPEN:")) {
                String app = extractValue(cmdBlock, "[CMD:OPEN:", "]");
                openAppByName(app);
                Thread.sleep(2000); // Tunggu aplikasi terbuka
            }
            
            // [CMD:TYPE:teks pesan] - Mengetik secara nyata di kolom aktif
            if (cmdBlock.contains("[CMD:TYPE:")) {
                String textToType = extractValue(cmdBlock, "[CMD:TYPE:", "]");
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    AccessibilityNodeInfo inputBox = findNodeByClassName(root, "android.widget.EditText");
                    if (inputBox != null) {
                        Bundle arguments = new Bundle();
                        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
                        inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                        Thread.sleep(1000); // Jeda simulasi ngetik
                    }
                }
            }
            
            // [CMD:CLICK:Kirim] - Mencari tombol dengan teks tertentu lalu di klik
            if (cmdBlock.contains("[CMD:CLICK:")) {
                String textToClick = extractValue(cmdBlock, "[CMD:CLICK:", "]");
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    clickNodeByText(root, textToClick);
                    Thread.sleep(500);
                }
            }

            // [CMD:SCROLL_DOWN]
            if (cmdBlock.contains("[CMD:SCROLL_DOWN]")) {
                swipe(500, 1500, 500, 300, 500);
            }

            // Command Spesifik yang lebih cepat
            if (cmdBlock.contains("[CMD:YOUTUBE:")) {
                String query = extractValue(cmdBlock, "[CMD:YOUTUBE:", "]");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            if (cmdBlock.contains("[CMD:SHOPEE:")) {
                String query = extractValue(cmdBlock, "[CMD:SHOPEE:", "]");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("shopee://search?keyword=" + Uri.encode(query)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(intent); } catch (Exception e) {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://shopee.co.id/search?keyword=" + Uri.encode(query)));
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(webIntent);
                }
            }
        } catch (Exception e) {
            Log.e("NovaAction", "Error Macro: " + e.getMessage());
        }
    }

    private String extractValue(String fullCmd, String prefix, String suffix) {
        try {
            int start = fullCmd.indexOf(prefix) + prefix.length();
            int end = fullCmd.indexOf(suffix, start);
            return fullCmd.substring(start, end).trim();
        } catch (Exception e) { return ""; }
    }

    private void openAppByName(String appName) {
        PackageManager pm = getPackageManager();
        String targetPackage = "";
        appName = appName.toLowerCase();
        if (appName.contains("wa") || appName.contains("whatsapp")) targetPackage = "com.whatsapp";
        else if (appName.contains("youtube")) targetPackage = "com.google.android.youtube";
        else if (appName.contains("ig") || appName.contains("instagram")) targetPackage = "com.instagram.android";
        else if (appName.contains("tiktok")) targetPackage = "com.zhiliaoapp.musically";
        
        if (!targetPackage.isEmpty()) {
            Intent launchIntent = pm.getLaunchIntentForPackage(targetPackage);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
            }
        }
    }

    // Rekursif mencari EditText (Kotak Input)
    private AccessibilityNodeInfo findNodeByClassName(AccessibilityNodeInfo node, String className) {
        if (node == null) return null;
        if (className.equals(node.getClassName().toString()) && node.isEditable()) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findNodeByClassName(node.getChild(i), className);
            if (result != null) return result;
        }
        return null;
    }

    // Rekursif mencari tombol/elemen berdasarkan teks dan mengkliknya
    private boolean clickNodeByText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        CharSequence nodeText = node.getText();
        CharSequence nodeDesc = node.getContentDescription();
        
        boolean match = (nodeText != null && nodeText.toString().toLowerCase().contains(text.toLowerCase())) ||
                        (nodeDesc != null && nodeDesc.toString().toLowerCase().contains(text.toLowerCase()));
                        
        if (match && node.isClickable()) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            return true;
        }
        
        for (int i = 0; i < node.getChildCount(); i++) {
            if (clickNodeByText(node.getChild(i), text)) return true;
        }
        return false;
    }

    private void swipe(int startX, int startY, int endX, int endY, int durationMs) {
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, durationMs));
        dispatchGesture(builder.build(), null, null);
    }
}
