package com.novaagent.app.ui.bubble;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.core.engine.AutonomousLoopEngine;
import com.novaagent.app.core.di.ServiceLocator;

import java.util.ArrayList;
import java.util.Locale;

public class FloatingBubbleView implements BubbleViewModel.ViewCallback, EventBus.EventListener {

    private final Context context;
    private final WindowManager windowManager;
    private final BubbleViewModel viewModel;
    
    private View bubbleRootView;
    private WindowManager.LayoutParams layoutParams;
    
    private TextView tvStatus;
    private GradientDrawable bgAurora;
    private ValueAnimator breathingAnimator;
    
    private boolean isAddedToWindow = false;
    private boolean isAgentRunning = false;
    
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;

    public FloatingBubbleView(Context appContext, BubbleViewModel viewModel) {
        this.context = appContext;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.viewModel = viewModel;
        this.viewModel.attachView(this);
        
        setupSpeechRecognizer();
        setupTextToSpeech();
        
        EventBus.getInstance().subscribe(AgentEvent.EventType.VOICE_RECEIVED, this);
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(new Locale("id", "ID"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to English if Indonesian is not installed
                    textToSpeech.setLanguage(Locale.US);
                    Toast.makeText(context, "Data TTS Bahasa Indonesia tidak ditemukan di HP ini. Nova akan pakai bahasa Inggris.", Toast.LENGTH_LONG).show();
                }
                textToSpeech.setSpeechRate(1.1f);
                textToSpeech.setPitch(1.1f);
            }
        });
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (event.type == AgentEvent.EventType.VOICE_RECEIVED) {
            String speechText = (String) event.payload;
            if (textToSpeech != null && speechText != null && !speechText.isEmpty()) {
                textToSpeech.speak(speechText, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        }
    }

    private void setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    tvStatus.setText("MENDENGAR...");
                    bgAurora.setStroke(dpToPx(3), Color.parseColor("#FF0055"));
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {
                    tvStatus.setText("MEMIKIRKAN...");
                    bgAurora.setStroke(dpToPx(2), Color.parseColor("#E024FF"));
                    startBreathingEffect();
                }
                @Override public void onError(int error) {
                    tvStatus.setText("SUARA TAK JELAS");
                    stopBreathingEffect();
                    isAgentRunning = false;
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        startAgentTask(matches.get(0));
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private int dpToPx(int dp) {
        return Math.round((float) dp * context.getResources().getDisplayMetrics().density);
    }

    @SuppressLint("ClickableViewAccessibility")
    public void show() {
        if (isAddedToWindow) return;

        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER);
        
        int bubbleSize = dpToPx(90); // Sedikit lebih estetik
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(bubbleSize, bubbleSize);
        rootLayout.setLayoutParams(params);

        bgAurora = new GradientDrawable();
        bgAurora.setShape(GradientDrawable.OVAL);
        bgAurora.setColor(Color.parseColor("#E60B0F19")); // Hitam Transparan
        bgAurora.setStroke(dpToPx(2), Color.parseColor("#00FFFF")); // Tepi Neon Cyan
        rootLayout.setBackground(bgAurora);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("NOVA Ai");
        tvTitle.setTextColor(Color.parseColor("#FFFFFF"));
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(null, Typeface.BOLD);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setShadowLayer(15f, 0f, 0f, Color.parseColor("#00FFFF"));
        
        TextView tvAuthor = new TextView(context);
        tvAuthor.setText("by Azman");
        tvAuthor.setTextColor(Color.parseColor("#888888"));
        tvAuthor.setTextSize(8f);
        tvAuthor.setGravity(Gravity.CENTER);

        tvStatus = new TextView(context);
        tvStatus.setText("TAP & SPEAK");
        tvStatus.setTextColor(Color.parseColor("#00FFFF"));
        tvStatus.setTextSize(9f);
        tvStatus.setTypeface(null, Typeface.BOLD);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setPadding(0, dpToPx(4), 0, 0);

        rootLayout.addView(tvTitle);
        rootLayout.addView(tvAuthor);
        rootLayout.addView(tvStatus);

        bubbleRootView = rootLayout;

        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? 
                         WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                         WindowManager.LayoutParams.TYPE_PHONE;

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        layoutParams.x = 0;
        layoutParams.y = 0;

        bubbleRootView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x; initialY = layoutParams.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        isDragging = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        float diffX = event.getRawX() - initialTouchX;
                        float diffY = event.getRawY() - initialTouchY;
                        if (Math.abs(diffX) > 10 || Math.abs(diffY) > 10) {
                            isDragging = true;
                            layoutParams.x = initialX - (int) diffX; // Geser kebalik karena Gravity.END
                            layoutParams.y = initialY + (int) diffY;
                            windowManager.updateViewLayout(bubbleRootView, layoutParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            if (isAgentRunning) stopAgent();
                            else listenToVoice();
                        }
                        return true;
                }
                return false;
            }
        });

        try { windowManager.addView(bubbleRootView, layoutParams); isAddedToWindow = true; } catch (Exception e) {}
    }
    
    private void startBreathingEffect() {
        if (breathingAnimator == null) {
            breathingAnimator = ValueAnimator.ofInt(50, 255);
            breathingAnimator.setDuration(800);
            breathingAnimator.setRepeatCount(ValueAnimator.INFINITE);
            breathingAnimator.setRepeatMode(ValueAnimator.REVERSE);
            breathingAnimator.addUpdateListener(animator -> {
                int alpha = (int) animator.getAnimatedValue();
                bgAurora.setStroke(dpToPx(2), Color.argb(alpha, 224, 36, 255)); // E024FF
            });
        }
        breathingAnimator.start();
    }

    private void stopBreathingEffect() {
        if (breathingAnimator != null) {
            breathingAnimator.cancel();
        }
        bgAurora.setStroke(dpToPx(2), Color.parseColor("#00FFFF"));
    }

    private void listenToVoice() {
        if (textToSpeech != null && textToSpeech.isSpeaking()) textToSpeech.stop(); 
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "id-ID");
            try {
                speechRecognizer.startListening(intent);
                isAgentRunning = true;
            } catch (Exception e) { 
                Toast.makeText(context, "Microphone Error", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startAgentTask(String task) {
        try {
            AutonomousLoopEngine engine = ServiceLocator.getInstance().resolve(AutonomousLoopEngine.class);
            engine.startTask(task);
        } catch (Exception e) {
            onError("Sistem Belum Siap");
        }
    }

    private void stopAgent() {
        isAgentRunning = false;
        stopBreathingEffect();
        if (speechRecognizer != null) speechRecognizer.cancel();
        if (textToSpeech != null && textToSpeech.isSpeaking()) textToSpeech.stop();
        
        try {
            AutonomousLoopEngine engine = ServiceLocator.getInstance().resolve(AutonomousLoopEngine.class);
            engine.stopTask();
        } catch (Exception e) {}
        
        tvStatus.setText("TAP & SPEAK");
        tvStatus.setTextColor(Color.parseColor("#00FFFF"));
    }

    public void remove() {
        EventBus.getInstance().unsubscribe(AgentEvent.EventType.VOICE_RECEIVED, this);
        if (textToSpeech != null) { textToSpeech.stop(); textToSpeech.shutdown(); }
        if (speechRecognizer != null) speechRecognizer.destroy();
        stopBreathingEffect();
        if (isAddedToWindow && bubbleRootView != null) {
            try { windowManager.removeView(bubbleRootView); isAddedToWindow = false; } catch (Exception e) {}
        }
    }

    @Override
    public void onStateChanged(String stateName) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (tvStatus != null && isAgentRunning) {
                tvStatus.setText(stateName);
                if (stateName.equals("IDLE") || stateName.equals("TUGAS SELESAI")) {
                    tvStatus.setTextColor(Color.parseColor("#00FF00"));
                    isAgentRunning = false;
                    stopBreathingEffect();
                    bubbleRootView.postDelayed(this::stopAgent, 2500);
                } else if (stateName.equals("ERROR")) {
                    onError("API ERROR");
                } else {
                    tvStatus.setTextColor(Color.parseColor("#E024FF"));
                }
            }
        });
    }

    @Override
    public void onError(String errorMessage) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (tvStatus != null) {
                tvStatus.setText("GAGAL: " + errorMessage.substring(0, Math.min(10, errorMessage.length())));
                tvStatus.setTextColor(Color.parseColor("#FF0055"));
                stopBreathingEffect();
                bgAurora.setStroke(dpToPx(2), Color.parseColor("#FF0055"));
                bubbleRootView.postDelayed(this::stopAgent, 3000);
            }
        });
    }
}
