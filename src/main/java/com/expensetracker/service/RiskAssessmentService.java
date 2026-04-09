package com.expensetracker.service;

import com.expensetracker.model.ClassificationResult;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExtractedExpensePayload;
import com.expensetracker.model.HistoricalRiskFeatures;
import com.expensetracker.model.RiskAssessment;
import com.expensetracker.model.RiskPriorPayload;
import com.expensetracker.model.Validation;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskAssessmentService {

    private static final BigDecimal LOW_THRESHOLD = BigDecimal.valueOf(0.35);
    private static final BigDecimal MEDIUM_THRESHOLD = BigDecimal.valueOf(0.70);
    private static final BigDecimal DUPLICATE_WEIGHT = BigDecimal.valueOf(0.35);
    private static final BigDecimal RECONCILIATION_WEIGHT = BigDecimal.valueOf(0.25);
    private static final BigDecimal CATEGORY_OUTLIER_WEIGHT = BigDecimal.valueOf(0.15);
    private static final BigDecimal MISSING_CRITICAL_WEIGHT = BigDecimal.valueOf(0.10);
    private static final BigDecimal LOW_CONFIDENCE_WEIGHT = BigDecimal.valueOf(0.10);
    private static final BigDecimal HIGH_TAX_RATIO_WEIGHT = BigDecimal.valueOf(0.05);
    private static final BigDecimal Z_SCORE_THRESHOLD = BigDecimal.valueOf(2.50);

    private final HistoricalRiskFeatureService historicalRiskFeatureService;

    public RiskAssessmentService(HistoricalRiskFeatureService historicalRiskFeatureService) {
        this.historicalRiskFeatureService = historicalRiskFeatureService;
    }

    public RiskAssessment assess(Expense expense,
                                 Validation validation,
                                 ClassificationResult classificationResult,
                                 RiskPriorPayload riskPrior,
                                 ExtractedExpensePayload payload,
                                 Long userId) {
        RiskAssessment assessment = new RiskAssessment();
        List<String> signals = new ArrayList<>();
        HistoricalRiskFeatures historical = historicalRiskFeatureService.calculate(expense, userId);
        BigDecimal businessRisk = BigDecimal.ZERO;
        boolean forceHighRisk = false;

        if (historical.isDuplicateInvoice()) {
            businessRisk = businessRisk.add(DUPLICATE_WEIGHT);
            signals.add("Duplicate invoice number detected for the same vendor");
            forceHighRisk = true;
        }

        if (hasSignal(validation, "Line item total does not reconcile")) {
            businessRisk = businessRisk.add(RECONCILIATION_WEIGHT);
            signals.add("Line items do not reconcile with the invoice total");
            forceHighRisk = true;
        }

        if (historical.getCategoryAmountZScore().compareTo(Z_SCORE_THRESHOLD) > 0) {
            businessRisk = businessRisk.add(CATEGORY_OUTLIER_WEIGHT);
            signals.add("Amount is significantly above the category history");
        }

        if (hasCriticalMissingFields(expense, validation)) {
            businessRisk = businessRisk.add(MISSING_CRITICAL_WEIGHT);
            signals.add("Critical invoice fields are missing or incomplete");
        }

        BigDecimal confidenceAverage = averageConfidence(payload, classificationResult);
        if (confidenceAverage.compareTo(BigDecimal.valueOf(0.65)) < 0) {
            businessRisk = businessRisk.add(LOW_CONFIDENCE_WEIGHT);
            signals.add("Extraction confidence is low or incomplete");
        }

        if (hasHighTaxRatio(expense)) {
            businessRisk = businessRisk.add(HIGH_TAX_RATIO_WEIGHT);
            signals.add("Tax ratio appears unusually high");
        }

        businessRisk = businessRisk.min(BigDecimal.ONE);
        BigDecimal mlRisk = riskPrior == null || riskPrior.getAnomalyScore() == null
                ? BigDecimal.ZERO
                : sanitizeScore(riskPrior.getAnomalyScore());
        BigDecimal finalRisk = sanitizeScore(mlRisk.multiply(BigDecimal.valueOf(0.50))
                .add(businessRisk.multiply(BigDecimal.valueOf(0.50))));

        if (forceHighRisk && finalRisk.compareTo(MEDIUM_THRESHOLD) < 0) {
            finalRisk = MEDIUM_THRESHOLD;
        }
        if (validation != null && validation.isAnomaly() && finalRisk.compareTo(LOW_THRESHOLD) < 0) {
            finalRisk = LOW_THRESHOLD;
        }

        assessment.setRiskScore(finalRisk);
        assessment.setRiskBand(resolveBand(finalRisk, forceHighRisk));
        assessment.setReviewRequired(forceHighRisk || finalRisk.compareTo(LOW_THRESHOLD) >= 0 || (validation != null && validation.isAnomaly()));
        assessment.setSignals(limitSignals(signals, validation));
        assessment.setReasonSummary(String.join("; ", assessment.getSignals()));
        assessment.setModelVersion(riskPrior != null && riskPrior.getModelVersion() != null
                ? riskPrior.getModelVersion()
                : "rules-risk-v1");
        assessment.setSource(riskPrior != null && riskPrior.getAnomalyScore() != null
                ? "hybrid-ml-rules"
                : resolveFallbackSource(expense));
        return assessment;
    }

    private String resolveFallbackSource(Expense expense) {
        if (expense == null || expense.getName() == null || expense.getAmount() == null) {
            return "fallback-validation-only";
        }
        return "rules-only";
    }

    private List<String> limitSignals(List<String> signals, Validation validation) {
        List<String> combined = new ArrayList<>(signals);
        if (validation != null && validation.getSignals() != null) {
            for (String signal : validation.getSignals()) {
                if (!combined.contains(signal)) {
                    combined.add(signal);
                }
            }
        }
        return combined.stream().limit(3).toList();
    }

    private boolean hasSignal(Validation validation, String fragment) {
        if (validation == null || validation.getSignals() == null) {
            return false;
        }
        return validation.getSignals().stream().anyMatch(signal -> signal.contains(fragment));
    }

    private boolean hasCriticalMissingFields(Expense expense, Validation validation) {
        if (expense == null) {
            return true;
        }
        if (expense.getName() == null || expense.getName().isBlank()
                || expense.getInvoiceNumber() == null || expense.getInvoiceNumber().isBlank()
                || expense.getDate() == null
                || expense.getAmount() == null) {
            return true;
        }
        return hasSignal(validation, "missing");
    }

    private BigDecimal averageConfidence(ExtractedExpensePayload payload, ClassificationResult classificationResult) {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        if (payload != null && payload.getConfidence() != null) {
            for (Double value : payload.getConfidence().values()) {
                if (value == null) {
                    continue;
                }
                total = total.add(BigDecimal.valueOf(value));
                count++;
            }
        }
        if (classificationResult != null && classificationResult.getConfidence() != null) {
            total = total.add(classificationResult.getConfidence());
            count++;
        }
        if (count == 0) {
            return BigDecimal.valueOf(0.50);
        }
        return total.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP);
    }

    private boolean hasHighTaxRatio(Expense expense) {
        if (expense == null || expense.getAmount() == null || expense.getTax() == null) {
            return false;
        }
        if (expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal ratio = expense.getTax().divide(expense.getAmount(), 4, RoundingMode.HALF_UP);
        return ratio.compareTo(BigDecimal.valueOf(0.35)) > 0;
    }

    private BigDecimal sanitizeScore(BigDecimal score) {
        if (score == null) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (score.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return score.setScale(4, RoundingMode.HALF_UP);
    }

    private String resolveBand(BigDecimal score, boolean forceHighRisk) {
        if (forceHighRisk || score.compareTo(MEDIUM_THRESHOLD) >= 0) {
            return "HIGH";
        }
        if (score.compareTo(LOW_THRESHOLD) >= 0) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
