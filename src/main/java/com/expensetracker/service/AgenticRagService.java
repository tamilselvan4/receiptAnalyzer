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

    private static final String GEMINI_API_KEY = "AIzaSyAuBo_zAhWGI6_xuN1ttesz5bykdCzTCVk";

    public JSONObject analyzeReceipt(String fileName, byte[] fileBytes) throws IOException {
        AgentState state = new AgentState();
        state.addThought("Starting Agentic RAG-based receipt analysis...");

//        JSONObject ocrJson = callOCR(fileName, fileBytes);
        String ocrText = callOCR(fileName, fileBytes);
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

    private String callOCR(String fileName, byte[] fileBytes) {

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

        return ocrService.extractText(tempFile.getAbsolutePath(), uuid, extension, "/Users/tamilselvans/M.E/tamil/test-samples/");

//        return new JSONObject(extractedText);
    }

    private String extractInvoiceDataUsingGenAI(String ocrText) {
        String prompt = "From the following OCR text, extract the expense information and return it as a valid JSON object only. " +
                "Do not include any explanation or formatting. The JSON must have the following fields:\n" +
                "- name: seller or vendor name\n" +
                "- amount: invoice total amount\n" +
                "- tax: tax amount, if any (0 if not available)\n" +
                "- currency: 3-letter currency code (if missing, return INR)\n" +
                "- date: invoice date in YYYY-MM-DD format\n" +
                "- category: general category such as Travel, Meals, Office Supplies, Electronics, Services, General\n" +
                "- comment: additional notes\n" +
                "- invoice_number: invoice number if present\n" +
                "- due_date: due date in YYYY-MM-DD format or null\n" +
                "- seller_address: seller address or null\n" +
                "- client_name: client name or null\n" +
                "- client_address: client address or null\n" +
                "- discount: discount amount or null\n" +
                "- payment_method: payment method or null\n" +
                "- bank_name: bank name or null\n" +
                "- account_number: account number or null\n" +
                "- line_items: array of objects with description, quantity, total_price\n\n" +
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
