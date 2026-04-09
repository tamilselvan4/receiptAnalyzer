from __future__ import annotations

import argparse
import json
from pathlib import Path

from extractor.category_classifier import load_category_classifier, load_rows_from_split


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir", required=True)
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--report-file")
    args = parser.parse_args()

    classifier = load_category_classifier(args.model_dir)
    report = classifier.evaluate(load_rows_from_split(args.splits_dir, "test"))
    if args.report_file:
        Path(args.report_file).write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
