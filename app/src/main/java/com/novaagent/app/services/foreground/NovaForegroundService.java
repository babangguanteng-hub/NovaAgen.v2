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
import com.novaagent.app.core.engine.AutonomousLoopEngine;
import com.novaagent.app.data.cache.ScreenCache;
import com.novaagent.app.infrastructure.ocr.MlKitOcrEngine;
import com.novaagent.app.infrastructure.system.NovaWatchdog;
import com.novaagent.app.infrastructure.system.NovaVoiceEngine;
import com.novaagent.app.ui.bubble.FloatingBubbleView;

public class NovaForegroundService extends Service {
    private static final String CHANNEL_ID = "NovaAgentChannel";
    private FloatingBubbleView floatingBubbleView;
    private MediaProjectionWrapper mediaProjectionWrapper;
    private NovaWatchdog watchdog;
    private AutonomousLoopEngine loopEngine;
    private NovaVoiceEngine voiceEngine;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        
        // 1. Hidupkan Mesin Suara (Pita Suara)
        voiceEngine = new NovaVoiceEngine(this);

        // 2. Hidupkan Otak AI
        loopEngine = new AutonomousLoopEngine(this);
        ServiceLocator.getInstance().register(AutonomousLoopEngine.class, loopEngine);

        // 3. Tampilkan UI
        floatingBubbleView = new FloatingBubbleView(this);
        floatingBubbleView.show();

        // 4. Hidupkan Watchdog
        watchdog = new NovaWatchdog();
        watchdog.start();
        ServiceLocator.getInstance().register(NovaWatchdog.class, watchdog);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Nova Agent")
                .setContentText("Sistem Otonom Berjalan")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(1, notification);

        if (intent != null && intent.getIntExtra("code", 0) != 0) {
            int resultCode = intent.getIntExtra("code", 0);
            Intent data = intent.getParcelableExtra("data");
            
            if (mediaProjectionWrapper == null) {
                MediaProjectionManager projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (projectionManager != null) {
                    MediaProjection mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                    MlKitOcrEngine ocrEngine = new MlKitOcrEngine();
                    ScreenCache screenCache = new ScreenCache();
                    mediaProjectionWrapper = new MediaProjectionWrapper(this, mediaProjection, ocrEngine, screenCache);
                    mediaProjectionWrapper.start();
                    ServiceLocator.getInstance().register(MediaProjectionWrapper.class, mediaProjectionWrapper);
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBubbleView != null) floatingBubbleView.remove();
        if (mediaProjectionWrapper != null) mediaProjectionWrapper.stop();
        if (watchdog != null) watchdog.stop();
        if (loopEngine != null) loopEngine.stopTask();
        if (voiceEngine != null) voiceEngine.shutdown(); // Matikan suara
        
        ServiceLocator.getInstance().register(NovaWatchdog.class, null);
        ServiceLocator.getInstance().register(MediaProjectionWrapper.class, null);
        ServiceLocator.getInstance().register(AutonomousLoopEngine.class, null);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Nova Agent", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
