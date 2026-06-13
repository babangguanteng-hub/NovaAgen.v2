package com.novaagent.app.data.cache;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Filter Visual Layar menggunakan Algoritma pHash (Perceptual Hash) Ekstrem Ringan.
 * Memeriksa apakah gambar layar berubah signifikan sebelum mengumpankannya ke ML Kit.
 */
public class ScreenCache {
    private long lastScreenHash = 0L;

    /**
     * Membandingkan Bitmap baru dengan hash layar sebelumnya.
     * Mengembalikan TRUE jika layar dianggap sama (statik), FALSE jika berubah.
     */
    public boolean isDuplicate(Bitmap currentBitmap) {
        if (currentBitmap == null || currentBitmap.isRecycled()) return false;

        long currentHash = computeAHash(currentBitmap);
        
        // Cek hamming distance (perbedaan bit). Jika selisih bit <= 2, dianggap gambar sama.
        int hammingDistance = Long.bitCount(lastScreenHash ^ currentHash);
        boolean isSame = hammingDistance <= 2;

        if (!isSame) {
            lastScreenHash = currentHash; // Update cache dengan hash baru
        }
        
        return isSame;
    }

    public void invalidate() {
        lastScreenHash = 0L;
    }

    /**
     * Algoritma Average Hash (AHash) Sangat Cepat (O(1) Memory).
     * Mereduksi bitmap menjadi grid 8x8 secara mental (tanpa alokasi memori baru),
     * lalu menghitung rata-rata abu-abu (grayscale) untuk menjadi signature 64-bit.
     */
    private long computeAHash(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // Sampling grid 8x8
        int stepX = Math.max(1, width / 8);
        int stepY = Math.max(1, height / 8);
        
        int[] pixels = new int[64];
        long totalGray = 0;
        int idx = 0;

        // Kumpulkan 64 titik sampel
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int pxX = Math.min(x * stepX, width - 1);
                int pxY = Math.min(y * stepY, height - 1);
                
                int color = bitmap.getPixel(pxX, pxY);
                // Ekstrak luminance (grayscale kasar: R+G+B / 3)
                int gray = ((Color.red(color) + Color.green(color) + Color.blue(color)) / 3);
                
                pixels[idx++] = gray;
                totalGray += gray;
            }
        }

        int avgGray = (int) (totalGray / 64);
        long hash = 0L;

        // Bangun hash 64-bit: Bit 1 jika lebih terang dari rata-rata, 0 jika lebih gelap
        for (int i = 0; i < 64; i++) {
            if (pixels[i] >= avgGray) {
                hash |= (1L << i);
            }
        }
        return hash;
    }
}
