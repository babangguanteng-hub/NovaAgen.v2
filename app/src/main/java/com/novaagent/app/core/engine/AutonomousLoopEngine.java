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
    private final GroqApiClient groqClient;
    private final SystemActionInjector actionInjector;
    private final SafetyPolicyEngine safetyEngine;
    private final Handler loopHandler;
    private boolean isRunning = false;

    public AutonomousLoopEngine(Context context) {
        this.groqClient = new GroqApiClient(context);
        this.actionInjector = new SystemActionInjector(context);
        this.safetyEngine = new SafetyPolicyEngine();
        this.loopHandler = new Handler(Looper.getMainLooper());

        EventBus.getInstance().subscribe(AgentEvent.EventType.SCREEN_UPDATED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ACTION_VALIDATED, this);
    }

    public void startTask(String task) {
        this.isRunning = true;
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "MENGAMATI..."));
        requestScreenData();
    }

    public void stopTask() { this.isRunning = false; }

    private void requestScreenData() {
        try {
            ServiceLocator.getInstance().resolve(NovaAccessibilityService.class).requestScreenScan();
        } catch (Exception e) { stopTask(); }
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!isRunning) return;
        if (event.type == AgentEvent.EventType.SCREEN_UPDATED) {
            groqClient.sendPrompt(PromptRegistry.SYSTEM_PROMPT, 
                PromptRegistry.buildUserPrompt("Misi: " + event.payload.toString(), "Status: Observasi"), 
                new GroqApiClient.GroqCallback() {
                    @Override public void onSuccess(String json) {
                        try {
                            ActionCommandDto cmd = new ActionCommandDto(json);
                            // LOGIKA KESADARAN: Jika AI bilang 'done', artinya dia diam/mengobrol
                            if (!"done".equalsIgnoreCase(cmd.action)) {
                                safetyEngine.evaluateAction(cmd, null);
                            } else {
                                if (cmd.speech != null) EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.VOICE_RECEIVED, cmd.speech));
                                stopTask();
                            }
                        } catch (Exception e) { stopTask(); }
                    }
                    @Override public void onError(String err) { stopTask(); }
                });
        } else if (event.type == AgentEvent.EventType.ACTION_VALIDATED) {
            actionInjector.executeAction((ActionCommandDto) event.payload);
        }
    }
}
