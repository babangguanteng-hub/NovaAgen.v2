package com.novaagent.app.ui.config;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.novaagent.app.services.foreground.NovaForegroundService;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Meminta Izin Notifikasi untuk Android 13
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        // Layout sederhana (Bisa diganti XML nanti)
        Button startButton = new Button(this);
        startButton.setText("MULAI NOVA AGENT");
        startButton.setOnClickListener(v -> checkPermissionsAndStart());
        setContentView(startButton);
    }

    private void checkPermissionsAndStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "Izinkan Tampil di Atas Aplikasi Lain!", Toast.LENGTH_LONG).show();
            return;
        }

        // Mulai alur Izin Tangkapan Layar (Media Projection)
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
                // Izin diberikan! Kirim token ke Foreground Service
                Intent serviceIntent = new Intent(this, NovaForegroundService.class);
                serviceIntent.putExtra("code", resultCode);
                serviceIntent.putExtra("data", data);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                Toast.makeText(this, "Nova Diaktifkan!", Toast.LENGTH_SHORT).show();
                finish(); // Tutup activity agar user kembali ke Home
            } else {
                Toast.makeText(this, "Izin rekam layar ditolak!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
