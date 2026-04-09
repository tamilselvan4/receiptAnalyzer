from __future__ import annotations

from pathlib import Path

try:
    import pytesseract
    from PIL import Image
except ImportError:  # pragma: no cover
    pytesseract = None
    Image = None


def extract_ocr_text(image_path: str | Path) -> str:
    if pytesseract is None or Image is None:
        raise RuntimeError("pytesseract and Pillow are required for OCR generation.")
    with Image.open(image_path) as image:
        return pytesseract.image_to_string(image)
