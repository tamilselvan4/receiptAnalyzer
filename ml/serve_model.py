from __future__ import annotations

import argparse
import tempfile
from pathlib import Path

from flask import Flask, jsonify, request

from extractor.category_classifier import load_category_classifier
from extractor.ocr import extract_ocr_text
from extractor.predictor import load_extractor
from extractor.risk_model import load_risk_model
from extractor.schema import ClassificationResultPayload, ExtractionResult
from extractor.transformer_predictor import StructuredPredictionError, load_structured_predictor


def build_app(model_dir: str | Path, mode: str = "legacy", device_preference: str = "auto") -> Flask:
    app = Flask(__name__)
    legacy_extractor = load_extractor(model_dir)
    structured_predictor = None
    structured_load_error = None
    category_classifier = None
    category_load_error = None
    risk_model = None
    risk_load_error = None

    if mode == "structured":
        try:
            structured_predictor = load_structured_predictor(model_dir, device_preference=device_preference)
        except Exception as exc:
            structured_load_error = str(exc)

    try:
        category_classifier = load_category_classifier(model_dir)
    except Exception as exc:
        category_load_error = str(exc)

    try:
        risk_model = load_risk_model(model_dir)
    except Exception as exc:
        risk_load_error = str(exc)

    @app.get("/health")
    def health() -> tuple[dict[str, str], int]:
        status = {
            "status": "ok",
            "mode": mode,
            "structured_loaded": structured_predictor is not None,
            "category_loaded": category_classifier is not None,
            "risk_loaded": risk_model is not None,
        }
        if structured_load_error:
            status["structured_load_error"] = structured_load_error
        if category_load_error:
            status["category_load_error"] = category_load_error
        if risk_load_error:
            status["risk_load_error"] = risk_load_error
        return status, 200

    @app.post("/extract-image")
    def extract_image():
        if "file" not in request.files:
            return jsonify({"error": "file is required"}), 400

        uploaded = request.files["file"]
        suffix = Path(uploaded.filename or "upload.png").suffix or ".png"
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as handle:
            temp_path = Path(handle.name)
        uploaded.save(temp_path)

        try:
            ocr_text = extract_ocr_text(temp_path)
            if mode == "legacy" or structured_predictor is None:
                result = legacy_extractor.predict(ocr_text)
                if structured_load_error:
                    result.extra_fields["structured_fallback_reason"] = structured_load_error
                enrich_with_ai_signals(result, ocr_text, category_classifier, risk_model, category_load_error, risk_load_error)
                return jsonify(result.to_response())

            legacy_result = legacy_extractor.predict(ocr_text)
            try:
                structured_result = structured_predictor.predict(ocr_text)
                result = merge_results(structured_result, legacy_result)
            except StructuredPredictionError as exc:
                legacy_result.extra_fields["structured_fallback_reason"] = str(exc)
                result = legacy_result
            enrich_with_ai_signals(result, ocr_text, category_classifier, risk_model, category_load_error, risk_load_error)
            return jsonify(result.to_response())
        except Exception as exc:
            fallback = ExtractionResult(
                name=None,
                invoice_number=None,
                amount=None,
                tax=None,
                discount=None,
                currency="INR",
                date=None,
                due_date=None,
                category=None,
                comment=None,
                seller_address=None,
                client_name=None,
                client_address=None,
                payment_method=None,
                bank_name=None,
                account_number=None,
                line_items=[],
                confidence={},
                raw_ocr_text=None,
                extra_fields={"error": str(exc)},
                classification=None,
                risk_prior=None,
            )
            return jsonify(fallback.to_response()), 500
        finally:
            temp_path.unlink(missing_ok=True)

    return app


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=5050)
    parser.add_argument("--mode", default="legacy", choices=("structured", "legacy"))
    parser.add_argument("--device", default="auto", choices=("auto", "mps", "cpu"))
    args = parser.parse_args()

    app = build_app(args.model_dir, mode=args.mode, device_preference=args.device)
    app.run(host=args.host, port=args.port)


def merge_results(structured_result: ExtractionResult, legacy_result: ExtractionResult) -> ExtractionResult:
    extra_fields = dict(structured_result.extra_fields)
    extra_fields["header_source"] = "structured"
    extra_fields["line_item_source"] = "legacy"

    merged_confidence = dict(structured_result.confidence)
    for field, score in legacy_result.confidence.items():
        merged_confidence.setdefault(f"legacy_{field}", score)

    return ExtractionResult(
        name=structured_result.name,
        invoice_number=structured_result.invoice_number,
        amount=structured_result.amount,
        tax=structured_result.tax,
        discount=structured_result.discount,
        currency=structured_result.currency,
        date=structured_result.date,
        due_date=structured_result.due_date,
        category=structured_result.category,
        comment=structured_result.comment,
        seller_address=structured_result.seller_address,
        client_name=structured_result.client_name,
        client_address=structured_result.client_address,
        payment_method=structured_result.payment_method,
        bank_name=structured_result.bank_name,
        account_number=structured_result.account_number,
        line_items=legacy_result.line_items,
        confidence=merged_confidence,
        raw_ocr_text=structured_result.raw_ocr_text or legacy_result.raw_ocr_text,
        extra_fields=extra_fields,
        classification=structured_result.classification or legacy_result.classification,
        risk_prior=structured_result.risk_prior or legacy_result.risk_prior,
    )


def enrich_with_ai_signals(result: ExtractionResult,
                           ocr_text: str,
                           category_classifier,
                           risk_model,
                           category_load_error: str | None,
                           risk_load_error: str | None) -> None:
    if category_classifier is not None:
        result.classification = category_classifier.predict(ocr_text, result)
    else:
        result.classification = ClassificationResultPayload(
            predicted_category=result.category,
            confidence=result.confidence.get("category"),
            alternatives=[],
            model_version="category-extractor-fallback-v1",
            source="extractor",
        )
        if category_load_error:
            result.extra_fields["category_fallback_reason"] = category_load_error

    if risk_model is not None:
        result.risk_prior = risk_model.predict(ocr_text, result, result.classification)
    elif risk_load_error:
        result.extra_fields["risk_fallback_reason"] = risk_load_error


if __name__ == "__main__":
    main()
