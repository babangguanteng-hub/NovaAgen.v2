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
import android.graphics.RadialGradient;
import android.graphics.Shader;
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
    
    // Aurora Animation Variables
    private float mAnimationOffset = 0f;
    private Handler mAnimHandler = new Handler(Looper.getMainLooper());
    private Runnable mAnimRunnable;

    @Override
    public void onCreate() {
        super.onCreate();
        mSharedPrefs = getSharedPreferences("NovaAgentPrefs", MODE_PRIVATE);
        
        // 1. FOREGROUND SERVICE HANDSHAKE
        createNotificationChannel();
        Notification notification = buildNotification("Nova Agent aktif melayang.");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            startForeground(NOTIFICATION_ID, notification);
        }

        // 2. TTS INIT
        mTTS = new TextToSpeech(this, this);

        // 3. GAMBAR GELEMBUNG MELAYANG AURORA DENGAN WATERMARK DEVS
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mBubbleView = new View(this) {
            private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                float width = getWidth();
                float height = getHeight();
                float cx = width / 2f;
                float cy = height / 2f;
                float radius = width / 2f;

                // Animasi Aurora: Pulsing Shifting Gradient Radius
                mAnimationOffset += 0.05f;
                float waveFactor = (float) Math.sin(mAnimationOffset) * 10f;
                float currentRadius = radius - 10f + waveFactor;

                // 1. Gambar Glow Shadow Aurora Luar
                mPaint.setShader(new RadialGradient(cx, cy, radius,
                        new int[]{0xFF00F5D4, 0xFF6B46C1, 0x00000000},
                        new float[]{0f, 0.7f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, radius, mPaint);

                // 2. Gambar Solid Aurora Core Tengah
                mPaint.setShader(new RadialGradient(cx, cy, currentRadius,
                        new int[]{0xFF6B46C1, 0xFFD53F8C, 0xFF00F5D4},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawCircle(cx, cy, currentRadius - 5f, mPaint);

                // 3. Gambar Stroke Border Neon
                mPaint.setShader(null);
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setColor(0xFFFFFFFF);
                mPaint.setStrokeWidth(3f);
                canvas.drawCircle(cx, cy, currentRadius - 5f, mPaint);
                mPaint.setStyle(Paint.Style.FILL); // Reset

                // 4. Gambar Teks Custom "NOVA AI by Moch Khoirul Azman"
                mTextPaint.setColor(Color.WHITE);
                mTextPaint.setFakeBoldText(true);
                mTextPaint.setTextAlign(Paint.Align.CENTER);
                mTextPaint.setShadowLayer(5f, 0, 0, Color.BLACK);

                // Line 1: NOVA AI
                mTextPaint.setTextSize(22f);
                mTextPaint.setColor(0xFF00F5D4); // Cyan Neon
                canvas.drawText("NOVA AI", cx, cy - 25f, mTextPaint);

                // Line 2: by
                mTextPaint.setTextSize(12f);
                mTextPaint.setColor(Color.WHITE);
                canvas.drawText("by", cx, cy - 5f, mTextPaint);

                // Line 3: Moch Khoirul
                mTextPaint.setTextSize(11f);
                mTextPaint.setColor(0xFFD53F8C); // Pink Magenta
                canvas.drawText("Moch Khoirul", cx, cy + 18f, mTextPaint);

                // Line 4: Azman
                canvas.drawText("Azman", cx, cy + 34f, mTextPaint);
            }
        };

        // Mulai Loop Animasi Ringan
        mAnimRunnable = new Runnable() {
            @Override
            public void run() {
                mBubbleView.invalidate();
                mAnimHandler.postDelayed(this, 33); // 30 FPS untuk kelancaran sempurna
            }
        };
        mAnimHandler.post(mAnimRunnable);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_PHONE;

        // Kita perbesar ukuran sedikit menjadi 220x220 agar tulisan Moch Khoirul Azman terbaca sangat tajam
        mParams = new WindowManager.LayoutParams(
                220, 220,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = 100;
        mParams.y = 100;

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
                        } catch (Exception ignored) {}
                        lastAction = event.getAction();
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (lastAction == MotionEvent.ACTION_DOWN) {
                            toggleVoiceListening();
                        }
                        lastAction = event.getAction();
                        return true;
                }
                return false;
            }
        });

        try {
            mWindowManager.addView(mBubbleView, mParams);
        } catch (Exception e) {
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
        speakDirectly("Halo saya Nova, sedang mendengarkan.");
        
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                if (mSpeechRecognizer != null) {
                    mSpeechRecognizer.destroy();
                }
                mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(FloatingBubbleService.this);
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");

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

                mSpeechRecognizer.startListening(intent);
            } catch (Exception e) {
                mIsListening = false;
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
        String activeProvider = mSharedPrefs.getString("SelectedAiProvider", "groq");
        String apiKey = mSharedPrefs.getString("ApiKey_" + activeProvider, "");

        if (apiKey.isEmpty()) {
            speakDirectly("Kunci API " + activeProvider.toUpperCase() + " belum disetel di aplikasi.");
            return;
        }

        speakDirectly("Sedang berpikir.");

        GroqApiClient.requestAiDecision(activeProvider, apiKey, voiceCommand, new GroqApiClient.GroqResponseCallback() {
            @Override
            public void onSuccess(String aiResponseText) {
                speakDirectly(aiResponseText);
                
                Intent intent = new Intent("com.nova.agent.EXECUTE");
                intent.putExtra("ai_response", aiResponseText);
                intent.putExtra("original_command", voiceCommand);
                intent.setPackage(getPackageName());
                sendBroadcast(intent);
            }

            @Override
            public void onError(Throwable throwable) {
                speakDirectly("Koneksi otak gagal: " + throwable.getMessage());
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
                .setContentTitle("Nova Agent")
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
        mAnimHandler.removeCallbacks(mAnimRunnable);
        if (mBubbleView != null && mWindowManager != null) {
            try {
                mWindowManager.removeView(mBubbleView);
            } catch (Exception ignored) {}
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
