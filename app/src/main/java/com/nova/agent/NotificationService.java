package com.nova.agent;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class NotificationService extends NotificationListenerService {
    public static boolean isServiceRunning = false;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        isServiceRunning = true;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isServiceRunning = false;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!sbn.getPackageName().equals("com.whatsapp")) return;

        Notification notification = sbn.getNotification();
        Bundle extras = notification.extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE);
        CharSequence textChar = extras.getCharSequence(Notification.EXTRA_TEXT);
        
        if (title != null && textChar != null) {
            String message = textChar.toString();
            Log.d("NovaNotif", "WA dari " + title + ": " + message);

            Intent intent = new Intent("com.nova.agent.READ_NOTIF");
            intent.putExtra("sender", title);
            intent.putExtra("message", message);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }
}
