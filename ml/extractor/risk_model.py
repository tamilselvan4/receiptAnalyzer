from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

from .normalization import load_jsonl, normalize_text
from .schema import ClassificationResultPayload, ExtractionResult, RiskPriorPayload


RISK_MODEL_DIRNAME = "risk_model"
RISK_MODEL_FILE = "risk_model.joblib"
RISK_MODEL_VERSION = "risk-iforest-v1"
FEATURE_NAMES = [
    "amount",
    "tax",
    "discount",
    "tax_ratio",
    "line_item_count",
    "average_line_item_price",
    "max_line_item_price",
    "ocr_text_length",
    "missing_field_count",
    "vendor_name_quality",
    "invoice_number_quality",
    "classification_confidence",
]


def _safe_float(value: Any) -> float:
    if value is None:
        return 0.0
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


def _line_item_prices(line_items: list[dict[str, Any]]) -> list[float]:
    prices = []
    for item in line_items or []:
        if not isinstance(item, dict):
            continue
        price = _safe_float(item.get("total_price"))
        if price >= 0:
            prices.append(price)
    return prices


def _missing_field_count(payload: dict[str, Any]) -> int:
    fields = ["name", "invoice_number", "amount", "date", "category"]
    return sum(1 for field in fields if not payload.get(field))


def _quality_flag(value: Any) -> float:
    text = normalize_text(value)
    if not text:
        return 0.0
    return 1.0 if len(text) >= 3 else 0.5


def feature_dict_from_payload(ocr_text: str, payload: dict[str, Any], classification_confidence: float = 0.5) -> dict[str, float]:
    amount = _safe_float(payload.get("amount"))
    tax = _safe_float(payload.get("tax"))
    discount = _safe_float(payload.get("discount"))
    prices = _line_item_prices(payload.get("line_items") or [])
    average_price = float(np.mean(prices)) if prices else 0.0
    max_price = float(max(prices)) if prices else 0.0
    tax_ratio = (tax / amount) if amount > 0 else 0.0
    return {
        "amount": amount,
        "tax": tax,
        "discount": discount,
        "tax_ratio": tax_ratio,
        "line_item_count": float(len(prices)),
        "average_line_item_price": average_price,
        "max_line_item_price": max_price,
        "ocr_text_length": float(len(ocr_text or "")),
        "missing_field_count": float(_missing_field_count(payload)),
        "vendor_name_quality": _quality_flag(payload.get("name")),
        "invoice_number_quality": _quality_flag(payload.get("invoice_number")),
        "classification_confidence": classification_confidence,
    }


def features_from_row(row: dict[str, Any]) -> dict[str, float]:
    payload = row.get("output", {})
    return feature_dict_from_payload(row.get("input", ""), payload, classification_confidence=1.0)


def features_from_extraction(ocr_text: str,
                             extraction: ExtractionResult,
                             classification: ClassificationResultPayload | None) -> dict[str, float]:
    payload = {
        "name": extraction.name,
        "invoice_number": extraction.invoice_number,
        "amount": extraction.amount,
        "tax": extraction.tax,
        "discount": extraction.discount,
        "line_items": extraction.line_items,
        "date": extraction.date,
        "category": classification.predicted_category if classification else extraction.category,
    }
    confidence = classification.confidence if classification and classification.confidence is not None else 0.5
    return feature_dict_from_payload(ocr_text, payload, classification_confidence=confidence)


def heuristic_flags(payload: dict[str, Any]) -> list[str]:
    flags: list[str] = []
    amount = _safe_float(payload.get("amount"))
    tax = _safe_float(payload.get("tax"))
    discount = _safe_float(payload.get("discount"))
    prices = _line_item_prices(payload.get("line_items") or [])
    if amount <= 0:
        flags.append("non_positive_amount")
    if tax < 0 or discount < 0:
        flags.append("negative_financial_field")
    if prices and abs(sum(prices) + tax - discount - amount) > 2.0:
        flags.append("reconciliation_gap")
    if _missing_field_count(payload) >= 2:
        flags.append("missing_critical_fields")
    if amount > 100000:
        flags.append("high_amount")
    return flags


@dataclass
class RiskModel:
    scaler: StandardScaler
    model: IsolationForest
    score_min: float
    score_max: float

    @classmethod
    def train(cls, rows: list[dict[str, Any]]) -> "RiskModel":
        matrix = np.array([[features_from_row(row)[name] for name in FEATURE_NAMES] for row in rows], dtype=float)
        scaler = StandardScaler()
        scaled = scaler.fit_transform(matrix)
        model = IsolationForest(n_estimators=250, contamination=0.12, random_state=42)
        model.fit(scaled)
        scores = -model.score_samples(scaled)
        return cls(
            scaler=scaler,
            model=model,
            score_min=float(scores.min()),
            score_max=float(scores.max()),
        )

    def save(self, model_dir: str | Path) -> Path:
        target = Path(model_dir) / RISK_MODEL_DIRNAME
        target.mkdir(parents=True, exist_ok=True)
        path = target / RISK_MODEL_FILE
        joblib.dump(
            {
                "scaler": self.scaler,
                "model": self.model,
                "score_min": self.score_min,
                "score_max": self.score_max,
            },
            path,
        )
        return path

    @classmethod
    def load(cls, model_dir: str | Path) -> "RiskModel":
        payload = joblib.load(resolve_risk_model_path(model_dir))
        return cls(
            scaler=payload["scaler"],
            model=payload["model"],
            score_min=payload["score_min"],
            score_max=payload["score_max"],
        )

    def predict(self,
                ocr_text: str,
                extraction: ExtractionResult,
                classification: ClassificationResultPayload | None) -> RiskPriorPayload:
        features = features_from_extraction(ocr_text, extraction, classification)
        matrix = np.array([[features[name] for name in FEATURE_NAMES]], dtype=float)
        scaled = self.scaler.transform(matrix)
        raw_score = float(-self.model.score_samples(scaled)[0])
        anomaly_score = normalize_score(raw_score, self.score_min, self.score_max)
        flags = heuristic_flags({
            "name": extraction.name,
            "invoice_number": extraction.invoice_number,
            "amount": extraction.amount,
            "tax": extraction.tax,
            "discount": extraction.discount,
            "line_items": extraction.line_items,
            "date": extraction.date,
            "category": classification.predicted_category if classification else extraction.category,
        })
        return RiskPriorPayload(
            anomaly_score=round(anomaly_score, 4),
            model_version=RISK_MODEL_VERSION,
            feature_flags=flags[:5],
        )

    def evaluate(self, rows: list[dict[str, Any]]) -> dict[str, Any]:
        scored_rows = []
        overlap = 0
        for row in rows:
            payload = row.get("output", {})
            features = features_from_row(row)
            matrix = np.array([[features[name] for name in FEATURE_NAMES]], dtype=float)
            scaled = self.scaler.transform(matrix)
            raw_score = float(-self.model.score_samples(scaled)[0])
            normalized = normalize_score(raw_score, self.score_min, self.score_max)
            flags = heuristic_flags(payload)
            if normalized >= 0.70 and flags:
                overlap += 1
            scored_rows.append({
                "id": row.get("id"),
                "anomaly_score": round(normalized, 4),
                "heuristic_flags": flags,
                "amount": payload.get("amount"),
                "category": payload.get("category"),
                "name": payload.get("name"),
            })

        scored_rows.sort(key=lambda item: item["anomaly_score"], reverse=True)
        scores = [row["anomaly_score"] for row in scored_rows]
        return {
            "model_version": RISK_MODEL_VERSION,
            "test_size": len(rows),
            "score_distribution": {
                "min": round(min(scores), 4) if scores else 0.0,
                "max": round(max(scores), 4) if scores else 0.0,
                "mean": round(float(np.mean(scores)), 4) if scores else 0.0,
                "median": round(float(np.median(scores)), 4) if scores else 0.0,
            },
            "high_risk_threshold": 0.70,
            "high_risk_count": sum(1 for score in scores if score >= 0.70),
            "overlap_with_heuristic_flags": overlap,
            "top_risky_cases": scored_rows[:25],
        }


def normalize_score(raw_score: float, score_min: float, score_max: float) -> float:
    if score_max <= score_min:
        return 0.5
    normalized = (raw_score - score_min) / (score_max - score_min)
    return max(0.0, min(1.0, normalized))


def resolve_risk_model_path(model_dir: str | Path) -> Path:
    candidate = Path(model_dir)
    if candidate.name == RISK_MODEL_DIRNAME and (candidate / RISK_MODEL_FILE).exists():
        return candidate / RISK_MODEL_FILE
    nested = candidate / RISK_MODEL_DIRNAME / RISK_MODEL_FILE
    if nested.exists():
        return nested
    raise FileNotFoundError(f"Risk model not found under {candidate}")


def load_risk_model(model_dir: str | Path) -> RiskModel:
    return RiskModel.load(model_dir)


def load_rows_from_split(splits_dir: str | Path, split_name: str) -> list[dict[str, Any]]:
    return load_jsonl(Path(splits_dir) / f"{split_name}.jsonl")
