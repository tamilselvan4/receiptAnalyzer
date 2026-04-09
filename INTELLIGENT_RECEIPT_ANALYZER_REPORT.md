# Intelligent Receipt Analyzer
## with Semantic Classification and Intelligent Anomaly Review

## Abstract

This project implements an intelligent receipt and invoice understanding system that combines OCR, structured field extraction, semantic expense classification, explainable anomaly scoring, manual review, and persistence in a unified workflow. The system uses a Spring Boot backend with a Vaadin interface, a Python-based local extraction service, and an optional external fallback for sparse local results. Instead of presenting fraud detection as a fully supervised prediction task, the project frames suspicious-document handling as AI-assisted anomaly scoring and review prioritization.

The current implementation now includes two explicit AI modules beyond extraction: a supervised expense category classifier and a hybrid risk engine. The category classifier predicts the most likely expense class with confidence and ranked alternatives, and the review UI maps that signal into a fixed business-category dropdown with automatic preselection and a manual `Other` option. The risk engine combines an Isolation Forest anomaly prior with explainable business checks such as duplicate invoice detection, reconciliation mismatch, missing critical fields, and amount outliers relative to historical records. This makes the project academically stronger than a basic expense tracker while remaining honest about the lack of labeled fraud data.

## 1. Problem Statement

Manual invoice and receipt processing is slow, error-prone, and difficult to standardize. OCR alone is insufficient because financial documents vary widely in layout and often contain noisy scans, missing metadata, inconsistent totals, and ambiguous categories. A practical solution must therefore combine text extraction, structured understanding, semantic classification, and risk-aware review support.

This project addresses that problem with a human-in-the-loop invoice intelligence system that:

- extracts structured accounting fields from uploaded documents,
- classifies expenses into business categories,
- assigns explainable risk scores for suspicious or inconsistent records,
- highlights high-priority documents for manual review,
- and stores the results for downstream reporting.

## 2. Objectives

The main objectives are:

1. Automate invoice and receipt understanding from uploaded files.
2. Extract meaningful structured fields from OCR-driven text.
3. Add explicit semantic expense classification with measurable outputs.
4. Add AI-assisted anomaly scoring without over-claiming labeled fraud detection.
5. Support human review through explainable signals and editable UI flows.
6. Provide reproducible ML training and evaluation workflows for academic reporting.

## 3. System Architecture

The implemented workflow is:

1. A user uploads an invoice or receipt through the Vaadin interface.
2. The Java backend sends the file to the local Python extraction service.
3. The local service returns structured fields and, when models are available, category and anomaly-prior outputs.
4. If the local response is too sparse and external fallback is enabled, OCR text is generated and sent to Gemini for structured extraction.
5. The backend validates the extracted result, predicts category when needed, computes historical context features, and blends those signals into a final risk assessment.
6. The UI displays extracted fields, a fixed dropdown of common categories, the suggested category, classification confidence, risk score, risk band, and top review signals.
7. The reviewed expense is saved to MySQL together with the AI assessment fields.

### 3.1 Architecture summary

| Module | Method | Input | Output | Evidence |
| --- | --- | --- | --- | --- |
| OCR and extraction | Local Flask service with OCR-backed predictors | Uploaded image/PDF | Structured invoice fields | [ml/serve_model.py](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/serve_model.py) |
| External fallback | Gemini JSON extraction over OCR text | OCR text | Structured fallback fields | [AgenticRagService.java](/Users/tamilselvans/M.E/tamil/project/expensetracker/src/main/java/com/expensetracker/service/AgenticRagService.java) |
| Category classification | Supervised classifier with semantic backend when available and TF-IDF fallback baseline | OCR text + vendor/comment/line-item text | Category, confidence, alternatives | [ExpenseClassificationService.java](/Users/tamilselvans/M.E/tamil/project/expensetracker/src/main/java/com/expensetracker/service/ExpenseClassificationService.java) and [ml/extractor/category_classifier.py](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/extractor/category_classifier.py) |
| Risk prior | Isolation Forest anomaly model | Structured numeric and quality features | Anomaly score | [ml/extractor/risk_model.py](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/extractor/risk_model.py) |
| Explainable risk assessment | Hybrid fusion of ML prior and business rules | Extraction, validation, historical features | Risk score, band, explanation signals | [RiskAssessmentService.java](/Users/tamilselvans/M.E/tamil/project/expensetracker/src/main/java/com/expensetracker/service/RiskAssessmentService.java) |
| Dashboard | Vaadin report tab | Persisted expenses + AI assessments | Risk queue, category summary, distributions | [HomeView.java](/Users/tamilselvans/M.E/tamil/project/expensetracker/src/main/java/com/expensetracker/frontend/HomeView.java) |

## 4. Implemented AI Modules

### 4.1 Semantic expense classification

The system now exposes expense classification as a first-class output instead of leaving category handling implicit inside extraction. For each processed document, the app can show:

- predicted category,
- confidence score,
- top alternative classes,
- and model version/source.

For usability and consistency, the editable form no longer relies on free-text category entry alone. It uses a hardcoded business-category dropdown and preselects the most relevant category from invoice content, while still allowing a custom `Other` entry when the fixed list is insufficient.

The Python workspace supports training and evaluating a category classifier. When optional sentence-embedding dependencies are available, the model can use semantic embeddings. In the checked-in environment, the current benchmark falls back to a TF-IDF baseline, which keeps the implementation reproducible without exaggerating model sophistication.

### 4.2 Hybrid anomaly scoring

The project does not claim supervised fraud accuracy because no labeled fraud dataset is available. Instead, it implements an explainable anomaly-review pipeline:

- an `IsolationForest` model produces an anomaly prior from structured invoice features,
- Java-side validation contributes reconciliation, amount, and completeness signals,
- historical lookups add duplicate-invoice and category-outlier context,
- the final risk score is blended and converted into `LOW`, `MEDIUM`, or `HIGH`.

This framing is academically safer and technically stronger than calling the current system a finished fraud detector.

### 4.3 Human-in-the-loop review prioritization

The upload and review screen now surfaces:

- predicted category,
- preselected dropdown category for user confirmation,
- classification confidence,
- risk score,
- risk band,
- top explanation signals,
- and review recommendation status.

This supports a clear viva argument: the system performs AI-assisted triage rather than blind automation.

## 5. Data and ML Workspace

The `ml` workspace provides a reproducible research pipeline for:

- dataset audit,
- canonical label normalization,
- OCR generation,
- train/validation/test split creation,
- legacy extraction model training,
- category-classifier training and evaluation,
- risk-model training and evaluation,
- high-risk case spot-check reporting,
- and local Flask inference serving.

The main regeneration entry point is:

```bash
cd ml
./regenerate_reports.sh
```

This script rebuilds the checked-in benchmark artifacts used in the project documentation.

## 6. Current Repository Evidence

The current repository supports the following conservative evidence:

- dataset audit confirms a non-trivial labeled invoice dataset,
- category classifier report shows accuracy `0.7887` and macro F1 `0.4470`,
- anomaly report records `7` high-risk cases on the current test split,
- the same anomaly report records `7` overlapping heuristic-review cases,
- high-risk spot-check cases are preserved for qualitative discussion,
- structured header extraction remains exploratory because the full rerun is still pending after a parser fix.

These claims are supported by:

- [category_eval_report.json](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/category_eval_report.json)
- [risk_eval_report.json](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/risk_eval_report.json)
- [high_risk_spotcheck_report.json](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/high_risk_spotcheck_report.json)
- [structured_eval_report.json](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/structured_eval_report.json)

## 7. Strengths

The strongest aspects of the project are:

- local-first hybrid extraction instead of full dependence on an external API,
- explicit semantic classification output,
- explainable hybrid anomaly scoring,
- persistence of AI assessment fields for dashboards and reporting,
- human-in-the-loop correction instead of unsafe full automation,
- reproducible ML scripts and evaluation artifacts,
- and a visible demo path through the report dashboard and upload-review flow.

## 8. Limitations

The project should still state these limitations clearly:

1. Fraud detection is not trained on labeled fraud examples, so fraud accuracy is not claimed.
2. The anomaly model is an unsupervised prior and should be described as review prioritization.
3. OCR quality still depends on document quality and Tesseract configuration.
4. The structured transformer extractor is still exploratory pending a clean full rerun.
5. The present category benchmark is based on the checked-in dataset split and should not be generalized beyond it.
6. The expanded UI category dropdown is broader than the currently benchmarked seven-label classifier, so some business-facing categories are recommended via transparent rules rather than newly supervised training.
7. Workflow features such as approval routing, authentication, and audit trails are outside the current scope.

## 9. Viva Positioning

The safest and strongest viva wording is:

- “The project performs semantic expense classification.”
- “The project uses hybrid anomaly scoring for suspicious-document review.”
- “The project provides explainable risk assessment and manual review prioritization.”
- “The fraud-related component is framed as AI-assisted anomaly detection, not a labeled fraud classifier.”

Phrases to avoid:

- “high-accuracy fraud detection”
- “production-grade fraud prevention”
- “state-of-the-art fraud classifier”
- “deep learning fraud detector” without corresponding labeled evaluation

## 10. Conclusion

This project now stands as more than an expense tracker or OCR demo. It combines invoice extraction, semantic classification, hybrid anomaly scoring, explainable review support, and persistence into a coherent academic prototype. The result is strong enough to defend alongside more AI-labeled student projects, provided it is presented honestly as an intelligent invoice-understanding and review-prioritization system rather than a fully validated fraud-detection platform.
