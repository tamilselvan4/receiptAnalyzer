package com.expensetracker.service;

import com.expensetracker.config.InvoiceProcessingProperties;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiParserService {

    private static final Logger log = LoggerFactory.getLogger(AiParserService.class);

    private final RestTemplate restTemplate;
    private final InvoiceProcessingProperties properties;

    public AiParserService(RestTemplate restTemplate, InvoiceProcessingProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public String extractInvoiceData(String ocrText) {
        String prompt = "Extract and return the following fields in JSON format:\n"
                + "- Company Name\n"
                + "- Company Address\n"
                + "- Invoice Number\n"
                + "- Invoice Date\n"
                + "- Due Date\n"
                + "- Bill To\n"
                + "- Ship To\n"
                + "- Line Items (as array of {description, unit price, quantity, amount})\n"
                + "- Subtotal\n"
                + "- Tax\n"
                + "- Total\n"
                + "- Payment Terms\n\n"
                + "Here is the invoice text:\n" + ocrText;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> body = new HashMap<>();
        body.put("messages", List.of(message));
        body.put("temperature", 0.2);
        body.put("model", "local-model");

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(properties.getLmStudio().getUrl(), request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                return (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
            }
        } catch (Exception exception) {
            log.warn("LM Studio parsing call failed: {}", exception.getMessage());
        }
        return "Failed to parse invoice.";
    }

    public String extractInvoiceDataUsingGenAI(String ocrText) {
        String apiKey = properties.getExternalFallback().getGeminiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            return "{}";
        }

        String prompt = "From the following OCR text, extract the expense information and return it as a valid JSON object only. "
                + "Do not include any explanation or formatting. The JSON must have the following fields:\n"
                + "- name: Name of the vendor or service (e.g., company or provider name)\n"
                + "- amount: Total amount charged\n"
                + "- tax: Tax amount, if any (0 if not available)\n"
                + "- currency: The currency of the invoice(if null, return default INR)\n"
                + "- date: Date of the expense in YYYY-MM-DD format\n"
                + "- category: A general category (e.g., Travel, Meals, Office Supplies, Bike Repair, etc.)\n"
                + "- comment: Any additional relevant notes or invoice numbers\n\n"
                + "Return only the JSON object with no markdown or extra characters.\n\n"
                + "OCR Text:\n" + ocrText;

        Client client = Client.builder().apiKey(apiKey).build();
        GenerateContentResponse response = client.models.generateContent(
                properties.getExternalFallback().getModel(),
                prompt,
                null);
        return response.text();
    }

    public String extractInvoiceDataUsingLocalModel(String ocrText) {
        String prompt = "From the following OCR text, extract the expense information and return it as a valid JSON object only. "
                + "Do not include any explanation or formatting. The JSON must have the following fields:\n"
                + "- name: Name of the vendor or service (e.g., company or provider name)\n"
                + "- amount: Total amount charged\n"
                + "- tax: Tax amount, if any (0 if not available)\n"
                + "- currency: The currency of the invoice(if null, return default INR)\n"
                + "- date: Date of the expense in YYYY-MM-DD format\n"
                + "- category: A general category (e.g., Travel, Meals, Office Supplies, Bike Repair, etc.)\n"
                + "- comment: Any additional relevant notes or invoice numbers\n\n"
                + "Return only the JSON object with no markdown or extra characters.\n\n"
                + "OCR Text:\n" + ocrText;

        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> requestBody = Map.of(
                "model", "local-model",
                "messages", List.of(message),
                "temperature", 0.3
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(properties.getLmStudio().getUrl(), request, Map.class);
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> choices = (List<Map<String, Object>>) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> firstChoice = choices.get(0);
                    Map<String, Object> messageBody = (Map<String, Object>) firstChoice.get("message");
                    String content = (String) messageBody.get("content");
                    return content.trim()
                            .replaceAll("(?s)```(json)?", "")
                            .replaceAll("^[^\\{]*", "")
                            .replaceAll("[^\\}]*$", "");
                }
            }
        } catch (Exception exception) {
            log.warn("Error calling LM Studio: {}", exception.getMessage());
        }

        return "{}";
    }
}
