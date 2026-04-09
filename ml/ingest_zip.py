from __future__ import annotations

import argparse
import zipfile
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--zip-file", required=True)
    parser.add_argument("--output-dir", required=True)
    args = parser.parse_args()

    zip_path = Path(args.zip_file)
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(zip_path, "r") as archive:
        archive.extractall(output_dir)

    print(f"unzipped={zip_path} output_dir={output_dir}")


if __name__ == "__main__":
    main()
