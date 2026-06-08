package com.nova.agent;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class ActionAssistantService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Logika asisten aksi pintar berjalan di sini
    }

    @Override
    public void onInterrupt() {
        // Dipanggil saat sistem menghentikan layanan aksesibilitas
    }
}
