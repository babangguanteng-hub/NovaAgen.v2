package com.nova.agent;
import android.os.Handler;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * MainActivity mengelola tampilan dashboard kontrol Nova Agent.
 * Bertanggung jawab terhadap:
 * 1. Pengecekan perizinan sistem (Aksesibilitas, Overlay, Rekam Audio, Tulis Setelan).
 * 2. Menyimpan Groq API Key secara aman ke SharedPreferences melalui Dialog Material.
 * 3. Mengontrol siklus hidup FloatingBubbleService.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_AUDIO = 201;
    private static final int REQUEST_CODE_OVERLAY = 202;
    private static final int REQUEST_CODE_WRITE_SETTINGS = 203;

    private SharedPreferences mSharedPreferences;
    private TextView mStatusTextView;
    private TextView mPermissionSummaryView;
    private Button mToggleServiceBtn;
    private Button mSetupKeyBtn;
    private Button mAccessibilityBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSharedPreferences = getSharedPreferences("NovaPrefs", MODE_PRIVATE);

        // Membangun antarmuka secara dinamis dengan tema Dark Purple & Neon Magenta
        setContentView(buildContentView());

        updateUIState();
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        scrollView.setBackgroundColor(Color.parseColor("#090216")); // Sangat Gelap Violet
        scrollView.setFillViewport(true);

        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(50, 80, 50, 80);
        mainContainer.setGravity(Gravity.CENTER_HORIZONTAL);

        // Title Header
        TextView titleText = new TextView(this);
        titleText.setText("NOVA AGENT");
        titleText.setTextColor(Color.parseColor("#D433FF")); // Neon Pink/Magenta
        titleText.setTextSize(34);
        titleText.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        titleText.setGravity(Gravity.CENTER);
        mainContainer.addView(titleText);

        // Subtitle
        TextView subtitleText = new TextView(this);
        subtitleText.setText("AI Background Assistant & Device Automator");
        subtitleText.setTextColor(Color.parseColor("#9D66FF")); // Soft Violet
        subtitleText.setTextSize(14);
        subtitleText.setPadding(0, 8, 0, 60);
        subtitleText.setGravity(Gravity.CENTER);
        mainContainer.addView(subtitleText);

        // CARD PANEL 1: STATUS & KONTROL UTAMA
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(createCardBackground());
        statusCard.setPadding(45, 45, 45, 45);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 40);
        statusCard.setLayoutParams(cardParams);

        mStatusTextView = new TextView(this);
        mStatusTextView.setText("Status Layanan: Memeriksa...");
        mStatusTextView.setTextColor(Color.WHITE);
        mStatusTextView.setTextSize(16);
        mStatusTextView.setTypeface(Typeface.DEFAULT_BOLD);
        mStatusTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        mStatusTextView.setPadding(0, 0, 0, 15);
        statusCard.addView(mStatusTextView);

        mPermissionSummaryView = new TextView(this);
        mPermissionSummaryView.setText("Memeriksa status izin sistem...");
        mPermissionSummaryView.setTextColor(Color.parseColor("#B0A8C0"));
        mPermissionSummaryView.setTextSize(13);
        mPermissionSummaryView.setLineSpacing(5, 1.1f);
        mPermissionSummaryView.setPadding(0, 0, 0, 30);
        statusCard.addView(mPermissionSummaryView);

        mToggleServiceBtn = new Button(this);
        mToggleServiceBtn.setText("AKTIFKAN FLOATING BUBBLE");
        mToggleServiceBtn.setTextColor(Color.WHITE);
        mToggleServiceBtn.setBackground(createButtonBackground("#651FFF")); // Deep Indigo Neon
        mToggleServiceBtn.setTypeface(Typeface.DEFAULT_BOLD);
        mToggleServiceBtn.setPadding(0, 30, 0, 30);
        mToggleServiceBtn.setOnClickListener(v -> handleFloatingServiceToggle());
        statusCard.addView(mToggleServiceBtn);

        mAccessibilityBtn = new Button(this);
        mAccessibilityBtn.setText("AKTIFKAN LAYANAN AKSESIBILITAS");
        mAccessibilityBtn.setTextColor(Color.BLACK);
        mAccessibilityBtn.setBackground(createButtonBackground("#00FFC2")); // Neon Teal Cyan
        mAccessibilityBtn.setTypeface(Typeface.DEFAULT_BOLD);
        mAccessibilityBtn.setPadding(0, 30, 0, 30);
        LinearLayout.LayoutParams accessParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        accessParams.setMargins(0, 20, 0, 0);
        mAccessibilityBtn.setLayoutParams(accessParams);
        mAccessibilityBtn.setOnClickListener(v -> launchAccessibilitySettings());
        statusCard.addView(mAccessibilityBtn);

        mainContainer.addView(statusCard);

        // CARD PANEL 2: INTEGRASI API KEY
        LinearLayout configCard = new LinearLayout(this);
        configCard.setOrientation(LinearLayout.VERTICAL);
        configCard.setBackground(createCardBackground());
        configCard.setPadding(45, 45, 45, 45);
        configCard.setLayoutParams(cardParams);

        TextView apiTitle = new TextView(this);
        apiTitle.setText("Konfigurasi API AI");
        apiTitle.setTextColor(Color.WHITE);
        apiTitle.setTextSize(16);
        apiTitle.setTypeface(Typeface.DEFAULT_BOLD);
        apiTitle.setPadding(0, 0, 0, 10);
        configCard.addView(apiTitle);

        TextView apiDesc = new TextView(this);
        apiDesc.setText("Nova Agent membutuhkan kunci otentikasi Groq API Key untuk memproses instruksi suara.");
        apiDesc.setTextColor(Color.parseColor("#8E84A3"));
        apiDesc.setTextSize(12);
        apiDesc.setPadding(0, 0, 0, 30);
        configCard.addView(apiDesc);

        mSetupKeyBtn = new Button(this);
        mSetupKeyBtn.setText("PENGATURAN GROQ API KEY");
        mSetupKeyBtn.setTextColor(Color.WHITE);
        mSetupKeyBtn.setBackground(createButtonBackground("#2A1E4A")); // Dark Slate
        mSetupKeyBtn.setTypeface(Typeface.DEFAULT_BOLD);
        mSetupKeyBtn.setPadding(0, 30, 0, 30);
        mSetupKeyBtn.setOnClickListener(v -> showApiKeyDialog());
        configCard.addView(mSetupKeyBtn);

        mainContainer.addView(configCard);
        scrollView.addView(mainContainer);

        return scrollView;
    }

    private GradientDrawable createCardBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.parseColor("#130927")); // Card Background Violet
        drawable.setCornerRadius(28f);
        drawable.setStroke(3, Color.parseColor("#28164A")); // Glow Border
        return drawable;
    }

    private GradientDrawable createButtonBackground(String hexColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.parseColor(hexColor));
        drawable.setCornerRadius(20f);
        return drawable;
    }

    /**
     * Memperbarui informasi UI berdasarkan status layanan dan izin yang dimiliki ponsel.
     */
    private void updateUIState() {
        boolean isServiceActive = FloatingBubbleService.isRunning();
        
        if (isServiceActive) {
            mStatusTextView.setText("Status Layanan: BUBBLE AKTIF");
            mStatusTextView.setTextColor(Color.parseColor("#00FFC2"));
            mToggleServiceBtn.setText("MATIKAN FLOATING BUBBLE");
            mToggleServiceBtn.setBackground(createButtonBackground("#FF2E93")); // Neon Magenta-Red
        } else {
            mStatusTextView.setText("Status Layanan: TIDAK AKTIF");
            mStatusTextView.setTextColor(Color.parseColor("#9D66FF"));
            mToggleServiceBtn.setText("AKTIFKAN FLOATING BUBBLE");
            mToggleServiceBtn.setBackground(createButtonBackground("#651FFF"));
        }

        // Susun laporan ringkasan izin yang tersisa
        StringBuilder summary = new StringBuilder();
        boolean allPassed = true;

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            summary.append("❌ Izin Rekam Audio (Dibutuhkan)\n");
            allPassed = false;
        } else {
            summary.append("✅ Izin Rekam Audio (Disetujui)\n");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            summary.append("❌ Izin Overlay Jendela / Diatas Aplikasi Lain (Dibutuhkan)\n");
            allPassed = false;
        } else {
            summary.append("✅ Izin Overlay Jendela (Disetujui)\n");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            summary.append("❌ Izin Menulis Pengaturan / Kecerahan (Dibutuhkan)\n");
            allPassed = false;
        } else {
            summary.append("✅ Izin Menulis Pengaturan (Disetujui)\n");
        }

        if (!isAccessibilityServiceRunning()) {
            summary.append("❌ Layanan Aksesibilitas Nova Agent (Dibutuhkan)\n");
            mAccessibilityBtn.setVisibility(View.VISIBLE);
            allPassed = false;
        } else {
            summary.append("✅ Layanan Aksesibilitas (Aktif)\n");
            mAccessibilityBtn.setVisibility(View.GONE);
        }

        mPermissionSummaryView.setText(summary.toString().trim());
        if (allPassed) {
            mPermissionSummaryView.setTextColor(Color.parseColor("#00FFC2"));
        } else {
            mPermissionSummaryView.setTextColor(Color.parseColor("#B0A8C0"));
        }
    }

    private boolean isAccessibilityServiceRunning() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo service : services) {
            if (service.getId().contains(ActionAssistantService.class.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private void handleFloatingServiceToggle() {
        // Alur Validasi Izin 1: Rekam Audio
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_CODE_AUDIO);
            return;
        }

        // Alur Validasi Izin 2: Overlay Jendela
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_OVERLAY);
            return;
        }

        // Alur Validasi Izin 3: Tulis Pengaturan Sistem
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_WRITE_SETTINGS);
            return;
        }

        // Eksekusi Layanan
        Intent serviceIntent = new Intent(this, FloatingBubbleService.class);
        if (FloatingBubbleService.isRunning()) {
            stopService(serviceIntent);
        } else {
            ContextCompat.startForegroundService(this, serviceIntent);
        }

        // Tunda pembaruan UI sebentar agar siklus siklus hidup terikat
        new Handler().postDelayed(this::updateUIState, 400);
    }

    private void launchAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "Aktifkan 'Nova Agent' dari menu Aksesibilitas.", Toast.LENGTH_LONG).show();
    }

    /**
     * Menampilkan Dialog Material Input API Key.
     */
    private void showApiKeyDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("Pengaturan Groq API Key");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final EditText input = new EditText(this);
        input.setHint("Masukkan Kunci Api (gsk_...)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextColor(Color.WHITE);
        String currentKey = mSharedPreferences.getString("groq_api_key", "");
        input.setText(currentKey);

        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton("Simpan", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String key = input.getText().toString().trim();
                mSharedPreferences.edit().putString("groq_api_key", key).apply();
                Toast.makeText(MainActivity.this, "Kunci API berhasil disimpan!", Toast.LENGTH_SHORT).show();
                updateUIState();
            }
        });

        builder.setNegativeButton("Batal", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIState();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handleFloatingServiceToggle();
            } else {
                Toast.makeText(this, "Aplikasi membutuhkan izin Mikrofon untuk mendeteksi perintah suara.", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OVERLAY || requestCode == REQUEST_CODE_WRITE_SETTINGS) {
            updateUIState();
        }
    }
}