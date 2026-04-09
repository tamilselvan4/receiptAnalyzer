from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .schema import ExtractionResult
from .structured_format import HEADER_FIELDS, build_header_prompt, parse_generated_header


STRUCTURED_MODEL_DIRNAME = "structured_header_model"
MODEL_CONFIG_FILE = "structured_model_config.json"


class StructuredPredictionError(RuntimeError):
    pass


@dataclass
class StructuredHeaderPredictor:
    model: Any
    tokenizer: Any
    device: Any
    max_input_length: int
    max_target_length: int

    @classmethod
    def load(cls, model_dir: str | Path, device_preference: str = "auto") -> "StructuredHeaderPredictor":
        import torch
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

        resolved_model_dir = resolve_structured_model_dir(model_dir)
        config_path = resolved_model_dir / MODEL_CONFIG_FILE
        if not config_path.exists():
            raise FileNotFoundError(f"Structured model config missing at {config_path}")

        config = json.loads(config_path.read_text(encoding="utf-8"))
        device = resolve_device(device_preference)
        tokenizer = AutoTokenizer.from_pretrained(resolved_model_dir)
        model = AutoModelForSeq2SeqLM.from_pretrained(resolved_model_dir)
        model.to(device)
        model.eval()
        if device.type == "mps":
            torch.mps.empty_cache()

        return cls(
            model=model,
            tokenizer=tokenizer,
            device=device,
            max_input_length=int(config.get("max_input_length", 1536)),
            max_target_length=int(config.get("max_target_length", 384)),
        )

    def predict(self, ocr_text: str) -> ExtractionResult:
        import torch

        prompt = build_header_prompt(ocr_text)
        encoded = self.tokenizer(
            prompt,
            max_length=self.max_input_length,
            truncation=True,
            return_tensors="pt",
        )
        encoded = {key: value.to(self.device) for key, value in encoded.items()}

        try:
            with torch.no_grad():
                output_ids = self.model.generate(
                    **encoded,
                    max_new_tokens=self.max_target_length,
                    num_beams=4,
                    do_sample=False,
                    early_stopping=True,
                )
            raw_output = self.tokenizer.decode(output_ids[0], skip_special_tokens=True)
            payload = parse_generated_header(raw_output)
        except Exception as exc:
            raise StructuredPredictionError(str(exc)) from exc

        confidence = {
            field: 0.88 if payload.get(field) is not None else 0.6
            for field in HEADER_FIELDS
        }

        return ExtractionResult(
            name=payload["name"],
            invoice_number=payload["invoice_number"],
            amount=payload["amount"],
            tax=payload["tax"],
            discount=payload["discount"],
            currency=payload["currency"],
            date=payload["date"],
            due_date=payload["due_date"],
            category=payload["category"],
            comment=payload["comment"],
            seller_address=payload["seller_address"],
            client_name=payload["client_name"],
            client_address=payload["client_address"],
            payment_method=payload["payment_method"],
            bank_name=payload["bank_name"],
            account_number=payload["account_number"],
            line_items=[],
            confidence=confidence,
            raw_ocr_text=ocr_text,
            extra_fields={},
        )


def resolve_structured_model_dir(model_dir: str | Path) -> Path:
    candidate = Path(model_dir)
    if (candidate / MODEL_CONFIG_FILE).exists():
        return candidate
    nested = candidate / STRUCTURED_MODEL_DIRNAME
    if nested.exists():
        return nested
    raise FileNotFoundError(f"Structured model directory not found under {candidate}")


def resolve_device(preference: str = "auto") -> Any:
    import torch

    normalized = preference.strip().lower()
    if normalized == "cpu":
        return torch.device("cpu")
    if normalized == "mps":
        if torch.backends.mps.is_available():
            return torch.device("mps")
        raise RuntimeError("MPS requested but not available.")
    if normalized != "auto":
        raise ValueError(f"Unsupported device preference: {preference}")

    if torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def load_structured_predictor(model_dir: str | Path, device_preference: str = "auto") -> StructuredHeaderPredictor:
    return StructuredHeaderPredictor.load(model_dir, device_preference=device_preference)
