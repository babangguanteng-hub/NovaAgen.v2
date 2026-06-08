package com.nova.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import com.nova.agent.api.GroqApiClient;
import java.util.ArrayList;
import java.util.Locale;

public class FloatingBubbleService extends Service implements TextToSpeech.OnInitListener {
    private WindowManager mWindowManager;
    private View mBubbleView;
    private WindowManager.LayoutParams mParams;
    private SpeechRecognizer mSpeechRecognizer;
    private TextToSpeech mTTS;
    private SharedPreferences mSharedPrefs;
    private Vibrator mVibrator;
    
    public boolean mIsListening = false;
    private float mAnimOffset = 0f;
    private Handler mAnimHandler = new Handler(Looper.getMainLooper());
    private Runnable mAnimRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        mSharedPrefs = getSharedPreferences("NovaAgentPrefs", MODE_PRIVATE);
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        
        // Setup Foreground Service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel("NovaBubble", "Nova Bubble", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Notification notif = new NotificationCompat.Builder(this, "NovaBubble")
                .setContentTitle("NOVA AI")
                .setContentText("Asisten Moch Khoirul Azman Aktif")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(999, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else { startForeground(999, notif); }
        } catch (Exception ignored) {}

        mTTS = new TextToSpeech(this, this);

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mBubbleView = new View(this) {
            private Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private Path wavePath = new Path();

            @Override
            protected void onDraw(Canvas canvas) {
                float cx = getWidth() / 2f;
                float cy = getHeight() / 2f;
                float baseRadius = (getWidth() / 2f) - 20f;

                mAnimOffset += 0.04f;

                // 1. Bola Cahaya Latar (Aurora Glow)
                // Jika sedang mendengar (mIsListening), ubah warna menjadi Merah Menyala
                int colorOuter = mIsListening ? 0xFFFF0055 : 0xFF8A2BE2; // Red vs BlueViolet
                int colorInner = mIsListening ? 0xFFFF5555 : 0xFFD02090; // LightRed vs VioletRed

                glowPaint.setShader(new RadialGradient(cx, cy, baseRadius + 15f,
                        new int[]{colorInner, colorOuter, Color.TRANSPARENT},
                        null, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, baseRadius + 15f, glowPaint);

                // 2. Gelombang Energi (Sine Wave) yang melingkar
                wavePaint.setStyle(Paint.Style.STROKE);
                wavePaint.setStrokeWidth(3f);
                wavePaint.setColor(mIsListening ? 0xFFFFFFFF : 0xFF00F5D4); // White or Neon Cyan
                wavePaint.setMaskFilter(new BlurMaskFilter(4f, BlurMaskFilter.Blur.SOLID));
                
                wavePath.reset();
                int numPoints = 60;
                for (int i = 0; i <= numPoints; i++) {
                    float angle = (float) (i * 2 * Math.PI / numPoints);
                    // Matematika gelombang referensi gambar
                    float wave = (float) Math.sin(angle * 5 + mAnimOffset * 3) * 12f; 
                    float r = baseRadius + wave;
                    float x = cx + r * (float) Math.cos(angle);
                    float y = cy + r * (float) Math.sin(angle);
                    if (i == 0) wavePath.moveTo(x, y);
                    else wavePath.lineTo(x, y);
                }
                wavePath.close();
                canvas.drawPath(wavePath, wavePaint);

                // 3. Teks Identitas Azman
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setFakeBoldText(true);
                textPaint.setShadowLayer(5f, 0, 0, Color.BLACK);

                if (!mIsListening) {
                    textPaint.setTextSize(26f);
                    textPaint.setColor(0xFFFFFFFF);
                    canvas.drawText("NOVA", cx, cy - 10f, textPaint);
                    
                    textPaint.setTextSize(10f);
                    textPaint.setColor(0xFF00F5D4);
                    canvas.drawText("by M. K. Azman", cx, cy + 15f, textPaint);
                } else {
                    textPaint.setTextSize(18f);
                    textPaint.setColor(0xFFFFFFFF);
                    canvas.drawText("MENDENGAR...", cx, cy + 5f, textPaint);
                }
            }
        };

        mAnimRunnable = new Runnable() {
            @Override
            public void run() {
                mBubbleView.invalidate();
                mAnimHandler.postDelayed(this, 30); // ~30fps smooth animation
            }
        };
        mAnimHandler.post(mAnimRunnable);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
        mParams = new WindowManager.LayoutParams(250, 250, layoutType, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        mParams.gravity = Gravity.TOP | Gravity.START; mParams.x = 100; mParams.y = 100;

        mBubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY, lastAction;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x; initialY = mParams.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        lastAction = event.getAction(); return true;
                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        mWindowManager.updateViewLayout(mBubbleView, mParams);
                        lastAction = event.getAction(); return true;
                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) toggleVoiceListening();
                        return true;
                }
                return false;
            }
        });
        try { mWindowManager.addView(mBubbleView, mParams); } catch (Exception ignored) {}
    }

    private void toggleVoiceListening() {
        if (mIsListening) stopListening(); else startListening();
    }

    private void startListening() {
        mIsListening = true;
        // FIX BUG BENTROK: Tidak ada lagi suara "Sedang mendengar". Diganti dengan Getaran Haptic!
        if (mVibrator != null) mVibrator.vibrate(50);
        if (mTTS != null) mTTS.stop(); // Hentikan ocehan AI jika masih ngoceh
        
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (mSpeechRecognizer != null) mSpeechRecognizer.destroy();
                mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");

                mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle p) {}
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float r) {}
                    @Override public void onBufferReceived(byte[] b) {}
                    @Override public void onEndOfSpeech() { mIsListening = false; }
                    @Override public void onError(int e) { mIsListening = false; }
                    @Override public void onResults(Bundle results) {
                        mIsListening = false;
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) processCommand(matches.get(0));
                    }
                    @Override public void onPartialResults(Bundle p) {}
                    @Override public void onEvent(int e, Bundle p) {}
                });
                mSpeechRecognizer.startListening(intent);
            } catch (Exception e) { mIsListening = false; }
        });
    }

    private void stopListening() {
        mIsListening = false;
        if (mSpeechRecognizer != null) mSpeechRecognizer.stopListening();
    }

    private void processCommand(String voiceCommand) {
        String provider = mSharedPrefs.getString("SelectedAiProvider", "groq");
        String apiKey = mSharedPrefs.getString("ApiKey_" + provider, "");
        if (apiKey.isEmpty()) { speak("API Key kosong."); return; }

        GroqApiClient.requestAiDecision(provider, apiKey, voiceCommand, new GroqApiClient.GroqResponseCallback() {
            @Override
            public void onSuccess(String speechText, String commandCode) {
                speak(speechText);
                if (commandCode != null) {
                    // Kirim perintah eksekusi layar ke ActionAssistantService
                    Intent intent = new Intent("com.nova.agent.EXECUTE_ACTION");
                    intent.putExtra("command_code", commandCode);
                    intent.setPackage(getPackageName());
                    sendBroadcast(intent);
                }
            }
            @Override public void onError(Throwable t) { speak("Koneksi gagal."); }
        });
    }

    private void speak(String text) {
        if (mTTS != null) mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NovaID");
    }

    @Override public void onInit(int status) { if (status == TextToSpeech.SUCCESS) mTTS.setLanguage(new Locale("id", "ID")); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        super.onDestroy();
        mAnimHandler.removeCallbacks(mAnimRunnable);
        try { mWindowManager.removeView(mBubbleView); } catch (Exception ignored) {}
        if (mSpeechRecognizer != null) mSpeechRecognizer.destroy();
        if (mTTS != null) mTTS.shutdown();
    }
}
