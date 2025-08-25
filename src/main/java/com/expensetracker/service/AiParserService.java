package com.expensetracker.service;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AiParserService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${lm.studio.url}")
    private String lmStudioUrl;

    public String extractInvoiceData(String ocrText) {
        String prompt = "Extract and return the following fields in JSON format:\n" +
                "- Company Name\n" +
                "- Company Address\n" +
                "- Invoice Number\n" +
                "- Invoice Date\n" +
                "- Due Date\n" +
                "- Bill To\n" +
                "- Ship To\n" +
                "- Line Items (as array of {description, unit price, quantity, amount})\n" +
                "- Subtotal\n" +
                "- Tax\n" +
                "- Total\n" +
                "- Payment Terms\n\n" +
                "Here is the invoice text:\n" + ocrText;

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Prepare chat message payload
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", prompt
        );

        Map<String, Object> body = new HashMap<>();
        body.put("messages", List.of(message));
        body.put("temperature", 0.2);
        body.put("model", "local-model");  // Optional, LM Studio uses default

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        String lmStudioUrl = "http://localhost:1234/v1/chat/completions";

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(lmStudioUrl, request, Map.class);
            Map<String, Object> responseBody = response.getBody();

            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                return (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "Failed to parse invoice.";
    }

    public String extractInvoiceDataUsingGenAI(String ocrText) {

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

        String apiKey = "AIzaSyAZs-uYlply2gk4kDCHVq4CARaKyooBdlg";
        Client client = Client.builder().apiKey(apiKey).build();
        GenerateContentResponse response =
                client.models.generateContent(
                        "gemini-2.5-flash",
                        prompt,
                        null);
        return response.text();
    }

    public String extractInvoiceDataUsingLocalMethod(String ocrText) {

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

        // Build the request body
        Map<String, Object> message = Map.of(
                "role", "user",
                "content", prompt
        );

        Map<String, Object> requestBody = Map.of(
                "model", "local-model", // or actual model name (you can check LM Studio's console)
                "messages", List.of(message),
                "temperature", 0.3
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(lmStudioUrl, request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> body = response.getBody();

                List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> mes = (Map<String, Object>) firstChoice.get("message");
                    String content = (String) mes.get("content");
                    return content.trim();
                }
            }

        } catch (Exception e) {
            System.err.println("Error calling LM Studio: " + e.getMessage());
        }

        return "{}"; // fallback empty JSON
    }

    public static void main(String[] args) {
        AiParserService service = new AiParserService();
        OcrService ocrService = new OcrService();
        String ocrText = ocrService.extractText("/Users/tamilselvans/Downloads/invoice.png", UUID.randomUUID().toString(), "png", "/Users/tamilselvans/M.E/project/uploads/");
//        String result = service.extractInvoiceData(ocrText);
        String result = service.extractInvoiceDataUsingGenAI(ocrText);
        System.out.println("Extracted Invoice Data: " + result);
    }
}

