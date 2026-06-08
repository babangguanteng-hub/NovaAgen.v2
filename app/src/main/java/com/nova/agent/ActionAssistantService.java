package com.nova.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
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
import java.util.List;

public class ActionAssistantService extends AccessibilityService {
    public static boolean isServiceRunning = false;
    private CommandReceiver commandReceiver;

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
        Log.d("NovaAction", "Nova Real-Time Control AKTIF!");
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
            Log.d("NovaAction", "Menerima Kode Makro: " + aiCommand);
            
            new Thread(new Runnable() {
                @Override
                public void run() { executeMacro(aiCommand); }
            }).start();
        }
    }

    private void executeMacro(String cmdBlock) {
        try {
            // [CMD:OPEN:nama_aplikasi]
            if (cmdBlock.contains("[CMD:OPEN:")) {
                String app = extractValue(cmdBlock, "[CMD:OPEN:", "]");
                openSmartApp(app);
                Thread.sleep(3000); // Beri waktu 3 detik agar aplikasi terbuka penuh
            }
            
            // [CMD:TYPE:teks]
            if (cmdBlock.contains("[CMD:TYPE:")) {
                String textToType = extractValue(cmdBlock, "[CMD:TYPE:", "]");
                // Tunggu sampai kotak ketik muncul (Maksimal 5 detik pencarian)
                AccessibilityNodeInfo inputBox = waitForNodeByClassName("android.widget.EditText", 5000);
                if (inputBox != null) {
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textToType);
                    inputBox.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    Thread.sleep(1000); 
                }
            }
            
            // [CMD:CLICK:teks_tombol]
            if (cmdBlock.contains("[CMD:CLICK:")) {
                String textToClick = extractValue(cmdBlock, "[CMD:CLICK:", "]");
                waitForNodeAndClick(textToClick, 4000);
            }

            // [CMD:SCROLL_DOWN]
            if (cmdBlock.contains("[CMD:SCROLL_DOWN]")) {
                swipe(500, 1500, 500, 300, 400); // Geser dari bawah ke atas (Scroll Down visual)
            }
            // [CMD:SCROLL_UP]
            if (cmdBlock.contains("[CMD:SCROLL_UP]")) {
                swipe(500, 300, 500, 1500, 400);
            }

            if (cmdBlock.contains("[CMD:YOUTUBE:")) {
                String query = extractValue(cmdBlock, "[CMD:YOUTUBE:", "]");
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            if (cmdBlock.contains("[CMD:SHOPEE:")) {
                String query = extractValue(cmdBlock, "[CMD:SHOPEE:", "]");
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://shopee.co.id/search?keyword=" + Uri.encode(query)));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(webIntent);
            }
            if (cmdBlock.contains("[CMD:HOME]")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
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

    // PENCARI APLIKASI SUPER PINTAR (Bisa buka SEMUA aplikasi di HP)
    private void openSmartApp(String appNameRequested) {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        String requestedLower = appNameRequested.toLowerCase().trim();
        
        // Pengecualian khusus untuk singkatan populer
        if (requestedLower.equals("wa")) requestedLower = "whatsapp";
        if (requestedLower.equals("ig")) requestedLower = "instagram";

        for (ApplicationInfo packageInfo : packages) {
            String installedAppName = pm.getApplicationLabel(packageInfo).toString().toLowerCase();
            if (installedAppName.contains(requestedLower)) {
                Intent launchIntent = pm.getLaunchIntentForPackage(packageInfo.packageName);
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(launchIntent);
                    return; // Selesai jika ketemu
                }
            }
        }
        // Jika tidak ketemu, coba buka Playstore untuk mencari aplikasi tersebut
        Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(appNameRequested)));
        fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(fallbackIntent); } catch (Exception ignored) {}
    }

    // FITUR MATA AI: Menunggu elemen muncul sebelum dieksekusi (Anti Gagal)
    private AccessibilityNodeInfo waitForNodeByClassName(String className, int timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                AccessibilityNodeInfo node = findNodeByClassName(root, className);
                if (node != null) return node;
            }
            Thread.sleep(500); // Cek setiap setengah detik
        }
        return null;
    }

    private boolean waitForNodeAndClick(String text, int timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && clickNodeByText(root, text)) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    private AccessibilityNodeInfo findNodeByClassName(AccessibilityNodeInfo node, String className) {
        if (node == null) return null;
        if (className.equals(node.getClassName().toString()) && node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo result = findNodeByClassName(node.getChild(i), className);
            if (result != null) return result;
        }
        return null;
    }

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
