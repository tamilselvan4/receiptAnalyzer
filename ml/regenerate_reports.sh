#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

PYTHON_BIN="${PYTHON_BIN:-python3}"
RAW_DIR="${RAW_DIR:-training_data/raw}"
REPORT_DIR="${REPORT_DIR:-training_data/reports}"
PROCESSED_LABELS="${PROCESSED_LABELS:-training_data/processed/canonical_labels.jsonl}"
SPLITS_DIR="${SPLITS_DIR:-training_data/processed/splits}"
MODEL_DIR="${MODEL_DIR:-training_data/model}"
OCR_DIR="${OCR_DIR:-training_data/ocr}"
STRUCTURED_REPORT="${STRUCTURED_REPORT:-training_data/reports/structured_eval_report.json}"
CATEGORY_REPORT="${CATEGORY_REPORT:-training_data/reports/category_eval_report.json}"
RISK_REPORT="${RISK_REPORT:-training_data/reports/risk_eval_report.json}"
SPOTCHECK_REPORT="${SPOTCHECK_REPORT:-training_data/reports/high_risk_spotcheck_report.json}"
RISK_FEATURE_REPORT="${RISK_FEATURE_REPORT:-training_data/reports/train_risk_features.csv}"
LEGACY_MODEL_REPORT="${LEGACY_MODEL_REPORT:-training_data/reports/local_model_eval_report.json}"

if [[ ! -d "$RAW_DIR" ]]; then
  echo "Raw dataset directory not found: $RAW_DIR" >&2
  echo "Unzip invoice batches under training_data/raw before regenerating reports." >&2
  exit 1
fi

echo "[1/8] Auditing dataset"
"$PYTHON_BIN" audit_dataset.py --raw-dir "$RAW_DIR" --report-dir "$REPORT_DIR"

echo "[2/8] Normalizing labels"
"$PYTHON_BIN" normalize_labels.py --raw-dir "$RAW_DIR" --output "$PROCESSED_LABELS"

echo "[3/8] Generating OCR artifacts"
"$PYTHON_BIN" generate_ocr.py --labels "$PROCESSED_LABELS" --output-dir "$OCR_DIR"

echo "[4/8] Rebuilding train/validation/test splits"
"$PYTHON_BIN" build_splits.py --labels "$PROCESSED_LABELS" --ocr-dir "$OCR_DIR" --output-dir "$SPLITS_DIR"

echo "[5/8] Training legacy extraction model, category classifier, and risk features"
"$PYTHON_BIN" train_model.py --splits-dir "$SPLITS_DIR" --model-dir "$MODEL_DIR"
"$PYTHON_BIN" evaluate_model.py --splits-dir "$SPLITS_DIR" --model-dir "$MODEL_DIR" > "$LEGACY_MODEL_REPORT"
"$PYTHON_BIN" train_category_classifier.py --splits-dir "$SPLITS_DIR" --model-dir "$MODEL_DIR"
"$PYTHON_BIN" evaluate_category_classifier.py --splits-dir "$SPLITS_DIR" --model-dir "$MODEL_DIR" --report-file "$CATEGORY_REPORT"
"$PYTHON_BIN" build_risk_features.py --splits-dir "$SPLITS_DIR" --split train --output-file "$RISK_FEATURE_REPORT"

echo "[6/8] Training and evaluating risk model"
"$PYTHON_BIN" train_risk_model.py --splits-dir "$SPLITS_DIR" --model-dir "$MODEL_DIR"
"$PYTHON_BIN" evaluate_risk_model.py --splits-dir "$SPLITS_DIR" --model-dir "$MODEL_DIR" --report-file "$RISK_REPORT"
"$PYTHON_BIN" spotcheck_high_risk_cases.py --splits-dir "$SPLITS_DIR" --model-dir "$MODEL_DIR" --output-file "$SPOTCHECK_REPORT" --limit 10

if [[ -d "$MODEL_DIR/structured_header_model" && -f "$MODEL_DIR/model_bundle.joblib" ]]; then
  echo "[7/8] Running structured evaluation"
  "$PYTHON_BIN" evaluate_structured_model.py \
    --splits-dir "$SPLITS_DIR" \
    --model-dir "$MODEL_DIR/structured_header_model" \
    --legacy-model-dir "$MODEL_DIR" \
    --report-file "$STRUCTURED_REPORT"
else
  echo "[7/8] Skipping structured evaluation because model artifacts are incomplete."
  echo "Expected $MODEL_DIR/structured_header_model and $MODEL_DIR/model_bundle.joblib" >&2
fi

echo "[8/8] Report regeneration complete."
echo "Report regeneration complete."
