from __future__ import annotations

import argparse

from extractor.pipeline import train_model


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir", required=True)
    parser.add_argument("--model-dir", required=True)
    args = parser.parse_args()

    model_path = train_model(args.splits_dir, args.model_dir)
    print(f"model_saved={model_path}")


if __name__ == "__main__":
    main()
