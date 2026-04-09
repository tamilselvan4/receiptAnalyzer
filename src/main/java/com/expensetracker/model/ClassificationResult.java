package com.expensetracker.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassificationResult {

    @JsonProperty("predicted_category")
    private String predictedCategory;
    private BigDecimal confidence;
    private List<ClassificationAlternative> alternatives = new ArrayList<>();
    @JsonProperty("model_version")
    private String modelVersion;
    private String source;

    public String getPredictedCategory() {
        return predictedCategory;
    }

    public void setPredictedCategory(String predictedCategory) {
        this.predictedCategory = predictedCategory;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public void setConfidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public List<ClassificationAlternative> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<ClassificationAlternative> alternatives) {
        this.alternatives = alternatives == null ? new ArrayList<>() : new ArrayList<>(alternatives);
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
