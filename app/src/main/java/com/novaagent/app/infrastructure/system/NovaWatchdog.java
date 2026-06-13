package com.novaagent.app.infrastructure.system;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;

/**
 * Detak Jantung Sistem. 
 * Memastikan setiap komponen penting tetap "hidup". 
 * Jika tidak ada 'ping' dalam 15 detik, sistem dianggap Deadlocked.
 */
public class NovaWatchdog {
    private static final String TAG = "NovaWatchdog";
    private static final long HEARTBEAT_INTERVAL = 5000;
    private static final long TIMEOUT_THRESHOLD = 15000;
    
    private HandlerThread watchdogThread;
    private Handler handler;
    private long lastHeartbeatTime = 0;
    private boolean isRunning = false;

    public void start() {
        if (isRunning) return;
        watchdogThread = new HandlerThread("WatchdogThread");
        watchdogThread.start();
        handler = new Handler(watchdogThread.getLooper());
        isRunning = true;
        
        lastHeartbeatTime = SystemClock.elapsedRealtime();
        handler.post(watchdogRunnable);
    }

    public void stop() {
        isRunning = false;
        if (watchdogThread != null) watchdogThread.quitSafely();
    }

    // Dipanggil oleh Service/Engine sebagai bukti "Saya masih bekerja"
    public void ping() {
        lastHeartbeatTime = SystemClock.elapsedRealtime();
    }

    private final Runnable watchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) return;
            
            long currentTime = SystemClock.elapsedRealtime();
            if (currentTime - lastHeartbeatTime > TIMEOUT_THRESHOLD) {
                Log.e(TAG, "CRITICAL: Jantung aplikasi berhenti! Memicu Protokol Recovery.");
                // Mengirim sinyal darurat ke RecoveryManager
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.RECOVERY_TRIGGERED, 3));
            }
            handler.postDelayed(this, HEARTBEAT_INTERVAL);
        }
    };
}
