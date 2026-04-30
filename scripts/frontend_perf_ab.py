#!/usr/bin/env python3
from __future__ import annotations

import argparse
def main() -> int:
    argparse.ArgumentParser(
        description="Deprecated: frontend A/B comparison was removed because frontend is always enabled."
    ).parse_args()
    print("frontend_perf_ab.py is obsolete: borb no longer supports disabling the frontend.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
