from __future__ import annotations

import argparse
import json

from extractor.pipeline import build_splits


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--labels", required=True)
    parser.add_argument("--ocr-dir", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()

    report = build_splits(args.labels, args.ocr_dir, args.output_dir, seed=args.seed)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
