package com.expensetracker.service;

import com.expensetracker.config.InvoiceProcessingProperties;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LocalAiExtractionServiceTest {

    @Test
    void usesConfiguredEndpointAndParsesPayload() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        InvoiceProcessingProperties properties = new InvoiceProcessingProperties();
        properties.getLocalAi().setUrl("http://localhost:5050/extract-image");

        LocalAiExtractionService service = new LocalAiExtractionService(restTemplate, objectMapper(), properties);
        Path tempFile = Files.createTempFile("local-ai-test", ".png");
        try {
            server.expect(requestTo("http://localhost:5050/extract-image"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess("""
                            {"name":"Configured Vendor","amount":42.5,"tax":2.5,"currency":"INR"}
                            """, MediaType.APPLICATION_JSON));

            var result = service.extractExpense(tempFile.toString());
            assertTrue(result.isPresent());
            assertEquals("Configured Vendor", result.get().getName());
            assertEquals("INR", result.get().getCurrency());
            server.verify();
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        return mapper;
    }
}
