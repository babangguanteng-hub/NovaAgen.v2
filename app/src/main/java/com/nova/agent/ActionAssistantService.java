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
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

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
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override
    public void onInterrupt() {}

    private class CommandReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String aiCommand = intent.getStringExtra("command_code");
            if (aiCommand != null) handleAICommand(aiCommand);
        }
    }

    private void handleAICommand(String cmd) {
        // 1. OPEN APP DENGAN PARAMETER KHUSUS
        if (cmd.startsWith("[OPEN_URL:")) {
            String url = extractValue(cmd, "[OPEN_URL:", "]");
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } 
        // 2. SEARCH DI SHOPEE / YOUTUBE / GOOGLE
        else if (cmd.startsWith("[SEARCH_YOUTUBE:")) {
            String query = extractValue(cmd, "[SEARCH_YOUTUBE:", "]");
            Intent intent = new Intent(Intent.ACTION_SEARCH);
            intent.setPackage("com.google.android.youtube");
            intent.putExtra("query", query);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        else if (cmd.startsWith("[SEARCH_SHOPEE:")) {
            String query = extractValue(cmd, "[SEARCH_SHOPEE:", "]");
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("shopeeid://search?keyword=" + query));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        // 3. LAUNCH APPS
        else if (cmd.startsWith("[OPEN_APP:")) {
            String pkg = extractValue(cmd, "[OPEN_APP:", "]");
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        }
    }

    private String extractValue(String fullCmd, String prefix, String suffix) {
        try {
            int start = fullCmd.indexOf(prefix) + prefix.length();
            int end = fullCmd.indexOf(suffix, start);
            return fullCmd.substring(start, end).trim();
        } catch (Exception e) { return ""; }
    }
}
