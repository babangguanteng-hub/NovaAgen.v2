package com.novaagent.app.services.foreground;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.core.engine.AutonomousLoopEngine;
import com.novaagent.app.infrastructure.system.NovaWatchdog;
import com.novaagent.app.infrastructure.system.RecoveryManager;
import com.novaagent.app.ui.bubble.BubbleViewModel;
import com.novaagent.app.ui.bubble.FloatingBubbleView;

public class NovaForegroundService extends Service {
    private static final String TAG = "NovaForegroundService";
    private static final String CHANNEL_ID = "nova_agent_core_channel";
    private static final int NOTIFICATION_ID = 9991;

    private NovaWatchdog watchdog;
    private RecoveryManager recoveryManager;
    private AutonomousLoopEngine loopEngine;
    
    // UI Komponen
    private BubbleViewModel bubbleViewModel;
    private FloatingBubbleView floatingBubbleView;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "NovaForegroundService diciptakan.");
        createNotificationChannel();
        
        // 1. Inisialisasi Otak
        loopEngine = new AutonomousLoopEngine();
        loopEngine.prepareEngine(this.getApplicationContext());
        ServiceLocator.getInstance().register(AutonomousLoopEngine.class, loopEngine);

        // 2. Inisialisasi Sistem Pertahanan
        recoveryManager = new RecoveryManager(this);
        watchdog = new NovaWatchdog();
        ServiceLocator.getInstance().register(NovaWatchdog.class, watchdog);
        ServiceLocator.getInstance().register(RecoveryManager.class, recoveryManager);
        watchdog.startMonitoring();

        // 3. Inisialisasi UI Melayang
        bubbleViewModel = new BubbleViewModel();
        floatingBubbleView = new FloatingBubbleView(this.getApplicationContext(), bubbleViewModel);
        floatingBubbleView.show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Nova Agent Aktif")
                .setContentText("Sistem otonom sedang berjaga di latar belakang.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY; 
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "Nova Agent Core Service", NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "NovaForegroundService dihancurkan.");
        if (watchdog != null) watchdog.stopMonitoring();
        if (recoveryManager != null) recoveryManager.destroy();
        if (floatingBubbleView != null) floatingBubbleView.remove();
        if (bubbleViewModel != null) bubbleViewModel.destroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
