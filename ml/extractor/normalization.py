from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

from dateutil import parser as date_parser

from .schema import CANONICAL_FIELDS, CanonicalExpenseRecord, DEFAULT_CURRENCY


COLUMN_ALIASES = {
    "source_image": ["image", "image_name", "filename", "file_name", "png", "source_image", "receipt_image"],
    "name": ["name", "vendor", "vendor_name", "merchant", "merchant_name", "company", "company_name", "supplier"],
    "invoice_number": ["invoice_number", "invoice_no", "invoice_id", "bill_number"],
    "amount": ["amount", "total", "grand_total", "invoice_total", "net_amount", "expense_amount"],
    "tax": ["tax", "tax_amount", "gst", "vat", "sales_tax", "cgst", "sgst", "igst"],
    "discount": ["discount", "discount_amount"],
    "currency": ["currency", "currency_code", "curr", "invoice_currency"],
    "date": ["date", "invoice_date", "expense_date", "bill_date", "receipt_date"],
    "due_date": ["due_date", "payment_due_date"],
    "category": ["category", "expense_type", "type", "expense_category"],
    "comment": ["comment", "description", "notes", "invoice_number", "invoice_no", "reference", "memo"],
    "seller_address": ["seller_address", "vendor_address", "merchant_address"],
    "client_name": ["client_name", "customer_name", "bill_to"],
    "client_address": ["client_address", "customer_address", "bill_to_address"],
    "payment_method": ["payment_method", "payment_type"],
    "bank_name": ["bank_name", "bank"],
    "account_number": ["account_number", "account_no", "iban"],
}

CURRENCY_SYMBOLS = {
    "$": "USD",
    "€": "EUR",
    "£": "GBP",
    "₹": "INR",
}

LINE_ITEM_HINTS = {"item", "description", "quantity", "qty", "unit_price", "line_total", "hsn"}


def slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.strip().lower()).strip("_")


def normalize_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def normalize_amount(value: Any) -> float | None:
    text = normalize_text(value)
    if not text:
        return None
    text = re.sub(r"(?<=\d)\s+(?=\d)", "", text)
    for symbol in CURRENCY_SYMBOLS:
        text = text.replace(symbol, "")
    cleaned = re.sub(r"[^0-9,.\-]", "", text)
    if not cleaned:
        return None

    if "," in cleaned and "." in cleaned:
        if cleaned.rfind(",") > cleaned.rfind("."):
            cleaned = cleaned.replace(".", "").replace(",", ".")
        else:
            cleaned = cleaned.replace(",", "")
    elif "," in cleaned:
        if re.search(r",\d{1,2}$", cleaned):
            cleaned = cleaned.replace(",", ".")
        else:
            cleaned = cleaned.replace(",", "")
    elif "." in cleaned and not re.search(r"\.\d{1,2}$", cleaned):
        cleaned = cleaned.replace(".", "")

    match = re.search(r"-?\d+(?:\.\d+)?", cleaned)
    return float(match.group(0)) if match else None


def normalize_currency(value: Any, fallback_text: str | None = None) -> str:
    text = normalize_text(value)
    if text:
        upper = text.upper()
        if len(upper) == 3 and upper.isalpha():
            return upper
        for symbol, code in CURRENCY_SYMBOLS.items():
            if symbol in text:
                return code
    if fallback_text:
        for symbol, code in CURRENCY_SYMBOLS.items():
            if symbol in fallback_text:
                return code
        code_match = re.search(r"\b([A-Z]{3})\b", fallback_text.upper())
        if code_match:
            return code_match.group(1)
    return DEFAULT_CURRENCY


def normalize_date(value: Any) -> str | None:
    text = normalize_text(value)
    if not text:
        return None
    try:
        parsed = date_parser.parse(text, dayfirst=False, fuzzy=True)
        return parsed.date().isoformat()
    except (ValueError, OverflowError):
        return None


def load_jsonl(path: Path) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    with path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                records.append(json.loads(line))
    return records


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        for row in rows:
            handle.write(json.dumps(row, ensure_ascii=True) + "\n")


def infer_column_mapping(columns: list[str]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    slugged = {slug(column): column for column in columns}
    for target, aliases in COLUMN_ALIASES.items():
        for alias in aliases:
            if alias in slugged:
                mapping[target] = slugged[alias]
                break
    return mapping


def looks_like_line_items(columns: list[str]) -> bool:
    normalized = {slug(column) for column in columns}
    return len(normalized & LINE_ITEM_HINTS) >= 2


def _parse_json_blob(value: Any) -> dict[str, Any]:
    text = normalize_text(value)
    if not text:
        return {}
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        return {}


def _derive_category(*parts: Any) -> str:
    text = " ".join(str(part) for part in parts if part).lower()
    rules = [
        ("Travel", ("taxi", "flight", "hotel", "train", "bus", "travel", "uber", "lyft", "ticket")),
        ("Meals", ("restaurant", "cafe", "coffee", "food", "meal", "pizza", "kitchen", "wine", "bottle")),
        ("Office Supplies", ("office", "printer", "paper", "supply", "stationery", "desk", "rack", "holder")),
        ("Electronics", ("laptop", "phone", "charger", "monitor", "usb", "camera", "electronic")),
        ("Furniture", ("chair", "table", "furniture", "shelf", "cabinet")),
        ("Services", ("service", "consulting", "subscription", "maintenance", "repair")),
    ]
    for category, keywords in rules:
        if any(keyword in text for keyword in keywords):
            return category
    return "General"


def _build_comment(invoice_number: str | None, due_date: str | None, payment_method: str | None) -> str | None:
    parts = []
    if invoice_number:
        parts.append(f"Invoice {invoice_number}")
    if due_date:
        parts.append(f"Due {due_date}")
    if payment_method:
        parts.append(f"Payment {payment_method}")
    return " | ".join(parts) if parts else None


def _canonicalize_nested_json(row: dict[str, Any], image_path: str | None = None) -> CanonicalExpenseRecord | None:
    json_blob = _parse_json_blob(row.get("Json Data") or row.get("json_data"))
    if not json_blob:
        return None

    invoice = json_blob.get("invoice", {}) or {}
    subtotal = json_blob.get("subtotal", {}) or {}
    payment = json_blob.get("payment_instructions", {}) or {}
    items = json_blob.get("items", []) or []
    descriptions = " ".join(str(item.get("description", "")) for item in items if isinstance(item, dict))
    ocr_text = normalize_text(row.get("OCRed Text") or row.get("ocr_text"))

    invoice_number = normalize_text(invoice.get("invoice_number"))
    due_date = normalize_date(payment.get("due_date") or invoice.get("due_date"))
    payment_method = normalize_text(payment.get("payment_method"))
    line_items = []
    for item in items:
        if not isinstance(item, dict):
            continue
        line_items.append({
            "description": normalize_text(item.get("description")),
            "quantity": normalize_amount(item.get("quantity")),
            "total_price": normalize_amount(item.get("total_price")),
        })

    return CanonicalExpenseRecord(
        source_image=normalize_text(image_path) or normalize_text(row.get("File Name")) or normalize_text(row.get("file_name")) or "",
        name=normalize_text(invoice.get("seller_name")) or normalize_text(invoice.get("client_name")),
        invoice_number=invoice_number,
        amount=normalize_amount(subtotal.get("total")),
        tax=normalize_amount(subtotal.get("tax")),
        discount=normalize_amount(subtotal.get("discount")),
        currency=normalize_currency(None, ocr_text or json.dumps(json_blob)),
        date=normalize_date(invoice.get("invoice_date")),
        due_date=due_date,
        category=_derive_category(invoice.get("seller_name"), descriptions, ocr_text),
        comment=_build_comment(invoice_number, due_date, payment_method),
        seller_address=normalize_text(invoice.get("seller_address")),
        client_name=normalize_text(invoice.get("client_name")),
        client_address=normalize_text(invoice.get("client_address")),
        payment_method=payment_method,
        bank_name=normalize_text(payment.get("bank_name")),
        account_number=normalize_text(payment.get("account_number")),
        line_items=line_items,
        ocr_text=ocr_text,
        raw_fields={
            "json_data": json_blob,
            "file_name": row.get("File Name") or row.get("file_name"),
        },
    )


def canonicalize_row(row: dict[str, Any], mapping: dict[str, str], image_path: str | None = None) -> CanonicalExpenseRecord:
    nested = _canonicalize_nested_json(row, image_path)
    if nested is not None:
        return nested

    source_image = normalize_text(image_path) or normalize_text(row.get(mapping.get("source_image", ""))) or ""
    fallback_blob = " ".join(str(value) for value in row.values() if value is not None)
    raw_fields = {key: value for key, value in row.items() if normalize_text(value) is not None}

    record = CanonicalExpenseRecord(
        source_image=source_image,
        name=normalize_text(row.get(mapping.get("name", ""))),
        invoice_number=normalize_text(row.get(mapping.get("invoice_number", ""))),
        amount=normalize_amount(row.get(mapping.get("amount", ""))),
        tax=normalize_amount(row.get(mapping.get("tax", ""))),
        discount=normalize_amount(row.get(mapping.get("discount", ""))),
        currency=normalize_currency(row.get(mapping.get("currency", "")), fallback_blob),
        date=normalize_date(row.get(mapping.get("date", ""))),
        due_date=normalize_date(row.get(mapping.get("due_date", ""))),
        category=normalize_text(row.get(mapping.get("category", ""))),
        comment=normalize_text(row.get(mapping.get("comment", ""))),
        seller_address=normalize_text(row.get(mapping.get("seller_address", ""))),
        client_name=normalize_text(row.get(mapping.get("client_name", ""))),
        client_address=normalize_text(row.get(mapping.get("client_address", ""))),
        payment_method=normalize_text(row.get(mapping.get("payment_method", ""))),
        bank_name=normalize_text(row.get(mapping.get("bank_name", ""))),
        account_number=normalize_text(row.get(mapping.get("account_number", ""))),
        line_items=[],
        ocr_text=normalize_text(row.get("OCRed Text") or row.get("ocr_text")),
        raw_fields=raw_fields,
    )
    return record


def summarize_columns(columns: list[str]) -> list[dict[str, Any]]:
    summary = []
    mapping = infer_column_mapping(columns)
    for canonical in CANONICAL_FIELDS:
        summary.append({
            "canonical_field": canonical,
            "mapped_column": mapping.get(canonical),
        })
    return summary
