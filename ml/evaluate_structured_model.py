from __future__ import annotations

import argparse
import json
from pathlib import Path

from extractor.normalization import load_jsonl, normalize_text
from extractor.predictor import load_extractor
from extractor.structured_format import HEADER_FIELDS, normalize_header_payload, normalize_multiline_text
from extractor.transformer_predictor import StructuredPredictionError, load_structured_predictor


NUMERIC_FIELDS = {"amount", "tax", "discount"}
DATE_FIELDS = {"date", "due_date"}
TEXT_FIELDS = {"name", "invoice_number", "currency", "category", "comment", "client_name"}
ADDRESS_OR_PAYMENT_FIELDS = {"seller_address", "client_address", "payment_method", "bank_name", "account_number"}
ACCURACY_FIELDS = ["amount", "tax", "discount", "currency", "date", "due_date", "name", "invoice_number", "client_name", "category"]
ACCEPTANCE_THRESHOLDS = {
    "amount_accuracy": 0.80,
    "tax_accuracy": 0.80,
    "currency_accuracy": 0.98,
    "date_accuracy": 0.95,
    "name_accuracy": 0.95,
    "valid_json_rate": 0.99,
}


def normalize_value(field: str, value):
    if field in NUMERIC_FIELDS:
        return None if value is None else float(value)
    if field in DATE_FIELDS:
        return value
    if field in ADDRESS_OR_PAYMENT_FIELDS:
        return normalize_multiline_text(value)
    return normalize_text(value)


def exact_match(field: str, expected, actual) -> bool:
    expected_value = normalize_value(field, expected)
    actual_value = normalize_value(field, actual)
    if field in NUMERIC_FIELDS:
        if expected_value is None:
            return actual_value is None
        return actual_value is not None and abs(expected_value - actual_value) <= 0.01
    return expected_value == actual_value


def empty_metric_bucket() -> dict[str, float]:
    return {"correct": 0.0, "total": 0.0}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir", required=True)
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--device", default="auto", choices=("auto", "mps", "cpu"))
    parser.add_argument("--legacy-model-dir")
    parser.add_argument("--report-file")
    args = parser.parse_args()

    predictor = load_structured_predictor(args.model_dir, device_preference=args.device)
    legacy_predictor = load_extractor(args.legacy_model_dir) if args.legacy_model_dir else None
    test_rows = load_jsonl(Path(args.splits_dir) / "test.jsonl")

    metric_counts = {field: empty_metric_bucket() for field in ACCURACY_FIELDS}
    address_precision = {
        field: {"correct_non_empty": 0.0, "predicted_non_empty": 0.0}
        for field in ADDRESS_OR_PAYMENT_FIELDS
    }
    legacy_metric_counts = {field: empty_metric_bucket() for field in ACCURACY_FIELDS} if legacy_predictor else None

    valid_json_count = 0
    total_rows = len(test_rows)
    failures = []

    for row in test_rows:
        truth = normalize_header_payload(row["output"], strict=False)
        try:
            prediction = predictor.predict(row["input"])
            valid_json_count += 1
        except StructuredPredictionError as exc:
            prediction = None
            failures.append({"id": row["id"], "error": str(exc)})

        if prediction is not None:
            prediction_map = {
                field: getattr(prediction, field)
                for field in HEADER_FIELDS
            }
            for field in ACCURACY_FIELDS:
                expected = truth.get(field)
                if expected is None:
                    continue
                metric_counts[field]["total"] += 1
                if exact_match(field, expected, prediction_map.get(field)):
                    metric_counts[field]["correct"] += 1

            for field in ADDRESS_OR_PAYMENT_FIELDS:
                predicted_value = normalize_value(field, prediction_map.get(field))
                if predicted_value:
                    address_precision[field]["predicted_non_empty"] += 1
                    if exact_match(field, truth.get(field), predicted_value):
                        address_precision[field]["correct_non_empty"] += 1

        if legacy_predictor is not None:
            legacy = legacy_predictor.predict(row["input"])
            legacy_map = {
                field: getattr(legacy, field)
                for field in HEADER_FIELDS
            }
            for field in ACCURACY_FIELDS:
                expected = truth.get(field)
                if expected is None:
                    continue
                legacy_metric_counts[field]["total"] += 1
                if exact_match(field, expected, legacy_map.get(field)):
                    legacy_metric_counts[field]["correct"] += 1

    report = {
        "test_size": total_rows,
        "valid_json_rate": ratio(valid_json_count, total_rows),
        "metrics": {
            "amount_accuracy": ratio(metric_counts["amount"]["correct"], metric_counts["amount"]["total"]),
            "tax_accuracy": ratio(metric_counts["tax"]["correct"], metric_counts["tax"]["total"]),
            "discount_accuracy": ratio(metric_counts["discount"]["correct"], metric_counts["discount"]["total"]),
            "currency_accuracy": ratio(metric_counts["currency"]["correct"], metric_counts["currency"]["total"]),
            "date_accuracy": ratio(metric_counts["date"]["correct"], metric_counts["date"]["total"]),
            "due_date_accuracy": ratio(metric_counts["due_date"]["correct"], metric_counts["due_date"]["total"]),
            "name_accuracy": ratio(metric_counts["name"]["correct"], metric_counts["name"]["total"]),
            "invoice_number_accuracy": ratio(metric_counts["invoice_number"]["correct"], metric_counts["invoice_number"]["total"]),
            "client_name_accuracy": ratio(metric_counts["client_name"]["correct"], metric_counts["client_name"]["total"]),
            "category_accuracy": ratio(metric_counts["category"]["correct"], metric_counts["category"]["total"]),
        },
        "non_empty_precision": {
            field: ratio(values["correct_non_empty"], values["predicted_non_empty"])
            for field, values in address_precision.items()
        },
        "acceptance_thresholds": ACCEPTANCE_THRESHOLDS,
        "meets_acceptance": False,
        "failures": failures[:25],
    }

    report["metrics"]["valid_json_rate"] = report["valid_json_rate"]
    report["meets_acceptance"] = all(
        report["metrics"].get(metric_name, report.get(metric_name, 0.0)) >= threshold
        for metric_name, threshold in ACCEPTANCE_THRESHOLDS.items()
    )

    if legacy_metric_counts is not None:
        report["legacy_metrics"] = {
            "amount_accuracy": ratio(legacy_metric_counts["amount"]["correct"], legacy_metric_counts["amount"]["total"]),
            "tax_accuracy": ratio(legacy_metric_counts["tax"]["correct"], legacy_metric_counts["tax"]["total"]),
            "discount_accuracy": ratio(legacy_metric_counts["discount"]["correct"], legacy_metric_counts["discount"]["total"]),
            "currency_accuracy": ratio(legacy_metric_counts["currency"]["correct"], legacy_metric_counts["currency"]["total"]),
            "date_accuracy": ratio(legacy_metric_counts["date"]["correct"], legacy_metric_counts["date"]["total"]),
            "due_date_accuracy": ratio(legacy_metric_counts["due_date"]["correct"], legacy_metric_counts["due_date"]["total"]),
            "name_accuracy": ratio(legacy_metric_counts["name"]["correct"], legacy_metric_counts["name"]["total"]),
            "invoice_number_accuracy": ratio(legacy_metric_counts["invoice_number"]["correct"], legacy_metric_counts["invoice_number"]["total"]),
            "client_name_accuracy": ratio(legacy_metric_counts["client_name"]["correct"], legacy_metric_counts["client_name"]["total"]),
            "category_accuracy": ratio(legacy_metric_counts["category"]["correct"], legacy_metric_counts["category"]["total"]),
        }

    if args.report_file:
        Path(args.report_file).write_text(json.dumps(report, indent=2), encoding="utf-8")

    print(json.dumps(report, indent=2))


def ratio(correct: float, total: float) -> float:
    if total == 0:
        return 0.0
    return round(correct / total, 4)


if __name__ == "__main__":
    main()
