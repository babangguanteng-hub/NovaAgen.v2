package com.novaagent.app.data.model;

/**
 * Menyimpan hasil evaluasi pasca-aksi (setelah tap/swipe dilakukan).
 * Skor menentukan apakah agen perlu mencoba ulang aksi yang sama atau lanjut ke langkah berikutnya.
 */
public class ActionVerificationResult {
    public final boolean isVerified;
    public final double score; // 0.0 sampai 1.0
    public final String reason;

    public ActionVerificationResult(boolean isVerified, double score, String reason) {
        this.isVerified = isVerified;
        this.score = score;
        this.reason = reason;
    }
}
