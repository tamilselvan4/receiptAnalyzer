from __future__ import annotations

import argparse

from extractor.pipeline import normalize_labels


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-dir", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    rows = normalize_labels(args.raw_dir, args.output)
    print(f"normalized_rows={len(rows)} output={args.output}")


if __name__ == "__main__":
    main()
