package com.expensetracker.service;

import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseExtractionResult;
import com.expensetracker.model.ExpenseLineItem;
import com.expensetracker.model.ClassificationResult;
import com.expensetracker.model.ExtractedExpensePayload;
import com.expensetracker.model.ExtractedLineItemPayload;
import com.expensetracker.model.RiskAssessment;
import com.expensetracker.model.RiskPriorPayload;
import com.expensetracker.model.Validation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ExpenseExtractionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ExpenseExtractionCoordinator.class);

    private final LocalAiExtractionService localAiExtractionService;
    private final AgenticRagService agenticRagService;
    private final InvoiceValidationService invoiceValidationService;
    private final ExpenseClassificationService expenseClassificationService;
    private final RiskAssessmentService riskAssessmentService;
    private final ObjectMapper objectMapper;

    public ExpenseExtractionCoordinator(LocalAiExtractionService localAiExtractionService,
                                        AgenticRagService agenticRagService,
                                        InvoiceValidationService invoiceValidationService,
                                        ExpenseClassificationService expenseClassificationService,
                                        RiskAssessmentService riskAssessmentService,
                                        ObjectMapper extractionObjectMapper) {
        this.localAiExtractionService = localAiExtractionService;
        this.agenticRagService = agenticRagService;
        this.invoiceValidationService = invoiceValidationService;
        this.expenseClassificationService = expenseClassificationService;
        this.riskAssessmentService = riskAssessmentService;
        this.objectMapper = extractionObjectMapper;
    }

    public ExpenseExtractionResult extract(String fileName, byte[] fileBytes, boolean allowExternalFallback) throws IOException {
        return extract(fileName, fileBytes, allowExternalFallback, null);
    }

    public ExpenseExtractionResult extract(String fileName,
                                           byte[] fileBytes,
                                           boolean allowExternalFallback,
                                           Long userId) throws IOException {
        String extension = resolveExtension(fileName);
        File tempFile = File.createTempFile("expense-extract-", extension);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(fileBytes);
        }

        try {
            Optional<ExtractedExpensePayload> localPayload = localAiExtractionService.extractExpense(tempFile.getAbsolutePath());
            if (localPayload.isPresent() && hasMinimumStructuredData(localPayload.get())) {
                return toResult(localPayload.get(), "local-model", userId);
            }

            if (allowExternalFallback) {
                try {
                    JSONObject externalPayload = agenticRagService.analyzeReceipt(fileName, fileBytes);
                    return fromExternalPayload(externalPayload, userId);
                } catch (Exception exception) {
                    log.warn("External fallback failed for {}: {}", fileName, exception.getMessage());
                }
            }

            ExtractedExpensePayload payload = localPayload.orElseGet(ExtractedExpensePayload::new);
            String source = localPayload.isPresent() ? "local-model-partial" : "local-model-empty";
            return toResult(payload, source, userId);
        } finally {
            if (!tempFile.delete()) {
                log.debug("Temporary extraction file was not deleted: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private ExpenseExtractionResult fromExternalPayload(JSONObject externalPayload, Long userId) throws IOException {
        JSONObject extractedData = externalPayload.optJSONObject("structured_data");
        ExtractedExpensePayload payload = extractedData == null
                ? new ExtractedExpensePayload()
                : objectMapper.readValue(extractedData.toString(), ExtractedExpensePayload.class);
        payload.setRawOcrText(externalPayload.optString("ocr_text", null));
        return toResult(payload, "external-api", userId);
    }

    private ExpenseExtractionResult toResult(ExtractedExpensePayload payload, String source, Long userId) {
        Expense normalized = normalizeExpense(toExpense(payload));
        Validation validation = invoiceValidationService.validate(normalized);
        ClassificationResult classificationResult = expenseClassificationService.classify(normalized, payload, payload.getRawOcrText());
        RiskPriorPayload riskPrior = payload.getRiskPrior();
        RiskAssessment riskAssessment = riskAssessmentService.assess(
                normalized,
                validation,
                classificationResult,
                riskPrior,
                payload,
                userId
        );
        applyAssessment(normalized, classificationResult, riskAssessment);
        return new ExpenseExtractionResult(normalized, validation, source, payload.getRawOcrText(), classificationResult, riskAssessment);
    }

    private Expense toExpense(ExtractedExpensePayload payload) {
        Expense expense = new Expense();
        expense.setName(payload.getName());
        expense.setInvoiceNumber(payload.getInvoiceNumber());
        expense.setAmount(payload.getAmount());
        expense.setTax(payload.getTax() == null ? BigDecimal.ZERO : payload.getTax());
        expense.setDiscount(payload.getDiscount());
        expense.setCurrency(payload.getCurrency());
        expense.setDate(payload.getDate());
        expense.setDueDate(payload.getDueDate());
        expense.setCategory(payload.getCategory());
        expense.setSellerAddress(payload.getSellerAddress());
        expense.setClientName(payload.getClientName());
        expense.setClientAddress(payload.getClientAddress());
        expense.setPaymentMethod(payload.getPaymentMethod());
        expense.setBankName(payload.getBankName());
        expense.setAccountNumber(payload.getAccountNumber());
        expense.setComment(buildComment(payload));
        expense.setLineItems(mapLineItems(payload.getLineItems()));
        return expense;
    }

    private List<ExpenseLineItem> mapLineItems(List<ExtractedLineItemPayload> payloadLineItems) {
        List<ExpenseLineItem> lineItems = new ArrayList<>();
        if (payloadLineItems == null) {
            return lineItems;
        }
        for (int index = 0; index < payloadLineItems.size(); index++) {
            ExtractedLineItemPayload payload = payloadLineItems.get(index);
            if (payload == null) {
                continue;
            }
            ExpenseLineItem lineItem = new ExpenseLineItem();
            lineItem.setLineIndex(index);
            lineItem.setDescription(payload.getDescription() == null ? "" : payload.getDescription());
            lineItem.setQuantity(payload.getQuantity());
            lineItem.setTotalPrice(payload.getTotalPrice());
            lineItems.add(lineItem);
        }
        return lineItems;
    }

    private String buildComment(ExtractedExpensePayload payload) {
        String comment = payload.getComment();
        String invoiceNumber = payload.getInvoiceNumber();
        Map<String, Object> extras = payload.getExtraFields();
        if ((invoiceNumber == null || invoiceNumber.isBlank()) && extras != null && !extras.isEmpty()) {
            Object invoiceNumberExtra = extras.get("invoice_number");
            if (invoiceNumberExtra != null) {
                invoiceNumber = invoiceNumberExtra.toString();
            }
        }

        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            return comment;
        }
        if (comment == null || comment.isBlank()) {
            return "Invoice " + invoiceNumber;
        }
        if (comment.contains(invoiceNumber)) {
            return comment;
        }
        return comment + " | Invoice " + invoiceNumber;
    }

    private Expense normalizeExpense(Expense expense) {
        if (expense.getCurrency() == null || expense.getCurrency().isBlank()) {
            expense.setCurrency("INR");
        } else {
            expense.setCurrency(expense.getCurrency().trim().toUpperCase());
        }
        if (expense.getTax() == null) {
            expense.setTax(BigDecimal.ZERO);
        }
        if (expense.getLineItems() == null) {
            expense.setLineItems(List.of());
        }
        return expense;
    }

    private void applyAssessment(Expense expense, ClassificationResult classificationResult, RiskAssessment riskAssessment) {
        if (classificationResult != null) {
            expense.setCategory(classificationResult.getPredictedCategory());
            expense.setPredictedCategory(classificationResult.getPredictedCategory());
            expense.setClassificationConfidence(classificationResult.getConfidence());
            expense.setClassificationModelVersion(classificationResult.getModelVersion());
            expense.setClassificationAlternativesJson(writeJson(classificationResult.getAlternatives()));
        }
        if (riskAssessment != null) {
            expense.setRiskScore(riskAssessment.getRiskScore());
            expense.setRiskBand(riskAssessment.getRiskBand());
            expense.setRiskModelVersion(riskAssessment.getModelVersion());
            expense.setReviewRequired(riskAssessment.isReviewRequired());
            expense.setRiskSignalsJson(writeJson(riskAssessment.getSignals()));
            expense.setRiskReasonSummary(riskAssessment.getReasonSummary());
            expense.setRiskSource(riskAssessment.getSource());
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            log.debug("Unable to serialize AI assessment payload: {}", exception.getMessage());
            return "[]";
        }
    }

    private boolean hasMinimumStructuredData(ExtractedExpensePayload payload) {
        return payload.getAmount() != null
                || (payload.getName() != null && !payload.getName().isBlank())
                || (payload.getInvoiceNumber() != null && !payload.getInvoiceNumber().isBlank())
                || (payload.getLineItems() != null && !payload.getLineItems().isEmpty());
    }

    private String resolveExtension(String fileName) {
        int dotIndex = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return ".png";
        }
        return fileName.substring(dotIndex);
    }
}
