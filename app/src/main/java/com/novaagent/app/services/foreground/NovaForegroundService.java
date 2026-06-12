package com.novaagent.app.services.foreground;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.core.engine.AutonomousLoopEngine;
import com.novaagent.app.ui.bubble.BubbleViewModel;
import com.novaagent.app.ui.bubble.FloatingBubbleView;

public class NovaForegroundService extends Service {
    private static final String CHANNEL_ID = "NovaAgentChannel";
    private AutonomousLoopEngine loopEngine;
    private FloatingBubbleView floatingBubbleView;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // 1. CARA BARU: Ciptakan Otak dan langsung beri akses data HP (Context)
        loopEngine = new AutonomousLoopEngine(this.getApplicationContext());
        
        // 2. Wajib Daftarkan Otak ini agar bisa dipanggil oleh Gelembung (Bubble)
        try {
            ServiceLocator.getInstance().register(AutonomousLoopEngine.class, loopEngine);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 3. Tampilkan Bubble UI
        BubbleViewModel viewModel = new BubbleViewModel();
        floatingBubbleView = new FloatingBubbleView(this.getApplicationContext(), viewModel);
        floatingBubbleView.show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Nova Agent Aktif")
                .setContentText("Sistem Otonom AI siap menerima perintah.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Ikon mikrofon bawaan OS
                .build();

        startForeground(1, notification);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBubbleView != null) {
            floatingBubbleView.remove();
        }
        try {
            ServiceLocator.getInstance().register(AutonomousLoopEngine.class, null);
        } catch (Exception e) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Nova Agent Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
