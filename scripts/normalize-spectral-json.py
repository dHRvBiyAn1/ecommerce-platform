#!/usr/bin/env python3
"""Extract Spectral's leading JSON diagnostics from its raw CLI output."""

import json
import sys
from pathlib import Path


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: normalize-spectral-json.py RAW_FILE JSON_FILE", file=sys.stderr)
        return 2
    raw_path, json_path = map(Path, sys.argv[1:])
    try:
        raw = raw_path.read_text(encoding="utf-8")
        payload, _end = json.JSONDecoder().raw_decode(raw.lstrip())
        if not isinstance(payload, list):
            raise ValueError("Spectral JSON output must be an array")
        json_path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    except (OSError, json.JSONDecodeError, ValueError) as error:
        print(f"cannot normalize Spectral output: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
