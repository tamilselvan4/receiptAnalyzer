package com.expensetracker.service;

import com.expensetracker.config.InvoiceProcessingProperties;
import com.expensetracker.model.ExtractedExpensePayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Service
public class LocalAiExtractionService {

    private static final Logger log = LoggerFactory.getLogger(LocalAiExtractionService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final InvoiceProcessingProperties properties;

    public LocalAiExtractionService(RestTemplate restTemplate,
                                    ObjectMapper extractionObjectMapper,
                                    InvoiceProcessingProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = extractionObjectMapper;
        this.properties = properties;
    }

    public Optional<ExtractedExpensePayload> extractExpense(String tempFile) {
        String pythonUrl = properties.getLocalAi().getUrl();
        if (pythonUrl == null || pythonUrl.isBlank()) {
            log.warn("Local AI extraction URL is not configured; skipping local extraction");
            return Optional.empty();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(tempFile));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String response = restTemplate.postForObject(pythonUrl, requestEntity, String.class);
            if (response == null || response.isBlank()) {
                log.warn("Local AI service returned an empty response for {}", tempFile);
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(response, ExtractedExpensePayload.class));
        } catch (RestClientException exception) {
            log.warn("Local AI extraction request failed: {}", exception.getMessage());
            return Optional.empty();
        } catch (Exception exception) {
            log.warn("Failed to parse local AI extraction response: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public String localTextExtraction(String tempFile) {
        return extractExpense(tempFile)
                .map(payload -> {
                    try {
                        return objectMapper.writeValueAsString(payload);
                    } catch (Exception exception) {
                        log.warn("Failed to serialize local extraction payload: {}", exception.getMessage());
                        return "";
                    }
                })
                .orElse("");
    }
}
