package com.expensetracker.service;

import com.expensetracker.config.InvoiceProcessingProperties;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AgenticRagService {

    private static final Logger log = LoggerFactory.getLogger(AgenticRagService.class);

    private final OcrService ocrService;
    private final InvoiceProcessingProperties properties;

    public AgenticRagService(OcrService ocrService, InvoiceProcessingProperties properties) {
        this.ocrService = ocrService;
        this.properties = properties;
    }

    public JSONObject analyzeReceipt(String fileName, byte[] fileBytes) throws IOException {
        AgentState state = new AgentState();
        state.addThought("Starting external fallback analysis");

        String ocrText = callOCR(fileName, fileBytes);
        if (ocrText.isBlank()) {
            throw new IOException("OCR returned no text for external fallback");
        }
        state.addThought("Extracted OCR text, length: " + ocrText.length());

        String genAIJson = extractInvoiceDataUsingGenAI(ocrText);
        JSONObject aiExtracted = safeJsonParse(genAIJson);
        state.addThought("External model returned structured output");

        JSONObject finalOutput = new JSONObject();
        finalOutput.put("ocr_text", ocrText);
        finalOutput.put("structured_data", aiExtracted);
        finalOutput.put("agent_trace", state.getThoughts());
        finalOutput.put("status", "external-fallback-completed");
        return finalOutput;
    }

    private String callOCR(String fileName, byte[] fileBytes) {
        String documentId = "receipt_" + UUID.randomUUID();
        String extension = resolveExtension(fileName);
        File tempFile = null;
        try {
            tempFile = File.createTempFile(documentId, extension);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(fileBytes);
            }
            return ocrService.extractText(tempFile.getAbsolutePath(), documentId, extension);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to prepare temporary file for OCR", exception);
        } finally {
            if (tempFile != null && !tempFile.delete()) {
                log.debug("Temporary OCR file was not deleted: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private String extractInvoiceDataUsingGenAI(String ocrText) {
        String apiKey = properties.getExternalFallback().getGeminiApiKey();
        if (!properties.getExternalFallback().isEnabled()) {
            throw new IllegalStateException("External fallback is disabled");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }

        String prompt = "From the following OCR text, extract the expense information and return it as a valid JSON object only. "
                + "Do not include any explanation or formatting. The JSON must have the following fields:\n"
                + "- name: seller or vendor name\n"
                + "- amount: invoice total amount\n"
                + "- tax: tax amount, if any (0 if not available)\n"
                + "- currency: 3-letter currency code (if missing, return INR)\n"
                + "- date: invoice date in YYYY-MM-DD format\n"
                + "- category: general category such as Travel, Meals, Office Supplies, Electronics, Services, General\n"
                + "- comment: additional notes\n"
                + "- invoice_number: invoice number if present\n"
                + "- due_date: due date in YYYY-MM-DD format or null\n"
                + "- seller_address: seller address or null\n"
                + "- client_name: client name or null\n"
                + "- client_address: client address or null\n"
                + "- discount: discount amount or null\n"
                + "- payment_method: payment method or null\n"
                + "- bank_name: bank name or null\n"
                + "- account_number: account number or null\n"
                + "- line_items: array of objects with description, quantity, total_price\n\n"
                + "Return only the JSON object with no markdown or extra characters.\n\n"
                + "OCR Text:\n" + ocrText;

        Client client = Client.builder().apiKey(apiKey).build();
        GenerateContentResponse response = client.models.generateContent(
                properties.getExternalFallback().getModel(),
                prompt,
                null);
        return response.text();
    }

    private JSONObject safeJsonParse(String jsonText) {
        try {
            String sanitized = jsonText == null ? "" : jsonText
                    .replace("```json", "")
                    .replace("```", "")
                    .trim();
            return new JSONObject(sanitized);
        } catch (Exception exception) {
            JSONObject fallback = new JSONObject();
            fallback.put("error", "Invalid JSON returned by external model");
            fallback.put("raw_output", jsonText);
            return fallback;
        }
    }

    private String resolveExtension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return ".png";
        }
        return fileName.substring(dotIndex);
    }

    static class AgentState {
        private final List<String> thoughts = new ArrayList<>();

        void addThought(String thought) {
            thoughts.add("[agent] " + thought);
        }

        List<String> getThoughts() {
            return thoughts;
        }
    }
}
