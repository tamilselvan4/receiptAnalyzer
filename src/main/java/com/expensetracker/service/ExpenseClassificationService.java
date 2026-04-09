package com.expensetracker.service;

import com.expensetracker.config.ExpenseCategoryCatalog;
import com.expensetracker.model.ClassificationAlternative;
import com.expensetracker.model.ClassificationResult;
import com.expensetracker.model.Expense;
import com.expensetracker.model.ExtractedExpensePayload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class ExpenseClassificationService {

    private static final String MODEL_VERSION = "category-expanded-rules-v1";

    private final ExpenseCategoryCatalog expenseCategoryCatalog;

    public ExpenseClassificationService(ExpenseCategoryCatalog expenseCategoryCatalog) {
        this.expenseCategoryCatalog = expenseCategoryCatalog;
    }

    public ClassificationResult classify(Expense expense, ExtractedExpensePayload payload, String rawOcrText) {
        ClassificationResult localModelResult = payload == null ? null : payload.getClassification();
        String canonicalPrediction = resolveCanonicalPrediction(expense, localModelResult, rawOcrText);
        ScoreInputs scoreInputs = buildScoreInputs(expense, rawOcrText);
        Map<String, Integer> scores = scoreExpandedCategories(scoreInputs, canonicalPrediction);
        List<Map.Entry<String, Integer>> ranked = scores.entrySet().stream()
                .sorted((left, right) -> {
                    int scoreOrder = Integer.compare(right.getValue(), left.getValue());
                    return scoreOrder != 0 ? scoreOrder : left.getKey().compareToIgnoreCase(right.getKey());
                })
                .toList();

        String predicted = ranked.isEmpty() || ranked.getFirst().getValue() <= 0
                ? "General"
                : ranked.getFirst().getKey();
        int totalScore = Math.max(ranked.stream().mapToInt(Map.Entry::getValue).sum(), 1);
        int topScore = ranked.isEmpty() ? 1 : Math.max(ranked.getFirst().getValue(), 1);

        ClassificationResult result = new ClassificationResult();
        result.setPredictedCategory(predicted);
        result.setConfidence(normalizeConfidence(topScore, totalScore));
        result.setAlternatives(buildAlternatives(ranked, predicted, totalScore));
        result.setModelVersion(MODEL_VERSION);
        result.setSource(localModelResult != null && localModelResult.getPredictedCategory() != null
                ? "local-ai-plus-expanded-rules"
                : "rules-or-extractor");
        return result;
    }

    private String resolveCanonicalPrediction(Expense expense, ClassificationResult localModelResult, String rawOcrText) {
        if (localModelResult != null && localModelResult.getPredictedCategory() != null && !localModelResult.getPredictedCategory().isBlank()) {
            return expenseCategoryCatalog.mapCanonicalCategory(localModelResult.getPredictedCategory());
        }
        if (expense != null && expense.getCategory() != null && !expense.getCategory().isBlank()) {
            return expenseCategoryCatalog.mapCanonicalCategory(expense.getCategory());
        }

        String corpus = buildCanonicalCorpus(expense, rawOcrText);
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("Travel", countMatches(corpus, "taxi", "flight", "hotel", "train", "travel", "uber", "lyft", "ticket", "airport"));
        scores.put("Meals", countMatches(corpus, "restaurant", "cafe", "coffee", "food", "meal", "pizza", "kitchen", "wine", "dining"));
        scores.put("Office Supplies", countMatches(corpus, "office", "printer", "paper", "supply", "stationery", "desk", "holder", "file", "notebook"));
        scores.put("Electronics", countMatches(corpus, "laptop", "phone", "charger", "monitor", "usb", "camera", "console", "electronic"));
        scores.put("Furniture", countMatches(corpus, "chair", "table", "furniture", "shelf", "cabinet", "desk"));
        scores.put("Services", countMatches(corpus, "service", "consulting", "subscription", "maintenance", "repair", "support"));
        scores.put("General", 1);

        return scores.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("General");
    }

    private Map<String, Integer> scoreExpandedCategories(ScoreInputs scoreInputs, String canonicalPrediction) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String category : expenseCategoryCatalog.getAvailableCategories()) {
            if (ExpenseCategoryCatalog.OTHER.equals(category)) {
                continue;
            }
            int score = scoreCategory(scoreInputs, category);
            if (category.equalsIgnoreCase(expenseCategoryCatalog.mapCanonicalCategory(canonicalPrediction))) {
                score += 2;
            }
            scores.put(category, score);
        }
        return scores;
    }

    private int scoreCategory(ScoreInputs scoreInputs, String category) {
        int score = 0;
        for (String keyword : expenseCategoryCatalog.getCategoryKeywords().getOrDefault(category, List.of())) {
            score += scoreMatches(scoreInputs.sellerCorpus, keyword, 2);
            score += scoreMatches(scoreInputs.lineItemCorpus, keyword, 2);
            score += scoreMatches(scoreInputs.freeTextCorpus, keyword, 1);
        }
        return score;
    }

    private int scoreMatches(String corpus, String keyword, int weight) {
        if (corpus == null || corpus.isBlank() || keyword == null || keyword.isBlank()) {
            return 0;
        }
        return corpus.contains(keyword.toLowerCase(Locale.ENGLISH)) ? weight : 0;
    }

    private List<ClassificationAlternative> buildAlternatives(List<Map.Entry<String, Integer>> ranked,
                                                              String predicted,
                                                              int totalScore) {
        List<ClassificationAlternative> alternatives = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : ranked) {
            if (entry.getKey().equalsIgnoreCase(predicted)) {
                continue;
            }
            alternatives.add(new ClassificationAlternative(entry.getKey(), normalizeConfidence(entry.getValue(), totalScore)));
            if (alternatives.size() == 3) {
                break;
            }
        }
        return alternatives;
    }

    private BigDecimal normalizeConfidence(int score, int totalScore) {
        double ratio = totalScore <= 0 ? 0.5d : (double) score / (double) totalScore;
        double bounded = Math.max(0.35d, Math.min(0.99d, ratio));
        return BigDecimal.valueOf(bounded).setScale(4, RoundingMode.HALF_UP);
    }

    private int countMatches(String corpus, String... keywords) {
        int score = 0;
        for (String keyword : keywords) {
            if (corpus.contains(keyword)) {
                score++;
            }
        }
        return score;
    }

    private String buildCanonicalCorpus(Expense expense, String rawOcrText) {
        ScoreInputs inputs = buildScoreInputs(expense, rawOcrText);
        return String.join(" ", inputs.sellerCorpus, inputs.lineItemCorpus, inputs.freeTextCorpus).trim();
    }

    private ScoreInputs buildScoreInputs(Expense expense, String rawOcrText) {
        StringBuilder sellerBuilder = new StringBuilder();
        StringBuilder lineItemBuilder = new StringBuilder();
        StringBuilder freeTextBuilder = new StringBuilder();
        if (expense != null) {
            append(sellerBuilder, expense.getName());
            append(freeTextBuilder, expense.getComment());
            append(freeTextBuilder, expense.getCategory());
            if (expense.getLineItems() != null) {
                expense.getLineItems().stream()
                        .map(lineItem -> lineItem == null ? null : lineItem.getDescription())
                        .filter(Objects::nonNull)
                        .sorted(String::compareToIgnoreCase)
                        .forEach(text -> append(lineItemBuilder, text));
            }
        }
        append(freeTextBuilder, rawOcrText);
        return new ScoreInputs(
                sellerBuilder.toString().toLowerCase(Locale.ENGLISH),
                lineItemBuilder.toString().toLowerCase(Locale.ENGLISH),
                freeTextBuilder.toString().toLowerCase(Locale.ENGLISH)
        );
    }

    private void append(StringBuilder builder, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        builder.append(' ').append(text);
    }

    private record ScoreInputs(String sellerCorpus, String lineItemCorpus, String freeTextCorpus) {
    }
}
