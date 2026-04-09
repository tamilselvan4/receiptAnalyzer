from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score

from .normalization import load_jsonl, normalize_text
from .schema import ClassificationAlternativePayload, ClassificationResultPayload, ExtractionResult


CATEGORY_MODEL_DIRNAME = "category_classifier"
CATEGORY_MODEL_FILE = "category_classifier.joblib"
DEFAULT_EMBEDDING_MODEL = "sentence-transformers/all-MiniLM-L6-v2"


def _build_text(row: dict[str, Any]) -> str:
    output = row.get("output", {})
    parts = [
        row.get("input", ""),
        output.get("name"),
        output.get("comment"),
        output.get("category"),
        output.get("client_name"),
        output.get("seller_address"),
        " ".join(item.get("description", "") for item in output.get("line_items", []) if isinstance(item, dict)),
    ]
    return " \n ".join(str(part) for part in parts if part)


def _build_inference_text(ocr_text: str, extraction: ExtractionResult) -> str:
    parts = [
        ocr_text or "",
        extraction.name,
        extraction.comment,
        extraction.category,
        extraction.client_name,
        extraction.seller_address,
        " ".join(item.get("description", "") for item in extraction.line_items if isinstance(item, dict)),
    ]
    return " \n ".join(str(part) for part in parts if part)


def _load_sentence_transformer(model_name: str):
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError:
        return None
    return SentenceTransformer(model_name)


@dataclass
class CategoryClassifier:
    backend: str
    classifier: Any
    vectorizer: Any | None = None
    embedding_model_name: str | None = None

    @classmethod
    def train(cls, rows: list[dict[str, Any]], embedding_model: str = DEFAULT_EMBEDDING_MODEL) -> "CategoryClassifier":
        texts = [_build_text(row) for row in rows]
        labels = [normalize_text(row.get("output", {}).get("category")) or "General" for row in rows]

        encoder = _load_sentence_transformer(embedding_model)
        if encoder is not None:
            embeddings = encoder.encode(texts, normalize_embeddings=True)
            classifier = LogisticRegression(max_iter=2000, random_state=42)
            classifier.fit(embeddings, labels)
            return cls(
                backend="sentence-transformers",
                classifier=classifier,
                embedding_model_name=embedding_model,
            )

        vectorizer = TfidfVectorizer(ngram_range=(1, 2), min_df=1, max_features=25000)
        matrix = vectorizer.fit_transform(texts)
        classifier = LogisticRegression(max_iter=2000, random_state=42)
        classifier.fit(matrix, labels)
        return cls(
            backend="tfidf",
            classifier=classifier,
            vectorizer=vectorizer,
        )

    def save(self, model_dir: str | Path) -> Path:
        target = Path(model_dir) / CATEGORY_MODEL_DIRNAME
        target.mkdir(parents=True, exist_ok=True)
        payload = {
            "backend": self.backend,
            "classifier": self.classifier,
            "vectorizer": self.vectorizer,
            "embedding_model_name": self.embedding_model_name,
        }
        path = target / CATEGORY_MODEL_FILE
        joblib.dump(payload, path)
        return path

    @classmethod
    def load(cls, model_dir: str | Path) -> "CategoryClassifier":
        payload = joblib.load(resolve_category_model_path(model_dir))
        return cls(
            backend=payload["backend"],
            classifier=payload["classifier"],
            vectorizer=payload.get("vectorizer"),
            embedding_model_name=payload.get("embedding_model_name"),
        )

    def _transform(self, texts: list[str]):
        if self.backend == "sentence-transformers":
            encoder = _load_sentence_transformer(self.embedding_model_name or DEFAULT_EMBEDDING_MODEL)
            if encoder is None:
                raise RuntimeError("sentence-transformers backend requested but package is not installed.")
            return encoder.encode(texts, normalize_embeddings=True)
        return self.vectorizer.transform(texts)

    def predict(self, ocr_text: str, extraction: ExtractionResult) -> ClassificationResultPayload:
        text = _build_inference_text(ocr_text, extraction)
        features = self._transform([text])
        probabilities = self.classifier.predict_proba(features)[0]
        classes = list(self.classifier.classes_)
        ranked = sorted(zip(classes, probabilities.tolist()), key=lambda item: item[1], reverse=True)
        predicted_category, confidence = ranked[0]
        alternatives = [
            ClassificationAlternativePayload(label=label, confidence=round(score, 4))
            for label, score in ranked[1:4]
        ]
        return ClassificationResultPayload(
            predicted_category=predicted_category,
            confidence=round(confidence, 4),
            alternatives=alternatives,
            model_version="category-embed-v1" if self.backend == "sentence-transformers" else "category-tfidf-v1",
            source="local-model",
        )

    def evaluate(self, rows: list[dict[str, Any]]) -> dict[str, Any]:
        texts = [_build_text(row) for row in rows]
        y_true = [normalize_text(row.get("output", {}).get("category")) or "General" for row in rows]
        features = self._transform(texts)
        y_pred = self.classifier.predict(features)
        labels = sorted(set(y_true) | set(y_pred))
        matrix = confusion_matrix(y_true, y_pred, labels=labels)
        confusion = []
        for index, actual in enumerate(labels):
            row = matrix[index]
            top_index = int(np.argmax(row)) if row.size else 0
            confusion.append({
                "actual": actual,
                "predicted": labels[top_index],
                "count": int(row[top_index]) if row.size else 0,
            })
        return {
            "backend": self.backend,
            "model_version": "category-embed-v1" if self.backend == "sentence-transformers" else "category-tfidf-v1",
            "test_size": len(rows),
            "accuracy": round(float(accuracy_score(y_true, y_pred)), 4),
            "macro_f1": round(float(f1_score(y_true, y_pred, average="macro")), 4),
            "labels": labels,
            "confusion_matrix": matrix.tolist(),
            "top_confusions": sorted(confusion, key=lambda item: item["count"], reverse=True)[:10],
        }


def resolve_category_model_path(model_dir: str | Path) -> Path:
    candidate = Path(model_dir)
    if candidate.name == CATEGORY_MODEL_DIRNAME and (candidate / CATEGORY_MODEL_FILE).exists():
        return candidate / CATEGORY_MODEL_FILE
    nested = candidate / CATEGORY_MODEL_DIRNAME / CATEGORY_MODEL_FILE
    if nested.exists():
        return nested
    raise FileNotFoundError(f"Category classifier model not found under {candidate}")


def load_category_classifier(model_dir: str | Path) -> CategoryClassifier:
    return CategoryClassifier.load(model_dir)


def load_rows_from_split(splits_dir: str | Path, split_name: str) -> list[dict[str, Any]]:
    return load_jsonl(Path(splits_dir) / f"{split_name}.jsonl")
