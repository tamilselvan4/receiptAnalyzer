package com.expensetracker.service;

import com.expensetracker.config.ExpenseCategoryCatalog;
import com.expensetracker.model.ClassificationResult;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseLineItem;
import com.expensetracker.model.ExtractedExpensePayload;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExpenseClassificationServiceTest {

    private final ExpenseClassificationService classificationService = new ExpenseClassificationService(new ExpenseCategoryCatalog());

    @Test
    void predictsCategoryFromInvoiceTextWhenModelPayloadIsMissing() {
        Expense expense = new Expense();
        expense.setName("Metro Cab");
        expense.setComment("Taxi fare from airport");
        ExpenseLineItem item = new ExpenseLineItem();
        item.setDescription("Taxi travel fare");
        item.setTotalPrice(BigDecimal.valueOf(450));
        expense.setLineItems(List.of(item));

        ClassificationResult result = classificationService.classify(expense, new ExtractedExpensePayload(), "Taxi fare and airport travel booking");

        assertEquals("Travel", result.getPredictedCategory());
        assertNotNull(result.getConfidence());
        assertFalse(result.getAlternatives().isEmpty());
    }

    @Test
    void predictsExpandedCategoryForFuelKeywords() {
        Expense expense = new Expense();
        expense.setName("Indian Oil Fuel Station");
        expense.setComment("Fuel refill for client visit");

        ClassificationResult result = classificationService.classify(expense, new ExtractedExpensePayload(), "Diesel fuel recharge");

        assertEquals("Fuel", result.getPredictedCategory());
    }

    @Test
    void fallsBackToGeneralWhenNoRelevantKeywordExists() {
        Expense expense = new Expense();
        expense.setName("Unknown Vendor");
        expense.setComment("miscellaneous expense");

        ClassificationResult result = classificationService.classify(expense, new ExtractedExpensePayload(), "reference note only");

        assertEquals("General", result.getPredictedCategory());
    }
}
