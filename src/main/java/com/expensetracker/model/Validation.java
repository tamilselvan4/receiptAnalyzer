package com.expensetracker.model;

import java.util.ArrayList;
import java.util.List;

public class Validation {
    private boolean anomaly;
    private String reason;
    private String reviewLevel = "LOW";
    private List<String> signals = new ArrayList<>();

    public boolean isAnomaly() {
        return anomaly;
    }

    public void setAnomaly(boolean anomaly) {
        this.anomaly = anomaly;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReviewLevel() {
        return reviewLevel;
    }

    public void setReviewLevel(String reviewLevel) {
        this.reviewLevel = reviewLevel;
    }

    public List<String> getSignals() {
        return signals;
    }

    public void setSignals(List<String> signals) {
        this.signals = signals == null ? new ArrayList<>() : new ArrayList<>(signals);
    }
}
