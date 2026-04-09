from __future__ import annotations

import csv
import json
import random
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any

from .normalization import (
    canonicalize_row,
    infer_column_mapping,
    load_jsonl,
    looks_like_line_items,
    slug,
    summarize_columns,
    write_jsonl,
)
from .ocr import extract_ocr_text
from .predictor import InvoiceExtractor, load_extractor


IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg"}


def _find_csv_files(raw_dir: Path) -> list[Path]:
    return sorted(path for path in raw_dir.rglob("*.csv") if path.is_file())


def _find_images(root: Path) -> dict[str, list[Path]]:
    index: dict[str, list[Path]] = defaultdict(list)
    for image in root.rglob("*"):
        if image.suffix.lower() in IMAGE_EXTENSIONS and image.is_file():
            index[image.stem.lower()].append(image)
    return index


def _read_csv(path: Path) -> list[dict[str, Any]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        return list(reader)


def _match_image_for_row(row: dict[str, Any], mapping: dict[str, str], image_index: dict[str, list[Path]]) -> str | None:
    image_value = row.get(mapping.get("source_image", ""), "")
    if image_value:
        candidate = Path(str(image_value)).stem.lower()
        matches = image_index.get(candidate)
        if matches:
            return str(matches[0])

    values = [str(value) for value in row.values() if value]
    for value in values:
        candidate = Path(value).stem.lower()
        matches = image_index.get(candidate)
        if matches:
            return str(matches[0])
    return None


def audit_dataset(raw_dir: str | Path, report_dir: str | Path) -> dict[str, Any]:
    raw_root = Path(raw_dir)
    report_root = Path(report_dir)
    report_root.mkdir(parents=True, exist_ok=True)
    image_index = _find_images(raw_root)
    csv_files = _find_csv_files(raw_root)

    csv_reports = []
    global_columns = Counter()
    matched_pairs = 0
    total_rows = 0
    unmatched_rows = []

    for csv_file in csv_files:
        rows = _read_csv(csv_file)
        columns = list(rows[0].keys()) if rows else []
        mapping = infer_column_mapping(columns)
        null_counts = {column: 0 for column in columns}
        for row in rows:
            total_rows += 1
            for column in columns:
                if row.get(column) in (None, ""):
                    null_counts[column] += 1
            matched_image = _match_image_for_row(row, mapping, image_index)
            if matched_image:
                matched_pairs += 1
            else:
                unmatched_rows.append({"csv": str(csv_file), "row": row})

        for column in columns:
            global_columns[slug(column)] += 1

        csv_reports.append({
            "csv_file": str(csv_file),
            "row_count": len(rows),
            "columns": columns,
            "column_mapping": summarize_columns(columns),
            "null_frequency": {
                column: (null_counts[column] / len(rows) if rows else 0.0)
                for column in columns
            },
            "looks_like_line_items": looks_like_line_items(columns),
        })

    report = {
        "csv_count": len(csv_files),
        "image_count": sum(len(paths) for paths in image_index.values()),
        "row_count": total_rows,
        "matched_pairs": matched_pairs,
        "unmatched_row_count": len(unmatched_rows),
        "schema_frequency": global_columns.most_common(),
        "csv_reports": csv_reports,
        "top_candidate_fields": [item["canonical_field"] for item in summarize_columns([column for column, _ in global_columns.most_common()])],
        "line_item_batches": [item["csv_file"] for item in csv_reports if item["looks_like_line_items"]],
    }

    (report_root / "audit_report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    if unmatched_rows:
        (report_root / "unmatched_rows.json").write_text(json.dumps(unmatched_rows[:500], indent=2), encoding="utf-8")
    return report


def normalize_labels(raw_dir: str | Path, output: str | Path) -> list[dict[str, Any]]:
    raw_root = Path(raw_dir)
    image_index = _find_images(raw_root)
    rows_out: list[dict[str, Any]] = []

    for csv_file in _find_csv_files(raw_root):
        rows = _read_csv(csv_file)
        columns = list(rows[0].keys()) if rows else []
        mapping = infer_column_mapping(columns)
        for row in rows:
            matched_image = _match_image_for_row(row, mapping, image_index)
            canonical = canonicalize_row(row, mapping, matched_image)
            rows_out.append(canonical.to_dict())

    write_jsonl(Path(output), rows_out)
    return rows_out


def generate_ocr(labels_path: str | Path, output_dir: str | Path) -> list[dict[str, str]]:
    labels = load_jsonl(Path(labels_path))
    output_root = Path(output_dir)
    output_root.mkdir(parents=True, exist_ok=True)
    generated: list[dict[str, str]] = []

    for row in labels:
        source_image = row.get("source_image")
        if not source_image:
            continue
        source_path = Path(source_image)
        ocr_text = extract_ocr_text(source_path)
        target = output_root / f"{source_path.stem}.txt"
        target.write_text(ocr_text, encoding="utf-8")
        generated.append({"source_image": source_image, "ocr_path": str(target)})

    return generated


def build_splits(labels_path: str | Path, ocr_dir: str | Path, output_dir: str | Path, seed: int = 42) -> dict[str, int]:
    labels = load_jsonl(Path(labels_path))
    rows = []
    for row in labels:
        source_image = row.get("source_image")
        if not source_image:
            continue
        stem = Path(source_image).stem
        input_text = row.get("ocr_text")
        if not input_text:
            ocr_path = Path(ocr_dir) / f"{stem}.txt"
            if not ocr_path.exists():
                continue
            input_text = ocr_path.read_text(encoding="utf-8")
        rows.append({
            "id": stem,
            "input": input_text,
            "output": {
                "name": row.get("name"),
                "invoice_number": row.get("invoice_number"),
                "amount": row.get("amount"),
                "tax": row.get("tax"),
                "discount": row.get("discount"),
                "currency": row.get("currency"),
                "date": row.get("date"),
                "due_date": row.get("due_date"),
                "category": row.get("category"),
                "comment": row.get("comment"),
                "seller_address": row.get("seller_address"),
                "client_name": row.get("client_name"),
                "client_address": row.get("client_address"),
                "payment_method": row.get("payment_method"),
                "bank_name": row.get("bank_name"),
                "account_number": row.get("account_number"),
                "line_items": row.get("line_items") or [],
            },
            "source_image": source_image,
        })

    random.Random(seed).shuffle(rows)
    total = len(rows)
    train_end = int(total * 0.8)
    val_end = int(total * 0.9)
    splits = {
        "train": rows[:train_end],
        "validation": rows[train_end:val_end],
        "test": rows[val_end:],
    }
    output_root = Path(output_dir)
    output_root.mkdir(parents=True, exist_ok=True)
    for split_name, split_rows in splits.items():
        write_jsonl(output_root / f"{split_name}.jsonl", split_rows)
    return {name: len(value) for name, value in splits.items()}


def train_model(splits_dir: str | Path, model_dir: str | Path) -> Path:
    train_rows = load_jsonl(Path(splits_dir) / "train.jsonl")
    extractor = InvoiceExtractor.train(train_rows)
    return extractor.save(model_dir)


def evaluate_model(splits_dir: str | Path, model_dir: str | Path) -> dict[str, Any]:
    model = load_extractor(model_dir)
    test_rows = load_jsonl(Path(splits_dir) / "test.jsonl")
    metrics: dict[str, dict[str, float]] = {
        "amount": {"correct": 0, "total": 0},
        "tax": {"correct": 0, "total": 0},
        "currency": {"correct": 0, "total": 0},
        "date": {"correct": 0, "total": 0},
        "name": {"correct": 0, "total": 0},
        "category": {"correct": 0, "total": 0},
        "comment": {"non_empty": 0, "total": 0},
    }

    for row in test_rows:
        prediction = model.predict(row["input"])
        truth = row["output"]

        for field in ("amount", "tax"):
            expected = truth.get(field)
            actual = getattr(prediction, field)
            if expected is not None:
                metrics[field]["total"] += 1
                if actual is not None and abs(float(expected) - float(actual)) <= 0.01:
                    metrics[field]["correct"] += 1

        for field in ("currency", "date", "name", "category"):
            expected = truth.get(field)
            actual = getattr(prediction, field)
            if expected:
                metrics[field]["total"] += 1
                if str(expected).strip().lower() == str(actual).strip().lower():
                    metrics[field]["correct"] += 1

        metrics["comment"]["total"] += 1
        if prediction.comment:
            metrics["comment"]["non_empty"] += 1

    return {
        "test_size": len(test_rows),
        "metrics": {
            "amount_accuracy": _ratio(metrics["amount"]["correct"], metrics["amount"]["total"]),
            "tax_accuracy": _ratio(metrics["tax"]["correct"], metrics["tax"]["total"]),
            "currency_accuracy": _ratio(metrics["currency"]["correct"], metrics["currency"]["total"]),
            "date_accuracy": _ratio(metrics["date"]["correct"], metrics["date"]["total"]),
            "name_accuracy": _ratio(metrics["name"]["correct"], metrics["name"]["total"]),
            "category_accuracy": _ratio(metrics["category"]["correct"], metrics["category"]["total"]),
            "comment_non_empty_rate": _ratio(metrics["comment"]["non_empty"], metrics["comment"]["total"]),
        },
    }


def _ratio(correct: float, total: float) -> float:
    if total == 0:
        return 0.0
    return round(correct / total, 4)
