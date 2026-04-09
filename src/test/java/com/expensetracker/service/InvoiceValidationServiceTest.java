package com.expensetracker.service;

import com.expensetracker.config.InvoiceProcessingProperties;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseLineItem;
import com.expensetracker.model.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvoiceValidationServiceTest {

    private final InvoiceValidationService validationService = new InvoiceValidationService(new InvoiceProcessingProperties());

    @Test
    void passesWellFormedInvoice() {
        Expense expense = new Expense();
        expense.setName("Acme");
        expense.setInvoiceNumber("INV-100");
        expense.setAmount(BigDecimal.valueOf(118));
        expense.setTax(BigDecimal.valueOf(18));
        expense.setDiscount(BigDecimal.ZERO);
        expense.setCurrency("INR");
        expense.setDate(LocalDate.of(2024, 1, 10));
        expense.setDueDate(LocalDate.of(2024, 1, 20));

        ExpenseLineItem item = new ExpenseLineItem();
        item.setDescription("Subscription");
        item.setTotalPrice(BigDecimal.valueOf(100));
        expense.setLineItems(List.of(item));

        Validation validation = validationService.validate(expense);

        assertFalse(validation.isAnomaly());
        assertEquals("LOW", validation.getReviewLevel());
        assertEquals("Validation checks passed", validation.getReason());
    }

    @Test
    void flagsInconsistentInvoiceForManualReview() {
        Expense expense = new Expense();
        expense.setName("");
        expense.setInvoiceNumber("");
        expense.setAmount(BigDecimal.valueOf(-10));
        expense.setTax(BigDecimal.valueOf(50));
        expense.setDiscount(BigDecimal.valueOf(-1));
        expense.setDate(LocalDate.of(2024, 2, 10));
        expense.setDueDate(LocalDate.of(2024, 2, 1));

        ExpenseLineItem item = new ExpenseLineItem();
        item.setDescription("Consulting");
        item.setTotalPrice(BigDecimal.valueOf(100));
        expense.setLineItems(List.of(item));

        Validation validation = validationService.validate(expense);

        assertTrue(validation.isAnomaly());
        assertEquals("HIGH", validation.getReviewLevel());
        assertTrue(validation.getSignals().stream().anyMatch(signal -> signal.contains("Vendor name is missing")));
        assertTrue(validation.getSignals().stream().anyMatch(signal -> signal.contains("Line item total does not reconcile")));
    }

    @Test
    void lineItemsReconcileWhenAmountMatchesItemsPlusTaxMinusDiscount() {
        Expense expense = new Expense();
        expense.setName("Vendor");
        expense.setInvoiceNumber("INV-101");
        expense.setAmount(BigDecimal.valueOf(108));
        expense.setTax(BigDecimal.valueOf(10));
        expense.setDiscount(BigDecimal.valueOf(2));
        expense.setDate(LocalDate.of(2024, 2, 10));

        ExpenseLineItem item = new ExpenseLineItem();
        item.setDescription("Product");
        item.setTotalPrice(BigDecimal.valueOf(100));
        expense.setLineItems(List.of(item));

        Validation validation = validationService.validate(expense);

        assertFalse(validation.getSignals().stream().anyMatch(signal -> signal.contains("Line item total does not reconcile")));
    }
}
