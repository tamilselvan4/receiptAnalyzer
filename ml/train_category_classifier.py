from __future__ import annotations

import argparse

from extractor.category_classifier import CategoryClassifier, load_rows_from_split


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits-dir", required=True)
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--embedding-model", default="sentence-transformers/all-MiniLM-L6-v2")
    args = parser.parse_args()

    rows = load_rows_from_split(args.splits_dir, "train")
    classifier = CategoryClassifier.train(rows, embedding_model=args.embedding_model)
    model_path = classifier.save(args.model_dir)
    print(f"category_model_saved={model_path}")


if __name__ == "__main__":
    main()
