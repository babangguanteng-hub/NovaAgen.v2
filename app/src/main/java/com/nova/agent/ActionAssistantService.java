package com.nova.agent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Path;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
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
            
            new Thread(new Runnable() {
                @Override
                public void run() { executeMacro(aiCommand); }
            }).start();
        }
    }

    private void executeMacro(String cmdBlock) {
        try {
            if (cmdBlock.contains("[CMD:OPEN:")) {
                String app = extractValue(cmdBlock, "[CMD:OPEN:", "]");
                openSmartAppDirectly(app);
                Thread.sleep(2500); 
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
            
            if (cmdBlock.contains("[CMD:CLICK:")) {
                String textToClick = extractValue(cmdBlock, "[CMD:CLICK:", "]");
                waitForNodeAndClick(textToClick, 4000);
            }

            if (cmdBlock.contains("[CMD:TAP_COORDINATE:")) {
                String coords = extractValue(cmdBlock, "[CMD:TAP_COORDINATE:", "]");
                String[] parts = coords.split(",");
                if (parts.length == 2) {
                    int x = Integer.parseInt(parts[0].trim());
                    int y = Integer.parseInt(parts[1].trim());
                    tapAtCoordinate(x, y);
                    Thread.sleep(500);
                }
            }

            if (cmdBlock.contains("[CMD:SCROLL_DOWN]")) {
                swipe(500, 1400, 500, 400, 350);
            }
            if (cmdBlock.contains("[CMD:SCROLL_UP]")) {
                swipe(500, 400, 500, 1400, 350);
            }

            if (cmdBlock.contains("[CMD:FLASHLIGHT:ON]")) {
                toggleFlashlight(true);
            }
            if (cmdBlock.contains("[CMD:FLASHLIGHT:OFF]")) {
                toggleFlashlight(false);
            }
            
            if (cmdBlock.contains("[CMD:HOME]")) {
                performGlobalAction(GLOBAL_ACTION_HOME);
            }
            if (cmdBlock.contains("[CMD:BACK]")) {
                performGlobalAction(GLOBAL_ACTION_BACK);
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

    private void openSmartAppDirectly(String appNameRequested) {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables = pm.queryIntentActivities(mainIntent, 0);

        String requestedLower = appNameRequested.toLowerCase().trim();
        if (requestedLower.equals("wa")) requestedLower = "whatsapp";
        if (requestedLower.equals("ig")) requestedLower = "instagram";

        for (ResolveInfo info : launchables) {
            String installedName = info.loadLabel(pm).toString().toLowerCase();
            if (installedName.contains(requestedLower)) {
                Intent intent = pm.getLaunchIntentForPackage(info.activityInfo.packageName);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    return;
                }
            }
        }
    }

    private void toggleFlashlight(boolean turnOn) {
        try {
            CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = camManager.getCameraIdList()[0];
            camManager.setTorchMode(cameraId, turnOn);
        } catch (Exception e) {
            Log.e("NovaAction", "Gagal mengontrol Senter: " + e.getMessage());
        }
    }

    private void tapAtCoordinate(int x, int y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        dispatchGesture(builder.build(), null, null);
    }

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

    private boolean waitForNodeAndClick(String text, int timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && clickNodeByText(root, text)) return true;
            Thread.sleep(300);
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
