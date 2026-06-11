package com.novaagent.app.core.engine;

import android.content.Context;
import android.util.Log;

import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.core.di.ServiceLocator;
import com.novaagent.app.core.state.AgentState;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.data.model.UnifiedScreenContext;
import com.novaagent.app.data.repository.GroqApiClient;
import com.novaagent.app.data.repository.PromptRegistry;
import com.novaagent.app.infrastructure.injector.SystemActionInjector;
import com.novaagent.app.services.accessibility.NovaAccessibilityService;

import org.json.JSONArray;

public class AutonomousLoopEngine implements EventBus.EventListener {
    private static final String TAG = "AutonomousLoopEngine";
    
    private AgentState currentState = AgentState.IDLE;
    private String currentTask = ""; 
    
    private GroqApiClient groqClient;
    private SystemActionInjector actionInjector;
    private SafetyPolicyEngine safetyEngine;
    private ActionVerificationScore verificationScore;
    
    private UnifiedScreenContext currentScreenContext;
    private ActionCommandDto pendingCommand;

    public AutonomousLoopEngine() {
        EventBus.getInstance().subscribe(AgentEvent.EventType.STATE_CHANGED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.SCREEN_UPDATED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ACTION_VALIDATED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ACTION_EXECUTED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ACTION_FAILED, this);
    }

    public void prepareEngine(Context serviceContext) {
        try {
            groqClient = new GroqApiClient(serviceContext);
            actionInjector = new SystemActionInjector();
            safetyEngine = new SafetyPolicyEngine();
            verificationScore = new ActionVerificationScore();
            Log.d(TAG, "Dependencies LoopEngine berhasil disiapkan.");
        } catch (Exception e) {
            Log.e(TAG, "Gagal menyiapkan dependencies", e);
        }
    }

    public void startTask(String task) {
        this.currentTask = task;
        Log.i(TAG, "==== MULAI TUGAS BARU: " + task + " ====");
        changeState(AgentState.OBSERVING);
    }

    public void stopTask() {
        this.currentTask = "";
        changeState(AgentState.IDLE);
    }

    private void changeState(AgentState newState) {
        this.currentState = newState;
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, newState.name()));
    }

    @Override
    public void onEvent(AgentEvent event) {
        switch (event.type) {
            case STATE_CHANGED:
                handleStateChange((String) event.payload);
                break;
            case SCREEN_UPDATED:
                if (currentState == AgentState.OBSERVING) {
                    processScreenUpdate((JSONArray) event.payload);
                }
                break;
            case ACTION_VALIDATED:
                if (currentState == AgentState.SAFETY_CHECK) {
                    executeAction((ActionCommandDto) event.payload);
                }
                break;
            case ACTION_EXECUTED:
                if (currentState == AgentState.EXECUTING) {
                    changeState(AgentState.VERIFYING_SCORE);
                    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        changeState(AgentState.OBSERVING);
                    }, 1500);
                }
                break;
            case ACTION_FAILED:
                if (currentState == AgentState.EXECUTING) {
                    changeState(AgentState.ERROR);
                }
                break;
            default:
                break;
        }
    }

    private void handleStateChange(String stateStr) {
        if (stateStr.equals(AgentState.OBSERVING.name())) {
            try {
                NovaAccessibilityService service = ServiceLocator.getInstance().resolve(NovaAccessibilityService.class);
                service.requestScreenScan();
            } catch (Exception e) {
                Log.e(TAG, "Accessibility belum siap.");
            }
        }
    }

    private void processScreenUpdate(JSONArray screenNodes) {
        currentScreenContext = new UnifiedScreenContext("unknown_pkg", screenNodes, new JSONArray());
        changeState(AgentState.THINKING);
        
        String prompt = PromptRegistry.buildUserPrompt(currentTask, screenNodes.toString());
        changeState(AgentState.LLM_NETWORK_CALL);
        
        groqClient.sendPrompt(PromptRegistry.SYSTEM_PROMPT, prompt, new GroqApiClient.GroqCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                try {
                    pendingCommand = new ActionCommandDto(jsonResponse);
                    changeState(AgentState.SAFETY_CHECK);
                    safetyEngine.evaluateAction(pendingCommand, currentScreenContext);
                } catch (Exception e) {
                    changeState(AgentState.ERROR);
                }
            }

            @Override
            public void onError(String errorMessage) {
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.RECOVERY_TRIGGERED, "API_ERROR_" + errorMessage));
            }
        });
    }

    private void executeAction(ActionCommandDto command) {
        changeState(AgentState.EXECUTING);
        actionInjector.executeAction(command);
    }
}
