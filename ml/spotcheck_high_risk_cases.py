from __future__ import annotations

import argparse
import json
from pathlib import Path

from extractor.risk_model import load_risk_model, load_rows_from_split


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir", required=True)
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--output-file", required=True)
    parser.add_argument("--limit", type=int, default=10)
    args = parser.parse_args()

    model = load_risk_model(args.model_dir)
    report = model.evaluate(load_rows_from_split(args.splits_dir, "test"))
    payload = {
        "model_version": report["model_version"],
        "limit": args.limit,
        "cases": report["top_risky_cases"][:args.limit],
    }
    output = Path(args.output_file)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(json.dumps(payload, indent=2))


if __name__ == "__main__":
    main()
