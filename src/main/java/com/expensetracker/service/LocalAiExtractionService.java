package com.expensetracker.service;

import com.expensetracker.model.ExtractedExpensePayload;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;

import java.util.Optional;

@Service
public class LocalAiExtractionService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper;

    @Value("${local.ai.url:http://localhost:5050/extract-image}")
    private String pythonUrl;

    public LocalAiExtractionService() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }

    public Optional<ExtractedExpensePayload> extractExpense(String tempFile) {
        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(tempFile));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(pythonUrl, requestEntity, String.class);
            if (response == null || response.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(response, ExtractedExpensePayload.class));
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }

    public String localTextExtraction(String tempFile) {
        return extractExpense(tempFile)
                .map(payload -> {
                    try {
                        return objectMapper.writeValueAsString(payload);
                    } catch (Exception e) {
                        return "";
                    }
                })
                .orElse("");
    }
}
