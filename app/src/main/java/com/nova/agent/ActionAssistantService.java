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
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionAssistantService extends AccessibilityService {
    // Flag statis untuk mengatasi bug "Status Mati" di UI
    public static boolean isServiceRunning = false;
    private CommandReceiver commandReceiver;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        isServiceRunning = true;
        
        // Mendaftar pendengar perintah dari AI
        commandReceiver = new CommandReceiver();
        IntentFilter filter = new IntentFilter("com.nova.agent.EXECUTE_ACTION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(commandReceiver, filter);
        }
        Log.d("NovaAction", "Layanan Aksesibilitas Nova AKTIF.");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Pemantauan UI untuk fitur baca layar di masa depan
    }

    @Override
    public void onInterrupt() {}

    @Override
    public boolean onUnbind(Intent intent) {
        isServiceRunning = false;
        if (commandReceiver != null) {
            try { unregisterReceiver(commandReceiver); } catch (Exception ignored) {}
        }
        return super.onUnbind(intent);
    }

    // --- MESIN EKSEKUSI PERINTAH ---
    private class CommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String aiCommand = intent.getStringExtra("command_code");
            if (aiCommand == null) return;
            executeAction(aiCommand);
        }
    }

    private void executeAction(String cmdBlock) {
        Log.d("NovaAction", "Mengeksekusi Kode: " + cmdBlock);
        try {
            if (cmdBlock.startsWith("[CMD:OPEN:")) {
                String appName = cmdBlock.replace("[CMD:OPEN:", "").replace("]", "").toLowerCase().trim();
                openAppByName(appName);
            } 
            else if (cmdBlock.startsWith("[CMD:YOUTUBE:")) {
                String query = cmdBlock.replace("[CMD:YOUTUBE:", "").replace("]", "").trim();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            else if (cmdBlock.startsWith("[CMD:SHOPEE:")) {
                String query = cmdBlock.replace("[CMD:SHOPEE:", "").replace("]", "").trim();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("shopee://search?keyword=" + Uri.encode(query)));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(intent); } catch (Exception e) {
                    // Fallback ke browser jika aplikasi Shopee tidak ada
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://shopee.co.id/search?keyword=" + Uri.encode(query)));
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(webIntent);
                }
            }
            else if (cmdBlock.equals("[CMD:SCROLL_DOWN]")) {
                performGlobalAction(GLOBAL_ACTION_HOME); // Fallback ringan
                swipe(500, 1500, 500, 300);
            }
            else if (cmdBlock.equals("[CMD:HOME]")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
            else if (cmdBlock.equals("[CMD:BACK]")) {
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
        } catch (Exception e) {
            Log.e("NovaAction", "Gagal eksekusi: " + e.getMessage());
        }
    }

    private void openAppByName(String appName) {
        PackageManager pm = getPackageManager();
        String targetPackage = "";
        if (appName.contains("wa") || appName.contains("whatsapp")) targetPackage = "com.whatsapp";
        else if (appName.contains("youtube")) targetPackage = "com.google.android.youtube";
        else if (appName.contains("ig") || appName.contains("instagram")) targetPackage = "com.instagram.android";
        else if (appName.contains("tiktok")) targetPackage = "com.zhiliaoapp.musically";
        else if (appName.contains("chrome")) targetPackage = "com.android.chrome";
        
        if (!targetPackage.isEmpty()) {
            Intent launchIntent = pm.getLaunchIntentForPackage(targetPackage);
            if (launchIntent != null) {
                startActivity(launchIntent);
            } else {
                Toast.makeText(this, "Aplikasi tidak ditemukan", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void swipe(int startX, int startY, int endX, int endY) {
        Path path = new Path();
        path.moveTo(startX, startY);
        path.lineTo(endX, endY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        GestureDescription gestureDescription = builder.addStroke(new GestureDescription.StrokeDescription(path, 10, 400)).build();
        dispatchGesture(gestureDescription, null, null);
    }
}
