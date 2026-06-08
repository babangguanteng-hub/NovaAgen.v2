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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvAudioCheck, tvOverlayCheck, tvWriteSettingsCheck, tvAccessibilityCheck;
    private Button btnFloatingBubble, btnAccessibility, btnApiKey;
    private Spinner spinnerAiProvider;

    private SharedPreferences sharedPrefs;
    private static final String PREFS_NAME = "NovaAgentPrefs";
    private static final String KEY_BYPASS_WRITE_SETTINGS = "BypassWriteSettings";
    private static final String KEY_AI_PROVIDER = "SelectedAiProvider";
    private static final int REQ_AUDIO_PERMISSION = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // ROOT SCROLLVIEW
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF09070F); // Deep Black Purple background

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(45, 60, 45, 60);
        mainLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        // HEADER BRANDING (Sangat Elegan)
        TextView tvTitle = new TextView(this);
        tvTitle.setText("NOVA AGENT");
        tvTitle.setTextSize(34);
        tvTitle.setTextColor(0xFF00F5D4); // Neon Teal
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        mainLayout.addView(tvTitle);

        TextView tvSub = new TextView(this);
        tvSub.setText("AI Background Assistant & Device Automator");
        tvSub.setTextSize(12);
        tvSub.setTextColor(0xFF9F7AEA); // Glowing Purple
        tvSub.setGravity(Gravity.CENTER);
        tvSub.setPadding(0, 5, 0, 40);
        mainLayout.addView(tvSub);

        // CARD 1: SELEKTOR PILIHAN OTAK AI
        LinearLayout cardAiSelect = createCard();
        TextView tvSelectTitle = new TextView(this);
        tvSelectTitle.setText("PILIH OTAK ASISTEN AI");
        tvSelectTitle.setTextSize(14);
        tvSelectTitle.setTextColor(0xFFE2E8F0);
        tvSelectTitle.setPadding(0, 0, 0, 15);
        cardAiSelect.addView(tvSelectTitle);

        spinnerAiProvider = new Spinner(this);
        String[] providers = {"Groq AI (Llama 3.1)", "Google Gemini (2.5 Flash)", "OpenRouter (Llama 3 Free)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, providers);
        spinnerAiProvider.setAdapter(adapter);
        
        // Load setelan terakhir
        String currentProvider = sharedPrefs.getString(KEY_AI_PROVIDER, "groq");
        if ("gemini".equals(currentProvider)) spinnerAiProvider.setSelection(1);
        else if ("openrouter".equals(currentProvider)) spinnerAiProvider.setSelection(2);
        else spinnerAiProvider.setSelection(0);

        spinnerAiProvider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = "groq";
                if (position == 1) selected = "gemini";
                else if (position == 2) selected = "openrouter";
                sharedPrefs.edit().putString(KEY_AI_PROVIDER, selected).apply();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        cardAiSelect.addView(spinnerAiProvider);
        mainLayout.addView(cardAiSelect);

        // CARD 2: STATUS & TOMBOL LAYANAN
        LinearLayout cardStatus = createCard();
        tvStatus = new TextView(this);
        tvStatus.setText("Status Layanan: TIDAK AKTIF");
        tvStatus.setTextSize(18);
        tvStatus.setTextColor(0xFFE53E3E);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, 0, 0, 25);
        tvStatus.setTypeface(null, android.graphics.Typeface.BOLD);
        cardStatus.addView(tvStatus);

        tvAudioCheck = createCheckItem(" Izin Rekam Audio");
        tvOverlayCheck = createCheckItem(" Izin Overlay Jendela");
        tvWriteSettingsCheck = createCheckItem(" Izin Menulis Pengaturan / Kecerahan");
        tvAccessibilityCheck = createCheckItem(" Layanan Aksesibilitas");

        cardStatus.addView(tvAudioCheck);
        cardStatus.addView(tvOverlayCheck);
        cardStatus.addView(tvWriteSettingsCheck);
        cardStatus.addView(tvAccessibilityCheck);

        View spacing = new View(this);
        spacing.setMinimumHeight(20);
        cardStatus.addView(spacing);

        btnFloatingBubble = createButton("AKTIFKAN FLOATING BUBBLE", 0xFF6B46C1);
        btnFloatingBubble.setOnClickListener(v -> handleFloatingBubbleClick());
        cardStatus.addView(btnFloatingBubble);

        btnAccessibility = createButton("AKTIFKAN LAYANAN AKSESIBILITAS", 0xFF00F5D4);
        btnAccessibility.setTextColor(0xFF09070F);
        btnAccessibility.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        cardStatus.addView(btnAccessibility);

        mainLayout.addView(cardStatus);

        // CARD 3: KEY MANAGER
        LinearLayout cardApi = createCard();
        TextView tvApiTitle = new TextView(this);
        tvApiTitle.setText("Konfigurasi API AI");
        tvApiTitle.setTextSize(16);
        tvApiTitle.setTextColor(0xFFE2E8F0);
        tvApiTitle.setPadding(0, 0, 0, 15);
        cardApi.addView(tvApiTitle);

        btnApiKey = createButton("PENGATURAN API KEY", 0xFF2D3748);
        btnApiKey.setOnClickListener(v -> showApiKeyInputDialog());
        cardApi.addView(btnApiKey);
        mainLayout.addView(cardApi);

        // PANEL METADATA & KARYA CIPTA (Aesthetic Credits)
        LinearLayout creditLayout = new LinearLayout(this);
        creditLayout.setOrientation(LinearLayout.VERTICAL);
        creditLayout.setPadding(20, 40, 20, 20);
        creditLayout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView tvDev = new TextView(this);
        tvDev.setText("Developer: Moch Khoirul Azman");
        tvDev.setTextSize(14);
        tvDev.setTextColor(0xFFD53F8C); // Pink Magenta Glowing
        tvDev.setGravity(Gravity.CENTER);
        tvDev.setTypeface(null, android.graphics.Typeface.BOLD);
        creditLayout.addView(tvDev);

        TextView tvRelease = new TextView(this);
        tvRelease.setText("Tanggal Rilis: Senin, 8 Juni 2026");
        tvRelease.setTextSize(12);
        tvRelease.setTextColor(0xFFA0AEC0);
        tvRelease.setGravity(Gravity.CENTER);
        tvRelease.setPadding(0, 5, 0, 0);
        creditLayout.addView(tvRelease);

        mainLayout.addView(creditLayout);

        scrollView.addView(mainLayout);
        setContentView(scrollView);

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
        card.setBackgroundColor(0xFF13111C); // Sleek card BG
        card.setPadding(40, 40, 40, 40);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 30);
        card.setLayoutParams(params);
        return card;
    }

    private TextView createCheckItem(String text) {
        TextView tv = new TextView(this);
        tv.setText("❌" + text + " (Dibutuhkan)");
        tv.setTextSize(13);
        tv.setTextColor(0xFFA0AEC0);
        tv.setPadding(0, 8, 0, 8);
        return tv;
    }

    private Button createButton(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setBackgroundColor(color);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 10, 0, 10);
        btn.setLayoutParams(params);
        return btn;
    }

    private void updateUIState() {
        boolean audioGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean overlayGranted = Settings.canDrawOverlays(this);
        boolean writeSettingsGranted = Settings.System.canWrite(this) || sharedPrefs.getBoolean(KEY_BYPASS_WRITE_SETTINGS, false);
        boolean accessibilityActive = isAccessibilityServiceEnabled(this, ActionAssistantService.class);

        if (audioGranted) {
            tvAudioCheck.setText("✅ Izin Rekam Audio (Disetujui)");
            tvAudioCheck.setTextColor(0xFF48BB78);
        } else {
            tvAudioCheck.setText("❌ Izin Rekam Audio (Dibutuhkan)");
            tvAudioCheck.setTextColor(0xFFE53E3E);
        }

        if (overlayGranted) {
            tvOverlayCheck.setText("✅ Izin Overlay Jendela (Disetujui)");
            tvOverlayCheck.setTextColor(0xFF48BB78);
        } else {
            tvOverlayCheck.setText("❌ Izin Overlay Jendela (Dibutuhkan)");
            tvOverlayCheck.setTextColor(0xFFE53E3E);
        }

        if (writeSettingsGranted) {
            tvWriteSettingsCheck.setText("✅ Izin Menulis Pengaturan / Kecerahan (Disetujui)");
            tvWriteSettingsCheck.setTextColor(0xFF48BB78);
        } else {
            tvWriteSettingsCheck.setText("❌ Izin Menulis Pengaturan / Kecerahan (Dibutuhkan)");
            tvWriteSettingsCheck.setTextColor(0xFFE53E3E);
        }

        if (accessibilityActive) {
            tvAccessibilityCheck.setText("✅ Layanan Aksesibilitas (Aktif)");
            tvAccessibilityCheck.setTextColor(0xFF48BB78);
        } else {
            tvAccessibilityCheck.setText("❌ Layanan Aksesibilitas (Dibutuhkan)");
            tvAccessibilityCheck.setTextColor(0xFFE53E3E);
        }

        if (audioGranted && overlayGranted && writeSettingsGranted && accessibilityActive) {
            tvStatus.setText("Status Layanan: AKTIF & SIAP");
            tvStatus.setTextColor(0xFF48BB78);
        } else {
            tvStatus.setText("Status Layanan: TIDAK AKTIF");
            tvStatus.setTextColor(0xFFE53E3E);
        }
    }

    private void handleFloatingBubbleClick() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_AUDIO_PERMISSION);
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            showOverlayPermissionDialog();
            return;
        }

        if (!Settings.System.canWrite(this) && !sharedPrefs.getBoolean(KEY_BYPASS_WRITE_SETTINGS, false)) {
            showWriteSettingsPopupDialog();
            return;
        }

        Intent intent = new Intent(this, FloatingBubbleService.class);
        androidx.core.content.ContextCompat.startForegroundService(this, intent);
        Toast.makeText(this, "Nova Agent Floating Bubble diaktifkan!", Toast.LENGTH_SHORT).show();
    }

    private void showWriteSettingsPopupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🛡️ Izin Sistem Diperlukan");
        builder.setMessage("Nova Agent membutuhkan izin 'Mengubah Setelan Sistem' untuk asisten AI.\n\n" +
                "⚠️ INFORMASI ANDROID GO:\n" +
                "Sistem operasi Infinix Android Go mengunci izin ini (sakelar abu-abu).\n\n" +
                "Apakah Anda ingin melewati ini?");

        builder.setPositiveButton("Buka Setelan", (dialog, which) -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        builder.setNegativeButton("Abaikan (Android Go)", (dialog, which) -> {
            sharedPrefs.edit().putBoolean(KEY_BYPASS_WRITE_SETTINGS, true).apply();
            updateUIState();
            Toast.makeText(MainActivity.this, "Izin dilewati dengan aman untuk Android Go!", Toast.LENGTH_LONG).show();
            handleFloatingBubbleClick();
        });

        builder.setNeutralButton("Batal", null);
        builder.show();
    }

    private void showOverlayPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Izin Hamparan Jendela");
        builder.setMessage("Silakan aktifkan izin 'Tampilkan di atas aplikasi lain' di halaman berikutnya.");
        builder.setPositiveButton("Buka Setelan", (dialog, which) -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void showApiKeyInputDialog() {
        String activeProvider = sharedPrefs.getString(KEY_AI_PROVIDER, "groq");
        String providerKeyName = "ApiKey_" + activeProvider;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("API Key " + activeProvider.toUpperCase());
        
        final EditText input = new EditText(this);
        input.setHint("Masukkan Kunci API " + activeProvider.toUpperCase() + "...");
        input.setText(sharedPrefs.getString(providerKeyName, ""));
        builder.setView(input);

        builder.setPositiveButton("Simpan", (dialog, which) -> {
            String key = input.getText().toString().trim();
            sharedPrefs.edit().putString(providerKeyName, key).apply();
            Toast.makeText(MainActivity.this, "API Key " + activeProvider.toUpperCase() + " Berhasil Disimpan!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        String prefString = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return prefString != null && prefString.contains(context.getPackageName() + "/" + serviceClass.getName());
    }
}
