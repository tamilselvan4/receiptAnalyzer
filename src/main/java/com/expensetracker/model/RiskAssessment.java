package com.expensetracker.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class RiskAssessment {

    private BigDecimal riskScore;
    private String riskBand;
    private boolean reviewRequired;
    private List<String> signals = new ArrayList<>();
    private String reasonSummary;
    private String modelVersion;
    private String source;

    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(BigDecimal riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskBand() {
        return riskBand;
    }

    public void setRiskBand(String riskBand) {
        this.riskBand = riskBand;
    }

    public boolean isReviewRequired() {
        return reviewRequired;
    }

    public void setReviewRequired(boolean reviewRequired) {
        this.reviewRequired = reviewRequired;
    }

    public List<String> getSignals() {
        return signals;
    }

    public void setSignals(List<String> signals) {
        this.signals = signals == null ? new ArrayList<>() : new ArrayList<>(signals);
    }

    public String getReasonSummary() {
        return reasonSummary;
    }

    public void setReasonSummary(String reasonSummary) {
        this.reasonSummary = reasonSummary;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
