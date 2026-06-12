package com.novaagent.app.core.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.data.repository.GroqApiClient;
import com.novaagent.app.data.repository.PromptRegistry;
import com.novaagent.app.infrastructure.injector.SystemActionInjector;
import com.novaagent.app.services.accessibility.NovaAccessibilityService;
import com.novaagent.app.core.di.ServiceLocator;

public class AutonomousLoopEngine implements EventBus.EventListener {
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
        EventBus.getInstance().subscribe(AgentEvent.EventType.SCREEN_UPDATED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ACTION_EXECUTED, this);
    }

    public void startTask(String task) {
        this.currentTask = task;
        this.isRunning = true;
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "MEMINDAI..."));
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
            stopTask();
        }
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!isRunning) return;

        if (event.type == AgentEvent.EventType.SCREEN_UPDATED) {
            String screenContext = String.valueOf(event.payload).replaceAll("[^a-zA-Z0-9 \\n()\\[\\]\"',.:/_-]", "");
            if (screenContext.length() > 2000) screenContext = screenContext.substring(0, 2000);
            processWithAI(screenContext);
        } 
        else if (event.type == AgentEvent.EventType.ACTION_EXECUTED) {
            // [PENTING]: Aplikasi seperti YouTube butuh waktu loading setelah diklik
            // Jeda 4 detik sebelum AI memindai layar lagi untuk mengambil langkah berikutnya
            mainHandler.postDelayed(this::requestScreenData, 4000);
        }
    }

    private void processWithAI(String screenContext) {
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "BERPIKIR..."));
        String userPrompt = PromptRegistry.buildUserPrompt(currentTask, screenContext);

        groqClient.sendPrompt(PromptRegistry.SYSTEM_PROMPT, userPrompt, new GroqApiClient.GroqCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                if (!isRunning) return;
                try {
                    ActionCommandDto cmd = new ActionCommandDto(jsonResponse);
                    if ("done".equalsIgnoreCase(cmd.action)) {
                        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "TUGAS SELESAI"));
                        if (cmd.speech != null && !cmd.speech.isEmpty()) EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.VOICE_RECEIVED, cmd.speech));
                        stopTask();
                    } else {
                        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "EKSEKUSI..."));
                        actionInjector.executeAction(cmd);
                    }
                } catch (Exception e) {
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
