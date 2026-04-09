from __future__ import annotations

import argparse

from extractor.risk_model import RiskModel, load_rows_from_split


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir", required=True)
    parser.add_argument("--model-dir", required=True)
    args = parser.parse_args()

    rows = load_rows_from_split(args.splits_dir, "train")
    model = RiskModel.train(rows)
    model_path = model.save(args.model_dir)
    print(f"risk_model_saved={model_path}")


if __name__ == "__main__":
    main()
