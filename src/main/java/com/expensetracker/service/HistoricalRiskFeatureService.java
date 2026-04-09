package com.expensetracker.service;

import com.expensetracker.model.Expense;
import com.expensetracker.model.HistoricalRiskFeatures;
import com.expensetracker.repository.ExpenseRepo;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class HistoricalRiskFeatureService {

    private final ExpenseRepo expenseRepo;

    public HistoricalRiskFeatureService(ExpenseRepo expenseRepo) {
        this.expenseRepo = expenseRepo;
    }

    public HistoricalRiskFeatures calculate(Expense expense, Long userId) {
        HistoricalRiskFeatures features = new HistoricalRiskFeatures();
        if (expense == null || userId == null) {
            return features;
        }

        List<Expense> history = expenseRepo.findByUserId(userId).stream()
                .filter(candidate -> expense.getId() == null || !expense.getId().equals(candidate.getId()))
                .toList();

        String normalizedVendor = normalize(expense.getName());
        String normalizedInvoice = normalize(expense.getInvoiceNumber());
        BigDecimal amount = expense.getAmount() == null ? BigDecimal.ZERO : expense.getAmount();
        String normalizedCategory = normalize(expense.getCategory());

        int vendorFrequency = 0;
        int repeatedAmountCount = 0;
        int categoryCount = 0;
        double categorySum = 0.0;
        double categorySquaredSum = 0.0;

        for (Expense candidate : history) {
            if (!normalizedVendor.isEmpty() && normalizedVendor.equals(normalize(candidate.getName()))) {
                vendorFrequency++;
                if (!normalizedInvoice.isEmpty() && normalizedInvoice.equals(normalize(candidate.getInvoiceNumber()))) {
                    features.setDuplicateInvoice(true);
                }
            }

            if (candidate.getAmount() != null && candidate.getAmount().compareTo(amount) == 0) {
                repeatedAmountCount++;
            }

            if (!normalizedCategory.isEmpty() && normalizedCategory.equals(normalize(candidate.getCategory())) && candidate.getAmount() != null) {
                double candidateAmount = candidate.getAmount().doubleValue();
                categoryCount++;
                categorySum += candidateAmount;
                categorySquaredSum += candidateAmount * candidateAmount;
            }
        }

        features.setVendorFrequency(vendorFrequency);
        features.setRepeatedAmountCount(repeatedAmountCount);
        features.setCategoryAmountZScore(calculateZScore(amount, categoryCount, categorySum, categorySquaredSum));
        return features;
    }

    private BigDecimal calculateZScore(BigDecimal amount, int categoryCount, double categorySum, double categorySquaredSum) {
        if (categoryCount < 2 || amount == null) {
            return BigDecimal.ZERO;
        }

        double mean = categorySum / categoryCount;
        double variance = (categorySquaredSum / categoryCount) - (mean * mean);
        if (variance <= 0.0001d) {
            return BigDecimal.ZERO;
        }

        double stdDev = Math.sqrt(variance);
        double zScore = Math.abs((amount.doubleValue() - mean) / stdDev);
        return BigDecimal.valueOf(zScore).setScale(4, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ENGLISH);
    }
}
