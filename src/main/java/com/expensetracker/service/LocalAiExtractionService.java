package com.expensetracker.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class LocalAiExtractionService {

    public String localTextExtraction(String tempFile) {
        try {

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(tempFile));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String pythonUrl = "http://localhost:5050/extract-image";
            RestTemplate restTemplate = new RestTemplate();

            return restTemplate.postForObject(pythonUrl, requestEntity, String.class);


        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
