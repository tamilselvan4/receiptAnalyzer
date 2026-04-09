package com.expensetracker.service;

import com.expensetracker.config.ExpenseCategoryCatalog;
import com.expensetracker.config.InvoiceProcessingProperties;
import com.expensetracker.repository.ExpenseRepo;
import com.expensetracker.model.ExpenseExtractionResult;
import com.expensetracker.model.ExtractedExpensePayload;
import com.expensetracker.model.ExtractedLineItemPayload;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExpenseExtractionCoordinatorTest {

    private final ObjectMapper objectMapper = objectMapper();
    private final InvoiceValidationService validationService = new InvoiceValidationService(new InvoiceProcessingProperties());
    private final ExpenseClassificationService classificationService = new ExpenseClassificationService(new ExpenseCategoryCatalog());
    private final ExpenseRepo expenseRepo = mock(ExpenseRepo.class);
    private final RiskAssessmentService riskAssessmentService = new RiskAssessmentService(new HistoricalRiskFeatureService(expenseRepo));

    @Test
    void returnsLocalExtractionWhenStructuredDataExists() throws Exception {
        LocalAiExtractionService localService = mock(LocalAiExtractionService.class);
        AgenticRagService externalService = mock(AgenticRagService.class);

        ExtractedExpensePayload payload = new ExtractedExpensePayload();
        payload.setName("Acme Supplies");
        payload.setAmount(BigDecimal.valueOf(125.50));
        payload.setTax(BigDecimal.valueOf(5.50));
        payload.setDiscount(BigDecimal.valueOf(3.25));
        payload.setCurrency("usd");
        payload.setDate(LocalDate.of(2024, 5, 1));
        payload.setDueDate(LocalDate.of(2024, 5, 15));
        payload.setCategory("Office Supplies");
        payload.setComment("Desk supplies");
        payload.setInvoiceNumber("INV-42");
        payload.setSellerAddress("12 Test Street");
        payload.setClientName("Globex");
        payload.setClientAddress("45 Client Avenue");
        payload.setPaymentMethod("Card");
        payload.setBankName("Axis Bank");
        payload.setAccountNumber("AC-123");
        payload.setRawOcrText("invoice number INV-42");
        payload.setLineItems(java.util.List.of(lineItem("Paper", "2", "60.00"), lineItem("Pens", "5", "63.25")));

        when(localService.extractExpense(any())).thenReturn(Optional.of(payload));

        ExpenseExtractionCoordinator coordinator = coordinator(localService, externalService);
        ExpenseExtractionResult result = coordinator.extract("invoice.png", "image".getBytes(), true);

        assertEquals("local-model", result.getSource());
        assertEquals("Acme Supplies", result.getExpense().getName());
        assertEquals("INV-42", result.getExpense().getInvoiceNumber());
        assertEquals(BigDecimal.valueOf(125.50).setScale(2), result.getExpense().getAmount().setScale(2));
        assertEquals("USD", result.getExpense().getCurrency());
        assertEquals(LocalDate.of(2024, 5, 15), result.getExpense().getDueDate());
        assertEquals("Globex", result.getExpense().getClientName());
        assertEquals("Card", result.getExpense().getPaymentMethod());
        assertEquals(2, result.getExpense().getLineItems().size());
        assertTrue(result.getExpense().getComment().contains("INV-42"));
        assertEquals("Office Supplies", result.getClassificationResult().getPredictedCategory());
        assertEquals("LOW", result.getRiskAssessment().getRiskBand());
        assertFalse(result.getValidation().isAnomaly());
    }

    @Test
    void fallsBackToExternalWhenLocalExtractionIsEmpty() throws Exception {
        LocalAiExtractionService localService = mock(LocalAiExtractionService.class);
        AgenticRagService externalService = mock(AgenticRagService.class);

        when(localService.extractExpense(any())).thenReturn(Optional.empty());
        JSONObject externalPayload = new JSONObject()
                .put("ocr_text", "fallback ocr")
                .put("structured_data", new JSONObject()
                        .put("name", "Fallback Vendor")
                        .put("invoice_number", "INV-900")
                        .put("amount", 99.99)
                        .put("tax", 0)
                        .put("discount", 5.50)
                        .put("currency", "INR")
                        .put("date", "2024-06-01")
                        .put("due_date", "2024-06-15")
                        .put("category", "Travel")
                        .put("comment", "API result")
                        .put("client_name", "Fallback Client")
                        .put("payment_method", "Wire")
                        .put("line_items", new JSONArray()
                                .put(new JSONObject()
                                        .put("description", "Taxi fare")
                                        .put("quantity", 1)
                                        .put("total_price", 99.99))));
        when(externalService.analyzeReceipt(any(), any())).thenReturn(externalPayload);

        ExpenseExtractionCoordinator coordinator = coordinator(localService, externalService);
        ExpenseExtractionResult result = coordinator.extract("invoice.png", "image".getBytes(), true);

        assertEquals("external-api", result.getSource());
        assertEquals("Fallback Vendor", result.getExpense().getName());
        assertEquals("INV-900", result.getExpense().getInvoiceNumber());
        assertEquals("Fallback Client", result.getExpense().getClientName());
        assertEquals("Wire", result.getExpense().getPaymentMethod());
        assertEquals(1, result.getExpense().getLineItems().size());
        assertEquals("fallback ocr", result.getRawOcrText());
        assertEquals("Travel", result.getClassificationResult().getPredictedCategory());
        verify(externalService).analyzeReceipt(any(), any());
    }

    @Test
    void prefillsExpandedCategoryOnExpenseFromRulesBasedSuggestion() throws Exception {
        LocalAiExtractionService localService = mock(LocalAiExtractionService.class);
        AgenticRagService externalService = mock(AgenticRagService.class);

        ExtractedExpensePayload payload = new ExtractedExpensePayload();
        payload.setName("Airtel Broadband");
        payload.setAmount(BigDecimal.valueOf(1299));
        payload.setRawOcrText("Broadband internet monthly plan");

        when(localService.extractExpense(any())).thenReturn(Optional.of(payload));

        ExpenseExtractionCoordinator coordinator = coordinator(localService, externalService);
        ExpenseExtractionResult result = coordinator.extract("invoice.png", "image".getBytes(), false);

        assertEquals("Internet", result.getClassificationResult().getPredictedCategory());
        assertEquals("Internet", result.getExpense().getCategory());
    }

    @Test
    void returnsPartialLocalResultWhenFallbackFails() throws Exception {
        LocalAiExtractionService localService = mock(LocalAiExtractionService.class);
        AgenticRagService externalService = mock(AgenticRagService.class);

        ExtractedExpensePayload payload = new ExtractedExpensePayload();
        when(localService.extractExpense(any())).thenReturn(Optional.of(payload));
        when(externalService.analyzeReceipt(any(), any())).thenThrow(new IllegalStateException("missing key"));

        ExpenseExtractionCoordinator coordinator = coordinator(localService, externalService);
        ExpenseExtractionResult result = coordinator.extract("invoice.png", "image".getBytes(), true);

        assertEquals("local-model-partial", result.getSource());
        assertTrue(result.getValidation().isAnomaly());
        assertTrue(result.getValidation().getReason().contains("Vendor name is missing"));
    }

    private ExpenseExtractionCoordinator coordinator(LocalAiExtractionService localService,
                                                     AgenticRagService externalService) {
        when(expenseRepo.findByUserId(any(Long.class))).thenReturn(java.util.List.of());
        return new ExpenseExtractionCoordinator(
                localService,
                externalService,
                validationService,
                classificationService,
                riskAssessmentService,
                objectMapper
        );
    }

    private ExtractedLineItemPayload lineItem(String description, String quantity, String totalPrice) {
        ExtractedLineItemPayload payload = new ExtractedLineItemPayload();
        payload.setDescription(description);
        payload.setQuantity(new BigDecimal(quantity));
        payload.setTotalPrice(new BigDecimal(totalPrice));
        return payload;
    }

    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
        return mapper;
    }
}
