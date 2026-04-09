from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


DEFAULT_CURRENCY = "INR"
CANONICAL_FIELDS = [
    "source_image",
    "name",
    "invoice_number",
    "amount",
    "tax",
    "discount",
    "currency",
    "date",
    "due_date",
    "category",
    "comment",
    "seller_address",
    "client_name",
    "client_address",
    "payment_method",
    "bank_name",
    "account_number",
    "line_items",
]


@dataclass
class ClassificationAlternativePayload:
    label: str
    confidence: float


@dataclass
class ClassificationResultPayload:
    predicted_category: str | None
    confidence: float | None
    alternatives: list[ClassificationAlternativePayload] = field(default_factory=list)
    model_version: str | None = None
    source: str | None = None


@dataclass
class RiskPriorPayload:
    anomaly_score: float | None
    model_version: str | None = None
    feature_flags: list[str] = field(default_factory=list)


@dataclass
class CanonicalExpenseRecord:
    source_image: str
    name: str | None = None
    invoice_number: str | None = None
    amount: float | None = None
    tax: float | None = None
    discount: float | None = None
    currency: str | None = DEFAULT_CURRENCY
    date: str | None = None
    due_date: str | None = None
    category: str | None = None
    comment: str | None = None
    seller_address: str | None = None
    client_name: str | None = None
    client_address: str | None = None
    payment_method: str | None = None
    bank_name: str | None = None
    account_number: str | None = None
    line_items: list[dict[str, Any]] = field(default_factory=list)
    ocr_text: str | None = None
    raw_fields: dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        payload = asdict(self)
        payload["currency"] = payload["currency"] or DEFAULT_CURRENCY
        return payload


@dataclass
class ExtractionResult:
    name: str | None
    invoice_number: str | None
    amount: float | None
    tax: float | None
    discount: float | None
    currency: str | None
    date: str | None
    due_date: str | None
    category: str | None
    comment: str | None
    seller_address: str | None = None
    client_name: str | None = None
    client_address: str | None = None
    payment_method: str | None = None
    bank_name: str | None = None
    account_number: str | None = None
    line_items: list[dict[str, Any]] = field(default_factory=list)
    confidence: dict[str, float] = field(default_factory=dict)
    raw_ocr_text: str | None = None
    extra_fields: dict[str, Any] = field(default_factory=dict)
    classification: ClassificationResultPayload | None = None
    risk_prior: RiskPriorPayload | None = None

    def to_response(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "invoice_number": self.invoice_number,
            "amount": self.amount,
            "tax": self.tax,
            "discount": self.discount,
            "currency": self.currency or DEFAULT_CURRENCY,
            "date": self.date,
            "due_date": self.due_date,
            "category": self.category,
            "comment": self.comment,
            "seller_address": self.seller_address,
            "client_name": self.client_name,
            "client_address": self.client_address,
            "payment_method": self.payment_method,
            "bank_name": self.bank_name,
            "account_number": self.account_number,
            "line_items": self.line_items,
            "confidence": self.confidence,
            "raw_ocr_text": self.raw_ocr_text,
            "extra_fields": self.extra_fields,
            "classification": asdict(self.classification) if self.classification else None,
            "risk_prior": asdict(self.risk_prior) if self.risk_prior else None,
        }
