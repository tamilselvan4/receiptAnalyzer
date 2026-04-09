package com.expensetracker.service;

import com.expensetracker.config.InvoiceProcessingProperties;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExpenseLineItem;
import com.expensetracker.model.Validation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class InvoiceValidationService {

    private final InvoiceProcessingProperties properties;

    public InvoiceValidationService(InvoiceProcessingProperties properties) {
        this.properties = properties;
    }

    public Validation validate(Expense expense) {
        Validation validation = new Validation();
        List<String> signals = new ArrayList<>();

        BigDecimal amount = safe(expense.getAmount());
        BigDecimal tax = safe(expense.getTax());
        BigDecimal discount = safe(expense.getDiscount());

        if (expense.getName() == null || expense.getName().isBlank()) {
            signals.add("Vendor name is missing");
        }
        if (expense.getInvoiceNumber() == null || expense.getInvoiceNumber().isBlank()) {
            signals.add("Invoice number is missing");
        }
        if (expense.getDate() == null) {
            signals.add("Invoice date is missing");
        }
        if (expense.getAmount() == null) {
            signals.add("Total amount is missing");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            signals.add("Total amount must be greater than zero");
        }
        if (tax.compareTo(BigDecimal.ZERO) < 0) {
            signals.add("Tax cannot be negative");
        }
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            signals.add("Discount cannot be negative");
        }
        if (tax.compareTo(amount) > 0 && amount.compareTo(BigDecimal.ZERO) > 0) {
            signals.add("Tax exceeds the total amount");
        }
        if (expense.getDueDate() != null && expense.getDate() != null && expense.getDueDate().isBefore(expense.getDate())) {
            signals.add("Due date is earlier than the invoice date");
        }
        if (amount.compareTo(properties.getValidation().getHighAmountThreshold()) > 0) {
            signals.add("Amount exceeds the configured review threshold");
        }

        BigDecimal lineItemTotal = calculateLineItemTotal(expense.getLineItems());
        if (lineItemTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal expectedTotal = lineItemTotal.add(tax).subtract(discount);
            BigDecimal delta = expectedTotal.subtract(amount).abs();
            if (delta.compareTo(properties.getValidation().getLineItemTolerance()) > 0) {
                signals.add("Line item total does not reconcile with amount, tax, and discount");
            }
        }

        boolean anomaly = !signals.isEmpty();
        validation.setAnomaly(anomaly);
        validation.setSignals(signals);
        validation.setReviewLevel(anomaly ? inferReviewLevel(signals) : "LOW");
        validation.setReason(anomaly
                ? "Manual review recommended: " + String.join("; ", signals)
                : "Validation checks passed");
        return validation;
    }

    private BigDecimal calculateLineItemTotal(List<ExpenseLineItem> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return lineItems.stream()
                .map(ExpenseLineItem::getTotalPrice)
                .filter(value -> value != null && value.compareTo(BigDecimal.ZERO) >= 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String inferReviewLevel(List<String> signals) {
        boolean severe = signals.stream().anyMatch(signal ->
                signal.contains("greater than zero")
                        || signal.contains("negative")
                        || signal.contains("earlier")
                        || signal.contains("reconcile")
                        || signal.contains("exceeds"));
        return severe ? "HIGH" : "MEDIUM";
    }
}
