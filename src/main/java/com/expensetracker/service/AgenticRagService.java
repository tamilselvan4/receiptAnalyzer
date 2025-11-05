package com.expensetracker.service;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

@Service
public class AgenticRagService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final OcrService ocrService = new OcrService();
    private static final String OCR_API = "http://localhost:5050/extract-image";

    private static final String GEMINI_API_KEY = "AIzaSyAZs-uYlply2gk4kDCHVq4CARaKyooBdlg";

    public JSONObject analyzeReceipt(String fileName, byte[] fileBytes) throws IOException {
        AgentState state = new AgentState();
        state.addThought("Starting Agentic RAG-based receipt analysis...");

        JSONObject ocrJson = callOCR(fileName, fileBytes);
        String ocrText = ocrJson.optString("raw_text", "");
        state.addThought("Extracted OCR text, length: " + ocrText.length());

        String genAIJson = extractInvoiceDataUsingGenAI(ocrText);
        JSONObject aiExtracted = safeJsonParse(genAIJson);
        state.addThought("AI model extracted fields successfully.");

        JSONObject validated = performAnomalyCheck(aiExtracted);
        state.addThought("Fraud detection & reasoning completed.");

        JSONObject finalOutput = new JSONObject();
        finalOutput.put("ocr_text", ocrText);
        finalOutput.put("structured_data", aiExtracted);
        finalOutput.put("validation", validated);
        finalOutput.put("agent_trace", state.getThoughts());
        finalOutput.put("status", "Agentic RAG Processing Completed");

        return finalOutput;
    }

    private JSONObject callOCR(String fileName, byte[] fileBytes) {

        String uuid = "1_" + UUID.randomUUID();
        int dotIndex = fileName.lastIndexOf('.');
        String extension = (dotIndex > 0) ? fileName.substring(fileName.lastIndexOf('.') + 1) : "";

        File tempFile = null;
        try {
            tempFile = File.createTempFile(uuid, extension);

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(fileBytes);
        }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String extractedText = ocrService.extractText(tempFile.getAbsolutePath(), uuid, extension, "/Users/tamilselvans/M.E/tamil/test-samples/");

        return new JSONObject(extractedText);
    }

    private String extractInvoiceDataUsingGenAI(String ocrText) {
        String prompt = "From the following OCR text, extract the expense information and return it as a valid JSON object only. " +
                "Do not include any explanation or formatting. The JSON must have the following fields:\n" +
                "- name: Name of the vendor or service (e.g., company or provider name)\n" +
                "- amount: Total amount charged\n" +
                "- tax: Tax amount, if any (0 if not available)\n" +
                "- currency: The currency of the invoice(if null, return default INR)\n" +
                "- date: Date of the expense in YYYY-MM-DD format\n" +
                "- category: A general category (e.g., Travel, Meals, Office Supplies, Bike Repair, etc.)\n" +
                "- comment: Any additional relevant notes or invoice numbers\n\n" +
                "Return only the JSON object with no markdown or extra characters.\n\n" +
                "OCR Text:\n" + ocrText;

        Client client = Client.builder().apiKey(GEMINI_API_KEY).build();
        GenerateContentResponse response =
                client.models.generateContent(
                        "gemini-2.5-flash",
                        prompt,
                        null);
        return response.text();
    }

    private JSONObject performAnomalyCheck(JSONObject data) {
        JSONObject result = new JSONObject();
        double amount = data.optDouble("amount", 0);
        double tax = data.optDouble("tax", 0);
        String vendor = data.optString("name", "unknown");

        if (amount < 0 || tax < 0) {
            result.put("anomaly", true);
            result.put("reason", "Negative values detected");
        } else if (amount > 100000) {
            result.put("anomaly", true);
            result.put("reason", "Unusually high expense detected");
        } else if (vendor.equalsIgnoreCase("unknown")) {
            result.put("anomaly", true);
            result.put("reason", "Vendor name missing or unreadable");
        } else {
            result.put("anomaly", false);
            result.put("reason", "No fraud detected");
        }

        return result;
    }

    private JSONObject safeJsonParse(String jsonText) {
        try {
            return new JSONObject(jsonText);
        } catch (Exception e) {
            JSONObject fallback = new JSONObject();
            fallback.put("error", "Invalid JSON returned by GenAI");
            fallback.put("raw_output", jsonText);
            return fallback;
        }
    }

    static class AgentState {
        private final List<String> thoughts = new ArrayList<>();
        void addThought(String t) { thoughts.add("🤖 " + t); }
        List<String> getThoughts() { return thoughts; }
    }
}
