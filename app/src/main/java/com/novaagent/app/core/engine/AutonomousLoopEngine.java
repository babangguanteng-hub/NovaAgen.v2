package com.novaagent.app.core.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;
import com.novaagent.app.data.model.ActionCommandDto;
import com.novaagent.app.data.model.ActionVerificationResult;
import com.novaagent.app.data.model.UnifiedScreenContext;
import com.novaagent.app.data.repository.GroqApiClient;
import com.novaagent.app.data.repository.PromptRegistry;
import com.novaagent.app.infrastructure.injector.SystemActionInjector;
import com.novaagent.app.services.accessibility.NovaAccessibilityService;
import com.novaagent.app.core.di.ServiceLocator;

/**
 * Otak Pusat Agen Otonom. 
 * Mengendalikan jalur pipa: Mata (OS) -> Otak (Groq) -> Firewall (Safety) -> Tangan (Injector) -> Verifikasi.
 */
public class AutonomousLoopEngine implements EventBus.EventListener {
    private static final String TAG = "AutonomousLoopEngine";
    
    private final GroqApiClient groqClient;
    private final SystemActionInjector actionInjector;
    private final SafetyPolicyEngine safetyEngine;
    private final ActionVerificationScore verificationEngine;
    
    private String currentTask = "";
    private boolean isRunning = false;
    private final Handler loopHandler;

    // State Variables
    private UnifiedScreenContext lastScreenContext;
    private String lastActionExecuted;

    public AutonomousLoopEngine(Context context) {
        this.groqClient = new GroqApiClient(context);
        this.actionInjector = new SystemActionInjector(context);
        this.safetyEngine = new SafetyPolicyEngine();
        this.verificationEngine = new ActionVerificationScore();
        this.loopHandler = new Handler(Looper.getMainLooper());

        // Mendaftarkan telinga ke EventBus
        EventBus.getInstance().subscribe(AgentEvent.EventType.SCREEN_UPDATED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ACTION_VALIDATED, this);
        EventBus.getInstance().subscribe(AgentEvent.EventType.ACTION_EXECUTED, this);
    }

    public void startTask(String task) {
        this.currentTask = task;
        this.isRunning = true;
        this.lastScreenContext = null;
        this.lastActionExecuted = null;
        
        Log.i(TAG, "Memulai Misi: " + task);
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "MEMINDAI..."));
        requestScreenData();
    }

    public void stopTask() {
        this.isRunning = false;
        this.currentTask = "";
        Log.i(TAG, "Misi Dihentikan.");
    }

    private void requestScreenData() {
        if (!isRunning) return;
        try {
            NovaAccessibilityService service = ServiceLocator.getInstance().resolve(NovaAccessibilityService.class);
            service.requestScreenScan();
        } catch (Exception e) {
            Log.e(TAG, "Gagal meminta akses layar. Service belum siap?", e);
            stopTask();
        }
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!isRunning) return;

        try {
            switch (event.type) {
                case SCREEN_UPDATED:
                    UnifiedScreenContext newContext = (UnifiedScreenContext) event.payload;
                    
                    // Jika ada aksi sebelumnya, verifikasi dulu keberhasilannya
                    if (lastActionExecuted != null && lastScreenContext != null) {
                        ActionVerificationResult result = verificationEngine.verify(lastActionExecuted, lastScreenContext, newContext, true);
                        if (!result.isVerified) {
                            Log.w(TAG, "Verifikasi Gagal: " + result.reason);
                            // Di masa depan: Tambahkan Retry Logic untuk AI di sini
                        }
                    }

                    lastScreenContext = newContext; // Simpan untuk evaluasi berikutnya
                    
                    // Meratakan UI menjadi teks ringan untuk dikirim ke Groq (Omit JSON berat)
                    String flatUiText = newContext.toString();
                    if (flatUiText.length() > 2500) flatUiText = flatUiText.substring(0, 2500); // Batas Token LLM
                    
                    processWithAI(flatUiText);
                    break;

                case ACTION_VALIDATED:
                    ActionCommandDto safeCmd = (ActionCommandDto) event.payload;
                    lastActionExecuted = safeCmd.action;
                    EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "EKSEKUSI..."));
                    actionInjector.executeAction(safeCmd);
                    break;

                case ACTION_EXECUTED:
                    // Memberikan waktu UI Android untuk merender transisi halaman (4 detik)
                    // Tidak menggunakan Thread.sleep() agar EventBus tidak macet!
                    loopHandler.postDelayed(this::requestScreenData, 4000);
                    break;
                    
                default:
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error dalam Loop Engine", e);
            stopTask();
        }
    }

    private void processWithAI(String uiContextString) {
        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "BERPIKIR..."));
        String userPrompt = PromptRegistry.buildUserPrompt(currentTask, uiContextString);

        groqClient.sendPrompt(PromptRegistry.SYSTEM_PROMPT, userPrompt, new GroqApiClient.GroqCallback() {
            @Override
            public void onSuccess(String jsonResponse) {
                if (!isRunning) return;
                try {
                    ActionCommandDto cmd = new ActionCommandDto(jsonResponse);
                    
                    if ("done".equalsIgnoreCase(cmd.action)) {
                        EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.STATE_CHANGED, "TUGAS SELESAI"));
                        if (cmd.speech != null && !cmd.speech.isEmpty()) {
                            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.VOICE_RECEIVED, cmd.speech));
                        }
                        stopTask();
                    } else {
                        // Kunci Arsitektur V2: Lempar ke Firewall sebelum dieksekusi!
                        safetyEngine.evaluateAction(cmd, lastScreenContext);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Gagal memparsing respon JSON dari LLM", e);
                }
            }

            @Override
            public void onError(String errorMessage) {
                if (!isRunning) return;
                EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.ERROR, "Koneksi API Gagal"));
                stopTask();
            }
        });
    }
}
