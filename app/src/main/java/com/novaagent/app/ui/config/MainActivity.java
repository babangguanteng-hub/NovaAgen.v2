package com.novaagent.app.ui.config;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.novaagent.app.R;
import com.novaagent.app.services.accessibility.NovaAccessibilityService;
import com.novaagent.app.services.foreground.NovaForegroundService;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private static final int PERMISSION_REQ_CODE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("nova_config", MODE_PRIVATE);

        EditText etApiKey = findViewById(R.id.etApiKey);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnBattery = findViewById(R.id.btnBattery);
        Button btnPermissions = findViewById(R.id.btnPermissions);

        etApiKey.setText(prefs.getString("groq_api_key", ""));

        btnSave.setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            if(!key.isEmpty()){
                prefs.edit().putString("groq_api_key", key).apply();
                Toast.makeText(this, "API Key Tersimpan!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "API Key kosong!", Toast.LENGTH_SHORT).show();
            }
        });

        btnBattery.setOnClickListener(v -> requestBatteryOptimization());
        btnPermissions.setOnClickListener(v -> startPermissionWaterfall());
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                startActivity(intent);
                Toast.makeText(this, "Pilih 'Izinkan' agar Nova tidak mati sendiri.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Baterai sudah dibebaskan. Nova aman!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // --- ALUR PERIZINAN BERTAHAP (WATERFALL) ---

    private void startPermissionWaterfall() {
        String apiKey = prefs.getString("groq_api_key", "");
        if(apiKey.isEmpty()) {
            Toast.makeText(this, "Isi dan Simpan API Key dulu!", Toast.LENGTH_LONG).show();
            return;
        }

        // TAHAP 1: Minta Popup Izin Resmi Android (Mic & Notif)
        if (requiresRuntimePermissions()) {
            requestRuntimePermissions();
            return; // Berhenti disini, lanjut setelah user klik Allow/Deny
        }

        // TAHAP 2: Minta Izin Mengambang (Overlay)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "IZINKAN: Ditampilkan di Atas Aplikasi Lain", Toast.LENGTH_LONG).show();
            return;
        }

        // TAHAP 3: Minta Izin Aksesibilitas (Mata & Tangan AI)
        if (!isAccessibilityServiceEnabled(this, NovaAccessibilityService.class)) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "AKTIFKAN: Nova Agent di menu Aksesibilitas", Toast.LENGTH_LONG).show();
            return;
        }

        // TAHAP 4: Semua Diizinkan! Nyalakan Mesin.
        startNovaService();
    }

    private boolean requiresRuntimePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return true;
        }
        return false;
    }

    private void requestRuntimePermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), PERMISSION_REQ_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQ_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                // Lanjut ke tahap berikutnya (Overlay / Accessibility)
                startPermissionWaterfall(); 
            } else {
                Toast.makeText(this, "Nova BUTUH Izin Mikrofon untuk mendengar perintah Anda!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        String expectedComponentName = context.getPackageName() + "/" + accessibilityService.getName();
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null) return false;
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equalsIgnoreCase(expectedComponentName)) return true;
        }
        return false;
    }

    private void startNovaService() {
        Intent serviceIntent = new Intent(this, NovaForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "SISTEM NOVA ONLINE! \uD83D\uDE80", Toast.LENGTH_SHORT).show();
        finish();
    }
}
