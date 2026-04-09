from __future__ import annotations

import argparse
import json

from extractor.pipeline import audit_dataset


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw-dir", required=True)
    parser.add_argument("--report-dir", required=True)
    args = parser.parse_args()

    report = audit_dataset(args.raw_dir, args.report_dir)
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
