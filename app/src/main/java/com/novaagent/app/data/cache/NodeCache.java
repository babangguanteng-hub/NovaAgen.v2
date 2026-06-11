package com.novaagent.app.data.cache;

import android.util.Log;

import org.json.JSONArray;

/**
 * NodeCache bertugas menyimpan representasi layar (JSON) terakhir.
 * Jika layar belum berubah secara signifikan, kita tidak perlu
 * melakukan ekstraksi AccessibilityNodeInfo yang berat di memori.
 */
public class NodeCache {
    private static final String TAG = "NodeCache";
    private static final long TTL_MS = 1500; // Cache valid selama 1.5 detik

    private JSONArray cachedScreenData;
    private long lastCacheTime = 0;

    public void saveCache(JSONArray screenData) {
        this.cachedScreenData = screenData;
        this.lastCacheTime = System.currentTimeMillis();
        Log.d(TAG, "NodeCache diperbarui. Ukuran elemen: " + screenData.length());
    }

    public JSONArray getValidCache() {
        if (cachedScreenData != null && (System.currentTimeMillis() - lastCacheTime) < TTL_MS) {
            Log.d(TAG, "Menggunakan NodeCache (Bypass Traversal).");
            return cachedScreenData;
        }
        return null;
    }

    public void invalidate() {
        cachedScreenData = null;
        lastCacheTime = 0;
        Log.d(TAG, "NodeCache dibersihkan.");
    }
}
