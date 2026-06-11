package com.novaagent.app.core.engine;

import android.util.Log;

import com.novaagent.app.data.model.ActionVerificationResult;
import com.novaagent.app.data.model.UnifiedScreenContext;

/**
 * Algoritma cerdas untuk menilai apakah aksi yang dieksekusi (contoh: Tap)
 * benar-benar menyebabkan perubahan pada antarmuka Android (contoh: halaman berpindah).
 */
public class ActionVerificationScore {
    private static final String TAG = "ActionVerificationScore";
    
    // Threshold minimal agar aksi dianggap sukses
    private static final double THRESHOLD_SUCCESS = 0.7;

    /**
     * Membandingkan kondisi layar SEBELUM aksi dengan kondisi layar SESUDAH aksi.
     */
    public ActionVerificationResult verify(
            String actionName, 
            UnifiedScreenContext contextBefore, 
            UnifiedScreenContext contextAfter, 
            boolean osGestureCallbackSuccess) {

        double score = 0.0;
        StringBuilder reasons = new StringBuilder();

        // 1. Sinyal dasar dari OS Android (Dispatcher mengembalikan sukses)
        if (osGestureCallbackSuccess) {
            score += 0.2;
            reasons.append("[OS Callback OK] ");
        }

        // Jika salah satu konteks null, kita tidak bisa melakukan perbandingan layout
        if (contextBefore == null || contextAfter == null) {
            boolean passed = score >= 0.2 && actionName.equals("home"); // Home biasanya tidak perlu cek layout detail
            return new ActionVerificationResult(passed, score, "Konteks layar tidak lengkap. Skor: " + score);
        }

        // 2. Cek pergantian Package (Aplikasi berpindah)
        if (!contextBefore.packageName.equals(contextAfter.packageName)) {
            score += 0.5; // Ini sinyal sangat kuat bahwa layar berpindah total
            reasons.append("[Package Changed] ");
        }

        // 3. Cek perubahan jumlah elemen visual (Layout berubah)
        int elementDiff = Math.abs(contextBefore.elements.size() - contextAfter.elements.size());
        if (elementDiff > 5) {
            score += 0.3;
            reasons.append("[Layout Massively Changed] ");
        } else if (elementDiff > 0) {
            score += 0.1;
            reasons.append("[Layout Slightly Changed] ");
        }

        // Tentukan hasil final
        boolean isVerified = score >= THRESHOLD_SUCCESS;
        
        Log.d(TAG, "Hasil Verifikasi: " + isVerified + " | Skor: " + score + " | Alasan: " + reasons.toString());
        
        return new ActionVerificationResult(isVerified, score, reasons.toString());
    }
}
