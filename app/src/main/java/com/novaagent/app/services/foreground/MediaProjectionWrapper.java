package com.novaagent.app.services.foreground;

import android.content.Context;
import android.util.Log;

/**
 * Placeholder / Kerangka Dasar untuk MediaProjection.
 * Saat ini mengamankan arsitektur agar Android 14 tidak crash ketika ForegroundService 
 * dideklarasikan dengan tipe "mediaProjection".
 * Akan diintegrasikan penuh dengan MlKitOcrEngine pada iterasi berikutnya.
 */
public class MediaProjectionWrapper {
    private static final String TAG = "MediaProjectionWrapper";
    private Context context;

    public MediaProjectionWrapper(Context context) {
        this.context = context;
        Log.d(TAG, "MediaProjectionWrapper disiapkan. Menunggu izin intent (nantinya).");
    }

    public void startCapture() {
        // Todo: Implementasi ImageReader & VirtualDisplay di sini untuk OCR
        Log.w(TAG, "Screen Capture belum diaktifkan secara penuh.");
    }

    public void stopCapture() {
        Log.d(TAG, "Screen Capture dihentikan.");
    }
}
