package com.novaagent.app.core.engine;

import com.novaagent.app.data.model.ActionVerificationResult;
import com.novaagent.app.data.model.UnifiedScreenContext;
import com.novaagent.app.data.model.UnifiedScreenContext.ScreenElement;

/**
 * Penilai Kesuksesan Aksi (Menyelesaikan Defect M001).
 * Menggunakan perbandingan bobot visual karakter, bukan sekadar jumlah array elemen.
 */
public class ActionVerificationScore {
    private static final double THRESHOLD_SUCCESS = 0.7;

    public ActionVerificationResult verify(String actionName, UnifiedScreenContext ctxBefore, UnifiedScreenContext ctxAfter, boolean gestureSuccess) {
        double score = 0.0;
        StringBuilder reasons = new StringBuilder();

        if (gestureSuccess) {
            score += 0.2;
            reasons.append("[OS Terima Gestur] ");
        }

        if (ctxBefore == null || ctxAfter == null) {
            boolean passed = score >= 0.2 && (actionName.contains("volume") || actionName.equals("home"));
            return new ActionVerificationResult(passed, score, "Bypass Konteks. Skor: " + score);
        }

        // Skor Mutlak: Perpindahan Aplikasi
        if (!ctxBefore.packageName.equals(ctxAfter.packageName)) {
            score += 0.6;
            reasons.append("[App Pindah: ").append(ctxAfter.packageName).append("] ");
        } else {
            // Skor Relatif: Bobot Visual UI
            int weightBefore = calculateVisualWeight(ctxBefore);
            int weightAfter = calculateVisualWeight(ctxAfter);
            
            int diff = Math.abs(weightBefore - weightAfter);
            double percentageChanged = (weightBefore == 0) ? 1.0 : (double) diff / weightBefore;

            if (percentageChanged > 0.4) {
                score += 0.5;
                reasons.append("[Layar Berubah Drastis: ").append(Math.round(percentageChanged * 100)).append("%] ");
            } else if (percentageChanged > 0.1 || "type".equals(actionName) || "scroll".equals(actionName)) {
                score += 0.3;
                reasons.append("[Perubahan Relevan: ").append(Math.round(percentageChanged * 100)).append("%] ");
            } else {
                reasons.append("[UI Statik] ");
            }
        }

        boolean isVerified = score >= THRESHOLD_SUCCESS;
        return new ActionVerificationResult(isVerified, score, reasons.toString());
    }

    private int calculateVisualWeight(UnifiedScreenContext ctx) {
        if (ctx == null || ctx.elements == null) return 0;
        int weight = 0;
        for (ScreenElement el : ctx.elements) {
            if (el.text != null) weight += el.text.length();
        }
        return weight;
    }
}
