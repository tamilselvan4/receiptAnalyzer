from __future__ import annotations

import argparse
import csv
from pathlib import Path

from extractor.risk_model import FEATURE_NAMES, features_from_row, load_rows_from_split


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir", required=True)
    parser.add_argument("--split", default="train", choices=("train", "validation", "test"))
    parser.add_argument("--output-file", required=True)
    args = parser.parse_args()

    rows = load_rows_from_split(args.splits_dir, args.split)
    output_file = Path(args.output_file)
    output_file.parent.mkdir(parents=True, exist_ok=True)
    with output_file.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["id", *FEATURE_NAMES])
        writer.writeheader()
        for row in rows:
            features = features_from_row(row)
            writer.writerow({"id": row.get("id"), **features})


if __name__ == "__main__":
    main()
