from __future__ import annotations

import json
import re
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics.pairwise import cosine_similarity

from .normalization import normalize_amount, normalize_currency, normalize_date, normalize_text
from .schema import DEFAULT_CURRENCY, ExtractionResult


MODEL_FILE = "model_bundle.joblib"
COMPANY_SUFFIXES = {"PLC", "LLC", "Inc", "Sons", "Ltd", "Group", "Corp", "Co", "Company"}
ADDRESS_MARKERS = {
    "Unit", "Suite", "Apt", "Apt.", "Box", "DPO", "FPO", "PSC", "USS", "USCGC", "USNS",
    "Highway", "Road", "Rd", "Street", "St", "Avenue", "Ave", "Route", "Trail", "Wall",
    "Gardens", "Way", "Drive", "Fork", "Center", "Crossing", "Crescent", "Mission",
    "Pass", "Loop", "Station"
}


def _safe_float(value: Any) -> float | None:
    if value is None:
        return None
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _most_common_string(values: list[str | None]) -> str | None:
    cleaned = [value for value in values if value]
    if not cleaned:
        return None
    return Counter(cleaned).most_common(1)[0][0]


def _average_numeric(values: list[float | None]) -> float | None:
    cleaned = [value for value in values if value is not None]
    if not cleaned:
        return None
    return round(float(sum(cleaned) / len(cleaned)), 2)


def _most_common_payload(values: list[Any]) -> Any:
    serialized = [json.dumps(value, sort_keys=True) for value in values if value not in (None, "", [], {})]
    if not serialized:
        return None
    return json.loads(Counter(serialized).most_common(1)[0][0])


def _extract_invoice_number(text: str) -> str | None:
    patterns = [
        r"(?:invoice|bill)\s*(?:number|no|#)\s*[:\-]?\s*([A-Z0-9\-\/]+)",
        r"\bINV[-\s]?[A-Z0-9]+\b",
    ]
    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if match:
            return match.group(1).strip()
    return None


def _extract_seller_name(text: str) -> str | None:
    seller_block = re.search(r"Seller:\s*(?:\n\s*)*([^\n]+)", text, flags=re.IGNORECASE)
    if seller_block:
        candidate = seller_block.group(1).strip(" ,.:;")
        lower_candidate = candidate.lower()
        if candidate and lower_candidate not in {"client", "seller"} and not lower_candidate.startswith("client"):
            return candidate

    match = re.search(r"Client:\s*(.+)", text)
    tail = match.group(1) if match else text
    raw_tokens = tail.replace("\n", " ").split()

    tokens: list[str] = []
    for token in raw_tokens:
        base = token.strip(",.:;")
        if any(char.isdigit() for char in token):
            break
        if base in ADDRESS_MARKERS:
            break
        tokens.append(token.replace(";", ","))
        if len(tokens) >= 10:
            break

    if not tokens:
        return None
    if len(tokens) >= 3 and "-" in tokens[0] and tokens[2].strip(",.:;") in COMPANY_SUFFIXES:
        return tokens[0].strip(",.:;")
    if len(tokens) >= 4 and tokens[0].endswith((",", ";")) and tokens[2].lower() == "and":
        return " ".join(tokens[:4]).replace(";", ",")
    if len(tokens) >= 3 and tokens[1].lower() == "and":
        return " ".join(tokens[:3]).replace(";", ",")
    if len(tokens) >= 3 and tokens[2].strip(",.:;") in COMPANY_SUFFIXES:
        return " ".join(tokens[:3]).replace(";", ",")
    if len(tokens) >= 2 and tokens[1].strip(",.:;") in COMPANY_SUFFIXES:
        return " ".join(tokens[:2]).replace(";", ",")
    return tokens[0].strip(",.:;")


def _extract_amount_candidates(text: str) -> list[float]:
    candidates = []
    for match in re.finditer(r"(?:(?:INR|USD|EUR|GBP|Rs\.?|₹|\$|€|£)\s*)?(-?\d[\d,]*(?:\.\d{1,2})?)", text):
        value = normalize_amount(match.group(0))
        if value is not None:
            candidates.append(value)
    return candidates


def _extract_keyword_amount(text: str, keywords: tuple[str, ...]) -> float | None:
    for line in text.splitlines():
        lowered = line.lower()
        if any(keyword in lowered for keyword in keywords):
            amounts = _extract_amount_candidates(line)
            if amounts:
                return amounts[-1]
    return None


def _confidence_from_similarity(score: float, agreement: float = 1.0) -> float:
    normalized = max(0.0, min(1.0, (score + 1.0) / 2.0))
    return round(max(0.05, min(0.99, normalized * agreement)), 2)


def _align_numeric_candidate(candidate: float | None, fallback: float | None) -> float | None:
    if candidate is None:
        return fallback
    if fallback is None or fallback <= 0:
        return round(candidate, 2)

    options = [candidate, candidate / 10.0, candidate / 100.0]
    best = min(options, key=lambda value: abs(value - fallback))
    ratio = max(candidate, fallback) / max(min(candidate, fallback), 0.01)
    if ratio >= 10:
        return round(best, 2)
    if abs(best - fallback) <= max(5.0, fallback * 0.5):
        return round(best, 2)
    return round(candidate, 2)


def _select_amount_candidate(candidates: list[float], fallback: float | None) -> float | None:
    if not candidates:
        return fallback
    if fallback is None:
        return round(max(candidates), 2)

    aligned = [_align_numeric_candidate(candidate, fallback) for candidate in candidates]
    best = min(aligned, key=lambda value: abs(value - fallback) if value is not None else float("inf"))
    return round(best, 2) if best is not None else fallback


def _extract_summary_amount_tax(text: str) -> tuple[float | None, float | None]:
    summary_index = text.lower().rfind("summary")
    if summary_index >= 0:
        summary_tail = text[summary_index:]
        summary_amounts = _extract_amount_candidates(summary_tail)
        if len(summary_amounts) >= 2:
            return summary_amounts[-1], summary_amounts[-2]
        if len(summary_amounts) == 1:
            return summary_amounts[0], None

    for line in text.splitlines():
        lowered = line.lower()
        if "total" not in lowered and "tota" not in lowered:
            continue
        amounts = _extract_amount_candidates(line)
        if len(amounts) >= 3:
            return amounts[-1], amounts[-2]
        if len(amounts) == 2:
            return amounts[-1], amounts[0]
        if len(amounts) == 1:
            return amounts[0], None
    return None, None


def _extract_issue_date(text: str) -> str | None:
    patterns = [
        r"date of issue[:\s]+([0-9]{1,2}[\/\-][0-9]{1,2}[\/\-][0-9]{2,4})",
        r"invoice date[:\s]+([0-9]{1,2}[\/\-][0-9]{1,2}[\/\-][0-9]{2,4})",
        r"date[:\s]+([0-9]{1,2}[\/\-][0-9]{1,2}[\/\-][0-9]{2,4})",
    ]
    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if match:
            return normalize_date(match.group(1))
    return None


def _extract_due_date(text: str) -> str | None:
    patterns = [
        r"due date[:\s]+([0-9]{1,2}[\/\-][0-9]{1,2}[\/\-][0-9]{2,4})",
        r"payment due[:\s]+([0-9]{1,2}[\/\-][0-9]{1,2}[\/\-][0-9]{2,4})",
    ]
    for pattern in patterns:
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if match:
            return normalize_date(match.group(1))
    return None


def _extract_party_names(text: str) -> tuple[str | None, str | None]:
    match = re.search(r"Client:\s+(.+?)(?:Tax Id:|ITEMS|SUMMARY)", text, flags=re.IGNORECASE)
    if not match:
        return None, None

    tokens = [token.strip(",.:;") for token in match.group(1).split() if token.strip(",.:;")]
    if len(tokens) < 2:
        return None, None
    return tokens[0], tokens[1]


def _extract_labeled_value(text: str, labels: tuple[str, ...]) -> str | None:
    for label in labels:
        pattern = rf"{label}[:\s]+([A-Z0-9\-\/ ]+)"
        match = re.search(pattern, text, flags=re.IGNORECASE)
        if match:
            value = normalize_text(match.group(1))
            if value:
                return value
    return None


@dataclass
class InvoiceExtractor:
    vectorizer: TfidfVectorizer
    text_matrix: Any
    records: list[dict[str, Any]]
    category_model: LogisticRegression | None = None

    @classmethod
    def train(cls, rows: list[dict[str, Any]]) -> "InvoiceExtractor":
        texts = [row["input"] for row in rows]
        if not texts:
            raise ValueError("Training rows are empty.")
        vectorizer = TfidfVectorizer(ngram_range=(1, 2), min_df=1, max_features=20000)
        text_matrix = vectorizer.fit_transform(texts)

        categories = [normalize_text(row["output"].get("category")) for row in rows]
        valid_categories = [category for category in categories if category]
        category_model = None
        if len(set(valid_categories)) >= 2:
            labels = [category or "Unknown" for category in categories]
            category_model = LogisticRegression(max_iter=1000, random_state=42)
            category_model.fit(text_matrix, labels)

        return cls(
            vectorizer=vectorizer,
            text_matrix=text_matrix,
            records=rows,
            category_model=category_model,
        )

    def save(self, model_dir: str | Path) -> Path:
        target = Path(model_dir)
        target.mkdir(parents=True, exist_ok=True)
        bundle_path = target / MODEL_FILE
        joblib.dump(
            {
                "vectorizer": self.vectorizer,
                "text_matrix": self.text_matrix,
                "records": self.records,
                "category_model": self.category_model,
            },
            bundle_path,
        )
        return bundle_path

    @classmethod
    def load(cls, model_dir: str | Path) -> "InvoiceExtractor":
        bundle = joblib.load(Path(model_dir) / MODEL_FILE)
        return cls(
            vectorizer=bundle["vectorizer"],
            text_matrix=bundle["text_matrix"],
            records=bundle["records"],
            category_model=bundle.get("category_model"),
        )

    def predict(self, text: str, top_k: int = 5) -> ExtractionResult:
        query = self.vectorizer.transform([text])
        similarities = cosine_similarity(query, self.text_matrix).ravel()
        top_indices = np.argsort(similarities)[::-1][:top_k]
        neighbors = [self.records[index] for index in top_indices]
        top_score = float(similarities[top_indices[0]]) if len(top_indices) else 0.0

        neighbor_outputs = [neighbor["output"] for neighbor in neighbors]
        primary_output = neighbor_outputs[0] if neighbor_outputs else {}
        names = [normalize_text(output.get("name")) for output in neighbor_outputs]
        invoice_numbers = [normalize_text(output.get("invoice_number")) for output in neighbor_outputs]
        amounts = [_safe_float(output.get("amount")) for output in neighbor_outputs]
        taxes = [_safe_float(output.get("tax")) for output in neighbor_outputs]
        discounts = [_safe_float(output.get("discount")) for output in neighbor_outputs]
        currencies = [normalize_text(output.get("currency")) for output in neighbor_outputs]
        dates = [normalize_text(output.get("date")) for output in neighbor_outputs]
        due_dates = [normalize_text(output.get("due_date")) for output in neighbor_outputs]
        comments = [normalize_text(output.get("comment")) for output in neighbor_outputs]
        seller_addresses = [normalize_text(output.get("seller_address")) for output in neighbor_outputs]
        client_names = [normalize_text(output.get("client_name")) for output in neighbor_outputs]
        client_addresses = [normalize_text(output.get("client_address")) for output in neighbor_outputs]
        payment_methods = [normalize_text(output.get("payment_method")) for output in neighbor_outputs]
        bank_names = [normalize_text(output.get("bank_name")) for output in neighbor_outputs]
        account_numbers = [normalize_text(output.get("account_number")) for output in neighbor_outputs]
        line_items = [output.get("line_items") for output in neighbor_outputs]

        summary_total, summary_tax = _extract_summary_amount_tax(text)
        total_from_keywords = summary_total or _extract_keyword_amount(text, ("total", "tota", "amount due", "grand total", "balance due"))
        tax_from_keywords = summary_tax or _extract_keyword_amount(text, ("tax", "gst", "vat", "sales tax", "cgst", "sgst", "igst"))
        amount_candidates = _extract_amount_candidates(text)
        filtered_amounts = [value for value in amount_candidates if value < 100000]
        neighbor_amount = _average_numeric(amounts)
        neighbor_tax = _average_numeric(taxes)
        amount_guess = total_from_keywords if total_from_keywords is not None else (
            _select_amount_candidate(filtered_amounts, neighbor_amount)
        )
        amount_guess = _align_numeric_candidate(amount_guess, neighbor_amount)
        tax_guess = tax_from_keywords if tax_from_keywords is not None else neighbor_tax
        tax_guess = _align_numeric_candidate(tax_guess, neighbor_tax)
        if tax_guess is None and filtered_amounts and len(filtered_amounts) >= 2:
            sorted_amounts = sorted(filtered_amounts)
            maybe_tax = sorted_amounts[-1] - sorted_amounts[-2]
            if maybe_tax >= 0:
                tax_guess = _align_numeric_candidate(maybe_tax, neighbor_tax)

        seller_from_text, client_from_text = _extract_party_names(text)
        predicted_name = _extract_seller_name(text) or seller_from_text or _most_common_string(names)
        predicted_currency = normalize_currency(_most_common_string(currencies), text)
        predicted_date = _extract_issue_date(text) or normalize_date(text) or _most_common_string(dates)
        predicted_due_date = _extract_due_date(text) or _most_common_string(due_dates)
        predicted_comment = _most_common_string(comments)
        predicted_invoice_number = _extract_invoice_number(text) or _most_common_string(invoice_numbers)
        predicted_discount = _extract_keyword_amount(text, ("discount",)) or _average_numeric(discounts)
        predicted_seller_address = normalize_text(primary_output.get("seller_address")) or _most_common_string(seller_addresses)
        predicted_client_name = client_from_text or _most_common_string(client_names)
        predicted_client_address = normalize_text(primary_output.get("client_address")) or _most_common_string(client_addresses)
        predicted_payment_method = _extract_labeled_value(text, ("payment method",)) or _most_common_string(payment_methods)
        predicted_bank_name = _extract_labeled_value(text, ("bank name",)) or _most_common_string(bank_names)
        predicted_account_number = _extract_labeled_value(text, ("account number", "account no", "iban")) or _most_common_string(account_numbers)
        predicted_line_items = _most_common_payload(line_items) or []

        predicted_category = _most_common_string([normalize_text(output.get("category")) for output in neighbor_outputs])
        if self.category_model is not None:
            predicted_category = self.category_model.predict(query)[0]

        extra_fields = {}
        if predicted_invoice_number and (not predicted_comment or predicted_invoice_number not in predicted_comment):
            predicted_comment = (predicted_comment + " | " if predicted_comment else "") + f"Invoice {predicted_invoice_number}"

        agreements = {
            "name": sum(1 for value in names if value == predicted_name) / max(1, len(names)),
            "invoice_number": sum(1 for value in invoice_numbers if value == predicted_invoice_number) / max(1, len(invoice_numbers)),
            "amount": sum(1 for value in amounts if value == amount_guess) / max(1, len(amounts)),
            "tax": sum(1 for value in taxes if value == tax_guess) / max(1, len(taxes)),
            "discount": sum(1 for value in discounts if value == predicted_discount) / max(1, len(discounts)),
            "currency": sum(1 for value in currencies if value == predicted_currency) / max(1, len(currencies)),
            "date": sum(1 for value in dates if value == predicted_date) / max(1, len(dates)),
            "due_date": sum(1 for value in due_dates if value == predicted_due_date) / max(1, len(due_dates)),
            "category": 1.0 if predicted_category else 0.5,
            "comment": 1.0 if predicted_comment else 0.5,
            "client_name": sum(1 for value in client_names if value == predicted_client_name) / max(1, len(client_names)),
        }

        return ExtractionResult(
            name=predicted_name,
            invoice_number=predicted_invoice_number,
            amount=amount_guess,
            tax=tax_guess,
            discount=predicted_discount,
            currency=predicted_currency or DEFAULT_CURRENCY,
            date=predicted_date,
            due_date=predicted_due_date,
            category=predicted_category,
            comment=predicted_comment,
            seller_address=predicted_seller_address,
            client_name=predicted_client_name,
            client_address=predicted_client_address,
            payment_method=predicted_payment_method,
            bank_name=predicted_bank_name,
            account_number=predicted_account_number,
            line_items=predicted_line_items,
            confidence={key: _confidence_from_similarity(top_score, agreement) for key, agreement in agreements.items()},
            raw_ocr_text=text,
            extra_fields=extra_fields,
        )


def load_extractor(model_dir: str | Path) -> InvoiceExtractor:
    return InvoiceExtractor.load(model_dir)
