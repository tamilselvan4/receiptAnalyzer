package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RiskPriorPayload {

    @JsonProperty("anomaly_score")
    private BigDecimal anomalyScore;
    @JsonProperty("model_version")
    private String modelVersion;
    @JsonProperty("feature_flags")
    private List<String> featureFlags = new ArrayList<>();

    public BigDecimal getAnomalyScore() {
        return anomalyScore;
    }

    public void setAnomalyScore(BigDecimal anomalyScore) {
        this.anomalyScore = anomalyScore;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public List<String> getFeatureFlags() {
        return featureFlags;
    }

    public void setFeatureFlags(List<String> featureFlags) {
        this.featureFlags = featureFlags == null ? new ArrayList<>() : new ArrayList<>(featureFlags);
    }
}
