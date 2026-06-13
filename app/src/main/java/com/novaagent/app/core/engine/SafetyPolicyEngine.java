package com.novaagent.app.core.engine;

import android.util.Log;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.data.model.UnifiedScreenContext;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Firewall Keamanan Otonom (Menyelesaikan Defect H001).
 * Mengevaluasi perintah LLM sebelum dieksekusi berdasarkan konteks paket aplikasi yang sedang terbuka.
 */
public class SafetyPolicyEngine {
    private static final String TAG = "SafetyPolicyEngine";

    // Pola Regex untuk Aksi Kritis
    private static final Pattern CRITICAL_KEYWORDS = Pattern.compile(
            "(transfer|kirim uang|bayar|checkout|factory reset|hapus semua|uninstall|" +
            "developer options|unknown sources|password|kata sandi|pin)",
            Pattern.CASE_INSENSITIVE
    );

    // Zona Merah: Aplikasi yang membutuhkan izin eksplisit dari pengguna
    private static final List<String> SENSITIVE_PACKAGES = Arrays.asList(
            "com.android.settings", 
            "com.google.android.packageinstaller", 
            "com.android.vending", 
            "com.bca", "com.mandiri", "com.gojek", "com.shopee", "com.dana"
    );

    public void evaluateAction(ActionCommandDto command, UnifiedScreenContext screenContext) {
        boolean isSafe = true;
        String blockReason = "";

        String currentPackage = (screenContext != null && screenContext.packageName != null) 
                                ? screenContext.packageName.toLowerCase() : "unknown";
        
        boolean isSensitiveContext = false;
        for (String pkg : SENSITIVE_PACKAGES) {
            if (currentPackage.contains(pkg)) {
                isSensitiveContext = true;
                break;
            }
        }

        // 1. Evaluasi Teks yang akan Diketik AI
        if (command.textToType != null && !command.textToType.isEmpty()) {
            if (CRITICAL_KEYWORDS.matcher(command.textToType).find()) {
                isSafe = false;
                blockReason = "AI mencoba mengetik kata sandi/perintah bahaya: " + command.textToType;
            }
        }

        // 2. Evaluasi Target Klik (Contextual Heuristics)
        if (isSafe && ("tap".equals(command.action) || "click".equals(command.action)) && screenContext != null) {
            String targetText = screenContext.getTextAtCoordinate(command.x, command.y);
            
            if (targetText != null && CRITICAL_KEYWORDS.matcher(targetText).find()) {
                if (isSensitiveContext) {
                    isSafe = false;
                    blockReason = "Mencegah klik pada '" + targetText + "' di aplikasi sensitif (" + currentPackage + ").";
                } else {
                    Log.w(TAG, "Peringatan: Klik pada '" + targetText + "' diizinkan karena tidak berada di zona merah (" + currentPackage + ").");
                }
            }
        }

        if (isSafe) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ACTION_VALIDATED, command));
        } else {
            Log.e(TAG, "BLOCKED BY FIREWALL: " + blockReason);
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.RECOVERY_TRIGGERED, 3)); // Memicu konfirmasi/reset
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "AKSI DIBLOKIR KEAMANAN"));
        }
    }
}
