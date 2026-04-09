package com.expensetracker.service;

import com.expensetracker.model.ClassificationResult;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseLineItem;
import com.expensetracker.model.ExtractedExpensePayload;
import com.expensetracker.model.RiskAssessment;
import com.expensetracker.model.RiskPriorPayload;
import com.expensetracker.model.Validation;
import com.expensetracker.repository.ExpenseRepo;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiskAssessmentServiceTest {

    @Test
    void duplicateInvoiceForcesHighRisk() {
        Expense current = baseExpense();
        Expense historical = baseExpense();
        historical.setId(5L);

        ExpenseRepo repo = mock(ExpenseRepo.class);
        when(repo.findByUserId(1L)).thenReturn(List.of(historical));

        RiskAssessmentService service = new RiskAssessmentService(new HistoricalRiskFeatureService(repo));
        Validation validation = new Validation();
        validation.setSignals(List.of());

        RiskAssessment assessment = service.assess(
                current,
                validation,
                classification("Travel", 0.91),
                riskPrior(0.20),
                new ExtractedExpensePayload(),
                1L
        );

        assertEquals("HIGH", assessment.getRiskBand());
        assertTrue(assessment.getSignals().stream().anyMatch(signal -> signal.contains("Duplicate invoice")));
    }

    @Test
    void lowConfidenceAndMissingFieldsRequireReview() {
        Expense current = new Expense();
        current.setAmount(BigDecimal.valueOf(20));

        ExpenseRepo repo = mock(ExpenseRepo.class);
        when(repo.findByUserId(1L)).thenReturn(List.of());

        RiskAssessmentService service = new RiskAssessmentService(new HistoricalRiskFeatureService(repo));
        Validation validation = new Validation();
        validation.setAnomaly(true);
        validation.setSignals(List.of("Vendor name is missing"));

        ExtractedExpensePayload payload = new ExtractedExpensePayload();

        RiskAssessment assessment = service.assess(current, validation, classification("General", 0.40), null, payload, 1L);

        assertTrue(assessment.isReviewRequired());
        assertEquals("MEDIUM", assessment.getRiskBand());
    }

    private Expense baseExpense() {
        Expense expense = new Expense();
        expense.setName("Metro Cab");
        expense.setInvoiceNumber("INV-1");
        expense.setAmount(BigDecimal.valueOf(100));
        expense.setTax(BigDecimal.valueOf(10));
        expense.setDiscount(BigDecimal.ZERO);
        expense.setDate(LocalDate.of(2024, 1, 10));
        expense.setCategory("Travel");
        ExpenseLineItem lineItem = new ExpenseLineItem();
        lineItem.setDescription("Taxi fare");
        lineItem.setTotalPrice(BigDecimal.valueOf(90));
        expense.setLineItems(List.of(lineItem));
        return expense;
    }

    private ClassificationResult classification(String label, double confidence) {
        ClassificationResult result = new ClassificationResult();
        result.setPredictedCategory(label);
        result.setConfidence(BigDecimal.valueOf(confidence));
        result.setModelVersion("test");
        return result;
    }

    private RiskPriorPayload riskPrior(double score) {
        RiskPriorPayload payload = new RiskPriorPayload();
        payload.setAnomalyScore(BigDecimal.valueOf(score));
        payload.setModelVersion("risk-iforest-v1");
        return payload;
    }
}
