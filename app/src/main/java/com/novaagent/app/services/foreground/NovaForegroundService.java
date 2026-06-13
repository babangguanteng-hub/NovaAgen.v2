package com.novaagent.app.services.foreground;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.data.cache.ScreenCache;
import com.novaagent.app.infrastructure.ocr.MlKitOcrEngine;
import com.novaagent.app.infrastructure.system.NovaWatchdog;
import com.novaagent.app.ui.bubble.FloatingBubbleView;

public class NovaForegroundService extends Service {
    private static final String CHANNEL_ID = "NovaAgentChannel";
    private FloatingBubbleView floatingBubbleView;
    private MediaProjectionWrapper mediaProjectionWrapper;
    private NovaWatchdog watchdog;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        // 1. Tampilkan Bubble UI (Sekarang hanya butuh 1 parameter: Context)
        floatingBubbleView = new FloatingBubbleView(this);
        floatingBubbleView.show();

        // 2. Hidupkan Sistem Imun (Watchdog)
        watchdog = new NovaWatchdog();
        watchdog.start();
        ServiceLocator.getInstance().register(NovaWatchdog.class, watchdog);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Wajib untuk Android 8.0+: Menampilkan notifikasi agar layanan tidak dibunuh OS
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Nova Agent Aktif")
                .setContentText("Sistem Otonom Berjalan di Latar Belakang")
                .setSmallIcon(android.R.drawable.ic_menu_camera) // Menggunakan ikon bawaan OS
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(1, notification);

        // 3. Tangkap Token Tangkapan Layar dari MainActivity
        if (intent != null && intent.getIntExtra("code", 0) != 0) {
            int resultCode = intent.getIntExtra("code", 0);
            Intent data = intent.getParcelableExtra("data");
            
            if (mediaProjectionWrapper == null) {
                MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (projectionManager != null) {
                    MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                    
                    // Merakit Sistem Mata (OCR & Cache)
                    MlKitOcrEngine ocrEngine = new MlKitOcrEngine();
                    ScreenCache screenCache = new ScreenCache();
                    
                    mediaProjectionWrapper = new MediaProjectionWrapper(this, mediaProjection, ocrEngine, screenCache);
                    mediaProjectionWrapper.start();
                    
                    // Daftarkan ke Locator agar bisa dipanggil oleh Otak (LoopEngine)
                    ServiceLocator.getInstance().register(MediaProjectionWrapper.class, mediaProjectionWrapper);
                }
            }
        }

        return START_STICKY; // Jika dibunuh OS, otomatis dihidupkan lagi
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBubbleView != null) floatingBubbleView.remove();
        if (mediaProjectionWrapper != null) mediaProjectionWrapper.stop();
        if (watchdog != null) watchdog.stop();
        
        // Bersihkan memori dari Service Locator
        ServiceLocator.getInstance().register(NovaWatchdog.class, null);
        ServiceLocator.getInstance().register(MediaProjectionWrapper.class, null);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // Kita tidak menggunakan IPC binding standar
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Nova Agent Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
