package com.novaagent.app.infrastructure.system;

import android.content.Context;
import android.util.Log;

import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;

/**
 * Bertanggung jawab menangani pemulihan sistem jika terjadi crash atau freeze
 * pada komponen Accessibility, OCR, atau Jaringan.
 */
public class RecoveryManager implements EventBus.EventListener {
    private static final String TAG = "RecoveryManager";
    private final Context appContext;

    public RecoveryManager(Context context) {
        this.appContext = context.getApplicationContext();
        EventBus.getInstance().subscribe(AgentEvent.EventType.RECOVERY_TRIGGERED, this);
        Log.d(TAG, "RecoveryManager diinisialisasi.");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (event.type == AgentEvent.EventType.RECOVERY_TRIGGERED) {
            String reason = (String) event.payload;
            Log.e(TAG, "Menerima sinyal RECOVERY_TRIGGERED. Alasan: " + reason);
            executeRecovery(reason);
        }
    }

    private void executeRecovery(String reason) {
        if (reason.contains("OCR_TIMEOUT") || reason.contains("NODE_READ_FAIL")) {
            executeLevel1Recovery();
        } else if (reason.contains("BUBBLE_LOST") || reason.contains("PROJECTION_DEAD")) {
            executeLevel2Recovery();
        } else if (reason.contains("ACCESSIBILITY_KILLED")) {
            executeLevel3Recovery();
        } else {
            Log.w(TAG, "Alasan recovery tidak dikenali, menjalankan Level 1 Default.");
            executeLevel1Recovery();
        }
    }

    // Level 1: Soft Reset (Clear Cache & Restart Logic Loop)
    private void executeLevel1Recovery() {
        Log.d(TAG, "Menjalankan LEVEL 1 RECOVERY (Soft Reset)...");
        // TODO: Panggil method clear() di ScreenCache dan NodeCache nanti
        // Mengirim sinyal ke LoopEngine untuk kembali ke IDLE
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "IDLE"));
    }

    // Level 2: Service Rebind (Restart Overlay & Projection)
    private void executeLevel2Recovery() {
        Log.d(TAG, "Menjalankan LEVEL 2 RECOVERY (Service Rebind)...");
        // TODO: Restart FloatingBubbleService / Rebind WindowManager
    }

    // Level 3: Hard Restart (Panggil ulang layanan secara paksa)
    private void executeLevel3Recovery() {
        Log.e(TAG, "Menjalankan LEVEL 3 RECOVERY (Hard Restart)... Membutuhkan intervensi Android OS.");
        // TODO: Minta ForegroundService untuk meluncurkan Intent darurat ke layar
    }
    
    public void destroy() {
        EventBus.getInstance().unsubscribe(AgentEvent.EventType.RECOVERY_TRIGGERED, this);
    }
}
