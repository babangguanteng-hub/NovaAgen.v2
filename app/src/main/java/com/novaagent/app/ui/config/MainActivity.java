package com.novaagent.app.ui.config;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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

        // --- SISTEM PEMBACA BLACK BOX ---
        String crashLog = prefs.getString("last_crash_log", "");
        if (!crashLog.isEmpty()) {
            showCrashReportDialog(crashLog);
        }

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

    private void showCrashReportDialog(String crashLog) {
        new AlertDialog.Builder(this)
            .setTitle("⚠️ LAPORAN KERUSAKAN (CRASH)")
            .setMessage("Nova baru saja berhenti secara tidak normal. Laporan:\n\n" + crashLog)
            .setCancelable(false)
            .setPositiveButton("SALIN & TUTUP", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Crash Log", crashLog);
                clipboard.setPrimaryClip(clip);
                
                // Hapus log setelah disalin agar tidak muncul terus
                prefs.edit().remove("last_crash_log").apply();
                Toast.makeText(this, "Tersalin! Paste ke obrolan AI Anda.", Toast.LENGTH_LONG).show();
            })
            .show();
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
            } else {
                Toast.makeText(this, "Baterai sudah dibebaskan. Nova aman!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startPermissionWaterfall() {
        String apiKey = prefs.getString("groq_api_key", "");
        if(apiKey.isEmpty()) {
            Toast.makeText(this, "Isi dan Simpan API Key dulu!", Toast.LENGTH_LONG).show();
            return;
        }

        if (requiresRuntimePermissions()) {
            requestRuntimePermissions();
            return; 
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "IZINKAN: Ditampilkan di Atas Aplikasi Lain", Toast.LENGTH_LONG).show();
            return;
        }

        if (!isAccessibilityServiceEnabled(this, NovaAccessibilityService.class)) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "AKTIFKAN: Nova Agent di menu Aksesibilitas", Toast.LENGTH_LONG).show();
            return;
        }

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
            if (allGranted) startPermissionWaterfall(); 
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
