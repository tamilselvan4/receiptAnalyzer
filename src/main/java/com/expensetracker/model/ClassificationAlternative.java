package com.expensetracker.model;

import java.math.BigDecimal;

public class ClassificationAlternative {

    private String label;
    private BigDecimal confidence;

    public ClassificationAlternative() {
    }

    public ClassificationAlternative(String label, BigDecimal confidence) {
        this.label = label;
        this.confidence = confidence;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }
}
