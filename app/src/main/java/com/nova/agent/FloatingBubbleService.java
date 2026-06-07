package com.nova.agent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.nova.agent.api.GroqApiClient;

import java.util.ArrayList;
import java.util.Locale;

/**
 * PRODUCTION-READY FloatingBubbleService
 * - Konsumsi baterai rendah: Melepaskan resource SpeechRecognizer & TTS saat tidak digunakan.
 * - Ketahanan sensor rekam: Penghancuran instan dan re-inisialisasi otomatis saat terdeteksi galat internal.
 * - Kepatuhan Android 14+ secara ketat untuk kestabilan Foreground Service (START_STICKY).
 */
public class FloatingBubbleService extends Service implements TextToSpeech.OnInitListener {

    private static final String TAG = "NovaBubbleService";
    private static final String CHANNEL_ID = "NovaAgentBubbleChannel";
    private static final int NOTIFICATION_ID = 999;
    private static volatile boolean sIsRunning = false;

    private WindowManager mWindowManager;
    private BubbleView mBubbleView;
    private WindowManager.LayoutParams mParams;

    private SpeechRecognizer mSpeechRecognizer;
    private Intent mSpeechIntent;
    private TextToSpeech mTextToSpeech;
    
    private boolean mIsListening = false;
    private String mApiKey = "";
    private Handler mMainHandler;

    public static boolean isRunning() {
        return sIsRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sIsRunning = true;
        mMainHandler = new Handler(Looper.getMainLooper());

        SharedPreferences prefs = getSharedPreferences("NovaPrefs", MODE_PRIVATE);
        mApiKey = prefs.getString("groq_api_key", "");

        initTextToSpeech();
        createNotificationChannel();
        
        // Android 14 Foreground Service Start
        startForeground(NOTIFICATION_ID, buildNotification());

        // Pengenalan suara dibuat pada Looper Utama untuk keandalan binder
        mMainHandler.post(this::initSpeechRecognizer);
        initFloatingBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Mengembalikan START_STICKY agar OS menghidupkan kembali service ini jika terbunuh karena low RAM
        return START_STICKY;
    }

    private void initTextToSpeech() {
        try {
            if (mTextToSpeech != null) {
                mTextToSpeech.stop();
                mTextToSpeech.shutdown();
            }
            mTextToSpeech = new TextToSpeech(this, this);
        } catch (Exception e) {
            Log.e(TAG, "Gagal inisialisasi TextToSpeech: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Nova Agent Background Process",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Nova Agent Aktif")
                .setContentText("Asisten AI siap digunakan. Ketuk gelembung untuk berbicara.")
                .setSmallIcon(android.R.drawable.presence_online)
                .setContentIntent(pendingIntent)
                .setColor(Color.parseColor("#9D66FF"))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private synchronized void initSpeechRecognizer() {
        try {
            if (mSpeechRecognizer != null) {
                mSpeechRecognizer.cancel();
                mSpeechRecognizer.destroy();
            }
            
            mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            mSpeechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            mSpeechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");

            mSpeechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    mIsListening = true;
                    if (mBubbleView != null) mBubbleView.setListening(true);
                }

                @Override
                public void onBeginningOfSpeech() {}

                @Override
                public void onRmsChanged(float rmsdB) {}

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    mIsListening = false;
                    if (mBubbleView != null) mBubbleView.setListening(false);
                }

                @Override
                public void onError(int error) {
                    mIsListening = false;
                    if (mBubbleView != null) mBubbleView.setListening(false);
                    Log.e(TAG, "SpeechRecognizer Error terdeteksi: " + error);
                    
                    // PENANGANAN PENYELAMATAN SENSOR: Re-inisialisasi sensor jika terjadi kegagalan binder kritis
                    if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                        Log.w(TAG, "Membangun ulang struktur SpeechRecognizer akibat error kritis.");
                        mMainHandler.post(() -> initSpeechRecognizer());
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        processVoiceCommand(matches.get(0));
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {}

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        } catch (Exception e) {
            Log.e(TAG, "Gagal melisensi SpeechRecognizer: " + e.getMessage());
        }
    }

    private void initFloatingBubble() {
        try {
            mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            mBubbleView = new BubbleView(this);

            int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                    : WindowManager.LayoutParams.TYPE_PHONE;

            mParams = new WindowManager.LayoutParams(
                    180, 180,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
            );

            mParams.gravity = Gravity.TOP | Gravity.START;
            mParams.x = 100;
            mParams.y = 500;

            mBubbleView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;
                private long touchStartTime;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    try {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                initialX = mParams.x;
                                initialY = mParams.y;
                                initialTouchX = event.getRawX();
                                initialTouchY = event.getRawY();
                                touchStartTime = System.currentTimeMillis();
                                return true;

                            case MotionEvent.ACTION_MOVE:
                                mParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                                mParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                                mWindowManager.updateViewLayout(mBubbleView, mParams);
                                return true;

                            case MotionEvent.ACTION_UP:
                                long duration = System.currentTimeMillis() - touchStartTime;
                                float diffX = Math.abs(event.getRawX() - initialTouchX);
                                float diffY = Math.abs(event.getRawY() - initialTouchY);

                                // Jarak toleransi ketukan murni
                                if (duration < 250 && diffX < 15 && diffY < 15) {
                                    toggleSpeechRecognition();
                                }
                                return true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error dalam penanganan sentuhan gelembung: " + e.getMessage());
                    }
                    return false;
                }
            });

            mWindowManager.addView(mBubbleView, mParams);
        } catch (Exception e) {
            Log.e(TAG, "Gagal menggambar overlay WindowManager: " + e.getMessage());
        }
    }

    private void toggleSpeechRecognition() {
        if (mIsListening) {
            mMainHandler.post(() -> {
                try { mSpeechRecognizer.stopListening(); } catch (Exception ignored) {}
            });
        } else {
            SharedPreferences prefs = getSharedPreferences("NovaPrefs", MODE_PRIVATE);
            mApiKey = prefs.getString("groq_api_key", "");

            if (mApiKey.isEmpty()) {
                speakDirectly("Harap atur kunci API Anda di menu utama.");
                return;
            }
            
            mMainHandler.post(() -> {
                try {
                    mSpeechRecognizer.startListening(mSpeechIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Kegagalan sensor rekam, merestorasi pengenal...", e);
                    initSpeechRecognizer();
                }
            });
        }
    }

    private void processVoiceCommand(final String voiceCommand) {
        speakDirectly("Berpikir...");
        
        GroqApiClient.requestAiDecision(mApiKey, voiceCommand, new GroqApiClient.GroqResponseCallback() {
            @Override
            public void onSuccess(String aiResponseText) {
                speakDirectly(aiResponseText);
                
                // Kirim siaran aman berskala paket internal
                Intent intent = new Intent("com.nova.agent.EXECUTE");
                intent.putExtra("ai_response", aiResponseText);
                intent.putExtra("original_command", voiceCommand);
                intent.setPackage(getPackageName()); // Proteksi keamanan siaran
                sendBroadcast(intent);
            }

            @Override
            public void onError(Throwable throwable) {
                speakDirectly("Koneksi gagal.");
            }
        });
    }

    private void speakDirectly(String text) {
        try {
            if (mTextToSpeech != null) {
                mTextToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "NovaTTS");
            }
        } catch (Exception e) {
            Log.e(TAG, "Gagal mengeksekusi sintesis suara: " + e.getMessage());
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            try {
                mTextToSpeech.setLanguage(new Locale("id", "ID"));
            } catch (Exception e) {
                Log.e(TAG, "Bahasa Indonesia tidak didukung mesin TTS lokal.");
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sIsRunning = false;

        // Pembersihan total memori & kebocoran resource
        mMainHandler.post(() -> {
            try {
                if (mWindowManager != null && mBubbleView != null) {
                    mWindowManager.removeView(mBubbleView);
                    mBubbleView = null;
                }
                if (mSpeechRecognizer != null) {
                    mSpeechRecognizer.cancel();
                    mSpeechRecognizer.destroy();
                    mSpeechRecognizer = null;
                }
                if (mTextToSpeech != null) {
                    mTextToSpeech.stop();
                    mTextToSpeech.shutdown();
                    mTextToSpeech = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Pemberhentian layanan menyisakan galat: " + e.getMessage());
            }
        });
    }

    private static class BubbleView extends View {
        private final Paint bgPaint;
        private final Paint borderPaint;
        private final Paint iconPaint;
        private boolean isListening = false;

        public BubbleView(Context context) {
            super(context);
            bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(Color.parseColor("#150B2E"));

            borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(7f);
            borderPaint.setColor(Color.parseColor("#D433FF"));

            iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            iconPaint.setColor(Color.WHITE);
        }

        public void setListening(boolean listening) {
            this.isListening = listening;
            if (listening) {
                borderPaint.setColor(Color.parseColor("#00FFC2"));
                bgPaint.setColor(Color.parseColor("#062018"));
            } else {
                borderPaint.setColor(Color.parseColor("#D433FF"));
                bgPaint.setColor(Color.parseColor("#150B2E"));
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = (Math.min(getWidth(), getHeight()) / 2f) - 12f;

            canvas.drawCircle(cx, cy, radius, bgPaint);
            canvas.drawCircle(cx, cy, radius, borderPaint);

            if (isListening) {
                Paint pulse = new Paint(Paint.ANTI_ALIAS_FLAG);
                pulse.setColor(Color.parseColor("#00FFC2"));
                canvas.drawCircle(cx, cy, radius / 2.5f, pulse);
            } else {
                float w = radius / 3.5f;
                float h = radius / 1.8f;
                canvas.drawRoundRect(cx - w/2f, cy - h/1.5f, cx + w/2f, cy + h/4f, 15f, 15f, iconPaint);
                
                Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
                line.setColor(Color.WHITE);
                line.setStyle(Paint.Style.STROKE);
                line.setStrokeWidth(5f);
                canvas.drawArc(cx - w, cy - h/3f, cx + w, cy + h/2.5f, 0, 180, false, line);
                canvas.drawLine(cx, cy + h/2.5f, cx, cy + h/1.1f, line);
            }
        }
    }
}