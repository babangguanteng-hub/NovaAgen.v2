package com.novaagent.app.ui.config;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.novaagent.app.services.accessibility.NovaAccessibilityService;
import com.novaagent.app.services.foreground.NovaForegroundService;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private EditText etApiKey;
    private boolean isServiceStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("nova_config", MODE_PRIVATE);

        // --- UI PREMIUM CLEAN NEON MURNI PROGRAMMATIC ---
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Color.parseColor("#0A0A12")); // Deep Space Black
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dpToPx(30), dpToPx(50), dpToPx(30), dpToPx(50));
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        // Teks Judul
        TextView tvTitle = new TextView(this);
        tvTitle.setText("NOVA AGENT");
        tvTitle.setTextSize(32f);
        tvTitle.setTextColor(Color.parseColor("#FFFFFF"));
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setShadowLayer(15f, 0, 0, Color.parseColor("#E024FF")); // Neon Purple Glow
        layout.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("Autonomous AI Core by Moch Khoirul Azman");
        tvSub.setTextSize(12f);
        tvSub.setTextColor(Color.parseColor("#00FFFF")); // Cyan
        tvSub.setPadding(0, 0, 0, dpToPx(40));
        layout.addView(tvSub);

        // Input API Key dengan Border Kapsul Neon
        etApiKey = new EditText(this);
        etApiKey.setHint("Masukkan Groq API Key...");
        etApiKey.setHintTextColor(Color.parseColor("#555555"));
        etApiKey.setTextColor(Color.WHITE);
        etApiKey.setText(prefs.getString("groq_api_key", ""));
        etApiKey.setPadding(dpToPx(20), dpToPx(15), dpToPx(20), dpToPx(15));
        
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setShape(GradientDrawable.RECTANGLE);
        inputBg.setCornerRadius(dpToPx(30));
        inputBg.setColor(Color.parseColor("#151522"));
        inputBg.setStroke(dpToPx(2), Color.parseColor("#00FFFF")); // Border Cyan
        etApiKey.setBackground(inputBg);
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(0, 0, 0, dpToPx(20));
        etApiKey.setLayoutParams(inputParams);
        layout.addView(etApiKey);

        // Tombol Simpan & Mulai
        Button btnStart = new Button(this);
        btnStart.setText("INISIALISASI AGEN");
        btnStart.setTextColor(Color.WHITE);
        btnStart.setTypeface(null, Typeface.BOLD);
        
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(dpToPx(30));
        btnBg.setColor(Color.parseColor("#99E024FF")); // Ungu transparan
        btnBg.setStroke(dpToPx(2), Color.parseColor("#E024FF")); // Ungu tegas
        btnStart.setBackground(btnBg);
        
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(55));
        btnStart.setLayoutParams(btnParams);
        layout.addView(btnStart);

        scrollView.addView(layout);
        setContentView(scrollView);

        // Aksi Tombol
        btnStart.setOnClickListener(v -> {
            String key = etApiKey.getText().toString().trim();
            if(!key.isEmpty()){
                prefs.edit().putString("groq_api_key", key).apply();
                Toast.makeText(this, "API Key Disimpan!", Toast.LENGTH_SHORT).show();
                checkAndRequestPermissions();
            } else {
                Toast.makeText(this, "API Key wajib diisi untuk menjalankan AI", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Cek perizinan otomatis saat aplikasi dibuka (jika API key sudah ada)
        if (!prefs.getString("groq_api_key", "").isEmpty() && !isServiceStarted) {
            checkAndRequestPermissions();
        }
    }

    private void checkAndRequestPermissions() {
        // 1. Popup Keamanan: Cek Izin Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPremiumPopup("Izin Sistem Diperlukan", 
                "Nova Agent memerlukan izin 'Tampil di Atas Aplikasi Lain' agar UI Aurora dapat mengambang dan memantau tugas.", 
                "AKTIFKAN OVERLAY", () -> {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
            return;
        }

        // 2. Popup Keamanan: Cek Izin Aksesibilitas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 101);
            Toast.makeText(this, "Izinkan akses Mikrofon agar AI bisa mendengar suara Anda", Toast.LENGTH_LONG).show();
            return;
        }

        if (!isAccessibilityServiceEnabled(this, NovaAccessibilityService.class)) {
            showPremiumPopup("Mata AI Terkunci", 
                "Sistem keamanan Android mewajibkan Anda menyalakan layanan Aksesibilitas 'Nova Agent' agar AI dapat melihat layar dan melakukan klik.", 
                "BUKA PENGATURAN", () -> {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            });
            return;
        }

        // Jika semua lulus, jalankan mesin!
        if (!isServiceStarted) {
            startNovaService();
        }
    }

    // Custom Premium Popup / Dialog
    private void showPremiumPopup(String title, String message, String btnText, Runnable action) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton(btnText, (dialog, which) -> action.run());
        builder.setNegativeButton("NANTI", (dialog, which) -> dialog.dismiss());
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<?> accessibilityService) {
        String expectedComponentName = context.getPackageName() + "/" + accessibilityService.getName();
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null) return false;

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equalsIgnoreCase(expectedComponentName)) {
                return true;
            }
        }
        return false;
    }

    private void startNovaService() {
        isServiceStarted = true;
        Intent serviceIntent = new Intent(this, NovaForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Sistem Nova Agent Aktif!", Toast.LENGTH_SHORT).show();
        finish(); // Tutup Activity, biarkan agent di background
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
}
