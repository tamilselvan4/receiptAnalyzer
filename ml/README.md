# Invoice Extraction ML Workspace

This directory contains the machine-learning and evaluation side of the project. It supports the academic claim that the system is not only a CRUD application, but a hybrid invoice-understanding pipeline with reproducible preprocessing and measurable extraction behavior.

## Scope

- dataset audit and normalization
- OCR text generation
- train/validation/test split creation
- legacy local extraction model
- supervised category classification
- unsupervised anomaly/risk scoring
- structured header model training and evaluation
- Flask-based local inference service used by the Spring Boot app

## Expected dataset layout

Unzip each dataset batch under `training_data/raw/` so that every batch has one CSV and one folder of source images:

```text
training_data/raw/
  batch1_1/
    invoice_001.png
    invoice_002.png
  batch1_1.csv
  batch1_2/
  batch1_2.csv
```

## Setup

```bash
cd ml
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

`pytesseract` also requires the system `tesseract` binary.

## End-to-end workflow

```bash
python ingest_zip.py --zip-file /path/to/invoices.zip --output-dir training_data/raw
python audit_dataset.py --raw-dir training_data/raw --report-dir training_data/reports
python normalize_labels.py --raw-dir training_data/raw --output training_data/processed/canonical_labels.jsonl
python generate_ocr.py --labels training_data/processed/canonical_labels.jsonl --output-dir training_data/ocr
python build_splits.py --labels training_data/processed/canonical_labels.jsonl --ocr-dir training_data/ocr --output-dir training_data/processed/splits
python train_model.py --splits-dir training_data/processed/splits --model-dir training_data/model
python evaluate_model.py --splits-dir training_data/processed/splits --model-dir training_data/model
python train_category_classifier.py --splits-dir training_data/processed/splits --model-dir training_data/model
python evaluate_category_classifier.py --splits-dir training_data/processed/splits --model-dir training_data/model --report-file training_data/reports/category_eval_report.json
python build_risk_features.py --splits-dir training_data/processed/splits --split train --output-file training_data/reports/train_risk_features.csv
python train_risk_model.py --splits-dir training_data/processed/splits --model-dir training_data/model
python evaluate_risk_model.py --splits-dir training_data/processed/splits --model-dir training_data/model --report-file training_data/reports/risk_eval_report.json
python spotcheck_high_risk_cases.py --splits-dir training_data/processed/splits --model-dir training_data/model --output-file training_data/reports/high_risk_spotcheck_report.json --limit 10
python train_structured_model.py --splits-dir training_data/processed/splits --model-dir training_data/model
python evaluate_structured_model.py --splits-dir training_data/processed/splits --model-dir training_data/model/structured_header_model --legacy-model-dir training_data/model --report-file training_data/reports/structured_eval_report.json
python serve_model.py --model-dir training_data/model
python serve_model.py --model-dir training_data/model --mode structured
```

For a repeatable refresh of the audit, preprocessing, split generation, and structured evaluation outputs, run:

```bash
./regenerate_reports.sh
```

## Presentation-ready summary

- The production application uses the local extraction service first.
- Category classification is supervised using the existing `category` labels in the invoice dataset.
- Risk scoring is unsupervised: an Isolation Forest prior is blended with backend rule signals and historical checks.
- The structured header model is still under evaluation and does not replace the legacy path by default.
- The repository does not claim final acceptance for the structured model yet.

The current checked-in report at [`training_data/reports/structured_eval_report.json`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/structured_eval_report.json) explicitly states that a full rerun is pending after the parser fix. The spot-check report at [`training_data/reports/structured_spotcheck_report.json`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/structured_spotcheck_report.json) can be cited as exploratory evidence only, not as a final benchmark claim.

## Current evidence in the repository

- dataset audit report: [`training_data/reports/audit_report.json`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/audit_report.json)
- category classification report: [`training_data/reports/category_eval_report.json`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/category_eval_report.json)
- anomaly/risk evaluation report: [`training_data/reports/risk_eval_report.json`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/risk_eval_report.json)
- high-risk spot-check report: [`training_data/reports/high_risk_spotcheck_report.json`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/high_risk_spotcheck_report.json)
- pending structured evaluation marker: [`training_data/reports/structured_eval_report.json`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/structured_eval_report.json)
- preserved pre-parser-fix report: [`training_data/reports/structured_eval_report_pre_parser_fix.json`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/structured_eval_report_pre_parser_fix.json)
- structured spot-check comparison: [`training_data/reports/structured_spotcheck_report.json`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/training_data/reports/structured_spotcheck_report.json)

## Local inference contract

`POST /extract-image` with multipart file returns a JSON payload such as:

```json
{
  "name": "Vendor name",
  "amount": 123.45,
  "tax": 10.0,
  "currency": "INR",
  "date": "2024-01-15",
  "category": "Office Supplies",
  "comment": "Invoice INV-12345",
  "confidence": {
    "name": 0.98,
    "amount": 0.92
  },
  "raw_ocr_text": "optional OCR text",
  "classification": {
    "predicted_category": "Office Supplies",
    "confidence": 0.91,
    "alternatives": [
      {"label": "Services", "confidence": 0.05},
      {"label": "General", "confidence": 0.04}
    ],
    "model_version": "category-tfidf-v1"
  },
  "risk_prior": {
    "anomaly_score": 0.37,
    "model_version": "risk-iforest-v1",
    "feature_flags": [
      "high_amount",
      "low_header_confidence"
    ]
  },
  "extra_fields": {
    "invoice_number": "INV-12345"
  }
}
```

The added `classification` and `risk_prior` blocks are optional. The Java backend will fall back to extractor-driven category hints and rules-only risk assessment when these model outputs are unavailable.

## Current benchmark snapshot

The current checked-in reports support these conservative claims:

- category classifier backend: `tfidf`
- category test accuracy: `0.7887`
- category macro F1: `0.4470`
- anomaly model: `IsolationForest`
- anomaly high-risk count on the checked-in test split: `7`
- anomaly overlap with heuristic validation flags on the checked-in test split: `7`

These numbers should be cited as repository evidence for the present dataset and split, not as generalized production guarantees.

## Experimental components

Prototype anomaly-detection and memory-RAG utilities were moved to [`experimental`](/Users/tamilselvans/M.E/tamil/project/expensetracker/ml/experimental). They are not part of the main application flow and should be described as exploratory work only.

## Methodology boundaries

- Supervised:
  - expense category classification
- Unsupervised / hybrid:
  - anomaly prior and review prioritization
- Exploratory:
  - structured transformer header extractor until a clean full rerun is completed

The project should describe the anomaly module as review prioritization, not as labeled fraud detection.
