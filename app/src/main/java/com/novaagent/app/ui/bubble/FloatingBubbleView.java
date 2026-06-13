package com.novaagent.app.ui.bubble;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.core.engine.AutonomousLoopEngine;

public class FloatingBubbleView implements EventBus.EventListener {
    private final Context context;
    private final WindowManager windowManager;
    private LinearLayout container;
    private TextView statusText;
    private boolean isAdded = false;

    public FloatingBubbleView(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        
        // Mendaftar ke EventBus untuk mendengarkan perubahan status AI
        EventBus.getInstance().subscribe(AgentEvent.EventType.STATE_CHANGED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ERROR, this);
        
        initView();
    }

    private void initView() {
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(0xAA000000); // Hitam transparan
        container.setPadding(20, 20, 20, 20);

        statusText = new TextView(context);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setText("IDLE");
        statusText.setTextSize(12f);

        Button actionBtn = new Button(context);
        actionBtn.setText("NOVA");
        actionBtn.setOnClickListener(v -> {
            try {
                AutonomousLoopEngine engine = ServiceLocator.getInstance().resolve(AutonomousLoopEngine.class);
                // Contoh hardcoded sementara, nantinya disambungkan ke SpeechRecognizer (Voice)
                engine.startTask("Carikan video kucing lucu di YouTube"); 
            } catch (Exception e) {
                statusText.setText("Error: Otak Belum Siap");
            }
        });

        container.addView(statusText);
        container.addView(actionBtn);

        // Menambahkan kemampuan digeser (Drag)
        container.setOnTouchListener(new android.view.View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private WindowManager.LayoutParams params;

            @Override
            public boolean onTouch(android.view.View v, MotionEvent event) {
                if (params == null) params = (WindowManager.LayoutParams) container.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x; initialY = params.y;
                        initialTouchX = event.getRawX(); initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(container, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        // Jika tidak bergeser jauh, anggap sebagai klik
                        if (Math.abs(event.getRawX() - initialTouchX) < 10 && Math.abs(event.getRawY() - initialTouchY) < 10) {
                            actionBtn.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    public void show() {
        if (isAdded) return;
        int layoutFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O 
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0; params.y = 100;

        try {
            windowManager.addView(container, params);
            isAdded = true;
        } catch (Exception e) {}
    }

    public void remove() {
        if (isAdded && container != null) {
            try { windowManager.removeView(container); } catch (Exception e) {}
            isAdded = false;
        }
        EventBus.getInstance().unsubscribe(AgentEvent.EventType.STATE_CHANGED, this);
        EventBus.getInstance().unsubscribe(AgentEvent.EventType.ERROR, this);
    }

    @Override
    public void onEvent(AgentEvent event) {
        // Pembaruan UI selalu aman karena kita melempar event ini di MainLane (UI Thread) EventBus!
        if (event.type == AgentEvent.EventType.STATE_CHANGED || event.type == AgentEvent.EventType.ERROR) {
            if (statusText != null) {
                statusText.setText(String.valueOf(event.payload));
            }
        }
    }
}
