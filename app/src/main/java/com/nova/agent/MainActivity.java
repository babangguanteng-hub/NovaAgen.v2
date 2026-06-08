package com.nova.agent;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvAudioCheck;
    private TextView tvOverlayCheck;
    private TextView tvWriteSettingsCheck;
    private TextView tvAccessibilityCheck;
    private Button btnFloatingBubble;
    private Button btnAccessibility;
    private Button btnApiKey;

    private SharedPreferences sharedPrefs;
    private static final String PREFS_NAME = "NovaAgentPrefs";
    private static final String KEY_BYPASS_WRITE_SETTINGS = "BypassWriteSettings";
    private static final String KEY_API_KEY = "GroqApiKey";
    private static final int REQ_AUDIO_PERMISSION = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Membangun Antarmuka Pengguna Secara Programmatic & Dinamis (Aman dari Masalah Layout XML)
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF0F0E17); // Dark Purple Background

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 60, 40, 60);
        mainLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        // Header
        TextView tvTitle = new TextView(this);
        tvTitle.setText("NOVA AGENT");
        tvTitle.setTextSize(32);
        tvTitle.setTextColor(0xFFD53F8C); // Pink Magenta Neon
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setPaintFlags(tvTitle.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        mainLayout.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("AI Background Assistant & Device Automator");
        tvSub.setTextSize(14);
        tvSub.setTextColor(0xFF9F7AEA); // Light Purple
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, 10, 0, 50);
        mainLayout.addView(tvSub);

        // CARD 1: STATUS DAN IZIN
        LinearLayout cardStatus = createCard();
        
        tvStatus = new TextView(this);
        tvStatus.setText("Status Layanan: TIDAK AKTIF");
        tvStatus.setTextSize(18);
        tvStatus.setTextColor(0xFFE2E8F0);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 0, 0, 30);
        cardStatus.addView(tvStatus);

        tvAudioCheck = createCheckItem(" Izin Rekam Audio");
        tvOverlayCheck = createCheckItem(" Izin Overlay Jendela");
        tvWriteSettingsCheck = createCheckItem(" Izin Menulis Pengaturan / Kecerahan");
        tvAccessibilityCheck = createCheckItem(" Layanan Aksesibilitas");

        cardStatus.addView(tvAudioCheck);
        cardStatus.addView(tvOverlayCheck);
        cardStatus.addView(tvWriteSettingsCheck);
        cardStatus.addView(tvAccessibilityCheck);

        // Spacing sebelum tombol
        View spacing = new View(this);
        spacing.setMinimumHeight(30);
        cardStatus.addView(spacing);

        btnFloatingBubble = createButton("AKTIFKAN FLOATING BUBBLE", 0xFF6B46C1); // Purple Button
        btnFloatingBubble.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleFloatingBubbleClick();
            }
        });
        cardStatus.addView(btnFloatingBubble);

        btnAccessibility = createButton("AKTIFKAN LAYANAN AKSESIBILITAS", 0xFF00F5D4); // Teal Button
        btnAccessibility.setTextColor(0xFF0F0E17);
        btnAccessibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }
        });
        cardStatus.addView(btnAccessibility);

        mainLayout.addView(cardStatus);

        // CARD 2: KONFIGURASI API
        LinearLayout cardApi = createCard();
        TextView tvApiTitle = new TextView(this);
        tvApiTitle.setText("Konfigurasi API AI");
        tvApiTitle.setTextSize(18);
        tvApiTitle.setTextColor(0xFFE2E8F0);
        tvApiTitle.setPadding(0, 0, 0, 15);
        cardApi.addView(tvApiTitle);

        TextView tvApiDesc = new TextView(this);
        tvApiDesc.setText("Nova Agent membutuhkan kunci otentikasi Groq API Key untuk memproses instruksi suara Anda.");
        tvApiDesc.setTextSize(13);
        tvApiDesc.setTextColor(0xFFA0AEC0);
        tvApiDesc.setPadding(0, 0, 0, 30);
        cardApi.addView(tvApiDesc);

        btnApiKey = createButton("PENGATURAN GROQ API KEY", 0xFF2D3748); // Dark Grey Button
        btnApiKey.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showApiKeyInputDialog();
            }
        });
        cardApi.addView(btnApiKey);

        mainLayout.addView(cardApi);

        scrollView.addView(mainLayout);
        setContentView(scrollView);

        // Lakukan pembaruan indikator berkala di UI
        new Handler(Looper.getMainLooper()).postDelayed(this::updateUIState, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIState();
    }

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFF1A1D24); // Dark Card BG
        card.setPadding(40, 40, 40, 40);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 40);
        card.setLayoutParams(params);
        return card;
    }

    private TextView createCheckItem(String text) {
        TextView tv = new TextView(this);
        tv.setText("❌" + text + " (Dibutuhkan)");
        tv.setTextSize(14);
        tv.setTextColor(0xFFA0AEC0);
        tv.setPadding(0, 10, 0, 10);
        return tv;
    }

    private Button createButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 15, 0, 15);
        btn.setLayoutParams(params);
        return btn;
    }

    // Fungsi Utama: Memperbarui Seluruh Logika Status Izin
    private void updateUIState() {
        boolean audioGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean overlayGranted = Settings.canDrawOverlays(this);
        boolean writeSettingsGranted = Settings.System.canWrite(this) || sharedPrefs.getBoolean(KEY_BYPASS_WRITE_SETTINGS, false);
        boolean accessibilityActive = isAccessibilityServiceEnabled(this, ActionAssistantService.class);

        // Update indikator teks Rekam Audio
        if (audioGranted) {
            tvAudioCheck.setText("✅ Izin Rekam Audio (Disetujui)");
            tvAudioCheck.setTextColor(0xFF48BB78);
        } else {
            tvAudioCheck.setText("❌ Izin Rekam Audio (Dibutuhkan)");
            tvAudioCheck.setTextColor(0xFFE53E3E);
        }

        // Update indikator teks Overlay
        if (overlayGranted) {
            tvOverlayCheck.setText("✅ Izin Overlay Jendela (Disetujui)");
            tvOverlayCheck.setTextColor(0xFF48BB78);
        } else {
            tvOverlayCheck.setText("❌ Izin Overlay Jendela (Dibutuhkan)");
            tvOverlayCheck.setTextColor(0xFFE53E3E);
        }

        // Update indikator teks Menulis Pengaturan
        if (writeSettingsGranted) {
            tvWriteSettingsCheck.setText("✅ Izin Menulis Pengaturan / Kecerahan (Disetujui)");
            tvWriteSettingsCheck.setTextColor(0xFF48BB78);
        } else {
            tvWriteSettingsCheck.setText("❌ Izin Menulis Pengaturan / Kecerahan (Dibutuhkan)");
            tvWriteSettingsCheck.setTextColor(0xFFE53E3E);
        }

        // Update indikator teks Aksesibilitas
        if (accessibilityActive) {
            tvAccessibilityCheck.setText("✅ Layanan Aksesibilitas (Aktif)");
            tvAccessibilityCheck.setTextColor(0xFF48BB78);
        } else {
            tvAccessibilityCheck.setText("❌ Layanan Aksesibilitas (Dibutuhkan)");
            tvAccessibilityCheck.setTextColor(0xFFE53E3E);
        }

        // Status Layanan Utama
        if (audioGranted && overlayGranted && writeSettingsGranted && accessibilityActive) {
            tvStatus.setText("Status Layanan: AKTIF & SIAP");
            tvStatus.setTextColor(0xFF48BB78); // Green
        } else {
            tvStatus.setText("Status Layanan: TIDAK AKTIF");
            tvStatus.setTextColor(0xFFE53E3E); // Red
        }
    }

    // Mengambil tindakan saat tombol Aktifkan Gelembung diklik
    private void handleFloatingBubbleClick() {
        // 1. Periksa izin Rekam Audio terlebih dahulu
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_PERMISSION);
            return;
        }

        // 2. Periksa izin Overlay Jendela
        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionExplanationDialog();
            return;
        }

        // 3. Periksa izin Menulis Pengaturan (Tantangan Infinix Android Go Anda)
        if (!Settings.System.canWrite(this) && !sharedPrefs.getBoolean(KEY_BYPASS_WRITE_SETTINGS, false)) {
            showWriteSettingsPopupDialog();
            return;
        }

        // Jika semua izin terpenuhi, jalankan Floating Bubble!
        Intent intent = new Intent(this, FloatingBubbleService.class);
        startService(intent);
        Toast.makeText(this, "Nova Agent Floating Bubble diaktifkan!", Toast.LENGTH_SHORT).show();
    }

    // 🌟 POPUP POPULER: Dialog Edukatif Pengatur Kecerahan/Sistem (Safeguard Android Go)
    private void showWriteSettingsPopupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🛡️ Izin Sistem Diperlukan");
        builder.setMessage("Nova Agent memerlukan izin 'Mengubah Setelan Sistem' agar asisten AI bisa mengatur kecerahan layar atau volume secara langsung.\n\n" +
                "⚠️ INFORMASI ANDROID GO:\n" +
                "Karena perangkat Infinix Anda menggunakan Android Go Edition, sistem operasi kemungkinan mengunci izin ini (sakelar berwarna abu-abu).\n\n" +
                "Apa yang ingin Anda lakukan?");

        // Pilihan 1: Jalur Resmi (Mencoba membuka halaman setelan sistem)
        builder.setPositiveButton("Buka Setelan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });

        // Pilihan 2: Jalur Penyelamat Android Go (Abaikan & aktifkan secara aman!)
        builder.setNegativeButton("Abaikan (Android Go)", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sharedPrefs.edit().putBoolean(KEY_BYPASS_WRITE_SETTINGS, true).apply();
                updateUIState();
                Toast.makeText(MainActivity.this, "Izin dilewati dengan aman untuk Android Go!", Toast.LENGTH_LONG).show();
                
                // Coba jalankan ulang floating bubble sekarang setelah izin dilewati
                handleFloatingBubbleClick();
            }
        });

        builder.setNeutralButton("Batal", null);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showOverlayPermissionExplanationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Izin Hamparan Jendela");
        builder.setMessage("Nova Agent membutuhkan izin untuk menampilkan bola melayang di atas aplikasi lain. Silakan aktifkan izin 'Tampilkan di atas aplikasi lain' di halaman berikutnya.");
        builder.setPositiveButton("Buka Setelan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void showApiKeyInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pengaturan Groq API Key");
        
        final EditText input = new EditText(this);
        input.setHint("Masukkan API Key Groq Anda...");
        input.setText(sharedPrefs.getString(KEY_API_KEY, ""));
        builder.setView(input);

        builder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String key = input.getText().toString().trim();
                sharedPrefs.edit().putString(KEY_API_KEY, key).apply();
                Toast.makeText(MainActivity.this, "API Key berhasil disimpan!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        String prefString = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return prefString != null && prefString.contains(context.getPackageName() + "/" + serviceClass.getName());
    }
}
