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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.nova.agent.api.GroqApiClient;
import java.util.ArrayList;
import java.util.Locale;

public class FloatingBubbleService extends Service implements TextToSpeech.OnInitListener {
    private static final String TAG = "NovaBubbleService";
    private static final String CHANNEL_ID = "NovaAgentBubbleChannel";
    private static final int NOTIFICATION_ID = 999;

    private WindowManager mWindowManager;
    private View mBubbleView;
    private WindowManager.LayoutParams mParams;
    private SpeechRecognizer mSpeechRecognizer;
    private TextToSpeech mTTS;
    private SharedPreferences mSharedPrefs;
    private boolean mIsListening = false;

    @Override
    public void onCreate() {
        super.onCreate();
        mSharedPrefs = getSharedPreferences("NovaAgentPrefs", MODE_PRIVATE);
        
        // 1. BUAT NOTIFIKASI FOREGROUND INSTAN (Wajib untuk mencegah crash Android 14)
        createNotificationChannel();
        Notification notification = buildNotification("Nova Agent aktif di latar belakang");
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal memulai Foreground Service: " + e.getMessage());
            startForeground(NOTIFICATION_ID, notification);
        }

        // 2. INISIALISASI TEXT-TO-SPEECH
        mTTS = new TextToSpeech(this, this);

        // 3. GAMBAR GELEMBUNG MELAYANG SECARA AMAN (Bebas dari Crash WindowManager di Android Go)
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mBubbleView = new View(this) {
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                // Gambar Bola Melayang Ungu Neon dengan Bingkai Biru Muda
                paint.setColor(0xFF00F5D4); // Neon Blue Border
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, paint);
                paint.setColor(0xFF6B46C1); // Purple Core
                canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, (getWidth() / 2f) - 6f, paint);
            }
        };

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        mParams = new WindowManager.LayoutParams(
                140, 140, // Lebar & Tinggi Gelembung melayang
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = 100;
        mParams.y = 100;

        // Pasang sensor gerakan seret pada gelembung
        mBubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int lastAction;
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = mParams.x;
                        initialY = mParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        lastAction = event.getAction();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        mParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        mParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        try {
                            mWindowManager.updateViewLayout(mBubbleView, mParams);
                        } catch (Exception e) {
                            Log.e(TAG, "Gagal menggeser gelembung: " + e.getMessage());
                        }
                        lastAction = event.getAction();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            // Deteksi klik cepat -> Mulai/Hentikan Mendengar Perintah Suara!
                            toggleVoiceListening();
                        }
                        lastAction = event.getAction();
                        return true;
                }
                return false;
            }
        });

        // Tampilkan gelembung di layar secara aman dengan proteksi try-catch
        try {
            mWindowManager.addView(mBubbleView, mParams);
        } catch (Exception e) {
            Log.e(TAG, "Gagal menampilkan gelembung melayang: " + e.getMessage());
            Toast.makeText(this, "Gagal memunculkan gelembung. Pastikan izin Overlay aktif!", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void toggleVoiceListening() {
        if (mIsListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    private void startListening() {
        mIsListening = true;
        speakDirectly("Ya, saya mendengarkan.");
        
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mSpeechRecognizer != null) {
                        mSpeechRecognizer.destroy();
                    }
                    mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(FloatingBubbleService.this);
                    Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                    intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID"); // Bahasa Indonesia

                    mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                        @Override
                        public void onReadyForSpeech(Bundle params) {}
                        @Override
                        public void onBeginningOfSpeech() {}
                        @Override
                        public void onRmsChanged(float rmsdB) {}
                        @Override
                        public void onBufferReceived(byte[] buffer) {}
                        @Override
                        public void onEndOfSpeech() {
                            mIsListening = false;
                        }
                        @Override
                        public void onError(int error) {
                            mIsListening = false;
                            Log.e(TAG, "SpeechRecognizer Error: " + error);
                        }

                        @Override
                        public void onResults(Bundle results) {
                            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                            if (matches != null && !matches.isEmpty()) {
                                String voiceCommand = matches.get(0);
                                processVoiceCommand(voiceCommand);
                            }
                        }

                        @Override
                        public void onPartialResults(Bundle partialResults) {}
                        @Override
                        public void onEvent(int eventType, Bundle params) {}
                    });

                    mSpeechRecognizer.startListening(intent);
                } catch (Exception e) {
                    mIsListening = false;
                    Log.e(TAG, "Gagal memulai perekam suara: " + e.getMessage());
                }
            }
        });
    }

    private void stopListening() {
        mIsListening = false;
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.stopListening();
        }
    }

    private void processVoiceCommand(String voiceCommand) {
        String apiKey = mSharedPrefs.getString("GroqApiKey", "");
        if (apiKey.isEmpty()) {
            speakDirectly("API Key belum disetel. Silakan isi terlebih dahulu di dalam aplikasi.");
            return;
        }

        speakDirectly("Sedang berpikir.");

        GroqApiClient.requestAiDecision(apiKey, voiceCommand, new GroqApiClient.GroqResponseCallback() {
            @Override
            public void onSuccess(String aiResponseText) {
                speakDirectly(aiResponseText);
                
                // Kirim siaran aman untuk diolah oleh Layanan Aksesibilitas kita
                Intent intent = new Intent("com.nova.agent.EXECUTE");
                intent.putExtra("ai_response", aiResponseText);
                intent.putExtra("original_command", voiceCommand);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }

            @Override
            public void onError(Throwable throwable) {
                speakDirectly("Koneksi ke otak Groq gagal.");
            }
        });
    }

    private void speakDirectly(String text) {
        if (mTTS != null) {
            mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NovaSpeechID");
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            mTTS.setLanguage(new Locale("id", "ID"));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Layanan Melayang Nova Agent",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Nova Agent Aktif")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBubbleView != null && mWindowManager != null) {
            try {
                mWindowManager.removeView(mBubbleView);
            } catch (Exception e) {
                Log.e(TAG, "Gagal melepas gelembung melayang: " + e.getMessage());
            }
        }
        if (mSpeechRecognizer != null) {
            mSpeechRecognizer.destroy();
        }
        if (mTTS != null) {
            mTTS.stop();
            mTTS.shutdown();
        }
    }
}
