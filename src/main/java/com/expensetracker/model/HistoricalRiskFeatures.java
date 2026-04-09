package com.expensetracker.model;

import java.math.BigDecimal;

public class HistoricalRiskFeatures {

    private boolean duplicateInvoice;
    private int vendorFrequency;
    private BigDecimal categoryAmountZScore = BigDecimal.ZERO;
    private int repeatedAmountCount;

    public boolean isDuplicateInvoice() {
        return duplicateInvoice;
    }

    public void setDuplicateInvoice(boolean duplicateInvoice) {
        this.duplicateInvoice = duplicateInvoice;
    }

    public int getVendorFrequency() {
        return vendorFrequency;
    }

    public void setVendorFrequency(int vendorFrequency) {
        this.vendorFrequency = vendorFrequency;
    }

    public BigDecimal getCategoryAmountZScore() {
        return categoryAmountZScore;
    }

    public void setCategoryAmountZScore(BigDecimal categoryAmountZScore) {
        this.categoryAmountZScore = categoryAmountZScore;
    }

    public int getRepeatedAmountCount() {
        return repeatedAmountCount;
    }

    public void setRepeatedAmountCount(int repeatedAmountCount) {
        this.repeatedAmountCount = repeatedAmountCount;
    }
}
