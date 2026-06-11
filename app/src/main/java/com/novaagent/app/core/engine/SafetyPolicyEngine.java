package com.novaagent.app.core.engine;

import android.util.Log;

import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.data.model.UnifiedScreenContext;

import java.util.regex.Pattern;

/**
 * Mesin Kebijakan Keamanan.
 * Bertugas memfilter perintah dari LLM Groq sebelum dieksekusi oleh SystemActionInjector.
 * Mencegah agen mentransfer uang, menghapus data, atau mengubah setelan sensitif tanpa izin user.
 */
public class SafetyPolicyEngine {
    private static final String TAG = "SafetyPolicyEngine";

    // Daftar kata kunci berbahaya (Regex)
    private static final Pattern BLACKLIST_PATTERN = Pattern.compile(
            "(transfer|kirim uang|bayar|checkout|factory reset|hapus semua|uninstall|copot pemasangan|" +
            "developer options|opsi pengembang|unknown sources|sumber tidak dikenal|password|kata sandi|pin)",
            Pattern.CASE_INSENSITIVE
    );

    public SafetyPolicyEngine() {
        Log.d(TAG, "SafetyPolicyEngine aktif dan siap memantau.");
    }

    /**
     * Mengevaluasi tingkat bahaya suatu perintah berdasarkan teks di layar saat ini
     * dan teks target yang ingin diklik/diketik oleh Agen.
     */
    public void evaluateAction(ActionCommandDto command, UnifiedScreenContext screenContext) {
        boolean isSafe = true;
        String reason = "";

        // 1. Cek parameter teks dari perintah agen itu sendiri
        if (command.text != null && BLACKLIST_PATTERN.matcher(command.text).find()) {
            isSafe = false;
            reason = "Perintah mengandung kata kunci berbahaya: " + command.text;
        }

        // 2. Cek konteks layar di sekitar area yang akan di-tap (jika ini aksi tap)
        if (isSafe && command.action.equals("tap") && screenContext != null) {
            String elementText = screenContext.getTextAtCoordinate(command.x, command.y);
            if (elementText != null && BLACKLIST_PATTERN.matcher(elementText).find()) {
                isSafe = false;
                reason = "Agen mencoba mengklik elemen sensitif: " + elementText;
            }
        }

        if (isSafe) {
            Log.d(TAG, "Aksi aman (" + command.action + "). Diteruskan ke Eksekutor.");
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_VALIDATED, command));
        } else {
            Log.e(TAG, "AKSI BERBAHAYA DIBLOKIR: " + reason);
            // Minta konfirmasi pengguna via suara/UI
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.RECOVERY_TRIGGERED, "USER_CONFIRMATION_NEEDED|" + reason));
            // Ubah state agen jadi menunggu
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "USER_CONFIRMATION"));
        }
    }
}
