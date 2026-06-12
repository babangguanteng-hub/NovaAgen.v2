package com.novaagent.app.core.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.data.repository.GroqApiClient;
import com.novaagent.app.data.repository.PromptRegistry;
import com.novaagent.app.infrastructure.injector.SystemActionInjector;
import com.novaagent.app.services.accessibility.NovaAccessibilityService;
import com.novaagent.app.core.di.ServiceLocator;

public class AutonomousLoopEngine implements EventBus.EventListener {
    private static final String TAG = "AutoLoopEngine";
    private final Context context;
    private final GroqApiClient groqClient;
    private final SystemActionInjector actionInjector;
    private String currentTask = "";
    private boolean isRunning = false;
    private final Handler mainHandler;

    public AutonomousLoopEngine(Context context) {
        this.context = context;
        this.groqClient = new GroqApiClient(context);
        this.actionInjector = new SystemActionInjector();
        this.mainHandler = new Handler(Looper.getMainLooper());
        
        // Mendaftarkan telinga saraf ke EventBus
        EventBus.getInstance().subscribe(AgentEvent.EventType.SCREEN_UPDATED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ACTION_EXECUTED, this);
    }

    public void startTask(String task) {
        this.currentTask = task;
        this.isRunning = true;
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "MEMINDAI LAYAR..."));
        requestScreenData();
    }

    public void stopTask() {
        this.isRunning = false;
        this.currentTask = "";
    }

    private void requestScreenData() {
        if (!isRunning) return;
        try {
            NovaAccessibilityService service = ServiceLocator.getInstance().resolve(NovaAccessibilityService.class);
            service.requestScreenScan();
        } catch (Exception e) {
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ERROR, "Aksesibilitas belum siap"));
            stopTask();
        }
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!isRunning) return;

        try {
            // [ANTI-CRASH]: Menangkap data layar sebagai String, BUKAN JSONArray
            if (event.type == AgentEvent.EventType.SCREEN_UPDATED) {
                String screenContext = String.valueOf(event.payload);
                processWithAI(screenContext);
            } 
            // [LOGIKA OTONOM]: Setelah Nova nge-klik/swipe, tunggu 2 detik, lalu scan layar lagi
            else if (event.type == AgentEvent.EventType.ACTION_EXECUTED) {
                mainHandler.postDelayed(this::requestScreenData, 2000); // Tunggu UI HP berubah
            }
        } catch (Exception e) {
            Log.e(TAG, "Error Engine", e);
            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ERROR, "Error Internal Otak"));
            stopTask();
        }
    }

    private void processWithAI(String screenContext) {
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "OTAK BERPIKIR..."));
        String userPrompt = PromptRegistry.buildUserPrompt(currentTask, screenContext);

        groqClient.sendPrompt(PromptRegistry.SYSTEM_PROMPT, userPrompt, new GroqApiClient.GroqCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                if (!isRunning) return;
                try {
                    ActionCommandDto cmd = new ActionCommandDto(jsonResponse);
                    
                    // Jika AI merasa tugasnya sudah selesai
                    if ("done".equalsIgnoreCase(cmd.action)) {
                        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "TUGAS SELESAI"));
                        if (cmd.speech != null && !cmd.speech.isEmpty()) {
                            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.VOICE_RECEIVED, cmd.speech));
                        }
                        stopTask();
                    } 
                    // Jika AI merasa masih harus nge-klik / swipe layar
                    else {
                        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "MENGEKSEKUSI..."));
                        actionInjector.executeAction(cmd);
                    }
                } catch (Exception e) {
                    EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ERROR, "Format Groq Salah"));
                    stopTask();
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (!isRunning) return;
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ERROR, errorMessage));
                stopTask();
            }
        });
    }
}
