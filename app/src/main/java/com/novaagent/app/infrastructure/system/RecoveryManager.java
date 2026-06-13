package com.novaagent.app.infrastructure.system;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;

/**
 * Dokter Penyelamat. 
 * Mengeksekusi strategi pemulihan berdasarkan level kerusakan sistem.
 */
public class RecoveryManager implements EventBus.EventListener {
    private static final String TAG = "RecoveryManager";
    private final Context context;

    public RecoveryManager(Context context) {
        this.context = context;
        EventBus.getInstance().subscribe(AgentEvent.EventType.RECOVERY_TRIGGERED, this);
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (event.type == AgentEvent.EventType.RECOVERY_TRIGGERED) {
            int level = (event.payload instanceof Integer) ? (int) event.payload : 3;
            executeRecovery(level);
        }
    }

    private void executeRecovery(int level) {
        Log.w(TAG, "Protokol Recovery Level: " + level);
        switch (level) {
            case 1: // Soft Reset
                Log.i(TAG, "Level 1: Reset Cache.");
                break;
            case 2: // Service Rebind
                Log.i(TAG, "Level 2: Rebinding Service.");
                break;
            case 3: // Hard Recovery (Paling Parah)
                Log.e(TAG, "Level 3: Hard Restart. Aksesibilitas mati!");
                promptUserForAccessibility();
                break;
        }
    }

    private void promptUserForAccessibility() {
        // Melempar user ke Settings agar bisa re-enable service secara manual
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Gagal meluncurkan intent darurat", e);
        }
    }
}
