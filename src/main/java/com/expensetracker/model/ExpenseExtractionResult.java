package com.expensetracker.model;

public class ExpenseExtractionResult {

    private final Expense expense;
    private final Validation validation;
    private final String source;
    private final String rawOcrText;
    private final ClassificationResult classificationResult;
    private final RiskAssessment riskAssessment;

    public ExpenseExtractionResult(Expense expense,
                                   Validation validation,
                                   String source,
                                   String rawOcrText,
                                   ClassificationResult classificationResult,
                                   RiskAssessment riskAssessment) {
        this.expense = expense;
        this.validation = validation;
        this.source = source;
        this.rawOcrText = rawOcrText;
        this.classificationResult = classificationResult;
        this.riskAssessment = riskAssessment;
    }

    public Expense getExpense() {
        return expense;
    }

    public Validation getValidation() {
        return validation;
    }

    public String getSource() {
        return source;
    }

    public String getRawOcrText() {
        return rawOcrText;
    }

    public ClassificationResult getClassificationResult() {
        return classificationResult;
    }

    public RiskAssessment getRiskAssessment() {
        return riskAssessment;
    }
}
