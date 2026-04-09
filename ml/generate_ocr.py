from __future__ import annotations

import argparse

from extractor.pipeline import generate_ocr


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--labels", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    generated = generate_ocr(args.labels, args.output_dir)
    print(f"generated_ocr={len(generated)} output_dir={args.output_dir}")


if __name__ == "__main__":
    main()
