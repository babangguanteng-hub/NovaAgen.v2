package com.novaagent.app.infrastructure.system;

import android.util.Log;

import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * NovaWatchdog memantau kesehatan komponen sistem setiap 5 detik.
 * Jika menemukan anomali (misal waktu tunggu terlalu lama), ia akan memicu RecoveryManager.
 */
public class NovaWatchdog {
    private static final String TAG = "NovaWatchdog";
    private final ScheduledExecutorService scheduler;
    
    private long lastAccessibilityHeartbeat = 0;
    private long lastLoopEngineActivity = 0;
    
    private static final long MAX_ACCESSIBILITY_DEAD_TIME_MS = 15000; // 15 detik

    public NovaWatchdog() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    public void startMonitoring() {
        Log.d(TAG, "NovaWatchdog mulai berpatroli...");
        updateHeartbeat();
        
        // Cek kesehatan setiap 5 detik
        scheduler.scheduleAtFixedRate(this::checkSystemHealth, 5, 5, TimeUnit.SECONDS);
    }

    public void updateHeartbeat() {
        lastAccessibilityHeartbeat = System.currentTimeMillis();
        lastLoopEngineActivity = System.currentTimeMillis();
    }

    private void checkSystemHealth() {
        long now = System.currentTimeMillis();
        
        // 1. Cek apakah Accessibility Service mati (di-kill OS)
        if (now - lastAccessibilityHeartbeat > MAX_ACCESSIBILITY_DEAD_TIME_MS) {
            Log.e(TAG, "WATCHDOG ALERT: Accessibility Service tidak merespon (Mati)!");
            triggerRecovery("ACCESSIBILITY_KILLED");
            // Reset heartbeat agar tidak looping spam recovery
            lastAccessibilityHeartbeat = now; 
        }
    }

    private void triggerRecovery(String reason) {
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.RECOVERY_TRIGGERED, reason));
    }

    public void stopMonitoring() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            Log.d(TAG, "NovaWatchdog berhenti berpatroli.");
        }
    }
}
