package com.nova.agent;

import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private TextView tvStatus, tvAudioCheck, tvOverlayCheck, tvWriteSettingsCheck, tvAccessibilityCheck, tvNotifCheck, tvUsageCheck, tvAdminCheck;
    private Spinner spinnerAiProvider;
    private SharedPreferences sharedPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPrefs = getSharedPreferences("NovaAgentPrefs", Context.MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF07070D);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(40, 60, 40, 60);

        TextView title = new TextView(this);
        title.setText("NOVA AI V3");
        title.setTextSize(40);
        title.setTextColor(0xFF00F5D4);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setShadowLayer(15, 0, 0, 0xFF00F5D4);
        main.addView(title);

        TextView subTitle = new TextView(this);
        subTitle.setText("Autonomous OS Automation Engine");
        subTitle.setTextColor(0xFF8A2BE2);
        subTitle.setGravity(Gravity.CENTER);
        subTitle.setPadding(0, 0, 0, 40);
        main.addView(subTitle);

        // Panel AI Selector
        LinearLayout cardAi = createCard();
        TextView titleAi = new TextView(this); titleAi.setText("🚀 CONFIGURASI AI BRAIN"); titleAi.setTextColor(Color.WHITE); titleAi.setPadding(0,0,0,20);
        cardAi.addView(titleAi);
        
        spinnerAiProvider = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, 
            new String[]{"Groq Llama 3 (Super Cepat)", "Google Gemini (Paling Pintar)", "OpenRouter (Gratis Bebas)"});
        spinnerAiProvider.setAdapter(adapter);
        
        String savedAi = sharedPrefs.getString("SelectedAiProvider", "groq");
        if(savedAi.equals("gemini")) spinnerAiProvider.setSelection(1);
        else if(savedAi.equals("openrouter")) spinnerAiProvider.setSelection(2);

        spinnerAiProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                String sel = pos == 1 ? "gemini" : (pos == 2 ? "openrouter" : "groq");
                sharedPrefs.edit().putString("SelectedAiProvider", sel).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        cardAi.addView(spinnerAiProvider);
        
        Button btnKey = createButton("🔑 PENGATURAN API KEY", 0xFF2D3748);
        btnKey.setOnClickListener(v -> showKeyDialog());
        cardAi.addView(btnKey);
        main.addView(cardAi);

        // PANEL STATUS PERIZINAN SUPER LENGKAP (Memenuhi Permintaan Azman!)
        LinearLayout cardStatus = createCard();
        tvStatus = new TextView(this); tvStatus.setTextSize(18); tvStatus.setGravity(Gravity.CENTER); tvStatus.setPadding(0,0,0,20);
        cardStatus.addView(tvStatus);

        tvAudioCheck = createCheck("Perekam Suara"); 
        tvOverlayCheck = createCheck("Overlay Bubble");
        tvWriteSettingsCheck = createCheck("Ubah Pengaturan Perangkat"); 
        tvAccessibilityCheck = createCheck("Kontrol Layar (Aksesibilitas)");
        tvNotifCheck = createCheck("Akses Notifikasi (Baca WA)");
        tvUsageCheck = createCheck("Akses Data Penggunaan");
        tvAdminCheck = createCheck("Akses Admin Perangkat");

        cardStatus.addView(tvAudioCheck); cardStatus.addView(tvOverlayCheck); cardStatus.addView(tvWriteSettingsCheck); 
        cardStatus.addView(tvAccessibilityCheck); cardStatus.addView(tvNotifCheck); cardStatus.addView(tvUsageCheck); cardStatus.addView(tvAdminCheck);

        // TOMBOL-TOMBOL PERIZINAN AKTIF (Satu Kali Klik Langsung Mengarah Ke Pengaturan Sistem)
        Button btnGrantAdvanced = createButton("⚙️ AKTIFKAN SELURUH PERIZINAN", 0xFF00F5D4);
        btnGrantAdvanced.setTextColor(Color.BLACK);
        btnGrantAdvanced.setOnClickListener(v -> showPermissionsSelectorPopup());
        cardStatus.addView(btnGrantAdvanced);

        Button btnBubble = createButton("🟣 LUNCURKAN NOVA GELEMBUNG", 0xFF6B46C1);
        btnBubble.setOnClickListener(v -> launchNova());
        cardStatus.addView(btnBubble);

        main.addView(cardStatus);

        // Plakat Rilis Moch Khoirul Azman
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, 30, 0, 0);

        TextView devInfo = new TextView(this);
        devInfo.setText("Dirancang & Dikembangkan Oleh:\nMOCH KHOIRUL AZMAN");
        devInfo.setTextColor(0xFFD53F8C);
        devInfo.setTextSize(14);
        devInfo.setGravity(Gravity.CENTER);
        devInfo.setTypeface(null, android.graphics.Typeface.BOLD);
        footer.addView(devInfo);

        TextView dateInfo = new TextView(this);
        dateInfo.setText("Tanggal Rilis Resmi: Senin, 8 Juni 2026\nVersi: Ultimate Automation v2.5");
        dateInfo.setTextColor(0xFF718096);
        dateInfo.setTextSize(12);
        dateInfo.setGravity(Gravity.CENTER);
        dateInfo.setPadding(0, 10, 0, 0);
        footer.addView(dateInfo);

        main.addView(footer);
        scroll.addView(main);
        setContentView(scroll);

        final Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            @Override public void run() { updateUI(); h.postDelayed(this, 1000); }
        });
    }

    private LinearLayout createCard() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(0xFF111119); l.setPadding(40,40,40,40);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0,0,0,30); l.setLayoutParams(p); return l;
    }
    private TextView createCheck(String text) {
        TextView t = new TextView(this); t.setText("❌ " + text); t.setTextColor(Color.GRAY); t.setPadding(0,5,0,5); return t;
    }
    private Button createButton(String text, int color) {
        Button b = new Button(this); b.setText(text); b.setBackgroundColor(color); b.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0,15,0,15); b.setLayoutParams(p); return b;
    }

    private void updateUI() {
        boolean a = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean o = Settings.canDrawOverlays(this);
        boolean w = Settings.System.canWrite(this) || sharedPrefs.getBoolean("BypassWriteSettings", false);
        boolean acc = ActionAssistantService.isServiceRunning;
        
        // Cek Notifikasi
        String notifListener = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        boolean n = notifListener != null && notifListener.contains(getPackageName());

        // Cek Usage Stats (Akses Penggunaan)
        boolean u = false;
        try {
            android.app.AppOpsManager appOps = (android.app.AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), getPackageName());
            u = (mode == android.app.AppOpsManager.MODE_ALLOWED);
        } catch (Exception ignored) {}

        // Cek Admin Perangkat
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName adminComponent = new ComponentName(this, DeviceAdminReceiver.class);
        boolean adm = dpm.isAdminActive(adminComponent);

        tvAudioCheck.setText(a ? "✅ Rekam Audio (Disetujui)" : "❌ Rekam Audio"); tvAudioCheck.setTextColor(a ? 0xFF48BB78 : 0xFFE53E3E);
        tvOverlayCheck.setText(o ? "✅ Overlay Bubble (Disetujui)" : "❌ Overlay Bubble"); tvOverlayCheck.setTextColor(o ? 0xFF48BB78 : 0xFFE53E3E);
        tvWriteSettingsCheck.setText(w ? "✅ Setelan Sistem (Disetujui)" : "❌ Setelan Sistem"); tvWriteSettingsCheck.setTextColor(w ? 0xFF48BB78 : 0xFFE53E3E);
        tvAccessibilityCheck.setText(acc ? "✅ Kontrol Layar / Aksesibilitas (Aktif)" : "❌ Kontrol Layar"); tvAccessibilityCheck.setTextColor(acc ? 0xFF48BB78 : 0xFFE53E3E);
        tvNotifCheck.setText(n ? "✅ Akses Notifikasi / Baca WA (Aktif)" : "❌ Akses Notifikasi"); tvNotifCheck.setTextColor(n ? 0xFF48BB78 : 0xFFE53E3E);
        tvUsageCheck.setText(u ? "✅ Akses Data Penggunaan (Disetujui)" : "❌ Akses Data Penggunaan"); tvUsageCheck.setTextColor(u ? 0xFF48BB78 : 0xFFE53E3E);
        tvAdminCheck.setText(adm ? "✅ Admin Perangkat (Aktif)" : "❌ Admin Perangkat"); tvAdminCheck.setTextColor(adm ? 0xFF48BB78 : 0xFFE53E3E);

        if(a && o && w && acc && n && u && adm) { tvStatus.setText("SYSTEM ONLINE & ULTIMATE AUTOMATION READY"); tvStatus.setTextColor(0xFF00F5D4); }
        else { tvStatus.setText("SYSTEM OFFLINE"); tvStatus.setTextColor(0xFFE53E3E); }
    }

    private void showPermissionsSelectorPopup() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("⚙️ Pilih Izin Yang Ingin Diaktifkan");
        String[] items = {
            "1. Kontrol Layar (Aksesibilitas)",
            "2. Akses Notifikasi (Baca WhatsApp)",
            "3. Akses Data Penggunaan",
            "4. Admin Perangkat",
            "5. Ubah Pengaturan Perangkat (Write Settings)"
        };
        b.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                else if (which == 1) startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
                else if (which == 2) startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                else if (which == 3) {
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, new ComponentName(MainActivity.this, DeviceAdminReceiver.class));
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Nova membutuhkan hak admin untuk optimasi sistem.");
                    startActivity(intent);
                }
                else if (which == 4) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            }
        });
        b.show();
    }

    private void launchNova() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, 101); return;
        }
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Izinkan Overlay Dulu!", Toast.LENGTH_SHORT).show(); return; }
        androidx.core.content.ContextCompat.startForegroundService(this, new Intent(this, FloatingBubbleService.class));
        Toast.makeText(this, "Nova AI Meluncur!", Toast.LENGTH_SHORT).show();
    }

    private void showKeyDialog() {
        final String p = sharedPrefs.getString("SelectedAiProvider", "groq");
        AlertDialog.Builder b = new AlertDialog.Builder(this); b.setTitle("API Key untuk " + p.toUpperCase());
        final EditText input = new EditText(this); input.setText(sharedPrefs.getString("ApiKey_" + p, ""));
        b.setView(input);
        b.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sharedPrefs.edit().putString("ApiKey_" + p, input.getText().toString()).apply();
            }
        });
        b.setNegativeButton("Batal", null); b.show();
    }
}
