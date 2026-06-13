package com.novaagent.app.ui.bubble;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
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
        EventBus.getInstance().subscribe(AgentEvent.EventType.STATE_CHANGED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ERROR, this);
        initView();
    }

    private void initView() {
        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(30, 20, 30, 20);
        container.setGravity(Gravity.CENTER);

        // Desain Bubble Modern (Melingkar/Radius, Cyber Theme)
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadius(50f); // Melengkung Halus
        shape.setColor(0xDD1E1E1E); // Hitam Elegan (Sedikit transparan)
        shape.setStroke(3, 0xFF00E5FF); // Garis Pinggir Cyan Neon
        container.setBackground(shape);

        statusText = new TextView(context);
        statusText.setTextColor(0xFF00E5FF); // Cyan
        statusText.setText("NOVA: IDLE");
        statusText.setTextSize(14f);
        statusText.setTypeface(null, android.graphics.Typeface.BOLD);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 0, 0, 10);

        Button actionBtn = new Button(context);
        actionBtn.setText("JALANKAN");
        actionBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00E5FF));
        actionBtn.setTextColor(0xFF000000);
        
        actionBtn.setOnClickListener(v -> {
            try {
                AutonomousLoopEngine engine = ServiceLocator.getInstance().resolve(AutonomousLoopEngine.class);
                engine.startTask("Carikan artikel tentang teknologi AI terbaru hari ini");
            } catch (Exception e) {
                statusText.setText("ERROR: OTAK MATI");
            }
        });

        container.addView(statusText);
        container.addView(actionBtn);

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
        params.x = 20; params.y = 200;

        try { windowManager.addView(container, params); isAdded = true; } catch (Exception e) {}
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
        if (event.type == AgentEvent.EventType.STATE_CHANGED || event.type == AgentEvent.EventType.ERROR) {
            if (statusText != null) {
                statusText.setText(String.valueOf(event.payload));
            }
        }
    }
}
