package com.novaagent.app.ui.config;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.novaagent.app.R;
import com.novaagent.app.services.foreground.NovaForegroundService;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private MediaProjectionManager projectionManager;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        prefs = getSharedPreferences("nova_config", MODE_PRIVATE);

        // Meminta Izin Dasar (Notifikasi & Mikrofon)
        requestBasicPermissions();

        // Bind UI
        EditText apiKeyInput = findViewById(R.id.apiKeyInput);
        Button btnSaveKey = findViewById(R.id.btnSaveKey);
        Button btnAccessibility = findViewById(R.id.btnAccessibility);
        Button btnOverlay = findViewById(R.id.btnOverlay);
        Button btnStart = findViewById(R.id.btnStart);

        // Load API Key lama
        apiKeyInput.setText(prefs.getString("groq_api_key", ""));

        // Simpan API Key
        btnSaveKey.setOnClickListener(v -> {
            String key = apiKeyInput.getText().toString().trim();
            if (key.isEmpty()) {
                Toast.makeText(this, "API Key tidak boleh kosong!", Toast.LENGTH_SHORT).show();
            } else {
                prefs.edit().putString("groq_api_key", key).apply();
                Toast.makeText(this, "API Key Disimpan!", Toast.LENGTH_SHORT).show();
            }
        });

        // Buka Pengaturan Aksesibilitas
        btnAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(this, "Cari 'Nova Otonom AI' dan Aktifkan", Toast.LENGTH_LONG).show();
        });

        // Buka Pengaturan Overlay (Tampil di atas aplikasi lain)
        btnOverlay.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Toast.makeText(this, "Izin Mengambang Sudah Aktif!", Toast.LENGTH_SHORT).show();
            }
        });

        // Mulai Layanan Nova
        btnStart.setOnClickListener(v -> checkPermissionsAndStart());
    }

    private void requestBasicPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms = new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS};
            } else {
                perms = new String[]{Manifest.permission.RECORD_AUDIO};
            }
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(perms, 101);
            }
        }
    }

    private void checkPermissionsAndStart() {
        String key = prefs.getString("groq_api_key", "");
        if (key.isEmpty()) {
            Toast.makeText(this, "Harap simpan API Key terlebih dahulu!", Toast.LENGTH_LONG).show();
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Harap berikan Izin Mengambang (Overlay)!", Toast.LENGTH_LONG).show();
            return;
        }

        // Meminta Izin Tangkapan Layar (Media Projection)
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, NovaForegroundService.class);
                serviceIntent.putExtra("code", resultCode);
                serviceIntent.putExtra("data", data);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                Toast.makeText(this, "Nova Diaktifkan!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Izin rekam layar wajib diberikan untuk melihat AI!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
