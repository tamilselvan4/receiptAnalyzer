from __future__ import annotations

import argparse
import json

from extractor.pipeline import evaluate_model


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir", required=True)
    parser.add_argument("--model-dir", required=True)
    args = parser.parse_args()

    report = evaluate_model(args.splits_dir, args.model_dir)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
