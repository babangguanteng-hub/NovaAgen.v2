package com.novaagent.app.infrastructure.ocr;

import android.graphics.Bitmap;
import android.util.Log;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.novaagent.app.core.bus.AgentEvent;
import com.novaagent.app.core.bus.EventBus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Memproses gambar Bitmap menjadi teks menggunakan Google ML Kit On-Device.
 * Sangat dioptimalkan agar tidak menahan referensi Bitmap di memori.
 */
public class MlKitOcrEngine {
    private static final String TAG = "MlKitOcrEngine";
    private final TextRecognizer recognizer;
    private final ExecutorService ocrThread;

    public MlKitOcrEngine() {
        // Menggunakan recognizer versi Latin (ringan, tanpa perlu download model dari cloud)
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        ocrThread = Executors.newSingleThreadExecutor();
        Log.d(TAG, "MlKitOcrEngine siap.");
    }

    /**
     * Bitmap WAJIB dalam format RGB_565 agar ukuran RAM-nya setengah dari ARGB_8888.
     */
    public void processImage(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            Log.e(TAG, "Bitmap null atau sudah di-recycle. OCR dibatalkan.");
            return;
        }

        ocrThread.execute(() -> {
            try {
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                
                recognizer.process(image)
                        .addOnSuccessListener(visionText -> {
                            JSONArray ocrArray = new JSONArray();
                            for (com.google.mlkit.vision.text.Text.TextBlock block : visionText.getTextBlocks()) {
                                for (com.google.mlkit.vision.text.Text.Line line : block.getLines()) {
                                    try {
                                        JSONObject obj = new JSONObject();
                                        obj.put("text", line.getText());
                                        obj.put("source", "ocr");
                                        if (line.getBoundingBox() != null) {
                                            android.graphics.Rect rect = line.getBoundingBox();
                                            obj.put("cx", rect.centerX());
                                            obj.put("cy", rect.centerY());
                                            obj.put("bounds", rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom);
                                        }
                                        ocrArray.put(obj);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error parsing baris OCR JSON", e);
                                    }
                                }
                            }
                            Log.d(TAG, "OCR Selesai. Ditemukan " + ocrArray.length() + " elemen teks.");
                            // Kirim hasil OCR ke EventBus
                            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.OCR_COMPLETED, ocrArray));
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Gagal memproses OCR", e);
                            EventBus.getInstance().publish(new AgentEvent(AgentEvent.EventType.RECOVERY_TRIGGERED, "OCR_TIMEOUT"));
                        });

            } catch (Exception e) {
                Log.e(TAG, "Exception fatal di background thread OCR", e);
            } finally {
                // SANGAT PENTING: Selalu buang bitmap dari memori setelah jadi InputImage
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        });
    }
}
